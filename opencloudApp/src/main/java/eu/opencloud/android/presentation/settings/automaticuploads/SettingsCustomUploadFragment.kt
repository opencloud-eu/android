/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 openCloud GmbH.
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

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import eu.opencloud.android.R
import eu.opencloud.android.domain.automaticuploads.model.AutomaticUploadsMediaType
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.extensions.collectLatestLifecycleFlow
import eu.opencloud.android.extensions.showAlertDialog
import eu.opencloud.android.extensions.showMessageInSnackbar
import eu.opencloud.android.presentation.accounts.ManageAccountsViewModel
import eu.opencloud.android.ui.activity.FolderPickerActivity
import eu.opencloud.android.utils.DisplayUtils
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import java.io.File

/**
 * Detail screen of one additional folder upload.
 */
class SettingsCustomUploadFragment : PreferenceFragmentCompat() {

    private val configurationName: String by lazy { requireArguments().getString(ARG_CONFIGURATION_NAME)!! }

    private val customUploadViewModel by viewModel<SettingsCustomUploadViewModel> { parametersOf(configurationName) }
    private val manageAccountsViewModel by viewModel<ManageAccountsViewModel>()

    private var prefSourcePath: Preference? = null
    private var prefMediaType: ListPreference? = null
    private var prefAccount: ListPreference? = null
    private var prefUploadPath: Preference? = null
    private var prefBehaviour: ListPreference? = null
    private var prefLastSync: Preference? = null
    private var prefOnWifi: CheckBoxPreference? = null
    private var prefOnCharging: CheckBoxPreference? = null
    private var prefRemove: Preference? = null
    private var spaceId: String? = null

