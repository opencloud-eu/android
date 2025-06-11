/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2020 ownCloud GmbH.
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
import eu.opencloud.android.domain.validator.FileNameValidator

class CreateFolderAsyncUseCase(
    private val fileRepository: FileRepository
) : BaseUseCaseWithResult<Unit, CreateFolderAsyncUseCase.Params>() {

    private val fileNameValidator = FileNameValidator()

    override fun run(params: Params) {
        fileNameValidator.validateOrThrowException(params.folderName)

        val remotePath = params.parentFile.remotePath.plus(params.folderName).plus(OCFile.PATH_SEPARATOR)
        return fileRepository.createFolder(remotePath = remotePath, parentFolder = params.parentFile)
    }

    data class Params(val folderName: String, val parentFile: OCFile)
}
