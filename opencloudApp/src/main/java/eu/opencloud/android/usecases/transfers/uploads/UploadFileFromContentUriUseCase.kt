/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
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

import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.workers.RemoveSourceFileWorker
import eu.opencloud.android.workers.UploadFileFromContentUriWorker
import timber.log.Timber

class UploadFileFromContentUriUseCase(
    private val workManager: WorkManager
) : BaseUseCase<Unit, UploadFileFromContentUriUseCase.Params>() {

    override fun run(params: Params) {
        val inputDataUploadFileFromContentUriWorker = Data.Builder()
            .putString(UploadFileFromContentUriWorker.KEY_PARAM_ACCOUNT_NAME, params.accountName)
            .putString(UploadFileFromContentUriWorker.KEY_PARAM_BEHAVIOR, params.behavior)
            .putString(UploadFileFromContentUriWorker.KEY_PARAM_CONTENT_URI, params.contentUri.toString())
            .putString(UploadFileFromContentUriWorker.KEY_PARAM_UPLOAD_PATH, params.uploadPath)
            .putLong(UploadFileFromContentUriWorker.KEY_PARAM_UPLOAD_ID, params.uploadIdInStorageManager)
            .apply {
                params.lastModifiedInSeconds?.let {
                    putString(UploadFileFromContentUriWorker.KEY_PARAM_LAST_MODIFIED, it)
                }
            }
            .build()
        val inputDataRemoveSourceFileWorker = Data.Builder()
            .putString(UploadFileFromContentUriWorker.KEY_PARAM_CONTENT_URI, params.contentUri.toString())
            .build()

        val networkRequired = if (params.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkRequired)
            .setRequiresCharging(params.chargingOnly)
            .build()

        val uploadFileFromContentUriWorker = OneTimeWorkRequestBuilder<UploadFileFromContentUriWorker>()
            .setInputData(inputDataUploadFileFromContentUriWorker)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.SECONDS
            )
            .addTag(params.accountName)
            .addTag(params.uploadIdInStorageManager.toString())
            .build()

        // Use unique work name based on upload ID to prevent concurrent uploads of same file
        val uniqueWorkName = "upload_content_uri_${params.uploadIdInStorageManager}"

        val behavior = UploadBehavior.fromString(params.behavior)
        if (behavior == UploadBehavior.MOVE) {
            val removeSourceFileWorker = OneTimeWorkRequestBuilder<RemoveSourceFileWorker>()
                .setInputData(inputDataRemoveSourceFileWorker)
                .build()
            workManager.beginUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP, // Keep existing work to prevent duplicate uploads
                uploadFileFromContentUriWorker
            ).then(removeSourceFileWorker) // File is already uploaded, so the original one can be removed if the behaviour is MOVE
            .enqueue()
        } else {
            workManager.enqueueUniqueWork(
                uniqueWorkName,
                ExistingWorkPolicy.KEEP, // Keep existing work to prevent duplicate uploads
                uploadFileFromContentUriWorker
            )
        }

        Timber.i("Plain upload of ${params.contentUri.path} has been enqueued with unique work name: $uniqueWorkName")
    }

    data class Params(
        val accountName: String,
        val contentUri: Uri,
        val lastModifiedInSeconds: String?,
        val behavior: String,
        val uploadPath: String,
        val uploadIdInStorageManager: Long,
        val wifiOnly: Boolean,
        val chargingOnly: Boolean,
    )
}
