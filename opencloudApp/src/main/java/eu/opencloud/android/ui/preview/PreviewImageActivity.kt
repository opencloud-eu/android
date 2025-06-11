/**
 * openCloud Android client application
 *
 * @author David A. Velasco
 * @author David González Verdugo
 * @author Christian Schabesberger
 * @author Aitor Ballesteros Pavón
 * @author Juan Carlos Garrote Gascón
 * @author Jorge Aguado Recio
 *
 * Copyright (C) 2024 ownCloud GmbH.
 *
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 *
 * This program is distributed in the hd that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package eu.opencloud.android.ui.preview

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import androidx.work.WorkInfo
import eu.opencloud.android.R
import eu.opencloud.android.data.providers.SharedPreferencesProvider
import eu.opencloud.android.domain.exceptions.AccountNotFoundException
import eu.opencloud.android.domain.files.model.FileListOption
import eu.opencloud.android.domain.files.model.OCFile
import eu.opencloud.android.domain.files.model.OCFile.Companion.ROOT_PARENT_ID
import eu.opencloud.android.domain.files.usecases.SortFilesUseCase
import eu.opencloud.android.domain.utils.Event
import eu.opencloud.android.extensions.showErrorInSnackbar
import eu.opencloud.android.presentation.authentication.AccountUtils
import eu.opencloud.android.presentation.common.UIResult
import eu.opencloud.android.presentation.files.SortOrder
import eu.opencloud.android.presentation.files.SortType
import eu.opencloud.android.presentation.files.operations.FileOperation
import eu.opencloud.android.presentation.files.operations.FileOperationsViewModel
import eu.opencloud.android.presentation.spaces.SpacesListViewModel
import eu.opencloud.android.ui.activity.FileActivity
import eu.opencloud.android.ui.activity.FileDisplayActivity
import eu.opencloud.android.ui.fragment.FileFragment
import eu.opencloud.android.usecases.transfers.DOWNLOAD_ADDED_MESSAGE
import eu.opencloud.android.usecases.transfers.DOWNLOAD_FINISH_MESSAGE
import eu.opencloud.android.utils.PreferenceUtils
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

/**
 * Holds a swiping galley where image files contained in an openCloud directory are shown
 */
