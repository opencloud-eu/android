/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 OpenCloud GmbH.
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

package eu.opencloud.android.presentation.viewmodels

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.work.WorkInfo
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.domain.UseCaseResult
import eu.opencloud.android.domain.appregistry.usecases.CreateFileWithAppProviderUseCase
import eu.opencloud.android.domain.appregistry.usecases.GetAppRegistryForMimeTypeAsStreamUseCase
import eu.opencloud.android.domain.appregistry.usecases.GetAppRegistryWhichAllowCreationAsStreamUseCase
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.domain.automaticuploads.usecases.GetPictureUploadsConfigurationStreamUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.GetVideoUploadsConfigurationStreamUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetPictureUploadsUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetVideoUploadsUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SavePictureUploadsConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.SaveVideoUploadsConfigurationUseCase
import eu.opencloud.android.domain.availableoffline.usecases.GetFilesAvailableOfflineFromAccountAsStreamUseCase
import eu.opencloud.android.domain.availableoffline.usecases.SetFilesAsAvailableOfflineUseCase
import eu.opencloud.android.domain.availableoffline.usecases.UnsetFilesAsAvailableOfflineUseCase
import eu.opencloud.android.domain.files.model.FileListOption
import eu.opencloud.android.domain.files.usecases.CopyFileUseCase
import eu.opencloud.android.domain.files.usecases.CreateFolderAsyncUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByIdUseCase
import eu.opencloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import eu.opencloud.android.domain.files.usecases.GetFolderContentAsStreamUseCase
import eu.opencloud.android.domain.files.usecases.GetSharedByLinkForAccountAsStreamUseCase
import eu.opencloud.android.domain.files.usecases.IsAnyFileAvailableLocallyAndNotAvailableOfflineUseCase
import eu.opencloud.android.domain.files.usecases.ManageDeepLinkUseCase
import eu.opencloud.android.domain.files.usecases.MoveFileUseCase
import eu.opencloud.android.domain.files.usecases.RemoveFileUseCase
import eu.opencloud.android.domain.files.usecases.RenameFileUseCase
import eu.opencloud.android.domain.files.usecases.SetLastUsageFileUseCase
import eu.opencloud.android.domain.files.usecases.SortFilesWithSyncInfoUseCase
import eu.opencloud.android.domain.spaces.usecases.GetPersonalSpaceForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpaceByIdForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpaceWithSpecialsByIdForAccountUseCase
import eu.opencloud.android.domain.spaces.usecases.GetSpacesFromEveryAccountUseCaseAsStream
import eu.opencloud.android.domain.transfers.usecases.ClearSuccessfulTransfersUseCase
import eu.opencloud.android.domain.transfers.usecases.GetAllTransfersAsStreamUseCase
import eu.opencloud.android.presentation.common.UIResult
import eu.opencloud.android.presentation.files.SortOrder
import eu.opencloud.android.presentation.files.SortOrder.Companion.PREF_FILE_LIST_SORT_ORDER
import eu.opencloud.android.presentation.files.SortType
import eu.opencloud.android.presentation.files.SortType.Companion.PREF_FILE_LIST_SORT_TYPE
import eu.opencloud.android.presentation.files.filelist.MainFileListViewModel
import eu.opencloud.android.presentation.files.operations.FileOperation
import eu.opencloud.android.presentation.files.operations.FileOperationsViewModel
import eu.opencloud.android.presentation.settings.automaticuploads.SettingsPictureUploadsViewModel
import eu.opencloud.android.presentation.settings.automaticuploads.SettingsVideoUploadsViewModel
import eu.opencloud.android.presentation.transfers.TransfersViewModel
import eu.opencloud.android.providers.AccountProvider
import eu.opencloud.android.providers.ContextProvider
import eu.opencloud.android.providers.WorkManagerProvider
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_BACKUP
import eu.opencloud.android.testutil.OC_FILE
import eu.opencloud.android.testutil.OC_FOLDER
import eu.opencloud.android.testutil.OC_FOLDER_WITH_SPACE_ID
import eu.opencloud.android.testutil.OC_ROOT_FOLDER
import eu.opencloud.android.testutil.OC_SPACE_PERSONAL
import eu.opencloud.android.testutil.OC_TRANSFER
import eu.opencloud.android.testutil.livedata.getEmittedValues
import eu.opencloud.android.usecases.files.FilterFileMenuOptionsUseCase
import eu.opencloud.android.usecases.synchronization.SynchronizeFileUseCase
import eu.opencloud.android.usecases.synchronization.SynchronizeFolderUseCase
import eu.opencloud.android.usecases.transfers.downloads.CancelDownloadForFileUseCase
import eu.opencloud.android.usecases.transfers.downloads.CancelDownloadsRecursivelyUseCase
import eu.opencloud.android.usecases.transfers.uploads.CancelUploadForFileUseCase
import eu.opencloud.android.usecases.transfers.uploads.CancelUploadUseCase
import eu.opencloud.android.usecases.transfers.uploads.CancelUploadsRecursivelyUseCase
import eu.opencloud.android.usecases.transfers.uploads.ClearFailedTransfersUseCase
import eu.opencloud.android.usecases.transfers.uploads.RetryFailedUploadsForAccountUseCase
import eu.opencloud.android.usecases.transfers.uploads.RetryFailedUploadsUseCase
import eu.opencloud.android.usecases.transfers.uploads.RetryUploadFromContentUriUseCase
import eu.opencloud.android.usecases.transfers.uploads.RetryUploadFromSystemUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFilesFromContentUriUseCase
import eu.opencloud.android.usecases.transfers.uploads.UploadFilesFromSystemUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@ExperimentalCoroutinesApi
class KeyAppViewModelsTest : ViewModelTest() {

