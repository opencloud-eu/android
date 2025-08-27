/**
 * openCloud Android client application
 *
 * @author Abel García de Prada
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
package eu.opencloud.android.data.user

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import eu.opencloud.android.data.OpencloudDatabase
import eu.opencloud.android.data.user.datasources.implementation.OCLocalUserDataSource.Companion.toEntity
import eu.opencloud.android.data.user.datasources.implementation.OCLocalUserDataSource.Companion.toModel
import eu.opencloud.android.data.user.db.UserDao
import eu.opencloud.android.testutil.OC_ACCOUNT_NAME
import eu.opencloud.android.testutil.OC_USER_QUOTA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@MediumTest
class UserDaoTest {

    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var userDao: UserDao
    private val userQuotaEntity = OC_USER_QUOTA.toEntity()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OpencloudDatabase.switchToInMemory(context)
        val db: OpencloudDatabase = OpencloudDatabase.getDatabase(context)
        userDao = db.userDao()
    }

    /******************************************************************************************************
     ******************************************** User Quota **********************************************
     ******************************************************************************************************/

    @Test
    fun insertQuotaForAccount() {
        userDao.insertOrReplace(userQuotaEntity)

        val userQuotaEntity = userDao.getQuotaForAccount(OC_ACCOUNT_NAME)

        assertNotNull(userQuotaEntity)
        assertEquals(OC_USER_QUOTA, userQuotaEntity?.toModel())
    }

    @Test
    fun replaceQuotaForAccount() {
        userDao.insertOrReplace(userQuotaEntity)
        userDao.insertOrReplace(userQuotaEntity.copy(available = -3))

        val userQuotaEntity = userDao.getQuotaForAccount(OC_ACCOUNT_NAME)

        assertNotNull(userQuotaEntity)
        assertEquals(OC_USER_QUOTA.copy(available = -3), userQuotaEntity?.toModel())
    }

    @Test
    fun getQuotaForAccountNull() {
        val userQuotaEntity = userDao.getQuotaForAccount(OC_ACCOUNT_NAME)

        assertNull(userQuotaEntity)
    }
}
