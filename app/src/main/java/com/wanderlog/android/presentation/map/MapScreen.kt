package com.wanderlog.android.presentation.map

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.format.DateTimeFormatter
import com.wanderlog.android.core.util.toFriendlyDateTimePartsOrNull

@Composable
fun MapScreen(onBack: () -> Unit, viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var satellite by rememberSaveable { mutableStateOf(false) }
    var showConnections by rememberSaveable { mutableStateOf(true) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var fitRequest by remember { mutableIntStateOf(0) }
    var mapLoaded by remember { mutableStateOf(false) }
    val camera = rememberCameraPositionState()
    val listState = rememberLazyListState()
    val days = remember(state.points) { state.points.groupBy { it.dayNumber ?: Int.MAX_VALUE }.toSortedMap() }
    val stops = remember(state.points, selectedDay, query) { visibleMapPoints(state.points, selectedDay, query) }
    val selected = stops.firstOrNull { it.item.id == state.selectedItemId }
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val mapStyle = remember(dark) { MapStyleOptions(if (dark) DARK_MAP_STYLE else LIGHT_MAP_STYLE) }

    LaunchedEffect(days) {
        if (selectedDay != null && selectedDay !in days.keys) selectedDay = null
    }
    LaunchedEffect(stops) {
        if (state.selectedItemId != null && stops.none { it.item.id == state.selectedItemId }) viewModel.selectItem(null)
    }
    // The map must have a measured viewport before requesting coordinate bounds.
    LaunchedEffect(mapLoaded, stops, fitRequest) {
        if (!mapLoaded || stops.isEmpty()) return@LaunchedEffect
        val positions = stops.map { it.position() }.distinct()
        val update = if (positions.size == 1) CameraUpdateFactory.newLatLngZoom(positions.first(), 14f)
        else CameraUpdateFactory.newLatLngBounds(
            LatLngBounds.builder().apply { positions.forEach(::include) }.build(),
            with(density) { 48.dp.roundToPx() }
        )
        camera.animate(update)
    }
    LaunchedEffect(state.selectedItemId, mapLoaded) {
        if (!mapLoaded || selected == null) return@LaunchedEffect
        camera.animate(CameraUpdateFactory.newLatLngZoom(selected.position(), 15f))
        val index = stops.indexOf(selected)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Scaffold { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val compact = maxHeight < 500.dp
            val panelHeight = if (expanded) maxHeight * 0.48f else if (compact) 140.dp else 210.dp
            if (state.mapError == null) GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                properties = MapProperties(
                    mapType = if (satellite) MapType.HYBRID else MapType.NORMAL,
                    mapStyleOptions = if (satellite) null else mapStyle
                ),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, compassEnabled = false),
                contentPadding = PaddingValues(top = if (compact) 148.dp else 220.dp, bottom = panelHeight + 12.dp, end = 64.dp),
                onMapLoaded = { mapLoaded = true },
                onMapClick = { viewModel.selectItem(null) }
            ) {
                days.entries.forEachIndexed { dayIndex, (_, dayStops) ->
                    val sorted = dayStops.sortedBy { it.item.sortOrder }
                    val visible = sorted.filter { point -> stops.any { it.item.id == point.item.id } }
                    val color = dayColor(dayIndex)
                    // Search results are isolated pins; do not invent connections across hidden stops.
                    if (showConnections && query.isBlank() && visible.size > 1) {
                        Polyline(points = visible.map { it.position() }, color = color.copy(alpha = 0.7f), width = 7f,
                            pattern = listOf(Dash(24f), Gap(14f)))
                    }
                    visible.forEach { point ->
                        val isSelected = selected?.item?.id == point.item.id
                        val number = sorted.indexOf(point) + 1
                        val icon = remember(number, color, isSelected, mapLoaded) {
                            if (mapLoaded) numberedMarker(number, color, isSelected) else null
                        }
                        Marker(
                            state = remember(point.item.id, point.position()) { MarkerState(point.position()) },
                            title = point.item.title,
                            contentDescription = "${point.dayNumber?.let { "Day $it, " }.orEmpty()}stop $number: ${point.item.title}",
                            icon = icon,
                            anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                            zIndex = if (isSelected) 2f else 1f,
                            onClick = { viewModel.selectItem(point.item.id); true }
                        )
                    }
                }
            }

            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(24.dp), shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to itinerary") }
                            Column(Modifier.weight(1f)) {
                                Text("Explore your trip", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("${state.points.size} places · ${days.size} ${if (days.size == 1) "day" else "days"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (state.isResolving) CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                        }
                        if (!compact) OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            placeholder = { Text("Find a place in this trip") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } } } else null,
                            singleLine = true, shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { DayChip("All days", selectedDay == null, MaterialTheme.colorScheme.primary) { selectedDay = null } }
                    items(days.keys.toList()) { day ->
                        val point = days.getValue(day).first()
                        val label = if (point.dayNumber == null) "Unscheduled" else "Day $day · ${point.dayDate?.format(DateTimeFormatter.ofPattern("MMM d")).orEmpty()}"
                        DayChip(label, selectedDay == day, dayColor(days.keys.indexOf(day))) { selectedDay = day }
                    }
                }
            }

            Column(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = panelHeight + 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MapControl(Icons.Default.Layers, if (satellite) "Show standard map" else "Show satellite map", satellite) { satellite = !satellite }
                MapControl(Icons.Default.Route, if (showConnections) "Hide stop connections" else "Show stop connections", showConnections) { showConnections = !showConnections }
                MapControl(Icons.Default.CenterFocusStrong, "Fit visible places", false) { viewModel.selectItem(null); fitRequest++ }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelHeight),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp
            ) {
                Column(Modifier.padding(top = 8.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(32.dp, 4.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (selectedDay == null) "Your places" else if (selectedDay == Int.MAX_VALUE) "Unscheduled places" else "Day $selectedDay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${stops.size} ${if (stops.size == 1) "stop" else "stops"} · Tap a place to explore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { expanded = !expanded }) { Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess, if (expanded) "Collapse places" else "Expand places") }
                    }
                    when {
                        state.mapError != null -> MapMessage("Map unavailable", "Your places are saved. Map setup needs attention before they can be displayed.")
                        state.isResolving && state.points.isEmpty() -> MapMessage("Finding your places", "Adding your itinerary stops to the map…")
                        state.points.isEmpty() -> MapMessage("Your trip starts here", "Add places to your itinerary to see them on the map.")
                        stops.isEmpty() -> MapMessage("No places found", "Try another search or select All days.")
                        else -> LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(stops, key = { it.item.id }) { point ->
                                val day = point.dayNumber ?: Int.MAX_VALUE
                                val number = days.getValue(day).sortedBy { it.item.sortOrder }.indexOf(point) + 1
                                PlaceRow(point, number, dayColor(days.keys.indexOf(day)), point.item.id == selected?.item?.id) { viewModel.selectItem(point.item.id) }
                            }
                            if (showConnections && query.isBlank()) item {
                                Text("Dotted lines show stop order, not travel routes.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, shadowElevation = 2.dp,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            if (selected) Icon(Icons.Default.Check, null, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MapControl(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        IconButton(onClick = onClick) { Icon(icon, label) }
    }
}

@Composable
private fun PlaceRow(point: MapPointUi, number: Int, color: Color, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = color, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(listOfNotNull(point.dayNumber?.let { "DAY $it" }, point.item.startTime?.let { it.toFriendlyDateTimePartsOrNull()?.time ?: it.toFriendlyDateTimePartsOrNull()?.date ?: it }).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(point.item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val address = point.item.place?.address ?: point.item.place?.name
                if (!address.isNullOrBlank()) Text(address, style = MaterialTheme.typography.bodySmall, maxLines = if (selected) 3 else 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                val position = point.position()
                val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
                    .appendQueryParameter("api", "1")
                    .appendQueryParameter("destination", "${position.latitude},${position.longitude}")
                    .apply { point.item.place?.placeId?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("destination_place_id", it) } }
                    .build()
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, "No app available to open directions", Toast.LENGTH_SHORT).show() }
            }) { Icon(Icons.Default.Directions, "Directions to ${point.item.title}", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun MapMessage(title: String, message: String) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun visibleMapPoints(points: List<MapPointUi>, day: Int?, query: String): List<MapPointUi> =
    points.filter { point ->
        (day == null || (point.dayNumber ?: Int.MAX_VALUE) == day) &&
            (query.isBlank() || listOfNotNull(point.item.title, point.item.place?.name, point.item.place?.address)
                .any { it.contains(query.trim(), ignoreCase = true) })
    }.sortedWith(compareBy<MapPointUi> { it.dayNumber ?: Int.MAX_VALUE }.thenBy { it.item.sortOrder })

private fun MapPointUi.position() = LatLng(requireNotNull(item.place?.latitude), requireNotNull(item.place?.longitude))

private fun dayColor(index: Int): Color = listOf(Color(0xFF00796B), Color(0xFF4263C7), Color(0xFFB45B28), Color(0xFF8554A5), Color(0xFFB54365), Color(0xFF38758B))[index.coerceAtLeast(0) % 6]

private fun numberedMarker(number: Int, color: Color, selected: Boolean): BitmapDescriptor {
    val size = if (selected) 112 else 92
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    paint.color = android.graphics.Color.argb(35, 0, 0, 0)
    canvas.drawCircle(center, center + 3f, center - 3f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, center - 5f, paint)
    paint.color = color.toArgb()
    canvas.drawCircle(center, center, center - 11f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = if (number > 99) 28f else 36f
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    canvas.drawText(number.toString(), center, center - (paint.descent() + paint.ascent()) / 2, paint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private const val LIGHT_MAP_STYLE = """[
 {"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]},
 {"featureType":"landscape","elementType":"geometry","stylers":[{"color":"#f1f3ef"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#b9dcd9"}]}
]"""
private const val DARK_MAP_STYLE = """[
 {"elementType":"geometry","stylers":[{"color":"#242f3e"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#242f3e"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#b5c6cf"}]},
 {"featureType":"poi","elementType":"labels","stylers":[{"visibility":"off"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#38495a"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#172f3b"}]}
]"""
