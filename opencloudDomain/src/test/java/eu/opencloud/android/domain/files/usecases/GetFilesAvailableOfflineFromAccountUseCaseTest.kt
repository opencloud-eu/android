/*
 * openCloud Android client application
 *
 * Copyright (C) 2021 ownCloud GmbH.
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

import eu.opencloud.android.domain.availableoffline.usecases.GetFilesAvailableOfflineFromAccountUseCase
import eu.opencloud.android.domain.exceptions.UnauthorizedException
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.testutil.OC_AVAILABLE_OFFLINE_FILES
import eu.opencloud.android.testutil.OC_FILES_EMPTY
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert
import org.junit.Test

class GetFilesAvailableOfflineFromAccountUseCaseTest {

    private val repository: FileRepository = spyk()
    private val useCase = GetFilesAvailableOfflineFromAccountUseCase(repository)
    private val useCaseParams = GetFilesAvailableOfflineFromAccountUseCase.Params(owner = "owner")

    @Test
    fun `get files available offline - ok`() {
        every { repository.getFilesAvailableOfflineFromAccount(useCaseParams.owner) } returns OC_AVAILABLE_OFFLINE_FILES

        val useCaseResult = useCase(useCaseParams)

        Assert.assertTrue(useCaseResult.isSuccess)
        Assert.assertEquals(OC_AVAILABLE_OFFLINE_FILES, useCaseResult.getDataOrNull())

        verify(exactly = 1) { repository.getFilesAvailableOfflineFromAccount(useCaseParams.owner) }
    }

    @Test
    fun `get files available offline - ok - empty list`() {
        every { repository.getFilesAvailableOfflineFromAccount(useCaseParams.owner) } returns OC_FILES_EMPTY

        val useCaseResult = useCase(useCaseParams)

        Assert.assertTrue(useCaseResult.isSuccess)
        Assert.assertEquals(OC_FILES_EMPTY, useCaseResult.getDataOrNull())

        verify(exactly = 1) { repository.getFilesAvailableOfflineFromAccount(useCaseParams.owner) }
    }

    @Test
    fun `get files savailable offline - ko`() {
        every { repository.getFilesAvailableOfflineFromAccount(useCaseParams.owner) } throws UnauthorizedException()

        val useCaseResult = useCase(useCaseParams)

        Assert.assertTrue(useCaseResult.isError)
        Assert.assertTrue(useCaseResult.getThrowableOrNull() is UnauthorizedException)

        verify(exactly = 1) { repository.getFilesAvailableOfflineFromAccount(useCaseParams.owner) }
    }
}
