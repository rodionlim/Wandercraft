package com.wanderlog.android.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface SyncMetadataStamp {
    fun now(): Long
    fun currentDeviceId(): String
}

@Singleton
class DefaultSyncMetadataStamp @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncMetadataStamp {

    override fun now(): Long = System.currentTimeMillis()

    @Synchronized
    override fun currentDeviceId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null)
            ?: UUID.randomUUID().toString().also { generatedId ->
                prefs.edit().putString(KEY_DEVICE_ID, generatedId).apply()
            }
    }

    private companion object {
        const val PREFS_NAME = "sync_metadata"
        const val KEY_DEVICE_ID = "device_id"
    }
}
