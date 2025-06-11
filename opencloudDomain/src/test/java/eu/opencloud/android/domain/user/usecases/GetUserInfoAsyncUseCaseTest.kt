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
package eu.opencloud.android.domain.user.usecases

import eu.opencloud.android.domain.exceptions.UnauthorizedException
import eu.opencloud.android.domain.user.UserRepository
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_USER_INFO
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetUserInfoAsyncUseCaseTest {

    private val repository: UserRepository = spyk()
    private val useCase = GetUserInfoAsyncUseCase(repository)
    private val useCaseParams = GetUserInfoAsyncUseCase.Params(OC_ACCOUNT_NAME)

    @Test
    fun `get user info - ok`() {
        every { repository.getUserInfo(OC_ACCOUNT_NAME) } returns OC_USER_INFO

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isSuccess)
        assertEquals(OC_USER_INFO, useCaseResult.getDataOrNull())

        verify(exactly = 1) { repository.getUserInfo(OC_ACCOUNT_NAME) }
    }

    @Test
    fun `get user info - ko`() {
        every { repository.getUserInfo(OC_ACCOUNT_NAME) } throws UnauthorizedException()

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isError)
        assertTrue(useCaseResult.getThrowableOrNull() is UnauthorizedException)

        verify(exactly = 1) { repository.getUserInfo(OC_ACCOUNT_NAME) }
    }
}
