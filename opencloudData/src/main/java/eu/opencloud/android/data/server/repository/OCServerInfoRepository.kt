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
import eu.opencloud.android.domain.webfinger.model.WebFingerRel
import timber.log.Timber

class OCServerInfoRepository(
    private val remoteServerInfoDataSource: RemoteServerInfoDataSource,
    private val webFingerDatasource: RemoteWebFingerDataSource,
    private val oidcRemoteOAuthDataSource: RemoteOAuthDataSource,
) : ServerInfoRepository {

    override fun getServerInfo(path: String, creatingAccount: Boolean, enforceOIDC: Boolean): ServerInfo {
        val oidcIssuerFromWebFinger: String? = if (creatingAccount) retrieveOIDCIssuerFromWebFinger(serverUrl = path) else null

        if (oidcIssuerFromWebFinger != null) {
            val openIDConnectServerConfiguration = oidcRemoteOAuthDataSource.performOIDCDiscovery(oidcIssuerFromWebFinger)
            return ServerInfo.OIDCServer(
                openCloudVersion = "10.12",
                baseUrl = path,
                oidcServerConfiguration = openIDConnectServerConfiguration
            )
        }

        val serverInfo = remoteServerInfoDataSource.getServerInfo(path, enforceOIDC)

        return if (serverInfo is ServerInfo.BasicServer) {
            serverInfo
        } else {
            // Could be OAuth or OpenID Connect
            val openIDConnectServerConfiguration = try {
                oidcRemoteOAuthDataSource.performOIDCDiscovery(serverInfo.baseUrl)
            } catch (exception: Exception) {
                Timber.d(exception, "OIDC discovery not found")
                null
            }

            if (openIDConnectServerConfiguration != null) {
                ServerInfo.OIDCServer(
                    openCloudVersion = serverInfo.openCloudVersion,
                    baseUrl = serverInfo.baseUrl,
                    oidcServerConfiguration = openIDConnectServerConfiguration
                )
            } else {
                ServerInfo.OAuth2Server(
                    openCloudVersion = serverInfo.openCloudVersion,
                    baseUrl = serverInfo.baseUrl,
                )
            }
        }
    }

    private fun retrieveOIDCIssuerFromWebFinger(
        serverUrl: String,
    ): String? {
        val oidcIssuer = try {
            webFingerDatasource.getInstancesFromWebFinger(
                lookupServer = serverUrl,
                rel = WebFingerRel.OIDC_ISSUER_DISCOVERY,
                resource = serverUrl,
            ).firstOrNull()
        } catch (exception: Exception) {
            Timber.d(exception, "Cant retrieve the oidc issuer from webfinger.")
            null
        }

        return oidcIssuer
    }
}
