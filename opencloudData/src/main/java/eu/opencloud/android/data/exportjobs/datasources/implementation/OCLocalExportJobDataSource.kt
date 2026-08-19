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
package eu.opencloud.android.data.exportjobs.datasources.implementation

import androidx.annotation.VisibleForTesting
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import eu.opencloud.android.data.exportjobs.datasources.LocalExportJobDataSource
import eu.opencloud.android.data.exportjobs.db.ExportJobDao
import eu.opencloud.android.data.exportjobs.db.ExportJobEntity
import eu.opencloud.android.data.exportjobs.db.ExportJobEntity.Companion.FILE_IDS_SEPARATOR
import eu.opencloud.android.domain.exportjobs.model.OCExportJob
import java.lang.reflect.Type

class OCLocalExportJobDataSource(
    private val exportJobDao: ExportJobDao
) : LocalExportJobDataSource {

    override fun saveExportJob(exportJob: OCExportJob): Long =
        exportJobDao.insertOrReplace(exportJob.toEntity())

    override fun getExportJobById(id: Long): OCExportJob? =
        exportJobDao.getExportJobWithId(id)?.toModel()

    override fun getExportJobIdsForAccount(accountName: String): List<Long> =
        exportJobDao.getExportJobIdsForAccount(accountName)

    override fun deleteExportJobById(id: Long) {
        exportJobDao.deleteExportJobWithId(id)
    }

    override fun deleteExportJobsForAccount(accountName: String) {
        exportJobDao.deleteExportJobsForAccount(accountName)
    }

    companion object {
        private val completedItemKeysType: Type = Types.newParameterizedType(List::class.java, String::class.java)
        private val completedItemKeysAdapter: JsonAdapter<List<String>> =
            Moshi.Builder().build().adapter(completedItemKeysType)

        @VisibleForTesting
        fun ExportJobEntity.toModel() = OCExportJob(
            accountName = accountName,
            targetFolderTreeUri = targetFolderTreeUri,
            fileIds = fileIds.split(FILE_IDS_SEPARATOR).mapNotNull { it.toLongOrNull() },
            attemptCount = attemptCount,
            processedCount = processedCount,
            failedCount = failedCount,
            completedItemKeys = completedItemKeysAdapter.fromJson(completedItemKeys).orEmpty().toSet(),
            id = id,
        )

        @VisibleForTesting
        fun OCExportJob.toEntity() = ExportJobEntity(
            accountName = accountName,
            targetFolderTreeUri = targetFolderTreeUri,
            fileIds = fileIds.joinToString(FILE_IDS_SEPARATOR),
            attemptCount = attemptCount,
            processedCount = processedCount,
            failedCount = failedCount,
            completedItemKeys = completedItemKeysAdapter.toJson(completedItemKeys.toList()),
        ).apply { this@toEntity.id?.let { this.id = it } }
    }
}
