/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
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

package eu.opencloud.android.presentation.viewmodels.authentication

import eu.opencloud.android.R
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.authentication.oauth.RegisterClientUseCase
import eu.opencloud.android.domain.authentication.oauth.RequestTokenUseCase
import eu.opencloud.android.domain.authentication.usecases.GetBaseUrlUseCase
import eu.opencloud.android.domain.authentication.usecases.LoginBasicAsyncUseCase
import eu.opencloud.android.domain.authentication.usecases.LoginOAuthAsyncUseCase
import eu.opencloud.android.domain.authentication.usecases.SupportsOAuth2UseCase
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.capabilities.usecases.RefreshCapabilitiesFromServerAsyncUseCase
import eu.opencloud.android.domain.exceptions.ServerNotReachableException
import eu.opencloud.android.domain.server.usecases.GetServerInfoAsyncUseCase
import eu.opencloud.android.domain.spaces.usecases.RefreshSpacesFromServerAsyncUseCase
import eu.opencloud.android.domain.utils.Event
import eu.opencloud.android.domain.webfinger.usecases.GetOpenCloudInstanceFromWebFingerUseCase
import eu.opencloud.android.domain.webfinger.usecases.GetOpenCloudInstancesFromAuthenticatedWebFingerUseCase
import eu.opencloud.android.presentation.authentication.AuthenticationViewModel
import eu.opencloud.android.presentation.authentication.oauth.OAuthUtils
import eu.opencloud.android.presentation.common.UIResult
import eu.opencloud.android.presentation.viewmodels.ViewModelTest
import eu.opencloud.android.providers.ContextProvider
import eu.opencloud.android.providers.WorkManagerProvider
import eu.opencloud.android.testutil.OC_ACCESS_TOKEN
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_AUTH_TOKEN_TYPE
import eu.opencloud.android.testutil.OC_SECURE_BASE_URL
import eu.opencloud.android.testutil.OC_BASIC_PASSWORD
import eu.opencloud.android.testutil.OC_BASIC_USERNAME
import eu.opencloud.android.testutil.OC_REFRESH_TOKEN
import eu.opencloud.android.testutil.OC_SCOPE
import eu.opencloud.android.testutil.OC_SECURE_SERVER_INFO_BASIC_AUTH
import eu.opencloud.android.testutil.OC_SECURE_SERVER_INFO_BEARER_AUTH
import eu.opencloud.android.testutil.OC_SECURE_SERVER_INFO_BEARER_AUTH_WEBFINGER_INSTANCE
import eu.opencloud.android.testutil.OC_WEBFINGER_INSTANCE_URL
import eu.opencloud.android.testutil.oauth.OC_CLIENT_REGISTRATION
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@ExperimentalCoroutinesApi
class AuthenticationViewModelTest : ViewModelTest() {
    private lateinit var authenticationViewModel: AuthenticationViewModel

    private lateinit var loginBasicAsyncUseCase: LoginBasicAsyncUseCase
    private lateinit var loginOAuthAsyncUseCase: LoginOAuthAsyncUseCase
    private lateinit var getServerInfoAsyncUseCase: GetServerInfoAsyncUseCase
    private lateinit var supportsOAuth2UseCase: SupportsOAuth2UseCase
    private lateinit var getBaseUrlUseCase: GetBaseUrlUseCase
    private lateinit var getOpenCloudInstanceFromWebFingerUseCase: GetOpenCloudInstanceFromWebFingerUseCase
    private lateinit var getOpenCloudInstancesFromAuthenticatedWebFingerUseCase: GetOpenCloudInstancesFromAuthenticatedWebFingerUseCase
    private lateinit var refreshSpacesFromServerAsyncUseCase: RefreshSpacesFromServerAsyncUseCase
    private lateinit var refreshCapabilitiesFromServerAsyncUseCase: RefreshCapabilitiesFromServerAsyncUseCase
    private lateinit var getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase
    private lateinit var requestTokenUseCase: RequestTokenUseCase
    private lateinit var registerClientUseCase: RegisterClientUseCase
    private lateinit var workManagerProvider: WorkManagerProvider
    private lateinit var contextProvider: ContextProvider

