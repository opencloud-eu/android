/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2026 openCloud Contributors.
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

import okhttp3.internal.tls.OkHostnameVerifier;
import timber.log.Timber;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * HostnameVerifier with a fallback for self-signed servers explicitly trusted by the user.
 * <p>
 * The RFC 2818/6125 check from {@link OkHostnameVerifier} is applied first. If it fails, the peer
 * certificate is compared against the user-managed known-servers store. A match means the user
 * already opted in to trust exactly this certificate (typically after accepting the untrusted-cert
 * dialog), so the hostname mismatch is tolerated — this covers local self-hosted setups where the
 * URL uses a LAN hostname that is not part of the server certificate's SAN.
 */
public class KnownServersHostnameVerifier implements HostnameVerifier {

    private final Context mContext;
    private final HostnameVerifier mDelegate;

    public KnownServersHostnameVerifier(Context context) {
        this(context, OkHostnameVerifier.INSTANCE);
    }

    KnownServersHostnameVerifier(Context context, HostnameVerifier delegate) {
        if (context == null) {
            throw new IllegalArgumentException("Context may not be NULL!");
        }
        mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        mDelegate = delegate;
    }

    @Override
    public boolean verify(String hostname, SSLSession session) {
        if (mDelegate.verify(hostname, session)) {
            return true;
        }
        try {
            Certificate[] peerCerts = session.getPeerCertificates();
            if (peerCerts.length > 0 && peerCerts[0] instanceof X509Certificate) {
                return NetworkUtils.isCertInKnownServersStore(peerCerts[0], mContext);
            }
        } catch (SSLPeerUnverifiedException e) {
            Timber.d(e, "No peer certificates during hostname verification for %s", hostname);
        }
        return false;
    }
}
