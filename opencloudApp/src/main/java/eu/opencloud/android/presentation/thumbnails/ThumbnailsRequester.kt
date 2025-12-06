/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

package eu.opencloud.android.presentation.thumbnails

import android.accounts.Account
import android.accounts.AccountManager
import android.net.Uri
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger
import eu.opencloud.android.MainApp.Companion.appContext
import eu.opencloud.android.data.ClientManager
import java.util.concurrent.ConcurrentHashMap
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.model.OCFileWithSyncInfo
import eu.opencloud.android.domain.spaces.model.SpaceSpecial
import eu.opencloud.android.lib.common.SingleSessionManager
import eu.opencloud.android.lib.common.http.HttpConstants.ACCEPT_ENCODING_HEADER
import eu.opencloud.android.lib.common.http.HttpConstants.ACCEPT_ENCODING_IDENTITY
import eu.opencloud.android.lib.common.http.HttpConstants.AUTHORIZATION_HEADER
import eu.opencloud.android.lib.common.http.HttpConstants.OC_X_REQUEST_ID
import eu.opencloud.android.lib.common.http.HttpConstants.USER_AGENT_HEADER
import eu.opencloud.android.lib.common.utils.RandomUtils
import eu.opencloud.android.presentation.authentication.AccountUtils
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.Response
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.Locale

object ThumbnailsRequester : KoinComponent {
    private val clientManager: ClientManager by inject()

    private const val SPACE_SPECIAL_PREVIEW_URI = "%s?scalingup=0&a=1&x=%d&y=%d&c=%s&preview=1"
    private const val FILE_PREVIEW_URI = "%s/remote.php/webdav%s?x=%d&y=%d&c=%s&preview=1"

    private const val DISK_CACHE_SIZE: Long = 1024 * 1024 * 100 // 100MB

    private val imageLoaders = ConcurrentHashMap<String, ImageLoader>()
    private var sharedDiskCache: DiskCache? = null
    private var sharedMemoryCache: MemoryCache? = null

    private fun getSharedDiskCache(): DiskCache {
        if (sharedDiskCache == null) {
            sharedDiskCache = DiskCache.Builder()
                .directory(appContext.cacheDir.resolve("thumbnails_coil_cache"))
                .maxSizeBytes(DISK_CACHE_SIZE)
                .build()
        }
        return sharedDiskCache!!
    }

    private fun getSharedMemoryCache(): MemoryCache {
        if (sharedMemoryCache == null) {
            sharedMemoryCache = MemoryCache.Builder(appContext)
                .maxSizePercent(0.25)
                .build()
        }
        return sharedMemoryCache!!
    }

    fun getAvatarUri(account: Account): String {
        val accountManager = AccountManager.get(appContext)
        val baseUrl = accountManager.getUserData(account, eu.opencloud.android.lib.common.accounts.AccountUtils.Constants.KEY_OC_BASE_URL)
        val username = AccountUtils.getUsernameOfAccount(account.name)
        return "$baseUrl/index.php/avatar/${android.net.Uri.encode(username)}/384"
    }

    fun getPreviewUriForFile(file: OCFile, account: Account, etag: String? = null): String =
        getPreviewUri(file.remotePath, etag ?: file.etag, account)

    fun getPreviewUriForFile(fileWithSyncInfo: OCFileWithSyncInfo, account: Account): String =
        getPreviewUriForFile(fileWithSyncInfo.file, account)

    fun getPreviewUriForSpaceSpecial(spaceSpecial: SpaceSpecial): String =
        String.format(Locale.US, SPACE_SPECIAL_PREVIEW_URI, spaceSpecial.webDavUrl, 1024, 1024, spaceSpecial.eTag)

    private fun getPreviewUri(remotePath: String?, etag: String?, account: Account): String {
        val accountManager = AccountManager.get(appContext)
        val baseUrl = accountManager.getUserData(account, eu.opencloud.android.lib.common.accounts.AccountUtils.Constants.KEY_OC_BASE_URL)
        val path = if (remotePath?.startsWith("/") == true) remotePath else "/$remotePath"
        val encodedPath = Uri.encode(path, "/")

        return String.format(Locale.US, FILE_PREVIEW_URI, baseUrl, encodedPath, 1024, 1024, etag)
    }

    fun getCoilImageLoader(): ImageLoader {
        val account = AccountUtils.getCurrentOpenCloudAccount(appContext)
        return getCoilImageLoader(account)
    }

    fun getCoilImageLoader(account: Account): ImageLoader {
        val accountName = account.name
        return imageLoaders.getOrPut(accountName) {
            val openCloudClient = clientManager.getClientForCoilThumbnails(accountName)

            val coilRequestHeaderInterceptor = CoilRequestHeaderInterceptor(
                clientManager = clientManager,
                accountName = accountName
            )

            ImageLoader(appContext).newBuilder().okHttpClient(
                okHttpClient = openCloudClient.okHttpClient.newBuilder()
                    .addNetworkInterceptor(coilRequestHeaderInterceptor).build()
            ).logger(DebugLogger())
                .memoryCache {
                    getSharedMemoryCache()
                }
                .diskCache {
                    getSharedDiskCache()
                }
                .build()
        }
    }

    private class CoilRequestHeaderInterceptor(
        private val clientManager: ClientManager,
        private val accountName: String
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val openCloudClient = clientManager.getClientForCoilThumbnails(accountName)
            val requestHeaders = hashMapOf(
                AUTHORIZATION_HEADER to openCloudClient.credentials.headerAuth,
                ACCEPT_ENCODING_HEADER to ACCEPT_ENCODING_IDENTITY,
                USER_AGENT_HEADER to SingleSessionManager.getUserAgent(),
                OC_X_REQUEST_ID to RandomUtils.generateRandomUUID(),
            )

            val request = chain.request().newBuilder()
            requestHeaders.toHeaders().forEach { request.addHeader(it.first, it.second) }
            val response = chain.proceed(request.build())
            var builder = response.newBuilder()
            var changed = false

            val cacheControl = response.header("Cache-Control")
            if (cacheControl.isNullOrEmpty() || cacheControl.contains("no-cache")) {
                builder.removeHeader("Cache-Control")
                builder.addHeader("Cache-Control", "max-age=5000, must-revalidate")
                changed = true
            }

            if (chain.request().url.toString().contains("/avatar/") && response.header("Content-Type").isNullOrEmpty()) {
                builder.addHeader("Content-Type", "image/png")
                changed = true
            }

            if (changed) {
                return builder.build().also { Timber.d("Header :" + it.headers) }
            }
            return response
        }
    }
}
