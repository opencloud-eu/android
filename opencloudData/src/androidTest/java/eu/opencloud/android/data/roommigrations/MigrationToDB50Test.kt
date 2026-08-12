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
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.EXPORT_JOBS_TABLE_NAME
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.FILES_TABLE_NAME
import eu.opencloud.android.data.migrations.MIGRATION_49_50
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@SmallTest
class MigrationToDB50Test : MigrationTest() {

    @Test
    fun migrationFrom49to50_preservesFilesAndAddsExportJobs() {
        performMigrationTest(
            previousVersion = 49,
            currentVersion = 50,
            insertData = { database -> insertFileToTest(database) },
            validateMigration = { database -> validateMigrationTo50(database) },
            listOfMigrations = arrayOf(MIGRATION_49_50)
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

    private fun validateMigrationTo50(database: SupportSQLiteDatabase) {
        database.query("SELECT * FROM `$FILES_TABLE_NAME`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("user@example.com", cursor.getString(cursor.getColumnIndex("owner")))
            assertEquals("/Documents/test.txt", cursor.getString(cursor.getColumnIndex("remotePath")))
        }

        database.execSQL(
            "INSERT INTO `$EXPORT_JOBS_TABLE_NAME`" +
                "(" +
                "accountName, " +
                "targetFolderTreeUri, " +
                "fileIds, " +
                "attemptCount, " +
                "processedCount, " +
                "failedCount" +
                ")" +
                " VALUES " +
                "(?, ?, ?, ?, ?, ?)",
            arrayOf(
                "user@example.com",
                "content://provider/tree/export",
                "1,2,3",
                0,
                0,
                0
            )
        )

        database.query("SELECT * FROM `$EXPORT_JOBS_TABLE_NAME`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("user@example.com", cursor.getString(cursor.getColumnIndex("accountName")))
            assertEquals("content://provider/tree/export", cursor.getString(cursor.getColumnIndex("targetFolderTreeUri")))
            assertEquals("1,2,3", cursor.getString(cursor.getColumnIndex("fileIds")))
            assertTrue(cursor.getLong(cursor.getColumnIndex("id")) > 0)
        }

        database.close()
    }
}
