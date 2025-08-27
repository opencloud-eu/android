/* openCloud Android Library is available under MIT license
 *   Copyright (C) 2016 ownCloud GmbH.
 *   Copyright (C) 2012  Bartek Przybylski
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

package eu.opencloud.android.lib.resources.status

import android.os.Parcel
import android.os.Parcelable
import timber.log.Timber

class OpenCloudVersion(version: String, productversion: String) : Comparable<OpenCloudVersion>, Parcelable {

    // format is in version
    // 0xAABBCCDD
    // for version AA.BB.CC.DD
    // ie version 2.0.3 will be stored as 0x02000300
    private var mVersion: Int = 0
    var isVersionValid: Boolean = false
        set

    var isVersionHidden: Boolean = false

    var isProductversionValid: Boolean = false

    val version: String
        get() = if (isVersionValid) {
            toString()
        } else {
            INVALID_ZERO_VERSION
        }

    val isServerVersionSupported: Boolean
        get() = isProductversionValid
        //get() = mVersion >= MINIMUN_VERSION_SUPPORTED

    val isPublicSharingWriteOnlySupported: Boolean
        get() = isProductversionValid
        //get() = mVersion >= MINIMUM_VERSION_WITH_WRITE_ONLY_PUBLIC_SHARING

    init {
        Timber.d("From server OpenCloudVersion=" + version);
        var versionToParse = version
        mVersion = 0
        isVersionValid = false
        isVersionHidden = version.isBlank()
        // FIXME: Fix all the functions creating an OpenCloudVersion(...):
        isProductversionValid = !productversion.isBlank()
        val countDots = versionToParse.length - versionToParse.replace(".", "").length

        // Complete the version. Version must have 3 dots
        for (i in countDots until MAX_DOTS) {
            versionToParse = "$versionToParse.0"
        }

        parseVersion(versionToParse)

    }

    override fun toString(): String {
        // gets the first digit of version, shifting hexadecimal version to right 'til max position
        var versionToString = ((mVersion shr 8 * MAX_DOTS) % 256).toString()
        for (i in MAX_DOTS - 1 downTo 0) {
            // gets another digit of version, shifting hexadecimal version to right 8*i bits and...
            // ...discarding left part with mod 256
            versionToString = versionToString + "." + ((mVersion shr 8 * i) % 256).toString()
        }
        if (!isVersionValid) {
            versionToString += " INVALID"
        }
        return versionToString
    }

    override fun compareTo(other: OpenCloudVersion): Int =
        if (other.mVersion == mVersion)
            0
        else if (other.mVersion < mVersion) 1 else -1

    private fun parseVersion(version: String) {
        try {
            mVersion = getParsedVersion(version)
            isVersionValid = true

        } catch (e: Exception) {
            Timber.w(e, "Version is invalid")
            isVersionValid = false
            // if invalid, the instance will respond as if server is 8.1, minimum with capabilities API,
            // and "dead" : https://github.com/opencloud/core/wiki/Maintenance-and-Release-Schedule
        }
    }

    @Throws(NumberFormatException::class)
    private fun getParsedVersion(version: String): Int {
        var versionToParse = version
        var versionValue = 0

        // get only numeric part
        versionToParse = versionToParse.replace("[^\\d.]".toRegex(), "")

        val nums = versionToParse.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var i = 0
        while (i < nums.size && i <= MAX_DOTS) {
            versionValue += Integer.parseInt(nums[i])
            if (i < nums.size - 1) {
                versionValue = versionValue shl 8
            }
            i++
        }
        Timber.d("versionValue="+ versionValue + " "+"%x".format(versionValue));
        return versionValue
    }

    override fun describeContents(): Int =
        super.hashCode()

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(mVersion)
        dest.writeInt(if (isVersionValid) 1 else 0)
    }

    companion object {
        private const val INVALID_ZERO_VERSION = "0.0.0"

        private const val MAX_DOTS = 3
    }
}
