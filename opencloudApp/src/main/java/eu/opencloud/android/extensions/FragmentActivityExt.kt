/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * Copyright (C) 2020 ownCloud GmbH.
 *
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

package eu.opencloud.android.extensions

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity

fun FragmentActivity.showDialogFragment(
    newFragment: DialogFragment, fragmentTag: String
) {
    val ft = supportFragmentManager.beginTransaction()
    val prev = supportFragmentManager.findFragmentByTag(fragmentTag)

    if (prev != null) {
        ft.remove(prev)
    }
    ft.addToBackStack(null)

    newFragment.show(ft, fragmentTag)
}
