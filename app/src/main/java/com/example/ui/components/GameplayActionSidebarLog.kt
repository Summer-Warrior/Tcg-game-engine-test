package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BuiltInTcgCatalog
import com.example.data.ClientSystemUiProfile
import com.example.data.SerializedGameBoardState
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold

/**
 * Categories of real-time gameplay actions & Firebase-synced move history recorded in the scrollable sidebar log.
 */
enum class GameplayActionCategory(
    val displayLabel: String,
    val badgeColor: Color
) {
    ALL("All", Color(0xFF00E5FF)),
    MOVE_HISTORY("Moves", Color(0xFF00E676)),
    CARD_PLAY("Card Plays", EmeraldReady),
    ATTACK("Attacks", CrimsonAlert),
    PHASE_CHANGE("Phases", RadiantGold),
    CHAIN_SYNC("Chain/Sync", Color(0xFFB388FF))
}

/**
 * Structured representation of a single gameplay event or Firebase-synced move history item in the sidebar log.
 */
data class GameplayActionLogEntry(
    val sequenceNumber: Int,
    val category: GameplayActionCategory,
    val opcodeBadge: String,
    val turnOrPhaseBadge: String,
    val headline: String,
    val detailLine: String,
    val rawLine: String,
    val isFirebaseSynced: Boolean = true
)

/**
 * Parses raw protocol and tabletop action log strings (including Firebase Firestore move history
 * entries such as `"Player 1 moved Card X to Field [MMZ-1]"`, `MOVE_HISTORY`, `MSG_MOVE`,
 * `MSG_SUMMONING`, `MSG_DAMAGE`, `MSG_NEW_PHASE`, `MSG_DRAW`, `MSG_CHAINING`, and `SYNC_BROADCAST`)
 * into structured, color-coded [GameplayActionLogEntry] items for the scrollable sidebar.
 */
object GameplayLogParser {

