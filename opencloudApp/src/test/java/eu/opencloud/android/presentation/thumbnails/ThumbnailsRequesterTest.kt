/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 opencloud.
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

package eu.opencloud.android.presentation.thumbnails

import android.net.Uri
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThumbnailsRequesterTest {

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.encode(any<String>(), any<String>()) } answers {
            encodeSpaces(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `preview uri for personal file uses legacy webdav path`() {
        val uri = ThumbnailsRequester.buildPreviewUri(
            accountBaseUrl = "https://server.url/",
            remotePath = "/Photos/image.jpg",
            spaceId = null,
            spaceWebDavUrl = null,
            etag = "etag",
            width = 1024,
            height = 768,
        )

        assertEquals(
            "https://server.url/webdav/Photos/image.jpg?x=1024&y=768&c=etag&preview=1",
            uri
        )
    }

    @Test
    fun `preview uri for space file uses space webdav url from sync info`() {
        val uri = ThumbnailsRequester.buildPreviewUri(
            accountBaseUrl = "https://server.url",
            remotePath = "/MyFolder/test.jpg",
            spaceId = "ignored-space-id",
            spaceWebDavUrl = "https://server.url/dav/spaces/space-id\$opaque/",
            etag = "space-etag",
            width = 512,
            height = 512,
        )

        assertEquals(
            "https://server.url/dav/spaces/space-id\$opaque/MyFolder/test.jpg?x=512&y=512&c=space-etag&preview=1",
            uri
        )
    }

    @Test
    fun `preview uri for space file falls back to account base url and space id`() {
        val uri = ThumbnailsRequester.buildPreviewUri(
            accountBaseUrl = "https://server.url/",
            remotePath = "/MyFolder/test.jpg",
            spaceId = "space-id\$opaque",
            spaceWebDavUrl = null,
            etag = "space-etag",
            width = 256,
            height = 256,
        )

        assertEquals(
            "https://server.url/dav/spaces/space-id\$opaque/MyFolder/test.jpg?x=256&y=256&c=space-etag&preview=1",
            uri
        )
    }

    @Test
    fun `preview uri preserves subfolders and encodes spaces`() {
        val uri = ThumbnailsRequester.buildPreviewUri(
            accountBaseUrl = "https://server.url",
            remotePath = "/My Folder/test image.jpg",
            spaceId = "space-id\$opaque",
            spaceWebDavUrl = "https://server.url/dav/spaces/space-id\$opaque",
            etag = null,
            width = 1024,
            height = 1024,
        )

        assertEquals(
            "https://server.url/dav/spaces/space-id\$opaque/My%20Folder/test%20image.jpg?x=1024&y=1024&c=&preview=1",
            uri
        )
    }

    private fun encodeSpaces(value: String): String =
        value.replace(" ", "%20")
}
