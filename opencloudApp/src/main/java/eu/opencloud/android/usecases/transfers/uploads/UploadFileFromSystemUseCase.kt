/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 * @author Aitor Ballesteros Pavón
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
import eu.opencloud.android.workers.RemoveSourceFileWorker
import eu.opencloud.android.workers.UploadFileFromContentUriWorker
import eu.opencloud.android.workers.UploadFileFromFileSystemWorker
import timber.log.Timber

class UploadFileFromSystemUseCase(
    private val workManager: WorkManager
) : BaseUseCase<Unit, UploadFileFromSystemUseCase.Params>() {

    override fun run(params: Params) {
        val inputDataUploadFileFromFileSystemWorker = Data.Builder()
            .putString(UploadFileFromFileSystemWorker.KEY_PARAM_ACCOUNT_NAME, params.accountName)
            .putString(UploadFileFromFileSystemWorker.KEY_PARAM_BEHAVIOR, params.behavior)
            .putString(UploadFileFromFileSystemWorker.KEY_PARAM_LOCAL_PATH, params.localPath)
            .putString(UploadFileFromFileSystemWorker.KEY_PARAM_UPLOAD_PATH, params.uploadPath)
            .putLong(UploadFileFromFileSystemWorker.KEY_PARAM_UPLOAD_ID, params.uploadIdInStorageManager)
            .apply {
                params.lastModifiedInSeconds?.let {
                    putString(UploadFileFromFileSystemWorker.KEY_PARAM_LAST_MODIFIED, it)
                }
            }
            .build()
        val inputDataRemoveSourceFileWorker = Data.Builder().apply {
            params.sourcePath?.let {
                putString(UploadFileFromContentUriWorker.KEY_PARAM_CONTENT_URI, it)
            }
        }.build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadFileFromSystemWorker = OneTimeWorkRequestBuilder<UploadFileFromFileSystemWorker>()
            .setInputData(inputDataUploadFileFromFileSystemWorker)
            .setConstraints(constraints)
            .addTag(params.accountName)
            .addTag(params.uploadIdInStorageManager.toString())
            .build()

        // Use unique work name based on upload ID to prevent concurrent uploads of same file
        val uniqueWorkName = "upload_file_system_${params.uploadIdInStorageManager}"

        val behavior = UploadBehavior.fromString(params.behavior)
        if (behavior == UploadBehavior.MOVE && params.sourcePath != null) {
            val removeSourceFileWorker = OneTimeWorkRequestBuilder<RemoveSourceFileWorker>()
                .setInputData(inputDataRemoveSourceFileWorker)
                .build()
            workManager.beginUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP, // Keep existing work to prevent duplicate uploads
                uploadFileFromSystemWorker
            ).then(removeSourceFileWorker) // File is already uploaded, so the original one can be removed if the behaviour is MOVE
            .enqueue()
        } else {
            workManager.enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP, // Keep existing work to prevent duplicate uploads
                uploadFileFromSystemWorker
            )
        }

        Timber.i("Plain upload of ${params.localPath} has been enqueued with unique work name: $uniqueWorkName")
    }

    data class Params(
        val accountName: String,
        val localPath: String,
        val lastModifiedInSeconds: String?,
        val behavior: String,
        val uploadPath: String,
        val uploadIdInStorageManager: Long,
        val sourcePath: String? = null,
    )
}
