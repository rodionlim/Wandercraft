package com.wanderlog.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wanderlog.android.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): ExpenseEntity?

        @Query(
                """
                SELECT * FROM expenses
                WHERE trip_id = :tripId
                    AND deleted_at IS NULL
                    AND EXISTS (
                            SELECT 1 FROM trips
                            WHERE trips.id = expenses.trip_id
                                AND trips.deleted_at IS NULL
                    )
                ORDER BY date DESC, created_at DESC
                """
        )
    fun getExpensesForTrip(tripId: String): Flow<List<ExpenseEntity>>

        @Query(
                """
                SELECT * FROM expenses
                WHERE trip_id = :tripId
                    AND deleted_at IS NULL
                    AND EXISTS (
                            SELECT 1 FROM trips
                            WHERE trips.id = expenses.trip_id
                                AND trips.deleted_at IS NULL
                    )
                ORDER BY date DESC, created_at DESC
                """
        )
    suspend fun getExpensesForTripOnce(tripId: String): List<ExpenseEntity>

        @Query("SELECT * FROM expenses WHERE trip_id = :tripId ORDER BY date DESC, created_at DESC")
        suspend fun getExpensesForTripForSync(tripId: String): List<ExpenseEntity>

        @Query("SELECT SUM(amount) FROM expenses WHERE trip_id = :tripId AND deleted_at IS NULL")
    fun getTotalSpent(tripId: String): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE deleted_at IS NOT NULL")
    suspend fun purgeDeletedExpenses()

    @Query(
        """
        UPDATE expenses
        SET title = :title,
            amount = :amount,
            currency_code = :currencyCode,
            category = :category,
            date = :date,
            notes = :notes,
            cost_breakdown = :breakdownRaw,
            updated_at = :updatedAt,
            last_modified_by_device_id = :lastModifiedByDeviceId
        WHERE id = :id
        """
    )
    suspend fun updateExpense(
        id: String,
        title: String,
        amount: Double,
        currencyCode: String,
        category: String,
        date: java.time.LocalDate?,
        notes: String?,
        breakdownRaw: String = "",
        updatedAt: Long,
        lastModifiedByDeviceId: String
    )

    @Query(
        """
        UPDATE expenses
        SET deleted_at = :deletedAt,
            updated_at = :deletedAt,
            last_modified_by_device_id = :lastModifiedByDeviceId
        WHERE id = :expenseId AND deleted_at IS NULL
        """
    )
    suspend fun markDeleted(
        expenseId: String,
        deletedAt: Long,
        lastModifiedByDeviceId: String
    )
}
