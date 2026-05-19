/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2026 openCloud GmbH.
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
package eu.opencloud.android.lib.common.http.methods

import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.URL

class HttpBaseMethodTest {

    @Test
    fun responseBodyPreviewIsTruncatedButFullBodyReadsRemainUnchanged() {
        val fullBody = "a".repeat(HttpBaseMethod.MAX_RESPONSE_BODY_PREVIEW_BYTES.toInt() + 10)
        val method = TestHttpMethod(fullBody.toResponseBody("text/plain".toMediaType()))

        assertEquals(
            HttpBaseMethod.MAX_RESPONSE_BODY_PREVIEW_BYTES.toInt(),
            method.getResponseBodyPreviewAsString().length
        )
        assertEquals(fullBody.length, method.getResponseBodyAsString().length)
    }

    @Test
    fun remoteOperationResultUsesBoundedPreviewForErrorBodies() {
        val preview = "x".repeat(HttpBaseMethod.MAX_RESPONSE_BODY_PREVIEW_BYTES.toInt())
        val method = TestHttpMethod(
            responseBody = FailingAfterPreviewResponseBody(preview),
            statusCode = HttpConstants.HTTP_FORBIDDEN,
        )

        val result = RemoteOperationResult<Any>(method)

        assertEquals(RemoteOperationResult.ResultCode.FORBIDDEN, result.code)
    }

    private class TestHttpMethod(
        responseBody: ResponseBody,
        statusCode: Int = HttpConstants.HTTP_FORBIDDEN,
    ) : HttpBaseMethod(URL("https://example.test")) {
        override var response: Response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message("Forbidden")
            .body(responseBody)
            .build()

        override fun onExecute(okHttpClient: OkHttpClient): Int = response.code
    }

    private class FailingAfterPreviewResponseBody(preview: String) : ResponseBody() {
        private val previewBytes = preview.toByteArray()

        override fun contentType() = "text/plain".toMediaType()

        override fun contentLength() = Long.MAX_VALUE

        override fun source(): BufferedSource = object : Source {
            private var offset = 0

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (offset < previewBytes.size) {
                    val count = minOf(byteCount, (previewBytes.size - offset).toLong()).toInt()
                    sink.write(previewBytes, offset, count)
                    offset += count
                    return count.toLong()
                }

                throw IOException("Full response body should not be read")
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }.buffer()
    }
}
