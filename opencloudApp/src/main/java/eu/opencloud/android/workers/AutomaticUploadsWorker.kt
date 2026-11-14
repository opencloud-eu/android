/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2022 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.workers

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.opencloud.android.R
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.automaticuploads.usecases.GetAutomaticUploadsConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SaveFolderBackupConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SavePictureUploadsConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SaveVideoUploadsConfigurationUseCase
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.domain.transfers.model.TransferStatus
import eu.opencloud.android.presentation.settings.SettingsActivity
import eu.opencloud.android.domain.transfers.model.UploadEnqueuedBy
import eu.opencloud.android.usecases.transfers.uploads.UploadFileFromContentUriUseCase
import eu.opencloud.android.utils.MimetypeIconUtil
import eu.opencloud.android.utils.NotificationUtils
import eu.opencloud.android.utils.UPLOAD_NOTIFICATION_CHANNEL_ID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import timber.log.Timber
import java.io.File
import java.util.Date
import java.util.concurrent.TimeUnit

class AutomaticUploadsWorker(
    val appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent {

    enum class SyncType(val prefixForType: String) {
        PICTURE_UPLOADS("image/"), VIDEO_UPLOADS("video/"), CUSTOM_UPLOADS("");

        fun getNotificationId(): Int =
            when (this) {
                PICTURE_UPLOADS -> pictureUploadsNotificationId
                VIDEO_UPLOADS -> videoUploadsNotificationId
                CUSTOM_UPLOADS -> customUploadsNotificationId
            }
    }

    private val getAutomaticUploadsConfigurationUseCase: GetAutomaticUploadsConfigurationUseCase by inject()

    private val transferRepository: TransferRepository by inject()

    // Only one scan at a time per process. WorkManager can restart this worker while a previous run is still
    // blocked in I/O, and the periodic, foreground and "upload now" triggers can overlap. Parallel scans waste
    // time and race on lastSyncTimestamp.
    override suspend fun doWork(): Result = scanMutex.withLock { doScan() }

    private fun doScan(): Result {
        Timber.i("Starting AutomaticUploadsWorker with UUID ${this.id}")
        when (val useCaseResult = getAutomaticUploadsConfigurationUseCase(Unit)) {
            is UseCaseResult.Success -> {
                val cameraUploadsConfiguration = useCaseResult.data
                if (cameraUploadsConfiguration == null || cameraUploadsConfiguration.areAutomaticUploadsDisabled()) {
                    cancelWorker()
                    return Result.success()
                }
                cameraUploadsConfiguration.pictureUploadsConfiguration?.let { pictureUploadsConfiguration ->
                    try {
                        checkSourcePathIsAValidUriOrThrowException(pictureUploadsConfiguration.sourcePath)
                        syncFolder(pictureUploadsConfiguration)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        Timber.e(illegalArgumentException, "Source path for picture uploads is not valid")
                        showNotificationToUpdateUri(SyncType.PICTURE_UPLOADS)
                    } catch (securityException: SecurityException) {
                        Timber.e(securityException, "Lost access to source folder for picture uploads")
                        showNotificationToUpdateUri(SyncType.PICTURE_UPLOADS)
                    } catch (exception: Exception) {
                        Timber.e(exception, "Could not scan source folder for picture uploads, will retry next run")
                    }
                }
                cameraUploadsConfiguration.videoUploadsConfiguration?.let { videoUploadsConfiguration ->
                    try {
                        checkSourcePathIsAValidUriOrThrowException(videoUploadsConfiguration.sourcePath)
                        syncFolder(videoUploadsConfiguration)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        Timber.e(illegalArgumentException, "Source path for video uploads is not valid")
                        showNotificationToUpdateUri(SyncType.VIDEO_UPLOADS)
                    } catch (securityException: SecurityException) {
                        Timber.e(securityException, "Lost access to source folder for video uploads")
                        showNotificationToUpdateUri(SyncType.VIDEO_UPLOADS)
                    } catch (exception: Exception) {
                        Timber.e(exception, "Could not scan source folder for video uploads, will retry next run")
                    }
                }
                cameraUploadsConfiguration.customUploadsConfigurations.forEach { customUploadsConfiguration ->
                    try {
                        checkSourcePathIsAValidUriOrThrowException(customUploadsConfiguration.sourcePath)
                        syncFolder(customUploadsConfiguration)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        Timber.e(illegalArgumentException, "Source path for folder uploads is not valid")
                        showNotificationToUpdateUri(SyncType.CUSTOM_UPLOADS)
                    } catch (securityException: SecurityException) {
                        Timber.e(securityException, "Lost access to source folder for folder uploads")
                        showNotificationToUpdateUri(SyncType.CUSTOM_UPLOADS)
                    } catch (exception: Exception) {
                        Timber.e(exception, "Could not scan source folder for folder uploads, will retry next run")
                    }
                }
            }
            is UseCaseResult.Error -> {
                Timber.e(useCaseResult.throwable, "Worker ${useCaseResult.throwable}")
            }
        }
        Timber.i("Finishing CameraUploadsWorker with UUID ${this.id}")
        return Result.success()
    }

    @Throws(IllegalArgumentException::class)
    private fun checkSourcePathIsAValidUriOrThrowException(sourcePath: String) {
        val sourceUri: Uri = sourcePath.toUri()
        DocumentFile.fromTreeUri(applicationContext, sourceUri)
    }

    private fun cancelWorker() {
        WorkManager.getInstance(appContext).cancelUniqueWork(AUTOMATIC_UPLOADS_WORKER)
    }

    private fun syncFolder(folderBackUpConfiguration: FolderBackUpConfiguration?) {
        if (folderBackUpConfiguration == null) return
        if (folderBackUpConfiguration.sourcePath.isBlank()) {
            Timber.w("No source folder set for ${folderBackUpConfiguration.name}, skipping")
            return
        }

        val syncType = when {
            folderBackUpConfiguration.isPictureUploads -> SyncType.PICTURE_UPLOADS
            folderBackUpConfiguration.isVideoUploads -> SyncType.VIDEO_UPLOADS
            folderBackUpConfiguration.isCustomUploads -> SyncType.CUSTOM_UPLOADS
            // Else should not happen for the moment. Maybe in upcoming features..
            else -> SyncType.PICTURE_UPLOADS
        }

        val mimeTypeFilter: (String) -> Boolean = if (syncType == SyncType.CUSTOM_UPLOADS) {
            { mimeType -> folderBackUpConfiguration.mediaType.matches(mimeType) }
        } else {
            { mimeType -> mimeType.startsWith(syncType.prefixForType) }
        }

        val currentTimestamp = System.currentTimeMillis()

        val localPicturesDocumentFiles: List<LocalFile> = getFilesReadyToUpload(
            syncType = syncType,
            mimeTypeFilter = mimeTypeFilter,
            sourcePath = folderBackUpConfiguration.sourcePath,
            lastSyncTimestamp = folderBackUpConfiguration.lastSyncTimestamp,
            currentTimestamp = currentTimestamp,
        )

        showNotification(syncType, localPicturesDocumentFiles.size)

        for (documentFile in localPicturesDocumentFiles) {
            // Dedup: if this content URI already has a queued, in-progress, or succeeded transfer,
            // skip it. Without this, a worker killed mid-loop (before updateTimestamp) or a
            // file whose lastModified changed (e.g. media scanner) would be re-discovered and
            // enqueued with a new upload ID — leading to duplicate uploads or 0-byte files
            // when two workers race on the same cache path.
            val contentUri = documentFile.uri.toString()
            Timber.i("Enqueueing %s (%s) -> %s", documentFile.name, documentFile.mimeType, folderBackUpConfiguration.uploadPath)
            if (transferRepository.existsNonFailedTransferForUri(contentUri)) {
                Timber.d("Skipping already-tracked file: %s", documentFile.name)
                continue
            }
            val uploadId = storeInUploadsDatabase(
                documentFile = documentFile,
                uploadPath = folderBackUpConfiguration.uploadPath.plus(File.separator).plus(documentFile.name),
                accountName = folderBackUpConfiguration.accountName,
                behavior = folderBackUpConfiguration.behavior,
                createdByWorker = when (syncType) {
                    SyncType.PICTURE_UPLOADS, SyncType.CUSTOM_UPLOADS -> UploadEnqueuedBy.ENQUEUED_AS_AUTOMATIC_UPLOAD_PICTURE
                    SyncType.VIDEO_UPLOADS -> UploadEnqueuedBy.ENQUEUED_AS_AUTOMATIC_UPLOAD_VIDEO
                },
                spaceId = folderBackUpConfiguration.spaceId
            )
            enqueueSingleUpload(
                contentUri = documentFile.uri,
                uploadPath = folderBackUpConfiguration.uploadPath.plus(File.separator).plus(documentFile.name),
                lastModified = documentFile.lastModified,
                behavior = folderBackUpConfiguration.behavior.toString(),
                accountName = folderBackUpConfiguration.accountName,
                uploadId = uploadId,
                wifiOnly = folderBackUpConfiguration.wifiOnly,
                chargingOnly = folderBackUpConfiguration.chargingOnly
            )
        }
        // Save safeTimestamp (not currentTimestamp) so that files skipped by the
        // write-safety buffer are re-evaluated on the next run instead of being lost.
        val safeTimestamp = currentTimestamp - WRITE_SAFETY_BUFFER_MS
        updateTimestamp(folderBackUpConfiguration, syncType, safeTimestamp)
    }

    private fun showNotification(
        syncType: SyncType,
        numberOfFilesToUpload: Int
    ) {
        if (numberOfFilesToUpload == 0) return

        val contentText = when (syncType) {
            SyncType.PICTURE_UPLOADS -> R.string.uploader_upload_picture_upload_files
            SyncType.VIDEO_UPLOADS -> R.string.uploader_upload_video_upload_files
            SyncType.CUSTOM_UPLOADS -> R.string.uploader_upload_custom_upload_files
        }

        NotificationUtils.createBasicNotification(
            context = appContext,
            contentTitle = appContext.getString(R.string.uploader_upload_camera_upload_files),
            contentText = appContext.getString(contentText, numberOfFilesToUpload),
            notificationChannelId = UPLOAD_NOTIFICATION_CHANNEL_ID,
            notificationId = syncType.getNotificationId(),
            intent = NotificationUtils.composePendingIntentToUploadList(appContext),
            onGoing = false,
            timeOut = 5_000
        )
    }

    private fun showNotificationToUpdateUri(
        syncType: SyncType
    ) {
        val contentText: Int = when (syncType) {
            SyncType.PICTURE_UPLOADS -> R.string.uploader_upload_picture_upload_error
            SyncType.VIDEO_UPLOADS -> R.string.uploader_upload_video_upload_error
            SyncType.CUSTOM_UPLOADS -> R.string.uploader_upload_custom_upload_error
        }
        val notificationKey: String = when (syncType) {
            SyncType.PICTURE_UPLOADS -> SettingsActivity.NOTIFICATION_INTENT_PICTURES
            SyncType.VIDEO_UPLOADS -> SettingsActivity.NOTIFICATION_INTENT_VIDEOS
            SyncType.CUSTOM_UPLOADS -> SettingsActivity.NOTIFICATION_INTENT_CUSTOM_FOLDERS
        }
        NotificationUtils.createBasicNotification(
            context = appContext,
            contentTitle = appContext.getString(R.string.uploader_upload_camera_upload_source_path_error),
            contentText = appContext.getString(contentText),
            notificationChannelId = UPLOAD_NOTIFICATION_CHANNEL_ID,
            notificationId = syncType.getNotificationId(),
            intent = NotificationUtils.composePendingIntentToAutomaticUploads(appContext, notificationKey),
            onGoing = false,
            timeOut = null
        )
    }

    private fun updateTimestamp(
        folderBackUpConfiguration: FolderBackUpConfiguration,
        syncType: SyncType,
        currentTimestamp: Long,
    ) {

        when (syncType) {
            SyncType.PICTURE_UPLOADS -> {
                val savePictureUploadsConfigurationUseCase: SavePictureUploadsConfigurationUseCase by inject()
                savePictureUploadsConfigurationUseCase(
                    SavePictureUploadsConfigurationUseCase.Params(folderBackUpConfiguration.copy(lastSyncTimestamp = currentTimestamp))
                )
            }
            SyncType.VIDEO_UPLOADS -> {
                val saveVideoUploadsConfigurationUseCase: SaveVideoUploadsConfigurationUseCase by inject()
                saveVideoUploadsConfigurationUseCase(
                    SaveVideoUploadsConfigurationUseCase.Params(folderBackUpConfiguration.copy(lastSyncTimestamp = currentTimestamp))
                )
            }
            SyncType.CUSTOM_UPLOADS -> {
                val saveFolderBackupConfigurationUseCase: SaveFolderBackupConfigurationUseCase by inject()
                saveFolderBackupConfigurationUseCase(
                    SaveFolderBackupConfigurationUseCase.Params(folderBackUpConfiguration.copy(lastSyncTimestamp = currentTimestamp))
                )
            }
        }
    }

    /**
     * Lists the files directly inside [sourcePath] (a SAF tree URI) with a single ContentResolver query.
     *
     * Unlike DocumentFile.listFiles(), which needs one IPC round trip per file for name, type and date (minutes for
     * a camera folder with thousands of files), this reads everything in one go. It also throws on errors instead of
     * silently returning an empty list, so the caller does not advance lastSyncTimestamp past files it never saw.
     */
    @Throws(Exception::class)
    private fun getFilesReadyToUpload(
        syncType: SyncType,
        mimeTypeFilter: (String) -> Boolean,
        sourcePath: String,
        lastSyncTimestamp: Long,
        currentTimestamp: Long,
    ): List<LocalFile> {
        val treeUri: Uri = sourcePath.toUri()
        val localFiles = listChildDocuments(treeUri)

        // Exclude files modified within the last few seconds. Camera apps may still be
        // writing the file (not all apps use atomic rename), so picking it up too early
        // can result in uploading a truncated or 0-byte JPEG.
        val safeTimestamp = currentTimestamp - WRITE_SAFETY_BUFFER_MS

        val files = localFiles.filterNot { it.isDirectory }
        val filteredList: List<LocalFile> = files
            .filter { it.lastModified >= lastSyncTimestamp }
            .filter { it.lastModified < safeTimestamp }
            .filter { mimeTypeFilter(it.mimeType) }
            .sortedBy { it.lastModified }

        Timber.i("Last sync ${syncType.name}: ${Date(lastSyncTimestamp)}")
        Timber.i("CurrentTimestamp ${Date(currentTimestamp)}")
        Timber.i("${files.size} files found in folder: ${treeUri.path}")
        Timber.i("${filteredList.size} files are ${syncType.name} and were taken after last sync")

        return filteredList
    }

    private fun listChildDocuments(treeUri: Uri): List<LocalFile> {
        val contentResolver = appContext.contentResolver
        val hasPersistedPermission = contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }
        if (!hasPersistedPermission) {
            Timber.w("No persisted read permission for $treeUri. Persisted: ${contentResolver.persistedUriPermissions.map { it.uri }}")
        }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val result = mutableListOf<LocalFile>()
        val cursor = contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw IllegalStateException("Query returned no cursor for $childrenUri")
        cursor.use {
            while (it.moveToNext()) {
                val documentId = it.getString(0) ?: continue
                val name = it.getString(1) ?: continue
                val providerMimeType: String? = it.getString(2)
                val isDirectory = providerMimeType == DocumentsContract.Document.MIME_TYPE_DIR
                val mimeType = if (isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else MimetypeIconUtil.getBestMimeTypeByFilename(name)
                result.add(
                    LocalFile(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        name = name,
                        mimeType = mimeType ?: "",
                        lastModified = if (it.isNull(3)) 0L else it.getLong(3),
                        length = if (it.isNull(4)) 0L else it.getLong(4),
                        isDirectory = isDirectory,
                    )
                )
            }
        }
        return result
    }

    private data class LocalFile(
        val uri: Uri,
        val name: String,
        val mimeType: String,
        val lastModified: Long,
        val length: Long,
        val isDirectory: Boolean,
    )

    private fun enqueueSingleUpload(
        contentUri: Uri,
        uploadPath: String,
        lastModified: Long,
        behavior: String,
        accountName: String,
        uploadId: Long,
        wifiOnly: Boolean,
        chargingOnly: Boolean
    ) {
        val lastModifiedInSeconds = (lastModified / 1000L).toString()

        UploadFileFromContentUriUseCase(WorkManager.getInstance(appContext))(
            UploadFileFromContentUriUseCase.Params(
                accountName = accountName,
                contentUri = contentUri,
                lastModifiedInSeconds = lastModifiedInSeconds,
                behavior = behavior,
                uploadPath = uploadPath,
                uploadIdInStorageManager = uploadId,
                wifiOnly = wifiOnly,
                chargingOnly = chargingOnly
            )
        )
    }

    private fun storeInUploadsDatabase(
        documentFile: LocalFile,
        uploadPath: String,
        accountName: String,
        behavior: UploadBehavior,
        createdByWorker: UploadEnqueuedBy,
        spaceId: String?,
    ): Long {
        val ocTransfer = OCTransfer(
            localPath = documentFile.uri.toString(),
            remotePath = uploadPath,
            accountName = accountName,
            fileSize = documentFile.length,
            status = TransferStatus.TRANSFER_QUEUED,
            localBehaviour = behavior,
            forceOverwrite = false,
            createdBy = createdByWorker,
            spaceId = spaceId,
            sourcePath = documentFile.uri.toString(),
        )

        return transferRepository.saveTransfer(ocTransfer)
    }

    companion object {
        const val AUTOMATIC_UPLOADS_WORKER = "AUTOMATIC_UPLOADS_WORKER"
        const val IMMEDIATE_UPLOADS_WORKER = "IMMEDIATE_AUTOMATIC_UPLOADS_WORKER"
        const val SYNC_NOW_UPLOADS_WORKER = "SYNC_NOW_AUTOMATIC_UPLOADS_WORKER"
        private val scanMutex = Mutex()
        const val repeatInterval: Long = 15L
        val repeatIntervalTimeUnit: TimeUnit = TimeUnit.MINUTES
        private const val pictureUploadsNotificationId = 101
        private const val videoUploadsNotificationId = 102
        private const val customUploadsNotificationId = 103
        const val WRITE_SAFETY_BUFFER_MS = 10_000L
    }
}
