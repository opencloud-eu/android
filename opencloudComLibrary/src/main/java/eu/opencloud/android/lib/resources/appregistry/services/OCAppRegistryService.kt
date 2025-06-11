/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2023 ownCloud GmbH.
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

package eu.opencloud.android.lib.resources.appregistry.services

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.appregistry.CreateRemoteFileWithAppProviderOperation
import eu.opencloud.android.lib.resources.appregistry.GetRemoteAppRegistryOperation
import eu.opencloud.android.lib.resources.appregistry.GetUrlToOpenInWebRemoteOperation
import eu.opencloud.android.lib.resources.appregistry.responses.AppRegistryResponse

class OCAppRegistryService(override val client: OpenCloudClient) : AppRegistryService {
    override fun getAppRegistry(appUrl: String?): RemoteOperationResult<AppRegistryResponse> =
        GetRemoteAppRegistryOperation(appUrl).execute(client)

    override fun getUrlToOpenInWeb(openWebEndpoint: String, fileId: String, appName: String): RemoteOperationResult<String> =
        GetUrlToOpenInWebRemoteOperation(
            openWithWebEndpoint = openWebEndpoint,
            fileId = fileId,
            appName = appName
        ).execute(client)

    override fun createFileWithAppProvider(
        createFileWithAppProviderEndpoint: String,
        parentContainerId: String,
        filename: String
    ): RemoteOperationResult<String> =
        CreateRemoteFileWithAppProviderOperation(
            createFileWithAppProviderEndpoint = createFileWithAppProviderEndpoint,
            parentContainerId = parentContainerId,
            filename = filename,
        ).execute(client)
}
