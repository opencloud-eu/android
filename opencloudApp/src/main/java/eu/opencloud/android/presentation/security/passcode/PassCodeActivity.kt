/**
 * openCloud Android client application
 *
 * @author Bartek Przybylski
 * @author masensio
 * @author David A. Velasco
 * @author Christian Schabesberger
 * @author David González Verdugo
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author David Crespo Ríos
 * @author  Aitor Ballesteros Pavón
 * @author Jorge Aguado Recio
 *
 * Copyright (C) 2011 Bartek Przybylski
 * Copyright (C) 2025 ownCloud GmbH.
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

package eu.opencloud.android.presentation.security.passcode

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import eu.opencloud.android.BuildConfig
import eu.opencloud.android.R
import eu.opencloud.android.databinding.PasscodelockBinding
import eu.opencloud.android.domain.utils.Event
import eu.opencloud.android.extensions.showBiometricDialog
import eu.opencloud.android.extensions.showMessageInSnackbar
import eu.opencloud.android.presentation.documentsprovider.DocumentsProviderUtils.notifyDocumentsProviderRoots
import eu.opencloud.android.presentation.security.biometric.BiometricStatus
import eu.opencloud.android.presentation.security.biometric.BiometricViewModel
import eu.opencloud.android.presentation.security.biometric.EnableBiometrics
import eu.opencloud.android.presentation.settings.security.SettingsSecurityFragment.Companion.EXTRAS_LOCK_ENFORCED
import eu.opencloud.android.utils.PreferenceUtils
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class PassCodeActivity : AppCompatActivity(), NumberKeyboardListener, EnableBiometrics {

    // ViewModel
    private val passCodeViewModel: PassCodeViewModel by viewModel {
        parametersOf(
            getPasscodeAction(intent.action)
        )
    }

    private val biometricViewModel by viewModel<BiometricViewModel>()

    private var _binding: PasscodelockBinding? = null
    val binding get() = _binding!!

    private lateinit var passCodeEditTexts: Array<EditText?>

    private var numberOfPasscodeDigits = 0
    private var confirmingPassCode = false
    private val resultIntent = Intent()

    /**
     * Initializes the activity.
     *
     * @param savedInstanceState    Previously saved state - irrelevant in this case
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        subscribeToViewModel()

        _binding = PasscodelockBinding.inflate(layoutInflater)

        // protection against screen recording
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } // else, let it go, or taking screenshots & testing will not be possible

        setContentView(binding.root)

        if (intent.getBooleanExtra(BIOMETRIC_HAS_FAILED, false)) {
            showMessageInSnackbar(message = getString(R.string.biometric_not_available))
        }

        numberOfPasscodeDigits = passCodeViewModel.getPassCode()?.length ?: passCodeViewModel.getNumberOfPassCodeDigits()
        passCodeEditTexts = arrayOfNulls(numberOfPasscodeDigits)

        // Allow or disallow touches with other visible windows
        binding.passcodeLockLayout.filterTouchesWhenObscured =
            PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(this)
        binding.explanation.filterTouchesWhenObscured =
            PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(this)

        inflatePasscodeTxtLine()

        binding.numberKeyboard.setListener(this)

        if (passCodeViewModel.getNumberOfAttempts() >= NUM_ATTEMPTS_WITHOUT_TIMER) {
            lockScreen()
        }

        when (intent.action) {
            ACTION_CHECK -> { //When you start the app with passcode
                // this is a pass code request; the user has to input the right value
                binding.header.text = getString(R.string.pass_code_enter_pass_code)
                binding.explanation.visibility = View.INVISIBLE
                supportActionBar?.setDisplayHomeAsUpEnabled(false) //Don´t show the back arrow
            }

            ACTION_CREATE -> { //Create a new password
                if (confirmingPassCode) {
                    //the app was in the passcode confirmation
                    requestPassCodeConfirmation()
                } else {
                    if (intent.extras?.getBoolean(EXTRAS_MIGRATION) == true) {
                        binding.header.text =
                            getString(R.string.pass_code_configure_your_pass_code_migration, passCodeViewModel.getNumberOfPassCodeDigits())
                    } else {
                        // pass code preference has just been activated in Preferences;
                        // will receive and confirm pass code value
                        binding.header.text = getString(R.string.pass_code_configure_your_pass_code)
                    }
                    binding.explanation.visibility = View.VISIBLE
                    when {
                        intent.extras?.getBoolean(EXTRAS_MIGRATION) == true -> {
                            supportActionBar?.setDisplayHomeAsUpEnabled(false)
                        }

                        intent.extras?.getBoolean(EXTRAS_LOCK_ENFORCED) == true -> {
                            supportActionBar?.setDisplayHomeAsUpEnabled(false)
                        }

                        else -> {
                            supportActionBar?.setDisplayHomeAsUpEnabled(true)
                        }
                    }
                }
            }

            ACTION_REMOVE -> { // Remove password
                // pass code preference has just been disabled in Preferences;
                // will confirm user knows pass code, then remove it
                binding.header.text = getString(R.string.pass_code_remove_your_pass_code)
                binding.explanation.visibility = View.INVISIBLE
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
            }

            else -> {
                throw IllegalArgumentException(R.string.illegal_argument_exception_message.toString() + " ")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        onBackPressed()
        return true
    }

    override fun onBackPressed() {
        PassCodeManager.onActivityStopped(this)
        super.onBackPressed()
    }

    private fun inflatePasscodeTxtLine() {
        val layoutCode = findViewById<LinearLayout>(R.id.layout_code)
        val numberOfPasscodeDigits = (passCodeViewModel.getPassCode()?.length ?: passCodeViewModel.getNumberOfPassCodeDigits())
        for (i in 0 until numberOfPasscodeDigits) {
            val txt = layoutInflater.inflate(R.layout.passcode_edit_text, layoutCode, false) as EditText
            layoutCode.addView(txt)
            passCodeEditTexts[i] = txt
        }
        passCodeEditTexts.first()?.requestFocus()
    }

    override fun onNumberClicked(number: Int) {
        passCodeViewModel.onNumberClicked(number)
    }

    override fun onBackspaceButtonClicked() {
        passCodeViewModel.onBackspaceClicked()
    }

    private fun subscribeToViewModel() {
        passCodeViewModel.getTimeToUnlockLiveData.observe(this, Event.EventObserver {
            binding.lockTime.text = getString(R.string.lock_time_try_again, it)
        })
        passCodeViewModel.getFinishedTimeToUnlockLiveData.observe(this, Event.EventObserver {
            binding.lockTime.visibility = View.INVISIBLE
            for (editText: EditText? in passCodeEditTexts) {
                editText?.isEnabled = true
            }
            passCodeEditTexts.first()?.requestFocus()
        })

        passCodeViewModel.status.observe(this) { status ->
            when (status.action) {
                PasscodeAction.CHECK -> {
                    when (status.type) {
                        PasscodeType.OK -> actionCheckOk()
                        PasscodeType.MIGRATION -> actionCheckMigration()
                        else -> actionCheckError()
                    }
                }

                PasscodeAction.REMOVE -> {
                    if (status.type == PasscodeType.OK) {
                        actionRemoveOk()
                    } else {
                        actionRemoveError()
                    }
                }

                PasscodeAction.CREATE -> {
                    when (status.type) {
                        PasscodeType.NO_CONFIRM -> actionCreateNoConfirm()
                        PasscodeType.CONFIRM -> actionCreateConfirm()
                        else -> actionCreateError()
                    }
                }
            }
        }

        passCodeViewModel.passcode.observe(this) { passcode ->
            if (passcode.isNotEmpty()) {
                passCodeEditTexts[passcode.length - 1]?.apply {
                    text = Editable.Factory.getInstance().newEditable(passcode.last().toString())
                    isEnabled = false
                }
            }

            if (passcode.length < numberOfPasscodeDigits) {
                //Backspace
                passCodeEditTexts[passcode.length]?.apply {
                    isEnabled = true
                    setText("")
                    requestFocus()
                }
            }
        }
    }

    private fun actionCheckOk() {
        // pass code accepted in request, user is allowed to access the app
        binding.error.visibility = View.INVISIBLE

        PassCodeManager.onActivityStopped(this)
        finish()
    }

    private fun actionCheckMigration() {
        binding.error.visibility = View.INVISIBLE

        val intent = Intent(baseContext, PassCodeActivity::class.java)
        intent.apply {
            action = ACTION_CREATE
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRAS_MIGRATION, true)
        }
        startActivity(intent)

        PassCodeManager.onActivityStopped(this)
        finish()
    }

    private fun actionCheckError() {
        showErrorAndRestart(
            errorMessage = R.string.pass_code_wrong, headerMessage = getString(R.string.pass_code_enter_pass_code),
            explanationVisibility = View.INVISIBLE
        )
        if (passCodeViewModel.getNumberOfAttempts() >= NUM_ATTEMPTS_WITHOUT_TIMER) {
            lockScreen()
        }
    }

    private fun actionRemoveOk() {
        val resultIntent = Intent()
        setResult(RESULT_OK, resultIntent)
        notifyDocumentsProviderRoots(applicationContext)
        finish()
    }

    private fun actionRemoveError() {
        showErrorAndRestart(
            errorMessage = R.string.pass_code_wrong, headerMessage = getString(R.string.pass_code_enter_pass_code),
            explanationVisibility = View.INVISIBLE
        )
    }

    private fun actionCreateNoConfirm() {
        binding.error.visibility = View.INVISIBLE
        requestPassCodeConfirmation()
    }

    private fun actionCreateConfirm() {
        // confirmed: user typed the same pass code twice
        if (intent.extras?.getBoolean(EXTRAS_MIGRATION) == true) passCodeViewModel.setMigrationRequired(false)
        savePassCodeAndExit()
    }

    private fun actionCreateError() {
        val headerMessage = if (intent.extras?.getBoolean(EXTRAS_MIGRATION) == true) getString(
            R.string.pass_code_configure_your_pass_code_migration,
            passCodeViewModel.getNumberOfPassCodeDigits()
        )
        else getString(R.string.pass_code_configure_your_pass_code)
        showErrorAndRestart(
            errorMessage = R.string.pass_code_mismatch, headerMessage = headerMessage, explanationVisibility = View.VISIBLE
        )
    }

    private fun lockScreen() {
        val timeToUnlock = passCodeViewModel.getTimeToUnlockLeft()
        if (timeToUnlock > 0) {
            binding.lockTime.visibility = View.VISIBLE
            for (editText: EditText? in passCodeEditTexts) {
                editText?.isEnabled = false
            }
            passCodeViewModel.initUnlockTimer()
        }
    }

    private fun showErrorAndRestart(
        errorMessage: Int, headerMessage: String,
        explanationVisibility: Int
    ) {
        binding.error.setText(errorMessage)
        binding.error.visibility = View.VISIBLE
        binding.header.text = headerMessage
        binding.explanation.visibility = explanationVisibility
        clearBoxes()
    }

    /**
     * Ask to the user for retyping the pass code just entered before saving it as the current pass
     * code.
     */
    private fun requestPassCodeConfirmation() {
        clearBoxes()
        binding.header.setText(R.string.pass_code_reenter_your_pass_code)
        binding.explanation.visibility = View.INVISIBLE
        confirmingPassCode = true
    }

    /**
     * Sets the input fields to empty strings and puts the focus on the first one.
     */
    private fun clearBoxes() {
        for (passCodeEditText in passCodeEditTexts) {
            passCodeEditText?.apply {
                isEnabled = true
                setText("")
            }
        }
        passCodeEditTexts.first()?.requestFocus()
    }

    /**
     * Overrides click on the BACK arrow to correctly cancel ACTION_ENABLE or ACTION_DISABLE, while
     * preventing than ACTION_CHECK may be worked around.
     *
     * @param keyCode       Key code of the key that triggered the down event.
     * @param event         Event triggered.
     * @return              'True' when the key event was processed by this method.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            if ((ACTION_CREATE == intent.action &&
                        intent.extras?.getBoolean(EXTRAS_LOCK_ENFORCED) != true) ||
                ACTION_REMOVE == intent.action
            ) {
                finish()
            } // else, do nothing, but report that the key was consumed to stay alive
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Saves the pass code input by the user as the current pass code.
     */
    private fun savePassCodeAndExit() {
        setResult(RESULT_OK, resultIntent)
        notifyDocumentsProviderRoots(applicationContext)
        if (biometricViewModel.isBiometricLockAvailable()) {
            showBiometricDialog(this)
        } else {
            PassCodeManager.onActivityStopped(this)
            finish()
        }
    }

    override fun onOptionSelected(optionSelected: BiometricStatus) {
        when (optionSelected) {
            BiometricStatus.ENABLED_BY_USER -> {
                passCodeViewModel.setBiometricsState(enabled = true)
            }

            BiometricStatus.DISABLED_BY_USER -> {
                passCodeViewModel.setBiometricsState(enabled = false)
            }
        }
        PassCodeManager.onActivityStopped(this)
        finish()
    }

    private fun getPasscodeAction(action: String?): PasscodeAction =
        when (action) {
            ACTION_REMOVE -> {
                PasscodeAction.REMOVE
            }

            ACTION_CREATE -> {
                PasscodeAction.CREATE
            }

            else -> {
                PasscodeAction.CHECK
            }
        }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
         when (keyCode) {

            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                val number = keyCode - KeyEvent.KEYCODE_0
                passCodeViewModel.onNumberClicked(number)
                binding.numberKeyboard.setFocusOnKey(number)
                true
            }

            KeyEvent.KEYCODE_DEL -> {
                passCodeViewModel.onBackspaceClicked()
                true
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                PassCodeManager.onActivityStopped(this)
                super.onBackPressed()
                true
            }

            else -> {
                super.onKeyUp(keyCode, event)
            }
        }

    companion object {
        const val ACTION_CREATE = "ACTION_REQUEST_WITH_RESULT"
        const val ACTION_REMOVE = "ACTION_CHECK_WITH_RESULT"
        const val ACTION_CHECK = "ACTION_CHECK"

        // NOTE: PREFERENCE_SET_PASSCODE must have the same value as settings_security.xml-->android:key for passcode preference
        const val PREFERENCE_SET_PASSCODE = "set_pincode"
        const val PREFERENCE_PASSCODE = "PrefPinCode"
        const val PREFERENCE_MIGRATION_REQUIRED = "PrefMigrationRequired"

        // NOTE: This is required to read the legacy pin code format
        const val PREFERENCE_PASSCODE_D = "PrefPinCode"

        const val EXTRAS_MIGRATION = "PASSCODE_MIGRATION"
        const val PASSCODE_MIN_LENGTH = 4

        private const val NUM_ATTEMPTS_WITHOUT_TIMER = 3

        const val BIOMETRIC_HAS_FAILED = "BIOMETRIC_HAS_FAILED"

    }
}
