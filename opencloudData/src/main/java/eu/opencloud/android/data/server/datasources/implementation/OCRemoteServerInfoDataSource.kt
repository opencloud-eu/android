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

package eu.opencloud.android.data.server.datasources.implementation

import eu.opencloud.android.data.ClientManager
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.data.server.datasources.RemoteServerInfoDataSource
import eu.opencloud.android.domain.exceptions.OpencloudVersionNotSupportedException
import eu.opencloud.android.domain.exceptions.SpecificServiceUnavailableException
import eu.opencloud.android.domain.server.model.AuthenticationMethod
import eu.opencloud.android.domain.server.model.ServerInfo
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.network.WebdavUtils.normalizeProtocolPrefix
import eu.opencloud.android.lib.resources.status.RemoteServerInfo
import eu.opencloud.android.lib.resources.status.services.ServerInfoService

class OCRemoteServerInfoDataSource(
    private val serverInfoService: ServerInfoService,
    private val clientManager: ClientManager
) : RemoteServerInfoDataSource {

    // Basically, tries to access to the root folder without authorization and analyzes the response.
    fun getAuthenticationMethod(path: String): AuthenticationMethod {
        // Use the same client across the whole login process to keep cookies updated.
        val opencloudClient = clientManager.getClientForAnonymousCredentials(path, false)

        // Step 1: Check whether the root folder exists.
        val checkPathExistenceResult =
            serverInfoService.checkPathExistence(path = path, isUserLoggedIn = false, client = opencloudClient)

        // Step 2: Check if server is available (If server is in maintenance for example, throw exception with specific message)
        if (checkPathExistenceResult.httpCode == HttpConstants.HTTP_SERVICE_UNAVAILABLE) {
            throw SpecificServiceUnavailableException(checkPathExistenceResult.httpPhrase)
        }

        // Step 3: look for authentication methods
        var authenticationMethod = AuthenticationMethod.NONE
        if (checkPathExistenceResult.httpCode == HttpConstants.HTTP_UNAUTHORIZED) {
            val authenticateHeaders = checkPathExistenceResult.authenticateHeaders
            var isBasic = false
            authenticateHeaders.forEach { authenticateHeader ->
                if (authenticateHeader.contains(AuthenticationMethod.BEARER_TOKEN.toString())) {
                    return AuthenticationMethod.BEARER_TOKEN  // Bearer top priority
                } else if (authenticateHeader.contains(AuthenticationMethod.BASIC_HTTP_AUTH.toString())) {
                    isBasic = true
                }
            }

            if (isBasic) {
                authenticationMethod = AuthenticationMethod.BASIC_HTTP_AUTH
            }
        }

        return authenticationMethod
    }

    fun getRemoteStatus(path: String): RemoteServerInfo {
        val openCloudClient = clientManager.getClientForAnonymousCredentials(path, true)

        val remoteStatusResult = serverInfoService.getRemoteStatus(path, openCloudClient)

        val remoteServerInfo = executeRemoteOperation {
            remoteStatusResult
        }

        if (!remoteServerInfo.openCloudVersion.isServerVersionSupported && !remoteServerInfo.openCloudVersion.isVersionHidden) {
            throw OpencloudVersionNotSupportedException()
        }

        return remoteServerInfo
    }

    override fun getServerInfo(path: String, enforceOIDC: Boolean): ServerInfo {
        // First step: check the status of the server (including its version)
        val remoteServerInfo = getRemoteStatus(path)
        val normalizedProtocolPrefix =
            normalizeProtocolPrefix(remoteServerInfo.baseUrl, remoteServerInfo.isSecureConnection)

        // Second step: get authentication method required by the server
        val authenticationMethod =
            if (enforceOIDC) AuthenticationMethod.BEARER_TOKEN
            else getAuthenticationMethod(normalizedProtocolPrefix)

        return if (authenticationMethod == AuthenticationMethod.BEARER_TOKEN) {
            ServerInfo.OAuth2Server(
                openCloudVersion = remoteServerInfo.openCloudVersion.version,
                baseUrl = normalizedProtocolPrefix
            )
        } else {
            ServerInfo.BasicServer(
                openCloudVersion = remoteServerInfo.openCloudVersion.version,
                baseUrl = normalizedProtocolPrefix,
            )
        }
    }
}
