package com.wanderlog.android.presentation.itinerary.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wanderlog.android.core.util.FriendlyDateTimeParts
import com.wanderlog.android.core.util.flightDetailLine
import com.wanderlog.android.core.util.notesForDisplay
import com.wanderlog.android.core.util.toFriendlyDateTimePartsOrNull
import com.wanderlog.android.core.util.toCurrencyString
import com.wanderlog.android.domain.model.Expense
import com.wanderlog.android.core.util.toFriendlyDateTimeOrSelf
import com.wanderlog.android.domain.model.ItineraryItem
import com.wanderlog.android.domain.model.ItineraryItemType

@Composable
fun ItineraryItemCard(
    item: ItineraryItem,
    linkedExpense: Expense? = null,
    attachmentCount: Int = 0,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onOpenInMaps: (() -> Unit)? = null,
    onManageAttachments: (() -> Unit)? = null,
    dragHandle: @Composable (() -> Unit)? = null
) {
    val accent = item.itemType.accent()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored badge with the type icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.itemType.icon(),
                    contentDescription = item.itemType.name,
                    modifier = Modifier.size(22.dp),
                    tint = accent
                )
            }
            Spacer(Modifier.width(12.dp))

            // Title + metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                val hasMeta = item.startTime != null || item.endTime != null || item.place?.address != null
                if (hasMeta) Spacer(Modifier.height(3.dp))

                if (item.itemType == ItineraryItemType.FLIGHT) {
                    item.startTime?.let { start ->
                        FlightScheduleBlock(
                            label = "Depart",
                            parts = start.toFriendlyDateTimePartsOrNull(),
                            fallback = start.toFriendlyDateTimeOrSelf(),
                            location = item.flightDetailLine("Departure"),
                            accent = accent
                        )
                    }
                    item.endTime?.let { end ->
                        FlightScheduleBlock(
                            label = "Arrive",
                            parts = end.toFriendlyDateTimePartsOrNull(),
                            fallback = end.toFriendlyDateTimeOrSelf(),
                            location = item.flightDetailLine("Arrival"),
                            accent = accent
                        )
                    }
                } else {
                    item.startTime?.let {
                        val endTime = item.endTime.normalizedDisplayValue()
                        val time = if (endTime != null) {
                            "${it.toFriendlyDateTimeOrSelf()} – ${endTime.toFriendlyDateTimeOrSelf()}"
                        } else {
                            it.toFriendlyDateTimeOrSelf()
                        }
                        Text(
                            time,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (item.itemType != ItineraryItemType.FLIGHT) {
                    item.place?.address?.let { addr ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                addr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                            onOpenInMaps?.let {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable(onClick = it)
                                        .background(accent.copy(alpha = 0.10f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Open in Google Maps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item.bookingRef?.let { ref ->
                    Text(
                        "Ref: $ref",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                item.rating?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "$rating/10",
                            style = MaterialTheme.typography.bodySmall,
                            color = accent,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
                linkedExpense?.let { expense ->
                    val budgetLabel = if (expense.breakdown.isNotEmpty() && expense.activeAmount != expense.amount) {
                        "Budget: ${expense.activeAmount.toCurrencyString(expense.currencyCode)} (Total: ${expense.amount.toCurrencyString(expense.currencyCode)})"
                    } else {
                        "Budget: ${expense.amount.toCurrencyString(expense.currencyCode)}"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            budgetLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = accent,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    if (expense.breakdown.isNotEmpty()) {
                        val breakdownSummary = expense.breakdown.joinToString(" • ") { part ->
                            val formatted = "${part.amount.toCurrencyString(expense.currencyCode)} → ${part.label}"
                            if (part.isActive) formatted else "$formatted (inactive)"
                        }
                        Text(
                            breakdownSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                item.notesForDisplay()?.let { notes ->
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
                if (onManageAttachments != null && attachmentCount > 0) {
                    TextButton(onClick = onManageAttachments, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (attachmentCount == 1) "1 attachment" else "$attachmentCount attachments",
                            color = accent
                        )
                    }
                }
            }

            dragHandle?.invoke()
        }
    }
}

private fun String?.normalizedDisplayValue(): String? =
    this?.trim()?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }

@Composable
private fun FlightScheduleBlock(
    label: String,
    parts: FriendlyDateTimeParts?,
    fallback: String,
    location: String?,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label:",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = parts?.let { scheduleParts ->
                    scheduleParts.time?.let { "${scheduleParts.date} • $it" } ?: scheduleParts.date
                } ?: fallback,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
        location?.let { place ->
            Text(
                text = place,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

private fun ItineraryItemType.icon(): ImageVector = when (this) {
    ItineraryItemType.FLIGHT -> Icons.Default.Flight
    ItineraryItemType.HOTEL -> Icons.Default.Hotel
    ItineraryItemType.ACTIVITY -> Icons.Default.Map
    ItineraryItemType.FOOD -> Icons.Default.Restaurant
    ItineraryItemType.GROCERIES -> Icons.Default.LocalGroceryStore
    ItineraryItemType.SHOPPING -> Icons.Default.ShoppingBag
    ItineraryItemType.NOTE -> Icons.AutoMirrored.Filled.Note
    ItineraryItemType.TRANSPORT -> Icons.Default.Train
    ItineraryItemType.PLACE -> Icons.Default.Place
}

private fun ItineraryItemType.accent(): Color = when (this) {
    ItineraryItemType.FLIGHT -> Color(0xFF2D7FF9)      // sky blue
    ItineraryItemType.HOTEL -> Color(0xFF8B5CF6)       // purple
    ItineraryItemType.ACTIVITY -> Color(0xFFEF4444)    // red
    ItineraryItemType.FOOD -> Color(0xFFD97706)        // amber orange
    ItineraryItemType.GROCERIES -> Color(0xFF16A34A)   // green
    ItineraryItemType.SHOPPING -> Color(0xFFEC4899)    // pink
    ItineraryItemType.NOTE -> Color(0xFF6B7280)        // slate
    ItineraryItemType.TRANSPORT -> Color(0xFF10B981)   // emerald
    ItineraryItemType.PLACE -> Color(0xFFF59E0B)       // amber
}
