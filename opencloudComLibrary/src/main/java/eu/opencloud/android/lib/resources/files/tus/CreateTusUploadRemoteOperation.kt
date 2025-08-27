package eu.opencloud.android.lib.resources.files.tus

import android.util.Base64
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.nonwebdav.PostMethod
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URL

/**
 * TUS Create Upload operation (POST)
 * Creates a new upload resource and returns its Location URL in the result data.
 */
class CreateTusUploadRemoteOperation(
    private val uploadLength: Long? = null,
    private val deferLength: Boolean = false,
    private val metadata: Map<String, String> = emptyMap(),
) : RemoteOperation<String>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<String> =
        try {
            val postBody = ByteArray(0).toRequestBody(null)
            val collectionUrl = client.uploadsWebDavUri.toString()

            val postMethod = PostMethod(URL(collectionUrl), postBody).apply {
                setRequestHeader(HttpConstants.TUS_RESUMABLE, HttpConstants.TUS_RESUMABLE_VERSION_1_0_0)
                uploadLength?.let { setRequestHeader(HttpConstants.UPLOAD_LENGTH, it.toString()) }
                    ?: run {
                        if (deferLength) setRequestHeader(HttpConstants.UPLOAD_DEFER_LENGTH, "1")
                    }
                if (metadata.isNotEmpty()) {
                    setRequestHeader(HttpConstants.UPLOAD_METADATA, encodeTusMetadata(metadata))
                }
            }

            val status = client.executeHttpMethod(postMethod)
            Timber.d("Create TUS upload - $status${if (!isSuccess(status)) "(FAIL)" else ""}")

            if (isSuccess(status)) {
                val locationHeader = postMethod.getResponseHeader(HttpConstants.LOCATION_HEADER)
                    ?: postMethod.getResponseHeader(HttpConstants.LOCATION_HEADER_LOWER)
                val base = URL(postMethod.getFinalUrl().toString())
                val resolved = resolveLocationToAbsolute(locationHeader, base)
                if (!resolved.isNullOrBlank()) {
                    RemoteOperationResult<String>(ResultCode.OK).apply { data = resolved }
                } else {
                    RemoteOperationResult<String>(postMethod).apply { data = "" }
                }
            } else RemoteOperationResult<String>(postMethod).apply { data = "" }
        } catch (e: Exception) {
            val result = RemoteOperationResult<String>(e)
            Timber.e(e, "Create TUS upload failed")
            result
        }

    private fun isSuccess(status: Int) =
        status.isOneOf(HttpConstants.HTTP_CREATED, HttpConstants.HTTP_OK)

    private fun encodeTusMetadata(metadata: Map<String, String>): String =
        metadata.entries.joinToString(",") { (key, value) ->
            val encoded = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "$key $encoded"
        }

    private fun resolveLocationToAbsolute(location: String?, base: URL): String? {
        if (location.isNullOrBlank()) return null
        return try {
            URL(base, location).toString()
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve Location header: %s", location)
            null
        }
    }
}
