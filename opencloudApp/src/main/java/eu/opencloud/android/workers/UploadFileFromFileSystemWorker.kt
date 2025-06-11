/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author Jorge Aguado Recio
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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.opencloud.android.R
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.exceptions.LocalFileNotFoundException
import eu.opencloud.android.domain.exceptions.UnauthorizedException
import eu.opencloud.android.domain.files.model.OCFile.Companion.PATH_SEPARATOR
import eu.opencloud.android.domain.files.usecases.CleanConflictUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase
import eu.opencloud.android.domain.files.usecases.SaveFileOrFolderUseCase
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.domain.transfers.model.TransferResult
import eu.opencloud.android.domain.transfers.model.TransferStatus
import eu.opencloud.android.extensions.parseError
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

class UploadFileFromFileSystemWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent, OnDatatransferProgressListener {

    private lateinit var account: Account
    private lateinit var fileSystemPath: String
    private lateinit var lastModified: String
    private lateinit var behavior: UploadBehavior
    private lateinit var uploadPath: String
    private lateinit var mimetype: String
    private var removeLocal: Boolean = true
    private var uploadIdInStorageManager: Long = -1
    private lateinit var ocTransfer: OCTransfer
    private var fileSize: Long = 0
    private var spaceWebDavUrl: String? = null

    private lateinit var uploadFileOperation: UploadFileFromFileSystemOperation
    private val saveFileOrFolderUseCase: SaveFileOrFolderUseCase by inject()
    private val cleanConflictUseCase: CleanConflictUseCase by inject()
    private val getWebdavUrlForSpaceUseCase: GetWebDavUrlForSpaceUseCase by inject()

    // Etag in conflict required to overwrite files in server. Otherwise, the upload will be rejected.
    private var eTagInConflict: String = ""

    private var lastPercent = 0

    private val transferRepository: TransferRepository by inject()

    override suspend fun doWork(): Result {

        if (!areParametersValid()) return Result.failure()

        transferRepository.updateTransferStatusToInProgressById(uploadIdInStorageManager)

        spaceWebDavUrl =
            getWebdavUrlForSpaceUseCase(GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = ocTransfer.spaceId))

