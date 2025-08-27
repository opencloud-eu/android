/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 * Copyright (C) 2023 ownCloud GmbH.
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
package eu.opencloud.android.data

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.core.net.toUri
import eu.opencloud.android.data.authentication.SELECTED_ACCOUNT
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.lib.common.ConnectionValidator
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.SingleSessionManager
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentials
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentialsFactory.getAnonymousCredentials
import eu.opencloud.android.lib.resources.appregistry.services.AppRegistryService
import eu.opencloud.android.lib.resources.appregistry.services.OCAppRegistryService
import eu.opencloud.android.lib.resources.files.services.FileService
import eu.opencloud.android.lib.resources.files.services.implementation.OCFileService
import eu.opencloud.android.lib.resources.shares.services.ShareService
import eu.opencloud.android.lib.resources.shares.services.ShareeService
import eu.opencloud.android.lib.resources.shares.services.implementation.OCShareService
import eu.opencloud.android.lib.resources.shares.services.implementation.OCShareeService
import eu.opencloud.android.lib.resources.spaces.services.OCSpacesService
import eu.opencloud.android.lib.resources.spaces.services.SpacesService
import eu.opencloud.android.lib.resources.status.services.CapabilityService
import eu.opencloud.android.lib.resources.status.services.implementation.OCCapabilityService
import eu.opencloud.android.lib.resources.users.services.UserService
import eu.opencloud.android.lib.resources.users.services.implementation.OCUserService
import timber.log.Timber

class ClientManager(
    private val accountManager: AccountManager,
    private val preferencesProvider: SharedPreferencesProvider,
    val context: Context,
    val accountType: String,
    private val connectionValidator: ConnectionValidator
) {
    // This client will maintain cookies across the whole login process.
    private var openCloudClient: OpenCloudClient? = null

    // Cached client to avoid retrieving the client for each service
    private var openCloudClientForCurrentAccount: OpenCloudClient? = null

    init {
        SingleSessionManager.setConnectionValidator(connectionValidator)
    }

    /**
     * Returns a client for the login process.
     * Helpful to keep the cookies from the status request to the final login and user info retrieval.
     * For regular uses, use [getClientForAccount]
     */
    fun getClientForAnonymousCredentials(
        path: String,
        requiresNewClient: Boolean,
        openCloudCredentials: OpenCloudCredentials? = getAnonymousCredentials()
    ): OpenCloudClient {
        val safeClient = openCloudClient
        val pathUri = path.toUri()

        return if (requiresNewClient || safeClient == null || safeClient.baseUri != pathUri) {
            Timber.d("Creating new client for path: $pathUri. Old client path: ${safeClient?.baseUri}, requiresNewClient: $requiresNewClient")
            OpenCloudClient(
                pathUri,
                connectionValidator,
                true,
                SingleSessionManager.getDefaultSingleton(),
                context
            ).apply {
                credentials = openCloudCredentials
            }.also {
                openCloudClient = it
            }
        } else {
            Timber.d("Reusing anonymous client for ${safeClient.baseUri}")
            safeClient
        }
    }

    private fun getClientForAccount(
        accountName: String?
    ): OpenCloudClient {
        val account: Account? = if (accountName.isNullOrBlank()) {
            getCurrentAccount()
        } else {
            accountManager.getAccountsByType(accountType).firstOrNull { it.name == accountName }
        }

        val openCloudAccount = OpenCloudAccount(account, context)
        return SingleSessionManager.getDefaultSingleton().getClientFor(openCloudAccount, context, connectionValidator).also {
            openCloudClientForCurrentAccount = it
        }
    }

    private fun getCurrentAccount(): Account? {
        val ocAccounts = accountManager.getAccountsByType(accountType)

        val accountName = preferencesProvider.getString(SELECTED_ACCOUNT, null)

        // account validation: the saved account MUST be in the list of openCloud Accounts known by the AccountManager
        accountName?.let { selectedAccountName ->
            ocAccounts.firstOrNull { it.name == selectedAccountName }?.let { return it }
        }

        // take first account as fallback
        return ocAccounts.firstOrNull()
    }

    fun getClientForCoilThumbnails(accountName: String) = getClientForAccount(accountName = accountName)

    fun getUserService(accountName: String? = ""): UserService {
        val openCloudClient = getClientForAccount(accountName)
        return OCUserService(client = openCloudClient)
    }

    fun getFileService(accountName: String? = ""): FileService {
        val openCloudClient = getClientForAccount(accountName)
        return OCFileService(client = openCloudClient)
    }

    fun getCapabilityService(accountName: String? = ""): CapabilityService {
        val openCloudClient = getClientForAccount(accountName)
        return OCCapabilityService(client = openCloudClient)
    }

    fun getShareService(accountName: String? = ""): ShareService {
        val openCloudClient = getClientForAccount(accountName)
        return OCShareService(client = openCloudClient)
    }

    fun getShareeService(accountName: String? = ""): ShareeService {
        val openCloudClient = getClientForAccount(accountName)
        return OCShareeService(client = openCloudClient)
    }

    fun getSpacesService(accountName: String): SpacesService {
        val openCloudClient = getClientForAccount(accountName)
        return OCSpacesService(client = openCloudClient)
    }

    fun getAppRegistryService(accountName: String): AppRegistryService {
        val openCloudClient = getClientForAccount(accountName)
        return OCAppRegistryService(client = openCloudClient)
    }
}
