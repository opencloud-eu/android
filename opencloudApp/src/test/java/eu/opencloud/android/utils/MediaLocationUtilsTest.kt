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

package eu.opencloud.android.utils

import android.Manifest
import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MediaLocationUtilsTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val mediaStoreUri: Uri = Uri.parse("content://${MediaStore.AUTHORITY}/external/images/media/42")
    private val otherProviderUri: Uri = Uri.parse("content://com.example.provider/files/42")

    private fun grantMediaLocationPermission() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    @Test
    @Config(sdk = [28])
    fun permission_is_implicitly_granted_below_android_10() {
        assertTrue(MediaLocationUtils.isAccessMediaLocationGranted(context))
    }

    @Test
    @Config(sdk = [29])
    fun permission_is_not_granted_by_default_on_android_10() {
        assertFalse(MediaLocationUtils.isAccessMediaLocationGranted(context))
    }

    @Test
    @Config(sdk = [28])
    fun original_uri_is_untouched_below_android_10() {
        assertEquals(mediaStoreUri, MediaLocationUtils.getOriginalMediaUri(context, mediaStoreUri))
    }

    @Test
    @Config(sdk = [29])
    fun original_uri_is_untouched_without_permission() {
        assertEquals(mediaStoreUri, MediaLocationUtils.getOriginalMediaUri(context, mediaStoreUri))
    }

    @Test
    @Config(sdk = [29])
    fun media_store_uri_requires_original_when_permission_granted() {
        grantMediaLocationPermission()

        val originalUri = MediaLocationUtils.getOriginalMediaUri(context, mediaStoreUri)

        assertEquals(MediaStore.setRequireOriginal(mediaStoreUri), originalUri)
        assertEquals("1", originalUri.getQueryParameter("requireOriginal"))
    }

    @Test
    @Config(sdk = [29])
    fun non_media_uri_is_untouched_even_with_permission() {
        grantMediaLocationPermission()

        assertEquals(otherProviderUri, MediaLocationUtils.getOriginalMediaUri(context, otherProviderUri))
    }
}
