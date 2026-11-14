/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
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

package eu.opencloud.android.domain.automaticuploads.model

data class FolderBackUpConfiguration(
    val accountName: String,
    val behavior: UploadBehavior,
    val sourcePath: String,
    val uploadPath: String,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val lastSyncTimestamp: Long,
    val name: String,
    val spaceId: String?,
    val mediaType: AutomaticUploadsMediaType = AutomaticUploadsMediaType.IMAGES_AND_VIDEOS,
) {

    val isPictureUploads get() = name == pictureUploadsName
    val isVideoUploads get() = name == videoUploadsName
    val isCustomUploads get() = name.startsWith(customUploadsNamePrefix)

    companion object {
        const val pictureUploadsName = "Picture uploads"
        const val videoUploadsName = "Video uploads"

        /**
         * Additional, user-defined folder uploads are stored in the same table as picture and video uploads.
         * Each one gets a unique name built with this prefix plus a random UUID.
         */
        const val customUploadsNamePrefix = "Folder uploads:"

        fun newCustomUploadsName(): String = customUploadsNamePrefix + java.util.UUID.randomUUID().toString()
    }
}

/**
 * Kind of files that an automatic upload configuration picks up from its source folder.
 * Only used by custom folder uploads; picture and video uploads keep their fixed behaviour.
 */
enum class AutomaticUploadsMediaType {
    IMAGES, VIDEOS, IMAGES_AND_VIDEOS, ALL_FILES;

    fun matches(mimeType: String): Boolean =
        when (this) {
            IMAGES -> mimeType.startsWith("image/")
            VIDEOS -> mimeType.startsWith("video/")
            IMAGES_AND_VIDEOS -> mimeType.startsWith("image/") || mimeType.startsWith("video/")
            ALL_FILES -> true
        }

    companion object {
        fun fromString(string: String?): AutomaticUploadsMediaType =
            values().firstOrNull { it.name.equals(string, ignoreCase = true) } ?: IMAGES_AND_VIDEOS
    }
}

/**
 * Behaviour to after uploading a file.
 *
 * Move - Remove the source file after a successful upload
 * Copy - Keep the source file after a successful upload
 */
enum class UploadBehavior {
    MOVE, COPY;

    @Deprecated("Legacy Local Behavior. Remove asap")
    fun toLegacyLocalBehavior(): Int =
        when (this) {
            MOVE -> LEGACY_LOCAL_BEHAVIOUR_MOVE
            COPY -> LEGACY_LOCAL_BEHAVIOUR_COPY
        }

    companion object {
        private const val LEGACY_LOCAL_BEHAVIOUR_COPY = 0
        private const val LEGACY_LOCAL_BEHAVIOUR_MOVE = 1

        fun fromString(string: String): UploadBehavior =
            if (string.equals("MOVE", ignoreCase = true)) {
                MOVE
            } else {
                COPY
            }
    }
}
