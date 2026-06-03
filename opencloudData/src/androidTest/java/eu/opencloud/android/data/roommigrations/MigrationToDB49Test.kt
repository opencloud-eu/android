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

package eu.opencloud.android.data.roommigrations

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.filters.SmallTest
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.FILES_TABLE_NAME
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.FILE_REMOTE_ETAG
import eu.opencloud.android.data.migrations.MIGRATION_48_49
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@SmallTest
class MigrationToDB49Test : MigrationTest() {

    @Test
    fun migrationFrom48to49_preservesFilesAndAddsRemoteEtag() {
        performMigrationTest(
            previousVersion = 48,
            currentVersion = 49,
            insertData = { database -> insertFileToTest(database) },
            validateMigration = { database -> validateMigrationTo49(database) },
            listOfMigrations = arrayOf(MIGRATION_48_49)
        )
    }

    private fun insertFileToTest(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO `$FILES_TABLE_NAME`" +
                "(" +
                "owner, " +
                "remotePath, " +
                "length, " +
                "modificationTimestamp, " +
                "mimeType, " +
                "needsToUpdateThumbnail, " +
                "sharedByLink" +
                ")" +
                " VALUES " +
                "(?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                "user@example.com",
                "/Documents/test.txt",
                1024,
                1_700_000_000,
                "text/plain",
                0,
                0
            )
        )
    }

    private fun validateMigrationTo49(database: SupportSQLiteDatabase) {
        val cursor = database.query("SELECT * FROM `$FILES_TABLE_NAME`")
        assertTrue(cursor.moveToFirst())

        val remoteEtagIndex = cursor.getColumnIndex(FILE_REMOTE_ETAG)
        assertTrue(remoteEtagIndex != -1)

        assertEquals("user@example.com", cursor.getString(cursor.getColumnIndex("owner")))
        assertEquals("/Documents/test.txt", cursor.getString(cursor.getColumnIndex("remotePath")))
        assertTrue(cursor.isNull(remoteEtagIndex))

        cursor.close()
        database.close()
    }
}
