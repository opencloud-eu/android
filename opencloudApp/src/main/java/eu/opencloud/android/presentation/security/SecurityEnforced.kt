/**
 * openCloud Android client application
 *
 * @author Fernando Sanz Velasco
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
 *
 */

package eu.opencloud.android.presentation.security

interface SecurityEnforced {
    fun optionLockSelected(type: LockType)
}

enum class LockType {
    PASSCODE, PATTERN;

    companion object {
        fun parseFromInteger(value: Int): LockType =
            if (value == 0) {
                PASSCODE
            } else {
                PATTERN
            }
    }
}
