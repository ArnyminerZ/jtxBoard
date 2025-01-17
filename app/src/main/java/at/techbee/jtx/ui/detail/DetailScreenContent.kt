/*
 * Copyright (c) Techbee e.U.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.techbee.jtx.ui.detail

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.jtx.R
import at.techbee.jtx.database.Component
import at.techbee.jtx.database.ICalCollection
import at.techbee.jtx.database.ICalCollection.Factory.LOCAL_ACCOUNT_TYPE
import at.techbee.jtx.database.ICalObject
import at.techbee.jtx.database.Module
import at.techbee.jtx.database.properties.*
import at.techbee.jtx.database.relations.ICalEntity
import at.techbee.jtx.database.views.ICal4List
import at.techbee.jtx.flavored.BillingManager
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_ALARMS
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_ATTACHMENTS
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_ATTENDEES
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_COMMENTS
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_CONTACT
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_LOCATION
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_RECURRENCE
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_RESOURCES
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_SUBNOTES
import at.techbee.jtx.ui.detail.DetailSettings.Companion.ENABLE_URL
import at.techbee.jtx.ui.reusable.dialogs.ColorPickerDialog
import at.techbee.jtx.ui.reusable.dialogs.UnsavedChangesDialog
import at.techbee.jtx.ui.reusable.elements.CollectionsSpinner
import at.techbee.jtx.ui.reusable.elements.ColoredEdge
import at.techbee.jtx.ui.reusable.elements.ProgressElement
import at.techbee.jtx.ui.settings.DropdownSettingOption
import at.techbee.jtx.ui.settings.SettingsStateHolder
import at.techbee.jtx.util.DateTimeUtils
import com.arnyminerz.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import org.apache.commons.lang3.StringUtils
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreenContent(
    iCalEntity: State<ICalEntity?>,
    isEditMode: MutableState<Boolean>,
    changeState: MutableState<DetailViewModel.DetailChangeState>,
    subtasks: State<List<ICal4List>>,
    subnotes: State<List<ICal4List>>,
    isChild: Boolean,
    allWriteableCollections: List<ICalCollection>,
    allCategories: List<String>,
    allResources: List<String>,
    detailSettings: DetailSettings,
    icalObjectIdList: List<Long>,
    sliderIncrement: Int,
    modifier: Modifier = Modifier,
    player: MediaPlayer?,
    goBackRequested: MutableState<Boolean>,    // Workaround to also go Back from Top menu
    saveICalObject: (changedICalObject: ICalObject, changedCategories: List<Category>, changedComments: List<Comment>, changedAttendees: List<Attendee>, changedResources: List<Resource>, changedAttachments: List<Attachment>, changedAlarms: List<Alarm>) -> Unit,
    deleteICalObject: () -> Unit,
    onProgressChanged: (itemId: Long, newPercent: Int, isLinkedRecurringInstance: Boolean) -> Unit,
    onMoveToNewCollection: (icalObject: ICalObject, newCollection: ICalCollection) -> Unit,
    onSubEntryAdded: (icalObject: ICalObject, attachment: Attachment?) -> Unit,
    onSubEntryDeleted: (icalObjectId: Long) -> Unit,
    onSubEntryUpdated: (icalObjectId: Long, newText: String) -> Unit,
    goToDetail: (itemId: Long, editMode: Boolean, list: List<Long>) -> Unit,
    goBack: () -> Unit
) {

    val context = LocalContext.current
    val localInspectionMode = LocalInspectionMode.current

    val autoAlarmSetting by remember {
        if(!localInspectionMode)
            SettingsStateHolder(context).settingAutoAlarm
        else
            mutableStateOf(false)
    }

    var timeout by remember { mutableStateOf(false) }
    LaunchedEffect(timeout, iCalEntity.value) {
        if (iCalEntity.value == null && !timeout) {
            delay((1).seconds)
            timeout = true
        }
    }

    // item was not loaded yet or was deleted in the background
    if (iCalEntity.value == null && timeout) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(stringResource(id = R.string.sorry), style = MaterialTheme.typography.displayMedium)
            Text(stringResource(id = R.string.details_entry_could_not_be_loaded), textAlign = TextAlign.Center)
            Button(onClick = { goBack() }) {
                Text(stringResource(id = R.string.back))
            }
        }
        return
    } else if(iCalEntity.value == null && !timeout) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var color by rememberSaveable { mutableStateOf(iCalEntity.value?.property?.color) }
    var summary by rememberSaveable { mutableStateOf(iCalEntity.value?.property?.summary ?: "") }
    var description by remember {
        mutableStateOf(TextFieldValue(iCalEntity.value?.property?.description ?: ""))
    }

    val isProPurchased = BillingManager.getInstance().isProPurchased.observeAsState(true)
    val allPossibleCollections = allWriteableCollections.filter {
            it.accountType == LOCAL_ACCOUNT_TYPE || isProPurchased.value            // filter remote collections if pro was not purchased
    }

    val icalObject by rememberSaveable {
        mutableStateOf(
            iCalEntity.value?.property ?: ICalObject()
        )
    }

    // make sure the eTag, flags, scheduleTag and fileName gets updated in the background if the sync is triggered, so that another sync won't overwrite the changes!
    iCalEntity.value?.property?.eTag?.let { icalObject.eTag = it }
    iCalEntity.value?.property?.flags?.let { icalObject.flags = it }
    iCalEntity.value?.property?.scheduleTag?.let { icalObject.scheduleTag = it }
    iCalEntity.value?.property?.fileName?.let { icalObject.fileName = it }

    val categories =
        rememberSaveable { mutableStateOf(iCalEntity.value?.categories ?: emptyList()) }
    val resources = rememberSaveable { mutableStateOf(iCalEntity.value?.resources ?: emptyList()) }
    val attendees = rememberSaveable { mutableStateOf(iCalEntity.value?.attendees ?: emptyList()) }
    val comments = rememberSaveable { mutableStateOf(iCalEntity.value?.comments ?: emptyList()) }
    val attachments =
        rememberSaveable { mutableStateOf(iCalEntity.value?.attachments ?: emptyList()) }
    val alarms = rememberSaveable { mutableStateOf(iCalEntity.value?.alarms ?: emptyList()) }

    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }
    var showAllOptions by rememberSaveable { mutableStateOf(false) }


    val previousIsEditModeState = rememberSaveable { mutableStateOf(isEditMode.value) }
    if (previousIsEditModeState.value && !isEditMode.value) {  //changed from edit to view mode
        saveICalObject(
            icalObject,
            categories.value,
            comments.value,
            attendees.value,
            resources.value,
            attachments.value,
            alarms.value
        )
    }
    previousIsEditModeState.value = isEditMode.value


    // save 10 seconds after changed, then reset value
    if (changeState.value == DetailViewModel.DetailChangeState.CHANGEUNSAVED && detailSettings.switchSetting[DetailSettings.ENABLE_AUTOSAVE] != false) {
        LaunchedEffect(changeState) {
            delay((10).seconds.inWholeMilliseconds)
            saveICalObject(
                icalObject,
                categories.value,
                comments.value,
                attendees.value,
                resources.value,
                attachments.value,
                alarms.value
            )
        }
    }

    fun processGoBack() {
        if(isEditMode.value && icalObject.summary.isNullOrEmpty() && icalObject.description.isNullOrEmpty())
            deleteICalObject()
        else if(changeState.value == DetailViewModel.DetailChangeState.CHANGEUNSAVED)
            showUnsavedChangesDialog = true
        else
            goBack()
        goBackRequested.value = false
    }

    /**
     * Updates the alarms when the dates get changed
     */
    fun updateAlarms() {
        alarms.value.forEach { alarm ->
            if(alarm.triggerRelativeDuration.isNullOrEmpty())
                return@forEach

            val dur = try { Duration.parse(alarm.triggerRelativeDuration!!) } catch (e: IllegalArgumentException) { return@forEach }
            if(alarm.triggerRelativeTo == AlarmRelativeTo.END.name) {
                alarm.triggerTime = icalObject.due!! + dur.inWholeMilliseconds
                alarm.triggerTimezone = icalObject.dueTimezone
            } else {
                alarm.triggerTime = icalObject.dtstart!! + dur.inWholeMilliseconds
                alarm.triggerTimezone = icalObject.dtstartTimezone
            }
        }

        //handle autoAlarm
        val autoAlarm = if(autoAlarmSetting == DropdownSettingOption.AUTO_ALARM_ON_DUE && icalObject.due != null) {
            Alarm.createDisplayAlarm(
                dur = (0).minutes,
                alarmRelativeTo = AlarmRelativeTo.END,
                referenceDate = icalObject.due!!,
                referenceTimezone = icalObject.dueTimezone
            )
        } else if(autoAlarmSetting == DropdownSettingOption.AUTO_ALARM_ON_START && icalObject.dtstart != null) {
            Alarm.createDisplayAlarm(
                dur = (0).minutes,
                alarmRelativeTo = null,
                referenceDate = icalObject.dtstart!!,
                referenceTimezone = icalObject.dtstartTimezone
            )
        } else null

        if(autoAlarm != null && alarms.value.none { alarm -> alarm.triggerRelativeDuration == autoAlarm.triggerRelativeDuration && alarm.triggerRelativeTo == autoAlarm.triggerRelativeTo })
            alarms.value = alarms.value.plus(autoAlarm)
    }

    if(goBackRequested.value)
        processGoBack()

    BackHandler {
        processGoBack()
    }

    if(showUnsavedChangesDialog) {
        UnsavedChangesDialog(
            onSave = {
                showUnsavedChangesDialog = false
                saveICalObject(
                    icalObject,
                    categories.value,
                    comments.value,
                    attendees.value,
                    resources.value,
                    attachments.value,
                    alarms.value
                )
                goBack()
            },
            onDiscard = {
                showUnsavedChangesDialog = false
                goBack()
            }
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = color,
            onColorChanged = { newColor ->
                color = newColor
                icalObject.color = newColor
                changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
            },
            onDismiss = {
                showColorPicker = false
            }
        )
    }

    Box(modifier = modifier.verticalScroll(rememberScrollState())) {

        ColoredEdge(color, iCalEntity.value?.ICalCollection?.color)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            AnimatedVisibility(!isEditMode.value || isChild) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ElevatedCard(
                        modifier = Modifier.weight(1f)
                    ) {

                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Folder, stringResource(id = R.string.collection))
                            Text(iCalEntity.value?.ICalCollection?.displayName + iCalEntity.value?.ICalCollection?.accountName?.let { " (" + it + ")" })
                        }
                    }
                }
            }

            AnimatedVisibility(isEditMode.value && !isChild) {

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        CollectionsSpinner(
                            collections = allPossibleCollections,
                            preselected = iCalEntity.value?.ICalCollection ?: allPossibleCollections.first(),
                            includeReadOnly = false,
                            includeVJOURNAL = if (iCalEntity.value?.property?.component == Component.VJOURNAL.name || subnotes.value.isNotEmpty()) true else null,
                            includeVTODO = if (iCalEntity.value?.property?.component == Component.VTODO.name || subtasks.value.isNotEmpty()) true else null,
                            onSelectionChanged = { newCollection ->
                                if (icalObject.collectionId != newCollection.collectionId) {
                                    saveICalObject(
                                        icalObject,
                                        categories.value,
                                        comments.value,
                                        attendees.value,
                                        resources.value,
                                        attachments.value,
                                        alarms.value
                                    )
                                    onMoveToNewCollection(icalObject, newCollection)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                        )
                        IconButton(onClick = { showColorPicker = true }) {
                            Icon(Icons.Outlined.ColorLens, stringResource(id = R.string.color))
                        }
                    }
                }
            }

            DetailsCardDates(
                icalObject = icalObject,
                isEditMode = isEditMode.value,
                onDtstartChanged = { datetime, timezone ->
                    icalObject.dtstart = datetime
                    icalObject.dtstartTimezone = timezone
                    updateAlarms()
                    changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                },
                onDueChanged = { datetime, timezone ->
                    icalObject.due = datetime
                    icalObject.dueTimezone = timezone
                    updateAlarms()
                    changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                },
                onCompletedChanged = { datetime, timezone ->
                    icalObject.completed = datetime
                    icalObject.completedTimezone = timezone
                    changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                },
            )

            AnimatedVisibility(!isEditMode.value) {
                SelectionContainer {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        if (summary.isNotBlank())
                            Text(
                                summary.trim(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content)
                                //fontWeight = FontWeight.Bold
                            )

                        if (description.text.isNotBlank()) {
                            if(detailSettings.switchSetting[DetailSettings.ENABLE_MARKDOWN] != false)
                                MarkdownText(
                                    markdown = description.text.trim(),
                                    modifier = Modifier.padding(8.dp),
                                    bodyStyle = TextStyle(textDirection = TextDirection.Content)
                                )
                            else
                                Text(
                                    text = description.text.trim(),
                                    modifier = Modifier.padding(8.dp),
                                    style = TextStyle(textDirection = TextDirection.Content)
                                )
                        }
                    }
                }
            }

            AnimatedVisibility(isEditMode.value) {

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    OutlinedTextField(
                        value = summary,
                        onValueChange = {
                            summary = it
                            icalObject.summary = it.ifEmpty { null }
                            changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                        },
                        label = { Text(stringResource(id = R.string.summary)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        textStyle = TextStyle(textDirection = TextDirection.Content)
                        )

                    OutlinedTextField(
                        value = description,
                        onValueChange = {

                            val enteredCharIndex = StringUtils.indexOfDifference(it.text, description.text)
                            val enteredCharIsReturn =
                                enteredCharIndex >=0
                                        && it.text.substring(enteredCharIndex).startsWith(System.lineSeparator())
                                        && it.text.length > description.text.length  // excludes backspace!

                            val before = it.getTextBeforeSelection(Int.MAX_VALUE)
                            val after = if(it.selection.start < it.annotatedString.lastIndex) it.annotatedString.subSequence(it.selection.start, it.annotatedString.lastIndex) else AnnotatedString("")
                            val lines =  before.split(System.lineSeparator())
                            val previous = if(lines.lastIndex > 1) lines[lines.lastIndex-1] else before
                            val containsHyphenBullet =  previous.contains(Regex("^[-]\\s.*"))
                            val containsStarBullet =  previous.contains(Regex("^[*]\\s.*"))

                            description = if(description.text != it.text && (containsHyphenBullet || containsStarBullet) && enteredCharIsReturn)
                                TextFieldValue(
                                    annotatedString = before
                                        .plus(AnnotatedString(if(containsHyphenBullet) "- " else if(containsStarBullet) "* " else ""))
                                        .plus(after),
                                    selection = TextRange(it.selection.start+2)
                                )
                            else
                                it
                            icalObject.description = it.text.ifEmpty { null }
                            changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                        },
                        label = { Text(stringResource(id = R.string.description)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        textStyle = TextStyle(textDirection = TextDirection.Content)
                    )
                }
            }

            if (icalObject.module == Module.TODO.name) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    ProgressElement(
                        iCalObjectId = icalObject.id,
                        progress = icalObject.percent,
                        isReadOnly = iCalEntity.value?.ICalCollection?.readonly == true,
                        isLinkedRecurringInstance = icalObject.isRecurLinkedInstance,
                        sliderIncrement = sliderIncrement,
                        onProgressChanged = { itemId, newPercent, isLinked ->
                            icalObject.percent = newPercent
                            onProgressChanged(itemId, newPercent, isLinked)
                            changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                        },
                        showProgressLabel = true,
                        showSlider = true,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }


            DetailsCardStatusClassificationPriority(
                icalObject = icalObject,
                isEditMode = isEditMode.value,
                onStatusChanged = { newStatus ->
                    icalObject.status = newStatus
                    changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                },
                onClassificationChanged = { newClassification ->
                    icalObject.classification = newClassification
                    changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                },
                onPriorityChanged = { newPriority ->
                    icalObject.priority = newPriority
                    changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                },
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(categories.value.isNotEmpty() || (isEditMode.value && (detailSettings.switchSetting[DetailSettings.ENABLE_CATEGORIES]?:true || showAllOptions))) {
                DetailsCardCategories(
                    initialCategories = categories.value,
                    isEditMode = isEditMode.value,
                    onCategoriesUpdated = { newCategories ->
                        categories.value = newCategories
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    },
                    allCategories = allCategories,
                )
            }

            AnimatedVisibility(subtasks.value.isNotEmpty() || (isEditMode.value && iCalEntity.value?.ICalCollection?.supportsVTODO == true && (detailSettings.switchSetting[DetailSettings.ENABLE_SUBTASKS]?: true || showAllOptions))) {
                DetailsCardSubtasks(
                    subtasks = subtasks.value,
                    isEditMode = isEditMode,
                    sliderIncrement = sliderIncrement,
                    onProgressChanged = { itemId, newPercent, isLinkedRecurringInstance ->
                        onProgressChanged(itemId, newPercent, isLinkedRecurringInstance)
                    },
                    onSubtaskAdded = { subtask -> onSubEntryAdded(subtask, null) },
                    onSubtaskUpdated = { icalObjectId, newText ->
                        onSubEntryUpdated(
                            icalObjectId,
                            newText
                        )
                    },
                    onSubtaskDeleted = { icalObjectId -> onSubEntryDeleted(icalObjectId) },
                    goToDetail = goToDetail
                )
            }

            AnimatedVisibility(subnotes.value.isNotEmpty() || (isEditMode.value && iCalEntity.value?.ICalCollection?.supportsVJOURNAL == true && (detailSettings.switchSetting[ENABLE_SUBNOTES]?: false || showAllOptions))) {
                DetailsCardSubnotes(
                    subnotes = subnotes.value,
                    isEditMode = isEditMode,
                    onSubnoteAdded = { subnote, attachment ->
                        onSubEntryAdded(
                            subnote,
                            attachment
                        )
                    },
                    onSubnoteUpdated = { icalObjectId, newText ->
                        onSubEntryUpdated(
                            icalObjectId,
                            newText
                        )
                    },
                    onSubnoteDeleted = { icalObjectId -> onSubEntryDeleted(icalObjectId) },
                    player = player,
                    goToDetail = goToDetail
                )
            }

            AnimatedVisibility(resources.value.isNotEmpty() || (isEditMode.value && (detailSettings.switchSetting[ENABLE_RESOURCES]?:false || showAllOptions))) {
                DetailsCardResources(
                    initialResources = resources.value,
                    isEditMode = isEditMode.value,
                    onResourcesUpdated = { newResources ->
                        resources.value = newResources
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    },
                    allResources = allResources,
                )
            }

            AnimatedVisibility(attendees.value.isNotEmpty() || (isEditMode.value && (detailSettings.switchSetting[ENABLE_ATTENDEES]?:false || showAllOptions))) {
                DetailsCardAttendees(
                    initialAttendees = attendees.value,
                    isEditMode = isEditMode.value,
                    onAttendeesUpdated = { newAttendees ->
                        attendees.value = newAttendees
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    }
                )
            }

            AnimatedVisibility(icalObject.contact?.isNotBlank() == true || (isEditMode.value && (detailSettings.switchSetting[ENABLE_CONTACT]?:false || showAllOptions))) {
                DetailsCardContact(
                    initialContact = icalObject.contact ?: "",
                    isEditMode = isEditMode.value,
                    onContactUpdated = { newContact ->
                        icalObject.contact = newContact.ifEmpty { null }
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    },
                )
            }

            AnimatedVisibility(icalObject.url?.isNotEmpty() == true || (isEditMode.value && (detailSettings.switchSetting[ENABLE_URL]?:false || showAllOptions))) {
                DetailsCardUrl(
                    initialUrl = icalObject.url ?: "",
                    isEditMode = isEditMode.value,
                    onUrlUpdated = { newUrl ->
                        icalObject.url = newUrl.ifEmpty { null }
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    },
                )
            }

            AnimatedVisibility((icalObject.location?.isNotEmpty() == true || (icalObject.geoLat != null && icalObject.geoLong != null)) || (isEditMode.value && (detailSettings.switchSetting[ENABLE_LOCATION]?:false || showAllOptions))) {
                DetailsCardLocation(
                    initialLocation = icalObject.location,
                    initialGeoLat = icalObject.geoLat,
                    initialGeoLong = icalObject.geoLong,
                    isEditMode = isEditMode.value,
                    onLocationUpdated = { newLocation, newGeoLat, newGeoLong ->
                        icalObject.geoLat = newGeoLat
                        icalObject.geoLong = newGeoLong
                        icalObject.location = newLocation.ifEmpty { null }
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    },
                )
            }

            AnimatedVisibility(comments.value.isNotEmpty() || (isEditMode.value && (detailSettings.switchSetting[ENABLE_COMMENTS]?:false || showAllOptions))) {
                DetailsCardComments(
                    initialComments = comments.value,
                    isEditMode = isEditMode.value,
                    onCommentsUpdated = { newComments ->
                        comments.value = newComments
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    }
                )
            }

            AnimatedVisibility(attachments.value.isNotEmpty() || (isEditMode.value && (detailSettings.switchSetting[ENABLE_ATTACHMENTS]?:false || showAllOptions))) {
                DetailsCardAttachments(
                    initialAttachments = attachments.value,
                    isEditMode = isEditMode.value,
                    isRemoteCollection = iCalEntity.value?.ICalCollection?.accountType != LOCAL_ACCOUNT_TYPE,
                    onAttachmentsUpdated = { newAttachments ->
                        attachments.value = newAttachments
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    }
                )
            }

            AnimatedVisibility(alarms.value.isNotEmpty() || (isEditMode.value && (detailSettings.switchSetting[ENABLE_ALARMS]?:false || (showAllOptions && icalObject.module == Module.TODO.name)))) {
                DetailsCardAlarms(
                    alarms = alarms,
                    icalObject = icalObject,
                    isEditMode = isEditMode.value,
                    onAlarmsUpdated = { newAlarms ->
                        alarms.value = newAlarms
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    })
            }

            AnimatedVisibility(icalObject.rrule != null
                    || icalObject.isRecurLinkedInstance
                    || icalObject.recurOriginalIcalObjectId != null
                    || (isEditMode.value && (detailSettings.switchSetting[ENABLE_RECURRENCE]?:false || (showAllOptions && icalObject.module != Module.NOTE.name)))
            ) {   // only Todos have recur!
                DetailsCardRecur(
                    icalObject = icalObject,
                    isEditMode = isEditMode.value,
                    onRecurUpdated = { updatedRRule ->
                        icalObject.rrule = updatedRRule?.toString()
                        changeState.value = DetailViewModel.DetailChangeState.CHANGEUNSAVED
                    },
                    goToDetail = goToDetail
                )
            }

            AnimatedVisibility(isEditMode.value && !showAllOptions) {
                TextButton(
                    onClick = { showAllOptions = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.details_show_all_options))
                }
            }

            AnimatedVisibility(!isEditMode.value) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)) {
                    Text(
                        stringResource(id = R.string.view_created_text, DateTimeUtils.convertLongToFullDateTimeString(icalObject.created, null)),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic
                    )
                    Text(
                        stringResource(id = R.string.view_last_modified_text, DateTimeUtils.convertLongToFullDateTimeString(icalObject.lastModified, null)),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            AnimatedVisibility(!isEditMode.value) {
                val curIndex = icalObjectIdList.indexOf(iCalEntity.value?.property?.id?: 0)
                if(icalObjectIdList.size > 1 && curIndex >=0) {
                    Row(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        if (curIndex > 0) {
                            IconButton(onClick = {
                                goToDetail(
                                    icalObjectIdList[curIndex-1],
                                    false,
                                    icalObjectIdList
                                )
                            }) {
                                Icon(Icons.Outlined.NavigateBefore, stringResource(id = R.string.previous))
                            }
                        } else { Spacer(modifier = Modifier.size(48.dp)) }
                        Text(text = "${icalObjectIdList.indexOf(iCalEntity.value?.property?.id ?: 0) + 1}/${icalObjectIdList.size}")
                        if(curIndex != icalObjectIdList.lastIndex) {
                            IconButton(onClick = {
                                goToDetail(
                                    icalObjectIdList[curIndex+1],
                                    false,
                                    icalObjectIdList
                                )
                            }) {
                                Icon(Icons.Outlined.NavigateNext, stringResource(id = R.string.next))
                            }
                        } else { Spacer(modifier = Modifier.size(48.dp)) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DetailScreenContent_JOURNAL() {
    MaterialTheme {
        val entity = ICalEntity().apply {
            this.property = ICalObject.createJournal("MySummary")
            //this.property.dtstart = System.currentTimeMillis()
        }
        entity.property.description = "Hello World, this \nis my description."
        entity.property.contact = "John Doe, +1 555 5545"
        entity.categories = listOf(
            Category(1, 1, "MyCategory1", null, null),
            Category(2, 1, "My Dog likes Cats", null, null),
            Category(3, 1, "This is a very long category", null, null),
        )

        val prefs: SharedPreferences = LocalContext.current.getSharedPreferences(
            DetailViewModel.PREFS_DETAIL_JOURNALS,
            Context.MODE_PRIVATE
        )
        val detailSettings = DetailSettings(prefs)

        DetailScreenContent(
            iCalEntity = remember { mutableStateOf(entity) },
            isEditMode = remember { mutableStateOf(false) },
            changeState = remember { mutableStateOf(DetailViewModel.DetailChangeState.CHANGEUNSAVED) },
            subtasks = remember { mutableStateOf(emptyList()) },
            subnotes = remember { mutableStateOf(emptyList()) },
            isChild = false,
            player = null,
            sliderIncrement = 10,
            goBackRequested = remember { mutableStateOf(false) },
            allWriteableCollections = listOf(ICalCollection.createLocalCollection(LocalContext.current)),
            allCategories = emptyList(),
            allResources = emptyList(),
            detailSettings = detailSettings,
            icalObjectIdList = emptyList(),
            saveICalObject = { _, _, _, _, _, _, _ -> },
            deleteICalObject = { },
            onProgressChanged = { _, _, _ -> },
            onMoveToNewCollection = { _, _ -> },
            onSubEntryAdded = { _, _ -> },
            onSubEntryDeleted = { },
            onSubEntryUpdated = { _, _ -> },
            goToDetail = { _, _, _ -> },
            goBack = { }
        )
    }
}


@Preview(showBackground = true)
@Composable
fun DetailScreenContent_TODO_editInitially() {
    MaterialTheme {
        val entity = ICalEntity().apply {
            this.property = ICalObject.createTask("MySummary")
        }
        entity.property.description = "Hello World, this \nis my description."
        entity.property.contact = "John Doe, +1 555 5545"

        val prefs: SharedPreferences = LocalContext.current.getSharedPreferences(
            DetailViewModel.PREFS_DETAIL_TODOS,
            Context.MODE_PRIVATE
        )
        val detailSettings = DetailSettings(prefs)

        DetailScreenContent(
            iCalEntity = remember { mutableStateOf(entity) },
            isEditMode = remember { mutableStateOf(true) },
            changeState = remember { mutableStateOf(DetailViewModel.DetailChangeState.CHANGESAVING) },
            subtasks = remember { mutableStateOf(emptyList()) },
            subnotes = remember { mutableStateOf(emptyList()) },
            isChild = false,
            player = null,
            goBackRequested = remember { mutableStateOf(false) },
            allWriteableCollections = listOf(ICalCollection.createLocalCollection(LocalContext.current)),
            allCategories = emptyList(),
            allResources = emptyList(),
            detailSettings = detailSettings,
            icalObjectIdList = emptyList(),
            sliderIncrement = 10,
            saveICalObject = { _, _, _, _, _, _, _ -> },
            deleteICalObject = { },
            onProgressChanged = { _, _, _ -> },
            onMoveToNewCollection = { _, _ -> },
            onSubEntryAdded = { _, _ -> },
            onSubEntryDeleted = { },
            onSubEntryUpdated = { _, _ -> },
            goToDetail = { _, _, _ -> },
            goBack = { }
        )
    }
}




@Preview(showBackground = true)
@Composable
fun DetailScreenContent_TODO_editInitially_isChild() {
    MaterialTheme {
        val entity = ICalEntity().apply {
            this.property = ICalObject.createTask("MySummary")
        }
        entity.property.description = "Hello World, this \nis my description."
        entity.property.contact = "John Doe, +1 555 5545"

        val prefs: SharedPreferences = LocalContext.current.getSharedPreferences(
            DetailViewModel.PREFS_DETAIL_TODOS,
            Context.MODE_PRIVATE
        )
        val detailSettings = DetailSettings(prefs)

        DetailScreenContent(
            iCalEntity = remember { mutableStateOf(entity) },
            isEditMode = remember { mutableStateOf(true) },
            changeState = remember { mutableStateOf(DetailViewModel.DetailChangeState.CHANGESAVING) },
            subtasks = remember { mutableStateOf(emptyList()) },
            subnotes = remember { mutableStateOf(emptyList()) },
            isChild = true,
            player = null,
            goBackRequested = remember { mutableStateOf(false) },
            allWriteableCollections = listOf(ICalCollection.createLocalCollection(LocalContext.current)),
            allCategories = emptyList(),
            allResources = emptyList(),
            detailSettings = detailSettings,
            icalObjectIdList = emptyList(),
            sliderIncrement = 10,
            saveICalObject = { _, _, _, _, _, _, _ -> },
            deleteICalObject = { },
            onProgressChanged = { _, _, _ -> },
            onMoveToNewCollection = { _, _ -> },
            onSubEntryAdded = { _, _ -> },
            onSubEntryDeleted = { },
            onSubEntryUpdated = { _, _ -> },
            goToDetail = { _, _, _ -> },
            goBack = { }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DetailScreenContent_failedLoading() {
    MaterialTheme {

        val prefs: SharedPreferences = LocalContext.current.getSharedPreferences(
            DetailViewModel.PREFS_DETAIL_TODOS,
            Context.MODE_PRIVATE
        )
        val detailSettings = DetailSettings(prefs)

        DetailScreenContent(
            iCalEntity = remember { mutableStateOf(null) },
            isEditMode = remember { mutableStateOf(true) },
            changeState = remember { mutableStateOf(DetailViewModel.DetailChangeState.CHANGESAVING) },
            subtasks = remember { mutableStateOf(emptyList()) },
            subnotes = remember { mutableStateOf(emptyList()) },
            isChild = true,
            player = null,
            goBackRequested = remember { mutableStateOf(false) },
            allWriteableCollections = listOf(ICalCollection.createLocalCollection(LocalContext.current)),
            allCategories = emptyList(),
            allResources = emptyList(),
            detailSettings = detailSettings,
            icalObjectIdList = emptyList(),
            sliderIncrement = 10,
            saveICalObject = { _, _, _, _, _, _, _ -> },
            deleteICalObject = { },
            onProgressChanged = { _, _, _ -> },
            onMoveToNewCollection = { _, _ -> },
            onSubEntryAdded = { _, _ -> },
            onSubEntryDeleted = { },
            onSubEntryUpdated = { _, _ -> },
            goToDetail = { _, _, _ -> },
            goBack = { }
        )
    }
}

