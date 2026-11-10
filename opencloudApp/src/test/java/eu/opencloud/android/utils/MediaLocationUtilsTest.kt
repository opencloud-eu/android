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
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MediaLocationUtilsTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val mediaStoreUri: Uri = Uri.parse("content://${MediaStore.AUTHORITY}/external/images/media/42")
    private val otherProviderUri: Uri = Uri.parse("content://com.example.provider/files/42")

    // What automatic uploads read: a document inside the tree picked with the storage access framework.
    private val safDocumentUri: Uri = Uri.parse(
        "content://com.android.externalstorage.documents/tree/primary%3ADCIM/document/primary%3ADCIM%2FCamera%2FIMG_42.jpg"
    )
    private val originalMediaStoreUri: Uri by lazy { MediaStore.setRequireOriginal(mediaStoreUri) } // API >= 29 only

    private val contentResolver: ContentResolver = mockk()
    private val redactedStream: InputStream = ByteArrayInputStream(byteArrayOf(1))
    private val originalStream: InputStream = ByteArrayInputStream(byteArrayOf(2))

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockSafDocument(answerGetMediaUri: () -> Uri?) {
        mockkStatic(DocumentsContract::class)
        mockkStatic(MediaStore::class)
        every { DocumentsContract.isDocumentUri(any(), safDocumentUri) } returns true
        every { MediaStore.getMediaUri(any(), safDocumentUri) } answers { answerGetMediaUri() }
    }

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

    @Test
    @Config(sdk = [29])
    fun saf_document_uri_is_translated_to_the_original_media_store_uri() {
        grantMediaLocationPermission()
        mockSafDocument { mediaStoreUri }

        assertEquals(originalMediaStoreUri, MediaLocationUtils.getOriginalMediaUri(context, safDocumentUri))
    }

    @Test
    @Config(sdk = [29])
    fun saf_document_uri_is_not_translated_without_permission() {
        mockSafDocument { mediaStoreUri }

        assertEquals(safDocumentUri, MediaLocationUtils.getOriginalMediaUri(context, safDocumentUri))
        verify(exactly = 0) { MediaStore.getMediaUri(any(), any()) }
    }

    @Test
    @Config(sdk = [29])
    fun saf_document_uri_is_untouched_when_get_media_uri_throws() {
        grantMediaLocationPermission()
        mockSafDocument { throw IllegalArgumentException("Provider does not support media URIs") }

        assertEquals(safDocumentUri, MediaLocationUtils.getOriginalMediaUri(context, safDocumentUri))
    }

    @Test
    @Config(sdk = [29])
    fun saf_document_uri_is_untouched_when_it_has_no_media_store_counterpart() {
        grantMediaLocationPermission()
        mockSafDocument { null }

        assertEquals(safDocumentUri, MediaLocationUtils.getOriginalMediaUri(context, safDocumentUri))
    }

    @Test
    @Config(sdk = [29])
    fun open_returns_the_original_when_it_can_be_opened() {
        grantMediaLocationPermission()
        mockSafDocument { mediaStoreUri }
        every { contentResolver.openInputStream(originalMediaStoreUri) } returns originalStream

        val stream = MediaLocationUtils.openInputStreamPreservingLocation(context, contentResolver, safDocumentUri)

        assertSame(originalStream, stream)
        verify(exactly = 0) { contentResolver.openInputStream(safDocumentUri) }
    }

    @Test
    @Config(sdk = [29])
    fun open_falls_back_to_the_given_uri_when_opening_the_original_throws() {
        grantMediaLocationPermission()
        mockSafDocument { mediaStoreUri }
        every { contentResolver.openInputStream(originalMediaStoreUri) } throws SecurityException("No access to the original")
        every { contentResolver.openInputStream(safDocumentUri) } returns redactedStream

        val stream = MediaLocationUtils.openInputStreamPreservingLocation(context, contentResolver, safDocumentUri)

        assertSame(redactedStream, stream)
    }

    @Test
    @Config(sdk = [29])
    fun open_falls_back_to_the_given_uri_when_the_original_is_not_found() {
        grantMediaLocationPermission()
        mockSafDocument { mediaStoreUri }
        every { contentResolver.openInputStream(originalMediaStoreUri) } throws FileNotFoundException()
        every { contentResolver.openInputStream(safDocumentUri) } returns redactedStream

        val stream = MediaLocationUtils.openInputStreamPreservingLocation(context, contentResolver, safDocumentUri)

        assertSame(redactedStream, stream)
    }

    @Test
    @Config(sdk = [29])
    fun open_falls_back_to_the_given_uri_when_opening_the_original_returns_null() {
        grantMediaLocationPermission()
        mockSafDocument { mediaStoreUri }
        every { contentResolver.openInputStream(originalMediaStoreUri) } returns null
        every { contentResolver.openInputStream(safDocumentUri) } returns redactedStream

        val stream = MediaLocationUtils.openInputStreamPreservingLocation(context, contentResolver, safDocumentUri)

        assertSame(redactedStream, stream)
    }

    @Test
    @Config(sdk = [29])
    fun open_reads_the_given_uri_directly_without_permission() {
        every { contentResolver.openInputStream(mediaStoreUri) } returns redactedStream

        val stream = MediaLocationUtils.openInputStreamPreservingLocation(context, contentResolver, mediaStoreUri)

        assertSame(redactedStream, stream)
        verify(exactly = 1) { contentResolver.openInputStream(any()) }
    }
}
