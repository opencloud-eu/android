/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 *
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

package eu.opencloud.android.dependecyinjection

import android.accounts.AccountManager
import eu.opencloud.android.MainApp.Companion.accountType
import eu.opencloud.android.MainApp.Companion.dataFolder
import eu.opencloud.android.data.OpencloudDatabase
import eu.opencloud.android.data.appregistry.datasources.LocalAppRegistryDataSource
import eu.opencloud.android.data.appregistry.datasources.implementation.OCLocalAppRegistryDataSource
import eu.opencloud.android.data.authentication.datasources.LocalAuthenticationDataSource
import eu.opencloud.android.data.authentication.datasources.implementation.OCLocalAuthenticationDataSource
import eu.opencloud.android.data.capabilities.datasources.LocalCapabilitiesDataSource
import eu.opencloud.android.data.capabilities.datasources.implementation.OCLocalCapabilitiesDataSource
import eu.opencloud.android.data.files.datasources.LocalFileDataSource
import eu.opencloud.android.data.files.datasources.implementation.OCLocalFileDataSource
import eu.opencloud.android.data.folderbackup.datasources.LocalFolderBackupDataSource
import eu.opencloud.android.data.folderbackup.datasources.implementation.OCLocalFolderBackupDataSource
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.data.providers.implementation.OCSharedPreferencesProvider
import eu.opencloud.android.data.sharing.shares.datasources.LocalShareDataSource
import eu.opencloud.android.data.sharing.shares.datasources.implementation.OCLocalShareDataSource
import eu.opencloud.android.data.spaces.datasources.LocalSpacesDataSource
import eu.opencloud.android.data.spaces.datasources.implementation.OCLocalSpacesDataSource
import eu.opencloud.android.data.providers.LocalStorageProvider
import eu.opencloud.android.data.providers.ScopedStorageProvider
import eu.opencloud.android.data.transfers.datasources.LocalTransferDataSource
import eu.opencloud.android.data.transfers.datasources.implementation.OCLocalTransferDataSource
import eu.opencloud.android.data.user.datasources.LocalUserDataSource
import eu.opencloud.android.data.user.datasources.implementation.OCLocalUserDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val localDataSourceModule = module {
    single { AccountManager.get(androidContext()) }

    single { OpencloudDatabase.getDatabase(androidContext()).appRegistryDao() }
    single { OpencloudDatabase.getDatabase(androidContext()).capabilityDao() }
    single { OpencloudDatabase.getDatabase(androidContext()).fileDao() }
    single { OpencloudDatabase.getDatabase(androidContext()).folderBackUpDao() }
    single { OpencloudDatabase.getDatabase(androidContext()).shareDao() }
    single { OpencloudDatabase.getDatabase(androidContext()).spacesDao() }
    single { OpencloudDatabase.getDatabase(androidContext()).transferDao() }
    single { OpencloudDatabase.getDatabase(androidContext()).userDao() }

    singleOf(::OCSharedPreferencesProvider) bind SharedPreferencesProvider::class
    single<LocalStorageProvider> { ScopedStorageProvider(dataFolder, androidContext()) }

    factory<LocalAuthenticationDataSource> { OCLocalAuthenticationDataSource(androidContext(), get(), get(), accountType) }
    factoryOf(::OCLocalFolderBackupDataSource) bind LocalFolderBackupDataSource::class
    factoryOf(::OCLocalAppRegistryDataSource) bind LocalAppRegistryDataSource::class
    factoryOf(::OCLocalCapabilitiesDataSource) bind LocalCapabilitiesDataSource::class
    factoryOf(::OCLocalFileDataSource) bind LocalFileDataSource::class
    factoryOf(::OCLocalShareDataSource) bind LocalShareDataSource::class
    factoryOf(::OCLocalSpacesDataSource) bind LocalSpacesDataSource::class
    factoryOf(::OCLocalTransferDataSource) bind LocalTransferDataSource::class
    factoryOf(::OCLocalUserDataSource) bind LocalUserDataSource::class
}
