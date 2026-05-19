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

package eu.opencloud.android.lib.common.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.KeyChain;

import timber.log.Timber;

import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class ClientCertificateManager {

    private static final String PREFS_NAME = "mtls_prefs";
    private static final String PREF_KEY_ALIAS = "key_alias";
    private static final String PREF_MTLS_ENABLED = "mtls_enabled";

    public static void setAlias(Context context, String alias) {
        getPrefs(context).edit()
                .putString(PREF_KEY_ALIAS, alias)
                .putBoolean(PREF_MTLS_ENABLED, true)
                .apply();
    }

    public static void removeAlias(Context context) {
        getPrefs(context).edit()
                .remove(PREF_KEY_ALIAS)
                .putBoolean(PREF_MTLS_ENABLED, false)
                .apply();
    }

    public static String getAlias(Context context) {
        return getPrefs(context).getString(PREF_KEY_ALIAS, null);
    }

    public static boolean isMtlsEnabled(Context context) {
        return getPrefs(context).getBoolean(PREF_MTLS_ENABLED, false);
    }

    public static void setMtlsEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(PREF_MTLS_ENABLED, enabled).apply();
    }

    public static KeyManager[] getKeyManagers(Context context) {
        if (!isMtlsEnabled(context)) {
            return null;
        }

        String alias = getAlias(context);
        if (alias == null) {
            return null;
        }

        return new KeyManager[]{new KeyChainKeyManager(context, alias)};
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static class KeyChainKeyManager implements X509KeyManager {
        private final Context context;
        private final String alias;

        KeyChainKeyManager(Context context, String alias) {
            this.context = context.getApplicationContext();
            this.alias = alias;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return alias;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            try {
                return KeyChain.getCertificateChain(context, alias);
            } catch (Exception e) {
                Timber.e(e, "Failed to get certificate chain for alias: %s", alias);
                return null;
            }
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            try {
                return KeyChain.getPrivateKey(context, alias);
            } catch (Exception e) {
                Timber.e(e, "Failed to get private key for alias: %s", alias);
                return null;
            }
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return new String[]{alias};
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return null;
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return null;
        }
    }
}
