package eu.opencloud.android.lib.common.http

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import eu.opencloud.android.lib.common.network.CertificateCombinedException
import eu.opencloud.android.lib.common.network.NetworkUtils
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field
import javax.net.ssl.SSLPeerUnverifiedException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], manifest = Config.NONE)
class HttpClientTlsTest {

    private val context by lazy { ApplicationProvider.getApplicationContext<Context>() }
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        resetKnownServersStore()
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
        resetKnownServersStore()
    }

    @Test
    fun `accepts user-trusted certificate despite hostname mismatch`() {
        val wrongHostnameCertificate = HeldCertificate.Builder()
            .commonName(WRONG_HOSTNAME)
            .addSubjectAlternativeName(WRONG_HOSTNAME)
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(wrongHostnameCertificate)
            .build()

        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        server.start()

        NetworkUtils.addCertToKnownServersStore(wrongHostnameCertificate.certificate, context)

        val request = Request.Builder()
            .url(server.url("/"))
            .build()

        TestHttpClient(context).okHttpClient.newCall(request).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("ok", response.body?.string())
        }
    }

    @Test
    fun `rejects certificate with hostname mismatch when not in known servers`() {
        val wrongHostnameCertificate = HeldCertificate.Builder()
            .commonName(WRONG_HOSTNAME)
            .addSubjectAlternativeName(WRONG_HOSTNAME)
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(wrongHostnameCertificate)
            .build()

        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        server.start()

        val request = Request.Builder()
            .url(server.url("/"))
            .build()

        val thrown = assertThrows(Exception::class.java) {
            TestHttpClient(context).okHttpClient.newCall(request).execute().use { }
        }

        assertNotNull(thrown)
    }

    @Test
    fun `wraps hostname mismatch as certificate combined exception`() {
        val peerUnverifiedException = SSLPeerUnverifiedException("Hostname localhost not verified")

        val result = RemoteOperationResult<Unit>(peerUnverifiedException)

        assertEquals(
            RemoteOperationResult.ResultCode.SSL_RECOVERABLE_PEER_UNVERIFIED,
            result.code
        )
        assertTrue(result.exception is CertificateCombinedException)

        val combinedException = result.exception as CertificateCombinedException
        assertSame(peerUnverifiedException, combinedException.sslPeerUnverifiedException)
    }

    private fun resetKnownServersStore() {
        context.deleteFile(KNOWN_SERVERS_STORE_FILE)

        val field: Field = NetworkUtils::class.java.getDeclaredField("mKnownServersStore")
        field.isAccessible = true
        field.set(null, null)
    }

    private class TestHttpClient(context: Context) : HttpClient(context)

    companion object {
        private const val WRONG_HOSTNAME = "wrong.example.test"
        private const val KNOWN_SERVERS_STORE_FILE = "knownServers.bks"
    }
}
