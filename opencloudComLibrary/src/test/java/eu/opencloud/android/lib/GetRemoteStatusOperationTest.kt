/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2021 ownCloud GmbH.
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
package eu.opencloud.android.lib

import android.net.Uri
import android.os.Build
import eu.opencloud.android.lib.resources.status.GetRemoteStatusOperation
import eu.opencloud.android.lib.resources.status.HttpScheme.HTTPS_PREFIX
import eu.opencloud.android.lib.resources.status.HttpScheme.HTTP_PREFIX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], manifest = Config.NONE)
class GetRemoteStatusOperationTest {

    @Test
    fun `uses http or https - ok - http`() {
        assertTrue(GetRemoteStatusOperation.usesHttpOrHttps(Uri.parse(HTTP_SOME_OPENCLOUD)))
    }

    @Test
    fun `uses http or https - ok - https`() {
        assertTrue(GetRemoteStatusOperation.usesHttpOrHttps(Uri.parse(HTTPS_SOME_OPENCLOUD)))
    }

    @Test
    fun `uses http or https - ok - no http or https`() {
        assertFalse(GetRemoteStatusOperation.usesHttpOrHttps(Uri.parse(SOME_OPENCLOUD)))
    }

    @Test
    fun `build full https url - ok - http`() {
        assertEquals(
            Uri.parse(HTTP_SOME_OPENCLOUD),
            GetRemoteStatusOperation.buildFullHttpsUrl(Uri.parse(HTTP_SOME_OPENCLOUD))
        )
    }

    @Test
    fun `build full https url - ok - https`() {
        assertEquals(
            Uri.parse(HTTPS_SOME_OPENCLOUD),
            GetRemoteStatusOperation.buildFullHttpsUrl(Uri.parse(HTTPS_SOME_OPENCLOUD))
        )
    }

    @Test
    fun `build full https url - ok - no prefix`() {
        assertEquals(
            Uri.parse(HTTPS_SOME_OPENCLOUD),
            GetRemoteStatusOperation.buildFullHttpsUrl(Uri.parse(SOME_OPENCLOUD))
        )
    }

    @Test
    fun `build full https url - ok - no https with subdir`() {
        assertEquals(
            Uri.parse(HTTPS_SOME_OPENCLOUD_WITH_SUBDIR),
            GetRemoteStatusOperation.buildFullHttpsUrl(
                Uri.parse(HTTPS_SOME_OPENCLOUD_WITH_SUBDIR)
            )
        )
    }

    @Test
    fun `build full https url - ok - no prefix with subdir`() {
        assertEquals(
            Uri.parse(HTTPS_SOME_OPENCLOUD_WITH_SUBDIR),
            GetRemoteStatusOperation.buildFullHttpsUrl(
                Uri.parse(SOME_OPENCLOUD_WITH_SUBDIR)
            )
        )
    }

    @Test
    fun `build full https url - ok - ip`() {
        assertEquals(Uri.parse(HTTPS_SOME_IP), GetRemoteStatusOperation.buildFullHttpsUrl(Uri.parse(SOME_IP)))
    }

    @Test
    fun `build full https url - ok - http ip`() {
        assertEquals(Uri.parse(HTTP_SOME_IP), GetRemoteStatusOperation.buildFullHttpsUrl(Uri.parse(HTTP_SOME_IP)))
    }

    @Test
    fun `build full https url - ok - ip with port`() {
        assertEquals(
            Uri.parse(HTTPS_SOME_IP_WITH_PORT),
            GetRemoteStatusOperation.buildFullHttpsUrl(Uri.parse(SOME_IP_WITH_PORT))
        )
    }

    @Test
    fun `build full https url - ok - ip with http and port`() {
        assertEquals(
            Uri.parse(HTTP_SOME_IP_WITH_PORT),
            GetRemoteStatusOperation.buildFullHttpsUrl(Uri.parse(HTTP_SOME_IP_WITH_PORT))
        )
    }

    companion object {
        const val SOME_OPENCLOUD = "some_opencloud.eu"
        const val HTTP_SOME_OPENCLOUD = "$HTTP_PREFIX$SOME_OPENCLOUD"
        const val HTTPS_SOME_OPENCLOUD = "$HTTPS_PREFIX$SOME_OPENCLOUD"

        const val SOME_OPENCLOUD_WITH_SUBDIR = "some_opencloud.eu/subdir"
        const val HTTP_SOME_OPENCLOUD_WITH_SUBDIR = "$HTTP_PREFIX$SOME_OPENCLOUD_WITH_SUBDIR"
        const val HTTPS_SOME_OPENCLOUD_WITH_SUBDIR = "$HTTPS_PREFIX$SOME_OPENCLOUD_WITH_SUBDIR"

        const val SOME_IP = "184.123.185.12"
        const val HTTP_SOME_IP = "$HTTP_PREFIX$SOME_IP"
        const val HTTPS_SOME_IP = "$HTTPS_PREFIX$SOME_IP"

        const val SOME_IP_WITH_PORT = "184.123.185.12:5678"
        const val HTTP_SOME_IP_WITH_PORT = "$HTTP_PREFIX$SOME_IP_WITH_PORT"
        const val HTTPS_SOME_IP_WITH_PORT = "$HTTPS_PREFIX$SOME_IP_WITH_PORT"
    }
}
