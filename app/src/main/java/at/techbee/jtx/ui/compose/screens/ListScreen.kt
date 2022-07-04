package at.techbee.jtx.ui.compose.screens


import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import at.techbee.jtx.ui.IcalListFragmentDirections
import at.techbee.jtx.ui.IcalListViewModel
import at.techbee.jtx.ui.ViewMode
import at.techbee.jtx.ui.compose.appbars.ListBottomAppBar
import at.techbee.jtx.ui.compose.dialogs.QuickAddDialog
import at.techbee.jtx.ui.compose.stateholder.SettingsStateHolder
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    icalListViewModel: IcalListViewModel,
    navController: NavController
) {

    val filterBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val coroutineScope = rememberCoroutineScope()
    val settingsStateHolder = SettingsStateHolder(LocalContext.current)

    var showQuickAddDialog by remember { mutableStateOf(false) }
    val allCollections = icalListViewModel.allCollections.observeAsState(emptyList())
    if(showQuickAddDialog) {
        QuickAddDialog(
            module = icalListViewModel.module,
            allCollections = allCollections.value,
            onEntrySaved = { newICalObject, categories, editAfterSaving ->
                icalListViewModel.insertQuickItem(newICalObject, categories)
                /*  //TODO
            if(AdManager.getInstance()?.isAdFlavor() == true && BillingManager.getInstance()?.isProPurchased?.value == false)
                AdManager.getInstance()?.showInterstitialAd(requireActivity())     // don't forget to show an ad if applicable ;-)
             */
                if (editAfterSaving)
                    TODO("Not implemented")
            },
            onDismiss = { showQuickAddDialog = false }
        )
    }

    Scaffold(
        bottomBar = {
            ListBottomAppBar(
                module = icalListViewModel.module,
                iCal4ListLive = icalListViewModel.iCal4List,
                onAddNewEntry = { /* TODO */ },
                onAddNewQuickEntry = { showQuickAddDialog = true },
                listSettings = icalListViewModel.listSettings,
                onListSettingsChanged = { icalListViewModel.updateSearch(saveListSettings = true) },
                onFilterIconClicked = {
                    coroutineScope.launch {
                        filterBottomSheetState.show()
                    }
                },
                onGoToDateSelected = { id -> icalListViewModel.scrollOnceId.postValue(id)}
            )
        }) {


        when (icalListViewModel.listSettings.viewMode.value) {
            ViewMode.LIST -> {
                ListScreenList(
                    listLive = icalListViewModel.iCal4List,
                    subtasksLive = icalListViewModel.allSubtasksMap,
                    subnotesLive = icalListViewModel.allSubnotesMap,
                    attachmentsLive = icalListViewModel.allAttachmentsMap,
                    scrollOnceId = icalListViewModel.scrollOnceId,
                    listSettings = icalListViewModel.listSettings,
                    isSubtasksExpandedDefault = settingsStateHolder.settingAutoExpandSubtasks,
                    isSubnotesExpandedDefault = settingsStateHolder.settingAutoExpandSubnotes,
                    isAttachmentsExpandedDefault = settingsStateHolder.settingAutoExpandAttachments,
                    settingShowProgressMaintasks = settingsStateHolder.settingShowProgressForMainTasks,
                    settingShowProgressSubtasks = settingsStateHolder.settingShowProgressForSubTasks,
                    settingProgressIncrement = settingsStateHolder.settingStepForProgress,
                    goToView = { itemId ->
                        navController.navigate(
                            IcalListFragmentDirections
                                .actionIcalListFragmentToIcalViewFragment()
                                .setItem2show(itemId)
                        )
                    },
                    goToEdit = { itemId ->
                        icalListViewModel.postDirectEditEntity(
                            itemId
                        )
                    },
                    onProgressChanged = { itemId, newPercent, isLinkedRecurringInstance ->
                        icalListViewModel.updateProgress(
                            itemId,
                            newPercent,
                            isLinkedRecurringInstance
                        )
                    },
                    onExpandedChanged = { itemId: Long, isSubtasksExpanded: Boolean, isSubnotesExpanded: Boolean, isAttachmentsExpanded: Boolean ->
                        icalListViewModel.updateExpanded(
                            itemId,
                            isSubtasksExpanded,
                            isSubnotesExpanded,
                            isAttachmentsExpanded
                        )
                    },
                )
            }
            ViewMode.GRID -> {
                ListScreenGrid(
                    listLive = icalListViewModel.iCal4List,
                    scrollOnceId = icalListViewModel.scrollOnceId,
                    onProgressChanged = { itemId, newPercent, isLinkedRecurringInstance ->
                        icalListViewModel.updateProgress(
                            itemId,
                            newPercent,
                            isLinkedRecurringInstance
                        )
                    },
                    goToView = { itemId ->
                        navController.navigate(
                            IcalListFragmentDirections
                                .actionIcalListFragmentToIcalViewFragment()
                                .setItem2show(itemId)
                        )
                    },
                    goToEdit = { itemId ->
                        icalListViewModel.postDirectEditEntity(
                            itemId
                        )
                    }
                )
            }
            ViewMode.COMPACT -> {
                ListScreenCompact(
                    listLive = icalListViewModel.iCal4List,
                    subtasksLive = icalListViewModel.allSubtasksMap,
                    scrollOnceId = icalListViewModel.scrollOnceId,
                    listSettings = icalListViewModel.listSettings,
                    onProgressChanged = { itemId, newPercent, isLinkedRecurringInstance ->
                        icalListViewModel.updateProgress(
                            itemId,
                            newPercent,
                            isLinkedRecurringInstance
                        )
                    },
                    goToView = { itemId ->
                        navController.navigate(
                            IcalListFragmentDirections
                                .actionIcalListFragmentToIcalViewFragment()
                                .setItem2show(itemId)
                        )
                    },
                    goToEdit = { itemId ->
                        icalListViewModel.postDirectEditEntity(
                            itemId
                        )
                    }
                )
            }
            ViewMode.KANBAN -> {
                ListScreenKanban(
                    module = icalListViewModel.module,
                    listLive = icalListViewModel.iCal4List,
                    scrollOnceId = icalListViewModel.scrollOnceId,
                    onProgressChanged = { itemId, newPercent, isLinkedRecurringInstance, scrollOnce ->
                        icalListViewModel.updateProgress(
                            itemId,
                            newPercent,
                            isLinkedRecurringInstance,
                            scrollOnce
                        )
                    },
                    onStatusChanged = { itemId, newStatus, isLinkedRecurringInstance, scrollOnce ->
                        icalListViewModel.updateStatusJournal(
                            itemId,
                            newStatus,
                            isLinkedRecurringInstance,
                            scrollOnce
                        )
                    },
                    goToView = { itemId ->
                        navController.navigate(
                            IcalListFragmentDirections
                                .actionIcalListFragmentToIcalViewFragment()
                                .setItem2show(itemId)
                        )
                    },
                    goToEdit = { itemId ->
                        icalListViewModel.postDirectEditEntity(
                            itemId
                        )
                    }
                )
            }
        }

        ModalBottomSheetLayout(
            sheetState = filterBottomSheetState,
            sheetContent = {
                FilterScreen(
                    module = icalListViewModel.module,
                    listSettings = icalListViewModel.listSettings,
                    allCollectionsLive = icalListViewModel.allCollections,
                    allCategoriesLive = icalListViewModel.allCategories,
                    onListSettingsChanged = { icalListViewModel.updateSearch(saveListSettings = true) }
                )
            }
        ) {}
    }
}

