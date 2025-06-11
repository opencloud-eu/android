/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
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

package eu.opencloud.android.domain.spaces.usecases

import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.spaces.SpacesRepository
import eu.opencloud.android.domain.spaces.model.OCSpace

class GetSpaceWithSpecialsByIdForAccountUseCase(
    private val spacesRepository: SpacesRepository
) : BaseUseCase<OCSpace?, GetSpaceWithSpecialsByIdForAccountUseCase.Params>() {

    override fun run(params: Params): OCSpace? {
        if (params.spaceId == null) return null
        return spacesRepository.getSpaceWithSpecialsByIdForAccount(params.spaceId, params.accountName)
    }

    data class Params(
        val spaceId: String?,
        val accountName: String,
    )
}
