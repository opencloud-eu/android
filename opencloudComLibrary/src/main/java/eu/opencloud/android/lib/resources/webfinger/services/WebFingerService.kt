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
package eu.opencloud.android.lib.resources.webfinger.services

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.operations.RemoteOperationResult

interface WebFingerService {
    fun getInstancesFromWebFinger(
        lookupServer: String,
        resource: String,
        rel: String,
        client: OpenCloudClient,
    ): RemoteOperationResult<List<String>>
}
