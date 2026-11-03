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

import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.opencloud.android.data.ProviderMeta

/**
 * Selection of a pending "save to device folder" job. [fileIds] holds the selected file ids
 * joined by [FILE_IDS_SEPARATOR], so an unbounded selection never has to travel through the
 * 10 KiB WorkManager input data.
 *
 * [attemptCount], [processedCount], [failedCount] and [completedItemKeys] hold the progress of the
 * export, so that a worker that was stopped and started again continues inside a selected folder
 * where it left off and gives up after a bounded number of attempts.
 */
@Entity(tableName = ProviderMeta.ProviderTableMeta.EXPORT_JOBS_TABLE_NAME)
data class ExportJobEntity(
    val accountName: String,
    val targetFolderTreeUri: String,
    val fileIds: String,
    val attemptCount: Int = 0,
    val processedCount: Int = 0,
    val failedCount: Int = 0,
    val completedItemKeys: String = "[]",
) {
    @PrimaryKey(autoGenerate = true) var id: Long = 0

    companion object {
        const val FILE_IDS_SEPARATOR = ","
    }
}
