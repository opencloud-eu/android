/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 ownCloud GmbH.
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
package eu.opencloud.android.domain.files.usecases

import eu.opencloud.android.domain.exceptions.UnauthorizedException
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_FILE_WITH_SYNC_INFO
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilesUseCaseTest {

    private val repository: FileRepository = spyk()
    private val useCase = SearchFilesUseCase(repository)
    private val useCaseParams = SearchFilesUseCase.Params(
        searchQuery = "test",
        accountName = OC_ACCOUNT_NAME,
        spaceId = null,
    )

    @Test
    fun `search files - ok`() {
        every { repository.searchFiles(useCaseParams.searchQuery, useCaseParams.accountName, useCaseParams.spaceId) } returns listOf(
            OC_FILE_WITH_SYNC_INFO
        )

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isSuccess)
        assertEquals(listOf(OC_FILE_WITH_SYNC_INFO), useCaseResult.getDataOrNull())

        verify(exactly = 1) {
            repository.searchFiles(useCaseParams.searchQuery, useCaseParams.accountName, useCaseParams.spaceId)
        }
    }

    @Test
    fun `search files - ko`() {
        every {
            repository.searchFiles(useCaseParams.searchQuery, useCaseParams.accountName, useCaseParams.spaceId)
        } throws UnauthorizedException()

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isError)

        verify(exactly = 1) {
            repository.searchFiles(useCaseParams.searchQuery, useCaseParams.accountName, useCaseParams.spaceId)
        }
    }
}
