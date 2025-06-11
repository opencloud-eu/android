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

package eu.opencloud.android.domain.files.model

import android.os.Parcelable
import android.webkit.MimeTypeMap
import eu.opencloud.android.domain.availableoffline.model.AvailableOfflineStatus
import eu.opencloud.android.domain.availableoffline.model.AvailableOfflineStatus.AVAILABLE_OFFLINE
import eu.opencloud.android.domain.availableoffline.model.AvailableOfflineStatus.AVAILABLE_OFFLINE_PARENT
import eu.opencloud.android.domain.extensions.isOneOf
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.Locale

@Parcelize
data class OCFile(
    var id: Long? = null,
    var parentId: Long? = null,
    val owner: String,
    var length: Long,
    var creationTimestamp: Long? = 0,
    var modificationTimestamp: Long,
    val remotePath: String,
    var mimeType: String,
    var etag: String? = "",
    val permissions: String? = null,
    var remoteId: String? = null,
    val privateLink: String? = "",
    var storagePath: String? = null,
    var treeEtag: String? = "",
    var availableOfflineStatus: AvailableOfflineStatus? = null,
    var lastSyncDateForData: Long? = 0,
    var lastUsage: Long? = null,
    var needsToUpdateThumbnail: Boolean = false,
    var modifiedAtLastSyncForData: Long? = 0,
    var etagInConflict: String? = null,
    val fileIsDownloading: Boolean? = false,
    var sharedWithSharee: Boolean? = false,
    var sharedByLink: Boolean = false,
    val spaceId: String? = null,
) : Parcelable {

    val fileName: String
        get() = File(remotePath).name.let { it.ifBlank { ROOT_PATH } }

    /**
     * Use this to find out if this file is a folder.
     *
     * @return true if it is a folder
     */
    val isFolder
        get() = mimeType.isOneOf(MIME_DIR, MIME_DIR_UNIX)

    /**
     * @return 'True' if the file contains audio
     */
    val isAudio: Boolean
        get() = isOfType(MIME_PREFIX_AUDIO)

    /**
     * @return 'True' if the file contains video
     */
    val isVideo: Boolean
        get() = isOfType(MIME_PREFIX_VIDEO)

    /**
     * @return 'True' if the file contains an image
     */
    val isImage: Boolean
        get() = isOfType(MIME_PREFIX_IMAGE)

    /**
     * @return 'True' if the file is simple text (e.g. not application-dependent, like .doc or .docx)
     */
    val isText: Boolean
        get() = isOfType(MIME_PREFIX_TEXT)

    /**
     * @return 'True' if the file has the 'W' (can write) within its group of permissions
     */
    val hasWritePermission: Boolean
        get() = permissions?.contains(char = 'W', ignoreCase = true) ?: false

    /**
     * @return 'True' if the file has the 'D' (can delete) within its group of permissions
     */
    val hasDeletePermission: Boolean
        get() = permissions?.contains(char = 'D', ignoreCase = true) ?: false

    /**
     * @return 'True' if the file has the 'N' (can rename) within its group of permissions
     */
    val hasRenamePermission: Boolean
        get() = permissions?.contains(char = 'N', ignoreCase = true) ?: false

    /**
     * @return 'True' if the file has the 'V' (can move) within its group of permissions
     */
    val hasMovePermission: Boolean
        get() = permissions?.contains(char = 'V', ignoreCase = true) ?: false

    /**
     * @return 'True' if the file has the 'C' (can add file) within its group of permissions
     */
    val hasAddFilePermission: Boolean
        get() = permissions?.contains(char = 'C', ignoreCase = true) ?: false

    /**
     * @return 'True' if the file has the 'K' (can add subdirectories) within its group of permissions
     */
    val hasAddSubdirectoriesPermission: Boolean
        get() = permissions?.contains(char = 'K', ignoreCase = true) ?: false

    /**
     * @return 'True' if the file has the 'R' (can reshare) within its group of permissions
     */
    val hasResharePermission: Boolean
        get() = permissions?.contains(char = 'R', ignoreCase = true) ?: false

    /**
     * Use this to check if this file is available locally
     *
     * @return true if it is
     */
    val isAvailableLocally: Boolean
        get() =
            storagePath?.takeIf {
                it.isNotBlank()
            }?.let { storagePath ->
                File(storagePath).exists()
            } ?: false

    /**
     * Can be used to check, whether or not this file exists in the database
     * already
     *
     * @return true, if the file exists in the database
     */
    val fileExists: Boolean
        get() = id != null && id != -1L

    /**
     * @return 'True' if the file is hidden
     */
    val isHidden: Boolean
        get() = fileName.startsWith(".")

    val isSharedWithMe
        get() = permissions != null && permissions.contains(PERMISSION_SHARED_WITH_ME)

    val isAvailableOffline
        get() = availableOfflineStatus?.isOneOf(AVAILABLE_OFFLINE, AVAILABLE_OFFLINE_PARENT) ?: false

    val localModificationTimestamp: Long
        get() =
            storagePath?.takeIf {
                it.isNotBlank()
            }?.let { storagePath ->
                File(storagePath).lastModified()
            } ?: 0

    @Deprecated("Do not use this constructor. Remove it as soon as possible")
    constructor(remotePath: String, mimeType: String, parentId: Long?, owner: String, spaceId: String? = null) : this(
        remotePath = remotePath,
        mimeType = mimeType,
        parentId = parentId,
        owner = owner,
        spaceId = spaceId,
        modificationTimestamp = 0,
        length = 0
    )

    /**
     * get remote path of parent file
     * @return remote path
     */
    fun getParentRemotePath(): String {
        val parentPath: String = File(remotePath).parent ?: throw IllegalArgumentException("Parent path is null")
        return if (parentPath.endsWith("$PATH_SEPARATOR")) parentPath else "$parentPath$PATH_SEPARATOR"
    }

    fun copyLocalPropertiesFrom(sourceFile: OCFile) {
        parentId = sourceFile.parentId
        id = sourceFile.id
        lastSyncDateForData = sourceFile.lastSyncDateForData
        modifiedAtLastSyncForData = sourceFile.modifiedAtLastSyncForData
        storagePath = sourceFile.storagePath
        treeEtag = sourceFile.treeEtag
        etagInConflict = sourceFile.etagInConflict
        availableOfflineStatus = sourceFile.availableOfflineStatus
        lastUsage = sourceFile.lastUsage
    }

    /**
     * @param   type        Type to match in the file MIME type; it's MUST include the trailing "/"
     * @return              'True' if the file MIME type matches the received parameter in the type part.
     */
    private fun isOfType(type: String): Boolean =
        mimeType.startsWith(type) || getMimeTypeFromName()?.startsWith(type) ?: false

    fun getMimeTypeFromName(): String? {
        val extension = remotePath.substringAfterLast('.').lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    companion object {
        const val PATH_SEPARATOR = '/'
        const val ROOT_PATH: String = "/"
        const val ROOT_PARENT_ID: Long = 0
    }
}
