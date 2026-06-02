/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 openCloud GmbH.
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

package eu.opencloud.android.usecases.files

import androidx.work.WorkManager
import eu.opencloud.android.domain.availableoffline.model.AvailableOfflineStatus
import eu.opencloud.android.domain.capabilities.CapabilityRepository
import eu.opencloud.android.domain.files.model.FileMenuOption
import eu.opencloud.android.domain.files.model.OCFileSyncInfo
import eu.opencloud.android.domain.spaces.usecases.GetSpaceWithSpecialsByIdForAccountUseCase
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_CAPABILITY
import eu.opencloud.android.testutil.OC_FILE
import eu.opencloud.android.testutil.OC_FOLDER
import eu.opencloud.android.testutil.OC_SPACE_PERSONAL
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FilterFileMenuOptionsUseCaseTest {

    private val workManager = mockk<WorkManager>(relaxed = true)
    private val capabilityRepository = mockk<CapabilityRepository>()
    private val getSpaceWithSpecialsByIdForAccountUseCase = mockk<GetSpaceWithSpecialsByIdForAccountUseCase>()

    private lateinit var useCase: FilterFileMenuOptionsUseCase

    @Before
    fun setUp() {
        every { capabilityRepository.getStoredCapabilities(OC_ACCOUNT_NAME) } returns OC_CAPABILITY
        every { getSpaceWithSpecialsByIdForAccountUseCase(any()) } returns OC_SPACE_PERSONAL

        useCase = FilterFileMenuOptionsUseCase(
            workManager = workManager,
            capabilityRepository = capabilityRepository,
            getSpaceWithSpecialsByIdForAccountUseCase = getSpaceWithSpecialsByIdForAccountUseCase,
        )
    }

    @Test
    fun `empty file selection returns no menu options`() {
        val options = useCase(defaultParams(files = emptyList()))

        assertEquals(emptyList<FileMenuOption>(), options)
    }

    @Test
    fun `single remote file shows available file actions`() {
        val options = useCase(
            defaultParams(
                files = listOf(OC_FILE),
                filesSyncInfo = listOf(OCFileSyncInfo(fileId = OC_FILE.id!!)),
                displaySelectAll = true,
                displaySelectInverse = true,
            )
        )

        assertEquals(
            listOf(
                FileMenuOption.SELECT_ALL,
                FileMenuOption.SELECT_INVERSE,
                FileMenuOption.SHARE,
                FileMenuOption.OPEN_WITH,
                FileMenuOption.DOWNLOAD,
                FileMenuOption.RENAME,
                FileMenuOption.MOVE,
                FileMenuOption.COPY,
                FileMenuOption.SEND,
                FileMenuOption.SET_AV_OFFLINE,
                FileMenuOption.DETAILS,
                FileMenuOption.REMOVE,
            ),
            options
        )
    }

    @Test
    fun `synchronizing file shows cancel sync and hides blocked actions`() {
        val options = useCase(
            defaultParams(
                files = listOf(OC_FILE),
                filesSyncInfo = listOf(OCFileSyncInfo(fileId = OC_FILE.id!!, isSynchronizing = true)),
            )
        )

        assertEquals(
            listOf(
                FileMenuOption.SHARE,
                FileMenuOption.CANCEL_SYNC,
                FileMenuOption.DETAILS,
            ),
            options
        )
    }

    @Test
    fun `available offline folder shows sync and unset offline actions`() {
        val folder = OC_FOLDER.copy(availableOfflineStatus = AvailableOfflineStatus.AVAILABLE_OFFLINE)

        val options = useCase(
            defaultParams(
                files = listOf(folder),
                filesSyncInfo = listOf(OCFileSyncInfo(fileId = folder.id!!)),
            )
        )

        assertEquals(
            listOf(
                FileMenuOption.SHARE,
                FileMenuOption.SYNC,
                FileMenuOption.RENAME,
                FileMenuOption.MOVE,
                FileMenuOption.COPY,
                FileMenuOption.UNSET_AV_OFFLINE,
                FileMenuOption.REMOVE,
            ),
            options
        )
    }

    private fun defaultParams(
        files: List<eu.opencloud.android.domain.files.model.OCFile>,
        filesSyncInfo: List<OCFileSyncInfo> = emptyList(),
        displaySelectAll: Boolean = false,
        displaySelectInverse: Boolean = false,
    ) = FilterFileMenuOptionsUseCase.Params(
        files = files,
        filesSyncInfo = filesSyncInfo,
        accountName = OC_ACCOUNT_NAME,
        isAnyFileVideoPreviewing = false,
        displaySelectAll = displaySelectAll,
        displaySelectInverse = displaySelectInverse,
        onlyAvailableOfflineFiles = false,
        onlySharedByLinkFiles = false,
        shareViaLinkAllowed = true,
        shareWithUsersAllowed = true,
        sendAllowed = true,
    )
}
