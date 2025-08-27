package eu.opencloud.android.domain.files.usecases

import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.domain.files.model.OCFileWithSyncInfo
import kotlinx.coroutines.flow.Flow

class GetFileWithSyncInfoByIdUseCase(
    private val fileRepository: FileRepository
) : BaseUseCase<Flow<OCFileWithSyncInfo?>, GetFileWithSyncInfoByIdUseCase.Params>() {

    override fun run(params: Params): Flow<OCFileWithSyncInfo?> =
        fileRepository.getFileWithSyncInfoByIdAsFlow(params.fileId)

    data class Params(val fileId: Long)

}
