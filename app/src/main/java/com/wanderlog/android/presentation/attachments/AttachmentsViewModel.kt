package com.wanderlog.android.presentation.attachments

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanderlog.android.domain.model.Attachment
import com.wanderlog.android.domain.model.ItemAttachmentLinkType
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.normalizeAttachmentTags
import com.wanderlog.android.domain.repository.AttachmentRepository
import com.wanderlog.android.domain.repository.ItineraryItemAttachmentRepository
import com.wanderlog.android.domain.repository.ItineraryRepository
import com.wanderlog.android.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttachmentsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: AttachmentRepository,
    itineraryRepository: ItineraryRepository,
    private val itineraryItemAttachmentRepository: ItineraryItemAttachmentRepository
) : ViewModel() {

    val tripId: String = savedStateHandle.get<String>(Screen.Attachments.ARG_TRIP_ID)!!

    fun consumePickOnOpen(): Boolean {
        val shouldPick = savedStateHandle.get<Boolean>(Screen.Attachments.ARG_PICK_ON_OPEN) == true
        val alreadyConsumed = savedStateHandle.get<Boolean>(KEY_CONSUMED_PICK_ON_OPEN) == true
        if (!shouldPick || alreadyConsumed) return false
        savedStateHandle[KEY_CONSUMED_PICK_ON_OPEN] = true
        savedStateHandle[Screen.Attachments.ARG_PICK_ON_OPEN] = false
        return true
    }

    val attachments: StateFlow<List<Attachment>> = repository.getAttachmentsForTrip(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val itineraryItems: StateFlow<List<ItineraryItem>> = itineraryRepository.getItemsForTrip(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    fun import(uri: Uri, label: String?, tags: List<String> = emptyList()) {
        importAll(listOf(uri), label, tags)
    }

    fun importAll(uris: List<Uri>, label: String?, tags: List<String> = emptyList()) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImporting.value = true
            _error.value = null
            val errors = mutableListOf<String>()
            for (uri in uris) {
                runCatching { repository.importFromUri(tripId, uri, label, tags) }
                    .onFailure { errors += (it.message ?: "Import failed") }
            }
            if (errors.isNotEmpty()) {
                _error.value = errors.distinct().joinToString("\n")
            }
            _isImporting.value = false
        }
    }

    fun updateAttachment(attachment: Attachment, displayName: String, tagsText: String) {
        val normalizedName = displayName.trim()
        if (normalizedName.isBlank()) {
            _error.value = "Attachment name cannot be blank"
            return
        }

        viewModelScope.launch {
            runCatching {
                repository.update(
                    attachment.copy(
                        displayName = normalizedName,
                        tags = normalizeAttachmentTags(tagsText.split(','))
                    )
                )
            }.onFailure { _error.value = it.message ?: "Update failed" }
        }
    }

    fun relinkAttachment(attachment: Attachment, itemId: String) {
        viewModelScope.launch {
            runCatching {
                itineraryItemAttachmentRepository.addAttachmentToItem(
                    tripId = tripId,
                    itemId = itemId,
                    attachmentId = attachment.id,
                    linkType = ItemAttachmentLinkType.MANUAL
                )
            }.onFailure { _error.value = it.message ?: "Failed to link attachment" }
        }
    }

    fun delete(attachment: Attachment) {
        viewModelScope.launch { repository.delete(attachment) }
    }

    fun clearError() { _error.value = null }

    companion object {
        private const val KEY_CONSUMED_PICK_ON_OPEN = "consumed_pick_on_open"
    }
}
