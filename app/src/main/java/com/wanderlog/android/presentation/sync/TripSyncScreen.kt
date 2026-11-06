package com.wanderlog.android.presentation.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.wanderlog.android.core.ui.component.WanderTopBar
import com.wanderlog.android.data.sync.NearbySyncMode

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TripSyncScreen(
    onBack: () -> Unit,
    viewModel: TripSyncViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val permissionState = rememberMultiplePermissionsState(nearbyPermissions())

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::saveTripFile)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::openTripFile)
    }
    state.pendingFile?.let { bundle ->
        AlertDialog(
            onDismissRequest = { if (!state.fileBusy) viewModel.dismissFilePreview() },
            title = { Text("Import ${bundle.trip.name}?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${bundle.trip.destination} · ${bundle.trip.startDate} to ${bundle.trip.endDate}")
                Text("${bundle.itineraryItems.count { it.metadata.deletedAt == null }} itinerary items · ${bundle.attachments.count { it.metadata.deletedAt == null }} attachments")
                Text("Adds this trip if it is new. For an existing trip, newer records win, including saved deletions. Other trips are kept.")
                state.fileMessage?.let { Text(it) }
            } },
            confirmButton = { TextButton(onClick = viewModel::mergeTripFile, enabled = !state.fileBusy) { Text(if (state.fileBusy) "Importing…" else "Import and merge") } },
            dismissButton = { TextButton(onClick = viewModel::dismissFilePreview, enabled = !state.fileBusy) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            WanderTopBar(
                title = if (state.tripName.isBlank()) "Trip Sync" else "Sync ${state.tripName}",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Share with Google Drive", style = MaterialTheme.typography.titleMedium)
                        Text("Save a trip file to Drive, then share it with another Google account using the Drive app. On the other phone, open the shared file here.")
                        Text("Includes itinerary, budget, packing, notes, cover and booking attachments. API keys are excluded.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            val name = state.tripName.replace(Regex("[^a-zA-Z0-9 _-]"), "").take(60).ifBlank { "Trip" }
                            saveFile.launch("$name-${System.currentTimeMillis()}.wandercraft.json")
                        }, enabled = state.tripId != null && !state.fileBusy && !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                            Text("Save trip to Drive or files")
                        }
                        OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }, enabled = !state.fileBusy && !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                            Text("Open shared trip")
                        }
                        Text("Choose Google Drive in the file picker. If a shared file is missing, download it from Drive first and open it from Downloads. Both phones need a version with trip file sharing.", style = MaterialTheme.typography.bodySmall)
                        Text("Sharing uses snapshots. To send later changes, save and share a new file. There is no automatic background sync.", style = MaterialTheme.typography.bodySmall)
                        if (state.fileBusy) Text("Working on trip file…")
                        state.fileMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item {
                SyncStatusCard(
                    endpointName = state.localEndpointName,
                    mode = state.mode,
                    statusMessage = state.statusMessage,
                    lastSyncSummary = state.lastSyncSummary,
                    connectedPeerName = state.connectedPeerName
                )
            }

            item {
                PermissionCard(
                    hasPermissions = permissionState.allPermissionsGranted,
                    onRequest = { permissionState.launchMultiplePermissionRequest() }
                )
            }

            item {
                SessionControls(
                    enabled = permissionState.allPermissionsGranted && !state.fileBusy && state.pendingFile == null,
                    canHost = state.tripId != null,
                    isBusy = state.isBusy,
                    onHost = viewModel::startHosting,
                    onDiscover = viewModel::startDiscovery,
                    onStop = viewModel::stopSession
                )
            }

            item {
                Text("Nearby devices", style = MaterialTheme.typography.titleMedium)
            }

            if (state.discoveredPeers.isEmpty()) {
                item {
                    Text(
                        if (state.mode == NearbySyncMode.DISCOVERING) "No nearby sync hosts found yet."
                        else "Start discovery to find a nearby device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(state.discoveredPeers, key = { it.endpointId }) { peer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(peer.endpointName, style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(onClick = { viewModel.connect(peer) }, enabled = !state.isBusy && state.mode == NearbySyncMode.DISCOVERING) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }

            item {
                Text("Session log", style = MaterialTheme.typography.titleMedium)
            }

            if (state.logLines.isEmpty()) {
                item {
                    Text("No sync activity yet.")
                }
            } else {
                items(state.logLines) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }

            state.errorMessage?.let { message ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Sync error", style = MaterialTheme.typography.titleSmall)
                            Text(message, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = viewModel::clearError) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(
    endpointName: String,
    mode: NearbySyncMode,
    statusMessage: String,
    lastSyncSummary: String?,
    connectedPeerName: String?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("This device", style = MaterialTheme.typography.titleSmall)
            Text(endpointName.ifBlank { "Preparing endpoint name..." })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(mode.name.lowercase().replaceFirstChar(Char::titlecase)) })
                connectedPeerName?.let { peerName ->
                    AssistChip(onClick = {}, label = { Text("Connected to $peerName") })
                }
            }
            Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
            lastSyncSummary?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun PermissionCard(
    hasPermissions: Boolean,
    onRequest: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Permissions", style = MaterialTheme.typography.titleSmall)
            Text(
                if (hasPermissions) "Nearby sync permissions are granted."
                else "Nearby sync needs Bluetooth and nearby device permissions before it can advertise or discover peers.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (!hasPermissions) {
                Button(onClick = onRequest) {
                    Text("Grant permissions")
                }
            }
        }
    }
}

@Composable
private fun SessionControls(
    enabled: Boolean,
    canHost: Boolean,
    isBusy: Boolean,
    onHost: () -> Unit,
    onDiscover: () -> Unit,
    onStop: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Start sync", style = MaterialTheme.typography.titleSmall)
            Text(
                "One device hosts the trip session. The other discovers it and connects. After connection, the app exchanges manifests and sends the needed bundle automatically.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onHost, enabled = enabled && canHost && !isBusy, modifier = Modifier.weight(1f)) {
                    Text("Host")
                }
                Button(onClick = onDiscover, enabled = enabled && !isBusy, modifier = Modifier.weight(1f)) {
                    Text("Join")
                }
            }
            if (!canHost) {
                Text(
                    "Hosting is only available when you open sync from a specific trip. From the trip list, this screen is join-only so you can receive a hosted trip onto an empty device.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("Stop session")
            }
        }
    }
}

private fun nearbyPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}
