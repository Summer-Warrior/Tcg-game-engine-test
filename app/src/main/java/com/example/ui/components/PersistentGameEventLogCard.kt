package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MatchGameEvent
import com.example.data.PersistentGameEventType
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold

/**
 * Persistent, scrollable, Firestore-synchronized Game Event Log component.
 *
 * Streams immutable event documents from `/matches/{matchId}/events` in real time across
 * connected clients, rendering entries such as `"Player 1 played Blue-Eyes White Dragon"`
 * or `"Life points reduced by 5"`, with category filtering, auto-scroll lock, expandable height,
 * and a quick event logger bar for tabletop bookkeeping.
 */
@Composable
fun PersistentGameEventLogCard(
    matchId: String,
    events: List<MatchGameEvent>,
    isFirestoreConnected: Boolean,
    errorMessage: String? = null,
    sampleCardName: String = "Chrono Dragon",
    defaultLifeDeltaStep: Long = 5L,
    onLogQuickCardPlay: (String) -> Unit,
    onLogQuickLifeReduction: (Long) -> Unit,
    onLogCustomEvent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var autoScrollEnabled by rememberSaveable { mutableStateOf(true) }
    var isExpandedHeight by rememberSaveable { mutableStateOf(false) }
    var customEventInput by rememberSaveable { mutableStateOf("") }

    val filteredEvents = remember(events, selectedFilter) {
        if (selectedFilter == "ALL") {
            events
        } else {
            events.filter { it.eventType.equals(selectedFilter, ignoreCase = true) }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(filteredEvents.size, autoScrollEnabled) {
        if (autoScrollEnabled && filteredEvents.isNotEmpty()) {
            listState.animateScrollToItem(filteredEvents.lastIndex)
        }
    }

    val syncBadgeColor = when {
        errorMessage != null -> CrimsonAlert
        isFirestoreConnected && matchId.isNotBlank() -> EmeraldReady
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = syncBadgeColor.copy(alpha = 0.55f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("persistent_firestore_event_log_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. Header: Persistent Log Title + Firestore Sync Status + Auto-Scroll / Expand Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = stringResource(id = R.string.persistent_event_log_title),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.persistent_event_log_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isFirestoreConnected && matchId.isNotBlank()) {
                                    Icons.Default.CloudDone
                                } else {
                                    Icons.Default.CloudOff
                                },
                                contentDescription = "Firestore event log sync status",
                                tint = syncBadgeColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFirestoreConnected && matchId.isNotBlank()) {
                                    "FIRESTORE PERSISTENT • ${events.size} event(s) • Room ${matchId.take(8)}"
                                } else {
                                    "OFFLINE PREVIEW • Attach a match room to persist events"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = syncBadgeColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { autoScrollEnabled = !autoScrollEnabled },
                        modifier = Modifier.testTag("event_log_autoscroll_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignBottom,
                            contentDescription = "Toggle Auto-Scroll to Latest Event",
                            tint = if (autoScrollEnabled) EmeraldReady else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { isExpandedHeight = !isExpandedHeight },
                        modifier = Modifier.testTag("event_log_expand_toggle")
                    ) {
                        Icon(
                            imageVector = if (isExpandedHeight) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand or Collapse Scrollable Event Log",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonAlert,
                    modifier = Modifier.testTag("event_log_error_banner")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Category Filter Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filterItems = listOf(
                    "ALL" to "All (${events.size})",
                    PersistentGameEventType.CARD_PLAYED.code to "Card Plays",
                    PersistentGameEventType.LIFE_CHANGED.code to "Life Points",
                    PersistentGameEventType.COMBAT_ATTACK.code to "Combat",
                    PersistentGameEventType.BOARD_MOVE.code to "Board Moves",
                    PersistentGameEventType.PHASE_CHANGED.code to "Phases"
                )
                filterItems.forEach { (code, label) ->
                    FilterChip(
                        selected = selectedFilter == code,
                        onClick = { selectedFilter = code },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("event_log_filter_$code")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Persistent Scrollable Game Event Feed
            val viewportHeight = if (isExpandedHeight) 420.dp else 240.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewportHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                if (filteredEvents.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = if (selectedFilter == "ALL") {
                                "No synchronized game events recorded yet."
                            } else {
                                "No events matching filter '$selectedFilter'."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Play a card, adjust life points, or use the quick logger below to persist events to Firestore.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("persistent_event_log_scrollable_list")
                    ) {
                        itemsIndexed(
                            items = filteredEvents,
                            key = { idx, item -> item.id.ifBlank { "evt_${idx}_${item.summary.hashCode()}" } }
                        ) { index, event ->
                            PersistentEventRow(
                                sequenceNumber = index + 1,
                                event = event
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(8.dp))

            // 4. Quick Action Event Buttons + Custom Game Event Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onLogQuickCardPlay(sampleCardName) },
                    enabled = matchId.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("event_log_quick_play_card_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = "Log Card Played Event",
                        tint = EmeraldReady,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Log Card Play",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { onLogQuickLifeReduction(defaultLifeDeltaStep) },
                    enabled = matchId.isNotBlank(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("event_log_quick_lp_damage_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Log Life Points Reduced Event",
                        tint = CrimsonAlert,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Log LP -$defaultLifeDeltaStep",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonAlert,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customEventInput,
                    onValueChange = { customEventInput = it },
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.persistent_event_log_input_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    enabled = matchId.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("event_log_custom_input")
                )
                IconButton(
                    onClick = {
                        val trimmed = customEventInput.trim()
                        if (trimmed.isNotBlank()) {
                            onLogCustomEvent(trimmed)
                            customEventInput = ""
                        }
                    },
                    enabled = matchId.isNotBlank() && customEventInput.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (matchId.isNotBlank() && customEventInput.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .testTag("event_log_send_custom_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Custom Event to Firestore Log",
                        tint = if (matchId.isNotBlank() && customEventInput.isNotBlank()) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersistentEventRow(
    sequenceNumber: Int,
    event: MatchGameEvent
) {
    val eventType = event.parsedType
    val (accentColor, iconVector) = eventVisualStyle(eventType, event.lifeDelta)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.38f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left Icon Badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = eventType.displayLabel,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "#${sequenceNumber.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val badgeLabel = when {
                        eventType == PersistentGameEventType.LIFE_CHANGED && event.lifeDelta < 0L ->
                            "LP ${event.lifeDelta}"
                        eventType == PersistentGameEventType.LIFE_CHANGED && event.lifeDelta > 0L ->
                            "LP +${event.lifeDelta}"
                        else -> eventType.shortBadge
                    }
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = "T${event.turnNumber} • ${event.activePhase}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = event.formattedTime(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = event.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            val metaLine = buildString {
                append(event.actorLabel)
                if (event.detail.isNotBlank()) {
                    append(" • ")
                    append(event.detail)
                }
            }
            if (metaLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun eventVisualStyle(
    type: PersistentGameEventType,
    lifeDelta: Long
): Pair<Color, ImageVector> {
    return when (type) {
        PersistentGameEventType.CARD_PLAYED -> EmeraldReady to Icons.Default.Style
        PersistentGameEventType.LIFE_CHANGED -> {
            if (lifeDelta < 0L) CrimsonAlert to Icons.Default.Favorite
            else EmeraldReady to Icons.Default.Favorite
        }
        PersistentGameEventType.COMBAT_ATTACK -> CrimsonAlert to Icons.Default.Bolt
        PersistentGameEventType.BOARD_MOVE -> MaterialTheme.colorScheme.primary to Icons.Default.SwapHoriz
        PersistentGameEventType.PHASE_CHANGED -> RadiantGold to Icons.Default.PlayArrow
        PersistentGameEventType.SYSTEM_SYNC -> MaterialTheme.colorScheme.secondary to Icons.Default.Sync
        PersistentGameEventType.CUSTOM_EVENT -> MaterialTheme.colorScheme.primary to Icons.Default.History
    }
}
