package screens

import com.kaspersky.kaspresso.screens.KScreen
import eu.opencloud.android.R
import io.github.kakaocup.kakao.text.KButton

object TrustCertificate: KScreen<TrustCertificate>() {
    override val layoutId: Int? = R.layout.ssl_untrusted_cert_layout
    override val viewClass: Class<*>? = null

    val yesBtn = KButton { withId(R.id.ok) }
}
