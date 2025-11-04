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
import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.opencloud.android.R
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.exceptions.LocalFileNotFoundException
import eu.opencloud.android.domain.exceptions.UnauthorizedException
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
import eu.opencloud.android.lib.resources.files.UploadFileFromFileSystemOperation
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.utils.NotificationUtils
import eu.opencloud.android.utils.RemoteFileUtils.getAvailableRemotePath
import eu.opencloud.android.utils.UPLOAD_NOTIFICATION_CHANNEL_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class UploadFileFromFileSystemWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent, OnDatatransferProgressListener {

    private lateinit var account: Account
    private lateinit var fileSystemPath: String
    private var lastModified: String = ""
    private lateinit var behavior: UploadBehavior
    private lateinit var uploadPath: String
    private lateinit var mimetype: String
    private var removeLocal: Boolean = false
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

    private var lastPercent = -1

    private val transferRepository: TransferRepository by inject()
    private val tusUploadHelper by lazy { TusUploadHelper(transferRepository) }

    private val foregroundJob = SupervisorJob()
    private val foregroundScope = CoroutineScope(Dispatchers.Default + foregroundJob)
    @Volatile
    private var currentForegroundProgress = Int.MIN_VALUE
    private var foregroundInitialized = false

    override suspend fun doWork(): Result {

        if (!areParametersValid()) return Result.failure()

        startForeground()

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

            if (shouldRetry(throwable)) {
                Timber.i("Retrying upload %d after transient failure", uploadIdInStorageManager)
                return Result.retry()
            }

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
        val paramRemoveLocal = workerParameters.inputData.getBoolean(KEY_PARAM_REMOVE_LOCAL, false)

        account = AccountUtils.getOpenCloudAccountByName(appContext, paramAccountName) ?: return false
        fileSystemPath = paramFileSystemUri.takeUnless { it.isNullOrBlank() } ?: return false
        uploadPath = paramUploadPath ?: return false
        behavior = paramBehavior?.let { UploadBehavior.valueOf(it) } ?: return false
        lastModified = paramLastModified.orEmpty()
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
        ensureValidLastModified(fileInFileSystem)
    }

    private fun ensureValidLastModified(sourceFile: File) {
        val current = lastModified.toLongOrNull()
        if (current != null && current > 0) {
            return
        }

        val fallbackMillis = sourceFile.lastModified().takeIf { it > 0 }
            ?: System.currentTimeMillis()
        lastModified = (fallbackMillis / 1000L).toString()
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
        val tusSupport = capabilitiesForAccount?.filesTusSupport
        val supportsTus = tusSupport != null

        val hasPendingTusSession = !ocTransfer.tusUploadUrl.isNullOrBlank()
        val shouldTryTus = hasPendingTusSession || (supportsTus && fileSize >= TusUploadHelper.DEFAULT_CHUNK_SIZE)

        var attemptedTus = false
        if (shouldTryTus) {
            attemptedTus = true
            Timber.d(
                "Attempting TUS upload (size=%d, threshold=%d, resume=%s)",
                fileSize,
                TusUploadHelper.DEFAULT_CHUNK_SIZE,
                hasPendingTusSession
            )
            val tusSucceeded = try {
                tusUploadHelper.upload(
                    client = client,
                    transfer = ocTransfer,
                    uploadId = uploadIdInStorageManager,
                    localPath = fileSystemPath,
                    remotePath = uploadPath,
                    fileSize = fileSize,
                    mimeType = mimetype,
                    lastModified = lastModified,
                    tusSupport = tusSupport,
                    progressListener = this,
                    progressCallback = ::updateProgressFromTus,
                    spaceWebDavUrl = spaceWebDavUrl,
                )
                true
            } catch (throwable: Throwable) {
                Timber.w(throwable, "TUS upload failed, falling back to single PUT")
                if (shouldRetry(throwable)) {
                    throw throwable
                }
                false
            }

            if (tusSucceeded) {
                if (removeLocal) {
                    removeLocalFile()
                }
                Timber.d("TUS upload completed for %s", uploadPath)
                return
            }
        } else {
            Timber.d(
                "Skipping TUS: file too small or unsupported (size=%d, threshold=%d, supportsTus=%s)",
                fileSize,
                TusUploadHelper.DEFAULT_CHUNK_SIZE,
                supportsTus
            )
        }

        Timber.d("Falling back to single PUT upload for %s", uploadPath)
        uploadPlainFile(client)
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

        if (result == Unit) {
            clearTusState()
            if (removeLocal) {
                removeLocalFile() // Removed file from tmp folder
            }
        }
    }

    private fun updateProgressFromTus(offset: Long, totalSize: Long) {
        if (totalSize <= 0) return
        val percent: Int = (100.0 * offset.toDouble() / totalSize.toDouble()).toInt()
        if (percent == lastPercent) return

        CoroutineScope(Dispatchers.IO).launch {
            val progress = workDataOf(DownloadFileWorker.WORKER_KEY_PROGRESS to percent)
            setProgress(progress)
        }
        scheduleForegroundUpdate(percent)
        lastPercent = percent
    }

    private fun removeLocalFile() {
        val fileDeleted = File(fileSystemPath).delete()
        Timber.d("File with path: $fileSystemPath has been removed: $fileDeleted after uploading.")
    }

    private fun clearTusState() {
        transferRepository.updateTusState(
            id = uploadIdInStorageManager,
            tusUploadUrl = null,
            tusUploadLength = null,
            tusUploadMetadata = null,
            tusUploadChecksum = null,
            tusResumableVersion = null,
            tusUploadExpires = null,
            tusUploadConcat = null,
        )
    }

    private fun shouldRetry(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        if (throwable is LocalFileNotFoundException) return false
        if (throwable is CancellationException) return true
        if (throwable is IOException) return true
        return shouldRetry(throwable.cause)
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
                        length = fileSize,
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

        scheduleForegroundUpdate(percent)
        lastPercent = percent
    }

    private suspend fun startForeground() {
        if (foregroundInitialized) return
        foregroundInitialized = true
        currentForegroundProgress = Int.MIN_VALUE
        try {
            setForeground(createForegroundInfo(-1))
        } catch (e: Exception) {
            Timber.w(e, "Failed to set foreground for upload worker")
        }
        currentForegroundProgress = -1
    }

    private fun scheduleForegroundUpdate(progress: Int) {
        if (!foregroundInitialized) return
        if (progress == currentForegroundProgress) return
        currentForegroundProgress = progress
        foregroundScope.launch {
            try {
                setForeground(createForegroundInfo(progress))
            } catch (e: Exception) {
                Timber.w(e, "Failed to update foreground notification")
            }
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo =
        ForegroundInfo(
            computeNotificationId(),
            buildForegroundNotification(progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

    private fun buildForegroundNotification(progress: Int): Notification {
        val fileName = File(uploadPath).name
        val builder = NotificationUtils
            .newNotificationBuilder(appContext, UPLOAD_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.uploader_upload_in_progress_ticker))
            .setContentIntent(NotificationUtils.composePendingIntentToUploadList(appContext))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSubText(fileName)

        if (progress in 0..100) {
            builder.setContentText(
                appContext.getString(
                    R.string.uploader_upload_in_progress_content,
                    progress,
                    fileName
                )
            )
            builder.setProgress(100, progress, false)
        } else {
            builder.setContentText(appContext.getString(R.string.uploader_upload_in_progress_ticker))
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun computeNotificationId(): Int {
        val id = uploadIdInStorageManager
        return if (id in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            id.toInt()
        } else {
            id.hashCode()
        }
    }

    companion object {
        const val KEY_PARAM_ACCOUNT_NAME: String = "KEY_PARAM_ACCOUNT_NAME"
        const val KEY_PARAM_BEHAVIOR: String = "KEY_PARAM_BEHAVIOR"
        const val KEY_PARAM_LOCAL_PATH: String = "KEY_PARAM_LOCAL_PATH"
        const val KEY_PARAM_LAST_MODIFIED: String = "KEY_PARAM_LAST_MODIFIED"
        const val KEY_PARAM_UPLOAD_PATH: String = "KEY_PARAM_UPLOAD_PATH"
        const val KEY_PARAM_UPLOAD_ID: String = "KEY_PARAM_UPLOAD_ID"
        const val KEY_PARAM_REMOVE_LOCAL: String = "KEY_REMOVE_LOCAL"
    }
}
