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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import eu.opencloud.android.R
import eu.opencloud.android.presentation.common.BottomSheetFragmentItemView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListSelectorTest {

    @Test
    fun selectorLoadsWithoutThemeInLightAndDarkModes() {
        for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val context = contextForNightMode(nightMode)
            val selector = context.resources.getDrawable(R.drawable.list_selector, null) as StateListDrawable
            // Remove the transition so the current drawable can be checked immediately.
            selector.setExitFadeDuration(0)
            for (state in listOf(intArrayOf(), intArrayOf(android.R.attr.state_pressed), intArrayOf(android.R.attr.state_focused))) {
                selector.state = state
                val expectedColor = if (state.isEmpty()) R.color.theme_color_background else R.color.theme_color_selection
                assertEquals(context.getColor(expectedColor), (selector.current as ColorDrawable).color)
            }
        }
    }

    @Test
    fun gridRowsInflateInLightAndDarkModes() {
        assertRowInflates(R.layout.grid_item)
    }

    @Test
    fun uploadRowsInflateInLightAndDarkModes() {
        assertRowInflates(R.layout.uploader_list_item_layout)
    }

    @Test
    fun selectedSortRowStaysReadableInLightAndDarkModes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val context = ContextThemeWrapper(contextForNightMode(nightMode), R.style.Theme_openCloud)
                val sheet = LayoutInflater.from(context).inflate(R.layout.sort_bottom_sheet_fragment, null)
                val row = sheet.findViewById<BottomSheetFragmentItemView>(R.id.sort_by_name)
                row.setSelected(R.drawable.ic_baseline_arrow_upward)
                val textColor = row.findViewById<TextView>(R.id.item_title).currentTextColor
                assertTrue(ColorUtils.calculateContrast(textColor, context.getColor(R.color.theme_color_surface)) >= 4.5)
            }
        }
    }

    @Test
    fun gridSharingIndicatorsUseReadableTintsInLightAndDarkModes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val context = ContextThemeWrapper(contextForNightMode(nightMode), R.style.Theme_openCloud)
                val row = LayoutInflater.from(context).inflate(R.layout.grid_item, null)
                for (id in listOf(R.id.shared_via_users_icon, R.id.shared_by_link_icon)) {
                    val tint = row.findViewById<ImageView>(id).imageTintList
                    assertNotNull(tint)
                    assertTrue(ColorUtils.calculateContrast(tint!!.defaultColor, context.getColor(R.color.theme_color_surface)) >= 3.0)
                }
            }
        }
    }

    private fun assertRowInflates(layout: Int) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val context = ContextThemeWrapper(contextForNightMode(nightMode), R.style.Theme_openCloud)
                assertNotNull(LayoutInflater.from(context).inflate(layout, null).background)
            }
        }
    }

    private fun contextForNightMode(nightMode: Int): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        return context.createConfigurationContext(configuration)
    }
}
