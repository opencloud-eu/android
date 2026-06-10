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

import android.util.Base64
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min

object TusChecksumHelper {
    private const val BUFFER_SIZE = 64 * 1024
    private const val SHA1_DIGEST_ALGORITHM = "SHA-1"
    const val SHA1_WIRE_ALGORITHM = "sha1"
    private const val SHA1_METADATA_ALGORITHM = "SHA1"

    data class StoredChecksum(
        val algorithm: String,
        val hex: String,
    ) {
        val storageValue: String
            get() = "${algorithm.lowercase(Locale.ROOT)}:${hex.lowercase(Locale.ROOT)}"

        val metadataValue: String
            get() = "${metadataAlgorithm()} ${hex.lowercase(Locale.ROOT)}"

        val uploadAlgorithm: String
            get() = algorithm.lowercase(Locale.ROOT)

        private fun metadataAlgorithm(): String =
            if (algorithm.lowercase(Locale.ROOT) == SHA1_WIRE_ALGORITHM) SHA1_METADATA_ALGORITHM
            else algorithm.uppercase(Locale.ROOT)
    }

    data class CopyChecksumResult(
        val bytesCopied: Long,
        val sha1Hex: String,
    )

    fun storedSha1(hex: String): StoredChecksum =
        StoredChecksum(
            algorithm = SHA1_WIRE_ALGORITHM,
            hex = hex.lowercase(Locale.ROOT),
        )

    fun parseStoredChecksum(value: String?): StoredChecksum? {
        if (value.isNullOrBlank()) return null

        val separatorIndex = value.indexOf(':')
        if (separatorIndex <= 0 || separatorIndex == value.lastIndex) return null

        val algorithm = value.substring(0, separatorIndex).trim().lowercase(Locale.ROOT)
        val hex = value.substring(separatorIndex + 1).trim().lowercase(Locale.ROOT)
        if (algorithm.isBlank() || hex.isBlank()) return null

        return StoredChecksum(algorithm = algorithm, hex = hex)
    }

    fun sha1Hex(file: File): String =
        FileInputStream(file).use { input ->
            sha1Hex(input)
        }

    fun sha1Hex(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance(SHA1_DIGEST_ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().toHex()
    }

    fun copyAndSha1Hex(inputStream: InputStream, outputStream: OutputStream): CopyChecksumResult {
        val digest = MessageDigest.getInstance(SHA1_DIGEST_ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        var copiedBytes = 0L

        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            outputStream.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            copiedBytes += read.toLong()
        }

        return CopyChecksumResult(
            bytesCopied = copiedBytes,
            sha1Hex = digest.digest().toHex(),
        )
    }

    fun uploadChecksumHeader(file: File, offset: Long, length: Long, algorithm: String): String {
        if (algorithm.lowercase(Locale.ROOT) != SHA1_WIRE_ALGORITHM) {
            throw IllegalArgumentException("Unsupported TUS checksum algorithm: $algorithm")
        }
        val base64Digest = sha1Base64ForFileRange(file, offset, length)
        return "$SHA1_WIRE_ALGORITHM $base64Digest"
    }

    fun sha1Base64ForFileRange(file: File, offset: Long, length: Long): String {
        require(offset >= 0) { "Offset must be non-negative" }
        require(length >= 0) { "Length must be non-negative" }

        val digest = MessageDigest.getInstance(SHA1_DIGEST_ALGORITHM)
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = length

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            while (remaining > 0) {
                val read = raf.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                if (read == -1) {
                    throw EOFException("Unable to read $length bytes from ${file.absolutePath} at offset $offset")
                }
                digest.update(buffer, 0, read)
                remaining -= read.toLong()
            }
        }

        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
}
