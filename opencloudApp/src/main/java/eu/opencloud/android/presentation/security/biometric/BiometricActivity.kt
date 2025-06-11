/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * @author Juan Carlos Garrote Gascón
 * @author Jorge Aguado Recio
 *
 * Copyright (C) 2025 ownCloud GmbH.
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

package eu.opencloud.android.presentation.security.biometric

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import eu.opencloud.android.R
import eu.opencloud.android.presentation.security.passcode.PassCodeActivity
import eu.opencloud.android.presentation.security.passcode.PassCodeManager
import eu.opencloud.android.presentation.security.pattern.PatternManager
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber
import java.util.concurrent.Executor
import eu.opencloud.android.presentation.security.biometric.BiometricManager as OpenCloudBiometricManager

class BiometricActivity : AppCompatActivity() {

    // ViewModel
    private val biometricViewModel by viewModel<BiometricViewModel>()

    private lateinit var cryptoObject: BiometricPrompt.CryptoObject
    private val handler = Handler()
    private val executor = Executor { command -> handler.post(command) }

    /**
     * Initializes the activity.
     * <p>
     * An intent with a valid ACTION is expected; if none is found, an
     * [IllegalArgumentException] will be thrown.
     *
     * @param savedInstanceState Previously saved state - irrelevant in this case
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        biometricViewModel.initCipher()?.let {
            cryptoObject = BiometricPrompt.CryptoObject(it)
        }

        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
            showBiometricPrompt()
        } else {
            authError(biometricHasFailed = true)
        }
    }

    private fun showBiometricPrompt() {
        val promptInfo = PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(android.R.string.cancel))
            .setConfirmationRequired(true)
            .build()
        val biometricPrompt = BiometricPrompt(this@BiometricActivity,
            executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Timber.e("onAuthenticationError ($errorCode): $errString")
                    authError(biometricHasFailed = errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    if (result.cryptoObject?.cipher != cryptoObject.cipher) {
                        authError(biometricHasFailed = true)
                    } else {
                        if (biometricViewModel.shouldAskForNewPassCode()) {
                            biometricViewModel.removePassCode()
                            val intent = Intent(baseContext, PassCodeActivity::class.java)
                            intent.action = PassCodeActivity.ACTION_CREATE
                            intent.putExtra(PassCodeActivity.EXTRAS_MIGRATION, true)
                            startActivity(intent)
                        }
                        biometricViewModel.setLastUnlockTimestamp()
                        OpenCloudBiometricManager.onActivityStopped(this@BiometricActivity)
                        finish()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Timber.e("onAuthenticationFailed")
                }
            })

        // Displays the "log in" prompt.
        try {
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } catch (e: Exception) {
            Timber.e(e, "cryptoObject property has not been initialized correctly")
            authError(biometricHasFailed = true)
        }
    }

    private fun authError(biometricHasFailed: Boolean) {
        if (PassCodeManager.isPassCodeEnabled()) {
            PassCodeManager.onBiometricCancelled(this, biometricHasFailed)
        } else if (PatternManager.isPatternEnabled()) {
            PatternManager.onBiometricCancelled(this, biometricHasFailed)
        }

        finish()
    }

    companion object {
        const val PREFERENCE_SET_BIOMETRIC = "set_biometric"

        const val KEY_NAME = "default_key"
    }
}
