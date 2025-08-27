/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author Aitor Ballesteros Pavón
 *
 * Copyright (C) 2024 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.workers

import android.accounts.Account
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.opencloud.android.R
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.data.providers.LocalStorageProvider
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.exceptions.LocalFileNotFoundException
import eu.opencloud.android.domain.exceptions.UnauthorizedException
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.domain.transfers.model.TransferResult
import eu.opencloud.android.domain.transfers.model.TransferStatus
import eu.opencloud.android.extensions.isContentUri
import eu.opencloud.android.extensions.parseError
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.SingleSessionManager
import eu.opencloud.android.lib.common.network.OnDatatransferProgressListener
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.resources.files.CheckPathExistenceRemoteOperation
import eu.opencloud.android.lib.resources.files.CreateRemoteFolderOperation
import eu.opencloud.android.lib.resources.files.FileUtils
import eu.opencloud.android.lib.resources.files.UploadFileFromFileSystemOperation
import eu.opencloud.android.lib.resources.files.chunks.ChunkedUploadFromFileSystemOperation
import eu.opencloud.android.lib.resources.files.chunks.ChunkedUploadFromFileSystemOperation.Companion.CHUNK_SIZE
import eu.opencloud.android.lib.resources.files.services.implementation.OCChunkService
import eu.opencloud.android.lib.resources.files.tus.CreateTusUploadRemoteOperation
import eu.opencloud.android.lib.resources.files.tus.GetTusUploadOffsetRemoteOperation
import eu.opencloud.android.lib.resources.files.tus.PatchTusUploadChunkRemoteOperation
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.utils.NotificationUtils
import eu.opencloud.android.utils.RemoteFileUtils.getAvailableRemotePath
import eu.opencloud.android.utils.SecurityUtils
import eu.opencloud.android.utils.UPLOAD_NOTIFICATION_CHANNEL_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.security.MessageDigest

class UploadFileFromContentUriWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent, OnDatatransferProgressListener {

    private lateinit var account: Account
    private lateinit var contentUri: Uri
    private lateinit var lastModified: String
    private lateinit var behavior: UploadBehavior
    private lateinit var uploadPath: String
    private lateinit var cachePath: String
    private lateinit var mimeType: String
    private var fileSize: Long = 0
    private var uploadIdInStorageManager: Long = -1
    private lateinit var ocTransfer: OCTransfer
    private var spaceWebDavUrl: String? = null

    private lateinit var uploadFileOperation: UploadFileFromFileSystemOperation

    private var lastPercent = 0

    private val transferRepository: TransferRepository by inject()
    private val getWebdavUrlForSpaceUseCase: GetWebDavUrlForSpaceUseCase by inject()
    private val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase by inject()
    

    override suspend fun doWork(): Result {

        if (!areParametersValid()) return Result.failure()

        transferRepository.updateTransferStatusToInProgressById(uploadIdInStorageManager)

        spaceWebDavUrl =
            getWebdavUrlForSpaceUseCase(GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = ocTransfer.spaceId))

        val localStorageProvider: LocalStorageProvider by inject()
        cachePath = localStorageProvider.getTemporalPath(account.name, ocTransfer.spaceId) + uploadPath