    private val commonException = ServerNotReachableException()

    @Before
    fun setUp() {
        contextProvider = mockk()

        every { contextProvider.isConnected() } returns true

        Dispatchers.setMain(testCoroutineDispatcher)
        startKoin {
            allowOverride(override = true)
            modules(
                module {
                    factory {
                        contextProvider
                    }
                })
        }

        loginBasicAsyncUseCase = mockk()
        loginOAuthAsyncUseCase = mockk()
        getServerInfoAsyncUseCase = mockk()
        supportsOAuth2UseCase = mockk()
        getBaseUrlUseCase = mockk()
        getOpenCloudInstanceFromWebFingerUseCase = mockk()
        getOpenCloudInstancesFromAuthenticatedWebFingerUseCase = mockk()
        refreshCapabilitiesFromServerAsyncUseCase = mockk()
        refreshSpacesFromServerAsyncUseCase = mockk()
        workManagerProvider = mockk(relaxUnitFun = true)
        getStoredCapabilitiesUseCase = mockk()
        requestTokenUseCase = mockk()
        registerClientUseCase = mockk()

        mockkConstructor(OAuthUtils::class)
        every { anyConstructed<OAuthUtils>().generateRandomCodeVerifier() } returns "CODE VERIFIER"
        every { anyConstructed<OAuthUtils>().generateCodeChallenge(any()) } returns "CODE CHALLENGE"
        every { anyConstructed<OAuthUtils>().generateRandomState() } returns "STATE"
        every { contextProvider.getBoolean(R.bool.enforce_secure_connection) } returns false
        every { contextProvider.getBoolean(R.bool.enforce_oidc) } returns false

        testCoroutineDispatcher.pauseDispatcher()

        authenticationViewModel = AuthenticationViewModel(
            loginBasicAsyncUseCase = loginBasicAsyncUseCase,
            loginOAuthAsyncUseCase = loginOAuthAsyncUseCase,
            getServerInfoAsyncUseCase = getServerInfoAsyncUseCase,
            supportsOAuth2UseCase = supportsOAuth2UseCase,
            getBaseUrlUseCase = getBaseUrlUseCase,
            getOpenCloudInstanceFromWebFingerUseCase = getOpenCloudInstanceFromWebFingerUseCase,
            getOpenCloudInstancesFromAuthenticatedWebFingerUseCase = getOpenCloudInstancesFromAuthenticatedWebFingerUseCase,
            refreshCapabilitiesFromServerAsyncUseCase = refreshCapabilitiesFromServerAsyncUseCase,
            refreshSpacesFromServerAsyncUseCase = refreshSpacesFromServerAsyncUseCase,
            getStoredCapabilitiesUseCase = getStoredCapabilitiesUseCase,
            requestTokenUseCase = requestTokenUseCase,
            registerClientUseCase = registerClientUseCase,
            workManagerProvider = workManagerProvider,
            coroutinesDispatcherProvider = coroutineDispatcherProvider,
            contextProvider = contextProvider,
        )
    }

    @After
    override fun tearDown() {
        super.tearDown()
        stopKoin()
    }

    @Test
    fun getServerInfoOk() {
        every { getServerInfoAsyncUseCase(any()) } returns UseCaseResult.Success(OC_SECURE_SERVER_INFO_BASIC_AUTH)
        authenticationViewModel.getServerInfo(OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl)

        assertEmittedValues(
            expectedValues = listOf(
                Event(UIResult.Loading()),
                Event(UIResult.Success(OC_SECURE_SERVER_INFO_BASIC_AUTH))
            ),
            liveData = authenticationViewModel.serverInfo
        )
    }

    @Test
    fun getServerInfoException() {
        every { getServerInfoAsyncUseCase(any()) } returns UseCaseResult.Error(commonException)
        authenticationViewModel.getServerInfo(OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl)

        assertEmittedValues(
            expectedValues = listOf(
                Event(UIResult.Loading()),
                Event(UIResult.Error(commonException))
            ),
            liveData = authenticationViewModel.serverInfo
        )
    }

