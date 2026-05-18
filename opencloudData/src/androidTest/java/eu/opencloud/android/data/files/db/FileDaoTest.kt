/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 ownCloud GmbH.
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

package eu.opencloud.android.data.files.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import eu.opencloud.android.data.OpencloudDatabase
import eu.opencloud.android.testutil.OC_FILE_ENTITY
import eu.opencloud.android.testutil.OC_FOLDER_ENTITY
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@MediumTest
class FileDaoTest {
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var fileDao: FileDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OpencloudDatabase.switchToInMemory(context)
        val db: OpencloudDatabase = OpencloudDatabase.getDatabase(context)
        fileDao = db.fileDao()
    }

    @Test
    fun copyKeepsSourceRemoteEtagForThumbnailCacheToken() {
        val finalRemotePath = "/Photos/copied-image.jpg"
        val sourceFile = OC_FILE_ENTITY.copy(
            remoteEtag = "source-remote-etag",
            etag = "source-etag",
        ).apply { id = OC_FILE_ENTITY.id }

        fileDao.copy(
            sourceFile = sourceFile,
            targetFolder = OC_FOLDER_ENTITY,
            finalRemotePath = finalRemotePath,
            remoteId = "copiedRemoteId",
            replace = false,
        )

        val copiedFile = fileDao.getFileByOwnerAndRemotePath(
            owner = OC_FOLDER_ENTITY.owner,
            remotePath = finalRemotePath,
            spaceId = OC_FOLDER_ENTITY.spaceId,
        )

        assertEquals("source-remote-etag", copiedFile?.remoteEtag)
    }

    @Test
    fun copyFallsBackToSourceEtagWhenSourceRemoteEtagIsBlank() {
        val finalRemotePath = "/Photos/copied-image-with-fallback.jpg"
        val sourceFile = OC_FILE_ENTITY.copy(
            remoteEtag = "",
            etag = "source-etag",
        ).apply { id = OC_FILE_ENTITY.id }

        fileDao.copy(
            sourceFile = sourceFile,
            targetFolder = OC_FOLDER_ENTITY,
            finalRemotePath = finalRemotePath,
            remoteId = "copiedRemoteIdWithFallback",
            replace = false,
        )

        val copiedFile = fileDao.getFileByOwnerAndRemotePath(
            owner = OC_FOLDER_ENTITY.owner,
            remotePath = finalRemotePath,
            spaceId = OC_FOLDER_ENTITY.spaceId,
        )

        assertEquals("source-etag", copiedFile?.remoteEtag)
    }
}
