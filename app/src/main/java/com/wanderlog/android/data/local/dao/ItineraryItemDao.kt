package com.wanderlog.android.data.local.dao

import androidx.room.Dao
import androidx.room.Upsert
import androidx.room.Query
import androidx.room.Update
import com.wanderlog.android.data.local.entity.ItineraryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItineraryItemDao {

        @Query("SELECT * FROM itinerary_items WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: String): ItineraryItemEntity?

        @Query("SELECT * FROM itinerary_items WHERE id = :id")
        suspend fun getByIdIncludingDeleted(id: String): ItineraryItemEntity?

        @Query(
                """
                SELECT * FROM itinerary_items
                WHERE trip_day_id = :dayId
                    AND deleted_at IS NULL
                    AND EXISTS (
                            SELECT 1 FROM trip_days
                            WHERE trip_days.id = itinerary_items.trip_day_id
                                AND trip_days.deleted_at IS NULL
                    )
                    AND EXISTS (
                            SELECT 1 FROM trips
                            WHERE trips.id = itinerary_items.trip_id
                                AND trips.deleted_at IS NULL
                    )
                ORDER BY sort_order ASC
                """
        )
    fun getItemsForDay(dayId: String): Flow<List<ItineraryItemEntity>>

        @Query(
                """
                SELECT * FROM itinerary_items
                WHERE trip_id = :tripId
                    AND deleted_at IS NULL
                    AND EXISTS (
                            SELECT 1 FROM trip_days
                            WHERE trip_days.id = itinerary_items.trip_day_id
                                AND trip_days.deleted_at IS NULL
                    )
                    AND EXISTS (
                            SELECT 1 FROM trips
                            WHERE trips.id = itinerary_items.trip_id
                                AND trips.deleted_at IS NULL
                    )
                ORDER BY trip_day_id, sort_order ASC
                """
        )
    fun getItemsForTrip(tripId: String): Flow<List<ItineraryItemEntity>>

        @Query(
                """
                SELECT * FROM itinerary_items
                WHERE trip_id = :tripId
                    AND deleted_at IS NULL
                    AND EXISTS (
                            SELECT 1 FROM trip_days
                            WHERE trip_days.id = itinerary_items.trip_day_id
                                AND trip_days.deleted_at IS NULL
                    )
                    AND EXISTS (
                            SELECT 1 FROM trips
                            WHERE trips.id = itinerary_items.trip_id
                                AND trips.deleted_at IS NULL
                    )
                ORDER BY trip_day_id, sort_order ASC
                """
        )
    suspend fun getItemsForTripOnce(tripId: String): List<ItineraryItemEntity>

        @Query("SELECT * FROM itinerary_items WHERE trip_id = :tripId ORDER BY trip_day_id, sort_order ASC")
        suspend fun getItemsForTripForSync(tripId: String): List<ItineraryItemEntity>

    @Upsert
    suspend fun insertItem(item: ItineraryItemEntity)

    @Upsert
    suspend fun insertItems(items: List<ItineraryItemEntity>)

    @Update
    suspend fun updateItem(item: ItineraryItemEntity)

    @Query("DELETE FROM itinerary_items WHERE deleted_at IS NOT NULL")
    suspend fun purgeDeletedItems()

    @Query(
        """
        UPDATE itinerary_items
        SET deleted_at = :deletedAt,
            updated_at = :deletedAt,
            last_modified_by_device_id = :lastModifiedByDeviceId
        WHERE id = :itemId AND deleted_at IS NULL
        """
    )
    suspend fun markDeleted(
        itemId: String,
        deletedAt: Long,
        lastModifiedByDeviceId: String
    )

    @Query(
        """
        UPDATE itinerary_items
        SET sort_order = :order,
            updated_at = :updatedAt,
            last_modified_by_device_id = :lastModifiedByDeviceId
        WHERE id = :itemId
        """
    )
    suspend fun updateSortOrder(
        itemId: String,
        order: Int,
        updatedAt: Long,
        lastModifiedByDeviceId: String
    )
}
