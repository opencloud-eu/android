package eu.opencloud.android.workers

import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.domain.capabilities.model.OCCapability
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.network.OnDatatransferProgressListener

import eu.opencloud.android.lib.resources.files.chunks.ChunkedUploadFromFileSystemOperation
import eu.opencloud.android.lib.resources.files.tus.CreateTusUploadRemoteOperation
import eu.opencloud.android.lib.resources.files.tus.GetTusUploadOffsetRemoteOperation
import eu.opencloud.android.lib.resources.files.tus.PatchTusUploadChunkRemoteOperation
import timber.log.Timber
import java.io.File
import kotlin.math.min

/**
 * Shared helper encapsulating the TUS upload flow so workers can reuse the same implementation.
 */
class TusUploadHelper(
    private val transferRepository: TransferRepository,
) {
    @Volatile
    private var cancelled = false
    
    fun cancel() {
        cancelled = true
        Timber.d("TUS: upload cancellation requested")
    }

    /**
     * Runs the full TUS upload flow. On success the method returns normally. On failure an exception
     * is thrown so the caller can decide whether to retry or surface the error.
     */
    @Throws(Exception::class)
    fun upload(
        client: OpenCloudClient,
        transfer: OCTransfer,
        uploadId: Long,
        localPath: String,
        remotePath: String,
        fileSize: Long,
        mimeType: String,
        lastModified: String?,
        tusSupport: OCCapability.TusSupport?,
        progressListener: OnDatatransferProgressListener?,
        progressCallback: ((Long, Long) -> Unit)? = null,
        spaceWebDavUrl: String? = null,
    ) {
        Timber.d("TUS: starting upload for %s size=%d", remotePath, fileSize)

        var tusUrl = transfer.tusUploadUrl
        val checksumHex = transfer.tusUploadChecksum?.substringAfter("sha256:")

        if (tusUrl.isNullOrBlank()) {
            val fileName = File(remotePath).name
            val metadata = linkedMapOf(
                "filename" to fileName,
                "mimetype" to mimeType,
            )
            lastModified?.takeIf { it.isNotBlank() }?.let { metadata["mtime"] = it }
            checksumHex?.let { metadata["checksum"] = "sha256 $it" }

            Timber.d(
                "TUS: creating upload resource filename=%s size=%d metadata=%s",
                fileName,
                fileSize,
                metadata
            )

            val collectionUrl = resolveTusCollectionUrl(
                client = client,
                spaceWebDavUrl = spaceWebDavUrl
            )

            // Use creation-with-upload like the browser does for OpenCloud compatibility
            val firstChunkSize = minOf(CreateTusUploadRemoteOperation.DEFAULT_FIRST_CHUNK, fileSize)
            val createdLocation = executeRemoteOperation {
                CreateTusUploadRemoteOperation(
                    file = File(localPath),
                    remotePath = remotePath,
                    mimetype = mimeType,
                    metadata = metadata,
                    useCreationWithUpload = true,
                    firstChunkSize = firstChunkSize,
                    tusUrl = "",
                    collectionUrlOverride = collectionUrl,
                ).execute(client)
            }

            if (createdLocation.isNullOrBlank()) {
                throw IllegalStateException("TUS: unable to create upload resource for $remotePath")
            }

            tusUrl = createdLocation
            val metadataString = metadata.entries.joinToString(";") { (key, value) -> "$key=$value" }

            transferRepository.updateTusState(
                id = uploadId,
                tusUploadUrl = tusUrl,
                tusUploadLength = fileSize,
                tusUploadMetadata = metadataString,
                tusUploadChecksum = checksumHex?.let { "sha256:$it" },
                tusResumableVersion = "1.0.0",
                tusUploadExpires = null,
                tusUploadConcat = null,
            )
        }

        val resolvedTusUrl = tusUrl ?: throw IllegalStateException("TUS: missing upload URL for $remotePath")

        var offset = try {
            executeRemoteOperation {
                GetTusUploadOffsetRemoteOperation(resolvedTusUrl).execute(client)
            }
        } catch (e: java.io.IOException) {
            Timber.w(e, "TUS: failed to fetch current offset")
            throw e
        } catch (e: Throwable) {
            Timber.w(e, "TUS: failed to fetch current offset")
            0L
        }.coerceAtLeast(0L)
        Timber.d("TUS: resume offset %d / %d", offset, fileSize)
        progressCallback?.invoke(offset, fileSize)

        offset = performUploadLoop(
            client = client,
            resolvedTusUrl = resolvedTusUrl,
            localPath = localPath,
            fileSize = fileSize,
            tusSupport = tusSupport,
            progressListener = progressListener,
            progressCallback = progressCallback,
            initialOffset = offset
        )

        // Verify upload is actually complete
        if (offset != fileSize) {
            Timber.e("TUS: upload loop exited but offset=%d != fileSize=%d", offset, fileSize)
            throw java.io.IOException("TUS: upload incomplete - offset $offset does not match file size $fileSize")
        }
        
        transferRepository.updateTusState(
            id = uploadId,
            tusUploadUrl = null,
            tusUploadLength = null,
            tusUploadMetadata = null,
            tusUploadChecksum = null,
            tusResumableVersion = null,
            tusUploadExpires = null,
            tusUploadConcat = null,
        )
        Timber.i("TUS: upload completed for %s (size=%d)", remotePath, fileSize)
    }

    private fun performUploadLoop(
        client: OpenCloudClient,
        resolvedTusUrl: String,
        localPath: String,
        fileSize: Long,
        tusSupport: OCCapability.TusSupport?,
        progressListener: OnDatatransferProgressListener?,
        progressCallback: ((Long, Long) -> Unit)?,
        initialOffset: Long
    ): Long {
        var offset = initialOffset
        val serverMaxChunk = tusSupport?.maxChunkSize?.takeIf { it > 0 }?.toLong()
        val httpOverride = tusSupport?.httpMethodOverride
        var consecutiveFailures = 0

        while (offset < fileSize && !cancelled) {
            val remaining = fileSize - offset
            val chunkSize = min(DEFAULT_CHUNK_SIZE, min(remaining, serverMaxChunk ?: Long.MAX_VALUE))
            Timber.d("TUS: uploading chunk=%d at offset=%d remaining=%d", chunkSize, offset, remaining)

            if (cancelled) {
                Timber.i("TUS: upload cancelled by user at offset %d", offset)
                throw java.io.InterruptedIOException("TUS upload cancelled by user")
            }
            
            val patchOperation = PatchTusUploadChunkRemoteOperation(
                localPath = localPath,
                uploadUrl = resolvedTusUrl,
                offset = offset,
                chunkSize = chunkSize,
                httpMethodOverride = httpOverride,
            ).apply {
                progressListener?.let { addDataTransferProgressListener(it) }
            }

            val patchResult = patchOperation.execute(client)
            if (!patchResult.isSuccess || patchResult.data == null || patchResult.data!! < offset) {
                consecutiveFailures++
                Timber.w(
                    "TUS: PATCH failed at offset %d (retry %d/%d)",
                    offset,
                    consecutiveFailures,
                    MAX_RETRIES
                )

                // Try to recover the offset from server
                val recoveredOffset = tryRecoverOffset(
                    client = client,
                    tusUrl = resolvedTusUrl,
                    currentOffset = offset,
                    totalSize = fileSize,
                    progressCallback = progressCallback,
                )

                if (recoveredOffset != null && recoveredOffset > offset) {
                    // Server has progressed beyond our current offset, update and reset retry counter
                    Timber.d("TUS: server advanced from %d to %d, continuing", offset, recoveredOffset)
                    offset = recoveredOffset
                    consecutiveFailures = 0
                    continue
                } else if (recoveredOffset != null && recoveredOffset == offset) {
                    // Server is at same offset, we need to retry the same chunk
                    Timber.d("TUS: server confirmed offset %d, will retry same chunk", offset)
                    // Don't update offset, will retry after backoff
                } else if (recoveredOffset != null && recoveredOffset < offset) {
                    // Server is behind our position (e.g. crash/data loss). Rewind and continue.
                    Timber.w("TUS: server offset %d is behind current %d. Rewinding...", recoveredOffset, offset)
                    offset = recoveredOffset
                    consecutiveFailures = 0
                    continue
                } else {
                    // Recovery failed or returned invalid offset
                    Timber.w("TUS: offset recovery failed (recovered=%s, current=%d)", recoveredOffset, offset)
                }

                // Check if we've exhausted retries
                if (consecutiveFailures >= MAX_RETRIES) {
                    throw java.io.IOException(
                        "TUS: giving up after $MAX_RETRIES retries at offset $offset (network error)",
                        IllegalStateException("TUS: max retries exceeded")
                    )
                }

                // Exponential backoff before retry
                val delayMs = min(MAX_RETRY_DELAY_MS, BASE_RETRY_DELAY_MS shl (consecutiveFailures - 1))
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.IOException(
                        "TUS: interrupted while retrying at offset $offset",
                        InterruptedException("TUS retry interrupted")
                    )
                }
                continue
            }

            // Success - validate the returned offset
            val newOffset = patchResult.data!!
            if (newOffset < offset) {
                Timber.e("TUS: server returned offset %d less than current %d, upload corrupted", newOffset, offset)
                throw java.io.IOException("TUS: server offset went backwards from $offset to $newOffset")
            }
            if (newOffset > fileSize) {
                Timber.e("TUS: server returned offset %d exceeds file size %d", newOffset, fileSize)
                throw java.io.IOException("TUS: server offset $newOffset exceeds file size $fileSize")
            }

            offset = newOffset
            progressCallback?.invoke(offset, fileSize)
            consecutiveFailures = 0
        }
        return offset
    }

    private fun resolveTusCollectionUrl(
        client: OpenCloudClient,
        spaceWebDavUrl: String?,
    ): String {
        // For OpenCloud, TUS works on the WebDAV space endpoint
        // Use the space WebDAV URL if available, otherwise fall back to user files
        val base = (spaceWebDavUrl?.takeIf { it.isNotBlank() }
            ?: client.userFilesWebDavUri.toString()).trim()

        // Use the space root directly for TUS (no trailing slash for OpenCloud)
        val normalizedBase = base.trimEnd('/')

        Timber.d("TUS: using collection endpoint: %s", normalizedBase)

        return normalizedBase
    }

    private fun tryRecoverOffset(
        client: OpenCloudClient,
        tusUrl: String,
        currentOffset: Long,
        totalSize: Long,
        progressCallback: ((Long, Long) -> Unit)?,
    ): Long? = try {
            val newOffset = executeRemoteOperation {
                GetTusUploadOffsetRemoteOperation(tusUrl).execute(client)
            }
            if (newOffset >= 0 && newOffset <= totalSize) {
                if (newOffset > currentOffset) {
                    // Server has advanced beyond our position
                    progressCallback?.invoke(newOffset, totalSize)
                    Timber.d("TUS: recovered offset %d (was %d)", newOffset, currentOffset)
                } else if (newOffset == currentOffset) {
                    // Server is at same position, return it to confirm
                    Timber.d("TUS: server confirmed current offset %d", currentOffset)
                } else {
                    // Server is behind our position - can happen if server lost data (crash)
                    Timber.w("TUS: server offset %d is behind current %d", newOffset, currentOffset)
                }
                newOffset
            } else {
                Timber.w("TUS: invalid recovered offset %d (total=%d)", newOffset, totalSize)
                null
            }
        } catch (recoverError: Throwable) {
            Timber.w(recoverError, "TUS: recover offset failed")
            if (recoverError is java.io.IOException) {
                throw recoverError
            }
            null
        }


    companion object {
        const val DEFAULT_CHUNK_SIZE = ChunkedUploadFromFileSystemOperation.CHUNK_SIZE
        private const val MAX_RETRIES = 5
        private const val BASE_RETRY_DELAY_MS = 250L
        private const val MAX_RETRY_DELAY_MS = 2_000L
    }
}
