/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 OpenCloud GmbH.
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
import eu.opencloud.android.domain.files.model.OCFile

/**
 * Lists the immediate server-side children of a folder without updating Room or local storage.
 */
class GetRemoteFolderContentUseCase(
    private val fileRepository: FileRepository,
) : BaseUseCaseWithResult<List<OCFile>, GetRemoteFolderContentUseCase.Params>() {

    override fun run(params: Params): List<OCFile> =
        fileRepository.getRemoteFolderContent(
            remotePath = params.remotePath,
            accountName = params.accountName,
            spaceId = params.spaceId,
        )

    data class Params(
        val remotePath: String,
        val accountName: String,
        val spaceId: String? = null,
    )
}