    private lateinit var contextProvider: ContextProvider

    @Before
    fun setUp() {
        contextProvider = mockk(relaxed = true)
        every { contextProvider.isConnected() } returns true
        kotlinx.coroutines.Dispatchers.setMain(testCoroutineDispatcher)
        startKoin {
            allowOverride(override = true)
            modules(
                module {
                    factory { contextProvider }
                }
            )
        }
    }

    @After
    override fun tearDown() {
        stopKoin()
        super.tearDown()
    }

    @Test
    fun transfers_combinesTransfersWithSpacesAndDelegatesUploads() = runTest(testCoroutineDispatcher) {
        val uploadFilesFromSystemUseCase = mockk<UploadFilesFromSystemUseCase>()
        every { uploadFilesFromSystemUseCase(any()) } returns Unit
        val getAllTransfersAsStreamUseCase = mockk<GetAllTransfersAsStreamUseCase>()
        val getSpacesFromEveryAccountUseCaseAsStream = mockk<GetSpacesFromEveryAccountUseCaseAsStream>()
        val transfer = OC_TRANSFER.copy(id = 7, spaceId = OC_SPACE_PERSONAL.id)
        every { getAllTransfersAsStreamUseCase(Unit) } returns flowOf(listOf(transfer))
        every { getSpacesFromEveryAccountUseCaseAsStream(Unit) } returns flowOf(listOf(OC_SPACE_PERSONAL))
        val workInfosLiveData = MutableLiveData<List<WorkInfo>>(emptyList())
        val workManagerProvider = mockk<WorkManagerProvider>()
        every { workManagerProvider.getRunningUploadsWorkInfosLiveData() } returns workInfosLiveData

        val viewModel = TransfersViewModel(
            uploadFilesFromContentUriUseCase = mockk(relaxed = true),
            uploadFilesFromSystemUseCase = uploadFilesFromSystemUseCase,
            cancelUploadUseCase = mockk(relaxed = true),
            retryUploadFromSystemUseCase = mockk(relaxed = true),
            retryUploadFromContentUriUseCase = mockk(relaxed = true),
            retryFailedUploadsForAccountUseCase = mockk(relaxed = true),
            clearFailedTransfersUseCase = mockk(relaxed = true),
            retryFailedUploadsUseCase = mockk(relaxed = true),
            clearSuccessfulTransfersUseCase = mockk(relaxed = true),
            getAllTransfersAsStreamUseCase = getAllTransfersAsStreamUseCase,
            cancelDownloadForFileUseCase = mockk(relaxed = true),
            cancelUploadForFileUseCase = mockk(relaxed = true),
            cancelUploadsRecursivelyUseCase = mockk(relaxed = true),
            cancelDownloadsRecursivelyUseCase = mockk(relaxed = true),
            getSpacesFromEveryAccountUseCaseAsStream = getSpacesFromEveryAccountUseCaseAsStream,
            coroutinesDispatcherProvider = coroutineDispatcherProvider,
            workManagerProvider = workManagerProvider,
        )

        val transferWithSpace = viewModel.transfersWithSpaceStateFlow.first { it.isNotEmpty() }.single()
        assertEquals(transfer, transferWithSpace.first)
        assertEquals(OC_SPACE_PERSONAL, transferWithSpace.second)

        viewModel.uploadFilesFromSystem(
            accountName = OC_ACCOUNT_NAME,
            listOfLocalPaths = listOf("/tmp/image.jpg"),
            uploadFolderPath = "/Photos/",
            spaceId = OC_SPACE_PERSONAL.id,
        )
        testCoroutineDispatcher.scheduler.advanceUntilIdle()

        verify {
            uploadFilesFromSystemUseCase(
                UploadFilesFromSystemUseCase.Params(
                    accountName = OC_ACCOUNT_NAME,
                    listOfLocalPaths = listOf("/tmp/image.jpg"),
                    uploadFolderPath = "/Photos/",
                    spaceId = OC_SPACE_PERSONAL.id,
                )
            )
        }
    }

