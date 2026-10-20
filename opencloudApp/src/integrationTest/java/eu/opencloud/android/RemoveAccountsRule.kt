package eu.opencloud.android

import android.accounts.AccountManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Removes all app accounts so every attempt starts from the start screen. */
class RemoveAccountsRule : ExternalResource() {
    override fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountManager = AccountManager.get(context)
        accountManager.getAccountsByType(context.getString(R.string.account_type)).forEach {
            accountManager.removeAccountExplicitly(it)
        }
    }
}
