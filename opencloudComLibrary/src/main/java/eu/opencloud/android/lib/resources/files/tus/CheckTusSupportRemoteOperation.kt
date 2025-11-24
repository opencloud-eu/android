package eu.opencloud.android.lib.resources.files.tus

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.nonwebdav.OptionsMethod
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import timber.log.Timber
import java.net.URL

/**
 * TUS capability detection (OPTIONS on uploads collection)
 * Returns true when the server advertises Tus-Version 1.0.0 and the 'creation' extension.
 */
class CheckTusSupportRemoteOperation(
    private val collectionUrlOverride: String? = null,
) : RemoteOperation<Boolean>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<Boolean> {
        return try {
            val base = (collectionUrlOverride ?: client.userFilesWebDavUri.toString()).trim()
            val candidates = linkedSetOf(base, base.ensureTrailingSlash())
            var lastResult: RemoteOperationResult<Boolean>? = null

            for (endpoint in candidates) {
                val options = OptionsMethod(URL(endpoint)).apply {
                    setRequestHeader(HttpConstants.TUS_RESUMABLE, HttpConstants.TUS_RESUMABLE_VERSION_1_0_0)
                }
                val status = client.executeHttpMethod(options)
                Timber.d("TUS OPTIONS %s - %d", endpoint, status)
                if (isSuccess(status)) {
                    val version = options.getResponseHeader(HttpConstants.TUS_VERSION) ?: ""
                    val extensions = options.getResponseHeader(HttpConstants.TUS_EXTENSION) ?: ""
                    val versionSupported = version.split(',').any { it.trim() == HttpConstants.TUS_RESUMABLE_VERSION_1_0_0 }
                    val creationSupported = extensions.split(',')
                        .map { it.trim().lowercase() }
                        .any { it == "creation" || it == "creation-with-upload" }

                    Timber.d("TUS supported (headers) at %s: version=%s extensions=%s", endpoint, version, extensions)

                    val supported = versionSupported && creationSupported
                    val result = RemoteOperationResult<Boolean>(ResultCode.OK).apply { data = supported }
                    if (supported) {
                        return result
                    }
                    lastResult = result
                } else if (status != 0) {
                    lastResult = RemoteOperationResult<Boolean>(options).apply { data = false }
                }
            }

            lastResult ?: RemoteOperationResult<Boolean>(ResultCode.OK).apply { data = false }
        } catch (e: Exception) {
            val result = RemoteOperationResult<Boolean>(e)
            Timber.w(e, "TUS detection failed, assuming unsupported")
            result.apply { data = false }
        }

    private fun isSuccess(status: Int) =
        status.isOneOf(HttpConstants.HTTP_NO_CONTENT, HttpConstants.HTTP_OK)

    private fun String.ensureTrailingSlash(): String =
        if (this.endsWith("/")) this else "$this/"
}
