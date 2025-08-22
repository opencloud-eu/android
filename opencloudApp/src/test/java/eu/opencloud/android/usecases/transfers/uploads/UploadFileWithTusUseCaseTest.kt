package eu.opencloud.android.usecases.transfers.uploads

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UploadFileWithTusUseCaseTest {

    @MockK(relaxed = true)
    lateinit var workManager: WorkManager

    private lateinit var useCase: UploadFileWithTusUseCase

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        useCase = UploadFileWithTusUseCase(workManager)
    }

    @Test
    fun `enqueue work with content uri sets tags correctly`() {
        val params = UploadFileWithTusUseCase.Params(
            accountName = "user@example.com",
            uploadPath = "/Photos/pic.jpg",
            uploadIdInStorageManager = 123L,
            contentUri = "content://com.example/file/1",
            localPath = null,
        )

        val requestSlot: CapturingSlot<OneTimeWorkRequest> = slot()
        every { workManager.enqueue(capture(requestSlot)) } returns io.mockk.mockk(relaxed = true)

        useCase(params)

        val req = requestSlot.captured
        assertTrue(req is OneTimeWorkRequest)
        assertTrue(req.tags.contains(params.accountName))
        assertTrue(req.tags.contains(params.uploadIdInStorageManager.toString()))

        verify(exactly = 1) { workManager.enqueue(any<OneTimeWorkRequest>()) }
    }

    @Test
    fun `enqueue work with local path sets tags correctly`() {
        val params = UploadFileWithTusUseCase.Params(
            accountName = "user2@example.com",
            uploadPath = "/Docs/file.pdf",
            uploadIdInStorageManager = 9876L,
            contentUri = null,
            localPath = "/local/file.pdf",
        )

        val requestSlot: CapturingSlot<OneTimeWorkRequest> = slot()
        every { workManager.enqueue(capture(requestSlot)) } returns io.mockk.mockk(relaxed = true)

        useCase(params)

        val req = requestSlot.captured
        assertTrue(req is OneTimeWorkRequest)
        assertTrue(req.tags.contains(params.accountName))
        assertTrue(req.tags.contains(params.uploadIdInStorageManager.toString()))

        verify(exactly = 1) { workManager.enqueue(any<OneTimeWorkRequest>()) }
    }
}
