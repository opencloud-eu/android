/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

package eu.opencloud.android.presentation.transfers

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.spaces.model.OCSpace
import eu.opencloud.android.domain.spaces.usecases.GetSpacesFromEveryAccountUseCaseAsStream
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.domain.transfers.usecases.ClearSuccessfulTransfersUseCase
import eu.opencloud.android.domain.transfers.usecases.GetAllTransfersAsStreamUseCase
import eu.opencloud.android.providers.CoroutinesDispatcherProvider
import eu.opencloud.android.providers.WorkManagerProvider
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransfersViewModel(
    private val uploadFilesFromContentUriUseCase: UploadFilesFromContentUriUseCase,
    private val uploadFilesFromSystemUseCase: UploadFilesFromSystemUseCase,
    private val cancelUploadUseCase: CancelUploadUseCase,
    private val retryUploadFromSystemUseCase: RetryUploadFromSystemUseCase,
    private val retryUploadFromContentUriUseCase: RetryUploadFromContentUriUseCase,
    private val retryFailedUploadsForAccountUseCase: RetryFailedUploadsForAccountUseCase,
    private val clearFailedTransfersUseCase: ClearFailedTransfersUseCase,
    private val retryFailedUploadsUseCase: RetryFailedUploadsUseCase,
    private val clearSuccessfulTransfersUseCase: ClearSuccessfulTransfersUseCase,
    getAllTransfersAsStreamUseCase: GetAllTransfersAsStreamUseCase,
    private val cancelDownloadForFileUseCase: CancelDownloadForFileUseCase,
    private val cancelUploadForFileUseCase: CancelUploadForFileUseCase,
    private val cancelUploadsRecursivelyUseCase: CancelUploadsRecursivelyUseCase,
    private val cancelDownloadsRecursivelyUseCase: CancelDownloadsRecursivelyUseCase,
    getSpacesFromEveryAccountUseCaseAsStream: GetSpacesFromEveryAccountUseCaseAsStream,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    workManagerProvider: WorkManagerProvider,
) : ViewModel() {
    private val _workInfosListLiveData = MediatorLiveData<List<WorkInfo>>()
    val workInfosListLiveData: LiveData<List<WorkInfo>>
        get() = _workInfosListLiveData

    val transfersWithSpaceStateFlow: StateFlow<List<Pair<OCTransfer, OCSpace?>>> = combine(
        getAllTransfersAsStreamUseCase(Unit),
        getSpacesFromEveryAccountUseCaseAsStream(Unit)
    ) { transfers: List<OCTransfer>, spaces: List<OCSpace> ->
        transfers.map { transfer ->
            val spaceForTransfer = spaces.firstOrNull { space -> transfer.spaceId == space.id && transfer.accountName == space.accountName }
            Pair(transfer, spaceForTransfer)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private var workInfosLiveData = workManagerProvider.getRunningUploadsWorkInfosLiveData()

    init {
        _workInfosListLiveData.addSource(workInfosLiveData) { workInfos ->
            _workInfosListLiveData.postValue(workInfos)
        }
    }

    fun uploadFilesFromContentUri(
        accountName: String,
        listOfContentUris: List<Uri>,
        uploadFolderPath: String,
        spaceId: String?
    ) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            uploadFilesFromContentUriUseCase(
                UploadFilesFromContentUriUseCase.Params(
                    accountName = accountName,
                    listOfContentUris = listOfContentUris,
                    uploadFolderPath = uploadFolderPath,
                    spaceId = spaceId,
                )
            )
        }
    }

    fun uploadFilesFromSystem(
        accountName: String,
        listOfLocalPaths: List<String>,
        uploadFolderPath: String,
        spaceId: String?,
    ) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            uploadFilesFromSystemUseCase(
                UploadFilesFromSystemUseCase.Params(
                    accountName = accountName,
                    listOfLocalPaths = listOfLocalPaths,
                    uploadFolderPath = uploadFolderPath,
                    spaceId = spaceId,
                )
            )
        }
    }

    fun cancelUpload(upload: OCTransfer) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            cancelUploadUseCase(
                CancelUploadUseCase.Params(upload = upload)
            )
        }
    }

    fun cancelTransfersForFile(ocFile: OCFile) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            cancelUploadForFileUseCase(CancelUploadForFileUseCase.Params(ocFile))
            cancelDownloadForFileUseCase(CancelDownloadForFileUseCase.Params(ocFile))
        }
    }

    fun cancelTransfersRecursively(ocFiles: List<OCFile>, accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            cancelDownloadsRecursivelyUseCase(CancelDownloadsRecursivelyUseCase.Params(ocFiles, accountName))
        }
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            cancelUploadsRecursivelyUseCase(CancelUploadsRecursivelyUseCase.Params(ocFiles, accountName))
        }
    }

    fun retryUploadFromSystem(id: Long) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            retryUploadFromSystemUseCase(
                RetryUploadFromSystemUseCase.Params(uploadIdInStorageManager = id)
            )
        }
    }

    fun retryUploadFromContentUri(id: Long) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            retryUploadFromContentUriUseCase(
                RetryUploadFromContentUriUseCase.Params(uploadIdInStorageManager = id)
            )
        }
    }

    fun retryUploadsForAccount(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            retryFailedUploadsForAccountUseCase(RetryFailedUploadsForAccountUseCase.Params(accountName))
        }
    }

    fun clearFailedTransfers() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            clearFailedTransfersUseCase(Unit)
        }
    }

    fun retryFailedTransfers() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            retryFailedUploadsUseCase(Unit)
        }
    }

    fun clearSuccessfulTransfers() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            clearSuccessfulTransfersUseCase(Unit)
        }
    }
}
