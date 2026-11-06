package com.wanderlog.android.presentation.ai.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wanderlog.android.core.ui.component.LoadingOverlay
import com.wanderlog.android.core.ui.component.WanderTopBar

@Composable
fun AiGenerateScreen(
    onBack: () -> Unit,
    viewModel: AiGenerateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.committed) {
        if (state.committed) onBack()
    }

    Scaffold(topBar = { WanderTopBar(title = "AI Itinerary", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.mode == AiGenerateMode.FULL_TRIP,
                            onClick = { viewModel.onModeChange(AiGenerateMode.FULL_TRIP) },
                            label = { Text("Full trip") }
                        )
                        FilterChip(
                            selected = state.mode == AiGenerateMode.UPDATE_MULTIPLE_DAYS,
                            onClick = { viewModel.onModeChange(AiGenerateMode.UPDATE_MULTIPLE_DAYS) },
                            label = { Text("Update days") }
                        )
                    }

                    OutlinedTextField(
                        value = state.destination,
                        onValueChange = viewModel::onDestinationChange,
                        label = { Text("Destination") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = state.preferences,
                        onValueChange = viewModel::onPreferencesChange,
                        label = {
                            Text(
                                if (state.mode == AiGenerateMode.UPDATE_MULTIPLE_DAYS) {
                                    "What should change? AI can choose the best days"
                                } else {
                                    "Travel style / preferences"
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    if (state.mode == AiGenerateMode.UPDATE_MULTIPLE_DAYS) {
                        Text(
                            "AI can choose whichever existing days fit your request best, and will only suggest new items for those days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.estimatedContextTokens > 0) {
                            Text(
                                "Approx. prompt tokens: ${state.estimatedContextTokens} context • ${state.estimatedInputTokens} input • ${state.estimatedTotalTokens} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = viewModel::generate,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        Text(
                            if (state.mode == AiGenerateMode.UPDATE_MULTIPLE_DAYS) {
                                if (state.generatedDays.isEmpty()) "Suggest Multi-Day Update" else "Regenerate Suggestions"
                            } else {
                                if (state.generatedDays.isEmpty()) "Generate Itinerary" else "Regenerate"
                            }
                        )
                    }
                }
            }

            if (state.generatedDays.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (state.mode == AiGenerateMode.UPDATE_MULTIPLE_DAYS) {
                                "Preview — suggested additions across ${state.generatedDays.size} day(s)"
                            } else {
                                "Preview — ${state.generatedDays.size} days generated"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state.mode == AiGenerateMode.UPDATE_MULTIPLE_DAYS && state.overlappingGeneratedItemIds.isNotEmpty()) {
                            Text(
                                "Possible overlaps with existing itinerary items are unchecked by default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::selectAllGeneratedItems) {
                                Text("Select all")
                            }
                            OutlinedButton(onClick = viewModel::clearGeneratedItemSelection) {
                                Text("Clear")
                            }
                        }
                    }
                }

                itemsIndexed(state.generatedDays) { _, day ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Day ${day.dayNumber} — ${day.date}",
                                style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            day.items.forEach { item ->
                                val timePrefix = item.startTime?.let { "[$it] " } ?: ""
                                val isSelected = item.id in state.selectedGeneratedItemIds
                                val isOverlap = item.id in state.overlappingGeneratedItemIds
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            viewModel.onGeneratedItemSelectionChanged(item.id, checked)
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "$timePrefix${item.title}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (isOverlap) {
                                            Text(
                                                "Possible overlap with an existing item on this day",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Discard") }
                        Button(onClick = viewModel::commitToItinerary, modifier = Modifier.weight(1f)) {
                            Text(
                                when (state.mode) {
                                    AiGenerateMode.UPDATE_MULTIPLE_DAYS -> "Add to Matching Days"
                                    AiGenerateMode.FULL_TRIP -> "Add to Itinerary"
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
        if (state.isLoading) LoadingOverlay()
    }
}
