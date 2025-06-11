/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2020 ownCloud GmbH.
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
package eu.opencloud.android.data.oauth.repository

import eu.opencloud.android.data.oauth.datasources.RemoteOAuthDataSource
import eu.opencloud.android.domain.authentication.oauth.OAuthRepository
import eu.opencloud.android.domain.authentication.oauth.model.ClientRegistrationInfo
import eu.opencloud.android.domain.authentication.oauth.model.ClientRegistrationRequest
import eu.opencloud.android.domain.authentication.oauth.model.OIDCServerConfiguration
import eu.opencloud.android.domain.authentication.oauth.model.TokenRequest
import eu.opencloud.android.domain.authentication.oauth.model.TokenResponse

class OCOAuthRepository(
    private val oidcRemoteOAuthDataSource: RemoteOAuthDataSource,
) : OAuthRepository {

    override fun performOIDCDiscovery(baseUrl: String): OIDCServerConfiguration =
        oidcRemoteOAuthDataSource.performOIDCDiscovery(baseUrl)

    override fun performTokenRequest(tokenRequest: TokenRequest): TokenResponse =
        oidcRemoteOAuthDataSource.performTokenRequest(tokenRequest)

    override fun registerClient(clientRegistrationRequest: ClientRegistrationRequest): ClientRegistrationInfo =
        oidcRemoteOAuthDataSource.registerClient(clientRegistrationRequest)
}
