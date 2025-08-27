/**
 * openCloud Android client application
 *
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

package eu.opencloud.android.usecases.files

import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.domain.BaseUseCaseWithResult
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.domain.files.usecases.RemoveFileUseCase
import eu.opencloud.android.presentation.settings.advanced.PREFERENCE_REMOVE_LOCAL_FILES
import eu.opencloud.android.presentation.settings.advanced.RemoveLocalFiles

class RemoveLocallyFilesWithLastUsageOlderThanGivenTimeUseCase(
    private val fileRepository: FileRepository,
    private val removeFileUseCase: RemoveFileUseCase,
    private val preferencesProvider: SharedPreferencesProvider,
) : BaseUseCaseWithResult<Unit, RemoveLocallyFilesWithLastUsageOlderThanGivenTimeUseCase.Params>() {
    override fun run(params: Params) {
        val timeSelected =
            RemoveLocalFiles.valueOf(preferencesProvider.getString(PREFERENCE_REMOVE_LOCAL_FILES, RemoveLocalFiles.NEVER.name)!!).toMilliseconds()
        val listOfFilesToDelete = fileRepository.getFilesWithLastUsageOlderThanGivenTime(timeSelected)
        if (listOfFilesToDelete.isNotEmpty()) {
            val listOfFilesToDeleteUpdated = listOfFilesToDelete.filter { file ->
                file.remoteId != params.idFilePreviewing
            }
            removeFileUseCase(RemoveFileUseCase.Params(listOfFilesToDeleteUpdated, true))
        }
    }

    data class Params(
        val idFilePreviewing: String?,
    )
}