    fun parseLogLines(
        rawLogs: List<String>,
        currentTurn: Long,
        currentPhase: String,
        syncedBoardState: SerializedGameBoardState? = null
    ): List<GameplayActionLogEntry> {
        val visibleRawLogs = rawLogs
            .filterNot { it.startsWith(SerializedGameBoardState.SYNC_ENVELOPE_PREFIX) }
            .toMutableList()

        // Ensure the latest Firebase-synced move summary from syncedBoardState is included if not already present
        val remoteSummary = syncedBoardState?.lastEventSummary?.trim().orEmpty()
        if (remoteSummary.isNotBlank() &&
            visibleRawLogs.none { it.contains(remoteSummary, ignoreCase = true) }
        ) {
            visibleRawLogs.add(
                "MOVE_HISTORY [Firebase Rev #${syncedBoardState?.revisionNumber ?: 1} • Turn $currentTurn]: $remoteSummary"
            )
        }

        return visibleRawLogs.mapIndexed { idx, raw ->
            val seq = idx + 1
            val upper = raw.uppercase()

            val isExplicitMoveHistory = upper.contains("MOVED ") ||
                upper.contains("MOVE_HISTORY") ||
                upper.contains("MSG_MOVE") ||
                upper.contains("DRAG_MOVE") ||
                upper.contains("DRAG_DEPLOY") ||
                upper.contains("DRAG_STACK") ||
                upper.contains("PEER_DRAG_REPOSITION") ||
                upper.contains("PEER_CARD_DEPLOY") ||
                upper.contains("SYNC_BROADCAST")

            val category = when {
                isExplicitMoveHistory -> GameplayActionCategory.MOVE_HISTORY
                upper.contains("DAMAGE") ||
                    upper.contains("ATTACK") ||
                    upper.contains("BATTLE") ||
                    upper.contains("COMBAT") ||
                    upper.contains("DESTROY") ||
                    upper.contains("INFLICTED") ||
                    upper.contains("CLASH") ||
                    upper.contains("WINNER") ||
                    upper.contains("DUEL COMPLETE") -> GameplayActionCategory.ATTACK

                upper.contains("PHASE") ||
                    upper.contains("TURN") && (upper.contains("ENTERED") || upper.contains("ADVANCE") || upper.contains("SHIFT")) ||
                    upper.startsWith("[MSG_NEW_PHASE]") ||
                    upper.startsWith("MSG_NEW_PHASE") -> GameplayActionCategory.PHASE_CHANGE

                upper.contains("SUMMON") ||
                    upper.contains("DEPLOY") ||
                    upper.contains("DRAW") ||
                    upper.contains("DREW") ||
                    upper.contains("SET ") ||
                    upper.contains("MSG_SET") ||
                    upper.contains("ZONE_ACTIVATE") ||
                    upper.contains("CAST") -> GameplayActionCategory.CARD_PLAY

                else -> GameplayActionCategory.CHAIN_SYNC
            }

            val opcodeBadge = when (category) {
                GameplayActionCategory.MOVE_HISTORY -> when {
                    upper.contains("PLAYER 2") || upper.contains("PEER") -> "P2_MOVE_SYNC"
                    upper.contains("STACK") || upper.contains("CHAIN") -> "FIELD_STACK_MOVE"
                    else -> "P1_MOVE_SYNC"
                }
                GameplayActionCategory.CARD_PLAY -> when {
                    upper.contains("DRAW") || upper.contains("DREW") -> "MSG_DRAW"
                    upper.contains("SET") -> "MSG_SET"
                    else -> "MSG_SUMMONING"
                }
                GameplayActionCategory.ATTACK -> when {
                    upper.contains("COMPLETE") || upper.contains("WINNER") -> "DUEL_END"
                    upper.contains("COUNTER") -> "COUNTER_ATK"
                    else -> "MSG_DAMAGE"
                }
                GameplayActionCategory.PHASE_CHANGE -> "MSG_NEW_PHASE"
                GameplayActionCategory.CHAIN_SYNC -> when {
                    upper.contains("CHAIN") -> "MSG_CHAINING"
                    upper.contains("BIGINT21") || upper.contains("21-DIGIT") -> "BIGINT21_EXEC"
                    else -> "CTOS_SYNC"
                }
                GameplayActionCategory.ALL -> "EVENT"
            }

            val bracketRegex = Regex("\\[(.*?)]")
            val matches = bracketRegex.findAll(raw).map { it.groupValues[1] }.toList()
            val turnOrPhaseBadge = matches.firstOrNull {
                it.contains("Turn", ignoreCase = true) ||
                    it.contains("Rev", ignoreCase = true) ||
                    it.contains("Phase", ignoreCase = true) ||
                    it.contains("DP") || it.contains("MP") || it.contains("BP") || it.contains("EP")
            } ?: "T$currentTurn • $currentPhase"

            val cleanedBody = raw
                .replace(Regex("^\\[?[A-Z0-9_]+]?\\s*"), "")
                .replace(Regex("^\\[Turn[^]]*]\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^\\[[^]]*Rev\\s*#[0-9]+[^]]*]\\s*:?\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifBlank { raw }

            val headline = when (category) {
                GameplayActionCategory.MOVE_HISTORY -> {
                    when {
                        cleanedBody.contains("moved ", ignoreCase = true) -> cleanedBody
                        else -> "Player 1 moved $cleanedBody to Field"
                    }
                }
                GameplayActionCategory.CARD_PLAY -> cleanedBody
                GameplayActionCategory.ATTACK -> cleanedBody
                GameplayActionCategory.PHASE_CHANGE -> cleanedBody
                else -> cleanedBody
            }

            val detailLine = when (category) {
                GameplayActionCategory.MOVE_HISTORY ->
                    "Firebase Firestore Move Sync • Card Zone Transition Recorded"
                GameplayActionCategory.CARD_PLAY ->
                    "Card Play / Hand-to-Field Zone Transition"
                GameplayActionCategory.ATTACK ->
                    "Combat Calculation & Life Point Delta"
                GameplayActionCategory.PHASE_CHANGE ->
                    "Turn / Phase State Machine Transition"
                else ->
                    "Firebase Protocol & Chain Link Resolution"
            }

            GameplayActionLogEntry(
                sequenceNumber = seq,
                category = category,
                opcodeBadge = opcodeBadge,
                turnOrPhaseBadge = turnOrPhaseBadge,
                headline = headline,
                detailLine = detailLine,
                rawLine = raw,
                isFirebaseSynced = true
            )
        }
    }
}

/**
 * Scrollable real-time Game Event & Move History Sidebar Log component that records, filters, and
 * displays key gameplay actions—including move history such as `'Player 1 moved Card X to Field'`,
 * card plays, attacks, and phase changes—synced in real time via Firebase Firestore.
 *
 * Can be rendered as a docked right-hand sidebar on wide/tablet/split views or as an
 * interactive expandable sidebar panel on compact handheld screens.
 */
@Composable
fun GameplayActionSidebarLog(
    uiProfile: ClientSystemUiProfile,
    rawActionLogs: List<String>,
    turnNumber: Long,
    activePhase: String,
    isDockedSidebar: Boolean = false,
    syncedBoardState: SerializedGameBoardState? = null,
    onToggleDockedMode: (() -> Unit)? = null,
    onSelectPhaseFromSidebar: ((String) -> Unit)? = null,
    onRecordSyncedMoveFromSidebar: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(uiProfile.accentHex)
    var selectedCategory by remember { mutableStateOf(GameplayActionCategory.ALL) }
    var quickMoveCounter by remember(uiProfile.baseVariantId) { mutableIntStateOf(1) }

    val parsedEntries = remember(rawActionLogs, turnNumber, activePhase, syncedBoardState?.revisionNumber, syncedBoardState?.lastEventSummary) {
        GameplayLogParser.parseLogLines(
            rawLogs = rawActionLogs,
            currentTurn = turnNumber,
            currentPhase = activePhase,
            syncedBoardState = syncedBoardState
        )
    }

    val filteredEntries = remember(parsedEntries, selectedCategory) {
        if (selectedCategory == GameplayActionCategory.ALL) {
            parsedEntries.asReversed()
        } else if (selectedCategory == GameplayActionCategory.MOVE_HISTORY) {
            parsedEntries.filter {
                it.category == GameplayActionCategory.MOVE_HISTORY ||
                    it.category == GameplayActionCategory.CARD_PLAY
            }.asReversed()
        } else {
            parsedEntries.filter { it.category == selectedCategory }.asReversed()
        }
    }

    val moveCount = remember(parsedEntries) {
        parsedEntries.count {
            it.category == GameplayActionCategory.MOVE_HISTORY ||
                it.category == GameplayActionCategory.CARD_PLAY
        }
    }
    val cardPlayCount = remember(parsedEntries) {
        parsedEntries.count { it.category == GameplayActionCategory.CARD_PLAY }
    }
    val attackCount = remember(parsedEntries) {
        parsedEntries.count { it.category == GameplayActionCategory.ATTACK }
    }
    val phaseChangeCount = remember(parsedEntries) {
        parsedEntries.count { it.category == GameplayActionCategory.PHASE_CHANGE }
    }

    val listState = rememberLazyListState()

    // Automatically scroll to the newest entry at index 0 when new actions arrive
    LaunchedEffect(parsedEntries.size) {
        if (filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = accentColor.copy(alpha = 0.6f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("gameplay_action_sidebar_log"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Sidebar Header with Live Indicator, Firebase Sync Status & Dock Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(EmeraldReady, CircleShape)
                    )
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Firebase Synced Game Event & Move Log",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "FIREBASE GAME EVENT & MOVE LOG",
                            style = MaterialTheme.typography.labelLarge,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiProfile.hudBadgeLabel} • Turn $turnNumber • Phase: $activePhase • ${parsedEntries.size} Synced Events",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onToggleDockedMode != null) {
                    IconButton(
                        onClick = onToggleDockedMode,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("sidebar_log_dock_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalSplit,
                            contentDescription = if (isDockedSidebar) {
                                "Switch to Stacked Full-Width Log"
                            } else {
                                "Dock as Side-by-Side Sidebar"
                            },
                            tint = if (isDockedSidebar) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 1B. Firebase Real-Time Move History Sync Telemetry Bar + Quick Move Sync Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianElevated)
                    .border(1.dp, EmeraldReady.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("sidebar_firebase_sync_status_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Synced via Firebase Firestore",
                        tint = EmeraldReady,
                        modifier = Modifier.size(15.dp)
                    )
                    Column {
                        Text(
                            text = "SYNCED VIA FIREBASE • REV #${syncedBoardState?.revisionNumber ?: parsedEntries.size.coerceAtLeast(1)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldReady,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tracks 'Player 1 moved Card X to Field' in real time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onRecordSyncedMoveFromSidebar != null) {
                    Button(
                        onClick = {
                            val cards = BuiltInTcgCatalog.cardsForVariant(uiProfile.baseVariantId)
                            val card = cards[(quickMoveCounter - 1) % cards.size]
                            val slotCode = uiProfile.fieldSlotCodes[(quickMoveCounter - 1) % uiProfile.fieldSlotCodes.size]
                            val playerNum = if (quickMoveCounter % 2 == 1) 1 else 2
                            val moveLine = "MOVE_HISTORY [Turn $turnNumber • $activePhase]: Player $playerNum moved '${card.name}' to Field [$slotCode]"
                            quickMoveCounter++
                            onRecordSyncedMoveFromSidebar(moveLine)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldReady,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("sidebar_sync_move_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapCalls,
                            contentDescription = "Sync Move to Firebase",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+ Sync Move",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Live Telemetry Counter Strip (Moves, Plays, Attacks, Phases)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianElevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetryCounterPill(
                    label = "Moves",
                    count = moveCount,
                    color = Color(0xFF00E676)
                )
                TelemetryCounterPill(
                    label = "Plays",
                    count = cardPlayCount,
                    color = EmeraldReady
                )
                TelemetryCounterPill(
                    label = "Attacks",
                    count = attackCount,
                    color = CrimsonAlert
                )
                TelemetryCounterPill(
                    label = "Phases",
                    count = phaseChangeCount,
                    color = RadiantGold
                )
            }

            // 3. Interactive Phase Quick-Switch Strip (Records Phase Changes Directly Into Log)
            if (onSelectPhaseFromSidebar != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Quick Phase Transition (Tap to Shift Phase & Record):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiProfile.phaseStripCodes.forEach { phaseCode ->
                            val isCurrent = phaseCode.equals(activePhase, ignoreCase = true) ||
                                (activePhase == "MAIN" && (phaseCode == "MP1" || phaseCode == "MAIN-1" || phaseCode == "DEPLOY" || phaseCode == "MATERIALIZE"))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isCurrent) accentColor else ObsidianElevated
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrent) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectPhaseFromSidebar(phaseCode) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .testTag("sidebar_phase_button_$phaseCode")
                            ) {
                                Text(
                                    text = phaseCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) Color.Black else MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 4. Action Category Filter Chips (All, Moves, Card Plays, Attacks, Phases, Chain/Sync)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filter Gameplay Actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                GameplayActionCategory.entries.forEach { category ->
                    val selected = selectedCategory == category
                    FilterChip(
                        selected = selected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category.displayLabel,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = category.badgeColor.copy(alpha = 0.22f),
                            selectedLabelColor = category.badgeColor
                        ),
                        modifier = Modifier.testTag("sidebar_filter_${category.name}")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

            // 5. Scrollable Real-Time Feed of Gameplay Action & Move History Entries
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 210.dp, max = if (isDockedSidebar) 540.dp else 320.dp)
                    .testTag("sidebar_action_log_scroll_list"),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredEntries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianElevated)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No ${selectedCategory.displayLabel.lowercase()} recorded yet in this filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = filteredEntries,
                        key = { _, entry -> "${entry.sequenceNumber}_${entry.rawLine.hashCode()}" }
                    ) { idx, entry ->
                        GameplayActionEntryRow(
                            entry = entry,
                            isLatest = idx == 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryCounterPill(
    label: String,
    count: Int,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Text(
            text = "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GameplayActionEntryRow(
    entry: GameplayActionLogEntry,
    isLatest: Boolean
) {
    val badgeColor = entry.category.badgeColor
    val iconVector: ImageVector = when (entry.category) {
        GameplayActionCategory.MOVE_HISTORY -> Icons.Default.SwapCalls
        GameplayActionCategory.CARD_PLAY -> Icons.Default.Layers
        GameplayActionCategory.ATTACK -> Icons.Default.GpsFixed
        GameplayActionCategory.PHASE_CHANGE -> Icons.Default.Schedule
        GameplayActionCategory.CHAIN_SYNC -> Icons.Default.Bolt
        GameplayActionCategory.ALL -> Icons.AutoMirrored.Filled.CompareArrows
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isLatest) badgeColor.copy(alpha = 0.12f) else ObsidianElevated
            )
            .border(
                width = if (isLatest) 1.2.dp else 1.dp,
                color = if (isLatest) badgeColor.copy(alpha = 0.7f) else badgeColor.copy(alpha = 0.28f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
            .testTag("sidebar_log_item_${entry.sequenceNumber}"),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Category Icon Pill
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = entry.category.displayLabel,
                tint = badgeColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
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
                        text = "#${entry.sequenceNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = entry.opcodeBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isLatest) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EmeraldReady.copy(alpha = 0.22f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "FIREBASE LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldReady,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = entry.turnOrPhaseBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = entry.headline,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isLatest || entry.category == GameplayActionCategory.MOVE_HISTORY) {
                    FontWeight.Bold
                } else {
                    FontWeight.Medium
                }
            )

            Text(
                text = entry.detailLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
