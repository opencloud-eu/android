/* openCloud Android Library is available under MIT license
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
*
*/

package eu.opencloud.android.lib

import android.os.Build
import eu.opencloud.android.lib.common.http.methods.nonwebdav.GetMethod
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.status.StatusRequester
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URL

/**
 * The status endpoint is the first thing hit when adding or re-authenticating an account, so the
 * failure it reports is the failure the user sees on the login screen. It used to parse the body as
 * JSON before looking at the status code, which turned every non-JSON error response into a bogus
 * "malformed server configuration".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], manifest = Config.NONE)
class StatusRequesterHandleResultTest {

    private val requester = StatusRequester()

    @Test
    fun `handle request result - ko - forbidden with an html body reports the http error`() {
        val result = requester.handleRequestResult(requestResult(403, CLOUDFLARE_MTLS_ERROR_PAGE, HTML), BASE_URL)

        assertEquals(RemoteOperationResult.ResultCode.FORBIDDEN, result.code)
        assertEquals(403, result.httpCode)
    }

    @Test
    fun `handle request result - ko - bad request with an nginx no-certificate body reports the http error`() {
        val result = requester.handleRequestResult(requestResult(400, NGINX_MTLS_ERROR_PAGE, HTML), BASE_URL)

        assertEquals(RemoteOperationResult.ResultCode.UNHANDLED_HTTP_CODE, result.code)
        assertEquals(400, result.httpCode)
    }

    @Test
    fun `handle request result - ko - bad gateway with an html body reports the http error`() {
        val result = requester.handleRequestResult(requestResult(502, "<html><body>Bad gateway</body></html>", HTML), BASE_URL)

        assertEquals(RemoteOperationResult.ResultCode.UNHANDLED_HTTP_CODE, result.code)
        assertEquals(502, result.httpCode)
    }

    @Test
    fun `handle request result - ko - unauthorized with an empty body reports the http error`() {
        val result = requester.handleRequestResult(requestResult(401, "", HTML), BASE_URL)

        assertEquals(RemoteOperationResult.ResultCode.UNAUTHORIZED, result.code)
    }

    @Test
    fun `handle request result - ko - not installed`() {
        val body = """{"installed":false,"version":"10.0.0.0","productversion":"1.0.0"}"""

        val result = requester.handleRequestResult(requestResult(200, body, JSON), BASE_URL)

        assertEquals(RemoteOperationResult.ResultCode.INSTANCE_NOT_CONFIGURED, result.code)
    }

    @Test
    fun `handle request result - ok - installed over https`() {
        val body = """{"installed":true,"version":"10.0.0.0","productversion":"1.0.0"}"""

        val result = requester.handleRequestResult(requestResult(200, body, JSON), BASE_URL)

        assertEquals(RemoteOperationResult.ResultCode.OK_SSL, result.code)
        assertEquals(BASE_URL, result.data.baseUrl)
    }

    private fun requestResult(code: Int, body: String, contentType: String): StatusRequester.RequestResult {
        val url = URL(STATUS_URL)
        val response = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()
        val getMethod = GetMethod(url).apply { this.response = response }
        return StatusRequester.RequestResult(getMethod, code, STATUS_URL)
    }

    companion object {
        private const val BASE_URL = "https://cloud.somewhere.com"
        private const val STATUS_URL = "$BASE_URL/status.php"
        private const val HTML = "text/html"
        private const val JSON = "application/json"

        /** What Cloudflare returns when the client certificate is missing on an mTLS-protected host. */
        private const val CLOUDFLARE_MTLS_ERROR_PAGE =
            "<html><head><title>403 Forbidden</title></head><body>No required SSL certificate was sent</body></html>"

        /** What nginx returns in the same situation, verbatim from client.badssl.com. Note the unclosed <hr>. */
        private const val NGINX_MTLS_ERROR_PAGE =
            "<html>\n<head><title>400 No required SSL certificate was sent</title></head>\n" +
                    "<body bgcolor=\"white\">\n<center><h1>400 Bad Request</h1></center>\n" +
                    "<center>No required SSL certificate was sent</center>\n" +
                    "<hr><center>nginx/1.10.3 (Ubuntu)</center>\n</body>\n</html>"
    }
}
