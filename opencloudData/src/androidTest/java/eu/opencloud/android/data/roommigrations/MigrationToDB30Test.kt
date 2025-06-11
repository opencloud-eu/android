/**
 *   openCloud Android client application
 *
 *   @author Abel García de Prada
 *   Copyright (C) 2020 ownCloud GmbH.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package eu.opencloud.android.data.roommigrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.filters.SmallTest
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.CAPABILITIES_ACCOUNT_NAME
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.CAPABILITIES_CORE_POLLINTERVAL
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.CAPABILITIES_DAV_CHUNKING_VERSION
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_DAYS
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.LEGACY_CAPABILITIES_VERSION_MAYOR
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MICRO
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MINOR
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
import eu.opencloud.android.testutil.OC_CAPABILITY
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test the migration from database to version 30.
 */
@SmallTest
class MigrationToDB30Test : MigrationTest() {

    @Test
    fun migrationFrom27To30_containsCorrectData() {
        performMigrationTest(
            previousVersion = DB_VERSION_27,
            currentVersion = DB_VERSION_30,
            insertData = { database -> insertDataToTest(database) },
            validateMigration = { database -> validateMigrationTo30(database) },
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
            )
        )
    }

    @Test
    fun migrationFrom28To30_containsCorrectData() {
        performMigrationTest(
            previousVersion = DB_VERSION_28,
            currentVersion = DB_VERSION_30,
            insertData = { database -> insertDataToTest(database) },
            validateMigration = { database -> validateMigrationTo30(database) },
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
            )
        )
    }

    @Test
    fun migrationFrom29To30_containsCorrectData() {
        performMigrationTest(
            previousVersion = DB_VERSION_29,
            currentVersion = DB_VERSION_30,
            insertData = { database -> insertDataToTest(database) },
            validateMigration = { database -> validateMigrationTo30(database) },
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
            )
        )
    }

    @Test
    fun startInVersion30_containsCorrectData() {
        performMigrationTest(
            previousVersion = DB_VERSION_30,
            currentVersion = DB_VERSION_30,
            insertData = { database -> insertDataToTest(database, true) },
            validateMigration = { },
            listOfMigrations = arrayOf()
        )
    }

    private fun insertDataToTest(database: SupportSQLiteDatabase, addNewField: Boolean = false) {
        database.run {
            insert(
                CAPABILITIES_TABLE_NAME,
                SQLiteDatabase.CONFLICT_NONE,
                if (addNewField) cvWithDefaultValues else MigrationToDB28Test.cvWithDefaultValues
            )
            close()
        }
    }

    private fun validateMigrationTo30(database: SupportSQLiteDatabase) {
        val capabilitiesCount = getCount(database, CAPABILITIES_TABLE_NAME)
        assertEquals(1, capabilitiesCount)
        database.close()
    }

    companion object {
        val cvWithDefaultValues = ContentValues().apply {
            put(CAPABILITIES_ACCOUNT_NAME, OC_CAPABILITY.accountName)
            put(LEGACY_CAPABILITIES_VERSION_MAYOR, OC_CAPABILITY.versionMajor)
            put(CAPABILITIES_VERSION_MINOR, OC_CAPABILITY.versionMinor)
            put(CAPABILITIES_VERSION_MICRO, OC_CAPABILITY.versionMicro)
            put(CAPABILITIES_CORE_POLLINTERVAL, OC_CAPABILITY.corePollInterval)
            put(CAPABILITIES_DAV_CHUNKING_VERSION, OC_CAPABILITY.davChunkingVersion)
            put(CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_DAYS, OC_CAPABILITY.filesSharingPublicExpireDateDays)
        }
    }
}
