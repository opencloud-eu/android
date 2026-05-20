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

package eu.opencloud.android.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MimetypeIconUtilTest {

    @Test
    fun `getBestMimeTypeForOpen keeps specific server mime type`() {
        val mimeType = MimetypeIconUtil.getBestMimeTypeForOpen(
            "application/pdf",
            "Invoice.PDF",
        )

        assertEquals("application/pdf", mimeType)
    }

    @Test
    fun `getBestMimeTypeForOpen uses file extension when server mime type is generic`() {
        val mimeType = MimetypeIconUtil.getBestMimeTypeForOpen(
            MimetypeIconUtil.UNKNOWN_MIME_TYPE,
            "Invoice.PDF",
        )

        assertEquals("application/pdf", mimeType)
    }

    @Test
    fun `getBestMimeTypeForOpen uses file extension when server mime type is wildcard`() {
        val mimeType = MimetypeIconUtil.getBestMimeTypeForOpen(
            "*/*",
            "Invoice.PDF",
        )

        assertEquals("application/pdf", mimeType)
    }

    @Test
    fun `getBestMimeTypeForOpen uses file extension when server mime type is null or blank`() {
        assertEquals(
            "application/pdf",
            MimetypeIconUtil.getBestMimeTypeForOpen(
                null,
                "Invoice.PDF",
            )
        )
        assertEquals(
            "application/pdf",
            MimetypeIconUtil.getBestMimeTypeForOpen(
                "",
                "Invoice.PDF",
            )
        )
        assertEquals(
            "application/pdf",
            MimetypeIconUtil.getBestMimeTypeForOpen(
                " ",
                "Invoice.PDF",
            )
        )
    }

    @Test
    fun `getBestMimeTypeForOpen does not override specific server mime type`() {
        val mimeType = MimetypeIconUtil.getBestMimeTypeForOpen(
            "text/markdown",
            "Invoice.PDF",
        )

        assertEquals("text/markdown", mimeType)
    }

    @Test
    fun `getBestMimeTypeForOpen falls back to unknown mime type for unknown extension`() {
        val mimeType = MimetypeIconUtil.getBestMimeTypeForOpen(
            MimetypeIconUtil.UNKNOWN_MIME_TYPE,
            "Invoice.notarealextension",
        )

        assertEquals(MimetypeIconUtil.UNKNOWN_MIME_TYPE, mimeType)
    }

    @Test
    fun `getBestMimeTypeForOpen handles filenames without extensions`() {
        assertEquals(
            MimetypeIconUtil.UNKNOWN_MIME_TYPE,
            MimetypeIconUtil.getBestMimeTypeForOpen(
                MimetypeIconUtil.UNKNOWN_MIME_TYPE,
                "Invoice",
            )
        )
        assertEquals(
            MimetypeIconUtil.UNKNOWN_MIME_TYPE,
            MimetypeIconUtil.getBestMimeTypeForOpen(
                MimetypeIconUtil.UNKNOWN_MIME_TYPE,
                "",
            )
        )
        assertEquals(
            MimetypeIconUtil.UNKNOWN_MIME_TYPE,
            MimetypeIconUtil.getBestMimeTypeForOpen(
                MimetypeIconUtil.UNKNOWN_MIME_TYPE,
                null,
            )
        )
    }
}
