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
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.FILE_IS_VAULT
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.SPACES_TABLE_NAME
import eu.opencloud.android.data.spaces.db.SpacesEntity.Companion.SPACES_CONTENT_TYPE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto migration 50 -> 51 adds the vault markers of files and spaces.
 * See https://github.com/opencloud-eu/android/issues/228
 */
@SmallTest
class MigrationToDB51Test : MigrationTest() {

    @Test
    fun migrationFrom50to51_preservesDataAndAddsVaultColumns() {
        performMigrationTest(
            previousVersion = 50,
            currentVersion = 51,
            insertData = { database -> insertDataToTest(database) },
            validateMigration = { database -> validateMigrationTo51(database) },
            listOfMigrations = emptyArray()
        )
    }

    private fun insertDataToTest(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO `$SPACES_TABLE_NAME`" +
                "(account_name, drive_type, space_id, name, root_id, root_web_dav_url)" +
                " VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf(ACCOUNT_NAME, "project", SPACE_ID, "Space", SPACE_ID, "https://server.url/dav/spaces/$SPACE_ID")
        )
        database.execSQL(
            "INSERT INTO `$FILES_TABLE_NAME`" +
                "(owner, remotePath, length, modificationTimestamp, mimeType, needsToUpdateThumbnail, sharedByLink, spaceId)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(ACCOUNT_NAME, "/Secret.vault/", 0, 1_700_000_000, "DIR", 0, 0, SPACE_ID)
        )
    }

    private fun validateMigrationTo51(database: SupportSQLiteDatabase) {
        database.query("SELECT * FROM `$FILES_TABLE_NAME`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            val isVaultIndex = cursor.getColumnIndex(FILE_IS_VAULT)
            assertTrue(isVaultIndex != -1)
            assertEquals(0, cursor.getInt(isVaultIndex)) // Corrected by the next folder refresh
            assertEquals("/Secret.vault/", cursor.getString(cursor.getColumnIndex("remotePath")))
        }

        database.query("SELECT * FROM `$SPACES_TABLE_NAME`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            val contentTypeIndex = cursor.getColumnIndex(SPACES_CONTENT_TYPE)
            assertTrue(contentTypeIndex != -1)
            assertTrue(cursor.isNull(contentTypeIndex)) // Corrected by the next spaces refresh
            assertEquals(SPACE_ID, cursor.getString(cursor.getColumnIndex("space_id")))
        }

        database.close()
    }

    companion object {
        private const val ACCOUNT_NAME = "user@example.com"
        private const val SPACE_ID = "8871f4f3-fc6f-4a66-8bed-62f175f76f38\$0aa0e03c-ec36-498c-bb9f-1234566789"
    }
}
