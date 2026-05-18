/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 *
 * Copyright (C) 2021 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.opencloud.android.data.providers

import android.content.Context
import eu.opencloud.android.data.providers.implementation.OCSharedPreferencesProvider
import timber.log.Timber
import java.io.File

class ScopedStorageProvider(
    rootFolderName: String,
    private val context: Context,
    private val preferencesProvider: SharedPreferencesProvider
) : LocalStorageProvider(rootFolderName) {

    private var rootFolderPath: String? = null

    constructor(rootFolderName: String, context: Context) : this(
        rootFolderName = rootFolderName,
        context = context,
        preferencesProvider = OCSharedPreferencesProvider(context)
    )

    override fun getPrimaryStorageDirectory(): File {
        val fileManagerAccessEnabled = preferencesProvider.getBoolean("enable_file_manager_access", false)

        if (fileManagerAccessEnabled) {
            val mediaDir = context.externalMediaDirs.firstOrNull()
            if (mediaDir != null) {
                if (!mediaDir.exists()) {
                    mediaDir.mkdirs()
                }
                return mediaDir
            }
            Timber.w("File manager access enabled but external media dir is unavailable")
        }

        val externalDir = context.getExternalFilesDir(null)
        val internalDir = context.filesDir
        val internalRoot = File(internalDir, rootFolderName)
        val internalRootLegacy = File(internalDir, rootFolderName.lowercase())

        // If there's existing data in internal storage, keep using it to avoid a split storage layout.
        // Check both current rootFolderName and legacy lowercase variant for existing installs.
        // New installs without prior data will use external storage if available.
        return if (
            (internalRoot.exists() && internalRoot.isDirectory && internalRoot.listFiles()?.isNotEmpty() == true) ||
            (internalRootLegacy.exists() && internalRootLegacy.isDirectory)
        ) {
            internalDir
        } else {
            externalDir ?: internalDir
        }
    }

    override fun getRootFolderPath(): String {
        rootFolderPath?.let { return it }

        val newPath = super.getRootFolderPath()
        val newDir = File(newPath)

        if (newDir.exists()) {
            rootFolderPath = newPath
            return newPath
        }

        val primaryStorage = getPrimaryStorageDirectory().absolutePath
        val oldFolderName = rootFolderName.lowercase()
        val oldPath = primaryStorage + File.separator + oldFolderName
        val oldDir = File(oldPath)

        if (oldDir.exists() && oldDir.isDirectory) {
            try {
                newDir.parentFile?.mkdirs()
                if (oldDir.renameTo(newDir)) {
                    Timber.i("Migrated root folder from '$oldFolderName' to '$rootFolderName'")
                    rootFolderPath = newPath
                    return newPath
                } else if (newDir.exists() && oldDir.canonicalPath == newDir.canonicalPath) {
                    // Case-insensitive filesystem: renameTo may fail but both paths refer to the same directory
                    Timber.i("Root folder already accessible at new path (case-insensitive filesystem)")
                    rootFolderPath = newPath
                    return newPath
                } else {
                    Timber.w("Failed to rename root folder from '$oldFolderName' to '$rootFolderName', using old path")
                    rootFolderPath = oldPath
                    return oldPath
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to migrate root folder name")
                rootFolderPath = oldPath
                return oldPath
            }
        }

        rootFolderPath = newPath
        return newPath
    }

    override fun getAccountDirectoryPath(accountName: String): String =
        getRootFolderPath() + File.separator + sanitizeAccountName(accountName)

    override fun invalidateCache() {
        rootFolderPath = null
    }
}
