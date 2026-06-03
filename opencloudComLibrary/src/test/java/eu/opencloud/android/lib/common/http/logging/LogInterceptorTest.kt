/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2026 openCloud GmbH.
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 *   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 *   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 *
 */
package eu.opencloud.android.lib.common.http.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class LogInterceptorTest {

    @Test
    fun durationStringFormatsSubSecondDuration() {
        assertEquals("duration(0h, 0min, 0s, 999ms)", formatDuration(999L))
    }

    @Test
    fun durationStringFormatsMinuteDuration() {
        assertEquals("duration(0h, 1min, 1s, 0ms)", formatDuration(61_000L))
    }

    @Test
    fun durationStringFormatsHourDuration() {
        assertEquals("duration(1h, 1min, 1s, 0ms)", formatDuration(3_661_000L))
    }

    @Test
    fun durationStringFormatsMixedDuration() {
        assertEquals("duration(2h, 3min, 1s, 234ms)", formatDuration(7_381_234L))
    }

    private fun formatDuration(millis: Long): String {
        val method = LogInterceptor::class.java.getDeclaredMethod("getDurationString", Long::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(LogInterceptor(), millis) as String
    }
}
