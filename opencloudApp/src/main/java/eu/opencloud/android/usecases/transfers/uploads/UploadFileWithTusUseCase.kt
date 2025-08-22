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
import eu.opencloud.android.workers.TusUploadWorker

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

        val work = OneTimeWorkRequestBuilder<TusUploadWorker>()
            .setInputData(input)
            .setConstraints(constraints)
            .addTag(params.accountName)
            .addTag(params.uploadIdInStorageManager.toString())
            .build()

        workManager.enqueue(work)
    }

    data class Params(
        val accountName: String,
        val uploadPath: String,
        val uploadIdInStorageManager: Long,
        val contentUri: String? = null,
        val localPath: String? = null,
    )
}
