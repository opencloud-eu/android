/*
 * openCloud Android client application
 *
 * @author Bartek Przybylski
 * @author David A. Velasco
 * @author David González Verdugo
 * Copyright (C) 2011  Bartek Przybylski
 * Copyright (C) 2020 ownCloud GmbH.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package eu.opencloud.android.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;

import androidx.core.content.ContextCompat;
import com.google.android.material.snackbar.Snackbar;
import eu.opencloud.android.MainApp;
import eu.opencloud.android.R;

import java.io.File;
import java.math.BigDecimal;
import java.net.IDN;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * A helper class for some string operations.
 */
public class DisplayUtils {

    private static final String OPENCLOUD_APP_NAME = "OpenCloud";

    private static final String[] sizeSuffixes = {"B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};
    private static final int[] sizeScales = {0, 0, 1, 1, 1, 2, 2, 2, 2};

    private static Map<String, String> mimeType2HumanReadable;

    static {
        mimeType2HumanReadable = new HashMap<>();
        // images
        mimeType2HumanReadable.put("image/jpeg", "JPEG image");
        mimeType2HumanReadable.put("image/jpg", "JPEG image");
        mimeType2HumanReadable.put("image/png", "PNG image");
        mimeType2HumanReadable.put("image/bmp", "Bitmap image");
        mimeType2HumanReadable.put("image/gif", "GIF image");
        mimeType2HumanReadable.put("image/svg+xml", "JPEG image");
        mimeType2HumanReadable.put("image/tiff", "TIFF image");
        // music
        mimeType2HumanReadable.put("audio/mpeg", "MP3 music file");
        mimeType2HumanReadable.put("application/ogg", "OGG music file");
    }

    /**
     * Converts the file size in bytes to human readable output.
     * <ul>
     * <li>appends a size suffix, e.g. B, KB, MB etc.</li>
     * <li>rounds the size based on the suffix to 0,1 or 2 decimals</li>
     * </ul>
     *
     * @param bytes Input file size
     * @return Like something readable like "12 MB"
     */
    public static String bytesToHumanReadable(long bytes, Context context, boolean includeMultipleDecimals) {
        if (bytes < 0) {
            return context.getString(R.string.common_pending);

        } else {
            double result = bytes;
            int attachedSuff = 0;
            while (result >= 1024 && attachedSuff < sizeSuffixes.length) {
                result /= 1024.;
                attachedSuff++;
            }

            BigDecimal readableResult = new BigDecimal(result).setScale(
                    sizeScales[attachedSuff],
                    BigDecimal.ROUND_HALF_UP
            ).stripTrailingZeros();

            if (!includeMultipleDecimals && readableResult.scale() > 0) { // Only for decimal numbers
                readableResult = readableResult.setScale(1, BigDecimal.ROUND_HALF_UP);
            }

            // Unscale only values with ten exponent
            return (readableResult.scale() < 0 ?
                    readableResult.setScale(0) :
                    readableResult
            ) + " " + sizeSuffixes[attachedSuff];
        }
    }

    /**
     * Converts MIME types like "image/jpg" to more end user friendly output
     * like "JPG image".
     *
     * @param mimetype MIME type to convert
     * @return A human friendly version of the MIME type
     */
    public static String convertMIMEtoPrettyPrint(String mimetype) {
        if (mimeType2HumanReadable.containsKey(mimetype)) {
            return mimeType2HumanReadable.get(mimetype);
        }
        if (mimetype.split("/").length >= 2) {
            return mimetype.split("/")[1].toUpperCase() + " file";
        }
        return "Unknown type";
    }

    /**
     * Converts Unix time to human readable format
     *
     * @param milliseconds that have passed since 01/01/1970
     * @return The human readable time for the users locale
     */
    public static String unixTimeToHumanReadable(long milliseconds) {
        Date date = new Date(milliseconds);
        DateFormat df = DateFormat.getDateTimeInstance();
        return df.format(date);
    }

    public static int getSeasonalIconId() {
        if (Calendar.getInstance().get(Calendar.DAY_OF_YEAR) >= 354 &&
                MainApp.Companion.getAppContext().getString(R.string.app_name).equals(OPENCLOUD_APP_NAME)) {
            //return R.drawable.winter_holidays_icon;
            return R.mipmap.icon;
        } else {
            return R.mipmap.icon;
        }
    }

    /**
     * Converts an internationalized domain name (IDN) in an URL to and from ASCII/Unicode.
     *
     * @param url     the URL where the domain name should be converted
     * @param toASCII if true converts from Unicode to ASCII, if false converts from ASCII to Unicode
     * @return the URL containing the converted domain name
     */
    public static String convertIdn(String url, boolean toASCII) {

        String urlNoDots = url;
        String dots = "";
        while (urlNoDots.startsWith(".")) {
            urlNoDots = url.substring(1);
            dots = dots + ".";
        }

        // Find host name after '//' or '@'
        int hostStart = 0;
        if (urlNoDots.contains("//")) {
            hostStart = url.indexOf("//") + "//".length();
        } else if (url.contains("@")) {
            hostStart = url.indexOf("@") + "@".length();
        }

        int hostEnd = url.substring(hostStart).indexOf("/");
        // Handle URL which doesn't have a path (path is implicitly '/')
        hostEnd = (hostEnd == -1 ? urlNoDots.length() : hostStart + hostEnd);

        String host = urlNoDots.substring(hostStart, hostEnd);
        host = (toASCII ? IDN.toASCII(host) : IDN.toUnicode(host));

        return dots + urlNoDots.substring(0, hostStart) + host + urlNoDots.substring(hostEnd);
    }

    /**
     * calculates the relative time string based on the given modification timestamp.
     *
     * @param context               the app's context
     * @param modificationTimestamp the UNIX timestamp of the file modification time.
     * @return a relative time string
     */
    public static CharSequence getRelativeTimestamp(Context context, long modificationTimestamp) {
        return getRelativeDateTimeString(context, modificationTimestamp, DateUtils.SECOND_IN_MILLIS,
                DateUtils.WEEK_IN_MILLIS, 0);
    }

    public static CharSequence getRelativeDateTimeString(
            Context c, long time, long minResolution, long transitionResolution, int flags
    ) {

        CharSequence dateString;

        // in Future
        if (time > System.currentTimeMillis()) {
            return DisplayUtils.unixTimeToHumanReadable(time);
        }
        // < 60 seconds -> seconds ago
        else if ((System.currentTimeMillis() - time) < 60 * 1000) {
            return c.getString(R.string.file_list_seconds_ago);
        } else {
            dateString = DateUtils.getRelativeDateTimeString(c, time, minResolution, transitionResolution, flags);
        }

        String[] parts = dateString.toString().split(",");
        if (parts.length == 2) {
            if (parts[1].contains(":") && !parts[0].contains(":")) {
                return parts[0];
            } else if (parts[0].contains(":") && !parts[1].contains(":")) {
                return parts[1];
            }
        }
        //dateString contains unexpected format. fallback: use relative date time string from android api as is.
        return dateString.toString();
    }

    /**
     * Update the passed path removing the last "/" if it is not the root folder
     *
     * @param path
     */
    public static String getPathWithoutLastSlash(String path) {

        // Remove last slash from path
        if (path.length() > 1 && path.charAt(path.length() - 1) == File.separator.charAt(0)) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * set the opencloud standard colors for the snackbar.
     *
     * @param context  the context relevant for setting the color according to the context's theme
     * @param snackbar the snackbar to be colored
     */
    public static void colorSnackbar(Context context, Snackbar snackbar) {
        // Changing action button text color
        snackbar.setActionTextColor(ContextCompat.getColor(context, R.color.white));
    }

    public static int getDrawerHeaderHeight(int displayCutout, Resources resources) {
        if (resources.getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            int displayCutoutDP =
                    displayCutout / (resources.getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT);
            return (int) resources.getDimension(R.dimen.nav_drawer_header_height) + displayCutoutDP;
        } else {
            return (int) resources.getDimension(R.dimen.nav_drawer_header_height);
        }
    }
}
