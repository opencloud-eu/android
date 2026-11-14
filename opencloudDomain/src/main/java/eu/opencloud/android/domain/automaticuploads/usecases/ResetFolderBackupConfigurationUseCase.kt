package eu.opencloud.android.domain.automaticuploads.usecases

import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.automaticuploads.FolderBackupRepository

class ResetFolderBackupConfigurationUseCase(
    private val folderBackupRepository: FolderBackupRepository
) : BaseUseCase<Unit, ResetFolderBackupConfigurationUseCase.Params>() {

    override fun run(params: Params) =
        folderBackupRepository.resetFolderBackupConfigurationByName(params.name)

    data class Params(
        val name: String
    )
}
