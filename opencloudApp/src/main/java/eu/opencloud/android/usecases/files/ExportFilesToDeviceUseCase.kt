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

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.exportjobs.ExportJobRepository
import eu.opencloud.android.domain.exportjobs.model.OCExportJob
import eu.opencloud.android.workers.ExportFilesToDeviceWorker
import timber.log.Timber

/**
 * Enqueues an [ExportFilesToDeviceWorker] that copies the selected files and folders into a
 * device folder the user picked through the Storage Access Framework. Addresses
 * opencloud-eu/android#180.
 *
 * The selection is not bounded (select all has no item limit) and WorkManager rejects input data
 * bigger than 10 KiB, so the selection is persisted as an export job and only the generated job
 * id is passed to the worker. The worker resolves the job, and the files it refers to, from the
 * database and removes the job once it is done.
 */
class ExportFilesToDeviceUseCase(
    private val workManager: WorkManager,
    private val exportJobRepository: ExportJobRepository,
) : BaseUseCase<Unit, ExportFilesToDeviceUseCase.Params>() {

    // Collecting the jobs without work and writing a new one must not interleave, a job would be
    // taken for abandoned in the moment between it being written and its work being enqueued.
    override fun run(params: Params): Unit = synchronized(ENQUEUE_LOCK) { enqueueExport(params) }

    private fun enqueueExport(params: Params) {
        if (params.fileIds.isEmpty()) return

        deleteAbandonedExportJobs(params.accountName)

        val exportJobId = exportJobRepository.saveExportJob(
            OCExportJob(
                accountName = params.accountName,
                targetFolderTreeUri = params.targetFolderTreeUri,
                fileIds = params.fileIds,
            )
        )

        val inputData = workDataOf(
            ExportFilesToDeviceWorker.KEY_PARAM_EXPORT_JOB_ID to exportJobId,
        )

        val exportWork = OneTimeWorkRequestBuilder<ExportFilesToDeviceWorker>()
            .setInputData(inputData)
            .addTag(params.accountName)
            // A bare numeric tag is the namespace of the transfer ids (see ClearFailedTransfersUseCase),
            // so the job id is prefixed to keep exports out of it.
            .addTag(EXPORT_JOB_TAG_PREFIX + exportJobId)
            .build()

        // The unique name is the job, exactly as the upload use cases key theirs by transfer id.
        // Exports must not be chained: a work whose predecessor failed is never run by
        // WorkManager, so one item that could not be exported would silently swallow the whole
        // next export. Exports running next to each other cannot lose anything, the worker never
        // removes a document before its replacement is complete and stages every write under a
        // name of its own.
        val uniqueWorkName = EXPORT_WORK_NAME_PREFIX + exportJobId
        workManager.enqueueUniqueWork(uniqueWorkName, ExistingWorkPolicy.KEEP, exportWork)
        Timber.i("Export of ${params.fileIds.size} item(s) to a device folder has been enqueued as job $exportJobId.")
    }

    /**
     * Removes the jobs of this account whose work does not exist anymore.
     *
     * A job is normally consumed by the worker itself, but work that is cancelled before it ever
     * runs (or while it runs, which the worker survives on purpose so that it can be run again)
     * leaves its job behind. Nothing else prunes them, so they are collected here, where the list
     * is short and no export of this account is being started at the same time.
     */
    private fun deleteAbandonedExportJobs(accountName: String) {
        val storedJobIds = exportJobRepository.getExportJobIdsForAccount(accountName)
        if (storedJobIds.isEmpty()) return

        val pendingJobIds = runCatching {
            workManager.getWorkInfosByTag(accountName).get()
                .filterNot { it.state.isFinished }
                .flatMap { workInfo -> workInfo.tags.mapNotNull { it.removePrefixOrNull(EXPORT_JOB_TAG_PREFIX)?.toLongOrNull() } }
                .toSet()
        }.getOrElse {
            // Without the state of the work nothing may be removed, a job of a running export
            // would be taken away from it.
            Timber.w(it, "Could not read the state of the exports of the account, no job is removed")
            return
        }

        storedJobIds.filterNot { pendingJobIds.contains(it) }.forEach { abandonedJobId ->
            Timber.i("Export job $abandonedJobId has no work anymore, it is removed")
            exportJobRepository.deleteExportJobById(abandonedJobId)
        }
    }

    private fun String.removePrefixOrNull(prefix: String): String? =
        if (startsWith(prefix)) substring(prefix.length) else null

    data class Params(
        val accountName: String,
        val fileIds: List<Long>,
        val targetFolderTreeUri: String,
    )

    companion object {
        private const val EXPORT_JOB_TAG_PREFIX = "export_job_"
        private const val EXPORT_WORK_NAME_PREFIX = "export_to_device_"
        private val ENQUEUE_LOCK = Any()
    }
}
