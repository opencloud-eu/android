/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Christian Schabesberger
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
import eu.opencloud.android.testutil.OC_FOLDER
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert
import org.junit.Test

class GetFileByRemotePathUseCaseTest {

    private val repository: FileRepository = spyk()
    private val useCase = GetFileByRemotePathUseCase(repository)
    private val useCaseParams = GetFileByRemotePathUseCase.Params("owner", "remotePath")

    @Test
    fun `get file by remote path - ok`() {
        every { repository.getFileByRemotePath(useCaseParams.remotePath, useCaseParams.owner) } returns OC_FOLDER

        val useCaseResult = useCase(useCaseParams)

        Assert.assertTrue(useCaseResult.isSuccess)

        Assert.assertEquals(OC_FOLDER, useCaseResult.getDataOrNull())

        verify(exactly = 1) { repository.getFileByRemotePath(useCaseParams.remotePath, useCaseParams.owner) }
    }

    @Test
    fun `get file by remote path - ok - null`() {
        every { repository.getFileByRemotePath(useCaseParams.remotePath, useCaseParams.owner) } returns null

        val useCaseResult = useCase(useCaseParams)

        Assert.assertTrue(useCaseResult.isSuccess)
        Assert.assertEquals(null, useCaseResult.getDataOrNull())

        verify(exactly = 1) { repository.getFileByRemotePath(useCaseParams.remotePath, useCaseParams.owner) }
    }

    @Test
    fun `get file by remote path - ko`() {
        every {
            repository.getFileByRemotePath(useCaseParams.remotePath, useCaseParams.owner)
        } throws UnauthorizedException()

        val useCaseResult = useCase(useCaseParams)

        Assert.assertTrue(useCaseResult.isError)
        Assert.assertTrue(useCaseResult.getThrowableOrNull() is UnauthorizedException)

        verify(exactly = 1) { repository.getFileByRemotePath(useCaseParams.remotePath, useCaseParams.owner) }
    }
}
