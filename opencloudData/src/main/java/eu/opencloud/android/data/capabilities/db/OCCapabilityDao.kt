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

package eu.opencloud.android.data.capabilities.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta

@Dao
interface OCCapabilityDao {
    @Query(SELECT)
    fun getCapabilitiesForAccountAsLiveData(
        accountName: String
    ): LiveData<OCCapabilityEntity?>

    @Query(SELECT)
    fun getCapabilitiesForAccount(
        accountName: String
    ): OCCapabilityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(ocCapability: OCCapabilityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(ocCapabilities: List<OCCapabilityEntity>): List<Long>

    @Query(DELETE_CAPABILITIES_BY_ACCOUNTNAME)
    fun deleteByAccountName(accountName: String)

    @Transaction
    fun replace(ocCapabilities: List<OCCapabilityEntity>) {
        ocCapabilities.forEach { ocCapability ->
            ocCapability.accountName?.run {
                deleteByAccountName(this)
            }
        }
        insertOrReplace(ocCapabilities)
    }

    companion object {
        private const val SELECT = """
            SELECT *
            FROM ${ProviderTableMeta.CAPABILITIES_TABLE_NAME}
            WHERE ${ProviderTableMeta.CAPABILITIES_ACCOUNT_NAME} = :accountName
        """
        private const val DELETE_CAPABILITIES_BY_ACCOUNTNAME = """
            DELETE
            FROM ${ProviderTableMeta.CAPABILITIES_TABLE_NAME}
            WHERE ${ProviderTableMeta.CAPABILITIES_ACCOUNT_NAME} = :accountName
        """
    }
}
