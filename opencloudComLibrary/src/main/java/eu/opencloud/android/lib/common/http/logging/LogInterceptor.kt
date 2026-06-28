/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2023 ownCloud GmbH.
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
package eu.opencloud.android.lib.common.http.logging

import eu.opencloud.android.lib.common.http.HttpConstants.AUTHORIZATION_HEADER
import eu.opencloud.android.lib.common.http.HttpConstants.OC_X_REQUEST_ID
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import timber.log.Timber
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class LogInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        if (!httpLogsEnabled) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val response = chain.proceed(request)
        logRequest(moshi, request)

        return response.also {
            logResponse(moshi, it, request)
        }
    }

    private fun logRequest(moshi: Moshi, request: Request) {
        val requestJsonAdapter = moshi.adapter(LogRequest::class.java)
        val requestId = request.headers[OC_X_REQUEST_ID] ?: ""
        Timber.d(
            "REQUEST $requestId ${
                requestJsonAdapter.toJson(
                    LogRequest(
                        Request(
                            body = getRequestBodyString(request.body),
                            headers = logHeaders(request.headers),
                            info = RequestInfo(
                                id = requestId,
                                method = request.method,
                                url = redactUrl(request.url),
                            )
                        )
                    )
                )
            }"
        )
    }

    private fun logHeaders(headers: Headers): Map<String, String> {
        val auxHeaders = headers.toMap().toMutableMap()
        if (auxHeaders.contains(AUTHORIZATION_HEADER)) {
            val authHeaderList = auxHeaders[AUTHORIZATION_HEADER]!!.split(" ")
            val authType = authHeaderList[0]
            val authInfo = if (redactAuthHeader) REDACTED else authHeaderList.getOrNull(1).orEmpty()
            auxHeaders[AUTHORIZATION_HEADER] = "$authType $authInfo"
        }
        return auxHeaders.mapValues { (header, value) ->
            if (SENSITIVE_HEADER_NAMES.any { it.equals(header, ignoreCase = true) }) {
                REDACTED
            } else {
                redactSensitiveData(value)
            }
        }
    }

    private fun getRequestBodyString(requestBodyParam: RequestBody?): String? {
        requestBodyParam?.let { requestBody ->
            if (requestBody.isOneShot()) {
                return "One shot body --   Omitted"
            }

            if (requestBody.isDuplex()) {
                return "Duplex body -- Omitted"
            }

            val buffer = Buffer()
            requestBody.writeTo(buffer)

            val contentType = requestBody.contentType()
            val charset: Charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
            if (contentType.isLoggable()) {
                return redactSensitiveData(buffer.readString(charset))
            } else if (requestBody.contentLength() > 0) {
                return "$BINARY_OMITTED ${requestBody.contentLength()} $BYTES"
            }
        }
        return null
    }

    private fun logResponse(moshi: Moshi, response: Response, request: Request) {
        val responseJsonAdapter = moshi.adapter(LogResponse::class.java)
        val contentType = response.body?.contentType()
        val charset: Charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
        val source = response.body?.source()
        source?.request(LIMIT_BODY_LOG)
        val buffer = source?.buffer
        val rawResponseBody = buffer?.clone()?.readString(charset)
        val bodyLength = rawResponseBody?.toByteArray(charset)?.size ?: 0
        val responseBody = getResponseBodyString(contentType, bodyLength, rawResponseBody ?: "")
        val duration = response.receivedResponseAtMillis - response.sentRequestAtMillis
        val requestId = request.headers[OC_X_REQUEST_ID] ?: ""
        Timber.d(
            "RESPONSE $requestId ${
                responseJsonAdapter.toJson(
                    LogResponse(
                        Response(
                            headers = logHeaders(response.headers),
                            body = responseBody?.let { Body(data = responseBody, length = bodyLength) },
                            info = ResponseInfo(
                                id = requestId,
                                method = request.method,
                                reply = Reply(
                                    cached = response.cacheResponse != null,
                                    duration = duration,
                                    durationString = getDurationString(duration),
                                    status = response.code,
                                    version = response.protocol.toString(),
                                ),
                                url = redactUrl(request.url),
                            )
                        )
                    )
                )
            }"
        )
    }

    private fun getResponseBodyString(contentType: MediaType?, contentLength: Int, responseBody: String): String? =
        if (contentType?.isLoggable() == true) {
            redactSensitiveData(responseBody)
        } else if (contentLength > 0) {
            "$BINARY_OMITTED $contentLength $BYTES"
        } else {
            null
        }

    private fun getDurationString(millis: Long): String {
        var auxMillis = millis
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        auxMillis -= TimeUnit.HOURS.toMillis(hours)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(auxMillis)
        auxMillis -= TimeUnit.MINUTES.toMillis(minutes)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(auxMillis)
        auxMillis -= TimeUnit.SECONDS.toMillis(seconds)
        return String.format(DURATION_FORMAT, hours, minutes, seconds, auxMillis)
    }

    private fun redactUrl(url: HttpUrl): String {
        var redactedUrl = url.newBuilder()
        url.queryParameterNames
            .filter(::isSensitiveField)
            .forEach { redactedUrl = redactedUrl.setQueryParameter(it, REDACTED) }
        return redactedUrl.build().toString()
    }

    private fun redactSensitiveData(value: String): String =
        FORM_FIELD_REGEX.replace(
            JSON_FIELD_REGEX.replace(
                TO_STRING_FIELD_REGEX.replace(value) {
                    "${it.groupValues[1]}$REDACTED"
                }
            ) {
                "${it.groupValues[1]}$REDACTED${it.groupValues[4]}"
            }
        ) {
            "${it.groupValues[1]}${it.groupValues[2]}=$REDACTED"
        }

    private fun isSensitiveField(field: String): Boolean =
        SENSITIVE_FIELD_NAMES.any { it.equals(field, ignoreCase = true) }

    companion object {
        var httpLogsEnabled: Boolean = false
        var redactAuthHeader: Boolean = true
        private const val REDACTED = "[redacted]"
        private const val LIMIT_BODY_LOG: Long = 1000000
        private const val BINARY_OMITTED = "<-- Body end for response -- Binary -- Omitted:"
        private const val BYTES = "bytes -->"
        private val SENSITIVE_HEADER_NAMES = setOf(
            "Cookie",
            "Set-Cookie",
            "X-Auth-Token",
        )
        private val SENSITIVE_FIELD_NAMES = setOf(
            "access_token",
            "accessToken",
            "authorization_code",
            "authorizationCode",
            "client_secret",
            "clientSecret",
            "code",
            "code_verifier",
            "codeVerifier",
            "id_token",
            "idToken",
            "password",
            "passcode",
            "pattern",
            "refresh_token",
            "refreshToken",
        )
        private val SENSITIVE_FIELD_PATTERN = SENSITIVE_FIELD_NAMES.joinToString("|") { Regex.escape(it) }
        private val JSON_FIELD_REGEX = Regex("""(?i)("($SENSITIVE_FIELD_PATTERN)"\s*:\s*")([^"]*)(")""")
        private val FORM_FIELD_REGEX = Regex("""(?i)(^|[?&])($SENSITIVE_FIELD_PATTERN)=([^&#]*)""")
        private val TO_STRING_FIELD_REGEX = Regex("""(?i)(\b($SENSITIVE_FIELD_PATTERN)\s*=\s*)([^,)\s&]+)""")
    }
}
