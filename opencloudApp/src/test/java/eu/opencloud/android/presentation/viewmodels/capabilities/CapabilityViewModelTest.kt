/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * @author Abel García de Prada
 * Copyright (C) 2020 ownCloud GmbH.
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

package eu.opencloud.android.presentation.viewmodels.capabilities

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.capabilities.model.OCCapability
import eu.opencloud.android.domain.capabilities.usecases.GetCapabilitiesAsLiveDataUseCase
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.capabilities.usecases.RefreshCapabilitiesFromServerAsyncUseCase
import eu.opencloud.android.domain.utils.Event
import eu.opencloud.android.presentation.common.UIResult
import eu.opencloud.android.presentation.capabilities.CapabilityViewModel
import eu.opencloud.android.providers.ContextProvider
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_CAPABILITY
import eu.opencloud.android.testutil.livedata.getLastEmittedValue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@ExperimentalCoroutinesApi
class CapabilityViewModelTest {
    private lateinit var capabilityViewModel: CapabilityViewModel

    private lateinit var getCapabilitiesAsLiveDataUseCase: GetCapabilitiesAsLiveDataUseCase
    private lateinit var refreshCapabilitiesFromServerUseCase: RefreshCapabilitiesFromServerAsyncUseCase
    private lateinit var getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase
    private lateinit var ocContextProvider: ContextProvider

    private val capabilitiesLiveData = MutableLiveData<OCCapability>()

    private val testCoroutineDispatcher = TestCoroutineDispatcher()
    private val coroutineDispatcherProvider: CoroutinesDispatcherProvider = CoroutinesDispatcherProvider(
        io = testCoroutineDispatcher,
        main = testCoroutineDispatcher,
        computation = testCoroutineDispatcher
    )

    private var testAccountName = OC_ACCOUNT_NAME

    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(testCoroutineDispatcher)
        ocContextProvider = mockk(relaxed = true)

        // To do: Add tests when is not connected
        every { ocContextProvider.isConnected() } returns true

        Dispatchers.setMain(testCoroutineDispatcher)
        startKoin {
            allowOverride(override = true)
            modules(
                module {
                    factory {
                        ocContextProvider
                    }
                })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testCoroutineDispatcher.cleanupTestCoroutines()

        stopKoin()
        unmockkAll()
    }

    private fun initTest() {
        getCapabilitiesAsLiveDataUseCase = spyk(mockkClass(GetCapabilitiesAsLiveDataUseCase::class))
        refreshCapabilitiesFromServerUseCase = spyk(mockkClass(RefreshCapabilitiesFromServerAsyncUseCase::class))
        getStoredCapabilitiesUseCase = spyk(mockkClass(GetStoredCapabilitiesUseCase::class))

        every { getCapabilitiesAsLiveDataUseCase(any()) } returns capabilitiesLiveData

        capabilityViewModel = CapabilityViewModel(
            accountName = testAccountName,
            getCapabilitiesAsLiveDataUseCase = getCapabilitiesAsLiveDataUseCase,
            refreshCapabilitiesFromServerAsyncUseCase = refreshCapabilitiesFromServerUseCase,
            getStoredCapabilitiesUseCase = getStoredCapabilitiesUseCase,
            coroutineDispatcherProvider = coroutineDispatcherProvider
        )
    }

    @Test
    fun getCapabilitiesAsLiveDataWithData() {
        initTest()

        val capability = OC_CAPABILITY.copy(accountName = testAccountName)

        getCapabilitiesAsLiveDataVerification(
            valueToTest = capability,
            expectedValue = Event(UIResult.Success(capability))
        )
    }

    @Test
    fun getCapabilitiesAsLiveDataWithoutData() {
        initTest()

        getCapabilitiesAsLiveDataVerification(
            valueToTest = null,
            expectedValue = Event(UIResult.Success(null))
        )
    }

    private fun getCapabilitiesAsLiveDataVerification(
        valueToTest: OCCapability?,
        expectedValue: Event<UIResult<OCCapability>>?
    ) {
        capabilitiesLiveData.postValue(valueToTest)

        val value = capabilityViewModel.capabilities.getLastEmittedValue()
        assertEquals(expectedValue, value)

        // Calls performed during OCCapabilityViewModel initialization
        verify(exactly = 1) { getCapabilitiesAsLiveDataUseCase(any()) }
        verify(exactly = 1) { refreshCapabilitiesFromServerUseCase(any()) }
    }

    @Test
    fun fetchCapabilitiesSuccess() {
        fetchCapabilitiesVerification(
            useCaseResult = UseCaseResult.Success(Unit),
            expectedValue = Event(UIResult.Loading())
        )
    }

    @Test
    fun fetchCapabilitiesError() {
        val error = Throwable()
        fetchCapabilitiesVerification(
            useCaseResult = UseCaseResult.Error(error),
            expectedValue = Event(UIResult.Error(error))
        )
    }

    private fun fetchCapabilitiesVerification(
        useCaseResult: UseCaseResult<Unit>,
        expectedValue: Event<UIResult<Unit>?>
    ) {
        initTest()
        coEvery { refreshCapabilitiesFromServerUseCase(any()) } returns useCaseResult

        capabilityViewModel.refreshCapabilitiesFromNetwork()

        val value = capabilityViewModel.capabilities.getLastEmittedValue()
        assertEquals(expectedValue, value)

        coVerify(exactly = 2) { refreshCapabilitiesFromServerUseCase(any()) }
        verify(exactly = 1) { getCapabilitiesAsLiveDataUseCase(any()) }
    }
}
