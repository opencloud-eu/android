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
package eu.opencloud.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.opencloud.android.data.ProviderMeta

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.run {
            execSQL(
                "CREATE TABLE IF NOT EXISTS `${ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME}2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT" +
                        " NOT NULL, `account` TEXT, `version_mayor` INTEGER NOT NULL, `version_minor` INTEGER NOT NULL, `version_micro` INTEGER" +
                        " NOT NULL, `version_string` TEXT, `version_edition` TEXT, `core_pollinterval` INTEGER NOT NULL, `dav_chunking_version`" +
                        " TEXT NOT NULL DEFAULT '',`sharing_api_enabled` INTEGER NOT NULL DEFAULT -1, `sharing_public_enabled` INTEGER NOT NULL" +
                        " DEFAULT -1, `sharing_public_password_enforced` INTEGER NOT NULL DEFAULT -1, `sharing_public_password_enforced_read_only`" +
                        " INTEGER NOT NULL DEFAULT -1, `sharing_public_password_enforced_read_write` INTEGER NOT NULL DEFAULT -1," +
                        " `sharing_public_password_enforced_public_only` INTEGER NOT NULL DEFAULT -1, `sharing_public_expire_date_enabled` INTEGER" +
                        " NOT NULL DEFAULT -1, `sharing_public_expire_date_days` INTEGER NOT NULL, `sharing_public_expire_date_enforced` INTEGER" +
                        " NOT NULL DEFAULT -1, `sharing_public_upload` INTEGER NOT NULL DEFAULT -1, `sharing_public_multiple` INTEGER NOT NULL" +
                        " DEFAULT -1, `supports_upload_only` INTEGER NOT NULL DEFAULT -1, `sharing_resharing` INTEGER NOT NULL DEFAULT -1," +
                        " `sharing_federation_outgoing` INTEGER NOT NULL DEFAULT -1, `sharing_federation_incoming` INTEGER NOT NULL DEFAULT -1," +
                        " `files_bigfilechunking` INTEGER NOT NULL DEFAULT -1, `files_undelete` INTEGER NOT NULL DEFAULT -1, `files_versioning`" +
                        " INTEGER NOT NULL DEFAULT -1)"
            )
            execSQL(
                "INSERT INTO `${ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME}2` SELECT id, account, version_mayor, version_minor," +
                        " version_micro, version_string, version_edition, core_pollinterval, '', IFNULL(sharing_api_enabled, -1)," +
                        " IFNULL(sharing_public_enabled, -1), IFNULL(sharing_public_password_enforced, -1)," +
                        "IFNULL(sharing_public_password_enforced_read_only, -1),IFNULL(sharing_public_password_enforced_read_write, -1)," +
                        "IFNULL(sharing_public_password_enforced_public_only, -1),IFNULL(sharing_public_expire_date_enabled, -1)," +
                        "IFNULL(sharing_public_expire_date_days, 0),IFNULL(sharing_public_expire_date_enforced, -1)," +
                        " IFNULL(sharing_public_upload, -1),IFNULL(sharing_public_multiple, -1), IFNULL(supports_upload_only, -1)," +
                        " IFNULL(sharing_resharing, -1),IFNULL(sharing_federation_outgoing, -1),IFNULL(sharing_federation_incoming, -1)," +
                        "IFNULL(files_bigfilechunking, -1),IFNULL(files_undelete, -1),IFNULL(files_versioning, -1)  " +
                        "FROM ${ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME}"
            )
            execSQL("DROP TABLE ${ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME}")
            execSQL("ALTER TABLE ${ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME}2 RENAME TO" +
                    " ${ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME}")

        }
    }
}
