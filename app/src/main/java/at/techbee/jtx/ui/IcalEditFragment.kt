/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.ui

import android.Manifest
import android.app.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.util.Size
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavDeepLinkBuilder
import androidx.navigation.fragment.findNavController
import androidx.work.*
import at.techbee.jtx.*
import at.techbee.jtx.NotificationPublisher
import at.techbee.jtx.R
import at.techbee.jtx.database.*
import at.techbee.jtx.database.Component
import at.techbee.jtx.database.properties.*
import at.techbee.jtx.databinding.*
import at.techbee.jtx.ui.IcalEditViewModel.Companion.RECURRENCE_MODE_DAY
import at.techbee.jtx.ui.IcalEditViewModel.Companion.RECURRENCE_MODE_MONTH
import at.techbee.jtx.ui.IcalEditViewModel.Companion.RECURRENCE_MODE_UNSUPPORTED
import at.techbee.jtx.ui.IcalEditViewModel.Companion.RECURRENCE_MODE_WEEK
import at.techbee.jtx.ui.IcalEditViewModel.Companion.RECURRENCE_MODE_YEAR
import at.techbee.jtx.ui.IcalEditViewModel.Companion.TAB_ALARMS
import at.techbee.jtx.ui.IcalEditViewModel.Companion.TAB_ATTACHMENTS
import at.techbee.jtx.ui.IcalEditViewModel.Companion.TAB_LOC_COMMENTS
import at.techbee.jtx.ui.IcalEditViewModel.Companion.TAB_GENERAL
import at.techbee.jtx.ui.IcalEditViewModel.Companion.TAB_PEOPLE_RES
import at.techbee.jtx.ui.IcalEditViewModel.Companion.TAB_RECURRING
import at.techbee.jtx.ui.IcalEditViewModel.Companion.TAB_SUBTASKS
import at.techbee.jtx.util.*
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import net.fortuna.ical4j.model.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ClassCastException
import java.time.*
import java.util.*


class IcalEditFragment : Fragment() {

    lateinit var binding: FragmentIcalEditBinding

    private lateinit var application: Application
    private lateinit var dataSource: ICalDatabaseDao
    private lateinit var viewModelFactory: IcalEditViewModelFactory
    lateinit var icalEditViewModel: IcalEditViewModel
    private lateinit var inflater: LayoutInflater
    private var container: ViewGroup? = null
    private var menu: Menu? = null

    private val allContactsMail: MutableList<String> = mutableListOf()
    //private val allContactsNameAndMail: MutableList<String> = mutableListOf()

    private var photoUri: Uri? = null     // Uri for captured photo

