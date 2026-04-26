package screens

import com.kaspersky.kaspresso.screens.KScreen
import eu.opencloud.android.R
import io.github.kakaocup.kakao.edit.KEditText
import io.github.kakaocup.kakao.text.KButton

object StartScreen : KScreen<StartScreen>() {
    override val layoutId: Int? = R.layout.account_setup
    override val viewClass: Class<*>? = null

    val hostUrlInput = KEditText { withId(R.id.hostUrlInput) }
    val checkServerButton = KButton { withId(R.id.embeddedCheckServerButton) }
}
