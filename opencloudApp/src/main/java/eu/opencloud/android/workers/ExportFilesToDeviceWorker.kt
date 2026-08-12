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
package eu.opencloud.android.workers

import android.accounts.Account
import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import eu.opencloud.android.R
import eu.opencloud.android.data.executeRemoteOperation
import eu.opencloud.android.data.providers.LocalStorageProvider
import eu.opencloud.android.domain.exportjobs.ExportJobRepository
import eu.opencloud.android.domain.exportjobs.model.OCExportJob
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.usecases.GetFileByIdUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import eu.opencloud.android.domain.files.usecases.GetRemoteFolderContentUseCase
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase
import eu.opencloud.android.domain.files.usecases.SaveFileOrFolderUseCase
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.SingleSessionManager
import eu.opencloud.android.lib.resources.files.DownloadRemoteFileOperation
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.utils.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import eu.opencloud.android.utils.FileStorageUtils
import eu.opencloud.android.utils.NOTIFICATION_TIMEOUT_STANDARD
import eu.opencloud.android.utils.NotificationUtils
import eu.opencloud.android.utils.NotificationUtils.createBasicNotification
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Exports (saves) the files and folders selected by the user into a device folder chosen through
 * the Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE). Folders are recreated recursively.
 * Files that are not already available locally, or whose local copy is outdated, are downloaded
 * first and then copied into the target tree. Addresses opencloud-eu/android#180.
 *
 * Exporting only reads: a document in the target folder is never removed before its replacement
 * is complete, and the local copy of a file is never overwritten when it holds changes that are
 * not uploaded yet.
 *
 * The selection is not passed through the WorkManager input data (it is unbounded and WorkManager
 * only accepts 10 KiB), but persisted as an export job; only the job id is passed here. The job
 * also carries how far the export got, so that a run that the system stopped goes on where it left
 * off instead of exporting everything again, and how often it was started, so that an export that
 * cannot finish is given up and reported instead of being restarted forever.
 */
