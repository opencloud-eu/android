/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2023 ownCloud GmbH.
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
 *
 */
package eu.opencloud.android.lib.resources.files

import android.net.Uri
import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.webdav.MoveMethod
import eu.opencloud.android.lib.common.network.WebdavUtils
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Remote operation moving a remote file or folder in the openCloud server to a different folder
 * in the same account and space.
 *
 * Allows renaming the moving file/folder at the same time.
 *
 * @author David A. Velasco
 * @author David González Verdugo
 * @author Abel García de Prada
 * @author Juan Carlos Garrote Gascón
 * @author Manuel Plazas Palacio
 *
 * @param sourceRemotePath  Remote path of the file/folder to copy.
 * @param targetRemotePath  Remote path desired for the file/folder to copy it.
 */
open class MoveRemoteFileOperation(
    private val sourceRemotePath: String,
    private val targetRemotePath: String,
    private val spaceWebDavUrl: String? = null,
    private val forceOverride: Boolean = false,
) : RemoteOperation<Unit>() {

    /**
     * Performs the rename operation.
     *
     * @param client Client object to communicate with the remote openCloud server.
     */
    override fun run(client: OpenCloudClient): RemoteOperationResult<Unit> =
        if (targetRemotePath == sourceRemotePath) {
            // nothing to do!
            RemoteOperationResult(ResultCode.OK)
        } else if (targetRemotePath.startsWith(sourceRemotePath)) {
            RemoteOperationResult(ResultCode.INVALID_MOVE_INTO_DESCENDANT)
        } else {
            /// perform remote operation
            var result: RemoteOperationResult<Unit>
            try {
                // After finishing a chunked upload, we have to move the resulting file from uploads folder to files one,
                // so this uri has to be customizable
                val srcWebDavUri = getSrcWebDavUriForClient(client)
                val moveMethod = MoveMethod(
                    url = URL((spaceWebDavUrl ?: srcWebDavUri.toString()) + WebdavUtils.encodePath(sourceRemotePath)),
                    destinationUrl = (spaceWebDavUrl ?: client.userFilesWebDavUri.toString()) + WebdavUtils.encodePath(targetRemotePath),
                    forceOverride = forceOverride,
                ).apply {
                    addRequestHeaders(this)
                    setReadTimeout(MOVE_READ_TIMEOUT, TimeUnit.SECONDS)
                    setConnectionTimeout(MOVE_CONNECTION_TIMEOUT, TimeUnit.SECONDS)
                }

                val status = client.executeHttpMethod(moveMethod)

                when {
                    isSuccess(status) -> {
                        result = RemoteOperationResult<Unit>(ResultCode.OK)
                    }

                    isPreconditionFailed(status) -> {
                        result = RemoteOperationResult<Unit>(ResultCode.INVALID_OVERWRITE)
                        client.exhaustResponse(moveMethod.getResponseBodyAsStream())

                        /// for other errors that could be explicitly handled, check first:
                        /// http://www.webdav.org/specs/rfc4918.html#rfc.section.9.9.4
                    }

                    else -> {
                        result = RemoteOperationResult<Unit>(moveMethod)
                        client.exhaustResponse(moveMethod.getResponseBodyAsStream())
                    }
                }

                Timber.i("Move $sourceRemotePath to $targetRemotePath - HTTP status code: $status")
            } catch (e: Exception) {
                result = RemoteOperationResult<Unit>(e)
                Timber.e(e, "Move $sourceRemotePath to $targetRemotePath: ${result.logMessage}")

            }
            result
        }

    /**
     * For standard moves, we will use [OpenCloudClient.getUserFilesWebDavUri].
     * In case we need a different source Uri, override this method.
     */
    open fun getSrcWebDavUriForClient(client: OpenCloudClient): Uri = client.userFilesWebDavUri

    /**
     * For standard moves, we won't need any special headers.
     * In case new headers are needed, override this method
     */
    open fun addRequestHeaders(moveMethod: MoveMethod) {
        //Adding this because the library has an error with override
        if (moveMethod.forceOverride) {
            moveMethod.setRequestHeader(OVERWRITE, TRUE)
        }
    }

    private fun isSuccess(status: Int) = status.isOneOf(HttpConstants.HTTP_CREATED, HttpConstants.HTTP_NO_CONTENT)

    private fun isPreconditionFailed(status: Int) = status == HttpConstants.HTTP_PRECONDITION_FAILED

    companion object {
        private const val MOVE_READ_TIMEOUT = 10L
        private const val MOVE_CONNECTION_TIMEOUT = 6L
        private const val OVERWRITE = "overwrite"
        private const val TRUE = "T"
    }
}
