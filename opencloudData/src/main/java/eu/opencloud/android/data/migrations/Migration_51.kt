package eu.opencloud.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta

val MIGRATION_50_51 = object : Migration(50, 51) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS `${ProviderTableMeta.EXPORT_JOBS_TABLE_NAME}` (" +
                "`accountName` TEXT NOT NULL, " +
                "`targetFolderTreeUri` TEXT NOT NULL, " +
                "`fileIds` TEXT NOT NULL, " +
                "`attemptCount` INTEGER NOT NULL, " +
                "`processedCount` INTEGER NOT NULL, " +
                "`failedCount` INTEGER NOT NULL, " +
                "`completedItemKeys` TEXT NOT NULL, " +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )
    }
}