    private var filepickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                processFileAttachment(result.data?.data)
            }
        }

    private var photoAttachmentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                processPhotoAttachment()
            }
        }


    companion object {
        const val TAG_PICKER_DTSTART = "dtstart"
        const val TAG_PICKER_DUE = "due"
        const val TAG_PICKER_COMPLETED = "completed"

        const val PREFS_EDIT_VIEW = "sharedPreferencesEditView"
        const val PREFS_LAST_COLLECTION = "lastUsedCollection"
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        // Get a reference to the binding object and inflate the fragment views.

        this.inflater = inflater
        this.binding = FragmentIcalEditBinding.inflate(inflater, container, false)
        this.container = container
        this.application = requireNotNull(this.activity).application

        this.dataSource = ICalDatabase.getInstance(application).iCalDatabaseDao

        val arguments = IcalEditFragmentArgs.fromBundle((requireArguments()))

        val prefs = activity?.getSharedPreferences(PREFS_EDIT_VIEW, Context.MODE_PRIVATE)!!


        // add menu
        setHasOptionsMenu(true)


        // Check if the permission to read local contacts is already granted, otherwise make a dialog to ask for permission
        if (ContextCompat.checkSelfPermission(
                requireActivity().applicationContext,
                Manifest.permission.READ_CONTACTS
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadContacts()
        } else {
            //request for permission to load contacts
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.edit_fragment_app_permission))
                .setMessage(getString(R.string.edit_fragment_app_permission_message))
                .setPositiveButton("Ok") { _, _ ->
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(Manifest.permission.READ_CONTACTS),
                        CONTACT_READ_PERMISSION_CODE
                    )
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .show()
        }


        this.viewModelFactory =
            IcalEditViewModelFactory(arguments.icalentity, dataSource, application)
        icalEditViewModel =
            ViewModelProvider(
                this, viewModelFactory
            )[IcalEditViewModel::class.java]

        binding.model = icalEditViewModel
        binding.lifecycleOwner = viewLifecycleOwner


        val priorityItems = resources.getStringArray(R.array.priority)

        var classificationItems: Array<String> = arrayOf()
        Classification.values().forEach {
            classificationItems = classificationItems.plus(getString(it.stringResource))
        }

        var statusItems: Array<String> = arrayOf()
        if (icalEditViewModel.iCalEntity.property.component == Component.VTODO.name) {
            StatusTodo.values()
                .forEach { statusItems = statusItems.plus(getString(it.stringResource)) }
        } else {
            StatusJournal.values()
                .forEach { statusItems = statusItems.plus(getString(it.stringResource)) }
        }

        //Don't show the recurring tab for Notes
        if(icalEditViewModel.iCalEntity.property.module == Module.NOTE.name && binding.icalEditTabs.tabCount >= TAB_RECURRING)
            binding.icalEditTabs.getTabAt(TAB_RECURRING)?.view?.visibility = View.GONE

        // Until implemented remove the tab for alarms and attachments
        binding.icalEditTabs.getTabAt(TAB_ALARMS)?.view?.visibility = View.GONE
        //binding.icalEditTabs.getTabAt(TAB_ATTACHMENTS).view.visibility = View.GONE


        binding.editCollectionSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {

                override fun onItemSelected(p0: AdapterView<*>?, view: View?, pos: Int, p3: Long) {
                    icalEditViewModel.iCalObjectUpdated.value?.collectionId =
                        icalEditViewModel.allCollections.value?.get(pos)?.collectionId ?: 1L
                    updateCollectionColor()

                    icalEditViewModel.allCollections.removeObservers(viewLifecycleOwner)     // make sure the selection doesn't change anymore by any sync happening that affects the oberser/collection-lsit
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }

        // notify the user if a duration was detected (currently not supported)
        if(icalEditViewModel.iCalEntity.property.duration?.isNotEmpty() == true) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.edit_fragment_recur_unsupported_duration_dialog_title))
                .setMessage(getString(R.string.edit_fragment_recur_unsupported_duration_dialog_message))
                .setPositiveButton(R.string.ok) { _, _ ->  }
                .show()
        }


        val weekdays = getLocalizedWeekdays()
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip0.text = weekdays[0]
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip1.text = weekdays[1]
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip2.text = weekdays[2]
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip3.text = weekdays[3]
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip4.text = weekdays[4]
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip5.text = weekdays[5]
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip6.text = weekdays[6]
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip0.setOnCheckedChangeListener { _, _ -> updateRRule() }
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip1.setOnCheckedChangeListener { _, _ -> updateRRule() }
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip2.setOnCheckedChangeListener { _, _ -> updateRRule() }
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip3.setOnCheckedChangeListener { _, _ -> updateRRule() }
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip4.setOnCheckedChangeListener { _, _ -> updateRRule() }
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip5.setOnCheckedChangeListener { _, _ -> updateRRule() }
        binding.editFragmentIcalEditRecur.editRecurWeekdayChip6.setOnCheckedChangeListener { _, _ -> updateRRule() }

        binding.editFragmentIcalEditRecur.editRecurEveryXNumberPicker.wrapSelectorWheel = false
        binding.editFragmentIcalEditRecur.editRecurEveryXNumberPicker.minValue = 1
        binding.editFragmentIcalEditRecur.editRecurEveryXNumberPicker.maxValue = 31
        binding.editFragmentIcalEditRecur.editRecurEveryXNumberPicker.setOnValueChangedListener { _, _, _ -> updateRRule() }

        binding.editFragmentIcalEditRecur.editRecurUntilXOccurencesPicker.wrapSelectorWheel = false
        binding.editFragmentIcalEditRecur.editRecurUntilXOccurencesPicker.minValue = 1
        binding.editFragmentIcalEditRecur.editRecurUntilXOccurencesPicker.maxValue = 100
        binding.editFragmentIcalEditRecur.editRecurUntilXOccurencesPicker.setOnValueChangedListener { _, _, _ -> updateRRule() }

        binding.editFragmentIcalEditRecur.editRecurOnTheXDayOfMonthNumberPicker.wrapSelectorWheel = false
        binding.editFragmentIcalEditRecur.editRecurOnTheXDayOfMonthNumberPicker.minValue = 1
        binding.editFragmentIcalEditRecur.editRecurOnTheXDayOfMonthNumberPicker.maxValue = 31
        binding.editFragmentIcalEditRecur.editRecurOnTheXDayOfMonthNumberPicker.setOnValueChangedListener { _, _, _ -> updateRRule() }


        binding.editFragmentIcalEditRecur.editRecurDaysMonthsSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    when(position) {
                        0 -> icalEditViewModel.recurrenceMode.value = RECURRENCE_MODE_DAY
                        1 -> icalEditViewModel.recurrenceMode.value = RECURRENCE_MODE_WEEK
                        2 -> icalEditViewModel.recurrenceMode.value = RECURRENCE_MODE_MONTH
                        3 -> icalEditViewModel.recurrenceMode.value = RECURRENCE_MODE_YEAR
                    }
                    icalEditViewModel.updateVisibility()
                    updateRRule()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}    // nothing to do
            }
        binding.editFragmentIcalEditRecur.editRecurDaysMonthsSpinner.setSelection(
            when(icalEditViewModel.recurrenceMode.value) {
                RECURRENCE_MODE_DAY -> 0
                RECURRENCE_MODE_WEEK -> 1
                RECURRENCE_MODE_MONTH -> 2
                RECURRENCE_MODE_YEAR -> 3
                else -> 0
            })

        //pre-set rules if rrule is present
        if(icalEditViewModel.iCalEntity.property.rrule!= null) {

            try {

                val recur = Recur(icalEditViewModel.iCalEntity.property.rrule)

                if(recur.interval == -1 || recur.count == -1)
                    throw Exception("Interval or count not set")

                if(icalEditViewModel.recurrenceMode.value == RECURRENCE_MODE_UNSUPPORTED)
                    throw Exception("Unsupported recurrence mode detected")

                if(recur.until != null)
                    throw Exception("Until value is currently not supported")

                if(recur.experimentalValues.isNotEmpty() || recur.hourList.isNotEmpty() || recur.minuteList.isNotEmpty() || recur.monthList.isNotEmpty() || recur.secondList.isNotEmpty() || recur.setPosList.isNotEmpty() || recur.skip != null || recur.weekNoList.isNotEmpty() || recur.weekStartDay != null || recur.yearDayList.isNotEmpty())
                    throw Exception("Unsupported values detected")

                binding.editFragmentIcalEditRecur.editRecurEveryXNumberPicker.value =
                    recur.interval

                binding.editFragmentIcalEditRecur.editRecurUntilXOccurencesPicker.value =
                    recur.count


                //pre-check the weekday-chips according to the rrule
                if (icalEditViewModel.recurrenceMode.value == RECURRENCE_MODE_WEEK) {
                    if(recur.dayList.size < 1)
                        throw Exception("Recurrence mode Weekly but no weekdays were set")
                    recur.dayList.forEach {
                        if ((isLocalizedWeekstartMonday() && it == WeekDay.MO) || (!isLocalizedWeekstartMonday() && it == WeekDay.SU))
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip0.isChecked = true
                        if ((isLocalizedWeekstartMonday() && it == WeekDay.TU) || (!isLocalizedWeekstartMonday() && it == WeekDay.MO))
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip1.isChecked = true
                        if ((isLocalizedWeekstartMonday() && it == WeekDay.WE) || (!isLocalizedWeekstartMonday() && it == WeekDay.TU))
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip2.isChecked = true
                        if ((isLocalizedWeekstartMonday() && it == WeekDay.TH) || (!isLocalizedWeekstartMonday() && it == WeekDay.WE))
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip3.isChecked = true
                        if ((isLocalizedWeekstartMonday() && it == WeekDay.FR) || (!isLocalizedWeekstartMonday() && it == WeekDay.TH))
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip4.isChecked = true
                        if ((isLocalizedWeekstartMonday() && it == WeekDay.SA) || (!isLocalizedWeekstartMonday() && it == WeekDay.FR))
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip5.isChecked = true
                        if ((isLocalizedWeekstartMonday() && it == WeekDay.SU) || (!isLocalizedWeekstartMonday() && it == WeekDay.SA))
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip6.isChecked = true
                    }
                }

                //pre-select the day of the month according to the rrule
                if (icalEditViewModel.recurrenceMode.value == RECURRENCE_MODE_MONTH) {
                    if(recur.monthDayList.size != 1)
                        throw Exception("Recurrence mode Monthly but no day or multiple days were set")
                    val selectedMonth = Recur(icalEditViewModel.iCalEntity.property.rrule).monthDayList[0]
                    binding.editFragmentIcalEditRecur.editRecurOnTheXDayOfMonthNumberPicker.value = selectedMonth
                }
            } catch (e: Exception) {
                Log.w("LoadRRule", "Failed to preset UI according to provided RRule\n$e")

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.edit_fragment_recur_unknown_rrule_dialog_title))
                    .setMessage(getString(R.string.edit_fragment_recur_unknown_rrule_dialog_message))
                    .setPositiveButton(R.string.ok) { _, _ ->
                        icalEditViewModel.iCalObjectUpdated.value?.rrule = null
                        icalEditViewModel.iCalObjectUpdated.value?.rdate = null
                        icalEditViewModel.iCalObjectUpdated.value?.exdate = null
                        binding.editFragmentIcalEditRecur.editRecurSwitch.isChecked = false
                    }
                    .show()
            }
        }

        binding.icalEditTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {

            override fun onTabSelected(tab: TabLayout.Tab?) {

                when (tab?.position) {
                    TAB_GENERAL -> icalEditViewModel.selectedTab = TAB_GENERAL
                    TAB_PEOPLE_RES -> icalEditViewModel.selectedTab = TAB_PEOPLE_RES
                    TAB_LOC_COMMENTS -> icalEditViewModel.selectedTab = TAB_LOC_COMMENTS
                    TAB_ATTACHMENTS -> icalEditViewModel.selectedTab = TAB_ATTACHMENTS
                    TAB_SUBTASKS -> icalEditViewModel.selectedTab = TAB_SUBTASKS
                    TAB_RECURRING -> icalEditViewModel.selectedTab = TAB_RECURRING
                    TAB_ALARMS -> icalEditViewModel.selectedTab = TAB_ALARMS
                    else -> icalEditViewModel.selectedTab = TAB_GENERAL
                }
                hideKeyboard()
                icalEditViewModel.updateVisibility()

            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // nothing to do
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // nothing to do
            }
        })

        binding.editSummaryEditTextinputfield.addTextChangedListener {
            updateToolbarText()
        }


        icalEditViewModel.savingClicked.observe(viewLifecycleOwner, {
            if (it == true) {

                // do some validation first
                if(!isDataValid())
                    return@observe

                icalEditViewModel.iCalObjectUpdated.value!!.percent =
                    binding.editProgressSlider.value.toInt()
                prefs.edit().putLong(
                    PREFS_LAST_COLLECTION,
                    icalEditViewModel.iCalObjectUpdated.value!!.collectionId
                ).apply()

                icalEditViewModel.update()
            }
        })

        icalEditViewModel.collectionNotFoundError.observe(viewLifecycleOwner, { error ->

            if(!error)
                return@observe

            // show a dialog to inform the user
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle(getString(R.string.edit_dialog_collection_not_found_error_title))
            builder.setMessage(getString(R.string.edit_dialog_collection_not_found_error_message))
            builder.setIcon(R.drawable.ic_error)
            builder.setPositiveButton(R.string.ok) { _, _ ->  }
            builder.show()

        })

        icalEditViewModel.deleteClicked.observe(viewLifecycleOwner, {
            if (it == true) {

                if(icalEditViewModel.iCalObjectUpdated.value?.id == 0L)
                    showDiscardMessage()
                else
                    showDeleteMessage()

            }
        })

        icalEditViewModel.returnIcalObjectId.observe(viewLifecycleOwner, {

            if (it != 0L) {
                // saving is done now, set the notification
                if (icalEditViewModel.iCalObjectUpdated.value!!.due != null && icalEditViewModel.iCalObjectUpdated.value!!.due!! > System.currentTimeMillis())

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        scheduleNotification(
                            context,
                            icalEditViewModel.iCalObjectUpdated.value!!.id,
                            icalEditViewModel.iCalObjectUpdated.value!!.summary
                                ?: "",
                            icalEditViewModel.iCalObjectUpdated.value!!.description
                                ?: "",
                            icalEditViewModel.iCalObjectUpdated.value!!.due!!
                        )
                    } else {
                        Log.i("scheduleNotification", "Due to necessity of PendingIntent.FLAG_IMMUTABLE, the notification functionality can only be used from Build Versions > M (Api-Level 23)")
                    }

                hideKeyboard()


                // show Ad if necessary
                AdManager.showAd(requireActivity())


                // return to list view
                val direction =
                    IcalEditFragmentDirections.actionIcalEditFragmentToIcalListFragment()
                direction.module2show = icalEditViewModel.iCalObjectUpdated.value!!.module
                direction.item2focus = it

                /*  // ALTERNATVE return to view fragment
                val direction = IcalEditFragmentDirections.actionIcalEditFragmentToIcalViewFragment()
                direction.item2show = it
                 */
                this.findNavController().navigate(direction)
            }
            icalEditViewModel.savingClicked.value = false
        })


        icalEditViewModel.iCalObjectUpdated.observe(viewLifecycleOwner) {

            updateToolbarText()

            binding.editProgressPercent.text = String.format("%.0f%%", it.percent?.toFloat() ?: 0F)

            // Set the default value of the priority Chip
            when (it.priority) {
                null -> binding.editPriorityChip.text = priorityItems[0]
                in 0..9 -> binding.editPriorityChip.text =
                    priorityItems[it.priority!!]  // if supported show the priority according to the String Array
                else -> binding.editPriorityChip.text = it.priority.toString()
            }

            // Set the default value of the Status Chip
            when (it.component) {
                Component.VTODO.name -> binding.editStatusChip.text =
                    StatusTodo.getStringResource(requireContext(), it.status) ?: it.status
                Component.VJOURNAL.name -> binding.editStatusChip.text =
                    StatusJournal.getStringResource(requireContext(), it.status) ?: it.status
                else -> binding.editStatusChip.text = it.status
            }       // if unsupported just show whatever is there

            // show the reset dates menu item if it is a to-do
            if(it.module == Module.TODO.name)
                menu?.findItem(R.id.menu_edit_clear_dates)?.isVisible = true

            // if the item has an original Id, the user chose to unlink the recurring instance from the original, the recurring values need to be deleted
            if(it.isRecurLinkedInstance) {
                it.rrule = null
                it.exdate = null
                it.rdate = null
                it.isRecurLinkedInstance = false    // remove the link
            }

            // Set the default value of the Classification Chip
            binding.editClassificationChip.text =
                Classification.getStringResource(requireContext(), it.classification)
                    ?: it.classification       // if unsupported just show whatever is there

            updateRRule()
            icalEditViewModel.updateVisibility()

            // update color for collection if possible
            updateCollectionColor()
        }


        icalEditViewModel.addTimeChecked.observe(viewLifecycleOwner) { addTime ->

            if (icalEditViewModel.iCalObjectUpdated.value == null)     // don't do anything if the object was not initialized yet
                return@observe

            if (addTime) {
                icalEditViewModel.iCalObjectUpdated.value!!.dtstartTimezone = null
                icalEditViewModel.iCalObjectUpdated.value!!.dueTimezone = null
                icalEditViewModel.iCalObjectUpdated.value!!.completedTimezone = null
                //binding.editTimezoneSpinner.setSelection(0)

            } else {
                icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone = ICalObject.TZ_ALLDAY
                icalEditViewModel.iCalObjectUpdated.value?.dueTimezone = ICalObject.TZ_ALLDAY
                icalEditViewModel.iCalObjectUpdated.value?.completedTimezone = ICalObject.TZ_ALLDAY

                // make sure that the time gets reset to 0 (if it was set before)
                icalEditViewModel.iCalObjectUpdated.value?.dtstart?.let {
                    val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), requireTzId(icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone))
                        .withHour(0)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0)
                    icalEditViewModel.iCalObjectUpdated.value?.dtstart = zonedDateTime.toInstant().toEpochMilli()
                }

                icalEditViewModel.iCalObjectUpdated.value?.due?.let {
                    val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), requireTzId(icalEditViewModel.iCalObjectUpdated.value?.dueTimezone))
                        .withHour(0)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0)
                    icalEditViewModel.iCalObjectUpdated.value?.due = zonedDateTime.toInstant().toEpochMilli()
                }

                icalEditViewModel.iCalObjectUpdated.value?.completed?.let {
                    val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), requireTzId(icalEditViewModel.iCalObjectUpdated.value?.completedTimezone))
                        .withHour(0)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0)
                    icalEditViewModel.iCalObjectUpdated.value?.completed = zonedDateTime.toInstant().toEpochMilli()
                }

            }

            // post itself to update UI
            icalEditViewModel.iCalObjectUpdated.postValue(icalEditViewModel.iCalObjectUpdated.value)

            // is this even necessary when the iCalObjectUpadted is posted anyway?
            icalEditViewModel.updateVisibility()                 // Update visibility of Elements on Change of showAll
        }

        icalEditViewModel.addTimezoneJournalChecked.observe(viewLifecycleOwner) { addJournalTimezone ->

            if (icalEditViewModel.iCalObjectUpdated.value == null)     // don't do anything if the object was not initialized yet
                return@observe

            if(addJournalTimezone) {
                if(icalEditViewModel.iCalObjectUpdated.value?.dtstart != null)
                    showTimezonePicker(TAG_PICKER_DTSTART)
            } else {
                icalEditViewModel.iCalObjectUpdated.value!!.dtstartTimezone = null
                icalEditViewModel.iCalObjectUpdated.value!!.dueTimezone = null
                icalEditViewModel.iCalObjectUpdated.value!!.completedTimezone = null
                icalEditViewModel.iCalObjectUpdated.postValue(icalEditViewModel.iCalObjectUpdated.value)
            }
        }


        icalEditViewModel.addTimezoneTodoChecked.observe(viewLifecycleOwner) { addTodoTimezone ->

            if (icalEditViewModel.iCalObjectUpdated.value == null)     // don't do anything if the object was not initialized yet
                return@observe

            if(addTodoTimezone) {
                when {
                    icalEditViewModel.iCalObjectUpdated.value?.dtstart != null -> showTimezonePicker(TAG_PICKER_DTSTART)
                    icalEditViewModel.iCalObjectUpdated.value?.due != null -> showTimezonePicker(TAG_PICKER_DUE)
                    icalEditViewModel.iCalObjectUpdated.value?.completed != null -> showTimezonePicker(TAG_PICKER_COMPLETED)
                }
            } else {
                icalEditViewModel.iCalObjectUpdated.value!!.dtstartTimezone = null
                icalEditViewModel.iCalObjectUpdated.value!!.dueTimezone = null
                icalEditViewModel.iCalObjectUpdated.value!!.completedTimezone = null
                icalEditViewModel.iCalObjectUpdated.postValue(icalEditViewModel.iCalObjectUpdated.value)
            }
        }

        icalEditViewModel.relatedSubtasks.observe(viewLifecycleOwner) {

            if (icalEditViewModel.savingClicked.value == true)    // don't do anything if saving was clicked, saving could interfere here!
                return@observe

            it.forEach { singleSubtask ->
                addSubtasksView(singleSubtask)
            }
        }

        icalEditViewModel.recurrenceChecked.observe(viewLifecycleOwner) {
            updateRRule()
            icalEditViewModel.updateVisibility()
        }


        //TODO: Check if the Sequence was updated in the meantime and notify user!


        icalEditViewModel.iCalEntity.comments?.forEach { singleComment ->
            if(!icalEditViewModel.commentUpdated.contains(singleComment)) {
                icalEditViewModel.commentUpdated.add(singleComment)
                addCommentView(singleComment)
            }
        }

        icalEditViewModel.iCalEntity.attachments?.forEach { singleAttachment ->
            if(!icalEditViewModel.attachmentUpdated.contains(singleAttachment)) {
                icalEditViewModel.attachmentUpdated.add(singleAttachment)
                addAttachmentView(singleAttachment)
            }
        }

        icalEditViewModel.iCalEntity.categories?.forEach { singleCategory ->
            if(!icalEditViewModel.categoryUpdated.contains(singleCategory)) {
                icalEditViewModel.categoryUpdated.add(singleCategory)
                addCategoryChip(singleCategory)
            }
        }

        icalEditViewModel.iCalEntity.attendees?.forEach { singleAttendee ->
            if(!icalEditViewModel.attendeeUpdated.contains(singleAttendee)) {
                icalEditViewModel.attendeeUpdated.add(singleAttendee)
                addAttendeeChip(singleAttendee)
            }
        }

        icalEditViewModel.iCalEntity.resources?.forEach { singleResource ->
            if(!icalEditViewModel.resourceUpdated.contains(singleResource)) {
                icalEditViewModel.resourceUpdated.add(singleResource)
                addResourceChip(singleResource)
            }
        }


        // Set up items to suggest for categories
        icalEditViewModel.allCategories.observe(viewLifecycleOwner, {
            // Create the adapter and set it to the AutoCompleteTextView
            if (icalEditViewModel.allCategories.value != null) {
                val arrayAdapter = ArrayAdapter(
                    application.applicationContext,
                    android.R.layout.simple_list_item_1,
                    icalEditViewModel.allCategories.value!!
                )
                binding.editCategoriesAddAutocomplete.setAdapter(arrayAdapter)
            }
        })

        // Set up items to suggest for resources
        icalEditViewModel.allResources.observe(viewLifecycleOwner, {
            // Create the adapter and set it to the AutoCompleteTextView
            if (icalEditViewModel.allResources.value != null) {
                val arrayAdapter = ArrayAdapter(
                    application.applicationContext,
                    android.R.layout.simple_list_item_1,
                    icalEditViewModel.allResources.value!!
                )
                binding.editResourcesAddAutocomplete.setAdapter(arrayAdapter)
            }
        })

        // initialize allRelatedto
        icalEditViewModel.allRelatedto.observe(viewLifecycleOwner, {

            // if the current item can be found as linkedICalObjectId and the reltype is CHILD, then it must be a child and changing the collection is not allowed
            // also making it recurring is not allowed
            if (icalEditViewModel.iCalObjectUpdated.value?.id != 0L && it?.find { rel -> rel.linkedICalObjectId == icalEditViewModel.iCalObjectUpdated.value?.id && rel.reltype == Reltype.CHILD.name } != null) {
                binding.editCollectionSpinner.isEnabled = false
                binding.editFragmentIcalEditRecur.editRecurSwitch.isEnabled = false
            }
        })

        icalEditViewModel.allCollections.observe(viewLifecycleOwner, {

            if (it.isNullOrEmpty())
                return@observe

            // do not update anything about the collections anymore, as the user might have changed the selection and interference must be avoided!
            if(icalEditViewModel.iCalObjectUpdated.value?.collectionId != null && icalEditViewModel.iCalObjectUpdated.value?.collectionId != icalEditViewModel.iCalEntity.property.collectionId)
                return@observe

            // set up the adapter for the organizer spinner
            val spinner: Spinner = binding.editCollectionSpinner
            val allCollectionNames: MutableList<String> = mutableListOf()
            icalEditViewModel.allCollections.value?.forEach { collection ->
                if(collection.displayName?.isNotEmpty() == true && collection.accountName?.isNotEmpty() == true)
                    allCollectionNames.add(collection.displayName + " (" + collection.accountName + ")")
                else
                    allCollectionNames.add(collection.displayName?: "-")
            }
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                allCollectionNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter

            // set the default selection for the spinner.
            val selectedCollectionPos: Int = if (icalEditViewModel.iCalEntity.property.id == 0L) {
                val lastUsedCollectionId = prefs.getLong(PREFS_LAST_COLLECTION, 1L)
                val lastUsedCollection = icalEditViewModel.allCollections.value?.find { colList -> colList.collectionId == lastUsedCollectionId }
                icalEditViewModel.allCollections.value?.indexOf(lastUsedCollection) ?: 0
            } else {
                icalEditViewModel.allCollections.value?.indexOf(icalEditViewModel.iCalEntity.ICalCollection)  ?: 0
            }
            binding.editCollectionSpinner.setSelection(selectedCollectionPos)

            //as loading the collections might take longer than loading the icalObject, we additionally set the color here
            updateCollectionColor()
        })


        binding.editDtstartCard.setOnClickListener {
            showDatePicker(
                icalEditViewModel.iCalObjectUpdated.value?.dtstart ?: System.currentTimeMillis(),
                icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone,
                TAG_PICKER_DTSTART
            )
        }

        binding.editTaskDatesFragment.editTaskDueCard.setOnClickListener {
            showDatePicker(
                icalEditViewModel.iCalObjectUpdated.value?.due ?: System.currentTimeMillis(),
                icalEditViewModel.iCalObjectUpdated.value?.dueTimezone,
                TAG_PICKER_DUE
            )
        }

        binding.editTaskDatesFragment.editTaskCompletedCard.setOnClickListener {
            showDatePicker(
                icalEditViewModel.iCalObjectUpdated.value?.completed ?: System.currentTimeMillis(),
                icalEditViewModel.iCalObjectUpdated.value?.completedTimezone,
                TAG_PICKER_COMPLETED
            )
        }

        binding.editTaskDatesFragment.editTaskStartedCard.setOnClickListener {
            showDatePicker(
                icalEditViewModel.iCalObjectUpdated.value?.dtstart ?: System.currentTimeMillis(),
                icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone,
                TAG_PICKER_DTSTART
            )
        }

        var restoreProgress = icalEditViewModel.iCalObjectUpdated.value?.percent ?: 0

        binding.editProgressSlider.addOnChangeListener { _, value, _ ->
            icalEditViewModel.iCalObjectUpdated.value?.percent = value.toInt()
            binding.editProgressCheckbox.isChecked = value == 100F
            binding.editProgressPercent.text = String.format("%.0f%%", value)   // takes care of localized representation of percentages (with 0 positions after the comma)
            if (value != 100F)
                restoreProgress = value.toInt()

            val statusBefore = icalEditViewModel.iCalObjectUpdated.value!!.status

            when (value.toInt()) {
                100 -> icalEditViewModel.iCalObjectUpdated.value!!.status =
                    StatusTodo.COMPLETED.name
                in 1..99 -> icalEditViewModel.iCalObjectUpdated.value!!.status =
                    StatusTodo.`IN-PROCESS`.name
                0 -> icalEditViewModel.iCalObjectUpdated.value!!.status =
                    StatusTodo.`NEEDS-ACTION`.name
            }

            // update the status only if it was actually changed, otherwise the performance sucks
            if (icalEditViewModel.iCalObjectUpdated.value!!.status != statusBefore) {
                when (icalEditViewModel.iCalObjectUpdated.value!!.component) {
                    Component.VTODO.name -> binding.editStatusChip.text =
                        StatusTodo.getStringResource(
                            requireContext(),
                            icalEditViewModel.iCalObjectUpdated.value!!.status
                        ) ?: icalEditViewModel.iCalObjectUpdated.value!!.status
                    Component.VJOURNAL.name -> binding.editStatusChip.text =
                        StatusJournal.getStringResource(
                            requireContext(),
                            icalEditViewModel.iCalObjectUpdated.value!!.status
                        ) ?: icalEditViewModel.iCalObjectUpdated.value!!.status
                    else -> binding.editStatusChip.text =
                        icalEditViewModel.iCalObjectUpdated.value!!.status
                }       // if unsupported just show whatever is there
            }
        }

        binding.editProgressCheckbox.setOnCheckedChangeListener { _, checked ->
            val newProgress: Int = if (checked) 100
            else restoreProgress

            binding.editProgressSlider.value =
                newProgress.toFloat()    // This will also trigger saving through the listener!
        }


        // Transform the category input into a chip when the Add-Button is clicked
        binding.editCategoriesAdd.setEndIconOnClickListener {
            addNewCategory()
        }

        // Transform the category input into a chip when the Done button in the keyboard is clicked
        binding.editCategoriesAdd.editText?.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    addNewCategory()
                    true
                }
                else -> false
            }
        }

        binding.editCategoriesAddAutocomplete.setOnItemClickListener { _, _, i, _ ->
            binding.editCategoriesAddAutocomplete.setText(binding.editCategoriesAddAutocomplete.adapter?.getItem(i).toString())
            addNewCategory()
        }


        binding.editResourcesAdd.setEndIconOnClickListener {
            addNewResource()
        }

        // Transform the resource input into a chip when the Done button in the keyboard is clicked
        binding.editResourcesAdd.editText?.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    addNewResource()
                    true
                }
                else -> false
            }
        }

        binding.editResourcesAddAutocomplete.setOnItemClickListener { _, _, i, _ ->
            binding.editResourcesAddAutocomplete.setText(binding.editResourcesAddAutocomplete.adapter?.getItem(i).toString())
            addNewResource()
        }

        binding.editAttendeesAddAutocomplete.setOnItemClickListener { _, _, i, _ ->
            binding.editAttendeesAddAutocomplete.setText(binding.editAttendeesAddAutocomplete.adapter.getItem(i).toString())
            addNewAttendee()
        }

        binding.editAttendeesAdd.setEndIconOnClickListener {
            addNewAttendee()
        }

        // Transform the category input into a chip when the Done button in the keyboard is clicked
        binding.editAttendeesAdd.editText?.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    addNewAttendee()
                    true
                }
                else -> false
            }
        }



        binding.editCommentAdd.setEndIconOnClickListener {
            // Respond to end icon presses
            val newComment = Comment(text = binding.editCommentAdd.editText?.text.toString())
            icalEditViewModel.commentUpdated.add(newComment)    // store the comment for saving
            addCommentView(newComment)      // add the new comment
            binding.editCommentAdd.editText?.text?.clear()  // clear the field

        }


        // Transform the comment input into a view when the Done button in the keyboard is clicked
        binding.editCommentAdd.editText?.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    val newComment =
                        Comment(text = binding.editCommentAdd.editText?.text.toString())
                    icalEditViewModel.commentUpdated.add(newComment)    // store the comment for saving
                    addCommentView(newComment)      // add the new comment
                    binding.editCommentAdd.editText?.text?.clear()  // clear the field
                    true
                }
                else -> false
            }
        }


        binding.editFragmentIcalEditAttachment.buttonAttachmentAdd.setOnClickListener {
            var chooseFile = Intent(Intent.ACTION_GET_CONTENT)
            chooseFile.type = "*/*"
            chooseFile = Intent.createChooser(chooseFile, "Choose a file")
            try {
                filepickerLauncher.launch(chooseFile)
            } catch (e: ActivityNotFoundException) {
                Log.e("chooseFileIntent", "Failed to open filepicker\n$e")
                Toast.makeText(context, "Failed to open filepicker", Toast.LENGTH_LONG).show()
            }
        }


        // don't show the button if the device does not have a camera
        if (!requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))
            binding.editFragmentIcalEditAttachment.buttonAttachmentTakePicture.visibility = View.GONE

        binding.editFragmentIcalEditAttachment.buttonAttachmentTakePicture.setOnClickListener {

            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                try {
                    val storageDir =
                        requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    val file = File.createTempFile("jtx_", ".jpg", storageDir)
                    //Log.d("externalFilesPath", file.absolutePath)

                    photoUri =
                        FileProvider.getUriForFile(requireContext(), AUTHORITY_FILEPROVIDER, file)
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    photoAttachmentLauncher.launch(takePictureIntent)

                } catch (e: ActivityNotFoundException) {
                    Log.e("takePictureIntent", "Failed to open camera\n$e")
                    Toast.makeText(context, "Failed to open camera", Toast.LENGTH_LONG).show()
                } catch (e: IOException) {
                    Log.e("takePictureIntent", "Failed to access storage\n$e")
                    Toast.makeText(context, "Failed to access storage", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.editFragmentIcalEditAttachment.buttonAttachmentAddLink.setOnClickListener {

            val addlinkBindingDialog = FragmentIcalEditAttachmentAddlinkDialogBinding.inflate(inflater)

            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle(R.string.edit_attachment_add_link_dialog)
            builder.setIcon(R.drawable.ic_link)
            builder.setView(addlinkBindingDialog.root)
            builder.setPositiveButton(R.string.save) { _, _ ->

                if(isValidURL(addlinkBindingDialog.editAttachmentAddDialogEdittext.text.toString())) {

                    val uri = Uri.parse(addlinkBindingDialog.editAttachmentAddDialogEdittext.text.toString())

                    if(uri != null) {
                        val filename = uri.lastPathSegment
                        val extension = filename?.substringAfterLast('.', "")

                        val newAttachment = Attachment(
                            //fmttype = mimeType,
                            uri = uri.toString(),
                            filename = uri.toString(),
                            extension = extension,
                            fmttype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        )

                        icalEditViewModel.attachmentUpdated.add(newAttachment)    // store the attachment for saving
                        addAttachmentView(newAttachment)      // add the new attachment
                        return@setPositiveButton
                    }
                }

                // only reached when attachment could not be added
                Toast.makeText(context, "Invalid URL, please provide a valid URL to add a linked attachment.", Toast.LENGTH_LONG).show()

            }

            builder.setNegativeButton(R.string.cancel) { _, _ ->
                // Do nothing, just close the message
            }

            addlinkBindingDialog.editAttachmentAddDialogEdittext.requestFocusFromTouch()
            builder.show()
        }

        binding.editSubtasksAdd.setEndIconOnClickListener {
            // Respond to end icon presses
            val newSubtask =
                ICalObject.createTask(summary = binding.editSubtasksAdd.editText?.text.toString())
            icalEditViewModel.subtaskUpdated.add(newSubtask)    // store the comment for saving
            addSubtasksView(newSubtask)      // add the new comment
            binding.editSubtasksAdd.editText?.text?.clear()  // clear the field

        }


        // Transform the comment input into a view when the Done button in the keyboard is clicked
        binding.editSubtasksAdd.editText?.setOnEditorActionListener { _, actionId, _ ->
            return@setOnEditorActionListener when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    val newSubtask =
                        ICalObject.createTask(summary = binding.editSubtasksAdd.editText?.text.toString())
                    icalEditViewModel.subtaskUpdated.add(newSubtask)    // store the comment for saving
                    addSubtasksView(newSubtask)      // add the new comment
                    binding.editSubtasksAdd.editText?.text?.clear()  // clear the field
                    true
                }
                else -> false
            }
        }


        binding.editStatusChip.setOnClickListener {

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Set status")
                .setItems(statusItems) { _, which ->
                    // Respond to item chosen
                    if (icalEditViewModel.iCalObjectUpdated.value!!.component == Component.VTODO.name) {
                        icalEditViewModel.iCalObjectUpdated.value!!.status =
                            StatusTodo.values().getOrNull(which)!!.name
                        binding.editStatusChip.text = StatusTodo.getStringResource(
                            requireContext(),
                            icalEditViewModel.iCalObjectUpdated.value!!.status
                        )
                    }

                    if (icalEditViewModel.iCalObjectUpdated.value!!.component == Component.VJOURNAL.name) {
                        icalEditViewModel.iCalObjectUpdated.value!!.status =
                            StatusJournal.values().getOrNull(which)!!.name
                        binding.editStatusChip.text = StatusJournal.getStringResource(
                            requireContext(),
                            icalEditViewModel.iCalObjectUpdated.value!!.status
                        )
                    }

                }
                .setIcon(R.drawable.ic_status)
                .show()
        }


        binding.editClassificationChip.setOnClickListener {

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Set classification")
                .setItems(classificationItems) { _, which ->
                    // Respond to item chosen
                    icalEditViewModel.iCalObjectUpdated.value!!.classification =
                        Classification.values().getOrNull(which)!!.name
                    binding.editClassificationChip.text = Classification.getStringResource(
                        requireContext(),
                        icalEditViewModel.iCalObjectUpdated.value!!.classification
                    )    // don't forget to update the UI
                }
                .setIcon(R.drawable.ic_classification)
                .show()
        }


        binding.editPriorityChip.setOnClickListener {

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Set priority")
                .setItems(priorityItems) { _, which ->
                    // Respond to item chosen
                    icalEditViewModel.iCalObjectUpdated.value!!.priority = which
                    binding.editPriorityChip.text =
                        priorityItems[which]     // don't forget to update the UI
                }
                .setIcon(R.drawable.ic_priority)
                .show()
        }


        binding.editUrlEdit.editText?.setOnFocusChangeListener { _, _ ->
            if ((binding.editUrlEdit.editText?.text?.isNotBlank() == true && !isValidURL(binding.editUrlEdit.editText?.text.toString())))
                icalEditViewModel.urlError.value = "Please enter a valid URL"
        }

        binding.editAttendeesAdd.editText?.setOnFocusChangeListener { _, _ ->
            if ((binding.editAttendeesAdd.editText?.text?.isNotBlank() == true && !isValidEmail(
                    binding.editAttendeesAdd.editText?.text.toString()
                ))
            )
                icalEditViewModel.attendeesError.value = "Please enter a valid E-Mail address"
        }

        binding.editBottomBar.setOnMenuItemClickListener { menuItem ->
            when(menuItem.itemId) {
                R.id.menu_edit_bottom_delete -> icalEditViewModel.deleteClicked()
            }
            true
        }


        return binding.root
    }

    override fun onResume() {

        updateToolbarText()

        /*
        icalEditViewModel.isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
         */
        icalEditViewModel.updateVisibility()


        super.onResume()
    }

    private fun updateToolbarText() {
        try {
            val activity = requireActivity() as MainActivity
            val toolbarText = if(icalEditViewModel.iCalEntity.property.id == 0L) {
                when (icalEditViewModel.iCalObjectUpdated.value?.module) {
                    Module.JOURNAL.name -> getString(R.string.toolbar_text_add_journal)
                    Module.NOTE.name -> getString(R.string.toolbar_text_add_note)
                    Module.TODO.name -> getString(R.string.toolbar_text_add_task)
                    else -> ""
                }
            } else {
                when (icalEditViewModel.iCalObjectUpdated.value?.module) {
                    Module.JOURNAL.name -> getString(R.string.toolbar_text_edit_journal)
                    Module.NOTE.name -> getString(R.string.toolbar_text_edit_note)
                    Module.TODO.name -> getString(R.string.toolbar_text_edit_task)
                    else -> ""
                }
            }

            activity.setToolbarTitle(toolbarText, binding.editSummaryEditTextinputfield.text.toString() )
        } catch (e: ClassCastException) {
            Log.d("setToolbarText", "Class cast to MainActivity failed (this is common for tests but doesn't really matter)\n$e")
        }
    }

    /**
     * This function updates the color of the colored edge with the color of the selected collection
     */
    private fun updateCollectionColor() {
        val selectedCollection = icalEditViewModel.allCollections.value?.find { collection ->
            collection.collectionId == icalEditViewModel.iCalObjectUpdated.value?.collectionId
        }
        if(selectedCollection?.color != null) {
            try {
                binding.editColorbar.visibility = View.VISIBLE
                binding.editColorbar.setColorFilter(selectedCollection.color!!)
            } catch (e: IllegalArgumentException) {
                Log.i("Invalid color","Invalid Color cannot be parsed: ${selectedCollection.color}")
                binding.editColorbar.visibility = View.INVISIBLE
            }
        } else {
            binding.editColorbar.visibility = View.INVISIBLE
        }
    }



    private fun showDatePicker(presetValueUTC: Long, timezone: String?, tag: String) {

        val tzId = requireTzId(timezone)
        val updatedUtcDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(presetValueUTC), tzId)

        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.edit_datepicker_dialog_select_date)
                .setSelection(presetValueUTC)
                .build()

        datePicker.addOnPositiveButtonClickListener {
            // Respond to positive button click.
            val selectedUtcDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
            val zonedTimestamp = updatedUtcDateTime.withYear(selectedUtcDateTime.year).withMonth(selectedUtcDateTime.monthValue).withDayOfMonth(selectedUtcDateTime.dayOfMonth).toInstant().toEpochMilli()

            when (tag) {
                TAG_PICKER_DTSTART -> icalEditViewModel.iCalObjectUpdated.value!!.dtstart = zonedTimestamp
                TAG_PICKER_DUE -> icalEditViewModel.iCalObjectUpdated.value!!.due = zonedTimestamp
                TAG_PICKER_COMPLETED -> icalEditViewModel.iCalObjectUpdated.value!!.completed = zonedTimestamp
            }

            // if DTSTART was changed, we additionally update the RRULE
            if(tag == TAG_PICKER_DTSTART)
                updateRRule()

            if (icalEditViewModel.addTimeChecked.value == true)    // let the user set the time only if the time is desired!
                showTimePicker(zonedTimestamp, tzId.id, tag)

            // post itself to update the UI
            icalEditViewModel.iCalObjectUpdated.postValue(icalEditViewModel.iCalObjectUpdated.value)
        }

        datePicker.show(parentFragmentManager, tag)

    }


    private fun showTimePicker(presetValueUTC: Long, timezone: String?, tag: String) {

        val tzId = requireTzId(timezone)
        val presetUtcDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(presetValueUTC), tzId)

        val timePicker =
            MaterialTimePicker.Builder()
                .setHour(presetUtcDateTime.hour)
                .setMinute(presetUtcDateTime.minute)
                .setTitleText(R.string.edit_datepicker_dialog_select_time)
                .build()

        timePicker.addOnPositiveButtonClickListener {

            val zonedTimestamp = presetUtcDateTime.withHour(timePicker.hour).withMinute(timePicker.minute).toInstant().toEpochMilli()

            when (tag) {
                TAG_PICKER_DTSTART -> icalEditViewModel.iCalObjectUpdated.value!!.dtstart = zonedTimestamp
                TAG_PICKER_DUE -> icalEditViewModel.iCalObjectUpdated.value!!.due = zonedTimestamp
                TAG_PICKER_COMPLETED -> icalEditViewModel.iCalObjectUpdated.value!!.completed = zonedTimestamp
            }

            // if DTSTART was changed, we additionally update the RRULE
            if(tag == TAG_PICKER_DTSTART)
                updateRRule()

            // post itself to update the UI
            icalEditViewModel.iCalObjectUpdated.postValue(icalEditViewModel.iCalObjectUpdated.value)

            if (icalEditViewModel.addTimezoneJournalChecked.value == true || icalEditViewModel.addTimezoneTodoChecked.value == true)
                showTimezonePicker(tag)
        }

        timePicker.show(parentFragmentManager, tag)
    }


    private fun showTimezonePicker(tag: String) {

        val spinner = Spinner(requireContext())

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            icalEditViewModel.possibleTimezones
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        when (tag) {
            TAG_PICKER_DUE -> spinner.setSelection(icalEditViewModel.possibleTimezones.indexOf(icalEditViewModel.iCalObjectUpdated.value?.dueTimezone))
            TAG_PICKER_COMPLETED -> spinner.setSelection(icalEditViewModel.possibleTimezones.indexOf(icalEditViewModel.iCalObjectUpdated.value?.completedTimezone))
            TAG_PICKER_DTSTART -> spinner.setSelection(icalEditViewModel.possibleTimezones.indexOf(icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone))
        }


        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Set timezone")
        builder.setIcon(R.drawable.ic_timezone)
        builder.setView(spinner)
        builder.setPositiveButton(R.string.save) { _, _ ->

            val selectedTimezone = icalEditViewModel.possibleTimezones[spinner.selectedItemPosition]

            when (tag) {
                TAG_PICKER_DUE -> {
                    val oldUtcDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(icalEditViewModel.iCalObjectUpdated.value?.due ?: System.currentTimeMillis()), requireTzId(icalEditViewModel.iCalObjectUpdated.value?.dueTimezone))
                    icalEditViewModel.iCalObjectUpdated.value?.dueTimezone = selectedTimezone
                    icalEditViewModel.iCalObjectUpdated.value?.due = oldUtcDateTime.withZoneSameLocal(requireTzId(selectedTimezone)).toInstant().toEpochMilli()
                }
                TAG_PICKER_COMPLETED -> {
                    val oldUtcDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(icalEditViewModel.iCalObjectUpdated.value?.completed ?: System.currentTimeMillis()), requireTzId(icalEditViewModel.iCalObjectUpdated.value?.completedTimezone))
                    icalEditViewModel.iCalObjectUpdated.value!!.completedTimezone = selectedTimezone
                    icalEditViewModel.iCalObjectUpdated.value?.completed = oldUtcDateTime.withZoneSameLocal(requireTzId(selectedTimezone)).toInstant().toEpochMilli()
                }
                TAG_PICKER_DTSTART -> {
                    val oldUtcDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(icalEditViewModel.iCalObjectUpdated.value?.dtstart ?: System.currentTimeMillis()), requireTzId(icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone))
                    icalEditViewModel.iCalObjectUpdated.value!!.dtstartTimezone = selectedTimezone
                    icalEditViewModel.iCalObjectUpdated.value?.dtstart = oldUtcDateTime.withZoneSameLocal(requireTzId(selectedTimezone)).toInstant().toEpochMilli()
                }
            }

            // if times are set but no timezone is set, we set the timezone also for the other date-times
            /*
            if(icalEditViewModel.iCalObjectUpdated.value?.dtstart != null && icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone == null)
                icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone = selectedTimezone
            if(icalEditViewModel.iCalObjectUpdated.value?.due != null && icalEditViewModel.iCalObjectUpdated.value?.dueTimezone == null)
                icalEditViewModel.iCalObjectUpdated.value?.dueTimezone = selectedTimezone
            if(icalEditViewModel.iCalObjectUpdated.value?.completed != null && icalEditViewModel.iCalObjectUpdated.value?.completedTimezone == null)
                icalEditViewModel.iCalObjectUpdated.value?.completedTimezone = selectedTimezone

             */



            // post itself to update the UI
            icalEditViewModel.iCalObjectUpdated.postValue(icalEditViewModel.iCalObjectUpdated.value)
        }

        builder.setNegativeButton(R.string.cancel) { _, _ ->
            // Do nothing, just close the message
        }

        builder.show()
    }

    private fun showDiscardMessage() {

        // show Alert Dialog before the item gets really deleted
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.edit_dialog_sure_to_discard_title))
        builder.setMessage(getString(R.string.edit_dialog_sure_to_discard_message))
        builder.setPositiveButton(R.string.discard) { _, _ ->

            hideKeyboard()
            context?.let { context -> Attachment.scheduleCleanupJob(context) }

            val direction = IcalEditFragmentDirections.actionIcalEditFragmentToIcalListFragment()
            direction.module2show = icalEditViewModel.iCalObjectUpdated.value!!.module
            this.findNavController().navigate(direction)
        }
        builder.setNegativeButton(R.string.cancel) { _, _ ->  }   // Do nothing, just close the message
        builder.show()

    }

    private fun showDeleteMessage() {

        // show Alert Dialog before the item gets really deleted
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.edit_dialog_sure_to_delete_title, icalEditViewModel.iCalObjectUpdated.value?.summary))
        builder.setMessage(getString(R.string.edit_dialog_sure_to_delete_message, icalEditViewModel.iCalObjectUpdated.value?.summary))
        builder.setPositiveButton(R.string.delete) { _, _ ->

            hideKeyboard()

            val summary = icalEditViewModel.iCalObjectUpdated.value?.summary
            icalEditViewModel.delete()
            Toast.makeText(context, getString(R.string.edit_toast_deleted_successfully, summary), Toast.LENGTH_LONG).show()

            context?.let { context -> Attachment.scheduleCleanupJob(context) }

            val direction = IcalEditFragmentDirections.actionIcalEditFragmentToIcalListFragment()
            direction.module2show = icalEditViewModel.iCalObjectUpdated.value!!.module
            this.findNavController().navigate(direction)
        }
        builder.setNegativeButton(R.string.cancel) { _, _ ->  }   // Do nothing, just close the message
        builder.show()
    }


    private fun addCategoryChip(category: Category) {

        if (category.text.isBlank())
            return

        val categoryChip = inflater.inflate(
            R.layout.fragment_ical_edit_categories_chip,
            binding.editCategoriesChipgroup,
            false
        ) as Chip
        categoryChip.text = category.text
        binding.editCategoriesChipgroup.addView(categoryChip)
        //displayedCategoryChips.add(category)

        categoryChip.setOnClickListener {
            // Responds to chip click
        }

        categoryChip.setOnCloseIconClickListener { chip ->
            icalEditViewModel.categoryUpdated.remove(category)  // add the category to the list for categories to be deleted
            chip.visibility = View.GONE
        }

        categoryChip.setOnCheckedChangeListener { _, _ ->
            // Responds to chip checked/unchecked
        }
    }


    private fun addResourceChip(resource: Resource) {

        if (resource.text.isNullOrBlank())
            return

        val resourceChip = inflater.inflate(
            R.layout.fragment_ical_edit_resource_chip,
            binding.editResourcesChipgroup,
            false
        ) as Chip
        resourceChip.text = resource.text
        binding.editResourcesChipgroup.addView(resourceChip)
        //displayedCategoryChips.add(resource)

        resourceChip.setOnClickListener {   }

        resourceChip.setOnCloseIconClickListener { chip ->
            icalEditViewModel.resourceUpdated.remove(resource)  // add the category to the list for categories to be deleted
            chip.visibility = View.GONE
        }

        resourceChip.setOnCheckedChangeListener { _, _ ->   }
    }


    private fun addAttendeeChip(attendee: Attendee) {

        if (attendee.caladdress.isBlank())
            return

        var attendeeRoles: Array<String> = arrayOf()
        Role.values().forEach { attendeeRoles = attendeeRoles.plus(getString(it.stringResource)) }

        val attendeeChip = inflater.inflate(
            R.layout.fragment_ical_edit_attendees_chip,
            binding.editAttendeesChipgroup,
            false
        ) as Chip
        attendeeChip.text = attendee.caladdress
        attendeeChip.chipIcon = ResourcesCompat.getDrawable(
            resources,
            Role.getDrawableResourceByName(attendee.role),
            null
        )
        binding.editAttendeesChipgroup.addView(attendeeChip)


        attendeeChip.setOnClickListener {

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Set attendee role")
                .setItems(attendeeRoles) { _, which ->
                    // Respond to item chosen
                    val curIndex =
                        icalEditViewModel.attendeeUpdated.indexOf(attendee)    // find the attendee in the original list
                    if (curIndex == -1)
                        icalEditViewModel.attendeeUpdated.add(attendee)                   // add the attendee to the list of updated items if it was not there yet
                    else
                        icalEditViewModel.attendeeUpdated[curIndex].role =
                            Role.values().getOrNull(which)?.name      // update the roleparam

                    attendee.role = Role.values().getOrNull(which)?.name
                    attendeeChip.chipIcon = ResourcesCompat.getDrawable(
                        resources, Role.values().getOrNull(which)?.icon
                            ?: R.drawable.ic_attendee_reqparticipant, null
                    )

                }
                .setIcon(R.drawable.ic_attendee)
                .show()
        }

        attendeeChip.setOnCloseIconClickListener { chip ->
            icalEditViewModel.attendeeUpdated.remove(attendee)  // add the category to the list for categories to be deleted
            chip.visibility = View.GONE
        }
    }


    private fun addCommentView(comment: Comment) {

        val bindingComment = FragmentIcalEditCommentBinding.inflate(inflater, container, false)
        bindingComment.editCommentTextview.text = comment.text
        //commentView.edit_comment_textview.text = comment.text
        binding.editCommentsLinearlayout.addView(bindingComment.root)

        // set on Click Listener to open a dialog to update the comment
        bindingComment.root.setOnClickListener {

            // set up the values for the TextInputEditText
            val updatedText = TextInputEditText(requireContext())
            updatedText.inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            updatedText.setText(comment.text)
            updatedText.isSingleLine = false
            updatedText.maxLines = 8
            updatedText.contentDescription = getString(R.string.edit_comment_add_dialog_hint)

            // set up the builder for the AlertDialog
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle(R.string.edit_comment_add_dialog_hint)
            builder.setIcon(R.drawable.ic_comment_add)
            builder.setView(updatedText)


            builder.setPositiveButton(R.string.save) { _, _ ->
                // update the comment
                val updatedComment = comment.copy()
                updatedComment.text = updatedText.text.toString()
                icalEditViewModel.commentUpdated.add(updatedComment)
                bindingComment.editCommentTextview.text = updatedComment.text
            }
            builder.setNegativeButton(R.string.cancel) { _, _ ->
                // Do nothing, just close the message
            }

            builder.setNeutralButton(R.string.delete) { _, _ ->
                icalEditViewModel.commentUpdated.remove(comment)
                bindingComment.root.visibility = View.GONE
            }
            builder.show()
        }
    }


    private fun addAttachmentView(attachment: Attachment) {

        val bindingAttachment =
            FragmentIcalEditAttachmentItemBinding.inflate(inflater, container, false)
        bindingAttachment.editAttachmentItemTextview.text = "${attachment.filename}"

        var thumbUri: Uri? = null

        try {
            val thumbSize = Size(50, 50)
            thumbUri = Uri.parse(attachment.uri)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val thumbBitmap = context?.contentResolver!!.loadThumbnail(thumbUri, thumbSize, null)
                bindingAttachment.editAttachmentItemPictureThumbnail.setImageBitmap(thumbBitmap)
            }


            // the method with the MediaMetadataRetriever might be useful when also PDF-thumbnails should be displayed. But currently this is not working...
            /*
            thumbUri = Uri.parse(attachment.uri)

            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(requireContext(), thumbUri)

            //show a thumbnail if possible
            if(mmr.embeddedPicture != null) {
                val bitmap = BitmapFactory.decodeByteArray(mmr.embeddedPicture, 0, mmr.embeddedPicture!!.size)
                bindingAttachment.editAttachmentPictureThumbnail.setImageBitmap(bitmap)
                bindingAttachment.editAttachmentPictureThumbnail.visibility = View.VISIBLE
            }

            mmr.release()

             */

        } catch (e: IllegalArgumentException) {
            Log.d("MediaMetadataRetriever", "Failed to retrive thumbnail \nUri: $thumbUri\n$e")
        } catch (e: FileNotFoundException) {
            Log.d("FileNotFound", "File with uri ${attachment.uri} not found.\n$e")
        }




        binding.editFragmentIcalEditAttachment.editAttachmentsLinearlayout.addView(bindingAttachment.root)

        // delete the attachment on click on the X
        bindingAttachment.editAttachmentItemDelete.setOnClickListener {
            icalEditViewModel.attachmentUpdated.remove(attachment)
            bindingAttachment.root.visibility = View.GONE
        }
    }


    private fun addSubtasksView(subtask: ICalObject?) {

        if (subtask == null)
            return

        val bindingSubtask = FragmentIcalEditSubtaskBinding.inflate(inflater, container, false)
        bindingSubtask.editSubtaskTextview.text = subtask.summary
        bindingSubtask.editSubtaskProgressSlider.value = subtask.percent?.toFloat() ?: 0F
        bindingSubtask.editSubtaskProgressPercent.text = String.format("%.0f%%", subtask.percent?.toFloat() ?: 0F)   // takes care of localized representation of percentages (with 0 positions after the comma)


        bindingSubtask.editSubtaskProgressCheckbox.isChecked = subtask.percent == 100

        var restoreProgress = subtask.percent

        bindingSubtask.editSubtaskProgressSlider.addOnChangeListener { _, value, _ ->
            //Update the progress in the updated list: try to find the matching uid (the only unique element for now) and then assign the percent
            //Attention, the new subtask must have been inserted before in the list!
            if (icalEditViewModel.subtaskUpdated.find { it.uid == subtask.uid } == null) {
                val changedItem = subtask.copy()
                changedItem.percent = value.toInt()
                icalEditViewModel.subtaskUpdated.add(changedItem)
            } else {
                icalEditViewModel.subtaskUpdated.find { it.uid == subtask.uid }?.percent =
                    value.toInt()
            }

            bindingSubtask.editSubtaskProgressCheckbox.isChecked = value == 100F
            bindingSubtask.editSubtaskProgressPercent.text = String.format("%.0f%%", value)
            if (value != 100F)
                restoreProgress = value.toInt()
        }

        bindingSubtask.editSubtaskProgressCheckbox.setOnCheckedChangeListener { _, checked ->
            val newProgress: Int = if (checked) 100
            else restoreProgress ?: 0

            bindingSubtask.editSubtaskProgressSlider.value =
                newProgress.toFloat()    // This will also trigger saving through the listener!
        }


        binding.editSubtasksLinearlayout.addView(bindingSubtask.root)

        // set on Click Listener to open a dialog to update the comment
        bindingSubtask.root.setOnClickListener {

            // set up the values for the TextInputEditText
            val updatedSummary = TextInputEditText(requireContext())
            updatedSummary.inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            updatedSummary.setText(subtask.summary)
            updatedSummary.isSingleLine = false
            updatedSummary.maxLines = 2

            // set up the builder for the AlertDialog
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Edit subtask")
            builder.setIcon(R.drawable.ic_comment_add)
            builder.setView(updatedSummary)


            builder.setPositiveButton("Save") { _, _ ->

                if (icalEditViewModel.subtaskUpdated.find { it.uid == subtask.uid } == null) {
                    val changedItem = subtask.copy()
                    changedItem.summary = updatedSummary.text.toString()
                    icalEditViewModel.subtaskUpdated.add(changedItem)
                } else {
                    icalEditViewModel.subtaskUpdated.find { it.uid == subtask.uid }?.summary =
                        updatedSummary.text.toString()
                }
                bindingSubtask.editSubtaskTextview.text = updatedSummary.text.toString()

            }
            builder.setNegativeButton("Cancel") { _, _ ->
                // Do nothing, just close the message
            }

            builder.setNeutralButton("Delete") { _, _ ->
                icalEditViewModel.subtaskDeleted.add(subtask)
                bindingSubtask.root.visibility = View.GONE
            }
            builder.show()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_ical_edit, menu)
        this.menu = menu
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.menu_edit_delete -> icalEditViewModel.deleteClicked()
            R.id.menu_edit_save -> icalEditViewModel.savingClicked()
            R.id.menu_edit_clear_dates -> icalEditViewModel.clearDates()
        }
        return super.onOptionsItemSelected(item)
    }


    private fun loadContacts() {

        /*
        Template: https://stackoverflow.com/questions/10117049/get-only-email-address-from-contact-list-android
         */

        val context = activity
        val cr = context!!.contentResolver
        val projection = arrayOf(
            ContactsContract.RawContacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Email.DATA
        )
        val order = ContactsContract.Contacts.DISPLAY_NAME
        val filter = ContactsContract.CommonDataKinds.Email.DATA + " NOT LIKE ''"
        val cur = cr.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            filter,
            null,
            order
        )

        if (cur!!.count > 0) {
            while (cur.moveToNext()) {

                //val name = cur.getString(1)    // according to projection 0 = DISPLAY_NAME, 1 = Email.DATA
                val emlAddr = cur.getString(2)
                //Log.println(Log.INFO, "cursor: ", "$name: $emlAddr")
                allContactsMail.add(emlAddr)
                //allContactsNameAndMail.add("$name ($emlAddr)")
                //allContactsAsAttendee.add(VAttendee(cnparam = name, attendee = emlAddr))

            }
            cur.close()
        }

        // TODO: Here it can be considered that also the cuname in the attendee is filled out based on the contacts entry.
        //val arrayAdapterNameAndMail = ArrayAdapter<String>(application.applicationContext, android.R.layout.simple_list_item_1, allContactsNameAndMail)
        val arrayAdapterNameAndMail = ArrayAdapter(
            application.applicationContext,
            android.R.layout.simple_list_item_1,
            allContactsMail
        )

        binding.editContactAddAutocomplete.setAdapter(arrayAdapterNameAndMail)
        binding.editAttendeesAddAutocomplete.setAdapter(arrayAdapterNameAndMail)


    }


    @RequiresApi(Build.VERSION_CODES.M)      // necessary because of the PendingIntent.FLAG_IMMUTABLE that is only available from M (API-Level 23)
    private fun scheduleNotification(
        context: Context?,
        iCalObjectId: Long,
        title: String,
        text: String,
        due: Long
    ) {

        if (context == null)
            return

        // prepare the args to open the icalViewFragment
        val args: Bundle = Bundle().apply {
            putLong("item2show", iCalObjectId)
        }
        // prepare the intent that is passed to the notification in setContentIntent(...)
        // this will be the intent that is executed when the user clicks on the notification
        val contentIntent = NavDeepLinkBuilder(context)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.navigation)
            .setDestination(R.id.icalViewFragment)
            .setArguments(args)
            .createPendingIntent()

        // this is the notification itself that will be put as an Extra into the notificationIntent
        val notification = NotificationCompat.Builder(context, MainActivity.CHANNEL_REMINDER_DUE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            //.setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
        notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL

        // the notificationIntent that is an Intent of the NotificationPublisher Class
        val notificationIntent = Intent(context, NotificationPublisher::class.java).apply {
            putExtra(NotificationPublisher.NOTIFICATION_ID, iCalObjectId)
            putExtra(NotificationPublisher.NOTIFICATION, notification)
        }

        // the pendingIntent is initiated that is passed on to the alarm manager
        val pendingIntent = PendingIntent.getBroadcast(
                context,
                iCalObjectId.toInt(),
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        // the alarmManager finally takes care, that the pendingIntent is queued to start the notification Intent that on click would start the contentIntent
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.RTC_WAKEUP, due, pendingIntent)

    }


    private fun processFileAttachment(fileUri: Uri?) {

        val filePath = fileUri?.path
        Log.d("fileUri", fileUri.toString())
        Log.d("filePath", filePath.toString())
        Log.d("fileName", fileUri?.lastPathSegment.toString())

        val mimeType = fileUri?.let { returnUri ->
            requireContext().contentResolver.getType(returnUri)
        }

        var filesize: Long? = null
        var filename: String? = null
        var fileextension: String? = null
        fileUri?.let { returnUri ->
            requireContext().contentResolver.query(returnUri, null, null, null, null)
        }?.use { cursor ->
            // Get the column indexes of the data in the Cursor, move to the first row in the Cursor, get the data, and display it.
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            filename = cursor.getString(nameIndex)
            filesize = cursor.getLong(sizeIndex)
            fileextension = "." + filename?.substringAfterLast('.', "")
        }


        if (filePath?.isNotEmpty() == true) {

            try {
                //val newFilePath = "${}/${System.currentTimeMillis()}$fileextension"
                val newFile = File(
                    Attachment.getAttachmentDirectory(requireContext()),
                    "${System.currentTimeMillis()}$fileextension"
                )
                newFile.createNewFile()

                val stream = requireContext().contentResolver.openInputStream(fileUri)
                if (stream != null) {
                    newFile.writeBytes(stream.readBytes())

                    val newAttachment = Attachment(
                        fmttype = mimeType,
                        //uri = "/${Attachment.ATTACHMENT_DIR}/${newFile.name}",
                        uri = FileProvider.getUriForFile(
                            requireContext(),
                            AUTHORITY_FILEPROVIDER,
                            newFile
                        ).toString(),
                        filename = filename,
                        extension = fileextension,
                        filesize = filesize
                    )
                    icalEditViewModel.attachmentUpdated.add(newAttachment)    // store the attachment for saving
                    addAttachmentView(newAttachment)      // add the new attachment
                    stream.close()
                }
            } catch (e: IOException) {
                Log.e("IOException", "Failed to process file\n$e")
            }
        }
    }

    private fun processPhotoAttachment() {

        Log.d("photoUri", "photoUri is now $photoUri")

        if (photoUri != null) {

            val mimeType =
                photoUri?.let { returnUri -> requireContext().contentResolver.getType(returnUri) }

            var filesize: Long? = null
            var filename: String? = null
            var fileextension: String? = null
            photoUri?.let { returnUri ->
                requireContext().contentResolver.query(returnUri, null, null, null, null)
            }?.use { cursor ->
                // Get the column indexes of the data in the Cursor, move to the first row in the Cursor, get the data, and display it.
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                cursor.moveToFirst()
                filename = cursor.getString(nameIndex)
                filesize = cursor.getLong(sizeIndex)
                fileextension = "." + filename?.substringAfterLast('.', "")
            }

            val newAttachment = Attachment(
                fmttype = mimeType,
                uri = photoUri.toString(),
                filename = filename,
                extension = fileextension,
                filesize = filesize
            )
            icalEditViewModel.attachmentUpdated.add(newAttachment)    // store the attachment for saving

            addAttachmentView(newAttachment)      // add the new attachment

            // Scanning the file makes it available in the gallery (currently not working) TODO
            //MediaScannerConnection.scanFile(requireContext(), arrayOf(photoUri.toString()), arrayOf(mimeType), null)

        } else {
            Log.e("REQUEST_IMAGE_CAPTURE", "Failed to process and store picture")
        }
        photoUri = null
    }


    private fun updateRRule() {

        // the RRule might need to be deleted if the switch was deactivated
        if(icalEditViewModel.recurrenceChecked.value == null || icalEditViewModel.recurrenceChecked.value == false) {
            icalEditViewModel.iCalObjectUpdated.value?.rrule = null
            return
        }

        if (icalEditViewModel.iCalEntity.property.module == Module.NOTE.name) {
            Toast.makeText(requireContext(), R.string.edit_recur_toast_notes_cannot_use_recur,Toast.LENGTH_LONG).show()
            return
        } else if(icalEditViewModel.iCalEntity.property.dtstart == null) {
            Toast.makeText(requireContext(), R.string.edit_recur_toast_requires_start_date,Toast.LENGTH_LONG).show()
            return
        }


        val recurBuilder = Recur.Builder()
        when( binding.editFragmentIcalEditRecur.editRecurDaysMonthsSpinner.selectedItemPosition) {
            RECURRENCE_MODE_DAY ->  {
                recurBuilder.frequency(Recur.Frequency.DAILY)
            }
            RECURRENCE_MODE_WEEK -> {
                recurBuilder.frequency(Recur.Frequency.WEEKLY)
                val dayList = WeekDayList()
                if((isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip0.isChecked) ||
                    (!isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip1.isChecked))
                    dayList.add(WeekDay.MO)
                if((isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip1.isChecked) ||
                    (!isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip2.isChecked))
                    dayList.add(WeekDay.TU)
                if((isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip2.isChecked) ||
                    (!isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip3.isChecked))
                    dayList.add(WeekDay.WE)
                if((isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip3.isChecked) ||
                    (!isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip4.isChecked))
                    dayList.add(WeekDay.TH)
                if((isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip4.isChecked) ||
                    (!isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip5.isChecked))
                    dayList.add(WeekDay.FR)
                if((isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip5.isChecked) ||
                    (!isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip6.isChecked))
                    dayList.add(WeekDay.SA)
                if((isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip6.isChecked) ||
                    (!isLocalizedWeekstartMonday() && binding.editFragmentIcalEditRecur.editRecurWeekdayChip0.isChecked))
                    dayList.add(WeekDay.SU)

                // the day of dtstart must be checked and should not be unchecked!
                val zonedDtstart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(icalEditViewModel.iCalObjectUpdated.value?.dtstart ?: System.currentTimeMillis()), requireTzId(icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone))

                when (zonedDtstart.dayOfWeek) {
                    DayOfWeek.MONDAY -> {
                        dayList.add(WeekDay.MO)
                        if(isLocalizedWeekstartMonday()) {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip0.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip0.isEnabled = false
                        } else {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip1.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip1.isEnabled = false
                        }
                    }
                    DayOfWeek.TUESDAY -> {
                        dayList.add(WeekDay.TU)
                        if(isLocalizedWeekstartMonday()) {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip1.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip1.isEnabled = false
                        } else {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip2.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip2.isEnabled = false
                        }
                    }
                    DayOfWeek.WEDNESDAY -> {
                        dayList.add(WeekDay.WE)
                        if(isLocalizedWeekstartMonday()) {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip2.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip2.isEnabled = false
                        } else {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip3.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip3.isEnabled = false
                        }
                    }
                    DayOfWeek.THURSDAY -> {
                        dayList.add(WeekDay.TH)
                        if(isLocalizedWeekstartMonday()) {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip3.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip3.isEnabled = false
                        } else {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip4.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip4.isEnabled = false
                        }
                    }
                    DayOfWeek.FRIDAY -> {
                        dayList.add(WeekDay.FR)
                        if(isLocalizedWeekstartMonday()) {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip4.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip4.isEnabled = false
                        } else {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip5.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip5.isEnabled = false
                        }
                    }
                    DayOfWeek.SATURDAY -> {
                        dayList.add(WeekDay.SA)
                        if(isLocalizedWeekstartMonday()) {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip5.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip5.isEnabled = false
                        } else {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip6.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip6.isEnabled = false
                        }
                    }
                    DayOfWeek.SUNDAY -> {
                        dayList.add(WeekDay.SU)
                        if(isLocalizedWeekstartMonday()) {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip6.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip6.isEnabled = false
                        } else {
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip0.isChecked = true
                            binding.editFragmentIcalEditRecur.editRecurWeekdayChip0.isEnabled = false
                        }
                    }
                    else -> { }
                }

                if(dayList.isNotEmpty())
                    recurBuilder.dayList(dayList)
            }
            RECURRENCE_MODE_MONTH -> {
                recurBuilder.frequency(Recur.Frequency.MONTHLY)
                val monthDayList = NumberList()
                monthDayList.add(binding.editFragmentIcalEditRecur.editRecurOnTheXDayOfMonthNumberPicker.value)
                recurBuilder.monthDayList(monthDayList)
            }
            RECURRENCE_MODE_YEAR -> {
                recurBuilder.frequency(Recur.Frequency.YEARLY)
            }
            else -> return
        }
        recurBuilder.interval(binding.editFragmentIcalEditRecur.editRecurEveryXNumberPicker.value)
        recurBuilder.count(binding.editFragmentIcalEditRecur.editRecurUntilXOccurencesPicker.value)
        val recur = recurBuilder.build()

        Log.d("recur", recur.toString())

        //store calculated rRule
        if(icalEditViewModel.recurrenceChecked.value == true)
            icalEditViewModel.iCalObjectUpdated.value?.rrule = recur.toString()
        else
            icalEditViewModel.iCalObjectUpdated.value?.rrule = null


        // update list
        icalEditViewModel.recurrenceList.clear()

        //UpdateUI
        icalEditViewModel.recurrenceList.addAll(icalEditViewModel.iCalEntity.property.getInstancesFromRrule())

        val lastOccurrenceString = convertLongToFullDateTimeString(icalEditViewModel.recurrenceList.lastOrNull(), icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone)

        var allOccurrencesString = ""
        icalEditViewModel.recurrenceList.forEach {
            allOccurrencesString += convertLongToFullDateTimeString(it, icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone) + "\n"
        }

        var allExceptionsString = ""
        getLongListfromCSVString(icalEditViewModel.iCalObjectUpdated.value?.exdate).forEach {
            allExceptionsString += convertLongToFullDateTimeString(it, icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone) + "\n"
        }

        var allAdditionsString = ""
        getLongListfromCSVString(icalEditViewModel.iCalObjectUpdated.value?.rdate).forEach {
            allAdditionsString += convertLongToFullDateTimeString(it, icalEditViewModel.iCalObjectUpdated.value?.dtstartTimezone) + "\n"
        }

        binding.editFragmentIcalEditRecur.editRecurLastOccurenceItem.text = lastOccurrenceString
        binding.editFragmentIcalEditRecur.editRecurAllOccurencesItems.text = allOccurrencesString
        binding.editFragmentIcalEditRecur.editRecurExceptionItems.text = allExceptionsString
        binding.editFragmentIcalEditRecur.editRecurAdditionsItems.text = allAdditionsString
    }


    private fun addNewCategory() {
        val newCat = Category(text = binding.editCategoriesAdd.editText?.text.toString())
        if (!icalEditViewModel.categoryUpdated.contains(newCat)) {
            icalEditViewModel.categoryUpdated.add(newCat)
            addCategoryChip(newCat)
        }
        binding.editCategoriesAdd.editText?.text?.clear()
    }

    private fun addNewResource() {
        val newRes = Resource(text = binding.editResourcesAdd.editText?.text.toString())
        if(!icalEditViewModel.resourceUpdated.contains(newRes)) {
            icalEditViewModel.resourceUpdated.add(newRes)
            addResourceChip(newRes)
        }
        binding.editResourcesAdd.editText?.text?.clear()
    }

    private fun addNewAttendee() {
        val newAtt = Attendee(caladdress = binding.editAttendeesAdd.editText?.text.toString())
        if ((newAtt.caladdress.isNotEmpty() && !isValidEmail(newAtt.caladdress)))
            icalEditViewModel.attendeesError.value = "Please enter a valid email-address"
        else if (!icalEditViewModel.attendeeUpdated.contains(newAtt)) {
            addAttendeeChip(newAtt)
            icalEditViewModel.attendeeUpdated.add(newAtt)
            binding.editAttendeesAddAutocomplete.text.clear()
        }
    }


    private fun isDataValid(): Boolean {

        var isValid = true
        var validationError = ""

        if(icalEditViewModel.iCalObjectUpdated.value?.summary == null && icalEditViewModel.iCalObjectUpdated.value?.description == null)
            validationError += resources.getString(R.string.edit_validation_errors_summary_or_description_necessary) + "\n"
        if(icalEditViewModel.iCalObjectUpdated.value?.dtstart != null && icalEditViewModel.iCalObjectUpdated.value?.due != null && icalEditViewModel.iCalObjectUpdated.value?.due!! < icalEditViewModel.iCalObjectUpdated.value?.dtstart!!)
            validationError += resources.getString(R.string.edit_validation_errors_dialog_due_date_before_dtstart) + "\n"

        if(binding.editCategoriesAddAutocomplete.text.isNotEmpty())
            validationError += resources.getString(R.string.edit_validation_errors_category_not_confirmed) + "\n"
        if(binding.editAttendeesAddAutocomplete.text.isNotEmpty())
            validationError += resources.getString(R.string.edit_validation_errors_attendee_not_confirmed) + "\n"
        if(binding.editResourcesAddAutocomplete.text?.isNotEmpty() == true)
            validationError += resources.getString(R.string.edit_validation_errors_resource_not_confirmed) + "\n"
        if(binding.editCommentAddEdittext.text?.isNotEmpty() == true)
            validationError += resources.getString(R.string.edit_validation_errors_comment_not_confirmed) + "\n"
        if(binding.editSubtasksAddEdittext.text?.isNotEmpty() == true)
            validationError += resources.getString(R.string.edit_validation_errors_subtask_not_confirmed) + "\n"
/*
        if(binding.editTaskDatesFragment.editDueDateEdittext.text?.isNotEmpty() == true && binding.editTaskDatesFragment.editDueTimeEdittext.text.isNullOrBlank() && binding.editTaskDatesFragment.editTaskAddStartedAndDueTimeSwitch.isActivated)
            validationError += resources.getString(R.string.edit_validation_errors_due_time_not_set) + "\n"
        if(binding.editTaskDatesFragment.editStartedDateEdittext.text?.isNotEmpty() == true && binding.editTaskDatesFragment.editStartedTimeEdittext.text.isNullOrBlank() && binding.editTaskDatesFragment.editTaskAddStartedAndDueTimeSwitch.isActivated)
            validationError += resources.getString(R.string.edit_validation_errors_start_time_not_set) + "\n"

 */
/*        if(binding.editCompletedTimeEdittext?.text.isNullOrBlank() && binding.editCompletedAddtimeSwitch?.isActivated == false)
            validationError += resources.getString(R.string.edit_validation_errors_completed_time_not_set) + "\n"
 */

        if(validationError.isNotEmpty()) {
            isValid = false

            validationError = resources.getString(R.string.edit_validation_errors_detected) + "\n\n" + validationError

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.edit_validation_errors_dialog_header)
                .setMessage(validationError)
                .setPositiveButton(R.string.ok) { _, _ ->   }
                .show()
        }

        return isValid

    }




    /**
     * This function makes sure that the soft keyboard gets closed
     */
    private fun hideKeyboard() {

        val imm =
            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

}
