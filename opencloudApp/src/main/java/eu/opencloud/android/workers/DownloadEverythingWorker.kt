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
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import eu.opencloud.android.MainApp
import eu.opencloud.android.R
import eu.opencloud.android.data.download.DownloadProgress
import eu.opencloud.android.data.download.DownloadProgressDataStore
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.model.OCFile.Companion.ROOT_PATH
import eu.opencloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalAndProjectSpacesForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.RefreshSpacesFromServerAsyncUseCase
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.usecases.synchronization.SynchronizeFileUseCase
import eu.opencloud.android.usecases.transfers.downloads.DownloadFileUseCase
import eu.opencloud.android.utils.FileStorageUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/**
 * Worker that downloads ALL files from all accounts for offline access.
 * This is an opt-in feature that can be enabled in Security Settings.
 *
 * This worker:
 * 1. Iterates through all connected accounts
 * 2. Discovers all spaces (personal + project) for each account
 * 3. Iteratively scans all folders to find all files
 * 4. Enqueues a download for each file that is not yet available locally
 * 5. Shows a notification with progress information
 *
 * **Resumability**: Progress is persisted to [DownloadProgressDataStore] after every
 * account, space, and folder. If the worker is interrupted (Doze, app killed, screen lock,
 * network drop, etc.), the next run will resume from where it left off instead of
 * restarting from scratch.
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
    private val synchronizeFileUseCase: SynchronizeFileUseCase by inject()
    private val progressDataStore: DownloadProgressDataStore by inject()

    private var totalFilesFound = 0
    private var filesDownloaded = 0
    private var filesAlreadyLocal = 0
    private var filesSkipped = 0
    private var foldersProcessed = 0
    private var wasInterrupted = false
    private var processedFolderIds = mutableSetOf<Long>()
    private var bytesEnqueuedInThisRun = 0L
    private var filesEnqueuedInThisRun = 0

    override suspend fun doWork(): Result {
        Timber.i("DownloadEverythingWorker started")

        // Create notification channel and show initial foreground notification
        createNotificationChannel()

        val usableSpace = FileStorageUtils.getUsableSpace()
        if (usableSpace < MIN_FREE_SPACE_BYTES) {
            val msg = "Insufficient storage: ${usableSpace / MB} MB available, need at least ${MIN_FREE_SPACE_BYTES / MB} MB"
            Timber.w("DownloadEverythingWorker aborted: $msg")
            updateNotification(msg)
            return Result.failure()
        }

        // Try to resume from previous progress
        val savedProgress = progressDataStore.loadProgress()
        if (savedProgress != null) {
            restoreCounters(savedProgress)
            savedProgress.processedFolderIds.let { processedFolderIds.addAll(it) }
            Timber.i(
                "Resuming download scan from account ${savedProgress.accountIndex + 1}, " +
                        "space ${savedProgress.spaceIndex}, ${savedProgress.foldersProcessed} folders already processed"
            )
            updateNotification(
                "Resuming: ${savedProgress.foldersProcessed} folders already processed"
            )
        } else {
            Timber.i("Starting fresh download scan")
            updateNotification("Starting download of all files...")
        }

        return try {
            processAllAccounts(savedProgress)

            if (wasInterrupted || isStopped) {
                Timber.w(
                    "Worker was interrupted (wasInterrupted=$wasInterrupted, isStopped=$isStopped). " +
                            "Progress saved. Requesting retry."
                )
                return Result.retry()
            }

            val summary = "Done! Files: $totalFilesFound, Downloaded: $filesDownloaded, " +
                    "Already local: $filesAlreadyLocal, Skipped: $filesSkipped, Folders: $foldersProcessed"
            Timber.i("DownloadEverythingWorker completed: $summary")
            updateNotification(summary)

            // Success — clear progress so next run starts fresh
            progressDataStore.clearProgress()
            Timber.i("Cleared download progress state")

            Result.success()
        } catch (exception: Exception) {
            Timber.e(exception, "DownloadEverythingWorker failed with fatal error")
            updateNotification("Failed: ${exception.message}")
            progressDataStore.clearProgress()
            Result.failure()
        }
    }

    private suspend fun processAllAccounts(savedProgress: DownloadProgress?) {
        val accountManager = AccountManager.get(appContext)
        val accounts = accountManager.getAccountsByType(MainApp.accountType)

        Timber.i("Found ${accounts.size} accounts to process")

        for (accountIndex in accounts.indices) {
            val account = accounts[accountIndex]
            if (isStopped || wasInterrupted) {
                Timber.w("Worker stopped by system or interrupted during account loop. Requesting retry.")
                wasInterrupted = true
                return
            }

            // Skip already processed accounts when resuming
            if (savedProgress != null && accountIndex < savedProgress.accountIndex) {
                Timber.d("Skipping already processed account $accountIndex")
                continue
            }

            val accountName = account.name
            Timber.i("Processing account ${accountIndex + 1}/${accounts.size}: $accountName")
            updateNotification("Account ${accountIndex + 1}/${accounts.size}: $accountName")

            try {
                processAccount(accountName, account, accountIndex, savedProgress)
            } catch (e: IOException) {
                Timber.e(e, "Network error processing account $accountName — requesting retry")
                wasInterrupted = true
                return
            } catch (e: Exception) {
                Timber.e(e, "Error processing account $accountName — continuing with next account")
            }
        }
    }

    private suspend fun processAccount(
        accountName: String,
        account: android.accounts.Account,
        accountIndex: Int,
        savedProgress: DownloadProgress?
    ) {
        val capabilities = getStoredCapabilitiesUseCase(GetStoredCapabilitiesUseCase.Params(accountName))
        val spacesAvailableForAccount = AccountUtils.isSpacesFeatureAllowedForAccount(
            appContext,
            account,
            capabilities
        )

        if (!spacesAvailableForAccount) {
            // Account does not support spaces - process legacy root
            Timber.i("Account $accountName uses legacy mode (no spaces)")
            processSpaceRoot(accountName, ROOT_PATH, null, savedProgress)
        } else {
            // Account supports spaces - process all spaces
            refreshSpacesFromServerAsyncUseCase(RefreshSpacesFromServerAsyncUseCase.Params(accountName))
            val spaces = getPersonalAndProjectSpacesForAccountUseCase(
                GetPersonalAndProjectSpacesForAccountUseCase.Params(accountName)
            )

            Timber.i("Account $accountName has ${spaces.size} spaces")

            spaces.forEachIndexed { spaceIndex, space ->
                if (isStopped || wasInterrupted) {
                    Timber.w("Worker stopped or interrupted during space loop. Requesting retry.")
                    wasInterrupted = true
                    return@forEachIndexed
                }

                // When resuming the same account, skip already processed spaces
                if (savedProgress != null &&
                    accountIndex == savedProgress.accountIndex &&
                    spaceIndex < savedProgress.spaceIndex
                ) {
                    Timber.d("Skipping already processed space $spaceIndex")
                    return@forEachIndexed
                }

                Timber.i("Processing space ${spaceIndex + 1}/${spaces.size}: ${space.name}")
                updateNotification("Space ${spaceIndex + 1}/${spaces.size}: ${space.name}")

                processSpaceRoot(accountName, ROOT_PATH, space.id, savedProgress)

                if (wasInterrupted) return@forEachIndexed

                // Save progress after each space
                saveCurrentProgress(accountIndex = accountIndex, spaceIndex = spaceIndex + 1)
            }
        }

        if (wasInterrupted) return

        // Save progress after each account (reset space index)
        saveCurrentProgress(accountIndex = accountIndex + 1, spaceIndex = 0)
    }

    /**
     * Restores counters from a previously saved [DownloadProgress].
     */
    private fun restoreCounters(progress: DownloadProgress) {
        totalFilesFound = progress.totalFilesFound
        filesDownloaded = progress.filesDownloaded
        filesAlreadyLocal = progress.filesAlreadyLocal
        filesSkipped = progress.filesSkipped
        foldersProcessed = progress.foldersProcessed
    }

    /**
     * Persists the current progress to [DownloadProgressDataStore].
     */
    private suspend fun saveCurrentProgress(accountIndex: Int, spaceIndex: Int) {
        progressDataStore.saveProgress(
            DownloadProgress(
                accountIndex = accountIndex,
                spaceIndex = spaceIndex,
                processedFolderIds = processedFolderIds,
                totalFilesFound = totalFilesFound,
                filesDownloaded = filesDownloaded,
                filesAlreadyLocal = filesAlreadyLocal,
                filesSkipped = filesSkipped,
                foldersProcessed = foldersProcessed,
                isRunning = true,
                lastUpdateTimestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Processes the root of a space by refreshing it and then iteratively processing all content.
     */
    private suspend fun processSpaceRoot(
        accountName: String,
        remotePath: String,
        spaceId: String?,
        savedProgress: DownloadProgress?
    ) {
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

            // Process the root folder iteratively to avoid stack overflow on deep hierarchies
            processFolderIteratively(accountName, rootFolder, spaceId, savedProgress)

        } catch (e: IOException) {
            Timber.e(e, "Network error processing space root: spaceId=$spaceId")
            throw e // Re-throw so caller can trigger retry
        } catch (e: Exception) {
            Timber.e(e, "Error processing space root: spaceId=$spaceId")
        }
    }

    /**
     * Iteratively processes a folder using an explicit stack instead of recursion.
     * This avoids StackOverflowError on deeply nested folder hierarchies.
     *
     * When resuming, folders whose IDs are already in [savedProgress.processedFolderIds]
     * are skipped.
     */
    private suspend fun processFolderIteratively(
        accountName: String,
        rootFolder: OCFile,
        spaceId: String?,
        savedProgress: DownloadProgress?
    ) {
        val folderStack = ArrayDeque<OCFile>()
        folderStack.addLast(rootFolder)

        // Collect processed folder IDs from saved progress for quick lookup
        val processedIds = savedProgress?.processedFolderIds ?: emptySet()

        while (folderStack.isNotEmpty()) {
            if (isStopped || wasInterrupted) {
                Timber.w("Worker stopped or interrupted during folder processing. Requesting retry.")
                wasInterrupted = true
                break
            }

            val folder = folderStack.removeLast()
            val folderId = folder.id
            if (folderId == null) {
                Timber.w("Folder ${folder.remotePath} has no id, skipping")
                continue
            }

            // Skip refreshing and file processing for folders already processed in a previous run,
            // BUT we must still fetch their content from local DB to add subfolders to the stack.
            val alreadyProcessed = folderId in processedIds
            if (!alreadyProcessed) {
                foldersProcessed++
                processedFolderIds.add(folderId)
                Timber.d("Processing folder: ${folder.remotePath} (id=$folderId)")

                // First refresh this folder from server
                try {
                    fileRepository.refreshFolder(
                        remotePath = folder.remotePath,
                        accountName = accountName,
                        spaceId = spaceId,
                        isActionSetFolderAvailableOfflineOrSynchronize = false
                    )
                } catch (e: IOException) {
                    Timber.e(e, "Network error refreshing folder ${folder.remotePath}")
                    wasInterrupted = true
                    processedFolderIds.remove(folderId)
                    break
                } catch (e: Exception) {
                    Timber.e(e, "Error refreshing folder ${folder.remotePath}")
                }
            } else {
                Timber.d("Folder ${folder.remotePath} (id=$folderId) already processed in previous run, fetching subfolders only")
            }

            // Now get ALL content from local database
            val folderContent = try {
                fileRepository.getFolderContent(folderId)
            } catch (e: Exception) {
                Timber.e(e, "Error getting folder content for ${folder.remotePath}")
                emptyList()
            }

            Timber.d("Folder ${folder.remotePath} contains ${folderContent.size} items")

            for (item in folderContent) {
                if (isStopped || wasInterrupted) {
                    Timber.w("Worker stopped or interrupted during item processing. Requesting retry.")
                    wasInterrupted = true
                    if (!alreadyProcessed) processedFolderIds.remove(folderId)
                    break
                }
                if (item.isFolder) {
                    folderStack.addLast(item)
                } else if (!alreadyProcessed) {
                    processFile(accountName, item)
                }
            }

            // Update notification periodically
            if (foldersProcessed % 5 == 0) {
                updateNotification("Scanning: $foldersProcessed folders, $totalFilesFound files found")
            }
        }
    }

    /**
     * Processes a single file: checks if it's already local,
     * and if not, enqueues a download.
     */
    private suspend fun processFile(accountName: String, file: OCFile) {
        totalFilesFound++

        try {
            if (file.isAvailableLocally) {
                if (file.isDownloadedRemoteVersionCurrent()) {
                    filesAlreadyLocal++
                    Timber.d("File already local and current: ${file.fileName}")
                } else {
                    synchronizeStaleLocalFile(file)
                }
            } else {
                enqueueDownloadIfPossible(accountName, file)
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

    private fun OCFile.isDownloadedRemoteVersionCurrent(): Boolean {
        val currentRemoteEtag = remoteEtag
        return currentRemoteEtag.isNullOrBlank() || etag == currentRemoteEtag
    }

    private fun synchronizeStaleLocalFile(file: OCFile) {
        Timber.i(
            "File ${file.fileName} is local but stale. Synced etag=${file.etag}, remote etag=${file.remoteEtag}"
        )

        when (val useCaseResult = synchronizeFileUseCase(SynchronizeFileUseCase.Params(file))) {
            is UseCaseResult.Success -> {
                handleStaleLocalSyncResult(file, useCaseResult.data)
            }
            is UseCaseResult.Error -> {
                filesSkipped++
                Timber.e(useCaseResult.throwable, "Error synchronizing stale local file ${file.fileName}")
            }
        }
    }

    private fun handleStaleLocalSyncResult(file: OCFile, syncType: SynchronizeFileUseCase.SyncType) {
        when (syncType) {
            is SynchronizeFileUseCase.SyncType.DownloadEnqueued -> {
                if (syncType.workerId != null) {
                    filesDownloaded++
                    markDownloadEnqueued(file)
                    Timber.i("Enqueued download for stale local file: ${file.fileName}")
                } else {
                    filesSkipped++
                    Timber.d("Download already enqueued or skipped for stale local file: ${file.fileName}")
                }
            }
            is SynchronizeFileUseCase.SyncType.ConflictResolvedWithCopy -> {
                if (syncType.workerId != null) {
                    filesDownloaded++
                    markDownloadEnqueued(file)
                    Timber.i("Resolved conflict and enqueued download for stale local file: ${file.fileName}")
                } else {
                    filesSkipped++
                    Timber.d("Conflict was handled but download was not enqueued for stale local file: ${file.fileName}")
                }
            }
            is SynchronizeFileUseCase.SyncType.UploadEnqueued -> {
                filesAlreadyLocal++
                Timber.i("Local changes for ${file.fileName} were queued for upload")
            }
            SynchronizeFileUseCase.SyncType.AlreadySynchronized -> {
                filesAlreadyLocal++
                Timber.d("File already synchronized after stale check: ${file.fileName}")
            }
            SynchronizeFileUseCase.SyncType.FileNotFound -> {
                filesSkipped++
                Timber.w("File no longer exists on server: ${file.fileName}")
            }
        }
    }

    private fun enqueueDownloadIfPossible(accountName: String, file: OCFile) {
        val usableSpace = FileStorageUtils.getUsableSpace() - bytesEnqueuedInThisRun
        if (file.length > 0 && file.length > usableSpace) {
            filesSkipped++
            Timber.w("Skipping ${file.fileName} (${file.length} bytes) — not enough free space")
            return
        }

        val downloadId = downloadFileUseCase(
            DownloadFileUseCase.Params(accountName, file)
        )
        if (downloadId != null) {
            filesDownloaded++
            markDownloadEnqueued(file)
            Timber.i("Enqueued download for: ${file.fileName}")
        } else {
            filesSkipped++
            Timber.d("Download already enqueued or skipped: ${file.fileName}")
        }
    }

    private fun markDownloadEnqueued(file: OCFile) {
        filesEnqueuedInThisRun++
        bytesEnqueuedInThisRun += maxOf(0L, file.length)
        if (filesEnqueuedInThisRun >= MAX_ENQUEUED_FILES_PER_RUN) {
            Timber.i("Reached max enqueued files for this run ($MAX_ENQUEUED_FILES_PER_RUN). Requesting retry.")
            wasInterrupted = true
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

    private suspend fun updateNotification(contentText: String) {
        try {
            setForeground(createForegroundInfo(contentText))
        } catch (e: Exception) {
            Timber.e(e, "Error updating foreground notification")
        }
    }

    private fun createForegroundInfo(contentText: String): ForegroundInfo =
        ForegroundInfo(
            NOTIFICATION_ID,
            buildNotification(contentText),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

    private fun buildNotification(contentText: String): Notification =
        NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Download Everything")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(R.drawable.notification_icon)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

    companion object {
        const val DOWNLOAD_EVERYTHING_WORKER = "DOWNLOAD_EVERYTHING_WORKER"
        const val repeatInterval: Long = 6L
        val repeatIntervalTimeUnit: TimeUnit = TimeUnit.HOURS

        private const val NOTIFICATION_CHANNEL_ID = "download_everything_channel"
        private const val NOTIFICATION_ID = 9001
        private const val MB = 1024L * 1024L
        private const val MIN_FREE_SPACE_BYTES = 100L * MB
        private const val MAX_ENQUEUED_FILES_PER_RUN = 500
    }
}