    @Test
    fun loginBasicOk() {
        every { loginBasicAsyncUseCase(any()) } returns UseCaseResult.Success(OC_BASIC_USERNAME)
        authenticationViewModel.loginBasic(OC_BASIC_USERNAME, OC_BASIC_PASSWORD, OC_ACCOUNT_NAME)

        assertEmittedValues(
            expectedValues = listOf(
                Event(UIResult.Loading()),
                Event(UIResult.Success(OC_BASIC_USERNAME))
            ),
            liveData = authenticationViewModel.loginResult
        )
    }

    @Test
    fun loginBasicException() {
        every { loginBasicAsyncUseCase(any()) } returns UseCaseResult.Error(commonException)
        authenticationViewModel.loginBasic(OC_BASIC_USERNAME, OC_BASIC_PASSWORD, null)

        assertEmittedValues(
            expectedValues = listOf(
                Event(UIResult.Loading()),
                Event(UIResult.Error(commonException))
            ),
            liveData = authenticationViewModel.loginResult
        )
    }

    @Test
    fun loginOAuthWebFingerInstancesOk() {
        every { getServerInfoAsyncUseCase(any()) } returns UseCaseResult.Success(OC_SECURE_SERVER_INFO_BEARER_AUTH)
        authenticationViewModel.getServerInfo(OC_SECURE_SERVER_INFO_BEARER_AUTH.baseUrl)

        every { loginOAuthAsyncUseCase(any()) } returns UseCaseResult.Success(OC_BASIC_USERNAME)
        every { getOpenCloudInstancesFromAuthenticatedWebFingerUseCase(any()) } returns UseCaseResult.Success(listOf(OC_WEBFINGER_INSTANCE_URL))

        authenticationViewModel.loginOAuth(
            serverBaseUrl = OC_SECURE_BASE_URL,
            username = OC_BASIC_USERNAME,
            authTokenType = OC_AUTH_TOKEN_TYPE,
            accessToken = OC_ACCESS_TOKEN,
            refreshToken = OC_REFRESH_TOKEN,
            scope = OC_SCOPE,
            clientRegistrationInfo = OC_CLIENT_REGISTRATION
        )

        assertEmittedValues(
            expectedValues = listOf(
                Event(UIResult.Loading()),
                Event(UIResult.Success(OC_BASIC_USERNAME))
            ),
            liveData = authenticationViewModel.loginResult
        )

        verify(exactly = 1) {
            loginOAuthAsyncUseCase(
                params = LoginOAuthAsyncUseCase.Params(
                    serverInfo = OC_SECURE_SERVER_INFO_BEARER_AUTH_WEBFINGER_INSTANCE,
                    username = OC_BASIC_USERNAME,
                    authTokenType = OC_AUTH_TOKEN_TYPE,
                    accessToken = OC_ACCESS_TOKEN,
                    refreshToken = OC_REFRESH_TOKEN,
                    scope = OC_SCOPE,
                    updateAccountWithUsername = null,
                    clientRegistrationInfo = OC_CLIENT_REGISTRATION
                )
            )
        }
    }

