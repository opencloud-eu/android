/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author Aitor Ballesteros Pavón
 * @author Jorge Aguado Recio
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

import eu.opencloud.android.domain.appregistry.usecases.CreateFileWithAppProviderUseCase
import eu.opencloud.android.domain.appregistry.usecases.GetAppRegistryForMimeTypeAsStreamUseCase
import eu.opencloud.android.domain.appregistry.usecases.GetAppRegistryWhichAllowCreationAsStreamUseCase
import eu.opencloud.android.domain.appregistry.usecases.GetUrlToOpenInWebUseCase
import eu.opencloud.android.domain.authentication.oauth.OIDCDiscoveryUseCase
import eu.opencloud.android.domain.authentication.oauth.RegisterClientUseCase
import eu.opencloud.android.domain.authentication.oauth.RequestTokenUseCase
import eu.opencloud.android.domain.authentication.usecases.GetBaseUrlUseCase
import eu.opencloud.android.domain.authentication.usecases.LoginBasicAsyncUseCase
import eu.opencloud.android.domain.authentication.usecases.LoginOAuthAsyncUseCase
import eu.opencloud.android.domain.authentication.usecases.SupportsOAuth2UseCase
import eu.opencloud.android.domain.availableoffline.usecases.GetFilesAvailableOfflineFromAccountAsStreamUseCase
import eu.opencloud.android.domain.availableoffline.usecases.GetFilesAvailableOfflineFromAccountUseCase
import eu.opencloud.android.domain.availableoffline.usecases.GetFilesAvailableOfflineFromEveryAccountUseCase
import eu.opencloud.android.domain.availableoffline.usecases.SetFilesAsAvailableOfflineUseCase
import eu.opencloud.android.domain.availableoffline.usecases.UnsetFilesAsAvailableOfflineUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.GetAutomaticUploadsConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.GetPictureUploadsConfigurationStreamUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.GetVideoUploadsConfigurationStreamUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetPictureUploadsUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetVideoUploadsUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SavePictureUploadsConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SaveVideoUploadsConfigurationUseCase
import eu.opencloud.android.domain.capabilities.usecases.GetCapabilitiesAsLiveDataUseCase
import eu.opencloud.android.domain.capabilities.usecases.GetStoredCapabilitiesUseCase
import eu.opencloud.android.domain.capabilities.usecases.RefreshCapabilitiesFromServerAsyncUseCase
import eu.opencloud.android.domain.files.usecases.IsAnyFileAvailableLocallyAndNotAvailableOfflineUseCase
import eu.opencloud.android.domain.files.usecases.CleanConflictUseCase
import eu.opencloud.android.domain.files.usecases.CleanWorkersUUIDUseCase
import eu.opencloud.android.domain.files.usecases.CopyFileUseCase
import eu.opencloud.android.domain.files.usecases.CreateFolderAsyncUseCase
import eu.opencloud.android.domain.files.usecases.DisableThumbnailsForFileUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByIdAsStreamUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByIdUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import eu.opencloud.android.domain.files.usecases.GetFileWithSyncInfoByIdUseCase
import eu.opencloud.android.domain.files.usecases.GetFolderContentAsStreamUseCase
import eu.opencloud.android.domain.files.usecases.GetFolderContentUseCase
import eu.opencloud.android.domain.files.usecases.GetFolderImagesUseCase
import eu.opencloud.android.domain.files.usecases.GetPersonalRootFolderForAccountUseCase
import eu.opencloud.android.domain.files.usecases.GetSearchFolderContentUseCase
import eu.opencloud.android.domain.files.usecases.GetSharedByLinkForAccountAsStreamUseCase
import eu.opencloud.android.domain.files.usecases.GetSharesRootFolderForAccount
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase
import eu.opencloud.android.domain.files.usecases.ManageDeepLinkUseCase
import eu.opencloud.android.domain.files.usecases.MoveFileUseCase
import eu.opencloud.android.domain.files.usecases.RemoveFileUseCase
import eu.opencloud.android.domain.files.usecases.RenameFileUseCase
import eu.opencloud.android.domain.files.usecases.SaveConflictUseCase
import eu.opencloud.android.domain.files.usecases.SaveDownloadWorkerUUIDUseCase
import eu.opencloud.android.domain.files.usecases.SaveFileOrFolderUseCase
import eu.opencloud.android.domain.files.usecases.SetLastUsageFileUseCase
import eu.opencloud.android.domain.files.usecases.SortFilesUseCase
import eu.opencloud.android.domain.files.usecases.SortFilesWithSyncInfoUseCase
import eu.opencloud.android.domain.files.usecases.UpdateAlreadyDownloadedFilesPathUseCase
import eu.opencloud.android.domain.server.usecases.GetServerInfoAsyncUseCase
import eu.opencloud.android.domain.sharing.sharees.GetShareesAsyncUseCase
import eu.opencloud.android.domain.sharing.shares.usecases.CreatePrivateShareAsyncUseCase
import eu.opencloud.android.domain.sharing.shares.usecases.CreatePublicShareAsyncUseCase
import eu.opencloud.android.domain.sharing.shares.usecases.DeleteShareAsyncUseCase
import eu.opencloud.android.domain.sharing.shares.usecases.EditPrivateShareAsyncUseCase
import eu.opencloud.android.domain.sharing.shares.usecases.EditPublicShareAsyncUseCase
import eu.opencloud.android.domain.sharing.shares.usecases.GetShareAsLiveDataUseCase
import eu.opencloud.android.domain.sharing.shares.usecases.GetSharesAsLiveDataUseCase
import eu.opencloud.android.domain.sharing.shares.usecases.RefreshSharesFromServerAsyncUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalAndProjectSpacesForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalAndProjectSpacesWithSpecialsForAccountAsStreamUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalSpaceForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalSpacesWithSpecialsForAccountAsStreamUseCase
import eu.opencloud.android.domain.spaces.usecases.GetProjectSpacesWithSpecialsForAccountAsStreamUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpaceByIdForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpaceWithSpecialsByIdForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpacesFromEveryAccountUseCaseAsStream
import eu.opencloud.android.domain.spaces.usecases.RefreshSpacesFromServerAsyncUseCase
import eu.opencloud.android.domain.transfers.usecases.ClearSuccessfulTransfersUseCase
import eu.opencloud.android.domain.transfers.usecases.GetAllTransfersAsStreamUseCase
import eu.opencloud.android.domain.transfers.usecases.GetAllTransfersUseCase
import eu.opencloud.android.domain.transfers.usecases.UpdatePendingUploadsPathUseCase
import eu.opencloud.android.domain.user.usecases.GetStoredQuotaUseCase
import eu.opencloud.android.domain.user.usecases.GetStoredQuotaAsStreamUseCase
import eu.opencloud.android.domain.user.usecases.GetUserAvatarAsyncUseCase
import eu.opencloud.android.domain.user.usecases.GetUserInfoAsyncUseCase
import eu.opencloud.android.domain.user.usecases.GetUserQuotasUseCase
import eu.opencloud.android.domain.user.usecases.GetUserQuotasAsStreamUseCase
import eu.opencloud.android.domain.user.usecases.RefreshUserQuotaFromServerAsyncUseCase
import eu.opencloud.android.domain.webfinger.usecases.GetOpenCloudInstanceFromWebFingerUseCase
import eu.opencloud.android.domain.webfinger.usecases.GetOpenCloudInstancesFromAuthenticatedWebFingerUseCase
import eu.opencloud.android.usecases.accounts.RemoveAccountUseCase
import eu.opencloud.android.usecases.files.FilterFileMenuOptionsUseCase
import eu.opencloud.android.usecases.files.RemoveLocalFilesForAccountUseCase
import eu.opencloud.android.usecases.files.RemoveLocallyFilesWithLastUsageOlderThanGivenTimeUseCase
import eu.opencloud.android.usecases.synchronization.SynchronizeFileUseCase
import eu.opencloud.android.usecases.synchronization.SynchronizeFolderUseCase
import eu.opencloud.android.usecases.transfers.downloads.CancelDownloadForFileUseCase
import eu.opencloud.android.usecases.transfers.downloads.CancelDownloadsRecursivelyUseCase
import eu.opencloud.android.usecases.transfers.downloads.DownloadFileUseCase
import eu.opencloud.android.usecases.transfers.downloads.GetLiveDataForDownloadingFileUseCase
import eu.opencloud.android.usecases.transfers.downloads.GetLiveDataForFinishedDownloadsFromAccountUseCase
import eu.opencloud.android.usecases.transfers.uploads.CancelTransfersFromAccountUseCase
import eu.opencloud.android.usecases.transfers.uploads.CancelUploadForFileUseCase
import eu.opencloud.android.usecases.transfers.uploads.CancelUploadUseCase
import eu.opencloud.android.usecases.transfers.uploads.CancelUploadsRecursivelyUseCase
import eu.opencloud.android.usecases.transfers.uploads.ClearFailedTransfersUseCase
import eu.opencloud.android.usecases.transfers.uploads.RetryFailedUploadsForAccountUseCase
import eu.opencloud.android.usecases.transfers.uploads.RetryFailedUploadsUseCase
import eu.opencloud.android.usecases.transfers.uploads.RetryUploadFromContentUriUseCase
import eu.opencloud.android.usecases.transfers.uploads.RetryUploadFromSystemUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFileFromContentUriUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFileFromSystemUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFileInConflictUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFilesFromContentUriUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFilesFromSystemUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFileWithTusUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val useCaseModule = module {
    // Authentication
    factoryOf(::GetBaseUrlUseCase)
    factoryOf(::GetOpenCloudInstanceFromWebFingerUseCase)
    factoryOf(::GetOpenCloudInstancesFromAuthenticatedWebFingerUseCase)
    factoryOf(::LoginBasicAsyncUseCase)
    factoryOf(::LoginOAuthAsyncUseCase)
    factoryOf(::SupportsOAuth2UseCase)

    // OAuth
    factoryOf(::OIDCDiscoveryUseCase)
    factoryOf(::RegisterClientUseCase)
    factoryOf(::RequestTokenUseCase)

    // Capabilities
    factoryOf(::GetCapabilitiesAsLiveDataUseCase)
    factoryOf(::GetStoredCapabilitiesUseCase)
    factoryOf(::RefreshCapabilitiesFromServerAsyncUseCase)

    // Files
    factoryOf(::CleanConflictUseCase)
    factoryOf(::CleanWorkersUUIDUseCase)
    factoryOf(::CopyFileUseCase)
    factoryOf(::CreateFolderAsyncUseCase)
    factoryOf(::DisableThumbnailsForFileUseCase)
    factoryOf(::FilterFileMenuOptionsUseCase)
    factoryOf(::GetFileByIdAsStreamUseCase)
    factoryOf(::GetFileByIdUseCase)
    factoryOf(::GetFileByRemotePathUseCase)
    factoryOf(::GetFileWithSyncInfoByIdUseCase)
    factoryOf(::GetFolderContentAsStreamUseCase)
    factoryOf(::GetFolderContentUseCase)
    factoryOf(::GetFolderImagesUseCase)
    factoryOf(::IsAnyFileAvailableLocallyAndNotAvailableOfflineUseCase)
    factoryOf(::GetPersonalRootFolderForAccountUseCase)
    factoryOf(::GetSearchFolderContentUseCase)
    factoryOf(::GetSharedByLinkForAccountAsStreamUseCase)
    factoryOf(::GetSharesRootFolderForAccount)
    factoryOf(::GetUrlToOpenInWebUseCase)
    factoryOf(::ManageDeepLinkUseCase)
    factoryOf(::MoveFileUseCase)
    factoryOf(::RemoveFileUseCase)
    factoryOf(::RemoveLocalFilesForAccountUseCase)
    factoryOf(::RemoveLocallyFilesWithLastUsageOlderThanGivenTimeUseCase)
    factoryOf(::RenameFileUseCase)
    factoryOf(::SaveConflictUseCase)
    factoryOf(::SaveDownloadWorkerUUIDUseCase)
    factoryOf(::SaveFileOrFolderUseCase)
    factoryOf(::SetLastUsageFileUseCase)
    factoryOf(::SortFilesUseCase)
    factoryOf(::SortFilesWithSyncInfoUseCase)
    factoryOf(::SynchronizeFileUseCase)
    factoryOf(::SynchronizeFolderUseCase)

    // Open in web
    factoryOf(::CreateFileWithAppProviderUseCase)
    factoryOf(::GetAppRegistryForMimeTypeAsStreamUseCase)
    factoryOf(::GetAppRegistryWhichAllowCreationAsStreamUseCase)
    factoryOf(::GetUrlToOpenInWebUseCase)

    // Av Offline
    factoryOf(::GetFilesAvailableOfflineFromAccountAsStreamUseCase)
    factoryOf(::GetFilesAvailableOfflineFromAccountUseCase)
    factoryOf(::GetFilesAvailableOfflineFromEveryAccountUseCase)
    factoryOf(::SetFilesAsAvailableOfflineUseCase)
    factoryOf(::UnsetFilesAsAvailableOfflineUseCase)

    // Sharing
    factoryOf(::CreatePrivateShareAsyncUseCase)
    factoryOf(::CreatePublicShareAsyncUseCase)
    factoryOf(::DeleteShareAsyncUseCase)
    factoryOf(::EditPrivateShareAsyncUseCase)
    factoryOf(::EditPublicShareAsyncUseCase)
    factoryOf(::GetShareAsLiveDataUseCase)
    factoryOf(::GetShareesAsyncUseCase)
    factoryOf(::GetSharesAsLiveDataUseCase)
    factoryOf(::RefreshSharesFromServerAsyncUseCase)

    // Spaces
    factoryOf(::GetPersonalAndProjectSpacesForAccountUseCase)
    factoryOf(::GetPersonalAndProjectSpacesWithSpecialsForAccountAsStreamUseCase)
    factoryOf(::GetPersonalSpaceForAccountUseCase)
    factoryOf(::GetPersonalSpacesWithSpecialsForAccountAsStreamUseCase)
    factoryOf(::GetProjectSpacesWithSpecialsForAccountAsStreamUseCase)
    factoryOf(::GetSpaceWithSpecialsByIdForAccountUseCase)
    factoryOf(::GetSpacesFromEveryAccountUseCaseAsStream)
    factoryOf(::GetWebDavUrlForSpaceUseCase)
    factoryOf(::RefreshSpacesFromServerAsyncUseCase)
    factoryOf(::GetSpaceByIdForAccountUseCase)

    // Transfers
    factoryOf(::CancelDownloadForFileUseCase)
    factoryOf(::CancelDownloadsRecursivelyUseCase)
    factoryOf(::CancelTransfersFromAccountUseCase)
    factoryOf(::CancelUploadForFileUseCase)
    factoryOf(::CancelUploadUseCase)
    factoryOf(::CancelUploadsRecursivelyUseCase)
    factoryOf(::ClearFailedTransfersUseCase)
    factoryOf(::ClearSuccessfulTransfersUseCase)
    factoryOf(::DownloadFileUseCase)
    factoryOf(::GetAllTransfersAsStreamUseCase)
    factoryOf(::GetAllTransfersUseCase)
    factoryOf(::GetLiveDataForDownloadingFileUseCase)
    factoryOf(::GetLiveDataForFinishedDownloadsFromAccountUseCase)
    factoryOf(::RetryFailedUploadsForAccountUseCase)
    factoryOf(::RetryFailedUploadsUseCase)
    factoryOf(::RetryUploadFromContentUriUseCase)
    factoryOf(::RetryUploadFromSystemUseCase)
    factoryOf(::UpdateAlreadyDownloadedFilesPathUseCase)
    factoryOf(::UpdatePendingUploadsPathUseCase)
    factoryOf(::UploadFileFromContentUriUseCase)
    factoryOf(::UploadFileFromSystemUseCase)
    factoryOf(::UploadFileWithTusUseCase)
    factoryOf(::UploadFileInConflictUseCase)
    factoryOf(::UploadFilesFromContentUriUseCase)
    factoryOf(::UploadFilesFromSystemUseCase)

    // User
    factoryOf(::GetStoredQuotaAsStreamUseCase)
    factoryOf(::GetStoredQuotaUseCase)
    factoryOf(::GetUserAvatarAsyncUseCase)
    factoryOf(::GetUserInfoAsyncUseCase)
    factoryOf(::GetUserQuotasAsStreamUseCase)
    factoryOf(::GetUserQuotasUseCase)
    factoryOf(::RefreshUserQuotaFromServerAsyncUseCase)

    // Server
    factoryOf(::GetServerInfoAsyncUseCase)

    // Camera Uploads
    factoryOf(::GetAutomaticUploadsConfigurationUseCase)
    factoryOf(::GetPictureUploadsConfigurationStreamUseCase)
    factoryOf(::GetVideoUploadsConfigurationStreamUseCase)
    factoryOf(::ResetPictureUploadsUseCase)
    factoryOf(::ResetVideoUploadsUseCase)
    factoryOf(::SavePictureUploadsConfigurationUseCase)
    factoryOf(::SaveVideoUploadsConfigurationUseCase)

    // Accounts
    factoryOf(::RemoveAccountUseCase)
}
