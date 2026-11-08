package com.wanderlog.android.core.di

import android.content.Context
import androidx.room.Room
import com.wanderlog.android.data.local.WanderlogDatabase
import com.wanderlog.android.data.local.dao.AttachmentDao
import com.wanderlog.android.data.local.dao.ExpenseDao
import com.wanderlog.android.data.local.dao.ItineraryItemAttachmentLinkDao
import com.wanderlog.android.data.local.dao.ItineraryItemDao
import com.wanderlog.android.data.local.dao.PackingItemDao
import com.wanderlog.android.data.local.dao.TripNoteDao
import com.wanderlog.android.data.local.dao.TripDao
import com.wanderlog.android.data.local.dao.TripDayDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WanderlogDatabase =
        Room.databaseBuilder(context, WanderlogDatabase::class.java, "wanderlog.db")
            .addMigrations(WanderlogDatabase.MIGRATION_2_3)
            .addMigrations(WanderlogDatabase.MIGRATION_3_4)
            .addMigrations(WanderlogDatabase.MIGRATION_4_5)
            .addMigrations(WanderlogDatabase.MIGRATION_5_6)
            .addMigrations(WanderlogDatabase.MIGRATION_6_7)
            .addMigrations(WanderlogDatabase.MIGRATION_7_8)
            .addMigrations(WanderlogDatabase.MIGRATION_8_9)
            .addMigrations(WanderlogDatabase.MIGRATION_9_10)
            .addMigrations(WanderlogDatabase.MIGRATION_10_11)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTripDao(db: WanderlogDatabase): TripDao = db.tripDao()
    @Provides fun provideTripDayDao(db: WanderlogDatabase): TripDayDao = db.tripDayDao()
    @Provides fun provideItineraryItemDao(db: WanderlogDatabase): ItineraryItemDao = db.itineraryItemDao()
    @Provides fun provideItineraryItemAttachmentLinkDao(db: WanderlogDatabase): ItineraryItemAttachmentLinkDao = db.itineraryItemAttachmentLinkDao()
    @Provides fun provideExpenseDao(db: WanderlogDatabase): ExpenseDao = db.expenseDao()
    @Provides fun providePackingItemDao(db: WanderlogDatabase): PackingItemDao = db.packingItemDao()
    @Provides fun provideTripNoteDao(db: WanderlogDatabase): TripNoteDao = db.tripNoteDao()
    @Provides fun provideAttachmentDao(db: WanderlogDatabase): AttachmentDao = db.attachmentDao()
}
