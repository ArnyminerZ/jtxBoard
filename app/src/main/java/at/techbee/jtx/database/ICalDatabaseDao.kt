/*
 * Copyright (c) Patrick Lang in collaboration with bitfire web engineering.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.database

import android.database.Cursor
import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import at.techbee.jtx.database.properties.*
import at.techbee.jtx.database.relations.ICal4ListWithRelatedto
import at.techbee.jtx.database.relations.ICalEntity
import at.techbee.jtx.database.views.ICal4List
import at.techbee.jtx.database.views.ICal4ViewNote
import at.techbee.jtx.database.views.VIEW_NAME_ICAL4LIST
import at.techbee.jtx.database.views.VIEW_NAME_ICAL4VIEWNOTE


/**
 * Defines methods for using the SleepNight class with Room.
 */
@Dao
interface ICalDatabaseDao {

/*
SELECTs (global selects without parameter)
 */

    /**
     * Retrieve an list of all DISTINCT Category names ([Category.text]) as a LiveData-List
     *
     * @return a list of [Category.text] as LiveData<List<String>>
     */
    @Transaction
    @Query("SELECT DISTINCT text FROM category ORDER BY text ASC")
    fun getAllCategories(): LiveData<List<String>>

    /**
     * Retrieve an list of all Attachment Uris
     *
     * @return a list of [Attachment.uri] as List<String>
     */
    @Transaction
    @Query("SELECT $COLUMN_ATTACHMENT_URI FROM $TABLE_NAME_ATTACHMENT")
    suspend fun getAllAttachmentUris(): List<String>

    /**
     * Retrieve an Attachment with a specific [id]
     *
     * @return the [Attachment] with this [id]
     */
    @Transaction
    @Query("SELECT * FROM $TABLE_NAME_ATTACHMENT WHERE $COLUMN_ATTACHMENT_ID = :id")
    fun getAttachmentById(id: Long): Attachment?



    /**
     * Retrieve an list of all DISTINCT Organizer caladdresses ([Organizer.caladdress]) as a LiveData-List
     *
     * @return a list of [Organizer.caladdress] as LiveData<List<String>>
     */
    @Transaction
    @Query("SELECT DISTINCT caladdress FROM organizer ORDER BY caladdress ASC")
    fun getAllOrganizers(): LiveData<List<String>>

    /**
     * Retrieve an list of all Collections ([Collection]) as a LiveData-List
     *
     * @return a list of [Collection] as LiveData<List<String>>
     */
    @Transaction
    @Query("SELECT * FROM collection ORDER BY _id ASC")
    fun getAllCollections(): LiveData<List<ICalCollection>>

    /**
     * Retrieve an list of all DISTINCT Collections ([Collection])
     * that support VTODO as a LiveData-List
     *
     * @return a list of VTODO-[Collection] as LiveData<List<String>>
     */
    @Transaction
    @Query("SELECT * FROM collection WHERE supportsVTODO = 1 ORDER BY _id ASC")
    fun getAllVTODOCollections(): LiveData<List<ICalCollection>>

    /**
     * Retrieve an list of all DISTINCT Collections ([Collection])
     * that support VJOURNAL as a LiveData-List
     *
     * @return a list of VJOURNAL-[Collection] as LiveData<List<String>>
     */
    @Transaction
    @Query("SELECT * FROM collection WHERE supportsVJOURNAL = 1 ORDER BY _id ASC")
    fun getAllVJOURNALCollections(): LiveData<List<ICalCollection>>



    /**
     * Retrieve an list of all Relatedto ([Relatedto]) as a List
     *
     * @return a list of [Relatedto] as List<Relatedto>
     */
    @Transaction
    @Query("SELECT * FROM relatedto")
    fun getAllRelatedto(): LiveData<List<Relatedto>>


    /**
     * Retrieve an list of [ICalObject] that are child-elements of another [ICalObject]
     * by checking if the [ICalObject.id] is listed as a [Relatedto.linkedICalObjectId].
     *
     * @return a list of [ICalObject] as LiveData<List<[ICalObject]>>
     */
    @Transaction
    @Query("SELECT $VIEW_NAME_ICAL4LIST.* from $VIEW_NAME_ICAL4LIST INNER JOIN $TABLE_NAME_RELATEDTO ON $VIEW_NAME_ICAL4LIST.$COLUMN_ID = $TABLE_NAME_RELATEDTO.$COLUMN_RELATEDTO_LINKEDICALOBJECT_ID WHERE $VIEW_NAME_ICAL4LIST.$COLUMN_COMPONENT = 'VTODO'")
    fun getAllSubtasks(): LiveData<List<ICal4List?>>

