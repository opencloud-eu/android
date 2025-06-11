/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2021 ownCloud GmbH.
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
package eu.opencloud.android.domain.automaticuploads.usecases

import eu.opencloud.android.domain.BaseUseCaseWithResult
import eu.opencloud.android.domain.automaticuploads.FolderBackupRepository
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration

class SavePictureUploadsConfigurationUseCase(
    private val folderBackupRepository: FolderBackupRepository
) : BaseUseCaseWithResult<Unit, SavePictureUploadsConfigurationUseCase.Params>() {

    override fun run(params: Params) =
        folderBackupRepository.saveFolderBackupConfiguration(params.pictureUploadsConfiguration)

    data class Params(
        val pictureUploadsConfiguration: FolderBackUpConfiguration
    )
}
