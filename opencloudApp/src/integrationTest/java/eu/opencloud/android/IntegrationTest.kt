package eu.opencloud.android

/**
 * Marks an end-to-end integration test (src/integrationTest) that drives the real UI against a
 * running OpenCloud server on the emulator, as opposed to the isolated instrumented tests.
 *
 * Run only these tests with:
 *   ./gradlew :opencloudApp:connectedOriginalDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.annotation=eu.opencloud.android.IntegrationTest
 *
 * Retention must be RUNTIME so the AndroidJUnitRunner can read it for filtering.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class IntegrationTest
