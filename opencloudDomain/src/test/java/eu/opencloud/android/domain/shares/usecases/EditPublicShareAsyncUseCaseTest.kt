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
import eu.opencloud.android.domain.sharing.shares.usecases.EditPublicShareAsyncUseCase
import eu.opencloud.android.testutil.OC_SHARE
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditPublicShareAsyncUseCaseTest {
    private val repository: ShareRepository = spyk()
    private val useCase = EditPublicShareAsyncUseCase(repository)
    private val useCaseParams = EditPublicShareAsyncUseCase.Params(
        OC_SHARE.remoteId,
        "",
        "",
        OC_SHARE.expirationDate,
        OC_SHARE.permissions,
        OC_SHARE.accountOwner
    )

    @Test
    fun `edit public share - ok`() {
        every {
            repository.updatePublicShare(any(), any(), any(), any(), any(), any())
        } returns Unit

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isSuccess)
        assertEquals(Unit, useCaseResult.getDataOrNull())

        verify(exactly = 1) {
            repository.updatePublicShare(
                remoteId = OC_SHARE.remoteId,
                name = "",
                password = "",
                expirationDateInMillis = OC_SHARE.expirationDate,
                permissions = OC_SHARE.permissions,
                accountName = OC_SHARE.accountOwner
            )
        }
    }

    @Test
    fun `edit public share - ko`() {
        every {
            repository.updatePublicShare(any(), any(), any(), any(), any(), any())
        } throws UnauthorizedException()

        val useCaseResult = useCase(useCaseParams)

        assertTrue(useCaseResult.isError)
        assertTrue(useCaseResult.getThrowableOrNull() is UnauthorizedException)

        verify(exactly = 1) {
            repository.updatePublicShare(
                remoteId = OC_SHARE.remoteId,
                name = "",
                password = "",
                expirationDateInMillis = OC_SHARE.expirationDate,
                permissions = OC_SHARE.permissions,
                accountName = OC_SHARE.accountOwner
            )
        }
    }
}
