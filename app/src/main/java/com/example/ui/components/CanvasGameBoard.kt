package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CardTemplate
import com.example.data.ClientSystemUiProfile
import com.example.data.SerializedGameBoardState
import com.example.data.SerializedZonePlacement
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold
import kotlin.math.hypot

/**
 * Identifies which player owns a play area zone on the Canvas GameBoard.
 */
enum class BoardPlayerOwner {
    PLAYER_1_HOST,
    PLAYER_2_OPPONENT,
    SHARED_NEXUS
}

/**
 * Functional role of a zone on the two-player Canvas GameBoard.
 */
enum class BoardZoneKind {
    MAIN_UNIT_ZONE,
    SUPPORT_SPELL_MANA_ZONE,
    SHARED_EMZ_OR_STACK,
    DECK_OR_GRAVEYARD,
    HAND_DOCK_SLOT
}

/**
 * Calculated responsive bounding box and metadata for a single zone on the Canvas GameBoard.
 */
data class CanvasBoardZoneSpec(
    val zoneId: String,
    val codeLabel: String,
    val subtitle: String,
    val owner: BoardPlayerOwner,
    val kind: BoardZoneKind,
    val rect: Rect
)

/**
 * Represents a card positioned inside a specific zone on the Canvas GameBoard.
 */
data class CanvasPlacedCard(
    val instanceId: String,
    val card: CardTemplate,
    val owner: BoardPlayerOwner,
    val isDefenseOrTapped: Boolean = false,
    val stackCount: Int = 1
) {
    fun toSerializedZonePlacement(zoneId: String): SerializedZonePlacement {
        return SerializedZonePlacement(
            zoneId = zoneId,
            owner = owner.name,
            cardId = card.id,
            cardName = card.name,
            cost = card.cost,
            attack = card.attack,
            defense = card.defense,
            cardType = card.cardType,
            abilityText = card.abilityText,
            attack21 = card.attack21,
            defense21 = card.defense21,
            passcode = card.passcode,
            isDefenseOrTapped = isDefenseOrTapped,
            stackCount = stackCount
        )
    }

    companion object {
        fun fromSerializedZonePlacement(
            placement: SerializedZonePlacement,
            baseVariantId: String
        ): CanvasPlacedCard {
            val resolvedOwner = when {
                placement.owner.contains("PLAYER_2", ignoreCase = true) ||
                    placement.zoneId.startsWith("P2_", ignoreCase = true) -> BoardPlayerOwner.PLAYER_2_OPPONENT
                placement.owner.contains("SHARED", ignoreCase = true) ||
                    placement.zoneId.startsWith("SHARED_", ignoreCase = true) -> BoardPlayerOwner.SHARED_NEXUS
                else -> BoardPlayerOwner.PLAYER_1_HOST
            }
            val card = placement.toCardTemplate(baseVariantId)
            return CanvasPlacedCard(
                instanceId = "sync_${placement.zoneId}_${card.id}",
                card = card,
                owner = resolvedOwner,
                isDefenseOrTapped = placement.isDefenseOrTapped,
                stackCount = placement.stackCount.coerceAtLeast(1)
            )
        }
    }
}

