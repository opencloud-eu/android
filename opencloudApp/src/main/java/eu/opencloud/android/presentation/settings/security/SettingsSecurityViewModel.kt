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

package eu.opencloud.android.presentation.settings.security

import androidx.lifecycle.ViewModel
import eu.opencloud.android.R
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.presentation.security.LockEnforcedType
import eu.opencloud.android.presentation.security.LockEnforcedType.Companion.parseFromInteger
import eu.opencloud.android.presentation.security.LockTimeout
import eu.opencloud.android.presentation.security.biometric.BiometricActivity
import eu.opencloud.android.presentation.security.isDeviceSecure
import eu.opencloud.android.presentation.security.passcode.PassCodeActivity
import eu.opencloud.android.presentation.security.pattern.PatternActivity
import eu.opencloud.android.providers.MdmProvider
import eu.opencloud.android.utils.CONFIGURATION_DEVICE_PROTECTION
import eu.opencloud.android.utils.CONFIGURATION_LOCK_DELAY_TIME
import eu.opencloud.android.utils.NO_MDM_RESTRICTION_YET

class SettingsSecurityViewModel(
    private val preferencesProvider: SharedPreferencesProvider,
    private val mdmProvider: MdmProvider,
) : ViewModel() {

    fun isPatternSet() = preferencesProvider.getBoolean(PatternActivity.PREFERENCE_SET_PATTERN, false)

    fun isPasscodeSet() = preferencesProvider.getBoolean(PassCodeActivity.PREFERENCE_SET_PASSCODE, false)

    fun setPrefLockAccessDocumentProvider(value: Boolean) =
        preferencesProvider.putBoolean(SettingsSecurityFragment.PREFERENCE_LOCK_ACCESS_FROM_DOCUMENT_PROVIDER, value)

    fun setPrefTouchesWithOtherVisibleWindows(value: Boolean) =
        preferencesProvider.putBoolean(SettingsSecurityFragment.PREFERENCE_TOUCHES_WITH_OTHER_VISIBLE_WINDOWS, value)

    fun getBiometricsState(): Boolean = preferencesProvider.getBoolean(BiometricActivity.PREFERENCE_SET_BIOMETRIC, false)

    // If device protection is true and device is not secure, or lock_enforced is enabled.
    fun isSecurityEnforcedEnabled() =
        (mdmProvider.getBrandingBoolean(CONFIGURATION_DEVICE_PROTECTION, R.bool.device_protection) && !isDeviceSecure()) ||
                parseFromInteger(mdmProvider.getBrandingInteger(NO_MDM_RESTRICTION_YET, R.integer.lock_enforced)) != LockEnforcedType.DISABLED

    fun isLockDelayEnforcedEnabled() = LockTimeout.parseFromInteger(
        mdmProvider.getBrandingInteger(
            mdmKey = CONFIGURATION_LOCK_DELAY_TIME,
            integerKey = R.integer.lock_delay_enforced
        )
    ) != LockTimeout.DISABLED

    // Download Everything Feature
    fun isDownloadEverythingEnabled(): Boolean =
        preferencesProvider.getBoolean(SettingsSecurityFragment.PREFERENCE_DOWNLOAD_EVERYTHING, false)

    fun setDownloadEverything(enabled: Boolean) =
        preferencesProvider.putBoolean(SettingsSecurityFragment.PREFERENCE_DOWNLOAD_EVERYTHING, enabled)

    // Auto-Sync Feature
    fun isAutoSyncEnabled(): Boolean =
        preferencesProvider.getBoolean(SettingsSecurityFragment.PREFERENCE_AUTO_SYNC, false)

    fun setAutoSync(enabled: Boolean) =
        preferencesProvider.putBoolean(SettingsSecurityFragment.PREFERENCE_AUTO_SYNC, enabled)

    // Conflict Resolution Strategy
    fun isPreferLocalOnConflictEnabled(): Boolean =
        preferencesProvider.getBoolean(SettingsSecurityFragment.PREFERENCE_PREFER_LOCAL_ON_CONFLICT, false)

    fun setPreferLocalOnConflict(enabled: Boolean) =
        preferencesProvider.putBoolean(SettingsSecurityFragment.PREFERENCE_PREFER_LOCAL_ON_CONFLICT, enabled)
}
