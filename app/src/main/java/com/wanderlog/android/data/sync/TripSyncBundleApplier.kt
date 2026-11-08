package com.wanderlog.android.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.wanderlog.android.core.util.ExpenseBreakdownCodec
import com.wanderlog.android.core.util.TravellerProfilesCodec
import com.wanderlog.android.data.local.WanderlogDatabase
import com.wanderlog.android.data.local.dao.AttachmentDao
import com.wanderlog.android.data.local.dao.ExpenseDao
import com.wanderlog.android.data.local.dao.ItineraryItemAttachmentLinkDao
import com.wanderlog.android.data.local.dao.ItineraryItemDao
import com.wanderlog.android.data.local.dao.PackingItemDao
import com.wanderlog.android.data.local.dao.TripNoteDao
import com.wanderlog.android.data.local.dao.TripDao
import com.wanderlog.android.data.local.dao.TripDayDao
import com.wanderlog.android.data.local.entity.AttachmentEntity
import com.wanderlog.android.data.local.entity.ExpenseEntity
import com.wanderlog.android.data.local.entity.ItineraryItemAttachmentLinkEntity
import com.wanderlog.android.data.local.entity.ItineraryItemEntity
import com.wanderlog.android.data.local.entity.PackingItemEntity
import com.wanderlog.android.data.local.entity.TripNoteEntity
import com.wanderlog.android.data.local.entity.TripDayEntity
import com.wanderlog.android.data.local.entity.TripEntity
import com.wanderlog.android.domain.model.toStoredAttachmentTags
import com.wanderlog.android.domain.model.sync.SyncAttachmentPayload
import com.wanderlog.android.domain.model.sync.SyncExpensePayload
import com.wanderlog.android.domain.model.sync.SyncItemAttachmentLinkPayload
import com.wanderlog.android.domain.model.sync.SyncItineraryItemPayload
import com.wanderlog.android.domain.model.sync.SyncPackingItemPayload
import com.wanderlog.android.domain.model.sync.SyncTripNotePayload
import com.wanderlog.android.domain.model.sync.SyncTripDayPayload
import com.wanderlog.android.domain.model.sync.SyncTripPayload
import com.wanderlog.android.domain.model.sync.TripSyncBundle
import com.wanderlog.android.domain.model.sync.TripSyncManifest
import com.wanderlog.android.domain.model.sync.toSyncRecord
import com.wanderlog.android.domain.usecase.sync.SyncMergePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class TripSyncApplyResult(
    val appliedRecords: Int,
    val skippedRecords: Int
)

