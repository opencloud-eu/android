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

package eu.opencloud.android.presentation.security.pattern

import androidx.lifecycle.ViewModel
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.presentation.security.AppLockSecretHash
import eu.opencloud.android.presentation.security.biometric.BiometricActivity

class PatternViewModel(
    private val preferencesProvider: SharedPreferencesProvider
) : ViewModel() {

    fun setPattern(pattern: String) {
        preferencesProvider.putString(PatternActivity.PREFERENCE_PATTERN, AppLockSecretHash.hash(pattern))
        preferencesProvider.putBoolean(PatternActivity.PREFERENCE_SET_PATTERN, true)
    }

    fun removePattern() {
        preferencesProvider.removePreference(PatternActivity.PREFERENCE_PATTERN)
        preferencesProvider.putBoolean(PatternActivity.PREFERENCE_SET_PATTERN, false)
    }

    fun checkPatternIsValid(patternValue: String?): Boolean {
        if (patternValue == null) return false

        val savedPattern = preferencesProvider.getString(PatternActivity.PREFERENCE_PATTERN, null)
        if (savedPattern.isNullOrEmpty()) return false

        val isValid = AppLockSecretHash.verify(patternValue, savedPattern)
        if (isValid && !AppLockSecretHash.isHash(savedPattern)) {
            setPattern(patternValue)
        }
        return isValid
    }

    fun setBiometricsState(enabled: Boolean) {
        preferencesProvider.putBoolean(BiometricActivity.PREFERENCE_SET_BIOMETRIC, enabled)
    }
}
