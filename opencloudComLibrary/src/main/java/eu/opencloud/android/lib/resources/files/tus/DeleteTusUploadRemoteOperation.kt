package eu.opencloud.android.lib.resources.files.tus

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.nonwebdav.DeleteMethod
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import timber.log.Timber
import java.net.URL

/**
 * TUS Delete Upload operation (DELETE)
 * Deletes an existing upload resource.
 */
class DeleteTusUploadRemoteOperation(
    private val uploadUrl: String,
) : RemoteOperation<Unit>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<Unit> =
        try {
            val deleteMethod = DeleteMethod(URL(uploadUrl)).apply {
                setRequestHeader(HttpConstants.TUS_RESUMABLE, HttpConstants.TUS_RESUMABLE_VERSION_1_0_0)
            }

            val status = client.executeHttpMethod(deleteMethod)
            Timber.d("Delete TUS upload - $status${if (!isSuccess(status)) "(FAIL)" else ""}")

            if (isSuccess(status)) RemoteOperationResult<Unit>(ResultCode.OK).apply { data = Unit }
            else RemoteOperationResult<Unit>(deleteMethod)
        } catch (e: Exception) {
            val result = RemoteOperationResult<Unit>(e)
            Timber.e(e, "Delete TUS upload failed")
            result
        }

    private fun isSuccess(status: Int): Boolean =
        status.isOneOf(HttpConstants.HTTP_NO_CONTENT, HttpConstants.HTTP_OK)
}
