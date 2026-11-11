/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 OpenCloud GmbH.
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

package eu.opencloud.android.presentation.settings.automaticuploads

import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import eu.opencloud.android.R
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.extensions.showMessageInSnackbar
import eu.opencloud.android.utils.MediaLocationUtils
import org.koin.android.ext.android.inject

/**
 * Asks for [Manifest.permission.ACCESS_MEDIA_LOCATION] from the picture and video automatic
 * upload settings. Without it, Android strips the GPS metadata from the media we read (API >= 29).
 *
 * The system dialog is only launched when Android will really show it, so the user is never
 * prompted on every visit and the "denied" hint only follows an answer the user actually gave.
 *
 * Must be created while the fragment is being initialized (it registers an activity result launcher).
 */
class MediaLocationPermissionRequester(private val fragment: Fragment) {

    private val preferencesProvider: SharedPreferencesProvider by fragment.inject()

    private val requestPermissionLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted && fragment.isAdded) {
                fragment.showMessageInSnackbar(fragment.getString(R.string.media_location_permission_denied))
            }
        }

    // True between the user switching uploads on and the "Important" dialog being dismissed,
    // so the system dialog does not open on top of it.
    private var isPostponed = false

    /**
     * The screen shows enabled automatic uploads: ask, but only if the user was never asked before.
     */
    fun requestIfNeverAsked() {
        if (isPostponed) return
        request(userInitiated = false)
    }

    /**
     * The user is switching automatic uploads on. Holds back [requestIfNeverAsked] until
     * [requestAfterEnabling] is called.
     */
    fun postpone() {
        isPostponed = true
    }

    /**
     * The user switched automatic uploads on and has dismissed the "Important" dialog. Asks again
     * after an earlier denial as long as Android still shows the dialog.
     */
    fun requestAfterEnabling() {
        isPostponed = false
        request(userInitiated = true)
    }

    private fun request(userInitiated: Boolean) {
        val context = fragment.context ?: return
        if (!fragment.isAdded) return

        val shouldRequest = shouldRequest(
            isGranted = MediaLocationUtils.isAccessMediaLocationGranted(context),
            alreadyRequested = preferencesProvider.getBoolean(PREFERENCE_MEDIA_LOCATION_PERMISSION_REQUESTED, false),
            shouldShowRationale = fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_MEDIA_LOCATION),
            userInitiated = userInitiated,
        )
        if (!shouldRequest) return

        preferencesProvider.putBoolean(PREFERENCE_MEDIA_LOCATION_PERMISSION_REQUESTED, true)
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    companion object {
        // Shared by pictures and videos: it is one permission, asked once.
        internal const val PREFERENCE_MEDIA_LOCATION_PERMISSION_REQUESTED = "PREFERENCE_MEDIA_LOCATION_PERMISSION_REQUESTED"

        /**
         * - Never asked: ask.
         * - Asked and denied once: Android reports a rationale and still shows the dialog. Ask again
         *   only when the user just enabled uploads, not on every visit to the screen.
         * - Asked and denied for good: Android answers "denied" by itself without showing anything,
         *   so do not launch at all.
         */
        internal fun shouldRequest(
            isGranted: Boolean,
            alreadyRequested: Boolean,
            shouldShowRationale: Boolean,
            userInitiated: Boolean,
        ): Boolean = !isGranted && (!alreadyRequested || (userInitiated && shouldShowRationale))
    }
}
