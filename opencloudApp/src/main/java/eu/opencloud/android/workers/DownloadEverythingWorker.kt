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
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.model.OCFile.Companion.ROOT_PATH
import eu.opencloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalAndProjectSpacesForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.RefreshSpacesFromServerAsyncUseCase
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.usecases.transfers.downloads.DownloadFileUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Worker that downloads ALL files from all accounts for offline access.
 * This is an opt-in feature that can be enabled in Security Settings.
 *
 * This worker:
 * 1. Iterates through all connected accounts
 * 2. Discovers all spaces (personal + project) for each account
 * 3. Recursively scans all folders to find all files
 * 4. Enqueues a download for each file that is not yet available locally
 * 5. Shows a notification with progress information
 */
class DownloadEverythingWorker(
    private val appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent {

    private val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase by inject()
    private val refreshSpacesFromServerAsyncUseCase: RefreshSpacesFromServerAsyncUseCase by inject()
    private val getPersonalAndProjectSpacesForAccountUseCase: GetPersonalAndProjectSpacesForAccountUseCase by inject()
    private val getFileByRemotePathUseCase: GetFileByRemotePathUseCase by inject()
    private val fileRepository: FileRepository by inject()
    private val downloadFileUseCase: DownloadFileUseCase by inject()

    private var totalFilesFound = 0
    private var filesDownloaded = 0
    private var filesAlreadyLocal = 0
    private var filesSkipped = 0
    private var foldersProcessed = 0

    override suspend fun doWork(): Result {
        Timber.i("DownloadEverythingWorker started")

        // Create notification channel and show initial notification
        createNotificationChannel()
        updateNotification("Starting download of all files...")

        return try {
            val accountManager = AccountManager.get(appContext)
            val accounts = accountManager.getAccountsByType(MainApp.accountType)

            Timber.i("Found ${accounts.size} accounts to process")
            updateNotification("Found ${accounts.size} accounts")

            accounts.forEachIndexed { accountIndex, account ->
                val accountName = account.name
                Timber.i("Processing account ${accountIndex + 1}/${accounts.size}: $accountName")
                updateNotification("Account ${accountIndex + 1}/${accounts.size}: $accountName")

                try {
                    // Get capabilities for account
                    val capabilities = getStoredCapabilitiesUseCase(GetStoredCapabilitiesUseCase.Params(accountName))
                    val spacesAvailableForAccount = AccountUtils.isSpacesFeatureAllowedForAccount(
                        appContext,
                        account,
                        capabilities
                    )

                    if (!spacesAvailableForAccount) {
                        // Account does not support spaces - process legacy root
                        Timber.i("Account $accountName uses legacy mode (no spaces)")
                        processSpaceRoot(accountName, ROOT_PATH, null)
                    } else {
                        // Account supports spaces - process all spaces
                        refreshSpacesFromServerAsyncUseCase(RefreshSpacesFromServerAsyncUseCase.Params(accountName))
                        val spaces = getPersonalAndProjectSpacesForAccountUseCase(
                            GetPersonalAndProjectSpacesForAccountUseCase.Params(accountName)
                        )

                        Timber.i("Account $accountName has ${spaces.size} spaces")

                        spaces.forEachIndexed { spaceIndex, space ->
                            Timber.i("Processing space ${spaceIndex + 1}/${spaces.size}: ${space.name}")
                            updateNotification("Space ${spaceIndex + 1}/${spaces.size}: ${space.name}")

                            processSpaceRoot(accountName, ROOT_PATH, space.root.id)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error processing account $accountName")
                }
            }

            val summary = "Done! Files: $totalFilesFound, Downloaded: $filesDownloaded, " +
                    "Already local: $filesAlreadyLocal, Skipped: $filesSkipped, Folders: $foldersProcessed"
            Timber.i("DownloadEverythingWorker completed: $summary")
            updateNotification(summary)

            Result.success()
        } catch (exception: Exception) {
            Timber.e(exception, "DownloadEverythingWorker failed")
            updateNotification("Failed: ${exception.message}")
            Result.failure()
        }
    }

    /**
     * Processes the root of a space by refreshing it and then recursively processing all content.
     */
    private fun processSpaceRoot(accountName: String, remotePath: String, spaceId: String?) {
        try {
            Timber.i("Processing space root: remotePath=$remotePath, spaceId=$spaceId")

            // First refresh the root folder from server to ensure DB has latest data
            fileRepository.refreshFolder(
                remotePath = remotePath,
                accountName = accountName,
                spaceId = spaceId,
                isActionSetFolderAvailableOfflineOrSynchronize = false
            )

            // Now get the root folder from local database
            val rootFolder = getFileByRemotePathUseCase(
                GetFileByRemotePathUseCase.Params(accountName, remotePath, spaceId)
            ).getDataOrNull()

            if (rootFolder == null) {
                Timber.w("Root folder not found after refresh for spaceId=$spaceId")
                return
            }

            Timber.i("Got root folder with id=${rootFolder.id}, remotePath=${rootFolder.remotePath}")

            // Process the root folder recursively
            processFolderRecursively(accountName, rootFolder, spaceId)

        } catch (e: Exception) {
            Timber.e(e, "Error processing space root: spaceId=$spaceId")
        }
    }

    /**
     * Recursively processes a folder: gets content from database,
     * enqueues downloads for files, and recurses into subfolders.
     */
    private fun processFolderRecursively(accountName: String, folder: OCFile, spaceId: String?) {
        try {
            val folderId = folder.id
            if (folderId == null) {
                Timber.w("Folder ${folder.remotePath} has no id, skipping")
                return
            }

            foldersProcessed++
            Timber.d("Processing folder: ${folder.remotePath} (id=$folderId)")

            // First refresh this folder from server
            try {
                fileRepository.refreshFolder(
                    remotePath = folder.remotePath,
                    accountName = accountName,
                    spaceId = spaceId,
                    isActionSetFolderAvailableOfflineOrSynchronize = false
                )
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing folder ${folder.remotePath}")
            }

            // Now get ALL content from local database (this returns everything, not just changes)
            val folderContent = fileRepository.getFolderContent(folderId)

            Timber.d("Folder ${folder.remotePath} contains ${folderContent.size} items")

            folderContent.forEach { item ->
                if (item.isFolder) {
                    // Recursively process subfolders
                    processFolderRecursively(accountName, item, spaceId)
                } else {
                    // Process file
                    processFile(accountName, item)
                }
            }

            // Update notification periodically
            if (foldersProcessed % 5 == 0) {
                updateNotification("Scanning: $foldersProcessed folders, $totalFilesFound files found")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error processing folder ${folder.remotePath}")
        }
    }

    /**
     * Processes a single file: checks if it's already local,
     * and if not, enqueues a download.
     */
    private fun processFile(accountName: String, file: OCFile) {
        totalFilesFound++

        try {
            if (file.isAvailableLocally) {
                // File is already downloaded
                filesAlreadyLocal++
                Timber.d("File already local: ${file.fileName}")
            } else {
                // Enqueue download
                val downloadId = downloadFileUseCase(DownloadFileUseCase.Params(accountName, file))
                if (downloadId != null) {
                    filesDownloaded++
                    Timber.i("Enqueued download for: ${file.fileName}")
                } else {
                    filesSkipped++
                    Timber.d("Download already enqueued or skipped: ${file.fileName}")
                }
            }

            // Update notification periodically (every 20 files)
            if (totalFilesFound % 20 == 0) {
                updateNotification("Found: $totalFilesFound files, $filesDownloaded queued for download")
            }
        } catch (e: Exception) {
            filesSkipped++
            Timber.e(e, "Error processing file ${file.fileName}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Download Everything",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress when downloading all files"
            }
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(contentText: String) {
        try {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Download Everything")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(R.drawable.notification_icon)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Error updating notification")
        }
    }

    companion object {
        const val DOWNLOAD_EVERYTHING_WORKER = "DOWNLOAD_EVERYTHING_WORKER"
        const val repeatInterval: Long = 6L
        val repeatIntervalTimeUnit: TimeUnit = TimeUnit.HOURS

        private const val NOTIFICATION_CHANNEL_ID = "download_everything_channel"
        private const val NOTIFICATION_ID = 9001
    }
}
