/**
 * openCloud Android client application
 *
 * @author David González Verdugo
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

package eu.opencloud.android.data.sharing.shares.datasources

import eu.opencloud.android.domain.sharing.shares.model.OCShare
import eu.opencloud.android.domain.sharing.shares.model.ShareType
import eu.opencloud.android.lib.resources.shares.RemoteShare.Companion.INIT_EXPIRATION_DATE_IN_MILLIS

interface RemoteShareDataSource {
    fun getShares(
        remoteFilePath: String,
        reshares: Boolean,
        subfiles: Boolean,
        accountName: String
    ): List<OCShare>

    fun insert(
        remoteFilePath: String,
        shareType: ShareType,
        shareWith: String,
        permissions: Int,
        name: String = "",
        password: String = "",
        expirationDate: Long = INIT_EXPIRATION_DATE_IN_MILLIS,
        accountName: String
    ): OCShare

    fun updateShare(
        remoteId: String,
        name: String = "",
        password: String? = "",
        expirationDateInMillis: Long = INIT_EXPIRATION_DATE_IN_MILLIS,
        permissions: Int,
        accountName: String
    ): OCShare

    fun deleteShare(
        remoteId: String,
        accountName: String,
    )
}
