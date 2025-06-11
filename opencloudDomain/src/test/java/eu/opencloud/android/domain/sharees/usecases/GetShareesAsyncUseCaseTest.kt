/**
 * openCloud Android client application
 *
 * @author David González Verdugo
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

package eu.opencloud.android.domain.sharees.usecases

import eu.opencloud.android.domain.exceptions.NoConnectionWithServerException
import eu.opencloud.android.domain.sharing.sharees.GetShareesAsyncUseCase
import eu.opencloud.android.domain.sharing.sharees.ShareeRepository
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class GetShareesAsyncUseCaseTest {

    private val repository: ShareeRepository = spyk()
    private val useCase = GetShareesAsyncUseCase(repository)
    private val useCaseParams = GetShareesAsyncUseCase.Params("user", 1, 5, OC_ACCOUNT_NAME)

    @Test
    fun `get sharees from server - ok`() {
        every { repository.getSharees(any(), any(), any(), any()) } returns arrayListOf()

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isSuccess)

        verify(exactly = 1) {
            repository.getSharees("user", 1, 5, OC_ACCOUNT_NAME)
        }
    }

    @Test
    fun `get sharees from server - ko`() {
        every { repository.getSharees(any(), any(), any(), any()) } throws NoConnectionWithServerException()

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isError)
        assertTrue(useCaseResult.getThrowableOrNull() is NoConnectionWithServerException)

        verify(exactly = 1) {
            repository.getSharees("user", 1, 5, OC_ACCOUNT_NAME)
        }
    }
}
