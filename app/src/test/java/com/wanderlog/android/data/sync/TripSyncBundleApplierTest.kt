package com.wanderlog.android.data.sync

import android.content.Context
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
import com.wanderlog.android.data.local.entity.TripEntity
import com.wanderlog.android.domain.model.ExpenseBreakdownItem
import com.wanderlog.android.domain.model.sync.SyncAttachmentPayload
import com.wanderlog.android.domain.model.sync.SyncExpensePayload
import com.wanderlog.android.domain.model.sync.SyncMetadata
import com.wanderlog.android.domain.model.sync.SyncTripPayload
import com.wanderlog.android.domain.model.sync.TripSyncBundle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Base64

class TripSyncBundleApplierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val tripDao = mockk<TripDao>(relaxed = true)
    private val tripDayDao = mockk<TripDayDao>(relaxed = true)
    private val itineraryItemDao = mockk<ItineraryItemDao>(relaxed = true)
    private val itineraryItemAttachmentLinkDao = mockk<ItineraryItemAttachmentLinkDao>(relaxed = true)
    private val expenseDao = mockk<ExpenseDao>(relaxed = true)
    private val packingItemDao = mockk<PackingItemDao>(relaxed = true)
    private val tripNoteDao = mockk<TripNoteDao>(relaxed = true)
    private val attachmentDao = mockk<AttachmentDao>(relaxed = true)
    private val context = mockk<Context>()

    @Test
    fun `applyBundleInternal inserts newer trip payload`() = kotlinx.coroutines.test.runTest {
        every { context.filesDir } returns tempFolder.newFolder("trip-files")
        val applier = TripSyncBundleApplier(
            database = null,
            tripDao = tripDao,
            tripDayDao = tripDayDao,
            itineraryItemDao = itineraryItemDao,
            itineraryItemAttachmentLinkDao = itineraryItemAttachmentLinkDao,
            expenseDao = expenseDao,
            packingItemDao = packingItemDao,
            tripNoteDao = tripNoteDao,
            attachmentDao = attachmentDao,
            context = context
        )
        val insertedTrip = slot<TripEntity>()
        val bundle = TripSyncBundle(
            generatedAt = 10L,
            trip = SyncTripPayload(
                id = "trip-1",
                name = "Kyoto",
                destination = "Kyoto",
                startDate = "2026-07-01",
                endDate = "2026-07-05",
                currencyCode = "JPY",
                metadata = SyncMetadata(updatedAt = 500L, lastModifiedByDeviceId = "device-remote")
            )
        )

        coEvery { tripDao.getTripByIdIncludingDeleted("trip-1") } returns TripEntity(
            id = "trip-1",
            name = "Old",
            destination = "Kyoto",
            startDate = java.time.LocalDate.parse("2026-07-01"),
            endDate = java.time.LocalDate.parse("2026-07-05"),
            createdAt = 100L,
            updatedAt = 200L,
            lastModifiedByDeviceId = "device-local"
        ) andThen TripEntity(
            id = "trip-1",
            name = "Old",
            destination = "Kyoto",
            startDate = java.time.LocalDate.parse("2026-07-01"),
            endDate = java.time.LocalDate.parse("2026-07-05"),
            createdAt = 100L,
            updatedAt = 200L,
            lastModifiedByDeviceId = "device-local"
        )
        coEvery { tripDao.insertTrip(capture(insertedTrip)) } returns Unit

        val result = applier.applyBundleInternal(bundle)

        assertEquals(1, result.appliedRecords)
        assertEquals(0, result.skippedRecords)
        assertEquals(100L, insertedTrip.captured.createdAt)
        assertEquals(500L, insertedTrip.captured.updatedAt)
        assertEquals("device-remote", insertedTrip.captured.lastModifiedByDeviceId)
    }

    @Test
    fun `applyBundleInternal skips older attachment payload`() = kotlinx.coroutines.test.runTest {
        val filesDir = tempFolder.newFolder("files")
        every { context.filesDir } returns filesDir
        val applier = TripSyncBundleApplier(
            database = null,
            tripDao = tripDao,
            tripDayDao = tripDayDao,
            itineraryItemDao = itineraryItemDao,
            itineraryItemAttachmentLinkDao = itineraryItemAttachmentLinkDao,
            expenseDao = expenseDao,
            packingItemDao = packingItemDao,
            tripNoteDao = tripNoteDao,
            attachmentDao = attachmentDao,
            context = context
        )
        val localAttachmentFile = java.io.File(filesDir, "attachments/trip-1/ticket.pdf")
        localAttachmentFile.parentFile?.mkdirs()
        localAttachmentFile.writeText("newer-local")
        val localAttachment = AttachmentEntity(
            id = "attachment-1",
            tripId = "trip-1",
            displayName = "ticket.pdf",
            mimeType = "application/pdf",
            localPath = "attachments/trip-1/ticket.pdf",
            sizeBytes = localAttachmentFile.length(),
            createdAt = 100L,
            updatedAt = 900L,
            lastModifiedByDeviceId = "device-local"
        )
        val bundle = TripSyncBundle(
            generatedAt = 10L,
            trip = SyncTripPayload(
                id = "trip-1",
                name = "Kyoto",
                destination = "Kyoto",
                startDate = "2026-07-01",
                endDate = "2026-07-05",
                currencyCode = "JPY",
                metadata = SyncMetadata(updatedAt = 1000L, lastModifiedByDeviceId = "device-trip")
            ),
            attachments = listOf(
                SyncAttachmentPayload(
                    id = "attachment-1",
                    tripId = "trip-1",
                    displayName = "ticket.pdf",
                    mimeType = "application/pdf",
                    localPath = "attachments/trip-1/ticket.pdf",
                    sizeBytes = 3L,
                    createdAt = 90L,
                    contentHash = hashBytesSha256("old".toByteArray()),
                    contentBase64 = java.util.Base64.getEncoder().encodeToString("old".toByteArray()),
                    metadata = SyncMetadata(updatedAt = 400L, lastModifiedByDeviceId = "device-remote")
                )
            )
        )

        coEvery { tripDao.getTripByIdIncludingDeleted("trip-1") } returns null andThen null
        coEvery { tripDao.insertTrip(any()) } returns Unit
        coEvery { attachmentDao.getByIdIncludingDeleted("attachment-1") } returns localAttachment

        val result = applier.applyBundleInternal(bundle)

        assertEquals(1, result.appliedRecords)
        assertEquals(1, result.skippedRecords)
        coVerify(exactly = 0) { attachmentDao.insert(any()) }
        assertEquals("newer-local", localAttachmentFile.readText())
    }

    @Test
    fun `applyBundleInternal writes incoming trip cover to local cache`() = kotlinx.coroutines.test.runTest {
        val filesDir = tempFolder.newFolder("files")
        every { context.filesDir } returns filesDir
        val applier = TripSyncBundleApplier(
            database = null,
            tripDao = tripDao,
            tripDayDao = tripDayDao,
            itineraryItemDao = itineraryItemDao,
            itineraryItemAttachmentLinkDao = itineraryItemAttachmentLinkDao,
            expenseDao = expenseDao,
            packingItemDao = packingItemDao,
            tripNoteDao = tripNoteDao,
            attachmentDao = attachmentDao,
            context = context
        )
        val insertedTrip = slot<TripEntity>()
        val coverBytes = "cover-image".toByteArray()
        val bundle = TripSyncBundle(
            generatedAt = 10L,
            trip = SyncTripPayload(
                id = "trip-1",
                name = "Perth",
                destination = "Perth",
                startDate = "2026-07-01",
                endDate = "2026-07-05",
                coverImageUri = "file:///remote/trip-covers/trip-1.jpg",
                coverImageContentHash = hashBytesSha256(coverBytes),
                coverImageContentBase64 = Base64.getEncoder().encodeToString(coverBytes),
                currencyCode = "AUD",
                metadata = SyncMetadata(updatedAt = 500L, lastModifiedByDeviceId = "device-remote")
            )
        )

        coEvery { tripDao.getTripByIdIncludingDeleted("trip-1") } returns null andThen null
        coEvery { tripDao.insertTrip(capture(insertedTrip)) } returns Unit

        val result = applier.applyBundleInternal(bundle)

        val coverFile = File(java.net.URI(insertedTrip.captured.coverImageUri!!))
        assertEquals(1, result.appliedRecords)
        assertTrue(coverFile.exists())
        assertEquals("cover-image", coverFile.readText())
        assertEquals(coverFile.toURI().toString(), insertedTrip.captured.coverImageUri)
    }

    @Test
    fun `applyBundleInternal persists expense cost breakdown and active flags`() = kotlinx.coroutines.test.runTest {
        every { context.filesDir } returns tempFolder.newFolder("trip-files-expense")
        val applier = TripSyncBundleApplier(
            database = null,
            tripDao = tripDao,
            tripDayDao = tripDayDao,
            itineraryItemDao = itineraryItemDao,
            itineraryItemAttachmentLinkDao = itineraryItemAttachmentLinkDao,
            expenseDao = expenseDao,
            packingItemDao = packingItemDao,
            tripNoteDao = tripNoteDao,
            attachmentDao = attachmentDao,
            context = context
        )
        val insertedExpense = slot<ExpenseEntity>()
        val breakdown = listOf(
            ExpenseBreakdownItem(label = "parents", amount = 700.0, isActive = false),
            ExpenseBreakdownItem(label = "our family", amount = 300.0, isActive = true)
        )
        val bundle = TripSyncBundle(
            generatedAt = 10L,
            trip = SyncTripPayload(
                id = "trip-1",
                name = "Kyoto",
                destination = "Kyoto",
                startDate = "2026-07-01",
                endDate = "2026-07-05",
                currencyCode = "SGD",
                metadata = SyncMetadata(updatedAt = 500L, lastModifiedByDeviceId = "device-remote")
            ),
            expenses = listOf(
                SyncExpensePayload(
                    id = "expense-1",
                    tripId = "trip-1",
                    title = "Hotel",
                    amount = 1000.0,
                    currencyCode = "SGD",
                    category = "ACCOMMODATION",
                    date = "2026-07-01",
                    breakdown = breakdown,
                    createdAt = 100L,
                    metadata = SyncMetadata(updatedAt = 500L, lastModifiedByDeviceId = "device-remote")
                )
            )
        )

        coEvery { tripDao.getTripByIdIncludingDeleted("trip-1") } returns null andThen null
        coEvery { tripDao.insertTrip(any()) } returns Unit
        coEvery { expenseDao.getByIdIncludingDeleted("expense-1") } returns null andThen null
        coEvery { expenseDao.insertExpense(capture(insertedExpense)) } returns Unit

        val result = applier.applyBundleInternal(bundle)

        assertEquals(2, result.appliedRecords)
        val domainExpense = insertedExpense.captured.toDomain()
        assertEquals(1000.0, domainExpense.amount, 0.001)
        assertEquals(300.0, domainExpense.activeAmount, 0.001)
        assertEquals(breakdown, domainExpense.breakdown)
    }
}
