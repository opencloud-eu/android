/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2020 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.presentation.avatar

import android.accounts.Account
import android.view.MenuItem
import android.widget.ImageView
import eu.opencloud.android.R
import coil.load
import eu.opencloud.android.MainApp.Companion.appContext
import eu.opencloud.android.presentation.thumbnails.ThumbnailsRequester
import org.koin.core.component.KoinComponent

class AvatarUtils : KoinComponent {

    /**
     * Show the avatar corresponding to the received account in an {@ImageView}.
     * <p>
     * The avatar is shown if available locally. The avatar is not
     * fetched from the server if not available.
     * <p>
     * If there is no avatar stored, a colored icon is generated with the first letter of the account username.
     * <p>
     * If this is not possible either, a predefined user icon is shown instead.
     *
     * @param account         OC account which avatar will be shown.
     * @param displayRadius   The radius of the circle where the avatar will be clipped into.
     * @param fetchIfNotCached When 'true', if there is no avatar stored in the cache, it's fetched from
     *                        the server. When 'false', server is not accessed, the fallback avatar is
     *                        generated instead.
     */
    fun loadAvatarForAccount(
        imageView: ImageView,
        account: Account,
        @Suppress("UnusedParameter") displayRadius: Float,
        imageLoader: coil.ImageLoader? = null
    ) {
        val uri = ThumbnailsRequester.getAvatarUri(account)
        val loader = imageLoader ?: ThumbnailsRequester.getCoilImageLoader(account)
        imageView.load(uri, loader) {
            placeholder(R.drawable.ic_account_circle)
            error(R.drawable.ic_account_circle)
            transformations(coil.transform.CircleCropTransformation())
        }
    }

    fun loadAvatarForAccount(
        menuItem: MenuItem,
        account: Account,
        @Suppress("UnusedParameter") displayRadius: Float
    ) {
        val uri = ThumbnailsRequester.getAvatarUri(account)
        val imageLoader = ThumbnailsRequester.getCoilImageLoader(account)
        val request = coil.request.ImageRequest.Builder(appContext)
            .data(uri)
            .target(
                onStart = { menuItem.setIcon(R.drawable.ic_account_circle) },
                onSuccess = { result -> menuItem.icon = result },
                onError = { menuItem.setIcon(R.drawable.ic_account_circle) }
            )
            .transformations(coil.transform.CircleCropTransformation())
            .build()
        imageLoader.enqueue(request)
    }
}
