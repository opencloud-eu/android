/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 *
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

package eu.opencloud.android.usecases.files

import androidx.work.WorkManager
import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.availableoffline.model.AvailableOfflineStatus
import eu.opencloud.android.domain.capabilities.CapabilityRepository
import eu.opencloud.android.domain.files.model.FileMenuOption
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.model.OCFileSyncInfo
import eu.opencloud.android.domain.spaces.usecases.GetSpaceWithSpecialsByIdForAccountUseCase
import eu.opencloud.android.extensions.getRunningWorkInfosByTags
import eu.opencloud.android.usecases.transfers.TRANSFER_TAG_DOWNLOAD

class FilterFileMenuOptionsUseCase(
    private val workManager: WorkManager,
    private val capabilityRepository: CapabilityRepository,
    private val getSpaceWithSpecialsByIdForAccountUseCase: GetSpaceWithSpecialsByIdForAccountUseCase,
) : BaseUseCase<MutableList<FileMenuOption>, FilterFileMenuOptionsUseCase.Params>() {
    override fun run(params: Params): MutableList<FileMenuOption> {
        val optionsToShow = mutableListOf<FileMenuOption>()
        val files = params.files

        if (files.isEmpty()) {
            return mutableListOf()
        }

        val filesSyncInfo = params.filesSyncInfo
        val capability = capabilityRepository.getStoredCapabilities(params.accountName)
        val space = getSpaceWithSpecialsByIdForAccountUseCase(GetSpaceWithSpecialsByIdForAccountUseCase.Params(
            spaceId = files.first().spaceId,
            accountName = params.accountName,
        ))

        val isAnyFileSynchronizing: Boolean = if (filesSyncInfo.isEmpty()) {
            anyFileSynchronizingLookingIntoWorkers(files, params.accountName)
        } else {
            anyFileSynchronizingLookingIIntoFilesSyncInfo(filesSyncInfo)
        }
        val isAnyFileVideoPreviewing = params.isAnyFileVideoPreviewing
        val isAnyFileVideoStreaming = isAnyFileVideoPreviewing && !anyFileDownloaded(files)
        val hasRenamePermission: Boolean = if (isSingleSelection(files)) {
            files.first().hasRenamePermission
        } else {
            false
        }
        val hasMovePermission = files.all { it.hasMovePermission }
        val hasRemovePermission = files.all { it.hasDeletePermission }
        val hasResharePermission: Boolean = if (isSingleSelection(files)) {
            files.first().hasResharePermission
        } else {
            false
        }
        val isPersonalSpace = space?.isPersonal ?: true
        val resharingAllowed = capability?.let { !anyFileSharedWithMe(files) || it.filesSharingResharing.isTrue } ?: false
        val displaySelectAll = params.displaySelectAll
        val displaySelectInverse = params.displaySelectInverse
        val onlyAvailableOfflineFiles = params.onlyAvailableOfflineFiles
        val onlySharedByLinkFiles = params.onlySharedByLinkFiles
        val shareViaLinkAllowed = params.shareViaLinkAllowed
        val shareWithUsersAllowed = params.shareWithUsersAllowed
        val sendAllowed = params.sendAllowed

        val noSyncAndPreviewing = !isAnyFileSynchronizing && !isAnyFileVideoPreviewing
        val noSyncAndStreaming = !isAnyFileSynchronizing && !isAnyFileVideoStreaming
        val shareViaLinkOrWithUsersAllowed = shareViaLinkAllowed || shareWithUsersAllowed
        val noFilesDownloadedOrIsSingleFile = allFilesDownloaded(files) || isSingleFile(files)

        // Select all
        if (displaySelectAll) {
            optionsToShow.add(FileMenuOption.SELECT_ALL)
        }
        // Select inverse
        if (displaySelectInverse) {
            optionsToShow.add(FileMenuOption.SELECT_INVERSE)
        }
        // Share
        if (!onlyAvailableOfflineFiles && shareViaLinkOrWithUsersAllowed && resharingAllowed &&
            isPersonalSpace && hasResharePermission) {
            optionsToShow.add(FileMenuOption.SHARE)
        }
        // Open with (different to preview!)
        if (!isAnyFileSynchronizing && isSingleFile(files)) {
            optionsToShow.add(FileMenuOption.OPEN_WITH)
        }
        // Download
        if (noSyncAndPreviewing && !onlyAvailableOfflineFiles && !onlySharedByLinkFiles &&
            !anyFolder(files) && !anyFileDownloaded(files)) {
            optionsToShow.add(FileMenuOption.DOWNLOAD)
        }
        // Synchronize
        if (!isAnyFileSynchronizing && !onlyAvailableOfflineFiles && !onlySharedByLinkFiles &&
            (anyFileDownloaded(files) || anyFolder(files))) {
            optionsToShow.add(FileMenuOption.SYNC)
        }
        // Cancel sync
        if (isAnyFileSynchronizing && !onlyAvailableOfflineFiles && !onlySharedByLinkFiles && !anyAvailableOfflineFile(files)) {
            optionsToShow.add(FileMenuOption.CANCEL_SYNC)
        }
        // Rename
        if (noSyncAndPreviewing && !onlyAvailableOfflineFiles && !onlySharedByLinkFiles &&
            hasRenamePermission) {
            optionsToShow.add(FileMenuOption.RENAME)
        }
        // Move
        if (noSyncAndPreviewing && !onlyAvailableOfflineFiles && !onlySharedByLinkFiles &&
            hasMovePermission) {
            optionsToShow.add(FileMenuOption.MOVE)
        }
        // Copy
        if (noSyncAndPreviewing && !onlyAvailableOfflineFiles && !onlySharedByLinkFiles) {
            optionsToShow.add(FileMenuOption.COPY)
        }
        // Send
        if (noSyncAndStreaming && !onlyAvailableOfflineFiles && !anyFolder(files) &&
            noFilesDownloadedOrIsSingleFile && sendAllowed) {
            optionsToShow.add(FileMenuOption.SEND)
        }
        // Set as available offline
        if (!isAnyFileSynchronizing && anyNotAvailableOfflineFile(files) && !isAnyFileVideoStreaming) {
            optionsToShow.add(FileMenuOption.SET_AV_OFFLINE)
        }
        // Unset as available offline
        if (anyAvailableOfflineFile(files) && !isAnyFileVideoStreaming) {
            optionsToShow.add(FileMenuOption.UNSET_AV_OFFLINE)
        }
        // Details
        if (isSingleFile(files)) {
            optionsToShow.add(FileMenuOption.DETAILS)
        }
        // Remove
        if (!isAnyFileSynchronizing && !onlyAvailableOfflineFiles && !onlySharedByLinkFiles && hasRemovePermission) {
            optionsToShow.add(FileMenuOption.REMOVE)
        }

        return optionsToShow
    }

    private fun anyFileSynchronizingLookingIntoWorkers(files: List<OCFile>, accountName: String): Boolean {
        val workInfos = workManager.getRunningWorkInfosByTags(listOf(TRANSFER_TAG_DOWNLOAD, accountName))
        val workInfosNotFinished = workInfos.filter { !it.state.isFinished }
        workInfosNotFinished.forEach { workInfoNotFinished ->
            if (files.any { workInfoNotFinished.tags.contains(it.id.toString()) }) {
                return true
            }
        }
        return false
    }

    private fun anyFileSynchronizingLookingIIntoFilesSyncInfo(filesSyncInfo: List<OCFileSyncInfo>) =
        filesSyncInfo.any { it.isSynchronizing }

    private fun anyFileDownloaded(files: List<OCFile>) =
        files.any { it.isAvailableLocally }

    private fun allFilesDownloaded(files: List<OCFile>) =
        files.all { it.isAvailableLocally }

    private fun anyFolder(files: List<OCFile>) =
        files.any { it.isFolder }

    private fun anyAvailableOfflineFile(files: List<OCFile>) =
        files.any { it.availableOfflineStatus == AvailableOfflineStatus.AVAILABLE_OFFLINE }

    private fun anyNotAvailableOfflineFile(files: List<OCFile>) =
        files.any { it.availableOfflineStatus == AvailableOfflineStatus.NOT_AVAILABLE_OFFLINE }

    private fun anyFileSharedWithMe(files: List<OCFile>) =
        files.any { it.isSharedWithMe }

    private fun isSingleSelection(files: List<OCFile>) =
        files.size == 1

    private fun isSingleFile(files: List<OCFile>) =
        isSingleSelection(files) && !files.first().isFolder


    data class Params(
        val files: List<OCFile>,
        val filesSyncInfo: List<OCFileSyncInfo> = emptyList(),
        val accountName: String,
        val isAnyFileVideoPreviewing: Boolean,
        val displaySelectAll: Boolean,
        val displaySelectInverse: Boolean,
        val onlyAvailableOfflineFiles: Boolean,
        val onlySharedByLinkFiles: Boolean,
        val shareViaLinkAllowed: Boolean,
        val shareWithUsersAllowed: Boolean,
        val sendAllowed: Boolean
    )
}
