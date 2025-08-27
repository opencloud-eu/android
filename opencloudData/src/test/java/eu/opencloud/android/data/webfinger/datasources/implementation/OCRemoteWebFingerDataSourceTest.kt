/**
 * openCloud Android client application
 *
 * @author Aitor Ballesteros Pavón
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

package eu.opencloud.android.data.webfinger.datasources.implementation

import eu.opencloud.android.data.ClientManager
import eu.opencloud.android.domain.webfinger.model.WebFingerRel
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.webfinger.services.implementation.OCWebFingerService
import eu.opencloud.android.testutil.OC_ACCESS_TOKEN
import eu.opencloud.android.testutil.OC_ACCOUNT_ID
import eu.opencloud.android.testutil.OC_SECURE_SERVER_INFO_BASIC_AUTH
import eu.opencloud.android.utils.createRemoteOperationResultMock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OCRemoteWebFingerDataSourceTest {

    private lateinit var ocRemoteWebFingerDatasource: OCRemoteWebFingerDataSource

    private val clientManager: ClientManager = mockk(relaxed = true)
    private val openCloudClient: OpenCloudClient = mockk(relaxed = true)
    private val ocWebFingerService: OCWebFingerService = mockk()
    private val urls: List<String> = listOf(
        "http://webfinger.opencloud/tests/server-instance1",
        "http://webfinger.opencloud/tests/server-instance2",
        "http://webfinger.opencloud/tests/server-instance3",
    )

    @Before
    fun setUp() {
        ocRemoteWebFingerDatasource = OCRemoteWebFingerDataSource(
            ocWebFingerService,
            clientManager,
        )

        every { clientManager.getClientForAnonymousCredentials(OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl, false) } returns openCloudClient

    }

    @Test
    fun `getInstancesFromWebFinger returns a list of String of web finger urls`() {

        val getInstancesFromWebFingerResult: RemoteOperationResult<List<String>> =
            createRemoteOperationResultMock(data = urls, isSuccess = true)

        every {
            ocWebFingerService.getInstancesFromWebFinger(
                lookupServer = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
                resource = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
                rel = WebFingerRel.OIDC_ISSUER_DISCOVERY.uri,
                openCloudClient,
            )
        } returns getInstancesFromWebFingerResult

        val actualResult = ocRemoteWebFingerDatasource.getInstancesFromWebFinger(
            lookupServer = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
            rel = WebFingerRel.OIDC_ISSUER_DISCOVERY,
            resource = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
        )

        assertEquals(getInstancesFromWebFingerResult.data, actualResult)

        verify(exactly = 1) {
            clientManager.getClientForAnonymousCredentials(any(), false)
            ocWebFingerService.getInstancesFromWebFinger(
                lookupServer = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
                resource = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
                rel = WebFingerRel.OIDC_ISSUER_DISCOVERY.uri,
                openCloudClient,
            )
        }
    }

    @Test
    fun `getInstancesFromAuthenticatedWebFinger returns a list of String of web finger urls`() {

        val getInstancesFromAuthenticatedWebFingerResult: RemoteOperationResult<List<String>> =
            createRemoteOperationResultMock(data = urls, isSuccess = true)

        every {
            ocWebFingerService.getInstancesFromWebFinger(
                lookupServer = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
                resource = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
                rel = WebFingerRel.OIDC_ISSUER_DISCOVERY.uri,
                openCloudClient,
            )
        } returns getInstancesFromAuthenticatedWebFingerResult

        val actualResult = ocRemoteWebFingerDatasource.getInstancesFromAuthenticatedWebFinger(
            lookupServer = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
            rel = WebFingerRel.OIDC_ISSUER_DISCOVERY,
            resource = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
            username = OC_ACCOUNT_ID,
            accessToken = OC_ACCESS_TOKEN
        )

        assertEquals(getInstancesFromAuthenticatedWebFingerResult.data, actualResult)

        verify(exactly = 1) {
            openCloudClient.credentials = any()
            clientManager.getClientForAnonymousCredentials(any(), false)
            ocWebFingerService.getInstancesFromWebFinger(
                lookupServer = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
                resource = OC_SECURE_SERVER_INFO_BASIC_AUTH.baseUrl,
                rel = WebFingerRel.OIDC_ISSUER_DISCOVERY.uri,
                any(),
            )
        }
    }
}
