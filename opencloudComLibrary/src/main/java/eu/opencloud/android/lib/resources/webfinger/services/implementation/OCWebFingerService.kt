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
package eu.opencloud.android.lib.resources.webfinger.services.implementation

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.webfinger.GetInstancesViaWebFingerOperation
import eu.opencloud.android.lib.resources.webfinger.responses.WebFingerResponse
import eu.opencloud.android.lib.resources.webfinger.services.WebFingerService

class OCWebFingerService : WebFingerService {

    override fun getInstancesFromWebFinger(
        lookupServer: String,
        resource: String,
        rel: String,
        client: OpenCloudClient,
    ): RemoteOperationResult<List<String>> {
        val result = GetInstancesViaWebFingerOperation(lockupServerDomain = lookupServer, rel = rel, resource = resource).execute(client)
        if (!result.isSuccess) {
            @Suppress("UNCHECKED_CAST")
            return result as RemoteOperationResult<List<String>>
        }
        val listResult = RemoteOperationResult<List<String>>(RemoteOperationResult.ResultCode.OK)
        listResult.data = result.data.links?.map { it.href } ?: listOf()
        return listResult
    }

    override fun getOidcDiscoveryFromWebFinger(
        lookupServer: String,
        resource: String,
        rel: String,
        platform: String,
        client: OpenCloudClient,
    ): RemoteOperationResult<WebFingerResponse> =
        GetInstancesViaWebFingerOperation(lockupServerDomain = lookupServer, rel = rel, resource = resource, platform = platform).execute(client)
}
