/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

package eu.opencloud.android.data.appregistry.datasources

import eu.opencloud.android.domain.appregistry.model.AppRegistry

interface RemoteAppRegistryDataSource {
    fun getAppRegistryForAccount(
        accountName: String,
        appUrl: String?,
    ): AppRegistry

    fun getUrlToOpenInWeb(
        accountName: String,
        openWebEndpoint: String,
        fileId: String,
        appName: String,
    ): String

    fun createFileWithAppProvider(
        accountName: String,
        createFileWithAppProviderEndpoint: String,
        parentContainerId: String,
        filename: String,
    ): String
}
