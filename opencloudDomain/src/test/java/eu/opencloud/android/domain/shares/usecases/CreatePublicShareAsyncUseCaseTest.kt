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

package eu.opencloud.android.domain.shares.usecases

import eu.opencloud.android.domain.exceptions.UnauthorizedException
import eu.opencloud.android.domain.sharing.shares.ShareRepository
import eu.opencloud.android.domain.sharing.shares.usecases.CreatePublicShareAsyncUseCase
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatePublicShareAsyncUseCaseTest {

    private val repository: ShareRepository = spyk()
    private val useCase = CreatePublicShareAsyncUseCase(repository)
    private val useCaseParams = CreatePublicShareAsyncUseCase.Params("", 1, "", "", 100,  "")

    @Test
    fun `create public share - ok`() {
        every {
            repository.insertPublicShare(any(), any(), any(), any(), any(), any())
        } returns Unit

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isSuccess)
        assertEquals(Unit, useCaseResult.getDataOrNull())

        verify(exactly = 1) { repository.insertPublicShare("", 1, "", "", 100,  "") }
    }

    @Test
    fun `create public share - ko`() {
        every {
            repository.insertPublicShare(any(), any(), any(), any(), any(), any())
        } throws UnauthorizedException()

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isError)
        assertTrue(useCaseResult.getThrowableOrNull() is UnauthorizedException)

        verify(exactly = 1) { repository.insertPublicShare("", 1, "", "", 100, "") }
    }

    @Test
    fun `create public share - ko - illegal argument exception`() {
        every {
            repository.insertPublicShare(any(), any(), any(), any(), any(), any())
        } throws IllegalArgumentException()

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isError)
        assertTrue(useCaseResult.getThrowableOrNull() is IllegalArgumentException)

        verify(exactly = 1) { repository.insertPublicShare("", 1, "", "", 100, "") }
    }
}