    @Test
    fun fileOperations_createFolderEmitsLoadingAndSuccess() {
        val createFolderAsyncUseCase = mockk<CreateFolderAsyncUseCase>()
        every { createFolderAsyncUseCase(any()) } returns UseCaseResult.Success(Unit)
        val viewModel = FileOperationsViewModel(
            createFolderAsyncUseCase = createFolderAsyncUseCase,
            copyFileUseCase = mockk(relaxed = true),
            moveFileUseCase = mockk(relaxed = true),
            removeFileUseCase = mockk(relaxed = true),
            renameFileUseCase = mockk(relaxed = true),
            synchronizeFileUseCase = mockk(relaxed = true),
            synchronizeFolderUseCase = mockk(relaxed = true),
            createFileWithAppProviderUseCase = mockk(relaxed = true),
            setFilesAsAvailableOfflineUseCase = mockk(relaxed = true),
            unsetFilesAsAvailableOfflineUseCase = mockk(relaxed = true),
            manageDeepLinkUseCase = mockk(relaxed = true),
            setLastUsageFileUseCase = mockk(relaxed = true),
            isAnyFileAvailableLocallyAndNotAvailableOfflineUseCase = mockk(relaxed = true),
            contextProvider = contextProvider,
            coroutinesDispatcherProvider = coroutineDispatcherProvider,
        )

        val emittedValues = viewModel.createFolder.getEmittedValues(2) {
            viewModel.performOperation(FileOperation.CreateFolder("Reports", OC_FOLDER))
            testCoroutineDispatcher.scheduler.advanceUntilIdle()
        }

        assertTrue(emittedValues[0]!!.peekContent() is UIResult.Loading)
        assertTrue(emittedValues[1]!!.peekContent() is UIResult.Success)
        verify {
            createFolderAsyncUseCase(CreateFolderAsyncUseCase.Params("Reports", OC_FOLDER))
        }
    }

