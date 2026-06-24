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

package eu.opencloud.android.lib.resources.files.tus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TusChecksumHelperTest {

    @Test
    fun sha1Hex_returnsKnownDigest() {
        val digest = TusChecksumHelper.sha1Hex(ByteArrayInputStream("hello".toByteArray()))

        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", digest)
    }

    @Test
    fun copyAndSha1Hex_writesBytesAndReturnsDigest() {
        val bytes = "original-upload-source".toByteArray()
        val output = ByteArrayOutputStream()

        val result = TusChecksumHelper.copyAndSha1Hex(ByteArrayInputStream(bytes), output)

        assertEquals(bytes.size.toLong(), result.bytesCopied)
        assertEquals(expectedSha1Hex(bytes), result.sha1Hex)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun sha1Base64ForFileRange_returnsDigestForRequestedRange() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val file = tempFile(bytes)
        val range = bytes.copyOfRange(3, 8)

        val digest = TusChecksumHelper.sha1Base64ForFileRange(file, offset = 3, length = 5)

        assertEquals(expectedSha1Base64(range), digest)
    }

    @Test
    fun copyAndSha1Hex_countsLargeCopiesPastBufferSize() {
        val bytes = ByteArray(140_000) { index -> (index % 251).toByte() }
        val output = ByteArrayOutputStream()

        val result = TusChecksumHelper.copyAndSha1Hex(ByteArrayInputStream(bytes), output)

        assertEquals(bytes.size.toLong(), result.bytesCopied)
        assertEquals(expectedSha1Hex(bytes), result.sha1Hex)
        assertArrayEquals(bytes, output.toByteArray())
    }

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("tus-checksum", ".bin").apply {
            writeBytes(bytes)
        }

    private fun expectedSha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

    private fun expectedSha1Base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest(bytes)
        )
}
