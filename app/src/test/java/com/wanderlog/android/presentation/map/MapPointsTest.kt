package com.wanderlog.android.presentation.map

import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.ItineraryItemType
import com.wanderlog.android.domain.model.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class MapPointsTest {
    private fun point(
        id: String,
        day: Int?,
        order: Int,
        title: String = id,
        address: String = "",
        itemType: ItineraryItemType = ItineraryItemType.PLACE
    ) = MapPointUi(
        ItineraryItem(id, "day-$day", "trip", title, itemType,
            place = Place(name = "Museum", address = address, latitude = 1.0, longitude = 103.0), sortOrder = order),
        dayNumber = day
    )

    @Test fun `all days follow day and itinerary order with unscheduled last`() {
        val points = listOf(point("later", 2, 0), point("unscheduled", null, 0), point("second", 1, 2), point("first", 1, 1))
        assertEquals(listOf("first", "second", "later", "unscheduled"), visibleMapPoints(points, null, "").map { it.item.id })
    }

    @Test fun `day 1 and final day flights are excluded from map view`() {
        val points = listOf(
            point("day1-flight", 1, 0, title = "SIN -> TPE", itemType = ItineraryItemType.FLIGHT),
            point("day1-hotel", 1, 1, title = "Taipei Hotel", itemType = ItineraryItemType.HOTEL),
            point("day2-flight", 2, 0, title = "Domestic flight", itemType = ItineraryItemType.FLIGHT),
            point("day3-market", 3, 0, title = "Night Market", itemType = ItineraryItemType.ACTIVITY),
            point("day3-flight", 3, 1, title = "TPE -> SIN", itemType = ItineraryItemType.FLIGHT)
        )
        assertEquals(
            listOf("day1-hotel", "day2-flight", "day3-market"),
            visibleMapPoints(points, null, "").map { it.item.id }
        )
        assertEquals(listOf("day1-hotel"), visibleMapPoints(points, 1, "").map { it.item.id })
        assertEquals(listOf("day3-market"), visibleMapPoints(points, 3, "").map { it.item.id })
    }

    @Test fun `search and day filters combine and match addresses ignoring case`() {
        val points = listOf(point("a", 1, 0, address = "Orchard Road"), point("b", 2, 0, address = "Orchard Road"), point("c", 1, 1))
        assertEquals(listOf("a"), visibleMapPoints(points, 1, " ORCHARD ").map { it.item.id })
        assertEquals(emptyList<MapPointUi>(), visibleMapPoints(points, 1, "missing"))
        assertEquals(3, visibleMapPoints(points, null, "museum").size)
    }

    @Test fun `unscheduled filter only includes undated stops`() {
        val points = listOf(point("a", 1, 0), point("b", null, 0))
        assertEquals(listOf("b"), visibleMapPoints(points, Int.MAX_VALUE, " ").map { it.item.id })
    }
}
