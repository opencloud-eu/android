/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

package eu.opencloud.android.presentation.settings.security

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.security.KeyChain
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import eu.opencloud.android.R
import eu.opencloud.android.extensions.avoidScreenshotsIfNeeded
import eu.opencloud.android.extensions.showMessageInSnackbar
import eu.opencloud.android.presentation.documentsprovider.DocumentsProviderUtils.notifyDocumentsProviderRoots
import eu.opencloud.android.presentation.security.LockTimeout
import eu.opencloud.android.presentation.security.PREFERENCE_LOCK_TIMEOUT
import eu.opencloud.android.presentation.security.biometric.BiometricActivity
import eu.opencloud.android.presentation.security.biometric.BiometricManager
import eu.opencloud.android.presentation.security.passcode.PassCodeActivity
import eu.opencloud.android.presentation.security.pattern.PatternActivity
import eu.opencloud.android.presentation.settings.SettingsFragment.Companion.removePreferenceFromScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsSecurityFragment : PreferenceFragmentCompat() {

    // ViewModel
    private val securityViewModel by viewModel<SettingsSecurityViewModel>()

    private var screenSecurity: PreferenceScreen? = null
    private var prefPasscode: CheckBoxPreference? = null
    private var prefPattern: CheckBoxPreference? = null
    private var prefBiometric: CheckBoxPreference? = null
    private var prefLockApplication: ListPreference? = null
    private var prefLockAccessDocumentProvider: CheckBoxPreference? = null
    private var prefTouchesWithOtherVisibleWindows: CheckBoxPreference? = null
    private var prefMtls: CheckBoxPreference? = null
    private var prefMtlsSelectCert: Preference? = null

    private val enablePasscodeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            } else {
                prefPasscode?.isChecked = true
                prefBiometric?.isChecked = securityViewModel.getBiometricsState()

                // Allow to use biometric lock, lock delay and access from document provider since Passcode lock has been enabled
                enableBiometricAndLockApplication()
            }
        }

    private val disablePasscodeLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            } else {
                prefPasscode?.isChecked = false

                // Do not allow to use biometric lock, lock delay nor access from document provider since Passcode lock has been disabled
                disableBiometric()
                prefLockApplication?.isEnabled = false
            }
        }

    private val enablePatternLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            } else {
                prefPattern?.isChecked = true
                prefBiometric?.isChecked = securityViewModel.getBiometricsState()

                // Allow to use biometric lock, lock delay and access from document provider since Pattern lock has been enabled
                enableBiometricAndLockApplication()
            }
        }

    private val disablePatternLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            } else {
                prefPattern?.isChecked = false

                // Do not allow to use biometric lock, lock delay nor access from document provider since Pattern lock has been disabled
                disableBiometric()
                prefLockApplication?.isEnabled = false
            }
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_security, rootKey)
        screenSecurity = findPreference(SCREEN_SECURITY)
        prefPasscode = findPreference(PassCodeActivity.PREFERENCE_SET_PASSCODE)
        prefPattern = findPreference(PatternActivity.PREFERENCE_SET_PATTERN)
        prefBiometric = findPreference(BiometricActivity.PREFERENCE_SET_BIOMETRIC)
        prefLockApplication = findPreference<ListPreference>(PREFERENCE_LOCK_TIMEOUT)?.apply {
            entries = listOf(
                getString(R.string.prefs_lock_application_entries_immediately),
                getString(R.string.prefs_lock_application_entries_1minute),
                getString(R.string.prefs_lock_application_entries_5minutes),
                getString(R.string.prefs_lock_application_entries_30minutes)
            ).toTypedArray()
            entryValues = listOf(
                LockTimeout.IMMEDIATELY.name,
                LockTimeout.ONE_MINUTE.name,
                LockTimeout.FIVE_MINUTES.name,
                LockTimeout.THIRTY_MINUTES.name
            ).toTypedArray()
            isEnabled = !securityViewModel.isLockDelayEnforcedEnabled()
        }
        prefLockAccessDocumentProvider = findPreference(PREFERENCE_LOCK_ACCESS_FROM_DOCUMENT_PROVIDER)
        prefTouchesWithOtherVisibleWindows = findPreference(PREFERENCE_TOUCHES_WITH_OTHER_VISIBLE_WINDOWS)

        prefPasscode?.isVisible = !securityViewModel.isSecurityEnforcedEnabled()
        prefPattern?.isVisible = !securityViewModel.isSecurityEnforcedEnabled()

        // Passcode lock
        prefPasscode?.setOnPreferenceChangeListener { _: Preference?, newValue: Any ->
            if (securityViewModel.isPatternSet()) {
                showMessageInSnackbar(getString(R.string.pattern_already_set))
            } else {
                val intent = Intent(activity, PassCodeActivity::class.java)
                if (newValue as Boolean) {
                    intent.action = PassCodeActivity.ACTION_CREATE
                    enablePasscodeLauncher.launch(intent)
                } else {
                    intent.action = PassCodeActivity.ACTION_REMOVE
                    disablePasscodeLauncher.launch(intent)
                }
            }
            false
        }

        // Pattern lock
        prefPattern?.setOnPreferenceChangeListener { _: Preference?, newValue: Any ->
            if (securityViewModel.isPasscodeSet()) {
                showMessageInSnackbar(getString(R.string.passcode_already_set))
            } else {
                val intent = Intent(activity, PatternActivity::class.java)
                if (newValue as Boolean) {
                    intent.action = PatternActivity.ACTION_REQUEST_WITH_RESULT
                    enablePatternLauncher.launch(intent)
                } else {
                    intent.action = PatternActivity.ACTION_CHECK_WITH_RESULT
                    disablePatternLauncher.launch(intent)
                }
            }
            false
        }

        // Biometric lock
        if (prefBiometric != null) {
            if (!BiometricManager.isHardwareDetected()) { // Biometric not supported
                screenSecurity?.removePreferenceFromScreen(prefBiometric)
            } else {
                // Disable biometric lock if Passcode or Pattern locks are disabled
                if (prefPasscode?.isChecked == false && prefPattern?.isChecked == false) { disableBiometric() }

                prefBiometric?.setOnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    val incomingValue = newValue as Boolean

                    // No biometric enrolled yet
                    if (incomingValue && !BiometricManager.hasEnrolledBiometric()) {
                        showMessageInSnackbar(getString(R.string.biometric_not_enrolled))
                        return@setOnPreferenceChangeListener false
                    }
                    true
                }
            }
        }

        // Lock application
        if (prefPasscode?.isChecked == false && prefPattern?.isChecked == false) { prefLockApplication?.isEnabled = false }

        // Lock access from document provider
        prefLockAccessDocumentProvider?.setOnPreferenceChangeListener { _: Preference?, newValue: Any ->
            securityViewModel.setPrefLockAccessDocumentProvider(true)
            notifyDocumentsProviderRoots(requireContext())
            true
        }

        // Touches with other visible windows
        prefTouchesWithOtherVisibleWindows?.setOnPreferenceChangeListener { _: Preference?, newValue: Any ->
            if (newValue as Boolean) {
                activity?.let {
                    AlertDialog.Builder(it)
                        .setTitle(getString(R.string.confirmation_touches_with_other_windows_title))
                        .setMessage(getString(R.string.confirmation_touches_with_other_windows_message))
                        .setNegativeButton(getString(R.string.common_no), null)
                        .setPositiveButton(
                            getString(R.string.common_yes)
                        ) { _: DialogInterface?, _: Int ->
                            securityViewModel.setPrefTouchesWithOtherVisibleWindows(true)
                            prefTouchesWithOtherVisibleWindows?.isChecked = true
                        }
                        .show()
                        .avoidScreenshotsIfNeeded()
                }
                return@setOnPreferenceChangeListener false
            }
            true
        }

        // mTLS client certificate
        prefMtls = findPreference(SettingsSecurityViewModel.PREFERENCE_ENABLE_MTLS)
        prefMtlsSelectCert = findPreference(SettingsSecurityViewModel.PREFERENCE_MTLS_SELECT_CERTIFICATE)

        updateMtlsCertSummary()

        prefMtls?.setOnPreferenceChangeListener { _: Preference?, newValue: Any ->
            val enabled = newValue as Boolean
            securityViewModel.setMtlsEnabled(requireContext(), enabled)
            if (!enabled) {
                securityViewModel.removeAlias(requireContext())
                updateMtlsCertSummary()
                invalidateHttpClients()
            }
            true
        }

        prefMtlsSelectCert?.setOnPreferenceClickListener {
            launchKeyChainPicker()
            true
        }
    }

    private fun launchKeyChainPicker() {
        val currentAlias = securityViewModel.getSelectedAlias(requireContext())
        KeyChain.choosePrivateKeyAlias(
            requireActivity(),
            { alias ->
                activity?.runOnUiThread {
                    if (alias != null) {
                        securityViewModel.setSelectedAlias(requireContext(), alias)
                        showMessageInSnackbar(getString(R.string.prefs_mtls_cert_selected))
                    } else {
                        securityViewModel.removeAlias(requireContext())
                        showMessageInSnackbar(getString(R.string.prefs_mtls_cert_removed))
                    }
                    updateMtlsCertSummary()
                    invalidateHttpClients()
                }
            },
            null, null, null, -1, currentAlias
        )
    }

    private fun updateMtlsCertSummary() {
        val alias = securityViewModel.getSelectedAlias(requireContext())
        prefMtlsSelectCert?.summary = if (alias != null) {
            getString(R.string.prefs_mtls_select_cert_summary, alias)
        } else {
            getString(R.string.prefs_mtls_select_cert_summary_none)
        }
    }

    private fun invalidateHttpClients() {
        eu.opencloud.android.lib.common.SingleSessionManager.getDefaultSingleton().invalidateAllClients()
    }

    private fun enableBiometricAndLockApplication() {
        prefBiometric?.apply {
            isEnabled = true
            summary = null
        }
        prefLockApplication?.isEnabled = !securityViewModel.isLockDelayEnforcedEnabled()
    }

    private fun disableBiometric() {
        prefBiometric?.apply {
            isChecked = false
            isEnabled = false
            summary = getString(R.string.prefs_biometric_summary)
        }
    }

    companion object {
        private const val SCREEN_SECURITY = "security_screen"
        const val PREFERENCE_LOCK_ACCESS_FROM_DOCUMENT_PROVIDER = "lock_access_from_document_provider"
        const val PREFERENCE_TOUCHES_WITH_OTHER_VISIBLE_WINDOWS = "touches_with_other_visible_windows"
        const val EXTRAS_LOCK_ENFORCED = "EXTRAS_LOCK_ENFORCED"
        const val PREFERENCE_LOCK_ATTEMPTS = "PrefLockAttempts"
    }
}