@Singleton
class TripSyncBundleApplier @Inject constructor(
    private val database: WanderlogDatabase?,
    private val tripDao: TripDao,
    private val tripDayDao: TripDayDao,
    private val itineraryItemDao: ItineraryItemDao,
    private val itineraryItemAttachmentLinkDao: ItineraryItemAttachmentLinkDao,
    private val expenseDao: ExpenseDao,
    private val packingItemDao: PackingItemDao,
    private val tripNoteDao: TripNoteDao,
    private val attachmentDao: AttachmentDao,
    @ApplicationContext private val context: Context
) {

    suspend fun applyBundle(bundle: TripSyncBundle): TripSyncApplyResult =
        requireNotNull(database).withTransaction { applyBundleInternal(bundle) }

    internal suspend fun applyBundleInternal(bundle: TripSyncBundle): TripSyncApplyResult {
        require(bundle.protocolVersion == TripSyncManifest.CURRENT_PROTOCOL_VERSION) {
            "Unsupported sync protocol version ${bundle.protocolVersion}"
        }

        validateSyncBundle(bundle)
        // Validate ownership before any writes, even when an incoming ID already exists.
        val existingDays = tripDayDao.getDaysForTripForSync(bundle.trip.id)
        for (day in bundle.tripDays) require(existingDays.none { it.date.toString() == day.date && it.id != day.id }) {
            "The devices have different day records for ${day.date}. Resolve the duplicate trip days before retrying."
        }
        for (day in bundle.tripDays) require(tripDayDao.getByIdIncludingDeleted(day.id)?.tripId.let { it == null || it == bundle.trip.id }) { "Trip day belongs to another trip" }
        for (item in bundle.itineraryItems) {
            require(itineraryItemDao.getByIdIncludingDeleted(item.id)?.tripId.let { it == null || it == bundle.trip.id }) { "Item belongs to another trip" }
            val dayTrip = bundle.tripDays.firstOrNull { it.id == item.tripDayId }?.tripId
                ?: tripDayDao.getByIdIncludingDeleted(item.tripDayId)?.tripId
            require(dayTrip == bundle.trip.id) { "Itinerary item has no matching trip day" }
        }
        for (entry in bundle.expenses) require(expenseDao.getByIdIncludingDeleted(entry.id)?.tripId.let { it == null || it == bundle.trip.id }) { "Expense belongs to another trip" }
        for (entry in bundle.packingItems) require(packingItemDao.getByIdIncludingDeleted(entry.id)?.tripId.let { it == null || it == bundle.trip.id }) { "Packing item belongs to another trip" }
        for (entry in bundle.tripNotes) require(tripNoteDao.getByIdIncludingDeleted(entry.id)?.tripId.let { it == null || it == bundle.trip.id }) { "Note belongs to another trip" }
        for (entry in bundle.attachments) require(attachmentDao.getByIdIncludingDeleted(entry.id)?.tripId.let { it == null || it == bundle.trip.id }) { "Attachment belongs to another trip" }
        for (entry in bundle.itemAttachmentLinks) {
            require(itineraryItemAttachmentLinkDao.getByIdIncludingDeleted(entry.id)?.tripId.let { it == null || it == bundle.trip.id }) { "Link belongs to another trip" }
            val itemTrip = bundle.itineraryItems.firstOrNull { it.id == entry.itineraryItemId }?.tripId
                ?: itineraryItemDao.getByIdIncludingDeleted(entry.itineraryItemId)?.tripId
            val attachmentTrip = bundle.attachments.firstOrNull { it.id == entry.attachmentId }?.tripId
                ?: attachmentDao.getByIdIncludingDeleted(entry.attachmentId)?.tripId
            require(itemTrip == bundle.trip.id && attachmentTrip == bundle.trip.id) { "Attachment link references missing or unrelated records" }
        }

        var applied = 0
        var skipped = 0

        if (applyTrip(bundle.trip)) applied++ else skipped++

        for (tripDay in bundle.tripDays) {
            if (applyTripDay(tripDay)) applied++ else skipped++
        }
        for (expense in bundle.expenses) {
            if (applyExpense(expense)) applied++ else skipped++
        }
        for (item in bundle.itineraryItems) {
            if (applyItineraryItem(item)) applied++ else skipped++
        }
        for (attachment in bundle.attachments) {
            if (applyAttachment(attachment)) applied++ else skipped++
        }
        for (itemAttachmentLink in bundle.itemAttachmentLinks) {
            if (applyItemAttachmentLink(itemAttachmentLink)) applied++ else skipped++
        }
        for (packingItem in bundle.packingItems) {
            if (applyPackingItem(packingItem)) applied++ else skipped++
        }
        for (tripNote in bundle.tripNotes) {
            if (applyTripNote(tripNote)) applied++ else skipped++
        }

        return TripSyncApplyResult(appliedRecords = applied, skippedRecords = skipped)
    }

    private suspend fun applyTrip(payload: SyncTripPayload): Boolean {
        val local = tripDao.getTripByIdIncludingDeleted(payload.id)?.toSyncRecord()
        if (!SyncMergePolicy.shouldApplyIncoming(local, payload.toSyncRecord())) return false

        val existing = tripDao.getTripByIdIncludingDeleted(payload.id)
        val resolvedCoverImageUri = materializeTripCover(context, payload, existing?.coverImageUri)
        tripDao.insertTrip(
            payload.toEntity(
                createdAt = existing?.createdAt ?: payload.metadata.updatedAt,
                resolvedCoverImageUri = resolvedCoverImageUri
            )
        )
        return true
    }

    private suspend fun applyTripDay(payload: SyncTripDayPayload): Boolean {
        val local = tripDayDao.getByIdIncludingDeleted(payload.id)?.toSyncRecord()
        if (!SyncMergePolicy.shouldApplyIncoming(local, payload.toSyncRecord())) return false

        val existing = tripDayDao.getByIdIncludingDeleted(payload.id)
        tripDayDao.insertAll(listOf(payload.toEntity(existing?.createdAt ?: payload.metadata.updatedAt)))
        return true
    }

    private suspend fun applyExpense(payload: SyncExpensePayload): Boolean {
        val local = expenseDao.getByIdIncludingDeleted(payload.id)?.toSyncRecord()
        if (!SyncMergePolicy.shouldApplyIncoming(local, payload.toSyncRecord())) return false

        expenseDao.insertExpense(payload.toEntity())
        return true
    }

    private suspend fun applyItineraryItem(payload: SyncItineraryItemPayload): Boolean {
        val local = itineraryItemDao.getByIdIncludingDeleted(payload.id)?.toSyncRecord()
        if (!SyncMergePolicy.shouldApplyIncoming(local, payload.toSyncRecord())) return false

        val existing = itineraryItemDao.getByIdIncludingDeleted(payload.id)
        itineraryItemDao.insertItem(payload.toEntity(existing?.createdAt ?: payload.metadata.updatedAt))
        return true
    }

    private suspend fun applyPackingItem(payload: SyncPackingItemPayload): Boolean {
        val local = packingItemDao.getByIdIncludingDeleted(payload.id)?.toSyncRecord()
        if (!SyncMergePolicy.shouldApplyIncoming(local, payload.toSyncRecord())) return false

        val existing = packingItemDao.getByIdIncludingDeleted(payload.id)
        packingItemDao.insertItem(payload.toEntity(existing?.createdAt ?: payload.metadata.updatedAt))
        return true
    }

    private suspend fun applyTripNote(payload: SyncTripNotePayload): Boolean {
        val local = tripNoteDao.getByIdIncludingDeleted(payload.id)?.toSyncRecord()
        if (!SyncMergePolicy.shouldApplyIncoming(local, payload.toSyncRecord())) return false

        val existing = tripNoteDao.getByIdIncludingDeleted(payload.id)
        tripNoteDao.insertNote(payload.toEntity(existing?.createdAt ?: payload.metadata.updatedAt))
        return true
    }

    private suspend fun applyItemAttachmentLink(payload: SyncItemAttachmentLinkPayload): Boolean {
        val local = itineraryItemAttachmentLinkDao.getByIdIncludingDeleted(payload.id)?.toSyncPayload()?.toSyncRecord()
        if (!SyncMergePolicy.shouldApplyIncoming(local, payload.toSyncRecord())) return false

        val existing = itineraryItemAttachmentLinkDao.getByIdIncludingDeleted(payload.id)
        itineraryItemAttachmentLinkDao.insert(
            payload.toEntity(existing?.createdAt ?: payload.metadata.updatedAt)
        )
        return true
    }

    private suspend fun applyAttachment(payload: SyncAttachmentPayload): Boolean {
        val localEntity = attachmentDao.getByIdIncludingDeleted(payload.id)
        val local = localEntity?.toSyncRecord(contentHash = if (localEntity.deletedAt != null) null else hashFileSha256(File(context.filesDir, localEntity.localPath)))
        if (!SyncMergePolicy.shouldApplyIncoming(local, payload.toSyncRecord())) return false

        if (payload.metadata.deletedAt == null && payload.contentBase64 == null) {
            require(localEntity != null && payload.contentHash != null && local?.contentHash == payload.contentHash) {
                "The other device is missing file content for ${payload.displayName}. Restore the file there and retry."
            }
        }
        // Immutable content files keep Room rollback from corrupting an older saved file.
        // Tombstones hide rows; physical cleanup must happen outside the merge transaction.
        val resolvedPath = if (payload.metadata.deletedAt == null && payload.contentBase64 != null) {
            val decoded = Base64.getDecoder().decode(payload.contentBase64)
            val relativePath = "attachments/sync/${hashBytesSha256(payload.id.toByteArray())}/${hashBytesSha256(decoded)}"
            val targetFile = File(context.filesDir, relativePath)
            targetFile.parentFile?.mkdirs()
            if (!targetFile.exists()) {
                val staging = File.createTempFile("sync-", ".tmp", targetFile.parentFile)
                try {
                    staging.writeBytes(decoded)
                    check(staging.renameTo(targetFile)) { "Unable to save attachment" }
                } finally { staging.delete() }
            }
            relativePath
        } else localEntity?.localPath ?: payload.localPath

        val entity = payload.toEntity().copy(localPath = resolvedPath)
        if (localEntity == null) {
            attachmentDao.insert(entity)
        } else {
            attachmentDao.update(entity)
        }
        return true
    }
}

