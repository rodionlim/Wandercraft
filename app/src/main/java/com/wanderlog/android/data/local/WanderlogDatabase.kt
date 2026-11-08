package com.wanderlog.android.data.local

import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wanderlog.android.data.local.converter.RoomConverters
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
import com.wanderlog.android.data.local.entity.ItineraryItemAttachmentLinkEntity
import com.wanderlog.android.data.local.entity.ItineraryItemEntity
import com.wanderlog.android.data.local.entity.PackingItemEntity
import com.wanderlog.android.data.local.entity.TripNoteEntity
import com.wanderlog.android.data.local.entity.TripDayEntity
import com.wanderlog.android.data.local.entity.TripEntity

@Database(
    entities = [
        TripEntity::class,
        TripDayEntity::class,
        ItineraryItemEntity::class,
        ItineraryItemAttachmentLinkEntity::class,
        ExpenseEntity::class,
        PackingItemEntity::class,
        TripNoteEntity::class,
        AttachmentEntity::class
    ],
    version = 11,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class WanderlogDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun tripDayDao(): TripDayDao
    abstract fun itineraryItemDao(): ItineraryItemDao
    abstract fun itineraryItemAttachmentLinkDao(): ItineraryItemAttachmentLinkDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun packingItemDao(): PackingItemDao
    abstract fun tripNoteDao(): TripNoteDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE itinerary_items ADD COLUMN linked_expense_id TEXT"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE trips ADD COLUMN traveller_names TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE packing_items ADD COLUMN traveller_name TEXT"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE packing_items ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val migratedAt = System.currentTimeMillis()

                database.execSQL(
                    "ALTER TABLE trips ADD COLUMN deleted_at INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE trips ADD COLUMN last_modified_by_device_id TEXT NOT NULL DEFAULT 'legacy'"
                )

                database.execSQL(
                    "ALTER TABLE trip_days ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE trip_days ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE trip_days ADD COLUMN deleted_at INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE trip_days ADD COLUMN last_modified_by_device_id TEXT NOT NULL DEFAULT 'legacy'"
                )
                database.execSQL(
                    "UPDATE trip_days SET created_at = $migratedAt, updated_at = $migratedAt"
                )

                database.execSQL(
                    "ALTER TABLE itinerary_items ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE itinerary_items ADD COLUMN deleted_at INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE itinerary_items ADD COLUMN last_modified_by_device_id TEXT NOT NULL DEFAULT 'legacy'"
                )
                database.execSQL(
                    "UPDATE itinerary_items SET updated_at = created_at"
                )

                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN deleted_at INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN last_modified_by_device_id TEXT NOT NULL DEFAULT 'legacy'"
                )
                database.execSQL(
                    "UPDATE expenses SET updated_at = created_at"
                )

                database.execSQL(
                    "ALTER TABLE packing_items ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE packing_items ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE packing_items ADD COLUMN deleted_at INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE packing_items ADD COLUMN last_modified_by_device_id TEXT NOT NULL DEFAULT 'legacy'"
                )
                database.execSQL(
                    "UPDATE packing_items SET created_at = $migratedAt, updated_at = $migratedAt"
                )

                database.execSQL(
                    "ALTER TABLE attachments ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE attachments ADD COLUMN deleted_at INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE attachments ADD COLUMN last_modified_by_device_id TEXT NOT NULL DEFAULT 'legacy'"
                )
                database.execSQL(
                    "UPDATE attachments SET updated_at = created_at"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS item_attachment_links (
                        id TEXT NOT NULL PRIMARY KEY,
                        trip_id TEXT NOT NULL,
                        itinerary_item_id TEXT NOT NULL,
                        attachment_id TEXT NOT NULL,
                        link_type TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        deleted_at INTEGER,
                        last_modified_by_device_id TEXT NOT NULL,
                        FOREIGN KEY(trip_id) REFERENCES trips(id) ON DELETE CASCADE,
                        FOREIGN KEY(itinerary_item_id) REFERENCES itinerary_items(id) ON DELETE CASCADE,
                        FOREIGN KEY(attachment_id) REFERENCES attachments(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_item_attachment_links_trip_id ON item_attachment_links(trip_id)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_item_attachment_links_itinerary_item_id ON item_attachment_links(itinerary_item_id)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_item_attachment_links_attachment_id ON item_attachment_links(attachment_id)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_item_attachment_links_itinerary_item_id_attachment_id ON item_attachment_links(itinerary_item_id, attachment_id)"
                )
                database.execSQL(
                    """
                    INSERT INTO item_attachment_links (
                        id,
                        trip_id,
                        itinerary_item_id,
                        attachment_id,
                        link_type,
                        created_at,
                        updated_at,
                        deleted_at,
                        last_modified_by_device_id
                    )
                    SELECT
                        itinerary_items.id || '__' || substr(itinerary_items.confirmation_url, 14),
                        itinerary_items.trip_id,
                        itinerary_items.id,
                        substr(itinerary_items.confirmation_url, 14),
                        'IMPORT_SOURCE',
                        itinerary_items.updated_at,
                        itinerary_items.updated_at,
                        NULL,
                        itinerary_items.last_modified_by_device_id
                    FROM itinerary_items
                    WHERE itinerary_items.confirmation_url LIKE 'attachment://%'
                    """.trimIndent()
                )
                database.execSQL(
                    "UPDATE itinerary_items SET confirmation_url = NULL WHERE confirmation_url LIKE 'attachment://%'"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE attachments ADD COLUMN tags TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE itinerary_items ADD COLUMN rating INTEGER"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trip_notes (
                        id TEXT NOT NULL PRIMARY KEY,
                        trip_id TEXT NOT NULL,
                        content TEXT NOT NULL,
                        is_global INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        deleted_at INTEGER,
                        last_modified_by_device_id TEXT NOT NULL,
                        FOREIGN KEY(trip_id) REFERENCES trips(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_trip_notes_trip_id ON trip_notes(trip_id)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_trip_notes_is_global ON trip_notes(is_global)"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE expenses ADD COLUMN cost_breakdown TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
