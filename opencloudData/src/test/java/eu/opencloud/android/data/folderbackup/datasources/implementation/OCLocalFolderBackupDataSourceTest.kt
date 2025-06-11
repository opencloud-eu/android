/**
 * openCloud Android client application
 *
 * @author Aitor Ballesteros Pavón
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

package eu.opencloud.android.data.folderbackup.datasources.implementation

import eu.opencloud.android.data.folderbackup.datasources.implementation.OCLocalFolderBackupDataSource.Companion.toModel
import eu.opencloud.android.data.folderbackup.db.FolderBackupDao
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.testutil.OC_BACKUP
import eu.opencloud.android.testutil.OC_BACKUP_ENTITY
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class OCLocalFolderBackupDataSourceTest {

    private lateinit var ocLocalFolderBackupDataSource: OCLocalFolderBackupDataSource
    private val folderBackupDao = mockk<FolderBackupDao>(relaxed = true)

    @Before
    fun setUp() {
        ocLocalFolderBackupDataSource = OCLocalFolderBackupDataSource(folderBackupDao)
    }

    @Test
    fun `getAutomaticUploadsConfiguration returns an AutomaticUploadsConfiguration when having valid configurations`() {
        every { folderBackupDao.getFolderBackUpConfigurationByName(FolderBackUpConfiguration.pictureUploadsName) } returns OC_BACKUP_ENTITY
        every { folderBackupDao.getFolderBackUpConfigurationByName(FolderBackUpConfiguration.videoUploadsName) } returns OC_BACKUP_ENTITY

        val resultCurrent = ocLocalFolderBackupDataSource.getAutomaticUploadsConfiguration()

        assertEquals(OC_BACKUP_ENTITY.toModel(), resultCurrent?.pictureUploadsConfiguration)
        assertEquals(OC_BACKUP_ENTITY.toModel(), resultCurrent?.videoUploadsConfiguration)

        verify(exactly = 1) {
            folderBackupDao.getFolderBackUpConfigurationByName(FolderBackUpConfiguration.pictureUploadsName)
            folderBackupDao.getFolderBackUpConfigurationByName(FolderBackUpConfiguration.videoUploadsName)
        }
    }

    @Test
    fun `getAutomaticUploadsConfiguration returns null when there are not configurations`() {
        every { folderBackupDao.getFolderBackUpConfigurationByName(FolderBackUpConfiguration.pictureUploadsName) } returns null
        every { folderBackupDao.getFolderBackUpConfigurationByName(FolderBackUpConfiguration.videoUploadsName) } returns null

        val resultCurrent = ocLocalFolderBackupDataSource.getAutomaticUploadsConfiguration()

        assertNull(resultCurrent)

        verify(exactly = 1) {
            folderBackupDao.getFolderBackUpConfigurationByName(FolderBackUpConfiguration.pictureUploadsName)
            folderBackupDao.getFolderBackUpConfigurationByName(FolderBackUpConfiguration.videoUploadsName)
        }
    }

    @Test
    fun `getFolderBackupConfigurationByNameAsFlow returns a Flow of AutomaticUploadsConfiguration when having valid configurations`() = runBlocking {
        every { folderBackupDao.getFolderBackUpConfigurationByNameAsFlow(FolderBackUpConfiguration.pictureUploadsName) } returns flowOf(
            OC_BACKUP_ENTITY
        )

        val resultCurrent = ocLocalFolderBackupDataSource.getFolderBackupConfigurationByNameAsFlow(FolderBackUpConfiguration.pictureUploadsName)

        val result = resultCurrent.first()
        assertEquals(OC_BACKUP_ENTITY.toModel(), result)

        verify(exactly = 1) {
            folderBackupDao.getFolderBackUpConfigurationByNameAsFlow(FolderBackUpConfiguration.pictureUploadsName)
        }
    }

    @Test
    fun `saveFolderBackupConfiguration saves valid configurations correctly`() {
        ocLocalFolderBackupDataSource.saveFolderBackupConfiguration(OC_BACKUP)

        verify(exactly = 1) {
            folderBackupDao.update(OC_BACKUP_ENTITY)
        }
    }

    @Test
    fun `resetFolderBackupConfigurationByName removes current folder backup configuration correctly`() {
        ocLocalFolderBackupDataSource.resetFolderBackupConfigurationByName(FolderBackUpConfiguration.pictureUploadsName)

        verify(exactly = 1) {
            folderBackupDao.delete(FolderBackUpConfiguration.pictureUploadsName)
        }
    }
}
