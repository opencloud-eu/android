/**
 * openCloud Android client application
 *
 * @author sassman
 * @author David A. Velasco
 * Copyright (C) 2011  Bartek Przybylski
 * Copyright (C) 2020 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;

import eu.opencloud.android.datamodel.FileDataStorageManager;
import eu.opencloud.android.lib.common.OpenCloudAccount;
import eu.opencloud.android.lib.common.OpenCloudClient;
import eu.opencloud.android.lib.common.SingleSessionManager;
import eu.opencloud.android.lib.common.accounts.AccountUtils.AccountNotFoundException;

import java.io.IOException;

/**
 * Base synchronization adapter for openCloud to discover full account.
 * <p>
 * Implements the standard {@link AbstractThreadedSyncAdapter}.
 */
public abstract class AbstractOpenCloudSyncAdapter extends
        AbstractThreadedSyncAdapter {

    private AccountManager accountManager;
    private Account account;
    private ContentProviderClient mContentProviderClient;
    private FileDataStorageManager mStoreManager;

    private OpenCloudClient mClient = null;

    public AbstractOpenCloudSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        this.setAccountManager(AccountManager.get(context));
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public void setAccountManager(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public ContentProviderClient getContentProviderClient() {
        return mContentProviderClient;
    }

    public void setContentProviderClient(ContentProviderClient contentProvider) {
        this.mContentProviderClient = contentProvider;
    }

    public void setStorageManager(FileDataStorageManager storage_manager) {
        mStoreManager = storage_manager;
    }

    public FileDataStorageManager getStorageManager() {
        return mStoreManager;
    }

    protected void initClientForCurrentAccount() throws OperationCanceledException,
            AuthenticatorException, IOException, AccountNotFoundException {
        OpenCloudAccount ocAccount = new OpenCloudAccount(account, getContext());
        mClient = SingleSessionManager.getDefaultSingleton().
                getClientFor(ocAccount, getContext());
    }

    protected OpenCloudClient getClient() {
        return mClient;
    }

}
