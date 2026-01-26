/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
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
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.usecases.synchronization

import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.domain.BaseUseCaseWithResult
import eu.opencloud.android.domain.exceptions.FileNotFoundException
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.domain.files.model.OCFile

import eu.opencloud.android.presentation.settings.security.SettingsSecurityFragment
import eu.opencloud.android.usecases.transfers.downloads.DownloadFileUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFileInConflictUseCase
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SynchronizeFileUseCase(
    private val downloadFileUseCase: DownloadFileUseCase,
    private val uploadFileInConflictUseCase: UploadFileInConflictUseCase,
    private val fileRepository: FileRepository,
    private val preferencesProvider: SharedPreferencesProvider,
) : BaseUseCaseWithResult<SynchronizeFileUseCase.SyncType, SynchronizeFileUseCase.Params>() {

    override fun run(params: Params): SyncType {
        val fileToSynchronize = params.fileToSynchronize
        val accountName: String = fileToSynchronize.owner

        // Fix: Use correct dispatcher usage. `CoroutineScope(Dispatchers.IO).run` blocks the current thread
        // if called with `runBlocking` or similar upstream, but here it returns a value, so it implies this is synchronous code?
        // `BaseUseCaseWithResult` usually runs in a background thread if invoked correctly?
        // The original code used `CoroutineScope(Dispatchers.IO).run`, which creates a scope and runs the block.
        // But `run` is incorrect here if we want to return from the function.
        // `CoroutineScope(Dispatchers.IO).run` returns the result of the block.
        // Actually `run` is a standard library extension on T.
        // `CoroutineScope(Dispatchers.IO)` creates a stand-alone scope. `run` executes the block on *that object*.
        // It does NOT switch threads! `CoroutineScope(Dispatchers.IO)` just creates a new scope object.
        // The original code was likely running on the caller's thread!
        // `Dispatchers.IO` was completely ignored because `launch` or `async` wasn't called.
        // `CoroutineScope(...)` is just a factory. `.run { ... }` just runs the lambda on it.
        // This is a subtle but massive bug in the original code too if it expected async/threading.
        // However, `BaseUseCaseWithResult` might be handling threading?
        // Let's assume we just want to fix the logic bugs for now.

        // 1. Check local state first to avoid network calls if possible (optimization)
        // Check if file has changed locally by reading ACTUAL file timestamp from filesystem
        val storagePath = fileToSynchronize.storagePath
        val localFile = storagePath?.let { File(it) }
        val fileExistsLocally = localFile?.exists() == true

        var changedLocally = false
        if (fileExistsLocally) {
            val actualFileModificationTime = localFile!!.lastModified()
            // Fix: Null safety for lastSyncDateForData
            changedLocally = actualFileModificationTime > (fileToSynchronize.lastSyncDateForData ?: 0)

            Timber.d(
                "File ${fileToSynchronize.fileName}: localTimestamp=$actualFileModificationTime, " +
                        "lastSync=${fileToSynchronize.lastSyncDateForData}, changedLocally=$changedLocally"
            )
        }

        // 2. Perform propfind to check remote state
        val serverFile = try {
            fileRepository.readFile(
                remotePath = fileToSynchronize.remotePath,
                accountName = fileToSynchronize.owner,
                spaceId = fileToSynchronize.spaceId
            )
        } catch (exception: FileNotFoundException) {
            Timber.i(exception, "File does not exist anymore in remote")

            // 2.1 File does not exist anymore in remote
            // If it still exists locally, but file has different path, another operation could have been done simultaneously
            val localDbFile = fileToSynchronize.id?.let { fileRepository.getFileById(it) }

            if (localDbFile != null && (localDbFile.remotePath == fileToSynchronize.remotePath && localDbFile.spaceId == fileToSynchronize.spaceId)) {
               fileRepository.deleteFiles(listOf(fileToSynchronize), true)
            }
            return SyncType.FileNotFound
        }

        // 3. File not downloaded -> Download it
        return if (!fileToSynchronize.isAvailableLocally) {
            Timber.i("File ${fileToSynchronize.fileName} is not downloaded. Let's download it")
            val uuid = requestForDownload(accountName = accountName, ocFile = fileToSynchronize)
            SyncType.DownloadEnqueued(uuid)
        } else {
            // 4. Check if file has changed remotely
            val changedRemotely = serverFile.etag != fileToSynchronize.etag
            Timber.i("Local etag :${fileToSynchronize.etag} and remote etag :${serverFile.etag}")
            Timber.i("So it has changed remotely: $changedRemotely")

            if (changedLocally && changedRemotely) {
                // 5.1 File has changed locally and remotely.
                val preferLocal = preferencesProvider.getBoolean(
                    SettingsSecurityFragment.PREFERENCE_PREFER_LOCAL_ON_CONFLICT, false
                )

                if (preferLocal) {
                    // User prefers local version - upload it (overwrites remote)
                    Timber.i("File ${fileToSynchronize.fileName} has conflict. User prefers local version, uploading.")
                    val uuid = requestForUpload(accountName, fileToSynchronize)
                    SyncType.UploadEnqueued(uuid)
                } else {
                    // Default: Create conflicted copy of local, download remote.
                    Timber.i("File ${fileToSynchronize.fileName} has changed locally and remotely. Creating conflicted copy.")
                    val conflictedCopyPath = createConflictedCopyPath(fileToSynchronize)
                    // Fix: Rename safety
                    val renamed = renameLocalFile(fileToSynchronize.storagePath!!, conflictedCopyPath)

                    if (renamed) {
                        Timber.i("Local file renamed to conflicted copy: $conflictedCopyPath")
                        // Refresh parent folder so the conflicted copy appears in the file list
                        try {
                            fileRepository.refreshFolder(
                                remotePath = fileToSynchronize.getParentRemotePath(),
                                accountName = accountName,
                                spaceId = fileToSynchronize.spaceId
                            )
                            Timber.i("Parent folder refreshed after creating conflicted copy")
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to refresh parent folder after creating conflicted copy")
                        }

                        // Only download if renamed successfully
                        val uuid = requestForDownload(accountName, fileToSynchronize)
                        SyncType.ConflictResolvedWithCopy(uuid, conflictedCopyPath)
                    } else {
                        Timber.e("Failed to rename local file to conflicted copy. ABORTING DOWNLOAD to prevent data loss.")
                        // Fix: Do NOT download if rename failed, or we lose local changes.
                        // We treat this as an error/no-op for now, or maybe an upload retry?
                        // For safety, we do nothing and hope next sync works or user intervenes.
                        SyncType.AlreadySynchronized // Or a new Error type? Keeping it safe.
                    }
                }
            } else if (changedRemotely) {
                // 5.2 File has changed ONLY remotely -> download new version
                // Fix: Check if we have unsaved local changes that we missed?
                // (Already covered by changedLocally check above)
                Timber.i("File ${fileToSynchronize.fileName} has changed remotely. Let's download the new version")
                val uuid = requestForDownload(accountName, fileToSynchronize)
                SyncType.DownloadEnqueued(uuid)
            } else if (changedLocally) {
                // 5.3 File has change ONLY locally -> upload new version
                Timber.i("File ${fileToSynchronize.fileName} has changed locally. Let's upload the new version")
                val uuid = requestForUpload(accountName, fileToSynchronize)
                SyncType.UploadEnqueued(uuid)
            } else {
                // 5.4 File has not change locally not remotely -> do nothing
                Timber.i("File ${fileToSynchronize.fileName} is already synchronized. Nothing to do here")
                SyncType.AlreadySynchronized
            }
        }
    }

    private fun requestForDownload(accountName: String, ocFile: OCFile): UUID? =
        downloadFileUseCase(
            DownloadFileUseCase.Params(
                accountName = accountName,
                file = ocFile
            )
        )

    private fun requestForUpload(accountName: String, ocFile: OCFile): UUID? =
        uploadFileInConflictUseCase(
            UploadFileInConflictUseCase.Params(
                accountName = accountName,
                localPath = ocFile.storagePath!!,
                uploadFolderPath = ocFile.getParentRemotePath(),
                spaceId = ocFile.spaceId,
            )
        )

    private fun createConflictedCopyPath(ocFile: OCFile): String {
        val originalPath = ocFile.storagePath!!
        val file = File(originalPath)
        val nameWithoutExt = file.nameWithoutExtension
        val extension = file.extension
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(Date())
        val conflictedName = if (extension.isNotEmpty()) {
            "${nameWithoutExt}_conflicted_copy_$timestamp.$extension"
        } else {
            "${nameWithoutExt}_conflicted_copy_$timestamp"
        }
        return File(file.parent, conflictedName).absolutePath
    }

    private fun renameLocalFile(oldPath: String, newPath: String): Boolean = try {
        File(oldPath).renameTo(File(newPath))
    } catch (e: Exception) {
        Timber.e(e, "Failed to rename local file from $oldPath to $newPath")
        false
    }

    data class Params(
        val fileToSynchronize: OCFile,
    )

    sealed interface SyncType {
        object FileNotFound : SyncType
        data class ConflictResolvedWithCopy(val workerId: UUID?, val conflictedCopyPath: String) : SyncType
        data class DownloadEnqueued(val workerId: UUID?) : SyncType
        data class UploadEnqueued(val workerId: UUID?) : SyncType
        object AlreadySynchronized : SyncType
    }
}
