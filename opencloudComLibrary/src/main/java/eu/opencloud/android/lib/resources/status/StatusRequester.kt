/* openCloud Android Library is available under MIT license
*   Copyright (C) 2021 ownCloud GmbH.
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

package eu.opencloud.android.lib.resources.status

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.nonwebdav.GetMethod
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.status.HttpScheme.HTTPS_SCHEME
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

internal class StatusRequester {

    /**
     * This function is ment to detect if a redirect from a secure to an unsecure connection
     * was made. If only connections from unsecure connections to unsecure connections were made
     * this function should not return true, because if the whole redirect chain was unsecure
     * we assume it was a debug setup.
     */
    fun isRedirectedToNonSecureConnection(
        redirectedToNonSecureLocationBefore: Boolean,
        baseUrl: String,
        redirectedUrl: String
    ) = redirectedToNonSecureLocationBefore ||
            (baseUrl.startsWith(HTTPS_SCHEME) &&
                    !redirectedUrl.startsWith(HTTPS_SCHEME))

    fun updateLocationWithRedirectPath(oldLocation: String, redirectedLocation: String): String =
        /** Redirection with different endpoint.
         * When asking for server.com/status.php and redirected to different.one/, we need to ask different.one/status.php
         */
        if (redirectedLocation.endsWith('/')) {
            redirectedLocation.trimEnd('/') + OpenCloudClient.STATUS_PATH
        } else if (!redirectedLocation.startsWith("/")) {
                redirectedLocation
        } else {
            val oldLocationURL = URL(oldLocation)
            URL(oldLocationURL.protocol, oldLocationURL.host, oldLocationURL.port, redirectedLocation).toString()
        }

    private fun getGetMethod(url: String): GetMethod =
        GetMethod(URL(url)).apply {
            setReadTimeout(TRY_CONNECTION_TIMEOUT, TimeUnit.SECONDS)
            setConnectionTimeout(TRY_CONNECTION_TIMEOUT, TimeUnit.SECONDS)
        }

    data class RequestResult(
        val getMethod: GetMethod,
        val status: Int,
        val lastLocation: String
    )

    fun request(baseLocation: String, client: OpenCloudClient): RequestResult {
        val currentLocation = baseLocation + OpenCloudClient.STATUS_PATH
        var status: Int
        val getMethod = getGetMethod(currentLocation)

        getMethod.followPermanentRedirects = true
        status = client.executeHttpMethod(getMethod)

        return RequestResult(getMethod, status, getMethod.getFinalUrl().toString())
    }

    private fun Int.isSuccess() = this == HttpConstants.HTTP_OK

    fun handleRequestResult(
        requestResult: RequestResult,
        baseUrl: String
    ): RemoteOperationResult<RemoteServerInfo> {
        val respJSON = JSONObject(requestResult.getMethod.getResponseBodyAsString())
        return if (!requestResult.status.isSuccess()) {
            RemoteOperationResult(requestResult.getMethod)
        } else if (!respJSON.getBoolean(NODE_INSTALLED)) {
            RemoteOperationResult(RemoteOperationResult.ResultCode.INSTANCE_NOT_CONFIGURED)
        } else {
            val ocVersion = OpenCloudVersion(respJSON.getString(NODE_VERSION))
            // the version object will be returned even if the version is invalid, no error code;
            // every app will decide how to act if (ocVersion.isVersionValid() == false)
            val result: RemoteOperationResult<RemoteServerInfo> =
                if (baseUrl.startsWith(HTTPS_SCHEME)) RemoteOperationResult(RemoteOperationResult.ResultCode.OK_SSL)
                else RemoteOperationResult(RemoteOperationResult.ResultCode.OK_NO_SSL)
            val finalUrl = URL(requestResult.lastLocation)
            val finalBaseUrl = URL(
                finalUrl.protocol,
                finalUrl.host,
                finalUrl.port,
                finalUrl.file.dropLastWhile { it != '/' }.trimEnd('/')
            )

            result.data = RemoteServerInfo(
                openCloudVersion = ocVersion,
                baseUrl = finalBaseUrl.toString(),
                isSecureConnection = finalBaseUrl.protocol.startsWith(HTTPS_SCHEME)
            )
            result
        }
    }

    companion object {
        /**
         * Maximum time to wait for a response from the server when the connection is being tested,
         * in milliseconds.
         */
        private const val TRY_CONNECTION_TIMEOUT = 5_000L
        private const val NODE_INSTALLED = "installed"
        private const val NODE_VERSION = "productversion"
    }
}
