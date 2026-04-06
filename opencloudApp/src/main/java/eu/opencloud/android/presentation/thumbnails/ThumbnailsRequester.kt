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
import eu.opencloud.android.data.providers.SharedPreferencesProvider
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
import okhttp3.Cache
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.Response
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.util.Locale

object ThumbnailsRequester : KoinComponent {
    private val clientManager: ClientManager by inject()
    private val preferencesProvider: SharedPreferencesProvider by inject()

    // https://docs.opencloud.eu/docs/next/dev/server/services/thumbnails/information/#thumbnail-query-string-parameters
    private const val SPACE_SPECIAL_PREVIEW_URI = "%s?scalingup=0&a=1&x=%d&y=%d&c=%s&preview=1"
    private const val FILE_PREVIEW_URI = "%s/webdav%s?x=%d&y=%d&c=%s&preview=1"

    private const val THUMBNAIL_DISK_CACHE_SIZE: Long = 1024 * 1024 * 100 // 100MB
    private const val AVATAR_HTTP_CACHE_SIZE: Long = 10L * 1024 * 1024 // 10MB

    private val thumbnailImageLoaders = ConcurrentHashMap<String, ImageLoader>()
    private val avatarImageLoaders = ConcurrentHashMap<String, ImageLoader>()

    private val sharedDiskCache: DiskCache by lazy {
        DiskCache.Builder()
            .directory(appContext.cacheDir.resolve("thumbnails_coil_cache"))
            .maxSizeBytes(THUMBNAIL_DISK_CACHE_SIZE)
            .build()
    }

    private val sharedMemoryCache: MemoryCache by lazy {
        MemoryCache.Builder(appContext)
            .maxSizePercent(0.25)
            .build()
    }

    // OkHttp's built-in HTTP cache for avatar responses. Unlike Coil's disk cache,
    // OkHttp's cache natively serves stale responses when the network is unavailable
    // (when must-revalidate is not set), giving us offline fallback for avatars.
    private val avatarHttpCache: Cache by lazy {
        Cache(appContext.cacheDir.resolve("avatar_http_cache"), AVATAR_HTTP_CACHE_SIZE)
    }

    fun getAvatarUri(account: Account): String {
        val accountManager = AccountManager.get(appContext)
        val baseUrl =
            accountManager.getUserData(account, eu.opencloud.android.lib.common.accounts.AccountUtils.Constants.KEY_OC_BASE_URL)
                ?.trimEnd('/')
                .orEmpty()
        // ?u= disambiguates the Coil cache key per account; without it two accounts
        // on the same server share the same URL and collide in the shared disk/memory cache.
        return "$baseUrl/graph/v1.0/me/photo/\$value?u=${account.name.hashCode().toString(16)}"
    }

    fun getPreviewUriForFile(file: OCFile, account: Account, etag: String? = null, width: Int = 1024, height: Int = 1024): String =
        getPreviewUri(file.remotePath, etag ?: file.remoteEtag, account, width, height)

    fun getPreviewUriForFile(fileWithSyncInfo: OCFileWithSyncInfo, account: Account, width: Int = 1024, height: Int = 1024): String =
        getPreviewUriForFile(fileWithSyncInfo.file, account, null, width, height)

    fun getPreviewUriForSpaceSpecial(spaceSpecial: SpaceSpecial): String =
        String.format(Locale.US, SPACE_SPECIAL_PREVIEW_URI, spaceSpecial.webDavUrl, 1024, 1024, spaceSpecial.eTag)

    private fun getPreviewUri(remotePath: String?, etag: String?, account: Account, width: Int, height: Int): String {
        val accountManager = AccountManager.get(appContext)
        val baseUrl = accountManager.getUserData(account, eu.opencloud.android.lib.common.accounts.AccountUtils.Constants.KEY_OC_BASE_URL)
            ?.trimEnd('/')
            .orEmpty()
        val path = if (remotePath?.startsWith("/") == true) remotePath else "/$remotePath"
        val encodedPath = Uri.encode(path, "/")

        return String.format(Locale.US, FILE_PREVIEW_URI, baseUrl, encodedPath, width, height, etag.orEmpty())
    }

    fun getContentAddressedImageLoader(): ImageLoader {
        val account = AccountUtils.getCurrentOpenCloudAccount(appContext)
        return getContentAddressedImageLoader(account)
    }

    /**
     * Thumbnail URLs are content-addressed: the file etag is baked into the URL, so the
     * URL changes when the file changes. The disk cache entry is therefore always valid
     * and can be served offline without server revalidation.
     *
     * Uses Coil's disk cache with respectCacheHeaders(false).
     */
    fun getContentAddressedImageLoader(account: Account): ImageLoader =
        thumbnailImageLoaders.getOrPut(account.name) {
            buildThumbnailImageLoader(account)
        }