    private val selectUploadPathLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            customUploadViewModel.handleSelectUploadPath(result.data)
        }

    private val selectSourcePathLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val contentUriForTree = result.data?.data ?: return@registerForActivityResult
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(contentUriForTree, takeFlags)
            customUploadViewModel.handleSelectSourcePath(contentUriForTree)
        }

    private val mediaLocationPermissionRequester = MediaLocationPermissionRequester(this)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_custom_upload, rootKey)

        prefSourcePath = findPreference(PREF_SOURCE_PATH)
        prefMediaType = findPreference<ListPreference>(PREF_MEDIA_TYPE)?.apply {
            val mediaTypes = AutomaticUploadsMediaType.values()
            entries = mediaTypes.map { getString(SettingsCustomUploadsFragment.mediaTypeLabel(it)) }.toTypedArray()
            entryValues = mediaTypes.map { it.name }.toTypedArray()
        }
        prefAccount = findPreference(PREF_ACCOUNT)
        prefUploadPath = findPreference(PREF_UPLOAD_PATH)
        prefBehaviour = findPreference<ListPreference>(PREF_BEHAVIOUR)?.apply {
            entries = listOf(
                getString(R.string.pref_behaviour_entries_keep_file),
                getString(R.string.pref_behaviour_entries_remove_original_file)
            ).toTypedArray()
            entryValues = listOf(UploadBehavior.COPY.name, UploadBehavior.MOVE.name).toTypedArray()
        }
        prefLastSync = findPreference(PREF_LAST_SYNC)
        prefOnWifi = findPreference(PREF_ON_WIFI)
        prefOnCharging = findPreference(PREF_ON_CHARGING)
        prefRemove = findPreference(PREF_REMOVE)

        initPreferenceListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        collectLatestLifecycleFlow(manageAccountsViewModel.userQuotas) { listUserQuotas ->
            val availableAccounts = listUserQuotas.filter { it.available != -4L }
            prefAccount?.apply {
                entries = availableAccounts.map { it.accountName }.toTypedArray()
                entryValues = availableAccounts.map { it.accountName }.toTypedArray()
                // With a single account there is nothing to choose: show it as information instead of a picker.
                val onlyOneAccount = availableAccounts.size <= 1
                isEnabled = !onlyOneAccount
                summaryProvider = if (onlyOneAccount) null else ListPreference.SimpleSummaryProvider.getInstance()
                if (onlyOneAccount) {
                    summary = availableAccounts.firstOrNull()?.accountName?.let {
                        getString(R.string.prefs_custom_upload_account_only_one, it)
                    } ?: value
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                customUploadViewModel.customUpload.collect { configuration ->
                    if (configuration == null) {
                        // Removed (or not found): go back to the list.
                        if (customUploadViewModel.loaded.value && isAdded) parentFragmentManager.popBackStack()
                        return@collect
                    }
                    mediaLocationPermissionRequester.requestIfNeverAsked()
                    prefSourcePath?.summary = SettingsCustomUploadsFragment.getSourceFolderDisplayName(configuration.sourcePath)
                    prefMediaType?.value = configuration.mediaType.name
                    prefAccount?.value = configuration.accountName
                    prefUploadPath?.summary = customUploadViewModel.getUploadPathString()
                    prefBehaviour?.value = configuration.behavior.name
                    prefLastSync?.summary = DisplayUtils.unixTimeToHumanReadable(configuration.lastSyncTimestamp)
                    prefOnWifi?.isChecked = configuration.wifiOnly
                    prefOnCharging?.isChecked = configuration.chargingOnly
                    spaceId = configuration.spaceId
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                customUploadViewModel.loaded.collect { loaded ->
                    if (loaded && customUploadViewModel.customUpload.value == null && isAdded) {
                        parentFragmentManager.popBackStack()
                    }
                }
            }
        }
    }

    private fun initPreferenceListeners() {
        prefUploadPath?.setOnPreferenceClickListener {
            var uploadPath = customUploadViewModel.getUploadPath()
            if (!uploadPath.endsWith(File.separator)) {
                uploadPath += File.separator
            }
            val intent = Intent(activity, FolderPickerActivity::class.java).apply {
                putExtra(FolderPickerActivity.EXTRA_PICKER_MODE, FolderPickerActivity.PickerMode.CAMERA_FOLDER)
                putExtra(FolderPickerActivity.KEY_SPACE_ID, spaceId)
                putExtra(FolderPickerActivity.KEY_ACCOUNT_NAME, customUploadViewModel.getAccountName())
            }
            selectUploadPathLauncher.launch(intent)
            true
        }

        prefSourcePath?.setOnPreferenceClickListener {
            val sourcePath = customUploadViewModel.getSourcePath()
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && sourcePath != null) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, sourcePath.toUri())
                }
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
            }
            selectSourcePathLauncher.launch(intent)
            true
        }

        prefMediaType?.setOnPreferenceChangeListener { _, newValue ->
            customUploadViewModel.handleSelectMediaType(newValue as String)
            true
        }

        prefAccount?.setOnPreferenceChangeListener { _, newValue ->
            customUploadViewModel.handleSelectAccount(newValue as String)
            true
        }

        prefBehaviour?.setOnPreferenceChangeListener { _, newValue ->
            customUploadViewModel.handleSelectBehaviour(newValue as String)
            true
        }

        prefOnWifi?.setOnPreferenceChangeListener { _, newValue ->
            customUploadViewModel.useWifiOnly(newValue as Boolean)
            true
        }

        prefOnCharging?.setOnPreferenceChangeListener { _, newValue ->
            customUploadViewModel.useChargingOnly(newValue as Boolean)
            true
        }

        findPreference<Preference>(PREF_SYNC_NOW)?.setOnPreferenceClickListener {
            customUploadViewModel.syncNow()
            showMessageInSnackbar(getString(R.string.prefs_custom_uploads_sync_now_started))
            true
        }

        findPreference<Preference>(PREF_UPLOAD_EXISTING)?.setOnPreferenceClickListener {
            showAlertDialog(
                title = getString(R.string.prefs_custom_upload_existing),
                message = getString(R.string.confirmation_custom_upload_existing_message),
                positiveButtonText = getString(R.string.custom_upload_existing_files_all),
                positiveButtonListener = { _: DialogInterface?, _: Int ->
                    customUploadViewModel.uploadExistingFiles()
                    showMessageInSnackbar(getString(R.string.prefs_custom_uploads_sync_now_started))
                },
                negativeButtonText = getString(android.R.string.cancel)
            )
            true
        }

        prefRemove?.setOnPreferenceClickListener {
            showAlertDialog(
                title = getString(R.string.confirmation_disable_camera_uploads_title),
                message = getString(R.string.confirmation_remove_custom_upload_message),
                positiveButtonText = getString(R.string.common_remove),
                positiveButtonListener = { _: DialogInterface?, _: Int ->
                    customUploadViewModel.removeCustomUpload()
                },
                negativeButtonText = getString(android.R.string.cancel)
            )
            true
        }
    }

    override fun onDestroy() {
        customUploadViewModel.scheduleAutomaticUploads()
        super.onDestroy()
    }

    companion object {
        private const val ARG_CONFIGURATION_NAME = "configuration_name"

        private const val PREF_SOURCE_PATH = "custom_upload_source_path"
        private const val PREF_MEDIA_TYPE = "custom_upload_media_type"
        private const val PREF_ACCOUNT = "custom_upload_account_name"
        private const val PREF_UPLOAD_PATH = "custom_upload_path"
        private const val PREF_BEHAVIOUR = "custom_upload_behaviour"
        private const val PREF_LAST_SYNC = "custom_upload_last_sync"
        private const val PREF_ON_WIFI = "custom_upload_on_wifi"
        private const val PREF_ON_CHARGING = "custom_upload_on_charging"
        private const val PREF_REMOVE = "custom_upload_remove"
        private const val PREF_SYNC_NOW = "custom_upload_sync_now"
        private const val PREF_UPLOAD_EXISTING = "custom_upload_existing"

        fun newInstance(configurationName: String) = SettingsCustomUploadFragment().apply {
            arguments = Bundle().apply { putString(ARG_CONFIGURATION_NAME, configurationName) }
        }
    }
}
