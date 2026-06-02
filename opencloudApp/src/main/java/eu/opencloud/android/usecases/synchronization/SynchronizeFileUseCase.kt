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

        // 1. Check local state first to avoid network calls if possible (optimization)
        // Check if file has changed locally by reading ACTUAL file timestamp from filesystem
        val storagePath = fileToSynchronize.storagePath
        val localFile = storagePath?.let { File(it) }
        val fileExistsLocally = localFile?.exists() == true

        var changedLocally = false
        if (fileExistsLocally) {
            val actualFileModificationTime = localFile!!.lastModified()
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

            if (changedLocally) {
                Timber.w("File deleted remotely but changed locally. Uploading local version instead of deleting.")
                val uuid = requestForUpload(accountName, fileToSynchronize, currentRemoteEtag = "")
                return SyncType.UploadEnqueued(uuid)
            } else {
                val localDbFile = fileToSynchronize.id?.let { fileRepository.getFileById(it) }

                val sameFile = localDbFile != null &&
                        localDbFile.remotePath == fileToSynchronize.remotePath &&
                        localDbFile.spaceId == fileToSynchronize.spaceId
                if (sameFile) {
                   fileRepository.deleteFiles(listOf(fileToSynchronize), true)
                }
                return SyncType.FileNotFound
            }
        }

        // 3. File not downloaded -> Download it or update state
        if (!fileToSynchronize.isAvailableLocally) {
            return if (fileToSynchronize.isAvailableOffline) {
                Timber.i("File ${fileToSynchronize.fileName} is marked for offline access but missing locally. Downloading.")
                val uuid = requestForDownload(accountName = accountName, ocFile = fileToSynchronize)
                SyncType.DownloadEnqueued(uuid)
            } else {
                Timber.i("File ${fileToSynchronize.fileName} is not downloaded and not marked for offline access. Updating database.")
                if (fileToSynchronize.storagePath != null) {
                    val updatedFile = fileToSynchronize.copy(storagePath = null)
                    fileRepository.saveFile(updatedFile)
                }
                SyncType.AlreadySynchronized
            }
        }

        // 4. Check if file has changed remotely
        val changedRemotely = serverFile.etag != fileToSynchronize.etag
        Timber.i("Local etag :${fileToSynchronize.etag} and remote etag :${serverFile.etag}")
        Timber.i("So it has changed remotely: $changedRemotely")

        if (changedLocally && changedRemotely) {
            return handleConflict(fileToSynchronize, accountName, serverFile.etag)
        } else if (changedRemotely) {
            // 5.2 File has changed ONLY remotely -> download new version
            Timber.i("File ${fileToSynchronize.fileName} has changed remotely. Let's download the new version")
            val uuid = requestForDownload(accountName, fileToSynchronize)
            return SyncType.DownloadEnqueued(uuid)
        } else if (changedLocally) {
            // 5.3 File has change ONLY locally -> upload new version
            Timber.i("File ${fileToSynchronize.fileName} has changed locally. Let's upload the new version")
            val uuid = requestForUpload(accountName, fileToSynchronize, serverFile.etag)
            return SyncType.UploadEnqueued(uuid)
        } else {
            // 5.4 File has not change locally not remotely -> do nothing
            if (!fileExistsLocally && fileToSynchronize.storagePath != null) {
                val updatedFile = fileToSynchronize.copy(storagePath = null)
                fileRepository.saveFile(updatedFile)
                Timber.i("File ${fileToSynchronize.fileName} no longer exists locally, updating database")
            }
            Timber.i("File ${fileToSynchronize.fileName} is already synchronized. Nothing to do here")
            return SyncType.AlreadySynchronized
        }
    }

    private fun handleConflict(fileToSynchronize: OCFile, accountName: String, currentRemoteEtag: String?): SyncType {
        val preferLocal = preferencesProvider.getBoolean(
            SettingsSecurityFragment.PREFERENCE_PREFER_LOCAL_ON_CONFLICT, false
        )

        if (preferLocal) {
            Timber.i("File ${fileToSynchronize.fileName} has conflict. User prefers local version, uploading.")
            val uuid = requestForUpload(accountName, fileToSynchronize, currentRemoteEtag)
            return SyncType.UploadEnqueued(uuid)
        }

        Timber.i("File ${fileToSynchronize.fileName} has changed locally and remotely. Creating conflicted copy.")
        val localPath = fileToSynchronize.storagePath
        if (localPath.isNullOrEmpty()) {
            Timber.e("File ${fileToSynchronize.fileName} has no local storage path. Cannot create conflicted copy.")
            return SyncType.AlreadySynchronized
        }
        val conflictedCopyPath = createConflictedCopyPath(localPath)
        val renamed = renameLocalFile(localPath, conflictedCopyPath)

        if (!renamed) {
            Timber.e("Failed to rename local file to conflicted copy. ABORTING DOWNLOAD to prevent data loss.")
            return SyncType.AlreadySynchronized
        }

        Timber.i("Local file renamed to conflicted copy: $conflictedCopyPath")

        val conflictedFile = OCFile(
            owner = fileToSynchronize.owner,
            parentId = fileToSynchronize.parentId,
            length = File(conflictedCopyPath).length(),
            modificationTimestamp = File(conflictedCopyPath).lastModified(),
            remotePath = fileToSynchronize.getParentRemotePath() + File(conflictedCopyPath).name,
            mimeType = fileToSynchronize.mimeType,
            storagePath = conflictedCopyPath,
            spaceId = fileToSynchronize.spaceId,
        )
        fileRepository.saveFile(conflictedFile)

        val uuid = requestForDownload(accountName, fileToSynchronize)
        return SyncType.ConflictResolvedWithCopy(uuid, conflictedCopyPath)
    }

    private fun requestForDownload(accountName: String, ocFile: OCFile): UUID? =
        downloadFileUseCase(
            DownloadFileUseCase.Params(
                accountName = accountName,
                file = ocFile
            )
        )

    private fun requestForUpload(accountName: String, ocFile: OCFile, currentRemoteEtag: String?): UUID? {
        val localPath = ocFile.storagePath
        if (localPath.isNullOrEmpty()) {
            Timber.e("Cannot upload file ${ocFile.fileName} because storagePath is null or empty.")
            return null
        }
        return uploadFileInConflictUseCase(
            UploadFileInConflictUseCase.Params(
                accountName = accountName,
                localPath = localPath,
                uploadFolderPath = ocFile.getParentRemotePath(),
                spaceId = ocFile.spaceId,
                currentRemoteEtag = currentRemoteEtag,
            )
        )
    }

    private fun createConflictedCopyPath(originalPath: String): String {
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
        val oldFile = File(oldPath)
        val newFile = File(newPath)
        if (oldFile.renameTo(newFile)) {
            true
        } else {
            Timber.w("Failed to renameTo, falling back to copyTo: $oldPath to $newPath")
            oldFile.copyTo(newFile, overwrite = true)
            if (!oldFile.delete()) {
                Timber.w("Failed to delete original file after copy: $oldPath. Removing conflicted copy.")
                newFile.delete()
                false
            } else {
                true
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to rename local file from $oldPath to $newPath")
        File(newPath).delete()
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
