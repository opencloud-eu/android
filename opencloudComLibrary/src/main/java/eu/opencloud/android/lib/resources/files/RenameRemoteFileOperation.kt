/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2021 ownCloud GmbH.
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

import eu.opencloud.android.lib.common.OpenCloudClient
import eu.opencloud.android.lib.common.http.HttpConstants
import eu.opencloud.android.lib.common.http.methods.webdav.MoveMethod
import eu.opencloud.android.lib.common.network.WebdavUtils
import eu.opencloud.android.lib.common.operations.RemoteOperation
import eu.opencloud.android.lib.common.operations.RemoteOperationResult
import eu.opencloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import eu.opencloud.android.lib.common.utils.isOneOf
import timber.log.Timber
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Remote operation performing the rename of a remote file or folder in the openCloud server.
 *
 * @author David A. Velasco
 * @author masensio
 */
class RenameRemoteFileOperation(
    private val oldName: String,
    private val oldRemotePath: String,
    private val newName: String,
    isFolder: Boolean,
    val spaceWebDavUrl: String? = null,
) : RemoteOperation<Unit>() {

    private var newRemotePath: String

    init {
        var parent = (File(oldRemotePath)).parent ?: throw IllegalArgumentException("Parent path is null")
        if (!parent.endsWith(File.separator)) {
            parent = parent.plus(File.separator)
        }
        newRemotePath = parent.plus(newName)
        if (isFolder) {
            newRemotePath.plus(File.separator)
        }
    }

    override fun run(client: OpenCloudClient): RemoteOperationResult<Unit> {
        var result: RemoteOperationResult<Unit>
        return try {
            if (newName == oldName) {
                RemoteOperationResult<Unit>(ResultCode.OK)
            } else if (targetPathIsUsed(client)) {
                RemoteOperationResult<Unit>(ResultCode.INVALID_OVERWRITE)
            } else {
                val moveMethod: MoveMethod = MoveMethod(
                    url = URL((spaceWebDavUrl ?: client.userFilesWebDavUri.toString()) + WebdavUtils.encodePath(oldRemotePath)),
                    destinationUrl = (spaceWebDavUrl ?: client.userFilesWebDavUri.toString()) + WebdavUtils.encodePath(newRemotePath),
                ).apply {
                    setReadTimeout(RENAME_READ_TIMEOUT, TimeUnit.MILLISECONDS)
                    setConnectionTimeout(RENAME_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                }
                val status = client.executeHttpMethod(moveMethod)

                result = if (isSuccess(status)) {
                    RemoteOperationResult<Unit>(ResultCode.OK)
                } else {
                    RemoteOperationResult<Unit>(moveMethod)
                }

                Timber.i("Rename $oldRemotePath to $newRemotePath - HTTP status code: $status")
                client.exhaustResponse(moveMethod.getResponseBodyAsStream())
                result
            }
        } catch (exception: Exception) {
            result = RemoteOperationResult<Unit>(exception)
            Timber.e(exception, "Rename $oldRemotePath to $newName: ${result.logMessage}")
            result
        }
    }

    /**
     * Checks if a file with the new name already exists.
     *
     * @return 'True' if the target path is already used by an existing file.
     */
    private fun targetPathIsUsed(client: OpenCloudClient): Boolean {
        val checkPathExistenceRemoteOperation = CheckPathExistenceRemoteOperation(newRemotePath, true)
        val exists = checkPathExistenceRemoteOperation.execute(client)
        return exists.isSuccess
    }

    private fun isSuccess(status: Int) = status.isOneOf(HttpConstants.HTTP_CREATED, HttpConstants.HTTP_NO_CONTENT)

    companion object {
        private const val RENAME_READ_TIMEOUT = 10_000L
        private const val RENAME_CONNECTION_TIMEOUT = 5_000L
    }
}
