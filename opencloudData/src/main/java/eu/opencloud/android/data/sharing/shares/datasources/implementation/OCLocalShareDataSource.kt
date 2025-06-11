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

package eu.opencloud.android.data.sharing.shares.datasources.implementation

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import eu.opencloud.android.data.sharing.shares.datasources.LocalShareDataSource
import eu.opencloud.android.data.sharing.shares.db.OCShareDao
import eu.opencloud.android.data.sharing.shares.db.OCShareEntity
import eu.opencloud.android.domain.sharing.shares.model.OCShare
import eu.opencloud.android.domain.sharing.shares.model.ShareType

class OCLocalShareDataSource(
    private val ocShareDao: OCShareDao,
) : LocalShareDataSource {

    override fun getSharesAsLiveData(
        filePath: String,
        accountName: String,
        shareTypes: List<ShareType>
    ): LiveData<List<OCShare>> =
        ocShareDao.getSharesAsLiveData(
            filePath,
            accountName,
            shareTypes.map { it.value },
        ).map { ocShareEntities ->
            ocShareEntities.map { ocShareEntity -> ocShareEntity.toModel() }
        }

    override fun getShareAsLiveData(remoteId: String): LiveData<OCShare> =
        ocShareDao.getShareAsLiveData(remoteId).map { ocShareEntity ->
            ocShareEntity.toModel()
        }

    override fun insert(ocShare: OCShare): Long =
        ocShareDao.insertOrReplace(
            ocShare.toEntity()
        )

    override fun insert(ocShares: List<OCShare>): List<Long> =
        ocShareDao.insertOrReplace(
            ocShares.map { ocShare -> ocShare.toEntity() }
        )

    override fun update(ocShare: OCShare): Long = ocShareDao.update(ocShare.toEntity())

    override fun replaceShares(ocShares: List<OCShare>): List<Long> =
        ocShareDao.replaceShares(
            ocShares.map { ocShare -> ocShare.toEntity() }
        )

    override fun deleteShare(remoteId: String): Int = ocShareDao.deleteShare(remoteId)

    override fun deleteSharesForFile(filePath: String, accountName: String) =
        ocShareDao.deleteSharesForFile(filePath, accountName)

    override fun deleteSharesForAccount(accountName: String) =
        ocShareDao.deleteSharesForAccount(accountName)

    companion object {
        @VisibleForTesting
        fun OCShareEntity.toModel(): OCShare =
            OCShare(
                id = id,
                shareType = ShareType.fromValue(shareType)!!,
                shareWith = shareWith,
                path = path,
                permissions = permissions,
                sharedDate = sharedDate,
                expirationDate = expirationDate,
                token = token,
                sharedWithDisplayName = sharedWithDisplayName,
                sharedWithAdditionalInfo = sharedWithAdditionalInfo,
                isFolder = isFolder,
                remoteId = remoteId,
                accountOwner = accountOwner,
                name = name,
                shareLink = shareLink
            )

        @VisibleForTesting
        fun OCShare.toEntity(): OCShareEntity =
            OCShareEntity(
                shareType = shareType.value,
                shareWith = shareWith,
                path = path,
                permissions = permissions,
                sharedDate = sharedDate,
                expirationDate = expirationDate,
                token = token,
                sharedWithDisplayName = sharedWithDisplayName,
                sharedWithAdditionalInfo = sharedWithAdditionalInfo,
                isFolder = isFolder,
                remoteId = remoteId,
                accountOwner = accountOwner,
                name = name,
                shareLink = shareLink
            )
    }
}
