package eu.opencloud.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta

val MIGRATION_48_49 = object : Migration(48, 49) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE ${ProviderTableMeta.FILES_TABLE_NAME} " +
                "ADD COLUMN `${ProviderTableMeta.FILE_REMOTE_ETAG}` TEXT DEFAULT NULL"
        )
    }
}
