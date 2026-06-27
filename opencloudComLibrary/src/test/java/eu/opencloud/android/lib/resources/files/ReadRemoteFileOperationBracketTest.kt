package eu.opencloud.android.lib.resources.files

import android.accounts.Account
import android.accounts.AccountManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.accounts.AccountUtils
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentialsFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression test for the NullPointerException reported in opencloud-eu/android#170 and #93:
 * reading a file whose name contains square brackets (e.g. "[B004EXIHDQ].png") returned a
 * 207 Multi-Status, but the single <response> was misclassified as not-SELF (because
 * UrlUtils.equals choked on "[" / "]") so PropfindMethod.root stayed null and
 * ReadRemoteFileOperation crashed on `propFind.root!!`.
 *
 * The mock server deliberately echoes the href with *literal* brackets while the request URL
 * percent-encodes them (%5B / %5D), which is exactly the encoding mismatch that used to make the
 * old java.net.URI fallback throw and return `false`.
 */
@RunWith(RobolectricTestRunner::class)
class ReadRemoteFileOperationBracketTest {

    private lateinit var server: MockWebServer
    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }

    private val accountType = "com.example"
    private val userId = "user-123"
    private val username = "user@example.com"
    private val token = "TEST_TOKEN"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newClient(): OpenCloudClient {
        val base = server.url("/").toString().removeSuffix("/")

        val am = AccountManager.get(context)
        val account = Account("$username@${Uri.parse(base).host}", accountType)
        am.addAccountExplicitly(account, null, null)
        am.setUserData(account, AccountUtils.Constants.KEY_OC_BASE_URL, base)
        am.setUserData(account, AccountUtils.Constants.KEY_ID, userId)

        val ocAccount = OpenCloudAccount(account, context)
        val client = OpenCloudClient(ocAccount.baseUri, null, true, null, context)
        client.account = ocAccount
        client.credentials = OpenCloudCredentialsFactory.newBearerCredentials(username, token)
        return client
    }

    private fun multiStatusBody(href: String): String =
        """<?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response>
                <d:href>$href</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getlastmodified>Mon, 23 Jun 2026 10:00:00 GMT</d:getlastmodified>
                    <d:getcontentlength>12345</d:getcontentlength>
                    <d:getcontenttype>image/png</d:getcontenttype>
                    <d:resourcetype/>
                    <d:getetag>"abc123"</d:getetag>
                    <oc:id>00000001ocidvalue</oc:id>
                    <oc:permissions>RDNVW</oc:permissions>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

    @Test
    fun readFileWithBracketsInNameDoesNotCrash() {
        val client = newClient()
        val remotePath = "/Test/Guards! Guards! [B004EXIHDQ].png"

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Simulate a server that returns the href with *literal* brackets, i.e. a different
                // (but semantically identical) encoding than the request's %5B / %5D.
                val hrefPath = request.path!!
                    .replace("%5B", "[")
                    .replace("%5D", "]")
                return MockResponse()
                    .setResponseCode(207) // HTTP Multi-Status
                    .addHeader("Content-Type", "application/xml; charset=utf-8")
                    .setBody(multiStatusBody(hrefPath))
            }
        }

        val result = ReadRemoteFileOperation(remotePath).execute(client)

        // Before the UrlUtils.equals fix this returned an error wrapping a NullPointerException.
        assertTrue("Expected success but got ${result.code} / ${result.exception}", result.isSuccess)
        assertNotNull(result.data)
        assertEquals("/Test/Guards! Guards! [B004EXIHDQ].png", result.data.remotePath)
    }
}