    /**
     * Retrieve an list the number of Subtasks of an [ICalObject] as a [SubtaskCount].
     * This is especially used for Sub-Sub-Tasks to show the number of Sub-Sub-Tasks on a Sub-Task.
     *
     * @return a list of [ICalObject] as LiveData<List<[ICalObject]>>
     */
    @Transaction
    @Query("SELECT icalobject._id as icalobjectId, count(*) as count from relatedto INNER JOIN icalobject ON icalobject._id = relatedto.icalObjectId WHERE icalobject.component = 'VTODO' GROUP BY icalobjectId")
    fun getSubtasksCount(): LiveData<List<SubtaskCount>>

    /**
     * Retrieve the number of items in the table of [ICalObject] as Int.
     * Currently only used for Tests.
     *
     * @return Int with the total number of [ICalObject] in the table.
     */
    @Query("SELECT count(*) FROM $TABLE_NAME_ICALOBJECT")
    fun getCount(): Int

    /**
     * Retrieve an [ICalObject] by Id asynchronously (suspend)
     *
     * @param id The id of the [ICalObject] in the DB
     * @return the [ICalObject] with the passed id or null if not found
     */
    @Query("SELECT * FROM $TABLE_NAME_ICALOBJECT WHERE $COLUMN_ID = :id")
    suspend fun getICalObjectById(id: Long): ICalObject?



