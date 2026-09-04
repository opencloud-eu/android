/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 openCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.presentation.settings

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceModeTest {

    @Test
    fun `maps persisted appearance values to night modes`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppearanceMode.fromPreferenceValue("SYSTEM").nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, AppearanceMode.fromPreferenceValue("LIGHT").nightMode)
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, AppearanceMode.fromPreferenceValue("DARK").nightMode)
    }

    @Test
    fun `uses system mode for missing or invalid persisted appearance values`() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreferenceValue(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromPreferenceValue("INVALID"))
    }

    @Test
    fun `uses the mode name as its canonical persisted value`() {
        assertEquals("SYSTEM", AppearanceMode.fromPreferenceValue(null).name)
        assertEquals("SYSTEM", AppearanceMode.fromPreferenceValue("INVALID").name)
        assertEquals("LIGHT", AppearanceMode.fromPreferenceValue("LIGHT").name)
        assertEquals("DARK", AppearanceMode.fromPreferenceValue("DARK").name)
    }
}