/**
 * openCloud Android client application
 * <p>
 * @author Aitor Ballesteros Pavón
 * <p>
 * Copyright (C) 2012  Bartek Przybylski
 * Copyright (C) 2024 ownCloud GmbH.
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

package eu.opencloud.android.presentation.authentication;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import eu.opencloud.android.MainApp;
import eu.opencloud.android.domain.capabilities.model.OCCapability;
import eu.opencloud.android.lib.common.accounts.AccountUtils.Constants;
import timber.log.Timber;

import java.util.Locale;

import static eu.opencloud.android.data.authentication.AuthenticationConstantsKt.KEY_FEATURE_ALLOWED;
import static eu.opencloud.android.data.authentication.AuthenticationConstantsKt.KEY_FEATURE_SPACES;
import static eu.opencloud.android.data.authentication.AuthenticationConstantsKt.SELECTED_ACCOUNT;
import static eu.opencloud.android.lib.common.accounts.AccountUtils.Constants.OAUTH_SUPPORTED_TRUE;

public class AccountUtils {

    private static final int ACCOUNT_VERSION = 1;

    /**
     * Can be used to get the currently selected openCloud {@link Account} in the
     * application preferences.
     *
     * @param context The current application {@link Context}
     * @return The openCloud {@link Account} currently saved in preferences, or the first
     * {@link Account} available, if valid (still registered in the system as openCloud
     * account). If none is available and valid, returns null.
     */
    public static Account getCurrentOpenCloudAccount(Context context) {
        Account[] ocAccounts = getAccounts(context);
        Account defaultAccount = null;

        SharedPreferences appPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String accountName = appPreferences.getString(SELECTED_ACCOUNT, null);

        // account validation: the saved account MUST be in the list of openCloud Accounts known by the AccountManager
        if (accountName != null) {
            for (Account account : ocAccounts) {
                if (account.name.equals(accountName)) {
                    defaultAccount = account;
                    break;
                }
            }
        }

        if (defaultAccount == null && ocAccounts.length != 0) {
            // take first account as fallback
            defaultAccount = ocAccounts[0];
        }

        return defaultAccount;
    }

    public static Account[] getAccounts(Context context) {
        AccountManager accountManager = AccountManager.get(context);
        return accountManager.getAccountsByType(MainApp.Companion.getAccountType());
    }

    public static void deleteAccounts(Context context) {
        AccountManager accountManager = AccountManager.get(context);
        Account[] accounts = getAccounts(context);
        for (Account account : accounts) {
            accountManager.removeAccount(account, null, null, null);
        }
    }

    public static boolean exists(String accountName, Context context) {
        Account[] ocAccounts = getAccounts(context);

        if (accountName != null) {
            int lastAtPos = accountName.lastIndexOf("@");
            String hostAndPort = accountName.substring(lastAtPos + 1);
            String username = accountName.substring(0, lastAtPos);
            String otherHostAndPort, otherUsername;
            Locale currentLocale = context.getResources().getConfiguration().locale;
            for (Account otherAccount : ocAccounts) {
                lastAtPos = otherAccount.name.lastIndexOf("@");
                otherHostAndPort = otherAccount.name.substring(lastAtPos + 1);
                otherUsername = otherAccount.name.substring(0, lastAtPos);
                if (otherHostAndPort.equals(hostAndPort) &&
                        otherUsername.toLowerCase(currentLocale).
                                equals(username.toLowerCase(currentLocale))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * returns the user's name based on the account name.
     *
     * @param accountName the account name
     * @return the user's name
     */
    public static String getUsernameOfAccount(String accountName) {
        if (accountName != null) {
            return accountName.substring(0, accountName.lastIndexOf("@"));
        } else {
            return null;
        }
    }

    /**
     * Returns opencloud account identified by accountName or null if it does not exist.
     *
     * @param context
     * @param accountName name of account to be returned
     * @return opencloud account named accountName
     */
    public static Account getOpenCloudAccountByName(Context context, String accountName) {
        Account[] ocAccounts = AccountManager.get(context).getAccountsByType(
                MainApp.Companion.getAccountType());
        for (Account account : ocAccounts) {
            if (account.name.equals(accountName)) {
                return account;
            }
        }
        return null;
    }

    public static boolean isSpacesFeatureAllowedForAccount(Context context, Account account, OCCapability capability) {
        if (capability == null || !capability.isSpacesAllowed()) {
            return false;
        }
        AccountManager accountManager = AccountManager.get(context);
        String spacesFeatureValue = accountManager.getUserData(account, KEY_FEATURE_SPACES);
        return KEY_FEATURE_ALLOWED.equals(spacesFeatureValue);
    }

    public static boolean setCurrentOpenCloudAccount(Context context, String accountName) {
        boolean result = false;
        if (accountName != null) {
            boolean found;
            for (Account account : getAccounts(context)) {
                found = (account.name.equals(accountName));
                if (found) {
                    SharedPreferences.Editor appPrefs = PreferenceManager
                            .getDefaultSharedPreferences(context).edit();
                    appPrefs.putString(SELECTED_ACCOUNT, accountName);

                    appPrefs.apply();
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Update the accounts in AccountManager to meet the current version of accounts expected by the app, if needed.
     * <p>
     * Introduced to handle a change in the structure of stored account names needed to allow different OC servers
     * in the same domain, but not in the same path.
     *
     * @param context Used to access the AccountManager.
     */
    public static void updateAccountVersion(Context context) {
        Account currentAccount = AccountUtils.getCurrentOpenCloudAccount(context);
        AccountManager accountMgr = AccountManager.get(context);

        if (currentAccount != null) {
            String currentAccountVersion = accountMgr.getUserData(currentAccount, Constants.KEY_OC_ACCOUNT_VERSION);

            if (currentAccountVersion == null) {
                Timber.i("Upgrading accounts to account version #%s", ACCOUNT_VERSION);
                Account[] ocAccounts = accountMgr.getAccountsByType(MainApp.Companion.getAccountType());
                String serverUrl, username, newAccountName, password;
                Account newAccount;
                for (Account account : ocAccounts) {
                    // build new account name
                    serverUrl = accountMgr.getUserData(account, Constants.KEY_OC_BASE_URL);
                    username = eu.opencloud.android.lib.common.accounts.AccountUtils.
                            getUsernameForAccount(account);
                    newAccountName = eu.opencloud.android.lib.common.accounts.AccountUtils.
                            buildAccountName(Uri.parse(serverUrl), username);

                    // migrate to a new account, if needed
                    if (!newAccountName.equals(account.name)) {
                        Timber.d("Upgrading " + account.name + " to " + newAccountName);

                        // create the new account
                        newAccount = new Account(newAccountName, MainApp.Companion.getAccountType());
                        password = accountMgr.getPassword(account);
                        accountMgr.addAccountExplicitly(newAccount, (password != null) ? password : "", null);

                        // copy base URL
                        accountMgr.setUserData(newAccount, Constants.KEY_OC_BASE_URL, serverUrl);

                        String isOauthStr = accountMgr.getUserData(account, Constants.KEY_SUPPORTS_OAUTH2);
                        boolean isOAuth = OAUTH_SUPPORTED_TRUE.equals(isOauthStr);
                        if (isOAuth) {
                            accountMgr.setUserData(newAccount, Constants.KEY_SUPPORTS_OAUTH2, OAUTH_SUPPORTED_TRUE);
                        }

                        // don't forget the account saved in preferences as the current one
                        if (currentAccount.name.equals(account.name)) {
                            AccountUtils.setCurrentOpenCloudAccount(context, newAccountName);
                        }

                        // remove the old account
                        accountMgr.removeAccount(account, null, null);
                        // will assume it succeeds, not a big deal otherwise

                    } else {
                        // servers which base URL is in the root of their domain need no change
                        Timber.d("%s needs no upgrade ", account.name);
                        newAccount = account;
                    }

                    // at least, upgrade account version
                    Timber.d("Setting version " + ACCOUNT_VERSION + " to " + newAccountName);
                    accountMgr.setUserData(
                            newAccount, Constants.KEY_OC_ACCOUNT_VERSION, Integer.toString(ACCOUNT_VERSION)
                    );

                }
            }
        }
    }
}
