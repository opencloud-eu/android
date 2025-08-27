/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2024 ownCloud GmbH.
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

package eu.opencloud.android.data.oauth.datasources.implementation

import eu.opencloud.android.data.ClientManager
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.data.oauth.datasources.RemoteOAuthDataSource
import eu.opencloud.android.domain.authentication.oauth.model.ClientRegistrationInfo
import eu.opencloud.android.domain.authentication.oauth.model.ClientRegistrationRequest
import eu.opencloud.android.domain.authentication.oauth.model.OIDCServerConfiguration
import eu.opencloud.android.domain.authentication.oauth.model.TokenRequest
import eu.opencloud.android.domain.authentication.oauth.model.TokenResponse
import eu.opencloud.android.lib.resources.oauth.params.ClientRegistrationParams
import eu.opencloud.android.lib.resources.oauth.params.TokenRequestParams
import eu.opencloud.android.lib.resources.oauth.responses.ClientRegistrationResponse
import eu.opencloud.android.lib.resources.oauth.responses.OIDCDiscoveryResponse
import eu.opencloud.android.lib.resources.oauth.services.OIDCService
import eu.opencloud.android.lib.resources.oauth.responses.TokenResponse as RemoteTokenResponse

class OCRemoteOAuthDataSource(
    private val clientManager: ClientManager,
    private val oidcService: OIDCService,
) : RemoteOAuthDataSource {

    override fun performOIDCDiscovery(baseUrl: String): OIDCServerConfiguration {
        val openCloudClient = clientManager.getClientForAnonymousCredentials(baseUrl, false)

        val serverConfiguration = executeRemoteOperation {
            oidcService.getOIDCServerDiscovery(openCloudClient)
        }

        return serverConfiguration.toModel()
    }

    override fun performTokenRequest(tokenRequest: TokenRequest): TokenResponse {
        // For token refreshments, a new client is required, otherwise it could keep outdated credentials or data.
        val requiresNewClient = tokenRequest is TokenRequest.RefreshToken
        val openCloudClient = clientManager.getClientForAnonymousCredentials(path = tokenRequest.baseUrl, requiresNewClient = requiresNewClient)

        val tokenResponse = executeRemoteOperation {
            oidcService.performTokenRequest(
                openCloudClient = openCloudClient,
                tokenRequest = tokenRequest.toParams()
            )
        }

        return tokenResponse.toModel()
    }

    override fun registerClient(clientRegistrationRequest: ClientRegistrationRequest): ClientRegistrationInfo {
        val openCloudClient =
            clientManager.getClientForAnonymousCredentials(clientRegistrationRequest.registrationEndpoint, false)

        val remoteClientRegistrationInfo = executeRemoteOperation {
            oidcService.registerClientWithRegistrationEndpoint(
                openCloudClient = openCloudClient,
                clientRegistrationParams = clientRegistrationRequest.toParams()
            )
        }

        return remoteClientRegistrationInfo.toModel()
    }

    /**************************************************************************************************************
     ************************************************* Mappers ****************************************************
     **************************************************************************************************************/
    private fun OIDCDiscoveryResponse.toModel(): OIDCServerConfiguration =
        OIDCServerConfiguration(
            authorizationEndpoint = this.authorizationEndpoint,
            checkSessionIframe = this.checkSessionIframe,
            endSessionEndpoint = this.endSessionEndpoint,
            issuer = this.issuer,
            registrationEndpoint = this.registrationEndpoint,
            responseTypesSupported = this.responseTypesSupported,
            scopesSupported = this.scopesSupported,
            tokenEndpoint = this.tokenEndpoint,
            tokenEndpointAuthMethodsSupported = this.tokenEndpointAuthMethodsSupported,
            userInfoEndpoint = this.userinfoEndpoint
        )

    private fun TokenRequest.toParams(): TokenRequestParams =
        when (this) {
            is TokenRequest.AccessToken ->
                TokenRequestParams.Authorization(
                    tokenEndpoint = this.tokenEndpoint,
                    authorizationCode = this.authorizationCode,
                    grantType = this.grantType,
                    scope = this.scope,
                    clientId = this.clientId,
                    clientSecret = this.clientSecret,
                    redirectUri = this.redirectUri,
                    clientAuth = this.clientAuth,
                    codeVerifier = this.codeVerifier
                )
            is TokenRequest.RefreshToken ->
                TokenRequestParams.RefreshToken(
                    tokenEndpoint = this.tokenEndpoint,
                    grantType = this.grantType,
                    scope = this.scope,
                    clientId = this.clientId,
                    clientSecret = this.clientSecret,
                    clientAuth = this.clientAuth,
                    refreshToken = this.refreshToken
                )
        }

    private fun RemoteTokenResponse.toModel(): TokenResponse =
        TokenResponse(
            accessToken = this.accessToken,
            expiresIn = this.expiresIn,
            refreshToken = this.refreshToken,
            tokenType = this.tokenType,
            userId = this.userId,
            scope = this.scope,
            additionalParameters = this.additionalParameters
        )

    private fun ClientRegistrationRequest.toParams(): ClientRegistrationParams =
        ClientRegistrationParams(
            registrationEndpoint = this.registrationEndpoint,
            clientName = this.clientName,
            redirectUris = this.redirectUris,
            tokenEndpointAuthMethod = this.tokenEndpointAuthMethod,
            applicationType = this.applicationType
        )

    private fun ClientRegistrationResponse.toModel(): ClientRegistrationInfo =
        ClientRegistrationInfo(
            clientId = this.clientId,
            clientSecret = this.clientSecret,
            clientIdIssuedAt = this.clientIdIssuedAt,
            clientSecretExpiration = this.clientSecretExpiration
        )
}
