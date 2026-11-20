/*
 * openCloud Android client application
 *
 * @author David A. Velasco
 * @author Christian Schabesberger
 * @author David González Verdugo
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2012  Bartek Przybylski
 * Copyright (C) 2024 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.presentation.authentication;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import eu.opencloud.android.MainApp;
import eu.opencloud.android.R;
import eu.opencloud.android.presentation.authentication.oauth.OAuthUtils;
import eu.opencloud.android.domain.UseCaseResult;
import eu.opencloud.android.domain.authentication.oauth.OIDCDiscoveryUseCase;
import eu.opencloud.android.domain.authentication.oauth.RequestTokenUseCase;
import eu.opencloud.android.domain.authentication.oauth.model.OIDCServerConfiguration;
import eu.opencloud.android.domain.authentication.oauth.model.TokenRequest;
import eu.opencloud.android.domain.authentication.oauth.model.TokenResponse;
import eu.opencloud.android.domain.exceptions.OAuth2ErrorAccessDeniedException;
import eu.opencloud.android.domain.exceptions.OAuth2ErrorException;
import eu.opencloud.android.domain.exceptions.UnauthorizedException;
import eu.opencloud.android.lib.common.accounts.AccountTypeUtils;
import eu.opencloud.android.lib.common.accounts.AccountUtils;
import kotlin.Lazy;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

import java.io.File;

import static eu.opencloud.android.data.authentication.AuthenticationConstantsKt.KEY_CLIENT_REGISTRATION_CLIENT_EXPIRATION_DATE;
import static eu.opencloud.android.data.authentication.AuthenticationConstantsKt.KEY_CLIENT_REGISTRATION_CLIENT_ID;
import static eu.opencloud.android.data.authentication.AuthenticationConstantsKt.KEY_CLIENT_REGISTRATION_CLIENT_SECRET;
import static eu.opencloud.android.data.authentication.AuthenticationConstantsKt.KEY_OAUTH2_REFRESH_TOKEN;
import static eu.opencloud.android.data.authentication.AuthenticationConstantsKt.KEY_OAUTH2_SCOPE;
import static eu.opencloud.android.data.authentication.AuthenticationConstantsKt.KEY_OIDC_ISSUER;
import static eu.opencloud.android.presentation.authentication.AuthenticatorConstants.KEY_AUTH_TOKEN_TYPE;
import static org.koin.java.KoinJavaComponent.inject;

/**
 * Authenticator for openCloud accounts.
 *
 * Controller class accessed from the system AccountManager, providing integration of openCloud accounts with the
 * Android system.
 */
public class AccountAuthenticator extends AbstractAccountAuthenticator {

    /**
     * Is used by android system to assign accounts to authenticators. Should be
     * used by application and all extensions.
     */
    private static final String KEY_REQUIRED_FEATURES = "requiredFeatures";
    public static final String KEY_ACCOUNT = "account";

    /**
     * Serializes the refresh of access tokens. Refresh tokens can be single-use (rotation), so two refreshes
     * running at once with the same refresh token make the second one fail with invalid_grant and may even
     * revoke the whole session at the IdP.
     */
    private static final Object REFRESH_TOKEN_LOCK = new Object();

    private Context mContext;

