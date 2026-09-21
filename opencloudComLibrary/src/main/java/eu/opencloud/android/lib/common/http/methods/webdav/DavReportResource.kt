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

import at.bitfire.dav4jvm.Dav4jvm
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.Response.HrefRelation
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import java.util.logging.Logger

class DavReportResource(
    httpClient: OkHttpClient,
    location: HttpUrl,
    log: Logger = Dav4jvm.log,
) : DavResource(httpClient, location, log) {

    @Throws(IOException::class, HttpException::class, DavException::class)
    fun report(
        requestBody: RequestBody,
        listOfHeaders: HashMap<String, String?>,
        callback: (Response, HrefRelation) -> Unit,
        rawCallback: (okhttp3.Response) -> Unit,
    ): List<Property> {
        val httpResponse = followRedirects {
            val requestBuilder = Request.Builder()
                .url(location)
                .method("REPORT", requestBody)

            listOfHeaders.forEach { (key, value) ->
                if (value != null) {
                    requestBuilder.header(key, value)
                }
            }

            val currentCall = httpClient.newCall(requestBuilder.build())
            this.call = currentCall
            currentCall.execute()
        }

        rawCallback(httpResponse)
        checkStatus(httpResponse)
        return processMultiStatus(httpResponse, callback)
    }
}