    @Test
    fun pictureUploads_updatesWifiOnlyAndSchedulesWorker() {
        val savePictureUploadsConfigurationUseCase = mockk<SavePictureUploadsConfigurationUseCase>()
        val saveParams = slot<SavePictureUploadsConfigurationUseCase.Params>()
        every { savePictureUploadsConfigurationUseCase(capture(saveParams)) } returns UseCaseResult.Success(Unit)
        val getPictureUploadsConfigurationStreamUseCase = mockk<GetPictureUploadsConfigurationStreamUseCase>()
        every { getPictureUploadsConfigurationStreamUseCase(Unit) } returns MutableStateFlow(
            OC_BACKUP.copy(
                accountName = OC_ACCOUNT_NAME,
                name = FolderBackUpConfiguration.pictureUploadsName,
                wifiOnly = true,
                chargingOnly = true,
            )
        )
        val workManagerProvider = mockk<WorkManagerProvider>(relaxed = true)
        val getSpaceByIdForAccountUseCase = mockk<GetSpaceByIdForAccountUseCase>()
        every { getSpaceByIdForAccountUseCase(any()) } returns OC_SPACE_PERSONAL

        val viewModel = SettingsPictureUploadsViewModel(
            accountProvider = mockk(relaxed = true),
            savePictureUploadsConfigurationUseCase = savePictureUploadsConfigurationUseCase,
            getPictureUploadsConfigurationStreamUseCase = getPictureUploadsConfigurationStreamUseCase,
            resetPictureUploadsUseCase = mockk(relaxed = true),
            getPersonalSpaceForAccountUseCase = mockk(relaxed = true),
            getSpaceByIdForAccountUseCase = getSpaceByIdForAccountUseCase,
            workManagerProvider = workManagerProvider,
            coroutinesDispatcherProvider = coroutineDispatcherProvider,
            contextProvider = contextProvider,
        )
        testCoroutineDispatcher.scheduler.advanceUntilIdle()

        viewModel.useWifiOnly(false)
        testCoroutineDispatcher.scheduler.advanceUntilIdle()
        viewModel.schedulePictureUploads()

        val savedConfiguration = saveParams.captured.pictureUploadsConfiguration
        assertFalse(savedConfiguration.wifiOnly)
        assertTrue(savedConfiguration.chargingOnly)
        assertEquals(FolderBackUpConfiguration.pictureUploadsName, savedConfiguration.name)
        verify { workManagerProvider.enqueueAutomaticUploadsWorker() }
    }

    @Test
    fun videoUploads_enablesSelectedAccountInPersonalSpace() {
        val saveVideoUploadsConfigurationUseCase = mockk<SaveVideoUploadsConfigurationUseCase>()
        val saveParams = slot<SaveVideoUploadsConfigurationUseCase.Params>()
        every { saveVideoUploadsConfigurationUseCase(capture(saveParams)) } returns UseCaseResult.Success(Unit)
        val getVideoUploadsConfigurationStreamUseCase = mockk<GetVideoUploadsConfigurationStreamUseCase>()
        every { getVideoUploadsConfigurationStreamUseCase(Unit) } returns flowOf(null)
        val getPersonalSpaceForAccountUseCase = mockk<GetPersonalSpaceForAccountUseCase>()
        every { getPersonalSpaceForAccountUseCase(any()) } returns OC_SPACE_PERSONAL

        val viewModel = SettingsVideoUploadsViewModel(
            accountProvider = mockk(relaxed = true),
            saveVideoUploadsConfigurationUseCase = saveVideoUploadsConfigurationUseCase,
            getVideoUploadsConfigurationStreamUseCase = getVideoUploadsConfigurationStreamUseCase,
            resetVideoUploadsUseCase = mockk(relaxed = true),
            getPersonalSpaceForAccountUseCase = getPersonalSpaceForAccountUseCase,
            getSpaceByIdForAccountUseCase = mockk(relaxed = true),
            workManagerProvider = mockk(relaxed = true),
            coroutinesDispatcherProvider = coroutineDispatcherProvider,
            contextProvider = contextProvider,
        )

        viewModel.enableVideoUploads(OC_ACCOUNT_NAME)
        testCoroutineDispatcher.scheduler.advanceUntilIdle()

        val savedConfiguration = saveParams.captured.videoUploadsConfiguration
        assertEquals(OC_ACCOUNT_NAME, savedConfiguration.accountName)
        assertEquals(FolderBackUpConfiguration.videoUploadsName, savedConfiguration.name)
        assertEquals(OC_SPACE_PERSONAL.id, savedConfiguration.spaceId)
    }

