/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.database.views

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.sqlite.db.SimpleSQLiteQuery
import at.techbee.jtx.R
import at.techbee.jtx.database.*
import at.techbee.jtx.database.properties.*
import at.techbee.jtx.ui.list.OrderBy
import at.techbee.jtx.ui.list.SortOrder
import at.techbee.jtx.util.DateTimeUtils
import java.time.*
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.days

const val VIEW_NAME_ICAL4LIST = "ical4list"

/**
 * This data class defines a view that is used by the IcalListViewModel.
 * It provides only necessary columns that are actually used by the View Model.
 * Additionally it provides the categories as a string concatenated field.
 * Additionally it provides the number of subtasks of an item.
 * Additionally it excludes remote items that are marked as deleted.
 */
@DatabaseView(
    viewName = VIEW_NAME_ICAL4LIST,
    value = "SELECT DISTINCT " +
            "main_icalobject.$COLUMN_ID, " +
            "main_icalobject.$COLUMN_MODULE, " +
            "main_icalobject.$COLUMN_COMPONENT, " +
            "main_icalobject.$COLUMN_SUMMARY, " +
            "main_icalobject.$COLUMN_DESCRIPTION, " +
            "main_icalobject.$COLUMN_LOCATION, " +
            "main_icalobject.$COLUMN_URL, " +
            "main_icalobject.$COLUMN_CONTACT, " +
            "main_icalobject.$COLUMN_DTSTART, " +
            "main_icalobject.$COLUMN_DTSTART_TIMEZONE, " +
            "main_icalobject.$COLUMN_DTEND, " +
            "main_icalobject.$COLUMN_DTEND_TIMEZONE, " +
            "main_icalobject.$COLUMN_STATUS, " +
            "main_icalobject.$COLUMN_CLASSIFICATION, " +
            "main_icalobject.$COLUMN_PERCENT, " +
            "main_icalobject.$COLUMN_PRIORITY, " +
            "main_icalobject.$COLUMN_DUE, " +
            "main_icalobject.$COLUMN_DUE_TIMEZONE, " +
            "main_icalobject.$COLUMN_COMPLETED, " +
            "main_icalobject.$COLUMN_COMPLETED_TIMEZONE, " +
            "main_icalobject.$COLUMN_DURATION, " +
            "main_icalobject.$COLUMN_CREATED, " +
            "main_icalobject.$COLUMN_DTSTAMP, " +
            "main_icalobject.$COLUMN_LAST_MODIFIED, " +
            "main_icalobject.$COLUMN_SEQUENCE, " +
            "main_icalobject.$COLUMN_UID, " +
            "collection.$COLUMN_COLLECTION_COLOR as colorCollection, " +
            "main_icalobject.$COLUMN_COLOR as colorItem, " +
            "main_icalobject.$COLUMN_ICALOBJECT_COLLECTIONID, " +
            "collection.$COLUMN_COLLECTION_ACCOUNT_NAME, " +
            "collection.$COLUMN_COLLECTION_DISPLAYNAME, " +
            "main_icalobject.$COLUMN_DELETED, " +
            "CASE WHEN main_icalobject.$COLUMN_DIRTY = 1 AND collection.$COLUMN_COLLECTION_ACCOUNT_TYPE != 'LOCAL' THEN 1 else 0 END as uploadPending, " +
            "main_icalobject.$COLUMN_RECUR_ORIGINALICALOBJECTID, " +
            "CASE WHEN main_icalobject.$COLUMN_RRULE IS NULL THEN 0 ELSE 1 END as isRecurringOriginal, " +
            "CASE WHEN main_icalobject.$COLUMN_RECURID IS NULL THEN 0 ELSE 1 END as isRecurringInstance, " +
            "main_icalobject.$COLUMN_RECUR_ISLINKEDINSTANCE, " +
            "CASE WHEN main_icalobject.$COLUMN_ID IN (SELECT sub_rel.$COLUMN_RELATEDTO_ICALOBJECT_ID FROM $TABLE_NAME_RELATEDTO sub_rel INNER JOIN $TABLE_NAME_ICALOBJECT sub_ical on sub_rel.$COLUMN_RELATEDTO_TEXT = sub_ical.$COLUMN_UID AND sub_ical.$COLUMN_MODULE = 'JOURNAL' AND sub_rel.$COLUMN_RELATEDTO_RELTYPE = 'PARENT') THEN 1 ELSE 0 END as isChildOfJournal, " +
            "CASE WHEN main_icalobject.$COLUMN_ID IN (SELECT sub_rel.$COLUMN_RELATEDTO_ICALOBJECT_ID FROM $TABLE_NAME_RELATEDTO sub_rel INNER JOIN $TABLE_NAME_ICALOBJECT sub_ical on sub_rel.$COLUMN_RELATEDTO_TEXT = sub_ical.$COLUMN_UID AND sub_ical.$COLUMN_MODULE = 'NOTE' AND sub_rel.$COLUMN_RELATEDTO_RELTYPE = 'PARENT') THEN 1 ELSE 0 END as isChildOfNote, " +
            "CASE WHEN main_icalobject.$COLUMN_ID IN (SELECT sub_rel.$COLUMN_RELATEDTO_ICALOBJECT_ID FROM $TABLE_NAME_RELATEDTO sub_rel INNER JOIN $TABLE_NAME_ICALOBJECT sub_ical on sub_rel.$COLUMN_RELATEDTO_TEXT = sub_ical.$COLUMN_UID AND sub_ical.$COLUMN_MODULE = 'TODO' AND sub_rel.$COLUMN_RELATEDTO_RELTYPE = 'PARENT') THEN 1 ELSE 0 END as isChildOfTodo, " +
            "(SELECT sub_rel.$COLUMN_RELATEDTO_TEXT FROM $TABLE_NAME_RELATEDTO sub_rel INNER JOIN $TABLE_NAME_ICALOBJECT sub_ical on sub_rel.$COLUMN_RELATEDTO_ICALOBJECT_ID = sub_ical.$COLUMN_ID AND sub_ical.$COLUMN_COMPONENT = 'VTODO' AND sub_rel.$COLUMN_RELATEDTO_RELTYPE = 'PARENT' AND sub_rel.$COLUMN_RELATEDTO_ICALOBJECT_ID = main_icalobject.$COLUMN_ID AND sub_ical.$COLUMN_DELETED = 0) as vtodoUidOfParent, " +
            "(SELECT sub_rel.$COLUMN_RELATEDTO_TEXT FROM $TABLE_NAME_RELATEDTO sub_rel INNER JOIN $TABLE_NAME_ICALOBJECT sub_ical on sub_rel.$COLUMN_RELATEDTO_ICALOBJECT_ID = sub_ical.$COLUMN_ID AND sub_ical.$COLUMN_COMPONENT = 'VJOURNAL' AND sub_rel.$COLUMN_RELATEDTO_RELTYPE = 'PARENT' AND sub_rel.$COLUMN_RELATEDTO_ICALOBJECT_ID = main_icalobject.$COLUMN_ID AND sub_ical.$COLUMN_DELETED = 0) as vjournalUidOfParent, " +
            "(SELECT group_concat($TABLE_NAME_CATEGORY.$COLUMN_CATEGORY_TEXT, \', \') FROM $TABLE_NAME_CATEGORY WHERE main_icalobject.$COLUMN_ID = $TABLE_NAME_CATEGORY.$COLUMN_CATEGORY_ICALOBJECT_ID GROUP BY $TABLE_NAME_CATEGORY.$COLUMN_CATEGORY_ICALOBJECT_ID) as categories, " +
            "(SELECT count(*) FROM $TABLE_NAME_ICALOBJECT sub_icalobject INNER JOIN $TABLE_NAME_RELATEDTO sub_relatedto ON sub_icalobject.$COLUMN_ID = sub_relatedto.$COLUMN_RELATEDTO_ICALOBJECT_ID AND sub_icalobject.$COLUMN_COMPONENT = 'VTODO' AND sub_relatedto.$COLUMN_RELATEDTO_TEXT = main_icalobject.$COLUMN_UID AND sub_relatedto.$COLUMN_RELATEDTO_RELTYPE = 'PARENT' AND sub_icalobject.$COLUMN_DELETED = 0) as numSubtasks, " +
            "(SELECT count(*) FROM $TABLE_NAME_ICALOBJECT sub_icalobject INNER JOIN $TABLE_NAME_RELATEDTO sub_relatedto ON sub_icalobject.$COLUMN_ID = sub_relatedto.$COLUMN_RELATEDTO_ICALOBJECT_ID AND sub_icalobject.$COLUMN_COMPONENT = 'VJOURNAL' AND sub_relatedto.$COLUMN_RELATEDTO_TEXT = main_icalobject.$COLUMN_UID AND sub_relatedto.$COLUMN_RELATEDTO_RELTYPE = 'PARENT' AND sub_icalobject.$COLUMN_DELETED = 0) as numSubnotes, " +
            "(SELECT count(*) FROM $TABLE_NAME_ATTACHMENT WHERE $COLUMN_ATTACHMENT_ICALOBJECT_ID = main_icalobject.$COLUMN_ID  ) as numAttachments, " +
            "(SELECT count(*) FROM $TABLE_NAME_ATTENDEE WHERE $COLUMN_ATTENDEE_ICALOBJECT_ID = main_icalobject.$COLUMN_ID  ) as numAttendees, " +
            "(SELECT count(*) FROM $TABLE_NAME_COMMENT WHERE $COLUMN_COMMENT_ICALOBJECT_ID = main_icalobject.$COLUMN_ID  ) as numComments, " +
            "(SELECT count(*) FROM $TABLE_NAME_RELATEDTO WHERE $COLUMN_RELATEDTO_ICALOBJECT_ID = main_icalobject.$COLUMN_ID  ) as numRelatedTodos, " +
            "(SELECT count(*) FROM $TABLE_NAME_RESOURCE WHERE $COLUMN_RESOURCE_ICALOBJECT_ID = main_icalobject.$COLUMN_ID  ) as numResources, " +
            "(SELECT count(*) FROM $TABLE_NAME_ALARM WHERE $COLUMN_ALARM_ICALOBJECT_ID = main_icalobject.$COLUMN_ID  ) as numAlarms, " +
            "(SELECT $COLUMN_ATTACHMENT_URI FROM $TABLE_NAME_ATTACHMENT WHERE $COLUMN_ATTACHMENT_ICALOBJECT_ID = main_icalobject.$COLUMN_ID AND ($COLUMN_ATTACHMENT_FMTTYPE LIKE 'audio/%' OR $COLUMN_ATTACHMENT_FMTTYPE LIKE 'video/%') LIMIT 1 ) as audioAttachment, " +
            "collection.$COLUMN_COLLECTION_READONLY as isReadOnly, " +
            "main_icalobject.$COLUMN_SUBTASKS_EXPANDED, " +
            "main_icalobject.$COLUMN_SUBNOTES_EXPANDED, " +
            "main_icalobject.$COLUMN_ATTACHMENTS_EXPANDED, " +
            "main_icalobject.$COLUMN_SORT_INDEX " +
            "FROM $TABLE_NAME_ICALOBJECT main_icalobject " +
            //"LEFT JOIN $TABLE_NAME_CATEGORY ON main_icalobject.$COLUMN_ID = $TABLE_NAME_CATEGORY.$COLUMN_CATEGORY_ICALOBJECT_ID " +
            "INNER JOIN $TABLE_NAME_COLLECTION collection ON main_icalobject.$COLUMN_ICALOBJECT_COLLECTIONID = collection.$COLUMN_COLLECTION_ID " +
            "WHERE main_icalobject.$COLUMN_DELETED = 0 AND main_icalobject.$COLUMN_RRULE IS NULL"
)           // locally deleted entries are already excluded in the view, original recur entries are also excluded