    AccountAuthenticator(Context context) {
        super(context);
        mContext = context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response,
                             String accountType, String authTokenType,
                             String[] requiredFeatures, Bundle options) {
        Timber.i("Adding account with type " + accountType + " and auth token " + authTokenType);

        final Bundle bundle = new Bundle();

        AccountManager accountManager = AccountManager.get(mContext);
        Account[] accounts = accountManager.getAccountsByType(MainApp.Companion.getAccountType());

        if (mContext.getResources().getBoolean(R.bool.multiaccount_support) || accounts.length < 1) {
            try {
                validateAccountType(accountType);
            } catch (AuthenticatorException e) {
                Timber.e(e, "Failed to validate account type %s", accountType);
                return e.getFailureBundle();
            }

            final Intent intent = new Intent(mContext, LoginActivity.class);
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
            intent.putExtra(KEY_AUTH_TOKEN_TYPE, authTokenType);
            intent.putExtra(KEY_REQUIRED_FEATURES, requiredFeatures);
            intent.putExtra(AuthenticatorConstants.EXTRA_ACTION, AuthenticatorConstants.ACTION_CREATE);

            setIntentFlags(intent);

            bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        }

        return bundle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response,
                                     Account account, Bundle options) {
        try {
            validateAccountType(account.type);
        } catch (AuthenticatorException e) {
            Timber.e(e, "Failed to validate account type %s", account.type);
            return e.getFailureBundle();
        }
        Intent intent = new Intent(mContext, LoginActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
                response);
        intent.putExtra(KEY_ACCOUNT, account);

        setIntentFlags(intent);

        Bundle resultBundle = new Bundle();
        resultBundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return resultBundle;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response,
                                 String accountType) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse accountAuthenticatorResponse,
                               Account account, String authTokenType, Bundle options) throws NetworkErrorException {
        /// validate parameters
        try {
            validateAccountType(account.type);
            validateAuthTokenType(authTokenType);
        } catch (AuthenticatorException e) {
            Timber.e(e, "Failed to validate account type %s", account.type);
            return e.getFailureBundle();
        }

        String accessToken;

        /// check if required token is stored
        final AccountManager accountManager = AccountManager.get(mContext);
        if (authTokenType.equals(AccountTypeUtils.getAuthTokenTypePass(MainApp.Companion.getAccountType()))) {
            // Basic
            accessToken = accountManager.getPassword(account);
        } else {
            // OAuth, gets an auth token from the AccountManager's cache. If no auth token is cached for
            // this account, null will be returned
            accessToken = accountManager.peekAuthToken(account, authTokenType);
            if (accessToken == null && canBeRefreshed(authTokenType) && clientSecretIsValid(accountManager, account)) {
                synchronized (REFRESH_TOKEN_LOCK) {
                    // Another thread may have refreshed the token while this one was waiting for the lock
                    accessToken = accountManager.peekAuthToken(account, authTokenType);
                    if (accessToken == null) {
                        // Throws NetworkErrorException if the failure is transient, so the caller does not mistake
                        // it for an expired session
                        accessToken = refreshToken(account, authTokenType, accountManager);
                    }
                }
            }
        }

        if (accessToken != null) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, MainApp.Companion.getAccountType());
            result.putString(AccountManager.KEY_AUTHTOKEN, accessToken);
            return result;
        }

        /// if not stored, return Intent to access the LoginActivity and UPDATE the token for the account
        return prepareBundleToAccessLoginActivity(accountAuthenticatorResponse, account, authTokenType, options);
    }

    /**
     * Check if the client has expired or not.
     * If the client has expired, we can not refresh the token and user needs to re-authenticate.
     *
     * @return true if the client is still valid
     */
    private boolean clientSecretIsValid(AccountManager accountManager, Account account) {
        String clientSecretExpiration = accountManager.getUserData(account,
                KEY_CLIENT_REGISTRATION_CLIENT_EXPIRATION_DATE);

        Timber.d("Client secret expiration [" + clientSecretExpiration + "]");
        if (clientSecretExpiration == null) {
            return true;
        }

        long currentTimeStamp = System.currentTimeMillis() / 1000L;
        int clientSecretExpirationInt = Integer.parseInt(clientSecretExpiration);
        boolean clientSecretIsValid = clientSecretExpirationInt == 0 || clientSecretExpirationInt > currentTimeStamp;

        Timber.d("Current time [" + currentTimeStamp + "]");
        Timber.d("Client is valid [" + clientSecretIsValid + "]");

        return clientSecretIsValid;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response,
                              Account account, String[] features) {
        final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        return result;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response,
                                    Account account, String authTokenType, Bundle options) {
        final Intent intent = new Intent(mContext, LoginActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
                response);
        intent.putExtra(KEY_ACCOUNT, account);
        intent.putExtra(KEY_AUTH_TOKEN_TYPE, authTokenType);
        setIntentFlags(intent);

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public Bundle getAccountRemovalAllowed(
            AccountAuthenticatorResponse response, Account account)
            throws NetworkErrorException {
        return super.getAccountRemovalAllowed(response, account);
    }

    private void setIntentFlags(Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
    }

    private void validateAccountType(String type)
            throws UnsupportedAccountTypeException {
        if (!type.equals(MainApp.Companion.getAccountType())) {
            throw new UnsupportedAccountTypeException();
        }
    }

    private void validateAuthTokenType(String authTokenType)
            throws UnsupportedAuthTokenTypeException {
        if (!authTokenType.equals(MainApp.Companion.getAuthTokenType()) &&
                !authTokenType.equals(AccountTypeUtils.getAuthTokenTypePass(MainApp.Companion.getAccountType())) &&
                !authTokenType.equals(AccountTypeUtils.getAuthTokenTypeAccessToken(MainApp.Companion.getAccountType())) &&
                !authTokenType.equals(AccountTypeUtils.getAuthTokenTypeRefreshToken(MainApp.Companion.getAccountType()))
        ) {
            throw new UnsupportedAuthTokenTypeException();
        }
    }

    public static class AuthenticatorException extends Exception {
        private static final long serialVersionUID = 1L;
        private Bundle mFailureBundle;

        AuthenticatorException(int code, String errorMsg) {
            mFailureBundle = new Bundle();
            mFailureBundle.putInt(AccountManager.KEY_ERROR_CODE, code);
            mFailureBundle
                    .putString(AccountManager.KEY_ERROR_MESSAGE, errorMsg);
        }

        Bundle getFailureBundle() {
            return mFailureBundle;
        }
    }

    public static class UnsupportedAccountTypeException extends
            AuthenticatorException {
        private static final long serialVersionUID = 1L;

        UnsupportedAccountTypeException() {
            super(AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION,
                    "Unsupported account type");
        }
    }

    public static class UnsupportedAuthTokenTypeException extends
            AuthenticatorException {
        private static final long serialVersionUID = 1L;

        UnsupportedAuthTokenTypeException() {
            super(AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION,
                    "Unsupported auth token type");
        }
    }

    private boolean canBeRefreshed(String authTokenType) {
        return (authTokenType.equals(AccountTypeUtils.getAuthTokenTypeAccessToken(MainApp.Companion.
                getAccountType())));
    }

    /**
     * Exchanges the stored refresh token for a new access token.
     *
     * @return the new access token, or null if the session is definitely over (no refresh token stored, or the IdP
     * rejected it) and the user needs to log in again.
     * @throws NetworkErrorException if the refresh failed for a transient reason (no network, timeout, server
     *                               error...). The stored tokens are untouched and a later attempt may succeed.
     */
    private String refreshToken(
            Account account,
            String authTokenType,
            AccountManager accountManager
    ) throws NetworkErrorException {

        // Prepare everything to perform the token request
        String refreshToken = accountManager.getUserData(account, KEY_OAUTH2_REFRESH_TOKEN);

        if (refreshToken == null || refreshToken.isEmpty()) {
            Timber.w("No refresh token stored for silent renewal of access token");
            return null;
        }

        Timber.d("Ready to exchange for new tokens. Account: [ %s ]", account.name);

        String baseUrl = accountManager.getUserData(account, AccountUtils.Constants.KEY_OC_BASE_URL);

        // Servers requiring mTLS reject discovery and token requests that do not present the account's certificate
        String clientCertAlias = AccountUtils.getClientCertAliasForAccount(mContext, account);

        // OIDC Discovery: prefer the stored webfinger issuer (points to the real IDP),
        // fall back to baseUrl for accounts created before webfinger support.
        String oidcIssuer = accountManager.getUserData(account, KEY_OIDC_ISSUER);
        String discoveryUrl = (oidcIssuer != null) ? oidcIssuer : baseUrl;

        @NotNull Lazy<OIDCDiscoveryUseCase> oidcDiscoveryUseCase = inject(OIDCDiscoveryUseCase.class);
        OIDCDiscoveryUseCase.Params oidcDiscoveryUseCaseParams =
                new OIDCDiscoveryUseCase.Params(discoveryUrl, clientCertAlias);
        UseCaseResult<OIDCServerConfiguration> oidcServerConfigurationUseCaseResult =
                oidcDiscoveryUseCase.getValue().invoke(oidcDiscoveryUseCaseParams);

        String tokenEndpoint;

        String clientId = accountManager.getUserData(account, KEY_CLIENT_REGISTRATION_CLIENT_ID);
        String clientSecret = accountManager.getUserData(account, KEY_CLIENT_REGISTRATION_CLIENT_SECRET);

        String clientIdForRequest = null;
        String clientSecretForRequest = null;
        String clientAuth;

        if (clientId == null) {
            Timber.d("Client Id not stored. Let's use the hardcoded one");
            clientId = mContext.getString(R.string.oauth2_client_id);
        }
        if (clientSecret == null) {
            Timber.d("Client Secret not stored. Let's use the hardcoded one");
            clientSecret = mContext.getString(R.string.oauth2_client_secret);
        }

        if (oidcServerConfigurationUseCaseResult.isSuccess()) {
            Timber.d("OIDC Discovery success. Server discovery info: [ %s ]",
                    oidcServerConfigurationUseCaseResult.getDataOrNull());

            // Use token endpoint retrieved from oidc discovery
            tokenEndpoint = oidcServerConfigurationUseCaseResult.getDataOrNull().getTokenEndpoint();

            // RFC 7636: Public clients (token_endpoint_auth_method: none) must not send Authorization header
            if (oidcServerConfigurationUseCaseResult.getDataOrNull() != null &&
                    oidcServerConfigurationUseCaseResult.getDataOrNull().isTokenEndpointAuthMethodNone()) {
                clientAuth = "";
                clientIdForRequest = clientId;
            } else if (oidcServerConfigurationUseCaseResult.getDataOrNull() != null &&
                    oidcServerConfigurationUseCaseResult.getDataOrNull().isTokenEndpointAuthMethodSupportedClientSecretPost()) {
                // For client_secret_post, credentials go in body, not Authorization header
                clientAuth = "";
                clientIdForRequest = clientId;
                clientSecretForRequest = clientSecret;
            } else {
                // For other methods (e.g., client_secret_basic), use Basic auth header
                clientAuth = OAuthUtils.Companion.getClientAuth(clientSecret, clientId);
            }
        } else {
            Throwable discoveryError = oidcServerConfigurationUseCaseResult.getThrowableOrNull();
            Timber.d("OIDC Discovery failed. Server discovery info: [ %s ]", String.valueOf(discoveryError));

            if (oidcIssuer != null) {
                // The IdP is known, so the legacy ownCloud endpoint below can not be the right one: the failure is
                // just the IdP being unreachable for now
                throw new NetworkErrorException("OIDC discovery failed for " + oidcIssuer, discoveryError);
            }

            tokenEndpoint = baseUrl + File.separator + mContext.getString(R.string.oauth2_url_endpoint_access);
            clientAuth = OAuthUtils.Companion.getClientAuth(clientSecret, clientId);
        }

        String scope = accountManager.getUserData(account, KEY_OAUTH2_SCOPE);
        if (scope == null) {
            scope = mContext.getResources().getString(R.string.oauth2_openid_scope);
        }

        TokenRequest oauthTokenRequest = new TokenRequest.RefreshToken(
                baseUrl,
                tokenEndpoint,
                clientAuth,
                scope,
                clientIdForRequest,
                clientSecretForRequest,
                refreshToken,
                clientCertAlias
        );

        // Token exchange
        @NotNull Lazy<RequestTokenUseCase> requestTokenUseCase = inject(RequestTokenUseCase.class);
        RequestTokenUseCase.Params requestTokenParams = new RequestTokenUseCase.Params(oauthTokenRequest);
        UseCaseResult<TokenResponse> tokenResponseResult = requestTokenUseCase.getValue().invoke(requestTokenParams);

        TokenResponse safeTokenResponse = tokenResponseResult.getDataOrNull();
        if (safeTokenResponse != null) {
            return handleSuccessfulRefreshToken(safeTokenResponse,
                    account, authTokenType, accountManager, refreshToken);
        }

        Throwable error = tokenResponseResult.getThrowableOrNull();
        if (isRefreshTokenRejected(error)) {
            Timber.e(error, "OAuth refresh token was rejected. Preparing to access Login Activity");
            return null;
        }

        Timber.w(error, "OAuth request to refresh access token failed, but the refresh token was not rejected");
        throw new NetworkErrorException("Could not refresh the access token for " + account.name, error);
    }

    /**
     * Tells whether a failed token request means the refresh token (or the client) is not valid anymore, as opposed
     * to a transient problem like no connection, a timeout or a server error.
     */
    static boolean isRefreshTokenRejected(Throwable error) {
        return error instanceof OAuth2ErrorException
                || error instanceof OAuth2ErrorAccessDeniedException
                || error instanceof UnauthorizedException;
    }

    private String handleSuccessfulRefreshToken(
            TokenResponse tokenResponse,
            Account account,
            String authTokenType,
            AccountManager accountManager,
            String oldRefreshToken
    ) {
            String newAccessToken = tokenResponse.getAccessToken();

            String refreshTokenToUseFromNowOn;
            if (tokenResponse.getRefreshToken() != null) {
                refreshTokenToUseFromNowOn = tokenResponse.getRefreshToken();
            } else {
                refreshTokenToUseFromNowOn = oldRefreshToken;
            }

            // The refresh token goes first: it may have been rotated, which makes the old one useless. If the process
            // dies before the access token is stored, the next call finds no access token and refreshes with it.
            accountManager.setUserData(account, KEY_OAUTH2_REFRESH_TOKEN, refreshTokenToUseFromNowOn);
            accountManager.setAuthToken(account, authTokenType, newAccessToken);

            Timber.d("Token refreshed successfully for account [ %s ]", account.name);

            return newAccessToken;
        }

    /**
     * Return bundle with intent to access LoginActivity and UPDATE the token for the account
     */
    private Bundle prepareBundleToAccessLoginActivity(
            AccountAuthenticatorResponse accountAuthenticatorResponse,
            Account account,
            String authTokenType,
            Bundle options
    ) {
        final Intent intent = new Intent(mContext, LoginActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
                accountAuthenticatorResponse);
        intent.putExtra(KEY_AUTH_TOKEN_TYPE, authTokenType);
        intent.putExtra(AuthenticatorConstants.EXTRA_ACCOUNT, account);
        intent.putExtra(
                AuthenticatorConstants.EXTRA_ACTION,
                AuthenticatorConstants.ACTION_UPDATE_EXPIRED_TOKEN
        );

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }
}
