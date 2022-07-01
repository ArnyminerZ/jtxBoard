/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.ui

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import at.techbee.jtx.database.ICalCollection
import at.techbee.jtx.database.ICalDatabase
import at.techbee.jtx.database.ICalDatabaseDao
import at.techbee.jtx.database.ICalObject
import at.techbee.jtx.database.properties.Attachment
import at.techbee.jtx.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith


@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class IcalViewViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: ICalDatabaseDao
    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var icalViewViewModel: IcalViewViewModel



    @Before
    fun setup()  {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        ICalDatabase.switchToInMemory(context)
        database = ICalDatabase.getInstance(context).iCalDatabaseDao
        database.insertCollectionSync(ICalCollection(collectionId = 1L, displayName = "testcollection automated tests"))

    }

    @After
    fun closeDb() {
        ICalDatabase.getInMemoryDB(context).close()
    }

/*
    @Test
    fun testEditingClicked() = runBlockingTest {

        val preparedEntry = ICalObject.createJournal()
        preparedEntry.id = database.insertICalObject(preparedEntry)

        icalViewViewModel = IcalViewViewModel(application, preparedEntry.id)
        Thread.sleep(100)
        icalViewViewModel.icalEntity.getOrAwaitValue()

        icalViewViewModel.editingClicked()

        assertNotNull(icalViewViewModel.entryToEdit)
    }
 */

    @Test
    fun testInsertRelatedNote() = runBlockingTest {

        val preparedEntry = ICalObject.createJournal()
        preparedEntry.id = database.insertICalObject(preparedEntry)

        icalViewViewModel = IcalViewViewModel(application, preparedEntry.id)
        Thread.sleep(100)
        icalViewViewModel.icalEntity.getOrAwaitValue()
        icalViewViewModel.icalEntity.observeForever {}

        icalViewViewModel.insertRelated(ICalObject.createNote("RelatedNote"), null)
        Thread.sleep(100)

        val childEntry = database.get(icalViewViewModel.icalEntity.value?.relatedto?.get(0)?.linkedICalObjectId!!)
        childEntry.getOrAwaitValue()

        assertEquals(true, icalViewViewModel.icalEntity.value?.property?.dirty)
        assertEquals(1L,  icalViewViewModel.icalEntity.value?.property?.sequence)
        assertTrue(icalViewViewModel.icalEntity.value?.relatedto?.size!! > 0)
        assertNotNull(childEntry)
    }

    @Test
    fun testInsertRelatedAudioNote() = runBlockingTest {

        val preparedEntry = ICalObject.createJournal()
        preparedEntry.id = database.insertICalObject(preparedEntry)

        icalViewViewModel = IcalViewViewModel(application, preparedEntry.id)
        Thread.sleep(100)
        icalViewViewModel.icalEntity.getOrAwaitValue()
        icalViewViewModel.icalEntity.observeForever {}

        icalViewViewModel.insertRelated(ICalObject.createNote(), Attachment(uri = "https://10.0.0.138"))
        Thread.sleep(100)

        val childEntry = database.get(icalViewViewModel.icalEntity.value?.relatedto?.get(0)?.linkedICalObjectId!!)
        childEntry.getOrAwaitValue()

        assertEquals(true, icalViewViewModel.icalEntity.value?.property?.dirty)
        assertEquals(1L,  icalViewViewModel.icalEntity.value?.property?.sequence)
        assertTrue(icalViewViewModel.icalEntity.value?.relatedto?.size!! > 0)
        assertNotNull(childEntry)
        assertTrue(childEntry.value?.attachments?.size!! > 0)
    }

    @Test
    fun testInsertRelatedTask() = runBlockingTest {

        val preparedEntry = ICalObject.createJournal()
        preparedEntry.id = database.insertICalObject(preparedEntry)

        icalViewViewModel = IcalViewViewModel(application, preparedEntry.id)
        Thread.sleep(100)
        icalViewViewModel.icalEntity.getOrAwaitValue()
        icalViewViewModel.icalEntity.observeForever {}

        icalViewViewModel.insertRelated(ICalObject.createTask("RelatedTask"), null)
        Thread.sleep(100)

        val childEntry = database.get(icalViewViewModel.icalEntity.value?.relatedto?.get(0)?.linkedICalObjectId!!)
        childEntry.getOrAwaitValue()

        assertEquals(true, icalViewViewModel.icalEntity.value?.property?.dirty)
        assertEquals(1L,  icalViewViewModel.icalEntity.value?.property?.sequence)
        assertTrue(icalViewViewModel.icalEntity.value?.relatedto?.size!! > 0)
        assertNotNull(childEntry)
    }

    @Test
    fun testUpdateProgress() = runBlockingTest {

        val preparedEntry = ICalObject.createTask("myTestTask")
        preparedEntry.id = database.insertICalObject(preparedEntry)

        icalViewViewModel = IcalViewViewModel(application, preparedEntry.id)
        Thread.sleep(100)
        icalViewViewModel.icalEntity.getOrAwaitValue()

        icalViewViewModel.updateProgress(icalViewViewModel.icalEntity.value!!.property.id, 88)

        database.getICalObjectById(icalViewViewModel.icalEntity.value!!.property.id)
        assertEquals(88, icalViewViewModel.icalEntity.value!!.property.percent)
    }

    @Test
    fun testVisibilitySettingsForTask() = runBlockingTest {

        val preparedEntry = ICalObject.createTask("myTestTask")
        preparedEntry.id = database.insertICalObject(preparedEntry)


        icalViewViewModel = IcalViewViewModel(application, preparedEntry.id)
        Thread.sleep(100)
        icalViewViewModel.icalEntity.getOrAwaitValue()

        assertNotNull(icalViewViewModel.icalEntity.getOrAwaitValue())
        assertNull(icalViewViewModel.dtstartFormatted.getOrAwaitValue())
        assertNotNull(icalViewViewModel.createdFormatted.getOrAwaitValue())
        assertNotNull(icalViewViewModel.lastModifiedFormatted.getOrAwaitValue())
        //assertNull(icalViewViewModel.completedFormatted.getOrAwaitValue())
        //assertNull(icalViewViewModel.startedFormatted.getOrAwaitValue())

        assertEquals(false, icalViewViewModel.dateVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.timeVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.urlVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.attendeesVisible.getOrAwaitValue())
        //assertEquals(false, icalViewViewModel.organizerVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.contactVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.commentsVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.attachmentsVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.alarmsVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.relatedtoVisible.getOrAwaitValue())
        assertEquals(true, icalViewViewModel.progressVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.priorityVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.subtasksVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.completedVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.startedVisible.getOrAwaitValue())
        assertEquals(false, icalViewViewModel.resourcesVisible.getOrAwaitValue())
    }
}