private fun materializeTripCover(context: Context, payload: SyncTripPayload, existingUri: String?): String? {
    val coverDir = File(context.filesDir, "trip-covers").apply { mkdirs() }
    val coverKey = hashBytesSha256(payload.id.toByteArray())
    val targetFile = File(coverDir, "$coverKey-${payload.coverImageContentBase64?.let { hashBytesSha256(Base64.getDecoder().decode(it)) } ?: "missing"}.jpg")

    if (payload.coverImageContentBase64 != null) {
        val decoded = Base64.getDecoder().decode(payload.coverImageContentBase64)
        payload.coverImageContentHash?.let { expectedHash ->
            require(hashBytesSha256(decoded) == expectedHash) {
                "Trip cover content hash mismatch for ${payload.id}"
            }
        }
        if (!targetFile.exists()) {
            val staging = File.createTempFile("cover-", ".tmp", coverDir)
            try { staging.writeBytes(decoded); check(staging.renameTo(targetFile)) { "Unable to save trip cover" } }
            finally { staging.delete() }
        }
        return targetFile.toURI().toString()
    }

    if (payload.coverImageUri.isNullOrBlank()) {
        return null
    }

    return when {
        payload.coverImageUri.startsWith("http://") ||
            payload.coverImageUri.startsWith("https://") -> payload.coverImageUri
        targetFile.exists() -> targetFile.toURI().toString()
        else -> existingUri
    }
}

