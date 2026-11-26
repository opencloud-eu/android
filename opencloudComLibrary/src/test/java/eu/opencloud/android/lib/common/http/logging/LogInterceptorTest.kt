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
package eu.opencloud.android.lib.common.http.logging

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class LogInterceptorTest {

    private val server = MockWebServer()
    private val logs = mutableListOf<String>()
    private val tree = object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            logs.add(message)
        }
    }

    @Before
    fun setUp() {
        Timber.plant(tree)
        LogInterceptor.httpLogsEnabled = true
    }

    @After
    fun tearDown() {
        Timber.uproot(tree)
        server.shutdown()
        LogInterceptor.httpLogsEnabled = false
        LogInterceptor.redactSecrets = true
    }

    @Test
    fun tokenRequestSecretsAreRedacted() {
        val log = executeTokenRequest()

        SECRETS.forEach { assertFalse("$it was logged", log.contains(it)) }
        assertTrue(log.contains("grant_type=refresh_token"))
        assertTrue(log.contains("token_type"))
        assertTrue(log.contains("[redacted]"))
    }

    @Test
    fun tokenRequestSecretsAreLoggedWhenRedactionIsDisabled() {
        LogInterceptor.redactSecrets = false

        val log = executeTokenRequest()

        SECRETS.forEach { assertTrue("$it was not logged", log.contains(it)) }
    }

    @Test
    fun authorizationCodeGrantSecretsAreRedacted() {
        val body = "code=the-code&grant_type=authorization_code&redirect_uri=oc%3A%2F%2Fandroid&code_verifier=the-verifier"

        assertEquals(
            "code=[redacted]&grant_type=authorization_code&redirect_uri=oc%3A%2F%2Fandroid&code_verifier=[redacted]",
            LogInterceptor.redactBody(body, FORM_MEDIA_TYPE)
        )
    }

    @Test
    fun clientRegistrationSecretIsRedacted() {
        val body = """{"client_id":"the-client","client_secret":"the-secret","client_secret_expires_at":0}"""

        assertEquals(
            """{"client_id":"the-client","client_secret":"[redacted]","client_secret_expires_at":0}""",
            LogInterceptor.redactBody(body, JSON_MEDIA_TYPE)
        )
    }

    @Test
    fun jsonCodeFieldIsNotRedacted() {
        // Graph API errors carry a "code" too, it is only a secret in the form body of a token request
        val body = """{"error":{"code":"itemNotFound","message":"not found"}}"""

        assertEquals(body, LogInterceptor.redactBody(body, JSON_MEDIA_TYPE))
    }

    @Test
    fun durationStringFormatsSubSecondDuration() {
        assertEquals("duration(0h, 0min, 0s, 999ms)", formatDuration(999L))
    }

    @Test
    fun durationStringFormatsMinuteDuration() {
        assertEquals("duration(0h, 1min, 1s, 0ms)", formatDuration(61_000L))
    }

    @Test
    fun durationStringFormatsHourDuration() {
        assertEquals("duration(1h, 1min, 1s, 0ms)", formatDuration(3_661_000L))
    }

    @Test
    fun durationStringFormatsMixedDuration() {
        assertEquals("duration(2h, 3min, 1s, 234ms)", formatDuration(7_381_234L))
    }

    /** @return everything logged for a refresh token request */
    private fun executeTokenRequest(): String {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"access_token":"$ACCESS_TOKEN","expires_in":300,"refresh_token":"$NEW_REFRESH_TOKEN",""" +
                            """"id_token":"$ID_TOKEN","token_type":"Bearer"}"""
                )
        )
        server.start()
        val request = Request.Builder()
            .url(server.url("/token"))
            .header("Authorization", "Basic $CLIENT_AUTH")
            .post(
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", REFRESH_TOKEN)
                    .add("client_id", "the-client")
                    .add("client_secret", CLIENT_SECRET)
                    .build()
            )
            .build()

        OkHttpClient.Builder().addInterceptor(LogInterceptor()).build().newCall(request).execute().close()

        return logs.joinToString("\n")
    }

    private fun formatDuration(millis: Long): String {
        val method = LogInterceptor::class.java.getDeclaredMethod("getDurationString", Long::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(LogInterceptor(), millis) as String
    }

    companion object {
        private const val ACCESS_TOKEN = "access-token-value"
        private const val REFRESH_TOKEN = "refresh-token-value"
        private const val NEW_REFRESH_TOKEN = "new-refresh-token-value"
        private const val ID_TOKEN = "id-token-value"
        private const val CLIENT_SECRET = "client-secret-value"
        private const val CLIENT_AUTH = "Y2xpZW50OnNlY3JldA=="
        private val SECRETS = listOf(ACCESS_TOKEN, REFRESH_TOKEN, NEW_REFRESH_TOKEN, ID_TOKEN, CLIENT_SECRET, CLIENT_AUTH)
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
