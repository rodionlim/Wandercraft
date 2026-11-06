package com.wanderlog.android.presentation.itinerary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import android.widget.Toast
import com.wanderlog.android.presentation.itinerary.export.writeItineraryPdf

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wanderlog.android.core.ui.component.ConfirmDialog
import com.wanderlog.android.core.ui.component.destinationVisualFor
import com.wanderlog.android.core.util.toCompactSlashDisplay
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.ItineraryItemType
import com.wanderlog.android.domain.model.Place
import com.wanderlog.android.presentation.ai.fileImport.ImportSheet
import com.wanderlog.android.presentation.itinerary.component.ItineraryItemCard
import com.wanderlog.android.presentation.itinerary.form.ItineraryItemFormSheet
import com.wanderlog.android.presentation.placeSearch.PlaceSearchSheet
import coil.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TripItineraryScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpenMap: (String?) -> Unit,
    onOpenBudget: () -> Unit,
    onOpenPacking: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenAiGenerate: () -> Unit,
    onOpenAskTrip: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenItemAttachments: (String) -> Unit,
    onOpenSync: () -> Unit,
    onOpenAttachments: (Boolean) -> Unit,
    viewModel: TripItineraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val exportContext = LocalContext.current
    val exportScope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var exportSnapshot by remember { mutableStateOf<ItineraryUiState?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot = exportSnapshot ?: state
        exportSnapshot = null
        if (uri != null) {
            exporting = true
            exportScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        exportContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                            writeItineraryPdf(snapshot.tripName, snapshot.tripDestination, snapshot.days, snapshot.allTripItems, output)
                        } ?: error("Unable to open the selected file")
                    }
                    Toast.makeText(exportContext, "Itinerary PDF saved", Toast.LENGTH_LONG).show()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Toast.makeText(exportContext, "Could not export PDF: ${error.message}", Toast.LENGTH_LONG).show()
                } finally { exporting = false }
            }
        }
    }
    val rawItems by viewModel.itemsForDay.collectAsState()
    val uriHandler = LocalUriHandler.current
    val selectedDayItems = remember(rawItems) { sortItineraryItems(rawItems) }
    val activeHotels = remember(state.activeHotelsForSelectedDay) { sortItineraryItems(state.activeHotelsForSelectedDay) }
    val filteredSelectedDayItems = remember(selectedDayItems, state.itemTypeFilter, state.ratingFilterMode, state.ratingThreshold) {
        filterItineraryItems(
            items = selectedDayItems,
            itemTypeFilter = state.itemTypeFilter,
            ratingFilterMode = state.ratingFilterMode,
            ratingThreshold = state.ratingThreshold
        )
    }
    val filteredActiveHotels = remember(activeHotels, state.itemTypeFilter, state.ratingFilterMode, state.ratingThreshold) {
        filterItineraryItems(
            items = activeHotels,
            itemTypeFilter = state.itemTypeFilter,
            ratingFilterMode = state.ratingFilterMode,
            ratingThreshold = state.ratingThreshold
        )
    }
    val filteredTripItems = remember(state.allTripItems, state.itemTypeFilter, state.ratingFilterMode, state.ratingThreshold) {
        filterItineraryItems(
            items = sortItineraryItems(state.allTripItems),
            itemTypeFilter = state.itemTypeFilter,
            ratingFilterMode = state.ratingFilterMode,
            ratingThreshold = state.ratingThreshold
        )
    }
    val tripSections = remember(filteredTripItems, state.days) {
        buildTripSections(filteredTripItems, state.days)
    }
    val showWholeTripResults = state.filterScope == ItineraryFilterScope.WHOLE_TRIP
    val canReorderSelectedDay = !showWholeTripResults && state.itemTypeFilter == null && state.ratingFilterMode == null

    var showItemForm by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ItineraryItem?>(null) }
    var showPlaceSearch by remember { mutableStateOf(false) }
    var selectedPlaceForForm by remember { mutableStateOf<Place?>(null) }
    var initialPlaceQuery by remember { mutableStateOf<String?>(null) }
    var showFileImport by remember { mutableStateOf(false) }
    var showAddOptions by remember { mutableStateOf(false) }
    var openFileImportAfterAddOptions by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<ItineraryItem?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    fun openItemForm(item: ItineraryItem?) {
        editingItem = item
        selectedPlaceForForm = null
        initialPlaceQuery = null
        showItemForm = true
    }

    fun closeItemForm() {
        showItemForm = false
        editingItem = null
        selectedPlaceForForm = null
        initialPlaceQuery = null
    }

    LaunchedEffect(showAddOptions, openFileImportAfterAddOptions) {
        if (!showAddOptions && openFileImportAfterAddOptions) {
            openFileImportAfterAddOptions = false
            showFileImport = true
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val mutable = selectedDayItems.toMutableList()
        mutable.add(to.index, mutable.removeAt(from.index))
        viewModel.reorderItems(mutable)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.tripName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenMap(null) }) {
                        Icon(Icons.Default.Map, contentDescription = "Map")
                    }
                    IconButton(onClick = onOpenBudget) {
                        Icon(Icons.Default.AttachMoney, contentDescription = "Budget")
                    }
                    IconButton(onClick = onOpenPacking) {
                        Icon(Icons.Default.Checklist, contentDescription = "Packing list")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (exporting) "Exporting PDF…" else "Export itinerary as PDF") },
                                enabled = !exporting && !state.isLoading && state.tripName.isNotBlank(),
                                onClick = {
                                    showOverflowMenu = false
                                    exportSnapshot = state
                                    exportLauncher.launch(state.tripName.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_") + " - itinerary.pdf")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Photos") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenPhotos()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Notes") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Note, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenNotes()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ask About Trip") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenAskTrip()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sync trip") },
                                leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenSync()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import") },
                                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showFileImport = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Attachments") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenAttachments(false)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddOptions = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.days.isEmpty()) {
                Text("Loading...", modifier = Modifier.align(Alignment.Center))
            } else {
                val visual = destinationVisualFor(state.tripDestination)
                val selectedDay = state.days.getOrNull(state.selectedDayIndex) ?: state.days.firstOrNull()
                Column(Modifier.fillMaxSize()) {
                    // Destination hero strip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .background(
                                Brush.linearGradient(
                                    listOf(visual.gradientStart, visual.gradientEnd)
                                )
                            )
                    ) {
                        state.tripCoverImageUri?.takeIf { it.isNotBlank() }?.let { cover ->
                            AsyncImage(
                                model = cover,
                                contentDescription = state.tripDestination,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.52f))
                                        )
                                    )
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                visual.emoji,
                                fontSize = if (state.tripCoverImageUri.isNullOrBlank()) 40.sp else 28.sp
                            )
                            Spacer(Modifier.padding(6.dp))
                            Column {
                                Text(
                                    state.tripDestination.ifBlank { "Your trip" },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    buildString {
                                        selectedDay?.let {
                                            append("Day ${it.dayNumber} • ${it.date.toCompactSlashDisplay()}")
                                        }
                                        if (state.days.isNotEmpty()) {
                                            if (isNotEmpty()) append("   ")
                                            append("${state.days.size} day")
                                            if (state.days.size != 1) append("s")
                                        }
                                    },
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    ScrollableTabRow(selectedTabIndex = state.selectedDayIndex) {
                        state.days.forEachIndexed { index, day ->
                            Tab(
                                selected = state.selectedDayIndex == index,
                                onClick = { viewModel.selectDay(index) },
                                text = { Text("Day ${day.dayNumber}") }
                            )
                        }
                    }

                    ItineraryFilters(
                        state = state,
                        onScopeSelected = viewModel::setFilterScope,
                        onTypeSelected = viewModel::filterByItemType,
                        onClearRatingFilter = viewModel::clearRatingFilter,
                        onRatingModeSelected = viewModel::setRatingFilterMode,
                        onRatingThresholdSelected = viewModel::setRatingThreshold
                    )

                    if (showWholeTripResults) {
                        if (tripSections.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No items match these filters across the trip.")
                            }
                        } else {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tripSections.forEach { section ->
                                    item(key = section.key) {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    items(section.items, key = { it.id }) { item ->
                                        ItineraryItemCard(
                                            item = item,
                                            linkedExpense = item.linkedExpenseId?.let(state.linkedExpensesById::get),
                                            attachmentCount = state.attachmentCountsByItemId[item.id] ?: 0,
                                            onClick = { openItemForm(item) },
                                            onOpenInMaps = item.place?.toGoogleMapsUrl()?.let { mapsUrl ->
                                                { uriHandler.openUri(mapsUrl) }
                                            },
                                            onManageAttachments = if ((state.attachmentCountsByItemId[item.id] ?: 0) > 0) {
                                                { onOpenItemAttachments(item.id) }
                                            } else {
                                                null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (filteredSelectedDayItems.isEmpty() && filteredActiveHotels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No items match these filters for this day.")
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (filteredActiveHotels.isNotEmpty()) {
                                item(key = "active-hotels-header") {
                                    Text(
                                        text = "Where you're staying",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                items(filteredActiveHotels, key = { "active-hotel-${it.id}" }) { item ->
                                    ItineraryItemCard(
                                        item = item,
                                        linkedExpense = item.linkedExpenseId?.let(state.linkedExpensesById::get),
                                        attachmentCount = state.attachmentCountsByItemId[item.id] ?: 0,
                                        onClick = { openItemForm(item) },
                                        onOpenInMaps = item.place?.toGoogleMapsUrl()?.let { mapsUrl ->
                                            { uriHandler.openUri(mapsUrl) }
                                        },
                                        onManageAttachments = if ((state.attachmentCountsByItemId[item.id] ?: 0) > 0) {
                                            { onOpenItemAttachments(item.id) }
                                        } else {
                                            null
                                        }
                                    )
                                }

                                if (filteredSelectedDayItems.isNotEmpty()) {
                                    item(key = "active-hotels-divider") {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }

                            if (canReorderSelectedDay) {
                                items(filteredSelectedDayItems, key = { it.id }) { item ->
                                    ReorderableItem(reorderableState, key = item.id) {
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                                    itemToDelete = item
                                                }
                                                false
                                            }
                                        )
                                        SwipeToDismissBox(
                                            state = dismissState,
                                            backgroundContent = {}
                                        ) {
                                            ItineraryItemCard(
                                                item = item,
                                                linkedExpense = item.linkedExpenseId?.let(state.linkedExpensesById::get),
                                                attachmentCount = state.attachmentCountsByItemId[item.id] ?: 0,
                                                onClick = { openItemForm(item) },
                                                onOpenInMaps = item.place?.toGoogleMapsUrl()?.let { mapsUrl ->
                                                    { uriHandler.openUri(mapsUrl) }
                                                },
                                                onManageAttachments = if ((state.attachmentCountsByItemId[item.id] ?: 0) > 0) {
                                                    { onOpenItemAttachments(item.id) }
                                                } else {
                                                    null
                                                },
                                                dragHandle = {
                                                    Icon(
                                                        Icons.Default.DragHandle,
                                                        contentDescription = "Drag",
                                                        tint = MaterialTheme.colorScheme.outlineVariant,
                                                        modifier = Modifier.draggableHandle()
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredSelectedDayItems, key = { it.id }) { item ->
                                    ItineraryItemCard(
                                        item = item,
                                        linkedExpense = item.linkedExpenseId?.let(state.linkedExpensesById::get),
                                        attachmentCount = state.attachmentCountsByItemId[item.id] ?: 0,
                                        onClick = { openItemForm(item) },
                                        onOpenInMaps = item.place?.toGoogleMapsUrl()?.let { mapsUrl ->
                                            { uriHandler.openUri(mapsUrl) }
                                        },
                                        onManageAttachments = if ((state.attachmentCountsByItemId[item.id] ?: 0) > 0) {
                                            { onOpenItemAttachments(item.id) }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddOptions) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddOptions = false },
            sheetState = sheetState
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add to Trip",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Choose how you want to add something to this trip.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        showAddOptions = false
                        openFileImportAfterAddOptions = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import from file / clipboard")
                }
                OutlinedButton(
                    onClick = {
                        showAddOptions = false
                        onOpenAiGenerate()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("AI Generate")
                }
                OutlinedButton(
                    onClick = {
                        showAddOptions = false
                        openItemForm(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manual Entry")
                }
                OutlinedButton(
                    onClick = {
                        showAddOptions = false
                        onOpenAttachments(true)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upload attachment")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showItemForm) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { closeItemForm() },
            sheetState = sheetState
        ) {
            val selectedDay = state.days.getOrNull(state.selectedDayIndex)
            if (selectedDay != null) {
                ItineraryItemFormSheet(
                    tripId = tripId,
                    dayId = selectedDay.id,
                    dayDate = selectedDay.date,
                    availableDays = state.days,
                    currencyCode = state.tripCurrencyCode,
                    editingItem = editingItem,
                    linkedExpense = editingItem?.linkedExpenseId?.let(state.linkedExpensesById::get),
                    selectedPlace = selectedPlaceForForm,
                    onSelectedPlaceApplied = { selectedPlaceForForm = null },
                    onDismiss = { closeItemForm() },
                    onDeleteRequested = editingItem?.let { item ->
                        {
                            closeItemForm()
                            itemToDelete = item
                        }
                    },
                    onManageAttachmentsRequested = editingItem?.let { item ->
                        {
                            closeItemForm()
                            onOpenItemAttachments(item.id)
                        }
                    },
                    onPlaceSearchRequested = { query ->
                        initialPlaceQuery = query
                        showPlaceSearch = true
                    }
                )
            }
        }
    }

    if (showPlaceSearch) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPlaceSearch = false },
            sheetState = sheetState
        ) {
            PlaceSearchSheet(
                initialQuery = initialPlaceQuery,
                onDismiss = { showPlaceSearch = false },
                onPlaceSelected = { place ->
                    selectedPlaceForForm = place
                    showPlaceSearch = false
                }
            )
        }
    }

    if (showFileImport) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFileImport = false },
            sheetState = sheetState
        ) {
            ImportSheet(
                tripId = tripId,
                onDismiss = { showFileImport = false }
            )
        }
    }

    itemToDelete?.let { item ->
        val isImportedGroup = (state.importAttachmentCountsByItemId[item.id] ?: 0) > 0
        ConfirmDialog(
            title = if (isImportedGroup) "Delete imported entries" else "Delete item",
            message = if (isImportedGroup) {
                "Remove \"${item.title}\" and the other itinerary entries, budget items, and attachment imported from the same file?"
            } else {
                "Remove \"${item.title}\"?"
            },
            onConfirm = { viewModel.deleteItem(item); itemToDelete = null },
            onDismiss = { itemToDelete = null }
        )
    }
}

@Composable
private fun ItineraryFilters(
    state: ItineraryUiState,
    onScopeSelected: (ItineraryFilterScope) -> Unit,
    onTypeSelected: (ItineraryItemType?) -> Unit,
    onClearRatingFilter: () -> Unit,
    onRatingModeSelected: (ItineraryRatingFilterMode) -> Unit,
    onRatingThresholdSelected: (Int) -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val summary = buildList {
        add(if (state.filterScope == ItineraryFilterScope.WHOLE_TRIP) "Whole trip" else "Selected day")
        state.itemTypeFilter?.let { itemType ->
            add(itemType.name.lowercase().replaceFirstChar { it.uppercase() })
        }
        if (state.ratingFilterMode != null && state.ratingThreshold != null) {
            val operator = if (state.ratingFilterMode == ItineraryRatingFilterMode.AT_LEAST) ">=" else "<="
            add("$operator ${state.ratingThreshold}")
        }
    }.joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { isExpanded = !isExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isExpanded) "Hide filters" else "Show filters")
        }

        if (!isExpanded) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Text("View", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = state.filterScope == ItineraryFilterScope.SELECTED_DAY,
                onClick = { onScopeSelected(ItineraryFilterScope.SELECTED_DAY) },
                label = { Text("Selected day") }
            )
            FilterChip(
                selected = state.filterScope == ItineraryFilterScope.WHOLE_TRIP,
                onClick = { onScopeSelected(ItineraryFilterScope.WHOLE_TRIP) },
                label = { Text("Whole trip") }
            )
        }

        Text("Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = state.itemTypeFilter == null,
                onClick = { onTypeSelected(null) },
                label = { Text("All") }
            )
            ItineraryItemType.values().forEach { itemType ->
                FilterChip(
                    selected = state.itemTypeFilter == itemType,
                    onClick = { onTypeSelected(if (state.itemTypeFilter == itemType) null else itemType) },
                    label = { Text(itemType.name.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Text("Rating", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = state.ratingFilterMode == null,
                onClick = onClearRatingFilter,
                label = { Text("Any rating") }
            )
            FilterChip(
                selected = state.ratingFilterMode == ItineraryRatingFilterMode.AT_LEAST,
                onClick = { onRatingModeSelected(ItineraryRatingFilterMode.AT_LEAST) },
                label = { Text(">=") }
            )
            FilterChip(
                selected = state.ratingFilterMode == ItineraryRatingFilterMode.AT_MOST,
                onClick = { onRatingModeSelected(ItineraryRatingFilterMode.AT_MOST) },
                label = { Text("<=") }
            )
        }

        if (state.ratingFilterMode != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..10).forEach { rating ->
                    FilterChip(
                        selected = state.ratingThreshold == rating,
                        onClick = { onRatingThresholdSelected(rating) },
                        label = { Text(rating.toString()) }
                    )
                }
            }
        }
    }
}

private data class ItineraryDaySection(
    val key: String,
    val title: String,
    val items: List<ItineraryItem>
)

private fun sortItineraryItems(items: List<ItineraryItem>): List<ItineraryItem> =
    items.sortedWith(
        compareBy<ItineraryItem>(
            { parseItinerarySortTime(it.startTime) == null },
            { parseItinerarySortTime(it.startTime) },
            { it.sortOrder },
            { it.title.lowercase() }
        )
    )

private fun filterItineraryItems(
    items: List<ItineraryItem>,
    itemTypeFilter: ItineraryItemType?,
    ratingFilterMode: ItineraryRatingFilterMode?,
    ratingThreshold: Int?
): List<ItineraryItem> = items.filter { item ->
    val typeMatches = itemTypeFilter == null || item.itemType == itemTypeFilter
    val ratingMatches = when {
        ratingFilterMode == null || ratingThreshold == null -> true
        item.rating == null -> false
        ratingFilterMode == ItineraryRatingFilterMode.AT_LEAST -> item.rating >= ratingThreshold
        else -> item.rating <= ratingThreshold
    }
    typeMatches && ratingMatches
}

private fun buildTripSections(
    items: List<ItineraryItem>,
    days: List<com.wanderlog.android.domain.model.TripDay>
): List<ItineraryDaySection> {
    val daysById = days.associateBy { it.id }
    return items
        .groupBy { it.tripDayId }
        .entries
        .sortedBy { entry -> daysById[entry.key]?.dayNumber ?: Int.MAX_VALUE }
        .map { (dayId, dayItems) ->
            val day = daysById[dayId]
            ItineraryDaySection(
                key = dayId,
                title = day?.let { "Day ${it.dayNumber} • ${it.date.toCompactSlashDisplay()}" } ?: "Other items",
                items = sortItineraryItems(dayItems)
            )
        }
}

private val itineraryTimeFormatters = listOf(
    DateTimeFormatter.ofPattern("H:mm"),
    DateTimeFormatter.ofPattern("HH:mm"),
    DateTimeFormatter.ofPattern("h:mm a"),
    DateTimeFormatter.ofPattern("h:mma"),
    DateTimeFormatter.ofPattern("h a"),
    DateTimeFormatter.ofPattern("ha")
)

private fun parseItinerarySortTime(value: String?): LocalTime? {
    val candidate = value?.trim().orEmpty()
    if (candidate.isBlank()) return null

    return runCatching { OffsetDateTime.parse(candidate).toLocalTime() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(candidate).toLocalTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(candidate).toLocalTime() }.getOrNull()
        ?: itineraryTimeFormatters.firstNotNullOfOrNull { formatter ->
            runCatching { LocalTime.parse(candidate.uppercase(), formatter) }.getOrNull()
        }
}

private fun Place.toGoogleMapsUrl(): String? {
    val coordinates = listOfNotNull(latitude, longitude)
        .takeIf { it.size == 2 }
        ?.joinToString(",")

    val query = address?.takeIf { it.isNotBlank() }
        ?: name.takeIf { it.isNotBlank() }
        ?: coordinates
    return query?.let { "https://www.google.com/maps/search/?api=1&query=${Uri.encode(it)}" }
}
