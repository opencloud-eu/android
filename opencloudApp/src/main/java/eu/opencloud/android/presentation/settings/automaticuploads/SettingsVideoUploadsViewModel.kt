/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 * @author Aitor Ballesteros Pavón
 * @author Jorge Aguado Recio
 *
 * Copyright (C) 2024 ownCloud GmbH.
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

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.R
import eu.opencloud.android.db.PreferenceManager.PREF__CAMERA_UPLOADS_DEFAULT_PATH
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration.Companion.videoUploadsName
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.automaticuploads.model.UseSubfoldersBehaviour
import eu.opencloud.android.domain.automaticuploads.usecases.GetVideoUploadsConfigurationStreamUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetVideoUploadsUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SaveVideoUploadsConfigurationUseCase
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.spaces.model.OCSpace
import eu.opencloud.android.domain.spaces.usecases.GetPersonalSpaceForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpaceByIdForAccountUseCase
import eu.opencloud.android.providers.AccountProvider
import eu.opencloud.android.providers.ContextProvider
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import eu.opencloud.android.providers.WorkManagerProvider
import eu.opencloud.android.ui.activity.FolderPickerActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class SettingsVideoUploadsViewModel(
    private val accountProvider: AccountProvider,
    private val saveVideoUploadsConfigurationUseCase: SaveVideoUploadsConfigurationUseCase,
    private val getVideoUploadsConfigurationStreamUseCase: GetVideoUploadsConfigurationStreamUseCase,
    private val resetVideoUploadsUseCase: ResetVideoUploadsUseCase,
    private val getPersonalSpaceForAccountUseCase: GetPersonalSpaceForAccountUseCase,
    private val getSpaceByIdForAccountUseCase: GetSpaceByIdForAccountUseCase,
    private val workManagerProvider: WorkManagerProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val contextProvider: ContextProvider,
) : ViewModel() {

    private val _videoUploads: MutableStateFlow<FolderBackUpConfiguration?> = MutableStateFlow(null)
    val videoUploads: StateFlow<FolderBackUpConfiguration?> = _videoUploads

    private var videoUploadsSpace: OCSpace? = null

    init {
        initVideoUploads()
    }

    private fun initVideoUploads() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getVideoUploadsConfigurationStreamUseCase(Unit).collect { videoUploadsConfiguration ->
                videoUploadsConfiguration?.accountName?.let {
                    getSpaceById(spaceId = videoUploadsConfiguration.spaceId, accountName = it)
                }
                _videoUploads.update { videoUploadsConfiguration }
            }
        }
    }

    fun enableVideoUploads(accountName: String) {
        // Use selected account as default.
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getPersonalSpaceForAccount(accountName)
            saveVideoUploadsConfigurationUseCase(
                SaveVideoUploadsConfigurationUseCase.Params(
                    composeVideoUploadsConfiguration(
                        accountName = accountName,
                        spaceId = videoUploadsSpace?.id,
                    )
                )
            )
        }
    }

    fun disableVideoUploads() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            resetVideoUploadsUseCase(Unit)
        }
    }

    fun useWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveVideoUploadsConfigurationUseCase(
                SaveVideoUploadsConfigurationUseCase.Params(composeVideoUploadsConfiguration(wifiOnly = wifiOnly))
            )
        }
    }

    fun useChargingOnly(chargingOnly: Boolean) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveVideoUploadsConfigurationUseCase(
                SaveVideoUploadsConfigurationUseCase.Params(
                    composeVideoUploadsConfiguration(chargingOnly = chargingOnly)
                )
            )
        }
    }

    fun handleSelectUseSubfoldersBehaviour(behaviourString: String) {
        val behaviour = UseSubfoldersBehaviour.fromString(behaviourString)

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveVideoUploadsConfigurationUseCase(
                SaveVideoUploadsConfigurationUseCase.Params(composeVideoUploadsConfiguration(useSubfoldersBehaviour = behaviour))
            )
        }
    }

    fun getVideoUploadsAccount() = _videoUploads.value?.accountName

    fun getVideoUploadsPath() = _videoUploads.value?.uploadPath ?: PREF__CAMERA_UPLOADS_DEFAULT_PATH

    fun getVideoUploadsSourcePath(): String? = _videoUploads.value?.sourcePath

    fun handleSelectVideoUploadsPath(data: Intent?) {
        val folderToUpload = data?.getParcelableExtra<OCFile>(FolderPickerActivity.EXTRA_FOLDER)
        folderToUpload?.remotePath?.let {
            viewModelScope.launch(coroutinesDispatcherProvider.io) {
                getSpaceById(spaceId = folderToUpload.spaceId, accountName = folderToUpload.owner)
                saveVideoUploadsConfigurationUseCase(
                    SaveVideoUploadsConfigurationUseCase.Params(
                        composeVideoUploadsConfiguration(
                            uploadPath = it,
                            spaceId = videoUploadsSpace?.id,
                        )
                    )
                )
            }
        }
    }

    fun handleSelectAccount(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getPersonalSpaceForAccount(accountName)
            saveVideoUploadsConfigurationUseCase(
                SaveVideoUploadsConfigurationUseCase.Params(
                    composeVideoUploadsConfiguration(
                        accountName = accountName,
                        uploadPath = null,
                        spaceId = videoUploadsSpace?.id,
                    )
                )
            )
        }
    }

    fun handleSelectBehaviour(behaviorString: String) {
        val behavior = UploadBehavior.fromString(behaviorString)

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveVideoUploadsConfigurationUseCase(
                SaveVideoUploadsConfigurationUseCase.Params(composeVideoUploadsConfiguration(behavior = behavior))
            )
        }
    }

    fun handleSelectVideoUploadsSourcePath(contentUriForTree: Uri) {
        // If the source path has changed, update camera uploads last sync
        val previousSourcePath = _videoUploads.value?.sourcePath?.trimEnd(File.separatorChar)

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveVideoUploadsConfigurationUseCase(
                SaveVideoUploadsConfigurationUseCase.Params(
                    composeVideoUploadsConfiguration(
                        sourcePath = contentUriForTree.toString(),
                        timestamp = System.currentTimeMillis().takeIf { previousSourcePath != contentUriForTree.encodedPath }
                    )
                )
            )
        }
    }

    fun scheduleVideoUploads() {
        workManagerProvider.enqueueAutomaticUploadsWorker()
    }

    private fun composeVideoUploadsConfiguration(
        accountName: String? = _videoUploads.value?.accountName,
        uploadPath: String? = _videoUploads.value?.uploadPath,
        wifiOnly: Boolean? = _videoUploads.value?.wifiOnly,
        chargingOnly: Boolean? = _videoUploads.value?.chargingOnly,
        sourcePath: String? = _videoUploads.value?.sourcePath,
        behavior: UploadBehavior? = _videoUploads.value?.behavior,
        useSubfoldersBehaviour: UseSubfoldersBehaviour? = _videoUploads.value?.useSubfoldersBehaviour,
        timestamp: Long? = _videoUploads.value?.lastSyncTimestamp,
        spaceId: String? = _videoUploads.value?.spaceId,
    ): FolderBackUpConfiguration =
        FolderBackUpConfiguration(
            accountName = accountName ?: accountProvider.getCurrentOpenCloudAccount()!!.name,
            behavior = behavior ?: UploadBehavior.COPY,
            sourcePath = sourcePath.orEmpty(),
            uploadPath = uploadPath ?: PREF__CAMERA_UPLOADS_DEFAULT_PATH,
            wifiOnly = wifiOnly ?: false,
            chargingOnly = chargingOnly ?: false,
            lastSyncTimestamp = timestamp ?: System.currentTimeMillis(),
            useSubfoldersBehaviour = useSubfoldersBehaviour ?: UseSubfoldersBehaviour.NONE,
            name = _videoUploads.value?.name ?: videoUploadsName,
            spaceId = spaceId,
        ).also {
            Timber.d("Video uploads configuration updated. New configuration: $it")
        }

    private fun handleSpaceName(spaceName: String?): String? =
        if (videoUploadsSpace?.isPersonal == true) {
            contextProvider.getString(R.string.bottom_nav_personal)
        } else {
            spaceName
        }

    fun getUploadPathString(): String {

        val spaceName = handleSpaceName(videoUploadsSpace?.name)
        val uploadPath = videoUploads.value?.uploadPath
        val spaceId = videoUploads.value?.spaceId

        return if (uploadPath != null) {
            if (spaceId != null) {
                "$spaceName: $uploadPath"
            } else {
                uploadPath
            }
        } else {
            if (spaceId != null) {
                "$spaceName: $PREF__CAMERA_UPLOADS_DEFAULT_PATH"
            } else {
                PREF__CAMERA_UPLOADS_DEFAULT_PATH
            }
        }
    }

    private fun getPersonalSpaceForAccount(accountName: String) {
        val result = getPersonalSpaceForAccountUseCase(
            GetPersonalSpaceForAccountUseCase.Params(
                accountName = accountName
            )
        )
        videoUploadsSpace = result
    }

    private fun getSpaceById(spaceId: String?, accountName: String) {
        val result = getSpaceByIdForAccountUseCase(
            GetSpaceByIdForAccountUseCase.Params(
                accountName = accountName,
                spaceId = spaceId
            )
        )
        videoUploadsSpace = result
    }
}
