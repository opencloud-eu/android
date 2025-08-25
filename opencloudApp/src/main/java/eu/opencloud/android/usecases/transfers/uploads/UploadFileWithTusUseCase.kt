/**
 * openCloud Android client application
 */
package eu.opencloud.android.usecases.transfers.uploads

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.workers.TusUploadWorker
import eu.opencloud.android.workers.RemoveSourceFileWorker
import eu.opencloud.android.workers.UploadFileFromContentUriWorker
import eu.opencloud.android.workers.UploadFileFromFileSystemWorker

class UploadFileWithTusUseCase(
    private val workManager: WorkManager
) : BaseUseCase<Unit, UploadFileWithTusUseCase.Params>() {

    override fun run(params: Params) {
        val input = workDataOf(
            TusUploadWorker.KEY_PARAM_ACCOUNT_NAME to params.accountName,
            TusUploadWorker.KEY_PARAM_UPLOAD_PATH to params.uploadPath,
            TusUploadWorker.KEY_PARAM_UPLOAD_ID to params.uploadIdInStorageManager,
            TusUploadWorker.KEY_PARAM_CONTENT_URI to params.contentUri,
            TusUploadWorker.KEY_PARAM_LOCAL_PATH to params.localPath,
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val tusWork = OneTimeWorkRequestBuilder<TusUploadWorker>()
            .setInputData(input)
            .setConstraints(constraints)
            .addTag(params.accountName)
            .addTag(params.uploadIdInStorageManager.toString())
            .build()

        val behavior = UploadBehavior.fromString(params.behavior)

        if (behavior == UploadBehavior.MOVE) {
            // Chain deletion of the source after successful upload
            val inputDataRemoveSourceFileWorker = when {
                !params.contentUri.isNullOrBlank() -> {
                    workDataOf(UploadFileFromContentUriWorker.KEY_PARAM_CONTENT_URI to params.contentUri)
                }
                !params.localPath.isNullOrBlank() -> {
                    workDataOf(UploadFileFromFileSystemWorker.KEY_PARAM_LOCAL_PATH to params.localPath)
                }
                else -> null
            }

            if (inputDataRemoveSourceFileWorker != null) {
                val removeSourceFileWorker = OneTimeWorkRequestBuilder<RemoveSourceFileWorker>()
                    .setInputData(inputDataRemoveSourceFileWorker)
                    .build()

                workManager
                    .beginWith(tusWork)
                    .then(removeSourceFileWorker)
                    .enqueue()
            } else {
                // No valid source to remove, just enqueue the upload
                workManager.enqueue(tusWork)
            }
        } else {
            // COPY behavior or others: just enqueue the upload
            workManager.enqueue(tusWork)
        }
    }

    data class Params(
        val accountName: String,
        val uploadPath: String,
        val uploadIdInStorageManager: Long,
        val behavior: String,
        val contentUri: String? = null,
        val localPath: String? = null,
    )
}
