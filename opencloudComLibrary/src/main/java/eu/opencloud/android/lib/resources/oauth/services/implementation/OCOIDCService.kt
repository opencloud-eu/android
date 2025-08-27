/* openCloud Android Library is available under MIT license
 *
 *   Copyright (C) 2021 ownCloud GmbH.
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 *   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 *   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 */
package eu.opencloud.android.lib.resources.oauth.services.implementation

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.oauth.GetOIDCDiscoveryRemoteOperation
import eu.opencloud.android.lib.resources.oauth.RegisterClientRemoteOperation
import eu.opencloud.android.lib.resources.oauth.TokenRequestRemoteOperation
import eu.opencloud.android.lib.resources.oauth.params.ClientRegistrationParams
import eu.opencloud.android.lib.resources.oauth.params.TokenRequestParams
import eu.opencloud.android.lib.resources.oauth.responses.ClientRegistrationResponse
import eu.opencloud.android.lib.resources.oauth.responses.OIDCDiscoveryResponse
import eu.opencloud.android.lib.resources.oauth.responses.TokenResponse
import eu.opencloud.android.lib.resources.oauth.services.OIDCService

class OCOIDCService : OIDCService {

    override fun getOIDCServerDiscovery(
        openCloudClient: OpenCloudClient
    ): RemoteOperationResult<OIDCDiscoveryResponse> =
        GetOIDCDiscoveryRemoteOperation().execute(openCloudClient)

    override fun performTokenRequest(
        openCloudClient: OpenCloudClient,
        tokenRequest: TokenRequestParams
    ): RemoteOperationResult<TokenResponse> =
        TokenRequestRemoteOperation(tokenRequest).execute(openCloudClient)

    override fun registerClientWithRegistrationEndpoint(
        openCloudClient: OpenCloudClient,
        clientRegistrationParams: ClientRegistrationParams
    ): RemoteOperationResult<ClientRegistrationResponse> =
        RegisterClientRemoteOperation(clientRegistrationParams).execute(openCloudClient)

}
