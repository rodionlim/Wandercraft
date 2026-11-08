package com.wanderlog.android.domain.model.sync

import com.squareup.moshi.JsonClass
import com.wanderlog.android.domain.model.ExpenseBreakdownItem
import com.wanderlog.android.domain.model.TravellerProfile

@JsonClass(generateAdapter = true)
data class TripSyncBundle(
    val protocolVersion: Int = TripSyncManifest.CURRENT_PROTOCOL_VERSION,
    val generatedAt: Long,
    val trip: SyncTripPayload,
    val tripDays: List<SyncTripDayPayload> = emptyList(),
    val itineraryItems: List<SyncItineraryItemPayload> = emptyList(),
    val itemAttachmentLinks: List<SyncItemAttachmentLinkPayload> = emptyList(),
    val expenses: List<SyncExpensePayload> = emptyList(),
    val packingItems: List<SyncPackingItemPayload> = emptyList(),
    val tripNotes: List<SyncTripNotePayload> = emptyList(),
    val attachments: List<SyncAttachmentPayload> = emptyList()
) {
    fun toManifest(): TripSyncManifest = TripSyncManifest(
        protocolVersion = protocolVersion,
        tripId = trip.id,
        generatedAt = generatedAt,
        records = buildList {
            add(trip.toManifestRecord())
            addAll(tripDays.map(SyncTripDayPayload::toManifestRecord))
            addAll(itineraryItems.map(SyncItineraryItemPayload::toManifestRecord))
            addAll(itemAttachmentLinks.map(SyncItemAttachmentLinkPayload::toManifestRecord))
            addAll(expenses.map(SyncExpensePayload::toManifestRecord))
            addAll(packingItems.map(SyncPackingItemPayload::toManifestRecord))
            addAll(tripNotes.map(SyncTripNotePayload::toManifestRecord))
            addAll(attachments.map(SyncAttachmentPayload::toManifestRecord))
        }
    )
}

@JsonClass(generateAdapter = true)
data class SyncMetadata(
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val lastModifiedByDeviceId: String
)

@JsonClass(generateAdapter = true)
data class SyncTripPayload(
    val id: String,
    val name: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val coverImageUri: String? = null,
    val coverImageContentHash: String? = null,
    val coverImageContentBase64: String? = null,
    val budgetAmount: Double? = null,
    val currencyCode: String,
    val travellerProfiles: List<TravellerProfile> = emptyList(),
    val metadata: SyncMetadata
)

@JsonClass(generateAdapter = true)
data class SyncTripDayPayload(
    val id: String,
    val tripId: String,
    val date: String,
    val dayNumber: Int,
    val notes: String? = null,
    val metadata: SyncMetadata
)

@JsonClass(generateAdapter = true)
data class SyncItineraryItemPayload(
    val id: String,
    val tripDayId: String,
    val tripId: String,
    val title: String,
    val itemType: String,
    val placeId: String? = null,
    val placeName: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val notes: String? = null,
    val rating: Int? = null,
    val bookingRef: String? = null,
    val linkedExpenseId: String? = null,
    val confirmationUrl: String? = null,
    val sortOrder: Int,
    val metadata: SyncMetadata
)

@JsonClass(generateAdapter = true)
data class SyncItemAttachmentLinkPayload(
    val id: String,
    val tripId: String,
    val itineraryItemId: String,
    val attachmentId: String,
    val linkType: String,
    val metadata: SyncMetadata
)

@JsonClass(generateAdapter = true)
data class SyncExpensePayload(
    val id: String,
    val tripId: String,
    val title: String,
    val amount: Double,
    val currencyCode: String,
    val category: String,
    val date: String? = null,
    val notes: String? = null,
    val breakdown: List<ExpenseBreakdownItem> = emptyList(),
    val createdAt: Long,
    val metadata: SyncMetadata
)

@JsonClass(generateAdapter = true)
data class SyncPackingItemPayload(
    val id: String,
    val tripId: String,
    val title: String,
    val quantity: Int,
    val isChecked: Boolean,
    val travellerName: String? = null,
    val category: String? = null,
    val sortOrder: Int,
    val metadata: SyncMetadata
)

@JsonClass(generateAdapter = true)
data class SyncTripNotePayload(
    val id: String,
    val tripId: String,
    val content: String,
    val isGlobal: Boolean,
    val metadata: SyncMetadata
)

@JsonClass(generateAdapter = true)
data class SyncAttachmentPayload(
    val id: String,
    val tripId: String,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val label: String? = null,
    val tags: List<String> = emptyList(),
    val sizeBytes: Long,
    val createdAt: Long,
    val contentHash: String? = null,
    val contentBase64: String? = null,
    val metadata: SyncMetadata
)

fun SyncTripPayload.toSyncRecord(): TripSyncRecord = TripSyncRecord(
    entityType = SyncEntityType.TRIP,
    id = id,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

fun SyncTripDayPayload.toSyncRecord(): TripSyncRecord = TripSyncRecord(
    entityType = SyncEntityType.TRIP_DAY,
    id = id,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

fun SyncItineraryItemPayload.toSyncRecord(): TripSyncRecord = TripSyncRecord(
    entityType = SyncEntityType.ITINERARY_ITEM,
    id = id,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

fun SyncItemAttachmentLinkPayload.toSyncRecord(): TripSyncRecord = TripSyncRecord(
    entityType = SyncEntityType.ITEM_ATTACHMENT_LINK,
    id = id,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

fun SyncExpensePayload.toSyncRecord(): TripSyncRecord = TripSyncRecord(
    entityType = SyncEntityType.EXPENSE,
    id = id,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

fun SyncPackingItemPayload.toSyncRecord(): TripSyncRecord = TripSyncRecord(
    entityType = SyncEntityType.PACKING_ITEM,
    id = id,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

fun SyncTripNotePayload.toSyncRecord(): TripSyncRecord = TripSyncRecord(
    entityType = SyncEntityType.TRIP_NOTE,
    id = id,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

fun SyncAttachmentPayload.toSyncRecord(): TripSyncRecord = TripSyncRecord(
    entityType = SyncEntityType.ATTACHMENT,
    id = id,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId,
    contentHash = contentHash,
    sizeBytes = sizeBytes
)

private fun SyncTripPayload.toManifestRecord(): TripSyncRecord = toSyncRecord()
private fun SyncTripDayPayload.toManifestRecord(): TripSyncRecord = toSyncRecord()
private fun SyncItineraryItemPayload.toManifestRecord(): TripSyncRecord = toSyncRecord()
private fun SyncItemAttachmentLinkPayload.toManifestRecord(): TripSyncRecord = toSyncRecord()
private fun SyncExpensePayload.toManifestRecord(): TripSyncRecord = toSyncRecord()
private fun SyncPackingItemPayload.toManifestRecord(): TripSyncRecord = toSyncRecord()
private fun SyncTripNotePayload.toManifestRecord(): TripSyncRecord = toSyncRecord()
private fun SyncAttachmentPayload.toManifestRecord(): TripSyncRecord = toSyncRecord()