    /**
     * Avatar URLs are NOT content-addressed: the URL (/graph/v1.0/me/photo/$value) is
     * fixed regardless of whether the user changes their profile picture. We need server
     * revalidation so the app picks up avatar changes, but we also need offline fallback.
     *
     * Coil's disk cache cannot serve stale entries on network error, so we use OkHttp's
     * built-in HTTP cache instead (which does). Coil still handles decoding, memory cache,
     * and transformations (CircleCrop etc.).
     */
    fun getRevalidatingImageLoader(account: Account): ImageLoader =
        avatarImageLoaders.getOrPut(account.name) {
            buildAvatarImageLoader(account)
        }

    private fun buildThumbnailImageLoader(account: Account): ImageLoader {
        val interceptor = CoilRequestHeaderInterceptor(clientManager, account.name)
        return ImageLoader(appContext).newBuilder()
            .okHttpClient {
                // Lazy: deferred to first image request (off main thread).
                // getClientForCoilThumbnails calls blockingGetAuthToken which
                // must not run on the main thread.
                clientManager.getClientForCoilThumbnails(account.name)
                    .okHttpClient.newBuilder()
                    .addInterceptor(interceptor).build()
            }
            .apply { if (preferencesProvider.getBoolean("enable_logging", false)) logger(DebugLogger()) }
            .memoryCache { sharedMemoryCache }
            .diskCache { sharedDiskCache }
            .respectCacheHeaders(false)
            .build()
    }

    private fun buildAvatarImageLoader(account: Account): ImageLoader {
        val interceptor = CoilRequestHeaderInterceptor(clientManager, account.name)
        return ImageLoader(appContext).newBuilder()
            .okHttpClient {
                clientManager.getClientForCoilThumbnails(account.name)
                    .okHttpClient.newBuilder()
                    .addInterceptor(interceptor)
                    .cache(avatarHttpCache)
                    .build()
            }
            .apply { if (preferencesProvider.getBoolean("enable_logging", false)) logger(DebugLogger()) }
            .memoryCache { sharedMemoryCache }
            // No Coil disk cache — OkHttp's HTTP cache handles persistence
            // and offline fallback instead.
            .diskCache(null)
            .respectCacheHeaders(true)
            .build()
    }

    private class CoilRequestHeaderInterceptor(
        private val clientManager: ClientManager,
        private val accountName: String
    ) : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val openCloudClient = try {
                clientManager.getClientForCoilThumbnails(accountName)
            } catch (e: Exception) {
                Timber.d(e, "Account $accountName not found, skipping thumbnail request")
                return Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(401)
                    .message("Account not found")
                    .body(okhttp3.ResponseBody.create(null, ""))
                    .build()
            }
            val credentials = openCloudClient.credentials
                ?: return Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(401)
                    .message("No credentials available")
                    .body(okhttp3.ResponseBody.create(null, ""))
                    .build()
            val requestHeaders = hashMapOf(
                AUTHORIZATION_HEADER to credentials.headerAuth,
                ACCEPT_ENCODING_HEADER to ACCEPT_ENCODING_IDENTITY,
                USER_AGENT_HEADER to SingleSessionManager.getUserAgent(),
                OC_X_REQUEST_ID to RandomUtils.generateRandomUUID(),
            )

            val requestBuilder = chain.request().newBuilder()
            requestHeaders.toHeaders().forEach { requestBuilder.addHeader(it.first, it.second) }
            val requestWithHeaders = requestBuilder.build()

            var response = chain.proceed(requestWithHeaders)

            var builder = response.newBuilder()
            var changed = false

            // The server sends no-cache (or no Cache-Control) for avatar responses.
            // Rewrite to max-age=300 so OkHttp's HTTP cache caches them.
            // Deliberately omitting must-revalidate: without it OkHttp serves stale
            // responses when the network is unavailable, giving us offline fallback.
            // For thumbnails this rewrite is ignored (respectCacheHeaders=false).
            val cacheControl = response.header("Cache-Control")
            if (cacheControl.isNullOrEmpty() || cacheControl.contains("no-cache")) {
                builder.removeHeader("Cache-Control")
                builder.addHeader("Cache-Control", "max-age=300")
                changed = true
            }

            val finalRequestUrl = response.request.url.toString()
            if (
                (finalRequestUrl.contains("/avatar/") || finalRequestUrl.contains("/photo/\$value")) &&
                response.header("Content-Type").isNullOrEmpty()
            ) {
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
