/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 OpenCloud GmbH.
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
package eu.opencloud.android.data.exportjobs.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.EXPORT_JOBS_TABLE_NAME

@Dao
interface ExportJobDao {
    @Query(SELECT_EXPORT_JOB_WITH_ID)
    fun getExportJobWithId(id: Long): ExportJobEntity?

    @Query(SELECT_EXPORT_JOB_IDS_FOR_ACCOUNT)
    fun getExportJobIdsForAccount(accountName: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrReplace(exportJobEntity: ExportJobEntity): Long

    @Query(DELETE_EXPORT_JOB_WITH_ID)
    fun deleteExportJobWithId(id: Long)

    @Query(DELETE_EXPORT_JOBS_FOR_ACCOUNT)
    fun deleteExportJobsForAccount(accountName: String)

    companion object {
        private const val SELECT_EXPORT_JOB_WITH_ID = """
            SELECT *
            FROM $EXPORT_JOBS_TABLE_NAME
            WHERE id = :id
        """

        private const val SELECT_EXPORT_JOB_IDS_FOR_ACCOUNT = """
            SELECT id
            FROM $EXPORT_JOBS_TABLE_NAME
            WHERE accountName = :accountName
        """

        private const val DELETE_EXPORT_JOB_WITH_ID = """
            DELETE
            FROM $EXPORT_JOBS_TABLE_NAME
            WHERE id = :id
        """

        private const val DELETE_EXPORT_JOBS_FOR_ACCOUNT = """
            DELETE
            FROM $EXPORT_JOBS_TABLE_NAME
            WHERE accountName = :accountName
        """
    }
}
