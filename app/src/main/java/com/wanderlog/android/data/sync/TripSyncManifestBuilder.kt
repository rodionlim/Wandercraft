package com.wanderlog.android.data.sync

import androidx.room.withTransaction
import com.wanderlog.android.data.local.WanderlogDatabase
import com.wanderlog.android.data.local.dao.TripNoteDao
import com.wanderlog.android.data.local.dao.ItineraryItemAttachmentLinkDao
import com.wanderlog.android.domain.model.sync.SyncEntityType
import com.wanderlog.android.domain.model.sync.TripSyncRecord
import android.content.Context
import com.wanderlog.android.data.local.dao.AttachmentDao
import com.wanderlog.android.data.local.dao.ExpenseDao
import com.wanderlog.android.data.local.dao.ItineraryItemDao
import com.wanderlog.android.data.local.dao.PackingItemDao
import com.wanderlog.android.data.local.dao.TripDao
import com.wanderlog.android.data.local.dao.TripDayDao
import com.wanderlog.android.data.local.entity.AttachmentEntity
import com.wanderlog.android.domain.model.sync.TripSyncManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripSyncManifestBuilder @Inject constructor(
    private val database: WanderlogDatabase,
    private val tripNoteDao: TripNoteDao,
    private val linkDao: ItineraryItemAttachmentLinkDao,
    private val tripDao: TripDao,
    private val tripDayDao: TripDayDao,
    private val itineraryItemDao: ItineraryItemDao,
    private val expenseDao: ExpenseDao,
    private val packingItemDao: PackingItemDao,
    private val attachmentDao: AttachmentDao,
    @ApplicationContext private val context: Context
) {

    suspend fun buildManifest(
        tripId: String,
        generatedAt: Long = System.currentTimeMillis()
    ): TripSyncManifest? = database.withTransaction { buildSnapshot(tripId, generatedAt) }

    private suspend fun buildSnapshot(tripId: String, generatedAt: Long): TripSyncManifest? {
        val trip = tripDao.getTripByIdIncludingDeleted(tripId) ?: return null

        if (trip.deletedAt != null) {
            return TripSyncManifest(
                tripId = tripId,
                generatedAt = generatedAt,
                records = listOf(trip.toSyncRecord())
            )
        }

        val tripDays = tripDayDao.getDaysForTripForSync(tripId)
        val itineraryItems = itineraryItemDao.getItemsForTripForSync(tripId)
        val expenses = expenseDao.getExpensesForTripForSync(tripId)
        val packingItems = packingItemDao.getItemsForTripForSync(tripId)
        val attachments = attachmentDao.getAttachmentsForTripForSync(tripId)

        val notes = tripNoteDao.getNotesForTripForSync(tripId)
        val links = linkDao.getLinksForTripForSync(tripId)
        val records = buildList {
            addAll(notes.map { it.toSyncRecord() })
            addAll(links.map { TripSyncRecord(SyncEntityType.ITEM_ATTACHMENT_LINK, it.id, it.updatedAt, it.deletedAt, it.lastModifiedByDeviceId) })
            add(trip.toSyncRecord())
            addAll(tripDays.map { it.toSyncRecord() })
            addAll(itineraryItems.map { it.toSyncRecord() })
            addAll(expenses.map { it.toSyncRecord() })
            addAll(packingItems.map { it.toSyncRecord() })
            addAll(attachments.map { attachment ->
                attachment.toSyncRecord(contentHash = if (attachment.deletedAt != null) null else hashAttachment(attachment))
            })
        }

        return TripSyncManifest(
            tripId = tripId,
            generatedAt = generatedAt,
            records = records
        )
    }

    private fun hashAttachment(attachment: AttachmentEntity): String? {
        val file = File(context.filesDir, attachment.localPath)
        if (!file.exists() || !file.isFile) return null

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                if (bytesRead == 0) continue
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
