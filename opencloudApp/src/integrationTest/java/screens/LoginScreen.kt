package screens

import com.kaspersky.components.kautomator.component.edit.UiEditText
import com.kaspersky.components.kautomator.component.text.UiButton
import com.kaspersky.components.kautomator.screen.UiScreen

object LoginScreen : UiScreen<LoginScreen>() {
    override val packageName: String = "com.android.chrome"

    // keycloak login form
    val username = UiEditText { withResourceName("username") }
    val password = UiEditText { withResourceName("password") }
    val loginButton = UiButton { withResourceName("kc-login") }
}
