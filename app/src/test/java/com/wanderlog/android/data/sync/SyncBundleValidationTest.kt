package com.wanderlog.android.data.sync

import com.wanderlog.android.domain.model.sync.*
import org.junit.Test
import java.util.Base64

class SyncBundleValidationTest {
    private val metadata = SyncMetadata(100, lastModifiedByDeviceId = "device")
    private val bundle = TripSyncBundle(generatedAt = 100, trip = SyncTripPayload("trip", "Trip", "Perth", "2026-05-09", "2026-05-10", currencyCode = "AUD", metadata = metadata))
    @Test(expected = IllegalArgumentException::class) fun rejectsRecordsFromAnotherTrip() {
        validateSyncBundle(bundle.copy(tripDays = listOf(SyncTripDayPayload("day", "another", "2026-05-09", 1, metadata = metadata))))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDuplicateRecords() {
        val day = SyncTripDayPayload("day", "trip", "2026-05-09", 1, metadata = metadata)
        validateSyncBundle(bundle.copy(tripDays = listOf(day, day)))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsCorruptedFilesBeforeApplyingRecords() {
        validateSyncBundle(bundle.copy(trip = bundle.trip.copy(coverImageContentBase64 = Base64.getEncoder().encodeToString("bytes".toByteArray()), coverImageContentHash = "wrong")))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsAttachmentPathTraversal() {
        validateSyncBundle(bundle.copy(attachments = listOf(SyncAttachmentPayload("attachment", "trip", "file", "text/plain", "attachments/../../database", sizeBytes = 1, createdAt = 100, metadata = metadata))))
    }
}
