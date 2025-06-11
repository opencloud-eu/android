/**
 * openCloud Android client application
 *
 * @author Aitor Ballesteros Pavón
 *
 * Copyright (C) 2024 ownCloud GmbH.
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

package eu.opencloud.android.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.opencloud.android.ui.preview.PreviewAudioFragment
import eu.opencloud.android.ui.preview.PreviewImageFragment
import eu.opencloud.android.ui.preview.PreviewTextFragment
import eu.opencloud.android.ui.preview.PreviewVideoActivity
import eu.opencloud.android.usecases.files.RemoveLocallyFilesWithLastUsageOlderThanGivenTimeUseCase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.concurrent.TimeUnit

class RemoveLocallyFilesWithLastUsageOlderThanGivenTimeWorker(
    val appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent {

    private val removeLocallyFilesWithLastUsageOlderThanGivenTimeUseCase: RemoveLocallyFilesWithLastUsageOlderThanGivenTimeUseCase by inject()
    override suspend fun doWork(): Result =
        try {
            removeLocallyFilesWithLastUsageOlderThanGivenTimeUseCase(
                RemoveLocallyFilesWithLastUsageOlderThanGivenTimeUseCase.Params(
                    idFilePreviewing = filePreviewing(),
                )
            )
            Result.success()
        } catch (exception: Exception) {
            Timber.e(exception, "An error occurred when trying to remove local files")
            Result.failure()
        }

    private fun filePreviewing(): String? =
        when {
            PreviewVideoActivity.isOpen -> PreviewVideoActivity.currentFilePreviewing?.remoteId
            PreviewTextFragment.isOpen -> PreviewTextFragment.currentFilePreviewing?.remoteId
            PreviewImageFragment.isOpen -> PreviewImageFragment.currentFilePreviewing?.remoteId
            PreviewAudioFragment.isOpen -> PreviewAudioFragment.currentFilePreviewing?.remoteId
            else -> null
        }

    companion object {
        const val DELETE_FILES_OLDER_GIVEN_TIME_WORKER = "DELETE_FILES_OLDER_GIVEN_TIME_WORKER"
        const val repeatInterval: Long = 1L
        val repeatIntervalTimeUnit: TimeUnit = TimeUnit.HOURS
    }
}
