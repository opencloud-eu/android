/* openCloud Android Library is available under MIT license
 *
 *   Copyright (C) 2024 ownCloud GmbH.
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
 */

package eu.opencloud.android.lib.resources.oauth.params

import eu.opencloud.android.lib.common.http.HttpConstants
import okhttp3.FormBody
import okhttp3.RequestBody

sealed class TokenRequestParams(
    val tokenEndpoint: String,
    val clientAuth: String,
    val grantType: String,
    val scope: String,
    val clientId: String?,
    val clientSecret: String?,
) {
    abstract fun toRequestBody(): RequestBody

    class Authorization(
        tokenEndpoint: String,
        clientAuth: String,
        grantType: String,
        scope: String,
        clientId: String?,
        clientSecret: String?,
        val authorizationCode: String,
        val redirectUri: String,
        val codeVerifier: String,
    ) : TokenRequestParams(tokenEndpoint, clientAuth, grantType, scope, clientId, clientSecret) {

        override fun toRequestBody(): RequestBody =
            FormBody.Builder().apply {
                add(HttpConstants.OAUTH_HEADER_AUTHORIZATION_CODE, authorizationCode)
                add(HttpConstants.OAUTH_HEADER_GRANT_TYPE, grantType)
                add(HttpConstants.OAUTH_HEADER_REDIRECT_URI, redirectUri)
                add(HttpConstants.OAUTH_HEADER_CODE_VERIFIER, codeVerifier)
                add(HttpConstants.OAUTH_HEADER_SCOPE, scope)
                if (clientId != null) add(HttpConstants.OAUTH_BODY_CLIENT_ID, clientId)
                if (clientSecret != null) add(HttpConstants.OAUTH_BODY_CLIENT_SECRET, clientSecret)
            }.build()

    }

    class RefreshToken(
        tokenEndpoint: String,
        clientAuth: String,
        grantType: String,
        scope: String,
        clientId: String?,
        clientSecret: String?,
        val refreshToken: String? = null
    ) : TokenRequestParams(tokenEndpoint, clientAuth, grantType, scope, clientId, clientSecret) {

        override fun toRequestBody(): RequestBody =
            FormBody.Builder().apply {
                add(HttpConstants.OAUTH_HEADER_GRANT_TYPE, grantType)
                add(HttpConstants.OAUTH_HEADER_SCOPE, scope)
                if (!refreshToken.isNullOrBlank()) add(HttpConstants.OAUTH_HEADER_REFRESH_TOKEN, refreshToken)
                if (clientId != null) add(HttpConstants.OAUTH_BODY_CLIENT_ID, clientId)
                if (clientSecret != null) add(HttpConstants.OAUTH_BODY_CLIENT_SECRET, clientSecret)
            }.build()

    }
}
