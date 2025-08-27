/**
 *   openCloud Android client application
 *
 *   @author David González Verdugo
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

package eu.opencloud.android.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import eu.opencloud.android.data.appregistry.db.AppRegistryDao
import eu.opencloud.android.data.appregistry.db.AppRegistryEntity
import eu.opencloud.android.data.capabilities.db.OCCapabilityDao
import eu.opencloud.android.data.capabilities.db.OCCapabilityEntity
import eu.opencloud.android.data.files.db.FileDao
import eu.opencloud.android.data.files.db.OCFileEntity
import eu.opencloud.android.data.files.db.OCFileSyncEntity
import eu.opencloud.android.data.folderbackup.db.FolderBackUpEntity
import eu.opencloud.android.data.folderbackup.db.FolderBackupDao
import eu.opencloud.android.data.migrations.AutoMigration39To40
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
import eu.opencloud.android.data.sharing.shares.db.OCShareDao
import eu.opencloud.android.data.sharing.shares.db.OCShareEntity
import eu.opencloud.android.data.spaces.db.SpaceSpecialEntity
import eu.opencloud.android.data.spaces.db.SpacesDao
import eu.opencloud.android.data.spaces.db.SpacesEntity
import eu.opencloud.android.data.transfers.db.OCTransferEntity
import eu.opencloud.android.data.transfers.db.TransferDao
import eu.opencloud.android.data.user.db.UserDao
import eu.opencloud.android.data.user.db.UserQuotaEntity

@Database(
    entities = [
        AppRegistryEntity::class,
        FolderBackUpEntity::class,
        OCCapabilityEntity::class,
        OCFileEntity::class,
        OCFileSyncEntity::class,
        OCShareEntity::class,
        OCTransferEntity::class,
        SpacesEntity::class,
        SpaceSpecialEntity::class,
        UserQuotaEntity::class,
    ],
    autoMigrations = [
        AutoMigration(from = 36, to = 37),
        AutoMigration(from = 38, to = 39),
        AutoMigration(from = 39, to = 40, spec = AutoMigration39To40::class),
        AutoMigration(from = 40, to = 41),
        AutoMigration(from = 43, to = 44),
        AutoMigration(from = 44, to = 45),
        AutoMigration(from = 45, to = 46),
        AutoMigration(from = 46, to = 47),
        AutoMigration(from = 47, to = 48),
    ],
    version = ProviderMeta.DB_VERSION,
    exportSchema = true
)
abstract class OpencloudDatabase : RoomDatabase() {
    abstract fun appRegistryDao(): AppRegistryDao
    abstract fun capabilityDao(): OCCapabilityDao
    abstract fun fileDao(): FileDao
    abstract fun folderBackUpDao(): FolderBackupDao
    abstract fun shareDao(): OCShareDao
    abstract fun spacesDao(): SpacesDao
    abstract fun transferDao(): TransferDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: OpencloudDatabase? = null

        fun getDatabase(
            context: Context
        ): OpencloudDatabase =
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OpencloudDatabase::class.java,
                    ProviderMeta.NEW_DB_NAME
                )
                    .addMigrations(
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
                        MIGRATION_42_43)
                    .build()
                INSTANCE = instance
                instance
            }

        @VisibleForTesting
        fun switchToInMemory(context: Context, vararg migrations: Migration) {
            INSTANCE = Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                OpencloudDatabase::class.java
            ).addMigrations(*migrations)
                .build()
        }
    }
}
