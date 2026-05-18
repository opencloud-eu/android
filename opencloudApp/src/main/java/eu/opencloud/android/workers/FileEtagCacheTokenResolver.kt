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

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

internal object FileEtagCacheTokenResolver {
    private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    data class ResolvedEtags(
        val etag: String?,
        val remoteEtag: String?,
    )

    fun resolve(
        serverEtag: String?,
        existingEtag: String?,
        existingRemoteEtag: String?,
        localContentHashToken: String? = null,
    ): ResolvedEtags {
        val normalizedServerEtag = normalizeToken(serverEtag)
        val normalizedHashToken = normalizeToken(localContentHashToken)
        return resolveWithNormalizedValues(
            normalizedServerEtag = normalizedServerEtag,
            existingEtag = existingEtag,
            existingRemoteEtag = existingRemoteEtag,
            normalizedHashToken = normalizedHashToken,
        )
    }

    fun resolve(
        serverEtag: String?,
        existingEtag: String?,
        existingRemoteEtag: String?,
        localContentHashTokenProvider: () -> String?,
    ): ResolvedEtags {
        val normalizedServerEtag = normalizeToken(serverEtag)
        val normalizedHashToken = if (normalizedServerEtag == null) {
            normalizeToken(localContentHashTokenProvider())
        } else {
            null
        }
        return resolveWithNormalizedValues(
            normalizedServerEtag = normalizedServerEtag,
            existingEtag = existingEtag,
            existingRemoteEtag = existingRemoteEtag,
            normalizedHashToken = normalizedHashToken,
        )
    }

    fun sha256Token(file: File): String? =
        try {
            if (!file.isFile || !file.canRead()) {
                null
            } else {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                FileInputStream(file).use { input ->
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        digest.update(buffer, 0, bytesRead)
                    }
                }
                "sha256:${digest.digest().toHexString()}"
            }
        } catch (ignored: IOException) {
            null
        } catch (ignored: SecurityException) {
            null
        }

    private fun resolveWithNormalizedValues(
        normalizedServerEtag: String?,
        existingEtag: String?,
        existingRemoteEtag: String?,
        normalizedHashToken: String?,
    ): ResolvedEtags =
        ResolvedEtags(
            etag = normalizedServerEtag ?: existingEtag,
            remoteEtag = normalizedServerEtag
                ?: normalizedHashToken
                ?: normalizeToken(existingRemoteEtag)
                ?: normalizeToken(existingEtag),
        )

    private fun normalizeToken(value: String?): String? =
        value
            ?.trim()
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() }

    private fun ByteArray.toHexString(): String {
        val output = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xff
            output.append(HEX_CHARS[value ushr 4])
            output.append(HEX_CHARS[value and 0x0f])
        }
        return output.toString()
    }
}
