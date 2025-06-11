/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author Jorge Aguado Recio
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

package eu.opencloud.android.ui.preview

import android.accounts.Account
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import eu.opencloud.android.R
import eu.opencloud.android.domain.files.model.FileMenuOption
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.usecases.GetFileByIdAsStreamUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByIdUseCase
import eu.opencloud.android.providers.ContextProvider
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import eu.opencloud.android.usecases.files.FilterFileMenuOptionsUseCase
import eu.opencloud.android.usecases.transfers.downloads.GetLiveDataForFinishedDownloadsFromAccountUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PreviewImageViewModel(
    private val getFileByIdUseCase: GetFileByIdUseCase,
    private val getLiveDataForFinishedDownloadsFromAccountUseCase: GetLiveDataForFinishedDownloadsFromAccountUseCase,
    private val filterFileMenuOptionsUseCase: FilterFileMenuOptionsUseCase,
    getFileByIdAsStreamUseCase: GetFileByIdAsStreamUseCase,
    private val contextProvider: ContextProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    ocFile: OCFile,
) : ViewModel() {

    private val _downloads = MediatorLiveData<List<Pair<OCFile, WorkInfo>>>()
    val downloads: LiveData<List<Pair<OCFile, WorkInfo>>> = _downloads

    private val _menuOptions: MutableStateFlow<List<FileMenuOption>> = MutableStateFlow(emptyList())
    val menuOptions: StateFlow<List<FileMenuOption>> = _menuOptions

    private val currentFile: Flow<OCFile?> = getFileByIdAsStreamUseCase(GetFileByIdAsStreamUseCase.Params(ocFile.id!!))

    fun getCurrentFile(): Flow<OCFile?> = currentFile

    fun startListeningToDownloadsFromAccount(account: Account) {
        _downloads.addSource(
            getLiveDataForFinishedDownloadsFromAccountUseCase(GetLiveDataForFinishedDownloadsFromAccountUseCase.Params(account))
        ) { listOfWorkInfo ->
            viewModelScope.launch(coroutinesDispatcherProvider.io) {
                val finalList = getListOfPairs(listOfWorkInfo)
                _downloads.postValue(finalList)
            }
        }
    }

    fun filterMenuOptions(file: OCFile, accountName: String) {
        val shareViaLinkAllowed = contextProvider.getBoolean(R.bool.share_via_link_feature)
        val shareWithUsersAllowed = contextProvider.getBoolean(R.bool.share_with_users_feature)
        val sendAllowed = contextProvider.getString(R.string.send_files_to_other_apps).equals("on", ignoreCase = true)
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = filterFileMenuOptionsUseCase(
                FilterFileMenuOptionsUseCase.Params(
                    files = listOf(file),
                    accountName = accountName,
                    isAnyFileVideoPreviewing = false,
                    displaySelectAll = false,
                    displaySelectInverse = false,
                    onlyAvailableOfflineFiles = false,
                    onlySharedByLinkFiles = false,
                    shareViaLinkAllowed = shareViaLinkAllowed,
                    shareWithUsersAllowed = shareWithUsersAllowed,
                    sendAllowed = sendAllowed,
                )
            )
            result.apply {
                remove(FileMenuOption.RENAME)
                remove(FileMenuOption.MOVE)
                remove(FileMenuOption.COPY)
                remove(FileMenuOption.SYNC)
            }
            _menuOptions.update { result }
        }
    }

    /**
     * It receives a list of WorkInfo, and it returns a list of Pair(OCFile, WorkInfo)
     * This way, each OCFile is linked to its latest work info.
     */
    private fun getListOfPairs(
        listOfWorkInfo: List<WorkInfo>
    ): List<Pair<OCFile, WorkInfo>> {
        val finalList = mutableListOf<Pair<OCFile, WorkInfo>>()

        listOfWorkInfo.forEach { workInfo ->
            val id: Long = workInfo.tags.first { it.toLongOrNull() != null }.toLong()
            val useCaseResult = getFileByIdUseCase(GetFileByIdUseCase.Params(fileId = id))
            val file = useCaseResult.getDataOrNull()
            if (file != null) {
                finalList.add(Pair(file, workInfo))
            }
        }
        return finalList
    }
}
