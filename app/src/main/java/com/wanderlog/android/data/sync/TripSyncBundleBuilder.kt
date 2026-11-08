package com.wanderlog.android.data.sync

import androidx.room.withTransaction
import com.wanderlog.android.data.local.WanderlogDatabase
import android.content.Context
import android.net.Uri
import com.wanderlog.android.core.util.ExpenseBreakdownCodec
import com.wanderlog.android.core.util.TravellerProfilesCodec
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
import com.wanderlog.android.domain.model.toAttachmentTags
import com.wanderlog.android.domain.model.sync.SyncAttachmentPayload
import com.wanderlog.android.domain.model.sync.SyncExpensePayload
import com.wanderlog.android.domain.model.sync.SyncItemAttachmentLinkPayload
import com.wanderlog.android.domain.model.sync.SyncItineraryItemPayload
import com.wanderlog.android.domain.model.sync.SyncMetadata
import com.wanderlog.android.domain.model.sync.SyncPackingItemPayload
import com.wanderlog.android.domain.model.sync.SyncTripNotePayload
import com.wanderlog.android.domain.model.sync.SyncTripDayPayload
import com.wanderlog.android.domain.model.sync.SyncTripPayload
import com.wanderlog.android.domain.model.sync.TripSyncBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripSyncBundleBuilder @Inject constructor(
    private val database: WanderlogDatabase,
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

    suspend fun buildBundle(
        tripId: String,
        generatedAt: Long = System.currentTimeMillis()
    ): TripSyncBundle? = database.withTransaction { buildSnapshot(tripId, generatedAt) }

    private suspend fun buildSnapshot(tripId: String, generatedAt: Long): TripSyncBundle? {
        val trip = tripDao.getTripByIdIncludingDeleted(tripId) ?: return null
        if (trip.deletedAt != null) {
            return TripSyncBundle(
                generatedAt = generatedAt,
                trip = trip.toSyncPayload(context.filesDir)
            )
        }

        val tripDays = tripDayDao.getDaysForTripForSync(tripId).map(TripDayEntity::toSyncPayload)
        val itineraryItems = itineraryItemDao.getItemsForTripForSync(tripId).map(ItineraryItemEntity::toSyncPayload)
        val itemAttachmentLinks = itineraryItemAttachmentLinkDao.getLinksForTripForSync(tripId)
            .map(ItineraryItemAttachmentLinkEntity::toSyncPayload)
        val expenses = expenseDao.getExpensesForTripForSync(tripId).map(ExpenseEntity::toSyncPayload)
        val packingItems = packingItemDao.getItemsForTripForSync(tripId).map(PackingItemEntity::toSyncPayload)
        val tripNotes = tripNoteDao.getNotesForTripForSync(tripId).map(TripNoteEntity::toSyncPayload)
        val attachments = attachmentDao.getAttachmentsForTripForSync(tripId).map { it.toSyncPayload(context.filesDir) }

        return TripSyncBundle(
            generatedAt = generatedAt,
            trip = trip.toSyncPayload(context.filesDir),
            tripDays = tripDays,
            itineraryItems = itineraryItems,
            itemAttachmentLinks = itemAttachmentLinks,
            expenses = expenses,
            packingItems = packingItems,
            tripNotes = tripNotes,
            attachments = attachments
        )
    }
}

private fun TripEntity.toSyncPayload(filesDir: File): SyncTripPayload {
    val coverFile = coverImageUri?.toLocalCoverFileOrNull(filesDir)
    val coverBytes = coverFile?.takeIf { it.exists() && it.isFile }?.readBytes()

    return SyncTripPayload(
        id = id,
        name = name,
        destination = destination,
        startDate = startDate.toString(),
        endDate = endDate.toString(),
        coverImageUri = coverImageUri,
        coverImageContentHash = coverBytes?.let(::hashBytesSha256),
        coverImageContentBase64 = coverBytes?.let { Base64.getEncoder().encodeToString(it) },
        budgetAmount = budgetAmount,
        currencyCode = currencyCode,
        travellerProfiles = TravellerProfilesCodec.decode(travellerProfilesRaw),
        metadata = SyncMetadata(
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            lastModifiedByDeviceId = lastModifiedByDeviceId
        )
    )
}

private fun TripDayEntity.toSyncPayload(): SyncTripDayPayload = SyncTripDayPayload(
    id = id,
    tripId = tripId,
    date = date.toString(),
    dayNumber = dayNumber,
    notes = notes,
    metadata = SyncMetadata(
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
)

private fun ItineraryItemEntity.toSyncPayload(): SyncItineraryItemPayload = SyncItineraryItemPayload(
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
    metadata = SyncMetadata(
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
)

private fun ItineraryItemAttachmentLinkEntity.toSyncPayload(): SyncItemAttachmentLinkPayload = SyncItemAttachmentLinkPayload(
    id = id,
    tripId = tripId,
    itineraryItemId = itineraryItemId,
    attachmentId = attachmentId,
    linkType = linkType,
    metadata = SyncMetadata(
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
)

private fun ExpenseEntity.toSyncPayload(): SyncExpensePayload = SyncExpensePayload(
    id = id,
    tripId = tripId,
    title = title,
    amount = amount,
    currencyCode = currencyCode,
    category = category,
    date = date?.toString(),
    notes = notes,
    breakdown = ExpenseBreakdownCodec.decode(breakdownRaw),
    createdAt = createdAt,
    metadata = SyncMetadata(
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
)

private fun PackingItemEntity.toSyncPayload(): SyncPackingItemPayload = SyncPackingItemPayload(
    id = id,
    tripId = tripId,
    title = title,
    quantity = quantity,
    isChecked = isChecked,
    travellerName = travellerName,
    category = category,
    sortOrder = sortOrder,
    metadata = SyncMetadata(
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
)

private fun TripNoteEntity.toSyncPayload(): SyncTripNotePayload = SyncTripNotePayload(
    id = id,
    tripId = tripId,
    content = content,
    isGlobal = isGlobal,
    metadata = SyncMetadata(
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastModifiedByDeviceId = lastModifiedByDeviceId
    )
)

private fun AttachmentEntity.toSyncPayload(filesDir: File): SyncAttachmentPayload {
    val file = File(filesDir, localPath)
    val contentBase64 = if (deletedAt != null) {
        null
    } else {
        if (!file.exists() || !file.isFile) null else Base64.getEncoder().encodeToString(file.readBytes())
    }
    val contentHash = if (deletedAt != null) null else hashFileSha256(file)

    return SyncAttachmentPayload(
        id = id,
        tripId = tripId,
        displayName = displayName,
        mimeType = mimeType,
        localPath = localPath,
        label = label,
        tags = tags.toAttachmentTags(),
        sizeBytes = sizeBytes,
        createdAt = createdAt,
        contentHash = contentHash,
        contentBase64 = contentBase64,
        metadata = SyncMetadata(
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            lastModifiedByDeviceId = lastModifiedByDeviceId
        )
    )
}

private fun String.toLocalCoverFileOrNull(filesDir: File): File? {
    val parsed = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    if (parsed.scheme != null && parsed.scheme != "file") return null

    val path = parsed.path ?: return null
    val file = File(path)
    val coverDir = File(filesDir, "trip-covers")
    return file.takeIf { it.canonicalFile.toPath().startsWith(coverDir.canonicalFile.toPath()) }
}
