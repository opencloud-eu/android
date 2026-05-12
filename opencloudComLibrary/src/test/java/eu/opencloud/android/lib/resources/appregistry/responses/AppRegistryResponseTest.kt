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
package eu.opencloud.android.lib.resources.appregistry.responses

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRegistryResponseTest {

    private val adapter = Moshi.Builder().build().adapter(AppRegistryResponse::class.java)

    @Test
    fun parsesBackendProvidersWithoutProductName() {
        val response = adapter.fromJson(
            """
            {
              "mime-types": [
                {
                  "mime_type": "application/pdf",
                  "ext": "pdf",
                  "app_providers": [
                    {
                      "name": "OnlyOffice",
                      "icon": "https://some-website.test/onlyoffice-pdf-icon.png"
                    }
                  ],
                  "name": "PDF",
                  "description": "PDF document"
                }
              ]
            }
            """.trimIndent()
        )

        val provider = response!!.value.single().appProviders.single()
        assertEquals("OnlyOffice", provider.name)
        assertNull(provider.productName)
        assertEquals("https://some-website.test/onlyoffice-pdf-icon.png", provider.icon)
    }
}
