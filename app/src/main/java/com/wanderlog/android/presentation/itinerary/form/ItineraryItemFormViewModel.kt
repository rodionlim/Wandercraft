package com.wanderlog.android.presentation.itinerary.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanderlog.android.core.util.ImportedMoneyParser
import com.wanderlog.android.core.util.capAmountToTwoDecimals
import com.wanderlog.android.core.util.roundToTwoDecimals
import com.wanderlog.android.core.util.toEditableAmount
import com.wanderlog.android.domain.model.Expense
import com.wanderlog.android.domain.model.ExpenseBreakdownItem
import com.wanderlog.android.domain.model.ExpenseCategory
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.ItineraryItemType
import com.wanderlog.android.domain.model.Place
import com.wanderlog.android.domain.usecase.expense.AddExpenseUseCase
import com.wanderlog.android.domain.usecase.expense.DeleteExpenseUseCase
import com.wanderlog.android.domain.usecase.expense.UpdateExpenseUseCase
import com.wanderlog.android.domain.usecase.itinerary.AddItineraryItemUseCase
import com.wanderlog.android.domain.usecase.itinerary.UpdateItineraryItemUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class CostBreakdownFormItem(
    val label: String = "",
    val amount: String = "",
    val isActive: Boolean = true
)

data class ItemFormState(
    val title: String = "",
    val itemType: ItineraryItemType = ItineraryItemType.PLACE,
    val place: Place? = null,
    val startTime: String = "",
    val endTime: String = "",
    val notes: String = "",
    val rating: Int? = null,
    val bookingRef: String = "",
    val costAmount: String = "",
    val costCurrencyCode: String = "",
    val costBreakdown: List<CostBreakdownFormItem> = emptyList(),
    val linkedExpenseId: String? = null,
    val linkedExpenseExists: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ItineraryItemFormViewModel @Inject constructor(
    private val addExpense: AddExpenseUseCase,
    private val updateExpense: UpdateExpenseUseCase,
    private val deleteExpense: DeleteExpenseUseCase,
    private val addItem: AddItineraryItemUseCase,
    private val updateItem: UpdateItineraryItemUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ItemFormState())
    val state: StateFlow<ItemFormState> = _state.asStateFlow()
    private var loadedLinkedExpense: Expense? = null

    fun loadExisting(item: ItineraryItem, linkedExpense: Expense?) {
        loadedLinkedExpense = linkedExpense
        _state.update {
            it.copy(
                title = item.title,
                itemType = item.itemType,
                place = item.place,
                startTime = item.startTime ?: "",
                endTime = item.endTime ?: "",
                notes = item.notes ?: "",
                rating = item.rating,
                bookingRef = item.bookingRef ?: "",
                costAmount = linkedExpense?.amount?.toEditableAmount().orEmpty(),
                costCurrencyCode = linkedExpense?.currencyCode.orEmpty(),
                costBreakdown = linkedExpense?.breakdown?.map { entry ->
                    CostBreakdownFormItem(
                        label = entry.label,
                        amount = entry.amount.toEditableAmount(),
                        isActive = entry.isActive
                    )
                }.orEmpty(),
                linkedExpenseId = item.linkedExpenseId,
                linkedExpenseExists = linkedExpense != null,
                isSaved = false,
                error = null
            )
        }
    }

    fun resetForm() {
        loadedLinkedExpense = null
        _state.value = ItemFormState()
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onTypeChange(v: ItineraryItemType) = _state.update { it.copy(itemType = v) }
    fun onPlaceSelected(p: Place?) = _state.update { it.copy(place = p) }
    fun onStartTimeChange(v: String) = _state.update { it.copy(startTime = v) }
    fun onEndTimeChange(v: String) = _state.update { it.copy(endTime = v) }
    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }
    fun onRatingChange(v: Int?) = _state.update { it.copy(rating = v?.coerceIn(1, 10)) }
    fun onBookingRefChange(v: String) = _state.update { it.copy(bookingRef = v) }
    fun onCostChange(v: String) = _state.update { it.copy(costAmount = v.capAmountToTwoDecimals()) }
    fun onCostCurrencyCodeChange(v: String) = _state.update { it.copy(costCurrencyCode = v) }

    fun onAddCostBreakdownItem() = _state.update { current ->
        current.copy(costBreakdown = current.costBreakdown + CostBreakdownFormItem())
    }

    fun onCostBreakdownLabelChange(index: Int, label: String) = _state.update { current ->
        if (index !in current.costBreakdown.indices) return@update current
        val updated = current.costBreakdown.toMutableList()
        updated[index] = updated[index].copy(label = label)
        current.copy(costBreakdown = updated)
    }

    fun onCostBreakdownAmountChange(index: Int, amount: String) = _state.update { current ->
        if (index !in current.costBreakdown.indices) return@update current
        val cappedAmount = amount.capAmountToTwoDecimals()
        val previousSum = current.costBreakdown.sumParsedAmounts()
        val updated = current.costBreakdown.toMutableList()
        updated[index] = updated[index].copy(amount = cappedAmount)
        val newSum = updated.sumParsedAmounts()
        val currentCostParsed = ImportedMoneyParser.parseAmount(current.costAmount)
        val shouldSyncCost = current.costAmount.isBlank() ||
            (previousSum > 0.0 && currentCostParsed == previousSum) ||
            (newSum > 0.0 && currentCostParsed == newSum)
        current.copy(
            costBreakdown = updated,
            costAmount = if (shouldSyncCost && newSum > 0.0) newSum.toEditableAmount() else current.costAmount
        )
    }

    fun onCostBreakdownActiveChange(index: Int, isActive: Boolean) = _state.update { current ->
        if (index !in current.costBreakdown.indices) return@update current
        val updated = current.costBreakdown.toMutableList()
        updated[index] = updated[index].copy(isActive = isActive)
        current.copy(costBreakdown = updated)
    }

    fun onRemoveCostBreakdownItem(index: Int) = _state.update { current ->
        if (index !in current.costBreakdown.indices) return@update current
        val previousSum = current.costBreakdown.sumParsedAmounts()
        val updated = current.costBreakdown.toMutableList().also { it.removeAt(index) }
        val newSum = updated.sumParsedAmounts()
        val currentCostParsed = ImportedMoneyParser.parseAmount(current.costAmount)
        val shouldSyncCost = currentCostParsed == previousSum
        current.copy(
            costBreakdown = updated,
            costAmount = when {
                updated.isEmpty() && shouldSyncCost -> ""
                shouldSyncCost && newSum > 0.0 -> newSum.toEditableAmount()
                else -> current.costAmount
            }
        )
    }

    fun save(
        tripId: String,
        dayId: String,
        dayDate: LocalDate?,
        currencyCode: String,
        existingId: String?
    ) {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(error = "Title is required.") }
            return
        }

        val nonEmptyBreakdownRows = if (s.itemType.supportsLinkedExpense()) {
            s.costBreakdown.filter { it.label.isNotBlank() || it.amount.isNotBlank() }
        } else {
            emptyList()
        }
        val parsedBreakdown = mutableListOf<ExpenseBreakdownItem>()
        for (row in nonEmptyBreakdownRows) {
            val rowAmount = ImportedMoneyParser.parseAmount(row.amount)
            if (rowAmount == null || rowAmount < 0.0) {
                _state.update { it.copy(error = "Enter a valid amount for each cost breakdown item.") }
                return
            }
            val rowLabel = row.label.trim()
            if (rowLabel.isBlank()) {
                _state.update { it.copy(error = "Enter a label for each cost breakdown item.") }
                return
            }
            parsedBreakdown += ExpenseBreakdownItem(
                label = rowLabel,
                amount = rowAmount.roundToTwoDecimals(),
                isActive = row.isActive
            )
        }

        val parsedCostInput = when {
            s.costAmount.isBlank() -> null
            else -> ImportedMoneyParser.parseAmount(s.costAmount)?.roundToTwoDecimals()
        }
        if (s.costAmount.isNotBlank() && parsedCostInput == null) {
            _state.update { it.copy(error = "Enter a valid cost amount.") }
            return
        }
        val parsedCost = if (parsedBreakdown.isNotEmpty()) {
            parsedBreakdown.sumOf(ExpenseBreakdownItem::amount).roundToTwoDecimals()
        } else {
            parsedCostInput
        }

        val shouldLinkExpense = s.itemType.supportsLinkedExpense() && parsedCost != null
        val linkedExpenseId = if (shouldLinkExpense) s.linkedExpenseId ?: UUID.randomUUID().toString() else null
        val rating = s.rating?.takeIf { it in 1..10 }
        if (s.rating != null && rating == null) {
            _state.update { it.copy(error = "Rating must be between 1 and 10.") }
            return
        }
        val item = ItineraryItem(
            id = existingId ?: UUID.randomUUID().toString(),
            tripDayId = dayId,
            tripId = tripId,
            title = s.title.trim(),
            itemType = s.itemType,
            place = s.place,
            startTime = s.startTime.takeIf { it.isNotBlank() },
            endTime = s.endTime.takeIf { it.isNotBlank() },
            notes = s.notes.takeIf { it.isNotBlank() },
            rating = rating,
            bookingRef = s.bookingRef.takeIf { it.isNotBlank() },
            linkedExpenseId = linkedExpenseId
        )
        viewModelScope.launch {
            runCatching {
                syncLinkedExpense(
                    item = item,
                    amount = parsedCost,
                    breakdown = parsedBreakdown,
                    currencyCode = currencyCode,
                    dayDate = dayDate,
                    itemTitle = s.title.trim()
                )
                if (existingId == null) addItem(item) else updateItem(item)
            }.onSuccess {
                _state.update { it.copy(isSaved = true, error = null) }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to save item.") }
            }
        }
    }

    private suspend fun syncLinkedExpense(
        item: ItineraryItem,
        amount: Double?,
        breakdown: List<ExpenseBreakdownItem>,
        currencyCode: String,
        dayDate: LocalDate?,
        itemTitle: String
    ) {
        val defaultCategory = item.itemType.defaultExpenseCategory()
        if (defaultCategory != null && amount != null) {
            val existingExpense = loadedLinkedExpense
            val resolvedCurrency = _state.value.costCurrencyCode.takeIf { it.isNotBlank() }
                ?: existingExpense?.currencyCode
                ?: currencyCode
            val expense = Expense(
                id = item.linkedExpenseId ?: UUID.randomUUID().toString(),
                tripId = item.tripId,
                title = itemTitle,
                amount = amount,
                currencyCode = resolvedCurrency,
                category = defaultCategory,
                date = dayDate,
                notes = existingExpense?.notes,
                breakdown = breakdown
            )
            if (_state.value.linkedExpenseExists && loadedLinkedExpense != null) {
                updateExpense(expense)
            } else {
                addExpense(expense)
            }
            loadedLinkedExpense = expense
            _state.update {
                it.copy(
                    linkedExpenseId = expense.id,
                    linkedExpenseExists = true
                )
            }
            return
        }

        loadedLinkedExpense?.let { deleteExpense(it) }
        loadedLinkedExpense = null
        _state.update {
            it.copy(
                linkedExpenseId = null,
                linkedExpenseExists = false
            )
        }
    }
}

private fun List<CostBreakdownFormItem>.sumParsedAmounts(): Double =
    mapNotNull { ImportedMoneyParser.parseAmount(it.amount)?.takeIf { value -> value >= 0.0 } }.sum().roundToTwoDecimals()

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

private fun ItineraryItemType.defaultExpenseCategory(): ExpenseCategory? = when (this) {
    ItineraryItemType.ACTIVITY -> ExpenseCategory.ACTIVITY
    ItineraryItemType.FOOD -> ExpenseCategory.FOOD
    ItineraryItemType.GROCERIES -> ExpenseCategory.GROCERIES
    ItineraryItemType.SHOPPING -> ExpenseCategory.SHOPPING
    ItineraryItemType.TRANSPORT,
    ItineraryItemType.FLIGHT -> ExpenseCategory.TRANSPORT
    ItineraryItemType.HOTEL -> ExpenseCategory.ACCOMMODATION
    else -> null
}
