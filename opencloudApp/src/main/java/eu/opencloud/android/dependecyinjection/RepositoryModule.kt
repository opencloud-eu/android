/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * @author Abel García de Prada
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

package eu.opencloud.android.dependecyinjection

import eu.opencloud.android.data.appregistry.repository.OCAppRegistryRepository
import eu.opencloud.android.data.authentication.repository.OCAuthenticationRepository
import eu.opencloud.android.data.capabilities.repository.OCCapabilityRepository
import eu.opencloud.android.data.files.repository.OCFileRepository
import eu.opencloud.android.data.folderbackup.repository.OCFolderBackupRepository
import eu.opencloud.android.data.oauth.repository.OCOAuthRepository
import eu.opencloud.android.data.server.repository.OCServerInfoRepository
import eu.opencloud.android.data.sharing.sharees.repository.OCShareeRepository
import eu.opencloud.android.data.sharing.shares.repository.OCShareRepository
import eu.opencloud.android.data.spaces.repository.OCSpacesRepository
import eu.opencloud.android.data.transfers.repository.OCTransferRepository
import eu.opencloud.android.data.user.repository.OCUserRepository
import eu.opencloud.android.data.webfinger.repository.OCWebFingerRepository
import eu.opencloud.android.domain.appregistry.AppRegistryRepository
import eu.opencloud.android.domain.authentication.AuthenticationRepository
import eu.opencloud.android.domain.authentication.oauth.OAuthRepository
import eu.opencloud.android.domain.automaticuploads.FolderBackupRepository
import eu.opencloud.android.domain.capabilities.CapabilityRepository
import eu.opencloud.android.domain.files.FileRepository
import eu.opencloud.android.domain.server.ServerInfoRepository
import eu.opencloud.android.domain.sharing.sharees.ShareeRepository
import eu.opencloud.android.domain.sharing.shares.ShareRepository
import eu.opencloud.android.domain.spaces.SpacesRepository
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.domain.user.UserRepository
import eu.opencloud.android.domain.webfinger.WebFingerRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val repositoryModule = module {
    factoryOf(::OCAppRegistryRepository) bind AppRegistryRepository::class
    factoryOf(::OCAuthenticationRepository) bind AuthenticationRepository::class
    factoryOf(::OCCapabilityRepository) bind CapabilityRepository::class
    factoryOf(::OCFileRepository) bind FileRepository::class
    factoryOf(::OCFolderBackupRepository) bind FolderBackupRepository::class
    factoryOf(::OCOAuthRepository) bind OAuthRepository::class
    factoryOf(::OCServerInfoRepository) bind ServerInfoRepository::class
    factoryOf(::OCShareRepository) bind ShareRepository::class
    factoryOf(::OCShareeRepository) bind ShareeRepository::class
    factoryOf(::OCSpacesRepository) bind SpacesRepository::class
    factoryOf(::OCTransferRepository) bind TransferRepository::class
    factoryOf(::OCUserRepository) bind UserRepository::class
    factoryOf(::OCWebFingerRepository) bind WebFingerRepository::class
}
