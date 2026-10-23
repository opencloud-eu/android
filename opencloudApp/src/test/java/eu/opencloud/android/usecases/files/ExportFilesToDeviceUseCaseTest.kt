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

package eu.opencloud.android.usecases.files

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.ListenableFuture
import eu.opencloud.android.domain.exportjobs.ExportJobRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ExportFilesToDeviceUseCaseTest {

    private val workManager: WorkManager = mockk()
    private val exportJobRepository: ExportJobRepository = mockk(relaxUnitFun = true)
    private val useCase = ExportFilesToDeviceUseCase(workManager, exportJobRepository)

    @Before
    fun setUp() {
        // No export of this account has work, which is the normal state of a selection that is
        // still waiting for the folder picker.
        val noExportWork: ListenableFuture<List<WorkInfo>> = mockk()
        every { noExportWork.get() } returns emptyList()
        every { workManager.getWorkInfosByTag(any()) } returns noExportWork
    }

    @After
    fun tearDown() {
        useCase.forgetLiveJobs()
    }

    @Test
    fun `prepare export - ok - a selection waiting for the picker is not collected`() {
        every { exportJobRepository.saveExportJob(any()) } returns FIRST_JOB_ID andThen SECOND_JOB_ID
        every { exportJobRepository.getExportJobIdsForAccount(ACCOUNT_NAME) } returns emptyList() andThen listOf(FIRST_JOB_ID)

        val firstJobId = useCase.prepareExport(ACCOUNT_NAME, FILE_IDS)
        // A second instance, because the use case is injected per use.
        val secondJobId = ExportFilesToDeviceUseCase(workManager, exportJobRepository).prepareExport(ACCOUNT_NAME, FILE_IDS)

        assertEquals(FIRST_JOB_ID, firstJobId)
        assertEquals(SECOND_JOB_ID, secondJobId)
        verify(exactly = 0) { exportJobRepository.deleteExportJobById(FIRST_JOB_ID) }
    }

    @Test
    fun `prepare export - ok - a selection left behind by another process is collected`() {
        every { exportJobRepository.saveExportJob(any()) } returns SECOND_JOB_ID
        every { exportJobRepository.getExportJobIdsForAccount(ACCOUNT_NAME) } returns listOf(FIRST_JOB_ID)

        useCase.prepareExport(ACCOUNT_NAME, FILE_IDS)

        verify(exactly = 1) { exportJobRepository.deleteExportJobById(FIRST_JOB_ID) }
    }

    @Test
    fun `retain pending export - ok - a restored selection is not collected`() {
        every { exportJobRepository.saveExportJob(any()) } returns SECOND_JOB_ID
        every { exportJobRepository.getExportJobIdsForAccount(ACCOUNT_NAME) } returns listOf(FIRST_JOB_ID)

        useCase.retainPendingExport(FIRST_JOB_ID)
        useCase.prepareExport(ACCOUNT_NAME, FILE_IDS)

        verify(exactly = 0) { exportJobRepository.deleteExportJobById(FIRST_JOB_ID) }
    }

    @Test
    fun `prepare export - ko - an empty selection is not persisted`() {
        val jobId = useCase.prepareExport(ACCOUNT_NAME, emptyList())

        assertNull(jobId)
        verify(exactly = 0) { exportJobRepository.saveExportJob(any()) }
    }

    companion object {
        private const val ACCOUNT_NAME = "user@server"
        private const val FIRST_JOB_ID = 11L
        private const val SECOND_JOB_ID = 12L
        private val FILE_IDS = listOf(1L, 2L, 3L)
    }
}
