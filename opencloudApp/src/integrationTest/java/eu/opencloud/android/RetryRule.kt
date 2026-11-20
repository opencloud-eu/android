package eu.opencloud.android

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
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
                    finishAllActivities()
                }
            }
            base.evaluate()
        }
    }

    /**
     * ActivityScenarioRule only closes the launched activity, so activities opened later in a failed
     * attempt (e.g. FileDisplayActivity) would stay on top of the next attempt.
     */
    private fun finishAllActivities() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.entries
                .filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }
                .forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }
}
