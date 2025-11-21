/**
 * openCloud Android client application
 *
 * @author masensio
 * @author David A. Velasco
 * @author David González Verdugo
 * @author Christian Schabesberger
 * @author David Crespo Ríos
 * @author Juan Carlos Garrote Gascón
 * @author Aitor Ballesteros Pavón
 *
 * Copyright (C) 2024 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android

import android.app.Activity
import android.app.Application
import android.app.NotificationManager.IMPORTANCE_LOW
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat
import eu.opencloud.android.data.providers.implementation.OCSharedPreferencesProvider

import eu.opencloud.android.datamodel.ThumbnailsCacheManager
import eu.opencloud.android.db.PreferenceManager
import eu.opencloud.android.dependecyinjection.commonModule
import eu.opencloud.android.dependecyinjection.localDataSourceModule
import eu.opencloud.android.dependecyinjection.remoteDataSourceModule
import eu.opencloud.android.dependecyinjection.repositoryModule
import eu.opencloud.android.dependecyinjection.useCaseModule
import eu.opencloud.android.dependecyinjection.viewModelModule
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.spaces.model.OCSpace
import eu.opencloud.android.domain.spaces.usecases.GetPersonalSpaceForAccountUseCase
import eu.opencloud.android.domain.user.usecases.GetStoredQuotaUseCase
import eu.opencloud.android.extensions.createNotificationChannel
import eu.opencloud.android.lib.common.SingleSessionManager
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.presentation.migration.StorageMigrationActivity
import eu.opencloud.android.presentation.releasenotes.ReleaseNotesActivity
import eu.opencloud.android.presentation.security.biometric.BiometricActivity
import eu.opencloud.android.presentation.security.biometric.BiometricManager
import eu.opencloud.android.presentation.security.passcode.PassCodeActivity
import eu.opencloud.android.presentation.security.passcode.PassCodeManager
import eu.opencloud.android.presentation.security.pattern.PatternActivity
import eu.opencloud.android.presentation.security.pattern.PatternManager
import eu.opencloud.android.presentation.settings.logging.SettingsLogsFragment.Companion.PREFERENCE_ENABLE_LOGGING
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import eu.opencloud.android.providers.LogsProvider
import eu.opencloud.android.providers.MdmProvider
import eu.opencloud.android.ui.activity.FileDisplayActivity
import eu.opencloud.android.ui.activity.FileDisplayActivity.Companion.PREFERENCE_CLEAR_DATA_ALREADY_TRIGGERED
import eu.opencloud.android.ui.activity.WhatsNewActivity
import eu.opencloud.android.utils.CONFIGURATION_ALLOW_SCREENSHOTS
import eu.opencloud.android.utils.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import eu.opencloud.android.utils.DebugInjector
import eu.opencloud.android.utils.FILE_SYNC_CONFLICT_NOTIFICATION_CHANNEL_ID
import eu.opencloud.android.utils.FILE_SYNC_NOTIFICATION_CHANNEL_ID
import eu.opencloud.android.utils.MEDIA_SERVICE_NOTIFICATION_CHANNEL_ID
import eu.opencloud.android.utils.UPLOAD_NOTIFICATION_CHANNEL_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import timber.log.Timber

/**
 * Main Application of the project
 *
 *
 * Contains methods to build the "static" strings. These strings were before constants in different
 * classes
 */
class MainApp : Application() {

    override fun onCreate() {
        super.onCreate()

        appContext = applicationContext

        // Ensure Logcat shows Timber logs in debug builds
        if (BuildConfig.DEBUG) {
            try {
                Timber.plant(Timber.DebugTree())
            } catch (_: Throwable) {
                // ignore if already planted
            }
        }

        startLogsIfEnabled()

        DebugInjector.injectDebugTools(appContext)

        createNotificationChannels()

        SingleSessionManager.setUserAgent(userAgent)

        // initialise thumbnails cache on background thread
        ThumbnailsCacheManager.InitDiskCacheTask().execute()

        initDependencyInjection()

        // register global protection with pass code, pattern lock and biometric lock
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Timber.d("${activity.javaClass.simpleName} onCreate(Bundle) starting")

                // To prevent taking screenshots in MDM
                if (!areScreenshotsAllowed()) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }

