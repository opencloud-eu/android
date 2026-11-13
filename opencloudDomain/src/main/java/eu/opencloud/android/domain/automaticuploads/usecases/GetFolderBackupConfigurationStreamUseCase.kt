package eu.opencloud.android.domain.automaticuploads.usecases

import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.automaticuploads.FolderBackupRepository
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import kotlinx.coroutines.flow.Flow

class GetFolderBackupConfigurationStreamUseCase(
    private val folderBackupRepository: FolderBackupRepository
) : BaseUseCase<Flow<FolderBackUpConfiguration?>, GetFolderBackupConfigurationStreamUseCase.Params>() {

    override fun run(params: Params): Flow<FolderBackUpConfiguration?> =
        folderBackupRepository.getFolderBackupConfigurationByNameAsFlow(params.name)

    data class Params(
        val name: String
    )
}
