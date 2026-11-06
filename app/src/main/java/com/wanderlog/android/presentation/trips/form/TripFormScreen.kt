package com.wanderlog.android.presentation.trips.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wanderlog.android.core.ui.component.LoadingOverlay
import com.wanderlog.android.core.ui.component.WanderTopBar
import com.wanderlog.android.core.util.PopularDestinations
import com.wanderlog.android.core.util.toDisplayString
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripFormScreen(
    onBack: () -> Unit,
    viewModel: TripFormViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }

    var showDateRangePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            WanderTopBar(
                title = if (state.tripId == null) "New Trip" else "Edit Trip",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Trip name") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            DestinationField(
                value = state.destination,
                onValueChange = viewModel::onDestinationChange
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = "${state.startDate.toDisplayString()} - ${state.endDate.toDisplayString()}",
                onValueChange = {},
                readOnly = true,
                label = { Text("Trip dates") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showDateRangePicker = true }) { Text("Pick") }
                }
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = state.budgetAmount,
                onValueChange = viewModel::onBudgetChange,
                label = { Text("Budget (optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            if (state.tripId == null && state.availablePackingSourceTrips.isNotEmpty()) {
                CopyPackingFromField(
                    options = state.availablePackingSourceTrips,
                    selectedTripId = state.selectedPackingSourceTripId,
                    onTripSelected = viewModel::onPackingSourceTripChange
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = state.travellerCount,
                onValueChange = viewModel::onTravellerCountChange,
                label = { Text("Number of travellers") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = if (state.travellerProfiles.isEmpty()) ImeAction.Done else ImeAction.Next
                ),
                singleLine = true
            )

            state.travellerProfiles.forEachIndexed { index, travellerProfile ->
                val isLastTraveller = index == state.travellerProfiles.lastIndex
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = travellerProfile.name,
                        onValueChange = { viewModel.onTravellerNameChange(index, it) },
                        label = { Text("Traveller ${index + 1} name or alias") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        singleLine = true
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = travellerProfile.age,
                        onValueChange = { viewModel.onTravellerAgeChange(index, it) },
                        label = { Text("Age") },
                        modifier = Modifier.width(112.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = if (isLastTraveller) ImeAction.Done else ImeAction.Next
                        ),
                        singleLine = true
                    )
                }
            }

            state.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Text(if (state.tripId == null) "Create Trip" else "Save Changes")
            }
            Spacer(Modifier.height(24.dp))
        }

        if (state.isLoading) LoadingOverlay()
    }

    if (showDateRangePicker) {
        DateRangePickerDialogWrapper(
            initialStartDate = state.startDate,
            initialEndDate = state.endDate,
            onDismiss = { showDateRangePicker = false },
            onDateRangeSelected = { startDate, endDate ->
                viewModel.onDateRangeChange(startDate, endDate)
                showDateRangePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyPackingFromField(
    options: List<PackingSourceTripOption>,
    selectedTripId: String?,
    onTripSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.id == selectedTripId }?.label ?: "Start with an empty packing list"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Copy packing from (optional)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Start with an empty packing list") },
                onClick = {
                    onTripSelected(null)
                    expanded = false
                }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onTripSelected(option.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationField(
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val suggestions = remember(value) { PopularDestinations.suggestions(value) }

    ExposedDropdownMenuBox(
        expanded = expanded && suggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Destination") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerDialogWrapper(
    initialStartDate: LocalDate,
    initialEndDate: LocalDate,
    onDismiss: () -> Unit,
    onDateRangeSelected: (LocalDate, LocalDate) -> Unit
) {
    val startMillis = initialStartDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    val endMillis = initialEndDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = startMillis,
        initialSelectedEndDateMillis = endMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selectedStartMillis = dateRangePickerState.selectedStartDateMillis
                val selectedEndMillis = dateRangePickerState.selectedEndDateMillis
                if (selectedStartMillis != null && selectedEndMillis != null) {
                    onDateRangeSelected(
                        Instant.ofEpochMilli(selectedStartMillis).atZone(ZoneId.of("UTC")).toLocalDate(),
                        Instant.ofEpochMilli(selectedEndMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                    )
                }
            }, enabled = dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = dateRangePickerState)
    }
}