/**
 * Responsive Jetpack Compose `Canvas`-based `GameBoard` component that renders play areas (zones)
 * for two players (Player 1 Host Commander on the bottom half, Player 2 Opponent on the top half,
 * plus shared Extra Monster / Command / Stack zones in the center) and supports full touch-based
 * drag-and-drop card movement (`detectDragGestures`) as well as real-time Firebase Firestore
 * serialized state broadcasting between two connected clients.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CanvasGameBoard(
    uiProfile: ClientSystemUiProfile,
    hostName: String,
    opponentName: String,
    hostLife: Long,
    opponentLife: Long,
    hostUnits: List<CardTemplate>,
    opponentUnits: List<CardTemplate>,
    handCards: List<CardTemplate>,
    turnNumber: Long,
    activePhase: String,
    syncedBoardState: SerializedGameBoardState? = null,
    onDeployCardFromHand: (CardTemplate, String) -> Unit = { _, _ -> },
    onMoveCardBetweenZones: (CardTemplate, String, String) -> Unit = { _, _, _ -> },
    onAttackOpponentZone: (CardTemplate, String, CardTemplate?) -> Unit = { _, _, _ -> },
    onDrawCardRequest: () -> Unit = {},
    onBroadcastBoardState: (Map<String, SerializedZonePlacement>, String, String) -> Unit = { _, _, _ -> },
    onTriggerPeerClientSyncMove: (Map<String, SerializedZonePlacement>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accentColor = Color(uiProfile.accentHex)
    var isSpeedDuel3ZoneMode by remember(uiProfile.baseVariantId) { mutableStateOf(false) }
    var masterDuelChainIdx by remember(uiProfile.baseVariantId) { mutableIntStateOf(0) }
    var mateTapCount by remember(uiProfile.baseVariantId) { mutableIntStateOf(0) }
    var crossDuelLaneIdx by remember(uiProfile.baseVariantId) { mutableIntStateOf(1) }
    var crossDuelSkillIdx by remember(uiProfile.baseVariantId) { mutableIntStateOf(0) }
    var mtgoActiveShortcut by remember(uiProfile.baseVariantId) {
        mutableStateOf(uiProfile.mtgoPriorityShortcuts.firstOrNull() ?: "F2: Pass Priority (OK)")
    }
    var mtgoFloatingManaCount by remember(uiProfile.baseVariantId) { mutableIntStateOf(3) }
    var mtgaAutoTapEnabled by remember(uiProfile.baseVariantId) { mutableStateOf(true) }
    var mtgaFullControlEnabled by remember(uiProfile.baseVariantId) { mutableStateOf(false) }
    var lastEmoteBanner by remember(uiProfile.baseVariantId) { mutableStateOf<String?>(null) }

    val mainSlotCount = if (isSpeedDuel3ZoneMode) 3 else uiProfile.fieldSlotCodes.size.coerceIn(5, 6)
    val supportSlotCount = if (isSpeedDuel3ZoneMode) 3 else uiProfile.spellOrSupportSlotCodes.size.coerceIn(5, 5)

    // Zone -> Placed Card state map for interactive touch drag-and-drop
    val zoneCardMap = remember(uiProfile.baseVariantId) {
        mutableStateMapOf<String, CanvasPlacedCard>()
    }
    val dragHistoryLogs = remember(uiProfile.baseVariantId) {
        mutableStateListOf<String>()
    }
    var moveCounter by remember(uiProfile.baseVariantId) { mutableIntStateOf(0) }
    var showSerializedJsonInspector by remember(uiProfile.baseVariantId) { mutableStateOf(false) }

    // Selected zone for tap-inspect / tap-to-move accessibility
    var selectedZoneId by remember(uiProfile.baseVariantId) { mutableStateOf<String?>(null) }

    // Active Drag State
    var draggingFromZoneId by remember { mutableStateOf<String?>(null) }
    var draggingCard by remember { mutableStateOf<CanvasPlacedCard?>(null) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var dragCurrentOffset by remember { mutableStateOf(Offset.Zero) }
    var hoveredTargetZoneId by remember { mutableStateOf<String?>(null) }

    fun snapshotSerializedPlacements(): Map<String, SerializedZonePlacement> {
        return zoneCardMap.entries
            .filter { (zoneKey, _) ->
                !zoneKey.startsWith("P1_HAND_") && zoneKey != "P1_DECK" && zoneKey != "P2_DECK"
            }
            .associate { (zoneKey, placed) ->
                zoneKey to placed.toSerializedZonePlacement(zoneKey)
            }
    }

    fun emitBoardSyncBroadcast(eventType: String, eventSummary: String) {
        val placements = snapshotSerializedPlacements()
        onBroadcastBoardState(placements, eventType, eventSummary)
    }

    // Apply incoming real-time Firestore serialized state snapshots from connected clients
    LaunchedEffect(
        syncedBoardState?.matchId,
        syncedBoardState?.revisionNumber,
        syncedBoardState?.syncedTimestampMillis,
        syncedBoardState?.zonePlacements?.size
    ) {
        val remote = syncedBoardState ?: return@LaunchedEffect
        if (remote.zonePlacements.isNotEmpty()) {
            // Remove previous battlefield/support/shared zones that were cleared or moved remotely
            val keysToRemove = zoneCardMap.keys.filter { key ->
                !key.startsWith("P1_HAND_") && key != "P1_DECK" && key != "P2_DECK" &&
                    !remote.zonePlacements.containsKey(key)
            }
            keysToRemove.forEach { zoneCardMap.remove(it) }

            // Apply updated remote zone placements
            remote.zonePlacements.forEach { (zoneKey, placement) ->
                zoneCardMap[zoneKey] = CanvasPlacedCard.fromSerializedZonePlacement(
                    placement = placement,
                    baseVariantId = uiProfile.baseVariantId
                )
            }
            val syncNotice = "[FIRESTORE_SYNC Rev #${remote.revisionNumber}] ${remote.lastBroadcastClientName}: ${remote.lastEventSummary}"
            if (dragHistoryLogs.firstOrNull() != syncNotice) {
                dragHistoryLogs.add(0, syncNotice)
                if (dragHistoryLogs.size > 14) {
                    dragHistoryLogs.removeLast()
                }
            }
        }
    }

    // Synchronize external hostUnits, opponentUnits, and handCards into the interactive board state
    LaunchedEffect(uiProfile.baseVariantId, hostUnits.size, opponentUnits.size, handCards.size) {
        val hasRemoteSyncPlacements = syncedBoardState?.zonePlacements?.isNotEmpty() == true
        if (!hasRemoteSyncPlacements) {
            // Populate Player 1 Main Zones
            uiProfile.fieldSlotCodes.take(mainSlotCount).forEachIndexed { idx, _ ->
                val zoneKey = "P1_MAIN_$idx"
                val unit = hostUnits.getOrNull(idx)
                if (unit != null && !zoneCardMap.containsKey(zoneKey)) {
                    zoneCardMap[zoneKey] = CanvasPlacedCard(
                        instanceId = "p1_main_${unit.id}_$idx",
                        card = unit,
                        owner = BoardPlayerOwner.PLAYER_1_HOST
                    )
                }
            }
            // Populate Player 2 Opponent Main Zones
            uiProfile.fieldSlotCodes.take(mainSlotCount).forEachIndexed { idx, _ ->
                val zoneKey = "P2_MAIN_$idx"
                val oppUnit = opponentUnits.getOrNull(idx)
                if (oppUnit != null && !zoneCardMap.containsKey(zoneKey)) {
                    zoneCardMap[zoneKey] = CanvasPlacedCard(
                        instanceId = "p2_main_${oppUnit.id}_$idx",
                        card = oppUnit,
                        owner = BoardPlayerOwner.PLAYER_2_OPPONENT
                    )
                }
            }
        }
        // Populate Player 1 Hand Dock Slots (up to 5 visible draggable hand slots on Canvas)
        for (idx in 0 until 5) {
            val handZoneKey = "P1_HAND_$idx"
            val handCard = handCards.getOrNull(idx)
            if (handCard != null) {
                zoneCardMap[handZoneKey] = CanvasPlacedCard(
                    instanceId = "p1_hand_${handCard.id}_$idx",
                    card = handCard,
                    owner = BoardPlayerOwner.PLAYER_1_HOST
                )
            } else {
                zoneCardMap.remove(handZoneKey)
            }
        }
        // Ensure Deck & Graveyard stacks have representative cards so users can inspect/drag
        val sampleCard = handCards.firstOrNull() ?: hostUnits.firstOrNull()
        if (sampleCard != null) {
            if (!zoneCardMap.containsKey("P1_DECK")) {
                zoneCardMap["P1_DECK"] = CanvasPlacedCard(
                    instanceId = "p1_deck_top",
                    card = sampleCard,
                    owner = BoardPlayerOwner.PLAYER_1_HOST,
                    stackCount = 40
                )
            }
            if (!zoneCardMap.containsKey("P2_DECK")) {
                zoneCardMap["P2_DECK"] = CanvasPlacedCard(
                    instanceId = "p2_deck_top",
                    card = opponentUnits.firstOrNull() ?: sampleCard,
                    owner = BoardPlayerOwner.PLAYER_2_OPPONENT,
                    stackCount = 40
                )
            }
        }
    }

    // Build live serialized state representation (either from Firestore snapshot or current local Canvas state)
    val effectiveSerializedState = remember(
        syncedBoardState,
        zoneCardMap.entries.map { "${it.key}:${it.value.card.name}:${it.value.isDefenseOrTapped}:${it.value.stackCount}" },
        turnNumber,
        activePhase,
        hostLife,
        opponentLife
    ) {
        val currentPlacements = snapshotSerializedPlacements()
        if (syncedBoardState != null) {
            syncedBoardState.copy(
                zonePlacements = if (currentPlacements.isNotEmpty()) currentPlacements else syncedBoardState.zonePlacements,
                turnNumber = turnNumber,
                activePhase = activePhase,
                hostLife = hostLife,
                opponentLife = opponentLife
            )
        } else {
            SerializedGameBoardState(
                matchId = "local_arena_${uiProfile.baseVariantId.lowercase()}",
                baseVariantId = uiProfile.baseVariantId,
                baseVariantName = uiProfile.systemTitle,
                revisionNumber = moveCounter + 1,
                turnNumber = turnNumber,
                activePhase = activePhase,
                hostLife = hostLife,
                opponentLife = opponentLife,
                connectedClients = listOf(hostName, opponentName),
                lastBroadcastClientName = hostName,
                lastEventType = "CANVAS_LIVE_STATE",
                lastEventSummary = dragHistoryLogs.firstOrNull() ?: "2-Player Canvas GameBoard active",
                zonePlacements = currentPlacements
            )
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .testTag("canvas_game_board_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Bar: Title, 2-Player Life Badges & Reset/Rotate Actions
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
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PanToolAlt,
                            contentDescription = "Interactive Touch Drag and Drop GameBoard",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "CANVAS 2-PLAYER GAMEBOARD (FIRESTORE SYNC)",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .background(
                                        EmeraldReady.copy(alpha = 0.18f),
                                        RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "REV #${effectiveSerializedState.revisionNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldReady,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "${uiProfile.boardLayoutType} • Real-time Firestore broadcast between connected clients",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        zoneCardMap.clear()
                        uiProfile.fieldSlotCodes.take(mainSlotCount).forEachIndexed { idx, _ ->
                            hostUnits.getOrNull(idx)?.let { unit ->
                                zoneCardMap["P1_MAIN_$idx"] = CanvasPlacedCard(
                                    instanceId = "p1_main_${unit.id}_$idx",
                                    card = unit,
                                    owner = BoardPlayerOwner.PLAYER_1_HOST
                                )
                            }
                            opponentUnits.getOrNull(idx)?.let { oppUnit ->
                                zoneCardMap["P2_MAIN_$idx"] = CanvasPlacedCard(
                                    instanceId = "p2_main_${oppUnit.id}_$idx",
                                    card = oppUnit,
                                    owner = BoardPlayerOwner.PLAYER_2_OPPONENT
                                )
                            }
                        }
                        for (idx in 0 until 5) {
                            handCards.getOrNull(idx)?.let { handCard ->
                                zoneCardMap["P1_HAND_$idx"] = CanvasPlacedCard(
                                    instanceId = "p1_hand_${handCard.id}_$idx",
                                    card = handCard,
                                    owner = BoardPlayerOwner.PLAYER_1_HOST
                                )
                            }
                        }
                        selectedZoneId = null
                        val summary = "Reset 2-player Canvas zones & synchronized canonical state to Firestore"
                        dragHistoryLogs.add(0, "[BOARD_SYNC] $summary")
                        emitBoardSyncBroadcast("BOARD_RESET_SYNC", summary)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("canvas_board_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Board Positions",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sync Zones", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Player 2 (Top) vs Player 1 (Bottom) Status Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(CrimsonAlert, CircleShape)
                    )
                    Text(
                        text = "P2 TOP: $opponentName ($opponentLife ${uiProfile.lifeLabel})",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonAlert,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "T$turnNumber • $activePhase",
                    style = MaterialTheme.typography.labelSmall,
                    color = RadiantGold,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(EmeraldReady, CircleShape)
                    )
                    Text(
                        text = "P1 BOTTOM: $hostName ($hostLife ${uiProfile.lifeLabel})",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldReady,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 5-System Unified Client HUD Strip (Duel Links • Master Duel • Cross Duel • MTGO • MTG Arena)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianElevated)
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
                    .testTag("five_system_client_hud_panel"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE CLIENT ASPECTS: ${uiProfile.inspiredSystemsSummary.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold,
                        fontWeight = FontWeight.Bold
                    )
                    lastEmoteBanner?.let { emoteText ->
                        Text(
                            text = emoteText,
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldReady,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Row 1: Duel Links (Character Skill + 3-Zone Speed Duel Toggle) & Master Duel (Chain ON/AUTO/OFF + Companion Mate)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentChainMode = uiProfile.masterDuelChainModes.getOrElse(
                        masterDuelChainIdx % uiProfile.masterDuelChainModes.size.coerceAtLeast(1)
                    ) { "CHAIN: AUTO" }

                    FilterChip(
                        selected = currentChainMode.contains("ON"),
                        onClick = {
                            masterDuelChainIdx = (masterDuelChainIdx + 1) % uiProfile.masterDuelChainModes.size.coerceAtLeast(1)
                            val nextChain = uiProfile.masterDuelChainModes.getOrElse(masterDuelChainIdx) { "CHAIN: AUTO" }
                            val summary = "Player 1 switched Master Duel Fast-Effect toggle to [$nextChain]"
                            dragHistoryLogs.add(0, "[MASTER_DUEL] $summary")
                            emitBoardSyncBroadcast("MASTER_DUEL_CHAIN", summary)
                        },
                        label = {
                            Text(
                                text = "MD $currentChainMode",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.testTag("hud_master_duel_chain_toggle")
                    )

                    FilterChip(
                        selected = mateTapCount > 0,
                        onClick = {
                            mateTapCount++
                            val summary = "Player 1 tapped Master Duel Mate '${uiProfile.masterDuelMateName}' (Reaction #$mateTapCount • ${uiProfile.masterDuelFoilFinish})"
                            lastEmoteBanner = "Mate: ${uiProfile.masterDuelMateName} ✨"
                            dragHistoryLogs.add(0, "[MASTER_DUEL_MATE] $summary")
                            emitBoardSyncBroadcast("MASTER_DUEL_MATE", summary)
                        },
                        label = {
                            Text(
                                text = "Mate: ${uiProfile.masterDuelMateName}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("hud_master_duel_mate_button")
                    )

                    FilterChip(
                        selected = true,
                        onClick = {
                            onDrawCardRequest()
                            val summary = "Player 1 activated Duel Links Skill '${uiProfile.duelLinksSkillName}' — ${uiProfile.duelLinksSkillEffect}"
                            lastEmoteBanner = "SKILL: ${uiProfile.duelLinksSkillName}!"
                            dragHistoryLogs.add(0, "[DUEL_LINKS_SKILL] $summary")
                            emitBoardSyncBroadcast("DUEL_LINKS_SKILL", summary)
                        },
                        label = {
                            Text(
                                text = "DL Skill: ${uiProfile.duelLinksSkillName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.testTag("hud_duel_links_skill_button")
                    )

                    FilterChip(
                        selected = isSpeedDuel3ZoneMode,
                        onClick = {
                            isSpeedDuel3ZoneMode = !isSpeedDuel3ZoneMode
                            val formatName = if (isSpeedDuel3ZoneMode) {
                                "Duel Links 3-Zone Speed Duel (${uiProfile.speedDuelSlotCodes.joinToString("/")})"
                            } else {
                                "Standard ${uiProfile.fieldSlotCodes.size}-Zone Master/Tabletop Format"
                            }
                            val summary = "Player 1 switched field geometry to $formatName"
                            dragHistoryLogs.add(0, "[DUEL_LINKS_FORMAT] $summary")
                            emitBoardSyncBroadcast("SPEED_DUEL_FORMAT", summary)
                        },
                        label = {
                            Text(
                                text = if (isSpeedDuel3ZoneMode) "DL Format: 3-Zone Speed Duel" else "Format: ${uiProfile.fieldSlotCodes.size}-Zone Standard",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("hud_duel_links_speed_zone_toggle")
                    )
                }

                // Row 2: Cross Duel (3-Lane Tactical Clash & 3 Ace Skills) + MTGO (Chess Clock, F2/F6/F8 Priority & Mana Pool)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeLane = uiProfile.crossDuelLanes.getOrElse(
                        crossDuelLaneIdx % uiProfile.crossDuelLanes.size.coerceAtLeast(1)
                    ) { "CENTER BOSS LANE" }
                    val activeAceSkill = uiProfile.crossDuelAceSkills.getOrElse(
                        crossDuelSkillIdx % uiProfile.crossDuelAceSkills.size.coerceAtLeast(1)
                    ) { "Master Skill: Raid Boss" }

                    FilterChip(
                        selected = true,
                        onClick = {
                            crossDuelLaneIdx = (crossDuelLaneIdx + 1) % uiProfile.crossDuelLanes.size.coerceAtLeast(1)
                            crossDuelSkillIdx = (crossDuelSkillIdx + 1) % uiProfile.crossDuelAceSkills.size.coerceAtLeast(1)
                            val nextLane = uiProfile.crossDuelLanes.getOrElse(crossDuelLaneIdx) { "CENTER BOSS LANE" }
                            val nextSkill = uiProfile.crossDuelAceSkills.getOrElse(crossDuelSkillIdx) { "Master Skill" }
                            val summary = "Player 1 advanced unit along Cross Duel [$nextLane] with Ace Skill [$nextSkill]"
                            lastEmoteBanner = "Cross Duel: $nextLane"
                            dragHistoryLogs.add(0, "[CROSS_DUEL] $summary")
                            emitBoardSyncBroadcast("CROSS_DUEL_LANE", summary)
                        },
                        label = {
                            Text(
                                text = "Cross Duel: $activeLane • $activeAceSkill",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("hud_cross_duel_lane_button")
                    )

                    FilterChip(
                        selected = mtgoActiveShortcut.startsWith("F6"),
                        onClick = {
                            val currentIdx = uiProfile.mtgoPriorityShortcuts.indexOf(mtgoActiveShortcut)
                            val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % uiProfile.mtgoPriorityShortcuts.size.coerceAtLeast(1)
                            mtgoActiveShortcut = uiProfile.mtgoPriorityShortcuts.getOrElse(nextIdx) { "F6: Yield Through Turn" }
                            val summary = "Player 1 triggered MTGO Priority Control [$mtgoActiveShortcut] (${uiProfile.mtgoChessClockDefault})"
                            dragHistoryLogs.add(0, "[MTGO_PRIORITY] $summary")
                            emitBoardSyncBroadcast("MTGO_PRIORITY", summary)
                        },
                        label = {
                            Text(
                                text = "${uiProfile.mtgoChessClockDefault} • $mtgoActiveShortcut",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier.testTag("hud_mtgo_f6_yield_button")
                    )

                    FilterChip(
                        selected = mtgoFloatingManaCount > 0,
                        onClick = {
                            mtgoFloatingManaCount++
                            val pips = uiProfile.mtgoManaPoolPips.joinToString(" ")
                            val summary = "Player 1 tapped MTGO Mana Pool ($pips) -> Floating Mana = $mtgoFloatingManaCount"
                            dragHistoryLogs.add(0, "[MTGO_MANA_POOL] $summary")
                            emitBoardSyncBroadcast("MTGO_MANA_POOL", summary)
                        },
                        label = {
                            Text(
                                text = "Mana Pool ($mtgoFloatingManaCount): ${uiProfile.mtgoManaPoolPips.take(4).joinToString("")}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        modifier = Modifier.testTag("hud_mtgo_mana_pool_button")
                    )
                }

                // Row 3: MTG Arena (Auto-Tapper, Full Control, Timeout Rope & Interactive Emote Wheel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = mtgaAutoTapEnabled,
                        onClick = {
                            mtgaAutoTapEnabled = !mtgaAutoTapEnabled
                            val summary = "Player 1 toggled MTG Arena Auto-Tapper -> ${if (mtgaAutoTapEnabled) "AUTO-TAP ON" else "MANUAL TAP"}"
                            dragHistoryLogs.add(0, "[MTG_ARENA] $summary")
                            emitBoardSyncBroadcast("MTGA_AUTOTAP", summary)
                        },
                        label = {
                            Text(
                                text = if (mtgaAutoTapEnabled) "MTGA Auto-Tap: ON" else "MTGA Auto-Tap: OFF",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("hud_mtga_autotap_toggle")
                    )

                    FilterChip(
                        selected = mtgaFullControlEnabled,
                        onClick = {
                            mtgaFullControlEnabled = !mtgaFullControlEnabled
                            val summary = "Player 1 toggled MTG Arena Full Control -> ${if (mtgaFullControlEnabled) "HOLD PRIORITY (CTRL+SHIFT)" else "STANDARD PRIORITY"}"
                            dragHistoryLogs.add(0, "[MTG_ARENA] $summary")
                            emitBoardSyncBroadcast("MTGA_FULL_CONTROL", summary)
                        },
                        label = {
                            Text(
                                text = if (mtgaFullControlEnabled) "MTGA Full Control: HOLD" else "MTGA Full Control: OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.testTag("hud_mtga_full_control_toggle")
                    )

                    uiProfile.mtgaEmotes.forEachIndexed { idx, emote ->
                        FilterChip(
                            selected = lastEmoteBanner?.contains(emote) == true,
                            onClick = {
                                lastEmoteBanner = "Emote: \"$emote\""
                                val summary = "Player 1 sent MTG Arena Emote \"$emote\" to Field"
                                dragHistoryLogs.add(0, "[MTGA_EMOTE] $summary")
                                emitBoardSyncBroadcast("MTGA_EMOTE", summary)
                            },
                            label = {
                                Text(
                                    text = "💬 $emote",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag(if (idx == 0) "hud_mtga_emote_button" else "hud_mtga_emote_button_$idx")
                        )
                    }
                }
            }

            // Responsive Canvas Playmat Container
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF070A12))
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            ) {
                val isWideBoard = maxWidth >= 560.dp
                val canvasHeight = if (isWideBoard) 500.dp else 460.dp

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(canvasHeight)
                        .testTag("responsive_2p_gameboard_canvas")
                        .pointerInput(uiProfile.baseVariantId, mainSlotCount, supportSlotCount) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val zones = computeTwoPlayerBoardZones(
                                        canvasWidth = size.width.toFloat(),
                                        canvasHeight = size.height.toFloat(),
                                        uiProfile = uiProfile,
                                        mainSlotCount = mainSlotCount
                                    )
                                    val tappedZone = zones.firstOrNull { it.rect.contains(tapOffset) }
                                    if (tappedZone != null) {
                                        if (tappedZone.zoneId == "P1_DECK") {
                                            onDrawCardRequest()
                                            dragHistoryLogs.add(
                                                0,
                                                "[DECK_DRAW] Tapped P1 Deck Stack -> Drew card to Hand"
                                            )
                                        } else if (selectedZoneId == tappedZone.zoneId) {
                                            // Tapping an already-selected zone with a card toggles ATK/DEF or Untapped/Tapped orientation
                                            val existing = zoneCardMap[tappedZone.zoneId]
                                            if (existing != null && tappedZone.kind != BoardZoneKind.HAND_DOCK_SLOT) {
                                                val toggled = existing.copy(isDefenseOrTapped = !existing.isDefenseOrTapped)
                                                zoneCardMap[tappedZone.zoneId] = toggled
                                                moveCounter++
                                                val modeName = if (toggled.isDefenseOrTapped) "DEF / TAPPED (90°)" else "ATK / UNTAPPED (0°)"
                                                val summary = "Rotated '${existing.card.name}' in [${tappedZone.codeLabel}] -> $modeName"
                                                dragHistoryLogs.add(0, "[ORIENTATION] $summary")
                                                emitBoardSyncBroadcast("ORIENTATION_ROTATE", summary)
                                            }
                                        } else {
                                            selectedZoneId = tappedZone.zoneId
                                        }
                                    }
                                }
                            )
                        }
                        .pointerInput(uiProfile.baseVariantId, mainSlotCount, supportSlotCount) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val zones = computeTwoPlayerBoardZones(
                                        canvasWidth = size.width.toFloat(),
                                        canvasHeight = size.height.toFloat(),
                                        uiProfile = uiProfile,
                                        mainSlotCount = mainSlotCount
                                    )
                                    val originZone = zones.firstOrNull { it.rect.contains(startOffset) }
                                    if (originZone != null) {
                                        val cardInZone = zoneCardMap[originZone.zoneId]
                                        if (cardInZone != null) {
                                            draggingFromZoneId = originZone.zoneId
                                            draggingCard = cardInZone
                                            dragStartOffset = startOffset
                                            dragCurrentOffset = startOffset
                                            hoveredTargetZoneId = originZone.zoneId
                                            selectedZoneId = originZone.zoneId
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggingCard != null) {
                                        dragCurrentOffset += dragAmount
                                        val zones = computeTwoPlayerBoardZones(
                                            canvasWidth = size.width.toFloat(),
                                            canvasHeight = size.height.toFloat(),
                                            uiProfile = uiProfile,
                                            mainSlotCount = mainSlotCount
                                        )
                                        hoveredTargetZoneId = zones
                                            .firstOrNull { it.rect.contains(dragCurrentOffset) }
                                            ?.zoneId
                                    }
                                },
                                onDragEnd = {
                                    val activeCard = draggingCard
                                    val fromZoneId = draggingFromZoneId
                                    val toZoneId = hoveredTargetZoneId
                                    if (activeCard != null && fromZoneId != null && toZoneId != null && toZoneId != fromZoneId) {
                                        val zones = computeTwoPlayerBoardZones(
                                            canvasWidth = size.width.toFloat(),
                                            canvasHeight = size.height.toFloat(),
                                            uiProfile = uiProfile,
                                            mainSlotCount = mainSlotCount
                                        )
                                        val fromSpec = zones.firstOrNull { it.zoneId == fromZoneId }
                                        val toSpec = zones.firstOrNull { it.zoneId == toZoneId }
                                        if (fromSpec != null && toSpec != null) {
                                            var recordedBroadcastSummary = ""
                                            handleZoneDropInteraction(
                                                placedCard = activeCard,
                                                fromSpec = fromSpec,
                                                toSpec = toSpec,
                                                zoneCardMap = zoneCardMap,
                                                uiProfile = uiProfile,
                                                onDeployCardFromHand = onDeployCardFromHand,
                                                onMoveCardBetweenZones = onMoveCardBetweenZones,
                                                onAttackOpponentZone = onAttackOpponentZone,
                                                onLogRecorded = { logEntry ->
                                                    moveCounter++
                                                    recordedBroadcastSummary = logEntry
                                                    dragHistoryLogs.add(0, logEntry)
                                                    if (dragHistoryLogs.size > 14) {
                                                        dragHistoryLogs.removeLast()
                                                    }
                                                }
                                            )
                                            selectedZoneId = toSpec.zoneId
                                            if (recordedBroadcastSummary.isNotBlank()) {
                                                emitBoardSyncBroadcast("DRAG_DROP_ZONE_SYNC", recordedBroadcastSummary)
                                            }
                                        }
                                    }
                                    draggingFromZoneId = null
                                    draggingCard = null
                                    hoveredTargetZoneId = null
                                },
                                onDragCancel = {
                                    draggingFromZoneId = null
                                    draggingCard = null
                                    hoveredTargetZoneId = null
                                }
                            )
                        }
                ) {
                    val zones = computeTwoPlayerBoardZones(
                        canvasWidth = size.width,
                        canvasHeight = size.height,
                        uiProfile = uiProfile,
                        mainSlotCount = mainSlotCount
                    )

                    // 1. Render Playmat Background Grid, Player Half Dividers & Center Field Line
                    drawPlaymatSurface(
                        accentColor = accentColor,
                        phaseCode = activePhase,
                        turnNumber = turnNumber
                    )

                    // 2. Render All Play Area Zones for Player 2 (Top), Shared Center, and Player 1 (Bottom + Hand)
                    zones.forEach { zoneSpec ->
                        val isHovered = hoveredTargetZoneId == zoneSpec.zoneId && draggingCard != null
                        val isSelected = selectedZoneId == zoneSpec.zoneId
                        val isDragOrigin = draggingFromZoneId == zoneSpec.zoneId
                        val placedCard = zoneCardMap[zoneSpec.zoneId]

                        drawPlayAreaZone(
                            zoneSpec = zoneSpec,
                            placedCard = if (isDragOrigin) null else placedCard,
                            accentColor = accentColor,
                            isHoveredDropTarget = isHovered,
                            isSelected = isSelected,
                            isDragOrigin = isDragOrigin
                        )
                    }

                    // 3. Render Active Drag Vector Tether & Floating Card Preview Under Finger
                    val activeDragged = draggingCard
                    val originId = draggingFromZoneId
                    if (activeDragged != null && originId != null) {
                        val originZone = zones.firstOrNull { it.zoneId == originId }
                        val targetZone = zones.firstOrNull { it.zoneId == hoveredTargetZoneId }
                        val startCenter = originZone?.rect?.center ?: dragStartOffset
                        val isAttackHover = originZone?.owner == BoardPlayerOwner.PLAYER_1_HOST &&
                            targetZone?.owner == BoardPlayerOwner.PLAYER_2_OPPONENT &&
                            zoneCardMap[targetZone.zoneId] != null

                        drawDragTargetingTether(
                            start = startCenter,
                            end = dragCurrentOffset,
                            color = if (isAttackHover) CrimsonAlert else RadiantGold
                        )

                        drawFloatingDraggedCard(
                            center = dragCurrentOffset,
                            placedCard = activeDragged,
                            accentColor = if (isAttackHover) CrimsonAlert else accentColor,
                            actionBadge = when {
                                isAttackHover -> "ATTACK / CLASH!"
                                originZone?.kind == BoardZoneKind.HAND_DOCK_SLOT -> uiProfile.deployVerb.uppercase()
                                targetZone != null -> "DROP -> ${targetZone.codeLabel}"
                                else -> "DRAGGING"
                            }
                        )
                    }
                }
            }

            // Firestore Real-Time Sync Service Telemetry & Cross-Client Broadcast Bar
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldReady.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .testTag("canvas_firestore_sync_service_bar")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(EmeraldReady, CircleShape)
                                )
                                Text(
                                    text = "FIRESTORE REAL-TIME BOARD SYNC • REV #${effectiveSerializedState.revisionNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldReady,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Sender: ${effectiveSerializedState.lastBroadcastClientName} • ${effectiveSerializedState.zonePlacements.size} Synced Zones • Clients: ${effectiveSerializedState.connectedClients.joinToString(", ").ifBlank { "$hostName, $opponentName" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        FilterChip(
                            selected = showSerializedJsonInspector,
                            onClick = { showSerializedJsonInspector = !showSerializedJsonInspector },
                            label = {
                                Text(
                                    text = if (showSerializedJsonInspector) "Hide JSON" else "Serialized JSON",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("canvas_inspect_serialized_json_button")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                moveCounter++
                                val summary = "Manual GameBoard state broadcast (${snapshotSerializedPlacements().size} zones)"
                                dragHistoryLogs.add(0, "[FIRESTORE_BROADCAST] $summary")
                                emitBoardSyncBroadcast("MANUAL_BOARD_BROADCAST", summary)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldReady,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("canvas_broadcast_sync_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Broadcast State to Firestore",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                moveCounter++
                                val currentMap = snapshotSerializedPlacements()
                                onTriggerPeerClientSyncMove(currentMap)
                                dragHistoryLogs.add(
                                    0,
                                    "[PEER_CLIENT_SYNC] Requested connected Peer Client #2 move via Firestore"
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("canvas_peer_sync_move_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Peer Client #2 Sync Move",
                                style = MaterialTheme.typography.labelSmall,
                                color = RadiantGold
                            )
                        }
                    }

                    if (showSerializedJsonInspector) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF05070D))
                                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                                .testTag("canvas_serialized_json_preview")
                        ) {
                            Text(
                                text = effectiveSerializedState.toJsonString(pretty = true),
                                style = MaterialTheme.typography.labelSmall,
                                color = RadiantGold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 14,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Interactive Zone Inspector & Quick-Move Bar (Supports Touch Tap & Automated Testing)
            val inspectedZoneId = selectedZoneId ?: "P1_HAND_0"
            val inspectedCard = zoneCardMap[inspectedZoneId]
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianElevated),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("canvas_board_inspector_bar")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdsClick,
                                contentDescription = null,
                                tint = RadiantGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (inspectedCard != null) {
                                    "ZONE [$inspectedZoneId]: ${inspectedCard.card.name} (${inspectedCard.card.attack}/${inspectedCard.card.defense}) • ${if (inspectedCard.isDefenseOrTapped) "DEF/TAPPED" else "ATK/UNTAPPED"}"
                                } else {
                                    "ZONE [$inspectedZoneId]: Empty Play Area — Drag any card here or use Quick-Move below"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (inspectedCard != null) {
                            OutlinedButton(
                                onClick = {
                                    val toggled = inspectedCard.copy(
                                        isDefenseOrTapped = !inspectedCard.isDefenseOrTapped
                                    )
                                    zoneCardMap[inspectedZoneId] = toggled
                                    moveCounter++
                                    val summary = "Toggled '${inspectedCard.card.name}' in [$inspectedZoneId] -> ${if (toggled.isDefenseOrTapped) "DEF/TAPPED" else "ATK/UNTAPPED"}"
                                    dragHistoryLogs.add(0, "[ORIENTATION] $summary")
                                    emitBoardSyncBroadcast("ORIENTATION_TOGGLE", summary)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("canvas_toggle_card_position_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RotateRight,
                                    contentDescription = "Toggle ATK/DEF or Tap/Untap",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (inspectedCard.isDefenseOrTapped) "Set ATK / Untap" else "Set DEF / Tap 90°",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }

                    // Quick Drag-and-Drop Action Chips (for 1-tap accessibility & deterministic testing)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val handEntry = zoneCardMap.entries.firstOrNull { it.key.startsWith("P1_HAND_") }
                                val openMainIdx = (0 until mainSlotCount).firstOrNull { idx ->
                                    !zoneCardMap.containsKey("P1_MAIN_$idx")
                                } ?: 0
                                val targetZoneKey = "P1_MAIN_$openMainIdx"
                                val targetCode = uiProfile.fieldSlotCodes.getOrElse(openMainIdx) { "MMZ-1" }
                                if (handEntry != null) {
                                    val moved = handEntry.value
                                    zoneCardMap.remove(handEntry.key)
                                    zoneCardMap[targetZoneKey] = moved
                                    selectedZoneId = targetZoneKey
                                    moveCounter++
                                    onDeployCardFromHand(moved.card, targetCode)
                                    val summary = "Player 1 moved '${moved.card.name}' to Field [$targetCode]"
                                    dragHistoryLogs.add(0, "[MOVE_HISTORY] $summary")
                                    emitBoardSyncBroadcast("MOVE_HISTORY", summary)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = Color.Black
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("canvas_quick_deploy_hand_to_main_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapCalls,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Drop Hand -> P1 Battlefield",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val mainEntry = zoneCardMap.entries.firstOrNull { it.key.startsWith("P1_MAIN_") }
                                    ?: zoneCardMap.entries.firstOrNull { it.key.startsWith("P1_HAND_") }
                                if (mainEntry != null) {
                                    val moved = mainEntry.value
                                    zoneCardMap.remove(mainEntry.key)
                                    zoneCardMap["SHARED_STACK"] = moved
                                    selectedZoneId = "SHARED_STACK"
                                    moveCounter++
                                    onMoveCardBetweenZones(moved.card, mainEntry.key, "STACK/CHAIN")
                                    val summary = "Player 1 moved '${moved.card.name}' from [${mainEntry.key}] to Field [STACK/CHAIN]"
                                    dragHistoryLogs.add(0, "[MOVE_HISTORY] $summary")
                                    emitBoardSyncBroadcast("MOVE_HISTORY", summary)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("canvas_quick_move_to_stack_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Move to Stack / Chain",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val attacker = zoneCardMap.entries.firstOrNull { it.key.startsWith("P1_MAIN_") }?.value
                                    ?: handCards.firstOrNull()?.let {
                                        CanvasPlacedCard("atk_fallback", it, BoardPlayerOwner.PLAYER_1_HOST)
                                    }
                                val targetOppEntry = zoneCardMap.entries.firstOrNull { it.key.startsWith("P2_MAIN_") }
                                if (attacker != null) {
                                    val targetCode = uiProfile.fieldSlotCodes.firstOrNull() ?: "P2-M1"
                                    moveCounter++
                                    onAttackOpponentZone(attacker.card, targetCode, targetOppEntry?.value?.card)
                                    val summary = "Dragged '${attacker.card.name}' onto P2 Zone [$targetCode] (${targetOppEntry?.value?.card?.name ?: "Direct Attack"})!"
                                    dragHistoryLogs.add(0, "[DRAG_ATTACK] $summary")
                                    emitBoardSyncBroadcast("DRAG_ATTACK", summary)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("canvas_quick_attack_p2_zone_button")
                        ) {
                            Text(
                                text = "Drag Attack -> P2 Zone",
                                style = MaterialTheme.typography.labelSmall,
                                color = CrimsonAlert
                            )
                        }
                    }

                    // Recent Drag-and-Drop Telemetry Strip
                    if (dragHistoryLogs.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        Text(
                            text = dragHistoryLogs.first(),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldReady,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("canvas_board_last_drag_log")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Handles a completed touch drag-and-drop gesture between two zones on the Canvas GameBoard.
 */
