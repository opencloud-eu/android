/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 ownCloud GmbH.
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

import eu.opencloud.android.domain.files.model.OCFile
import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailsRequesterTest {

    @Test
    fun `thumbnail cache token prefers explicit etag`() {
        val file = file(remoteEtag = "remote-etag", etag = "local-etag")

        val token = ThumbnailsRequester.getThumbnailCacheToken(file, explicitEtag = "explicit-etag")

        assertEquals("explicit-etag", token)
    }

    @Test
    fun `thumbnail cache token uses remote etag before etag`() {
        val file = file(remoteEtag = "remote-etag", etag = "local-etag")

        val token = ThumbnailsRequester.getThumbnailCacheToken(file)

        assertEquals("remote-etag", token)
    }

    @Test
    fun `thumbnail cache token uses hash remote etag before etag`() {
        val file = file(remoteEtag = "sha256:local-content-hash", etag = "local-etag")

        val token = ThumbnailsRequester.getThumbnailCacheToken(file)

        assertEquals("sha256:local-content-hash", token)
    }

    @Test
    fun `thumbnail cache token falls back to etag when remote etag is missing`() {
        val fileWithNullRemoteEtag = file(remoteEtag = null, etag = "local-etag")
        val fileWithBlankRemoteEtag = file(remoteEtag = " ", etag = "local-etag")

        assertEquals("local-etag", ThumbnailsRequester.getThumbnailCacheToken(fileWithNullRemoteEtag))
        assertEquals("local-etag", ThumbnailsRequester.getThumbnailCacheToken(fileWithBlankRemoteEtag))
    }

    @Test
    fun `thumbnail cache token is blank without any etag`() {
        val file = file(remoteEtag = null, etag = "")

        val token = ThumbnailsRequester.getThumbnailCacheToken(file, explicitEtag = " ")

        assertEquals("", token)
    }

    private fun file(remoteEtag: String?, etag: String?) =
        OCFile(
            owner = "owner",
            length = 1,
            modificationTimestamp = 1,
            remotePath = "/Photos/image.jpg",
            mimeType = "image/jpeg",
            remoteEtag = remoteEtag,
            etag = etag,
        )
}
