package eu.opencloud.android.lib.resources.files.tus

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.nonwebdav.HeadMethod
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import timber.log.Timber
import java.net.URL

/**
 * TUS Get Upload Offset operation (HEAD)
 * Returns the current Upload-Offset for a given upload resource URL.
 */
class GetTusUploadOffsetRemoteOperation(
    private val uploadUrl: String,
) : RemoteOperation<Long>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<Long> =
        try {
            val headMethod = HeadMethod(URL(uploadUrl)).apply {
                setRequestHeader(HttpConstants.TUS_RESUMABLE, HttpConstants.TUS_RESUMABLE_VERSION_1_0_0)
            }

            val status = client.executeHttpMethod(headMethod)
            Timber.d("Get TUS upload offset - $status${if (!isSuccess(status)) "(FAIL)" else ""}")

            if (isSuccess(status)) {
                val offsetHeader = headMethod.getResponseHeader(HttpConstants.UPLOAD_OFFSET)
                val offset = offsetHeader?.toLongOrNull()
                if (offset != null) {
                    RemoteOperationResult<Long>(ResultCode.OK).apply { data = offset }
                } else {
                    RemoteOperationResult<Long>(headMethod).apply { data = -1L }
                }
            } else {
                RemoteOperationResult<Long>(headMethod).apply { data = -1L }
            }
        } catch (e: Exception) {
            val result = RemoteOperationResult<Long>(e)
            Timber.e(e, "Get TUS upload offset failed")
            result
        }

    private fun isSuccess(status: Int) =
        status.isOneOf(HttpConstants.HTTP_OK, HttpConstants.HTTP_NO_CONTENT)
}
