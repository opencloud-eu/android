/**
 * openCloud Android client application
 *
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

package eu.opencloud.android.presentation.spaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.model.OCFile.Companion.ROOT_PATH
import eu.opencloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import eu.opencloud.android.domain.spaces.model.OCSpace
import eu.opencloud.android.domain.spaces.usecases.GetPersonalAndProjectSpacesWithSpecialsForAccountAsStreamUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalSpacesWithSpecialsForAccountAsStreamUseCase
import eu.opencloud.android.domain.spaces.usecases.GetProjectSpacesWithSpecialsForAccountAsStreamUseCase
import eu.opencloud.android.domain.spaces.usecases.RefreshSpacesFromServerAsyncUseCase
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpacesListViewModel(
    private val refreshSpacesFromServerAsyncUseCase: RefreshSpacesFromServerAsyncUseCase,
    private val getPersonalSpacesWithSpecialsForAccountAsStreamUseCase: GetPersonalSpacesWithSpecialsForAccountAsStreamUseCase,
    private val getPersonalAndProjectSpacesWithSpecialsForAccountAsStreamUseCase: GetPersonalAndProjectSpacesWithSpecialsForAccountAsStreamUseCase,
    private val getProjectSpacesWithSpecialsForAccountAsStreamUseCase: GetProjectSpacesWithSpecialsForAccountAsStreamUseCase,
    private val getFileByRemotePathUseCase: GetFileByRemotePathUseCase,
    private val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val accountName: String,
    private val showPersonalSpace: Boolean,
) : ViewModel() {

    private val _spacesList: MutableStateFlow<SpacesListUiState> =
        MutableStateFlow(SpacesListUiState(spaces = emptyList(), refreshing = false, error = null, searchFilter = ""))
    val spacesList: StateFlow<SpacesListUiState> = _spacesList

    init {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            refreshSpacesFromServer()
            val capabilities = getStoredCapabilitiesUseCase(GetStoredCapabilitiesUseCase.Params(accountName))
            val isMultiPersonal = capabilities?.spaces?.hasMultiplePersonalSpaces
            val spacesListFlow = if (isMultiPersonal == true) getPersonalSpacesWithSpecialsForAccountAsStreamUseCase(
                GetPersonalSpacesWithSpecialsForAccountAsStreamUseCase.Params(accountName = accountName)
            ) else if (showPersonalSpace) getPersonalAndProjectSpacesWithSpecialsForAccountAsStreamUseCase(
                GetPersonalAndProjectSpacesWithSpecialsForAccountAsStreamUseCase.Params(accountName = accountName)
            ) else getProjectSpacesWithSpecialsForAccountAsStreamUseCase(
                GetProjectSpacesWithSpecialsForAccountAsStreamUseCase.Params(accountName = accountName)
            )
            spacesListFlow.collect { spaces ->
                _spacesList.update { it.copy(spaces = spaces) }
            }
        }
    }

    fun refreshSpacesFromServer() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            _spacesList.update { it.copy(refreshing = true) }
            when (val result = refreshSpacesFromServerAsyncUseCase(RefreshSpacesFromServerAsyncUseCase.Params(accountName))) {
                is UseCaseResult.Success -> _spacesList.update { it.copy(refreshing = false, error = null) }
                is UseCaseResult.Error -> _spacesList.update { it.copy(refreshing = false, error = result.throwable) }
            }
        }
    }

    fun getRootFileForSpace(ocSpace: OCSpace) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = getFileByRemotePathUseCase(
                GetFileByRemotePathUseCase.Params(
                    owner = ocSpace.accountName,
                    remotePath = ROOT_PATH,
                    spaceId = ocSpace.id
                )
            )
            result.getDataOrNull()?.let { rootFolderFromSpace ->
                _spacesList.update { it.copy(rootFolderFromSelectedSpace = rootFolderFromSpace) }
            }
        }
    }

    fun updateSearchFilter(newSearchFilter: String) {
        _spacesList.update { it.copy(searchFilter = newSearchFilter) }
    }

    data class SpacesListUiState(
        val spaces: List<OCSpace>,
        val rootFolderFromSelectedSpace: OCFile? = null,
        val refreshing: Boolean,
        val error: Throwable?,
        val searchFilter: String
    )
}
