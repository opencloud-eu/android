/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2026 OpenCloud GmbH.
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
 */

package eu.opencloud.android.lib.resources.files

import android.os.Build
import at.bitfire.dav4jvm.PropertyRegistry
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.XmlUtils
import com.squareup.moshi.Moshi
import eu.opencloud.android.lib.common.http.methods.webdav.properties.OCIntegrityId
import eu.opencloud.android.lib.resources.spaces.responses.SpaceResponse
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Vaults are encrypted folders and spaces created by the OpenCloud Web vault app.
 * See https://github.com/opencloud-eu/android/issues/228
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], manifest = Config.NONE)
class VaultParsingTest {

    private val spaceAdapter = Moshi.Builder().build().adapter(SpaceResponse::class.java)

    @Test
    fun vaultFolderIsDetected() {
        val vaultFolder = parseRemoteFiles().single { it.remotePath == "/Neuer Ordner.vault/" }
        assertTrue(vaultFolder.isVault)
    }

    @Test
    fun regularFolderIsNotAVault() {
        // The property is also listed for folders without it - in the 404 propstat, which must be ignored
        val regularFolder = parseRemoteFiles().single { it.remotePath == "/Regular/" }
        assertFalse(regularFolder.isVault)
    }

    @Test
    fun vaultSpaceContentTypeIsParsed() {
        val space = spaceAdapter.fromJson(spaceJson(""""@libre.graph.contentType": "application/vnd.opencloud.vault","""))!!
        assertEquals("application/vnd.opencloud.vault", space.libreGraphContentType)
    }

    @Test
    fun regularSpaceHasNoContentType() {
        val space = spaceAdapter.fromJson(spaceJson(""))!!
        assertNull(space.libreGraphContentType)
    }

    private fun spaceJson(extra: String) = """
        {
          $extra
          "driveType": "project",
          "id": "$SPACE_ID",
          "name": "Encrypted Space",
          "webUrl": "https://demo.opencloud.eu/f/$SPACE_ID",
          "root": { "id": "$SPACE_ID!5fd14bf4", "webDavUrl": "https://demo.opencloud.eu/dav/spaces/$SPACE_ID" }
        }
    """.trimIndent()

    private fun parseRemoteFiles(): List<RemoteFile> {
        PropertyRegistry.register(OCIntegrityId.Factory())

        val responses = mutableListOf<Response>()
        val parser = XmlUtils.newPullParser().apply { setInput(StringReader(PROPFIND_RESPONSE)) }
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.namespace == XmlUtils.NS_WEBDAV && parser.name == "response") {
                Response.parse(parser, SPACE_WEBDAV_URL.toHttpUrl()) { response, _ -> responses.add(response) }
            }
            eventType = parser.next()
        }

        return responses.map { RemoteFile.getRemoteFileFromDav(it, "alan", "alan", SPACE_WEBDAV_URL) }
    }

    companion object {
        private const val SPACE_ID = "d21381aa-01dc-45cb-a5b8-e6880c47c954\$4a67caa3-cff0-4899-a69d-816848d23b9d"
        private const val SPACE_WEBDAV_URL = "https://demo.opencloud.eu/dav/spaces/$SPACE_ID"

        // Vault folder response as returned by demo.opencloud.eu for a folder created by the OpenCloud Web vault app.
        // The regular folder lists the unknown property in the 404 propstat.
        private val PROPFIND_RESPONSE = """
            <d:multistatus xmlns:s="http://sabredav.org/ns" xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response>
                <d:href>/dav/spaces/$SPACE_ID/Neuer%20Ordner.vault/</d:href>
                <d:propstat><d:prop>
                  <oc:name>Neuer Ordner.vault</oc:name>
                  <d:getetag>"137340bf94d665aa48a209236c5e5246"</d:getetag>
                  <d:resourcetype><d:collection/></d:resourcetype>
                  <integrity-id xmlns="ocrclone">UkNMT05FAACfQDChK3k6+BoVBNQIgayGqNhjvJ2254BsRcxPn2He3iQUiq9hFlY89aId6jz54Brnyodo3h3ADKJORPJbBsmE9ZOV0rf8VQaxydgb
            </integrity-id>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/spaces/$SPACE_ID/Regular/</d:href>
                <d:propstat><d:prop>
                  <oc:name>Regular</oc:name>
                  <d:getetag>"0123456789abcdef0123456789abcdef"</d:getetag>
                  <d:resourcetype><d:collection/></d:resourcetype>
                </d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                <d:propstat><d:prop>
                  <integrity-id xmlns="ocrclone"></integrity-id>
                </d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
    }
}