        return try {
            checkPermissionsToReadDocumentAreGranted()
            val clientForThisUpload = getClientForThisUpload()
            checkParentFolderExistence(clientForThisUpload)
            checkNameCollisionAndGetAnAvailableOneInCase(clientForThisUpload)
            uploadDocument(clientForThisUpload)
            updateUploadsDatabaseWithResult(null)
            updateFilesDatabaseWithLatestDetails()
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
        val paramFileSystemUri = workerParameters.inputData.getString(KEY_PARAM_LOCAL_PATH)
        val paramUploadId = workerParameters.inputData.getLong(KEY_PARAM_UPLOAD_ID, -1)
        val paramRemoveLocal = workerParameters.inputData.getBoolean(KEY_PARAM_REMOVE_LOCAL, true)

        account = AccountUtils.getOpenCloudAccountByName(appContext, paramAccountName) ?: return false
        fileSystemPath = paramFileSystemUri.takeUnless { it.isNullOrBlank() } ?: return false
        uploadPath = paramUploadPath ?: return false
        behavior = paramBehavior?.let { UploadBehavior.valueOf(it) } ?: return false
        lastModified = paramLastModified ?: return false
        uploadIdInStorageManager = paramUploadId.takeUnless { it == -1L } ?: return false
        ocTransfer = retrieveUploadInfoFromDatabase() ?: return false
        removeLocal = paramRemoveLocal

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

    private fun checkPermissionsToReadDocumentAreGranted() {
        val fileInFileSystem = File(fileSystemPath)
        if (!fileInFileSystem.exists() || !fileInFileSystem.isFile || !fileInFileSystem.canRead()) {
            // Permissions not granted. Throw an exception to ask for them.
            throw LocalFileNotFoundException()
        }
        mimetype = fileInFileSystem.extension
        fileSize = fileInFileSystem.length()
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

        val checkPathExistenceOperation = CheckPathExistenceRemoteOperation(pathToGrant, false, spaceWebDavUrl)
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
        if (ocTransfer.forceOverwrite) {

            val getFileByRemotePathUseCase: GetFileByRemotePathUseCase by inject()
            val useCaseResult = getFileByRemotePathUseCase(
                GetFileByRemotePathUseCase.Params(
                    ocTransfer.accountName,
                    ocTransfer.remotePath,
                    ocTransfer.spaceId
                )
            )

            eTagInConflict = useCaseResult.getDataOrNull()?.etagInConflict.orEmpty()

            Timber.d("Upload will overwrite current server file with the following etag in conflict: $eTagInConflict")
        } else {

            Timber.d("Checking name collision in server")
            val remotePath = getAvailableRemotePath(
                openCloudClient = client,
                remotePath = uploadPath,
                spaceWebDavUrl = spaceWebDavUrl,
                isUserLogged = AccountUtils.getCurrentOpenCloudAccount(appContext) != null,
            )
            if (remotePath != uploadPath) {
                uploadPath = remotePath
                Timber.d("Name collision detected, let's rename it to $remotePath")
            }
        }
    }

    private fun uploadDocument(client: OpenCloudClient) {
        val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase by inject()
        val capabilitiesForAccount = getStoredCapabilitiesUseCase(
            GetStoredCapabilitiesUseCase.Params(
                accountName = account.name
            )
        )
        val isChunkingAllowed = capabilitiesForAccount != null && capabilitiesForAccount.isChunkingAllowed()
        Timber.d("Chunking is allowed: %s, and file size is greater than the minimum chunk size: %s", isChunkingAllowed, fileSize > CHUNK_SIZE)

        if (isChunkingAllowed && fileSize > CHUNK_SIZE) {
            uploadChunkedFile(client)
        } else {
            uploadPlainFile(client)
        }
    }

    private fun uploadPlainFile(client: OpenCloudClient) {
        uploadFileOperation = UploadFileFromFileSystemOperation(
            localPath = fileSystemPath,
            remotePath = uploadPath,
            mimeType = mimetype,
            lastModifiedTimestamp = lastModified,
            requiredEtag = eTagInConflict,
            spaceWebDavUrl = spaceWebDavUrl,
        ).apply {
            addDataTransferProgressListener(this@UploadFileFromFileSystemWorker)
        }

        val result = executeRemoteOperation { uploadFileOperation.execute(client) }

        if (result == Unit && removeLocal) {
            removeLocalFile() // Removed file from tmp folder
        }
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
            localPath = fileSystemPath,
            remotePath = uploadPath,
            mimeType = mimetype,
            lastModifiedTimestamp = lastModified,
            requiredEtag = eTagInConflict,
        ).apply {
            addDataTransferProgressListener(this@UploadFileFromFileSystemWorker)
        }

        val result = executeRemoteOperation { uploadFileOperation.execute(client) }

        // Step 3: Move remote file to the final remote destination
        val ocChunkService = OCChunkService(client)
        ocChunkService.moveFile(
            sourceRemotePath = "$immutableHashForChunkedFile$PATH_SEPARATOR${FileUtils.FINAL_CHUNKS_FILE}",
            targetRemotePath = uploadPath,
            fileLastModificationTimestamp = lastModified,
            fileLength = fileSize
        )

        // Step 4: Remove tmp file folder after uploading
        if (result == Unit && removeLocal) {
            removeLocalFile()
        }
    }

    private fun removeLocalFile() {
        val fileDeleted = File(fileSystemPath).delete()
        Timber.d("File with path: $fileSystemPath has been removed: $fileDeleted after uploading.")
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

    /**
     * Update the database with latest details about this file.
     *
     * We will ask for thumbnails after the upload
     * We will update info about the file (modification timestamp and etag)
     */
    private fun updateFilesDatabaseWithLatestDetails() {
        val currentTime = System.currentTimeMillis()
        val getFileByRemotePathUseCase: GetFileByRemotePathUseCase by inject()
        val file = getFileByRemotePathUseCase(GetFileByRemotePathUseCase.Params(account.name, ocTransfer.remotePath, ocTransfer.spaceId))
        file.getDataOrNull()?.let { ocFile ->
            val fileWithNewDetails =
                if (ocTransfer.forceOverwrite) {
                    ocFile.copy(
                        needsToUpdateThumbnail = true,
                        etag = uploadFileOperation.etag,
                        length = (File(ocTransfer.localPath).length()),
                        lastSyncDateForData = currentTime,
                        modifiedAtLastSyncForData = currentTime,
                    )
                } else {
                    // Uploading a file should remove any conflicts on the file.
                    ocFile.copy(
                        storagePath = null,
                    )
                }
            saveFileOrFolderUseCase(SaveFileOrFolderUseCase.Params(fileWithNewDetails))
            cleanConflictUseCase(
                CleanConflictUseCase.Params(
                    fileId = ocFile.id!!
                )
            )
        }
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
        const val KEY_PARAM_LOCAL_PATH = "KEY_PARAM_LOCAL_PATH"
        const val KEY_PARAM_LAST_MODIFIED = "KEY_PARAM_LAST_MODIFIED"
        const val KEY_PARAM_UPLOAD_PATH = "KEY_PARAM_UPLOAD_PATH"
        const val KEY_PARAM_UPLOAD_ID = "KEY_PARAM_UPLOAD_ID"
        const val KEY_PARAM_REMOVE_LOCAL = "KEY_REMOVE_LOCAL"
    }
}
