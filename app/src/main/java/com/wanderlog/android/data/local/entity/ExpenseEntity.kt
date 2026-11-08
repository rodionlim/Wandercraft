package com.wanderlog.android.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wanderlog.android.core.util.ExpenseBreakdownCodec
import com.wanderlog.android.domain.model.Expense
import com.wanderlog.android.domain.model.ExpenseCategory
import java.time.LocalDate

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(
        entity = TripEntity::class,
        parentColumns = ["id"],
        childColumns = ["trip_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("trip_id")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "trip_id") val tripId: String,
    val title: String,
    val amount: Double,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    val category: String,
    val date: LocalDate? = null,
    val notes: String? = null,
    @ColumnInfo(name = "cost_breakdown") val breakdownRaw: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "last_modified_by_device_id") val lastModifiedByDeviceId: String = ""
) {
    fun toDomain() = Expense(
        id = id,
        tripId = tripId,
        title = title,
        amount = amount,
        currencyCode = currencyCode,
        category = ExpenseCategory.valueOf(category),
        date = date,
        notes = notes,
        breakdown = ExpenseBreakdownCodec.decode(breakdownRaw)
    )

    companion object {
        fun fromDomain(
            expense: Expense,
            createdAt: Long = System.currentTimeMillis(),
            updatedAt: Long = createdAt,
            deletedAt: Long? = null,
            lastModifiedByDeviceId: String = ""
        ) = ExpenseEntity(
            id = expense.id,
            tripId = expense.tripId,
            title = expense.title,
            amount = expense.amount,
            currencyCode = expense.currencyCode,
            category = expense.category.name,
            date = expense.date,
            notes = expense.notes,
            breakdownRaw = ExpenseBreakdownCodec.encode(expense.breakdown),
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            lastModifiedByDeviceId = lastModifiedByDeviceId
        )
    }
}
