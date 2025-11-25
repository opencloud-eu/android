package eu.opencloud.android.lib.resources.files.tus

import android.accounts.Account
import android.accounts.AccountManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.accounts.AccountUtils
import eu.opencloud.android.lib.common.authentication.OpenCloudCredentialsFactory
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.files.tus.CreateTusUploadRemoteOperation.Base64Encoder
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class TusIntegrationTest {

    private lateinit var server: MockWebServer
    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }

    private val accountType = "com.example"
    private val userId = "user-123"
    private val username = "user@example.com"
    private val token = "TEST_TOKEN"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newClient(): OpenCloudClient {
        val base = server.url("/").toString().removeSuffix("/")

        val am = AccountManager.get(context)
        val account = Account("$username@${Uri.parse(base).host}", accountType)
        am.addAccountExplicitly(account, null, null)
        am.setUserData(account, AccountUtils.Constants.KEY_OC_BASE_URL, base)
        am.setUserData(account, AccountUtils.Constants.KEY_ID, userId)

        val ocAccount = OpenCloudAccount(account, context)
        val client = OpenCloudClient(ocAccount.baseUri, /*connectionValidator*/ null, /*sync*/ true, /*singleSession*/ null, context)
        client.account = ocAccount
        client.credentials = OpenCloudCredentialsFactory.newBearerCredentials(username, token)
        return client
    }

    @Test
    fun create_patch_head_delete_success() {
        val client = newClient()

        val collectionPath = "/remote.php/dav/uploads/$userId"
        val locationPath = "$collectionPath/UPLD-123"
        val localFile = File.createTempFile("tus", ".bin").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }

        // 1) POST Create -> 201 + Location
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .addHeader("Tus-Resumable", "1.0.0")
                .addHeader("Location", locationPath)
        )

        // 2) PATCH -> 204 + Upload-Offset
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .addHeader("Upload-Offset", "5")
        )

        // 3) HEAD -> 204 + Upload-Offset
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .addHeader("Upload-Offset", "5")
        )

        // 4) DELETE -> 204
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        // Create
        val create = CreateTusUploadRemoteOperation(
            file = localFile,
            remotePath = "/test.bin",
            mimetype = "application/octet-stream",
            metadata = mapOf("filename" to "test.bin"),
            useCreationWithUpload = false,
            firstChunkSize = null,
            tusUrl = null,
            collectionUrlOverride = server.url(collectionPath).toString(),
            base64Encoder = object : Base64Encoder {
                override fun encode(bytes: ByteArray): String =
                    Base64.getEncoder().encodeToString(bytes)
            }
        )
        val createResult = create.execute(client)
        if (!createResult.isSuccess) {
            val msg = "DEBUG: Create operation failed. Code: ${createResult.code}, " +
                "HttpCode: ${createResult.httpCode}, Exception: ${createResult.exception}"
            throw RuntimeException(msg, createResult.exception)
        }
        assertTrue("Create operation failed", createResult.isSuccess)
        val absoluteLocation = createResult.data
        assertNotNull(absoluteLocation)
        println("absoluteLocation: $absoluteLocation")
        println("locationPath: $locationPath")
        println("endsWith: ${absoluteLocation!!.endsWith(locationPath)}")
        assertTrue(absoluteLocation.endsWith(locationPath))

        // Verify POST request headers
        val postReq = server.takeRequest()
        assertEquals("POST", postReq.method)
        assertEquals("Bearer $token", postReq.getHeader("Authorization"))
        assertEquals("1.0.0", postReq.getHeader("Tus-Resumable"))
        assertEquals("5", postReq.getHeader("Upload-Length"))
        assertEquals(collectionPath, postReq.path)

        // Patch
        val patch = PatchTusUploadChunkRemoteOperation(
            localPath = localFile.absolutePath,
            uploadUrl = absoluteLocation,
            offset = 0,
            chunkSize = 5
        )
        val patchResult = patch.execute(client)
        assertTrue(patchResult.isSuccess)
        assertEquals(5L, patchResult.data)

        // Verify PATCH request
        val patchReq = server.takeRequest()
        assertEquals("PATCH", patchReq.method)
        assertEquals("Bearer $token", patchReq.getHeader("Authorization"))
        assertEquals("1.0.0", patchReq.getHeader("Tus-Resumable"))
        assertEquals("0", patchReq.getHeader("Upload-Offset"))
        assertEquals("application/offset+octet-stream", patchReq.getHeader("Content-Type"))
        assertEquals(Uri.parse(absoluteLocation).encodedPath, patchReq.path)

        // Head
        val head = GetTusUploadOffsetRemoteOperation(absoluteLocation)
        val headResult = head.execute(client)
        assertTrue(headResult.isSuccess)
        assertEquals(5L, headResult.data)

        val headReq = server.takeRequest()
        assertEquals("HEAD", headReq.method)
        assertEquals("Bearer $token", headReq.getHeader("Authorization"))
        assertEquals("1.0.0", headReq.getHeader("Tus-Resumable"))

        // Delete
        val del = DeleteTusUploadRemoteOperation(absoluteLocation)
        val delResult = del.execute(client)
        assertTrue(delResult.isSuccess)

        val delReq = server.takeRequest()
        assertEquals("DELETE", delReq.method)
        assertEquals("Bearer $token", delReq.getHeader("Authorization"))
        assertEquals("1.0.0", delReq.getHeader("Tus-Resumable"))
    }

    @Test
    fun patch_wrong_offset_returns_conflict() {
        val client = newClient()
        val locationPath = "/remote.php/dav/uploads/$userId/UPLD-err"

        // No need to POST; directly simulate an existing upload URL
        // Server responds 412 to PATCH
        server.enqueue(MockResponse().setResponseCode(412))

        val tmp = File.createTempFile("tus", ".bin")
        tmp.writeBytes(ByteArray(10) { 1 })

        val patch = PatchTusUploadChunkRemoteOperation(
            localPath = tmp.absolutePath,
            uploadUrl = server.url(locationPath).toString(),
            offset = 0,
            chunkSize = 10
        )
        val res = patch.execute(client)
        assertFalse(res.isSuccess)
        assertEquals(RemoteOperationResult.ResultCode.CONFLICT, res.code)

        val req = server.takeRequest()
        assertEquals("PATCH", req.method)
        assertEquals("Bearer $token", req.getHeader("Authorization"))
    }

    @Test
    fun cancel_before_start_returns_cancelled() {
        val client = newClient()
        val locationPath = "/remote.php/dav/uploads/$userId/UPLD-cancel"

        // No requests expected because we cancel before run
        val tmp = File.createTempFile("tus", ".bin")
        tmp.writeBytes(ByteArray(1024 * 64) { 1 })

        val op = PatchTusUploadChunkRemoteOperation(
            localPath = tmp.absolutePath,
            uploadUrl = server.url(locationPath).toString(),
            offset = 0,
            chunkSize = 1024 * 64L
        )
        op.cancel()
        val result = op.execute(client)
        assertTrue(result.isCancelled)
    }
}
