/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 *
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

package eu.opencloud.android.domain.transfers.model

enum class TransferStatus constructor(val value: Int) {
    TRANSFER_QUEUED(value = 0),
    TRANSFER_IN_PROGRESS(value = 1),
    TRANSFER_FAILED(value = 2),
    TRANSFER_SUCCEEDED(value = 3);

    companion object {
        fun fromValue(value: Int): TransferStatus =
            when (value) {
                0 -> TRANSFER_QUEUED
                1 -> TRANSFER_IN_PROGRESS
                2 -> TRANSFER_FAILED
                else -> TRANSFER_SUCCEEDED
            }
    }
}
