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
package eu.opencloud.android.data.authentication.datasources.implementation

import eu.opencloud.android.data.ClientManager
import eu.opencloud.android.data.authentication.datasources.RemoteAuthenticationDataSource
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.data.user.datasources.implementation.toDomain
import eu.opencloud.android.domain.user.model.UserInfo
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.OpenCloudClient.WEBDAV_FILES_PATH_4_0
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentials
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentialsFactory
import eu.opencloud.android.lib.resources.files.GetBaseUrlRemoteOperation
import eu.opencloud.android.lib.resources.users.GetRemoteUserInfoOperation

class OCRemoteAuthenticationDataSource(
    private val clientManager: ClientManager
) : RemoteAuthenticationDataSource {
    override fun loginBasic(serverPath: String, username: String, password: String): Pair<UserInfo, String?> =
        login(OpenCloudCredentialsFactory.newBasicCredentials(username, password), serverPath)

    override fun loginOAuth(serverPath: String, username: String, accessToken: String): Pair<UserInfo, String?> =
        login(OpenCloudCredentialsFactory.newBearerCredentials(username, accessToken), serverPath)

    private fun login(openCloudCredentials: OpenCloudCredentials, serverPath: String): Pair<UserInfo, String?> {

        val client: OpenCloudClient =
            clientManager.getClientForAnonymousCredentials(
                path = serverPath,
                requiresNewClient = false
            ).apply { credentials = openCloudCredentials }

        val getBaseUrlRemoteOperation = GetBaseUrlRemoteOperation()
        val rawBaseUrl = executeRemoteOperation { getBaseUrlRemoteOperation.execute(client) }

        val userBaseUri = rawBaseUrl?.replace(WEBDAV_FILES_PATH_4_0, "")
            ?: client.baseUri.toString()

        // Get user info. It is needed to save the account into the account manager
        lateinit var userInfo: UserInfo

        executeRemoteOperation {
            GetRemoteUserInfoOperation().execute(client)
        }.let { userInfo = it.toDomain() }

        return Pair(userInfo, userBaseUri)
    }
}
