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
package eu.opencloud.android.lib.common.http.methods.webdav

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import at.bitfire.dav4jvm.PropertyRegistry
import eu.opencloud.android.lib.common.http.HttpClient
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
class SearchMethodTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        PropertyRegistry.register(OCFileId.Factory())
        PropertyRegistry.register(OCSpaceId.Factory())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `buildSearchXml generates correct XML structure`() {
        val xml = SearchMethod.buildSearchXml("test-query", 50, SearchMethod.defaultSearchProperties)

        assertTrue(xml.contains("<oc:search-files"))
        assertTrue(xml.contains("<d:prop>"))
        assertTrue(xml.contains("<oc:search>"))
        assertTrue(xml.contains("<oc:pattern>test-query</oc:pattern>"))
        assertTrue(xml.contains("<oc:limit>50</oc:limit>"))
        assertTrue(xml.contains("</oc:search-files>"))
    }

    @Test
    fun `buildSearchXml escapes XML special characters`() {
        val xml = SearchMethod.buildSearchXml("foo & bar <baz>", 25, SearchMethod.defaultSearchProperties)
        assertTrue(xml.contains("foo &amp; bar &lt;baz&gt;"))
    }

    @Test
    fun `execute sends REPORT request and parses multistatus response`() {
        val multiStatusXml = """<?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
                <d:response>
                    <d:href>/remote.php/dav/spaces/space123/Documents/file.txt</d:href>
                    <d:propstat>
                        <d:status>HTTP/1.1 200 OK</d:status>
                        <d:prop>
                            <d:getcontenttype>text/plain</d:getcontenttype>
                            <d:getcontentlength>1234</d:getcontentlength>
                            <d:getetag>"abcd"</d:getetag>
                            <oc:fileid>file-id-123</oc:fileid>
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

        val searchMethod = SearchMethod(
            url = server.url("/remote.php/dav/spaces").toUrl(),
            searchQuery = "file",
            limit = 50,
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val httpClient = object : HttpClient(context) {}

        val statusCode = searchMethod.execute(httpClient)

        assertEquals(207, statusCode)
        assertEquals(1, searchMethod.members.size)

        val recordedRequest = server.takeRequest()
        assertEquals("REPORT", recordedRequest.method)
        assertEquals("/remote.php/dav/spaces", recordedRequest.path)
        assertTrue(recordedRequest.body.readUtf8().contains("<oc:pattern>file</oc:pattern>"))
    }
}
