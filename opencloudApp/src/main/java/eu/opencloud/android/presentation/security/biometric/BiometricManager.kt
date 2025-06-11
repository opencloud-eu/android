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

package eu.opencloud.android.presentation.security.biometric

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import eu.opencloud.android.MainApp.Companion.appContext
import eu.opencloud.android.data.providers.implementation.OCSharedPreferencesProvider
import eu.opencloud.android.presentation.security.LockTimeout
import eu.opencloud.android.presentation.security.PREFERENCE_LAST_UNLOCK_TIMESTAMP
import eu.opencloud.android.presentation.security.PREFERENCE_LOCK_TIMEOUT
import eu.opencloud.android.presentation.security.bayPassUnlockOnce
import eu.opencloud.android.presentation.security.passcode.PassCodeActivity
import eu.opencloud.android.presentation.security.passcode.PassCodeManager
import eu.opencloud.android.presentation.security.passcode.PassCodeManager.isPassCodeEnabled
import eu.opencloud.android.presentation.security.pattern.PatternActivity
import eu.opencloud.android.presentation.security.pattern.PatternManager
import eu.opencloud.android.presentation.security.pattern.PatternManager.isPatternEnabled
import kotlin.math.abs

object BiometricManager {

    private val exemptOfBiometricActivities: MutableSet<Class<*>> =
        mutableSetOf(BiometricActivity::class.java, PassCodeActivity::class.java, PatternActivity::class.java)
    private val visibleActivities: MutableSet<Class<*>> = mutableSetOf()
    private val preferencesProvider = OCSharedPreferencesProvider(appContext)
    private val androidBiometricManager: BiometricManager = BiometricManager.from(appContext)

    fun onActivityStarted(activity: Activity) {
        if (!exemptOfBiometricActivities.contains(activity.javaClass) && biometricShouldBeRequested()) {

            if (isHardwareDetected() && hasEnrolledBiometric()) {
                // Use biometric lock
                val i = Intent(appContext, BiometricActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                activity.startActivity(i)
            } else if (isPassCodeEnabled()) {
                // Cancel biometric lock and use passcode unlock method
                PassCodeManager.onBiometricCancelled(activity = activity, biometricHasFailed = false)
                visibleActivities.add(activity.javaClass)
            } else if (isPatternEnabled()) {
                // Cancel biometric lock and use pattern unlock method
                PatternManager.onBiometricCancelled(activity = activity, biometricHasFailed = false)
                visibleActivities.add(activity.javaClass)
            }

        }

        visibleActivities.add(activity.javaClass) // keep it AFTER biometricShouldBeRequested was checked

    }

    fun onActivityStopped(activity: Activity) {
        visibleActivities.remove(activity.javaClass)

        bayPassUnlockOnce()
        val powerMgr = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (isBiometricEnabled() && !powerMgr.isInteractive) {
            activity.moveTaskToBack(true)
        }
    }

    private fun biometricShouldBeRequested(): Boolean {
        val lastUnlockTimestamp = preferencesProvider.getLong(PREFERENCE_LAST_UNLOCK_TIMESTAMP, 0)
        val timeout = LockTimeout.valueOf(preferencesProvider.getString(PREFERENCE_LOCK_TIMEOUT, LockTimeout.IMMEDIATELY.name)!!).toMilliseconds()
        return if (visibleActivities.contains(BiometricActivity::class.java)) isBiometricEnabled()
        else if (abs(SystemClock.elapsedRealtime() - lastUnlockTimestamp) > timeout && visibleActivities.isEmpty()) isBiometricEnabled()
        else false
    }

    fun isBiometricEnabled(): Boolean =
        preferencesProvider.getBoolean(BiometricActivity.PREFERENCE_SET_BIOMETRIC, false)

    fun isHardwareDetected(): Boolean =
        androidBiometricManager.canAuthenticate(BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE &&
                androidBiometricManager.canAuthenticate(BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

    fun hasEnrolledBiometric(): Boolean =
        androidBiometricManager.canAuthenticate(BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
}
