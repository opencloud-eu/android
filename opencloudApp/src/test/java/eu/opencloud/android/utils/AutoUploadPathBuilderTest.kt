package eu.opencloud.android.utils

import androidx.documentfile.provider.DocumentFile
import eu.opencloud.android.domain.automaticuploads.model.FolderBackUpConfiguration
import eu.opencloud.android.domain.automaticuploads.model.UploadBehavior
import eu.opencloud.android.domain.automaticuploads.model.UseSubfoldersBehaviour
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AutoUploadPathBuilderTest {

    private val utcZone = ZoneId.of("UTC")
    private val fixedTimestamp = ZonedDateTime.of(2025, 1, 15, 12, 0, 0, 0, utcZone).toInstant().toEpochMilli()

    private val uploadPath = "/CameraUpload"
    private val fileName = "photo.jpg"

    @Test
    fun `buildUploadPath with NONE returns flat path`() {
        val documentFile = createDocumentFile(fixedTimestamp, fileName)
        val config = createConfig(UseSubfoldersBehaviour.NONE, uploadPath)

        val result = AutoUploadPathBuilder.buildUploadPath(documentFile, config, utcZone)

        assertEquals("$uploadPath/$fileName", result)
    }

    @Test
    fun `buildUploadPath with YEAR returns year subfolder`() {
        val documentFile = createDocumentFile(fixedTimestamp, fileName)
        val config = createConfig(UseSubfoldersBehaviour.YEAR, uploadPath)

        val result = AutoUploadPathBuilder.buildUploadPath(documentFile, config, utcZone)

        assertEquals("$uploadPath/2025/$fileName", result)
    }

    @Test
    fun `buildUploadPath with YEAR_MONTH returns year and month subfolders`() {
        val documentFile = createDocumentFile(fixedTimestamp, fileName)
        val config = createConfig(UseSubfoldersBehaviour.YEAR_MONTH, uploadPath)

        val result = AutoUploadPathBuilder.buildUploadPath(documentFile, config, utcZone)

        assertEquals("$uploadPath/2025/01/$fileName", result)
    }

    @Test
    fun `buildUploadPath with YEAR_MONTH_DAY returns year month and day subfolders`() {
        val documentFile = createDocumentFile(fixedTimestamp, fileName)
        val config = createConfig(UseSubfoldersBehaviour.YEAR_MONTH_DAY, uploadPath)

        val result = AutoUploadPathBuilder.buildUploadPath(documentFile, config, utcZone)

        assertEquals("$uploadPath/2025/01/15/$fileName", result)
    }

    private fun createDocumentFile(lastModified: Long, name: String): DocumentFile {
        val documentFile = mockk<DocumentFile>()
        every { documentFile.lastModified() } returns lastModified
        every { documentFile.name } returns name
        return documentFile
    }

    private fun createConfig(
        behaviour: UseSubfoldersBehaviour,
        uploadPath: String,
    ): FolderBackUpConfiguration = FolderBackUpConfiguration(
        accountName = "test@example.com",
        behavior = UploadBehavior.COPY,
        sourcePath = "/storage/emulated/0/DCIM/Camera",
        uploadPath = uploadPath,
        useSubfoldersBehaviour = behaviour,
        wifiOnly = false,
        chargingOnly = false,
        lastSyncTimestamp = 0L,
        name = "test",
        spaceId = null,
    )
}
