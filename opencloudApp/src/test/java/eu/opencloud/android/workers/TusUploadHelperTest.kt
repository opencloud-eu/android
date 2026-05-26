/**
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
 */

package eu.opencloud.android.workers

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.opencloud.android.domain.capabilities.model.OCCapability
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.testutil.OC_TRANSFER
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TusUploadHelperTest {

    private lateinit var server: MockWebServer
    private lateinit var transferRepository: TransferRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        transferRepository = mockk(relaxed = true)
        every { transferRepository.updateTusState(any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun upload_createsSessionWithFirstChunkAndClearsTusState() {
        val localFile = tempFileWithBytes(byteArrayOf(1, 2, 3, 4, 5))
        val uploadUrl = "/uploads/new-session"
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .addHeader("Location", uploadUrl)
                .addHeader("Upload-Offset", "5")
        )
        server.enqueue(MockResponse().setResponseCode(404))
        val progress = mutableListOf<Long>()

        val resultEtag = TusUploadHelper(transferRepository).upload(
            client = newClient(),
            transfer = OC_TRANSFER.copy(tusUploadUrl = null, tusUploadChecksum = "sha256:abc"),
            uploadId = UPLOAD_ID,
            localPath = localFile.absolutePath,
            remotePath = "/Photos/image.jpg",
            fileSize = localFile.length(),
            mimeType = "image/jpeg",
            lastModified = "1700000000",
            tusSupport = tusSupport(),
            progressListener = null,
            progressCallback = { offset, _ -> progress += offset },
            spaceWebDavUrl = server.url("/remote.php/dav/spaces/personal").toString(),
        )

        assertNull(resultEtag)
        assertEquals(listOf(5L), progress)

        val createRequest = server.takeRequest()
        assertEquals("POST", createRequest.method)
        assertEquals("/remote.php/dav/spaces/personal/Photos", createRequest.path)
        assertEquals("0", createRequest.getHeader("Upload-Offset"))
        assertEquals("5", createRequest.getHeader("Upload-Length"))
        assertTrue(createRequest.getHeader("Upload-Metadata")!!.contains("checksum"))

        verify {
            transferRepository.updateTusState(
                id = UPLOAD_ID,
                tusUploadUrl = server.url(uploadUrl).toString(),
                tusUploadLength = 5,
                tusUploadMetadata = "filename=image.jpg;mimetype=image/jpeg;mtime=1700000000;checksum=sha256 abc",
                tusUploadChecksum = "sha256:abc",
                tusResumableVersion = "1.0.0",
                tusUploadExpires = null,
                tusUploadConcat = null,
            )
            transferRepository.updateTusState(
                id = UPLOAD_ID,
                tusUploadUrl = null,
                tusUploadLength = null,
                tusUploadMetadata = null,
                tusUploadChecksum = null,
                tusResumableVersion = null,
                tusUploadExpires = null,
                tusUploadConcat = null,
            )
        }
    }

    @Test
    fun upload_resumesExistingSessionFromServerOffset() {
        val localFile = tempFileWithBytes(byteArrayOf(1, 2, 3, 4, 5))
        val existingTusUrl = server.url("/uploads/existing-session").toString()
        server.enqueue(MockResponse().setResponseCode(204).addHeader("Upload-Offset", "2"))
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .addHeader("Upload-Offset", "5")
                .addHeader("ETag", "\"remote-etag\"")
        )
        val progress = mutableListOf<Long>()

        val resultEtag = TusUploadHelper(transferRepository).upload(
            client = newClient(),
            transfer = OC_TRANSFER.copy(tusUploadUrl = existingTusUrl),
            uploadId = UPLOAD_ID,
            localPath = localFile.absolutePath,
            remotePath = "/Photos/image.jpg",
            fileSize = localFile.length(),
            mimeType = "image/jpeg",
            lastModified = null,
            tusSupport = tusSupport(maxChunkSize = 3),
            progressListener = null,
            progressCallback = { offset, _ -> progress += offset },
        )

        assertEquals("remote-etag", resultEtag)
        assertEquals(listOf(2L, 5L), progress)

        val headRequest = server.takeRequest()
        assertEquals("HEAD", headRequest.method)
        assertEquals("/uploads/existing-session", headRequest.path)

        val patchRequest = server.takeRequest()
        assertEquals("PATCH", patchRequest.method)
        assertEquals("/uploads/existing-session", patchRequest.path)
        assertEquals("2", patchRequest.getHeader("Upload-Offset"))

        verify(exactly = 1) {
            transferRepository.updateTusState(
                id = UPLOAD_ID,
                tusUploadUrl = null,
                tusUploadLength = null,
                tusUploadMetadata = null,
                tusUploadChecksum = null,
                tusResumableVersion = null,
                tusUploadExpires = null,
                tusUploadConcat = null,
            )
        }
    }

    @Test
    fun shouldAttemptTusUpload_usesFallbackForSmallFilesWithoutPendingSession() {
        val shouldAttemptTusUpload = TusUploadHelper.shouldAttemptTusUpload(
            fileSize = TusUploadHelper.DEFAULT_CHUNK_SIZE - 1,
            tusSupport = tusSupport(),
            tusUploadUrl = null,
        )

        assertFalse(shouldAttemptTusUpload)
    }

    @Test
    fun shouldAttemptTusUpload_usesTusForLargeFilesWithServerSupport() {
        val shouldAttemptTusUpload = TusUploadHelper.shouldAttemptTusUpload(
            fileSize = TusUploadHelper.DEFAULT_CHUNK_SIZE,
            tusSupport = tusSupport(),
            tusUploadUrl = null,
        )

        assertTrue(shouldAttemptTusUpload)
    }

    @Test
    fun shouldAttemptTusUpload_resumesPendingSessionsWithoutServerSupport() {
        val shouldAttemptTusUpload = TusUploadHelper.shouldAttemptTusUpload(
            fileSize = 1,
            tusSupport = null,
            tusUploadUrl = "https://server.test/uploads/session",
        )

        assertTrue(shouldAttemptTusUpload)
    }

    private fun newClient(): OpenCloudClient =
        OpenCloudClient(
            Uri.parse(server.url("/").toString().removeSuffix("/")),
            null,
            true,
            null,
            ApplicationProvider.getApplicationContext()
        )

    private fun tempFileWithBytes(bytes: ByteArray): File =
        File.createTempFile("tus-helper", ".bin").apply {
            writeBytes(bytes)
        }

    private fun tusSupport(maxChunkSize: Int = 10): OCCapability.TusSupport =
        OCCapability.TusSupport(
            version = "1.0.0",
            resumable = "1.0.0",
            extension = "creation,creation-with-upload",
            maxChunkSize = maxChunkSize,
            httpMethodOverride = null
        )

    companion object {
        private const val UPLOAD_ID = 42L
    }
}
