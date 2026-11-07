package com.wanderlog.android.presentation.attachments

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentViewerScreen(
    onBack: () -> Unit,
    viewModel: AttachmentViewerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val attachment = state.attachment
    var showRenameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(attachment?.displayName ?: "Attachment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (attachment != null) {
                        IconButton(onClick = { showRenameDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename attachment")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
                }

                attachment == null -> {}

                attachment.isImage() && state.file != null -> ZoomableAttachment(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) { zoomModifier ->
                    AsyncImage(
                        model = state.file,
                        contentDescription = attachment.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = zoomModifier.fillMaxSize()
                    )
                }

                attachment.isPdf() && state.pdfPages.isNotEmpty() -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(state.pdfPages) { index, page ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Page ${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ZoomableAttachment(modifier = Modifier.fillMaxWidth()) { zoomModifier ->
                                Image(
                                    bitmap = page.asImageBitmap(),
                                    contentDescription = "${attachment.displayName} page ${index + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = zoomModifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                state.textContent != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(state.textContent!!, style = MaterialTheme.typography.bodyMedium)
                }

                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Preview not supported for this file type.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Type: ${attachment.mimeType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Stored locally at: ${attachment.localPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {} // preserve height
        }
    }

    if (showRenameDialog && attachment != null) {
        var newName by remember(attachment.id, attachment.displayName) { mutableStateOf(attachment.displayName) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename attachment") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(newName)
                        showRenameDialog = false
                    },
                    enabled = newName.trim().isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ZoomableAttachment(
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val updatedScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = updatedScale
        offset = if (updatedScale == 1f) Offset.Zero else offset + panChange
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .transformable(state = transformableState),
        contentAlignment = Alignment.Center
    ) {
        content(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
        )
    }
}
