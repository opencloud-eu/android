/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2020 ownCloud GmbH.
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
import eu.opencloud.android.testutil.OC_FILE
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetFolderContentUseCaseTest {

    private val repository: FileRepository = spyk()
    private val useCase = GetFolderContentUseCase(repository)
    private val useCaseParams = GetFolderContentUseCase.Params(OC_FILE.parentId!!)

    @Test
    fun `get folder content - ok`() {
        every { repository.getFolderContent(useCaseParams.folderId) } returns listOf(OC_FILE)

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isSuccess)
        assertEquals(listOf(OC_FILE), useCaseResult.getDataOrNull())

        verify(exactly = 1) { repository.getFolderContent(useCaseParams.folderId) }
    }

    @Test
    fun `get folder content - ko`() {
        every { repository.getFolderContent(useCaseParams.folderId) } throws UnauthorizedException()

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isError)
        assertTrue(useCaseResult.getThrowableOrNull() is UnauthorizedException)

        verify(exactly = 1) { repository.getFolderContent(useCaseParams.folderId) }
    }
}
