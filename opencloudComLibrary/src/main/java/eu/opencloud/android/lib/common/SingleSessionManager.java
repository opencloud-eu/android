/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2020 ownCloud GmbH.
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

package eu.opencloud.android.lib.common;

import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.net.Uri;

import eu.opencloud.android.lib.common.accounts.AccountUtils;
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentials;
import timber.log.Timber;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author David A. Velasco
 * @author masensio
 * @author Christian Schabesberger
 * @author David González Verdugo
 */

public class SingleSessionManager {

    private static SingleSessionManager sDefaultSingleton;
    private static String sUserAgent;
    private static ConnectionValidator sConnectionValidator;

    private ConcurrentMap<String, OpenCloudClient> mClientsWithKnownUsername = new ConcurrentHashMap<>();
    private ConcurrentMap<String, OpenCloudClient> mClientsWithUnknownUsername = new ConcurrentHashMap<>();

    public static SingleSessionManager getDefaultSingleton() {
        if (sDefaultSingleton == null) {
            sDefaultSingleton = new SingleSessionManager();
        }
        return sDefaultSingleton;
    }

    public static void setConnectionValidator(ConnectionValidator connectionValidator) {
        sConnectionValidator = connectionValidator;
    }

    public static ConnectionValidator getConnectionValidator() {
        return sConnectionValidator;
    }

    public static String getUserAgent() {
        return sUserAgent;
    }

    public static void setUserAgent(String userAgent) {
        sUserAgent = userAgent;
    }

    private static OpenCloudClient createOpenCloudClient(Uri uri,
                                                       Context context,
                                                       ConnectionValidator connectionValidator,
                                                       SingleSessionManager singleSessionManager) {
        OpenCloudClient client = new OpenCloudClient(uri, connectionValidator, true, singleSessionManager, context);
        return client;
    }

    public OpenCloudClient getClientFor(OpenCloudAccount account,
                                       Context context) throws OperationCanceledException,
            AuthenticatorException, IOException {
        return getClientFor(account, context, getConnectionValidator());
    }

    public OpenCloudClient getClientFor(OpenCloudAccount account,
                                       Context context,
                                       ConnectionValidator connectionValidator) throws OperationCanceledException,
            AuthenticatorException, IOException {

        Timber.d("getClientFor starting ");
        if (account == null) {
            throw new IllegalArgumentException("Cannot get an OpenCloudClient for a null account");
        }

        OpenCloudClient client = null;
        String accountName = account.getName();
        String sessionName = account.getCredentials() == null ? "" :
                AccountUtils.buildAccountName(account.getBaseUri(), account.getCredentials().getAuthToken());

        if (accountName != null) {
            client = mClientsWithKnownUsername.get(accountName);
        }
        boolean reusingKnown = false;    // just for logs
        if (client == null) {
            if (accountName != null) {
                client = mClientsWithUnknownUsername.remove(sessionName);
                if (client != null) {
                    Timber.v("reusing client for session %s", sessionName);

                    mClientsWithKnownUsername.put(accountName, client);
                    Timber.v("moved client to account %s", accountName);
                }
            } else {
                client = mClientsWithUnknownUsername.get(sessionName);
            }
        } else {
            Timber.v("reusing client for account %s", accountName);
            if (client.getAccount() != null &&
                    client.getAccount().getCredentials() != null &&
                    (client.getAccount().getCredentials().getAuthToken() == null || client.getAccount().getCredentials().getAuthToken().isEmpty())
            ) {
                Timber.i("Client " + client.getAccount().getName() + " needs to refresh credentials");

                //the next two lines are a hack because okHttpclient is used as a singleton instead of being an
                //injected instance that can be deleted when required
                client.clearCookies();
                client.clearCredentials();

                client.setAccount(account);

                account.loadCredentials(context);
                client.setCredentials(account.getCredentials());

                Timber.i("Client " + account.getName() + " with credentials size" + client.getAccount().getCredentials().getAuthToken().length());
            }
            reusingKnown = true;
        }

        if (client == null) {
            // no client to reuse - create a new one
            client = createOpenCloudClient(
                    account.getBaseUri(),
                    context,
                    connectionValidator,
                    this); // TODO remove dependency on OpenCloudClientFactory

            //the next two lines are a hack because okHttpclient is used as a singleton instead of being an
            //injected instance that can be deleted when required
            client.clearCookies();
            client.clearCredentials();

            client.setAccount(account);

            account.loadCredentials(context);
            client.setCredentials(account.getCredentials());

            if (accountName != null) {
                mClientsWithKnownUsername.put(accountName, client);
                Timber.v("new client for account %s", accountName);

            } else {
                mClientsWithUnknownUsername.put(sessionName, client);
                Timber.v("new client for session %s", sessionName);
            }
        } else {
            if (!reusingKnown) {
                Timber.v("reusing client for session %s", sessionName);
            }

            keepUriUpdated(account, client);
        }
        Timber.d("getClientFor finishing ");
        return client;
    }

    public void removeClientFor(OpenCloudAccount account) {
        Timber.d("removeClientFor starting ");

        if (account == null) {
            return;
        }

        OpenCloudClient client;
        String accountName = account.getName();
        if (accountName != null) {
            client = mClientsWithKnownUsername.remove(accountName);
            if (client != null) {
                Timber.v("Removed client for account %s", accountName);
                return;
            } else {
                Timber.v("No client tracked for  account %s", accountName);
            }
        }

        mClientsWithUnknownUsername.clear();

        Timber.d("removeClientFor finishing ");
    }

    public void refreshCredentialsForAccount(String accountName, OpenCloudCredentials credentials) {
        OpenCloudClient openCloudClient = mClientsWithKnownUsername.get(accountName);
        if (openCloudClient == null) {
            return;
        }
        openCloudClient.setCredentials(credentials);
        mClientsWithKnownUsername.replace(accountName, openCloudClient);
    }

    // this method is just a patch; we need to distinguish accounts in the same host but
    // different paths; but that requires updating the accountNames for apps upgrading
    private void keepUriUpdated(OpenCloudAccount account, OpenCloudClient reusedClient) {
        Uri recentUri = account.getBaseUri();
        if (!recentUri.equals(reusedClient.getBaseUri())) {
            reusedClient.setBaseUri(recentUri);
        }
    }
}
