package com.wanderlog.android.presentation.itinerary.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.wanderlog.android.domain.model.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

class ItineraryPdfTest {
    @Test fun longNotesExportAcrossReadablePages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "itinerary-export-test.pdf")
        val days = listOf(TripDay("day", "trip", LocalDate.of(2026, 5, 9), 1))
        val items = listOf(ItineraryItem("item", "day", "trip", "Explore Perth & the coast", ItineraryItemType.ACTIVITY,
            place = Place(name = "Kings Park", address = "Fraser Avenue, Perth"), startTime = "2026-05-09T10:00:00+08:00",
            notes = (1..130).joinToString("\n") { "Travel note $it: Leave time to explore, take a break, and enjoy the view." }, bookingRef = "DEMO-123"))
        file.outputStream().use { writeItineraryPdf("Perth 2026", "Western Australia", days, items, it) }
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount >= 3)
            for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(1190, 1684, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(context.cacheDir, "itinerary-export-test-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
