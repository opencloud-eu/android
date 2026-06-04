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

package eu.opencloud.android.workers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentUriUploadCacheValidatorTest {

    @Test
    fun `exact large cache size is valid`() {
        val fileSize = 5_832_800_958L

        val isValid = ContentUriUploadCacheValidator.isValidCacheSize(
            actualSize = fileSize,
            expectedSize = fileSize,
        )

        assertTrue(isValid)
    }

    @Test
    fun `partial non-zero cache size is invalid`() {
        val isValid = ContentUriUploadCacheValidator.isValidCacheSize(
            actualSize = 1_180_123_136L,
            expectedSize = 5_832_800_958L,
        )

        assertFalse(isValid)
    }

    @Test
    fun `larger than expected cache size is invalid`() {
        val isValid = ContentUriUploadCacheValidator.isValidCacheSize(
            actualSize = 5_832_800_959L,
            expectedSize = 5_832_800_958L,
        )

        assertFalse(isValid)
    }

    @Test
    fun `zero byte cache size is invalid`() {
        val isValid = ContentUriUploadCacheValidator.isValidCacheSize(
            actualSize = 0L,
            expectedSize = 5_832_800_958L,
        )

        assertFalse(isValid)
    }

    @Test
    fun `unknown expected size keeps existing non-zero behavior`() {
        val isValid = ContentUriUploadCacheValidator.isValidCacheSize(
            actualSize = 42L,
            expectedSize = -1L,
        )

        assertTrue(isValid)
    }

    @Test
    fun `non-positive expected size still rejects zero byte cache`() {
        val isValid = ContentUriUploadCacheValidator.isValidCacheSize(
            actualSize = 0L,
            expectedSize = -1L,
        )

        assertFalse(isValid)
    }
}
