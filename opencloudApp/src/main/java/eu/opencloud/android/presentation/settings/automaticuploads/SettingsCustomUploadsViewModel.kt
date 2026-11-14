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

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.domain.automaticuploads.model.AutomaticUploadsMediaType
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.automaticuploads.usecases.GetCustomUploadsConfigurationsStreamUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SaveFolderBackupConfigurationUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalSpaceForAccountUseCase
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import eu.opencloud.android.providers.WorkManagerProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages the list of additional, user-defined automatic upload folders.
 */
class SettingsCustomUploadsViewModel(
    private val getCustomUploadsConfigurationsStreamUseCase: GetCustomUploadsConfigurationsStreamUseCase,
    private val saveFolderBackupConfigurationUseCase: SaveFolderBackupConfigurationUseCase,
    private val getPersonalSpaceForAccountUseCase: GetPersonalSpaceForAccountUseCase,
    private val workManagerProvider: WorkManagerProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
) : ViewModel() {

    private val _customUploads: MutableStateFlow<List<FolderBackUpConfiguration>> = MutableStateFlow(emptyList())
    val customUploads: StateFlow<List<FolderBackUpConfiguration>> = _customUploads

    init {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getCustomUploadsConfigurationsStreamUseCase(Unit).collect { configurations ->
                _customUploads.update { configurations }
            }
        }
    }

    /**
     * Creates a new folder upload for [sourceUri] and returns its (unique) name, so the caller can open its detail
     * screen right away. With [includeExistingFiles] everything already in the folder is uploaded (an immediate scan
     * is started); otherwise only files added from now on, same as camera uploads.
     */
    fun addCustomUpload(sourceUri: Uri, folderName: String?, accountName: String, includeExistingFiles: Boolean): String {
        val name = FolderBackUpConfiguration.newCustomUploadsName()
        val uploadPath = "/" + (folderName?.takeIf { it.isNotBlank() }?.replace("/", "_") ?: DEFAULT_UPLOAD_FOLDER)
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val personalSpace = getPersonalSpaceForAccountUseCase(
                GetPersonalSpaceForAccountUseCase.Params(accountName = accountName)
            )
            val configuration = FolderBackUpConfiguration(
                accountName = accountName,
                behavior = UploadBehavior.COPY,
                sourcePath = sourceUri.toString(),
                uploadPath = uploadPath,
                wifiOnly = false,
                chargingOnly = false,
                lastSyncTimestamp = if (includeExistingFiles) 0L else System.currentTimeMillis(),
                name = name,
                spaceId = personalSpace?.id,
                mediaType = AutomaticUploadsMediaType.IMAGES_AND_VIDEOS,
            )
            Timber.d("Adding folder upload: $configuration")
            saveFolderBackupConfigurationUseCase(SaveFolderBackupConfigurationUseCase.Params(configuration))
            // Saved first, so the scan below sees the new folder.
            if (includeExistingFiles) syncNow()
        }
        return name
    }

    fun scheduleAutomaticUploads() {
        workManagerProvider.enqueueAutomaticUploadsWorker()
    }

    fun syncNow() {
        workManagerProvider.enqueueAutomaticUploadsWorker()
        workManagerProvider.enqueueSyncNowAutomaticUploadsWorker()
    }

    companion object {
        private const val DEFAULT_UPLOAD_FOLDER = "FolderUploads"
    }
}
