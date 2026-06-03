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
package eu.opencloud.android.utils

import eu.opencloud.android.domain.files.model.OCFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationUtilsTest {

    @Test
    fun conflictNotificationIdUsesIntRangeFileId() {
        val file = conflictFile(id = 42L)

        assertEquals(42, NotificationUtils.getConflictNotificationId(file))
    }

    @Test
    fun conflictNotificationIdUsesStableFallbackForLargeFileId() {
        val file = conflictFile(id = Long.MAX_VALUE)

        val notificationId = NotificationUtils.getConflictNotificationId(file)

        assertNotEquals(Long.MAX_VALUE.toInt(), notificationId)
        assertEquals(notificationId, NotificationUtils.getConflictNotificationId(file.copy()))
    }

    @Test
    fun conflictNotificationIdUsesStableFallbackForMissingFileId() {
        val file = conflictFile(id = null)

        val notificationId = NotificationUtils.getConflictNotificationId(file)

        assertEquals(notificationId, NotificationUtils.getConflictNotificationId(file.copy()))
        assertNotEquals(
            notificationId,
            NotificationUtils.getConflictNotificationId(file.copy(remotePath = "/Documents/other.txt"))
        )
    }

    private fun conflictFile(id: Long?) = OCFile(
        id = id,
        owner = "user@example.org",
        remotePath = "/Documents/conflict.txt",
        remoteId = "remote-file-id",
        length = 1L,
        modificationTimestamp = 1L,
        mimeType = "text/plain",
        spaceId = "space-id",
    )
}
