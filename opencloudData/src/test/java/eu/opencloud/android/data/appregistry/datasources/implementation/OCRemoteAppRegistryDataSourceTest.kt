/**
 * openCloud Android client application
 *
 * @author Aitor Ballesteros Pavón
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

package eu.opencloud.android.data.appregistry.datasources.implementation

import eu.opencloud.android.data.ClientManager
import eu.opencloud.android.domain.appregistry.model.AppRegistryProvider
import eu.opencloud.android.lib.resources.appregistry.responses.AppRegistryMimeTypeResponse
import eu.opencloud.android.lib.resources.appregistry.responses.AppRegistryProviderResponse
import eu.opencloud.android.lib.resources.appregistry.responses.AppRegistryResponse
import eu.opencloud.android.lib.resources.appregistry.services.OCAppRegistryService
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_APP_REGISTRY
import eu.opencloud.android.testutil.OC_APP_REGISTRY_RESPONSE
import eu.opencloud.android.testutil.OC_FILE
import eu.opencloud.android.utils.createRemoteOperationResultMock
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OCRemoteAppRegistryDataSourceTest {

    private lateinit var ocRemoteAppRegistryDataSource: OCRemoteAppRegistryDataSource

    private val clientManager: ClientManager = mockk(relaxed = true)
    private val ocAppRegistryService: OCAppRegistryService = mockk()

    private val appUrl = "app/list"
    private val testEndpoint = "app/open-with-web"

    @Before
    fun setUp() {
        every { clientManager.getAppRegistryService(OC_ACCOUNT_NAME) } returns ocAppRegistryService

        ocRemoteAppRegistryDataSource = OCRemoteAppRegistryDataSource(clientManager)
    }

    @Test
    fun `getAppRegistryForAccount returns an AppRegistry`() {
        val getAppRegistryForAccountResult = createRemoteOperationResultMock(
            data = OC_APP_REGISTRY_RESPONSE, isSuccess = true
        )

        every { ocAppRegistryService.getAppRegistry(appUrl) } returns getAppRegistryForAccountResult

        val result = ocRemoteAppRegistryDataSource.getAppRegistryForAccount(OC_ACCOUNT_NAME, appUrl)

        assertEquals(OC_APP_REGISTRY, result)

        verify(exactly = 1) { ocAppRegistryService.getAppRegistry(appUrl) }
    }

    @Test
    fun `getAppRegistryForAccount defaults missing provider product name to provider name`() {
        val response = AppRegistryResponse(
            value = listOf(
                AppRegistryMimeTypeResponse(
                    mimeType = "application/pdf",
                    ext = "pdf",
                    appProviders = listOf(
                        AppRegistryProviderResponse(
                            name = "OnlyOffice",
                            productName = null,
                            icon = "https://some-website.test/onlyoffice-pdf-icon.png",
                        )
                    ),
                    name = "PDF",
                    description = "PDF document",
                )
            )
        )
        val getAppRegistryForAccountResult = createRemoteOperationResultMock(
            data = response, isSuccess = true
        )

        every { ocAppRegistryService.getAppRegistry(appUrl) } returns getAppRegistryForAccountResult

        val result = ocRemoteAppRegistryDataSource.getAppRegistryForAccount(OC_ACCOUNT_NAME, appUrl)

        assertEquals(
            AppRegistryProvider(
                name = "OnlyOffice",
                productName = "OnlyOffice",
                icon = "https://some-website.test/onlyoffice-pdf-icon.png",
            ),
            result.mimetypes.single().appProviders.single()
        )
    }

    @Test
    fun `getUrlToOpenInWeb returns a URL String`() {
        val expectedUrl = "https://example.com/file123"
        val appName = "TestApp"

        val getUrlToOpenInWebResult = createRemoteOperationResultMock(
            data = expectedUrl, isSuccess = true
        )

        every {
            ocAppRegistryService.getUrlToOpenInWeb(
                openWebEndpoint = testEndpoint,
                fileId = OC_FILE.remoteId.toString(),
                appName = appName,
            )
        } returns getUrlToOpenInWebResult

        val result = ocRemoteAppRegistryDataSource.getUrlToOpenInWeb(
            accountName = OC_ACCOUNT_NAME,
            openWebEndpoint = testEndpoint,
            fileId = OC_FILE.remoteId.toString(),
            appName = appName,
        )

        assertEquals(expectedUrl, result)

        verify {
            ocAppRegistryService.getUrlToOpenInWeb(
                openWebEndpoint = testEndpoint,
                fileId = OC_FILE.remoteId.toString(),
                appName = appName,
            )
        }
    }

    @Test
    fun `createFileWithAppProvider returns a URL String to open the file in web`() {
        val expectedFileUrl = "https://example.com/files/testFile.txt"

        val createFileWithAppProviderResult = createRemoteOperationResultMock(
            data = expectedFileUrl, isSuccess = true)

        every {
            ocAppRegistryService.createFileWithAppProvider(
                createFileWithAppProviderEndpoint = testEndpoint,
                parentContainerId = OC_FILE.remoteId.toString(),
                filename = OC_FILE.fileName,
            )
        } returns createFileWithAppProviderResult

        val result = ocRemoteAppRegistryDataSource.createFileWithAppProvider(
            accountName = OC_ACCOUNT_NAME,
            createFileWithAppProviderEndpoint = testEndpoint,
            parentContainerId = OC_FILE.remoteId.toString(),
            filename = OC_FILE.fileName,
        )

        assertEquals(expectedFileUrl, result)

        verify(exactly = 1) {
            ocAppRegistryService.createFileWithAppProvider(
                createFileWithAppProviderEndpoint = testEndpoint,
                parentContainerId = OC_FILE.remoteId.toString(),
                filename = OC_FILE.fileName,
            )
        }
    }
}
