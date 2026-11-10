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

package eu.opencloud.android.presentation.settings.automaticuploads

import eu.opencloud.android.presentation.settings.automaticuploads.MediaLocationPermissionRequester.Companion.shouldRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaLocationPermissionRequesterTest {

    @Test
    fun `never requests when the permission is already granted`() {
        for (alreadyRequested in listOf(false, true)) {
            for (shouldShowRationale in listOf(false, true)) {
                for (userInitiated in listOf(false, true)) {
                    assertFalse(shouldRequest(isGranted = true, alreadyRequested, shouldShowRationale, userInitiated))
                }
            }
        }
    }

    @Test
    fun `requests the first time, whether the screen was opened or uploads were just enabled`() {
        assertTrue(shouldRequest(isGranted = false, alreadyRequested = false, shouldShowRationale = false, userInitiated = false))
        assertTrue(shouldRequest(isGranted = false, alreadyRequested = false, shouldShowRationale = false, userInitiated = true))
    }

    @Test
    fun `does not request again on later visits to the screen`() {
        assertFalse(shouldRequest(isGranted = false, alreadyRequested = true, shouldShowRationale = true, userInitiated = false))
        assertFalse(shouldRequest(isGranted = false, alreadyRequested = true, shouldShowRationale = false, userInitiated = false))
    }

    @Test
    fun `requests again after one denial when the user enables uploads`() {
        assertTrue(shouldRequest(isGranted = false, alreadyRequested = true, shouldShowRationale = true, userInitiated = true))
    }

    @Test
    fun `does not request once the system denies without showing the dialog`() {
        assertFalse(shouldRequest(isGranted = false, alreadyRequested = true, shouldShowRationale = false, userInitiated = true))
    }
}
