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

package eu.opencloud.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.FOLDER_BACKUP_TABLE_NAME
import eu.opencloud.android.domain.automaticuploads.model.UseSubfoldersBehaviour

val MIGRATION_43_44 = object : Migration(43, 44) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.run {
            execSQL(
                "ALTER TABLE $FOLDER_BACKUP_TABLE_NAME ADD COLUMN `useSubfoldersBehaviour` TEXT NOT NULL DEFAULT ${
                    UseSubfoldersBehaviour.NONE.name
                }"
            )
        }
    }
}
