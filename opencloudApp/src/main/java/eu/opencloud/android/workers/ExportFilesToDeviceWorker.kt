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
package eu.opencloud.android.workers

import android.accounts.Account
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import eu.opencloud.android.R
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.data.providers.LocalStorageProvider
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.usecases.GetFileByIdUseCase
import eu.opencloud.android.domain.files.usecases.GetFolderContentUseCase
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase
import eu.opencloud.android.domain.files.usecases.SaveFileOrFolderUseCase
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.SingleSessionManager
import eu.opencloud.android.lib.resources.files.DownloadRemoteFileOperation
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.utils.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import eu.opencloud.android.utils.DOWNLOAD_NOTIFICATION_ID_DEFAULT
import eu.opencloud.android.utils.FileStorageUtils
import eu.opencloud.android.utils.NOTIFICATION_TIMEOUT_STANDARD
import eu.opencloud.android.utils.NotificationUtils.createBasicNotification
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Exports (saves) the files and folders selected by the user into a device folder chosen through
 * the Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE). Folders are recreated recursively.
 * Files that are not already available locally are downloaded into the app storage first and then
 * copied into the target tree. Addresses opencloud-eu/android#180.
 */
class ExportFilesToDeviceWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters), KoinComponent {

    private val getFileByIdUseCase: GetFileByIdUseCase by inject()
    private val getFolderContentUseCase: GetFolderContentUseCase by inject()
    private val getWebdavUrlForSpaceUseCase: GetWebDavUrlForSpaceUseCase by inject()
    private val saveFileOrFolderUseCase: SaveFileOrFolderUseCase by inject()
    private val localStorageProvider: LocalStorageProvider by inject()

    private lateinit var account: Account
    private var exportedCount = 0
    private var failedCount = 0

    override suspend fun doWork(): Result {
        val accountName = workerParameters.inputData.getString(KEY_PARAM_ACCOUNT)
        val fileIds = workerParameters.inputData.getLongArray(KEY_PARAM_FILE_IDS)
        val treeUriString = workerParameters.inputData.getString(KEY_PARAM_TARGET_TREE_URI)

        account = AccountUtils.getOpenCloudAccountByName(appContext, accountName) ?: return Result.failure()
        if (fileIds == null || fileIds.isEmpty() || treeUriString.isNullOrBlank()) return Result.failure()
        val targetTree = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUriString)) ?: return Result.failure()

        return try {
            fileIds.forEach { fileId ->
                if (isStopped) return Result.failure()
                val ocFile = getFileByIdUseCase(GetFileByIdUseCase.Params(fileId)).getDataOrNull()
                if (ocFile != null) {
                    exportFileOrFolder(ocFile, targetTree)
                }
            }
            notifyResult(failed = failedCount > 0)
            if (failedCount > 0 && exportedCount == 0) Result.failure() else Result.success()
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Export to device failed")
            notifyResult(failed = true)
            Result.failure()
        }
    }

    private fun exportFileOrFolder(ocFile: OCFile, parent: DocumentFile) {
        if (isStopped) return
        if (ocFile.isFolder) {
            val existingDir = parent.findFile(ocFile.fileName)?.takeIf { it.isDirectory }
            val directory = existingDir ?: parent.createDirectory(ocFile.fileName) ?: run {
                failedCount++
                return
            }
            val children = ocFile.id?.let {
                getFolderContentUseCase(GetFolderContentUseCase.Params(it)).getDataOrNull()
            }.orEmpty()
            children.forEach { exportFileOrFolder(it, directory) }
        } else {
            exportSingleFile(ocFile, parent)
        }
    }

    private fun exportSingleFile(ocFile: OCFile, parent: DocumentFile) {
        try {
            val localPath = ensureLocalCopy(ocFile)
            val mimeType = ocFile.mimeType.ifBlank { MIME_OCTET_STREAM }
            // Overwrite a previous export with the same name instead of creating a "(1)" duplicate.
            parent.findFile(ocFile.fileName)?.delete()
            val target = parent.createFile(mimeType, ocFile.fileName)
                ?: throw IOException("Could not create ${ocFile.fileName} in the target folder")
            appContext.contentResolver.openOutputStream(target.uri)?.use { output ->
                File(localPath).inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Could not open the target output stream for ${ocFile.fileName}")
            exportedCount++
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Export failed for ${ocFile.remotePath}")
            failedCount++
        }
    }

    /** Returns a local filesystem path for [ocFile], downloading it into the app storage if needed. */
    private fun ensureLocalCopy(ocFile: OCFile): String {
        val currentPath = ocFile.storagePath
        if (ocFile.isAvailableLocally && !currentPath.isNullOrBlank() && File(currentPath).exists()) {
            return currentPath
        }

        val temporalFolderPath = FileStorageUtils.getTemporalPath(account.name, ocFile.spaceId)
        val spaceWebDavUrl = getWebdavUrlForSpaceUseCase(
            GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = ocFile.spaceId)
        )
        val downloadOperation = DownloadRemoteFileOperation(ocFile.remotePath, temporalFolderPath, spaceWebDavUrl)
        // Throws on failure, aborting the export of this file.
        executeRemoteOperation { downloadOperation.execute(getClient()) }

        val temporalFile = File(temporalFolderPath + ocFile.remotePath)
        val finalPath = currentPath?.takeUnless { it.isBlank() }
            ?: localStorageProvider.getDefaultSavePathFor(
                accountName = account.name,
                remotePath = ocFile.remotePath,
                spaceId = ocFile.spaceId,
            )
        val finalFile = File(finalPath)
        finalFile.parentFile?.mkdirs()
        if (!temporalFile.renameTo(finalFile)) {
            temporalFile.copyTo(finalFile, overwrite = true)
            temporalFile.delete()
        }

        ocFile.apply {
            storagePath = finalPath
            length = finalFile.length()
            lastSyncDateForData = finalFile.lastModified()
        }
        saveFileOrFolderUseCase(SaveFileOrFolderUseCase.Params(ocFile))
        return finalPath
    }

    private fun getClient(): OpenCloudClient = SingleSessionManager.getDefaultSingleton()
        .getClientFor(OpenCloudAccount(AccountUtils.getOpenCloudAccountByName(appContext, account.name), appContext), appContext)

    private fun notifyResult(failed: Boolean) {
        val titleRes = if (failed) R.string.export_files_failed_ticker else R.string.export_files_succeeded_ticker
        createBasicNotification(
            context = appContext,
            contentTitle = appContext.getString(titleRes),
            notificationChannelId = DOWNLOAD_NOTIFICATION_CHANNEL_ID,
            notificationId = DOWNLOAD_NOTIFICATION_ID_DEFAULT,
            intent = null,
            contentText = "",
            timeOut = if (failed) null else NOTIFICATION_TIMEOUT_STANDARD,
        )
    }

    companion object {
        const val KEY_PARAM_ACCOUNT = "KEY_PARAM_ACCOUNT"
        const val KEY_PARAM_FILE_IDS = "KEY_PARAM_FILE_IDS"
        const val KEY_PARAM_TARGET_TREE_URI = "KEY_PARAM_TARGET_TREE_URI"
        private const val MIME_OCTET_STREAM = "application/octet-stream"
    }
}
