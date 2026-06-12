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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileEtagNormalizerTest {

    @Test
    fun `normalize trims surrounding whitespace and quotes`() {
        assertEquals("server-etag", FileEtagNormalizer.normalize(" \"server-etag\" "))
    }

    @Test
    fun `normalize keeps unquoted etag`() {
        assertEquals("server-etag", FileEtagNormalizer.normalize("server-etag"))
    }

    @Test
    fun `normalize returns null for blank values`() {
        assertNull(FileEtagNormalizer.normalize(null))
        assertNull(FileEtagNormalizer.normalize(""))
        assertNull(FileEtagNormalizer.normalize(" "))
        assertNull(FileEtagNormalizer.normalize("\"\""))
    }
}
