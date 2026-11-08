package com.wanderlog.android.presentation.map

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanderlog.android.core.util.flightDetailLine
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.ItineraryItemType
import com.wanderlog.android.domain.repository.ItineraryRepository
import com.wanderlog.android.domain.repository.PlacesRepository
import com.wanderlog.android.domain.repository.TripRepository
import com.wanderlog.android.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class MapDayMeta(
    val tripDayId: String,
    val dayNumber: Int,
    val date: LocalDate
)

data class MapPointUi(
    val item: ItineraryItem,
    val dayNumber: Int? = null,
    val dayDate: LocalDate? = null,
    val isFinalDay: Boolean = false
)

data class MapUiState(
    val points: List<MapPointUi> = emptyList(),
    val selectedItemId: String? = null,
    val mapError: String? = null,
    val isResolving: Boolean = true
)

@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val itineraryRepository: ItineraryRepository,
    private val placesRepository: PlacesRepository,
    private val tripRepository: TripRepository,
    @ApplicationContext context: Context
) : ViewModel() {

    private val tripId = savedStateHandle.get<String>(Screen.Map.ARG_TRIP_ID)!!
    private val dayId = savedStateHandle.get<String>(Screen.Map.ARG_DAY_ID)

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()
    private val attemptedResolutionIds = mutableSetOf<String>()
    private var dayMetadataById: Map<String, MapDayMeta> = emptyMap()

    init {
        _state.update { it.copy(mapError = resolveMapError(context)) }
        viewModelScope.launch {
            val flow = if (dayId != null)
                itineraryRepository.getItemsForDay(dayId) // dayId specific
            else
                itineraryRepository.getItemsForTrip(tripId)

            combine(
                tripRepository.getDaysForTripFlow(tripId),
                flow
            ) { days, all -> days to all }.collect { (days, all) ->
                dayMetadataById = days.associate { day ->
                    day.id to MapDayMeta(
                        tripDayId = day.id,
                        dayNumber = day.dayNumber,
                        date = day.date
                    )
                }
                val eligibleItems = all.filterNot(::isExcludedFromMap)
                val mappable = eligibleItems.filter { item ->
                    !needsFlightPlaceRefresh(item) &&
                    item.place?.latitude != null &&
                        item.place.longitude != null
                }.map(::withDayMetadata)
                val pendingResolution = eligibleItems.any { item ->
                    shouldResolveForMap(item) && item.id !in attemptedResolutionIds
                }

                _state.update {
                    it.copy(
                        points = mappable,
                        isResolving = pendingResolution
                    )
                }

                val updatedAnyItems = resolveMissingCoordinates(eligibleItems)
                if (!updatedAnyItems) {
                    _state.update { current -> current.copy(isResolving = false) }
                }
            }
        }
    }

    fun selectItem(id: String?) = _state.update { it.copy(selectedItemId = id) }

    private fun isExcludedFromMap(item: ItineraryItem): Boolean =
        item.itemType == ItineraryItemType.FLIGHT &&
            (dayMetadataById[item.tripDayId]?.dayNumber == 1 || item.isOnFinalTripDay())

    private fun withDayMetadata(item: ItineraryItem): MapPointUi {
        val meta = dayMetadataById[item.tripDayId]
        return MapPointUi(
            item = item,
            dayNumber = meta?.dayNumber,
            dayDate = meta?.date,
            isFinalDay = item.isOnFinalTripDay()
        )
    }

    private suspend fun resolveMissingCoordinates(items: List<ItineraryItem>): Boolean {
        var updatedAnyItems = false
        items
            .asSequence()
            .filterNot { it.id in attemptedResolutionIds }
            .filter(::shouldResolveForMap)
            .forEach { item ->
                attemptedResolutionIds += item.id

                val resolvedPlace = resolvePlace(item)
                if (resolvedPlace?.latitude != null && resolvedPlace.longitude != null) {
                    itineraryRepository.updateItem(item.copy(place = resolvedPlace))
                    updatedAnyItems = true
                }
            }
        return updatedAnyItems
    }

    private suspend fun resolvePlace(item: ItineraryItem) = runCatching {
        val preferredPlace = item.preferredFlightPlaceCandidate()
        val place = preferredPlace ?: item.place ?: return@runCatching null
        val queries = buildPlaceQueries(item)

        for (query in queries) {
            val match = placesRepository.searchPlaces(query, null).firstOrNull() ?: continue
            val details = match.placeId?.let { placesRepository.fetchPlaceDetails(it) } ?: match
            if (details.latitude != null && details.longitude != null) {
                return@runCatching details.copy(
                    name = place.name.ifBlank { details.name },
                    address = place.address ?: details.address
                )
            }
        }

        null
    }.getOrNull()

    private fun buildPlaceQueries(item: ItineraryItem): List<String> {
        val place = item.preferredFlightPlaceCandidate() ?: item.place ?: return emptyList()
        val title = item.title.trim().takeIf { it.isNotBlank() }
        val name = place.name.trim().takeIf { it.isNotBlank() }
        val address = place.address?.trim()?.takeIf { it.isNotBlank() }

        return buildList {
            if (title != null && name != null && !title.contains(name, ignoreCase = true)) {
                add("$title $name")
            }
            if (name != null && address != null) {
                add("$name $address")
            }
            if (title != null && address != null && !title.contains(address, ignoreCase = true)) {
                add("$title $address")
            }
            title?.let(::add)
            name?.let(::add)
            address?.let(::add)
        }.distinctBy { it.normalizeForComparison() }
    }

    private fun shouldResolveForMap(item: ItineraryItem): Boolean {
        val place = item.place ?: return item.preferredFlightPlaceCandidate() != null
        return place.latitude == null || place.longitude == null || needsFlightPlaceRefresh(item)
    }

    private fun needsFlightPlaceRefresh(item: ItineraryItem): Boolean {
        val preferredPlace = item.preferredFlightPlaceCandidate() ?: return false
        val savedPlace = item.place ?: return true
        val preferredName = preferredPlace.name.normalizeForComparison()
        if (preferredName.isBlank()) return false

        val savedText = listOfNotNull(savedPlace.name, savedPlace.address)
            .joinToString(" ")
            .normalizeForComparison()
        return !savedText.contains(preferredName)
    }

    private fun ItineraryItem.arrivalFlightPlaceCandidate() =
        flightDetailLine("Arrival")
            ?.substringBefore("(")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { arrivalName ->
                place?.copy(name = arrivalName, address = null)
                    ?: com.wanderlog.android.domain.model.Place(name = arrivalName)
            }

    private fun ItineraryItem.departureFlightPlaceCandidate() =
        flightDetailLine("Departure")
            ?.substringBefore("(")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { departureName ->
                place?.copy(name = departureName, address = null)
                    ?: com.wanderlog.android.domain.model.Place(name = departureName)
            }

    private fun ItineraryItem.preferredFlightPlaceCandidate() =
        if (isOnFinalTripDay()) departureFlightPlaceCandidate() ?: arrivalFlightPlaceCandidate()
        else arrivalFlightPlaceCandidate()

    private fun ItineraryItem.isOnFinalTripDay(): Boolean {
        val currentDay = dayMetadataById[tripDayId]?.dayNumber ?: return false
        val finalDay = dayMetadataById.values.maxOfOrNull { it.dayNumber } ?: return false
        return currentDay == finalDay
    }

    private fun String.normalizeForComparison(): String =
        trim().lowercase().replace(Regex("[^a-z0-9]+"), " ")

    private fun resolveMapError(context: Context): String? {
        val manifestKey = runCatching {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.getString("com.google.android.geo.API_KEY").orEmpty().trim()
        }.getOrDefault("")

        return when {
            manifestKey.isBlank() || manifestKey.contains("\${") -> {
                "Google Maps needs MAPS_API_KEY in local.properties and an app rebuild. The runtime key in Settings helps place search, but the map screen uses the manifest SDK key."
            }
            else -> null
        }
    }
}
