package com.wanderlog.android.data.sync

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wanderlog.android.data.local.WanderlogDatabase
import com.wanderlog.android.data.local.entity.*
import com.wanderlog.android.domain.model.sync.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SyncPersistenceTest {
    @Test fun newerParentKeepsNewerLocalChildrenAndAttachments() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WanderlogDatabase::class.java).build()
        try {
            val date = LocalDate.of(2026, 5, 9)
            val trip = TripEntity("regression-trip", "Trip", "Perth", date, date, updatedAt = 100)
            val day = TripDayEntity("day", trip.id, date, 1, updatedAt = 100)
            val item = ItineraryItemEntity(id = "item", tripDayId = day.id, tripId = trip.id, title = "Newer local stop", itemType = "PLACE", sortOrder = 0, updatedAt = 900)
            db.tripDao().insertTrip(trip)
            db.tripDayDao().insertAll(listOf(day))
            db.itineraryItemDao().insertItem(item)
            db.attachmentDao().insert(AttachmentEntity("attachment", trip.id, "Ticket", "text/plain", "attachments/test.txt", sizeBytes = 1, createdAt = 100))
            db.itineraryItemAttachmentLinkDao().insert(ItineraryItemAttachmentLinkEntity("link", trip.id, item.id, "attachment", "IMPORT_SOURCE", createdAt = 100))
            val applier = TripSyncBundleApplier(db, db.tripDao(), db.tripDayDao(), db.itineraryItemDao(), db.itineraryItemAttachmentLinkDao(), db.expenseDao(), db.packingItemDao(), db.tripNoteDao(), db.attachmentDao(), context)
            val bundle = TripSyncBundle(generatedAt = 1000, trip = SyncTripPayload(trip.id, "Renamed remotely", "Perth", date.toString(), date.toString(), currencyCode = "AUD", metadata = SyncMetadata(500, lastModifiedByDeviceId = "remote")),
                tripDays = listOf(SyncTripDayPayload(day.id, trip.id, date.toString(), 1, notes = "Remote day note", metadata = SyncMetadata(500, lastModifiedByDeviceId = "remote"))),
                itineraryItems = listOf(SyncItineraryItemPayload(item.id, day.id, trip.id, "Stale remote stop", "PLACE", sortOrder = 0, metadata = SyncMetadata(200, lastModifiedByDeviceId = "remote"))))
            applier.applyBundle(bundle)
            assertEquals("Renamed remotely", db.tripDao().getTripById(trip.id)?.name)
            assertEquals("Newer local stop", db.itineraryItemDao().getById(item.id)?.title)
            assertNotNull(db.itineraryItemAttachmentLinkDao().getByIdIncludingDeleted("link"))
            assertNotNull(db.attachmentDao().getById("attachment"))
            // Updating the stop itself must also preserve its attachment links.
            db.itineraryItemDao().insertItem(item.copy(title = "Updated stop"))
            assertNotNull(db.itineraryItemAttachmentLinkDao().getByIdIncludingDeleted("link"))
        } finally { db.close() }
    }
}
