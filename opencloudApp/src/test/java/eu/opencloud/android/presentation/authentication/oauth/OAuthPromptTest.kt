package eu.opencloud.android.presentation.authentication.oauth

import android.net.Uri
import eu.opencloud.android.R
import eu.opencloud.android.presentation.authentication.ACTION_CREATE
import eu.opencloud.android.presentation.authentication.ACTION_UPDATE_EXPIRED_TOKEN
import eu.opencloud.android.presentation.authentication.ACTION_UPDATE_TOKEN
import eu.opencloud.android.providers.MdmProvider
import eu.opencloud.android.utils.CONFIGURATION_OAUTH2_OPEN_ID_PROMPT
import io.mockk.every
import io.mockk.mockk
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
    fun promptIsTheConfiguredValueForEveryAction() {
        val mdmProvider = mockk<MdmProvider>()
        every {
            mdmProvider.getBrandingString(CONFIGURATION_OAUTH2_OPEN_ID_PROMPT, R.string.oauth2_openid_prompt)
        } returns "consent"

        for (action in listOf(ACTION_CREATE, ACTION_UPDATE_TOKEN, ACTION_UPDATE_EXPIRED_TOKEN)) {
            assertEquals("consent", OAuthUtils.getPrompt(action, mdmProvider))
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
            prompt = "consent",
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
