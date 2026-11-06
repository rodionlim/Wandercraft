package com.wanderlog.android.data.sync

import com.squareup.moshi.Moshi
import com.wanderlog.android.domain.model.sync.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class TripFileCodecTest {
    private val codec = TripFileCodec(Moshi.Builder().build())
    private val metadata = SyncMetadata(100, lastModifiedByDeviceId = "device")
    private val bundle = TripSyncBundle(generatedAt = 100, trip = SyncTripPayload("trip", "Perth", "Australia", "2026-05-09", "2026-05-10", currencyCode = "AUD", metadata = metadata))

    @Test fun roundTripPreservesFilesAndDeletionMetadata() {
        val original = bundle.copy(attachments = listOf(SyncAttachmentPayload("file", "trip", "Booking.txt", "text/plain", "attachments/file.txt", sizeBytes = 7, createdAt = 100, contentBase64 = Base64.getEncoder().encodeToString("booking".toByteArray()), metadata = metadata)), tripDays = listOf(SyncTripDayPayload("day", "trip", "2026-05-09", 1, metadata = metadata.copy(deletedAt = 101))))
        assertEquals(original, codec.decode(codec.encode(original).byteInputStream()))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownFileVersion() {
        codec.decode(codec.encode(bundle).replace("\"version\":1", "\"version\":2").byteInputStream())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOldProtocol() {
        codec.decode(codec.encode(bundle).replace("\"protocolVersion\":6", "\"protocolVersion\":5").byteInputStream())
    }
    @Test(expected = IllegalArgumentException::class) fun refusesIncompleteBackup() {
        codec.encode(bundle.copy(attachments = listOf(SyncAttachmentPayload("file", "trip", "Missing", "text/plain", "attachments/missing", sizeBytes = 1, createdAt = 100, metadata = metadata))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsCrossTripContents() {
        codec.decode(codec.encode(bundle.copy(tripDays = listOf(SyncTripDayPayload("day", "trip", "2026-05-09", 1, metadata = metadata)))).replace("\"tripId\":\"trip\"", "\"tripId\":\"other\"").byteInputStream())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDeletedTrip() {
        codec.encode(bundle.copy(trip = bundle.trip.copy(metadata = metadata.copy(deletedAt = 200))))
    }
}
