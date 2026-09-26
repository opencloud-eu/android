/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2021 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import eu.opencloud.android.BuildConfig
import eu.opencloud.android.R
import eu.opencloud.android.presentation.releasenotes.ReleaseNotesActivity
import eu.opencloud.android.presentation.settings.privacypolicy.PrivacyPolicyActivity
import eu.opencloud.android.presentation.settings.SettingsFragment
import eu.opencloud.android.presentation.settings.AppearanceMode
import eu.opencloud.android.presentation.releasenotes.ReleaseNotesViewModel
import eu.opencloud.android.presentation.settings.more.SettingsMoreViewModel
import eu.opencloud.android.presentation.settings.SettingsViewModel
import eu.opencloud.android.providers.WorkManagerProvider
import eu.opencloud.android.utils.matchers.verifyPreference
import eu.opencloud.android.utils.releaseNotesList
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class SettingsFragmentTest {

    private lateinit var fragmentScenario: FragmentScenario<SettingsFragment>

    private var subsectionSecurity: Preference? = null
    private var prefAppearance: ListPreference? = null
    private var subsectionLogging: Preference? = null
    private var subsectionPictureUploads: Preference? = null
    private var subsectionVideoUploads: Preference? = null
    private var subsectionMore: Preference? = null
    private var prefPrivacyPolicy: Preference? = null
    private var subsectionWhatsNew: Preference? = null
    private var prefAboutApp: Preference? = null

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var moreViewModel: SettingsMoreViewModel
    private lateinit var releaseNotesViewModel: ReleaseNotesViewModel
    private lateinit var context: Context

    private lateinit var version: String
    private var previousNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
    private var previousAppearance: String? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        previousNightMode = AppCompatDelegate.getDefaultNightMode()
        previousAppearance = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(AppearanceMode.PREFERENCE_KEY, null)
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(AppearanceMode.PREFERENCE_KEY).commit()
        settingsViewModel = mockk(relaxed = true)
        moreViewModel = mockk(relaxed = true)
        releaseNotesViewModel = mockk(relaxed = true)

        stopKoin()

        startKoin {
            context
            allowOverride(override = true)
            modules(
                module {
                    single { mockk<WorkManagerProvider>(relaxed = true) }
                    viewModel {
                        settingsViewModel
                    }
                    viewModel {
                        moreViewModel
                    }
                    viewModel {
                        releaseNotesViewModel
                    }
                }
            )
        }

        version = String.format(
            context.getString(R.string.prefs_app_version_summary),
            context.getString(R.string.app_name),
            BuildConfig.BUILD_TYPE,
            BuildConfig.VERSION_NAME,
            BuildConfig.COMMIT_SHA1
        )

        Intents.init()
    }

    @After
    fun tearDown() {
        if (::fragmentScenario.isInitialized) {
            fragmentScenario.close()
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(AppearanceMode.PREFERENCE_KEY, previousAppearance).commit()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(previousNightMode)
        }
        Intents.release()
        unmockkAll()
    }

    private fun launchTest(
        attachedAccount: Boolean,
        moreSectionVisible: Boolean = true,
        privacyPolicyEnabled: Boolean = true,
        whatsNewSectionVisible: Boolean = true
    ) {
        every { settingsViewModel.isThereAttachedAccount() } returns attachedAccount
        every { moreViewModel.shouldMoreSectionBeVisible() } returns moreSectionVisible
        every { moreViewModel.isPrivacyPolicyEnabled() } returns privacyPolicyEnabled
        every { releaseNotesViewModel.shouldWhatsNewSectionBeVisible() } returns whatsNewSectionVisible

        fragmentScenario = launchFragmentInContainer(themeResId = R.style.Theme_openCloud)
        fragmentScenario.onFragment { fragment ->
            subsectionSecurity = fragment.findPreference(SUBSECTION_SECURITY)
            prefAppearance = fragment.findPreference(AppearanceMode.PREFERENCE_KEY)
            subsectionLogging = fragment.findPreference(SUBSECTION_LOGGING)
            subsectionPictureUploads = fragment.findPreference(SUBSECTION_PICTURE_UPLOADS)
            subsectionVideoUploads = fragment.findPreference(SUBSECTION_VIDEO_UPLOADS)
            subsectionMore = fragment.findPreference(SUBSECTION_MORE)
            prefPrivacyPolicy = fragment.findPreference(PREFERENCE_PRIVACY_POLICY)
            subsectionWhatsNew = fragment.findPreference(SUBSECTION_WHATSNEW)
            prefAboutApp = fragment.findPreference(PREFERENCE_ABOUT_APP)
        }
    }

    @Test
    fun settingsViewCommon() {
        launchTest(attachedAccount = false)

        assertEquals(
            listOf(
                context.getString(R.string.prefs_appearance_system),
                context.getString(R.string.prefs_appearance_light),
                context.getString(R.string.prefs_appearance_dark)
            ),
            prefAppearance?.entries?.toList()
        )
        assertEquals(AppearanceMode.entries.map { it.name }, prefAppearance?.entryValues?.map { it.toString() })
        assertEquals(AppearanceMode.SYSTEM.name, prefAppearance?.value)
        prefAppearance?.verifyPreference(
            keyPref = AppearanceMode.PREFERENCE_KEY,
            titlePref = context.getString(R.string.prefs_appearance),
            summaryPref = context.getString(R.string.prefs_appearance_system),
            visible = true,
            enabled = true
        )

        subsectionSecurity?.verifyPreference(
            keyPref = SUBSECTION_SECURITY,
            titlePref = context.getString(R.string.prefs_subsection_security),
            summaryPref = context.getString(R.string.prefs_subsection_security_summary),
            visible = true,
            enabled = true
        )

        subsectionLogging?.verifyPreference(
            keyPref = SUBSECTION_LOGGING,
            titlePref = context.getString(R.string.prefs_subsection_logging),
            summaryPref = context.getString(R.string.prefs_subsection_logging_summary),
            visible = true,
            enabled = true
        )

        subsectionMore?.verifyPreference(
            keyPref = SUBSECTION_MORE,
            titlePref = context.getString(R.string.prefs_subsection_more),
            summaryPref = context.getString(R.string.prefs_subsection_more_summary),
            visible = true,
            enabled = true
        )

        prefPrivacyPolicy?.verifyPreference(
            keyPref = PREFERENCE_PRIVACY_POLICY,
            titlePref = context.getString(R.string.prefs_privacy_policy),
            visible = true,
            enabled = true
        )

        subsectionWhatsNew?.verifyPreference(
            keyPref = SUBSECTION_WHATSNEW,
            titlePref = context.getString(R.string.prefs_subsection_whatsnew),
            visible = true,
            enabled = true
        )

        prefAboutApp?.verifyPreference(
            keyPref = PREFERENCE_ABOUT_APP,
            titlePref = context.getString(R.string.prefs_app_version),
            summaryPref = version,
            visible = true,
            enabled = true
        )
    }

    @Test
    fun invalidAppearanceModeIsNormalizedToSystem() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(AppearanceMode.PREFERENCE_KEY, "INVALID")
            .commit()

        launchTest(attachedAccount = false)

        assertEquals(AppearanceMode.SYSTEM.name, prefAppearance?.value)
        assertEquals(context.getString(R.string.prefs_appearance_system), prefAppearance?.summary)
        assertEquals(
            AppearanceMode.SYSTEM.name,
            PreferenceManager.getDefaultSharedPreferences(context)
                .getString(AppearanceMode.PREFERENCE_KEY, null)
        )
    }

    @Test
    fun appearanceSelectionAppliesAndPersistsNightMode() {
        launchTest(attachedAccount = false)

        listOf(
            AppearanceMode.LIGHT to R.string.prefs_appearance_light,
            AppearanceMode.DARK to R.string.prefs_appearance_dark,
            AppearanceMode.SYSTEM to R.string.prefs_appearance_system
        ).forEach { (mode, label) ->
            onView(withText(R.string.prefs_appearance)).perform(click())
            onView(withText(label)).perform(click())

            assertEquals(mode.nightMode, AppCompatDelegate.getDefaultNightMode())
            assertEquals(
                mode.name,
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(AppearanceMode.PREFERENCE_KEY, null)
            )
            fragmentScenario.recreate()
            fragmentScenario.onFragment { fragment ->
                val preference = fragment.findPreference<ListPreference>(AppearanceMode.PREFERENCE_KEY)
                assertEquals(mode.name, preference?.value)
                assertEquals(context.getString(label), preference?.summary)
            }
        }
    }

    @Test
    fun settingsViewNoAccountAttached() {
        launchTest(attachedAccount = false)

        subsectionPictureUploads?.verifyPreference(
            keyPref = SUBSECTION_PICTURE_UPLOADS,
            titlePref = context.getString(R.string.prefs_subsection_picture_uploads),
            summaryPref = context.getString(R.string.prefs_subsection_picture_uploads_summary),
            visible = false
        )

        subsectionVideoUploads?.verifyPreference(
            keyPref = SUBSECTION_VIDEO_UPLOADS,
            titlePref = context.getString(R.string.prefs_subsection_video_uploads),
            summaryPref = context.getString(R.string.prefs_subsection_video_uploads_summary),
            visible = false
        )
    }

    @Test
    fun settingsViewAccountAttached() {
        launchTest(attachedAccount = true)

        subsectionPictureUploads?.verifyPreference(
            keyPref = SUBSECTION_PICTURE_UPLOADS,
            titlePref = context.getString(R.string.prefs_subsection_picture_uploads),
            summaryPref = context.getString(R.string.prefs_subsection_picture_uploads_summary),
            visible = true,
            enabled = true
        )

        subsectionVideoUploads?.verifyPreference(
            keyPref = SUBSECTION_VIDEO_UPLOADS,
            titlePref = context.getString(R.string.prefs_subsection_video_uploads),
            summaryPref = context.getString(R.string.prefs_subsection_video_uploads_summary),
            visible = true,
            enabled = true
        )
    }

    @Test
    fun settingsMoreSectionHidden() {
        launchTest(attachedAccount = false, moreSectionVisible = false)

        subsectionMore?.verifyPreference(
            keyPref = SUBSECTION_MORE,
            titlePref = context.getString(R.string.prefs_subsection_more),
            summaryPref = context.getString(R.string.prefs_subsection_more_summary),
            visible = false
        )
    }

    @Test
    fun settingsWhatsNewSectionHidden() {
        launchTest(attachedAccount = false, whatsNewSectionVisible = false)

        subsectionWhatsNew?.verifyPreference(
            keyPref = SUBSECTION_WHATSNEW,
            titlePref = context.getString(R.string.prefs_subsection_whatsnew),
            visible = false
        )
    }

    @Test
    fun privacyPolicyOpensPrivacyPolicyActivity() {
        launchTest(attachedAccount = false)

        onView(withText(R.string.prefs_privacy_policy)).perform(click())
        Intents.intended(IntentMatchers.hasComponent(PrivacyPolicyActivity::class.java.name))
    }

    @Ignore("Flaky test")
    @Test
    fun whatsnewOpensReleaseNotesActivity() {
        launchTest(attachedAccount = false)
        every { releaseNotesViewModel.getReleaseNotes() } returns releaseNotesList

        onView(withText(R.string.prefs_subsection_whatsnew)).perform(click())
        Intents.intended(IntentMatchers.hasComponent(ReleaseNotesActivity::class.java.name))
    }

    @Test
    fun clickOnAppVersion() {
        launchTest(attachedAccount = false)

        onView(withText(R.string.prefs_app_version)).perform(click())
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?

        onView(withText(R.string.clipboard_text_copied)).check(matches(isEnabled()))
        assertEquals(version, clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context))
    }

    companion object {
        private const val SUBSECTION_SECURITY = "security_subsection"
        private const val SUBSECTION_LOGGING = "logging_subsection"
        private const val SUBSECTION_PICTURE_UPLOADS = "picture_uploads_subsection"
        private const val SUBSECTION_VIDEO_UPLOADS = "video_uploads_subsection"
        private const val SUBSECTION_MORE = "more_subsection"
        private const val PREFERENCE_PRIVACY_POLICY = "privacyPolicy"
        private const val PREFERENCE_ABOUT_APP = "about_app"
        private const val SUBSECTION_WHATSNEW = "whatsNew"
    }
}