        return try {
            if (ocTransfer.isContentUri(appContext)) {
                checkDocumentFileExists()
                checkPermissionsToReadDocumentAreGranted()
                copyFileToLocalStorage()
            }

            val clientForThisUpload = getClientForThisUpload()
            checkParentFolderExistence(clientForThisUpload)
            checkNameCollisionAndGetAnAvailableOneInCase(clientForThisUpload)
            uploadDocument(clientForThisUpload)
            updateUploadsDatabaseWithResult(null)
            Result.success()
        } catch (throwable: Throwable) {
            Timber.e(throwable)
            showNotification(throwable)
            updateUploadsDatabaseWithResult(throwable)
            Result.failure()
        }
    }

    private fun areParametersValid(): Boolean {
        val paramAccountName = workerParameters.inputData.getString(KEY_PARAM_ACCOUNT_NAME)
        val paramUploadPath = workerParameters.inputData.getString(KEY_PARAM_UPLOAD_PATH)
        val paramLastModified = workerParameters.inputData.getString(KEY_PARAM_LAST_MODIFIED)
        val paramBehavior = workerParameters.inputData.getString(KEY_PARAM_BEHAVIOR)
        val paramContentUri = workerParameters.inputData.getString(KEY_PARAM_CONTENT_URI)
        val paramUploadId = workerParameters.inputData.getLong(KEY_PARAM_UPLOAD_ID, -1)

        account = AccountUtils.getOpenCloudAccountByName(appContext, paramAccountName) ?: return false
        contentUri = paramContentUri?.toUri() ?: return false
        uploadPath = paramUploadPath ?: return false
        behavior = paramBehavior?.let { UploadBehavior.fromString(it) } ?: return false
        lastModified = paramLastModified ?: return false
        uploadIdInStorageManager = paramUploadId
        ocTransfer = retrieveUploadInfoFromDatabase() ?: return false

        return true
    }

    private fun retrieveUploadInfoFromDatabase(): OCTransfer? =
        transferRepository.getTransferById(uploadIdInStorageManager).also {
            if (it != null) {
                Timber.d("Upload with id ($uploadIdInStorageManager) has been found in database.")
                Timber.d("Upload info: $it")
            } else {
                Timber.w("Upload with id ($uploadIdInStorageManager) has not been found in database.")
                Timber.w("$uploadPath won't be uploaded")
            }
        }

    private fun checkDocumentFileExists() {
        val documentFile = DocumentFile.fromSingleUri(appContext, contentUri)
        if (documentFile?.exists() != true && documentFile?.isFile != true) {
            // File does not exists anymore. Throw an exception to tell the user
            throw LocalFileNotFoundException()
        }
    }

    private fun checkPermissionsToReadDocumentAreGranted() {
        val documentFile = DocumentFile.fromSingleUri(appContext, contentUri)
        if (documentFile?.canRead() != true) {
            // Permissions not granted. Throw an exception to ask for them.
            throw LocalFileNotFoundException()
        }
    }

    private fun copyFileToLocalStorage() {
        val cacheFile = File(cachePath)
        val cacheDir = cacheFile.parentFile
        if (cacheDir != null && !cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        cacheFile.createNewFile()

        val inputStream = appContext.contentResolver.openInputStream(contentUri)
        val outputStream = FileOutputStream(cachePath)
        outputStream.use { fileOut ->
            inputStream?.copyTo(fileOut)
        }
        inputStream?.close()
        outputStream.close()

        transferRepository.updateTransferSourcePath(uploadIdInStorageManager, contentUri.toString())
        transferRepository.updateTransferLocalPath(uploadIdInStorageManager, cachePath)
    }

    private fun getClientForThisUpload(): OpenCloudClient =
        SingleSessionManager.getDefaultSingleton()
            .getClientFor(
                OpenCloudAccount(AccountUtils.getOpenCloudAccountByName(appContext, account.name), appContext),
                appContext,
            )

    private fun checkParentFolderExistence(client: OpenCloudClient) {
        var pathToGrant: String = File(uploadPath).parent ?: ""
        pathToGrant = if (pathToGrant.endsWith(File.separator)) pathToGrant else pathToGrant + File.separator

        val checkPathExistenceOperation =
            CheckPathExistenceRemoteOperation(
                remotePath = pathToGrant,
                isUserLoggedIn = AccountUtils.getCurrentOpenCloudAccount(appContext) != null,
                spaceWebDavUrl = spaceWebDavUrl,
            )
        val checkPathExistenceResult = checkPathExistenceOperation.execute(client)
        if (checkPathExistenceResult.code == ResultCode.FILE_NOT_FOUND) {
            val createRemoteFolderOperation = CreateRemoteFolderOperation(
                remotePath = pathToGrant,
                createFullPath = true,
                spaceWebDavUrl = spaceWebDavUrl,
            )
            createRemoteFolderOperation.execute(client)
        }
    }

    private fun checkNameCollisionAndGetAnAvailableOneInCase(client: OpenCloudClient) {
        Timber.d("Checking name collision in server")
        val remotePath = getAvailableRemotePath(
            openCloudClient = client,
            remotePath = uploadPath,
            spaceWebDavUrl = spaceWebDavUrl,
            isUserLogged = AccountUtils.getCurrentOpenCloudAccount(appContext) != null,
        )
        if (remotePath != uploadPath) {
            uploadPath = remotePath
            Timber.d("Name collision detected, let's rename it to %s", remotePath)
        }
    }

    private fun uploadDocument(client: OpenCloudClient) {
        val cacheFile = File(cachePath)
        mimeType = cacheFile.extension
        fileSize = cacheFile.length()

        val capabilitiesForAccount = getStoredCapabilitiesUseCase(
            GetStoredCapabilitiesUseCase.Params(
                accountName = account.name
            )
        )
        val isChunkingAllowed = capabilitiesForAccount != null && capabilitiesForAccount.isChunkingAllowed()
        Timber.d("Chunking is allowed: %s, and file size is greater than the minimum chunk size: %s", isChunkingAllowed, fileSize > CHUNK_SIZE)

        // Prefer TUS for large files: optimistically try TUS and fall back on failure
        val usedTus = if (fileSize > CHUNK_SIZE) {
            Timber.d("Attempting TUS for large upload (size=%d, threshold=%d)", fileSize, CHUNK_SIZE)
            val ok = try {
                uploadTusFile(client)
                true
            } catch (e: Exception) {
                Timber.w(e, "TUS flow failed, will fallback to existing upload methods")
                false
            }
            Timber.d("TUS attempt result: %s", if (ok) "success" else "failed")
            ok
        } else {
            Timber.d("Skipping TUS: file too small (size=%d <= threshold=%d)", fileSize, CHUNK_SIZE)
            false
        }

        if (!usedTus) {
            if (isChunkingAllowed && fileSize > CHUNK_SIZE) {
                uploadChunkedFile(client)
            } else {
                uploadPlainFile(client)
            }
        }
        removeCacheFile()
    }

    private fun uploadTusFile(client: OpenCloudClient) {
        Timber.i("Starting TUS upload for %s (size=%d)", uploadPath, fileSize)

        // 1) Create or resume session
        var tusUrl = ocTransfer.tusUploadUrl
        if (tusUrl.isNullOrBlank()) {
            val fileName = File(uploadPath).name
            val sha256 = try { computeSha256Hex(cachePath) } catch (e: Exception) { Timber.w(e, "SHA-256 computation failed"); "" }
            val metadata = linkedMapOf(
                "filename" to fileName,
                "mimetype" to mimeType
            )
            if (sha256.isNotEmpty()) {
                metadata["checksum"] = "sha256 $sha256"
            }
            // Without explicit capability info, avoid creation-with-upload to maximize compatibility
            val firstChunk = 0L
            val useCreationWithUpload = false

            val create = CreateTusUploadRemoteOperation(
                file = File(cachePath),
                remotePath = uploadPath,
                mimetype = mimeType,
                metadata = metadata,
                useCreationWithUpload = useCreationWithUpload,
                firstChunkSize = null,
                tusUrl = ""
            )
            val createResult = create.execute(client)
            if (!createResult.isSuccess || createResult.data.isNullOrBlank()) {
                throw IllegalStateException("Failed to create TUS upload resource")
            }
            tusUrl = createResult.data
            transferRepository.updateTusState(
                id = uploadIdInStorageManager,
                tusUploadUrl = tusUrl,
                tusUploadOffset = 0L,
                tusUploadLength = fileSize,
                tusUploadMetadata = "filename=$fileName${if (sha256.isNotEmpty()) ";checksum=sha256 $sha256" else ""}",
                tusUploadChecksum = if (sha256.isNotEmpty()) "sha256:$sha256" else null,
                tusResumableVersion = "1.0.0",
                tusUploadExpires = null,
                tusUploadConcat = null,
            )
        }

        // 2) Query current offset
        var offset = 0L
        GetTusUploadOffsetRemoteOperation(tusUrl!!).execute(client).also { res ->
            if (res.isSuccess && (res.data ?: -1L) >= 0) offset = res.data!!
        }
        Timber.d("TUS resume offset: %d / %d", offset, fileSize)

        // Use fixed chunk size if server max is unknown
        val serverMaxChunk: Long? = null
        Timber.d("TUS using fixed chunk size: %d", CHUNK_SIZE)

        // 3) PATCH loop
        while (offset < fileSize) {
            val remaining = fileSize - offset
            val limitByServer = serverMaxChunk ?: Long.MAX_VALUE
            val toSend = minOf(CHUNK_SIZE, remaining, limitByServer)
            Timber.d("TUS using chunk=%d remaining=%d", toSend, remaining)
            val patch = PatchTusUploadChunkRemoteOperation(
                localPath = cachePath,
                uploadUrl = tusUrl,
                offset = offset,
                chunkSize = toSend
            ).apply {
                addDataTransferProgressListener(this@UploadFileFromContentUriWorker)
            }
            val result = patch.execute(client)
            if (result.isSuccess && (result.data ?: -1L) >= 0) {
                offset = result.data!!
                transferRepository.updateTusOffset(uploadIdInStorageManager, offset)
                // Also push overall progress explicitly
                val percent: Int = (100.0 * offset.toDouble() / fileSize.toDouble()).toInt()
                CoroutineScope(Dispatchers.IO).launch {
                    val progress = workDataOf(DownloadFileWorker.WORKER_KEY_PROGRESS to percent)
                    setProgress(progress)
                }
            } else {
                // Try recover on typical TUS conflicts by re-querying offset; else fail to fallback
                val head = GetTusUploadOffsetRemoteOperation(tusUrl).execute(client)
                val recovered = head.isSuccess && (head.data ?: -1L) >= 0
                if (recovered && head.data!! > offset) {
                    offset = head.data!!
                    transferRepository.updateTusOffset(uploadIdInStorageManager, offset)
                    continue
                }
                throw IllegalStateException("TUS PATCH failed and could not recover")
            }
        }
        Timber.i("TUS upload finished for %s", uploadPath)
    }

    private fun uploadPlainFile(client: OpenCloudClient) {
        uploadFileOperation = UploadFileFromFileSystemOperation(
            localPath = cachePath,
            remotePath = uploadPath,
            mimeType = mimeType,
            lastModifiedTimestamp = lastModified,
            requiredEtag = null,
            spaceWebDavUrl = spaceWebDavUrl,
        ).apply {
            addDataTransferProgressListener(this@UploadFileFromContentUriWorker)
        }

        executeRemoteOperation { uploadFileOperation.execute(client) }
    }

    private fun uploadChunkedFile(client: OpenCloudClient) {
        val immutableHashForChunkedFile = SecurityUtils.stringToMD5Hash(uploadPath) + System.currentTimeMillis()
        // Step 1: Create folder where the chunks will be uploaded.
        val createChunksRemoteFolderOperation = CreateRemoteFolderOperation(
            remotePath = immutableHashForChunkedFile,
            createFullPath = false,
            isChunksFolder = true
        )
        executeRemoteOperation { createChunksRemoteFolderOperation.execute(client) }

        // Step 2: Upload file by chunks
        uploadFileOperation = ChunkedUploadFromFileSystemOperation(
            transferId = immutableHashForChunkedFile,
            localPath = cachePath,
            remotePath = uploadPath,
            mimeType = mimeType,
            lastModifiedTimestamp = lastModified,
            requiredEtag = null,
        ).apply {
            addDataTransferProgressListener(this@UploadFileFromContentUriWorker)
        }

        executeRemoteOperation { uploadFileOperation.execute(client) }

        // Step 3: Move remote file to the final remote destination
        val ocChunkService = OCChunkService(client)
        ocChunkService.moveFile(
            sourceRemotePath = "${immutableHashForChunkedFile}${OCFile.PATH_SEPARATOR}${FileUtils.FINAL_CHUNKS_FILE}",
            targetRemotePath = uploadPath,
            fileLastModificationTimestamp = lastModified,
            fileLength = fileSize
        )
    }

    private fun removeCacheFile() {
        val cacheFile = File(cachePath)
        cacheFile.delete()
    }

    private fun computeSha256Hex(path: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(path).use { fis ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val read = fis.read(buf)
                if (read <= 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun updateUploadsDatabaseWithResult(throwable: Throwable?) {
        transferRepository.updateTransferWhenFinished(
            id = uploadIdInStorageManager,
            status = getUploadStatusForThrowable(throwable),
            transferEndTimestamp = System.currentTimeMillis(),
            lastResult = TransferResult.fromThrowable(throwable)
        )
    }

    private fun getUploadStatusForThrowable(throwable: Throwable?): TransferStatus =
        if (throwable == null) {
            TransferStatus.TRANSFER_SUCCEEDED
        } else {
            TransferStatus.TRANSFER_FAILED
        }

    private fun showNotification(throwable: Throwable) {
        // check credentials error
        val needsToUpdateCredentials = throwable is UnauthorizedException

        val tickerId =
            if (needsToUpdateCredentials) R.string.uploader_upload_failed_credentials_error else R.string.uploader_upload_failed_ticker

        val pendingIntent = if (needsToUpdateCredentials) {
            NotificationUtils.composePendingIntentToRefreshCredentials(appContext, account)
        } else {
            NotificationUtils.composePendingIntentToUploadList(appContext)
        }

        NotificationUtils.createBasicNotification(
            context = appContext,
            contentTitle = appContext.getString(tickerId),
            contentText = throwable.parseError("", appContext.resources, true).toString(),
            notificationChannelId = UPLOAD_NOTIFICATION_CHANNEL_ID,
            notificationId = 12,
            intent = pendingIntent,
            onGoing = false,
            timeOut = null
        )
    }

    override fun onTransferProgress(
        progressRate: Long,
        totalTransferredSoFar: Long,
        totalToTransfer: Long,
        filePath: String
    ) {
        val percent: Int = (100.0 * totalTransferredSoFar.toDouble() / totalToTransfer.toDouble()).toInt()
        if (percent == lastPercent) return

        // Set current progress. Observers will listen.
        CoroutineScope(Dispatchers.IO).launch {
            val progress = workDataOf(DownloadFileWorker.WORKER_KEY_PROGRESS to percent)
            setProgress(progress)
        }

        lastPercent = percent
    }

    companion object {
        const val KEY_PARAM_ACCOUNT_NAME = "KEY_PARAM_ACCOUNT_NAME"
        const val KEY_PARAM_BEHAVIOR = "KEY_PARAM_BEHAVIOR"
        const val KEY_PARAM_CONTENT_URI = "KEY_PARAM_CONTENT_URI"
        const val KEY_PARAM_LAST_MODIFIED = "KEY_PARAM_LAST_MODIFIED"
        const val KEY_PARAM_UPLOAD_PATH = "KEY_PARAM_UPLOAD_PATH"
        const val KEY_PARAM_UPLOAD_ID = "KEY_PARAM_UPLOAD_ID"
    }
}
