package eu.opencloud.android.domain.automaticuploads.usecases

import eu.opencloud.android.domain.BaseUseCaseWithResult
import eu.opencloud.android.domain.automaticuploads.FolderBackupRepository
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration

class SaveFolderBackupConfigurationUseCase(
    private val folderBackupRepository: FolderBackupRepository
) : BaseUseCaseWithResult<Unit, SaveFolderBackupConfigurationUseCase.Params>() {

    override fun run(params: Params) =
        folderBackupRepository.saveFolderBackupConfiguration(params.folderBackUpConfiguration)

    data class Params(
        val folderBackUpConfiguration: FolderBackUpConfiguration
    )
}
