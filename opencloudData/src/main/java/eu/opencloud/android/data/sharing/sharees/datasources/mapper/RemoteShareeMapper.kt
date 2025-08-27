/**
 * openCloud Android client application
 *
 * @author Christian Schabesberger
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

package eu.opencloud.android.data.sharing.sharees.datasources.mapper

import eu.opencloud.android.domain.mappers.RemoteMapper
import eu.opencloud.android.domain.sharing.sharees.model.OCSharee
import eu.opencloud.android.domain.sharing.shares.model.ShareType
import eu.opencloud.android.lib.resources.shares.responses.ShareeItem
import eu.opencloud.android.lib.resources.shares.responses.ShareeOcsResponse

class RemoteShareeMapper : RemoteMapper<List<OCSharee>, ShareeOcsResponse> {
    private fun mapShareeItemToOCSharee(item: ShareeItem, isExactMatch: Boolean) =
        OCSharee(
            label = item.label,
            shareType = ShareType.fromValue(item.value.shareType)!!,
            shareWith = item.value.shareWith,
            additionalInfo = item.value.additionalInfo ?: "",
            isExactMatch = isExactMatch
        )

    override fun toModel(remote: ShareeOcsResponse?): List<OCSharee> {
        val exactMatches = remote?.exact?.getFlatRepresentation()?.map {
            mapShareeItemToOCSharee(it, isExactMatch = true)
        }
        val nonExactMatches = remote?.getFlatRepresentationWithoutExact()?.map {
            mapShareeItemToOCSharee(it, isExactMatch = false)
        }
        return ArrayList<OCSharee>().apply {
            if (exactMatches != null) {
                addAll(exactMatches)
            }
            if (nonExactMatches != null) {
                addAll(nonExactMatches)
            }
        }
    }

    // not needed
    override fun toRemote(model: List<OCSharee>?): ShareeOcsResponse? = null
}
