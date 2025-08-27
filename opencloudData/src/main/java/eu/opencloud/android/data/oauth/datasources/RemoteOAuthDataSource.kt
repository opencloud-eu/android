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
package eu.opencloud.android.data.oauth.datasources

import eu.opencloud.android.domain.authentication.oauth.model.ClientRegistrationInfo
import eu.opencloud.android.domain.authentication.oauth.model.ClientRegistrationRequest
import eu.opencloud.android.domain.authentication.oauth.model.OIDCServerConfiguration
import eu.opencloud.android.domain.authentication.oauth.model.TokenRequest
import eu.opencloud.android.domain.authentication.oauth.model.TokenResponse

interface RemoteOAuthDataSource {
    fun performOIDCDiscovery(baseUrl: String): OIDCServerConfiguration
    fun performTokenRequest(tokenRequest: TokenRequest): TokenResponse

    fun registerClient(clientRegistrationRequest: ClientRegistrationRequest): ClientRegistrationInfo

}
