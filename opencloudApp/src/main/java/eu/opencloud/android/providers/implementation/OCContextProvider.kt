/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 *
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
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package eu.opencloud.android.providers.implementation

import android.content.Context
import eu.opencloud.android.providers.ContextProvider
import eu.opencloud.android.utils.ConnectivityUtils

class OCContextProvider(private val context: Context) : ContextProvider {

    override fun getBoolean(id: Int): Boolean = context.resources.getBoolean(id)

    override fun getString(id: Int): String = context.resources.getString(id)

    override fun getInt(id: Int): Int = context.resources.getInteger(id)

    override fun getContext(): Context = context

    override fun isConnected(): Boolean = ConnectivityUtils.isAppConnected(context)
}