    /**
     * Retrieve an [ICalCollection] by Id synchronously (non-suspend)
     *
     * @param id The id of the [ICalCollection] in the DB
     * @return the [ICalCollection] with the passed id or null if not found
     */
    @Query("SELECT * FROM $TABLE_NAME_COLLECTION WHERE $COLUMN_COLLECTION_ID = :id")
    fun getCollectionByIdSync(id: Long): ICalCollection?




/*
INSERTs (Asyncronously / Suspend)
 */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertICalObject(iCalObject: ICalObject): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendee(attendee: Attendee): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: Comment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganizer(organizer: Organizer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(resource: Resource): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelatedto(relatedto: Relatedto): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: Attachment): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertCollection(ICalCollection: ICalCollection): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtask(subtask: ICalObject): Long


/*
INSERTs (Synchronously)
 */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertICalObjectSync(iCalObject: ICalObject): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttendeeSync(attendee: Attendee): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategorySync(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCommentSync(comment: Comment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrganizerSync(organizer: Organizer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRelatedtoSync(relatedto: Relatedto): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertContactSync(contact: Contact): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertResourceSync(resource: Resource): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAttachmentSync(attachment: Attachment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCollectionSync(ICalCollection: ICalCollection): Long



/*
DELETEs by Object
 */


    /**
     * Delete an iCalObject by the object.
     *
     * @param icalObject The object of the icalObject that should be deleted.
     */
    @Delete
    fun delete(icalObject: ICalObject)

    /**
     * Delete an iCalObject by the object.
     *
     * @param ids The list of iCalObjects to be deleted.
     */
    @Query("DELETE FROM icalobject WHERE _id IN (:ids)")
    fun deleteICalObjectsbyIds(ids: List<Long>)




    /**
     * Delete a category by the object.
     *
     * @param category The object of the category that should be deleted.
     */
    @Delete
    fun deleteCategory(category: Category)

    /**
     * Delete a comment by the object.
     *
     * @param comment The object of the comment that should be deleted.
     */
    @Delete
    fun deleteComment(comment: Comment)

    /**
     * Delete an attachment by the object.
     *
     * @param attachment The object of the attachment that should be deleted.
     */
    @Delete
    fun deleteAttachment(attachment: Attachment)

    /**
     * Delete a relatedto by the object.
     *
     * @param rel The object of the relatedto that should be deleted.
     */
    @Delete
    fun deleteRelatedto(rel: Relatedto)

    /**
     * Delete an attendee by the object.
     *
     * @param attendee The object of the attendee that should be deleted.
     */
    @Delete
    fun deleteAttendee(attendee: Attendee)

    /**
     * Delete a relatedto by parentId and childId.
     *
     * @param parentId The Id of the parent IcalObject.
     * @param childId The Id of the child IcalObject.
     */
    @Query("DELETE FROM relatedto WHERE relatedto.icalObjectId = :parentId and relatedto.linkedICalObjectId = :childId")
    fun deleteRelatedto(parentId: Long, childId: Long)



    /**
     * Delete all children of a parent ICalObject by the parent ID.
     *
     * @param parentKey The row ID of the parent.
     */
    @Transaction
    @Query("DELETE FROM icalobject WHERE icalobject._id in (SELECT linkedICalObjectId FROM relatedto WHERE relatedto.icalObjectId = :parentKey)")
    fun deleteRelatedChildren(parentKey: Long)

    /**
     * Delete entities through a RawQuery.
     * This is especially used for the Content Provider
     *
     * @param query The DELETE statement.
     * @return A number of Entities deleted.
     */
    @Transaction
    @RawQuery
    fun deleteRAW(query: SupportSQLiteQuery): Int

    /**
     * Updates entities through a RawQuery.
     * This is especially used for the Content Provider
     *
     * @param query The UPDATE statement.
     * @return A number of Entities that were updated.
     */
    @Transaction
    @RawQuery
    fun updateRAW(query: SupportSQLiteQuery): Int







    @Update
    suspend fun update(icalObject: ICalObject)

    @Update
    fun updateAttachment(attachment: Attachment)

    @Transaction
    @Query("UPDATE $TABLE_NAME_ICALOBJECT SET $COLUMN_PERCENT = :progress, $COLUMN_STATUS = :status, $COLUMN_LAST_MODIFIED = :lastModified, $COLUMN_SEQUENCE = $COLUMN_SEQUENCE + 1, $COLUMN_DIRTY = 1 WHERE $COLUMN_ID = :id")
    suspend fun updateProgress(id: Long, progress: Int, status: String, lastModified: Long)

    @Transaction
    @Query("UPDATE $TABLE_NAME_ICALOBJECT SET $COLUMN_DELETED = 1, $COLUMN_LAST_MODIFIED = :lastModified, $COLUMN_SEQUENCE = $COLUMN_SEQUENCE + 1, $COLUMN_DIRTY = 1 WHERE $COLUMN_ID in (:ids)")
    suspend fun updateDeleted(ids: List<Long>, lastModified: Long)

    @Transaction
    @Query("UPDATE $TABLE_NAME_ICALOBJECT SET $COLUMN_ICALOBJECT_COLLECTIONID = :collectionId, $COLUMN_LAST_MODIFIED = :lastModified, $COLUMN_SEQUENCE = $COLUMN_SEQUENCE + 1, $COLUMN_DIRTY = 1 WHERE $COLUMN_ID in (:ids)")
    suspend fun updateCollection(ids: List<Long>, collectionId: Long, lastModified: Long)

    @Transaction
    @Query("UPDATE $TABLE_NAME_ICALOBJECT SET $COLUMN_LAST_MODIFIED = :lastModified, $COLUMN_SEQUENCE = $COLUMN_SEQUENCE + 1, $COLUMN_DIRTY = 1 WHERE $COLUMN_ID = :id")
    suspend fun updateSetDirty(id: Long, lastModified: Long)






    @Transaction
    @RawQuery(observedEntities = [ICal4List::class, Relatedto::class])
    fun getIcalObjectWithRelatedto(query: SupportSQLiteQuery): LiveData<List<ICal4ListWithRelatedto>>


    @Transaction
    @Query("SELECT * from icalobject WHERE _id = :key")
    fun get(key: Long): LiveData<ICalEntity?>


    @Transaction
    @Query("SELECT * from icalobject WHERE _id = :key")
    fun getSync(key: Long): ICalEntity?



    // This query makes a Join between icalobjects and the linked (child) elements (JOIN relatedto ON icalobject.id = relatedto.linkedICalObjectId ) and then filters for one specific parent element (WHERE relatedto.icalObjectId = :parentKey)
    @Transaction
    @Query("SELECT * from $VIEW_NAME_ICAL4VIEWNOTE WHERE $COLUMN_RELATEDTO_ICALOBJECT_ID = :parentKey")
    fun getRelatedNotes(parentKey: Long): LiveData<List<ICal4ViewNote?>>

    // This query makes a Join between icalobjects and the linked (child) elements (JOIN relatedto ON icalobject.id = relatedto.linkedICalObjectId ) and then filters for one specific parent element (WHERE relatedto.icalObjectId = :parentKey)
    @Transaction
    @Query("SELECT icalobject.* from icalobject INNER JOIN relatedto ON icalobject._id = relatedto.linkedICalObjectId WHERE relatedto.icalObjectId = :parentKey and icalobject.component = 'VTODO'")
    fun getRelatedTodos(parentKey: Long): LiveData<List<ICalObject?>>



    /*
    Queries for the content provider returning a Cursor
     */
    @Transaction
    @RawQuery
    fun getCursor(query: SupportSQLiteQuery): Cursor?

    @Transaction
    @RawQuery
    fun getICalObjectRaw(query: SupportSQLiteQuery): List<ICalObject>


}


class SubtaskCount {
    var icalobjectId: Long = 0L
    var count: Int = 0
}

