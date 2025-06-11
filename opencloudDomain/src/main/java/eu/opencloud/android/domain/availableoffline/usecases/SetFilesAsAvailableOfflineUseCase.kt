/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2022 ownCloud GmbH.
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
package eu.opencloud.android.domain.availableoffline.usecases

import eu.opencloud.android.domain.BaseUseCaseWithResult
import eu.opencloud.android.domain.availableoffline.model.AvailableOfflineStatus
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.domain.files.model.OCFile

class SetFilesAsAvailableOfflineUseCase(
    private val fileRepository: FileRepository,
) : BaseUseCaseWithResult<Unit, SetFilesAsAvailableOfflineUseCase.Params>() {

    override fun run(params: Params) {
        params.filesToSetAsAvailableOffline.forEach { fileToSetAsAvailableOffline ->
            // Its possible to multiselect several files including already available offline files.
            // If it is already available offline, we will ignore it.
            if (!fileToSetAsAvailableOffline.isAvailableOffline) {
                fileRepository.updateFileWithNewAvailableOfflineStatus(
                    ocFile = fileToSetAsAvailableOffline,
                    newAvailableOfflineStatus = AvailableOfflineStatus.AVAILABLE_OFFLINE,
                )
            }
        }
    }

    data class Params(
        val filesToSetAsAvailableOffline: List<OCFile>
    )
}
