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
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.exception.RedirectException
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.HttpBaseMethod
import eu.opencloud.android.lib.common.http.methods.webdav.properties.OCFileId
import eu.opencloud.android.lib.common.http.methods.webdav.properties.OCSpaceId
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.StringWriter
import java.net.URL

class SearchMethod(
    url: URL,
    private val searchQuery: String,
    private val limit: Int = DEFAULT_SEARCH_LIMIT,
    private val propertiesToRequest: Array<Property.Name> = defaultSearchProperties,
) : HttpBaseMethod(url) {

    override lateinit var response: okhttp3.Response
    val members: MutableList<Response> = arrayListOf()
    private var davReportResource: DavReportResource? = null

    override val isAborted: Boolean
        get() = davReportResource?.isCallAborted() ?: false

    override fun abort() {
        davReportResource?.cancelCall()
    }

    @Throws(Exception::class)
    override fun onExecute(okHttpClient: OkHttpClient): Int =
        try {
            val resource = DavReportResource(
                okHttpClient.newBuilder().followRedirects(false).build(),
                httpUrl,
                Dav4jvm.log
            )
            davReportResource = resource

            val xmlBody = buildSearchXml(searchQuery, limit, propertiesToRequest)
            val requestBody = xmlBody.toRequestBody("application/xml; charset=utf-8".toMediaType())

            resource.report(
                requestBody = requestBody,
                listOfHeaders = getRequestHeadersAsHashMap(),
                callback = { davResponse, _ ->
                    members.add(davResponse)
                },
                rawCallback = { rawResponse ->
                    response = rawResponse
                }
            )

            statusCode
        } catch (httpException: RedirectException) {
            response = okhttp3.Response.Builder()
                .header(HttpConstants.LOCATION_HEADER, httpException.redirectLocation)
                .code(httpException.code)
                .request(request)
                .message(httpException.message ?: "")
                .protocol(Protocol.HTTP_1_1)
                .build()
            httpException.code
        } catch (httpException: HttpException) {
            if (::response.isInitialized && response.body?.contentType() != null) {
                val responseBody = (httpException.responseBody ?: "").toResponseBody(response.body?.contentType())
                response = response.newBuilder()
                    .body(responseBody)
                    .build()
            }
            httpException.code
        }

    companion object {
        const val DEFAULT_SEARCH_LIMIT = 100

        val defaultSearchProperties: Array<Property.Name>
            get() = DavUtils.allPropSet + arrayOf(
                OCFileId.NAME,
                OCSpaceId.NAME,
            )

        fun buildSearchXml(
            pattern: String,
            limit: Int,
            properties: Array<Property.Name>,
        ): String {
            val serializer = XmlUtils.newSerializer()
            val writer = StringWriter()
            serializer.setOutput(writer)
            serializer.startDocument("UTF-8", null)
            serializer.setPrefix("d", XmlUtils.NS_WEBDAV)
            serializer.setPrefix("oc", XmlUtils.NS_OWNCLOUD)

            serializer.startTag(XmlUtils.NS_OWNCLOUD, "search-files")

            // <d:prop>
            serializer.startTag(XmlUtils.NS_WEBDAV, "prop")
            for (prop in properties) {
                serializer.startTag(prop.namespace, prop.name)
                serializer.endTag(prop.namespace, prop.name)
            }
            serializer.endTag(XmlUtils.NS_WEBDAV, "prop")

            // <oc:search>
            serializer.startTag(XmlUtils.NS_OWNCLOUD, "search")
            serializer.startTag(XmlUtils.NS_OWNCLOUD, "pattern")
            serializer.text(pattern)
            serializer.endTag(XmlUtils.NS_OWNCLOUD, "pattern")
            if (limit > 0) {
                serializer.startTag(XmlUtils.NS_OWNCLOUD, "limit")
                serializer.text(limit.toString())
                serializer.endTag(XmlUtils.NS_OWNCLOUD, "limit")
            }
            serializer.endTag(XmlUtils.NS_OWNCLOUD, "search")

            serializer.endTag(XmlUtils.NS_OWNCLOUD, "search-files")
            serializer.endDocument()

            return writer.toString()
        }
    }
}
