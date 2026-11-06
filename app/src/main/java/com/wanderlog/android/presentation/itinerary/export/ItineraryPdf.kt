package com.wanderlog.android.presentation.itinerary.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.wanderlog.android.core.util.toFriendlyDateTimeOrSelf
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.TripDay
import java.io.OutputStream
import java.time.format.DateTimeFormatter

/** Whole-trip export, independent of the currently selected day or UI filters. */
fun writeItineraryPdf(title: String, destination: String, days: List<TripDay>, items: List<ItineraryItem>, output: OutputStream) {
    val document = PdfDocument()
    val writer = ItineraryPdfWriter(document)
    try {
        writer.paragraph("WANDERCRAFT / TRAVEL ITINERARY", 9f, Color.rgb(0, 110, 98), bold = true)
        writer.paragraph(title, 27f, bold = true)
        writer.paragraph(destination, 13f)
        val orderedDays = days.sortedBy { it.dayNumber }
        if (orderedDays.isNotEmpty()) writer.paragraph(
            "${orderedDays.first().date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} - ${orderedDays.last().date.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}", 11f)
        writer.space(12f)
        fun writeItems(stops: List<ItineraryItem>) {
            if (stops.isEmpty()) writer.paragraph("No plans yet. Enjoy a little room to explore.", 11f)
            stops.sortedBy { it.sortOrder }.forEachIndexed { index, item ->
                writer.ensureSpace(70f)
                writer.paragraph("${index + 1}. ${item.title}", 14f, bold = true)
                writer.paragraph(item.itemType.name.lowercase().replaceFirstChar(Char::titlecase), 9f, Color.rgb(0, 110, 98))
                listOfNotNull(item.startTime?.let { "Start: ${it.toFriendlyDateTimeOrSelf()}" }, item.endTime?.let { "End: ${it.toFriendlyDateTimeOrSelf()}" },
                    item.place?.name, item.place?.address, item.bookingRef?.let { "Booking: $it" }, item.notes,
                    item.confirmationUrl?.let { "Confirmation: $it" }).distinct().filter { it.isNotBlank() }.forEach { writer.paragraph(it, 10f) }
                writer.space(10f)
            }
        }
        for (day in orderedDays) {
            writer.ensureSpace(110f)
            writer.paragraph("Day ${day.dayNumber}  /  ${day.date.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))}", 18f, Color.rgb(0, 110, 98), bold = true)
            day.notes?.takeIf { it.isNotBlank() }?.let { writer.paragraph(it, 11f) }
            writeItems(items.filter { it.tripDayId == day.id })
            writer.space(14f)
        }
        val unscheduled = items.filter { item -> days.none { it.id == item.tripDayId } }
        if (unscheduled.isNotEmpty()) {
            writer.ensureSpace(100f)
            writer.paragraph("Unscheduled", 18f, bold = true)
            writeItems(unscheduled)
        }
        if (days.isEmpty() && items.isEmpty()) writer.paragraph("No itinerary items yet.", 12f)
        writer.finish()
        document.writeTo(output)
    } finally {
        writer.finish()
        document.close()
    }
}

private class ItineraryPdfWriter(private val document: PdfDocument) {
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var y = 44f
    private val bottom = 786f
    private fun newPage() {
        finish()
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        y = 44f
    }
    fun finish() {
        page?.let {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.DKGRAY }
            it.canvas.drawText("Wandercraft  /  $pageNumber", 44f, 814f, paint)
            document.finishPage(it)
        }
        page = null
    }
    fun ensureSpace(height: Float) { if (page == null || y + height > bottom) newPage() }
    fun space(height: Float) { y += height }
    fun paragraph(text: String, size: Float, color: Int = Color.rgb(38, 46, 49), bold: Boolean = false) {
        if (text.isBlank()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 507)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3f, 1f).build()
        var firstLine = 0
        while (firstLine < layout.lineCount) {
            ensureSpace((layout.getLineBottom(firstLine) - layout.getLineTop(firstLine)).toFloat())
            val sourceTop = layout.getLineTop(firstLine)
            var lastLine = firstLine
            while (lastLine + 1 < layout.lineCount && y + layout.getLineBottom(lastLine + 1) - sourceTop <= bottom) lastLine++
            val height = layout.getLineBottom(lastLine) - sourceTop
            val canvas = requireNotNull(page).canvas
            canvas.save()
            canvas.clipRect(44f, y, 551f, y + height)
            canvas.translate(44f, y - sourceTop)
            layout.draw(canvas)
            canvas.restore()
            y += height + 6f
            firstLine = lastLine + 1
        }
    }
}
