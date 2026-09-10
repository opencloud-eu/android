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
        val files = params.files
        if (files.isEmpty()) {
            return mutableListOf()
        }
        val state = buildMenuState(params)
        return buildMenuOptions(files, params, state)
    }

    private data class MenuState(
        val isAnyFileSynchronizing: Boolean,
        val isAnyFileVideoStreaming: Boolean,
        val hasRenamePermission: Boolean,
        val hasMovePermission: Boolean,
        val hasRemovePermission: Boolean,
        val hasResharePermission: Boolean,
        val isPersonalSpace: Boolean,
        val resharingAllowed: Boolean,
    )

    private fun buildMenuState(params: Params): MenuState {
        val files = params.files
        val filesSyncInfo = params.filesSyncInfo
        val capability = capabilityRepository.getStoredCapabilities(params.accountName)
        val space = getSpaceWithSpecialsByIdForAccountUseCase(
            GetSpaceWithSpecialsByIdForAccountUseCase.Params(
                spaceId = files.first().spaceId,
                accountName = params.accountName,
            )
        )
        val isAnyFileSynchronizing: Boolean = if (filesSyncInfo.isEmpty()) {
            anyFileSynchronizingLookingIntoWorkers(files, params.accountName)
        } else {
            anyFileSynchronizingLookingIIntoFilesSyncInfo(filesSyncInfo)
        }
        val isAnyFileVideoPreviewing = params.isAnyFileVideoPreviewing
        val isAnyFileVideoStreaming =
            isAnyFileVideoPreviewing && !anyFileDownloaded(files)
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
        val resharingAllowed = capability?.let {
            !anyFileSharedWithMe(files) || it.filesSharingResharing.isTrue
        } ?: false
        return MenuState(
            isAnyFileSynchronizing = isAnyFileSynchronizing,
            isAnyFileVideoStreaming = isAnyFileVideoStreaming,
            hasRenamePermission = hasRenamePermission,
            hasMovePermission = hasMovePermission,
            hasRemovePermission = hasRemovePermission,
            hasResharePermission = hasResharePermission,
            isPersonalSpace = isPersonalSpace,
            resharingAllowed = resharingAllowed,
        )
    }

    private fun buildMenuOptions(
        files: List<OCFile>,
        params: Params,
        state: MenuState,
    ): MutableList<FileMenuOption> {
        val optionsToShow = mutableListOf<FileMenuOption>()
        val noSyncAndPreviewing =
            !state.isAnyFileSynchronizing && !params.isAnyFileVideoPreviewing
        val noSyncAndStreaming =
            !state.isAnyFileSynchronizing && !state.isAnyFileVideoStreaming
        val shareViaLinkOrWithUsersAllowed =
            params.shareViaLinkAllowed || params.shareWithUsersAllowed
        val noFilesDownloadedOrIsSingleFile =
            allFilesDownloaded(files) || isSingleFile(files)

        if (params.displaySelectAll) {
            optionsToShow.add(FileMenuOption.SELECT_ALL)
        }
        if (params.displaySelectInverse) {
            optionsToShow.add(FileMenuOption.SELECT_INVERSE)
        }
        if (!params.onlyAvailableOfflineFiles && shareViaLinkOrWithUsersAllowed &&
            state.resharingAllowed && state.isPersonalSpace &&
            state.hasResharePermission
        ) {
            optionsToShow.add(FileMenuOption.SHARE)
        }
        if (!state.isAnyFileSynchronizing && isSingleFile(files)) {
            optionsToShow.add(FileMenuOption.OPEN_WITH)
        }
        if (noSyncAndPreviewing && !params.onlyAvailableOfflineFiles &&
            !params.onlySharedByLinkFiles && !anyFolder(files) &&
            !anyFileDownloaded(files)
        ) {
            optionsToShow.add(FileMenuOption.DOWNLOAD)
        }
        if (!state.isAnyFileSynchronizing && !params.onlyAvailableOfflineFiles &&
            !params.onlySharedByLinkFiles &&
            (anyFileDownloaded(files) || anyFolder(files))
        ) {
            optionsToShow.add(FileMenuOption.SYNC)
        }
        if (state.isAnyFileSynchronizing && !params.onlyAvailableOfflineFiles &&
            !params.onlySharedByLinkFiles &&
            !anyAvailableOfflineFile(files)
        ) {
            optionsToShow.add(FileMenuOption.CANCEL_SYNC)
        }
        if (noSyncAndPreviewing && !params.onlyAvailableOfflineFiles &&
            !params.onlySharedByLinkFiles && state.hasRenamePermission
        ) {
            optionsToShow.add(FileMenuOption.RENAME)
        }
        if (noSyncAndPreviewing && !params.onlyAvailableOfflineFiles &&
            !params.onlySharedByLinkFiles && state.hasMovePermission
        ) {
            optionsToShow.add(FileMenuOption.MOVE)
        }
        if (noSyncAndPreviewing && !params.onlyAvailableOfflineFiles &&
            !params.onlySharedByLinkFiles
        ) {
            optionsToShow.add(FileMenuOption.COPY)
        }
        if (noSyncAndStreaming && !params.onlyAvailableOfflineFiles &&
            !anyFolder(files) && noFilesDownloadedOrIsSingleFile &&
            params.sendAllowed
        ) {
            optionsToShow.add(FileMenuOption.SEND)
        }
        if (!state.isAnyFileSynchronizing &&
            anyNotAvailableOfflineFile(files) &&
            !state.isAnyFileVideoStreaming
        ) {
            optionsToShow.add(FileMenuOption.SET_AV_OFFLINE)
        }
        if (anyAvailableOfflineFile(files) && !state.isAnyFileVideoStreaming) {
            optionsToShow.add(FileMenuOption.UNSET_AV_OFFLINE)
        }
        if (isSingleFile(files)) {
            optionsToShow.add(FileMenuOption.DETAILS)
        }
        if (!state.isAnyFileSynchronizing && !params.onlyAvailableOfflineFiles &&
            !params.onlySharedByLinkFiles && state.hasRemovePermission
        ) {
            optionsToShow.add(FileMenuOption.REMOVE)
        }
        if (isSingleSelection(files) && anyFolder(files)) {
            optionsToShow.add(FileMenuOption.ADD_TO_HOME_SCREEN)
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
