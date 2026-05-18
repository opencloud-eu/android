package eu.opencloud.android.utils

import eu.opencloud.android.data.extensions.moveRecursively
import eu.opencloud.android.domain.files.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

object StorageMigrationHelper {

    suspend fun migrateStorageDirectory(
        oldRootPath: String,
        newRootPath: String,
        fileRepository: FileRepository
    ): Boolean = withContext(Dispatchers.IO) {
        if (oldRootPath == newRootPath) {
            Timber.i("Old and new paths are the same, nothing to migrate.")
            return@withContext true
        }

        val oldDir = File(oldRootPath)
        val newDir = File(newRootPath)

        if (!oldDir.exists() || !oldDir.isDirectory) {
            Timber.i("Old directory does not exist or is not a directory, nothing to migrate.")
            return@withContext true
        }

        try {
            newDir.parentFile?.mkdirs()

            // Fast path: Try to rename the root directory
            if (oldDir.renameTo(newDir)) {
                Timber.i("Successfully renamed root directory from $oldRootPath to $newRootPath")
                fileRepository.updateDownloadedFilesStorageDirectoryInStoragePath(oldRootPath, newRootPath)
                return@withContext true
            }

            // Fallback: move recursively if rename fails (e.g. across mount points)
            Timber.w("renameTo failed, falling back to moveRecursively")
            oldDir.listFiles()?.forEach { file ->
                val targetFile = File(newDir, file.name)
                if (file.isDirectory) {
                    file.moveRecursively(targetFile, overwrite = true)
                } else {
                    file.copyTo(targetFile, overwrite = true)
                    file.delete()
                }
            }

            // Clean up old root
            oldDir.deleteRecursively()
            Timber.i("Successfully moved files to $newRootPath")
            fileRepository.updateDownloadedFilesStorageDirectoryInStoragePath(oldRootPath, newRootPath)
            return@withContext true

        } catch (e: Exception) {
            Timber.e(e, "Error migrating storage directory from $oldRootPath to $newRootPath")
            return@withContext false
        }
    }
}
