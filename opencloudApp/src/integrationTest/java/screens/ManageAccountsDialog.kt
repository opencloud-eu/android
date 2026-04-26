package screens

import com.kaspersky.kaspresso.screens.KScreen
import eu.opencloud.android.R
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.text.KTextView

object ManageAccountsDialog : KScreen<ManageAccountsDialog>() {
    override val layoutId: Int = R.layout.manage_accounts_dialog
    override val viewClass: Class<*>? = null

    val removeBtn = KButton { withId(R.id.removeButton) }
    val message = KTextView {
        containsText("Do you really want to remove the account")
    }
    val confirmBtn = KButton { withText(R.string.common_yes) }
}
