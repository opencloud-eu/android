/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
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
package eu.opencloud.android.domain.user.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInfoTest {
    @Test
    fun testConstructor() {
        val item = UserInfo(
            "admin",
            "adminOc",
            "admin@opencloud.eu"
        )

        assertEquals("admin", item.id)
        assertEquals("adminOc", item.displayName)
        assertEquals("admin@opencloud.eu", item.email)
    }

    @Test
    fun testEqualsOk() {
        val item1 = UserInfo(
            id = "admin",
            displayName = "adminOc",
            email = null
        )

        val item2 = UserInfo(
            "admin",
            "adminOc",
            null
        )

        assertTrue(item1 == item2)
        assertFalse(item1 === item2)
    }

    @Test
    fun testEqualsKo() {
        val item1 = UserInfo(
            id = "admin",
            displayName = "adminOc",
            email = null
        )

        val item2 = UserInfo(
            "admin",
            "adminOc",
            "demo@opencloud.eu"
        )

        assertFalse(item1 == item2)
        assertFalse(item1 === item2)
    }
}
