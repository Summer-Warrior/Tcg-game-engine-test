package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardEffectType
import com.example.data.CardTemplate
import com.example.data.ClientNode
import com.example.data.GameEngine
import com.example.data.GameEngineState
import com.example.data.MatchGameEvent
import com.example.data.MatchSession
import com.example.data.RealtimeGameSyncState
import com.example.data.SerializedGameBoardState
import com.example.data.SerializedZonePlacement
import com.example.data.SyncClientSeat
import com.example.data.TcgVariant
import com.example.data.TurnPhase
import com.example.engine.BoardArchetypeTemplate
import com.example.engine.CatalogBlueprint
import com.example.ui.components.AnimatedSyncedHealthBarCard
import com.example.ui.components.CanvasGameBoard
import com.example.ui.components.PersistentGameEventLogCard
import com.example.ui.components.RealtimeGameStateSyncPanel
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import com.example.viewmodel.UiState

private enum class TabletopDrawer(val label: String) {
    EVENT_LOG("Event Log"),
    CHAIN_STACK("LIFO Chain"),
    CALC_21("21-Digit Calc"),
    SENSORS("Sensors & Tiers"),
    VG_MECHANICS("VG Mechanics"),
    SYNC_HUB("Firestore Sync"),
    DUAL_CHAT("IC / OOC Chat"),
    ARENA_HOSTING("Format & Arena")
}

private enum class ChatChannelMode(val label: String) {
    IC_MEMORIAL("IC (Memorial Feed)"),
    OOC_NORMAL("OOC Normal"),
    OOC_RULING("OOC Ruling Call")
}

