package eu.opencloud.android.lib.resources.files.tus

import android.util.Base64
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.nonwebdav.PostMethod
import eu.opencloud.android.lib.common.network.ChunkFromFileRequestBody
import eu.opencloud.android.lib.common.network.WebdavUtils
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import java.nio.channels.FileChannel

class CreateTusUploadRemoteOperation(
    private val file: File,
    private val remotePath: String,
    @Suppress("UnusedPrivateProperty")
    private val mimetype: String,
    private val metadata: Map<String, String>,
    private val useCreationWithUpload: Boolean,
    private val firstChunkSize: Long?,
    private val tusUrl: String?,
    private val collectionUrlOverride: String? = null,
    private val base64Encoder: Base64Encoder = DefaultBase64Encoder()
) : RemoteOperation<String>() {

    interface Base64Encoder {
        fun encode(bytes: ByteArray): String
    }

    class DefaultBase64Encoder : Base64Encoder {
        override fun encode(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    override fun run(client: OpenCloudClient): RemoteOperationResult<String> = try {
        // Determine TUS endpoint URL based on provided parameters
        val targetFileUrl = if (!tusUrl.isNullOrBlank()) {
            tusUrl
        } else {
            val baseCollection = (collectionUrlOverride
                ?: client.userFilesWebDavUri.toString()).trim()
            // Remove trailing slash - OpenCloud expects no slash on space endpoints
            val resolvedCollection = buildCollectionUrl(baseCollection, remotePath).trimEnd('/')
            Timber.d("TUS resolved collection: %s", resolvedCollection)
            resolvedCollection
        }

            Timber.d("TUS Creation URL: %s", targetFileUrl)

            // Prepare request body first
            val postBody: RequestBody = if (useCreationWithUpload && (firstChunkSize ?: 0L) > 0L) {
                // creation-with-upload: include first chunk
                // Don't use .use{} here - the channel must stay open for OkHttp to read
                val raf = RandomAccessFile(file, "r")
                val channel: FileChannel = raf.channel
                ChunkFromFileRequestBody(
                    file = file,
                    contentType = HttpConstants.CONTENT_TYPE_OFFSET_OCTET_STREAM.toMediaTypeOrNull(),
                    channel = channel,
                    chunkSize = firstChunkSize!!
                )
            } else {
                // creation only: empty body
                ByteArray(0).toRequestBody(null)
            }

            val postMethod = PostMethod(URL(targetFileUrl), postBody)

            // Set TUS headers
            postMethod.setRequestHeader(HttpConstants.TUS_RESUMABLE, "1.0.0")
            postMethod.setRequestHeader(HttpConstants.UPLOAD_LENGTH, file.length().toString())
            postMethod.setRequestHeader(HttpConstants.CONTENT_TYPE_HEADER, HttpConstants.CONTENT_TYPE_OFFSET_OCTET_STREAM)
            // Set TUS-Extension header to indicate which extensions we want to use
            val extensions = if (useCreationWithUpload && (firstChunkSize ?: 0L) > 0L) {
                "creation,creation-with-upload"
            } else {
                "creation"
            }
            postMethod.setRequestHeader(HttpConstants.TUS_EXTENSION, extensions)

            // Prepare Upload-Metadata like iOS SDK
            val allMetadata = metadata.toMutableMap()
            allMetadata.putIfAbsent("filename", remotePath.substringAfterLast('/'))
            allMetadata.putIfAbsent("mtime", (file.lastModified() / 1000).toString())

            if (allMetadata.isNotEmpty()) {
                postMethod.setRequestHeader(HttpConstants.UPLOAD_METADATA, encodeTusMetadata(allMetadata))
            }

            // Set Upload-Offset for creation-with-upload
            if (useCreationWithUpload && (firstChunkSize ?: 0L) > 0L) {
                postMethod.setRequestHeader(HttpConstants.UPLOAD_OFFSET, "0")
            }

            val status = client.executeHttpMethod(postMethod)
            Timber.d("TUS Creation [%s] - %d%s", targetFileUrl, status, if (!isSuccess(status)) " (FAIL)" else "")
            if (!isSuccess(status)) {
                Timber.w("TUS Creation failed - Status: %d", status)
                Timber.w("  Target URL: %s", targetFileUrl)
                Timber.w("  Collection Override: %s", collectionUrlOverride)
                Timber.w("  User Files WebDAV: %s", client.userFilesWebDavUri)
                Timber.w("  Remote Path: %s", remotePath)
                Timber.w("  File Size: %d bytes", file.length())
                Timber.w("  Tus-Resumable: %s", postMethod.getRequestHeader(HttpConstants.TUS_RESUMABLE))
                Timber.w("  Upload-Length: %s", postMethod.getRequestHeader(HttpConstants.UPLOAD_LENGTH))
                Timber.w("  Upload-Metadata: %s", postMethod.getRequestHeader(HttpConstants.UPLOAD_METADATA))
            }

            // Debug logging for troubleshooting
            if (status == 412) {
                Timber.w("HTTP 412 Precondition Failed - Request headers:")
                Timber.w("  Tus-Resumable: %s", postMethod.getRequestHeader(HttpConstants.TUS_RESUMABLE))
                Timber.w("  Tus-Extension: %s", postMethod.getRequestHeader(HttpConstants.TUS_EXTENSION))
                Timber.w("  Upload-Length: %s", postMethod.getRequestHeader(HttpConstants.UPLOAD_LENGTH))
                Timber.w("  Upload-Metadata: %s", postMethod.getRequestHeader(HttpConstants.UPLOAD_METADATA))
                Timber.w("  Content-Type: %s", postMethod.getRequestHeader(HttpConstants.CONTENT_TYPE_HEADER))
                Timber.w("  Content-Length: %d", postBody.contentLength())
                if (useCreationWithUpload && (firstChunkSize ?: 0L) > 0L) {
                    Timber.w("  Upload-Offset: %s", postMethod.getRequestHeader(HttpConstants.UPLOAD_OFFSET))
                }
            }

            if (isSuccess(status)) {
                val locationHeader = postMethod.getResponseHeader(HttpConstants.LOCATION_HEADER)
                    ?: postMethod.getResponseHeader(HttpConstants.LOCATION_HEADER_LOWER)
                val base = URL(postMethod.getFinalUrl().toString())
                val resolved = resolveLocationToAbsolute(locationHeader, base)

                if (resolved != null) {
                    Timber.d("TUS upload resource created: %s", resolved)
                    RemoteOperationResult<String>(ResultCode.OK).apply { data = resolved }
                } else {
                    Timber.e("Location header is missing in TUS creation response")
                    RemoteOperationResult<String>(IllegalStateException("Location header missing")).apply {
                        data = ""
                    }
                }
            } else {
                Timber.w("TUS creation failed with status: %d", status)
                RemoteOperationResult<String>(postMethod).apply { data = "" }
            }
    } catch (e: Exception) {
        val result = RemoteOperationResult<String>(e)
        Timber.e(e, "TUS creation operation failed")
        result
    }

    private fun isSuccess(status: Int) =
        status.isOneOf(HttpConstants.HTTP_CREATED, HttpConstants.HTTP_OK)

    private fun encodeTusMetadata(metadata: Map<String, String>): String =
        metadata.entries.joinToString(",") { (key, value) ->
            val encoded = base64Encoder.encode(value.toByteArray(Charsets.UTF_8))
            "$key $encoded"
        }

    private fun resolveLocationToAbsolute(location: String?, base: URL): String? {
        if (location.isNullOrBlank()) return null
        return try {
            URL(base, location).toString()
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve Location header: %s", location)
            null
        }
    }



    private fun buildCollectionUrl(base: String, remotePath: String): String {
        val normalizedBase = base.trim().trimEnd('/')
        val sanitizedRemotePath = remotePath.trim().trimEnd('/').ifEmpty { "/" }
        if (sanitizedRemotePath == "/") {
            return normalizedBase
        }

        val encodedPath = WebdavUtils.encodePath(sanitizedRemotePath)
        val parentSegment = when (val idx = encodedPath.lastIndexOf('/')) {
            -1, 0 -> ""
            else -> encodedPath.substring(0, idx).removePrefix("/")
        }

        return if (parentSegment.isEmpty()) {
            normalizedBase
        } else {
            "$normalizedBase/$parentSegment"
        }
    }

    companion object {
        // Use 10MB for first chunk like the browser does
        const val DEFAULT_FIRST_CHUNK = 10 * 1024 * 1024L // 10MB
    }
}