private fun SyncTripPayload.toEntity(createdAt: Long, resolvedCoverImageUri: String?): TripEntity = TripEntity(
    id = id,
    name = name,
    destination = destination,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    coverImageUri = resolvedCoverImageUri,
    budgetAmount = budgetAmount,
    currencyCode = currencyCode,
    travellerProfilesRaw = TravellerProfilesCodec.encode(travellerProfiles),
    createdAt = createdAt,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

private fun SyncTripDayPayload.toEntity(createdAt: Long): TripDayEntity = TripDayEntity(
    id = id,
    tripId = tripId,
    date = LocalDate.parse(date),
    dayNumber = dayNumber,
    notes = notes,
    createdAt = createdAt,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

private fun SyncExpensePayload.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    tripId = tripId,
    title = title,
    amount = amount,
    currencyCode = currencyCode,
    category = category,
    date = date?.let(LocalDate::parse),
    notes = notes,
    breakdownRaw = ExpenseBreakdownCodec.encode(breakdown),
    createdAt = createdAt,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

private fun SyncItineraryItemPayload.toEntity(createdAt: Long): ItineraryItemEntity = ItineraryItemEntity(
    id = id,
    tripDayId = tripDayId,
    tripId = tripId,
    title = title,
    itemType = itemType,
    placeId = placeId,
    placeName = placeName,
    address = address,
    latitude = latitude,
    longitude = longitude,
    startTime = startTime,
    endTime = endTime,
    notes = notes,
    rating = rating,
    bookingRef = bookingRef,
    linkedExpenseId = linkedExpenseId,
    confirmationUrl = confirmationUrl,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

private fun SyncPackingItemPayload.toEntity(createdAt: Long): PackingItemEntity = PackingItemEntity(
    id = id,
    tripId = tripId,
    title = title,
    quantity = quantity,
    isChecked = isChecked,
    travellerName = travellerName,
    category = category,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

private fun SyncTripNotePayload.toEntity(createdAt: Long): TripNoteEntity = TripNoteEntity(
    id = id,
    tripId = tripId,
    content = content,
    isGlobal = isGlobal,
    createdAt = createdAt,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)

private fun SyncItemAttachmentLinkPayload.toEntity(createdAt: Long): ItineraryItemAttachmentLinkEntity =
    ItineraryItemAttachmentLinkEntity(
        id = id,
        tripId = tripId,
        itineraryItemId = itineraryItemId,
        attachmentId = attachmentId,
        linkType = linkType,
        createdAt = createdAt,
        updatedAt = metadata.updatedAt,
        deletedAt = metadata.deletedAt,
        lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
    )

private fun ItineraryItemAttachmentLinkEntity.toSyncPayload(): SyncItemAttachmentLinkPayload = SyncItemAttachmentLinkPayload(
    id = id,
    tripId = tripId,
    itineraryItemId = itineraryItemId,
    attachmentId = attachmentId,
    linkType = linkType,
    metadata = com.wanderlog.android.domain.model.sync.SyncMetadata(
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
)

private fun SyncAttachmentPayload.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    tripId = tripId,
    displayName = displayName,
    mimeType = mimeType,
    localPath = localPath,
    label = label,
    tags = tags.toStoredAttachmentTags(),
    sizeBytes = sizeBytes,
    createdAt = createdAt,
    updatedAt = metadata.updatedAt,
    deletedAt = metadata.deletedAt,
    lastModifiedByDeviceId = metadata.lastModifiedByDeviceId
)
