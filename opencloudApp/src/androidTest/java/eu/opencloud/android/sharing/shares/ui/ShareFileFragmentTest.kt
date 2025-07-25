/**
 * openCloud Android client application
 *
 * @author David González Verdugo
 * Copyright (C) 2020 ownCloud GmbH.
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

package eu.opencloud.android.sharing.shares.ui

import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import eu.opencloud.android.R
import eu.opencloud.android.domain.capabilities.model.CapabilityBooleanType
import eu.opencloud.android.domain.capabilities.model.OCCapability
import eu.opencloud.android.domain.sharing.shares.model.OCShare
import eu.opencloud.android.domain.sharing.shares.model.ShareType
import eu.opencloud.android.domain.utils.Event
import eu.opencloud.android.presentation.common.UIResult
import eu.opencloud.android.presentation.sharing.ShareFileFragment
import eu.opencloud.android.presentation.capabilities.CapabilityViewModel
import eu.opencloud.android.presentation.sharing.ShareViewModel
import eu.opencloud.android.testutil.OC_ACCOUNT
import eu.opencloud.android.testutil.OC_CAPABILITY
import eu.opencloud.android.testutil.OC_FILE
import eu.opencloud.android.testutil.OC_SHARE
import eu.opencloud.android.utils.matchers.assertVisibility
import eu.opencloud.android.utils.matchers.isDisplayed
import eu.opencloud.android.utils.matchers.withText
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class ShareFileFragmentTest {
    private val capabilityViewModel = mockk<CapabilityViewModel>(relaxed = true)
    private val capabilitiesLiveData = MutableLiveData<Event<UIResult<OCCapability>>>()
    private val shareViewModel = mockk<ShareViewModel>(relaxed = true)
    private val sharesLiveData = MutableLiveData<Event<UIResult<List<OCShare>>>>()

    @Before
    fun setUp() {
        every { capabilityViewModel.capabilities } returns capabilitiesLiveData
        every { shareViewModel.shares } returns sharesLiveData

        stopKoin()

        startKoin {
            androidContext(ApplicationProvider.getApplicationContext())
            allowOverride(override = true)
            modules(
                module {
                    viewModel {
                        capabilityViewModel
                    }
                    viewModel {
                        shareViewModel
                    }
                }
            )
        }
    }

    @Test
    fun showHeader() {
        loadShareFileFragment()
        onView(withId(R.id.shareFileName)).check(matches(withText(OC_FILE.fileName)))
    }

    @Test
    fun fileSizeVisible() {
        loadShareFileFragment()
        R.id.shareFileSize.isDisplayed(displayed = true)
    }

    @Test
    fun showPrivateLink() {
        loadShareFileFragment()
        R.id.getPrivateLinkButton.isDisplayed(displayed = true)
    }

    @Test
    fun hidePrivateLink() {
        loadShareFileFragment(capabilities = OC_CAPABILITY.copy(filesPrivateLinks = CapabilityBooleanType.FALSE))
        R.id.getPrivateLinkButton.isDisplayed(displayed = false)
    }

    /******************************************************************************************************
     ******************************************* PRIVATE SHARES *******************************************
     ******************************************************************************************************/

    private var userSharesList = listOf(
        OC_SHARE.copy(sharedWithDisplayName = "Batman"),
        OC_SHARE.copy(sharedWithDisplayName = "Joker")
    )

    private var groupSharesList = listOf(
        OC_SHARE.copy(
            shareType = ShareType.GROUP,
            sharedWithDisplayName = "Suicide Squad"
        ),
        OC_SHARE.copy(
            shareType = ShareType.GROUP,
            sharedWithDisplayName = "Avengers"
        )
    )

    @Test
    fun showUsersAndGroupsSectionTitle() {
        loadShareFileFragment(shares = userSharesList)
        onView(withText(R.string.share_with_user_section_title)).check(matches(isDisplayed()))
    }

    @Test
    fun showNoPrivateShares() {
        loadShareFileFragment(shares = listOf())
        onView(withText(R.string.share_no_users)).check(matches(isDisplayed()))
    }

    @Test
    fun showUserShares() {
        loadShareFileFragment(shares = userSharesList)
        onView(withText("Batman")).check(matches(isDisplayed()))
        onView(withText("Batman")).check(matches(hasSibling(withId(R.id.unshareButton))))
            .check(matches(isDisplayed()))
        onView(withText("Batman")).check(matches(hasSibling(withId(R.id.editShareButton))))
            .check(matches(isDisplayed()))
        onView(withText("Joker")).check(matches(isDisplayed()))
    }

    @Test
    fun showGroupShares() {
        loadShareFileFragment(shares = listOf(groupSharesList.first()))
        onView(withText("Suicide Squad (group)")).check(matches(isDisplayed()))
        onView(withText("Suicide Squad (group)")).check(matches(hasSibling(withId(R.id.icon))))
            .check(matches(isDisplayed()))
        onView(withTagValue(CoreMatchers.equalTo(R.drawable.ic_group))).check(matches(isDisplayed()))
    }

    /******************************************************************************************************
     ******************************************* PUBLIC SHARES ********************************************
     ******************************************************************************************************/

    private var publicShareList = listOf(
        OC_SHARE.copy(
            shareType = ShareType.PUBLIC_LINK,
            path = "/Photos/image.jpg",
            isFolder = false,
            name = "Image link",
            shareLink = "http://server:port/s/1"
        ),
        OC_SHARE.copy(
            shareType = ShareType.PUBLIC_LINK,
            path = "/Photos/image.jpg",
            isFolder = false,
            name = "Image link 2",
            shareLink = "http://server:port/s/2"
        ),
        OC_SHARE.copy(
            shareType = ShareType.PUBLIC_LINK,
            path = "/Photos/image.jpg",
            isFolder = false,
            name = "Image link 3",
            shareLink = "http://server:port/s/3"
        )
    )

    @Test
    fun showNoPublicShares() {
        loadShareFileFragment(shares = listOf())
        onView(withText(R.string.share_no_public_links)).check(matches(isDisplayed()))
    }

    @Test
    fun showPublicShares() {
        loadShareFileFragment(shares = publicShareList)
        onView(withText("Image link")).check(matches(isDisplayed()))
        onView(withText("Image link")).check(matches(hasSibling(withId(R.id.getPublicLinkButton))))
            .check(matches(isDisplayed()))
        onView(withText("Image link")).check(matches(hasSibling(withId(R.id.deletePublicLinkButton))))
            .check(matches(isDisplayed()))
        onView(withText("Image link")).check(matches(hasSibling(withId(R.id.editPublicLinkButton))))
            .check(matches(isDisplayed()))
        onView(withText("Image link 2")).check(matches(isDisplayed()))
        onView(withText("Image link 3")).check(matches(isDisplayed()))
    }

    @Test
    fun showPublicSharesSharingEnabled() {
        loadShareFileFragment(
            capabilities = OC_CAPABILITY.copy(filesSharingPublicEnabled = CapabilityBooleanType.TRUE),
            shares = publicShareList
        )

        onView(withText("Image link")).check(matches(isDisplayed()))
        onView(withText("Image link 2")).check(matches(isDisplayed()))
        onView(withText("Image link 3")).check(matches(isDisplayed()))
    }

    @Test
    fun hidePublicSharesSharingDisabled() {
        loadShareFileFragment(
            capabilities = OC_CAPABILITY.copy(filesSharingPublicEnabled = CapabilityBooleanType.FALSE),
            shares = publicShareList
        )

        R.id.shareViaLinkSection.assertVisibility(ViewMatchers.Visibility.GONE)
    }

    @Test
    fun createPublicShareMultipleCapability() {
        loadShareFileFragment(
            capabilities = OC_CAPABILITY.copy(
                versionString = "10.1.1",
                filesSharingPublicMultiple = CapabilityBooleanType.TRUE
            ),
            shares = listOf(publicShareList[0])
        )

        R.id.addPublicLinkButton.assertVisibility(ViewMatchers.Visibility.VISIBLE)
    }

    @Test
    fun cannotCreatePublicShareMultipleCapability() {
        loadShareFileFragment(
            capabilities = OC_CAPABILITY.copy(
                versionString = "10.1.1",
                filesSharingPublicMultiple = CapabilityBooleanType.FALSE
            ),
            shares = listOf(publicShareList[0])
        )

        R.id.addPublicLinkButton.assertVisibility(ViewMatchers.Visibility.INVISIBLE)
    }

    @Test
    fun cannotCreatePublicShareServerCapability() {
        loadShareFileFragment(
            capabilities = OC_CAPABILITY.copy(
                versionString = "9.3.1"
            ),
            shares = listOf(publicShareList[0])
        )

        R.id.addPublicLinkButton.assertVisibility(ViewMatchers.Visibility.INVISIBLE)
    }

    /******************************************************************************************************
     *********************************************** COMMON ***********************************************
     ******************************************************************************************************/

    @Test
    fun hideSharesSharingApiDisabled() {
        loadShareFileFragment(
            capabilities = OC_CAPABILITY.copy(
                filesSharingApiEnabled = CapabilityBooleanType.FALSE
            )
        )
        R.id.shareWithUsersSection.assertVisibility(ViewMatchers.Visibility.GONE)

        R.id.shareViaLinkSection.assertVisibility(ViewMatchers.Visibility.GONE)
    }

    @Test
    fun showError() {
        loadShareFileFragment(
            sharesUIResult = UIResult.Error(
                error = Throwable("It was not possible to retrieve the shares from the server")
            )
        )
        com.google.android.material.R.id.snackbar_text.withText(R.string.get_shares_error)
    }

    private fun loadShareFileFragment(
        capabilities: OCCapability = OC_CAPABILITY,
        capabilitiesEvent: Event<UIResult<OCCapability>> = Event(UIResult.Success(capabilities)),
        shares: List<OCShare> = listOf(OC_SHARE),
        sharesUIResult: UIResult<List<OCShare>> = UIResult.Success(shares)
    ) {
        val shareFileFragment = ShareFileFragment.newInstance(
            OC_FILE,
            OC_ACCOUNT
        )

        ActivityScenario.launch(TestShareFileActivity::class.java).onActivity {
            it.startFragment(shareFileFragment)
        }

        capabilitiesLiveData.postValue(capabilitiesEvent)
        sharesLiveData.postValue(Event(sharesUIResult))
    }
}
