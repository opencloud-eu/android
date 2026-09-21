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
package eu.opencloud.android.data.exportjobs.datasources

import eu.opencloud.android.domain.exportjobs.model.OCExportJob

interface LocalExportJobDataSource {
    fun saveExportJob(exportJob: OCExportJob): Long
    fun getExportJobById(id: Long): OCExportJob?
    fun getExportJobIdsForAccount(accountName: String): List<Long>
    fun deleteExportJobById(id: Long)
    fun deleteExportJobsForAccount(accountName: String)
}
