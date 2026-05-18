/**
 * openCloud Android client application
 *
 * @author OpenCloud Development Team
 *
 * Copyright (C) 2026 OpenCloud.
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

package eu.opencloud.android.data.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Data class representing the current progress of the [DownloadEverythingWorker] scan.
 */
data class DownloadProgress(
    val accountIndex: Int = 0,
    val spaceIndex: Int = 0,
    val processedFolderIds: Set<Long> = emptySet(),
    val totalFilesFound: Int = 0,
    val filesDownloaded: Int = 0,
    val filesAlreadyLocal: Int = 0,
    val filesSkipped: Int = 0,
    val foldersProcessed: Int = 0,
    val isRunning: Boolean = false,
    val lastUpdateTimestamp: Long = 0
)

/**
 * DataStore-backed persistence for [DownloadEverythingWorker] progress.
 *
 * Stores the scan state so that the worker can resume from where it left off
 * instead of restarting from scratch on every interruption (Doze, app killed,
 * screen lock, network drop, etc.).
 *
 * Progress older than [MAX_AGE_MS] is considered stale and will be discarded.
 */
class DownloadProgressDataStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)

    private val dataStore = context.dataStore

    /**
     * Persists the current progress to DataStore.
     */
    suspend fun saveProgress(progress: DownloadProgress) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_ACCOUNT_INDEX] = progress.accountIndex
                prefs[KEY_SPACE_INDEX] = progress.spaceIndex
                prefs[KEY_PROCESSED_FOLDER_IDS] = progress.processedFolderIds.joinToString(SEPARATOR)
                prefs[KEY_TOTAL_FILES_FOUND] = progress.totalFilesFound
                prefs[KEY_FILES_DOWNLOADED] = progress.filesDownloaded
                prefs[KEY_FILES_ALREADY_LOCAL] = progress.filesAlreadyLocal
                prefs[KEY_FILES_SKIPPED] = progress.filesSkipped
                prefs[KEY_FOLDERS_PROCESSED] = progress.foldersProcessed
                prefs[KEY_IS_RUNNING] = progress.isRunning
                prefs[KEY_LAST_UPDATE_TIMESTAMP] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save download progress")
        }
    }

    /**
     * Loads the last saved progress if it exists and is still valid.
     *
     * Returns `null` when:
     * - No progress was ever saved
     * - The saved progress is not marked as running
     * - The saved progress is older than [MAX_AGE_MS]
     */
    suspend fun loadProgress(): DownloadProgress? {
        return try {
            val prefs = dataStore.data.map { it }.firstOrNull() ?: return null

            val isRunning = prefs[KEY_IS_RUNNING] ?: false
            if (!isRunning) return null

            val timestamp = prefs[KEY_LAST_UPDATE_TIMESTAMP] ?: 0L
            val age = System.currentTimeMillis() - timestamp
            if (age > MAX_AGE_MS) {
                Timber.w("Download progress is ${age / 1000}s old (> ${MAX_AGE_MS / 1000}s), discarding")
                clearProgress()
                return null
            }

            val folderIdsString = prefs[KEY_PROCESSED_FOLDER_IDS] ?: ""
            val processedFolderIds = if (folderIdsString.isNotBlank()) {
                folderIdsString.split(SEPARATOR).mapNotNull { it.toLongOrNull() }.toSet()
            } else {
                emptySet()
            }

            DownloadProgress(
                accountIndex = prefs[KEY_ACCOUNT_INDEX] ?: 0,
                spaceIndex = prefs[KEY_SPACE_INDEX] ?: 0,
                processedFolderIds = processedFolderIds,
                totalFilesFound = prefs[KEY_TOTAL_FILES_FOUND] ?: 0,
                filesDownloaded = prefs[KEY_FILES_DOWNLOADED] ?: 0,
                filesAlreadyLocal = prefs[KEY_FILES_ALREADY_LOCAL] ?: 0,
                filesSkipped = prefs[KEY_FILES_SKIPPED] ?: 0,
                foldersProcessed = prefs[KEY_FOLDERS_PROCESSED] ?: 0,
                isRunning = true,
                lastUpdateTimestamp = timestamp
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load download progress")
            null
        }
    }

    /**
     * Clears all saved progress. Call this when the scan completes successfully.
     */
    suspend fun clearProgress() {
        try {
            dataStore.edit { prefs ->
                prefs.remove(KEY_ACCOUNT_INDEX)
                prefs.remove(KEY_SPACE_INDEX)
                prefs.remove(KEY_PROCESSED_FOLDER_IDS)
                prefs.remove(KEY_TOTAL_FILES_FOUND)
                prefs.remove(KEY_FILES_DOWNLOADED)
                prefs.remove(KEY_FILES_ALREADY_LOCAL)
                prefs.remove(KEY_FILES_SKIPPED)
                prefs.remove(KEY_FOLDERS_PROCESSED)
                prefs.remove(KEY_IS_RUNNING)
                prefs.remove(KEY_LAST_UPDATE_TIMESTAMP)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear download progress")
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "download_progress"
        private const val SEPARATOR = ","
        private const val MAX_AGE_MS = 24L * 60L * 60L * 1000L // 24 hours

        private val KEY_ACCOUNT_INDEX = intPreferencesKey("account_index")
        private val KEY_SPACE_INDEX = intPreferencesKey("space_index")
        private val KEY_PROCESSED_FOLDER_IDS = stringPreferencesKey("processed_folder_ids")
        private val KEY_TOTAL_FILES_FOUND = intPreferencesKey("total_files_found")
        private val KEY_FILES_DOWNLOADED = intPreferencesKey("files_downloaded")
        private val KEY_FILES_ALREADY_LOCAL = intPreferencesKey("files_already_local")
        private val KEY_FILES_SKIPPED = intPreferencesKey("files_skipped")
        private val KEY_FOLDERS_PROCESSED = intPreferencesKey("folders_processed")
        private val KEY_IS_RUNNING = booleanPreferencesKey("is_running")
        private val KEY_LAST_UPDATE_TIMESTAMP = longPreferencesKey("last_update_timestamp")
    }
}
