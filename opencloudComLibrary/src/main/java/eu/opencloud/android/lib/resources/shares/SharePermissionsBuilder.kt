/* openCloud Android Library is available under MIT license
 *   @author David A. Velasco
 *   Copyright (C) 2020 ownCloud GmbH.
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

package eu.opencloud.android.lib.resources.shares

/**
 * Provides method to define a set of share permissions and calculate the appropiate
 * int value representing it.
 */
class SharePermissionsBuilder {

    /** Set of permissions  */
    private var permissions = RemoteShare.READ_PERMISSION_FLAG    // READ is minimum permission

    /**
     * Sets or clears permission to reshare a file or folder.
     *
     * @param enabled 'True' to set, 'false' to clear.
     * @return Instance to builder itself, to allow consecutive calls to setters
     */
    fun setSharePermission(enabled: Boolean): SharePermissionsBuilder {
        updatePermission(RemoteShare.SHARE_PERMISSION_FLAG, enabled)
        return this
    }

    /**
     * Sets or clears permission to update a folder or folder.
     *
     * @param enabled 'True' to set, 'false' to clear.
     * @return Instance to builder itself, to allow consecutive calls to setters
     */
    fun setUpdatePermission(enabled: Boolean): SharePermissionsBuilder {
        updatePermission(RemoteShare.UPDATE_PERMISSION_FLAG, enabled)
        return this
    }

    /**
     * Sets or clears permission to create files in share folder.
     *
     * @param enabled 'True' to set, 'false' to clear.
     * @return Instance to builder itself, to allow consecutive calls to setters
     */
    fun setCreatePermission(enabled: Boolean): SharePermissionsBuilder {
        updatePermission(RemoteShare.CREATE_PERMISSION_FLAG, enabled)
        return this
    }

    /**
     * Sets or clears permission to delete files in a shared folder.
     *
     * @param enabled 'True' to set, 'false' to clear.
     * @return Instance to builder itself, to allow consecutive calls to setters
     */
    fun setDeletePermission(enabled: Boolean): SharePermissionsBuilder {
        updatePermission(RemoteShare.DELETE_PERMISSION_FLAG, enabled)
        return this
    }

    /**
     * Common code to update the value of the set of permissions.
     *
     * @param permissionsFlag Flag for the permission to update.
     * @param enable          'True' to set, 'false' to clear.
     */
    private fun updatePermission(permissionsFlag: Int, enable: Boolean) {
        if (enable) {
            // add permission
            permissions = permissions or permissionsFlag
        } else {
            // delete permission
            permissions = permissions and permissionsFlag.inv()
        }
    }

    /**
     * 'Builds' the int value for the accumulated set of permissions.
     *
     * @return An int value representing the accumulated set of permissions.
     */
    fun build(): Int = permissions
}
