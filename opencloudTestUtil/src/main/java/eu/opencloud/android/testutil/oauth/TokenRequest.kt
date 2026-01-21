/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2021 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.opencloud.android.testutil.oauth

import eu.opencloud.android.domain.authentication.oauth.model.TokenRequest
import eu.opencloud.android.testutil.OC_SECURE_BASE_URL
import eu.opencloud.android.testutil.OC_CLIENT_AUTH
import eu.opencloud.android.testutil.OC_REDIRECT_URI
import eu.opencloud.android.testutil.OC_REFRESH_TOKEN
import eu.opencloud.android.testutil.OC_SCOPE
import eu.opencloud.android.testutil.OC_TOKEN_ENDPOINT

val OC_TOKEN_REQUEST_REFRESH = TokenRequest.RefreshToken(
    baseUrl = OC_SECURE_BASE_URL,
    tokenEndpoint = OC_TOKEN_ENDPOINT,
    clientAuth = OC_CLIENT_AUTH,
    scope = OC_SCOPE,
    refreshToken = OC_REFRESH_TOKEN
)

val OC_TOKEN_REQUEST_ACCESS = TokenRequest.AccessToken(
    baseUrl = OC_SECURE_BASE_URL,
    tokenEndpoint = OC_TOKEN_ENDPOINT,
    clientAuth = OC_CLIENT_AUTH,
    scope = OC_SCOPE,
    authorizationCode = "4uth0r1z4t10nC0d3",
    redirectUri = OC_REDIRECT_URI,
    codeVerifier = "A high-entropy cryptographic random STRING using the unreserved characters"
)

/**
 * Test fixtures for public PKCE clients (RFC 7636).
 * Public clients MUST NOT send Authorization header during token exchange.
 */
val OC_TOKEN_REQUEST_REFRESH_PUBLIC_CLIENT = TokenRequest.RefreshToken(
    baseUrl = OC_SECURE_BASE_URL,
    tokenEndpoint = OC_TOKEN_ENDPOINT,
    clientAuth = "", // Empty for public clients per RFC 7636
    scope = OC_SCOPE,
    refreshToken = OC_REFRESH_TOKEN
)

val OC_TOKEN_REQUEST_ACCESS_PUBLIC_CLIENT = TokenRequest.AccessToken(
    baseUrl = OC_SECURE_BASE_URL,
    tokenEndpoint = OC_TOKEN_ENDPOINT,
    clientAuth = "", // Empty for public clients per RFC 7636
    scope = OC_SCOPE,
    authorizationCode = "4uth0r1z4t10nC0d3",
    redirectUri = OC_REDIRECT_URI,
    codeVerifier = "A high-entropy cryptographic random STRING using the unreserved characters"
)
