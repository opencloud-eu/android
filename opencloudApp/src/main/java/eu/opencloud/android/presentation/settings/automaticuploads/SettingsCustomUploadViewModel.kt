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

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.R
import eu.opencloud.android.domain.automaticuploads.model.AutomaticUploadsMediaType
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.automaticuploads.usecases.GetFolderBackupConfigurationStreamUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetFolderBackupConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SaveFolderBackupConfigurationUseCase
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.spaces.model.OCSpace
import eu.opencloud.android.domain.spaces.usecases.GetPersonalSpaceForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpaceByIdForAccountUseCase
import eu.opencloud.android.providers.ContextProvider
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import eu.opencloud.android.providers.WorkManagerProvider
import eu.opencloud.android.ui.activity.FolderPickerActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Edits a single additional folder upload, identified by its unique [configurationName].
 */
class SettingsCustomUploadViewModel(
    private val configurationName: String,
    private val getFolderBackupConfigurationStreamUseCase: GetFolderBackupConfigurationStreamUseCase,
    private val saveFolderBackupConfigurationUseCase: SaveFolderBackupConfigurationUseCase,
    private val resetFolderBackupConfigurationUseCase: ResetFolderBackupConfigurationUseCase,
    private val getPersonalSpaceForAccountUseCase: GetPersonalSpaceForAccountUseCase,
    private val getSpaceByIdForAccountUseCase: GetSpaceByIdForAccountUseCase,
    private val workManagerProvider: WorkManagerProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val contextProvider: ContextProvider,
) : ViewModel() {

    private val _customUpload: MutableStateFlow<FolderBackUpConfiguration?> = MutableStateFlow(null)
    val customUpload: StateFlow<FolderBackUpConfiguration?> = _customUpload

    /** True once the configuration has been loaded from the database at least once. */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    private var customUploadSpace: OCSpace? = null

    init {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getFolderBackupConfigurationStreamUseCase(
                GetFolderBackupConfigurationStreamUseCase.Params(configurationName)
            ).collect { configuration ->
                configuration?.let { getSpaceById(spaceId = it.spaceId, accountName = it.accountName) }
                _customUpload.update { configuration }
                _loaded.update { true }
            }
        }
    }

    fun getAccountName() = _customUpload.value?.accountName

    fun getUploadPath() = _customUpload.value?.uploadPath ?: "/"

    fun getSourcePath(): String? = _customUpload.value?.sourcePath

    fun useWifiOnly(wifiOnly: Boolean) = save { it.copy(wifiOnly = wifiOnly) }

    fun useChargingOnly(chargingOnly: Boolean) = save { it.copy(chargingOnly = chargingOnly) }

    fun handleSelectBehaviour(behaviorString: String) = save { it.copy(behavior = UploadBehavior.fromString(behaviorString)) }

    fun handleSelectMediaType(mediaTypeString: String) =
        save { it.copy(mediaType = AutomaticUploadsMediaType.fromString(mediaTypeString)) }

    fun handleSelectUploadPath(data: Intent?) {
        val folderToUpload = data?.getParcelableExtra<OCFile>(FolderPickerActivity.EXTRA_FOLDER) ?: return
        val remotePath = folderToUpload.remotePath
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getSpaceById(spaceId = folderToUpload.spaceId, accountName = folderToUpload.owner)
            saveNow { it.copy(uploadPath = remotePath, spaceId = customUploadSpace?.id) }
        }
    }

    fun handleSelectAccount(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            customUploadSpace = getPersonalSpaceForAccountUseCase(
                GetPersonalSpaceForAccountUseCase.Params(accountName = accountName)
            )
            saveNow { it.copy(accountName = accountName, spaceId = customUploadSpace?.id) }
        }
    }

    fun handleSelectSourcePath(contentUriForTree: Uri) {
        val newSourcePath = contentUriForTree.toString()
        save { current ->
            if (current.sourcePath == newSourcePath) {
                current
            } else {
                // New folder: only upload what gets added from now on, same as camera uploads do.
                current.copy(sourcePath = newSourcePath, lastSyncTimestamp = System.currentTimeMillis())
            }
        }
    }

    /** Re-scan the whole folder once: every file in it is queued (already uploaded/queued ones are skipped). */
    fun uploadExistingFiles() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveNow { it.copy(lastSyncTimestamp = 0L) }
            syncNow()
        }
    }

    fun removeCustomUpload() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            resetFolderBackupConfigurationUseCase(ResetFolderBackupConfigurationUseCase.Params(configurationName))
        }
    }

    fun scheduleAutomaticUploads() {
        workManagerProvider.enqueueAutomaticUploadsWorker()
    }

    fun syncNow() {
        workManagerProvider.enqueueAutomaticUploadsWorker()
        workManagerProvider.enqueueSyncNowAutomaticUploadsWorker()
    }

    fun getUploadPathString(): String {
        val configuration = _customUpload.value ?: return ""
        val spaceName = if (customUploadSpace?.isPersonal == true) {
            contextProvider.getString(R.string.bottom_nav_personal)
        } else {
            customUploadSpace?.name
        }
        return if (configuration.spaceId != null && spaceName != null) {
            "$spaceName: ${configuration.uploadPath}"
        } else {
            configuration.uploadPath
        }
    }

    private fun save(transform: (FolderBackUpConfiguration) -> FolderBackUpConfiguration) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) { saveNow(transform) }
    }

    private fun saveNow(transform: (FolderBackUpConfiguration) -> FolderBackUpConfiguration) {
        val current = _customUpload.value ?: return
        val updated = transform(current)
        if (updated == current) return
        Timber.d("Folder upload configuration updated. New configuration: $updated")
        saveFolderBackupConfigurationUseCase(SaveFolderBackupConfigurationUseCase.Params(updated))
    }

    private fun getSpaceById(spaceId: String?, accountName: String) {
        customUploadSpace = getSpaceByIdForAccountUseCase(
            GetSpaceByIdForAccountUseCase.Params(
                accountName = accountName,
                spaceId = spaceId
            )
        )
    }
}