class ExportFilesToDeviceWorker(
    private val appContext: Context,
    private val workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters), KoinComponent {

    private val exportJobRepository: ExportJobRepository by inject()
    private val getFileByIdUseCase: GetFileByIdUseCase by inject()
    private val getFileByRemotePathUseCase: GetFileByRemotePathUseCase by inject()
    private val getRemoteFolderContentUseCase: GetRemoteFolderContentUseCase by inject()
    private val getWebdavUrlForSpaceUseCase: GetWebDavUrlForSpaceUseCase by inject()
    private val saveFileOrFolderUseCase: SaveFileOrFolderUseCase by inject()
    private val localStorageProvider: LocalStorageProvider by inject()

    private lateinit var account: Account
    private val listedFolders = mutableMapOf<RemoteFolderKey, List<OCFile>>()
    private val temporaryDownloadRoots = mutableSetOf<File>()
    private val downloadSessionToken = UUID.randomUUID().toString()
    private var exportedCount = 0
    private var failedCount = 0
    private var abandonJob = false

    override suspend fun doWork(): Result {
        val exportJobId = workerParameters.inputData.getLong(KEY_PARAM_EXPORT_JOB_ID, -1)
        if (exportJobId < 0) return Result.failure()

        return try {
            val exportJob = exportJobRepository.getExportJobById(exportJobId) ?: run {
                // Without its selection the export cannot be run, and silently doing nothing would
                // leave the user waiting for files that are never written.
                Timber.e("The export job $exportJobId does not exist anymore, nothing is exported")
                notifyResult(failed = true)
                return Result.failure()
            }
            // Exporting a selection is a long, user triggered piece of work. Without a foreground
            // notification it is bound to the ten minutes a background worker gets, which a big
            // selection exceeds easily, and it would be stopped and started again forever.
            startForeground()
            runExportJob(exportJob)
        } finally {
            try {
                // WorkManager ignores the result of an interrupted worker and reschedules it, so
                // the job has to survive a stop. It is only consumed when this run really finished,
                // or when the export was given up.
                if (!isStopped || abandonJob) {
                    exportJobRepository.deleteExportJobById(exportJobId)
                }
            } finally {
                deleteTemporaryDownloads()
            }
        }
    }

    private fun runExportJob(exportJob: OCExportJob): Result {
        account = AccountUtils.getOpenCloudAccountByName(appContext, exportJob.accountName) ?: return Result.failure()
        if (exportJob.fileIds.isEmpty() || exportJob.targetFolderTreeUri.isBlank()) return Result.failure()
        val targetTree = DocumentFile.fromTreeUri(appContext, Uri.parse(exportJob.targetFolderTreeUri)) ?: return Result.failure()

        // An export that is stopped keeps its job so that it can go on, which would let an export
        // that can never finish run again forever without the user ever being told. The attempts
        // are therefore counted in the job itself: WorkManager does not count a run it interrupted
        // itself, so runAttemptCount stays at zero for exactly this case.
        val attemptCount = exportJob.attemptCount + 1
        if (attemptCount > MAX_EXPORT_ATTEMPTS) {
            Timber.e("The export of job ${exportJob.id} was started $MAX_EXPORT_ATTEMPTS times without finishing, it is given up")
            abandonJob = true
            notifyResult(failed = true)
            return Result.failure()
        }

        failedCount = exportJob.failedCount
        var processedCount = exportJob.processedCount.coerceIn(0, exportJob.fileIds.size)
        saveProgress(exportJob, attemptCount, processedCount)

        return try {
            while (processedCount < exportJob.fileIds.size) {
                if (isStopped) break
                exportSelectedItem(exportJob.fileIds[processedCount], targetTree)
                // An item that was interrupted halfway is not marked as done: the next run exports
                // it again, which is what makes going on where this one stopped safe.
                if (isStopped) break
                processedCount++
                saveProgress(exportJob, attemptCount, processedCount)
            }

            if (isStopped) {
                // The remaining items were skipped, so this run must not be reported as a success.
                Timber.w("Export to a device folder was stopped before finishing, it will be run again")
                return Result.retry()
            }

            Timber.i("Export to a device folder finished, $exportedCount item(s) exported in this run, $failedCount failed in total")
            notifyResult(failed = failedCount > 0)
            // Anything that could not be exported leaves the target folder incomplete, so it is
            // reported as a failure instead of a silent partial success. Exports are not chained,
            // so this never keeps another export from running.
            if (failedCount > 0) Result.failure() else Result.success()
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Export to device failed")
            notifyResult(failed = true)
            Result.failure()
        }
    }

    /**
     * Writes how far the export got, so that a run that is stopped and started again does not
     * export everything from the beginning.
     */
    private fun saveProgress(exportJob: OCExportJob, attemptCount: Int, processedCount: Int) {
        // A stopped worker must not write the job again: it may have been removed in the meantime,
        // for instance because the account was removed, and inserting it again would leave it
        // behind forever.
        if (isStopped) return
        exportJobRepository.saveExportJob(
            exportJob.copy(
                attemptCount = attemptCount,
                processedCount = processedCount,
                failedCount = failedCount,
            ).apply { id = exportJob.id }
        )
    }

    private fun exportSelectedItem(fileId: Long, targetTree: DocumentFile) {
        val storedFile = getFileByIdUseCase(GetFileByIdUseCase.Params(fileId)).getDataOrNull()
        if (storedFile == null) {
            Timber.e("File with id $fileId is not in the database anymore, it cannot be exported")
            failedCount++
            return
        }

        // Selected folders are listed directly from the server when they are traversed.
        val ocFile = if (storedFile.isFolder) storedFile else currentRemoteFile(storedFile) ?: return
        exportFileOrFolder(ocFile, targetTree)
    }

    /**
     * Returns a directly selected file as currently reported by the server, or null when it does
     * not exist anymore.
     *
     * The database is not authoritative: it only holds what the last refresh of the containing
     * folder reported, so a file changed on another client in the meantime would be exported from
     * an outdated local copy without anybody noticing. Every folder holding a selected file is
     * therefore listed once with a read-only Depth: 1 PROPFIND before its version is looked at.
     *
     * Listing is best effort though. Saving files that are already on the device is a local action
     * that has to keep working without a server, so a folder that cannot be listed
     * leaves the stored file as it is; [obtainCurrentContent] then only exports it when the stored
     * version validators prove the local copy to be the current one, and fails it otherwise.
     */
    private fun currentRemoteFile(localFile: OCFile): OCFile? {
        val parentRemotePath = localFile.getParentRemotePath()
        val remoteChildren = try {
            listRemoteFolder(parentRemotePath, localFile.spaceId)
        } catch (throwable: Throwable) {
            Timber.w(
                throwable,
                "The folder holding ${localFile.remotePath} could not be listed; only a proven-current local copy is exported",
            )
            return localFile
        }

        val remoteFile = remoteChildren.firstOrNull { candidate ->
            candidate.remotePath == localFile.remotePath ||
                (candidate.remoteId != null && candidate.remoteId == localFile.remoteId)
        }
        if (remoteFile == null) {
            Timber.e("${localFile.remotePath} does not exist anymore, it cannot be exported")
            failedCount++
            return null
        }
        return remoteFile.withLocalPropertiesFrom(localFile)
    }

    private fun listRemoteFolder(remotePath: String, spaceId: String?): List<OCFile> {
        val key = RemoteFolderKey(remotePath, spaceId)
        listedFolders[key]?.let { return it }

        val result = getRemoteFolderContentUseCase(
            GetRemoteFolderContentUseCase.Params(
                remotePath = remotePath,
                accountName = account.name,
                spaceId = spaceId,
            )
        )
        result.getThrowableOrNull()?.let { throw it }
        return result.getDataOrNull().orEmpty().also { listedFolders[key] = it }
    }

    private fun OCFile.withLocalPropertiesFrom(localFile: OCFile): OCFile = apply {
        copyLocalPropertiesFrom(localFile)
        // The remote model carries the current server eTag in remoteEtag. etag continues to
        // describe the locally synchronized content, so obtainCurrentContent can compare them.
        etag = localFile.etag
        needsToUpdateThumbnail = localFile.needsToUpdateThumbnail
    }

    private fun localStateFor(remoteFile: OCFile): OCFile {
        val localFile = getFileByRemotePathUseCase(
            GetFileByRemotePathUseCase.Params(
                owner = account.name,
                remotePath = remoteFile.remotePath,
                spaceId = remoteFile.spaceId,
            )
        ).getDataOrNull()
        return if (localFile == null) remoteFile else remoteFile.withLocalPropertiesFrom(localFile)
    }

    private fun deleteTemporaryDownloads() {
        temporaryDownloadRoots.forEach { root ->
            if (root.exists() && !root.deleteRecursively()) {
                Timber.w("Could not remove the export download directory ${root.absolutePath}")
            }
        }
        temporaryDownloadRoots.clear()
    }

    private fun exportFileOrFolder(ocFile: OCFile, parent: DocumentFile) {
        if (isStopped) return
        if (ocFile.isFolder) {
            exportFolder(ocFile, parent)
        } else {
            exportSingleFile(ocFile, parent)
        }
    }

    private fun exportFolder(ocFolder: OCFile, parent: DocumentFile) {
        val existing = parent.findFile(ocFolder.fileName)
        if (existing != null && !existing.isDirectory) {
            Timber.e("A file named ${ocFolder.fileName} already exists in the target folder, ${ocFolder.remotePath} was not exported")
            failedCount++
            return
        }

        // The content is listed before the destination directory is created: a folder that could
        // not be listed must not leave an empty directory behind that cannot be told apart from a
        // legitimately empty one.
        val children = try {
            listFolderContentFromServer(ocFolder)
        } catch (throwable: Throwable) {
            if (isStopped) {
                // The export was interrupted, not the listing. Counting it as failed would carry
                // that over into the run that exports this folder again.
                Timber.w("Listing ${ocFolder.remotePath} was interrupted, the folder is exported again")
                return
            }
            // Do not export a folder as if it were empty when its content could not be listed.
            Timber.e(throwable, "Could not list the content of ${ocFolder.remotePath}")
            failedCount++
            return
        }

        val directory = existing ?: parent.createDirectory(ocFolder.fileName) ?: run {
            Timber.e("Could not create the folder ${ocFolder.fileName} in the target folder")
            failedCount++
            return
        }
        children.forEach { exportFileOrFolder(it, directory) }
    }

    /**
     * Returns the current content of [ocFolder] as known by the server.
     *
     * The database is not authoritative here: a folder that was never opened has no children
     * stored, and the descendants of a folder may have changed since the last refresh, so an
     * export driven by the database alone silently omits files. The subtree is therefore walked
     * folder by folder with a read-only Depth: 1 PROPFIND per folder (openCloud disables Depth:
     * infinity by default). Local state is merged into matching server entries in memory, without
     * reconciling Room or deleting anything from local storage. Lookup failures are thrown so that
     * the folder is reported as failed instead of exported as an empty one.
     */
    private fun listFolderContentFromServer(ocFolder: OCFile): List<OCFile> {
        return listRemoteFolder(ocFolder.remotePath, ocFolder.spaceId).map(::localStateFor)
    }

    private fun exportSingleFile(ocFile: OCFile, parent: DocumentFile) {
        var downloadToDiscard: File? = null
        try {
            // A folder in the target tree may carry the name of a remote file. Removing it to make
            // room for the export would delete the whole folder, so such a collision is rejected.
            rejectDirectoryCollision(parent, ocFile.fileName)

            val content = obtainCurrentContent(ocFile)
            downloadToDiscard = content.fileToDiscard
            val mimeType = ocFile.mimeType.ifBlank { MIME_OCTET_STREAM }

            // The download above may have taken a while, so the target folder is checked again.
            rejectDirectoryCollision(parent, ocFile.fileName)

            val previousExport = parent.findFile(ocFile.fileName)
            if (previousExport == null) {
                writeIntoNewDocument(parent, mimeType, ocFile.fileName, content.path)
            } else {
                replacePreviousExport(parent, previousExport, mimeType, ocFile.fileName, content.path)
            }
            exportedCount++
        } catch (throwable: Throwable) {
            if (isStopped) {
                // Everything this run left half done is exported again, so it must not be counted
                // as a file that could not be exported.
                Timber.w("Export of ${ocFile.remotePath} was interrupted, it is exported again")
            } else {
                Timber.e(throwable, "Export failed for ${ocFile.remotePath}")
                failedCount++
            }
        } finally {
            downloadToDiscard?.delete()
        }
    }

    private fun rejectDirectoryCollision(parent: DocumentFile, fileName: String) {
        if (parent.findFile(fileName)?.isDirectory == true) {
            throw IOException("A folder named $fileName already exists in the target folder")
        }
    }

    /**
     * Writes the exported content into a document that does not exist yet. Nothing can be lost
     * here, so the final name is used right away; an incomplete document is removed again.
     */
    private fun writeIntoNewDocument(parent: DocumentFile, mimeType: String, fileName: String, sourcePath: String) {
        val target = parent.createFile(mimeType, fileName)
            ?: throw IOException("Could not create $fileName in the target folder")
        try {
            copyInto(target, sourcePath)
        } catch (throwable: Throwable) {
            target.delete()
            throw throwable
        }
    }

    /**
     * Replaces a copy of the file that was exported before, without ever leaving the target folder
     * without a copy of it.
     *
     * The new content is written into a staged document first. The previous copy is then moved
     * aside and only removed once the staged one carries its name; when the swap does not happen,
     * for whatever reason, the previous copy is put back and the staged one is dropped. Providers
     * that cannot rename documents at all fall back to overwriting the previous copy in place, and
     * the staged document holding the complete new content is only removed once that write
     * succeeded.
     */
    private fun replacePreviousExport(
        parent: DocumentFile,
        previousExport: DocumentFile,
        mimeType: String,
        fileName: String,
        sourcePath: String,
    ) {
        val staged = createStagedDocument(parent, mimeType, fileName)
        try {
            copyInto(staged, sourcePath)
        } catch (throwable: Throwable) {
            staged.delete()
            throw throwable
        }

        // renameTo() reports a provider that cannot rename with false, but it may just as well
        // let the failure of the provider through, so both mean the same here.
        val movedAside = renameDocument(previousExport, temporaryDocumentName(fileName, BACKUP_DOCUMENT_SUFFIX))
        if (!movedAside) {
            overwritePreviousExport(previousExport, staged, fileName, sourcePath)
            return
        }

        var replaced = false
        try {
            if (!staged.renameTo(fileName)) throw IOException("Could not rename the exported copy of $fileName")
            replaced = true
        } finally {
            // Whatever kept the staged copy from taking the name, a returned false as much as a
            // failure of the provider, the previous copy goes back to its name. Only once it is
            // really back may the staged copy go: as long as it is not, the staged one is the only
            // complete copy in the folder and is kept.
            if (!replaced) {
                if (renameDocument(previousExport, fileName)) {
                    staged.delete()
                } else {
                    Timber.e(
                        "$fileName is left in the target folder as ${previousExport.name} (the previous copy) " +
                            "and as ${staged.name} (the new content)"
                    )
                }
            }
        }

        if (!previousExport.delete()) {
            Timber.w("The replaced copy of $fileName could not be removed, it is left as ${previousExport.name}")
        }
    }

    /**
     * Overwrites the previously exported copy in place, for providers that cannot rename documents
     * at all. This is the one case that cannot be staged: a write that fails halfway leaves the
     * copy under the real name truncated. The staged document holding the complete new content is
     * therefore kept, and named in the failure, so that the content itself is never lost.
     */
    private fun overwritePreviousExport(
        previousExport: DocumentFile,
        staged: DocumentFile,
        fileName: String,
        sourcePath: String,
    ) {
        try {
            copyInto(previousExport, sourcePath, mode = MODE_WRITE_TRUNCATE)
        } catch (throwable: Throwable) {
            throw IOException(
                "$fileName could not be replaced in the target folder, its new content is left there as ${staged.name}",
                throwable,
            )
        }
        if (!staged.delete()) {
            Timber.w("The temporary copy of $fileName could not be removed, it is left as ${staged.name}")
        }
    }

    private fun renameDocument(document: DocumentFile, name: String): Boolean =
        runCatching { document.renameTo(name) }.getOrElse { throwable ->
            Timber.w(throwable, "Could not rename ${document.name} to $name")
            false
        }

    /**
     * Creates the document the new content is staged in, under a name that is not in use.
     *
     * The name carries a random token instead of a fixed suffix, so a document the user owns is
     * never removed to make room for it, and it keeps the extension of the exported file, so
     * providers deriving the extension from the mime type do not append another one.
     */
    private fun createStagedDocument(parent: DocumentFile, mimeType: String, fileName: String): DocumentFile {
        repeat(STAGED_NAME_ATTEMPTS) {
            val stagedName = temporaryDocumentName(fileName, STAGED_DOCUMENT_SUFFIX)
            if (parent.findFile(stagedName) == null) {
                return parent.createFile(mimeType, stagedName)
                    ?: throw IOException("Could not create $fileName in the target folder")
            }
        }
        throw IOException("Could not create a temporary copy of $fileName in the target folder")
    }

    private fun temporaryDocumentName(fileName: String, suffix: String): String {
        val separatorIndex = fileName.lastIndexOf('.')
        val hasExtension = separatorIndex > 0 && separatorIndex < fileName.length - 1
        val baseName = (if (hasExtension) fileName.substring(0, separatorIndex) else fileName).take(MAX_BASE_NAME_LENGTH)
        val extension = if (hasExtension) fileName.substring(separatorIndex + 1) else ""
        val token = UUID.randomUUID().toString().take(NAME_TOKEN_LENGTH)
        return baseName + "." + token + suffix + (if (hasExtension) ".$extension" else "")
    }

    /**
     * Copies the content into a document of the target folder. The copy is given up as soon as the
     * worker is stopped: WorkManager starts the export again while the stopped run may still be
     * writing, and two runs writing into the same documents must not overlap any longer than one
     * block of data.
     */
    private fun copyInto(target: DocumentFile, sourcePath: String, mode: String = MODE_WRITE) {
        appContext.contentResolver.openOutputStream(target.uri, mode)?.use { output ->
            File(sourcePath).inputStream().use { input ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    if (isStopped) throw IOException("The export was stopped while writing ${target.name}")
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } ?: throw IOException("Could not open the target output stream for ${target.name}")
    }

    /**
     * Returns a local path holding the content [ocFile] currently has on the server, downloading
     * it when needed.
     *
     * [OCFile.etag] is the version of the locally synchronized content while [OCFile.remoteEtag]
     * is the version known for the server, so the local copy is only reused when both match and it
     * has not changed locally since that synchronization. Otherwise exporting the local copy would
     * silently save content that is outdated or has not been uploaded yet.
     *
     * A download is only kept as the local copy of the file, and written to the database exactly
     * as DownloadFileWorker does, when it cannot destroy anything. Exporting is a read-only
     * action, so a local copy carrying changes that are not uploaded yet, or a file in conflict,
     * is left untouched and the fresh content is exported from a temporary file instead.
     */
    private fun obtainCurrentContent(ocFile: OCFile): ContentToExport {
        val currentPath = ocFile.storagePath?.takeUnless { it.isBlank() }
        if (ocFile.isAvailableLocally && currentPath != null && File(currentPath).exists() &&
            !ocFile.etag.isNullOrBlank() && ocFile.etag == ocFile.remoteEtag &&
            mayReplaceLocalCopy(ocFile, currentPath)
        ) {
            return ContentToExport(path = currentPath, fileToDiscard = null)
        }

        val downloadRoot = File(
            FileStorageUtils.getTemporalPath(account.name, ocFile.spaceId),
            "export-$id-$downloadSessionToken",
        )
        temporaryDownloadRoots += downloadRoot
        val temporalFolderPath = downloadRoot.absolutePath + File.separator
        val spaceWebDavUrl = getWebdavUrlForSpaceUseCase(
            GetWebDavUrlForSpaceUseCase.Params(accountName = account.name, spaceId = ocFile.spaceId)
        )
        val downloadOperation = DownloadRemoteFileOperation(ocFile.remotePath, temporalFolderPath, spaceWebDavUrl)
        // Throws on failure, aborting the export of this file.
        executeRemoteOperation { downloadOperation.execute(getClient()) }
        val temporalFile = File(temporalFolderPath + ocFile.remotePath)

        // A child discovered only by the read-only server listing has no Room parent. Keep its
        // download isolated and disposable instead of inserting an incomplete database row.
        if (ocFile.id == null) {
            return ContentToExport(path = temporalFile.absolutePath, fileToDiscard = temporalFile)
        }
        if (!mayReplaceLocalCopy(ocFile, currentPath)) {
            Timber.i("The local copy of ${ocFile.remotePath} holds changes that are not uploaded yet, it is kept as it is")
            return ContentToExport(path = temporalFile.absolutePath, fileToDiscard = temporalFile)
        }
        return ContentToExport(path = storeAsLocalCopy(ocFile, temporalFile, currentPath, downloadOperation), fileToDiscard = null)
    }

    /**
     * Whether the downloaded content may be kept as the local copy of [ocFile]. It may not when
     * the file is in conflict, or when the local copy was modified after the last synchronization
     * of its content, which is how SynchronizeFileUseCase detects a local change: those changes
     * only exist on this device and are still waiting to be uploaded.
     */
    private fun mayReplaceLocalCopy(ocFile: OCFile, currentPath: String?): Boolean {
        if (currentPath == null || !File(currentPath).exists()) return true
        if (ocFile.etagInConflict != null) return false
        return ocFile.localModificationTimestamp <= (ocFile.lastSyncDateForData ?: 0)
    }

    private fun storeAsLocalCopy(
        ocFile: OCFile,
        temporalFile: File,
        currentPath: String?,
        downloadOperation: DownloadRemoteFileOperation,
    ): String {
        val finalPath = currentPath ?: localStorageProvider.getDefaultSavePathFor(
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

        // Same metadata as after a regular download, see DownloadFileWorker. Without the eTags the
        // freshly downloaded copy would still look outdated on the next export.
        val currentTime = System.currentTimeMillis()
        ocFile.apply {
            val serverEtag = FileEtagNormalizer.normalize(downloadOperation.etag).orEmpty()
            needsToUpdateThumbnail = true
            modificationTimestamp = downloadOperation.modificationTimestamp
            etag = serverEtag
            remoteEtag = serverEtag
            storagePath = finalPath
            length = finalFile.length()
            lastSyncDateForData = finalFile.lastModified()
            modifiedAtLastSyncForData = downloadOperation.modificationTimestamp
            lastUsage = currentTime
        }
        saveFileOrFolderUseCase(SaveFileOrFolderUseCase.Params(ocFile))
        return finalPath
    }

    private fun getClient(): OpenCloudClient = SingleSessionManager.getDefaultSingleton()
        .getClientFor(OpenCloudAccount(AccountUtils.getOpenCloudAccountByName(appContext, account.name), appContext), appContext)

    /**
     * Announces the export as running work. Besides telling the user what is going on, this takes
     * the export out of the ten minutes a background worker is granted, which a large selection
     * exceeds long before it is done. It stays best effort: the system may refuse to start
     * foreground work, and the export itself does not depend on it.
     */
    private suspend fun startForeground() {
        try {
            setForeground(
                ForegroundInfo(
                    notificationId(PROGRESS_NOTIFICATION_SALT),
                    buildProgressNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            )
        } catch (throwable: Throwable) {
            Timber.w(throwable, "Could not run the export to a device folder in the foreground")
        }
    }

    private fun buildProgressNotification(): Notification =
        NotificationUtils.newNotificationBuilder(appContext, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.export_files_in_progress_ticker))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()

    private fun notifyResult(failed: Boolean) {
        val titleRes = if (failed) R.string.export_files_failed_ticker else R.string.export_files_succeeded_ticker
        createBasicNotification(
            context = appContext,
            contentTitle = appContext.getString(titleRes),
            notificationChannelId = DOWNLOAD_NOTIFICATION_CHANNEL_ID,
            notificationId = notificationId(RESULT_NOTIFICATION_SALT),
            intent = null,
            contentText = "",
            timeOut = if (failed) null else NOTIFICATION_TIMEOUT_STANDARD,
        )
    }

    /**
     * Content ready to be written into the target folder. [fileToDiscard] is set when it lives in
     * a temporary file that has to be removed once the export of this file is over.
     */
    private data class ContentToExport(
        val path: String,
        val fileToDiscard: File?,
    )

    private data class RemoteFolderKey(
        val remotePath: String,
        val spaceId: String?,
    )

    private fun notificationId(salt: Int): Int =
        ((id.hashCode() xor salt) and Int.MAX_VALUE).coerceAtLeast(1)

    companion object {
        const val KEY_PARAM_EXPORT_JOB_ID = "KEY_PARAM_EXPORT_JOB_ID"
        private const val MIME_OCTET_STREAM = "application/octet-stream"
        private const val STAGED_DOCUMENT_SUFFIX = ".exporttmp"
        private const val BACKUP_DOCUMENT_SUFFIX = ".exportbak"
        private const val STAGED_NAME_ATTEMPTS = 5
        private const val NAME_TOKEN_LENGTH = 8
        private const val MAX_BASE_NAME_LENGTH = 64
        private const val MODE_WRITE = "w"
        private const val MODE_WRITE_TRUNCATE = "wt"
        private const val COPY_BUFFER_SIZE = 8 * 1024
        private const val MAX_EXPORT_ATTEMPTS = 5
        private const val PROGRESS_NOTIFICATION_SALT = 0x20000000
        private const val RESULT_NOTIFICATION_SALT = 0x60000000
    }
}
