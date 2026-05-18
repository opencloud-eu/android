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

package eu.opencloud.android.workers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class FileEtagCacheTokenResolverTest {

    @Test
    fun `server etag wins for sync and thumbnail tokens`() {
        val resolvedEtags = FileEtagCacheTokenResolver.resolve(
            serverEtag = " \"server-etag\" ",
            existingEtag = "existing-etag",
            existingRemoteEtag = "existing-remote-etag",
            localContentHashToken = "sha256:local-hash",
        )

        assertEquals("server-etag", resolvedEtags.etag)
        assertEquals("server-etag", resolvedEtags.remoteEtag)
    }

    @Test
    fun `blank server etag preserves existing sync etag`() {
        val resolvedEtags = FileEtagCacheTokenResolver.resolve(
            serverEtag = " ",
            existingEtag = "existing-etag",
            existingRemoteEtag = "existing-remote-etag",
            localContentHashToken = "sha256:local-hash",
        )

        assertEquals("existing-etag", resolvedEtags.etag)
    }

    @Test
    fun `blank server etag uses local content hash for thumbnail token`() {
        val resolvedEtags = FileEtagCacheTokenResolver.resolve(
            serverEtag = "",
            existingEtag = "existing-etag",
            existingRemoteEtag = "existing-remote-etag",
            localContentHashToken = " sha256:local-hash ",
        )

        assertEquals("existing-etag", resolvedEtags.etag)
        assertEquals("sha256:local-hash", resolvedEtags.remoteEtag)
    }

    @Test
    fun `blank server etag without hash preserves existing thumbnail tokens`() {
        val resolvedRemoteEtags = FileEtagCacheTokenResolver.resolve(
            serverEtag = null,
            existingEtag = "existing-etag",
            existingRemoteEtag = "existing-remote-etag",
        )

        assertEquals("existing-etag", resolvedRemoteEtags.etag)
        assertEquals("existing-remote-etag", resolvedRemoteEtags.remoteEtag)

        val resolvedEtagFallback = FileEtagCacheTokenResolver.resolve(
            serverEtag = null,
            existingEtag = "existing-etag",
            existingRemoteEtag = " ",
        )

        assertEquals("existing-etag", resolvedEtagFallback.etag)
        assertEquals("existing-etag", resolvedEtagFallback.remoteEtag)
    }

    @Test
    fun `blank server etag without any fallback leaves thumbnail token missing`() {
        val resolvedEtags = FileEtagCacheTokenResolver.resolve(
            serverEtag = null,
            existingEtag = "",
            existingRemoteEtag = null,
        )

        assertEquals("", resolvedEtags.etag)
        assertNull(resolvedEtags.remoteEtag)
    }

    @Test
    fun `sha256 token hashes readable file content`() {
        val file = File.createTempFile("etag-token", ".txt")
        try {
            file.writeText("opencloud")

            val token = FileEtagCacheTokenResolver.sha256Token(file)

            assertEquals("sha256:dac2d7ad9a952aae0302e5cf934d512167aa2142946973b52c868ef6b69400b8", token)
        } finally {
            file.delete()
        }
    }
}
