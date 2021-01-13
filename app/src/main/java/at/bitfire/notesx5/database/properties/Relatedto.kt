package at.bitfire.notesx5.database.properties

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import at.bitfire.notesx5.database.VJournal

@Entity(tableName = "relatedto",
        foreignKeys = [ForeignKey(entity = VJournal::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("journalLinkId"),
                onDelete = ForeignKey.CASCADE)])
data class Relatedto (

        @PrimaryKey(autoGenerate = true)
        var id: Long = 0L,
        var journalLinkId: Long = 0L,
        var text: String = "",
        var reltypeparam: String? = null,
        var otherparam: String? = null
)

