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
package eu.opencloud.android.data.exportjobs.repository

import eu.opencloud.android.data.exportjobs.datasources.LocalExportJobDataSource
import eu.opencloud.android.domain.exportjobs.ExportJobRepository
import eu.opencloud.android.domain.exportjobs.model.OCExportJob

class OCExportJobRepository(
    private val localExportJobDataSource: LocalExportJobDataSource
) : ExportJobRepository {

    override fun saveExportJob(exportJob: OCExportJob): Long =
        localExportJobDataSource.saveExportJob(exportJob = exportJob)

    override fun getExportJobById(id: Long): OCExportJob? =
        localExportJobDataSource.getExportJobById(id = id)

    override fun getExportJobIdsForAccount(accountName: String): List<Long> =
        localExportJobDataSource.getExportJobIdsForAccount(accountName = accountName)

    override fun deleteExportJobById(id: Long) {
        localExportJobDataSource.deleteExportJobById(id = id)
    }

    override fun deleteExportJobsForAccount(accountName: String) {
        localExportJobDataSource.deleteExportJobsForAccount(accountName = accountName)
    }
}
