package eu.opencloud.android.presentation.authentication.oauth

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class OAuthPromptTest {
    @Test
    fun offlineAccessRequiresConsentOnly() {
        assertEquals("consent", OAuthUtils.getCreateAccountPrompt("openid profile email groups offline_access"))
        assertEquals("consent", OAuthUtils.getCreateAccountPrompt(" offline_access  openid "))
    }

    @Test
    fun otherScopesKeepLogin() {
        for (scope in listOf("", "openid profile email", "openid not_offline_access", "offline_access_extra")) {
            assertEquals("login", OAuthUtils.getCreateAccountPrompt(scope))
        }
    }

    @Test
    fun authorizationRequestEncodesOnePromptAndPreservesPkceAndRedirect() {
        val scope = "openid profile email groups offline_access"
        val uri = OAuthUtils.buildAuthorizationRequest(
            authorizationEndpoint = Uri.parse("https://idp.example/authorize?existing=value"),
            redirectUri = "oc://android.opencloud.eu",
            clientId = "OpenCloudAndroid",
            responseType = "code",
            scope = scope,
            prompt = OAuthUtils.getCreateAccountPrompt(scope),
            codeChallenge = "test-challenge",
            state = "test-state",
            username = null,
            sendLoginHintAndUser = false,
        )
        assertEquals(listOf("consent"), uri.getQueryParameters("prompt"))
        assertTrue(uri.encodedQuery!!.contains("prompt=consent"))
        assertEquals("oc://android.opencloud.eu", uri.getQueryParameter("redirect_uri"))
        assertEquals("OpenCloudAndroid", uri.getQueryParameter("client_id"))
        assertEquals(scope, uri.getQueryParameter("scope"))
        assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
        assertEquals("test-challenge", uri.getQueryParameter("code_challenge"))
        assertEquals("test-state", uri.getQueryParameter("state"))
        assertEquals("value", uri.getQueryParameter("existing"))
        println("Authorization request under test: $uri")
    }
}
