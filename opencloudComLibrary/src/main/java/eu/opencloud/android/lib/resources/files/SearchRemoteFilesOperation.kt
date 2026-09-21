/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2026 ownCloud GmbH.
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *   EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 *   BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 *   ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 */
package eu.opencloud.android.lib.resources.files

import at.bitfire.dav4jvm.PropertyRegistry
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.accounts.AccountUtils
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.HttpConstants.HTTP_MULTI_STATUS
import eu.opencloud.android.lib.common.http.HttpConstants.HTTP_OK
import eu.opencloud.android.lib.common.http.methods.webdav.SearchMethod
import eu.opencloud.android.lib.common.http.methods.webdav.properties.OCChecksums
import eu.opencloud.android.lib.common.http.methods.webdav.properties.OCFileId
import eu.opencloud.android.lib.common.http.methods.webdav.properties.OCShareTypes
import eu.opencloud.android.lib.common.http.methods.webdav.properties.OCSpaceId
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import timber.log.Timber
import java.net.URL

class SearchRemoteFilesOperation(
    val searchQuery: String,
    val spaceId: String? = null,
    val limit: Int = SearchMethod.DEFAULT_SEARCH_LIMIT,
) : RemoteOperation<ArrayList<RemoteFile>>() {

    override fun run(client: OpenCloudClient): RemoteOperationResult<ArrayList<RemoteFile>> {
        try {
            PropertyRegistry.register(OCShareTypes.Factory())
            PropertyRegistry.register(OCChecksums.Factory())
            PropertyRegistry.register(OCFileId.Factory())
            PropertyRegistry.register(OCSpaceId.Factory())

            val targetUrl = getTargetUrl(client)
            Timber.d("REPORT search '$searchQuery' -> url=$targetUrl, spaceId=$spaceId")
            var searchMethod = SearchMethod(
                url = targetUrl,
                searchQuery = searchQuery,
                limit = limit,
            )

            var status = client.executeHttpMethod(searchMethod)

            // If /remote.php/dav/spaces returned 404 and no specific space was requested,
            // fall back to the user files dav endpoint for legacy servers
            if (status == HttpConstants.HTTP_NOT_FOUND && spaceId == null) {
                val rawFallback = client.userFilesWebDavUri.toString()
                val fallbackUrl = URL(rawFallback.replace("//remote.php", "/remote.php"))
                Timber.d("Search on $targetUrl returned 404, falling back to $fallbackUrl")
                searchMethod = SearchMethod(
                    url = fallbackUrl,
                    searchQuery = searchQuery,
                    limit = limit,
                )
                status = client.executeHttpMethod(searchMethod)
            }

            return if (isSuccess(status)) {
                val remoteFiles = ArrayList<RemoteFile>()
                val userId = try {
                    if (mAccount != null && mContext != null) {
                        AccountUtils.getUserId(mAccount, mContext) ?: mAccount.name
                    } else {
                        mAccount?.name ?: ""
                    }
                } catch (e: Exception) {
                    Timber.d(e, "Could not get user id for account %s", mAccount?.name)
                    mAccount?.name ?: ""
                }
                val userName = mAccount?.name ?: ""

                searchMethod.members.forEach { resource ->
                    val remoteFile = RemoteFile.getRemoteFileFromDav(
                        davResource = resource,
                        userId = userId,
                        userName = userName,
                    )
                    if (remoteFile.spaceId == null && spaceId != null) {
                        remoteFile.spaceId = spaceId
                    }
                    remoteFiles.add(remoteFile)
                }

                RemoteOperationResult<ArrayList<RemoteFile>>(ResultCode.OK).apply {
                    data = remoteFiles
                    Timber.i("Search for '$searchQuery' completed with ${remoteFiles.size} files - HTTP status code: $status")
                }
            } else {
                RemoteOperationResult<ArrayList<RemoteFile>>(searchMethod).also {
                    Timber.w("Search for '$searchQuery' failed: ${it.logMessage}")
                }
            }
        } catch (e: Exception) {
            return RemoteOperationResult<ArrayList<RemoteFile>>(e).also {
                Timber.e(it.exception, "Search for '$searchQuery' encountered exception")
            }
        }
    }

    private fun getTargetUrl(client: OpenCloudClient): URL {
        val base = client.baseUri.toString().trimEnd('/')
        return if (spaceId != null) {
            URL("$base$SPACES_PATH/$spaceId")
        } else {
            URL("$base$SPACES_PATH")
        }
    }

    private fun isSuccess(status: Int): Boolean = status.isOneOf(HTTP_OK, HTTP_MULTI_STATUS)

    companion object {
        private const val SPACES_PATH = "/remote.php/dav/spaces"
    }
}
