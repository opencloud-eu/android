package eu.opencloud.android.data

import android.accounts.AccountManager
import android.content.Context
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.lib.common.ConnectionValidator
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ClientManagerTest {

    private val accountManager: AccountManager = mockk()
    private val preferencesProvider: SharedPreferencesProvider = mockk()
    private val context: Context = mockk(relaxed = true)
    private val connectionValidator: ConnectionValidator = mockk()
    private lateinit var clientManager: ClientManager

    @Before
    fun setUp() {
        mockkStatic(android.net.Uri::class)
        val uriMock = mockk<android.net.Uri>()
        io.mockk.every { android.net.Uri.parse(any()) } returns uriMock
        io.mockk.every { uriMock.toString() } returns "https://demo.opencloud.eu"

        clientManager = ClientManager(
            accountManager,
            preferencesProvider,
            context,
            "eu.opencloud.android.account",
            connectionValidator
        )
    }

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkStatic(android.net.Uri::class)
    }

    @Test
    fun `getClientForAnonymousCredentials reuses client and resets credentials`() {
        val url = "https://demo.opencloud.eu"
        val mockClient = mockk<eu.opencloud.android.lib.common.OpenCloudClient>(relaxed = true)
        val uriMock = android.net.Uri.parse(url)

        io.mockk.every { mockClient.baseUri } returns uriMock

        // Inject mock client into clientManager
        val field = ClientManager::class.java.getDeclaredField("openCloudClient")
        field.isAccessible = true
        field.set(clientManager, mockClient)

        // Call method - should reuse mockClient
        val resultClient = clientManager.getClientForAnonymousCredentials(url, false)

        assertEquals("Client should be reused", mockClient, resultClient)

        // Verify credentials were set
        io.mockk.verify { mockClient.credentials = any() }
    }
}
