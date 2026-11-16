/*
 * openCloud Android client application
 *
 * Copyright (C) 2026 OpenCloud GmbH.
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
 *
 */


package eu.opencloud.android.data

import eu.opencloud.android.data.files.datasources.implementation.OCLocalFileDataSource.Companion.toEntity
import eu.opencloud.android.data.files.datasources.implementation.OCLocalFileDataSource.Companion.toModel
import eu.opencloud.android.data.files.datasources.implementation.OCRemoteFileDataSource.Companion.toModel
import eu.opencloud.android.data.spaces.datasources.implementation.OCLocalSpacesDataSource.Companion.toEntity
import eu.opencloud.android.data.spaces.datasources.implementation.OCLocalSpacesDataSource.Companion.toModel
import eu.opencloud.android.data.spaces.datasources.implementation.OCRemoteSpacesDataSource.Companion.toModel
import eu.opencloud.android.domain.spaces.model.OCSpace.Companion.CONTENT_TYPE_VAULT
import eu.opencloud.android.lib.resources.files.RemoteFile
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_FOLDER_WITH_SPACE_ID
import eu.opencloud.android.testutil.OC_SPACE_PROJECT_WITHOUT_IMAGE
import eu.opencloud.android.testutil.SPACE_RESPONSE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vaults are encrypted folders and spaces created by the OpenCloud Web vault app.
 * See https://github.com/opencloud-eu/android/issues/228
 */
class VaultMappingTest {

    @Test
    fun `remote vault folder is mapped to a vault OCFile`() {
        val remoteFile = RemoteFile(remotePath = "/Secret.vault/", owner = OC_ACCOUNT_NAME, mimeType = "DIR", isVault = true)
        assertTrue(remoteFile.toModel().isVault)
        assertFalse(remoteFile.copy(isVault = false).toModel().isVault)
    }

    @Test
    fun `vault flag of OCFile survives the database`() {
        val vaultFolder = OC_FOLDER_WITH_SPACE_ID.copy(isVault = true)
        assertTrue(vaultFolder.toEntity().isVault)
        assertTrue(vaultFolder.toEntity().toModel().isVault)
        assertFalse(OC_FOLDER_WITH_SPACE_ID.toEntity().toModel().isVault)
    }

    @Test
    fun `remote vault space is mapped to a vault OCSpace`() {
        val vaultSpace = SPACE_RESPONSE.copy(libreGraphContentType = CONTENT_TYPE_VAULT).toModel(OC_ACCOUNT_NAME)
        assertEquals(CONTENT_TYPE_VAULT, vaultSpace.contentType)
        assertTrue(vaultSpace.isVault)
    }

    @Test
    fun `spaces without or with another content type are no vaults`() {
        assertFalse(SPACE_RESPONSE.toModel(OC_ACCOUNT_NAME).isVault)
        assertFalse(SPACE_RESPONSE.copy(libreGraphContentType = "application/vnd.opencloud.something-else").toModel(OC_ACCOUNT_NAME).isVault)
    }

    @Test
    fun `vault content type of OCSpace survives the database`() {
        val vaultSpace = OC_SPACE_PROJECT_WITHOUT_IMAGE.copy(contentType = CONTENT_TYPE_VAULT)
        assertEquals(CONTENT_TYPE_VAULT, vaultSpace.toEntity().contentType)
        assertTrue(vaultSpace.toEntity().toModel().isVault)
    }
}
