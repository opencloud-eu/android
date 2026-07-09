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

package eu.opencloud.android.lib.common.network

import android.content.Context
import android.security.KeyChain
import timber.log.Timber
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.X509KeyManager

object ClientCertificateManager {

    /**
     * Builds the [KeyManager]s that present the client certificate identified by [alias] (an alias
     * from the Android [KeyChain]) during the TLS handshake. The alias is resolved per account, so
     * callers pass the alias bound to the account/login that owns the connection.
     *
     * @return null when [alias] is null/blank, i.e. mTLS is not configured for this connection.
     */
    fun getKeyManagers(context: Context, alias: String?): Array<KeyManager>? {
        if (alias.isNullOrBlank()) return null
        return arrayOf(KeyChainKeyManager(context.applicationContext, alias))
    }

    private class KeyChainKeyManager(
        private val appContext: Context,
        private val alias: String,
    ) : X509KeyManager {

        // We always present the single configured alias: it is the only certificate the user
        // selected for this account, so there is nothing to choose between.
        override fun chooseClientAlias(keyType: Array<String>?, issuers: Array<Principal>?, socket: Socket?): String = alias

        override fun getClientAliases(keyType: String?, issuers: Array<Principal>?): Array<String> = arrayOf(alias)

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
            runCatching { KeyChain.getCertificateChain(appContext, this.alias) }
                .onFailure { Timber.e(it, "Failed to get certificate chain for alias: %s", this.alias) }
                .getOrNull()

        override fun getPrivateKey(alias: String?): PrivateKey? =
            runCatching { KeyChain.getPrivateKey(appContext, this.alias) }
                .onFailure { Timber.e(it, "Failed to get private key for alias: %s", this.alias) }
                .getOrNull()

        override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?): String? = null

        override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null
    }
}
