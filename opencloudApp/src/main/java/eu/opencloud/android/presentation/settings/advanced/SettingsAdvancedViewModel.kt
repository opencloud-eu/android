/**
 * openCloud Android client application
 *
 * @author David Crespo Ríos
 * @author Aitor Ballesteros Pavón
 *
 * Copyright (C) 2024 ownCloud GmbH.
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

package eu.opencloud.android.presentation.settings.advanced

import androidx.lifecycle.ViewModel
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.presentation.settings.advanced.SettingsAdvancedFragment.Companion.PREF_SHOW_HIDDEN_FILES
import eu.opencloud.android.providers.WorkManagerProvider
import eu.opencloud.android.workers.RemoveLocallyFilesWithLastUsageOlderThanGivenTimeWorker.Companion.DELETE_FILES_OLDER_GIVEN_TIME_WORKER

class SettingsAdvancedViewModel(
    private val preferencesProvider: SharedPreferencesProvider,
    private val workManagerProvider: WorkManagerProvider,
) : ViewModel() {

    fun isHiddenFilesShown(): Boolean =
        preferencesProvider.getBoolean(PREF_SHOW_HIDDEN_FILES, false)

    fun setShowHiddenFiles(hide: Boolean) {
        preferencesProvider.putBoolean(PREF_SHOW_HIDDEN_FILES, hide)
    }

    fun scheduleDeleteLocalFiles(newValue: String) {
        workManagerProvider.cancelAllWorkByTag(DELETE_FILES_OLDER_GIVEN_TIME_WORKER)
        if (newValue != RemoveLocalFiles.NEVER.name) {
            workManagerProvider.enqueueRemoveLocallyFilesWithLastUsageOlderThanGivenTimeWorker()
        }
    }
}
