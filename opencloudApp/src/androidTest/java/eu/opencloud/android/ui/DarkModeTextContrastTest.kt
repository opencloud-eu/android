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

import android.content.res.Configuration
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import com.andrognito.patternlockview.PatternLockView
import eu.opencloud.android.R
import eu.opencloud.android.presentation.files.createshortcut.CreateShortcutDialogFragment
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkModeTextContrastTest {

    @Test
    fun fileDetailsTextIsReadableInBothModes() {
        assertLayoutTextContrast(
            R.layout.file_details_fragment,
            listOf(R.id.fdname, R.id.fdType, R.id.fdSize, R.id.fdModified, R.id.fdCreated, R.id.fdLastSync, R.id.fdPath)
        )
    }

    @Test
    fun uploadHeadingIsReadableInBothModes() {
        assertLayoutTextContrast(R.layout.upload_list_group, listOf(R.id.uploadListGroupName))
    }

    @Test
    fun passwordActionsAreReadableInBothModes() {
        assertLayoutTextContrast(R.layout.share_public_dialog, listOf(R.id.generatePasswordButton, R.id.copyPasswordButton))
    }

    @Test
    fun shortcutActionsStayReadableAfterValidInput() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val context = contextForMode(nightMode)
                val fragment = CreateShortcutDialogFragment()
                val view = fragment.onCreateView(LayoutInflater.from(context), null, null)
                fragment.onViewCreated(view, null)
                view.findViewById<EditText>(R.id.create_shortcut_dialog_name_file_value).setText("notes")
                view.findViewById<EditText>(R.id.create_shortcut_dialog_url_value).setText("https://example.com")
                val button = view.findViewById<TextView>(R.id.createButton)
                assertTrue(button.isEnabled)
                val contrast = ColorUtils.calculateContrast(button.currentTextColor, context.getColor(R.color.theme_color_background))
                assertTrue("Create shortcut night=$nightMode contrast=$contrast", contrast >= 4.5)
            }
        }
    }

    @Test
    fun fileDetailsSpaceIconHasReadableTint() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val context = contextForMode(nightMode)
                val view = LayoutInflater.from(context).inflate(R.layout.file_details_fragment, null)
                val tint = view.findViewById<ImageView>(R.id.fdIconSpace).imageTintList
                assertNotNull(tint)
                val contrast = ColorUtils.calculateContrast(tint!!.defaultColor, context.getColor(R.color.theme_color_background))
                assertTrue("Spaces icon contrast=$contrast", contrast >= 3.0)
            }
        }
    }

    @Test
    fun patternDotsAreVisibleOnPhonesAndTablets() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                for (width in listOf(360, 800)) {
                    val context = contextForMode(nightMode, width)
                    val view = LayoutInflater.from(context).inflate(R.layout.activity_pattern_lock, null)
                    val pattern = view.findViewById<PatternLockView>(R.id.pattern_lock_view)
                    for (color in listOf(pattern.normalStateColor, pattern.correctStateColor, pattern.wrongStateColor)) {
                        val contrast = ColorUtils.calculateContrast(color, context.getColor(R.color.theme_color_background))
                        assertTrue("Pattern width=$width night=$nightMode contrast=$contrast", contrast >= 3.0)
                    }
                }
            }
        }
    }

    private fun contextForMode(nightMode: Int, width: Int = 360): ContextThemeWrapper {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(appContext.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            screenWidthDp = width
            smallestScreenWidthDp = width
        }
        return ContextThemeWrapper(appContext.createConfigurationContext(configuration), R.style.Theme_openCloud)
    }

    private fun assertLayoutTextContrast(layout: Int, textIds: List<Int>) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val context = contextForMode(nightMode)
                val view = LayoutInflater.from(context).inflate(layout, null)
                for (id in textIds) {
                    val text = view.findViewById<TextView>(id)
                    text.isEnabled = true
                    val background = context.getColor(R.color.theme_color_background)
                    val contrast = ColorUtils.calculateContrast(text.currentTextColor, background)
                    assertTrue("${context.resources.getResourceEntryName(id)} night=$nightMode contrast=$contrast", contrast >= 4.5)
                }
            }
        }
    }
}