private fun handleZoneDropInteraction(
    placedCard: CanvasPlacedCard,
    fromSpec: CanvasBoardZoneSpec,
    toSpec: CanvasBoardZoneSpec,
    zoneCardMap: MutableMap<String, CanvasPlacedCard>,
    uiProfile: ClientSystemUiProfile,
    onDeployCardFromHand: (CardTemplate, String) -> Unit,
    onMoveCardBetweenZones: (CardTemplate, String, String) -> Unit,
    onAttackOpponentZone: (CardTemplate, String, CardTemplate?) -> Unit,
    onLogRecorded: (String) -> Unit
) {
    val existingTargetCard = zoneCardMap[toSpec.zoneId]

    // Case 1: Dragging a Player 1 card onto an occupied Player 2 Opponent Zone -> Declare Attack / Clash!
    if (placedCard.owner == BoardPlayerOwner.PLAYER_1_HOST &&
        toSpec.owner == BoardPlayerOwner.PLAYER_2_OPPONENT &&
        existingTargetCard != null
    ) {
        onAttackOpponentZone(placedCard.card, toSpec.codeLabel, existingTargetCard.card)
        // If attacker ATK >= defender ATK, send defender to P2 Graveyard and advance attacker
        if (placedCard.card.attack >= existingTargetCard.card.attack) {
            zoneCardMap["P2_GY"] = existingTargetCard.copy(
                stackCount = (zoneCardMap["P2_GY"]?.stackCount ?: 0) + 1
            )
            zoneCardMap.remove(toSpec.zoneId)
            onLogRecorded(
                "[DRAG_CLASH] '${placedCard.card.name}' (${placedCard.card.attack}) destroyed P2 '${existingTargetCard.card.name}' in [${toSpec.codeLabel}] -> Sent to P2 Graveyard!"
            )
        } else {
            onLogRecorded(
                "[DRAG_CLASH] '${placedCard.card.name}' attacked P2 '${existingTargetCard.card.name}' in [${toSpec.codeLabel}]!"
            )
        }
        return
    }

    // Case 2: Dragging from Player 1 Hand Dock onto a Battlefield / Support / Stack Zone -> Deploy Card
    if (fromSpec.kind == BoardZoneKind.HAND_DOCK_SLOT &&
        toSpec.kind != BoardZoneKind.HAND_DOCK_SLOT
    ) {
        zoneCardMap.remove(fromSpec.zoneId)
        if (existingTargetCard != null && toSpec.kind == BoardZoneKind.DECK_OR_GRAVEYARD) {
            zoneCardMap[toSpec.zoneId] = placedCard.copy(stackCount = existingTargetCard.stackCount + 1)
        } else {
            if (existingTargetCard != null) {
                zoneCardMap[fromSpec.zoneId] = existingTargetCard
            }
            zoneCardMap[toSpec.zoneId] = placedCard
        }
        onDeployCardFromHand(placedCard.card, toSpec.codeLabel)
        onLogRecorded(
            "[MOVE_HISTORY] Player 1 moved '${placedCard.card.name}' to Field [${toSpec.codeLabel}] (${uiProfile.deployVerb})"
        )
        return
    }

    // Case 3: General Zone-to-Zone Card Movement (Reposition, Send to GY/Exile, Push to Stack, or Swap)
    zoneCardMap.remove(fromSpec.zoneId)
    if (existingTargetCard != null && toSpec.kind == BoardZoneKind.DECK_OR_GRAVEYARD) {
        zoneCardMap[toSpec.zoneId] = placedCard.copy(stackCount = existingTargetCard.stackCount + 1)
    } else {
        if (existingTargetCard != null) {
            zoneCardMap[fromSpec.zoneId] = existingTargetCard
        }
        zoneCardMap[toSpec.zoneId] = placedCard.copy(
            owner = if (toSpec.owner == BoardPlayerOwner.SHARED_NEXUS) placedCard.owner else toSpec.owner
        )
    }
    val playerLabel = if (placedCard.owner == BoardPlayerOwner.PLAYER_2_OPPONENT) "Player 2" else "Player 1"
    onMoveCardBetweenZones(placedCard.card, fromSpec.codeLabel, toSpec.codeLabel)
    onLogRecorded(
        "[MOVE_HISTORY] $playerLabel moved '${placedCard.card.name}' from [${fromSpec.codeLabel}] to Field [${toSpec.codeLabel}]"
    )
}

