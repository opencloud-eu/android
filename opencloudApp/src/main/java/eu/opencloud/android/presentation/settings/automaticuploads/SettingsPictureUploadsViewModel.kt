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
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration.Companion.pictureUploadsName
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.automaticuploads.model.UseSubfoldersBehaviour
import eu.opencloud.android.domain.automaticuploads.usecases.GetPictureUploadsConfigurationStreamUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetPictureUploadsUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SavePictureUploadsConfigurationUseCase
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

class SettingsPictureUploadsViewModel(
    private val accountProvider: AccountProvider,
    private val savePictureUploadsConfigurationUseCase: SavePictureUploadsConfigurationUseCase,
    private val getPictureUploadsConfigurationStreamUseCase: GetPictureUploadsConfigurationStreamUseCase,
    private val resetPictureUploadsUseCase: ResetPictureUploadsUseCase,
    private val getPersonalSpaceForAccountUseCase: GetPersonalSpaceForAccountUseCase,
    private val getSpaceByIdForAccountUseCase: GetSpaceByIdForAccountUseCase,
    private val workManagerProvider: WorkManagerProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val contextProvider: ContextProvider,
) : ViewModel() {

    private val _pictureUploads: MutableStateFlow<FolderBackUpConfiguration?> = MutableStateFlow(null)
    val pictureUploads: StateFlow<FolderBackUpConfiguration?> = _pictureUploads

    private var pictureUploadsSpace: OCSpace? = null

    init {
        initPictureUploads()
    }

    private fun initPictureUploads() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getPictureUploadsConfigurationStreamUseCase(Unit).collect { pictureUploadsConfiguration ->
                pictureUploadsConfiguration?.accountName?.let {
                    getSpaceById(spaceId = pictureUploadsConfiguration.spaceId, accountName = it)
                }
                _pictureUploads.update { pictureUploadsConfiguration }
            }
        }
    }

    fun enablePictureUploads(accountName: String) {
        // Use selected account as default.
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getPersonalSpaceForAccount(accountName)
            savePictureUploadsConfigurationUseCase(
                SavePictureUploadsConfigurationUseCase.Params(
                    composePictureUploadsConfiguration(
                        accountName = accountName,
                        spaceId = pictureUploadsSpace?.id,
                    )
                )
            )
        }
    }

    fun disablePictureUploads() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            resetPictureUploadsUseCase(Unit)
        }
    }

    fun useWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            savePictureUploadsConfigurationUseCase(
                SavePictureUploadsConfigurationUseCase.Params(composePictureUploadsConfiguration(wifiOnly = wifiOnly))
            )
        }
    }

    fun useChargingOnly(chargingOnly: Boolean) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            savePictureUploadsConfigurationUseCase(
                SavePictureUploadsConfigurationUseCase.Params(
                    composePictureUploadsConfiguration(chargingOnly = chargingOnly)
                )
            )
        }
    }

    fun handleSelectUseSubfoldersBehaviour(behaviourString: String) {
        val behaviour = UseSubfoldersBehaviour.fromString(behaviourString)

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            savePictureUploadsConfigurationUseCase(
                SavePictureUploadsConfigurationUseCase.Params(composePictureUploadsConfiguration(useSubfoldersBehaviour = behaviour))
            )
        }
    }

    fun getPictureUploadsAccount() = _pictureUploads.value?.accountName

    fun getPictureUploadsPath() = _pictureUploads.value?.uploadPath ?: PREF__CAMERA_UPLOADS_DEFAULT_PATH

    fun getPictureUploadsSourcePath(): String? = _pictureUploads.value?.sourcePath

    fun handleSelectPictureUploadsPath(data: Intent?) {
        val folderToUpload = data?.getParcelableExtra<OCFile>(FolderPickerActivity.EXTRA_FOLDER)
        folderToUpload?.remotePath?.let {
            viewModelScope.launch(coroutinesDispatcherProvider.io) {
                getSpaceById(spaceId = folderToUpload.spaceId, accountName = folderToUpload.owner)
                savePictureUploadsConfigurationUseCase(
                    SavePictureUploadsConfigurationUseCase.Params(
                        composePictureUploadsConfiguration(
                            uploadPath = it,
                            spaceId = pictureUploadsSpace?.id,
                        )
                    )
                )
            }
        }
    }

    fun handleSelectAccount(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getPersonalSpaceForAccount(accountName)
            savePictureUploadsConfigurationUseCase(
                SavePictureUploadsConfigurationUseCase.Params(
                    composePictureUploadsConfiguration(
                        accountName = accountName,
                        uploadPath = null,
                        spaceId = pictureUploadsSpace?.id,
                    )
                )
            )
        }
    }

    fun handleSelectBehaviour(behaviorString: String) {
        val behavior = UploadBehavior.fromString(behaviorString)

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            savePictureUploadsConfigurationUseCase(
                SavePictureUploadsConfigurationUseCase.Params(composePictureUploadsConfiguration(behavior = behavior))
            )
        }
    }

    fun handleSelectPictureUploadsSourcePath(contentUriForTree: Uri) {
        // If the source path has changed, update camera uploads last sync
        val previousSourcePath = _pictureUploads.value?.sourcePath?.trimEnd(File.separatorChar)

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            savePictureUploadsConfigurationUseCase(
                SavePictureUploadsConfigurationUseCase.Params(
                    composePictureUploadsConfiguration(
                        sourcePath = contentUriForTree.toString(),
                        timestamp = System.currentTimeMillis().takeIf { previousSourcePath != contentUriForTree.encodedPath }
                    )
                )
            )
        }
    }

    fun schedulePictureUploads() {
        workManagerProvider.enqueueAutomaticUploadsWorker()
    }

    private fun composePictureUploadsConfiguration(
        accountName: String? = _pictureUploads.value?.accountName,
        uploadPath: String? = _pictureUploads.value?.uploadPath,
        wifiOnly: Boolean? = _pictureUploads.value?.wifiOnly,
        chargingOnly: Boolean? = _pictureUploads.value?.chargingOnly,
        sourcePath: String? = _pictureUploads.value?.sourcePath,
        behavior: UploadBehavior? = _pictureUploads.value?.behavior,
        useSubfoldersBehaviour: UseSubfoldersBehaviour? = _pictureUploads.value?.useSubfoldersBehaviour,
        timestamp: Long? = _pictureUploads.value?.lastSyncTimestamp,
        spaceId: String? = _pictureUploads.value?.spaceId,
    ): FolderBackUpConfiguration = FolderBackUpConfiguration(
        accountName = accountName ?: accountProvider.getCurrentOpenCloudAccount()!!.name,
        behavior = behavior ?: UploadBehavior.COPY,
        sourcePath = sourcePath.orEmpty(),
        uploadPath = uploadPath ?: PREF__CAMERA_UPLOADS_DEFAULT_PATH,
        wifiOnly = wifiOnly ?: false,
        chargingOnly = chargingOnly ?: false,
        useSubfoldersBehaviour = useSubfoldersBehaviour ?: UseSubfoldersBehaviour.NONE,
        lastSyncTimestamp = timestamp ?: System.currentTimeMillis(),
        name = _pictureUploads.value?.name ?: pictureUploadsName,
        spaceId = spaceId,
    ).also {
        Timber.d("Picture uploads configuration updated. New configuration: $it")
    }

    private fun handleSpaceName(spaceName: String?): String? =
        if (pictureUploadsSpace?.isPersonal == true) {
            contextProvider.getString(R.string.bottom_nav_personal)
        } else {
            spaceName
        }

    fun getUploadPathString(): String {

        val spaceName = handleSpaceName(pictureUploadsSpace?.name)
        val uploadPath = pictureUploads.value?.uploadPath
        val spaceId = pictureUploads.value?.spaceId

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
        pictureUploadsSpace = result
    }

    private fun getSpaceById(spaceId: String?, accountName: String) {
        val result = getSpaceByIdForAccountUseCase(
            GetSpaceByIdForAccountUseCase.Params(
                accountName = accountName,
                spaceId = spaceId
            )
        )
        pictureUploadsSpace = result
    }
}
