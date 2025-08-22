/**
 * openCloud Android client application
 */
package eu.opencloud.android.workers

import android.accounts.Account
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.opencloud.android.R
import eu.opencloud.android.domain.exceptions.LocalFileNotFoundException
import eu.opencloud.android.domain.exceptions.UnauthorizedException
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.domain.transfers.model.TransferResult
import eu.opencloud.android.domain.transfers.model.TransferStatus
import eu.opencloud.android.extensions.isContentUri
import eu.opencloud.android.extensions.parseError
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.SingleSessionManager
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.providers.UploadManager
import eu.opencloud.android.utils.NotificationUtils
import eu.opencloud.android.lib.resources.files.CheckPathExistenceRemoteOperation
import eu.opencloud.android.lib.resources.files.CreateRemoteFolderOperation
import eu.opencloud.android.utils.RemoteFileUtils.getAvailableRemotePath
import eu.opencloud.android.utils.UPLOAD_NOTIFICATION_CHANNEL_ID
import io.tus.android.client.TusAndroidUpload
import io.tus.java.client.ProtocolException
import io.tus.java.client.TusUploader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File

class TusUploadWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent {

    private val transferRepository: TransferRepository by inject()
    private val uploadManager: UploadManager by inject()
    private val getWebdavUrlForSpaceUseCase: GetWebDavUrlForSpaceUseCase by inject()

    private lateinit var account: Account
    private lateinit var uploadPath: String
    private var uploadIdInStorageManager: Long = -1
    private lateinit var ocTransfer: OCTransfer

    private var lastPercent = 0
    private var spaceWebDavUrl: String? = null

