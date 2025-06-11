/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Jorge Aguado Recio
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
package eu.opencloud.android.data.user.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import eu.opencloud.android.data.ProviderMeta.ProviderTableMeta.USER_QUOTAS_TABLE_NAME

/**
 * Represents one record of the UserQuota table.
 */
@Entity(tableName = USER_QUOTAS_TABLE_NAME)
data class UserQuotaEntity(
    @PrimaryKey
    val accountName: String,
    val used: Long,
    val available: Long,
    val total: Long? = null,
    val state: String? = null
)
