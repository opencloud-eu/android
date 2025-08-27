/**
 * openCloud Android client application
 *
 * Copyright (C) 2022 ownCloud GmbH.
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
package eu.opencloud.android.data.webfinger.datasources.implementation

import eu.opencloud.android.data.ClientManager
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.data.webfinger.datasources.RemoteWebFingerDataSource
import eu.opencloud.android.domain.webfinger.model.WebFingerRel
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentialsFactory
import eu.opencloud.android.lib.resources.webfinger.services.WebFingerService

class OCRemoteWebFingerDataSource(
    private val webFingerService: WebFingerService,
    private val clientManager: ClientManager,
) : RemoteWebFingerDataSource {

    override fun getInstancesFromWebFinger(
        lookupServer: String,
        rel: WebFingerRel,
        resource: String
    ): List<String> {
        val openCloudClient = clientManager.getClientForAnonymousCredentials(lookupServer, false)

        return executeRemoteOperation {
            webFingerService.getInstancesFromWebFinger(
                lookupServer = lookupServer,
                rel = rel.uri,
                resource = resource,
                client = openCloudClient
            )
        }
    }

    override fun getInstancesFromAuthenticatedWebFinger(
        lookupServer: String,
        rel: WebFingerRel,
        resource: String,
        username: String,
        accessToken: String,
    ): List<String> {
        val openCloudCredentials = OpenCloudCredentialsFactory.newBearerCredentials(username, accessToken)

        val openCloudClient: OpenCloudClient =
            clientManager.getClientForAnonymousCredentials(
                path = lookupServer,
                requiresNewClient = false
            ).apply { credentials = openCloudCredentials }

        return executeRemoteOperation {
            webFingerService.getInstancesFromWebFinger(
                lookupServer = lookupServer,
                rel = rel.uri,
                resource = resource,
                client = openCloudClient
            )
        }
    }
}
