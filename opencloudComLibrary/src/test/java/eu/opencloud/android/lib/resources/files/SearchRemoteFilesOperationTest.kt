/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2026 ownCloud GmbH.
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
package eu.opencloud.android.lib.resources.files

import android.accounts.Account
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import at.bitfire.dav4jvm.PropertyRegistry
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.accounts.AccountUtils
import eu.opencloud.android.lib.common.http.methods.webdav.properties.OCFileId
import eu.opencloud.android.lib.common.http.methods.webdav.properties.OCSpaceId
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], manifest = Config.NONE)
class SearchRemoteFilesOperationTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenCloudClient
    private lateinit var context: Context
    private val accountType = "com.example"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        PropertyRegistry.register(OCFileId.Factory())
        PropertyRegistry.register(OCSpaceId.Factory())

        context = ApplicationProvider.getApplicationContext()
        val base = server.url("/").toString().trimEnd('/')
        val account = Account("alice@server", accountType)
        val am = android.accounts.AccountManager.get(context)
        am.addAccountExplicitly(account, null, null)
        am.setUserData(account, AccountUtils.Constants.KEY_OC_BASE_URL, base)
        am.setUserData(account, AccountUtils.Constants.KEY_ID, "alice")
        val ocAccount = OpenCloudAccount(account, context)
        client = OpenCloudClient(
            android.net.Uri.parse(base),
            null,
            false,
            null,
            context
        ).apply {
            setAccount(ocAccount)
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `search files across all spaces success`() {
        val multiStatusXml = """<?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
                <d:response>
                    <d:href>/remote.php/dav/spaces/space-123/Documents/contract.pdf</d:href>
                    <d:propstat>
                        <d:status>HTTP/1.1 200 OK</d:status>
                        <d:prop>
                            <d:getcontenttype>application/pdf</d:getcontenttype>
                            <d:getcontentlength>45678</d:getcontentlength>
                            <d:getetag>"etag123"</d:getetag>
                            <oc:fileid>fileid-pdf-1</oc:fileid>
                            <oc:permissions>RDNVW</oc:permissions>
                        </d:prop>
                    </d:propstat>
                </d:response>
            </d:multistatus>
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody(multiStatusXml)
        )

        val operation = SearchRemoteFilesOperation(
            searchQuery = "contract",
            spaceId = null,
        )

        val result = operation.execute(client)

        assertTrue(result.isSuccess)
        val files = result.data!!
        assertEquals(1, files.size)
        val file = files.first()
        assertEquals("/Documents/contract.pdf", file.remotePath)
        assertEquals("space-123", file.spaceId)
        assertEquals("fileid-pdf-1", file.remoteId)
        assertEquals("application/pdf", file.mimeType)
        assertEquals(45678L, file.length)

        val request = server.takeRequest()
        assertEquals("REPORT", request.method)
        assertEquals("/remote.php/dav/spaces", request.path)
    }

    @Test
    fun `search files falls back to legacy user endpoint on 404`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
        )

        val multiStatusXml = """<?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
                <d:response>
                    <d:href>/remote.php/dav/files/alice/notes.txt</d:href>
                    <d:propstat>
                        <d:status>HTTP/1.1 200 OK</d:status>
                        <d:prop>
                            <d:getcontenttype>text/plain</d:getcontenttype>
                            <d:getcontentlength>100</d:getcontentlength>
                            <d:getetag>"etag-txt"</d:getetag>
                            <oc:fileid>fileid-notes</oc:fileid>
                        </d:prop>
                    </d:propstat>
                </d:response>
            </d:multistatus>
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody(multiStatusXml)
        )

        val operation = SearchRemoteFilesOperation(
            searchQuery = "notes",
            spaceId = null,
        )

        val result = operation.execute(client)

        val firstRequest = server.takeRequest()
        assertEquals("/remote.php/dav/spaces", firstRequest.path)

        assertTrue(result.isSuccess)
        val files = result.data!!
        assertEquals(1, files.size)
        assertEquals("/notes.txt", files.first().remotePath)

        val secondRequest = server.takeRequest()
        assertTrue(secondRequest.path!!.startsWith("/remote.php/dav/files/"))
    }
}