@kotlinx.serialization.Serializable
data class ICal4List(

    @ColumnInfo(index = true, name = COLUMN_ID) var id: Long,
    @ColumnInfo(name = COLUMN_MODULE) var module: String,
    @ColumnInfo(name = COLUMN_COMPONENT) var component: String,
    @ColumnInfo(name = COLUMN_SUMMARY) var summary: String?,
    @ColumnInfo(name = COLUMN_DESCRIPTION) var description: String?,
    @ColumnInfo(name = COLUMN_LOCATION) var location: String?,
    @ColumnInfo(name = COLUMN_URL) var url: String?,
    @ColumnInfo(name = COLUMN_CONTACT) var contact: String?,

    @ColumnInfo(name = COLUMN_DTSTART) var dtstart: Long?,
    @ColumnInfo(name = COLUMN_DTSTART_TIMEZONE) var dtstartTimezone: String?,

    @ColumnInfo(name = COLUMN_DTEND) var dtend: Long?,
    @ColumnInfo(name = COLUMN_DTEND_TIMEZONE) var dtendTimezone: String?,

    @ColumnInfo(name = COLUMN_STATUS) var status: String?,
    @ColumnInfo(name = COLUMN_CLASSIFICATION) var classification: String?,

    @ColumnInfo(name = COLUMN_PERCENT) var percent: Int?,
    @ColumnInfo(name = COLUMN_PRIORITY) var priority: Int?,

    @ColumnInfo(name = COLUMN_DUE) var due: Long?,
    @ColumnInfo(name = COLUMN_DUE_TIMEZONE) var dueTimezone: String?,
    @ColumnInfo(name = COLUMN_COMPLETED) var completed: Long?,
    @ColumnInfo(name = COLUMN_COMPLETED_TIMEZONE) var completedTimezone: String?,
    @ColumnInfo(name = COLUMN_DURATION) var duration: String?,

    @ColumnInfo(name = COLUMN_CREATED) var created: Long,
    @ColumnInfo(name = COLUMN_DTSTAMP) var dtstamp: Long,
    @ColumnInfo(name = COLUMN_LAST_MODIFIED) var lastModified: Long,
    @ColumnInfo(name = COLUMN_SEQUENCE) var sequence: Long,
    @ColumnInfo(name = COLUMN_UID) var uid: String?,

    @ColumnInfo var colorCollection: Int?,
    @ColumnInfo var colorItem: Int?,


    @ColumnInfo(index = true, name = COLUMN_ICALOBJECT_COLLECTIONID) var collectionId: Long?,
    @ColumnInfo(name = COLUMN_COLLECTION_ACCOUNT_NAME) var accountName: String?,
    @ColumnInfo(name = COLUMN_COLLECTION_DISPLAYNAME) var collectionDisplayName: String?,

    @ColumnInfo(name = COLUMN_DELETED) var deleted: Boolean,
    @ColumnInfo var uploadPending: Boolean,

    @ColumnInfo(name = COLUMN_RECUR_ORIGINALICALOBJECTID) var recurOriginalIcalObjectId: Long?,
    @ColumnInfo var isRecurringOriginal: Boolean,
    @ColumnInfo var isRecurringInstance: Boolean,
    @ColumnInfo(name = COLUMN_RECUR_ISLINKEDINSTANCE) var isLinkedRecurringInstance: Boolean,

    @ColumnInfo var isChildOfJournal: Boolean,
    @ColumnInfo var isChildOfNote: Boolean,
    @ColumnInfo var isChildOfTodo: Boolean,

    @ColumnInfo var vtodoUidOfParent: String?,
    @ColumnInfo var vjournalUidOfParent: String?,

    @ColumnInfo var categories: String?,
    @ColumnInfo var numSubtasks: Int,
    @ColumnInfo var numSubnotes: Int,
    @ColumnInfo var numAttachments: Int,
    @ColumnInfo var numAttendees: Int,
    @ColumnInfo var numComments: Int,
    @ColumnInfo var numRelatedTodos: Int,
    @ColumnInfo var numResources: Int,
    @ColumnInfo var numAlarms: Int,
    @ColumnInfo var audioAttachment: String?,
    @ColumnInfo var isReadOnly: Boolean,

    @ColumnInfo(name = COLUMN_SUBTASKS_EXPANDED) var isSubtasksExpanded: Boolean? = null,
    @ColumnInfo(name = COLUMN_SUBNOTES_EXPANDED) var isSubnotesExpanded: Boolean? = null,
    @ColumnInfo(name = COLUMN_ATTACHMENTS_EXPANDED) var isAttachmentsExpanded: Boolean? = null,
    @ColumnInfo(name = COLUMN_SORT_INDEX) var sortIndex: Int? = null,
    )
{

    companion object {
        fun getSample() =
            ICal4List(
                id = 1L,
                module = Module.JOURNAL.name,
                component = Component.VJOURNAL.name,
                "My Summary",
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Curabitur tellus risus, tristique ac elit vitae, mollis feugiat quam. Duis aliquet arcu at purus porttitor ultricies. Vivamus sagittis feugiat ex eu efficitur. Aliquam nec cursus ante, a varius nisi. In a malesuada urna, in rhoncus est. Maecenas auctor molestie quam, quis lobortis tortor sollicitudin sagittis. Curabitur sit amet est varius urna mattis interdum.\n" +
                        "\n" +
                        "Phasellus id quam vel enim semper ullamcorper in ac velit. Aliquam eleifend dignissim lacinia. Donec elementum ex et dui iaculis, eget vehicula leo bibendum. Nam turpis erat, luctus ut vehicula quis, congue non ex. In eget risus consequat, luctus ipsum nec, venenatis elit. In in tellus vel mauris rhoncus bibendum. Pellentesque sit amet quam elementum, pharetra nisl id, vehicula turpis. ",
                null,
                null,
                null,
                System.currentTimeMillis(),
                null,
                null,
                null,
                status = StatusJournal.DRAFT.name,
                classification = Classification.CONFIDENTIAL.name,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                0,
                null,
                Color.Magenta.toArgb(),
                Color.Cyan.toArgb(),
                1L,
                "myAccount",
                "myCollection",
                deleted = false,
                uploadPending = true,
                recurOriginalIcalObjectId = null,
                isRecurringOriginal = true,
                isRecurringInstance = true,
                isLinkedRecurringInstance = true,
                isChildOfJournal = false,
                isChildOfNote = false,
                isChildOfTodo = false,
                vtodoUidOfParent = null,
                vjournalUidOfParent = null,
                categories = "Category1, Whatever",
                numSubtasks = 3,
                numSubnotes = 2,
                numAttachments = 4,
                numAttendees = 5,
                numComments = 6,
                numRelatedTodos = 7,
                numResources = 8,
                numAlarms = 2,
                audioAttachment = null,
                isReadOnly = true
            )

        fun constructQuery(
            module: Module,
            searchCategories: List<String> = emptyList(),
            searchStatusTodo: List<StatusTodo> = emptyList(),
            searchStatusJournal: List<StatusJournal> = emptyList(),
            searchClassification: List<Classification> = emptyList(),
            searchCollection: List<String> = emptyList(),
            searchAccount: List<String> = emptyList(),
            orderBy: OrderBy = OrderBy.CREATED,
            sortOrder: SortOrder = SortOrder.ASC,
            orderBy2: OrderBy = OrderBy.SUMMARY,
            sortOrder2: SortOrder = SortOrder.ASC,
            isExcludeDone: Boolean = false,
            isFilterOverdue: Boolean = false,
            isFilterDueToday: Boolean = false,
            isFilterDueTomorrow: Boolean = false,
            isFilterDueFuture: Boolean = false,
            isFilterStartInPast: Boolean = false,
            isFilterStartToday: Boolean = false,
            isFilterStartTomorrow: Boolean = false,
            isFilterStartFuture: Boolean = false,
            isFilterNoDatesSet: Boolean = false,
            searchText: String? = null,
            flatView: Boolean = false,
            searchSettingShowOneRecurEntryInFuture: Boolean = false
        ): SimpleSQLiteQuery {

            val args = arrayListOf<String>()

            // Beginning of query string
            var queryString = "SELECT DISTINCT $VIEW_NAME_ICAL4LIST.* FROM $VIEW_NAME_ICAL4LIST "
            if(searchCategories.isNotEmpty())
                queryString += "LEFT JOIN $TABLE_NAME_CATEGORY ON $VIEW_NAME_ICAL4LIST.$COLUMN_ID = $TABLE_NAME_CATEGORY.$COLUMN_CATEGORY_ICALOBJECT_ID "
            if(searchCollection.isNotEmpty() || searchAccount.isNotEmpty())
                queryString += "LEFT JOIN $TABLE_NAME_COLLECTION ON $VIEW_NAME_ICAL4LIST.$COLUMN_ICALOBJECT_COLLECTIONID = $TABLE_NAME_COLLECTION.$COLUMN_COLLECTION_ID "  // +

            // First query parameter Module must always be present!
            queryString += "WHERE $COLUMN_MODULE = ? "
            args.add(module.name)

            // Query for the given text search from the action bar
            searchText?.let { text ->
                if (text.length >= 2) {
                    queryString += "AND ($VIEW_NAME_ICAL4LIST.$COLUMN_SUMMARY LIKE ? OR $VIEW_NAME_ICAL4LIST.$COLUMN_DESCRIPTION LIKE ?) "
                    args.add("%" + text + "%")
                    args.add("%" + text + "%")
                }
            }

            // Query for the passed filter criteria from VJournalFilterFragment
            if (searchCategories.isNotEmpty()) {
                queryString += "AND $TABLE_NAME_CATEGORY.$COLUMN_CATEGORY_TEXT IN ("
                searchCategories.forEach {
                    queryString += "?,"
                    args.add(it)
                }
                queryString = queryString.removeSuffix(",")      // remove the last comma
                queryString += ") "
            }

            // Query for the passed filter criteria from FilterFragment
            if (searchStatusJournal.isNotEmpty() && (module == Module.JOURNAL || module == Module.NOTE)) {
                queryString += "AND $COLUMN_STATUS IN ("
                searchStatusJournal.forEach {
                    queryString += "?,"
                    args.add(it.toString())
                }
                queryString = queryString.removeSuffix(",")      // remove the last comma
                queryString += ") "
            }

            // Query for the passed filter criteria from FilterFragment
            if (searchStatusTodo.isNotEmpty() && module == Module.TODO) {
                queryString += "AND $COLUMN_STATUS IN ("
                searchStatusTodo.forEach {
                    queryString += "?,"
                    args.add(it.toString())
                }
                queryString = queryString.removeSuffix(",")      // remove the last comma
                queryString += ") "
            }

            if (isExcludeDone)
                queryString += "AND $COLUMN_PERCENT IS NOT 100 "

            val dateQuery = mutableListOf<String>()
            if (isFilterStartInPast)
                dateQuery.add("$COLUMN_DTSTART < ${System.currentTimeMillis()}")
            if (isFilterStartToday)
                dateQuery.add("$COLUMN_DTSTART BETWEEN ${DateTimeUtils.getTodayAsLong()} AND ${DateTimeUtils.getTodayAsLong() + (1).days.inWholeMilliseconds-1}")
            if (isFilterStartTomorrow)
                dateQuery.add("$COLUMN_DTSTART BETWEEN ${DateTimeUtils.getTodayAsLong()+ (1).days.inWholeMilliseconds} AND ${DateTimeUtils.getTodayAsLong() + (2).days.inWholeMilliseconds-1}")
            if (isFilterStartFuture)
                dateQuery.add("$COLUMN_DTSTART > ${System.currentTimeMillis()}")
            if (isFilterOverdue)
                dateQuery.add("$COLUMN_DUE < ${System.currentTimeMillis()}")
            if (isFilterDueToday)
                dateQuery.add("$COLUMN_DUE BETWEEN ${DateTimeUtils.getTodayAsLong()} AND ${DateTimeUtils.getTodayAsLong()+ (1).days.inWholeMilliseconds-1}")
            if (isFilterDueTomorrow)
                dateQuery.add("$COLUMN_DUE BETWEEN ${DateTimeUtils.getTodayAsLong()+ (1).days.inWholeMilliseconds} AND ${DateTimeUtils.getTodayAsLong() + (2).days.inWholeMilliseconds-1}")
            if (isFilterDueFuture)
                dateQuery.add("$COLUMN_DUE > ${System.currentTimeMillis()}")
            if(isFilterNoDatesSet)
                dateQuery.add("$COLUMN_DTSTART IS NULL AND $COLUMN_DUE IS NULL AND $COLUMN_COMPLETED IS NULL ")
            if(dateQuery.isNotEmpty())
                queryString += " AND (${dateQuery.joinToString(separator = " OR ")}) "

            // Query for the passed filter criteria from FilterFragment
            if (searchClassification.isNotEmpty()) {
                queryString += "AND $COLUMN_CLASSIFICATION IN ("
                searchClassification.forEach {
                    queryString += "?,"
                    args.add(it.toString())
                }
                queryString = queryString.removeSuffix(",")      // remove the last comma
                queryString += ") "
            }


            // Query for the passed filter criteria from FilterFragment
            if (searchCollection.isNotEmpty()) {
                queryString += "AND $TABLE_NAME_COLLECTION.$COLUMN_COLLECTION_DISPLAYNAME IN ("
                searchCollection.forEach {
                    queryString += "?,"
                    args.add(it)
                }
                queryString = queryString.removeSuffix(",")      // remove the last comma
                queryString += ") "
            }

            // Query for the passed filter criteria from FilterFragment
            if (searchAccount.isNotEmpty()) {
                queryString += "AND $TABLE_NAME_COLLECTION.$COLUMN_COLLECTION_ACCOUNT_NAME IN ("
                searchAccount.forEach {
                    queryString += "?,"
                    args.add(it)
                }
                queryString = queryString.removeSuffix(",")      // remove the last comma
                queryString += ") "
            }

            // Exclude items that are Child items by checking if they appear in the linkedICalObjectId of relatedto!
            //queryString += "AND $VIEW_NAME_ICAL4LIST.$COLUMN_ID NOT IN (SELECT $COLUMN_RELATEDTO_LINKEDICALOBJECT_ID FROM $TABLE_NAME_RELATEDTO) "
            if(!flatView)
                queryString += "AND $VIEW_NAME_ICAL4LIST.isChildOfTodo = 0 AND $VIEW_NAME_ICAL4LIST.isChildOfJournal = 0 AND $VIEW_NAME_ICAL4LIST.isChildOfNote = 0 "

            if(searchSettingShowOneRecurEntryInFuture) {
                queryString += "AND ($VIEW_NAME_ICAL4LIST.$COLUMN_RECUR_ISLINKEDINSTANCE = 0 " +
                        "OR $VIEW_NAME_ICAL4LIST.$COLUMN_DTSTART <= " +
                        "(SELECT MIN(recurList.$COLUMN_DTSTART) FROM $TABLE_NAME_ICALOBJECT as recurList WHERE recurList.$COLUMN_RECUR_ORIGINALICALOBJECTID = $VIEW_NAME_ICAL4LIST.$COLUMN_RECUR_ORIGINALICALOBJECTID AND recurList.$COLUMN_RECUR_ISLINKEDINSTANCE = 1 AND recurList.$COLUMN_DTSTART >= ${DateTimeUtils.getTodayAsLong()} )) "
            }

            queryString += "ORDER BY "
            queryString += orderBy.queryAppendix
            sortOrder.let { queryString += it.queryAppendix }

            queryString += ", "
            queryString += orderBy2.queryAppendix
            sortOrder2.let { queryString += it.queryAppendix }

            //Log.println(Log.INFO, "queryString", queryString)
            //Log.println(Log.INFO, "queryStringArgs", args.joinToString(separator = ", "))
            return SimpleSQLiteQuery(queryString, args.toArray())
        }
    }



    /**
     * @return the audioAttachment as Uri or null
     */
    fun getAudioAttachmentAsUri(): Uri? {
        return try {
            Uri.parse(audioAttachment)
        } catch (e: Exception) {
            null
        }
    }
}