/* openCloud Android Library is available under MIT license
 *   @author masensio
 *   @author David A. Velasco
 *   @author David González Verdugo
 *   @author Fernando Sanz Velasco
 *   Copyright (C) 2021 ownCloud GmbH
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 *   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 *   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 *
 */

package eu.opencloud.android.lib.resources.shares

import android.net.Uri
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.HttpConstants.PARAM_FORMAT
import eu.opencloud.android.lib.common.http.HttpConstants.VALUE_FORMAT
import eu.opencloud.android.lib.common.http.methods.nonwebdav.DeleteMethod
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import timber.log.Timber
import java.net.URL

/**
 * Remove a share
 *
 * @author masensio
 * @author David A. Velasco
 * @author David González Verdugo
 * @author Fernando Sanz Velasco
 *
 * @param remoteShareId Share ID
 */
class RemoveRemoteShareOperation(private val remoteShareId: String) : RemoteOperation<Unit>() {

    private fun buildRequestUri(baseUri: Uri) =
        baseUri.buildUpon()
            .appendEncodedPath(OCS_ROUTE)
            .appendEncodedPath(remoteShareId)
            .appendQueryParameter(PARAM_FORMAT, VALUE_FORMAT)
            .build()

    private fun onResultUnsuccessful(
        method: DeleteMethod,
        response: String?,
        status: Int
    ): RemoteOperationResult<Unit> {
        Timber.e("Failed response while removing share ")
        if (response != null) {
            Timber.e("*** status code: $status; response message: $response")
        } else {
            Timber.e("*** status code: $status")
        }
        return RemoteOperationResult(method)
    }

    private fun onRequestSuccessful(response: String?): RemoteOperationResult<Unit> {
        val result = RemoteOperationResult<Unit>(RemoteOperationResult.ResultCode.OK)
        Timber.d("Successful response: $response")
        Timber.d("*** Unshare link completed ")
        return result
    }

    override fun run(client: OpenCloudClient): RemoteOperationResult<Unit> {
        val requestUri = buildRequestUri(client.baseUri)

        val deleteMethod = DeleteMethod(URL(requestUri.toString())).apply {
            addRequestHeader(OCS_API_HEADER, OCS_API_HEADER_VALUE)
        }

        return try {
            val status = client.executeHttpMethod(deleteMethod)
            val response = deleteMethod.getResponseBodyAsString()

            if (isSuccess(status)) {
                onRequestSuccessful(response)
            } else {
                onResultUnsuccessful(deleteMethod, response, status)
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while unshare link")
            RemoteOperationResult(e)
        }
    }

    private fun isSuccess(status: Int): Boolean = status == HttpConstants.HTTP_OK

    companion object {
        // OCS Route
        private const val OCS_ROUTE = "ocs/v2.php/apps/files_sharing/api/v1/shares"
    }
}