    override suspend fun doWork(): Result {
        if (!areParametersValid()) return Result.failure()

        transferRepository.updateTransferStatusToInProgressById(uploadIdInStorageManager)

        return try {
            val client = getClientForThisUpload()
            val tusClient = uploadManager.createTusClient(client)

            // Prepare space WebDAV URL and perform pre-upload checks similar to other workers
            spaceWebDavUrl = getWebdavUrlForSpaceUseCase(
                GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = ocTransfer.spaceId)
            )
            checkParentFolderExistence(client)
            checkNameCollisionAndGetAnAvailableOneInCase(client)

            val sourceUri = getSourceUriOrThrow()
            val upload = TusAndroidUpload(sourceUri, appContext)

            // Set metadata for server-side placement (directory and filename)
            val remoteFile = File(uploadPath)
            val parentDir = (remoteFile.parent ?: "/").let { if (it.endsWith(File.separator)) it else it + File.separator }
            // TusAndroidUpload constructor sets filename, we override to ensure both keys exist
            upload.setMetadata(mapOf(
                "filename" to remoteFile.name,
                "dir" to parentDir,
            ))

            val uploader: TusUploader = tusClient.resumeOrCreateUpload(upload)
            // 5 MB chunks
            uploader.chunkSize = 5 * 1024 * 1024

            do {
                if (isStopped) {
                    // Stop uploading; the URL store allows resuming later
                    uploader.finish()
                    return Result.failure()
                }

                val totalBytes = upload.size
                val bytesUploaded = uploader.offset
                val percent = if (totalBytes > 0) (100.0 * bytesUploaded.toDouble() / totalBytes.toDouble()).toInt() else -1
                if (percent != lastPercent) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val progress = workDataOf(DownloadFileWorker.WORKER_KEY_PROGRESS to percent)
                        setProgress(progress)
                    }
                    lastPercent = percent
                }
            } while (uploader.uploadChunk() > -1)

            uploader.finish()

            updateUploadsDatabaseWithResult(null)
            Result.success()
        } catch (t: Throwable) {
            val normalized = mapTusException(t)
            Timber.e(normalized)
            showNotification(normalized)
            updateUploadsDatabaseWithResult(normalized)
            Result.failure()
        }
    }

    private fun areParametersValid(): Boolean {
        val paramAccountName = workerParameters.inputData.getString(KEY_PARAM_ACCOUNT_NAME)
        val paramUploadPath = workerParameters.inputData.getString(KEY_PARAM_UPLOAD_PATH)
        val paramContentUri = workerParameters.inputData.getString(KEY_PARAM_CONTENT_URI)
        val paramLocalPath = workerParameters.inputData.getString(KEY_PARAM_LOCAL_PATH)
        val paramUploadId = workerParameters.inputData.getLong(KEY_PARAM_UPLOAD_ID, -1)

        account = AccountUtils.getOpenCloudAccountByName(appContext, paramAccountName) ?: return false
        uploadPath = paramUploadPath ?: return false
        uploadIdInStorageManager = paramUploadId.takeUnless { it == -1L } ?: return false
        ocTransfer = retrieveUploadInfoFromDatabase() ?: return false

        // Ensure one source provided
        if (paramContentUri.isNullOrBlank() && paramLocalPath.isNullOrBlank()) return false

        return true
    }

    private fun retrieveUploadInfoFromDatabase(): OCTransfer? =
        transferRepository.getTransferById(uploadIdInStorageManager).also {
            if (it != null) {
                Timber.d("Upload with id ($uploadIdInStorageManager) found in DB: $it")
            } else {
                Timber.w("Upload with id ($uploadIdInStorageManager) not found in DB. $uploadPath won't be uploaded")
            }
        }

    private fun getSourceUriOrThrow(): Uri {
        return if (ocTransfer.isContentUri(appContext)) {
            val uri = Uri.parse(ocTransfer.sourcePath)
            // Basic readability check via DocumentFile would require extra perms; rely on resolver here
            if (uri == null) throw LocalFileNotFoundException()
            uri
        } else {
            val path = ocTransfer.localPath
            val file = File(path)
            if (!file.exists() || !file.isFile || !file.canRead()) throw LocalFileNotFoundException()
            Uri.fromFile(file)
        }
    }

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
        if (checkPathExistenceResult.code == eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode.FILE_NOT_FOUND) {
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

    private fun getClientForThisUpload(): OpenCloudClient =
        SingleSessionManager.getDefaultSingleton()
            .getClientFor(
                OpenCloudAccount(AccountUtils.getOpenCloudAccountByName(appContext, account.name), appContext),
                appContext,
            )

    private fun updateUploadsDatabaseWithResult(throwable: Throwable?) {
        transferRepository.updateTransferWhenFinished(
            id = uploadIdInStorageManager,
            status = if (throwable == null) TransferStatus.TRANSFER_SUCCEEDED else TransferStatus.TRANSFER_FAILED,
            transferEndTimestamp = System.currentTimeMillis(),
            lastResult = TransferResult.fromThrowable(throwable)
        )
    }

    private fun showNotification(throwable: Throwable) {
        val needsToUpdateCredentials = throwable is UnauthorizedException

        val tickerId = if (needsToUpdateCredentials) {
            R.string.uploader_upload_failed_credentials_error
        } else {
            R.string.uploader_upload_failed_ticker
        }

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

    private fun mapTusException(t: Throwable): Throwable {
        return if (t is ProtocolException) {
            val code = try {
                t.causingConnection?.responseCode
            } catch (e: Exception) {
                null
            }
            if (code == 401) {
                UnauthorizedException()
            } else {
                t
            }
        } else {
            t
        }
    }

    companion object {
        const val KEY_PARAM_ACCOUNT_NAME = "KEY_PARAM_ACCOUNT_NAME"
        const val KEY_PARAM_UPLOAD_PATH = "KEY_PARAM_UPLOAD_PATH"
        const val KEY_PARAM_CONTENT_URI = "KEY_PARAM_CONTENT_URI"
        const val KEY_PARAM_LOCAL_PATH = "KEY_PARAM_LOCAL_PATH"
        const val KEY_PARAM_UPLOAD_ID = "KEY_PARAM_UPLOAD_ID"
    }
}