/**
 * Computes responsive bounding rectangles (`Rect`) for all two-player play areas (zones)
 * based on the current `Canvas` width and height.
 *
 * Layout Hierarchy (Top to Bottom):
 * - Row 0 (Top): Player 2 Back Row (Deck, 5 Support/Spell/Land Zones, Graveyard)
 * - Row 1: Player 2 Front Row (5 or 6 Main Monster / Creature Zones)
 * - Row 2 (Center): Shared Nexus Row (EMZ-L / Command, Central Stack / Chain Link, EMZ-R / Exile)
 * - Row 3: Player 1 Front Row (5 or 6 Main Monster / Creature Zones)
 * - Row 4: Player 1 Back Row (Graveyard, 5 Support/Spell/Land Zones, Deck)
 * - Row 5 (Bottom Dock): Player 1 Interactive Draggable Hand Slots (5 slots)
 */
private fun computeTwoPlayerBoardZones(
    canvasWidth: Float,
    canvasHeight: Float,
    uiProfile: ClientSystemUiProfile,
    mainSlotCount: Int
): List<CanvasBoardZoneSpec> {
    val zones = mutableListOf<CanvasBoardZoneSpec>()
    val padX = (canvasWidth * 0.022f).coerceAtLeast(8f)
    val padY = (canvasHeight * 0.02f).coerceAtLeast(6f)
    val gapX = (canvasWidth * 0.014f).coerceAtLeast(5f)
    val gapY = (canvasHeight * 0.016f).coerceAtLeast(5f)

    val usableW = (canvasWidth - padX * 2f).coerceAtLeast(120f)
    val usableH = (canvasHeight - padY * 2f).coerceAtLeast(200f)

    // 6 horizontal bands: P2 Back, P2 Front, Shared Center, P1 Front, P1 Back, P1 Hand Dock
    val rowWeights = floatArrayOf(0.15f, 0.175f, 0.13f, 0.175f, 0.15f, 0.18f)
    val totalGapY = gapY * (rowWeights.size - 1)
    val netRowsH = (usableH - totalGapY).coerceAtLeast(120f)

    var currentTop = padY
    val rowRects = rowWeights.map { weight ->
        val h = netRowsH * (weight / rowWeights.sum())
        val r = Rect(padX, currentTop, padX + usableW, currentTop + h)
        currentTop += h + gapY
        r
    }

    // Helper to slice a row into N equal columns
    fun sliceRow(rowRect: Rect, columns: Int): List<Rect> {
        val totalColGaps = gapX * (columns - 1)
        val colW = ((rowRect.width - totalColGaps) / columns).coerceAtLeast(24f)
        return (0 until columns).map { idx ->
            val left = rowRect.left + idx * (colW + gapX)
            Rect(left, rowRect.top, left + colW, rowRect.bottom)
        }
    }

    // --- ROW 0: Player 2 Back Row (P2 Deck + 5 Support/Mana/S-T + P2 Graveyard = 7 slots) ---
    val p2BackCols = sliceRow(rowRects[0], 7)
    zones.add(
        CanvasBoardZoneSpec(
            zoneId = "P2_DECK",
            codeLabel = "P2-DK",
            subtitle = "P2 Deck",
            owner = BoardPlayerOwner.PLAYER_2_OPPONENT,
            kind = BoardZoneKind.DECK_OR_GRAVEYARD,
            rect = p2BackCols[0]
        )
    )
    for (i in 0 until 5) {
        val supportCode = uiProfile.spellOrSupportSlotCodes.getOrElse(i) { "S/T-${i + 1}" }
        zones.add(
            CanvasBoardZoneSpec(
                zoneId = "P2_SUP_$i",
                codeLabel = "P2:$supportCode",
                subtitle = "Opp Support",
                owner = BoardPlayerOwner.PLAYER_2_OPPONENT,
                kind = BoardZoneKind.SUPPORT_SPELL_MANA_ZONE,
                rect = p2BackCols[i + 1]
            )
        )
    }
    zones.add(
        CanvasBoardZoneSpec(
            zoneId = "P2_GY",
            codeLabel = "P2-GY",
            subtitle = "P2 Grave",
            owner = BoardPlayerOwner.PLAYER_2_OPPONENT,
            kind = BoardZoneKind.DECK_OR_GRAVEYARD,
            rect = p2BackCols[6]
        )
    )

    // --- ROW 1: Player 2 Front Row (Main Monster / Creature Zones) ---
    val p2MainCols = sliceRow(rowRects[1], mainSlotCount)
    for (i in 0 until mainSlotCount) {
        val slotCode = uiProfile.fieldSlotCodes.getOrElse(i) { "M-${i + 1}" }
        zones.add(
            CanvasBoardZoneSpec(
                zoneId = "P2_MAIN_$i",
                codeLabel = "P2:$slotCode",
                subtitle = "Opp Unit",
                owner = BoardPlayerOwner.PLAYER_2_OPPONENT,
                kind = BoardZoneKind.MAIN_UNIT_ZONE,
                rect = p2MainCols[i]
            )
        )
    }

    // --- ROW 2: Shared Center Nexus Row (EMZ-L / Command, Central Stack / Chain, EMZ-R / Exile) ---
    val centerCols = sliceRow(rowRects[2], 5)
    zones.add(
        CanvasBoardZoneSpec(
            zoneId = "SHARED_FIELD",
            codeLabel = "FIELD",
            subtitle = "Field/Sanctum",
            owner = BoardPlayerOwner.SHARED_NEXUS,
            kind = BoardZoneKind.SHARED_EMZ_OR_STACK,
            rect = centerCols[0]
        )
    )
    zones.add(
        CanvasBoardZoneSpec(
            zoneId = "SHARED_EMZ_L",
            codeLabel = "EMZ-L/CMD",
            subtitle = "Extra/Command",
            owner = BoardPlayerOwner.SHARED_NEXUS,
            kind = BoardZoneKind.SHARED_EMZ_OR_STACK,
            rect = centerCols[1]
        )
    )
    zones.add(
        CanvasBoardZoneSpec(
            zoneId = "SHARED_STACK",
            codeLabel = "STACK/CHAIN",
            subtitle = "Live Stack/CL",
            owner = BoardPlayerOwner.SHARED_NEXUS,
            kind = BoardZoneKind.SHARED_EMZ_OR_STACK,
            rect = centerCols[2]
        )
    )
    zones.add(
        CanvasBoardZoneSpec(
            zoneId = "SHARED_EMZ_R",
            codeLabel = "EMZ-R/LINK",
            subtitle = "Extra/Link",
            owner = BoardPlayerOwner.SHARED_NEXUS,
            kind = BoardZoneKind.SHARED_EMZ_OR_STACK,
            rect = centerCols[3]
        )
    )
    zones.add(
        CanvasBoardZoneSpec(
            zoneId = "SHARED_EXILE",
            codeLabel = "EXILE/BAN",
            subtitle = "Banished/Exile",
            owner = BoardPlayerOwner.SHARED_NEXUS,
            kind = BoardZoneKind.DECK_OR_GRAVEYARD,
            rect = centerCols[4]
        )
    )

    // --- ROW 3: Player 1 Front Row (Host Main Monster / Battlefield Creature Zones) ---
    val p1MainCols = sliceRow(rowRects[3], mainSlotCount)
    for (i in 0 until mainSlotCount) {
        val slotCode = uiProfile.fieldSlotCodes.getOrElse(i) { "M-${i + 1}" }
        zones.add(
            CanvasBoardZoneSpec(
                zoneId = "P1_MAIN_$i",
                codeLabel = slotCode,
                subtitle = "P1 Unit",
                owner = BoardPlayerOwner.PLAYER_1_HOST,
                kind = BoardZoneKind.MAIN_UNIT_ZONE,
                rect = p1MainCols[i]
            )
        )
    }

    // --- ROW 4: Player 1 Back Row (P1 Graveyard + 5 Support/Spell/Mana Zones + P1 Deck = 7 slots) ---
    val p1BackCols = sliceRow(rowRects[4], 7)
    zones.add(
        CanvasBoardZoneSpec(
            zoneId = "P1_GY",
            codeLabel = "P1-GY",
            subtitle = "Graveyard",
            owner = BoardPlayerOwner.PLAYER_1_HOST,
            kind = BoardZoneKind.DECK_OR_GRAVEYARD,
            rect = p1BackCols[0]
        )
    )
    for (i in 0 until 5) {
        val supportCode = uiProfile.spellOrSupportSlotCodes.getOrElse(i) { "S/T-${i + 1}" }
        zones.add(
            CanvasBoardZoneSpec(
                zoneId = "P1_SUP_$i",
                codeLabel = supportCode,
                subtitle = "P1 Support",
                owner = BoardPlayerOwner.PLAYER_1_HOST,
                kind = BoardZoneKind.SUPPORT_SPELL_MANA_ZONE,
                rect = p1BackCols[i + 1]
            )
        )
    }
    zones.add(
        CanvasBoardZoneSpec(
            zoneId = "P1_DECK",
            codeLabel = "P1-DECK",
            subtitle = "Tap=Draw",
            owner = BoardPlayerOwner.PLAYER_1_HOST,
            kind = BoardZoneKind.DECK_OR_GRAVEYARD,
            rect = p1BackCols[6]
        )
    )

    // --- ROW 5: Player 1 Interactive Draggable Hand Dock (5 Hand Card Slots) ---
    val p1HandCols = sliceRow(rowRects[5], 5)
    for (i in 0 until 5) {
        zones.add(
            CanvasBoardZoneSpec(
                zoneId = "P1_HAND_$i",
                codeLabel = "HAND-${i + 1}",
                subtitle = "Drag to Play",
                owner = BoardPlayerOwner.PLAYER_1_HOST,
                kind = BoardZoneKind.HAND_DOCK_SLOT,
                rect = p1HandCols[i]
            )
        )
    }

    return zones
}

