/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 * @author Aitor Ballesteros Pavón
 * @author Jorge Aguado Recio
 * @author Philipp Thaler
 *
 * Copyright (C) 2026 OpenCloud GmbH.
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

package eu.opencloud.android.presentation.settings.automaticuploads

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import eu.opencloud.android.R
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.automaticuploads.model.UseSubfoldersBehaviour
import eu.opencloud.android.extensions.collectLatestLifecycleFlow
import eu.opencloud.android.extensions.showAlertDialog
import eu.opencloud.android.extensions.showMessageInSnackbar
import eu.opencloud.android.ui.activity.FolderPickerActivity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class SettingsAutoUploadFragment : PreferenceFragmentCompat() {

    private val autoUploadViewModel by viewModel<SettingsAutoUploadViewModel> {
        parametersOf(requireArguments().getString(ARG_CONFIG_NAME))
    }

    private var prefEnableAutoUploads: SwitchPreferenceCompat? = null
    private var prefAutoUploadsPath: Preference? = null
    private var prefAutoUploadsOnWifi: CheckBoxPreference? = null
    private var prefAutoUploadsOnCharging: CheckBoxPreference? = null
    private var prefAutoUploadsSourcePath: Preference? = null
    private var prefAutoUploadsBehaviour: ListPreference? = null
    private var prefAutoUploadsUseSubfolderBehaviour: ListPreference? = null
    private var prefAutoUploadsAccount: ListPreference? = null
    private var prefAutoUploadsLastSync: Preference? = null
    private var spaceId: String? = null
    private lateinit var selectedAccount: String

    private val selectAutoUploadsPathLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            autoUploadViewModel.handleSelectAutoUploadsPath(result.data)
        }

    private val selectAutoUploadsSourcePathLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            // here we ask the content resolver to persist the permission for us
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val contentUriForTree = result.data!!.data!!

            requireContext().contentResolver.takePersistableUriPermission(contentUriForTree, takeFlags)
            autoUploadViewModel.handleSelectAutoUploadsSourcePath(contentUriForTree)
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_auto_upload, rootKey)

        prefEnableAutoUploads = findPreference(PREF_ENABLE_AUTO_UPLOADS)
        prefAutoUploadsPath = findPreference(PREF_AUTO_UPLOADS_PATH)
        prefAutoUploadsOnWifi = findPreference(PREF_AUTO_UPLOADS_WIFI_ONLY)
        prefAutoUploadsOnCharging = findPreference(PREF_AUTO_UPLOADS_CHARGING_ONLY)
        prefAutoUploadsSourcePath = findPreference(PREF_AUTO_UPLOADS_SOURCE)
        prefAutoUploadsLastSync = findPreference(PREF_AUTO_UPLOADS_LAST_SYNC)
        prefAutoUploadsBehaviour = findPreference<ListPreference>(PREF_AUTO_UPLOADS_BEHAVIOUR)?.apply {
            entries = listOf(
                getString(R.string.pref_behaviour_entries_keep_file),
                getString(R.string.pref_behaviour_entries_remove_original_file)
            ).toTypedArray()
            entryValues = listOf(UploadBehavior.COPY.name, UploadBehavior.MOVE.name).toTypedArray()
        }
        prefAutoUploadsUseSubfolderBehaviour = findPreference<ListPreference>(PREF_AUTO_UPLOADS_USE_SUBFOLDERS_BEHAVIOUR)?.apply {
            entries = listOf(
                getString(R.string.pref_use_subfolders_behaviour_none),
                getString(R.string.pref_use_subfolders_behaviour_year),
                getString(R.string.pref_use_subfolders_behaviour_year_month),
                getString(R.string.pref_use_subfolders_behaviour_year_month_day),
            ).toTypedArray()
            entryValues = listOf(
                UseSubfoldersBehaviour.NONE.name,
                UseSubfoldersBehaviour.YEAR.name,
                UseSubfoldersBehaviour.YEAR_MONTH.name,
                UseSubfoldersBehaviour.YEAR_MONTH_DAY.name,
            ).toTypedArray()
        }
        prefAutoUploadsAccount = findPreference(PREF_AUTO_UPLOADS_ACCOUNT_NAME)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeUiState()
        setupEventListeners()
    }

    private fun observeUiState() {
        collectLatestLifecycleFlow(autoUploadViewModel.uiState) { uiState ->
            prefEnableAutoUploads?.apply {
                isChecked = uiState.isEnabled
                if (uiState.mainTitle != 0) {
                    setTitle(uiState.mainTitle)
                }
                if (uiState.mainSummary != 0) {
                    setSummary(uiState.mainSummary)
                }
            }
            prefAutoUploadsPath?.apply {
                summary = uiState.uploadPathSummary
                if (uiState.uploadPathTitle != 0) {
                    setTitle(uiState.uploadPathTitle)
                }
            }
            prefAutoUploadsOnWifi?.apply {
                isChecked = uiState.wifiOnly
                if (uiState.wifiOnlyTitle != 0) {
                    setTitle(uiState.wifiOnlyTitle)
                }
            }
            prefAutoUploadsOnCharging?.apply {
                isChecked = uiState.chargingOnly
                if (uiState.chargingOnlyTitle != 0) {
                    setTitle(uiState.chargingOnlyTitle)
                }
            }
            prefAutoUploadsBehaviour?.value = uiState.behavior.name
            prefAutoUploadsUseSubfolderBehaviour?.value = uiState.useSubfoldersBehaviour.name
            prefAutoUploadsAccount?.apply {
                value = uiState.accountName
                entries = uiState.availableAccounts.toTypedArray()
                entryValues = uiState.availableAccounts.toTypedArray()
                if (uiState.accountTitle != 0) {
                    setTitle(uiState.accountTitle)
                    setDialogTitle(uiState.accountTitle)
                }
            }
            spaceId = uiState.spaceId

            prefAutoUploadsSourcePath?.apply {
                summary = uiState.sourcePathSummary
                if (uiState.sourcePathTitle != 0) {
                    setTitle(uiState.sourcePathTitle)
                }
            }
            prefAutoUploadsLastSync?.summary = uiState.lastSyncSummary

            val isEnabled = uiState.isEnabled
            prefAutoUploadsPath?.isEnabled = isEnabled
            prefAutoUploadsOnWifi?.isEnabled = isEnabled
            prefAutoUploadsOnCharging?.isEnabled = isEnabled
            prefAutoUploadsSourcePath?.isEnabled = isEnabled
            prefAutoUploadsBehaviour?.isEnabled = isEnabled
            prefAutoUploadsUseSubfolderBehaviour?.isEnabled = isEnabled
            prefAutoUploadsAccount?.isEnabled = isEnabled
            prefAutoUploadsLastSync?.isEnabled = isEnabled
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                autoUploadViewModel.autoUploads.collect {
                    it?.let {
                        autoUploadViewModel.scheduleAutoUploads()
                    }
                }
            }
        }
    }

    private fun setupEventListeners() {
        prefEnableAutoUploads?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            if (enabled) {
                showAccountSelectionDialog()
            } else {
                autoUploadViewModel.disableAutoUploads()
            }
            true
        }
        prefAutoUploadsPath?.setOnPreferenceClickListener {
            val accountName = autoUploadViewModel.getAutoUploadsAccount()
            if (accountName != null) {
                val intent = Intent(requireContext(), FolderPickerActivity::class.java).apply {
                    putExtra(FolderPickerActivity.KEY_ACCOUNT_NAME, accountName)
                    putExtra(FolderPickerActivity.KEY_SPACE_ID, spaceId)
                    putExtra(FolderPickerActivity.EXTRA_PICKER_MODE, FolderPickerActivity.PickerMode.CAMERA_FOLDER)
                }
                selectAutoUploadsPathLauncher.launch(intent)
            } else {
                showMessageInSnackbar(getString(R.string.prefs_camera_upload_no_account_selected))
            }
            true
        }

        prefAutoUploadsOnWifi?.setOnPreferenceChangeListener { _, newValue ->
            autoUploadViewModel.useWifiOnly(newValue as Boolean)
            true
        }

        prefAutoUploadsOnCharging?.setOnPreferenceChangeListener { _, newValue ->
            autoUploadViewModel.useChargingOnly(newValue as Boolean)
            true
        }

        prefAutoUploadsSourcePath?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            selectAutoUploadsSourcePathLauncher.launch(intent)
            true
        }

        prefAutoUploadsBehaviour?.setOnPreferenceChangeListener { _, newValue ->
            autoUploadViewModel.handleSelectBehaviour(newValue as String)
            true
        }

        prefAutoUploadsUseSubfolderBehaviour?.setOnPreferenceChangeListener { _, newValue ->
            autoUploadViewModel.handleSelectUseSubfoldersBehaviour(newValue as String)
            true
        }

        prefAutoUploadsAccount?.setOnPreferenceChangeListener { _, newValue ->
            autoUploadViewModel.handleSelectAccount(newValue as String)
            true
        }
    }

    private fun showAccountSelectionDialog() {
        val accountNames = autoUploadViewModel.uiState.value.availableAccounts
        if (accountNames.isEmpty()) {
            showMessageInSnackbar(getString(R.string.prefs_camera_upload_no_account_selected))
            prefEnableAutoUploads?.isChecked = false
            return
        }

        selectedAccount = accountNames[0]

        showAlertDialog(
            title = autoUploadViewModel.uiState.value.accountTitle.let { if (it != 0) getString(it) else "" },
            message = null,
            positiveButtonText = getString(R.string.common_ok),
            negativeButtonText = getString(R.string.common_cancel),
            positiveButtonAction = {
                autoUploadViewModel.enableAutoUploads(selectedAccount)
            },
            negativeButtonAction = {
                prefEnableAutoUploads?.isChecked = false
            },
            cancelable = false,
            singleChoiceItems = accountNames.toTypedArray(),
            checkedItem = 0,
            onSingleChoiceItemSelected = { _: DialogInterface, which: Int ->
                selectedAccount = accountNames[which]
            }
        )
    }

    companion object {
        const val ARG_CONFIG_NAME = "config_name"

        const val PREF_ENABLE_AUTO_UPLOADS = "auto_upload_enabled"
        const val PREF_AUTO_UPLOADS_ACCOUNT_NAME = "auto_upload_account_name"
        const val PREF_AUTO_UPLOADS_PATH = "auto_upload_path"
        const val PREF_AUTO_UPLOADS_SOURCE = "auto_upload_source_path"
        const val PREF_AUTO_UPLOADS_BEHAVIOUR = "auto_upload_behaviour"
        const val PREF_AUTO_UPLOADS_USE_SUBFOLDERS_BEHAVIOUR = "auto_upload_use_subfolders_behaviour"
        const val PREF_AUTO_UPLOADS_WIFI_ONLY = "auto_upload_on_wifi"
        const val PREF_AUTO_UPLOADS_CHARGING_ONLY = "auto_upload_on_charging"
        const val PREF_AUTO_UPLOADS_LAST_SYNC = "auto_upload_last_sync"

        fun newInstance(configName: String): SettingsAutoUploadFragment =
            SettingsAutoUploadFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CONFIG_NAME, configName)
                }
            }
    }
}
