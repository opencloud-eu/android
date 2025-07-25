/**
 * openCloud Android client application
 * <p>
 * Copyright (C) 2022 ownCloud GmbH.
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

package eu.opencloud.android.ui.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.snackbar.Snackbar;
import eu.opencloud.android.MainApp;
import eu.opencloud.android.presentation.authentication.AccountUtils;
import eu.opencloud.android.datamodel.FileDataStorageManager;
import eu.opencloud.android.domain.files.model.OCFile;
import eu.opencloud.android.ui.dialog.LoadingDialog;
import timber.log.Timber;

/**
 * Base Activity with common behaviour for activities dealing with openCloud {@link Account}s .
 */
public abstract class BaseActivity extends AppCompatActivity {

    /**
     * openCloud {@link Account} where the main {@link OCFile} handled by the activity is located.
     */
    private Account mCurrentAccount;

    /**
     * Flag to signal that the activity is finishing to enforce the creation of an openCloud {@link Account}.
     */
    private boolean mRedirectingToSetupAccount = false;

    /**
     * Flag to signal when the value of mAccount was set.
     */
    protected boolean mAccountWasSet;

    /**
     * Flag to signal when the value of mAccount was restored from a saved state.
     */
    protected boolean mAccountWasRestored;

    /**
     * Access point to the cached database for the current openCloud {@link Account}.
     */
    private FileDataStorageManager mStorageManager = null;

