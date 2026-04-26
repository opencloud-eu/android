/**
 * openCloud Android client application
 *
 * @author OpenCloud Development Team
 *
 * Copyright (C) 2026 OpenCloud.
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

import android.accounts.AccountManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.opencloud.android.MainApp
import eu.opencloud.android.R
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.usecases.synchronization.SynchronizeFileUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker that periodically syncs locally modified files to the cloud.
 * This is an opt-in feature that can be enabled in Security Settings.
 *
 * It monitors all downloaded files and checks if they have been modified locally.
 * If a file has been modified, it uploads the new version to the server.
 *
 * Shows a notification with sync progress and results.
 */
class LocalFileSyncWorker(
    private val appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent {

    private val fileRepository: FileRepository by inject()
    private val synchronizeFileUseCase: SynchronizeFileUseCase by inject()

    override suspend fun doWork(): Result {
        Timber.i("LocalFileSyncWorker started")

        createNotificationChannel()

        return try {
            val accountManager = AccountManager.get(appContext)
            val accounts = accountManager.getAccountsByType(MainApp.accountType)

            Timber.i("Checking ${accounts.size} accounts for local file changes")

            var totalFilesChecked = 0
            var filesUploaded = 0
            var filesDownloaded = 0
            var filesWithConflicts = 0
            var filesAlreadySynced = 0
            var filesNotFound = 0
            var errors = 0

            for (account in accounts) {
                val accountName = account.name
                Timber.d("Checking locally downloaded files for account: $accountName")

                val downloadedFiles = fileRepository.getDownloadedFilesForAccount(accountName)
                Timber.d("Found ${downloadedFiles.size} downloaded files for account $accountName")

                for (file in downloadedFiles) {
                    if (isStopped) {
                        Timber.i("Worker stopped by system. Halting sync.")
                        return Result.success()
                    }
                    if (file.isFolder) continue

                    totalFilesChecked++
                    val result = syncSingleFile(file)
                    when (result) {
                        is FileSyncResult.Uploaded -> filesUploaded++
                        is FileSyncResult.Downloaded -> filesDownloaded++
                        is FileSyncResult.Conflict -> filesWithConflicts++
                        is FileSyncResult.AlreadySynced -> filesAlreadySynced++
                        is FileSyncResult.NotFound -> filesNotFound++
                        is FileSyncResult.Error -> errors++
                    }
                }
            }

            val summary = buildString {
                append("Checked: $totalFilesChecked")
                if (filesUploaded > 0) append(" | Uploaded: $filesUploaded")
                if (filesDownloaded > 0) append(" | Downloaded: $filesDownloaded")
                if (filesWithConflicts > 0) append(" | Conflicts: $filesWithConflicts")
                if (errors > 0) append(" | Errors: $errors")
            }

            Timber.i("LocalFileSyncWorker completed: $summary")

            if (filesUploaded > 0 || filesDownloaded > 0 || filesWithConflicts > 0) {
                showCompletionNotification(summary)
            }

            Result.success()
        } catch (exception: Exception) {
            Timber.e(exception, "LocalFileSyncWorker failed")
            Result.failure()
        }
    }

    private suspend fun syncSingleFile(file: eu.opencloud.android.domain.files.model.OCFile): FileSyncResult {
        try {
            val storagePath = file.storagePath
            val shouldSync = if (!storagePath.isNullOrBlank()) {
                val localFile = java.io.File(storagePath)
                if (localFile.exists()) {
                    val lastModified = localFile.lastModified()
                    val lastSync = file.lastSyncDateForData ?: 0
                    lastModified > lastSync
                } else {
                    true
                }
            } else {
                true
            }

            if (!shouldSync) return FileSyncResult.AlreadySynced

            val useCaseResult = synchronizeFileUseCase(SynchronizeFileUseCase.Params(file))
            return when (useCaseResult) {
                is UseCaseResult.Success -> {
                    when (val syncResult = useCaseResult.data) {
                        is SynchronizeFileUseCase.SyncType.UploadEnqueued -> {
                            Timber.i("File ${file.fileName} has local changes, upload enqueued")
                            FileSyncResult.Uploaded
                        }
                        is SynchronizeFileUseCase.SyncType.DownloadEnqueued -> {
                            Timber.i("File ${file.fileName} has remote changes, download enqueued")
                            FileSyncResult.Downloaded
                        }
                        is SynchronizeFileUseCase.SyncType.ConflictResolvedWithCopy -> {
                            Timber.i(
                                "File ${file.fileName} had a conflict. " +
                                        "Conflicted copy created at: ${syncResult.conflictedCopyPath}"
                            )
                            FileSyncResult.Conflict
                        }
                        is SynchronizeFileUseCase.SyncType.AlreadySynchronized -> {
                            Timber.d("File ${file.fileName} is already synchronized")
                            FileSyncResult.AlreadySynced
                        }
                        is SynchronizeFileUseCase.SyncType.FileNotFound -> {
                            Timber.w("File ${file.fileName} was not found on server")
                            FileSyncResult.NotFound
                        }
                    }
                }
                is UseCaseResult.Error -> {
                    Timber.e(useCaseResult.throwable, "Error syncing file ${file.fileName}")
                    FileSyncResult.Error
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing file ${file.fileName}")
            return FileSyncResult.Error
        }
    }

    private sealed interface FileSyncResult {
        object Uploaded : FileSyncResult
        object Downloaded : FileSyncResult
        object Conflict : FileSyncResult
        object AlreadySynced : FileSyncResult
        object NotFound : FileSyncResult
        object Error : FileSyncResult
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Auto-Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when local file changes are synced"
            }
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showCompletionNotification(summary: String) {
        try {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Auto-Sync Complete")
                .setContentText(summary)
                .setSmallIcon(R.drawable.notification_icon)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Error showing notification")
        }
    }

    companion object {
        const val LOCAL_FILE_SYNC_WORKER = "LOCAL_FILE_SYNC_WORKER"
        const val repeatInterval: Long = 2L
        val repeatIntervalTimeUnit: TimeUnit = TimeUnit.HOURS

        private const val NOTIFICATION_CHANNEL_ID = "auto_sync_channel"
        private const val NOTIFICATION_ID = 9002
    }
}
