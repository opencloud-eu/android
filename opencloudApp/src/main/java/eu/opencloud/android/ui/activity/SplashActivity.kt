/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 *
 * Copyright (C) 2020 ownCloud GmbH.
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

package eu.opencloud.android.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import eu.opencloud.android.BuildConfig
import eu.opencloud.android.MainApp
import eu.opencloud.android.R
import eu.opencloud.android.data.providers.implementation.OCSharedPreferencesProvider
import eu.opencloud.android.presentation.security.LockTimeout
import eu.opencloud.android.presentation.security.PREFERENCE_LOCK_TIMEOUT
import eu.opencloud.android.providers.MdmProvider
import eu.opencloud.android.utils.CONFIGURATION_ALLOW_SCREENSHOTS
import eu.opencloud.android.utils.CONFIGURATION_DEVICE_PROTECTION
import eu.opencloud.android.utils.CONFIGURATION_LOCK_DELAY_TIME
import eu.opencloud.android.utils.CONFIGURATION_OAUTH2_OPEN_ID_PROMPT
import eu.opencloud.android.utils.CONFIGURATION_OAUTH2_OPEN_ID_SCOPE
import eu.opencloud.android.utils.CONFIGURATION_REDACT_AUTH_HEADER_LOGS
import eu.opencloud.android.utils.CONFIGURATION_SEND_LOGIN_HINT_AND_USER
import eu.opencloud.android.utils.CONFIGURATION_SERVER_URL
import eu.opencloud.android.utils.CONFIGURATION_SERVER_URL_INPUT_VISIBILITY

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mdmProvider = MdmProvider(this)

        if (BuildConfig.FLAVOR == MainApp.MDM_FLAVOR) {
            with(mdmProvider) {
                cacheStringRestriction(CONFIGURATION_SERVER_URL, R.string.server_url_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_SERVER_URL_INPUT_VISIBILITY, R.string.server_url_input_visibility_configuration_feedback_ok)
                cacheIntegerRestriction(CONFIGURATION_LOCK_DELAY_TIME, R.string.lock_delay_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_ALLOW_SCREENSHOTS, R.string.allow_screenshots_configuration_feedback_ok)
                cacheStringRestriction(CONFIGURATION_OAUTH2_OPEN_ID_SCOPE, R.string.oauth2_open_id_scope_configuration_feedback_ok)
                cacheStringRestriction(CONFIGURATION_OAUTH2_OPEN_ID_PROMPT, R.string.oauth2_open_id_prompt_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_DEVICE_PROTECTION, R.string.device_protection_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_REDACT_AUTH_HEADER_LOGS, R.string.redact_auth_header_logs_configuration_feedback_ok)
                cacheBooleanRestriction(CONFIGURATION_SEND_LOGIN_HINT_AND_USER, R.string.send_login_hint_and_user_configuration_feedback_ok)
            }
        }

        checkLockDelayEnforced(mdmProvider)

        startActivity(Intent(this, FileDisplayActivity::class.java))
        finish()
    }

    private fun checkLockDelayEnforced(mdmProvider: MdmProvider) {

        val lockDelayEnforced = mdmProvider.getBrandingInteger(CONFIGURATION_LOCK_DELAY_TIME, R.integer.lock_delay_enforced)
        val lockTimeout = LockTimeout.parseFromInteger(lockDelayEnforced)

        if (lockTimeout != LockTimeout.DISABLED) {
            OCSharedPreferencesProvider(this@SplashActivity).putString(PREFERENCE_LOCK_TIMEOUT, lockTimeout.name)
        }
    }
}
