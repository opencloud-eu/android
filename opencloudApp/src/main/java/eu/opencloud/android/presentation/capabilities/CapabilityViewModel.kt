/**
 * openCloud Android client application
 *
 * @author David González Verdugo
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.presentation.capabilities

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import eu.opencloud.android.domain.capabilities.model.OCCapability
import eu.opencloud.android.domain.capabilities.usecases.GetCapabilitiesAsLiveDataUseCase
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.capabilities.usecases.RefreshCapabilitiesFromServerAsyncUseCase
import eu.opencloud.android.domain.utils.Event
import eu.opencloud.android.extensions.ViewModelExt.runUseCaseWithResultAndUseCachedData
import eu.opencloud.android.presentation.common.UIResult
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * View Model to keep a reference to the capability repository and an up-to-date capability
 */
class CapabilityViewModel(
    private val accountName: String,
    getCapabilitiesAsLiveDataUseCase: GetCapabilitiesAsLiveDataUseCase,
    private val refreshCapabilitiesFromServerAsyncUseCase: RefreshCapabilitiesFromServerAsyncUseCase,
    private val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase,
    private val coroutineDispatcherProvider: CoroutinesDispatcherProvider
) : ViewModel() {

    private val _capabilities = MediatorLiveData<Event<UIResult<OCCapability>>>()
    val capabilities: LiveData<Event<UIResult<OCCapability>>> = _capabilities

    private var capabilitiesLiveData: LiveData<OCCapability?> = getCapabilitiesAsLiveDataUseCase(
        GetCapabilitiesAsLiveDataUseCase.Params(
            accountName = accountName
        )
    )

    init {
        _capabilities.addSource(capabilitiesLiveData) { capabilities ->
            _capabilities.postValue(Event(UIResult.Success(capabilities)))
        }

        refreshCapabilitiesFromNetwork()
    }

    fun refreshCapabilitiesFromNetwork() = runUseCaseWithResultAndUseCachedData(
        coroutineDispatcher = coroutineDispatcherProvider.io,
        cachedData = capabilitiesLiveData.value,
        liveData = _capabilities,
        useCase = refreshCapabilitiesFromServerAsyncUseCase,
        useCaseParams = RefreshCapabilitiesFromServerAsyncUseCase.Params(
            accountName = accountName
        )
    )

    fun checkMultiPersonal(): Boolean = runBlocking(CoroutinesDispatcherProvider().io) {
        val capabilities = withContext(CoroutinesDispatcherProvider().io) {
            getStoredCapabilitiesUseCase(GetStoredCapabilitiesUseCase.Params(accountName))
        }
        capabilities?.spaces?.hasMultiplePersonalSpaces == true
    }
}
