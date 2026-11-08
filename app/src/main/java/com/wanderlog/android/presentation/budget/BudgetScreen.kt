package com.wanderlog.android.presentation.budget

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wanderlog.android.core.util.ApproximateCurrencyConverter
import com.wanderlog.android.core.util.BudgetDisplayCurrencies
import com.wanderlog.android.core.util.ImportedMoneyParser
import com.wanderlog.android.core.ui.component.WanderTopBar
import com.wanderlog.android.core.util.generateDateRange
import com.wanderlog.android.core.util.toCurrencyString
import com.wanderlog.android.core.util.toDayOfWeekDisplay
import com.wanderlog.android.core.util.toDisplayString
import com.wanderlog.android.core.util.toShortDisplay
import com.wanderlog.android.domain.model.Expense
import com.wanderlog.android.domain.model.ExpenseCategory
import com.wanderlog.android.domain.model.TripDay
import androidx.compose.material3.DatePicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val tripStartDate = state.tripStartDate
    val tripEndDate = state.tripEndDate
    val selectedDay = state.selectedDay
    var expenseCurrencyMenuExpanded by remember { mutableStateOf(false) }
    var showAddOptions by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val dayOptions = buildBudgetDayOptions(
        tripDays = state.tripDays,
        tripStartDate = tripStartDate,
        tripEndDate = tripEndDate,
        expenses = state.expenses
    )
    val searchFilteredExpenses = state.expenses.filter { expense ->
        matchesBudgetSearch(expense.title, searchQuery) ||
            matchesBudgetSearch(expense.category.name, searchQuery) ||
            expense.breakdown.any { matchesBudgetSearch(it.label, searchQuery) }
    }
    val dayScopedExpenses = when {
        state.showUnscheduledOnly -> searchFilteredExpenses.filter { it.date == null }
        selectedDay != null -> searchFilteredExpenses.filter { it.date == selectedDay }
        else -> searchFilteredExpenses
    }
    val displayedExpenses = if (state.filterCategory == null) dayScopedExpenses
    else dayScopedExpenses.filter { it.category == state.filterCategory }
    val sectionedExpenses = buildBudgetSections(
        expenses = displayedExpenses,
        dayOptions = dayOptions,
        selectedDay = selectedDay,
        showUnscheduledOnly = state.showUnscheduledOnly,
        displayCurrencyCode = state.displayCurrencyCode
    )
    val scopeSpent = displayedExpenses.sumOf { expense ->
        ApproximateCurrencyConverter.convert(
            amount = expense.activeAmount,
            fromCurrency = expense.currencyCode,
            toCurrency = state.displayCurrencyCode
        )
    }
    val visibleCategories = ExpenseCategory.values().filter { category ->
        dayScopedExpenses.any { it.category == category } || state.filterCategory == category
    }
    val hasUnscheduledExpenses = state.expenses.any { it.date == null }
    val tripDayCount = when {
        state.tripDays.isNotEmpty() -> state.tripDays.size
        tripStartDate != null && tripEndDate != null ->
            generateDateRange(tripStartDate, tripEndDate).size
        else -> 0
    }
    val averageDailyBudget = state.convertedBudget?.takeIf { tripDayCount > 0 }?.div(tripDayCount)
    val selectedScopeLabel = when {
        state.showUnscheduledOnly -> "Unscheduled"
        selectedDay != null -> dayOptions.firstOrNull { it.date == selectedDay }?.label
            ?: selectedDay.toDisplayString()
        else -> "All days"
    }
    val scopeSummary = when {
        state.showUnscheduledOnly -> "${displayedExpenses.size} unscheduled expense${if (displayedExpenses.size == 1) "" else "s"}"
        selectedDay != null -> selectedDay.toDayOfWeekDisplay()
        else -> "${displayedExpenses.size} visible expense${if (displayedExpenses.size == 1) "" else "s"}"
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::startPhotoImport)
    }

    LaunchedEffect(state.showAddForm, searchVisible) {
        if (state.showAddForm) {
            val addFormIndex = if (searchVisible) 5 else 4
            listState.animateScrollToItem(addFormIndex)
        }
    }

    Scaffold(
        topBar = {
            WanderTopBar(
                title = "Budget",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchVisible) "Close search" else "Search budget items"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddOptions = true }) {
                Icon(Icons.Default.Add, "Add expense")
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                state.convertedBudget?.let { convertedBudget ->
                    val progress = (state.totalSpent / convertedBudget).coerceIn(0.0, 1.0).toFloat()
                    Column {
                        Text("Spent: ${state.totalSpent.toCurrencyString(state.displayCurrencyCode)} / ${convertedBudget.toCurrencyString(state.displayCurrencyCode)}")
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        val originalBudget = state.budget
                        if (originalBudget != null && state.tripCurrencyCode != state.displayCurrencyCode) {
                            Text(
                                "Trip budget entered as ${originalBudget.toCurrencyString(state.tripCurrencyCode)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } ?: Text("Total spent: ${state.totalSpent.toCurrencyString(state.displayCurrencyCode)}")
                Text(
                    "Display currency: ${state.displayCurrencyCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.usesApproximateConversion) {
                    Text(
                        "Approximate offline FX rates are used when expenses were recorded in different currencies. Change the display currency in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedDay == null && !state.showUnscheduledOnly,
                            onClick = viewModel::clearDayFilter,
                            label = { Text("All days") }
                        )
                    }
                    items(dayOptions, key = { it.label }) { day ->
                        FilterChip(
                            selected = selectedDay == day.date && !state.showUnscheduledOnly,
                            onClick = { viewModel.filterByDay(day.date) },
                            label = { Text(day.label) }
                        )
                    }
                    if (hasUnscheduledExpenses) {
                        item {
                            FilterChip(
                                selected = state.showUnscheduledOnly,
                                onClick = viewModel::filterByUnscheduled,
                                label = { Text("Unscheduled") }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(selectedScopeLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            scopeSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Visible spend: ${scopeSpent.toCurrencyString(state.displayCurrencyCode)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        averageDailyBudget?.let { dailyBudget ->
                            Text(
                                "Average trip-day budget: ${dailyBudget.toCurrencyString(state.displayCurrencyCode)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(selected = state.filterCategory == null, onClick = { viewModel.filterByCategory(null) }, label = { Text("All") })
                    }
                    items(visibleCategories) { cat ->
                        FilterChip(
                            selected = state.filterCategory == cat,
                            onClick = { viewModel.filterByCategory(if (state.filterCategory == cat) null else cat) },
                            label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (searchVisible) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search expenses") },
                        placeholder = { Text("Try food*, *ticket, or taxi") },
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

            }

            if (state.showAddForm) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (state.editingExpenseId == null) "Add Expense" else "Edit Expense",
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedTextField(value = state.addTitle, onValueChange = viewModel::onTitleChange, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(
                                value = state.addAmount,
                                onValueChange = viewModel::onAmountChange,
                                label = { Text("Amount") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                supportingText = if (state.addBreakdown.isNotEmpty()) {
                                    {
                                        val breakdownTotal = state.addBreakdown.sumOf {
                                            ImportedMoneyParser.parseAmount(it.amount)?.takeIf { v -> v >= 0.0 } ?: 0.0
                                        }
                                        val breakdownActive = state.addBreakdown
                                            .filter(BudgetBreakdownFormItem::isActive)
                                            .sumOf { ImportedMoneyParser.parseAmount(it.amount)?.takeIf { v -> v >= 0.0 } ?: 0.0 }
                                        Text(
                                            "Total: ${breakdownTotal.toCurrencyString(state.addCurrencyCode)} • Active (counted in spent): ${breakdownActive.toCurrencyString(state.addCurrencyCode)}"
                                        )
                                    }
                                } else null
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (state.addBreakdown.isNotEmpty()) {
                                    Text("Cost breakdown", style = MaterialTheme.typography.bodySmall)
                                }
                                state.addBreakdown.forEachIndexed { index, breakdownItem ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = breakdownItem.amount,
                                            onValueChange = { viewModel.onBreakdownAmountChange(index, it) },
                                            label = { Text("Amount") },
                                            modifier = Modifier.weight(0.38f),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = breakdownItem.label,
                                            onValueChange = { viewModel.onBreakdownLabelChange(index, it) },
                                            label = { Text("For (e.g. parents)") },
                                            modifier = Modifier.weight(0.47f),
                                            singleLine = true
                                        )
                                        BreakdownStatusBadge(
                                            isActive = breakdownItem.isActive,
                                            onClick = { viewModel.onBreakdownActiveChange(index, !breakdownItem.isActive) }
                                        )
                                        IconButton(
                                            onClick = { viewModel.onRemoveBreakdownItem(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove breakdown item",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = viewModel::onAddBreakdownItem,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add cost breakdown")
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Expense currency", style = MaterialTheme.typography.bodySmall)
                                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { expenseCurrencyMenuExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("${BudgetDisplayCurrencies.labelFor(state.addCurrencyCode)} (${state.addCurrencyCode})")
                                    }
                                    DropdownMenu(
                                        expanded = expenseCurrencyMenuExpanded,
                                        onDismissRequest = { expenseCurrencyMenuExpanded = false }
                                    ) {
                                        BudgetDisplayCurrencies.options.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text("${option.label} (${option.code})") },
                                                onClick = {
                                                    viewModel.onCurrencyCodeChange(option.code)
                                                    expenseCurrencyMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ExpenseCategory.values().forEach { cat ->
                                    FilterChip(selected = state.addCategory == cat, onClick = { viewModel.onCategoryChange(cat) }, label = { Text(cat.name.take(4)) })
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Expense date (optional)", style = MaterialTheme.typography.bodySmall)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(onClick = { showDatePicker = true }) {
                                        Text(state.selectedDate?.toDisplayString() ?: "Select date")
                                    }
                                    if (state.selectedDate != null) {
                                        TextButton(onClick = { viewModel.onDateChange(null) }) {
                                            Text("Clear")
                                        }
                                    }
                                }
                            }
                            state.formError?.let { errorMsg ->
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = viewModel::cancelEditing) { Text("Cancel") }
                                TextButton(onClick = viewModel::saveExpense) {
                                    Text(if (state.editingExpenseId == null) "Add" else "Save")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (sectionedExpenses.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("No expenses match this view.", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Try a different day, category, or search term.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                sectionedExpenses.forEach { section ->
                    item(key = section.key) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(section.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${section.totalSpent.toCurrencyString(state.displayCurrencyCode)} • ${section.expenses.size} item${if (section.expenses.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(section.expenses, key = { it.id }) { expense ->
                        BudgetExpenseCard(
                            expense = expense,
                            displayCurrencyCode = state.displayCurrencyCode,
                            onClick = { viewModel.editExpense(expense) },
                            onToggleBreakdownItem = { index -> viewModel.toggleBreakdownItemActive(expense, index) },
                            onDuplicate = { viewModel.duplicateExpense(expense) },
                            onDelete = { viewModel.deleteExpense(expense) }
                        )
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add to Budget",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Choose how you want to add expenses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        showAddOptions = false
                        viewModel.openAddForm()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manual entry")
                }
                OutlinedButton(
                    onClick = {
                        showAddOptions = false
                        photoPicker.launch(arrayOf("image/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload photo")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    when (val importStep = state.photoImportStep) {
        BudgetPhotoImportStep.Idle -> Unit
        BudgetPhotoImportStep.Parsing,
        is BudgetPhotoImportStep.Review,
        is BudgetPhotoImportStep.Error -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = viewModel::resetPhotoImport,
                sheetState = sheetState
            ) {
                BudgetPhotoImportSheet(
                    step = importStep,
                    onUpdateExpense = viewModel::updateImportedExpense,
                    onRemoveExpense = viewModel::removeImportedExpense,
                    onDismiss = viewModel::resetPhotoImport,
                    onCommit = viewModel::commitImportedExpenses,
                    onReset = viewModel::resetPhotoImport
                )
            }
        }
    }

    if (showDatePicker) {
        val selectedDateMillis = state.selectedDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.onDateChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            Surface {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
private fun BudgetExpenseCard(
    expense: Expense,
    displayCurrencyCode: String,
    onClick: () -> Unit,
    onToggleBreakdownItem: (Int) -> Unit = {},
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        expense.category.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    expense.date?.let { date ->
                        Text(
                            date.toDisplayString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (expense.currencyCode != displayCurrencyCode) {
                        Text(
                            "Original: ${expense.activeAmount.toCurrencyString(expense.currencyCode)}" +
                                if (expense.breakdown.isNotEmpty() && expense.activeAmount != expense.amount) {
                                    " (Total: ${expense.amount.toCurrencyString(expense.currencyCode)})"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val displayActiveAmount = ApproximateCurrencyConverter.convert(
                    amount = expense.activeAmount,
                    fromCurrency = expense.currencyCode,
                    toCurrency = displayCurrencyCode
                )
                val displayTotalAmount = ApproximateCurrencyConverter.convert(
                    amount = expense.amount,
                    fromCurrency = expense.currencyCode,
                    toCurrency = displayCurrencyCode
                )
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(
                        text = displayActiveAmount.toCurrencyString(displayCurrencyCode),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (expense.breakdown.isNotEmpty() && expense.activeAmount != expense.amount) {
                        Text(
                            text = "Total: ${displayTotalAmount.toCurrencyString(displayCurrencyCode)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (expense.currencyCode != displayCurrencyCode) {
                        Text(
                            text = "Approx.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Duplicate",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (expense.breakdown.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    expense.breakdown.forEachIndexed { index, part ->
                        val convertedPartAmount = ApproximateCurrencyConverter.convert(
                            amount = part.amount,
                            fromCurrency = expense.currencyCode,
                            toCurrency = displayCurrencyCode
                        )
                        val amountLabel = if (expense.currencyCode != displayCurrencyCode) {
                            "${convertedPartAmount.toCurrencyString(displayCurrencyCode)} (${part.amount.toCurrencyString(expense.currencyCode)})"
                        } else {
                            part.amount.toCurrencyString(expense.currencyCode)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$amountLabel → ${part.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (part.isActive) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textDecoration = if (part.isActive) TextDecoration.None else TextDecoration.LineThrough,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            BreakdownStatusBadge(
                                isActive = part.isActive,
                                onClick = { onToggleBreakdownItem(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownStatusBadge(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Text(
            text = if (isActive) "Active" else "Inactive",
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private data class BudgetDayOption(
    val date: LocalDate,
    val label: String
)

private data class BudgetExpenseSection(
    val key: String,
    val title: String,
    val totalSpent: Double,
    val expenses: List<Expense>
)

private fun buildBudgetDayOptions(
    tripDays: List<TripDay>,
    tripStartDate: LocalDate?,
    tripEndDate: LocalDate?,
    expenses: List<Expense>
): List<BudgetDayOption> {
    val scheduledDays = if (tripDays.isNotEmpty()) {
        tripDays.sortedBy(TripDay::dayNumber).map { day ->
            BudgetDayOption(
                date = day.date,
                label = "Day ${day.dayNumber} • ${day.date.toShortDisplay()}"
            )
        }
    } else if (tripStartDate != null && tripEndDate != null) {
        generateDateRange(tripStartDate, tripEndDate).mapIndexed { index, date ->
            BudgetDayOption(
                date = date,
                label = "Day ${index + 1} • ${date.toShortDisplay()}"
            )
        }
    } else {
        emptyList()
    }

    val knownDates = scheduledDays.map(BudgetDayOption::date).toSet()
    val extraExpenseDays = expenses
        .mapNotNull(Expense::date)
        .distinct()
        .filterNot(knownDates::contains)
        .sorted()
        .map { date ->
            BudgetDayOption(
                date = date,
                label = date.toDisplayString()
            )
        }

    return scheduledDays + extraExpenseDays
}

private fun buildBudgetSections(
    expenses: List<Expense>,
    dayOptions: List<BudgetDayOption>,
    selectedDay: LocalDate?,
    showUnscheduledOnly: Boolean,
    displayCurrencyCode: String
): List<BudgetExpenseSection> {
    if (showUnscheduledOnly) {
        return expenses.takeIf(List<Expense>::isNotEmpty)?.let { unscheduledExpenses ->
            listOf(
                BudgetExpenseSection(
                    key = "unscheduled",
                    title = "Unscheduled",
                    totalSpent = totalInDisplayCurrency(unscheduledExpenses, displayCurrencyCode),
                    expenses = unscheduledExpenses
                )
            )
        }.orEmpty()
    }

    if (selectedDay != null) {
        return expenses.takeIf(List<Expense>::isNotEmpty)?.let { selectedExpenses ->
            val label = dayOptions.firstOrNull { it.date == selectedDay }?.label ?: selectedDay.toDisplayString()
            listOf(
                BudgetExpenseSection(
                    key = selectedDay.toString(),
                    title = label,
                    totalSpent = totalInDisplayCurrency(selectedExpenses, displayCurrencyCode),
                    expenses = selectedExpenses
                )
            )
        }.orEmpty()
    }

    val sections = dayOptions.mapNotNull { day ->
        val dayExpenses = expenses.filter { it.date == day.date }
        if (dayExpenses.isEmpty()) {
            null
        } else {
            BudgetExpenseSection(
                key = day.date.toString(),
                title = day.label,
                totalSpent = totalInDisplayCurrency(dayExpenses, displayCurrencyCode),
                expenses = dayExpenses
            )
        }
    }.toMutableList()

    val unscheduledExpenses = expenses.filter { it.date == null }
    if (unscheduledExpenses.isNotEmpty()) {
        sections += BudgetExpenseSection(
            key = "unscheduled",
            title = "Unscheduled",
            totalSpent = totalInDisplayCurrency(unscheduledExpenses, displayCurrencyCode),
            expenses = unscheduledExpenses
        )
    }

    return sections
}

private fun totalInDisplayCurrency(expenses: List<Expense>, displayCurrencyCode: String): Double =
    expenses.sumOf { expense ->
        ApproximateCurrencyConverter.convert(
            amount = expense.activeAmount,
            fromCurrency = expense.currencyCode,
            toCurrency = displayCurrencyCode
        )
    }

private fun matchesBudgetSearch(value: String, query: String): Boolean {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return true

    val text = value.trim()
    if (text.isBlank()) return false

    val normalizedText = text.lowercase()
    val normalizedQuery = trimmedQuery.lowercase()
    if (!normalizedQuery.contains('*') && !normalizedQuery.contains('?')) {
        return normalizedText.contains(normalizedQuery)
    }

    val pattern = normalizedQuery
        .replace(".", "\\.")
        .replace("*", ".*")
        .replace("?", ".")
    return normalizedText.matches(Regex("^$pattern$"))
}
