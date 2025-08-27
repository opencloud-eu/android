/**
 * openCloud Android client application
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.domain.files.model.OCFile

/**
 * Returns the root folder for the shares space.
 *
 * returns the root folder from Shares jail
 */
class GetSharesRootFolderForAccount(
    private val fileRepository: FileRepository
) : BaseUseCase<OCFile?, GetSharesRootFolderForAccount.Params>() {

    override fun run(params: Params): OCFile? =
        fileRepository.getSharesRootFolderForAccount(owner = params.owner)

    data class Params(
        val owner: String,
    )
}