    @Test
    fun mainFileList_persistsLayoutSortAndNavigatesById() {
        val sharedPreferencesProvider = mockk<SharedPreferencesProvider>(relaxed = true)
        every { sharedPreferencesProvider.getBoolean(any(), any()) } returns false
        every { sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_TYPE, any()) } returns SortType.SORT_TYPE_BY_NAME.ordinal
        every { sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_ORDER, any()) } returns SortOrder.SORT_ORDER_ASCENDING.ordinal
        val getFileByIdUseCase = mockk<GetFileByIdUseCase>()
        every { getFileByIdUseCase(GetFileByIdUseCase.Params(OC_FOLDER_WITH_SPACE_ID.id!!)) } returns
            UseCaseResult.Success(OC_FOLDER_WITH_SPACE_ID)
        val synchronizeFolderUseCase = mockk<SynchronizeFolderUseCase>()
        every { synchronizeFolderUseCase(any()) } returns UseCaseResult.Success(Unit)
        val getAppRegistryWhichAllowCreationAsStreamUseCase = mockk<GetAppRegistryWhichAllowCreationAsStreamUseCase>()
        every { getAppRegistryWhichAllowCreationAsStreamUseCase(any()) } returns flowOf(emptyList())
        val getFolderContentAsStreamUseCase = mockk<GetFolderContentAsStreamUseCase>()
        every { getFolderContentAsStreamUseCase(any()) } returns flowOf(emptyList())
        val getSpaceWithSpecialsByIdForAccountUseCase = mockk<GetSpaceWithSpecialsByIdForAccountUseCase>()
        every { getSpaceWithSpecialsByIdForAccountUseCase(any()) } returns OC_SPACE_PERSONAL

        val viewModel = MainFileListViewModel(
            getFolderContentAsStreamUseCase = getFolderContentAsStreamUseCase,
            getSharedByLinkForAccountAsStreamUseCase = mockk(relaxed = true),
            getFilesAvailableOfflineFromAccountAsStreamUseCase = mockk(relaxed = true),
            getFileByIdUseCase = getFileByIdUseCase,
            getFileByRemotePathUseCase = mockk(relaxed = true),
            getSpaceWithSpecialsByIdForAccountUseCase = getSpaceWithSpecialsByIdForAccountUseCase,
            sortFilesWithSyncInfoUseCase = SortFilesWithSyncInfoUseCase(),
            synchronizeFolderUseCase = synchronizeFolderUseCase,
            getAppRegistryWhichAllowCreationAsStreamUseCase = getAppRegistryWhichAllowCreationAsStreamUseCase,
            getAppRegistryForMimeTypeAsStreamUseCase = mockk(relaxed = true),
            getUrlToOpenInWebUseCase = mockk(relaxed = true),
            filterFileMenuOptionsUseCase = mockk(relaxed = true),
            contextProvider = contextProvider,
            coroutinesDispatcherProvider = coroutineDispatcherProvider,
            sharedPreferencesProvider = sharedPreferencesProvider,
            initialFolderToDisplay = OC_ROOT_FOLDER,
            fileListOptionParam = FileListOption.ALL_FILES,
        )
        testCoroutineDispatcher.scheduler.advanceUntilIdle()

        viewModel.setGridModeAsPreferred()
        viewModel.updateSortTypeAndOrder(SortType.SORT_TYPE_BY_SIZE, SortOrder.SORT_ORDER_DESCENDING)
        viewModel.navigateToFolderId(OC_FOLDER_WITH_SPACE_ID.id!!)
        testCoroutineDispatcher.scheduler.advanceUntilIdle()

        assertEquals(OC_FOLDER_WITH_SPACE_ID, viewModel.getFile())
        verify { sharedPreferencesProvider.putBoolean("RECYCLER_VIEW_PREFERRED", true) }
        verify { sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_SIZE.ordinal) }
        verify { sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_DESCENDING.ordinal) }
        verify {
            synchronizeFolderUseCase(
                SynchronizeFolderUseCase.Params(
                    remotePath = OC_ROOT_FOLDER.remotePath,
                    accountName = OC_ROOT_FOLDER.owner,
                    spaceId = OC_ROOT_FOLDER.spaceId,
                    syncMode = SynchronizeFolderUseCase.SyncFolderMode.SYNC_CONTENTS,
                )
            )
        }
    }
}
