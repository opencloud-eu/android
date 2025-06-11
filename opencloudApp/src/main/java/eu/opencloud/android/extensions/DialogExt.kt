/**
 * openCloud Android client application
 *
 * @author David Crespo Ríos
 * Copyright (C) 2022 ownCloud GmbH.
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

package eu.opencloud.android.extensions

import android.app.Dialog
import android.view.WindowManager
import eu.opencloud.android.BuildConfig
import eu.opencloud.android.R

fun Dialog.avoidScreenshotsIfNeeded() {
    if (!BuildConfig.DEBUG && context.resources?.getBoolean(R.bool.allow_screenshots) == false) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
