package eu.opencloud.android.providers

import android.content.Context
import eu.opencloud.android.lib.common.OpenCloudClient
import io.tus.android.client.TusPreferencesURLStore
import io.tus.java.client.TusClient
import java.net.URL

/**
 * UploadManager configures a TusClient instance for resumable uploads.
 * - Sets the upload creation URL using the authenticated user's uploads WebDAV URI.
 * - Injects authentication headers from the provided OpenCloudClient credentials.
 * - Enables resuming using a shared URL store backed by SharedPreferences.
 */
class UploadManager(private val context: Context) {

    fun createTusClient(openCloudClient: OpenCloudClient): TusClient {
        val client = TusClient()

        // Enable resuming via SharedPreferences store
        val tusPrefs = context.getSharedPreferences("tus_url_store", Context.MODE_PRIVATE)
        client.enableResuming(TusPreferencesURLStore(tusPrefs))

        // Set creation URL
        val creationUrl = URL(openCloudClient.uploadsWebDavUri.toString())
        client.setUploadCreationURL(creationUrl)

        // Inject Authorization header if available
        val headers = HashMap<String, String>()
        val authHeader = openCloudClient.credentials?.headerAuth
        if (!authHeader.isNullOrEmpty()) {
            headers["Authorization"] = authHeader
        }
        client.setHeaders(headers)

        return client
    }
}
