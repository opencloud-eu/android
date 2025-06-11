/**
 * openCloud Android client application
 *
 * @author Tobias Kaminsky
 * @author David A. Velasco
 * @author Christian Schabesberger
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

package eu.opencloud.android.datamodel;

import android.accounts.Account;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import eu.opencloud.android.MainApp;
import eu.opencloud.android.R;
import eu.opencloud.android.domain.files.model.OCFile;
import eu.opencloud.android.domain.files.usecases.DisableThumbnailsForFileUseCase;
import eu.opencloud.android.domain.files.usecases.GetWebDavUrlForSpaceUseCase;
import eu.opencloud.android.domain.spaces.model.SpaceSpecial;
import eu.opencloud.android.lib.common.OpenCloudAccount;
import eu.opencloud.android.lib.common.OpenCloudClient;
import eu.opencloud.android.lib.common.SingleSessionManager;
import eu.opencloud.android.lib.common.accounts.AccountUtils;
import eu.opencloud.android.lib.common.http.HttpConstants;
import eu.opencloud.android.lib.common.http.methods.nonwebdav.GetMethod;
import eu.opencloud.android.ui.adapter.DiskLruImageCache;
import eu.opencloud.android.utils.BitmapUtils;
import kotlin.Lazy;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.Locale;

import static org.koin.java.KoinJavaComponent.inject;

/**
 * Manager for concurrent access to thumbnails cache.
 */
public class ThumbnailsCacheManager {

    private static final String CACHE_FOLDER = "thumbnailCache";

    private static final Object mThumbnailsDiskCacheLock = new Object();
    private static DiskLruImageCache mThumbnailCache = null;
    private static boolean mThumbnailCacheStarting = true;

    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    private static final CompressFormat mCompressFormat = CompressFormat.JPEG;
    private static final int mCompressQuality = 70;
    private static OpenCloudClient mClient = null;

    private static final String PREVIEW_URI = "%s%s?x=%d&y=%d&c=%s&preview=1";
    private static final String SPACE_SPECIAL_URI = "%s?scalingup=0&a=1&x=%d&y=%d&c=%s&preview=1";

    public static Bitmap mDefaultImg =
            BitmapFactory.decodeResource(
                    MainApp.Companion.getAppContext().getResources(),
                    R.drawable.file_image
            );

    public static class InitDiskCacheTask extends AsyncTask<File, Void, Void> {

        @Override
        protected Void doInBackground(File... params) {
            synchronized (mThumbnailsDiskCacheLock) {
                mThumbnailCacheStarting = true;

                if (mThumbnailCache == null) {
                    try {
                        // Check if media is mounted or storage is built-in, if so,
                        // try and use external cache dir; otherwise use internal cache dir
                        final String cachePath =
                                MainApp.Companion.getAppContext().getExternalCacheDir().getPath() +
                                        File.separator + CACHE_FOLDER;
                        Timber.d("create dir: %s", cachePath);
                        final File diskCacheDir = new File(cachePath);
                        mThumbnailCache = new DiskLruImageCache(
                                diskCacheDir,
                                DISK_CACHE_SIZE,
                                mCompressFormat,
                                mCompressQuality
                        );
                    } catch (Exception e) {
                        Timber.e(e, "Thumbnail cache could not be opened ");
                        mThumbnailCache = null;
                    }
                }
                mThumbnailCacheStarting = false; // Finished initialization
                mThumbnailsDiskCacheLock.notifyAll(); // Wake any waiting threads
            }
            return null;
        }
    }

    public static void addBitmapToCache(String key, Bitmap bitmap) {
        synchronized (mThumbnailsDiskCacheLock) {
            if (mThumbnailCache != null) {
                mThumbnailCache.put(key, bitmap);
            }
        }
    }

    public static void removeBitmapFromCache(String key) {
        synchronized (mThumbnailsDiskCacheLock) {
            if (mThumbnailCache != null) {
                mThumbnailCache.removeKey(key);
            }
        }
    }

    public static Bitmap getBitmapFromDiskCache(String key) {
        synchronized (mThumbnailsDiskCacheLock) {
            // Wait while disk cache is started from background thread
            while (mThumbnailCacheStarting) {
                try {
                    mThumbnailsDiskCacheLock.wait();
                } catch (InterruptedException e) {
                    Timber.e(e, "Wait in mThumbnailsDiskCacheLock was interrupted");
                }
            }
            if (mThumbnailCache != null) {
                return mThumbnailCache.getBitmap(key);
            }
        }
        return null;
    }

    public static class ThumbnailGenerationTask extends AsyncTask<Object, Void, Bitmap> {
        private final WeakReference<ImageView> mImageViewReference;
        private static Account mAccount;
        private Object mFile;
        private FileDataStorageManager mStorageManager;

        public ThumbnailGenerationTask(ImageView imageView, Account account) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            mImageViewReference = new WeakReference<>(imageView);
            mAccount = account;
        }

