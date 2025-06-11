/**
 * openCloud Android client application
 *
 * @author David González Verdugo
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

package eu.opencloud.android.utils

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.resources.files.CheckPathExistenceRemoteOperation

object RemoteFileUtils {
    /**
     * Checks if remotePath does not exist in the server and returns it, or adds
     * a suffix to it in order to avoid the server file is overwritten.
     *
     * @param openCloudClient
     * @param remotePath
     * @return
     */
    fun getAvailableRemotePath(
        openCloudClient: OpenCloudClient,
        remotePath: String,
        spaceWebDavUrl: String? = null,
        isUserLogged: Boolean,
    ): String {
        var checkExistsFile = existsFile(
            openCloudClient = openCloudClient,
            remotePath = remotePath,
            spaceWebDavUrl = spaceWebDavUrl,
            isUserLogged = isUserLogged,
        )
        if (!checkExistsFile) {
            return remotePath
        }
        val pos = remotePath.lastIndexOf(".")
        var suffix: String
        var extension = ""
        if (pos >= 0) {
            extension = remotePath.substring(pos + 1)
            remotePath.apply {
                substring(0, pos)
            }
        }
        var count = 1
        do {
            suffix = " ($count)"
            checkExistsFile = if (pos >= 0) {
                existsFile(
                    openCloudClient = openCloudClient,
                    remotePath = "${remotePath.substringBeforeLast('.', "")}$suffix.$extension",
                    spaceWebDavUrl = spaceWebDavUrl,
                    isUserLogged = isUserLogged,
                )
            } else {
                existsFile(
                    openCloudClient = openCloudClient,
                    remotePath = remotePath + suffix,
                    spaceWebDavUrl = spaceWebDavUrl,
                    isUserLogged = isUserLogged,
                )
            }
            count++
        } while (checkExistsFile)
        return if (pos >= 0) {
            "${remotePath.substringBeforeLast('.', "")}$suffix.$extension"
        } else {
            remotePath + suffix
        }
    }

    private fun existsFile(
        openCloudClient: OpenCloudClient,
        remotePath: String,
        spaceWebDavUrl: String?,
        isUserLogged: Boolean,
    ): Boolean {
        val existsOperation =
            CheckPathExistenceRemoteOperation(
                remotePath = remotePath,
                isUserLoggedIn = isUserLogged,
                spaceWebDavUrl = spaceWebDavUrl,
            )
        return existsOperation.execute(openCloudClient).isSuccess
    }
}
