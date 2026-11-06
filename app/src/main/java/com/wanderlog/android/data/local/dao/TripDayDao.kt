package com.wanderlog.android.data.local.dao

import androidx.room.Dao
import androidx.room.Upsert
import androidx.room.Query
import com.wanderlog.android.data.local.entity.TripDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDayDao {

    @Query("SELECT * FROM trip_days WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): TripDayEntity?

        @Query(
                """
                SELECT * FROM trip_days
                WHERE trip_id = :tripId
                    AND deleted_at IS NULL
                    AND EXISTS (
                            SELECT 1 FROM trips
                            WHERE trips.id = trip_days.trip_id
                                AND trips.deleted_at IS NULL
                    )
                ORDER BY day_number ASC
                """
        )
    fun getDaysForTrip(tripId: String): Flow<List<TripDayEntity>>

        @Query(
                """
                SELECT * FROM trip_days
                WHERE trip_id = :tripId
                    AND deleted_at IS NULL
                    AND EXISTS (
                            SELECT 1 FROM trips
                            WHERE trips.id = trip_days.trip_id
                                AND trips.deleted_at IS NULL
                    )
                ORDER BY day_number ASC
                """
        )
    suspend fun getDaysForTripOnce(tripId: String): List<TripDayEntity>

        @Query("SELECT * FROM trip_days WHERE trip_id = :tripId ORDER BY day_number ASC")
        suspend fun getDaysForTripForSync(tripId: String): List<TripDayEntity>

    @Upsert
    suspend fun insertAll(days: List<TripDayEntity>)

    @Query("DELETE FROM trip_days WHERE trip_id = :tripId")
    suspend fun deleteAllForTrip(tripId: String)

    @Query("DELETE FROM trip_days WHERE deleted_at IS NOT NULL")
    suspend fun purgeDeletedDays()
}
