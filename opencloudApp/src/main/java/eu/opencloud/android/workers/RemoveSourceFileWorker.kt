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
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.opencloud.android.workers.UploadFileFromContentUriWorker.Companion.KEY_PARAM_CONTENT_URI
import eu.opencloud.android.workers.UploadFileFromFileSystemWorker.Companion.KEY_PARAM_LOCAL_PATH
import org.koin.core.component.KoinComponent
import timber.log.Timber
import java.io.File

class RemoveSourceFileWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParameters
), KoinComponent {

    private var contentUri: Uri? = null
    private var localPath: String? = null

    override suspend fun doWork(): Result {
        if (!areParametersValid()) return Result.failure()
        return try {
            when {
                contentUri != null -> {
                    // Delete content URI file using DocumentFile
                    val documentFile = DocumentFile.fromSingleUri(appContext, contentUri!!)
                    documentFile?.delete()
                }
                localPath != null -> {
                    // Delete local filesystem file
                    val file = File(localPath!!)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                else -> {
                    Timber.w("No valid source to remove")
                    return Result.failure()
                }
            }
            Result.success()
        } catch (throwable: Throwable) {
            Timber.e(throwable)
            Result.failure()
        }
    }

    private fun areParametersValid(): Boolean {
        val paramContentUri = workerParameters.inputData.getString(KEY_PARAM_CONTENT_URI)
        val paramLocalPath = workerParameters.inputData.getString(KEY_PARAM_LOCAL_PATH)

        contentUri = paramContentUri?.toUri()
        localPath = paramLocalPath

        // At least one source must be provided
        return contentUri != null || !localPath.isNullOrBlank()
    }
}
