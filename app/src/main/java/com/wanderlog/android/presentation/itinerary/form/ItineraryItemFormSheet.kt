package com.wanderlog.android.presentation.itinerary.form

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wanderlog.android.core.util.ImportedMoneyParser
import com.wanderlog.android.core.util.toCurrencyString
import com.wanderlog.android.domain.model.Expense
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.ItineraryItemType
import com.wanderlog.android.domain.model.Place
import com.wanderlog.android.domain.model.TripDay
import java.time.LocalDate

@Composable
fun ItineraryItemFormSheet(
    tripId: String,
    dayId: String,
    dayDate: LocalDate?,
    availableDays: List<TripDay>,
    currencyCode: String,
    editingItem: ItineraryItem?,
    linkedExpense: Expense? = null,
    selectedPlace: Place? = null,
    onSelectedPlaceApplied: () -> Unit = {},
    onDismiss: () -> Unit,
    onDeleteRequested: (() -> Unit)? = null,
    onManageAttachmentsRequested: (() -> Unit)? = null,
    onPlaceSearchRequested: (String?) -> Unit,
    viewModel: ItineraryItemFormViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val expenseCurrencyCode = linkedExpense?.currencyCode ?: currencyCode
    var selectedDayId by remember(editingItem?.id, dayId, availableDays) {
        mutableStateOf(editingItem?.tripDayId ?: dayId)
    }

    LaunchedEffect(editingItem, linkedExpense) {
        if (editingItem != null) viewModel.loadExisting(editingItem, linkedExpense) else viewModel.resetForm()
    }

    LaunchedEffect(editingItem?.id, dayId) {
        selectedDayId = editingItem?.tripDayId ?: dayId
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDismiss()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetForm()
        }
    }

    LaunchedEffect(selectedPlace) {
        if (selectedPlace != null) {
            viewModel.onPlaceSelected(selectedPlace)
            onSelectedPlaceApplied()
        }
    }

    ItineraryItemFormContent(
        state = state,
        availableDays = availableDays,
        editingItem = editingItem,
        expenseCurrencyCode = expenseCurrencyCode,
        dayId = dayId,
        dayDate = dayDate,
        selectedDayId = selectedDayId,
        onSelectedDayIdChange = { selectedDayId = it },
        onPlaceSearchRequested = {},
        onSave = { targetDayId, targetDayDate ->
            viewModel.save(
                tripId = tripId,
                dayId = targetDayId,
                dayDate = targetDayDate,
                currencyCode = expenseCurrencyCode,
                existingId = editingItem?.id
            )
        },
        onManageAttachmentsRequested = onManageAttachmentsRequested,
        onDeleteRequested = onDeleteRequested,
        onTitleChange = viewModel::onTitleChange,
        onTypeChange = viewModel::onTypeChange,
        onStartTimeChange = viewModel::onStartTimeChange,
        onEndTimeChange = viewModel::onEndTimeChange,
        onNotesChange = viewModel::onNotesChange,
        onRatingChange = viewModel::onRatingChange,
        onBookingRefChange = viewModel::onBookingRefChange,
        onCostChange = viewModel::onCostChange,
        onAddCostBreakdownItem = viewModel::onAddCostBreakdownItem,
        onCostBreakdownLabelChange = viewModel::onCostBreakdownLabelChange,
        onCostBreakdownAmountChange = viewModel::onCostBreakdownAmountChange,
        onCostBreakdownActiveChange = viewModel::onCostBreakdownActiveChange,
        onRemoveCostBreakdownItem = viewModel::onRemoveCostBreakdownItem,
        onPlaceButtonClick = {
            onPlaceSearchRequested(
                state.place?.name
                    ?: state.place?.address
                    ?: state.title.takeIf { it.isNotBlank() }
            )
        }
    )
}

