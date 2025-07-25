/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 * @author David Crespo Ríos
 * @author Aitor Ballesteros Pavón
 *
 * Copyright (C) 2024 ownCloud GmbH.
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

package eu.opencloud.android.presentation.settings

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import eu.opencloud.android.R
import eu.opencloud.android.presentation.settings.advanced.SettingsAdvancedFragment
import eu.opencloud.android.presentation.settings.automaticuploads.SettingsPictureUploadsFragment
import eu.opencloud.android.presentation.settings.automaticuploads.SettingsVideoUploadsFragment
import eu.opencloud.android.presentation.settings.logging.SettingsLogsFragment
import eu.opencloud.android.presentation.settings.more.SettingsMoreFragment
import eu.opencloud.android.presentation.settings.security.SettingsSecurityFragment
import eu.opencloud.android.ui.activity.FileDisplayActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.standard_toolbar).apply {
            isVisible = true
        }
        findViewById<ConstraintLayout>(R.id.root_toolbar).apply {
            isVisible = false
        }
        setSupportActionBar(toolbar)
        updateToolbarTitle()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager.addOnBackStackChangedListener { updateToolbarTitle() }

        if (savedInstanceState != null) return

        redirectToSubsection(intent)
    }

    private fun updateToolbarTitle() {
        val titleId = when (supportFragmentManager.fragments.lastOrNull()) {
            is SettingsSecurityFragment -> R.string.prefs_subsection_security
            is SettingsLogsFragment -> R.string.prefs_subsection_logging
            is SettingsPictureUploadsFragment -> R.string.prefs_subsection_picture_uploads
            is SettingsVideoUploadsFragment -> R.string.prefs_subsection_video_uploads
            is SettingsAdvancedFragment -> R.string.prefs_subsection_advanced
            is SettingsMoreFragment -> R.string.prefs_subsection_more
            else -> R.string.actionbar_settings
        }
        setTitle(titleId)
        supportActionBar?.setTitle(titleId)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    intent = Intent(this, FileDisplayActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun redirectToSubsection(intent: Intent?) {
        val fragment = when (intent?.getStringExtra(KEY_NOTIFICATION_INTENT)) {
            NOTIFICATION_INTENT_PICTURES -> SettingsPictureUploadsFragment()
            NOTIFICATION_INTENT_VIDEOS -> SettingsVideoUploadsFragment()
            else -> SettingsFragment()
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, fragment)
            .commit()
    }

    companion object {
        const val KEY_NOTIFICATION_INTENT = "key_notification_intent"
        const val NOTIFICATION_INTENT_PICTURES = "picture_uploads"
        const val NOTIFICATION_INTENT_VIDEOS = "video_uploads"
    }
}
