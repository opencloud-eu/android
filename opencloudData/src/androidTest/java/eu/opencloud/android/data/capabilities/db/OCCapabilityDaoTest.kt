/**
 * openCloud Android client application
 *
 * @author David González Verdugo
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

package eu.opencloud.android.data.capabilities.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import eu.opencloud.android.data.OpencloudDatabase
import eu.opencloud.android.data.capabilities.datasources.implementation.OCLocalCapabilitiesDataSource.Companion.toEntity
import eu.opencloud.android.domain.capabilities.model.CapabilityBooleanType
import eu.opencloud.android.testutil.OC_CAPABILITY
import eu.opencloud.android.testutil.livedata.getLastEmittedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@SmallTest
class OCCapabilityDaoTest {
    private lateinit var ocCapabilityDao: OCCapabilityDao
    private val user1 = "user1@server"
    private val user2 = "user2@server"

    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OpencloudDatabase.switchToInMemory(context)
        val db: OpencloudDatabase = OpencloudDatabase.getDatabase(context)
        ocCapabilityDao = db.capabilityDao()
    }

    @Test
    fun insertCapabilitiesListAndRead() {
        val entityList: List<OCCapabilityEntity> = listOf(
            OC_CAPABILITY.copy(accountName = user1).toEntity(),
            OC_CAPABILITY.copy(accountName = user2).toEntity()
        )

        ocCapabilityDao.insertOrReplace(entityList)

        val capability = ocCapabilityDao.getCapabilitiesForAccount(user2)
        val capabilityAsLiveData = ocCapabilityDao.getCapabilitiesForAccountAsLiveData(user2).getLastEmittedValue()

        assertNotNull(capability)
        assertNotNull(capabilityAsLiveData)
        assertEquals(entityList[1], capability)
        assertEquals(entityList[1], capabilityAsLiveData)
    }

    @Test
    fun insertCapabilitiesAndRead() {
        val entity1 = OC_CAPABILITY.copy(accountName = user1).toEntity()
        val entity2 = OC_CAPABILITY.copy(accountName = user2).toEntity()

        ocCapabilityDao.insertOrReplace(entity1)
        ocCapabilityDao.insertOrReplace(entity2)

        val capability = ocCapabilityDao.getCapabilitiesForAccount(user2)
        val capabilityAsLiveData = ocCapabilityDao.getCapabilitiesForAccountAsLiveData(user2).getLastEmittedValue()

        assertNotNull(capability)
        assertNotNull(capabilityAsLiveData)
        assertEquals(entity2, capability)
        assertEquals(entity2, capabilityAsLiveData)
    }

    @Test
    fun getNonExistingCapabilities() {
        ocCapabilityDao.insertOrReplace(OC_CAPABILITY.copy(accountName = user1).toEntity())

        val capability = ocCapabilityDao.getCapabilitiesForAccountAsLiveData(user2).getLastEmittedValue()

        assertNull(capability)
    }

    @Test
    fun replaceCapabilityIfAlreadyExists_exists() {
        val entity1 = OC_CAPABILITY.copy(filesVersioning = CapabilityBooleanType.FALSE).toEntity()
        val entity2 = OC_CAPABILITY.copy(filesVersioning = CapabilityBooleanType.TRUE).toEntity()

        ocCapabilityDao.insertOrReplace(entity1)
        ocCapabilityDao.replace(listOf(entity2))

        val capability = ocCapabilityDao.getCapabilitiesForAccountAsLiveData(OC_CAPABILITY.accountName!!).getLastEmittedValue()

        assertNotNull(capability)
        assertEquals(entity2, capability)
    }

    @Test
    fun replaceCapabilityIfAlreadyExists_doesNotExist() {
        val entity1 = OC_CAPABILITY.copy(accountName = user1).toEntity()
        val entity2 = OC_CAPABILITY.copy(accountName = user2).toEntity()

        ocCapabilityDao.insertOrReplace(entity1)

        ocCapabilityDao.replace(listOf(entity2))

        val capability1 = ocCapabilityDao.getCapabilitiesForAccountAsLiveData(user1).getLastEmittedValue()

        assertNotNull(capability1)
        assertEquals(entity1, capability1)

        // capability2 didn't exist before, it should not replace the old one but got created
        val capability2 = ocCapabilityDao.getCapabilitiesForAccountAsLiveData(user2).getLastEmittedValue()

        assertNotNull(capability2)
        assertEquals(entity2, capability2)
    }

    @Test
    fun deleteCapability() {
        val entity = OC_CAPABILITY.copy(accountName = user1).toEntity()

        ocCapabilityDao.insertOrReplace(entity)

        val capability1 = ocCapabilityDao.getCapabilitiesForAccountAsLiveData(user1).getLastEmittedValue()

        assertNotNull(capability1)

        ocCapabilityDao.deleteByAccountName(user1)

        val capability2 = ocCapabilityDao.getCapabilitiesForAccountAsLiveData(user1).getLastEmittedValue()

        assertNull(capability2)
    }
}
