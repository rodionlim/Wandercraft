package com.wanderlog.android.presentation.itinerary.attachments

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wanderlog.android.core.ui.component.ConfirmDialog
import com.wanderlog.android.core.ui.component.WanderTopBar
import com.wanderlog.android.domain.model.Attachment
import com.wanderlog.android.domain.model.ItemAttachmentLinkType
import com.wanderlog.android.domain.model.ItineraryItemAttachment
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemAttachmentsScreen(
    onBack: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    viewModel: ItemAttachmentsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val attachments by viewModel.attachments.collectAsState()
    var pendingRemoval by remember { mutableStateOf<ItineraryItemAttachment?>(null) }
    var editingAttachment by remember { mutableStateOf<Attachment?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importAttachments(uris)
        }
    }

    Scaffold(
        topBar = {
            WanderTopBar(title = state.itemTitle, onBack = onBack)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(
                    arrayOf(
                        "text/markdown",
                        "text/plain",
                        "text/*",
                        "application/pdf",
                        "image/*",
                        "application/*"
                    )
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add attachment")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isImporting -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Adding attachment...")
                }

                attachments.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No attachments on this item yet",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Add boarding passes, screenshots, confirmations, or notes for this item.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(attachments, key = { it.attachment.id }) { linkedAttachment ->
                        ItemAttachmentRow(
                            linkedAttachment = linkedAttachment,
                            onOpen = { onOpenAttachment(linkedAttachment.attachment.id) },
                            onEdit = { editingAttachment = linkedAttachment.attachment },
                            onRemove = if (linkedAttachment.linkType == ItemAttachmentLinkType.MANUAL) {
                                { pendingRemoval = linkedAttachment }
                            } else {
                                null
                            }
                        )
                    }
                }
            }

            state.error?.let { error ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    androidx.compose.material3.TextButton(onClick = viewModel::clearError) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }

    editingAttachment?.let { attachment ->
        ItemAttachmentEditDialog(
            attachment = attachment,
            onDismiss = { editingAttachment = null },
            onSave = { displayName, tagsText ->
                viewModel.updateAttachment(attachment, displayName, tagsText)
                editingAttachment = null
            }
        )
    }

    pendingRemoval?.let { linkedAttachment ->
        ConfirmDialog(
            title = "Remove attachment",
            message = "Remove \"${linkedAttachment.attachment.displayName}\" from this itinerary item?",
            onConfirm = {
                viewModel.removeAttachment(linkedAttachment)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null }
        )
    }
}

@Composable
private fun ItemAttachmentRow(
    linkedAttachment: ItineraryItemAttachment,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: (() -> Unit)?
) {
    val attachment = linkedAttachment.attachment
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconFor(attachment),
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    text = if (linkedAttachment.linkType == ItemAttachmentLinkType.IMPORT_SOURCE) {
                        "Imported with this booking"
                    } else {
                        "Added to this item"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${humanSize(attachment.sizeBytes)} • ${formatDate(attachment.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                attachment.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                    Text(
                        text = tags.joinToString("  ") { tag -> "#$tag" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Rename or tag")
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove attachment")
                }
            }
        }
    }
}

@Composable
private fun ItemAttachmentEditDialog(
    attachment: Attachment,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var displayName by remember(attachment.id) { mutableStateOf(attachment.displayName) }
    var tagsText by remember(attachment.id) { mutableStateOf(attachment.tags.joinToString(", ")) }
    val isValid = displayName.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename attachment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags") },
                    supportingText = { Text("Separate tags with commas") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(displayName.trim(), tagsText) }, enabled = isValid) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun iconFor(attachment: Attachment): ImageVector = when {
    attachment.mimeType.startsWith("image/") -> Icons.Default.Image
    attachment.mimeType == "application/pdf" || attachment.displayName.endsWith(".pdf", ignoreCase = true) -> Icons.Default.PictureAsPdf
    attachment.mimeType.startsWith("text/") || attachment.displayName.endsWith(".md", ignoreCase = true) || attachment.displayName.endsWith(".txt", ignoreCase = true) -> Icons.Default.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))
