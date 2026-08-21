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

package eu.opencloud.android.presentation.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLockSecretHash {
    private const val PREFIX = "pbkdf2-sha256"
    private const val VERSION = "v1"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_LENGTH_BITS = 256
    private const val FIELD_SEPARATOR = ":"
    private const val PARTS_COUNT = 5
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    private val secureRandom = SecureRandom()

    fun hash(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val hash = pbkdf2(secret, salt, ITERATIONS)

        return listOf(
            PREFIX,
            VERSION,
            ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash),
        ).joinToString(FIELD_SEPARATOR)
    }

    fun verify(secret: String, storedSecret: String): Boolean =
        if (isHash(storedSecret)) {
            verifyHash(secret, storedSecret)
        } else {
            MessageDigest.isEqual(
                secret.toByteArray(StandardCharsets.UTF_8),
                storedSecret.toByteArray(StandardCharsets.UTF_8)
            )
        }

    fun isHash(storedSecret: String): Boolean =
        storedSecret.startsWith("$PREFIX$FIELD_SEPARATOR$VERSION$FIELD_SEPARATOR")

    private fun verifyHash(secret: String, storedHash: String): Boolean {
        val parts = storedHash.split(FIELD_SEPARATOR)
        if (parts.size != PARTS_COUNT || parts[0] != PREFIX || parts[1] != VERSION) return false

        return try {
            val iterations = parts[2].toInt()
            val salt = Base64.getDecoder().decode(parts[3])
            val expectedHash = Base64.getDecoder().decode(parts[4])
            val actualHash = pbkdf2(secret, salt, iterations)
            MessageDigest.isEqual(expectedHash, actualHash)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun pbkdf2(secret: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