                // If there's any lock protection, don't show wizard at this point, show it when lock activities
                // have finished
                if (activity !is PassCodeActivity &&
                    activity !is PatternActivity &&
                    activity !is BiometricActivity
                ) {
                    StorageMigrationActivity.runIfNeeded(activity)
                    if (isFirstRun()) {
                        WhatsNewActivity.runIfNeeded(activity)

                    } else {
                        ReleaseNotesActivity.runIfNeeded(activity)

                        val pref = PreferenceManager.getDefaultSharedPreferences(appContext)
                        val clearDataAlreadyTriggered = pref.contains(PREFERENCE_CLEAR_DATA_ALREADY_TRIGGERED)
                        if (clearDataAlreadyTriggered || isNewVersionCode()) {
                            val dontShowAgainDialogPref = pref.getBoolean(PREFERENCE_KEY_DONT_SHOW_SERVER_ACCOUNT_WARNING_DIALOG, false)
                            if (!dontShowAgainDialogPref && shouldShowDialog(activity)) {
                                val checkboxDialog = activity.layoutInflater.inflate(R.layout.checkbox_dialog, null)
                                val checkbox = checkboxDialog.findViewById<CheckBox>(R.id.checkbox_dialog)
                                checkbox.setText(R.string.server_accounts_warning_checkbox_message)
                                val builder = AlertDialog.Builder(activity).apply {
                                    setView(checkboxDialog)
                                    setTitle(R.string.server_accounts_warning_title)
                                    setMessage(R.string.server_accounts_warning_message)
                                    setCancelable(false)
                                    setPositiveButton(R.string.server_accounts_warning_button) { _, _ ->
                                        if (checkbox.isChecked) {
                                            pref.edit().putBoolean(PREFERENCE_KEY_DONT_SHOW_SERVER_ACCOUNT_WARNING_DIALOG, true).apply()
                                        }
                                    }
                                }
                                val alertDialog = builder.create()
                                alertDialog.show()
                            }
                        } else { // "Clear data" button is pressed from the app settings in the device settings.
                            AccountUtils.deleteAccounts(appContext)
                            WhatsNewActivity.runIfNeeded(activity)
                        }
                    }
                }

