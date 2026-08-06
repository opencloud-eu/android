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
package eu.opencloud.android.usecases.files

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.workers.ExportFilesToDeviceWorker
import timber.log.Timber

/**
 * Enqueues an [ExportFilesToDeviceWorker] that copies the selected files and folders into a
 * device folder the user picked through the Storage Access Framework. Complex objects cannot be
 * passed to a worker, so we only pass the file ids, the account and the target tree URI; the
 * worker resolves the files from the database. Addresses opencloud-eu/android#180.
 */
class ExportFilesToDeviceUseCase(
    private val workManager: WorkManager,
) : BaseUseCase<Unit, ExportFilesToDeviceUseCase.Params>() {

    override fun run(params: Params) {
        if (params.fileIds.isEmpty()) return

        val inputData = workDataOf(
            ExportFilesToDeviceWorker.KEY_PARAM_ACCOUNT to params.accountName,
            ExportFilesToDeviceWorker.KEY_PARAM_FILE_IDS to params.fileIds.toLongArray(),
            ExportFilesToDeviceWorker.KEY_PARAM_TARGET_TREE_URI to params.targetFolderTreeUri,
        )

        val exportWork = OneTimeWorkRequestBuilder<ExportFilesToDeviceWorker>()
            .setInputData(inputData)
            .addTag(params.accountName)
            .build()

        workManager.enqueue(exportWork)
        Timber.i("Export of ${params.fileIds.size} item(s) to a device folder has been enqueued.")
    }

    data class Params(
        val accountName: String,
        val fileIds: List<Long>,
        val targetFolderTreeUri: String,
    )
}