    private static final String DIALOG_WAIT_TAG = "DIALOG_WAIT";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Timber.v("onNewIntent() start");
        Account current = AccountUtils.getCurrentOpenCloudAccount(this);
        if (current != null && mCurrentAccount != null && !mCurrentAccount.name.equals(current.name)) {
            mCurrentAccount = current;
        }
        Timber.v("onNewIntent() stop");
    }

    /**
     * Since openCloud {@link Account}s can be managed from the system setting menu, the existence of the {@link
     * Account} associated to the instance must be checked every time it is restarted.
     */
    @Override
    protected void onRestart() {
        Timber.v("onRestart() start");
        super.onRestart();
        boolean validAccount = (mCurrentAccount != null && AccountUtils.exists(mCurrentAccount.name, this));
        if (!validAccount) {
            swapToDefaultAccount();
        }
        Timber.v("onRestart() end");
    }

    /**
     * Sets and validates the openCloud {@link Account} associated to the Activity.
     * <p/>
     * If not valid, tries to swap it for other valid and existing openCloud {@link Account}.
     * <p/>
     * POSTCONDITION: updates {@link #mAccountWasSet} and {@link #mAccountWasRestored}.
     *
     * @param account      New {@link Account} to set.
     * @param savedAccount When 'true', account was retrieved from a saved instance state.
     */
    protected void setAccount(Account account, boolean savedAccount) {
        Account oldAccount = mCurrentAccount;
        boolean validAccount =
                (account != null && AccountUtils.setCurrentOpenCloudAccount(getApplicationContext(),
                        account.name));
        if (validAccount) {
            mCurrentAccount = account;
            mAccountWasSet = true;
            mAccountWasRestored = (savedAccount || mCurrentAccount.equals(oldAccount));

        } else {
            swapToDefaultAccount();
        }
    }

    /**
     * Tries to swap the current openCloud {@link Account} for other valid and existing.
     * <p/>
     * If no valid openCloud {@link Account} exists, the the user is requested
     * to create a new openCloud {@link Account}.
     * <p/>
     * POSTCONDITION: updates {@link #mAccountWasSet} and {@link #mAccountWasRestored}.
     */
    protected void swapToDefaultAccount() {
        // default to the most recently used account
        Account newAccount = AccountUtils.getCurrentOpenCloudAccount(getApplicationContext());
        if (newAccount == null) {
            /// no account available: force account creation
            createAccount(true);
            mRedirectingToSetupAccount = true;
            mAccountWasSet = false;
            mAccountWasRestored = false;

        } else {
            mAccountWasSet = true;
            mAccountWasRestored = (newAccount.equals(mCurrentAccount));
            mCurrentAccount = newAccount;
        }
    }

    /**
     * Launches the account creation activity.
     *
     * @param mandatoryCreation When 'true', if an account is not created by the user, the app will be closed.
     *                          To use when no openCloud account is available.
     */
    private void createAccount(boolean mandatoryCreation) {
        AccountManager am = AccountManager.get(getApplicationContext());
        am.addAccount(MainApp.Companion.getAccountType(),
                null,
                null,
                null,
                this,
                new AccountCreationCallback(mandatoryCreation),
                new Handler());
    }

    /**
     * Called when the openCloud {@link Account} associated to the Activity was just updated.
     * <p>
     * Child classes must grant that state depending on the {@link Account} is updated.
     */
    protected void onAccountSet(boolean stateWasRecovered) {
        if (getAccount() != null) {
            mStorageManager = new FileDataStorageManager(getAccount());
            Timber.d("Account set: %s", getAccount().name);
        } else {
            Timber.e("onAccountChanged was called with NULL account associated!");
        }
    }

    public void setAccount(Account account) {
        mCurrentAccount = account;
    }

    /**
     * Getter for the openCloud {@link Account} where the main {@link OCFile} handled by the activity
     * is located.
     *
     * @return OpenCloud {@link Account} where the main {@link OCFile} handled by the activity
     * is located.
     */
    public Account getAccount() {
        return mCurrentAccount;
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mAccountWasSet) {
            onAccountSet(mAccountWasRestored);
        }
    }

    /**
     * @return 'True' when the Activity is finishing to enforce the setup of a new account.
     */
    protected boolean isRedirectingToSetupAccount() {
        return mRedirectingToSetupAccount;
    }

    public FileDataStorageManager getStorageManager() {
        if (mStorageManager == null) {
            if (getAccount() == null) {
                swapToDefaultAccount();
            }
            return mStorageManager = new FileDataStorageManager(getAccount());
        } else {
            return mStorageManager;
        }
    }

    /**
     * Method that gets called when a new account has been successfully created.
     *
     * @param future
     */
    protected void onAccountCreationSuccessful(AccountManagerFuture<Bundle> future) {
        // no special handling in base activity
    }

    /**
     * Helper class handling a callback from the {@link AccountManager} after the creation of
     * a new openCloud {@link Account} finished, successfully or not.
     */
    public class AccountCreationCallback implements AccountManagerCallback<Bundle> {

        boolean mMandatoryCreation;

        /**
         * Constuctor
         *
         * @param mandatoryCreation When 'true', if an account was not created, the app is closed.
         */
        public AccountCreationCallback(boolean mandatoryCreation) {
            mMandatoryCreation = mandatoryCreation;
        }

        @Override
        public void run(AccountManagerFuture<Bundle> future) {
            BaseActivity.this.mRedirectingToSetupAccount = false;
            boolean accountWasSet = false;
            if (future != null) {
                try {
                    Bundle result;
                    result = future.getResult();
                    String name = result.getString(AccountManager.KEY_ACCOUNT_NAME);
                    String type = result.getString(AccountManager.KEY_ACCOUNT_TYPE);
                    if (AccountUtils.setCurrentOpenCloudAccount(getApplicationContext(), name)) {
                        setAccount(new Account(name, type), false);
                        accountWasSet = true;
                    }

                    onAccountCreationSuccessful(future);
                } catch (OperationCanceledException e) {
                    Timber.d("Account creation canceled");

                } catch (Exception e) {
                    Timber.e(e, "Account creation finished in exception");
                }

            } else {
                Timber.e("Account creation callback with null bundle");
            }
            if (mMandatoryCreation && !accountWasSet) {
                finish();
            }
        }
    }

    /**
     * Show loading dialog
     */
    public void showLoadingDialog(int messageId) {
        // grant that only one waiting dialog is shown
        dismissLoadingDialog();
        // Construct dialog
        Fragment frag = getSupportFragmentManager().findFragmentByTag(DIALOG_WAIT_TAG);
        if (frag == null) {
            Timber.d("show loading dialog");
            LoadingDialog loading = LoadingDialog.newInstance(messageId, false);
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            loading.show(ft, DIALOG_WAIT_TAG);
            fm.executePendingTransactions();
        }
    }

    /**
     * Dismiss loading dialog
     */
    public void dismissLoadingDialog() {
        Fragment frag = getSupportFragmentManager().findFragmentByTag(DIALOG_WAIT_TAG);
        if (frag == null) {
            return;
        }

        Timber.d("dismiss loading dialog");
        LoadingDialog loading = (LoadingDialog) frag;
        loading.dismiss();
    }

    /**
     * Show a temporary message in a Snackbar bound to the content view
     *
     * @param message Message to show.
     */
    public void showSnackMessage(String message) {
        final View rootView = findViewById(android.R.id.content);

        if (rootView == null) {
            // If root view is not available don't let the app brake. show the notification anyway.
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
    }
}