                PreferenceManager.migrateFingerprintToBiometricKey(applicationContext)
                PreferenceManager.deleteOldSettingsPreferences(applicationContext)
            }

            private fun shouldShowDialog(activity: Activity) =
                runBlocking(CoroutinesDispatcherProvider().io) {
                    if (activity !is FileDisplayActivity) return@runBlocking false
                    val account = AccountUtils.getCurrentOpenCloudAccount(appContext) ?: return@runBlocking false

                    val getStoredCapabilitiesUseCase: GetStoredCapabilitiesUseCase by inject()
                    val capabilities = withContext(CoroutineScope(CoroutinesDispatcherProvider().io).coroutineContext) {
                        getStoredCapabilitiesUseCase(
                            GetStoredCapabilitiesUseCase.Params(
                                accountName = account.name
                            )
                        )
                    }
                    val spacesAllowed = capabilities != null && capabilities.isSpacesAllowed()

                    var personalSpace: OCSpace? = null
                    if (spacesAllowed) {
                        val getPersonalSpaceForAccountUseCase: GetPersonalSpaceForAccountUseCase by inject()
                        personalSpace = withContext(CoroutineScope(CoroutinesDispatcherProvider().io).coroutineContext) {
                            getPersonalSpaceForAccountUseCase(
                                GetPersonalSpaceForAccountUseCase.Params(
                                    accountName = account.name
                                )
                            )
                        }
                    }

                    val getStoredQuotaUseCase: GetStoredQuotaUseCase by inject()
                    val quota = withContext(CoroutineScope(CoroutinesDispatcherProvider().io).coroutineContext) {
                        getStoredQuotaUseCase(
                            GetStoredQuotaUseCase.Params(
                                accountName = account.name
                            )
                        )
                    }
                    val isLightUser = quota.getDataOrNull()?.available == -4L

                    spacesAllowed && personalSpace == null && !isLightUser
                }

            override fun onActivityStarted(activity: Activity) {
                Timber.v("${activity.javaClass.simpleName} onStart() starting")
                PassCodeManager.onActivityStarted(activity)
                PatternManager.onActivityStarted(activity)
                BiometricManager.onActivityStarted(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                Timber.v("${activity.javaClass.simpleName} onResume() starting")
            }

            override fun onActivityPaused(activity: Activity) {
                Timber.v("${activity.javaClass.simpleName} onPause() ending")
            }

            override fun onActivityStopped(activity: Activity) {
                Timber.v("${activity.javaClass.simpleName} onStop() ending")
                PassCodeManager.onActivityStopped(activity)
                PatternManager.onActivityStopped(activity)
                BiometricManager.onActivityStopped(activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                Timber.v("${activity.javaClass.simpleName} onSaveInstanceState(Bundle) starting")
            }

            override fun onActivityDestroyed(activity: Activity) {
                Timber.v("${activity.javaClass.simpleName} onDestroy() ending")
            }
        })

    }

    private fun startLogsIfEnabled() {
        val preferenceProvider = OCSharedPreferencesProvider(applicationContext)

        if (BuildConfig.DEBUG) {
            val alreadySet = preferenceProvider.containsPreference(PREFERENCE_ENABLE_LOGGING)
            if (!alreadySet) {
                preferenceProvider.putBoolean(PREFERENCE_ENABLE_LOGGING, true)
            }
        }

        enabledLogging = preferenceProvider.getBoolean(PREFERENCE_ENABLE_LOGGING, false)

        if (enabledLogging) {
            val mdmProvider = MdmProvider(applicationContext)
            LogsProvider(applicationContext, mdmProvider).startLogging()
        }
    }

    /**
     * Screenshots allowed in debug mode. Devs and tests <3
     * Otherwise, depends on branding.
     */
    private fun areScreenshotsAllowed(): Boolean {
        if (BuildConfig.DEBUG) return true

        val mdmProvider = MdmProvider(applicationContext)
        return mdmProvider.getBrandingBoolean(CONFIGURATION_ALLOW_SCREENSHOTS, R.bool.allow_screenshots)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        createNotificationChannel(
            id = DOWNLOAD_NOTIFICATION_CHANNEL_ID,
            name = getString(R.string.download_notification_channel_name),
            description = getString(R.string.download_notification_channel_description),
            importance = IMPORTANCE_LOW
        )

        createNotificationChannel(
            id = UPLOAD_NOTIFICATION_CHANNEL_ID,
            name = getString(R.string.upload_notification_channel_name),
            description = getString(R.string.upload_notification_channel_description),
            importance = IMPORTANCE_LOW
        )

        createNotificationChannel(
            id = MEDIA_SERVICE_NOTIFICATION_CHANNEL_ID,
            name = getString(R.string.media_service_notification_channel_name),
            description = getString(R.string.media_service_notification_channel_description),
            importance = IMPORTANCE_LOW
        )

        createNotificationChannel(
            id = FILE_SYNC_CONFLICT_NOTIFICATION_CHANNEL_ID,
            name = getString(R.string.file_sync_conflict_notification_channel_name),
            description = getString(R.string.file_sync_conflict_notification_channel_description),
            importance = IMPORTANCE_LOW
        )

        createNotificationChannel(
            id = FILE_SYNC_NOTIFICATION_CHANNEL_ID,
            name = getString(R.string.file_sync_notification_channel_name),
            description = getString(R.string.file_sync_notification_channel_description),
            importance = IMPORTANCE_LOW
        )
    }

    private fun isFirstRun(): Boolean {
        if (getLastSeenVersionCode() != 0) {
            return false
        }
        return AccountUtils.getCurrentOpenCloudAccount(appContext) == null
    }

    companion object {
        const val MDM_FLAVOR = "mdm"

        lateinit var appContext: Context
            private set
        var enabledLogging: Boolean = false
            private set

        const val PREFERENCE_KEY_LAST_SEEN_VERSION_CODE = "lastSeenVersionCode"

        const val PREFERENCE_KEY_DONT_SHOW_SERVER_ACCOUNT_WARNING_DIALOG = "PREFERENCE_KEY_DONT_SHOW_SERVER_ACCOUNT_WARNING_DIALOG"

        /**
         * Next methods give access in code to some constants that need to be defined in string resources to be referred
         * in AndroidManifest.xml file or other xml resource files; or that need to be easy to modify in build time.
         */

        val accountType: String
            get() = appContext.resources.getString(R.string.account_type)

        val versionCode: Int
            get() =
                try {
                    val pInfo: PackageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
                    val longVersionCode: Long = PackageInfoCompat.getLongVersionCode(pInfo)
                    longVersionCode.toInt()
                } catch (e: PackageManager.NameNotFoundException) {
                    Timber.w(e, "Version code not found, using 0 as fallback")
                    0
                }

        val authority: String
            get() = appContext.resources.getString(R.string.authority)

        val authTokenType: String
            get() = appContext.resources.getString(R.string.authority)

        val dataFolder: String
            get() = appContext.resources.getString(R.string.data_folder)

        // user agent
        // Mozilla/5.0 (Android) openCloud-android/1.7.0
        val userAgent: String
            get() {
                val appString = appContext.resources.getString(R.string.user_agent)
                val packageName = appContext.packageName
                var version: String?  = ""

                val pInfo: PackageInfo?
                try {
                    pInfo = appContext.packageManager.getPackageInfo(packageName, 0)
                    if (pInfo != null) {
                        version = pInfo.versionName
                    }
                } catch (e: PackageManager.NameNotFoundException) {
                    Timber.e(e, "Trying to get packageName")
                }

                return String.format(appString, version)
            }

        fun initDependencyInjection() {
            stopKoin()
            startKoin {
                androidContext(appContext)
                modules(
                    listOf(
                        commonModule,
                        viewModelModule,
                        useCaseModule,
                        repositoryModule,
                        localDataSourceModule,
                        remoteDataSourceModule
                    )
                )
            }
        }

        fun getLastSeenVersionCode(): Int {
            val pref = PreferenceManager.getDefaultSharedPreferences(appContext)
            return pref.getInt(PREFERENCE_KEY_LAST_SEEN_VERSION_CODE, 0)
        }

        private fun isNewVersionCode(): Boolean {
            val lastSeenVersionCode = getLastSeenVersionCode()
            if (lastSeenVersionCode == 0) { // The preferences have been deleted, so we can delete the accounts and navigate to login
                return false
            }
            return lastSeenVersionCode != versionCode // The version has changed and the accounts must not be deleted
        }
    }
}
