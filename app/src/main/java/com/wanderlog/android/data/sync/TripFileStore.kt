package com.wanderlog.android.data.sync

import android.content.Context
import android.net.Uri
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.wanderlog.android.domain.model.sync.TripSyncBundle
import com.wanderlog.android.domain.model.sync.TripSyncManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

@JsonClass(generateAdapter = true)
data class TripFile(val format: String, val version: Int, val bundle: TripSyncBundle)

class TripFileCodec @Inject constructor(moshi: Moshi) {
    private val adapter = moshi.adapter(TripFile::class.java)

    fun encode(bundle: TripSyncBundle): String {
        validate(bundle)
        return adapter.toJson(TripFile("wandercraft-trip", 1, bundle)).also {
            require(it.toByteArray().size <= MAX_BYTES) { "Trip file exceeds the 50 MB limit." }
        }
    }

    fun decode(input: InputStream): TripSyncBundle {
        // Bound reads even when a cloud provider cannot report the file size.
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(chunk, 0, minOf(chunk.size, MAX_BYTES + 1 - total))
            if (count < 0) break
            buffer.write(chunk, 0, count)
            total += count
            require(total <= MAX_BYTES) { "Trip file exceeds the 50 MB limit." }
        }
        val bytes = buffer.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Trip file exceeds the 50 MB limit." }
        val file = adapter.fromJson(bytes.toString(Charsets.UTF_8))
            ?: error("Empty trip file.")
        require(file.format == "wandercraft-trip" && file.version == 1) { "Unsupported trip file. Update Wandercraft on both phones." }
        validate(file.bundle)
        return file.bundle
    }

    private fun validate(bundle: TripSyncBundle) {
        require(bundle.protocolVersion == TripSyncManifest.CURRENT_PROTOCOL_VERSION) { "Update Wandercraft on both phones to open this trip." }
        require(bundle.trip.metadata.deletedAt == null) { "This trip has been deleted." }
        validateSyncBundle(bundle)
        require(bundle.attachments.all { it.metadata.deletedAt != null || it.contentBase64 != null }) { "A booking attachment is missing. Restore the missing file before exporting." }
        require(bundle.trip.coverImageUri == null || bundle.trip.coverImageContentBase64 != null) { "The trip cover file is missing." }
    }

    companion object { const val MAX_BYTES = 50 * 1024 * 1024 }
}

class TripFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val coordinator: TripSyncCoordinator,
    private val codec: TripFileCodec
) {
    suspend fun save(tripId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val bundle = coordinator.buildLocalBundle(tripId) ?: error("Trip no longer exists.")
        val json = codec.encode(bundle)
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot write to this location.")
        output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(uri: Uri): TripSyncBundle = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open this trip file.")
        input.use(codec::decode)
    }

    suspend fun merge(bundle: TripSyncBundle) = withContext(Dispatchers.IO) {
        coordinator.applyRemoteBundle(bundle)
    }
}
