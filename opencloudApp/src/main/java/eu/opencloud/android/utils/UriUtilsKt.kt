package eu.opencloud.android.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import eu.opencloud.android.R
import eu.opencloud.android.domain.files.model.OCFile
import timber.log.Timber
import java.io.File

object UriUtilsKt {
    fun getExposedFileUriForOCFile(context: Context, ocFile: OCFile): Uri? {
        if (ocFile.storagePath == null || ocFile.storagePath.toString().isEmpty()) {
            return null
        }
        // Use the FileProvider to get a content URI
        return try {
            FileProvider.getUriForFile(
                context,
                context.getString(R.string.file_provider_authority),
                File(ocFile.storagePath)
            )
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "File can't be exported")
            null
        }
    }

    /**
     * The URI to the file contents, if stored locally
     *
     * @return A URI to the local copy of the file, or NULL if not stored in the device
     */
    fun getStorageUriForFile(file: OCFile): Uri? {
        if (file.storagePath == null || file.length == 0.toLong()) {
            return null
        }

        return Uri.Builder().apply {
            scheme(ContentResolver.SCHEME_FILE)
            path(file.storagePath)
        }.build()
    }
}
