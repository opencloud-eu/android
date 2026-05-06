package eu.opencloud.android.utils

import androidx.documentfile.provider.DocumentFile
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.domain.automaticuploads.model.UseSubfoldersBehaviour
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AutoUploadPathBuilder {
    fun buildUploadPath(
        documentFile: DocumentFile,
        folderBackUpConfiguration: FolderBackUpConfiguration,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val pathBuilder = StringBuilder(folderBackUpConfiguration.uploadPath.plus(File.separator))

        val lastModifiedDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(documentFile.lastModified()), zoneId)
        val yearStr = lastModifiedDateTime.format(DateTimeFormatter.ofPattern("yyyy"))
        val monthStr = lastModifiedDateTime.format(DateTimeFormatter.ofPattern("MM"))
        val dayStr = lastModifiedDateTime.format(DateTimeFormatter.ofPattern("dd"))

        when (folderBackUpConfiguration.useSubfoldersBehaviour) {
            UseSubfoldersBehaviour.YEAR_MONTH_DAY -> {
                pathBuilder.append(yearStr).append(File.separator)
                pathBuilder.append(monthStr).append(File.separator)
                pathBuilder.append(dayStr).append(File.separator)
            }

            UseSubfoldersBehaviour.YEAR_MONTH -> {
                pathBuilder.append(yearStr).append(File.separator)
                pathBuilder.append(monthStr).append(File.separator)
            }

            UseSubfoldersBehaviour.YEAR -> {
                pathBuilder.append(yearStr).append(File.separator)
            }

            else -> {}
        }
        return pathBuilder.append(documentFile.name).toString()
    }
}
