/**
 * ownCloud Android client application
 *
 * @author Abel García de Prada
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

package com.owncloud.android.domain.server.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerInfoTest {

    @Test
    fun testConstructor() {
        val item = ServerInfo.BasicServer(
            "10.3.2.1",
            "https://demo.opencloud.eu"
        )

        assertEquals("https://demo.opencloud.eu", item.baseUrl)
        assertEquals("10.3.2.1", item.ownCloudVersion)
        assertTrue(item.isSecureConnection)
    }

    @Test
    fun testEqualsOk() {
        val item1 = ServerInfo.BasicServer(
            baseUrl = "https://demo.opencloud.eu",
            ownCloudVersion = "10.3.2.1",
        )

        val item2 = ServerInfo.BasicServer(
            "10.3.2.1",
            "https://demo.opencloud.eu",
        )

        assertTrue(item1 == item2)
        assertFalse(item1 === item2)
    }

    @Test
    fun testEqualsKo() {
        val item1 = ServerInfo.BasicServer(
            baseUrl = "https://demo.opencloud.eu",
            ownCloudVersion = "10.3.2.1",
        )

        val item2 = ServerInfo.BasicServer(
            "10.0.0.0",
            "https://demo.opencloud.eu",
        )

        assertFalse(item1 == item2)
        assertFalse(item1 === item2)
    }
}
