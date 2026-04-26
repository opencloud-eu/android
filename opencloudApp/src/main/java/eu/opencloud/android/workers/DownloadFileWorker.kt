/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
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
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import at.bitfire.dav4jvm.exception.UnauthorizedException
import eu.opencloud.android.R
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.data.providers.LocalStorageProvider
import eu.opencloud.android.domain.exceptions.CancelledException
import eu.opencloud.android.domain.exceptions.LocalStorageNotMovedException
import eu.opencloud.android.domain.exceptions.NoConnectionWithServerException
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.usecases.CleanConflictUseCase
import eu.opencloud.android.domain.files.usecases.CleanWorkersUUIDUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByIdUseCase
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase
import eu.opencloud.android.domain.files.usecases.SaveDownloadWorkerUUIDUseCase
import eu.opencloud.android.domain.files.usecases.SaveFileOrFolderUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpaceByIdForAccountUseCase
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.SingleSessionManager
import eu.opencloud.android.lib.common.network.OnDatatransferProgressListener
import eu.opencloud.android.lib.resources.files.DownloadRemoteFileOperation
import eu.opencloud.android.presentation.authentication.ACTION_UPDATE_EXPIRED_TOKEN
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.presentation.authentication.EXTRA_ACCOUNT
import eu.opencloud.android.presentation.authentication.EXTRA_ACTION
import eu.opencloud.android.presentation.authentication.LoginActivity
import eu.opencloud.android.presentation.transfers.TransferOperation.Download
import eu.opencloud.android.ui.errorhandling.ErrorMessageAdapter
import eu.opencloud.android.utils.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import eu.opencloud.android.utils.DOWNLOAD_NOTIFICATION_ID_DEFAULT
import eu.opencloud.android.utils.FileStorageUtils
import eu.opencloud.android.utils.NOTIFICATION_TIMEOUT_STANDARD
import eu.opencloud.android.utils.NotificationUtils
import eu.opencloud.android.utils.NotificationUtils.createBasicNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File

class DownloadFileWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters,
), KoinComponent, OnDatatransferProgressListener {

    private val getFileByIdUseCase: GetFileByIdUseCase by inject()
    private val saveFileOrFolderUseCase: SaveFileOrFolderUseCase by inject()
    private val getWebdavUrlForSpaceUseCase: GetWebDavUrlForSpaceUseCase by inject()
    private val cleanConflictUseCase: CleanConflictUseCase by inject()
    private val saveDownloadWorkerUuidUseCase: SaveDownloadWorkerUUIDUseCase by inject()
    private val cleanWorkersUuidUseCase: CleanWorkersUUIDUseCase by inject()
    private val localStorageProvider: LocalStorageProvider by inject()
    private val getSpaceByIdForAccountUseCase: GetSpaceByIdForAccountUseCase by inject()

    lateinit var account: Account
    lateinit var ocFile: OCFile
    private var spaceName: String? = null

    private lateinit var downloadRemoteFileOperation: DownloadRemoteFileOperation
    private var lastPercent = 0

    private var foregroundInitialized = false
    private var currentForegroundProgress = -1
    private val foregroundScope = CoroutineScope(Dispatchers.Main)

    /**
     * Temporal path for this file to be downloaded.
     */
    private val temporalFilePath
        get() = temporalFolderPath + ocFile.remotePath

    /**
     * Temporal path where every file of this account will be downloaded.
     * Uses the app's external cache directory which is always writable without special permissions
     * AND is on the same filesystem as /storage/emulated/0/ so that File.renameTo() works
     * when moving the downloaded file to its final location under OpenCloud/.
     */
    private val temporalFolderPath
        get(): String {
            val cacheBase = appContext.externalCacheDir ?: appContext.cacheDir
            requireNotNull(cacheBase) { "Both externalCacheDir and cacheDir are null" }
            val baseTmpDir = File(cacheBase, "download_tmp")
            val accountDir = File(baseTmpDir, Uri.encode(account.name, "@"))
            val spaceDir = if (ocFile.spaceId != null) File(accountDir, ocFile.spaceId!!) else accountDir
            return spaceDir.absolutePath
        }

    /**
     * Final path where this file should be stored.
     *
     * In case this file was previously downloaded, override it. Otherwise,
     * @see LocalStorageProvider.getDefaultSavePathFor
     */
    private val finalLocationForFile: String
        get() = ocFile.storagePath.takeUnless { it.isNullOrBlank() }
            ?: localStorageProvider.getDefaultSavePathFor(
                accountName = account.name,
                remotePath = ocFile.remotePath,
                spaceId = ocFile.spaceId,
                spaceName = spaceName
            )

    override suspend fun doWork(): Result {
        if (!areParametersValid()) return Result.failure()

        return try {
            startForeground()
            downloadFileToTemporalFile()
            moveTemporalFileToFinalLocation()
            updateDatabaseWithLatestInfoForThisFile()
            notifyDownloadResult(null)
        } catch (throwable: Throwable) {
            Timber.e(throwable)
            notifyDownloadResult(throwable)
        }
    }

    /**
     * Verify that the parameters are valid.
     *
     * This verification includes (at the moment):
     * - Check whether account exists
     * - Check whether file exists in the database
     * - Check that the file is not a folder
     */
    private fun areParametersValid(): Boolean {
        val accountName = workerParameters.inputData.getString(KEY_PARAM_ACCOUNT)
        val fileId = workerParameters.inputData.getLong(KEY_PARAM_FILE_ID, -1)

        account = AccountUtils.getOpenCloudAccountByName(appContext, accountName) ?: return false
        ocFile = getFileByIdUseCase(GetFileByIdUseCase.Params(fileId)).getDataOrNull() ?: return false

        if (ocFile.spaceId != null) {
            val space = getSpaceByIdForAccountUseCase(GetSpaceByIdForAccountUseCase.Params(account.name, ocFile.spaceId))
            if (space != null) {
                spaceName = space.name
            }
        }

        return !ocFile.isFolder
    }

    /**
     * Download the file or throw an exception if something goes wrong.
     * We will initialize a listener to update the notification according to the download progress.
     *
     * File will be downloaded to a temporalFolder in the RemoteOperation.
     * @see temporalFolderPath for the temporal location
     */
    private fun downloadFileToTemporalFile() {
        saveDownloadWorkerUuidUseCase(
            SaveDownloadWorkerUUIDUseCase.Params(
                fileId = workerParameters.inputData.getLong(KEY_PARAM_FILE_ID, -1),
                workerUuid = id
            )
        )

        val spaceWebDavUrl =
            getWebdavUrlForSpaceUseCase(GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = ocFile.spaceId))

        downloadRemoteFileOperation = DownloadRemoteFileOperation(
            ocFile.remotePath,
            temporalFolderPath,
            spaceWebDavUrl,
        ).apply {
            addDatatransferProgressListener(this@DownloadFileWorker)
        }
        val client = getClientForThisDownload()

        // It will throw an exception if something goes wrong.
        executeRemoteOperation {
            downloadRemoteFileOperation.execute(client)
        }
    }

    /**
     * Move the temporal file to the final location.
     * @see temporalFilePath for the temporal location
     * @see finalLocationForFile for the final one
     */
    private fun moveTemporalFileToFinalLocation() {
        val temporalLocation = File(temporalFilePath)

        if (FileStorageUtils.getUsableSpace() < temporalLocation.length()) {
            Timber.w("Not enough space to copy %s", temporalLocation.absolutePath)
        }

        val finalLocation = File(finalLocationForFile)
        finalLocation.parentFile?.mkdirs()

        if (temporalLocation.renameTo(finalLocation)) {
            return
        }

        Timber.w("renameTo failed from %s to %s, falling back to copy+delete", temporalLocation.absolutePath, finalLocation.absolutePath)
        try {
            temporalLocation.copyTo(finalLocation, overwrite = true)
            if (!temporalLocation.delete()) {
                Timber.w("Failed to delete temporal file after copy: %s", temporalLocation.absolutePath)
            }
        } catch (e: Exception) {
            Timber.e(e, "Copy+delete fallback also failed from %s to %s", temporalLocation.absolutePath, finalLocation.absolutePath)
            throw LocalStorageNotMovedException()
        }
    }

    /**
     * Update the database with latest details about this file.
     *
     * We will ask for thumbnails after a download
     * We will update info about the file (modification timestamp and etag)
     * We will update info about local storage (where it was stored and its size)
     */
    private fun updateDatabaseWithLatestInfoForThisFile() {
        val finalFile = File(finalLocationForFile)
        val currentTime = System.currentTimeMillis()
        ocFile.apply {
            needsToUpdateThumbnail = true
            modificationTimestamp = downloadRemoteFileOperation.modificationTimestamp
            etag = downloadRemoteFileOperation.etag
            remoteEtag = downloadRemoteFileOperation.etag
            storagePath = finalLocationForFile
            length = finalFile.length()
            // Use the file's actual mtime, not the current time. SynchronizeFileUseCase
            // compares lastSyncDateForData against the file's filesystem mtime to detect
            // local modifications. Using currentTime here can be slightly earlier than the
            // file's mtime (due to write time and second-precision rounding), which causes
            // a false "changed locally" detection and an unnecessary upload.
            // This mainly affects SAF (DocumentsStorageProvider) where apps like Google
            // Photos call openDocument repeatedly right after download, triggering the
            // false positive immediately. In normal app usage, the next sync is typically
            // much later and the small timestamp difference doesn't cause issues.
            lastSyncDateForData = finalFile.lastModified()
            modifiedAtLastSyncForData = downloadRemoteFileOperation.modificationTimestamp
            lastUsage = currentTime
        }
        saveFileOrFolderUseCase(SaveFileOrFolderUseCase.Params(ocFile))
        cleanConflictUseCase(
            CleanConflictUseCase.Params(
                fileId = ocFile.id!!
            )
        )

    }

    /**
     * Notify download result and then return Worker Result.
     */
    private fun notifyDownloadResult(
        throwable: Throwable?
    ): Result {
        cleanWorkersUuidUseCase(
            CleanWorkersUUIDUseCase.Params(
                fileId = workerParameters.inputData.getLong(KEY_PARAM_FILE_ID, -1)
            )
        )
        if (throwable !is CancelledException) {

            var tickerId = if (throwable == null) {
                R.string.downloader_download_succeeded_ticker
            } else {
                R.string.downloader_download_failed_ticker
            }

            var pendingIntent: PendingIntent? = null
            if (throwable is UnauthorizedException) {
                tickerId = R.string.downloader_download_failed_credentials_error
                pendingIntent = composePendingIntentToRefreshCredentials()
            }

            val contextText = ErrorMessageAdapter.getMessageFromTransfer(
                transferOperation = Download(finalLocationForFile),
                throwable = throwable,
                resources = appContext.resources
            )

            var timeOut: Long? = null

            // Remove success notification after timeout
            if (throwable == null) {
                timeOut = NOTIFICATION_TIMEOUT_STANDARD
            }

            createBasicNotification(
                context = appContext,
                contentTitle = appContext.getString(tickerId),
                notificationChannelId = DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                notificationId = DOWNLOAD_NOTIFICATION_ID_DEFAULT,
                intent = pendingIntent,
                contentText = contextText,
                timeOut = timeOut
            )
        }

        return if (throwable == null) {
            Result.success()
        } else {
            if (throwable is NoConnectionWithServerException) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun composePendingIntentToRefreshCredentials(): PendingIntent {
        val updateCredentialsIntent =
            Intent(appContext, LoginActivity::class.java).apply {
                putExtra(EXTRA_ACCOUNT, account.name)
                putExtra(EXTRA_ACTION, ACTION_UPDATE_EXPIRED_TOKEN)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_FROM_BACKGROUND)
            }

        return PendingIntent.getActivity(
            appContext,
            System.currentTimeMillis().toInt(),
            updateCredentialsIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getClientForThisDownload(): OpenCloudClient = SingleSessionManager.getDefaultSingleton()
        .getClientFor(
            OpenCloudAccount(AccountUtils.getOpenCloudAccountByName(appContext, account.name), appContext),
            appContext
        )

    override fun onTransferProgress(
        progressRate: Long,
        totalTransferredSoFar: Long,
        totalToTransfer: Long,
        filePath: String
    ) {
        if (this.isStopped) {
            Timber.w("Cancelling remote operation. The worker is stopped by user or system")
            downloadRemoteFileOperation.cancel()
            downloadRemoteFileOperation.removeDatatransferProgressListener(this)
        }

        val percent: Int = if (totalToTransfer == -1L) -1 else (100.0 * totalTransferredSoFar.toDouble() /
                totalToTransfer.toDouble()).toInt()
        if (percent == lastPercent) return

        // Set current progress. Observers will listen.
        CoroutineScope(Dispatchers.IO).launch {
            val progress = workDataOf(WORKER_KEY_PROGRESS to percent)
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
            Timber.w(e, "Failed to set foreground for download worker")
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
        val fileName = ocFile.fileName
        val builder = NotificationUtils
            .newNotificationBuilder(appContext, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.downloader_download_in_progress_ticker))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSubText(fileName)

        if (progress in 0..100) {
            builder.setContentText(
                appContext.getString(
                    R.string.downloader_download_in_progress_content,
                    progress,
                    fileName
                )
            )
            builder.setProgress(100, progress, false)
        } else {
            builder.setContentText(appContext.getString(R.string.downloader_download_in_progress_ticker))
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun computeNotificationId(): Int {
        val id = ocFile.id ?: 0L
        return if (id in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            id.toInt()
        } else {
            id.hashCode()
        }
    }

    companion object {
        const val KEY_PARAM_ACCOUNT = "KEY_PARAM_ACCOUNT"
        const val KEY_PARAM_FILE_ID = "KEY_PARAM_FILE_ID"
        const val WORKER_KEY_PROGRESS = "KEY_PROGRESS"
    }
}
