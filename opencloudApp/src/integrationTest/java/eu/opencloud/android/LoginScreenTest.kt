package eu.opencloud.android

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.kaspersky.kaspresso.kaspresso.Kaspresso
import com.kaspersky.kaspresso.params.FlakySafetyParams
import com.kaspersky.kaspresso.testcases.api.testcase.TestCase
import eu.opencloud.android.ui.activity.SplashActivity
import org.junit.Rule
import org.junit.Test
import screens.LoginScreen
import screens.MainScreen
import screens.ManageAccountsDialog
import screens.StartScreen

class LoginScreenTest : TestCase(
    kaspressoBuilder = Kaspresso.Builder.advanced {
        flakySafetyParams = FlakySafetyParams.custom(
            timeoutMs = 20_000L,
            intervalMs = 100L
        )
    }
) {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    @get:Rule
    val activityRule = ActivityScenarioRule(SplashActivity::class.java)


    @Test
    fun loginApp() {
        before {
            adbServer.performCmd("adb", listOf("reverse", "tcp:9200", "tcp:9200"))

        }.after {
            adbServer.performCmd("adb", listOf("shell", "am", "force-stop", "com.android.chrome"))
            adbServer.performCmd("adb", listOf("reverse", "--remove", "tcp:9200"))
        }.run {
            step("set opencloud url") {

                StartScreen {
                    hostUrlInput {
                        isVisible()
                        typeText("https://localhost:9200")
                    }
                    checkServerButton {
                        isVisible()
                        isClickable()
                        click()
                    }
                }
            }

            step("login") {
                LoginScreen {
                    username.isDisplayed()
                    password.isDisplayed()
                    loginButton.isDisplayed()
                    username.typeText("alan")
                    password.typeText("demo")
                    loginButton.click()
                    keepAccessForeverBtn {
                        isDisplayed()
                        isClickable()
                        click()
                    }
                }
            }
            step("check personal space") {
                MainScreen {
                    avatarButton.isVisible()
                    avatarButton.isClickable()
                    avatarButton.click()
                }
            }
            step("remove account") {
                ManageAccountsDialog {
                    removeBtn {
                        isVisible()
                        click()
                    }
                    message.isVisible()
                    confirmBtn.click()
                }
            }
        }
    }
}
