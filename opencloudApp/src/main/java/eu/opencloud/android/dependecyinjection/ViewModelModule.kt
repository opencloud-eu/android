/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author David Crespo Ríos
 *
 * Copyright (C) 2024 ownCloud GmbH.
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

package eu.opencloud.android.dependecyinjection

import eu.opencloud.android.MainApp
import eu.opencloud.android.domain.files.model.FileListOption
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.presentation.accounts.ManageAccountsViewModel
import eu.opencloud.android.presentation.authentication.AuthenticationViewModel
import eu.opencloud.android.presentation.authentication.oauth.OAuthViewModel
import eu.opencloud.android.presentation.capabilities.CapabilityViewModel
import eu.opencloud.android.presentation.common.DrawerViewModel
import eu.opencloud.android.presentation.conflicts.ConflictsResolveViewModel
import eu.opencloud.android.presentation.files.details.FileDetailsViewModel
import eu.opencloud.android.presentation.files.filelist.MainFileListViewModel
import eu.opencloud.android.presentation.files.operations.FileOperationsViewModel
import eu.opencloud.android.presentation.logging.LogListViewModel
import eu.opencloud.android.presentation.migration.MigrationViewModel
import eu.opencloud.android.presentation.previews.PreviewAudioViewModel
import eu.opencloud.android.presentation.previews.PreviewTextViewModel
import eu.opencloud.android.presentation.previews.PreviewVideoViewModel
import eu.opencloud.android.presentation.releasenotes.ReleaseNotesViewModel
import eu.opencloud.android.presentation.security.biometric.BiometricViewModel
import eu.opencloud.android.presentation.security.passcode.PassCodeViewModel
import eu.opencloud.android.presentation.security.passcode.PasscodeAction
import eu.opencloud.android.presentation.security.pattern.PatternViewModel
import eu.opencloud.android.presentation.settings.SettingsViewModel
import eu.opencloud.android.presentation.settings.advanced.SettingsAdvancedViewModel
import eu.opencloud.android.presentation.settings.automaticuploads.SettingsPictureUploadsViewModel
import eu.opencloud.android.presentation.settings.automaticuploads.SettingsVideoUploadsViewModel
import eu.opencloud.android.presentation.settings.logging.SettingsLogsViewModel
import eu.opencloud.android.presentation.settings.more.SettingsMoreViewModel
import eu.opencloud.android.presentation.settings.security.SettingsSecurityViewModel
import eu.opencloud.android.presentation.sharing.ShareViewModel
import eu.opencloud.android.presentation.spaces.SpacesListViewModel
import eu.opencloud.android.presentation.transfers.TransfersViewModel
import eu.opencloud.android.ui.ReceiveExternalFilesViewModel
import eu.opencloud.android.ui.preview.PreviewImageViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::ManageAccountsViewModel)
    viewModelOf(::BiometricViewModel)
    viewModelOf(::DrawerViewModel)
    viewModelOf(::FileDetailsViewModel)
    viewModelOf(::FileOperationsViewModel)
    viewModelOf(::LogListViewModel)
    viewModelOf(::OAuthViewModel)
    viewModelOf(::PatternViewModel)
    viewModelOf(::PreviewAudioViewModel)
    viewModelOf(::PreviewImageViewModel)
    viewModelOf(::PreviewTextViewModel)
    viewModelOf(::PreviewVideoViewModel)
    viewModelOf(::ReceiveExternalFilesViewModel)
    viewModelOf(::ReleaseNotesViewModel)
    viewModelOf(::SettingsAdvancedViewModel)
    viewModelOf(::SettingsLogsViewModel)
    viewModelOf(::SettingsMoreViewModel)
    viewModelOf(::SettingsPictureUploadsViewModel)
    viewModelOf(::SettingsSecurityViewModel)
    viewModelOf(::SettingsVideoUploadsViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::FileOperationsViewModel)

    viewModel { (accountName: String) -> CapabilityViewModel(accountName, get(), get(), get(), get()) }
    viewModel { (action: PasscodeAction) -> PassCodeViewModel(get(), get(), action) }
    viewModel { (filePath: String, accountName: String) ->
        ShareViewModel(filePath, accountName, get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { (initialFolderToDisplay: OCFile, fileListOption: FileListOption) ->
        MainFileListViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            initialFolderToDisplay, fileListOption)
    }
    viewModel { (ocFile: OCFile) -> ConflictsResolveViewModel(get(), get(), get(), get(), get(), ocFile) }
    viewModel { AuthenticationViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { MigrationViewModel(MainApp.dataFolder, get(), get(), get(), get(), get(), get(), get()) }
    viewModel { TransfersViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        get()) }
    viewModel { ReceiveExternalFilesViewModel(get(), get(), get(), get()) }
    viewModel { (accountName: String, showPersonalSpace: Boolean) ->
        SpacesListViewModel(get(), get(), get(), get(), get(), get(), get(), accountName, showPersonalSpace)
    }
}