@Composable
internal fun ItineraryItemFormContent(
    state: ItemFormState,
    availableDays: List<TripDay>,
    editingItem: ItineraryItem?,
    expenseCurrencyCode: String,
    dayId: String,
    dayDate: LocalDate?,
    selectedDayId: String,
    onSelectedDayIdChange: (String) -> Unit,
    onPlaceSearchRequested: () -> Unit,
    onSave: (String, LocalDate?) -> Unit,
    onManageAttachmentsRequested: (() -> Unit)?,
    onDeleteRequested: (() -> Unit)?,
    onTitleChange: (String) -> Unit,
    onTypeChange: (ItineraryItemType) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onRatingChange: (Int?) -> Unit,
    onBookingRefChange: (String) -> Unit,
    onCostChange: (String) -> Unit,
    onAddCostBreakdownItem: () -> Unit = {},
    onCostBreakdownLabelChange: (Int, String) -> Unit = { _, _ -> },
    onCostBreakdownAmountChange: (Int, String) -> Unit = { _, _ -> },
    onCostBreakdownActiveChange: (Int, Boolean) -> Unit = { _, _ -> },
    onRemoveCostBreakdownItem: (Int) -> Unit = {},
    onPlaceButtonClick: () -> Unit
) {
    val selectedDay = availableDays.firstOrNull { it.id == selectedDayId }
        ?: availableDays.firstOrNull { it.id == dayId }
        ?: availableDays.firstOrNull()
    val targetDayId = selectedDay?.id ?: dayId
    val targetDayDate = selectedDay?.date ?: dayDate

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (editingItem == null) "Add Item" else "Edit Item",
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge
        )

        if (editingItem != null && availableDays.isNotEmpty()) {
            Text(
                text = "Day",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableDays.forEach { day ->
                    FilterChip(
                        selected = day.id == targetDayId,
                        onClick = { onSelectedDayIdChange(day.id) },
                        label = {
                            Text(
                                text = "Day ${day.dayNumber}",
                                softWrap = false
                            )
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ItineraryItemType.values().forEach { type ->
                FilterChip(
                    selected = state.itemType == type,
                    onClick = { onTypeChange(type) },
                    label = {
                        Text(
                            text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                            softWrap = false
                        )
                    }
                )
            }
        }

        OutlinedButton(
            onClick = onPlaceButtonClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(placeButtonLabel(state.place) ?: "Search & add place")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.startTime,
                onValueChange = onStartTimeChange,
                label = { Text("Start (HH:mm)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = state.endTime,
                onValueChange = onEndTimeChange,
                label = { Text("End (HH:mm)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        OutlinedTextField(
            value = state.notes,
            onValueChange = onNotesChange,
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Text(
            text = "Rating (1-10 stars, optional)",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = state.rating == null,
                onClick = { onRatingChange(null) },
                label = { Text("No rating") }
            )
            (1..10).forEach { rating ->
                FilterChip(
                    selected = state.rating == rating,
                    onClick = { onRatingChange(if (state.rating == rating) null else rating) },
                    label = { Text(rating.toString()) }
                )
            }
        }

        OutlinedTextField(
            value = state.bookingRef,
            onValueChange = onBookingRefChange,
            label = { Text("Booking reference (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (state.itemType.supportsLinkedExpense()) {
            val breakdownTotal = state.costBreakdown.sumOf {
                ImportedMoneyParser.parseAmount(it.amount)?.takeIf { v -> v >= 0.0 } ?: 0.0
            }
            val breakdownActiveTotal = state.costBreakdown
                .filter(CostBreakdownFormItem::isActive)
                .sumOf { ImportedMoneyParser.parseAmount(it.amount)?.takeIf { v -> v >= 0.0 } ?: 0.0 }

            OutlinedTextField(
                value = state.costAmount,
                onValueChange = onCostChange,
                label = { Text("Cost ($expenseCurrencyCode, optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                supportingText = {
                    if (state.costBreakdown.isEmpty()) {
                        Text("This creates or updates a linked expense in Budget.")
                    } else {
                        Text(
                            "Total: ${breakdownTotal.toCurrencyString(expenseCurrencyCode)} • Active (counted in spent): ${breakdownActiveTotal.toCurrencyString(expenseCurrencyCode)}"
                        )
                    }
                }
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.costBreakdown.isNotEmpty()) {
                    Text(
                        text = "Cost breakdown",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                state.costBreakdown.forEachIndexed { index, breakdownItem ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = breakdownItem.amount,
                                onValueChange = { onCostBreakdownAmountChange(index, it) },
                                label = { Text("Amount ($expenseCurrencyCode)") },
                                modifier = Modifier.weight(0.42f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = breakdownItem.label,
                                onValueChange = { onCostBreakdownLabelChange(index, it) },
                                label = { Text("For (e.g. parents)") },
                                modifier = Modifier.weight(0.58f),
                                singleLine = true
                            )
                            IconButton(onClick = { onRemoveCostBreakdownItem(index) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove breakdown item"
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = breakdownItem.isActive,
                                onClick = { onCostBreakdownActiveChange(index, !breakdownItem.isActive) },
                                label = {
                                    Text(
                                        if (breakdownItem.isActive) {
                                            "Active (counts towards total spent)"
                                        } else {
                                            "Inactive (excluded from total spent)"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddCostBreakdownItem,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add cost breakdown")
                }
            }
        }

        state.error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

        Button(
            onClick = { onSave(targetDayId, targetDayDate) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }

        if (editingItem != null && onManageAttachmentsRequested != null) {
            OutlinedButton(
                onClick = onManageAttachmentsRequested,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage attachments")
            }
        }

        if (editingItem != null && onDeleteRequested != null) {
            OutlinedButton(
                onClick = onDeleteRequested,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete item")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

private fun placeButtonLabel(place: Place?): String? {
    val name = place?.name?.trim()?.takeIf { it.isNotBlank() }
    val address = place?.address?.trim()?.takeIf { it.isNotBlank() }

    return when {
        name != null && address != null && !address.contains(name, ignoreCase = true) -> "$name - $address"
        name != null -> name
        address != null -> address
        else -> null
    }
}

private fun ItineraryItemType.supportsLinkedExpense(): Boolean = when (this) {
    ItineraryItemType.ACTIVITY,
    ItineraryItemType.FOOD,
    ItineraryItemType.GROCERIES,
    ItineraryItemType.SHOPPING,
    ItineraryItemType.TRANSPORT,
    ItineraryItemType.FLIGHT,
    ItineraryItemType.HOTEL -> true

    else -> false
}
