/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author Jorge Aguado Recio
 *
 * Copyright (C) 2024 ownCloud GmbH.
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

package eu.opencloud.android.usecases.transfers.uploads

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.domain.transfers.model.TransferStatus
import eu.opencloud.android.domain.transfers.model.UploadEnqueuedBy
import eu.opencloud.android.workers.UploadFileFromFileSystemWorker
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * Use case to upload an update for a file in server.
 *
 * We use this to upload files from Conflicts - Keep local (Overwrite file in server)
 *
 * It stores the upload in the database and then enqueue a new worker to upload the single file
 */
class UploadFileInConflictUseCase(
    private val workManager: WorkManager,
    private val transferRepository: TransferRepository,
) : BaseUseCase<UUID?, UploadFileInConflictUseCase.Params>() {

    override fun run(params: Params): UUID? {
        val localFile = File(params.localPath)

        if (!localFile.exists()) {
            Timber.w("Upload of ${params.localPath} won't be enqueued. We were not able to find it in the local storage")
            return null
        }

        Timber.d("Upload file in conflict params: ${params.accountName} | ${params.localPath} | ${params.uploadFolderPath} ")
        val uploadId = storeInUploadsDatabase(
            localFile = localFile,
            uploadPath = params.uploadFolderPath.plus(localFile.name),
            accountName = params.accountName,
            spaceId = params.spaceId,
        )

        return enqueueSingleUpload(
            localPath = localFile.absolutePath,
            uploadPath = params.uploadFolderPath.plus(localFile.name),
            lastModifiedInSeconds = localFile.lastModified().div(1_000).toString(),
            accountName = params.accountName,
            uploadIdInStorageManager = uploadId,
            currentRemoteEtag = params.currentRemoteEtag,
        )
    }

    private fun storeInUploadsDatabase(
        localFile: File,
        uploadPath: String,
        accountName: String,
        spaceId: String?,
    ): Long {
        val ocTransfer = OCTransfer(
            localPath = localFile.absolutePath,
            remotePath = uploadPath,
            accountName = accountName,
            fileSize = localFile.length(),
            status = TransferStatus.TRANSFER_QUEUED,
            localBehaviour = UploadBehavior.COPY,
            forceOverwrite = true,
            createdBy = UploadEnqueuedBy.ENQUEUED_BY_USER,
            spaceId = spaceId,
        )

        return transferRepository.saveTransfer(ocTransfer).also {
            Timber.i("Upload of $uploadPath has been stored in the uploads database with id: $it")
        }
    }

    private fun enqueueSingleUpload(
        accountName: String,
        localPath: String,
        lastModifiedInSeconds: String,
        uploadIdInStorageManager: Long,
        uploadPath: String,
        currentRemoteEtag: String?,
    ): UUID {
        val inputDataBuilder = Data.Builder()
            .putString(UploadFileFromFileSystemWorker.KEY_PARAM_ACCOUNT_NAME, accountName)
            .putString(UploadFileFromFileSystemWorker.KEY_PARAM_BEHAVIOR, UploadBehavior.COPY.name)
            .putString(UploadFileFromFileSystemWorker.KEY_PARAM_LOCAL_PATH, localPath)
            .putString(UploadFileFromFileSystemWorker.KEY_PARAM_LAST_MODIFIED, lastModifiedInSeconds)
            .putString(UploadFileFromFileSystemWorker.KEY_PARAM_UPLOAD_PATH, uploadPath)
            .putLong(UploadFileFromFileSystemWorker.KEY_PARAM_UPLOAD_ID, uploadIdInStorageManager)
            .putBoolean(UploadFileFromFileSystemWorker.KEY_PARAM_REMOVE_LOCAL, false)

        currentRemoteEtag?.let {
            inputDataBuilder.putString(UploadFileFromFileSystemWorker.KEY_PARAM_OVERWRITE_ETAG, it)
        }
        val inputData = inputDataBuilder.build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadFileFromContentUriWorker = OneTimeWorkRequestBuilder<UploadFileFromFileSystemWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(accountName)
            .addTag(uploadIdInStorageManager.toString())
            .build()

        // Use unique work name based on upload ID to prevent concurrent uploads of same file
        val uniqueWorkName = "upload_conflict_${uploadIdInStorageManager}"
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP, // Keep existing work to prevent duplicate uploads
            uploadFileFromContentUriWorker
        )
        Timber.i("Plain upload of $localPath has been enqueued with unique work name: $uniqueWorkName")

        return uploadFileFromContentUriWorker.id
    }

    data class Params(
        val accountName: String,
        val localPath: String,
        val uploadFolderPath: String,
        val spaceId: String?,
        val currentRemoteEtag: String? = null,
    )
}
