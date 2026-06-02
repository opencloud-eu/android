/* openCloud Android Library is available under MIT license
 *
 *   Copyright (C) 2026 OpenCloud GmbH.
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

package eu.opencloud.android.lib.resources.shares

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentialsFactory
import eu.opencloud.android.lib.common.http.HttpConstants.CONTENT_TYPE_HEADER
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ShareRemoteOperationTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun createShare_sendsOcsHeaderAndOptionalFormFields() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(shareResponseJson(id = "11", permissions = 19)))

        val operation = CreateRemoteShareOperation(
            remoteFilePath = "/Photos/image.jpg",
            shareType = ShareType.USER,
            shareWith = "user@example.com",
            permissions = 19
        ).apply {
            name = "Vacation"
            password = "secret"
            expirationDateInMillis = 1_735_689_600_000
        }

        val result = operation.execute(newClient())

        assertTrue(result.isSuccess)
        assertEquals("11", result.data.shares.first().id)
        assertEquals(19, result.data.shares.first().permissions)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json", request.path)
        assertEquals("true", request.getHeader("OCS-APIREQUEST"))
        assertEquals("application/x-www-form-urlencoded", request.getHeader(CONTENT_TYPE_HEADER))

        val form = request.formBody()
        assertEquals("/Photos/image.jpg", form["path"])
        assertEquals("0", form["shareType"])
        assertEquals("user@example.com", form["shareWith"])
        assertEquals("19", form["permissions"])
        assertEquals("Vacation", form["name"])
        assertEquals("secret", form["password"])
        assertEquals("2025-01-01", form["expireDate"])
    }

    @Test
    fun updateShare_sendsOnlyRequestedFormFields() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(shareResponseJson(id = "12", permissions = 1)))

        val operation = UpdateRemoteShareOperation(remoteId = "12").apply {
            name = ""
            password = null
            expirationDateInMillis = -1
            permissions = 1
        }

        val result = operation.execute(newClient())

        assertTrue(result.isSuccess)

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/ocs/v2.php/apps/files_sharing/api/v1/shares/12?format=json", request.path)
        assertEquals("true", request.getHeader("OCS-APIREQUEST"))

        val form = request.formBody()
        assertEquals("", form["name"])
        assertEquals("", form["expireDate"])
        assertFalse(form.containsKey("password"))
        assertEquals("1", form["permissions"])
    }

    @Test
    fun createShare_returnsUnsuccessfulResultForServerError() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))

        val result = CreateRemoteShareOperation(
            remoteFilePath = "/Photos/image.jpg",
            shareType = ShareType.USER,
            shareWith = "user@example.com",
            permissions = 1
        ).execute(newClient())

        assertFalse(result.isSuccess)
        assertEquals(500, result.httpCode)
        assertNull(result.data)
    }

    private fun newClient(): OpenCloudClient =
        OpenCloudClient(
            Uri.parse(server.url("/").toString().removeSuffix("/")),
            null,
            true,
            null,
            ApplicationProvider.getApplicationContext()
        ).apply {
            credentials = OpenCloudCredentialsFactory.newBearerCredentials("user", "TOKEN")
        }

    private fun shareResponseJson(id: String, permissions: Int): String =
        """
        {
          "ocs": {
            "meta": {
              "status": "ok",
              "statuscode": 200,
              "message": null,
              "itemsperpage": null,
              "totalitems": null
            },
            "data": {
              "id": "$id",
              "share_with": "user@example.com",
              "path": "/Photos/image.jpg",
              "item_type": "file",
              "share_with_displayname": "User",
              "share_type": 0,
              "permissions": $permissions,
              "stime": 1700000000
            }
          }
        }
        """.trimIndent()

    private fun okhttp3.mockwebserver.RecordedRequest.formBody(): Map<String, String> =
        body.readUtf8()
            .split("&")
            .filter { it.isNotBlank() }
            .associate { entry ->
                val parts = entry.split("=", limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
}
