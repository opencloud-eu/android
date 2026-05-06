package eu.opencloud.android.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.FOLDER_BACKUP_TABLE_NAME
import eu.opencloud.android.domain.automaticuploads.model.UseSubfoldersBehaviour

val MIGRATION_49_50 = object : Migration(49, 50) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val cursor = database.query("PRAGMA table_info($FOLDER_BACKUP_TABLE_NAME)")
        var columnExists = false
        while (cursor.moveToNext()) {
            val columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            if (columnName == "useSubfoldersBehaviour") {
                columnExists = true
                break
            }
        }
        cursor.close()

        if (!columnExists) {
            database.execSQL(
                """
                ALTER TABLE $FOLDER_BACKUP_TABLE_NAME
                ADD COLUMN `useSubfoldersBehaviour` TEXT NOT NULL DEFAULT '${UseSubfoldersBehaviour.NONE.name}'
                """.trimIndent()
            )
        }
    }
}
