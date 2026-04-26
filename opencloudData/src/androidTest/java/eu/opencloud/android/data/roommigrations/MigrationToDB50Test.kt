/*
 * openCloud Android client application
 *
 * Copyright (C) 2025 OpenCloud GmbH.
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
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.FOLDER_BACKUP_TABLE_NAME
import eu.opencloud.android.data.migrations.MIGRATION_27_28
import eu.opencloud.android.data.migrations.MIGRATION_28_29
import eu.opencloud.android.data.migrations.MIGRATION_29_30
import eu.opencloud.android.data.migrations.MIGRATION_30_31
import eu.opencloud.android.data.migrations.MIGRATION_31_32
import eu.opencloud.android.data.migrations.MIGRATION_32_33
import eu.opencloud.android.data.migrations.MIGRATION_33_34
import eu.opencloud.android.data.migrations.MIGRATION_34_35
import eu.opencloud.android.data.migrations.MIGRATION_35_36
import eu.opencloud.android.data.migrations.MIGRATION_37_38
import eu.opencloud.android.data.migrations.MIGRATION_41_42
import eu.opencloud.android.data.migrations.MIGRATION_42_43
import eu.opencloud.android.data.migrations.MIGRATION_47_48
import eu.opencloud.android.data.migrations.MIGRATION_48_49
import eu.opencloud.android.data.migrations.MIGRATION_49_50
import org.junit.Assert
import org.junit.Test

@SmallTest
class MigrationToDB50Test : MigrationTest() {

    @Test
    fun migrationFrom49to50_containsCorrectData() {
        performMigrationTest(
            previousVersion = 49,
            currentVersion = 50,
            insertData = { database -> insertDataToTest(database) },
            validateMigration = { database -> validateMigrationTo50(database) },
            listOfMigrations = arrayOf(
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30,
                MIGRATION_30_31,
                MIGRATION_31_32,
                MIGRATION_32_33,
                MIGRATION_33_34,
                MIGRATION_34_35,
                MIGRATION_35_36,
                MIGRATION_37_38,
                MIGRATION_41_42,
                MIGRATION_42_43,
                MIGRATION_47_48,
                MIGRATION_48_49,
                MIGRATION_49_50
            )
        )
    }

    private fun insertDataToTest(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO `$FOLDER_BACKUP_TABLE_NAME`" +
                    "(" +
                    "accountName, " +
                    "behavior, " +
                    "sourcePath, " +
                    "uploadPath, " +
                    "wifiOnly, " +
                    "chargingOnly, " +
                    "name, " +
                    "lastSyncTimestamp, " +
                    "spaceId" +
                    ")" +
                    " VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf(
                "user@example.com",
                "COPY",
                "/storage/emulated/0/DCIM/Camera",
                "/CameraUpload/",
                1,
                0,
                "picture_uploads",
                1234567890L,
                null
            )
        )
    }

    private fun validateMigrationTo50(database: SupportSQLiteDatabase) {
        val cursor = database.query("SELECT * FROM $FOLDER_BACKUP_TABLE_NAME")
        Assert.assertTrue(cursor.moveToFirst())

        // Check if new column exists
        val useSubfoldersBehaviourIndex = cursor.getColumnIndex("useSubfoldersBehaviour")
        Assert.assertTrue(useSubfoldersBehaviourIndex != -1)

        // Check if default value is correct
        Assert.assertEquals("NONE", cursor.getString(useSubfoldersBehaviourIndex))

        // Check if existing data is preserved
        val accountNameIndex = cursor.getColumnIndex("accountName")
        Assert.assertEquals("user@example.com", cursor.getString(accountNameIndex))

        cursor.close()
        database.close()
    }
}
