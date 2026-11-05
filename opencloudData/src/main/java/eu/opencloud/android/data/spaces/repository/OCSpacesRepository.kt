/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author Jorge Aguado Recio
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

package eu.opencloud.android.data.spaces.repository

import eu.opencloud.android.data.capabilities.datasources.LocalCapabilitiesDataSource
import eu.opencloud.android.data.spaces.datasources.LocalSpacesDataSource
import eu.opencloud.android.data.spaces.datasources.RemoteSpacesDataSource
import eu.opencloud.android.data.user.datasources.LocalUserDataSource
import eu.opencloud.android.domain.spaces.SpacesRepository
import eu.opencloud.android.domain.spaces.model.OCSpace
import eu.opencloud.android.domain.user.model.UserQuota
import eu.opencloud.android.domain.user.model.UserQuotaState

class OCSpacesRepository(
    private val localSpacesDataSource: LocalSpacesDataSource,
    private val localUserDataSource: LocalUserDataSource,
    private val remoteSpacesDataSource: RemoteSpacesDataSource,
    private val localCapabilitiesDataSource: LocalCapabilitiesDataSource,
) : SpacesRepository {
    override fun refreshSpacesForAccount(accountName: String) {
        remoteSpacesDataSource.refreshSpacesForAccount(accountName).also { listOfSpaces ->
            if (listOfSpaces.isEmpty()) {
                localSpacesDataSource.deleteSpacesForAccount(accountName)
                localUserDataSource.saveQuotaForAccount(accountName, unavailableQuota(accountName))
                return@also
            }

            localSpacesDataSource.saveSpacesForAccount(listOfSpaces)
            val personalSpace = listOfSpaces.find { it.isPersonal }
            val capabilities = localCapabilitiesDataSource.getCapabilitiesForAccount(accountName)
            val isMultiPersonal = capabilities?.spaces?.hasMultiplePersonalSpaces
            val userQuota = getUserQuotaForPersonalSpace(accountName, personalSpace, isMultiPersonal == true)
            localUserDataSource.saveQuotaForAccount(accountName, userQuota)
        }
    }

    private fun getUserQuotaForPersonalSpace(
        accountName: String,
        personalSpace: OCSpace?,
        isMultiPersonal: Boolean,
    ): UserQuota {
        if (isMultiPersonal || personalSpace == null) {
            return unavailableQuota(accountName)
        }

        val quota = personalSpace.quota ?: return unavailableQuota(accountName)
        val total = quota.total ?: return unavailableQuota(accountName)
        val used = quota.used ?: 0
        val state = quota.state?.let { UserQuotaState.fromValue(it) } ?: UserQuotaState.NORMAL

        return if (total == 0L) {
            UserQuota(accountName, -3, used, total, state)
        } else {
            val remaining = quota.remaining ?: (total - used).coerceAtLeast(0)
            UserQuota(accountName, remaining, used, total, state)
        }
    }

    private fun unavailableQuota(accountName: String) =
        UserQuota(accountName, -4, 0, 0, UserQuotaState.NORMAL)

    override fun getSpacesFromEveryAccountAsStream() =
        localSpacesDataSource.getSpacesFromEveryAccountAsStream()

    override fun getSpacesByDriveTypeWithSpecialsForAccountAsFlow(accountName: String, filterDriveTypes: Set<String>) =
        localSpacesDataSource.getSpacesByDriveTypeWithSpecialsForAccountAsFlow(accountName = accountName, filterDriveTypes = filterDriveTypes)

    override fun getPersonalSpaceForAccount(accountName: String) =
        localSpacesDataSource.getPersonalSpaceForAccount(accountName)

    override fun getPersonalAndProjectSpacesForAccount(accountName: String) =
        localSpacesDataSource.getPersonalAndProjectSpacesForAccount(accountName)

    override fun getSpaceWithSpecialsByIdForAccount(spaceId: String?, accountName: String) =
        localSpacesDataSource.getSpaceWithSpecialsByIdForAccount(spaceId, accountName)

    override fun getSpaceByIdForAccount(spaceId: String?, accountName: String): OCSpace? =
        localSpacesDataSource.getSpaceByIdForAccount(spaceId = spaceId, accountName = accountName)

    override fun getWebDavUrlForSpace(accountName: String, spaceId: String?): String? =
        localSpacesDataSource.getWebDavUrlForSpace(accountName = accountName, spaceId = spaceId)

}