/**
 * Draws the playmat background, subtle tactical grid, and center dividing line between Player 2 (top)
 * and Player 1 (bottom).
 */
private fun DrawScope.drawPlaymatSurface(
    accentColor: Color,
    phaseCode: String,
    turnNumber: Long
) {
    // Subtle radial gradient playmat glow
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.14f),
                Color(0xFF0A0F1D),
                Color(0xFF060810)
            ),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = size.width.coerceAtLeast(size.height) * 0.7f
        )
    )

    // Tactical grid lines
    val gridColor = accentColor.copy(alpha = 0.07f)
    val cols = 12
    val rows = 10
    val stepX = size.width / cols
    val stepY = size.height / rows
    for (c in 1 until cols) {
        drawLine(
            color = gridColor,
            start = Offset(c * stepX, 0f),
            end = Offset(c * stepX, size.height),
            strokeWidth = 1f
        )
    }
    for (r in 1 until rows) {
        drawLine(
            color = gridColor,
            start = Offset(0f, r * stepY),
            end = Offset(size.width, r * stepY),
            strokeWidth = 1f
        )
    }

    // Center Field Divider Line separating Player 2 (Top) and Player 1 (Bottom)
    val centerY = size.height * 0.415f
    drawLine(
        color = accentColor.copy(alpha = 0.4f),
        start = Offset(12f, centerY),
        end = Offset(size.width - 12f, centerY),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
    )

    // Hand Dock Separator Line at the bottom
    val handSepY = size.height * 0.805f
    drawLine(
        color = EmeraldReady.copy(alpha = 0.35f),
        start = Offset(12f, handSepY),
        end = Offset(size.width - 12f, handSepY),
        strokeWidth = 1.5f
    )
}

