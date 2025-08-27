/**
 * openCloud Android client application
 *
 * @author Christian Schabesberger
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

package eu.opencloud.android.domain.webfinger.usecases

import eu.opencloud.android.domain.BaseUseCaseWithResult
import eu.opencloud.android.domain.webfinger.WebFingerRepository
import eu.opencloud.android.domain.webfinger.model.WebFingerRel

class GetOpenCloudInstanceFromWebFingerUseCase(
    private val webfingerRepository: WebFingerRepository
) : BaseUseCaseWithResult<String, GetOpenCloudInstanceFromWebFingerUseCase.Params>() {

    override fun run(params: Params): String =
        webfingerRepository.getInstancesFromWebFinger(
            server = params.server,
            rel = WebFingerRel.OPENCLOUD_INSTANCE,
            resource = params.resource
        ).first()

    data class Params(
        val server: String,
        val resource: String
    )
}
