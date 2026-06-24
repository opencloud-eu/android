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
 *
 */
package eu.opencloud.android.lib.common.http.methods.webdav.properties

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser

/**
 * Parses `<oc:checksums>` from a PROPFIND response.
 *
 * Per the OpenCloud WebDAV docs, the body is a single `<oc:checksum>` child whose text is a
 * whitespace-separated list of `ALGORITHM:value` pairs (documented as a historical quirk —
 * "this value is not an array, but a string separated by whitespaces"):
 *
 *     <oc:checksums>
 *       <oc:checksum>SHA1:1c68ea... MD5:2205e4... ADLER32:058801ab</oc:checksum>
 *     </oc:checksums>
 *
 * We split on whitespace at parse time so callers get a clean `List<String>` of individual
 * `ALGORITHM:value` entries. Tolerant of (a) multiple `<oc:checksum>` children, in case the
 * server quirk is ever fixed, and (b) extra whitespace inside the text node.
 */
class OCChecksums(val checksums: List<String>) : Property {

    override fun toString() = "checksums =[" + checksums.joinToString(", ") + "]"

    class Factory : PropertyFactory {
        override fun getName(): Property.Name = NAME

        override fun create(parser: XmlPullParser): OCChecksums {
            val raw = mutableListOf<String>()
            XmlUtils.readTextPropertyList(parser, CHECKSUM_NAME, raw)
            // Flatten: each text node may itself contain multiple whitespace-separated
            // "ALGORITHM:value" entries because of the documented server-side quirk.
            val flattened = raw.flatMap { it.split(WHITESPACE) }.filter { it.isNotEmpty() }
            return OCChecksums(flattened)
        }
    }

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_OWNCLOUD, "checksums")
        private val CHECKSUM_NAME = Property.Name(XmlUtils.NS_OWNCLOUD, "checksum")
        private val WHITESPACE = Regex("\\s+")
    }
}
