/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2016 ownCloud GmbH.
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

package eu.opencloud.android.lib.common

import android.accounts.AccountManager
import android.accounts.AccountsException
import android.content.Context
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentials
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentialsFactory.OpenCloudAnonymousCredentials
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.files.CheckPathExistenceRemoteOperation
import eu.opencloud.android.lib.resources.status.GetRemoteStatusOperation
import eu.opencloud.android.lib.resources.status.RemoteServerInfo
import org.apache.commons.lang3.exception.ExceptionUtils
import timber.log.Timber
import java.io.IOException

/**
 * ConnectionValidator
 *
 * @author Christian Schabesberger
 */
class ConnectionValidator(
    val context: Context,
    private val clearCookiesOnValidation: Boolean
) {
    fun validate(baseClient: OpenCloudClient, singleSessionManager: SingleSessionManager, context: Context): Boolean {
        try {
            var validationRetryCount = 0
            val client = OpenCloudClient(baseClient.baseUri, null, false, singleSessionManager, context)
            if (clearCookiesOnValidation) {
                client.clearCookies()
            } else {
                client.cookiesForBaseUri = baseClient.cookiesForBaseUri
            }

            client.account = baseClient.account
            client.credentials = baseClient.credentials
            while (validationRetryCount < VALIDATION_RETRY_COUNT) {
                Timber.d("validationRetryCount %d", validationRetryCount)
                var successCounter = 0
                var failCounter = 0

                client.setFollowRedirects(true)
                if (isOpenCloudStatusOk(client)) {
                    successCounter++
                } else {
                    failCounter++
                }

                // Skip the part where we try to check if we can access the parts where we have to be logged in... if we are not logged in
                if (baseClient.credentials !is OpenCloudAnonymousCredentials) {
                    client.setFollowRedirects(false)
                    val contentReply = canAccessRootFolder(client)
                    if (contentReply.httpCode == HttpConstants.HTTP_OK) {
                        if (contentReply.data == true) { //if data is true it means that the content reply was ok
                            successCounter++
                        } else {
                            failCounter++
                        }
                    } else {
                        failCounter++
                        if (contentReply.httpCode == HttpConstants.HTTP_UNAUTHORIZED) {
                            checkUnauthorizedAccess(client, singleSessionManager, contentReply.httpCode)
                        }
                    }
                }
                if (successCounter >= failCounter) {
                    baseClient.credentials = client.credentials
                    baseClient.cookiesForBaseUri = client.cookiesForBaseUri
                    return true
                }
                validationRetryCount++
            }
            Timber.d("Could not authenticate or get valid data from opencloud")
        } catch (e: Exception) {
            Timber.d(ExceptionUtils.getStackTrace(e))
        }
        return false
    }

    private fun isOpenCloudStatusOk(client: OpenCloudClient): Boolean {
        val reply = getOpenCloudStatus(client)
        // dont check status code. It currently relais on the broken redirect code of the opencloud client
        // To do: Use okhttp redirect and add this check again
        // return reply.httpCode == HttpConstants.HTTP_OK &&
        return !reply.isException &&
                reply.data != null
    }

    private fun getOpenCloudStatus(client: OpenCloudClient): RemoteOperationResult<RemoteServerInfo> {
        val remoteStatusOperation = GetRemoteStatusOperation()
        return remoteStatusOperation.execute(client)
    }

    private fun canAccessRootFolder(client: OpenCloudClient): RemoteOperationResult<Boolean> {
        val checkPathExistenceRemoteOperation = CheckPathExistenceRemoteOperation("/", true)
        return checkPathExistenceRemoteOperation.execute(client)
    }

    /**
     * Determines if credentials should be invalidated according the to the HTTPS status
     * of a network request just performed.
     *
     * @param httpStatusCode Result of the last request ran with the 'credentials' belows.
     * @return 'True' if credentials should and might be invalidated, 'false' if shouldn't or
     * cannot be invalidated with the given arguments.
     */
    private fun shouldInvalidateAccountCredentials(credentials: OpenCloudCredentials, account: OpenCloudAccount, httpStatusCode: Int): Boolean {
        var shouldInvalidateAccountCredentials = httpStatusCode == HttpConstants.HTTP_UNAUTHORIZED
        shouldInvalidateAccountCredentials = shouldInvalidateAccountCredentials and  // real credentials
                (credentials !is OpenCloudAnonymousCredentials)

        // test if have all the needed to effectively invalidate ...
        shouldInvalidateAccountCredentials =
            shouldInvalidateAccountCredentials and (account.savedAccount != null)
        Timber.d(
            """Received error: $httpStatusCode,
            account: ${account.name}
            credentials are real: ${credentials !is OpenCloudAnonymousCredentials},
            so we need to invalidate credentials for account ${account.name} : $shouldInvalidateAccountCredentials"""
        )
        return shouldInvalidateAccountCredentials
    }

    /**
     * Invalidates credentials stored for the given account in the system  [AccountManager] and in
     * current [SingleSessionManager.getDefaultSingleton] instance.
     *
     *
     * [.shouldInvalidateAccountCredentials] should be called first.
     *
     */
    private fun invalidateAccountCredentials(account: OpenCloudAccount, credentials: OpenCloudCredentials) {
        Timber.i("Invalidating account credentials for account $account")
        val am = AccountManager.get(context)
        am.invalidateAuthToken(
            account.savedAccount.type,
            credentials.authToken
        )
        am.clearPassword(account.savedAccount) // being strict, only needed for Basic Auth credentials
    }

    /**
     * Checks the status code of an execution and decides if should be repeated with fresh credentials.
     *
     *
     * Invalidates current credentials if the request failed as anauthorized.
     *
     *
     * Refresh current credentials if possible, and marks a retry.
     *
     * @return
     */
    private fun checkUnauthorizedAccess(client: OpenCloudClient, singleSessionManager: SingleSessionManager, status: Int): Boolean {
        var credentialsWereRefreshed = false
        val account = client.account
        val credentials = account.credentials
        if (shouldInvalidateAccountCredentials(credentials, account, status)) {
            invalidateAccountCredentials(account, credentials)

            if (credentials.authTokenCanBeRefreshed()) {
                try {
                    // This command does the actual refresh
                    Timber.i("Trying to refresh auth token for account $account")
                    account.loadCredentials(context)
                    // if mAccount.getCredentials().length() == 0 --> refresh failed
                    client.credentials = account.credentials
                    credentialsWereRefreshed = true
                } catch (e: AccountsException) {
                    Timber.e(
                        e, "Error while trying to refresh auth token for %s\ntrace: %s",
                        account.savedAccount.name,
                        ExceptionUtils.getStackTrace(e)
                    )
                } catch (e: IOException) {
                    Timber.e(
                        e, "Error while trying to refresh auth token for %s\ntrace: %s",
                        account.savedAccount.name,
                        ExceptionUtils.getStackTrace(e)
                    )
                }
                if (!credentialsWereRefreshed) {
                    // if credentials are not refreshed, client must be removed
                    // from the OpenCloudClientManager to prevent it is reused once and again
                    Timber.w("Credentials were not refreshed, client will be removed from the Session Manager to prevent using it over and over")
                    singleSessionManager.removeClientFor(account)
                }
            }
            // else: onExecute will finish with status 401
        }
        return credentialsWereRefreshed
    }

    companion object {
        private const val VALIDATION_RETRY_COUNT = 3
    }
}
