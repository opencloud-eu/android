package eu.opencloud.android.workers

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import eu.opencloud.android.R
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase
import eu.opencloud.android.domain.transfers.TransferRepository
import eu.opencloud.android.domain.transfers.model.TransferStatus
import eu.opencloud.android.lib.common.OpenCloudAccount
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.SingleSessionManager
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.resources.files.CheckPathExistenceRemoteOperation
import eu.opencloud.android.lib.resources.files.CreateRemoteFolderOperation
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.providers.UploadManager
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_TRANSFER
import eu.opencloud.android.utils.NotificationUtils
import io.mockk.*
import io.tus.android.client.TusAndroidUpload
import io.tus.java.client.ProtocolException
import io.tus.java.client.TusClient
import io.tus.java.client.TusHttpConnection
import io.tus.java.client.TusUploader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class TusUploadWorkerTest {

    private val testDispatcher = TestCoroutineDispatcher()

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters

    private lateinit var transferRepository: TransferRepository
    private lateinit var uploadManager: UploadManager
    private lateinit var getWebDavUrlForSpaceUseCase: GetWebDavUrlForSpaceUseCase

    private lateinit var account: Account
    private lateinit var ocClient: OpenCloudClient

    @Before
    fun setUp() {
        mockkStatic(AccountUtils::class)
        mockkStatic(SingleSessionManager::class)
        mockkObject(NotificationUtils)
        Dispatchers.setMain(testDispatcher)

        // Koin DI for injected deps inside worker
        transferRepository = mockk(relaxUnitFun = true)
        uploadManager = mockk(relaxUnitFun = true)
        getWebDavUrlForSpaceUseCase = mockk()

        startKoin {
            allowOverride(true)
            modules(
                module {
                    factory { transferRepository }
                    factory { uploadManager }
                    factory { getWebDavUrlForSpaceUseCase }
                }
            )
        }

        context = mockk(relaxed = true) {
            every { getString(R.string.uploader_upload_failed_credentials_error) } returns "Credentials error"
            every { getString(R.string.uploader_upload_failed_ticker) } returns "Upload failed"
            every { resources } returns mockk(relaxed = true)
        }

        // WorkerParameters only needs inputData for our tests
        workerParams = mockk(relaxed = true)

        // Account and client mocking
        account = Account(OC_ACCOUNT_NAME, "eu.opencloud")
        every { AccountUtils.getOpenCloudAccountByName(any(), any()) } returns account
        every { AccountUtils.getCurrentOpenCloudAccount(any()) } returns account
        val sessionManager = mockk<SingleSessionManager>()
        every { SingleSessionManager.getDefaultSingleton() } returns sessionManager
        ocClient = mockk(relaxed = true)
        every { sessionManager.getClientFor(any<OpenCloudAccount>(), any()) } returns ocClient

        // Default: WebDAV URL
        every { getWebDavUrlForSpaceUseCase.invoke(any()) } returns "https://server/webdav/"

        // Default: RemoteFileUtils - leave real unless overridden in tests that need it
    }

    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
        resetMain()
    }

    private fun buildInput(
        accountName: String? = OC_ACCOUNT_NAME,
        remotePath: String? = "/remote/path/file.txt",
        contentUri: String? = null,
        localPath: String? = null,
        uploadId: Long = 10L,
    ): Data {
        val pairs = mutableMapOf<String, Any>()
        accountName?.let { pairs[TusUploadWorker.KEY_PARAM_ACCOUNT_NAME] = it }
        remotePath?.let { pairs[TusUploadWorker.KEY_PARAM_UPLOAD_PATH] = it }
        contentUri?.let { pairs[TusUploadWorker.KEY_PARAM_CONTENT_URI] = it }
        localPath?.let { pairs[TusUploadWorker.KEY_PARAM_LOCAL_PATH] = it }
        pairs[TusUploadWorker.KEY_PARAM_UPLOAD_ID] = uploadId
        return Data.Builder().apply { pairs.forEach { (k, v) -> when (v) {
            is String -> putString(k, v)
            is Long -> putLong(k, v)
            else -> error("Unsupported type")
        } } }.build()
    }

    private fun makeWorker(input: Data): TusUploadWorker {
        every { workerParams.inputData } returns input
        return TusUploadWorker(context, workerParams)
    }

    @Test
    fun parametersInvalid_returnsFailureEarly() = runBlocking {
        // Missing upload id and remote path
        val input = buildInput(remotePath = null, uploadId = -1L, localPath = null, contentUri = null)
        val worker = makeWorker(input)

        val result = worker.doWork()

        // If parameters invalid, repository should not be set to in progress
        verify(exactly = 0) { transferRepository.updateTransferStatusToInProgressById(any()) }
        // We cannot compare Result instances directly across WorkManager; assert failure by behavior only
        assertTrue(result.toString().contains("FAILURE", ignoreCase = true))
    }

    @Test
    fun parentFolderMissing_triggersRemoteCreation() = runBlocking {
        val tmp = File.createTempFile("oc", ".txt").apply { writeText("hello") }
        val ocTransfer = OC_TRANSFER.copy(localPath = tmp.absolutePath)
        every { transferRepository.getTransferById(10L) } returns ocTransfer

        // Folder check returns FILE_NOT_FOUND
        mockkConstructor(CheckPathExistenceRemoteOperation::class)
        mockkConstructor(CreateRemoteFolderOperation::class)
        every { anyConstructed<CheckPathExistenceRemoteOperation>().execute(ocClient) } returns
            RemoteOperationResult<Boolean>(RemoteOperationResult.ResultCode.FILE_NOT_FOUND).apply { setData(false) }
        every { anyConstructed<CreateRemoteFolderOperation>().execute(ocClient) } returns
            RemoteOperationResult<Boolean>(RemoteOperationResult.ResultCode.OK).apply { setData(true) }

        // Mock Tus pipeline to finish immediately
        val tusClient = mockk<TusClient>()
        val uploader = mockk<TusUploader>(relaxed = true) {
            every { uploadChunk() } returns -1
            every { finish() } just Runs
            every { offset } returns 0
        }
        every { uploadManager.createTusClient(ocClient) } returns tusClient
        mockkConstructor(TusAndroidUpload::class)
        every { anyConstructed<TusAndroidUpload>().size } returns 100
        every { anyConstructed<TusAndroidUpload>().setMetadata(any()) } just Runs
        every { tusClient.resumeOrCreateUpload(any()) } returns uploader

        val worker = makeWorker(buildInput(localPath = ocTransfer.localPath))
        val result = worker.doWork()

        verify { anyConstructed<CreateRemoteFolderOperation>().execute(ocClient) }
        verify { transferRepository.updateTransferStatusToInProgressById(10L) }
        verify { transferRepository.updateTransferWhenFinished(10L, TransferStatus.TRANSFER_SUCCEEDED, any(), any()) }
        assertTrue(result.toString().contains("SUCCESS", ignoreCase = true))
    }

    @Test
    fun nameCollision_renamesUploadPath_setsMetadataAccordingly() = runBlocking {
        val tmp = File.createTempFile("oc", ".txt").apply { writeText("hello") }
        val ocTransfer = OC_TRANSFER.copy(localPath = tmp.absolutePath)
        every { transferRepository.getTransferById(10L) } returns ocTransfer

        // Folder exists
        mockkConstructor(CheckPathExistenceRemoteOperation::class)
        every { anyConstructed<CheckPathExistenceRemoteOperation>().execute(ocClient) } returns
            RemoteOperationResult<Boolean>(RemoteOperationResult.ResultCode.OK).apply { setData(true) }
        // Force name collision resolution
        mockkObject(eu.opencloud.android.utils.RemoteFileUtils)
        every {
            eu.opencloud.android.utils.RemoteFileUtils.getAvailableRemotePath(
                openCloudClient = ocClient,
                remotePath = any(),
                spaceWebDavUrl = any(),
                isUserLogged = any(),
            )
        } returns "/remote/path/file (1).txt"

        // Tus uploader
        val tusClient = mockk<TusClient>()
        val uploader = mockk<TusUploader>(relaxed = true) {
            every { uploadChunk() } returns -1
            every { finish() } just Runs
            every { offset } returns 0
        }
        every { uploadManager.createTusClient(ocClient) } returns tusClient
        mockkConstructor(TusAndroidUpload::class)
        val metaSlot = slot<Map<String, String>>()
        every { anyConstructed<TusAndroidUpload>().size } returns 100
        every { anyConstructed<TusAndroidUpload>().setMetadata(capture(metaSlot)) } just Runs
        every { tusClient.resumeOrCreateUpload(any()) } returns uploader

        val worker = makeWorker(buildInput(localPath = ocTransfer.localPath))
        worker.doWork()

        // Expect filename and dir correspond to renamed path
        assertTrue(metaSlot.isCaptured)
        val metadata = metaSlot.captured
        // filename should be "file (1).txt" and dir ends with "/remote/path/"
        assertTrue(metadata["filename"] == "file (1).txt")
        assertTrue(metadata["dir"]?.endsWith("/remote/path/") == true)
    }

    @Test
    fun unauthorized_mapsToUnauthorizedException_andShowsCredentialsNotification() = runBlocking {
        val tmp = File.createTempFile("oc", ".txt").apply { writeText("hello") }
        val ocTransfer = OC_TRANSFER.copy(localPath = tmp.absolutePath)
        every { transferRepository.getTransferById(10L) } returns ocTransfer

        // Folder exists
        mockkConstructor(CheckPathExistenceRemoteOperation::class)
        every { anyConstructed<CheckPathExistenceRemoteOperation>().execute(ocClient) } returns
            RemoteOperationResult<Boolean>(RemoteOperationResult.ResultCode.OK).apply { setData(true) }

        // Tus flow throws ProtocolException with 401
        val tusClient = mockk<TusClient>()
        val protoEx = mockk<ProtocolException>()
        val conn = mockk<TusHttpConnection>(relaxed = true)
        every { protoEx.causingConnection } returns conn
        every { conn.responseCode } returns 401

        every { uploadManager.createTusClient(ocClient) } returns tusClient
        mockkConstructor(TusAndroidUpload::class)
        every { anyConstructed<TusAndroidUpload>().size } returns 100
        every { anyConstructed<TusAndroidUpload>().setMetadata(any()) } just Runs
        every { tusClient.resumeOrCreateUpload(any()) } throws protoEx

        // Notification intents
        every { NotificationUtils.composePendingIntentToRefreshCredentials(any(), any()) } returns mockk<PendingIntent>()
        every { NotificationUtils.composePendingIntentToUploadList(any()) } returns mockk()
        every { NotificationUtils.createBasicNotification(any(), any(), any(), any(), any(), any(), any()) } just Runs

        val worker = makeWorker(buildInput(localPath = ocTransfer.localPath))
        worker.doWork()

        // Verify credentials refresh notification path was chosen
        verify { NotificationUtils.composePendingIntentToRefreshCredentials(context, account) }
        verify {
            NotificationUtils.createBasicNotification(
                context = context,
                contentTitle = "Credentials error",
                contentText = any(),
                notificationChannelId = any(),
                notificationId = any(),
                intent = any(),
                onGoing = false,
                timeOut = null
            )
        }
        // Repository updated as failed
        verify { transferRepository.updateTransferWhenFinished(10L, TransferStatus.TRANSFER_FAILED, any(), any()) }
    }

    @Test
    fun cancellation_stopsUploader_andFails() = runBlocking {
        val tmp = File.createTempFile("oc", ".txt").apply { writeText("hello") }
        val ocTransfer = OC_TRANSFER.copy(localPath = tmp.absolutePath)
        every { transferRepository.getTransferById(10L) } returns ocTransfer

        // Folder exists
        mockkConstructor(CheckPathExistenceRemoteOperation::class)
        every { anyConstructed<CheckPathExistenceRemoteOperation>().execute(ocClient) } returns
            RemoteOperationResult<Boolean>(RemoteOperationResult.ResultCode.OK).apply { setData(true) }

        // Tus uploader
        val tusClient = mockk<TusClient>()
        val uploader = mockk<TusUploader>(relaxed = true) {
            every { uploadChunk() } returns -1
            every { finish() } just Runs
            every { offset } returns 0
        }
        every { uploadManager.createTusClient(ocClient) } returns tusClient
        mockkConstructor(TusAndroidUpload::class)
        every { anyConstructed<TusAndroidUpload>().size } returns 100
        every { anyConstructed<TusAndroidUpload>().setMetadata(any()) } just Runs
        every { tusClient.resumeOrCreateUpload(any()) } returns uploader

        val worker = spyk(makeWorker(buildInput(localPath = ocTransfer.localPath)))
        every { worker.isStopped } returns true

        val result = worker.doWork()

        verify { uploader.finish() }
        assertTrue(result.toString().contains("FAILURE", ignoreCase = true))
        // Should not mark as finished in repository if cancelled inside loop
        verify(exactly = 0) { transferRepository.updateTransferWhenFinished(any(), any(), any(), any()) }
    }

    @Test
    fun success_updatesRepository() = runBlocking {
        val tmp = File.createTempFile("oc", ".txt").apply { writeText("hello") }
        val ocTransfer = OC_TRANSFER.copy(localPath = tmp.absolutePath)
        every { transferRepository.getTransferById(10L) } returns ocTransfer

        // Folder exists
        mockkConstructor(CheckPathExistenceRemoteOperation::class)
        every { anyConstructed<CheckPathExistenceRemoteOperation>().execute(ocClient) } returns
            RemoteOperationResult<Boolean>(RemoteOperationResult.ResultCode.OK).apply { setData(true) }

        // Tus uploader completes
        val tusClient = mockk<TusClient>()
        val uploader = mockk<TusUploader>(relaxed = true) {
            every { uploadChunk() } returnsMany listOf(50, -1)
            every { finish() } just Runs
            every { offset } returnsMany listOf(50, 100)
        }
        every { uploadManager.createTusClient(ocClient) } returns tusClient
        mockkConstructor(TusAndroidUpload::class)
        every { anyConstructed<TusAndroidUpload>().size } returns 100
        every { anyConstructed<TusAndroidUpload>().setMetadata(any()) } just Runs
        every { tusClient.resumeOrCreateUpload(any()) } returns uploader

        val worker = makeWorker(buildInput(localPath = ocTransfer.localPath))
        val result = worker.doWork()

        verify { transferRepository.updateTransferStatusToInProgressById(10L) }
        verify { transferRepository.updateTransferWhenFinished(10L, TransferStatus.TRANSFER_SUCCEEDED, any(), any()) }
        assertTrue(result.toString().contains("SUCCESS", ignoreCase = true))
    }
}
