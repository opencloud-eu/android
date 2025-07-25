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
package eu.opencloud.android.data.webfinger.datasources

import eu.opencloud.android.domain.webfinger.model.WebFingerRel

interface RemoteWebFingerDataSource {
    fun getInstancesFromWebFinger(
        lookupServer: String,
        rel: WebFingerRel,
        resource: String
    ): List<String>

    fun getInstancesFromAuthenticatedWebFinger(
        lookupServer: String,
        rel: WebFingerRel,
        resource: String,
        username: String,
        accessToken: String,
    ): List<String>
}
