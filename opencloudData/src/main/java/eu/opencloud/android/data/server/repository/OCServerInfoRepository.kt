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

package eu.opencloud.android.data.server.repository

import eu.opencloud.android.data.oauth.datasources.RemoteOAuthDataSource
import eu.opencloud.android.data.server.datasources.RemoteServerInfoDataSource
import eu.opencloud.android.data.webfinger.datasources.RemoteWebFingerDataSource
import eu.opencloud.android.domain.server.ServerInfoRepository
import eu.opencloud.android.domain.server.model.ServerInfo
import eu.opencloud.android.domain.webfinger.model.WebFingerOidcInfo
import eu.opencloud.android.domain.webfinger.model.WebFingerRel
import timber.log.Timber

class OCServerInfoRepository(
    private val remoteServerInfoDataSource: RemoteServerInfoDataSource,
    private val webFingerDatasource: RemoteWebFingerDataSource,
    private val oidcRemoteOAuthDataSource: RemoteOAuthDataSource,
) : ServerInfoRepository {

    override fun getServerInfo(path: String, creatingAccount: Boolean, enforceOIDC: Boolean): ServerInfo {
        // Try webfinger first to get OIDC client config (client_id, scopes) and issuer.
        // This is a lightweight call that returns null if webfinger is not available.
        val oidcInfoFromWebFinger: WebFingerOidcInfo? = retrieveOidcInfoFromWebFinger(serverUrl = path)

        // Always check server status to get the proper baseUrl and version.
        // We must not skip this, because the server may normalize/redirect the URL,
        // and the account name is built from baseUrl + username.
        val serverInfo = remoteServerInfoDataSource.getServerInfo(path, enforceOIDC)

        return if (serverInfo is ServerInfo.BasicServer) {
            serverInfo
        } else {
            // OIDC discovery: prefer the webfinger issuer if available (it may differ from baseUrl),
            // otherwise discover from the server's baseUrl.
            val oidcDiscoveryUrl = oidcInfoFromWebFinger?.issuer ?: serverInfo.baseUrl
            val openIDConnectServerConfiguration = try {
                oidcRemoteOAuthDataSource.performOIDCDiscovery(oidcDiscoveryUrl)
            } catch (exception: Exception) {
                Timber.d(exception, "OIDC discovery not found")
                null
            }

            if (openIDConnectServerConfiguration != null) {
                ServerInfo.OIDCServer(
                    openCloudVersion = serverInfo.openCloudVersion,
                    baseUrl = serverInfo.baseUrl,
                    oidcServerConfiguration = openIDConnectServerConfiguration,
                    webFingerClientId = oidcInfoFromWebFinger?.clientId,
                    webFingerScopes = oidcInfoFromWebFinger?.scopes,
                )
            } else {
                ServerInfo.OAuth2Server(
                    openCloudVersion = serverInfo.openCloudVersion,
                    baseUrl = serverInfo.baseUrl,
                )
            }
        }
    }

    private fun retrieveOidcInfoFromWebFinger(
        serverUrl: String,
    ): WebFingerOidcInfo? = try {
        webFingerDatasource.getOidcInfoFromWebFinger(
            lookupServer = serverUrl,
            rel = WebFingerRel.OIDC_ISSUER_DISCOVERY,
            resource = serverUrl,
        )
    } catch (exception: Exception) {
        Timber.d(exception, "Cant retrieve the oidc info from webfinger.")
        null
    }
}
