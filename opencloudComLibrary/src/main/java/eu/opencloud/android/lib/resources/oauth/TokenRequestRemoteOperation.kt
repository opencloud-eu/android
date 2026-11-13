/* openCloud Android Library is available under MIT license
 *
 *   @author Abel García de Prada
 *
 *   Copyright (C) 2020 ownCloud GmbH.
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
package eu.opencloud.android.lib.resources.oauth

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants.AUTHORIZATION_HEADER
import eu.opencloud.android.lib.common.http.HttpConstants.HTTP_OK
import eu.opencloud.android.lib.common.http.methods.nonwebdav.PostMethod
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.oauth.params.TokenRequestParams
import eu.opencloud.android.lib.resources.oauth.responses.TokenResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import timber.log.Timber
import java.net.URL

/**
 * Perform token request
 *
 * @author Abel García de Prada
 */
class TokenRequestRemoteOperation(
    private val tokenRequestParams: TokenRequestParams
) : RemoteOperation<TokenResponse>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<TokenResponse> {
        try {
            val requestBody = tokenRequestParams.toRequestBody()

            val postMethod = PostMethod(URL(tokenRequestParams.tokenEndpoint), requestBody)

            if (tokenRequestParams.clientAuth.isNotEmpty()) {
                postMethod.addRequestHeader(AUTHORIZATION_HEADER, tokenRequestParams.clientAuth)
            }

            val status = client.executeHttpMethod(postMethod)

            val responseBody = postMethod.getResponseBodyAsString()

            return if (status == HTTP_OK && responseBody != null) {
                Timber.d("Successful response $responseBody")

                // Parse the response
                val moshi: Moshi = Moshi.Builder().build()
                val jsonAdapter: JsonAdapter<TokenResponse> = moshi.adapter(TokenResponse::class.java)
                val tokenResponse: TokenResponse? = jsonAdapter.fromJson(responseBody)
                Timber.d("Get tokens completed and parsed to $tokenResponse")

                RemoteOperationResult<TokenResponse>(RemoteOperationResult.ResultCode.OK).apply {
                    data = tokenResponse
                }

            } else {
                Timber.e("Failed response while getting tokens from the server status code: $status; response message: $responseBody")
                if (tokenRequestParams is TokenRequestParams.RefreshToken && isRefreshTokenRejected(responseBody)) {
                    // The IdP says the refresh token (or the client) is not valid anymore: it will never work again
                    RemoteOperationResult<TokenResponse>(RemoteOperationResult.ResultCode.OAUTH2_ERROR)
                } else {
                    RemoteOperationResult<TokenResponse>(postMethod)
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Exception while getting tokens")
            return RemoteOperationResult<TokenResponse>(e)

        }
    }

    companion object {
        private val OAUTH_ERROR_REGEX = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"")

        /** Error codes of RFC 6749 section 5.2 that no retry can fix, the user has to authorize again. */
        private val REJECTED_REFRESH_TOKEN_ERRORS = setOf("invalid_grant", "invalid_client", "unauthorized_client", "access_denied")

        /**
         * @return true if the body of a failed token request carries an OAuth error telling that the refresh token
         * was rejected for good. Transient failures (5xx, proxies, timeouts...) do not match.
         */
        internal fun isRefreshTokenRejected(responseBody: String?): Boolean =
            responseBody?.let { OAUTH_ERROR_REGEX.find(it)?.groupValues?.get(1) } in REJECTED_REFRESH_TOKEN_ERRORS
    }
}
