package eu.opencloud.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta

val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `${ProviderTableMeta.EXPORT_JOBS_TABLE_NAME}` (" +
                "`accountName` TEXT NOT NULL, " +
                "`targetFolderTreeUri` TEXT NOT NULL, " +
                "`fileIds` TEXT NOT NULL, " +
                "`attemptCount` INTEGER NOT NULL, " +
                "`processedCount` INTEGER NOT NULL, " +
                "`failedCount` INTEGER NOT NULL, " +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )
    }
}
