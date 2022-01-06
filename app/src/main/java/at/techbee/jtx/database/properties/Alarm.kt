/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.database.properties

import android.content.ContentValues
import android.os.Parcelable
import android.provider.BaseColumns
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import at.techbee.jtx.database.COLUMN_ID
import at.techbee.jtx.database.ICalObject
import kotlinx.parcelize.Parcelize
import net.fortuna.ical4j.model.DateTime
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Trigger

/** The name of the the table for Alarms that are linked to an ICalObject.
 * [https://tools.ietf.org/html/rfc5545#section-3.8.1.10]*/
const val TABLE_NAME_ALARM = "alarm"

/** The name of the ID column for alarms.
 * This is the unique identifier of an Alarm
 * Type: [Long]*/
const val COLUMN_ALARM_ID = BaseColumns._ID

/** The name of the Foreign Key Column for IcalObjects.
 * Type: [Long] */
const val COLUMN_ALARM_ICALOBJECT_ID = "icalObjectId"
const val COLUMN_ALARM_ACTION = "action"
const val COLUMN_ALARM_DESCRIPTION = "description"
const val COLUMN_ALARM_TRIGGER = "trigger"
const val COLUMN_ALARM_SUMMARY = "summary"
const val COLUMN_ALARM_ATTENDEE = "attendee"
const val COLUMN_ALARM_DURATION = "duration"
const val COLUMN_ALARM_REPEAT = "repeat"
const val COLUMN_ALARM_ATTACH = "attach"
const val COLUMN_ALARM_OTHER = "other"



@Parcelize
@Entity(tableName = TABLE_NAME_ALARM,
    foreignKeys = [ForeignKey(entity = ICalObject::class,
        parentColumns = arrayOf(COLUMN_ID),
        childColumns = arrayOf(COLUMN_ALARM_ICALOBJECT_ID),
        onDelete = ForeignKey.CASCADE)])
data class Alarm (

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(index = true, name = COLUMN_ALARM_ID)
    var alarmId: Long = 0L,

    @ColumnInfo(index = true, name = COLUMN_ALARM_ICALOBJECT_ID)var icalObjectId: Long = 0L,
    @ColumnInfo(index = true, name = COLUMN_ALARM_ACTION) var action: String? = null,
    @ColumnInfo(index = true, name = COLUMN_ALARM_DESCRIPTION) var description: String? = null,
    @ColumnInfo(index = true, name = COLUMN_ALARM_TRIGGER) var trigger: String? = null,
    @ColumnInfo(index = true, name = COLUMN_ALARM_SUMMARY) var summary: String? = null,
    @ColumnInfo(index = true, name = COLUMN_ALARM_ATTENDEE)var attendee: String? = null,
    @ColumnInfo(index = true, name = COLUMN_ALARM_DURATION) var duration: String? = null,
    @ColumnInfo(index = true, name = COLUMN_ALARM_REPEAT) var repeat: String? = null,
    @ColumnInfo(index = true, name = COLUMN_ALARM_ATTACH) var attach: String? = null,
    @ColumnInfo(index = true, name = COLUMN_ALARM_OTHER) var other: String? = null,
): Parcelable

{
    companion object Factory {

        /**
         * Create a new [Alarm] Property from the specified [ContentValues].
         *
         * @param values A [Alarm] that at least contain [COLUMN_ALARM_ICALOBJECT_ID].
         * @return A newly created [Alarm] instance.
         */
        fun fromContentValues(values: ContentValues?): Alarm? {

            if (values == null)
                return null

            if(values.getAsLong(COLUMN_ALARM_ICALOBJECT_ID) == null)
                return null

            return Alarm().applyContentValues(values)
        }

        fun fromTimestamp(timestamp: Long) = Alarm().apply {
                this.trigger = getTriggerAsDateTime(timestamp)
            }

        fun getTriggerAsDateTime(timestamp: Long): String {
            val vAlarm = VAlarm(DateTime(timestamp))
            vAlarm.trigger.isUtc = true
            return vAlarm.trigger.value
        }
    }

    fun applyContentValues(values: ContentValues): Alarm {
        values.getAsLong(COLUMN_ALARM_ICALOBJECT_ID)?.let { icalObjectId -> this.icalObjectId = icalObjectId }
        values.getAsString(COLUMN_ALARM_ACTION)?.let { action -> this.action = action }
        values.getAsString(COLUMN_ALARM_DESCRIPTION)?.let { desc -> this.description = desc }
        values.getAsString(COLUMN_ALARM_TRIGGER)?.let { trigger -> this.trigger = trigger }
        values.getAsString(COLUMN_ALARM_SUMMARY)?.let { summary -> this.summary = summary }
        values.getAsString(COLUMN_ALARM_ATTENDEE)?.let { attendee -> this.attendee = attendee }
        values.getAsString(COLUMN_ALARM_DURATION)?.let { duration -> this.duration = duration }
        values.getAsString(COLUMN_ALARM_REPEAT)?.let { repeat -> this.repeat = repeat }
        values.getAsString(COLUMN_ALARM_ATTACH)?.let { attach -> this.attach = attach }
        values.getAsString(COLUMN_ALARM_OTHER)?.let { other -> this.other = other }
        return this
    }

    fun getTriggerAsLong(): Long? {
        if(trigger == null) 
            return null

        val vAlarm = VAlarm()
        vAlarm.properties.add(Trigger())
        vAlarm.trigger.value = trigger
        vAlarm.trigger.isUtc = true

        if(vAlarm.trigger.dateTime != null)
            return vAlarm.trigger.dateTime.time

        return null
    }
}