        public ThumbnailGenerationTask(ImageView imageView) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            mImageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Object... params) {
            Bitmap thumbnail = null;

            try {
                if (mAccount != null) {
                    OpenCloudAccount ocAccount = new OpenCloudAccount(
                            mAccount,
                            MainApp.Companion.getAppContext()
                    );
                    mClient = SingleSessionManager.getDefaultSingleton().
                            getClientFor(ocAccount, MainApp.Companion.getAppContext());
                }

                mFile = params[0];

                if (mFile instanceof OCFile) {
                    thumbnail = doOCFileInBackground();
                } else if (mFile instanceof File) {
                    thumbnail = doFileInBackground();
                } else if (mFile instanceof SpaceSpecial) {
                    thumbnail = doSpaceImageInBackground();
                    //} else {  do nothing
                }

            } catch (Throwable t) {
                // the app should never break due to a problem with thumbnails
                Timber.e(t, "Generation of thumbnail for " + mFile + " failed");
                if (t instanceof OutOfMemoryError) {
                    System.gc();
                }
            }

            return thumbnail;
        }

        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                final ImageView imageView = mImageViewReference.get();
                final ThumbnailGenerationTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
                if (this == bitmapWorkerTask) {
                    String tagId = "";
                    if (mFile instanceof OCFile) {
                        tagId = String.valueOf(((OCFile) mFile).getId());
                    } else if (mFile instanceof File) {
                        tagId = String.valueOf(mFile.hashCode());
                    } else if (mFile instanceof SpaceSpecial) {
                        tagId = ((SpaceSpecial) mFile).getId();
                    }
                    if (String.valueOf(imageView.getTag()).equals(tagId)) {
                        imageView.setImageBitmap(bitmap);
                    }
                }
            }
        }

        /**
         * Add thumbnail to cache
         *
         * @param imageKey: thumb key
         * @param bitmap:   image for extracting thumbnail
         * @param path:     image path
         * @param px:       thumbnail dp
         * @return Bitmap
         */
        private Bitmap addThumbnailToCache(String imageKey, Bitmap bitmap, String path, int px) {

            Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap, px, px);

            // Rotate image, obeying exif tag
            thumbnail = BitmapUtils.rotateImage(thumbnail, path);

            // Add thumbnail to cache
            addBitmapToCache(imageKey, thumbnail);

            return thumbnail;
        }

        /**
         * Converts size of file icon from dp to pixel
         *
         * @return int
         */
        private int getThumbnailDimension() {
            // Converts dp to pixel
            Resources r = MainApp.Companion.getAppContext().getResources();
            return Math.round(r.getDimension(R.dimen.file_icon_size_grid));
        }

        private String getPreviewUrl(OCFile ocFile, Account account) {
            String baseUrl = mClient.getBaseUri() + "/remote.php/dav/files/" + AccountUtils.getUserId(account, MainApp.Companion.getAppContext());

            if (ocFile.getSpaceId() != null) {
                Lazy<GetWebDavUrlForSpaceUseCase> getWebDavUrlForSpaceUseCaseLazy = inject(GetWebDavUrlForSpaceUseCase.class);
                baseUrl = getWebDavUrlForSpaceUseCaseLazy.getValue().invoke(
                        new GetWebDavUrlForSpaceUseCase.Params(ocFile.getOwner(), ocFile.getSpaceId())
                );

            }
            return String.format(Locale.ROOT,
                    PREVIEW_URI,
                    baseUrl,
                    Uri.encode(ocFile.getRemotePath(), "/"),
                    getThumbnailDimension(),
                    getThumbnailDimension(),
                    ocFile.getEtag());
        }

        private Bitmap doOCFileInBackground() {
            OCFile file = (OCFile) mFile;

            final String imageKey = String.valueOf(file.getRemoteId());

            // Check disk cache in background thread
            Bitmap thumbnail = getBitmapFromDiskCache(imageKey);

            // Not found in disk cache
            if (thumbnail == null || file.getNeedsToUpdateThumbnail()) {

                int px = getThumbnailDimension();

                // Download thumbnail from server
                if (mClient != null) {
                    GetMethod get;
                    try {
                        String uri = getPreviewUrl(file, mAccount);
                        Timber.d("URI: %s", uri);
                        get = new GetMethod(new URL(uri));
                        int status = mClient.executeHttpMethod(get);
                        if (status == HttpConstants.HTTP_OK) {
                            InputStream inputStream = get.getResponseBodyAsStream();
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            thumbnail = ThumbnailUtils.extractThumbnail(bitmap, px, px);

                            // Handle PNG
                            if (file.getMimeType().equalsIgnoreCase("image/png")) {
                                thumbnail = handlePNG(thumbnail, px);
                            }

                            // Add thumbnail to cache
                            if (thumbnail != null) {
                                addBitmapToCache(imageKey, thumbnail);
                            }
                        } else {
                            mClient.exhaustResponse(get.getResponseBodyAsStream());
                        }
                        if (status == HttpConstants.HTTP_OK || status == HttpConstants.HTTP_NOT_FOUND) {
                            @NotNull Lazy<DisableThumbnailsForFileUseCase> disableThumbnailsForFileUseCaseLazy = inject(DisableThumbnailsForFileUseCase.class);
                            disableThumbnailsForFileUseCaseLazy.getValue().invoke(new DisableThumbnailsForFileUseCase.Params(file.getId()));
                        }
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }

            return thumbnail;

        }

        private Bitmap handlePNG(Bitmap bitmap, int px) {
            Bitmap resultBitmap = Bitmap.createBitmap(px,
                    px,
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(resultBitmap);

            c.drawColor(ContextCompat.getColor(MainApp.Companion.getAppContext(), R.color.background_color));
            c.drawBitmap(bitmap, 0, 0, null);

            return resultBitmap;
        }

        private Bitmap doFileInBackground() {
            File file = (File) mFile;

            final String imageKey = String.valueOf(file.hashCode());

            // Check disk cache in background thread
            Bitmap thumbnail = getBitmapFromDiskCache(imageKey);

            // Not found in disk cache
            if (thumbnail == null) {

                int px = getThumbnailDimension();

                Bitmap bitmap = BitmapUtils.decodeSampledBitmapFromFile(
                        file.getAbsolutePath(), px, px);

                if (bitmap != null) {
                    thumbnail = addThumbnailToCache(imageKey, bitmap, file.getPath(), px);
                }
            }
            return thumbnail;
        }

        private String getSpaceSpecialUri(SpaceSpecial spaceSpecial) {
            // Converts dp to pixel
            Resources r = MainApp.Companion.getAppContext().getResources();
            Integer spacesThumbnailSize = Math.round(r.getDimension(R.dimen.spaces_thumbnail_height)) * 2;
            return String.format(Locale.ROOT,
                    SPACE_SPECIAL_URI,
                    spaceSpecial.getWebDavUrl(),
                    spacesThumbnailSize,
                    spacesThumbnailSize,
                    spaceSpecial.getETag());
        }

        private Bitmap doSpaceImageInBackground() {
            SpaceSpecial spaceSpecial = (SpaceSpecial) mFile;

            final String imageKey = spaceSpecial.getId();

            // Check disk cache in background thread
            Bitmap thumbnail = getBitmapFromDiskCache(imageKey);

            // Not found in disk cache
            if (thumbnail == null) {
                int px = getThumbnailDimension();

                // Download thumbnail from server
                if (mClient != null) {
                    GetMethod get;
                    try {
                        String uri = getSpaceSpecialUri(spaceSpecial);
                        Timber.d("URI: %s", uri);
                        get = new GetMethod(new URL(uri));
                        int status = mClient.executeHttpMethod(get);
                        if (status == HttpConstants.HTTP_OK) {
                            InputStream inputStream = get.getResponseBodyAsStream();
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            thumbnail = ThumbnailUtils.extractThumbnail(bitmap, px, px);

                            // Handle PNG
                            if (spaceSpecial.getFile().getMimeType().equalsIgnoreCase("image/png")) {
                                thumbnail = handlePNG(thumbnail, px);
                            }

                            // Add thumbnail to cache
                            if (thumbnail != null) {
                                addBitmapToCache(imageKey, thumbnail);
                            }
                        } else {
                            mClient.exhaustResponse(get.getResponseBodyAsStream());
                        }
                    } catch (Exception e) {
                        Timber.e(e);
                    }
                }
            }

            return thumbnail;

        }
    }

    public static boolean cancelPotentialThumbnailWork(Object file, ImageView imageView) {
        final ThumbnailGenerationTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Object bitmapData = bitmapWorkerTask.mFile;
            // If bitmapData is not yet set or it differs from the new data
            if (bitmapData == null || bitmapData != file) {
                // Cancel previous task
                bitmapWorkerTask.cancel(true);
                Timber.v("Cancelled generation of thumbnail for a reused imageView");
            } else {
                // The same work is already in progress
                return false;
            }
        }
        // No task associated with the ImageView, or an existing task was cancelled
        return true;
    }

    private static ThumbnailGenerationTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncThumbnailDrawable) {
                final AsyncThumbnailDrawable asyncDrawable = (AsyncThumbnailDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    public static class AsyncThumbnailDrawable extends BitmapDrawable {
        private final WeakReference<ThumbnailGenerationTask> bitmapWorkerTaskReference;

        public AsyncThumbnailDrawable(
                Resources res, Bitmap bitmap, ThumbnailGenerationTask bitmapWorkerTask
        ) {

            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        ThumbnailGenerationTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }
}
