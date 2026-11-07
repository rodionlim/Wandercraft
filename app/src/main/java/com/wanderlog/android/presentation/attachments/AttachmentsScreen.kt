package com.wanderlog.android.presentation.attachments

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wanderlog.android.core.ui.component.ConfirmDialog
import com.wanderlog.android.domain.model.Attachment
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.ItineraryItemType
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentsScreen(
    onBack: () -> Unit,
    onOpenAttachment: (attachmentId: String) -> Unit,
    pickOnOpen: Boolean = false,
    viewModel: AttachmentsViewModel = hiltViewModel()
) {
    val attachments by viewModel.attachments.collectAsState()
    val itineraryItems by viewModel.itineraryItems.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val error by viewModel.error.collectAsState()
    var toDelete by remember { mutableStateOf<Attachment?>(null) }
    var editingAttachment by remember { mutableStateOf<Attachment?>(null) }
    var relinkingAttachment by remember { mutableStateOf<Attachment?>(null) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var awaitingInitialPickResult by rememberSaveable { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val filteredAttachments = remember(attachments, searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isBlank()) {
            attachments
        } else {
            attachments.filter { attachment ->
                attachment.displayName.lowercase().contains(query) ||
                    attachment.label.orEmpty().lowercase().contains(query) ||
                    attachment.tags.any { tag -> tag.lowercase().contains(query) }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val wasInitialPick = awaitingInitialPickResult
        awaitingInitialPickResult = false
        if (uris.isNotEmpty()) {
            viewModel.importAll(uris, null)
        } else if (wasInitialPick) {
            onBack()
        }
    }

    LaunchedEffect(pickOnOpen) {
        if (pickOnOpen && viewModel.consumePickOnOpen()) {
            awaitingInitialPickResult = true
            picker.launch(
                arrayOf(
                    "text/markdown", "text/plain", "text/*",
                    "application/pdf",
                    "image/*",
                    "application/*"
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attachments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchVisible) "Close search" else "Search attachments"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(
                    arrayOf(
                        "text/markdown", "text/plain", "text/*",
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (searchVisible) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    label = { Text("Search attachments") },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isImporting -> Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Importing…")
                    }

                    attachments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.height(64.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No attachments yet", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Tap + to add a markdown note, PDF, or image.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    filteredAttachments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No attachments match your search.", style = MaterialTheme.typography.bodyLarge)
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredAttachments, key = { it.id }) { attachment ->
                            AttachmentRow(
                                attachment = attachment,
                                onOpen = { onOpenAttachment(attachment.id) },
                                onRelink = { relinkingAttachment = attachment },
                                onEdit = { editingAttachment = attachment },
                                onDelete = { toDelete = attachment }
                            )
                        }
                    }
                }

                error?.let { message ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::clearError) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }

    editingAttachment?.let { attachment ->
        AttachmentEditDialog(
            attachment = attachment,
            onDismiss = { editingAttachment = null },
            onSave = { displayName, tagsText ->
                viewModel.updateAttachment(attachment, displayName, tagsText)
                editingAttachment = null
            }
        )
    }

    relinkingAttachment?.let { attachment ->
        AttachmentRelinkDialog(
            attachment = attachment,
            itineraryItems = itineraryItems,
            onDismiss = { relinkingAttachment = null },
            onRelink = { item ->
                viewModel.relinkAttachment(attachment, item.id)
                relinkingAttachment = null
            }
        )
    }

    toDelete?.let { attachment ->
        ConfirmDialog(
            title = "Delete attachment",
            message = "Remove \"${attachment.displayName}\"?",
            onConfirm = { viewModel.delete(attachment); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
private fun AttachmentRow(
    attachment: Attachment,
    onOpen: () -> Unit,
    onRelink: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                iconFor(attachment),
                contentDescription = null,
                modifier = Modifier.height(32.dp).padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(attachment.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    "${humanSize(attachment.sizeBytes)} • ${formatDate(attachment.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                attachment.label?.takeIf { it.isNotBlank() }?.let { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
                attachment.tags.takeIf { it.isNotEmpty() }?.let { tags ->
                    Text(
                        text = tags.joinToString("  ") { tag -> "#$tag" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onRelink) {
                Icon(Icons.Default.Link, contentDescription = "Link to itinerary item")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Rename or tag")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun AttachmentRelinkDialog(
    attachment: Attachment,
    itineraryItems: List<ItineraryItem>,
    onDismiss: () -> Unit,
    onRelink: (ItineraryItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link attachment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose where to link \"${attachment.displayName}\".")
                if (itineraryItems.isEmpty()) {
                    Text(
                        text = "Add an itinerary item first, then come back here to link this attachment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(itineraryItems, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRelink(item) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = itemLinkSubtitle(item),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (itineraryItems.isEmpty()) "Close" else "Cancel")
            }
        }
    )
}

@Composable
private fun AttachmentEditDialog(
    attachment: Attachment,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var displayName by remember(attachment.id) { mutableStateOf(attachment.displayName) }
    var tagsText by remember(attachment.id) { mutableStateOf(attachment.tags.joinToString(", ")) }
    val isValid = displayName.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit attachment") },
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
    attachment.isImage() -> Icons.Default.Image
    attachment.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    attachment.isTextLike() -> Icons.Default.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatDate(epochMs: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMs))

private fun itemLinkSubtitle(item: ItineraryItem): String {
    val parts = buildList {
        add(item.itemType.name.lowercase().replaceFirstChar { it.uppercase() })
        item.startTime?.takeIf { it.isNotBlank() }?.let(::add)
        item.place?.name?.takeIf { it.isNotBlank() }?.let(::add)
    }
    return parts.joinToString(" • ")
}
