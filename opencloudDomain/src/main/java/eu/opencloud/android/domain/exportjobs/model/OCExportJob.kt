/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 ownCloud GmbH.
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
package eu.opencloud.android.domain.exportjobs.model

/**
 * A pending "save to device folder" job. The selection is stored here instead of being passed
 * to the worker, because WorkManager limits the serialized input data to 10 KiB and the
 * selection has no upper bound. Only the generated [id] travels through WorkManager.
 *
 * The job also carries the progress of the export, because a worker can be stopped and run again
 * at any time: [attemptCount] is how often it was started, so an export that cannot finish gives
 * up instead of being restarted forever, and [processedCount] is how many of the selected items
 * are done, so a new run continues where the stopped one left off instead of exporting everything
 * again. [failedCount] carries the items that could not be exported over those runs.
 */
data class OCExportJob(
    val accountName: String,
    val targetFolderTreeUri: String,
    val fileIds: List<Long>,
    val attemptCount: Int = 0,
    val processedCount: Int = 0,
    val failedCount: Int = 0,
    var id: Long? = null,
)
