package com.wanderlog.android.data.repository

import com.wanderlog.android.data.local.dao.ExpenseDao
import com.wanderlog.android.data.local.dao.ItineraryItemDao
import com.wanderlog.android.data.local.dao.PackingItemDao
import com.wanderlog.android.data.local.dao.TripDao
import com.wanderlog.android.data.local.dao.TripDayDao
import com.wanderlog.android.data.local.entity.ItineraryItemEntity
import com.wanderlog.android.data.local.entity.PackingItemEntity
import com.wanderlog.android.data.sync.SyncMetadataStamp
import com.wanderlog.android.domain.model.Expense
import com.wanderlog.android.domain.model.ExpenseBreakdownItem
import com.wanderlog.android.domain.model.ExpenseCategory
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.ItineraryItemType
import com.wanderlog.android.domain.model.PackingItem
import com.wanderlog.android.domain.model.Trip
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SyncMetadataStampingTest {

    private val syncMetadataStamp = mockk<SyncMetadataStamp>()

    @Test
    fun `updateItem preserves createdAt and stamps updater`() = runTest {
        val dao = mockk<ItineraryItemDao>(relaxed = true)
        val repository = ItineraryRepositoryImpl(dao, syncMetadataStamp)
        val item = ItineraryItem(
            id = "item-1",
            tripDayId = "day-1",
            tripId = "trip-1",
            title = "Museum",
            itemType = ItineraryItemType.ACTIVITY,
            startTime = "09:00",
            endTime = "10:00",
            sortOrder = 2
        )
        val existing = ItineraryItemEntity(
            id = item.id,
            tripDayId = item.tripDayId,
            tripId = item.tripId,
            title = "Old museum",
            itemType = item.itemType.name,
            createdAt = 111L,
            updatedAt = 150L,
            lastModifiedByDeviceId = "old-device"
        )
        val updatedItemSlot = slot<ItineraryItemEntity>()

        coEvery { dao.getByIdIncludingDeleted(item.id) } returns existing
        coEvery { dao.updateItem(capture(updatedItemSlot)) } returns Unit
        every { syncMetadataStamp.now() } returns 222L
        every { syncMetadataStamp.currentDeviceId() } returns "device-a"

        repository.updateItem(item)

        assertEquals(111L, updatedItemSlot.captured.createdAt)
        assertEquals(222L, updatedItemSlot.captured.updatedAt)
        assertEquals("device-a", updatedItemSlot.captured.lastModifiedByDeviceId)
    }

    @Test
    fun `updateExpense stamps updatedAt and device id`() = runTest {
        val dao = mockk<ExpenseDao>(relaxed = true)
        val repository = ExpenseRepositoryImpl(dao, syncMetadataStamp)
        val expense = Expense(
            id = "expense-1",
            tripId = "trip-1",
            title = "Hotel",
            amount = 1000.0,
            currencyCode = "USD",
            category = ExpenseCategory.ACCOMMODATION,
            date = LocalDate.of(2026, 5, 2),
            notes = "Family stay",
            breakdown = listOf(
                ExpenseBreakdownItem(label = "parents", amount = 700.0, isActive = false),
                ExpenseBreakdownItem(label = "our family", amount = 300.0, isActive = true)
            )
        )
        val breakdownRawSlot = slot<String>()
        val updatedAtSlot = slot<Long>()
        val deviceIdSlot = slot<String>()

        coEvery {
            dao.updateExpense(
                id = expense.id,
                title = expense.title,
                amount = expense.amount,
                currencyCode = expense.currencyCode,
                category = expense.category.name,
                date = expense.date,
                notes = expense.notes,
                breakdownRaw = capture(breakdownRawSlot),
                updatedAt = capture(updatedAtSlot),
                lastModifiedByDeviceId = capture(deviceIdSlot)
            )
        } returns Unit
        every { syncMetadataStamp.now() } returns 333L
        every { syncMetadataStamp.currentDeviceId() } returns "device-b"

        repository.updateExpense(expense)

        assertEquals(333L, updatedAtSlot.captured)
        assertEquals("device-b", deviceIdSlot.captured)
        assertEquals(expense.breakdown, com.wanderlog.android.core.util.ExpenseBreakdownCodec.decode(breakdownRawSlot.captured))
        assertEquals(300.0, expense.activeAmount, 0.001)
    }

    @Test
    fun `updateSortOrder stamps itinerary reorder`() = runTest {
        val dao = mockk<ItineraryItemDao>(relaxed = true)
        val repository = ItineraryRepositoryImpl(dao, syncMetadataStamp)
        val updatedAtSlot = slot<Long>()
        val deviceIdSlot = slot<String>()

        coEvery {
            dao.updateSortOrder(
                itemId = "item-1",
                order = 7,
                updatedAt = capture(updatedAtSlot),
                lastModifiedByDeviceId = capture(deviceIdSlot)
            )
        } returns Unit
        every { syncMetadataStamp.now() } returns 444L
        every { syncMetadataStamp.currentDeviceId() } returns "device-c"

        repository.updateSortOrder("item-1", 7)

        assertEquals(444L, updatedAtSlot.captured)
        assertEquals("device-c", deviceIdSlot.captured)
        coVerify(exactly = 1) {
            dao.updateSortOrder("item-1", 7, 444L, "device-c")
        }
    }

    @Test
    fun `deleteTrip hard deletes trip instead of creating tombstone`() = runTest {
        val tripDao = mockk<TripDao>(relaxed = true)
        val tripDayDao = mockk<TripDayDao>(relaxed = true)
        val repository = TripRepositoryImpl(tripDao, tripDayDao, syncMetadataStamp)
        val trip = Trip(
            id = "trip-1",
            name = "Seoul",
            destination = "Seoul",
            startDate = LocalDate.of(2026, 6, 1),
            endDate = LocalDate.of(2026, 6, 3)
        )

        repository.deleteTrip(trip)

        coVerify(exactly = 1) {
            tripDao.deleteTripById("trip-1")
        }
    }

    @Test
    fun `deleteItem marks itinerary tombstone with sync metadata`() = runTest {
        val dao = mockk<ItineraryItemDao>(relaxed = true)
        val repository = ItineraryRepositoryImpl(dao, syncMetadataStamp)
        val item = ItineraryItem(
            id = "item-delete-1",
            tripDayId = "day-1",
            tripId = "trip-1",
            title = "Delete me",
            itemType = ItineraryItemType.NOTE
        )

        every { syncMetadataStamp.now() } returns 666L
        every { syncMetadataStamp.currentDeviceId() } returns "device-item"

        repository.deleteItem(item)

        coVerify(exactly = 1) {
            dao.markDeleted("item-delete-1", 666L, "device-item")
        }
    }

    @Test
    fun `deleteExpense marks tombstone with sync metadata`() = runTest {
        val dao = mockk<ExpenseDao>(relaxed = true)
        val repository = ExpenseRepositoryImpl(dao, syncMetadataStamp)
        val expense = Expense(
            id = "expense-delete-1",
            tripId = "trip-1",
            title = "Taxi",
            amount = 18.0,
            currencyCode = "USD",
            category = ExpenseCategory.TRANSPORT
        )

        every { syncMetadataStamp.now() } returns 777L
        every { syncMetadataStamp.currentDeviceId() } returns "device-expense"

        repository.deleteExpense(expense)

        coVerify(exactly = 1) {
            dao.markDeleted("expense-delete-1", 777L, "device-expense")
        }
    }

    @Test
    fun `replaceItems tombstones existing packing rows before insert`() = runTest {
        val dao = mockk<PackingItemDao>(relaxed = true)
        val repository = PackingRepositoryImpl(dao, syncMetadataStamp)
        val existing = PackingItemEntity(
            id = "pack-1",
            tripId = "trip-1",
            title = "Shoes",
            createdAt = 100L,
            updatedAt = 120L,
            lastModifiedByDeviceId = "device-old"
        )
        val replacement = PackingItem(
            id = "pack-2",
            tripId = "trip-1",
            title = "Jacket"
        )
        val insertedItemsSlot = slot<List<PackingItemEntity>>()

        coEvery { dao.getItemsForTripOnce("trip-1") } returns listOf(existing)
        coEvery { dao.insertItems(capture(insertedItemsSlot)) } returns Unit
        every { syncMetadataStamp.now() } returns 888L
        every { syncMetadataStamp.currentDeviceId() } returns "device-pack"

        repository.replaceItems("trip-1", listOf(replacement))

        coVerify(exactly = 1) {
            dao.markDeleted("pack-1", 888L, "device-pack")
        }
        assertEquals(listOf("pack-2"), insertedItemsSlot.captured.map(PackingItemEntity::id))
        assertEquals(888L, insertedItemsSlot.captured.single().updatedAt)
        assertEquals("device-pack", insertedItemsSlot.captured.single().lastModifiedByDeviceId)
    }
}
