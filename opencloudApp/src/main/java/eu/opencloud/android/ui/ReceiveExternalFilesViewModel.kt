/**
 * openCloud Android client application
 *
 * Copyright (C) 2024 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.opencloud.android.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.utils.Event
import eu.opencloud.android.extensions.ViewModelExt.runUseCaseWithResult
import eu.opencloud.android.presentation.common.UIResult
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import eu.opencloud.android.usecases.synchronization.SynchronizeFolderUseCase
import kotlinx.coroutines.launch

class ReceiveExternalFilesViewModel(
    private val synchronizeFolderUseCase: SynchronizeFolderUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase,
    private val accountName: String,
) : ViewModel() {

    private val _syncFolderLiveData = MediatorLiveData<Event<UIResult<Unit>>>()
    val syncFolderLiveData: LiveData<Event<UIResult<Unit>>> = _syncFolderLiveData

    private val _spacesAreAllowed = MutableLiveData<Boolean>()
    val spacesAreAllowed: LiveData<Boolean> = _spacesAreAllowed

    init {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val capabilities = getStoredCapabilitiesUseCase(GetStoredCapabilitiesUseCase.Params(accountName))
            val spacesAvailableForAccount = capabilities?.isSpacesAllowed() == true
            _spacesAreAllowed.postValue(spacesAvailableForAccount)
        }
    }

    fun refreshFolderUseCase(
        folderToSync: OCFile,
    ) = runUseCaseWithResult(
        coroutineDispatcher = coroutinesDispatcherProvider.io,
        showLoading = true,
        liveData = _syncFolderLiveData,
        useCase = synchronizeFolderUseCase,
        useCaseParams = SynchronizeFolderUseCase.Params(
            accountName = folderToSync.owner,
            remotePath = folderToSync.remotePath,
            spaceId = folderToSync.spaceId,
            syncMode = SynchronizeFolderUseCase.SyncFolderMode.REFRESH_FOLDER
        )
    )

}
