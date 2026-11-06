package com.wanderlog.android.presentation.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wanderlog.android.core.util.BudgetDisplayCurrencies
import com.wanderlog.android.core.ui.component.ConfirmDialog
import com.wanderlog.android.core.ui.component.WanderTopBar

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var parsingModelMenuExpanded by remember { mutableStateOf(false) }
    var budgetCurrencyMenuExpanded by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showOpenAiKeyHelp by remember { mutableStateOf(false) }
    var showGeminiKeyHelp by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    Scaffold(topBar = { WanderTopBar(title = "Settings", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("API Keys", style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI Provider", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProvider.entries.forEach { provider ->
                        FilterChip(
                            selected = state.aiProvider == provider,
                            onClick = {
                                modelMenuExpanded = false
                                parsingModelMenuExpanded = false
                                viewModel.onAiProviderChange(provider)
                            },
                            label = { Text(provider.label) }
                        )
                    }
                }
            }

            val isGemini = state.aiProvider == AiProvider.GEMINI
            val activeModelOptions = if (isGemini) GeminiModels.options else OpenAiModels.options
            val activeParsingOptions = if (isGemini) GeminiModels.parsingOptions else OpenAiModels.parsingOptions
            val selectedModelId = if (isGemini) state.geminiModel else state.openAiModel
            val selectedParsingModelId = if (isGemini) state.geminiParsingModel else state.openAiParsingModel
            val selectedModelLabel = if (isGemini) {
                GeminiModels.labelFor(selectedModelId)
            } else {
                OpenAiModels.labelFor(selectedModelId)
            }
            val selectedParsingModelLabel = if (isGemini) {
                GeminiModels.labelFor(selectedParsingModelId)
            } else {
                OpenAiModels.labelFor(selectedParsingModelId)
            }

            val selectedOption = activeModelOptions.firstOrNull { it.id == selectedModelId }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${state.aiProvider.label} Model", style = MaterialTheme.typography.bodyMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { modelMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$selectedModelLabel ($selectedModelId)")
                    }

                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        activeModelOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.label} (${option.id})") },
                                onClick = {
                                    if (isGemini) {
                                        viewModel.onGeminiModelChange(option.id)
                                    } else {
                                        viewModel.onOpenAiModelChange(option.id)
                                    }
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    selectedOption?.usageTier ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val selectedParsingOption = activeParsingOptions.firstOrNull { it.id == selectedParsingModelId }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Booking Image/PDF Parsing Model", style = MaterialTheme.typography.bodyMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { parsingModelMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("$selectedParsingModelLabel ($selectedParsingModelId)")
                    }

                    DropdownMenu(
                        expanded = parsingModelMenuExpanded,
                        onDismissRequest = { parsingModelMenuExpanded = false }
                    ) {
                        activeParsingOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.label} (${option.id})") },
                                onClick = {
                                    if (isGemini) {
                                        viewModel.onGeminiParsingModelChange(option.id)
                                    } else {
                                        viewModel.onOpenAiParsingModelChange(option.id)
                                    }
                                    parsingModelMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    selectedParsingOption?.usageTier ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isGemini) {
                OutlinedTextField(
                    value = state.geminiKey,
                    onValueChange = viewModel::onGeminiKeyChange,
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGeminiKeyHelp = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = "How to create a Gemini API key"
                            )
                        }
                    },
                    singleLine = true
                )
            } else {
                OutlinedTextField(
                    value = state.openAiKey,
                    onValueChange = viewModel::onOpenAiKeyChange,
                    label = { Text("OpenAI API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showOpenAiKeyHelp = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                contentDescription = "How to create an OpenAI API key"
                            )
                        }
                    },
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = state.mapsKey,
                onValueChange = viewModel::onMapsKeyChange,
                label = { Text("Google Maps API Key (optional)") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                supportingText = {
                    Text("Leave this blank to use the manifest key from local.properties.")
                },
                singleLine = true
            )

            Text(
                "Keys are stored encrypted on your device using Android Keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Budget", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Budget display currency", style = MaterialTheme.typography.bodyMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { budgetCurrencyMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${BudgetDisplayCurrencies.labelFor(state.budgetDisplayCurrency)} (${state.budgetDisplayCurrency})")
                    }

                    DropdownMenu(
                        expanded = budgetCurrencyMenuExpanded,
                        onDismissRequest = { budgetCurrencyMenuExpanded = false }
                    ) {
                        BudgetDisplayCurrencies.options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("${option.label} (${option.code})") },
                                onClick = {
                                    viewModel.onBudgetDisplayCurrencyChange(option.code)
                                    budgetCurrencyMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    "Budget totals use offline approximate FX rates when expenses were recorded in different currencies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }

            Spacer(Modifier.height(8.dp))

            Text("Sync Maintenance", style = MaterialTheme.typography.titleMedium)
            Text(
                "Hard reset deleted sync tombstones on this device. This permanently removes soft-deleted rows so old deletions stop participating in future syncs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.resetInProgress
            ) {
                Text(if (state.resetInProgress) "Resetting tombstones..." else "Reset Deleted Tombstones")
            }

            state.resetMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "Reset deleted tombstones",
            message = "Permanently remove all deleted sync tombstones from this device? This is a local hard reset for past deletions and cannot be undone.",
            onConfirm = {
                showResetConfirm = false
                viewModel.resetDeletedTombstones()
            },
            onDismiss = {
                showResetConfirm = false
                viewModel.clearResetMessage()
            }
        )
    }

    if (showOpenAiKeyHelp) {
        OpenAiApiKeyHelpDialog(
            title = "Create your OpenAI API key",
            confirmLabel = "Open OpenAI",
            dismissLabel = "Close",
            onConfirm = {
                uriHandler.openUri("https://platform.openai.com/api-keys")
            },
            onDismiss = { showOpenAiKeyHelp = false }
        )
    }

    if (showGeminiKeyHelp) {
        GeminiApiKeyHelpDialog(
            title = "Create your Gemini API key",
            confirmLabel = "Open AI Studio",
            dismissLabel = "Close",
            onConfirm = {
                uriHandler.openUri("https://aistudio.google.com/apikey")
            },
            onDismiss = { showGeminiKeyHelp = false }
        )
    }
}
