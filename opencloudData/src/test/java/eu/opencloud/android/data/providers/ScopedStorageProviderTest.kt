package eu.opencloud.android.data.providers

import android.content.Context
import android.net.Uri
import android.os.Environment
import eu.opencloud.android.domain.transfers.model.OCTransfer
import eu.opencloud.android.testutil.OC_FILE
import eu.opencloud.android.testutil.OC_SPACE_PROJECT_WITH_IMAGE
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ScopedStorageProviderTest {
    private lateinit var scopedStorageProvider: ScopedStorageProvider

    private lateinit var context: Context
    private lateinit var filesDir: File

    private val spaceId = OC_SPACE_PROJECT_WITH_IMAGE.id
    private val accountName = "opencloud"
    private val newName = "opencloudNewName.txt"
    private val uriEncoded = "/path/to/remote/?x=%D1%88%D0%B5%D0%BB%D0%BB%D1%8B"
    private val rootFolderName = "root_folder"
    private val expectedSizeOfDirectoryValue: Long = 100
    private val separator = File.separator
    private val remotePath =
        listOf("storage", "emulated", "0", "opencloud", "remotepath").joinToString(separator, prefix = separator)
    private val rootFolderPath get() = filesDir.absolutePath + File.separator + rootFolderName
    private lateinit var directory: File

    @Before
    fun setUp() {
        context = mockk()
        filesDir = Files.createTempDirectory("scoped-storage-provider").toFile().apply { deleteOnExit() }
        directory = File(filesDir, "dir").apply {
            mkdirs()
            File(this, "child.bin").writeBytes(ByteArray(expectedSizeOfDirectoryValue.toInt()))
        }

        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns filesDir

        scopedStorageProvider = spyk(ScopedStorageProvider(rootFolderName, context))
        every { context.filesDir } returns filesDir
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getPrimaryStorageDirectory returns filesDir`() {
        val result = scopedStorageProvider.getPrimaryStorageDirectory()
        assertEquals(filesDir, result)

        verify(exactly = 1) {
            Environment.getExternalStorageDirectory()
        }
    }

    @Test
    fun `getRootFolderPath returns the root folder path String`() {
        val rootFolderPath = filesDir.absolutePath + File.separator + rootFolderName
        val actualPath = scopedStorageProvider.getRootFolderPath()
        assertEquals(rootFolderPath, actualPath)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }

    }

    @Test
    fun `getDefaultSavePathFor returns the path with spaces when there is a space`() {
        // ScopedStorageProvider overrides getAccountDirectoryPath and does NOT use Uri.encode
        val accountDirectoryPath = filesDir.absolutePath + File.separator + rootFolderName + File.separator + accountName
        val expectedPath = accountDirectoryPath + File.separator + spaceId + File.separator + remotePath
        val actualPath = scopedStorageProvider.getDefaultSavePathFor(accountName, remotePath, spaceId)

        assertEquals(expectedPath, actualPath)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }
    }

    @Test
    fun `getDefaultSavePathFor returns the path without spaces when there is not space`() {
        val spaceId = null

        // ScopedStorageProvider overrides getAccountDirectoryPath and does NOT use Uri.encode
        val accountDirectoryPath = filesDir.absolutePath + File.separator + rootFolderName + File.separator + accountName
        val expectedPath = accountDirectoryPath + remotePath
        val actualPath = scopedStorageProvider.getDefaultSavePathFor(accountName, remotePath, spaceId)

        assertEquals(expectedPath, actualPath)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }
    }

    @Test
    fun `getExpectedRemotePath returns expected remote path with separator in the end when there is separator and is folder true`() {

        val isFolder = true
        val expectedPath = expectedRemotePath(remotePath, newName, isFolder)
        val actualPath = scopedStorageProvider.getExpectedRemotePath(remotePath, newName, isFolder)

        assertEquals(expectedPath, actualPath)
    }

    @Test
    fun `getExpectedRemotePath returns expected remote path with separator in the end when is separator and is folder false`() {

        val isFolder = false
        val expectedPath = expectedRemotePath(remotePath, newName, isFolder)
        val actualPath = scopedStorageProvider.getExpectedRemotePath(remotePath, newName, isFolder)

        assertEquals(expectedPath, actualPath)
    }

    @Test
    fun `getExpectedRemotePath returns expected remote path with separator in the end when is not separator and is folder true`() {

        val isFolder = true
        val expectedPath = expectedRemotePath(remotePath, newName, isFolder)
        val actualPath = scopedStorageProvider.getExpectedRemotePath(remotePath, newName, isFolder)

        assertEquals(expectedPath, actualPath)
    }

    @Test
    fun `getExpectedRemotePath returns expected remote path with separator in the end when is not separator and is folder false`() {
        val isFolder = false
        val expectedPath = expectedRemotePath(remotePath, newName, isFolder)
        val actualPath = scopedStorageProvider.getExpectedRemotePath(remotePath, newName, isFolder)

        assertEquals(expectedPath, actualPath)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `getExpectedRemotePath returns a IllegalArgumentException when there is not file`() {
        val isFolder = false
        val remotePath = ""

        scopedStorageProvider.getExpectedRemotePath(remotePath, newName, isFolder)
    }

    @Test
    fun `getTemporalPath returns expected temporal path with separator and space when there is a space`() {
        mockkStatic(Uri::class)
        every { Uri.encode(accountName, "@") } returns uriEncoded

        val temporalPathWithoutSpace = rootFolderPath + File.separator + "tmp" + File.separator + uriEncoded

        val expectedValue = temporalPathWithoutSpace + File.separator + spaceId
        val actualValue = scopedStorageProvider.getTemporalPath(accountName, spaceId)
        assertEquals(expectedValue, actualValue)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }
    }

    @Test
    fun `getTemporalPath returns expected temporal path neither with separator not space when there is not a space`() {
        val spaceId = null

        mockkStatic(Uri::class)
        every { Uri.encode(accountName, "@") } returns uriEncoded

        val expectedValue = rootFolderPath + File.separator + TEMPORAL_FOLDER_NAME + File.separator + uriEncoded
        val actualValue = scopedStorageProvider.getTemporalPath(accountName, spaceId)
        assertEquals(expectedValue, actualValue)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }
    }

    @Test
    fun `getLogsPath returns logs path`() {
        val expectedValue = rootFolderPath + File.separator + LOGS_FOLDER_NAME + File.separator
        val actualValue = scopedStorageProvider.getLogsPath()

        assertEquals(expectedValue, actualValue)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }
    }

    @Test
    fun `getUsableSpace returns usable space from the storage directory`() {
        val actualUsableSpace = scopedStorageProvider.getUsableSpace()
        assertTrue(actualUsableSpace > 0)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }
    }

    @Test
    fun `sizeOfDirectory returns the sum the file size in bytes (Long) when isDirectory is true doing a recursive call if it's a directory`() {
        val actualValue = scopedStorageProvider.sizeOfDirectory(directory)

        assertEquals(expectedSizeOfDirectoryValue, actualValue)
    }

    @Test
    fun `sizeOfDirectory returns the sum the file size in bytes (Long) when isDirectory is false without doing a recursive call`() {
        val tmpDir = Files.createTempDirectory("scoped-storage-size").toFile().apply {
            deleteOnExit()
            File(this, "single.bin").writeBytes(ByteArray(expectedSizeOfDirectoryValue.toInt()))
        }

        val actualValue = scopedStorageProvider.sizeOfDirectory(tmpDir)
        assertEquals(expectedSizeOfDirectoryValue, actualValue)
    }

    @Test
    fun `sizeOfDirectory returns zero value when directory not exists`() {
        val expectedSizeOfDirectoryValue: Long = 0

        val missingDir = File(filesDir, "does-not-exist")

        val actualValue = scopedStorageProvider.sizeOfDirectory(missingDir)

        assertEquals(expectedSizeOfDirectoryValue, actualValue)
    }

    @Test
    fun `deleteLocalFile calls getPrimaryStorageDirectory()`() {
        mockkStatic(Uri::class)
        every { Uri.encode(any(), any()) } returns uriEncoded
        scopedStorageProvider.deleteLocalFile(OC_FILE)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }
    }

    @Test
    fun `moveLocalFile calls getPrimaryStorageDirectory()`() {
        val finalStoragePath = "file.txt"
        mockkStatic(Uri::class)

        every { Uri.encode(any(), any()) } returns uriEncoded
        scopedStorageProvider.moveLocalFile(OC_FILE, finalStoragePath)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }
    }

    @Test
    fun `deleteCacheIfNeeded delete cache file when  transfer local path start with cacheDir`() {
        val transfer: OCTransfer = mockk()
        val accountName = "testAccount"
        val localPath = "/file.txt"

        mockkStatic(Uri::class)
        every { Uri.encode(any(), any()) } returns uriEncoded
        every { transfer.accountName } returns accountName
        every { transfer.localPath } returns localPath

        scopedStorageProvider.deleteCacheIfNeeded(transfer)

        verify(exactly = 1) {
            scopedStorageProvider.getPrimaryStorageDirectory()
        }
    }

    private fun expectedRemotePath(current: String, newName: String, isFolder: Boolean): String {
        var parent = File(current).parent ?: throw IllegalArgumentException("Parent path is null")
        parent = if (parent.endsWith(File.separator)) parent else parent + File.separator
        var newRemotePath = parent + newName
        if (isFolder) {
            newRemotePath += File.separator
        }
        return newRemotePath
    }

    companion object {
        private const val LOGS_FOLDER_NAME = "logs"
        private const val TEMPORAL_FOLDER_NAME = "tmp"
    }

}
