/**
 * openCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2022 ownCloud GmbH.
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

package eu.opencloud.android.usecases.accounts

import eu.opencloud.android.data.appregistry.datasources.LocalAppRegistryDataSource
import eu.opencloud.android.data.capabilities.datasources.LocalCapabilitiesDataSource
import eu.opencloud.android.data.files.datasources.LocalFileDataSource
import eu.opencloud.android.data.sharing.shares.datasources.LocalShareDataSource
import eu.opencloud.android.data.spaces.datasources.LocalSpacesDataSource
import eu.opencloud.android.data.user.datasources.LocalUserDataSource
import eu.opencloud.android.domain.BaseUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.GetAutomaticUploadsConfigurationUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetPictureUploadsUseCase
import eu.opencloud.android.domain.automaticuploads.usecases.ResetVideoUploadsUseCase
import eu.opencloud.android.usecases.transfers.uploads.CancelTransfersFromAccountUseCase

/*
* Local data sources are injected instead of repositories to avoid crash in the app.
* Injecting the repositories implies injecting the remote data sources too, which
* need an OpencloudAccount that there is not at this point.
*/
class RemoveAccountUseCase(
    private val getAutomaticUploadsConfigurationUseCase: GetAutomaticUploadsConfigurationUseCase,
    private val resetPictureUploadsUseCase: ResetPictureUploadsUseCase,
    private val resetVideoUploadsUseCase: ResetVideoUploadsUseCase,
    private val cancelTransfersFromAccountUseCase: CancelTransfersFromAccountUseCase,
    private val localFileDataSource: LocalFileDataSource,
    private val localCapabilitiesDataSource: LocalCapabilitiesDataSource,
    private val localShareDataSource: LocalShareDataSource,
    private val localUserDataSource: LocalUserDataSource,
    private val localSpacesDataSource: LocalSpacesDataSource,
    private val localAppRegistryDataSource: LocalAppRegistryDataSource,
) : BaseUseCase<Unit, RemoveAccountUseCase.Params>() {

    override fun run(params: Params) {
        // Reset camera uploads if they were enabled for the removed account
        val cameraUploadsConfiguration = getAutomaticUploadsConfigurationUseCase(Unit)
        if (params.accountName == cameraUploadsConfiguration.getDataOrNull()?.pictureUploadsConfiguration?.accountName) {
            resetPictureUploadsUseCase(Unit)
        }
        if (params.accountName == cameraUploadsConfiguration.getDataOrNull()?.videoUploadsConfiguration?.accountName) {
            resetVideoUploadsUseCase(Unit)
        }

        // Cancel transfers of the removed account
        cancelTransfersFromAccountUseCase(
            CancelTransfersFromAccountUseCase.Params(accountName = params.accountName)
        )

        // Delete files for the removed account in database
        localFileDataSource.deleteFilesForAccount(params.accountName)

        // Delete capabilities for the removed account in database
        localCapabilitiesDataSource.deleteCapabilitiesForAccount(params.accountName)

        // Delete shares for the removed account in database
        localShareDataSource.deleteSharesForAccount(params.accountName)

        // Delete quota for the removed account in database
        localUserDataSource.deleteQuotaForAccount(params.accountName)

        // Delete spaces for the removed account in database
        localSpacesDataSource.deleteSpacesForAccount(params.accountName)

        // Delete app registry for the removed account in database
        localAppRegistryDataSource.deleteAppRegistryForAccount(params.accountName)
    }

    data class Params(
        val accountName: String
    )
}
