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
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.io.InputStream

/**
 * Since Android 10 the system redacts location metadata (GPS EXIF) from photos and videos
 * read through the media store or the storage access framework, unless the app holds
 * [Manifest.permission.ACCESS_MEDIA_LOCATION] AND asks for the original via
 * [MediaStore.setRequireOriginal]. Without this, uploads silently lose their location.
 */
object MediaLocationUtils {

    @JvmStatic
    fun isAccessMediaLocationGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Returns a URI that yields the unredacted original of [uri] (location metadata intact),
     * or [uri] itself when that is not possible: older Android, permission not granted, or a
     * content URI that has no media store counterpart (non-media documents, cloud providers...).
     *
     * SAF document URIs (what automatic uploads use) are translated to their media store URI
     * with [MediaStore.getMediaUri]; access is granted through the same document grant.
     */
    @JvmStatic
    fun getOriginalMediaUri(context: Context, uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !isAccessMediaLocationGranted(context)) {
            return uri
        }
        val mediaUri: Uri = when {
            uri.authority == MediaStore.AUTHORITY -> uri
            DocumentsContract.isDocumentUri(context, uri) -> try {
                MediaStore.getMediaUri(context, uri)
            } catch (e: Exception) {
                Timber.d(e, "No media store URI for %s, uploading it as is", uri)
                null
            } ?: return uri
            else -> return uri
        }
        return MediaStore.setRequireOriginal(mediaUri)
    }

    /**
     * Opens [uri] for reading, preferring the unredacted original (see [getOriginalMediaUri]).
     * Falls back to opening [uri] directly if the original can't be opened, so an upload never
     * fails because of the location metadata.
     */
    @JvmStatic
    fun openInputStreamPreservingLocation(context: Context, contentResolver: ContentResolver, uri: Uri): InputStream? {
        val originalUri = getOriginalMediaUri(context, uri)
        if (originalUri != uri) {
            try {
                contentResolver.openInputStream(originalUri)?.let { return it }
            } catch (e: Exception) {
                Timber.w(e, "Could not open original media for %s, falling back to redacted copy", uri)
            }
        }
        return contentResolver.openInputStream(uri)
    }
}