/**
 * Renders an individual play area zone slot and its placed card (if any) on the `Canvas`.
 */
private fun DrawScope.drawPlayAreaZone(
    zoneSpec: CanvasBoardZoneSpec,
    placedCard: CanvasPlacedCard?,
    accentColor: Color,
    isHoveredDropTarget: Boolean,
    isSelected: Boolean,
    isDragOrigin: Boolean
) {
    val ownerColor = when (zoneSpec.owner) {
        BoardPlayerOwner.PLAYER_1_HOST -> EmeraldReady
        BoardPlayerOwner.PLAYER_2_OPPONENT -> CrimsonAlert
        BoardPlayerOwner.SHARED_NEXUS -> RadiantGold
    }

    val fillAlpha = when {
        isHoveredDropTarget -> 0.32f
        isSelected -> 0.22f
        zoneSpec.kind == BoardZoneKind.HAND_DOCK_SLOT -> 0.14f
        else -> 0.09f
    }

    val strokeColor = when {
        isHoveredDropTarget -> RadiantGold
        isSelected -> accentColor
        isDragOrigin -> RadiantGold.copy(alpha = 0.8f)
        else -> ownerColor.copy(alpha = 0.45f)
    }

    val strokeWidth = when {
        isHoveredDropTarget -> 4f
        isSelected -> 3f
        else -> 1.5f
    }

    // Zone slot background & border
    drawRoundRect(
        color = ownerColor.copy(alpha = fillAlpha),
        topLeft = zoneSpec.rect.topLeft,
        size = zoneSpec.rect.size,
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = strokeColor,
        topLeft = zoneSpec.rect.topLeft,
        size = zoneSpec.rect.size,
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (isDragOrigin) PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f) else null
        )
    )

    val labelPaint = Paint().apply {
        isAntiAlias = true
        color = strokeColor.toArgb()
        textSize = (zoneSpec.rect.height * 0.19f).coerceIn(14f, 22f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    val subPaint = Paint().apply {
        isAntiAlias = true
        color = Color(0xFFB0BEC5).toArgb()
        textSize = (zoneSpec.rect.height * 0.16f).coerceIn(12f, 18f)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    if (placedCard == null) {
        // Draw zone code label and subtitle
        drawContext.canvas.nativeCanvas.drawText(
            zoneSpec.codeLabel.take(9),
            zoneSpec.rect.left + 6f,
            zoneSpec.rect.top + labelPaint.textSize + 4f,
            labelPaint
        )
        drawContext.canvas.nativeCanvas.drawText(
            if (isHoveredDropTarget) "DROP HERE" else zoneSpec.subtitle.take(10),
            zoneSpec.rect.left + 6f,
            zoneSpec.rect.bottom - 6f,
            subPaint
        )
    } else {
        drawCardInsideZone(
            zoneRect = zoneSpec.rect,
            zoneCode = zoneSpec.codeLabel,
            placedCard = placedCard,
            ownerColor = ownerColor,
            accentColor = accentColor
        )
    }
}

/**
 * Renders a placed card (with stack depth layers, ATK/DEF badge, and 90-degree rotation when in
 * Defense / Tapped state) inside a zone's bounding `Rect`.
 */
private fun DrawScope.drawCardInsideZone(
    zoneRect: Rect,
    zoneCode: String,
    placedCard: CanvasPlacedCard,
    ownerColor: Color,
    accentColor: Color
) {
    val inset = 4f
    val cardRect = Rect(
        left = zoneRect.left + inset,
        top = zoneRect.top + inset,
        right = zoneRect.right - inset,
        bottom = zoneRect.bottom - inset
    )

    // Draw stacked shadow layers if stackCount > 1 (e.g., Deck / Graveyard / Xyz Stack)
    val visibleStackLayers = (placedCard.stackCount - 1).coerceIn(0, 3)
    for (layer in visibleStackLayers downTo 1) {
        val offset = layer * 2.5f
        drawRoundRect(
            color = Color(0xFF1A2234),
            topLeft = Offset(cardRect.left + offset, cardRect.top - offset),
            size = cardRect.size,
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = ownerColor.copy(alpha = 0.5f),
            topLeft = Offset(cardRect.left + offset, cardRect.top - offset),
            size = cardRect.size,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 1f)
        )
    }

    val degrees = if (placedCard.isDefenseOrTapped) 90f else 0f
    rotate(degrees = degrees, pivot = cardRect.center) {
        val drawRect = if (placedCard.isDefenseOrTapped) {
            // Fit rotated card neatly inside the zone
            val rw = cardRect.height * 0.92f
            val rh = cardRect.width * 0.72f
            Rect(
                left = cardRect.center.x - rw / 2f,
                top = cardRect.center.y - rh / 2f,
                right = cardRect.center.x + rw / 2f,
                bottom = cardRect.center.y + rh / 2f
            )
        } else {
            cardRect
        }

        // Card Body Gradient
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF212B42),
                    Color(0xFF101624)
                ),
                startY = drawRect.top,
                endY = drawRect.bottom
            ),
            topLeft = drawRect.topLeft,
            size = drawRect.size,
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Card Frame Border
        drawRoundRect(
            color = if (placedCard.isDefenseOrTapped) RadiantGold else ownerColor,
            topLeft = drawRect.topLeft,
            size = drawRect.size,
            cornerRadius = CornerRadius(8f, 8f),
            style = Stroke(width = 2f)
        )

        // Top Art / Header Accent Strip
        val bannerHeight = (drawRect.height * 0.26f).coerceAtLeast(10f)
        drawRoundRect(
            color = accentColor.copy(alpha = 0.28f),
            topLeft = drawRect.topLeft,
            size = Size(drawRect.width, bannerHeight),
            cornerRadius = CornerRadius(8f, 8f)
        )
    }

    // Draw Card Title & ATK/DEF or Stack Count upright for immediate readability
    val titlePaint = Paint().apply {
        isAntiAlias = true
        color = Color.White.toArgb()
        textSize = (cardRect.height * 0.21f).coerceIn(13f, 21f)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val statPaint = Paint().apply {
        isAntiAlias = true
        color = RadiantGold.toArgb()
        textSize = (cardRect.height * 0.19f).coerceIn(12f, 19f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val badgePaint = Paint().apply {
        isAntiAlias = true
        color = ownerColor.toArgb()
        textSize = (cardRect.height * 0.16f).coerceIn(11f, 16f)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    drawContext.canvas.nativeCanvas.drawText(
        zoneCode.take(7),
        cardRect.left + 4f,
        cardRect.top + badgePaint.textSize + 2f,
        badgePaint
    )

    val displayName = placedCard.card.name.take(9)
    drawContext.canvas.nativeCanvas.drawText(
        displayName,
        cardRect.left + 4f,
        cardRect.center.y + titlePaint.textSize * 0.35f,
        titlePaint
    )

    val bottomStatText = if (placedCard.stackCount > 1) {
        "STACK x${placedCard.stackCount}"
    } else if (placedCard.isDefenseOrTapped) {
        "DEF ${placedCard.card.defense}"
    } else {
        "${placedCard.card.attack}/${placedCard.card.defense}"
    }
    drawContext.canvas.nativeCanvas.drawText(
        bottomStatText.take(10),
        cardRect.left + 4f,
        cardRect.bottom - 4f,
        statPaint
    )
}

/**
 * Draws the animated targeting tether from the drag origin zone center to the current touch point.
 */
private fun DrawScope.drawDragTargetingTether(
    start: Offset,
    end: Offset,
    color: Color
) {
    val dist = hypot(end.x - start.x, end.y - start.y)
    if (dist < 6f) return

    drawLine(
        color = color.copy(alpha = 0.85f),
        start = start,
        end = end,
        strokeWidth = 4f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
    )
    drawCircle(
        color = color,
        radius = 7f,
        center = start
    )
    drawCircle(
        color = color.copy(alpha = 0.35f),
        radius = 18f,
        center = end
    )
}

/**
 * Draws the elevated floating card preview directly under the user's finger during a drag gesture.
 */
private fun DrawScope.drawFloatingDraggedCard(
    center: Offset,
    placedCard: CanvasPlacedCard,
    accentColor: Color,
    actionBadge: String
) {
    val cardW = (size.width * 0.17f).coerceIn(84f, 132f)
    val cardH = (size.height * 0.18f).coerceIn(72f, 110f)
    val topLeft = Offset(center.x - cardW / 2f, center.y - cardH / 2f)

    // Outer glow shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.65f),
        topLeft = Offset(topLeft.x + 6f, topLeft.y + 8f),
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(12f, 12f)
    )

    // Floating Card Body
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF283552),
                Color(0xFF121828)
            ),
            startY = topLeft.y,
            endY = topLeft.y + cardH
        ),
        topLeft = topLeft,
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(12f, 12f)
    )

    drawRoundRect(
        color = RadiantGold,
        topLeft = topLeft,
        size = Size(cardW, cardH),
        cornerRadius = CornerRadius(12f, 12f),
        style = Stroke(width = 3f)
    )

    val badgePaint = Paint().apply {
        isAntiAlias = true
        color = RadiantGold.toArgb()
        textSize = 18f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val namePaint = Paint().apply {
        isAntiAlias = true
        color = Color.White.toArgb()
        textSize = 20f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val statPaint = Paint().apply {
        isAntiAlias = true
        color = accentColor.toArgb()
        textSize = 18f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    drawContext.canvas.nativeCanvas.drawText(
        actionBadge.take(12),
        topLeft.x + 8f,
        topLeft.y + 22f,
        badgePaint
    )
    drawContext.canvas.nativeCanvas.drawText(
        placedCard.card.name.take(11),
        topLeft.x + 8f,
        topLeft.y + cardH * 0.56f,
        namePaint
    )
    drawContext.canvas.nativeCanvas.drawText(
        "${placedCard.card.attack}/${placedCard.card.defense}",
        topLeft.x + 8f,
        topLeft.y + cardH - 10f,
        statPaint
    )
}
