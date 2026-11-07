package com.wanderlog.android.presentation.itinerary.attachments

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanderlog.android.domain.model.Attachment
import com.wanderlog.android.domain.model.ItineraryItemType
import com.wanderlog.android.domain.model.ItemAttachmentLinkType
import com.wanderlog.android.domain.model.ItineraryItemAttachment
import com.wanderlog.android.domain.model.defaultAttachmentTags
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemAttachmentsUiState(
    val itemTitle: String = "Attachments",
    val isImporting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ItemAttachmentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val attachmentRepository: AttachmentRepository,
    private val itineraryRepository: ItineraryRepository,
    private val itineraryItemAttachmentRepository: ItineraryItemAttachmentRepository
) : ViewModel() {

    val tripId: String = savedStateHandle.get<String>(Screen.ItemAttachments.ARG_TRIP_ID)!!
    val itemId: String = savedStateHandle.get<String>(Screen.ItemAttachments.ARG_ITEM_ID)!!

    val attachments: StateFlow<List<ItineraryItemAttachment>> =
        itineraryItemAttachmentRepository.getAttachmentsForItem(itemId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(ItemAttachmentsUiState())
    val state: StateFlow<ItemAttachmentsUiState> = _state.asStateFlow()
    private var itemType: ItineraryItemType? = null

    init {
        viewModelScope.launch {
            val item = itineraryRepository.getItemsForTrip(tripId).first().firstOrNull { it.id == itemId }
            itemType = item?.itemType
            _state.update { current -> current.copy(itemTitle = item?.title ?: "Attachments") }
        }
    }

    fun importAttachment(uri: Uri) {
        importAttachments(listOf(uri))
    }

    fun importAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, error = null) }
            val errors = mutableListOf<String>()
            for (uri in uris) {
                runCatching {
                    itineraryItemAttachmentRepository.importAttachmentForItem(
                        tripId = tripId,
                        itemId = itemId,
                        uri = uri,
                        label = state.value.itemTitle,
                        tags = itemType?.defaultAttachmentTags().orEmpty(),
                        linkType = ItemAttachmentLinkType.MANUAL
                    )
                }.onFailure { error ->
                    errors += (error.message ?: "Failed to add attachment")
                }
            }
            _state.update {
                it.copy(
                    isImporting = false,
                    error = errors.distinct().joinToString("\n").ifBlank { null }
                )
            }
        }
    }

    fun updateAttachment(attachment: Attachment, displayName: String, tagsText: String) {
        val normalizedName = displayName.trim()
        if (normalizedName.isBlank()) {
            _state.update { it.copy(error = "Attachment name cannot be blank") }
            return
        }

        viewModelScope.launch {
            runCatching {
                attachmentRepository.update(
                    attachment.copy(
                        displayName = normalizedName,
                        tags = normalizeAttachmentTags(tagsText.split(','))
                    )
                )
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to rename attachment") }
            }
        }
    }

    fun removeAttachment(linkedAttachment: ItineraryItemAttachment) {
        if (linkedAttachment.linkType != ItemAttachmentLinkType.MANUAL) {
            return
        }

        viewModelScope.launch {
            runCatching {
                itineraryItemAttachmentRepository.removeAttachmentFromItem(itemId, linkedAttachment.attachment.id)
                if (itineraryItemAttachmentRepository.getItemIdsForAttachment(linkedAttachment.attachment.id).isEmpty()) {
                    attachmentRepository.delete(linkedAttachment.attachment)
                }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to remove attachment") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
