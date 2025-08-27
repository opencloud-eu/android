/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Jorge Aguado Recio
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

package eu.opencloud.android.data.user.datasources.implementation

import eu.opencloud.android.data.ClientManager
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.data.user.datasources.RemoteUserDataSource
import eu.opencloud.android.domain.user.model.UserAvatar
import eu.opencloud.android.domain.user.model.UserInfo
import eu.opencloud.android.domain.user.model.UserQuota
import eu.opencloud.android.lib.resources.users.GetRemoteUserQuotaOperation
import eu.opencloud.android.lib.resources.users.RemoteAvatarData
import eu.opencloud.android.lib.resources.users.RemoteUserInfo

class OCRemoteUserDataSource(
    private val clientManager: ClientManager,
    private val avatarDimension: Int
) : RemoteUserDataSource {

    override fun getUserInfo(accountName: String): UserInfo =
        executeRemoteOperation {
            clientManager.getUserService(accountName).getUserInfo()
        }.toDomain()

    override fun getUserQuota(accountName: String): UserQuota =
        executeRemoteOperation {
            clientManager.getUserService(accountName).getUserQuota()
        }.toDomain(accountName)

    override fun getUserAvatar(accountName: String): UserAvatar =
        executeRemoteOperation {
            clientManager.getUserService(accountName = accountName).getUserAvatar(avatarDimension)
        }.toDomain()

}

/**************************************************************************************************************
 ************************************************* Mappers ****************************************************
 **************************************************************************************************************/
fun RemoteUserInfo.toDomain(): UserInfo =
    UserInfo(
        id = this.id,
        displayName = this.displayName,
        email = this.email
    )

private fun RemoteAvatarData.toDomain(): UserAvatar =
    UserAvatar(
        avatarData = this.avatarData,
        eTag = this.eTag,
        mimeType = this.mimeType
    )

private fun GetRemoteUserQuotaOperation.RemoteQuota.toDomain(accountName: String): UserQuota =
    UserQuota(
        accountName = accountName,
        available = this.free,
        used = this.used,
        total = this.total,
        state = null
    )
