/**
 * openCloud Android client application
 *
 * @author Bartosz Przybylski
 * @author Christian Schabesberger
 * @author David González Verdugo
 * @author Abel García de Prada
 * @author Shashvat Kedia
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2015  Bartosz Przybylski
 * Copyright (C) 2023 ownCloud GmbH.
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

package eu.opencloud.android.presentation.documentsprovider

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import eu.opencloud.android.MainApp
import eu.opencloud.android.R
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.exceptions.NoConnectionWithServerException
import eu.opencloud.android.domain.exceptions.validation.FileNameException
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.model.OCFile.Companion.PATH_SEPARATOR
import eu.opencloud.android.domain.files.model.OCFile.Companion.ROOT_PATH
import eu.opencloud.android.domain.files.usecases.CopyFileUseCase
import eu.opencloud.android.domain.files.usecases.CreateFolderAsyncUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByIdUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import eu.opencloud.android.domain.files.usecases.GetFolderContentUseCase
import eu.opencloud.android.domain.files.usecases.MoveFileUseCase
import eu.opencloud.android.domain.files.usecases.RemoveFileUseCase
import eu.opencloud.android.domain.files.usecases.RenameFileUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalAndProjectSpacesForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.RefreshSpacesFromServerAsyncUseCase
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.presentation.documentsprovider.cursors.FileCursor
import eu.opencloud.android.presentation.documentsprovider.cursors.RootCursor
import eu.opencloud.android.presentation.documentsprovider.cursors.SpaceCursor
import eu.opencloud.android.presentation.settings.security.SettingsSecurityFragment.Companion.PREFERENCE_LOCK_ACCESS_FROM_DOCUMENT_PROVIDER
import eu.opencloud.android.usecases.synchronization.SynchronizeFileUseCase
import eu.opencloud.android.usecases.transfers.downloads.DownloadFileUseCase
import eu.opencloud.android.usecases.synchronization.SynchronizeFolderUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFilesFromSystemUseCase
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.Vector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture

class DocumentsStorageProvider : DocumentsProvider() {
    /**
     * If a directory requires to sync, it will write the id of the directory into this variable.
     * After the sync function gets triggered again over the same directory, it will see that a sync got already
     * triggered, and does not need to be triggered again. This way a endless loop is prevented.
     */
    private var requestedFolderIdForSync: Long = -1
    private var syncRequired = true

    private var spacesSyncRequired = true

    private lateinit var fileToUpload: OCFile

    // Cache to avoid redundant PROPFINDs when apps (e.g. Google Photos) call
    // openDocument many times for the same file. Two layers:
    // 1. In-flight dedup: concurrent calls for the same file share one PROPFIND via
    //    a CompletableFuture. The first caller does the actual work, others wait.
    // 2. TTL cache: after a sync completes, skip re-checking the same file for a
    //    few seconds to handle rapid sequential calls.
    private val inFlightSyncs = ConcurrentHashMap<Long, CompletableFuture<SynchronizeFileUseCase.SyncType?>>()
    private val inFlightDownloads = ConcurrentHashMap<Long, CompletableFuture<Boolean>>()
    private var propfindCacheFileId: Long? = null
    private var propfindCacheTimestamp: Long = 0

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor? {
        Timber.d("Trying to open $documentId in mode $mode")

        // If documentId == NONEXISTENT_DOCUMENT_ID only Upload is needed because file does not exist in our database yet.
        var ocFile: OCFile
        val uploadOnly: Boolean = documentId == NONEXISTENT_DOCUMENT_ID || documentId == "null"

        var accessMode: Int = ParcelFileDescriptor.parseMode(mode)
        val isWrite: Boolean = mode.contains("w")

        if (!uploadOnly) {
            ocFile = getFileByIdOrException(documentId.toInt())

            if (!ocFile.isAvailableLocally) {
                // File has never been downloaded. Enqueue the download directly —
                // no need for a PROPFIND since we already know we need the file.
                // Apps like Google Photos call openDocument concurrently for the
                // same file — dedup so only one download is enqueued.
                if (!downloadFileCoalesced(ocFile.id!!, ocFile, documentId.toInt(), signal)) {
                    return null
                }
                ocFile = getFileByIdOrException(documentId.toInt())
                if (!ocFile.isAvailableLocally) {
                    return null
                }
                // Seed the TTL cache — the file was just downloaded, so there's no need
                // for a PROPFIND if Google Photos immediately calls openDocument again.
                propfindCacheFileId = ocFile.id
                propfindCacheTimestamp = System.currentTimeMillis()
            } else if (!isWrite) {
                // File is available locally and opened for reading. Check with the server
                // (PROPFIND) whether a newer version exists, and download it if so.
                //
                // Apps like Google Photos call openDocument many times concurrently for
                // the same file. Without dedup, each call does its own PROPFIND, and due
                // to the synchronized lock in OpenCloudClient.executeHttpMethod they
                // serialize — causing 10+ second waits per extra call. We handle this
                // with two layers:
                // 1. TTL cache: skip if we just confirmed this file is up-to-date.
                // 2. In-flight dedup: concurrent calls share one PROPFIND result.
                val fileId = ocFile.id!!
                val now = System.currentTimeMillis()
                if (fileId == propfindCacheFileId && now - propfindCacheTimestamp <= PROPFIND_CACHE_TTL_MS) {
                    Timber.d("Skipping PROPFIND for file $fileId, recently synced ${now - propfindCacheTimestamp}ms ago")
                } else {
                    val syncResult = syncFileWithServerCoalesced(ocFile)

                    when (syncResult) {
                        is SynchronizeFileUseCase.SyncType.AlreadySynchronized -> {
                            // File is up to date, nothing to wait for.
                        }
                        is SynchronizeFileUseCase.SyncType.DownloadEnqueued -> {
                            // A newer version exists. SynchronizeFileUseCase only enqueues
                            // a WorkManager download, it does not wait for it to finish.
                            if (!waitForDownload(syncResult.workerId, documentId.toInt(), signal)) {
                                return null
                            }
                        }
                        is SynchronizeFileUseCase.SyncType.ConflictResolvedWithCopy -> {
                            // Conflict resolved by keeping a local copy and downloading the remote version.
                            if (!waitForDownload(syncResult.workerId, documentId.toInt(), signal)) {
                                return null
                            }
                        }
                        is SynchronizeFileUseCase.SyncType.FileNotFound -> {
                            return null
                        }
                        is SynchronizeFileUseCase.SyncType.UploadEnqueued -> {
                            // Local file is newer, upload was enqueued. Serve the local version.
                        }
                        null -> {
                            // Sync failed, serve the local version anyway.
                        }
                    }

                    propfindCacheFileId = fileId
                    propfindCacheTimestamp = System.currentTimeMillis()

                    // Re-read the file from DB to get the updated state after download.
                    ocFile = getFileByIdOrException(documentId.toInt())
                    if (!ocFile.isAvailableLocally) {
                        return null
                    }
                }
            }
        } else {
            ocFile = fileToUpload
            accessMode = accessMode or ParcelFileDescriptor.MODE_CREATE
        }

        val fileToOpen = File(ocFile.storagePath)

        return if (!isWrite) {
            ParcelFileDescriptor.open(fileToOpen, accessMode)
        } else {
            val handler = Handler(MainApp.appContext.mainLooper)
            // Attach a close listener if the document is opened in write mode.
            try {
                ParcelFileDescriptor.open(fileToOpen, accessMode, handler) {
                    // Update the file with the cloud server. The client is done writing.
                    Timber.d("A file with id $documentId has been closed! Time to synchronize it with server.")
                    // If only needs to upload that file
                    if (uploadOnly) {
                        ocFile.length = fileToOpen.length()
                        val uploadFilesUseCase: UploadFilesFromSystemUseCase by inject()
                        val uploadFilesUseCaseParams = UploadFilesFromSystemUseCase.Params(
                            accountName = ocFile.owner,
                            listOfLocalPaths = listOf(fileToOpen.path),
                            uploadFolderPath = ocFile.remotePath.substringBeforeLast(PATH_SEPARATOR).plus(PATH_SEPARATOR),
                            spaceId = ocFile.spaceId,
                        )
                        CoroutineScope(Dispatchers.IO).launch {
                            uploadFilesUseCase(uploadFilesUseCaseParams)
                        }
                    } else {
                        syncFileWithServerAsync(ocFile)
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "Couldn't open document")
                throw FileNotFoundException("Failed to open document with id $documentId and mode $mode")
            }
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val resultCursor: MatrixCursor

        val folderId = try {
            parentDocumentId.toLong()
        } catch (numberFormatException: NumberFormatException) {
            null
        }

        // Folder id is null, so at this point we need to list the spaces for the account.
        if (folderId == null) {
            resultCursor = SpaceCursor(projection)

            val getPersonalAndProjectSpacesForAccountUseCase: GetPersonalAndProjectSpacesForAccountUseCase by inject()
            val getFileByRemotePathUseCase: GetFileByRemotePathUseCase by inject()

            getPersonalAndProjectSpacesForAccountUseCase(
                GetPersonalAndProjectSpacesForAccountUseCase.Params(
                    accountName = parentDocumentId,
                )
            ).forEach { space ->
                if (!space.isDisabled) {
                    getFileByRemotePathUseCase(
                        GetFileByRemotePathUseCase.Params(
                            owner = space.accountName,
                            remotePath = ROOT_PATH,
                            spaceId = space.id,
                        )
                    ).getDataOrNull()?.let { rootFolder ->
                        resultCursor.addSpace(space, rootFolder, context)
                    }
                }
            }

            /**
             * This will start syncing the spaces. User will only see this after updating his view with a
             * pull down, or by accessing the spaces folder.
             */
            if (spacesSyncRequired) {
                syncSpacesWithServer(parentDocumentId)
                resultCursor.setMoreToSync(true)
            }

            spacesSyncRequired = true
        } else {
            // Folder id is not null, so this is a regular folder
            resultCursor = FileCursor(projection)

            // Create result cursor before syncing folder again, in order to enable faster loading
            getFolderContent(folderId.toInt()).forEach { file -> resultCursor.addFile(file) }

            /**
             * This will start syncing the current folder. User will only see this after updating his view with a
             * pull down, or by accessing the folder again.
             */
            if (requestedFolderIdForSync != folderId && syncRequired) {
                // register for sync
                syncDirectoryWithServer(parentDocumentId)
                requestedFolderIdForSync = folderId
                resultCursor.setMoreToSync(true)
            } else {
                requestedFolderIdForSync = -1
            }

            syncRequired = true
        }

        // Create notification listener
        val notifyUri: Uri = toNotifyUri(toUri(parentDocumentId))
        resultCursor.setNotificationUri(context?.contentResolver, notifyUri)

        return resultCursor

    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        Timber.d("Query Document: $documentId")
        if (documentId == NONEXISTENT_DOCUMENT_ID) return FileCursor(projection).apply {
            addFile(fileToUpload)
        }

        val fileId = try {
            documentId.toInt()
        } catch (numberFormatException: NumberFormatException) {
            null
        }

        return if (fileId != null) {
            // file id is not null, this is a regular file.
            FileCursor(projection).apply {
                addFile(getFileByIdOrException(fileId))
            }
        } else {
            // file id is null, so at this point this is the root folder for spaces supported account.
            SpaceCursor(projection).apply {
                addRootForSpaces(context = context, accountName = documentId)
            }
        }
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<String>?): Cursor {
        val result = RootCursor(projection)
        val contextApp = context ?: return result
        val accounts = AccountUtils.getAccounts(contextApp)

        // If access from document provider is not allowed, return empty cursor
        val preferences: SharedPreferencesProvider by inject()
        val lockAccessFromDocumentProvider = preferences.getBoolean(PREFERENCE_LOCK_ACCESS_FROM_DOCUMENT_PROVIDER, false)
        return if (lockAccessFromDocumentProvider && accounts.isNotEmpty()) {
            result.apply { addProtectedRoot(contextApp) }
        } else {
            for (account in accounts) {
                val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase by inject()
                val capabilities = getStoredCapabilitiesUseCase(
                    GetStoredCapabilitiesUseCase.Params(
                        accountName = account.name
                    )
                )
                val spacesFeatureAllowedForAccount = AccountUtils.isSpacesFeatureAllowedForAccount(contextApp, account, capabilities)

                result.addRoot(account, contextApp, spacesFeatureAllowedForAccount)
            }
            result
        }
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor {
        // To do: Show thumbnail for spaces
        val file = getFileByIdOrException(documentId.toInt())

        val realFile = File(file.storagePath)

        return AssetFileDescriptor(
            ParcelFileDescriptor.open(realFile, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH
        )
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<String>?
    ): Cursor {
        val result = FileCursor(projection)

        val root = getFileByPathOrException(ROOT_PATH, AccountUtils.getCurrentOpenCloudAccount(context).name)

        for (f in findFiles(root, query)) {
            result.addFile(f)
        }

        return result
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        Timber.d("Create Document ParentID $parentDocumentId Type $mimeType DisplayName $displayName")
        val parentDocument = getFileByIdOrException(parentDocumentId.toInt())

        return if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            createFolder(parentDocument, displayName)
        } else {
            createFile(parentDocument, mimeType, displayName)
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        Timber.d("Trying to rename $documentId to $displayName")

        val file = getFileByIdOrException(documentId.toInt())

        val renameFileUseCase: RenameFileUseCase by inject()
        renameFileUseCase(RenameFileUseCase.Params(file, displayName)).also {
            checkUseCaseResult(
                it, file.parentId.toString()
            )
        }

        return null
    }

    override fun deleteDocument(documentId: String) {
        Timber.d("Trying to delete $documentId")
        val file = getFileByIdOrException(documentId.toInt())

        val removeFileUseCase: RemoveFileUseCase by inject()
        removeFileUseCase(RemoveFileUseCase.Params(listOf(file), false)).also {
            checkUseCaseResult(
                it, file.parentId.toString()
            )
        }
    }

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        Timber.d("Trying to copy $sourceDocumentId to $targetParentDocumentId")

        val sourceFile = getFileByIdOrException(sourceDocumentId.toInt())
        val targetParentFile = getFileByIdOrException(targetParentDocumentId.toInt())

        val copyFileUseCase: CopyFileUseCase by inject()

        copyFileUseCase(
            CopyFileUseCase.Params(
                listOfFilesToCopy = listOf(sourceFile),
                targetFolder = targetParentFile,
                replace = listOf(false),
                isUserLogged = AccountUtils.getCurrentOpenCloudAccount(context) != null,
            )
        ).also { result ->
            syncRequired = false
            checkUseCaseResult(result, targetParentFile.id.toString())
            // Returns the document id of the document copied at the target destination
            var newPath = targetParentFile.remotePath + sourceFile.fileName
            if (sourceFile.isFolder) newPath += File.separator
            val newFile = getFileByPathOrException(newPath, targetParentFile.owner)
            return newFile.id.toString()
        }
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        Timber.d("Trying to move $sourceDocumentId to $targetParentDocumentId")

        val sourceFile = getFileByIdOrException(sourceDocumentId.toInt())
        val targetParentFile = getFileByIdOrException(targetParentDocumentId.toInt())

        val moveFileUseCase: MoveFileUseCase by inject()

        moveFileUseCase(
            MoveFileUseCase.Params(
                listOfFilesToMove = listOf(sourceFile),
                targetFolder = targetParentFile,
                replace = listOf(false),
                isUserLogged = AccountUtils.getCurrentOpenCloudAccount(context) != null,
            )
        ).also { result ->
            syncRequired = false
            checkUseCaseResult(result, targetParentFile.id.toString())
            // Returns the document id of the document moved to the target destination
            var newPath = targetParentFile.remotePath + sourceFile.fileName
            if (sourceFile.isFolder) newPath += File.separator
            val newFile = getFileByPathOrException(newPath, targetParentFile.owner)
            return newFile.id.toString()
        }
    }

    private fun checkUseCaseResult(result: UseCaseResult<Any>, folderToNotify: String) {
        if (!result.isSuccess) {
            Timber.e(result.getThrowableOrNull()!!)
            if (result.getThrowableOrNull() is FileNameException) {
                throw UnsupportedOperationException("Operation contains at least one invalid character")
            }
            if (result.getThrowableOrNull() !is NoConnectionWithServerException) {
                notifyChangeInFolder(folderToNotify)
            }
            throw FileNotFoundException("Remote Operation failed")
        }
        syncRequired = false
        notifyChangeInFolder(folderToNotify)
    }

    private fun createFolder(parentDocument: OCFile, displayName: String): String {
        Timber.d("Trying to create a new folder with name $displayName and parent ${parentDocument.remotePath}")

        val createFolderAsyncUseCase: CreateFolderAsyncUseCase by inject()

        createFolderAsyncUseCase(CreateFolderAsyncUseCase.Params(displayName, parentDocument)).run {
            checkUseCaseResult(this, parentDocument.id.toString())
            val newPath = parentDocument.remotePath + displayName + File.separator
            val newFolder = getFileByPathOrException(newPath, parentDocument.owner, parentDocument.spaceId)
            return newFolder.id.toString()
        }
    }

    private fun createFile(
        parentDocument: OCFile,
        mimeType: String,
        displayName: String,
    ): String {
        // We just need to return a Document ID, so we'll return an empty one. File does not exist in our db yet.
        // File will be created at [openDocument] method.
        val cacheBase = context?.externalCacheDir ?: context?.cacheDir
        val baseTmpDir = File(cacheBase, "upload_tmp")
        val accountSanitized = Uri.encode(parentDocument.owner, "@")
        val accountDir = File(baseTmpDir, accountSanitized)
        val tempDir = if (parentDocument.spaceId != null) File(accountDir, parentDocument.spaceId!!) else accountDir
        val newFile = File(tempDir, displayName)
        newFile.parentFile?.mkdirs()
        fileToUpload = OCFile(
            remotePath = parentDocument.remotePath + displayName,
            mimeType = mimeType,
            parentId = parentDocument.id,
            owner = parentDocument.owner,
            spaceId = parentDocument.spaceId
        ).apply {
            storagePath = newFile.path
        }

        return NONEXISTENT_DOCUMENT_ID
    }

    /**
     * Synchronize a file with the server, coalescing concurrent requests.
     *
     * If another thread is already syncing this file, we wait for its result instead of
     * starting a second PROPFIND. This avoids the serialized lock contention in
     * OpenCloudClient.executeHttpMethod when multiple binder threads call openDocument
     * for the same file simultaneously.
     *
     * The future is always removed from [inFlightSyncs] when done (via finally),
     * so errors or timeouts cannot leave stale entries that would block future syncs.
     */
    private fun syncFileWithServerCoalesced(fileToSync: OCFile): SynchronizeFileUseCase.SyncType? {
        val fileId = fileToSync.id!!
        val newFuture = CompletableFuture<SynchronizeFileUseCase.SyncType?>()
        val existingFuture = inFlightSyncs.putIfAbsent(fileId, newFuture)

        if (existingFuture != null) {
            // Another thread is already syncing this file. Wait for its result.
            Timber.d("Sync for file $fileId already in flight, waiting for result")
            return try {
                existingFuture.get()
            } catch (e: Exception) {
                Timber.w(e, "In-flight sync for file $fileId failed, serving local version")
                null
            }
        }

        // We are the first thread — do the actual PROPFIND.
        return try {
            val result = syncFileWithServerBlocking(fileToSync)
            newFuture.complete(result)
            result
        } catch (e: Exception) {
            newFuture.completeExceptionally(e)
            throw e
        } finally {
            inFlightSyncs.remove(fileId)
        }
    }

    /**
     * Download a file, deduplicating concurrent requests for the same file.
     *
     * Same pattern as [syncFileWithServerCoalesced]: the first thread enqueues the
     * WorkManager download and waits; concurrent threads wait on the same future.
     * This prevents apps like Google Photos (which call openDocument 4+ times
     * concurrently) from enqueuing 4 separate download workers for the same file.
     *
     * @return true if the download succeeded, false otherwise.
     */
    private fun downloadFileCoalesced(fileId: Long, ocFile: OCFile, docId: Int, signal: CancellationSignal?): Boolean {
        val newFuture = CompletableFuture<Boolean>()
        val existingFuture = inFlightDownloads.putIfAbsent(fileId, newFuture)

        if (existingFuture != null) {
            Timber.d("Download for file $fileId already in flight, waiting")
            return try { existingFuture.get() } catch (_: Exception) { false }
        }

        return try {
            val downloadFileUseCase: DownloadFileUseCase by inject()
            val workerId = downloadFileUseCase(
                DownloadFileUseCase.Params(accountName = ocFile.owner, file = ocFile)
            )
            val ok = waitForDownload(workerId, docId, signal)
            newFuture.complete(ok)
            ok
        } catch (e: Exception) {
            Timber.w(e, "Download for file $fileId failed")
            newFuture.complete(false)
            false
        } finally {
            inFlightDownloads.remove(fileId)
        }
    }

    /**
     * Synchronize a file with the server and return the result.
     * Runs synchronously on the calling thread (blocks until the PROPFIND completes).
     * Note: if a download is needed, this only *enqueues* it — use [waitForDownload] to
     * wait for the actual download to finish.
     */
    private fun syncFileWithServerBlocking(fileToSync: OCFile): SynchronizeFileUseCase.SyncType? {
        Timber.d("Trying to sync file ${fileToSync.id} with server (blocking)")

        val synchronizeFileUseCase: SynchronizeFileUseCase by inject()
        val useCaseResult = synchronizeFileUseCase(
            SynchronizeFileUseCase.Params(fileToSynchronize = fileToSync)
        )
        Timber.d("${fileToSync.remotePath} from ${fileToSync.owner} synced with result: $useCaseResult")

        return useCaseResult.getDataOrNull()
    }

    /**
     * Fire-and-forget sync: used in the close handler after writes,
     * where we don't need to wait for the result.
     */
    private fun syncFileWithServerAsync(fileToSync: OCFile) {
        Timber.d("Trying to sync file ${fileToSync.id} with server (async)")

        val synchronizeFileUseCase: SynchronizeFileUseCase by inject()
        CoroutineScope(Dispatchers.IO).launch {
            val useCaseResult = synchronizeFileUseCase(
                SynchronizeFileUseCase.Params(fileToSynchronize = fileToSync)
            )
            Timber.d("${fileToSync.remotePath} from ${fileToSync.owner} synced with result: $useCaseResult")

            val syncResult = useCaseResult.getDataOrNull()
            if (syncResult is SynchronizeFileUseCase.SyncType.ConflictResolvedWithCopy) {
                Timber.i("File sync conflict auto-resolved. Conflicted copy at: ${syncResult.conflictedCopyPath}")
            }
        }
    }

    /**
     * Wait for a download to finish.
     *
     * If [workerId] is non-null, we use WorkManager to wait directly for that specific job.
     * If [workerId] is null, it means a download for this file was already in progress
     * (enqueued by a previous call), so we fall back to polling the DB until the file
     * becomes available locally.
     *
     * Note: openDocument can be called concurrently on multiple binder threads for the
     * same file (e.g. the calling app retries or requests the file multiple times).
     * The first call enqueues the download and gets a workerId; subsequent concurrent
     * calls get null (DownloadFileUseCase deduplicates) and use the polling fallback.
     *
     * @return true if the file is ready, false if cancelled.
     */
    private fun waitForDownload(workerId: UUID?, fileId: Int, signal: CancellationSignal?): Boolean {
        if (workerId != null) {
            // Poll WorkManager until this specific job reaches a terminal state.
            // Note: getWorkInfoById().get() returns the *current* state immediately,
            // it does NOT block until the work finishes.
            Timber.d("Waiting for download worker $workerId to finish")
            val workManager = WorkManager.getInstance(context!!)
            do {
                if (!waitOrGetCancelled(signal)) {
                    return false
                }
                val workInfo = workManager.getWorkInfoById(workerId).get()
                Timber.d("Download worker $workerId state: ${workInfo.state}")
                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> return true
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> return false
                    else -> { /* ENQUEUED, RUNNING, BLOCKED — keep waiting */ }
                }
            } while (true)
        }

        // workerId is null — a download was already in progress from a previous request.
        // Poll until the file appears locally, checking for cancellation each second.
        Timber.d("Download already in progress for file $fileId, polling until available")
        do {
            if (!waitOrGetCancelled(signal)) {
                return false
            }
            val file = getFileByIdOrException(fileId)
            if (file.isAvailableLocally) return true
        } while (true)
    }

    private fun syncDirectoryWithServer(parentDocumentId: String) {
        Timber.d("Trying to sync $parentDocumentId with server")
        val folderToSync = getFileByIdOrException(parentDocumentId.toInt())

        val synchronizeFolderUseCase: SynchronizeFolderUseCase by inject()
        val synchronizeFolderUseCaseParams = SynchronizeFolderUseCase.Params(
            remotePath = folderToSync.remotePath,
            accountName = folderToSync.owner,
            spaceId = folderToSync.spaceId,
            syncMode = SynchronizeFolderUseCase.SyncFolderMode.REFRESH_FOLDER,
        )

        CoroutineScope(Dispatchers.IO).launch {
            val useCaseResult = synchronizeFolderUseCase(synchronizeFolderUseCaseParams)
            Timber.d("${folderToSync.remotePath} from ${folderToSync.owner} was synced with server with result: $useCaseResult")

            if (useCaseResult.isSuccess) {
                notifyChangeInFolder(parentDocumentId)
            }
        }
    }

    private fun syncSpacesWithServer(parentDocumentId: String) {
        Timber.d("Trying to sync spaces from account $parentDocumentId with server")

        val refreshSpacesFromServerAsyncUseCase: RefreshSpacesFromServerAsyncUseCase by inject()
        val refreshSpacesFromServerAsyncUseCaseParams = RefreshSpacesFromServerAsyncUseCase.Params(
            accountName = parentDocumentId,
        )

        CoroutineScope(Dispatchers.IO).launch {
            val useCaseResult = refreshSpacesFromServerAsyncUseCase(refreshSpacesFromServerAsyncUseCaseParams)
            Timber.d("Spaces from account were synced with server with result: $useCaseResult")

            if (useCaseResult.isSuccess) {
                notifyChangeInFolder(parentDocumentId)
            }
            spacesSyncRequired = false
        }
    }

    private fun waitOrGetCancelled(cancellationSignal: CancellationSignal?): Boolean {
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            return false
        }

        return cancellationSignal == null || !cancellationSignal.isCanceled
    }

    private fun findFiles(root: OCFile, query: String): Vector<OCFile> {
        val result = Vector<OCFile>()

        val folderContent = getFolderContent(root.id!!.toInt())
        folderContent.forEach {
            if (it.fileName.contains(query)) {
                result.add(it)
                if (it.isFolder) result.addAll(findFiles(it, query))
            }
        }
        return result
    }

    private fun notifyChangeInFolder(folderToNotify: String) {
        context?.contentResolver?.notifyChange(toNotifyUri(toUri(folderToNotify)), null)
    }

    private fun toNotifyUri(uri: Uri): Uri = DocumentsContract.buildDocumentUri(
        context?.resources?.getString(R.string.document_provider_authority), uri.toString()
    )

    private fun toUri(documentId: String): Uri = Uri.parse(documentId)

    private fun getFileByIdOrException(id: Int): OCFile {
        val getFileByIdUseCase: GetFileByIdUseCase by inject()
        val result = getFileByIdUseCase(GetFileByIdUseCase.Params(id.toLong()))
        return result.getDataOrNull() ?: throw FileNotFoundException("File $id not found")
    }

    private fun getFileByPathOrException(remotePath: String, accountName: String, spaceId: String? = null): OCFile {
        val getFileByRemotePathUseCase: GetFileByRemotePathUseCase by inject()
        val result =
            getFileByRemotePathUseCase(GetFileByRemotePathUseCase.Params(owner = accountName, remotePath = remotePath, spaceId = spaceId))
        return result.getDataOrNull() ?: throw FileNotFoundException("File $remotePath not found")
    }

    private fun getFolderContent(id: Int): List<OCFile> {
        val getFolderContentUseCase: GetFolderContentUseCase by inject()
        val result = getFolderContentUseCase(GetFolderContentUseCase.Params(id.toLong()))
        return result.getDataOrNull() ?: throw FileNotFoundException("Folder $id not found")
    }

    companion object {
        const val NONEXISTENT_DOCUMENT_ID = "-1"
        const val PROPFIND_CACHE_TTL_MS = 3000L
    }
}
