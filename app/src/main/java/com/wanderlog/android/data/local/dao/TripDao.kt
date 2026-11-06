package com.wanderlog.android.data.local.dao

import androidx.room.Dao
import androidx.room.Upsert
import androidx.room.Query
import androidx.room.Update
import com.wanderlog.android.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips WHERE deleted_at IS NULL ORDER BY start_date ASC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId AND deleted_at IS NULL")
    suspend fun getTripById(tripId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripByIdIncludingDeleted(tripId: String): TripEntity?

    @Upsert
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: String)

    @Query("DELETE FROM trips WHERE deleted_at IS NOT NULL")
    suspend fun purgeDeletedTrips()

    @Query(
        """
        UPDATE trips
        SET deleted_at = :deletedAt,
            updated_at = :deletedAt,
            last_modified_by_device_id = :lastModifiedByDeviceId
        WHERE id = :tripId AND deleted_at IS NULL
        """
    )
    suspend fun markDeleted(
        tripId: String,
        deletedAt: Long,
        lastModifiedByDeviceId: String
    )
}
