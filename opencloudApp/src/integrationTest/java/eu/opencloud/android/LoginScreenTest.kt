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
import screens.TrustCertificate

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

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    @Test
    fun loginApp() {
        before {
//            adbServer.performCmd("adb", listOf("reverse", "tcp:9200", "tcp:9200"))
            adbServer.performCmd("adb", listOf("shell", "settings", "put", "global", "heads_up_notifications_enabled", "0"))
            adbServer.performCmd("adb", listOf("shell", "wm", "dismiss-keyguard"))
            adbServer.performCmd("adb", listOf("shell", "input", "keyevent", "82"))
        }.after {
            adbServer.performCmd("adb", listOf("shell", "am", "force-stop", "com.android.chrome"))
//            adbServer.performCmd("adb", listOf("reverse", "--remove", "tcp:9200"))
        }.run {
            step("set opencloud url") {
                StartScreen {
                    hostUrlInput {
                        isVisible()
                        typeText("https://cloud.rc.opencloud.rocks")
                    }
                    checkServerButton {
                        isVisible()
                        isClickable()
                        click()
                    }
                }
            }
//            step("trust certificate") {
//                TrustCertificate {
//                    yesBtn {
//                        isVisible()
//                        isClickable()
//                        click()
//                    }
//                }
//            }
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
