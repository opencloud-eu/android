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

package eu.opencloud.android.lib.resources.oauth

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants.AUTHORIZATION_HEADER
import eu.opencloud.android.lib.resources.oauth.params.ClientRegistrationParams
import eu.opencloud.android.lib.resources.oauth.params.TokenRequestParams
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
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
class OAuthRemoteOperationTest {

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
    fun tokenRequest_withConfidentialClient_sendsAuthorizationHeaderAndParsesResponse() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenResponseJson()))

        val result = TokenRequestRemoteOperation(
            TokenRequestParams.Authorization(
                tokenEndpoint = server.url("/token").toString(),
                clientAuth = "Basic abc123",
                grantType = "authorization_code",
                scope = "openid profile",
                clientId = "desktop-client",
                clientSecret = "client-secret",
                authorizationCode = "auth-code",
                redirectUri = "opencloud://oauth",
                codeVerifier = "verifier"
            )
        ).execute(newClient())

        assertTrue(result.isSuccess)
        assertEquals("access-token", result.data.accessToken)
        assertEquals("refresh-token", result.data.refreshToken)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/token", request.path)
        assertEquals("Basic abc123", request.getHeader(AUTHORIZATION_HEADER))

        val form = request.formBody()
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("auth-code", form["code"])
        assertEquals("opencloud://oauth", form["redirect_uri"])
        assertEquals("verifier", form["code_verifier"])
        assertEquals("desktop-client", form["client_id"])
        assertEquals("client-secret", form["client_secret"])
    }

    @Test
    fun tokenRequest_withPublicPkceClient_omitsAuthorizationHeader() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(tokenResponseJson()))

        val result = TokenRequestRemoteOperation(
            TokenRequestParams.Authorization(
                tokenEndpoint = server.url("/token").toString(),
                clientAuth = "",
                grantType = "authorization_code",
                scope = "openid",
                clientId = "public-client",
                clientSecret = null,
                authorizationCode = "auth-code",
                redirectUri = "opencloud://oauth",
                codeVerifier = "verifier"
            )
        ).execute(newClient())

        assertTrue(result.isSuccess)

        val request = server.takeRequest()
        assertNull(request.getHeader(AUTHORIZATION_HEADER))
        assertEquals("public-client", request.formBody()["client_id"])
    }

    @Test
    fun tokenRequest_invalidJson_returnsExceptionResult() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{ invalid json"))

        val result = TokenRequestRemoteOperation(
            TokenRequestParams.RefreshToken(
                tokenEndpoint = server.url("/token").toString(),
                clientAuth = "",
                grantType = "refresh_token",
                scope = "openid",
                clientId = "public-client",
                clientSecret = null,
                refreshToken = "refresh-token"
            )
        ).execute(newClient())

        assertFalse(result.isSuccess)
        assertTrue(result.exception != null)
    }

    @Test
    fun registerClient_sendsJsonRequestAndParsesCreatedResponse() {
        server.enqueue(MockResponse().setResponseCode(201).setBody(clientRegistrationResponseJson()))

        val result = RegisterClientRemoteOperation(
            ClientRegistrationParams(
                registrationEndpoint = server.url("/register").toString(),
                clientName = "OpenCloud Android",
                redirectUris = listOf("opencloud://oauth"),
                tokenEndpointAuthMethod = "none",
                applicationType = "native"
            )
        ).execute(newClient())

        assertTrue(result.isSuccess)
        assertEquals("client-id", result.data.clientId)
        assertEquals("client-secret", result.data.clientSecret)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/register", request.path)

        val body = JSONObject(request.body.readUtf8())
        assertEquals("native", body.getString("application_type"))
        assertEquals("OpenCloud Android", body.getString("client_name"))
        assertEquals("none", body.getString("token_endpoint_auth_method"))
        assertEquals("opencloud://oauth", body.getJSONArray("redirect_uris").getString(0))
    }

    @Test
    fun registerClient_unexpectedStatus_returnsUnsuccessfulResult() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_client_metadata"}"""))

        val result = RegisterClientRemoteOperation(
            ClientRegistrationParams(
                registrationEndpoint = server.url("/register").toString(),
                clientName = "OpenCloud Android",
                redirectUris = listOf("opencloud://oauth"),
                tokenEndpointAuthMethod = "none",
                applicationType = "native"
            )
        ).execute(newClient())

        assertFalse(result.isSuccess)
        assertEquals(400, result.httpCode)
    }

    private fun newClient(): OpenCloudClient =
        OpenCloudClient(
            Uri.parse(server.url("/").toString().removeSuffix("/")),
            null,
            true,
            null,
            ApplicationProvider.getApplicationContext()
        )

    private fun tokenResponseJson(): String =
        """
        {
          "access_token": "access-token",
          "expires_in": 3600,
          "refresh_token": "refresh-token",
          "token_type": "Bearer",
          "user_id": "user",
          "scope": "openid profile",
          "id_token": "id-token",
          "additional_parameters": {
            "server": "opencloud"
          }
        }
        """.trimIndent()

    private fun clientRegistrationResponseJson(): String =
        """
        {
          "client_id": "client-id",
          "client_secret": "client-secret",
          "client_id_issued_at": 1700000000,
          "client_secret_expires_at": 0
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
