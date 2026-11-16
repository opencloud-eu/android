/*
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
 *
 */

package eu.opencloud.android.usecases.synchronization

import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_FOLDER
import eu.opencloud.android.testutil.OC_PARENT_FOLDER_WITH_SPACE_ID
import eu.opencloud.android.usecases.synchronization.SynchronizeFolderUseCase.SyncFolderMode.REFRESH_FOLDER_RECURSIVELY
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

/**
 * Vault folders are encrypted by the OpenCloud Web vault app, so they must never be listed or synced.
 */
class SynchronizeFolderUseCaseTest {

    private val fileRepository = mockk<FileRepository>(relaxed = true)
    private val useCase = SynchronizeFolderUseCase(mockk(relaxed = true), fileRepository)

    private fun params(remotePath: String) = SynchronizeFolderUseCase.Params(
        remotePath = remotePath,
        accountName = OC_ACCOUNT_NAME,
        spaceId = PARENT.spaceId,
        syncMode = REFRESH_FOLDER_RECURSIVELY,
    )

    @Test
    fun `vault folder is not refreshed`() {
        every { fileRepository.getFileByRemotePath(VAULT.remotePath, OC_ACCOUNT_NAME, PARENT.spaceId) } returns VAULT

        useCase(params(VAULT.remotePath))

        verify(exactly = 0) { fileRepository.refreshFolder(any(), any(), any(), any()) }
    }

    @Test
    fun `recursive refresh does not descend into vault folders`() {
        every { fileRepository.getFileByRemotePath(PARENT.remotePath, OC_ACCOUNT_NAME, PARENT.spaceId) } returns PARENT
        every { fileRepository.refreshFolder(PARENT.remotePath, OC_ACCOUNT_NAME, PARENT.spaceId, false) } returns listOf(VAULT, REGULAR)
        every { fileRepository.refreshFolder(REGULAR.remotePath, OC_ACCOUNT_NAME, PARENT.spaceId, false) } returns emptyList()

        useCase(params(PARENT.remotePath))

        verify(exactly = 1) { fileRepository.refreshFolder(REGULAR.remotePath, OC_ACCOUNT_NAME, PARENT.spaceId, false) }
        verify(exactly = 0) { fileRepository.refreshFolder(VAULT.remotePath, any(), any(), any()) }
    }

    companion object {
        private val PARENT = OC_PARENT_FOLDER_WITH_SPACE_ID
        private val VAULT = OC_FOLDER.copy(remotePath = "/Folder/Secret.vault/", spaceId = PARENT.spaceId, isVault = true)
        private val REGULAR = OC_FOLDER.copy(remotePath = "/Folder/Regular/", spaceId = PARENT.spaceId)
    }
}
