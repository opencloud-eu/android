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