class PreviewImageActivity : FileActivity(),
    FileFragment.ContainerActivity,
    OnPageChangeListener {

    private val previewImageViewModel by viewModel <PreviewImageViewModel> {
        parametersOf(file)
    }
    private val fileOperationsViewModel: FileOperationsViewModel by viewModel()

    private lateinit var viewPager: ViewPager
    private lateinit var previewImagePagerAdapter: PreviewImagePagerAdapter
    private var savedPosition = 0
    private var hasSavedPosition = false
    private var localBroadcastManager: LocalBroadcastManager? = null
    private var fullScreenAnchorView: View? = null

    var mHideSystemUiHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            hideSystemUI(fullScreenAnchorView)
            showActionBar(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.preview_image_activity)

        // ActionBar
        supportActionBar?.run {
            setDisplayHomeAsUpEnabled(true)
            setHomeActionContentDescription(R.string.common_back)
            setHomeButtonEnabled(true)
        }
        showActionBar(false)

        /// FullScreen and Immersive Mode
        fullScreenAnchorView = window.decorView
        // to keep our UI controls visibility in line with system bars visibility
        fullScreenAnchorView?.setOnSystemUiVisibilityChangeListener { flags ->
            val visible = flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
            if (visible) {
                showActionBar(true)
                setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            } else {
                showActionBar(false)
                setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            }
        }
        window.statusBarColor = ContextCompat.getColor(this, R.color.opencloud_petrol_dark_transparent)
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
    }

    private fun startObservingFinishedDownloads() {
        previewImageViewModel.startListeningToDownloadsFromAccount(account = account)
        previewImageViewModel.downloads.observe(this) { pairFileWork ->
            if (pairFileWork.isEmpty()) return@observe

            pairFileWork.forEach { fileWork ->
                previewImagePagerAdapter.onDownloadEvent(
                    fileWork.first,
                    if (fileWork.second.state.isFinished) DOWNLOAD_FINISH_MESSAGE else DOWNLOAD_ADDED_MESSAGE,
                    fileWork.second.state == WorkInfo.State.SUCCEEDED
                )
            }
        }
    }

    private fun stopObservingWorkers() {
        previewImageViewModel.downloads.removeObservers(this)
    }

    private fun startObservingFileOperations() {
        fileOperationsViewModel.removeFileLiveData.observe(this, Event.EventObserver { uiResult ->
            when (uiResult) {
                is UIResult.Error -> {
                    dismissLoadingDialog()
                    showErrorInSnackbar(R.string.remove_fail_msg, uiResult.getThrowableOrNull())
                }

                is UIResult.Loading -> {
                    showLoadingDialog(R.string.wait_a_moment)
                }
                is UIResult.Success -> {

                    // Refresh the spaces and update the quota
                    val spacesListViewModel: SpacesListViewModel by viewModel { parametersOf(account.name, false) }
                    spacesListViewModel.refreshSpacesFromServer()

                    dismissLoadingDialog()
                    finish()
                }
            }
        })

        fileOperationsViewModel.syncFileLiveData.observe(this, Event.EventObserver { uiResult ->
            if (uiResult is UIResult.Error && uiResult.error is AccountNotFoundException) {
                showRequestAccountChangeNotice(getString(R.string.sync_fail_ticker_unauthorized), false)
            }
        })
    }

    private fun initViewPager() {
        // get parent from path
        val parentPath = file.remotePath.substring(
            0,
            file.remotePath.lastIndexOf(file.fileName)
        )
        var parentFolder = storageManager.getFileByPath(parentPath, file.spaceId)
        if (parentFolder == null) {
            // should not be necessary
            parentFolder = storageManager.getFileByPath(OCFile.ROOT_PATH, file.spaceId)
        }

        val sharedPreferencesProvider: SharedPreferencesProvider by inject()
        val sortType = sharedPreferencesProvider.getInt(SortType.PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_NAME.ordinal)
        val sortOrder = sharedPreferencesProvider.getInt(SortOrder.PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_ASCENDING.ordinal)
        val sortFilesUseCase: SortFilesUseCase by inject()
        val imageFiles = sortFilesUseCase(
            SortFilesUseCase.Params(
                listOfFiles = storageManager.getFolderImages(parentFolder),
                sortType = eu.opencloud.android.domain.files.usecases.SortType.fromPreferences(sortType),
                ascending = SortOrder.fromPreference(sortOrder) == SortOrder.SORT_ORDER_ASCENDING
            )
        )
        previewImagePagerAdapter = PreviewImagePagerAdapter(
            supportFragmentManager,
            account,
            imageFiles.toMutableList()
        )

        viewPager = findViewById(R.id.fragmentPager)
        viewPager.apply {
            filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)

            var position = if (hasSavedPosition) savedPosition else previewImagePagerAdapter.getFilePosition(file)
            position = if (position >= 0) position else 0
            adapter = previewImagePagerAdapter
            addOnPageChangeListener(this@PreviewImageActivity)
            currentItem = position
            if (position == 0) {
                // this is necessary because viewPager.setCurrentItem(0) does not trigger
                // a call to onPageSelected in the first layout request after viewPager.setAdapter(...) ;
                // see, for example:
                // https://android.googlesource.com/platform/frameworks/support.git/+/android-6.0
                // .1_r55/v4/java/android/support/v4/view/ViewPager.java#541
                // ; or just:
                // http://stackoverflow.com/questions/11794269/onpageselected-isnt-triggered-when-calling
                // -setcurrentitem0
                viewPager.post { onPageSelected(viewPager.currentItem) }
            }
        }
        startObservingFinishedDownloads()
        startObservingFileOperations()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available
        delayedHide()
    }

    private fun delayedHide(delayMillis: Int = INITIAL_HIDE_DELAY) {
        mHideSystemUiHandler.removeMessages(0)
        mHideSystemUiHandler.sendEmptyMessageDelayed(0, delayMillis.toLong())
    }

    /// handle Window Focus changes
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        // When the window loses focus (e.g. the action overflow is shown),
        // cancel any pending hide action.
        if (!hasFocus) {
            mHideSystemUiHandler.removeMessages(0)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (item.itemId == android.R.id.home) {
            if (isDrawerOpen()) {
                closeDrawer()
            } else {
                backToDisplayActivity()
            }
            true
        } else {
            super.onOptionsItemSelected(item)
        }

    override fun onResume() {
        super.onResume()
        startObservingFinishedDownloads()
    }

    public override fun onPause() {
        stopObservingWorkers()
        super.onPause()
    }

    private fun backToDisplayActivity() {
        finish()
    }

    override fun showDetails(file: OCFile) {
        val showDetailsIntent = Intent(this, FileDisplayActivity::class.java).apply {
            action = FileDisplayActivity.ACTION_DETAILS
            putExtra(EXTRA_FILE, file)
            putExtra(EXTRA_ACCOUNT, AccountUtils.getCurrentOpenCloudAccount(this@PreviewImageActivity))
        }
        finishAffinity()
        startActivity(showDetailsIntent)
    }

    /**
     * This method will be invoked when a new page becomes selected. Animation is not necessarily
     * complete.
     *
     * @param position Position index of the new selected page
     */
    override fun onPageSelected(position: Int) {
        Timber.d("onPageSelected %s", position)
        if (operationsServiceBinder != null) {
            savedPosition = position
            hasSavedPosition = true
            val currentFile = previewImagePagerAdapter.getFileAt(position)
            updateActionBarTitle(currentFile.fileName)
            if (!previewImagePagerAdapter.pendingErrorAt(position)) {
                fileOperationsViewModel.performOperation(FileOperation.SynchronizeFileOperation(currentFile, account.name))
            }

            // Call to reset image zoom to initial state
            (viewPager.adapter as PreviewImagePagerAdapter?)?.resetZoom()
        } else {
            // too soon! ; selection of page (first image) was faster than binding of FileOperationsService;
            // wait a bit!
            handler.post { onPageSelected(position) }
        }
    }

    /**
     * Called when the scroll state changes. Useful for discovering when the user begins dragging,
     * when the pager is automatically settling to the current page. when it is fully stopped/idle.
     *
     * @param state The new scroll state (SCROLL_STATE_IDLE, _DRAGGING, _SETTLING
     */
    override fun onPageScrollStateChanged(state: Int) {}

    /**
     * This method will be invoked when the current page is scrolled, either as part of a
     * programmatically initiated smooth scroll or a user initiated touch scroll.
     *
     * @param position             Position index of the first page currently being displayed.
     * Page position+1 will be visible if positionOffset is
     * nonzero.
     * @param positionOffset       Value from [0, 1) indicating the offset from the page
     * at position.
     * @param positionOffsetPixels Value in pixels indicating the offset from position.
     */
    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

    fun toggleFullScreen() {
        val safeFullScreenAnchorView = fullScreenAnchorView ?: return
        val visible = (safeFullScreenAnchorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0
        if (visible) {
            hideSystemUI(fullScreenAnchorView)
        } else {
            showSystemUI(fullScreenAnchorView)
        }
    }

    override fun onAccountSet(stateWasRecovered: Boolean) {
        super.onAccountSet(stateWasRecovered)
        account ?: return
        var file = file

        /// Validate handled file  (first image to preview)
        checkNotNull(file) { "Instanced with a NULL OCFile" }
        require(file.isImage) { "Non-image file passed as argument" }

        // Update file according to DB file, if it is possible
        if (file.id!! > ROOT_PARENT_ID) {
            file = storageManager.getFileById(file.id!!)
        }
        if (file != null) {
            /// Refresh the activity according to the Account and OCFile set
            setFile(file) // reset after getting it fresh from storageManager
            updateActionBarTitle(getFile().fileName)
            initViewPager()
        } else {
            // handled file not in the current Account
            finish()
        }
    }

    private fun hideSystemUI(anchorView: View?) {
        anchorView?.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hides NAVIGATION BAR; Android >= 4.0
                    or View.SYSTEM_UI_FLAG_FULLSCREEN // hides STATUS BAR;     Android >= 4.1
                    or View.SYSTEM_UI_FLAG_IMMERSIVE // stays interactive;    Android >= 4.4
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE // draw full window;     Android >= 4.1
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // draw full window;     Android >= 4.1
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    private fun showSystemUI(anchorView: View?) {
        anchorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE // draw full window;     Android >= 4.1
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // draw full window;     Android >= 4.1
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun navigateToOption(fileListOption: FileListOption) {
        backToDisplayActivity()
        super.navigateToOption(fileListOption)
    }

    private fun showActionBar(show: Boolean) {
        val actionBar = supportActionBar ?: return
        if (show) {
            actionBar.show()
        } else {
            actionBar.hide()
        }
    }

    private fun updateActionBarTitle(title: String) {
        supportActionBar?.title = title
    }

    // The main_menu won't be displayed
    override fun onCreateOptionsMenu(menu: Menu): Boolean =
        false

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean =
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            showSystemUI(fullScreenAnchorView)
            true
        } else {
            super.onKeyUp(keyCode, event)
        }

    companion object {
        private const val INITIAL_HIDE_DELAY = 0 // immediate hide
    }
}
