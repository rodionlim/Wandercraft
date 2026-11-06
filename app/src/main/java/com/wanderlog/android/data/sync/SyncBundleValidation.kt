package com.wanderlog.android.data.sync

import com.wanderlog.android.domain.model.sync.TripSyncBundle
import java.io.File
import java.time.LocalDate
import java.util.Base64

internal fun validateSyncBundle(bundle: TripSyncBundle) {
    require(bundle.trip.id.isNotBlank()) { "Missing trip ID" }
    require(!LocalDate.parse(bundle.trip.endDate).isBefore(LocalDate.parse(bundle.trip.startDate))) { "Invalid trip dates" }
    val children = bundle.tripDays.map { it.tripId } + bundle.itineraryItems.map { it.tripId } +
        bundle.expenses.map { it.tripId } + bundle.packingItems.map { it.tripId } +
        bundle.tripNotes.map { it.tripId } + bundle.attachments.map { it.tripId } + bundle.itemAttachmentLinks.map { it.tripId }
    require(children.all { it == bundle.trip.id }) { "Bundle contains records from another trip" }
    val records = bundle.toManifest().records
    require(records.all { it.id.isNotBlank() } && records.map { it.key }.distinct().size == records.size) { "Bundle contains missing or duplicate record IDs" }
    bundle.tripDays.forEach { LocalDate.parse(it.date) }
    bundle.expenses.forEach { it.date?.let(LocalDate::parse) }
    fun checkContent(encoded: String?, hash: String?) {
        if (encoded == null) return
        val bytes = Base64.getDecoder().decode(encoded)
        require(hash == null || hashBytesSha256(bytes) == hash) { "Transferred file failed its integrity check" }
    }
    checkContent(bundle.trip.coverImageContentBase64, bundle.trip.coverImageContentHash)
    bundle.attachments.forEach {
        require(!File(it.localPath).isAbsolute && it.localPath.split('/', '\\').none { part -> part == ".." } && it.localPath.startsWith("attachments/")) { "Invalid attachment path" }
        checkContent(it.contentBase64, it.contentHash)
    }
}
