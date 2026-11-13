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
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import eu.opencloud.android.R
import eu.opencloud.android.domain.automaticuploads.model.AutomaticUploadsMediaType
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.extensions.collectLatestLifecycleFlow
import eu.opencloud.android.extensions.showAlertDialog
import eu.opencloud.android.extensions.showMessageInSnackbar
import eu.opencloud.android.presentation.accounts.ManageAccountsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

/**
 * Lists the additional folders that are uploaded automatically (besides camera picture and video uploads)
 * and lets the user add new ones.
 */
class SettingsCustomUploadsFragment : PreferenceFragmentCompat() {

    private val customUploadsViewModel by viewModel<SettingsCustomUploadsViewModel>()
    private val manageAccountsViewModel by viewModel<ManageAccountsViewModel>()

    private var prefAddFolder: Preference? = null
    private var prefFoldersCategory: PreferenceCategory? = null
    private var prefEmpty: Preference? = null

    private var availableAccountNames: List<String> = emptyList()

    private val selectSourceFolderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val contentUriForTree = result.data?.data ?: return@registerForActivityResult
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(contentUriForTree, takeFlags)

            val folderName = try {
                DocumentFile.fromTreeUri(requireContext(), contentUriForTree)?.name
            } catch (e: Exception) {
                Timber.w(e, "Could not get folder name for $contentUriForTree")
                null
            }
            val accountName = selectAccountForNewFolder() ?: return@registerForActivityResult
            var addedName: String? = null
            fun add(includeExistingFiles: Boolean) {
                if (addedName != null) return
                addedName = customUploadsViewModel.addCustomUpload(contentUriForTree, folderName, accountName, includeExistingFiles)
                if (includeExistingFiles) showMessageInSnackbar(getString(R.string.prefs_custom_uploads_sync_now_started))
            }
            showAlertDialog(
                title = getString(R.string.custom_upload_existing_files_title),
                message = getString(R.string.custom_upload_existing_files_message),
                positiveButtonText = getString(R.string.custom_upload_existing_files_all),
                positiveButtonListener = { _, _ -> add(includeExistingFiles = true) },
                negativeButtonText = getString(R.string.custom_upload_existing_files_only_new),
                negativeButtonListener = { _, _ -> add(includeExistingFiles = false) },
                // Back / tap outside: keep the safe default of only new files.
                onDismissListener = {
                    add(includeExistingFiles = false)
                    addedName?.let { openCustomUpload(it) }
                }
            )
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_custom_uploads, rootKey)

        prefAddFolder = findPreference(PREF_ADD_FOLDER)
        prefFoldersCategory = findPreference(PREF_FOLDERS_CATEGORY)
        prefEmpty = findPreference(PREF_EMPTY)

        findPreference<Preference>(PREF_SYNC_NOW)?.setOnPreferenceClickListener {
            customUploadsViewModel.syncNow()
            showMessageInSnackbar(getString(R.string.prefs_custom_uploads_sync_now_started))
            true
        }

        prefAddFolder?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                )
            }
            selectSourceFolderLauncher.launch(intent)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        collectLatestLifecycleFlow(manageAccountsViewModel.userQuotas) { listUserQuotas ->
            availableAccountNames = listUserQuotas.filter { it.available != -4L }.map { it.accountName }
            prefAddFolder?.isEnabled = availableAccountNames.isNotEmpty()
            if (listUserQuotas.isNotEmpty() && availableAccountNames.isEmpty()) {
                showMessageInSnackbar(getString(R.string.prefs_automatic_uploads_not_available_users_light))
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                customUploadsViewModel.customUploads.collect { configurations ->
                    renderFolders(configurations)
                }
            }
        }
    }

    private fun renderFolders(configurations: List<FolderBackUpConfiguration>) {
        val category = prefFoldersCategory ?: return
        // Remove every dynamic entry, keep the "empty" placeholder.
        for (i in category.preferenceCount - 1 downTo 0) {
            val preference = category.getPreference(i)
            if (preference.key != PREF_EMPTY) category.removePreference(preference)
        }
        prefEmpty?.isVisible = configurations.isEmpty()

        configurations.forEachIndexed { index, configuration ->
            val preference = Preference(requireContext()).apply {
                key = configuration.name
                order = index + 1
                isIconSpaceReserved = false
                title = getSourceFolderDisplayName(configuration.sourcePath)
                summary = getString(
                    R.string.prefs_custom_uploads_entry_summary,
                    getString(mediaTypeLabel(configuration.mediaType)),
                    configuration.uploadPath
                )
                setOnPreferenceClickListener {
                    openCustomUpload(configuration.name)
                    true
                }
            }
            category.addPreference(preference)
        }
    }

    private fun selectAccountForNewFolder(): String? {
        val currentAccount = manageAccountsViewModel.getCurrentAccount()?.name
        return if (currentAccount != null && currentAccount in availableAccountNames) {
            currentAccount
        } else {
            availableAccountNames.firstOrNull()
        }
    }

    private fun openCustomUpload(name: String) {
        if (!isAdded) return
        parentFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsCustomUploadFragment.newInstance(name))
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroy() {
        customUploadsViewModel.scheduleAutomaticUploads()
        super.onDestroy()
    }

    companion object {
        private const val PREF_ADD_FOLDER = "custom_uploads_add"
        private const val PREF_SYNC_NOW = "custom_uploads_sync_now"
        private const val PREF_FOLDERS_CATEGORY = "custom_uploads_list"
        private const val PREF_EMPTY = "custom_uploads_empty"

        /**
         * Turns a SAF tree URI like content://com.android.externalstorage.documents/tree/primary%3APictures%2FScreenshots
         * into something readable like "Pictures/Screenshots".
         */
        fun getSourceFolderDisplayName(sourcePath: String): String {
            val treePath = android.net.Uri.parse(sourcePath).path ?: return sourcePath
            val documentPath = treePath.substringAfter("/tree/", treePath).trimEnd('/')
            val relative = documentPath.substringAfter(':', documentPath)
            return relative.ifBlank { documentPath }
        }

        fun mediaTypeLabel(mediaType: AutomaticUploadsMediaType): Int =
            when (mediaType) {
                AutomaticUploadsMediaType.IMAGES -> R.string.prefs_custom_upload_media_type_images
                AutomaticUploadsMediaType.VIDEOS -> R.string.prefs_custom_upload_media_type_videos
                AutomaticUploadsMediaType.IMAGES_AND_VIDEOS -> R.string.prefs_custom_upload_media_type_images_and_videos
                AutomaticUploadsMediaType.ALL_FILES -> R.string.prefs_custom_upload_media_type_all
            }
    }
}
