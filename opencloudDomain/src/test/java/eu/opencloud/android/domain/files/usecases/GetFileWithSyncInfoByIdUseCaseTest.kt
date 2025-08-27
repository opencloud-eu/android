package eu.opencloud.android.domain.files.usecases

import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.testutil.OC_FILE
import eu.opencloud.android.testutil.OC_FILE_WITH_SYNC_INFO_AND_SPACE
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Test

@ExperimentalCoroutinesApi
class GetFileWithSyncInfoByIdUseCaseTest {

    private val repository: FileRepository = spyk()
    private val useCase = GetFileWithSyncInfoByIdUseCase(repository)
    private val useCaseParams = GetFileWithSyncInfoByIdUseCase.Params(OC_FILE.id!!)

    @Test
    fun `getFileWithSyncInfoByIdAsFlow returns OCFileWithSyncInfo when no error`() = runTest {
        every { repository.getFileWithSyncInfoByIdAsFlow(useCaseParams.fileId) } returns flowOf(OC_FILE_WITH_SYNC_INFO_AND_SPACE)

        val useCaseResult = useCase(useCaseParams).first()

        Assert.assertEquals(OC_FILE_WITH_SYNC_INFO_AND_SPACE, useCaseResult)

        verify(exactly = 1) { repository.getFileWithSyncInfoByIdAsFlow(useCaseParams.fileId) }
    }

    @Test
    fun `getFileWithSyncInfoByIdAsFlow returns true when repository is null`() = runTest {
        val useCaseResult = useCase(useCaseParams)

        every { repository.getFileWithSyncInfoByIdAsFlow(useCaseParams.fileId) } returns flowOf(null)
        Assert.assertEquals(null, useCaseResult)

        verify(exactly = 1) { repository.getFileWithSyncInfoByIdAsFlow(useCaseParams.fileId) }
    }
}
