/* openCloud Android Library is available under MIT license
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

package eu.opencloud.android.lib.common

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import eu.opencloud.android.lib.common.accounts.AccountUtils
import eu.opencloud.android.lib.common.accounts.TokenRefreshFailedException
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentials
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentialsFactory
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * When a request gets a 401 the validator renews the token. Its outcome decides what the user sees, so a failed
 * renewal must be told apart: a transient problem (no network, IdP down) must not look like an expired session.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], manifest = Config.NONE)
class ConnectionValidatorTest {

    private val context: Context by lazy { ApplicationProvider.getApplicationContext() }
    private lateinit var server: MockWebServer
    private val propfindAuthHeaders: MutableList<String?> = Collections.synchronizedList(mutableListOf())

    private val validator by lazy { ConnectionValidator(context, false) }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/status.php" -> {
                    MockResponse().setResponseCode(200).setBody(STATUS_JSON)
                }
                request.method == "PROPFIND" -> {
                    propfindAuthHeaders.add(request.getHeader("Authorization"))
                    if (request.getHeader("Authorization") == "Bearer $NEW_TOKEN") {
                        MockResponse().setResponseCode(207).setBody(MULTISTATUS)
                    } else {
                        MockResponse().setResponseCode(401)
                    }
                }
                else -> {
                    MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `transient failure renewing the token is not swallowed`() {
        val client = clientWithAccount { throw TokenRefreshFailedException("IdP is down", null) }

        assertThrows(TokenRefreshFailedException::class.java) {
            validator.validate(client, SingleSessionManager.getDefaultSingleton(), context)
        }
        assertEquals("the failed renewal must not trigger a retry of the probe", 1, propfindAuthHeaders.size)
    }

    @Test
    fun `rejected token renewal fails the validation without retrying with stale credentials`() {
        // The authenticator only offers the login screen, so the account ends up with an empty token
        val client = clientWithAccount { bearer("") }

        val valid = validator.validate(client, SingleSessionManager.getDefaultSingleton(), context)

        assertFalse(valid)
        assertEquals(1, propfindAuthHeaders.size)
    }

    @Test
    fun `successful token renewal validates and hands the new token to the client`() {
        val client = clientWithAccount { bearer(NEW_TOKEN) }

        val valid = validator.validate(client, SingleSessionManager.getDefaultSingleton(), context)

        assertTrue(valid)
        assertEquals(NEW_TOKEN, client.credentials.authToken)
    }

    private fun clientWithAccount(onLoadCredentials: () -> OpenCloudCredentials): OpenCloudClient {
        val androidAccount = Account("user@localhost", ACCOUNT_TYPE)
        AccountManager.get(context).addAccountExplicitly(
            androidAccount,
            null,
            Bundle().apply {
                putString(AccountUtils.Constants.KEY_OC_BASE_URL, baseUrl())
                putString(AccountUtils.Constants.KEY_ID, "user")
            }
        )
        val account = ReloadableAccount(androidAccount, context, bearer(OLD_TOKEN), onLoadCredentials)
        return OpenCloudClient(Uri.parse(baseUrl()), validator, true, SingleSessionManager.getDefaultSingleton(), context).apply {
            this.account = account
            credentials = account.credentials
        }
    }

    private fun baseUrl() = server.url("/").toString().removeSuffix("/")

    private fun bearer(token: String) = OpenCloudCredentialsFactory.newBearerCredentials("user", token)

    /** Account whose "reload from the AccountManager" is scripted by the test. */
    private class ReloadableAccount(
        savedAccount: Account,
        context: Context,
        private var currentCredentials: OpenCloudCredentials,
        private val onLoadCredentials: () -> OpenCloudCredentials
    ) : OpenCloudAccount(savedAccount, context) {
        override fun getCredentials(): OpenCloudCredentials = currentCredentials

        override fun loadCredentials(context: Context) {
            currentCredentials = onLoadCredentials()
        }
    }

    companion object {
        private const val ACCOUNT_TYPE = "eu.opencloud.test"
        private const val OLD_TOKEN = "old-token"
        private const val NEW_TOKEN = "new-token"
        private const val STATUS_JSON = """{"installed":true,"version":"10.0.0.0","productversion":"1.0.0"}"""
        private const val MULTISTATUS = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>/remote.php/dav/files/user/</d:href>
    <d:propstat><d:prop/><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
  </d:response>
</d:multistatus>"""
    }
}
