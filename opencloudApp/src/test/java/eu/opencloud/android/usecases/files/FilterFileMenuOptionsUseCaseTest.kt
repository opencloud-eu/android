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

package eu.opencloud.android.usecases.files

import androidx.work.WorkManager
import eu.opencloud.android.domain.capabilities.CapabilityRepository
import eu.opencloud.android.domain.files.model.FileMenuOption
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.model.OCFileSyncInfo
import eu.opencloud.android.domain.spaces.usecases.GetSpaceWithSpecialsByIdForAccountUseCase
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_CAPABILITY
import eu.opencloud.android.testutil.OC_FILE
import eu.opencloud.android.testutil.OC_FOLDER
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterFileMenuOptionsUseCaseTest {

    private val capabilityRepository = mockk<CapabilityRepository> {
        every { getStoredCapabilities(any()) } returns OC_CAPABILITY
    }
    private val getSpaceWithSpecialsByIdForAccountUseCase = mockk<GetSpaceWithSpecialsByIdForAccountUseCase> {
        every { this@mockk(any()) } returns null
    }
    private val useCase = FilterFileMenuOptionsUseCase(mockk<WorkManager>(), capabilityRepository, getSpaceWithSpecialsByIdForAccountUseCase)

    private fun params(vararg files: OCFile, displaySelectAll: Boolean = false) =
        FilterFileMenuOptionsUseCase.Params(
            files = files.toList(),
            filesSyncInfo = files.map {
                OCFileSyncInfo(fileId = it.id!!, uploadWorkerUuid = null, downloadWorkerUuid = null, isSynchronizing = false)
            },
            accountName = OC_ACCOUNT_NAME,
            isAnyFileVideoPreviewing = false,
            displaySelectAll = displaySelectAll,
            displaySelectInverse = false,
            onlyAvailableOfflineFiles = false,
            onlySharedByLinkFiles = false,
            shareViaLinkAllowed = true,
            shareWithUsersAllowed = true,
            sendAllowed = true,
        )

    @Test
    fun `regular folder offers file actions`() {
        assertTrue(useCase(params(OC_FOLDER)).contains(FileMenuOption.COPY))
    }

    @Test
    fun `vault folder offers no file actions`() {
        assertEquals(emptyList<FileMenuOption>(), useCase(params(OC_FOLDER.copy(isVault = true))))
    }

    @Test
    fun `selection containing a vault only offers selection actions`() {
        assertEquals(
            listOf(FileMenuOption.SELECT_ALL),
            useCase(params(OC_FILE, OC_FOLDER.copy(isVault = true), displaySelectAll = true))
        )
    }
}
