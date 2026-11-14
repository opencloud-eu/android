package eu.opencloud.android.domain.automaticuploads.usecases

import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.automaticuploads.FolderBackupRepository
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import kotlinx.coroutines.flow.Flow

class GetCustomUploadsConfigurationsStreamUseCase(
    private val folderBackupRepository: FolderBackupRepository
) : BaseUseCase<Flow<List<FolderBackUpConfiguration>>, Unit>() {

    override fun run(params: Unit): Flow<List<FolderBackUpConfiguration>> =
        folderBackupRepository.getCustomUploadsConfigurationsAsFlow()
}