/**
 * Section 3: Consolidated Playmat-First Tabletop (~80%+ viewport).
 *
 * Replaces duplicated multi-widget boards with ONE unified playmat backed by a single state,
 * a fanned Hand Dock with card zoom + foil sheen, a compact Dual-Chamber Vitality/Phase strip,
 * and slide-over `ModalBottomSheet` drawers for secondary telemetry and mechanics.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TabletopArenaScreen(
    selectedBaseVariantId: String,
    activeCustomVariant: TcgVariant? = null,
    activeClientNode: ClientNode? = null,
    matchesState: UiState<List<MatchSession>>,
    activeMatchId: String?,
    playerHand: List<CardTemplate>,
    syncedBoardState: SerializedGameBoardState? = null,
    gameEngineState: GameEngineState = GameEngine.createInitialState(selectedBaseVariantId),
    onSelectMatch: (String) -> Unit,
    onLaunchMatch: (String) -> Unit,
    onBridgeClientsIntoMatch: (MatchSession) -> Unit,
    onDrawCard: (String, MatchSession?) -> Unit,
    onDeployCard: (MatchSession, CardTemplate) -> Unit,
    onExecuteCombatAndAdvanceTurn: (MatchSession) -> Unit,
    onChangePhase: (MatchSession, String) -> Unit = { _, _ -> },
    onStepGameEnginePhase: (MatchSession?) -> Unit = {},
    onSelectGameEnginePhase: (TurnPhase, MatchSession?) -> Unit = { _, _ -> },
    onQueueCardEffect: (CardTemplate, CardEffectType?, MatchSession?) -> Unit = { _, _, _ -> },
    onResolveNextEffect: (MatchSession?) -> Unit = {},
    onResolveAllEffects: (MatchSession?) -> Unit = {},
    onAdjustResources: (Long, MatchSession?) -> Unit = { _, _ -> },
    onSetResourcesExact: (Long, MatchSession?) -> Unit = { _, _ -> },
    onRefillResources: (MatchSession?) -> Unit = {},
    onExpandMaxResources: (MatchSession?) -> Unit = {},
    onRecordMatchActionLog: (MatchSession, String) -> Unit = { _, _ -> },
    onBroadcastBoardState: (MatchSession?, Map<String, SerializedZonePlacement>, String, String) -> Unit = { _, _, _, _ -> },
    onTriggerPeerClientSyncMove: (MatchSession?, Map<String, SerializedZonePlacement>) -> Unit = { _, _ -> },
    onRequestConfirmOpenClientWindow: (String, String?) -> Unit = { _, _ -> },
    discordDesignatedChannel: String = "#duel-broadcasts",
    onBroadcastGameStartToDiscord: (MatchSession?) -> Unit = {},
    onBroadcastMatchWinnerToDiscord: (MatchSession?) -> Unit = {},
    realtimeGameSyncState: RealtimeGameSyncState = RealtimeGameSyncState(),
    onSelectSyncSeat: (SyncClientSeat) -> Unit = {},
    onJoinSyncMatchById: (String) -> Unit = {},
    onAdjustSyncedLifePoints: (SyncClientSeat, Long) -> Unit = { _, _ -> },
    onSetSyncedLifePointsExact: (SyncClientSeat, Long) -> Unit = { _, _ -> },
    onDeploySyncedCardForActiveSeat: () -> Unit = {},
    onToggleSyncedZoneOrientation: (String) -> Unit = {},
    onRemoveSyncedZonePlacement: (String) -> Unit = {},
    onResetSyncedLifeAndBoard: () -> Unit = {},
    persistentEventsState: UiState<List<MatchGameEvent>> = UiState.Success(emptyList()),
    onLogQuickCardPlay: (String) -> Unit = {},
    onLogQuickLifeReduction: (Long) -> Unit = {},
    onLogCustomGameEvent: (String) -> Unit = {},
    activeFormatBlueprint: CatalogBlueprint? = null,
    onRecordIcMemorialEntry: (String) -> Unit = {}
) {
    val matches = (matchesState as? UiState.Success)?.data.orEmpty()
    val currentMatch = matches.firstOrNull { it.id == activeMatchId } ?: matches.firstOrNull()
    val effectiveBaseId = currentMatch?.baseVariantId ?: selectedBaseVariantId
    val uiProfile = BuiltInTcgCatalog.systemUiProfileFor(effectiveBaseId, activeClientNode)
    val activeArchetype = BoardArchetypeTemplate.fromId(
        activeFormatBlueprint?.boardArchetype ?: "zone-rows"
    )

    // Arena Hosting & Format State
    var arenaHostingMode by rememberSaveable { mutableStateOf("Native Universe") }
    var is3dGridOverlay by rememberSaveable { mutableStateOf(false) }
    var optInSideProjectFormats by rememberSaveable { mutableStateOf(false) }

    // Dual-Chamber Vitality State (Magic Pulse & Force Pulse + Outsider Toggle)
    var magicPulsePct by rememberSaveable { mutableIntStateOf(100) }
    var forcePulsePct by rememberSaveable { mutableIntStateOf(100) }
    var outsiderVitalityMode by rememberSaveable { mutableStateOf(false) }

    // Video-Game Mechanics State
    var pxzFrontlineCount by rememberSaveable { mutableIntStateOf(3) }
    var pxzXpGaugePct by rememberSaveable { mutableIntStateOf(75) }
    var crossHitActive by rememberSaveable { mutableStateOf(false) }
    var bossIntentText by rememberSaveable { mutableStateOf("Multi-Strike 1200 + Shield Break") }
    var programAdvanceReady by rememberSaveable { mutableStateOf(true) }

    // Planetary Sensor Indices & Material Tiers (1-8)
    var magicDensity by rememberSaveable { mutableFloatStateOf(0.72f) }
    var techIndex by rememberSaveable { mutableFloatStateOf(0.64f) }
    var arcaneInterference by rememberSaveable { mutableFloatStateOf(0.28f) }
    var planarStability by rememberSaveable { mutableFloatStateOf(0.88f) }
    var activeMaterialTier by rememberSaveable { mutableIntStateOf(5) }

    // Dual-Channel Chat State (IC vs OOC Normal vs OOC Ruling Calls)
    var activeChatMode by rememberSaveable { mutableStateOf(ChatChannelMode.IC_MEMORIAL) }
    var chatDraft by rememberSaveable { mutableStateOf("") }
    val chatFeed = remember {
        mutableStateListOf(
            Triple(ChatChannelMode.IC_MEMORIAL, "Tabletop Arbiter", "Playmat synchronized for ${activeFormatBlueprint?.name ?: uiProfile.systemTitle}."),
            Triple(ChatChannelMode.OOC_RULING, "Judge", "LIFO Chain & strict zero-time-travel match rules active.")
        )
    }

    // Active Drawer + Modals
    var openDrawer by remember { mutableStateOf<TabletopDrawer?>(null) }
    var zoomedHandCard by remember { mutableStateOf<CardTemplate?>(null) }
    var showNewMatchDialog by remember { mutableStateOf(false) }
    var newMatchTitle by remember { mutableStateOf("") }

    val hostUnits = remember(currentMatch?.hostBoardCards, effectiveBaseId) {
        currentMatch?.hostBoardCards.orEmpty().map { CardTemplate.fromBoardDescriptor(it, effectiveBaseId) }
    }
    val opponentUnits = remember(currentMatch?.opponentBoardCards, effectiveBaseId) {
        currentMatch?.opponentBoardCards.orEmpty().map { CardTemplate.fromBoardDescriptor(it, effectiveBaseId) }
    }
    val effectiveHostLife = if (realtimeGameSyncState.isAttached) realtimeGameSyncState.hostLife else (currentMatch?.hostLife ?: 8000L)
    val effectiveOpponentLife = if (realtimeGameSyncState.isAttached) realtimeGameSyncState.opponentLife else (currentMatch?.opponentLife ?: 8000L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("unified_tabletop_screen")
    ) {
        // 1. Compact Top Toolbar: Format/Archetype Pill + Drawer Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = openDrawer == TabletopDrawer.ARENA_HOSTING,
                onClick = { openDrawer = TabletopDrawer.ARENA_HOSTING },
                label = {
                    Text(
                        text = "${activeFormatBlueprint?.name?.take(18) ?: uiProfile.hudBadgeLabel} • ${if (is3dGridOverlay) "3D Grid" else "2D Mat"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.testTag("tabletop_format_chip")
            )
            DrawerChip("Sync", Icons.Default.Sync, openDrawer == TabletopDrawer.SYNC_HUB) { openDrawer = TabletopDrawer.SYNC_HUB }
            DrawerChip("Log", Icons.Default.History, openDrawer == TabletopDrawer.EVENT_LOG) { openDrawer = TabletopDrawer.EVENT_LOG }
            DrawerChip("Chain (${gameEngineState.effectStack.size})", Icons.Default.Layers, openDrawer == TabletopDrawer.CHAIN_STACK) { openDrawer = TabletopDrawer.CHAIN_STACK }
            DrawerChip("21d Calc", Icons.Default.Calculate, openDrawer == TabletopDrawer.CALC_21) { openDrawer = TabletopDrawer.CALC_21 }
            DrawerChip("Sensors", Icons.Default.Sensors, openDrawer == TabletopDrawer.SENSORS) { openDrawer = TabletopDrawer.SENSORS }
            DrawerChip("VG Mod", Icons.Default.SportsEsports, openDrawer == TabletopDrawer.VG_MECHANICS) { openDrawer = TabletopDrawer.VG_MECHANICS }
            DrawerChip("IC/OOC", Icons.Default.Chat, openDrawer == TabletopDrawer.DUAL_CHAT) { openDrawer = TabletopDrawer.DUAL_CHAT }
        }

        // 2. Compact Vitality & 4-Turn-Phase Strip (Dual-Chamber Magic Pulse & Force Pulse + LP)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LP $effectiveHostLife vs $effectiveOpponentLife • ${if (outsiderVitalityMode) "Outsider Vitality" else "Magic $magicPulsePct% | Force $forcePulsePct%"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldReady,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$arenaHostingMode • ${activeArchetype.displayLabel} • Phase: ${gameEngineState.currentPhase.displayName} • Turn ${gameEngineState.turnNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onStepGameEnginePhase(currentMatch) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("tabletop_next_phase_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Next Phase", modifier = Modifier.size(14.dp))
                    Text("Phase", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { showNewMatchDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("tabletop_new_match_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Match", modifier = Modifier.size(14.dp))
                    Text("Room", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 3. UNIFIED PLAYMAT VIEWPORT (~80% height — single CanvasGameBoard backed by single state)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF070B14))
                .testTag("tabletop_main_playmat_container")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Optional 3D Grid / PXZ / Archetype Status Overlay Strip (compact, non-duplicated)
                if (is3dGridOverlay || activeFormatBlueprint?.id == "ULTIMATE_TCG_WIP" || activeFormatBlueprint?.mechanicModules?.isNotEmpty() == true) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RadiantGold.copy(alpha = 0.12f))
                            .border(1.dp, RadiantGold.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (is3dGridOverlay) {
                                "3D Tactical Grid Overlay ON • PXZ Frontline $pxzFrontlineCount/5 • XP $pxzXpGaugePct% • Tier $activeMaterialTier"
                            } else {
                                "PXZ Frontline $pxzFrontlineCount/5 • XP $pxzXpGaugePct%${if (crossHitActive) " • CROSS HIT!" else ""} • Intent: $bossIntentText"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = RadiantGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                CanvasGameBoard(
                    uiProfile = uiProfile,
                    hostName = currentMatch?.hostName ?: "Commander",
                    opponentName = currentMatch?.connectedClients?.firstOrNull() ?: "Peer Client",
                    hostLife = effectiveHostLife,
                    opponentLife = effectiveOpponentLife,
                    hostUnits = hostUnits,
                    opponentUnits = opponentUnits,
                    handCards = playerHand,
                    turnNumber = gameEngineState.turnNumber,
                    activePhase = gameEngineState.currentPhase.displayName,
                    syncedBoardState = syncedBoardState,
                    onDeployCardFromHand = { card, zoneId ->
                        if (currentMatch != null) {
                            onDeployCard(currentMatch, card)
                        }
                        onLogQuickCardPlay("${card.name} -> $zoneId")
                    },
                    onMoveCardBetweenZones = { card, fromZone, toZone ->
                        if (currentMatch != null) {
                            onRecordMatchActionLog(currentMatch, "Moved ${card.name}: $fromZone -> $toZone")
                        }
                    },
                    onAttackOpponentZone = { attacker, targetZone, _ ->
                        if (currentMatch != null) {
                            onExecuteCombatAndAdvanceTurn(currentMatch)
                        }
                        onLogCustomGameEvent("${attacker.name} declared attack on $targetZone")
                    },
                    onDrawCardRequest = { onDrawCard(effectiveBaseId, currentMatch) },
                    onBroadcastBoardState = { placements, note, actor ->
                        onBroadcastBoardState(currentMatch, placements, note, actor)
                    }
                )
            }
        }

        // 4. Fanned Hand Dock with Card Zoom & Holographic Foil Sheen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("tabletop_fanned_hand_dock"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hand Dock (${playerHand.size}) • Tap card to zoom/play",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onDrawCard(effectiveBaseId, currentMatch) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("hand_dock_draw_button")
                    ) {
                        Text("Draw", style = MaterialTheme.typography.labelSmall)
                    }
                    if (currentMatch != null) {
                        Button(
                            onClick = { onExecuteCombatAndAdvanceTurn(currentMatch) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("hand_dock_combat_button")
                        ) {
                            Text("Combat / End", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(playerHand.indices.toList(), key = { idx -> "${playerHand[idx].id}_$idx" }) { idx ->
                    val card = playerHand[idx]
                    val fanTilt = ((idx - (playerHand.size - 1) / 2f) * 2.5f).coerceIn(-8f, 8f)
                    Box(
                        modifier = Modifier
                            .width(126.dp)
                            .graphicsLayer { rotationZ = fanTilt }
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF141B2D),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                        Color(0xFF1A142E)
                                    )
                                )
                            )
                            .border(1.2.dp, RadiantGold.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .clickable { zoomedHandCard = card }
                            .padding(8.dp)
                            .testTag("hand_card_${card.id}")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${card.cardType} • Cost ${card.cost}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RadiantGold,
                                maxLines = 1
                            )
                            Text(
                                text = "ATK ${card.attack} / DEF ${card.defense}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFBAE6FD)
                            )
                        }
                    }
                }
            }
        }
    }

    // Card Zoom Preview Dialog with Foil Sheen
    zoomedHandCard?.let { card ->
        AlertDialog(
            onDismissRequest = { zoomedHandCard = null },
            title = { Text(card.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF0E172A),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                                        RadiantGold.copy(alpha = 0.22f)
                                    )
                                )
                            )
                            .border(1.5.dp, RadiantGold, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(card.cardType, style = MaterialTheme.typography.labelSmall, color = RadiantGold, fontWeight = FontWeight.Bold)
                            Text("ATK ${card.attack} / DEF ${card.defense} • Cost ${card.cost}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                            Text("V2 Wire: ${card.toV2BoardDescriptor()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(card.abilityText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (currentMatch != null) {
                            onDeployCard(currentMatch, card)
                        }
                        onLogQuickCardPlay(card.name)
                        zoomedHandCard = null
                    }
                ) {
                    Text("Deploy to Mat")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            onQueueCardEffect(card, null, currentMatch)
                            zoomedHandCard = null
                        }
                    ) {
                        Text("Queue Chain")
                    }
                    TextButton(onClick = { zoomedHandCard = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Slide-Over Drawers via ModalBottomSheet
    openDrawer?.let { drawer ->
        ModalBottomSheet(onDismissRequest = { openDrawer = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(drawer.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                when (drawer) {
                    TabletopDrawer.ARENA_HOSTING -> {
                        Text("Arena Hosting Mode:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Native Universe", "Off-World Morphing Grid", "Out-of-Game Card Invocation").forEach { mode ->
                                FilterChip(
                                    selected = arenaHostingMode == mode,
                                    onClick = { arenaHostingMode = mode },
                                    label = { Text(mode, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("3D Grid Overlay (on top of 2D Mat)", style = MaterialTheme.typography.bodySmall)
                            Switch(checked = is3dGridOverlay, onCheckedChange = { is3dGridOverlay = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Vanguard Side-Project Formats (Strictly Opt-In)", style = MaterialTheme.typography.bodySmall)
                            Switch(checked = optInSideProjectFormats, onCheckedChange = { optInSideProjectFormats = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Outsider Vitality Mode (replaces Magic/Force Pulse)", style = MaterialTheme.typography.bodySmall)
                            Switch(checked = outsiderVitalityMode, onCheckedChange = { outsiderVitalityMode = it })
                        }
                        HorizontalDivider()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onRequestConfirmOpenClientWindow(effectiveBaseId, currentMatch?.id) }) {
                                Icon(Icons.Default.DesktopWindows, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Separate Window")
                            }
                            if (currentMatch != null) {
                                OutlinedButton(onClick = { onBridgeClientsIntoMatch(currentMatch) }) {
                                    Text("Bridge Clients")
                                }
                            }
                        }
                    }

                    TabletopDrawer.EVENT_LOG -> {
                        val events = (persistentEventsState as? UiState.Success)?.data.orEmpty()
                        PersistentGameEventLogCard(
                            matchId = currentMatch?.id ?: realtimeGameSyncState.matchId.ifBlank { "local_tabletop" },
                            events = events,
                            isFirestoreConnected = currentMatch != null || realtimeGameSyncState.isAttached,
                            onLogQuickCardPlay = onLogQuickCardPlay,
                            onLogQuickLifeReduction = onLogQuickLifeReduction,
                            onLogCustomEvent = onLogCustomGameEvent
                        )
                    }

                    TabletopDrawer.CHAIN_STACK -> {
                        Text(
                            text = "LIFO Stack / Chain Depth: ${gameEngineState.effectStack.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = RadiantGold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onResolveNextEffect(currentMatch) }) { Text("Resolve Top") }
                            OutlinedButton(onClick = { onResolveAllEffects(currentMatch) }) { Text("Resolve All") }
                        }
                        if (gameEngineState.effectStack.isEmpty()) {
                            Text("Chain is currently empty. Tap a card in the Hand Dock to queue an effect.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            gameEngineState.effectStack.reversed().forEachIndexed { idx, item ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Link #${gameEngineState.effectStack.size - idx}: ${item.sourceCard.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        Text("${item.effectType.name} • ${item.targetDescription}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    TabletopDrawer.CALC_21 -> {
                        var left21 by remember { mutableStateOf("100000000000000000000") }
                        var right21 by remember { mutableStateOf("23456789012345678901") }
                        var op by remember { mutableStateOf("ADD_21") }
                        val result21 = remember(left21, right21, op) {
                            BigInt21Engine.evaluateFunction21(op, left21, right21)
                        }
                        OutlinedTextField(value = left21, onValueChange = { left21 = it }, label = { Text("Register A (up to 21 digits)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = right21, onValueChange = { right21 = it }, label = { Text("Register B (up to 21 digits)") }, modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("ADD_21", "SUB_21", "MUL_21", "DIV_21").forEach { fn ->
                                FilterChip(selected = op == fn, onClick = { op = fn }, label = { Text(fn) })
                            }
                        }
                        Text(
                            text = "Result: ${BigInt21Engine.formatWithCommas(result21)}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldReady
                        )
                    }

                    TabletopDrawer.SENSORS -> {
                        Text("4 Planetary Sensor Indices & Material Tiers (1–8)", style = MaterialTheme.typography.labelMedium, color = RadiantGold)
                        SensorBar("Magic Density", magicDensity) { magicDensity = it }
                        SensorBar("Tech Index", techIndex) { techIndex = it }
                        SensorBar("Arcane Interference", arcaneInterference) { arcaneInterference = it }
                        SensorBar("Planar Stability", planarStability) { planarStability = it }
                        Text("Active Material Tier: Tier $activeMaterialTier / 8", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            (1..8).forEach { tier ->
                                FilterChip(
                                    selected = activeMaterialTier == tier,
                                    onClick = { activeMaterialTier = tier },
                                    label = { Text("T$tier") }
                                )
                            }
                        }
                    }

                    TabletopDrawer.VG_MECHANICS -> {
                        Text("PXZ Frontline ($pxzFrontlineCount/5) • XP Gauge $pxzXpGaugePct% / 150%", fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { (pxzXpGaugePct / 150f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { pxzFrontlineCount = (pxzFrontlineCount % 5) + 1 }) { Text("Cycle Frontline ($pxzFrontlineCount)") }
                            OutlinedButton(onClick = { pxzXpGaugePct = (pxzXpGaugePct + 25).coerceAtMost(150) }) { Text("+25% XP") }
                            Button(onClick = { crossHitActive = !crossHitActive }) { Text(if (crossHitActive) "Cross Hit ON" else "Trigger Cross Hit") }
                            OutlinedButton(onClick = { programAdvanceReady = !programAdvanceReady }) {
                                Text(if (programAdvanceReady) "Program Advance READY" else "Program Advance Used")
                            }
                        }
                        Text("Boss Intent Telegraph: $bossIntentText", style = MaterialTheme.typography.bodySmall, color = CrimsonAlert)
                    }

                    TabletopDrawer.SYNC_HUB -> {
                        AnimatedSyncedHealthBarCard(
                            syncState = realtimeGameSyncState,
                            fallbackHostLife = effectiveHostLife,
                            fallbackOpponentLife = effectiveOpponentLife,
                            onQuickAdjustLife = onAdjustSyncedLifePoints
                        )
                        RealtimeGameStateSyncPanel(
                            syncState = realtimeGameSyncState,
                            onSelectSeat = onSelectSyncSeat,
                            onJoinMatchById = onJoinSyncMatchById,
                            onAdjustLifePoints = onAdjustSyncedLifePoints,
                            onSetLifePointsExact = onSetSyncedLifePointsExact,
                            onDeploySyncedCardForActiveSeat = onDeploySyncedCardForActiveSeat,
                            onToggleZoneOrientation = onToggleSyncedZoneOrientation,
                            onRemoveZonePlacement = onRemoveSyncedZonePlacement,
                            onResetSyncedState = onResetSyncedLifeAndBoard
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onBroadcastGameStartToDiscord(currentMatch) }) {
                                Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Discord Start ($discordDesignatedChannel)")
                            }
                            OutlinedButton(onClick = { onBroadcastMatchWinnerToDiscord(currentMatch) }) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Discord Winner")
                            }
                        }
                    }

                    TabletopDrawer.DUAL_CHAT -> {
                        Text("Strict Dual-Channel Chat: IC messages feed Memorial Forge Archive; OOC Normal & Ruling Calls stay strictly separated.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            ChatChannelMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = activeChatMode == mode,
                                    onClick = { activeChatMode = mode },
                                    label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        chatFeed.filter { it.first == activeChatMode }.takeLast(8).forEach { (mode, who, body) ->
                            Text(
                                text = "[${mode.name}] $who: $body",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (mode == ChatChannelMode.IC_MEMORIAL) EmeraldReady else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = chatDraft,
                                onValueChange = { chatDraft = it },
                                placeholder = { Text("Write ${activeChatMode.label} message...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val clean = chatDraft.trim()
                                    if (clean.isNotEmpty()) {
                                        chatFeed.add(Triple(activeChatMode, "Commander", clean))
                                        if (activeChatMode == ChatChannelMode.IC_MEMORIAL) {
                                            onRecordIcMemorialEntry(clean)
                                        }
                                        chatDraft = ""
                                    }
                                }
                            ) {
                                Text("Send")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewMatchDialog) {
        AlertDialog(
            onDismissRequest = { showNewMatchDialog = false },
            title = { Text("Launch Multi-Client Match Room") },
            text = {
                OutlinedTextField(
                    value = newMatchTitle,
                    onValueChange = { newMatchTitle = it },
                    label = { Text("Match Room Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onLaunchMatch(newMatchTitle.trim().ifEmpty { "${uiProfile.hudBadgeLabel} Arena" })
                        newMatchTitle = ""
                        showNewMatchDialog = false
                    }
                ) {
                    Text("Launch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewMatchDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DrawerChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) }
    )
}

@Composable
private fun SensorBar(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: ${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
    }
}
