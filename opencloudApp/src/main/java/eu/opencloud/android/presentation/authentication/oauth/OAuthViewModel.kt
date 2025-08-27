/**
 * openCloud Android client application
 *
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

package eu.opencloud.android.presentation.authentication.oauth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import eu.opencloud.android.MainApp
import eu.opencloud.android.domain.authentication.oauth.OIDCDiscoveryUseCase
import eu.opencloud.android.domain.authentication.oauth.RegisterClientUseCase
import eu.opencloud.android.domain.authentication.oauth.RequestTokenUseCase
import eu.opencloud.android.domain.authentication.oauth.model.ClientRegistrationInfo
import eu.opencloud.android.domain.authentication.oauth.model.OIDCServerConfiguration
import eu.opencloud.android.domain.authentication.oauth.model.TokenRequest
import eu.opencloud.android.domain.authentication.oauth.model.TokenResponse
import eu.opencloud.android.domain.utils.Event
import eu.opencloud.android.extensions.ViewModelExt.runUseCaseWithResult
import eu.opencloud.android.presentation.common.UIResult
import eu.opencloud.android.providers.CoroutinesDispatcherProvider

class OAuthViewModel(
    private val getOIDCDiscoveryUseCase: OIDCDiscoveryUseCase,
    private val requestTokenUseCase: RequestTokenUseCase,
    private val registerClientUseCase: RegisterClientUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider
) : ViewModel() {

    val codeVerifier: String = OAuthUtils().generateRandomCodeVerifier()
    val codeChallenge: String = OAuthUtils().generateCodeChallenge(codeVerifier)
    val oidcState: String = OAuthUtils().generateRandomState()

    private val _oidcDiscovery = MediatorLiveData<Event<UIResult<OIDCServerConfiguration>>>()
    val oidcDiscovery: LiveData<Event<UIResult<OIDCServerConfiguration>>> = _oidcDiscovery

    private val _registerClient = MediatorLiveData<Event<UIResult<ClientRegistrationInfo>>>()
    val registerClient: LiveData<Event<UIResult<ClientRegistrationInfo>>> = _registerClient

    private val _requestToken = MediatorLiveData<Event<UIResult<TokenResponse>>>()
    val requestToken: LiveData<Event<UIResult<TokenResponse>>> = _requestToken

    fun getOIDCServerConfiguration(
        serverUrl: String
    ) = runUseCaseWithResult(
        coroutineDispatcher = coroutinesDispatcherProvider.io,
        showLoading = false,
        liveData = _oidcDiscovery,
        useCase = getOIDCDiscoveryUseCase,
        useCaseParams = OIDCDiscoveryUseCase.Params(baseUrl = serverUrl)
    )

    fun registerClient(
        registrationEndpoint: String
    ) {
        val registrationRequest = OAuthUtils.buildClientRegistrationRequest(
            registrationEndpoint = registrationEndpoint,
            MainApp.appContext
        )

        runUseCaseWithResult(
            coroutineDispatcher = coroutinesDispatcherProvider.io,
            showLoading = false,
            liveData = _registerClient,
            useCase = registerClientUseCase,
            useCaseParams = RegisterClientUseCase.Params(registrationRequest)
        )
    }

    fun requestToken(
        tokenRequest: TokenRequest
    ) = runUseCaseWithResult(
        coroutineDispatcher = coroutinesDispatcherProvider.io,
        showLoading = false,
        liveData = _requestToken,
        useCase = requestTokenUseCase,
        useCaseParams = RequestTokenUseCase.Params(tokenRequest = tokenRequest)
    )
}
