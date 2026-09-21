/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 ownCloud GmbH.
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
package eu.opencloud.android.domain.files.usecases

import eu.opencloud.android.domain.BaseUseCaseWithResult
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.domain.files.model.OCFileWithSyncInfo

class SearchFilesUseCase(
    private val repository: FileRepository,
) : BaseUseCaseWithResult<List<OCFileWithSyncInfo>, SearchFilesUseCase.Params>() {

    override fun run(params: Params): List<OCFileWithSyncInfo> = repository.searchFiles(
        searchQuery = params.searchQuery,
        accountName = params.accountName,
        spaceId = params.spaceId,
    )

    data class Params(
        val searchQuery: String,
        val accountName: String,
        val spaceId: String? = null,
    )
}
