/**
 * openCloud Android client application
 *
 * @author David González Verdugo
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

package eu.opencloud.android.domain.exceptions

import java.lang.Exception

/**
 * An HTTP status code we have no specific handling for. [httpCode] is 0 when the status is unknown.
 *
 * Note: the message is deliberately left null. Throwable.parseError() returns the message verbatim when
 * there is one, which would show the raw server phrase to the user.
 */
class UnhandledHttpCodeException(val httpCode: Int = 0) : Exception()
