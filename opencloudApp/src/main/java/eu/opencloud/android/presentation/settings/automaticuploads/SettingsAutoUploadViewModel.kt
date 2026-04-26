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

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.opencloud.android.R
import eu.opencloud.android.db.PreferenceManager.PREF__CAMERA_UPLOADS_DEFAULT_PATH
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration.Companion.pictureUploadsName
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.automaticuploads.model.UseSubfoldersBehaviour
import eu.opencloud.android.domain.automaticuploads.usecases.GetFolderBackupConfigurationStreamUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetFolderBackupConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SaveFolderBackupConfigurationUseCase
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.spaces.model.OCSpace
import eu.opencloud.android.domain.spaces.usecases.GetPersonalSpaceForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpaceByIdForAccountUseCase
import eu.opencloud.android.domain.user.usecases.GetUserQuotasAsStreamUseCase
import eu.opencloud.android.providers.AccountProvider
import eu.opencloud.android.providers.ContextProvider
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import eu.opencloud.android.providers.WorkManagerProvider
import eu.opencloud.android.ui.activity.FolderPickerActivity
import eu.opencloud.android.utils.DisplayUtils
import eu.opencloud.android.utils.UriUtilsKt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class SettingsAutoUploadViewModel(
    private val configName: String,
    private val accountProvider: AccountProvider,
    private val saveFolderBackupConfigurationUseCase: SaveFolderBackupConfigurationUseCase,
    private val getFolderBackupConfigurationStreamUseCase: GetFolderBackupConfigurationStreamUseCase,
    private val resetFolderBackupConfigurationUseCase: ResetFolderBackupConfigurationUseCase,
    private val getPersonalSpaceForAccountUseCase: GetPersonalSpaceForAccountUseCase,
    private val getSpaceByIdForAccountUseCase: GetSpaceByIdForAccountUseCase,
    private val workManagerProvider: WorkManagerProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val contextProvider: ContextProvider,
    private val getUserQuotasAsStreamUseCase: GetUserQuotasAsStreamUseCase,
) : ViewModel() {

    private val _autoUploads: MutableStateFlow<FolderBackUpConfiguration?> = MutableStateFlow(null)
    val autoUploads: StateFlow<FolderBackUpConfiguration?> = _autoUploads

    init {
        initAutoUploads()
    }

    private val _autoUploadsSpace = MutableStateFlow<OCSpace?>(null)
    private var autoUploadsSpace: OCSpace?
        get() = _autoUploadsSpace.value
        set(value) {
            _autoUploadsSpace.value = value
        }

    private val loggedAccounts: StateFlow<List<String>> = getUserQuotasAsStreamUseCase(Unit)
        .map { userQuotas ->
            userQuotas
                .filter { it.available != -4L }
                .map { it.accountName }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val uiState: StateFlow<AutoUploadsUiState> = combine(
        _autoUploads,
        _autoUploadsSpace,
        loggedAccounts
    ) { config, space, accounts ->
        mapToUiState(config, space, accounts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = mapToUiState(null, null, emptyList())
    )

    private fun mapToUiState(config: FolderBackUpConfiguration?, space: OCSpace?, accounts: List<String>): AutoUploadsUiState {
        val isEnabled = config != null
        val uploadPathSummary = getUploadPathString(config, space)
        val wifiOnly = config?.wifiOnly ?: false
        val chargingOnly = config?.chargingOnly ?: false
        val behavior = config?.behavior ?: UploadBehavior.COPY
        val useSubfoldersBehaviour = config?.useSubfoldersBehaviour ?: UseSubfoldersBehaviour.NONE
        val accountName = config?.accountName
        val spaceId = config?.spaceId

        val sourcePathTitle: Int
        val sourcePathSummary: String
        if (config == null || config.sourcePath.isEmpty()) {
            sourcePathSummary = contextProvider.getString(R.string.prefs_camera_upload_source_path_empty_summary)
            sourcePathTitle = R.string.prefs_camera_upload_source_path_title_required
        } else {
            val sourceUri = config.sourcePath.toUri()
            sourcePathSummary = UriUtilsKt.getPathFromUri(sourceUri)
            sourcePathTitle = R.string.prefs_camera_upload_source_path_title
        }

        val lastSyncSummary = if (config?.lastSyncTimestamp != null && config.lastSyncTimestamp != 0L) {
            DisplayUtils.getRelativeDateTimeString(
                contextProvider.getContext(),
                config.lastSyncTimestamp,
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS,
                0
            ).toString()
        } else {
            contextProvider.getString(R.string.prefs_camera_upload_last_sync_empty_summary)
        }

        val isPictureUploads = configName == pictureUploadsName

        return AutoUploadsUiState(
            isEnabled = isEnabled,
            uploadPathSummary = uploadPathSummary,
            wifiOnly = wifiOnly,
            chargingOnly = chargingOnly,
            behavior = behavior,
            useSubfoldersBehaviour = useSubfoldersBehaviour,
            accountName = accountName,
            sourcePathTitle = sourcePathTitle,
            sourcePathSummary = sourcePathSummary,
            lastSyncSummary = lastSyncSummary,
            availableAccounts = accounts,
            mainTitle = if (isPictureUploads) R.string.prefs_camera_picture_upload else R.string.prefs_camera_video_upload,
            mainSummary = if (isPictureUploads) R.string.prefs_camera_picture_upload_summary else R.string.prefs_camera_video_upload_summary,
            accountTitle = if (isPictureUploads) R.string.prefs_picture_upload_account else R.string.prefs_video_upload_account,
            wifiOnlyTitle = if (isPictureUploads) {
                R.string.prefs_camera_picture_upload_on_wifi
            } else {
                R.string.prefs_camera_video_upload_on_wifi
            },
            chargingOnlyTitle = if (isPictureUploads) {
                R.string.prefs_camera_picture_upload_on_charging
            } else {
                R.string.prefs_camera_video_upload_on_charging
            },
            uploadPathTitle = if (isPictureUploads) {
                R.string.prefs_camera_picture_upload_path_title
            } else {
                R.string.prefs_camera_video_upload_path_title
            },
            spaceId = spaceId
        )
    }


    private fun initAutoUploads() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getFolderBackupConfigurationStreamUseCase(GetFolderBackupConfigurationStreamUseCase.Params(configName)).collect { config ->
                config?.accountName?.let {
                    getSpaceById(spaceId = config.spaceId, accountName = it)
                }
                _autoUploads.update { config }
            }
        }
    }

    fun enableAutoUploads(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getPersonalSpaceForAccount(accountName)
            saveFolderBackupConfigurationUseCase(
                SaveFolderBackupConfigurationUseCase.Params(
                    composeAutoUploadsConfiguration(
                        accountName = accountName,
                        spaceId = autoUploadsSpace?.id,
                    )
                )
            )
        }
    }

    fun disableAutoUploads() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            resetFolderBackupConfigurationUseCase(ResetFolderBackupConfigurationUseCase.Params(configName))
        }
    }

    fun useWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveFolderBackupConfigurationUseCase(
                SaveFolderBackupConfigurationUseCase.Params(composeAutoUploadsConfiguration(wifiOnly = wifiOnly))
            )
        }
    }

    fun useChargingOnly(chargingOnly: Boolean) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveFolderBackupConfigurationUseCase(
                SaveFolderBackupConfigurationUseCase.Params(
                    composeAutoUploadsConfiguration(chargingOnly = chargingOnly)
                )
            )
        }
    }

    fun handleSelectUseSubfoldersBehaviour(behaviourString: String) {
        val behaviour = UseSubfoldersBehaviour.fromString(behaviourString)

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveFolderBackupConfigurationUseCase(
                SaveFolderBackupConfigurationUseCase.Params(composeAutoUploadsConfiguration(useSubfoldersBehaviour = behaviour))
            )
        }
    }

    fun getAutoUploadsAccount() = _autoUploads.value?.accountName

    fun handleSelectAutoUploadsPath(data: Intent?) {
        val folderToUpload = data?.let {
            IntentCompat.getParcelableExtra(it, FolderPickerActivity.EXTRA_FOLDER, OCFile::class.java)
        }
        folderToUpload?.remotePath?.let {
            viewModelScope.launch(coroutinesDispatcherProvider.io) {
                getSpaceById(spaceId = folderToUpload.spaceId, accountName = folderToUpload.owner)
                saveFolderBackupConfigurationUseCase(
                    SaveFolderBackupConfigurationUseCase.Params(
                        composeAutoUploadsConfiguration(
                            uploadPath = it,
                            spaceId = autoUploadsSpace?.id,
                        )
                    )
                )
            }
        }
    }

    fun handleSelectAccount(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getPersonalSpaceForAccount(accountName)
            saveFolderBackupConfigurationUseCase(
                SaveFolderBackupConfigurationUseCase.Params(
                    composeAutoUploadsConfiguration(
                        accountName = accountName,
                        uploadPath = null,
                        spaceId = autoUploadsSpace?.id,
                    )
                )
            )
        }
    }

    fun handleSelectBehaviour(behaviorString: String) {
        val behavior = UploadBehavior.fromString(behaviorString)

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveFolderBackupConfigurationUseCase(
                SaveFolderBackupConfigurationUseCase.Params(composeAutoUploadsConfiguration(behavior = behavior))
            )
        }
    }

    fun handleSelectAutoUploadsSourcePath(contentUriForTree: Uri) {
        val previousSourcePath = _autoUploads.value?.sourcePath?.trimEnd(File.separatorChar)

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            saveFolderBackupConfigurationUseCase(
                SaveFolderBackupConfigurationUseCase.Params(
                    composeAutoUploadsConfiguration(
                        sourcePath = contentUriForTree.toString(),
                        timestamp = System.currentTimeMillis().takeIf { previousSourcePath != contentUriForTree.toString() }
                    )
                )
            )
        }
    }

    fun scheduleAutoUploads() {
        workManagerProvider.enqueueAutomaticUploadsWorker()
    }

    private fun composeAutoUploadsConfiguration(
        accountName: String? = _autoUploads.value?.accountName,
        uploadPath: String? = _autoUploads.value?.uploadPath,
        wifiOnly: Boolean? = _autoUploads.value?.wifiOnly,
        chargingOnly: Boolean? = _autoUploads.value?.chargingOnly,
        sourcePath: String? = _autoUploads.value?.sourcePath,
        behavior: UploadBehavior? = _autoUploads.value?.behavior,
        useSubfoldersBehaviour: UseSubfoldersBehaviour? = _autoUploads.value?.useSubfoldersBehaviour,
        timestamp: Long? = _autoUploads.value?.lastSyncTimestamp,
        spaceId: String? = _autoUploads.value?.spaceId,
    ): FolderBackUpConfiguration = FolderBackUpConfiguration(
        accountName = accountName ?: accountProvider.getCurrentOpenCloudAccount()!!.name,
        behavior = behavior ?: UploadBehavior.COPY,
        sourcePath = sourcePath.orEmpty(),
        uploadPath = uploadPath ?: PREF__CAMERA_UPLOADS_DEFAULT_PATH,
        wifiOnly = wifiOnly ?: false,
        chargingOnly = chargingOnly ?: false,
        useSubfoldersBehaviour = useSubfoldersBehaviour ?: UseSubfoldersBehaviour.NONE,
        lastSyncTimestamp = timestamp ?: System.currentTimeMillis(),
        name = configName,
        spaceId = spaceId,
    ).also {
        Timber.d("Auto uploads configuration ($configName) updated. New configuration: $it")
    }

    private fun handleSpaceName(space: OCSpace?): String? =
        if (space?.isPersonal == true) {
            contextProvider.getString(R.string.bottom_nav_personal)
        } else {
            space?.name
        }

    private fun getUploadPathString(config: FolderBackUpConfiguration?, space: OCSpace?): String {
        val spaceName = handleSpaceName(space)
        val uploadPath = config?.uploadPath ?: PREF__CAMERA_UPLOADS_DEFAULT_PATH
        val spaceId = config?.spaceId

        return if (spaceId != null && spaceName != null) {
            "$spaceName: $uploadPath"
        } else {
            uploadPath
        }
    }

    private fun getPersonalSpaceForAccount(accountName: String) {
        val result = getPersonalSpaceForAccountUseCase(
            GetPersonalSpaceForAccountUseCase.Params(
                accountName = accountName
            )
        )
        autoUploadsSpace = result
    }

    private fun getSpaceById(spaceId: String?, accountName: String) {
        val result = getSpaceByIdForAccountUseCase(
            GetSpaceByIdForAccountUseCase.Params(
                accountName = accountName,
                spaceId = spaceId
            )
        )
        autoUploadsSpace = result
    }
}

data class AutoUploadsUiState(
    val isEnabled: Boolean = false,
    val uploadPathSummary: String = "",
    val wifiOnly: Boolean = false,
    val chargingOnly: Boolean = false,
    val behavior: UploadBehavior = UploadBehavior.COPY,
    val useSubfoldersBehaviour: UseSubfoldersBehaviour = UseSubfoldersBehaviour.NONE,
    val accountName: String? = null,
    @StringRes val sourcePathTitle: Int = 0,
    val sourcePathSummary: String = "",
    val lastSyncSummary: String = "",
    val availableAccounts: List<String> = emptyList(),
    @StringRes val mainTitle: Int = 0,
    @StringRes val mainSummary: Int = 0,
    @StringRes val accountTitle: Int = 0,
    @StringRes val wifiOnlyTitle: Int = 0,
    @StringRes val chargingOnlyTitle: Int = 0,
    @StringRes val uploadPathTitle: Int = 0,
    val spaceId: String? = null,
)
