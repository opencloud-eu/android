/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 OpenCloud GmbH.
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

package eu.opencloud.android.presentation.viewmodels.security

import eu.opencloud.android.presentation.security.AppLockSecretHash
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSecretHashTest {

    @Test
    fun `hash hides secret and validates matching secret`() {
        val secret = "1234"

        val storedSecret = AppLockSecretHash.hash(secret)

        assertNotEquals(secret, storedSecret)
        assertTrue(AppLockSecretHash.isHash(storedSecret))
        assertTrue(AppLockSecretHash.verify(secret, storedSecret))
        assertFalse(AppLockSecretHash.verify("4321", storedSecret))
    }

    @Test
    fun `verify still accepts legacy plaintext secret`() {
        assertTrue(AppLockSecretHash.verify("1234", "1234"))
        assertFalse(AppLockSecretHash.verify("4321", "1234"))
    }
}
