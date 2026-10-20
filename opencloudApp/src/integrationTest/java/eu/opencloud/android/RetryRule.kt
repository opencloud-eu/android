package eu.opencloud.android

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Re-runs a failed test. Retries come from the "retries" instrumentation argument (default 0).
 * Must be the outermost rule.
 */
class RetryRule(
    private val retries: Int = InstrumentationRegistry.getArguments().getString("retries")?.toIntOrNull() ?: 0
) : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            repeat(retries) {
                try {
                    return base.evaluate()
                } catch (t: Throwable) {
                    Log.w("RetryRule", "${description.displayName} failed, retrying", t)
                }
            }
            base.evaluate()
        }
    }
}
