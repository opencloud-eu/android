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
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.opencloud.android.R
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.automaticuploads.model.UseSubfoldersBehaviour
import eu.opencloud.android.domain.automaticuploads.usecases.GetAutomaticUploadsConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SavePictureUploadsConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SaveVideoUploadsConfigurationUseCase
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.domain.transfers.model.TransferStatus
import eu.opencloud.android.domain.transfers.model.UploadEnqueuedBy
import eu.opencloud.android.presentation.settings.SettingsActivity
import eu.opencloud.android.usecases.transfers.uploads.UploadFileFromContentUriUseCase
import eu.opencloud.android.utils.MimetypeIconUtil
import eu.opencloud.android.utils.NotificationUtils
import eu.opencloud.android.utils.UPLOAD_NOTIFICATION_CHANNEL_ID
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        PICTURE_UPLOADS("image/"), VIDEO_UPLOADS("video/");

        fun getNotificationId(): Int =
            when (this) {
                PICTURE_UPLOADS -> pictureUploadsNotificationId
                VIDEO_UPLOADS -> videoUploadsNotificationId
            }
    }

    private val getAutomaticUploadsConfigurationUseCase: GetAutomaticUploadsConfigurationUseCase by inject()

    private val transferRepository: TransferRepository by inject()

    override suspend fun doWork(): Result {
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
                        return Result.failure()
                    }
                }
                cameraUploadsConfiguration.videoUploadsConfiguration?.let { videoUploadsConfiguration ->
                    try {
                        checkSourcePathIsAValidUriOrThrowException(videoUploadsConfiguration.sourcePath)
                        syncFolder(videoUploadsConfiguration)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        Timber.e(illegalArgumentException, "Source path for video uploads is not valid")
                        showNotificationToUpdateUri(SyncType.VIDEO_UPLOADS)
                        return Result.failure()
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

        val syncType = when {
            folderBackUpConfiguration.isPictureUploads -> SyncType.PICTURE_UPLOADS
            folderBackUpConfiguration.isVideoUploads -> SyncType.VIDEO_UPLOADS
            // Else should not happen for the moment. Maybe in upcoming features..
            else -> SyncType.PICTURE_UPLOADS
        }

        val currentTimestamp = System.currentTimeMillis()

        val localPicturesDocumentFiles: List<DocumentFile> = getFilesReadyToUpload(
            syncType = syncType,
            sourcePath = folderBackUpConfiguration.sourcePath,
            lastSyncTimestamp = folderBackUpConfiguration.lastSyncTimestamp,
            currentTimestamp = currentTimestamp,
        )

        showNotification(syncType, localPicturesDocumentFiles.size)

        for (documentFile in localPicturesDocumentFiles) {
            val uploadPath = buildUploadPath(documentFile, folderBackUpConfiguration)
            val uploadId = storeInUploadsDatabase(
                documentFile = documentFile,
                uploadPath = uploadPath,
                accountName = folderBackUpConfiguration.accountName,
                behavior = folderBackUpConfiguration.behavior,
                createdByWorker = when (syncType) {
                    SyncType.PICTURE_UPLOADS -> UploadEnqueuedBy.ENQUEUED_AS_AUTOMATIC_UPLOAD_PICTURE
                    SyncType.VIDEO_UPLOADS -> UploadEnqueuedBy.ENQUEUED_AS_AUTOMATIC_UPLOAD_VIDEO
                },
                spaceId = folderBackUpConfiguration.spaceId
            )
            enqueueSingleUpload(
                contentUri = documentFile.uri,
                uploadPath = uploadPath,
                lastModified = documentFile.lastModified(),
                behavior = folderBackUpConfiguration.behavior.toString(),
                accountName = folderBackUpConfiguration.accountName,
                uploadId = uploadId,
                wifiOnly = folderBackUpConfiguration.wifiOnly,
                chargingOnly = folderBackUpConfiguration.chargingOnly
            )
        }
        updateTimestamp(folderBackUpConfiguration, syncType, currentTimestamp)
    }

    private fun buildUploadPath(
        documentFile: DocumentFile,
        folderBackUpConfiguration: FolderBackUpConfiguration,
    ): String {
        val pathBuilder = StringBuilder(folderBackUpConfiguration.uploadPath.plus(File.separator))

        val lastModifiedDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(documentFile.lastModified()), ZoneId.systemDefault())
        val yearStr = lastModifiedDateTime.format(DateTimeFormatter.ofPattern("yyyy"))
        val monthStr = lastModifiedDateTime.format(DateTimeFormatter.ofPattern("MM"))
        val dayStr = lastModifiedDateTime.format(DateTimeFormatter.ofPattern("dd"))

        when (folderBackUpConfiguration.useSubfoldersBehaviour) {
            UseSubfoldersBehaviour.YEAR_MONTH_DAY -> {
                pathBuilder.append(yearStr).append(File.separator)
                pathBuilder.append(monthStr).append(File.separator)
                pathBuilder.append(dayStr).append(File.separator)
            }

            UseSubfoldersBehaviour.YEAR_MONTH -> {
                pathBuilder.append(yearStr).append(File.separator)
                pathBuilder.append(monthStr).append(File.separator)
            }

            UseSubfoldersBehaviour.YEAR -> {
                pathBuilder.append(yearStr).append(File.separator)
            }

            else -> {}
        }
        return pathBuilder.append(documentFile.name).toString()
    }

    private fun showNotification(
        syncType: SyncType,
        numberOfFilesToUpload: Int
    ) {
        if (numberOfFilesToUpload == 0) return

        val contentText = when (syncType) {
            SyncType.PICTURE_UPLOADS -> R.string.uploader_upload_picture_upload_files
            SyncType.VIDEO_UPLOADS -> R.string.uploader_upload_video_upload_files
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
        }
        val notificationKey: String = when (syncType) {
            SyncType.PICTURE_UPLOADS -> SettingsActivity.NOTIFICATION_INTENT_PICTURES
            SyncType.VIDEO_UPLOADS -> SettingsActivity.NOTIFICATION_INTENT_VIDEOS
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
        }
    }

    private fun getFilesReadyToUpload(
        syncType: SyncType,
        sourcePath: String,
        lastSyncTimestamp: Long,
        currentTimestamp: Long,
    ): List<DocumentFile> {
        val sourceUri: Uri = sourcePath.toUri()
        val documentTree = DocumentFile.fromTreeUri(applicationContext, sourceUri)
        val arrayOfLocalFiles = documentTree?.listFiles() ?: arrayOf()

        val filteredList: List<DocumentFile> = arrayOfLocalFiles
            .sortedBy { it.lastModified() }
            .filter { it.lastModified() >= lastSyncTimestamp }
            .filter { it.lastModified() < currentTimestamp }
            .filter { MimetypeIconUtil.getBestMimeTypeByFilename(it.name).startsWith(syncType.prefixForType) }

        Timber.i("Last sync ${syncType.name}: ${Date(lastSyncTimestamp)}")
        Timber.i("CurrentTimestamp ${Date(currentTimestamp)}")
        Timber.i("${arrayOfLocalFiles.size} files found in folder: ${sourceUri.path}")
        Timber.i("${filteredList.size} files are ${syncType.name} and were taken after last sync")

        return filteredList
    }

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
        documentFile: DocumentFile,
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
            fileSize = documentFile.length(),
            status = TransferStatus.TRANSFER_QUEUED,
            localBehaviour = behavior,
            forceOverwrite = false,
            createdBy = createdByWorker,
            spaceId = spaceId,
        )

        return transferRepository.saveTransfer(ocTransfer)
    }

    companion object {
        const val AUTOMATIC_UPLOADS_WORKER = "AUTOMATIC_UPLOADS_WORKER"
        const val repeatInterval: Long = 15L
        val repeatIntervalTimeUnit: TimeUnit = TimeUnit.MINUTES
        private const val pictureUploadsNotificationId = 101
        private const val videoUploadsNotificationId = 102
    }
}
