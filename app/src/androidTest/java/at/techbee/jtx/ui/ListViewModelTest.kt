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
import at.techbee.jtx.database.*
import at.techbee.jtx.database.properties.Category
import at.techbee.jtx.getOrAwaitValue
import at.techbee.jtx.ui.list.ListViewModel
import at.techbee.jtx.ui.list.ListViewModelJournals
import at.techbee.jtx.ui.list.ListViewModelNotes
import at.techbee.jtx.ui.list.ListViewModelTodos
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class ListViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: ICalDatabaseDao
    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var listViewModel: ListViewModel

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        ICalDatabase.switchToInMemory(context)
        application = context.applicationContext as Application
        database = ICalDatabase.getInstance(context).iCalDatabaseDao
        database.insertCollectionSync(ICalCollection(collectionId = 1L, displayName = "testcollection automated tests", readonly = false, supportsVJOURNAL = true, supportsVTODO = true))
        database.insertCollectionSync(ICalCollection(collectionId = 2L, displayName = "testcollection readonly", readonly = true, supportsVJOURNAL = true, supportsVTODO = true))
    }

    @After
    fun closeDb()  {
        database.deleteAllICalObjects()
        ICalDatabase.getInstance(context).close()
    }


    @Test
    fun updateSearch_filter_Module_Journal() = runTest {

        listViewModel = ListViewModelJournals(application)
        listViewModel.iCal4List.observeForever {  }
        database.insertICalObject(ICalObject.createJournal())
        database.insertICalObject(ICalObject.createJournal())
        assertEquals(2, listViewModel.iCal4List.getOrAwaitValue(100).size)
    }

    @Test
    fun updateSearch_filter_Module_Note() = runTest {

        listViewModel = ListViewModelNotes(application)
        listViewModel.iCal4List.observeForever {  }
        database.insertICalObject(ICalObject.createNote("Note1"))
        assertEquals(1, listViewModel.iCal4List.value?.size)
    }


    @Test
    fun updateSearch_filter_Module_Todo() = runTest {

        listViewModel = ListViewModelTodos(application)
        listViewModel.iCal4List.observeForever {  }
        database.insertICalObject(ICalObject.createTask("Task1"))
        database.insertICalObject(ICalObject.createTask("Task2"))
        database.insertICalObject(ICalObject.createTask("Task3"))
        assertEquals(3, listViewModel.iCal4List.value?.size)
    }

    @Test
    fun updateSearch_filter_Text() = runTest {

        listViewModel = ListViewModelTodos(application)
        listViewModel.iCal4List.observeForever {  }
        database.insertICalObject(ICalObject.createTask("Task1_abc_Text"))
        database.insertICalObject(ICalObject.createTask("Task2_asdf_Text"))
        database.insertICalObject(ICalObject.createTask("Task3_abc"))
        listViewModel.listSettings.searchText.value = "%abc%"
        listViewModel.updateSearch()
        assertEquals(2, listViewModel.iCal4List.value?.size)
    }

    @Test
    fun updateSearch_filter_Categories() = runTest {

        listViewModel = ListViewModelTodos(application)
        listViewModel.iCal4List.observeForever {  }

        val id1 = database.insertICalObject(ICalObject.createTask("Task1"))
        database.insertCategory(Category(icalObjectId = id1, text = "Test1"))
        val id2 = database.insertICalObject(ICalObject.createTask("Task2"))
        database.insertCategory(Category(icalObjectId = id2, text = "Test1"))
        database.insertCategory(Category(icalObjectId = id2, text = "Whatever"))
        database.insertCategory(Category(icalObjectId = id2, text = "No matter"))
        val id3 = database.insertICalObject(ICalObject.createTask("Task3"))
        database.insertCategory(Category(icalObjectId = id3, text = "Whatever"))
        database.insertCategory(Category(icalObjectId = id3, text = "No matter"))
        database.insertICalObject(ICalObject.createTask("Task4"))  // val id4 = ...
        // no categories for id4

        listViewModel.listSettings.searchCategories.value = listViewModel.listSettings.searchCategories.value.plus("Test1")
        listViewModel.updateSearch()
        assertEquals(2, listViewModel.iCal4List.value?.size)

        listViewModel.listSettings.searchCategories.value = listViewModel.listSettings.searchCategories.value.plus("Whatever")
        listViewModel.updateSearch()
        assertEquals(3, listViewModel.iCal4List.value?.size)
    }



    @Test
    fun updateSearch_filter_Collections() = runTest {

        listViewModel = ListViewModelNotes(application)
        listViewModel.iCal4List.observeForever {  }

        val col1 = database.insertCollectionSync(ICalCollection(displayName = "ABC"))
        val col2 = database.insertCollectionSync(ICalCollection(displayName = "XYZ"))

        database.insertICalObject(ICalObject(collectionId = col1))
        database.insertICalObject(ICalObject(collectionId = col1))
        database.insertICalObject(ICalObject(collectionId = col2))

        listViewModel.listSettings.searchCollection.value = listViewModel.listSettings.searchCollection.value.plus("ABC")
        listViewModel.updateSearch()
        assertEquals(2, listViewModel.iCal4List.value?.size)

        listViewModel.listSettings.searchCollection.value = listViewModel.listSettings.searchCollection.value.plus("XYZ")
        listViewModel.updateSearch()
        assertEquals(3, listViewModel.iCal4List.value?.size)

        listViewModel.listSettings.searchCollection.value = listViewModel.listSettings.searchCollection.value.minus("ABC")
        listViewModel.updateSearch()
        assertEquals(1, listViewModel.iCal4List.value?.size)
    }

    @Test
    fun updateSearch_filter_StatusJournal() = runTest {

        listViewModel = ListViewModelNotes(application)
        listViewModel.iCal4List.observeForever {  }

        database.insertICalObject(ICalObject(summary="Note1", module = Module.NOTE.name, component = Component.VJOURNAL.name, status = StatusJournal.CANCELLED.name))
        database.insertICalObject(ICalObject(summary="Note2", module = Module.NOTE.name, component = Component.VJOURNAL.name, status = StatusJournal.DRAFT.name))
        database.insertICalObject(ICalObject(summary="Note3", module = Module.NOTE.name, component = Component.VJOURNAL.name, status = StatusJournal.FINAL.name))
        database.insertICalObject(ICalObject(summary="Note4", module = Module.NOTE.name, component = Component.VJOURNAL.name, status = StatusJournal.CANCELLED.name))

        listViewModel.listSettings.searchStatusJournal.value = listViewModel.listSettings.searchStatusJournal.value.plus(StatusJournal.DRAFT)
        listViewModel.updateSearch()
        assertEquals(1, listViewModel.iCal4List.value?.size)

        listViewModel.listSettings.searchStatusJournal.value = listViewModel.listSettings.searchStatusJournal.value.plus(StatusJournal.CANCELLED)
        listViewModel.updateSearch()
        assertEquals(3, listViewModel.iCal4List.value?.size)

        listViewModel.listSettings.searchStatusJournal.value = listViewModel.listSettings.searchStatusJournal.value.plus(StatusJournal.FINAL)
        listViewModel.updateSearch()
        assertEquals(4, listViewModel.iCal4List.value?.size)
    }

    @Test
    fun updateSearch_filter_StatusTodo() = runTest {

        listViewModel = ListViewModelTodos(application)
        listViewModel.iCal4List.observeForever {  }

        database.insertICalObject(ICalObject(summary="Task1", module = Module.TODO.name, component = Component.VTODO.name, status = StatusTodo.CANCELLED.name))
        database.insertICalObject(ICalObject(summary="Task4", module = Module.TODO.name, component = Component.VTODO.name,  status = StatusTodo.`NEEDS-ACTION`.name))
        database.insertICalObject(ICalObject(summary="Task2", module = Module.TODO.name, component = Component.VTODO.name,  status = StatusTodo.`IN-PROCESS`.name))
        database.insertICalObject(ICalObject(summary="Task3", module = Module.TODO.name, component = Component.VTODO.name,  status = StatusTodo.`IN-PROCESS`.name))
        database.insertICalObject(ICalObject(summary="Task4", module = Module.TODO.name, component = Component.VTODO.name,  status = StatusTodo.COMPLETED.name))

        listViewModel.listSettings.searchStatusTodo.value = listViewModel.listSettings.searchStatusTodo.value.plus(StatusTodo.`NEEDS-ACTION`)
        listViewModel.updateSearch()
        assertEquals(1, listViewModel.iCal4List.value?.size)

        listViewModel.listSettings.searchStatusTodo.value = listViewModel.listSettings.searchStatusTodo.value.plus(StatusTodo.`IN-PROCESS`)
        listViewModel.updateSearch()
        assertEquals(3, listViewModel.iCal4List.value?.size)

        listViewModel.listSettings.searchStatusTodo.value = listViewModel.listSettings.searchStatusTodo.value.plus(StatusTodo.COMPLETED)
        listViewModel.updateSearch()
        assertEquals(4, listViewModel.iCal4List.value?.size)
    }

    @Test
    fun updateSearch_filter_Classification() = runTest {

        listViewModel = ListViewModelTodos(application)
        listViewModel.iCal4List.observeForever {  }

        database.insertICalObject(ICalObject(summary="Task1", module = Module.TODO.name, component = Component.VTODO.name, classification = Classification.PUBLIC.name))
        database.insertICalObject(ICalObject(summary="Task4", module = Module.TODO.name, component = Component.VTODO.name,  classification = Classification.PUBLIC.name))
        database.insertICalObject(ICalObject(summary="Task2", module = Module.TODO.name, component = Component.VTODO.name, classification = Classification.PRIVATE.name))
        database.insertICalObject(ICalObject(summary="Task3", module = Module.TODO.name, component = Component.VTODO.name, classification = Classification.PRIVATE.name))
        database.insertICalObject(ICalObject(summary="Task4", module = Module.TODO.name, component = Component.VTODO.name, classification = Classification.CONFIDENTIAL.name))

        listViewModel.listSettings.searchClassification.value = listViewModel.listSettings.searchClassification.value.plus(Classification.PUBLIC)
        listViewModel.updateSearch()
        assertEquals(2, listViewModel.iCal4List.value?.size)

        listViewModel.listSettings.searchClassification.value = listViewModel.listSettings.searchClassification.value.plus(Classification.PRIVATE)
        listViewModel.updateSearch()
        assertEquals(4, listViewModel.iCal4List.value?.size)

        listViewModel.listSettings.searchClassification.value = listViewModel.listSettings.searchClassification.value.plus(Classification.CONFIDENTIAL)
        listViewModel.updateSearch()
        assertEquals(5, listViewModel.iCal4List.value?.size)
    }

    @Test
    fun clearFilter() {
        listViewModel = ListViewModelTodos(application)
        listViewModel.clearFilter()
        assertEquals(0, listViewModel.listSettings.searchCategories.value.size)
        assertEquals(0, listViewModel.listSettings.searchStatusJournal.value.size)
        assertEquals(0, listViewModel.listSettings.searchStatusTodo.value.size)
        assertEquals(0, listViewModel.listSettings.searchClassification.value.size)
        assertEquals(0, listViewModel.listSettings.searchCollection.value.size)
    }

    @Test
    fun updateProgress() = runTest {
        listViewModel = ListViewModelTodos(application)
        val id = database.insertICalObject(ICalObject.createTask("Test").apply { percent = 0 })

        withContext(Dispatchers.IO) {
            listViewModel.updateProgress(id, 50, false)
            Thread.sleep(100)
            val icalobject = database.getICalObjectById(id)
            assertEquals(50, icalobject?.percent)
        }
    }

    @Test
    fun updateProgress_withUnlink() = runTest {
        listViewModel = ListViewModelTodos(application)
        val item = ICalObject.createTask("Test").apply { percent = 22 }
        item.isRecurLinkedInstance = true

        val id = database.insertICalObject(item)

        withContext(Dispatchers.IO) {
            listViewModel.updateProgress(id, 50, false)
            Thread.sleep(100)
            val icalobject = database.getICalObjectById(id)

            assertEquals(50, icalobject?.percent)
            assertEquals(false, icalobject?.isRecurLinkedInstance)
        }

    }



    @Test
    fun deleteVisible() = runTest {

        listViewModel = ListViewModelTodos(application)
        listViewModel.iCal4List.observeForever {  }

        database.insertICalObject(ICalObject(summary="Task1", module = Module.TODO.name, component = Component.VTODO.name, classification = Classification.PUBLIC.name))
        database.insertICalObject(ICalObject(summary="Task4", module = Module.TODO.name, component = Component.VTODO.name,  classification = Classification.PUBLIC.name))
        database.insertICalObject(ICalObject(summary="Task2", module = Module.TODO.name, component = Component.VTODO.name, classification = Classification.PRIVATE.name))
        database.insertICalObject(ICalObject(summary="Task3", module = Module.TODO.name, component = Component.VTODO.name, classification = Classification.PRIVATE.name))
        database.insertICalObject(ICalObject(summary="Task4", module = Module.TODO.name, component = Component.VTODO.name, classification = Classification.CONFIDENTIAL.name))

        listViewModel.listSettings.searchClassification.value = listViewModel.listSettings.searchClassification.value.plus(Classification.PUBLIC)
        listViewModel.updateSearch()
        assertEquals(2, listViewModel.iCal4List.value?.size)

        withContext(Dispatchers.IO) {
            listViewModel.deleteVisible()
            Thread.sleep(100)
            listViewModel.listSettings.searchClassification.value = emptyList()
            listViewModel.updateSearch()
            assertEquals(3, listViewModel.iCal4List.value?.size)
        }

    }

    @Test
    fun getAllCollections() {
        listViewModel = ListViewModelTodos(application)
        listViewModel.allWriteableCollections.observeForever {  }
        assertEquals(1, listViewModel.allWriteableCollections.value?.size)
    }

    @Test
    fun getAllCategories() = runTest {
        listViewModel = ListViewModelTodos(application)
        listViewModel.allCategories.observeForever {  }

        val id1 = database.insertICalObject(ICalObject.createTask("Task1"))
        database.insertCategory(Category(icalObjectId = id1, text = "Test1"))
        val id2 = database.insertICalObject(ICalObject.createTask("Task2"))
        database.insertCategory(Category(icalObjectId = id2, text = "Test1"))
        database.insertCategory(Category(icalObjectId = id2, text = "Whatever"))
        database.insertCategory(Category(icalObjectId = id2, text = "No matter"))
        val id3 = database.insertICalObject(ICalObject.createTask("Task3"))
        database.insertCategory(Category(icalObjectId = id3, text = "Whatever"))
        database.insertCategory(Category(icalObjectId = id3, text = "No matter"))
        database.insertICalObject(ICalObject.createTask("Task4"))   // val id4 =

        // only 3 should be returned as the query selects only DISTINCT values!
        assertEquals(3, listViewModel.allCategories.value?.size)
    }
}