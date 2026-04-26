package screens

import com.kaspersky.kaspresso.screens.KScreen
import eu.opencloud.android.R
import io.github.kakaocup.kakao.text.KButton

object MainScreen : KScreen<MainScreen>() {
    override val layoutId: Int? = R.layout.activity_main
    override val viewClass: Class<*>? = null

    val avatarButton = KButton { withId(R.id.root_toolbar_avatar) }
}