    @Test
    fun loginOAuthOk() {
        every { getServerInfoAsyncUseCase(any()) } returns UseCaseResult.Success(OC_SECURE_SERVER_INFO_BEARER_AUTH)
        authenticationViewModel.getServerInfo(OC_SECURE_SERVER_INFO_BEARER_AUTH.baseUrl)

        every { loginOAuthAsyncUseCase(any()) } returns UseCaseResult.Success(OC_BASIC_USERNAME)
        every { getOpenCloudInstancesFromAuthenticatedWebFingerUseCase(any()) } returns UseCaseResult.Error(commonException)

        authenticationViewModel.loginOAuth(
            serverBaseUrl = OC_SECURE_BASE_URL,
            username = OC_BASIC_USERNAME,
            authTokenType = OC_AUTH_TOKEN_TYPE,
            accessToken = OC_ACCESS_TOKEN,
            refreshToken = OC_REFRESH_TOKEN,
            scope = OC_SCOPE,
            clientRegistrationInfo = OC_CLIENT_REGISTRATION
        )

        assertEmittedValues(
            expectedValues = listOf(
                Event(UIResult.Loading()),
                Event(UIResult.Success(OC_BASIC_USERNAME))
            ),
            liveData = authenticationViewModel.loginResult
        )

        verify(exactly = 1) {
            loginOAuthAsyncUseCase(
                params = LoginOAuthAsyncUseCase.Params(
                    serverInfo = OC_SECURE_SERVER_INFO_BEARER_AUTH,
                    username = OC_BASIC_USERNAME,
                    authTokenType = OC_AUTH_TOKEN_TYPE,
                    accessToken = OC_ACCESS_TOKEN,
                    refreshToken = OC_REFRESH_TOKEN,
                    scope = OC_SCOPE,
                    updateAccountWithUsername = null,
                    clientRegistrationInfo = OC_CLIENT_REGISTRATION
                )
            )
        }
    }

    @Test
    fun loginOAuthException() {
        every { getServerInfoAsyncUseCase(any()) } returns UseCaseResult.Success(OC_SECURE_SERVER_INFO_BEARER_AUTH)
        authenticationViewModel.getServerInfo(OC_SECURE_SERVER_INFO_BEARER_AUTH.baseUrl)

        every { loginOAuthAsyncUseCase(any()) } returns UseCaseResult.Error(commonException)
        every { getOpenCloudInstancesFromAuthenticatedWebFingerUseCase(any()) } returns UseCaseResult.Error(commonException)

        authenticationViewModel.loginOAuth(
            serverBaseUrl = OC_SECURE_BASE_URL,
            username = OC_BASIC_USERNAME,
            authTokenType = OC_AUTH_TOKEN_TYPE,
            accessToken = OC_ACCESS_TOKEN,
            refreshToken = OC_REFRESH_TOKEN,
            scope = OC_SCOPE,
            clientRegistrationInfo = OC_CLIENT_REGISTRATION
        )

        assertEmittedValues(
            expectedValues = listOf(
                Event(UIResult.Loading()),
                Event(UIResult.Error(commonException))
            ),
            liveData = authenticationViewModel.loginResult
        )
    }

    @Test
    fun supportsOAuthOk() {
        every { supportsOAuth2UseCase(any()) } returns UseCaseResult.Success(true)
        authenticationViewModel.supportsOAuth2(OC_BASIC_USERNAME)

        assertEmittedValues(
            expectedValues = listOf<Event<UIResult<Boolean>>>(Event(UIResult.Success(true))),
            liveData = authenticationViewModel.supportsOAuth2
        )
    }

    @Test
    fun supportsOAuthException() {
        every { supportsOAuth2UseCase(any()) } returns UseCaseResult.Error(commonException)
        authenticationViewModel.supportsOAuth2(OC_BASIC_USERNAME)

        assertEmittedValues(
            expectedValues = listOf<Event<UIResult<Boolean>>>(Event(UIResult.Error(commonException))),
            liveData = authenticationViewModel.supportsOAuth2
        )
    }

    @Test
    fun getBaseUrlOk() {
        every { getBaseUrlUseCase(any()) } returns UseCaseResult.Success(OC_SECURE_BASE_URL)
        authenticationViewModel.getBaseUrl(OC_BASIC_USERNAME)

        assertEmittedValues(
            expectedValues = listOf<Event<UIResult<String>>>(Event(UIResult.Success(OC_SECURE_BASE_URL))),
            liveData = authenticationViewModel.baseUrl
        )
    }

    @Test
    fun getBaseUrlException() {
        every { getBaseUrlUseCase(any()) } returns UseCaseResult.Error(commonException)
        authenticationViewModel.getBaseUrl(OC_BASIC_USERNAME)

        assertEmittedValues(
            expectedValues = listOf<Event<UIResult<String>>>(Event(UIResult.Error(commonException))),
            liveData = authenticationViewModel.baseUrl
        )
    }
}
