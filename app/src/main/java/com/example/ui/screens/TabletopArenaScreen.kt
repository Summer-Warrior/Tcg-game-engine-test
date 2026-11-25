package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewInAr
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardEffectType
import com.example.data.CardOrigin
import com.example.data.CardTemplate
import com.example.data.ClientNode
import com.example.data.GameEngine
import com.example.data.GameEngineState
import com.example.data.MatchGameEvent
import com.example.data.MatchSession
import com.example.data.NexusHubBridgeService
import com.example.data.RealtimeGameSyncState
import com.example.data.SerializedGameBoardState
import com.example.data.SerializedZonePlacement
import com.example.data.SyncClientSeat
import com.example.data.TcgVariant
import com.example.data.TurnPhase
import com.example.data.telemetry.CustomCardUsageReportSnapshot
import com.example.engine.BoardArchetypeTemplate
import com.example.engine.CatalogBlueprint
import com.example.ui.components.AttackImpactVfxEvent
import com.example.ui.components.BossSummonCutInEvent
import com.example.ui.components.CanvasGameBoard
import com.example.ui.components.DuelDiskLpCounterBar
import com.example.ui.components.DuelVfxOverlay
import com.example.ui.components.IlluminatedDuelDiskPhaseStrip
import com.example.ui.components.PersistentGameEventLogCard
import com.example.ui.components.PhaseSweepBannerEvent
import com.example.ui.components.RealtimeGameStateSyncPanel
import com.example.ui.components.TcgVisualModuleOverlay
import com.example.ui.theme.CardOriginBadge
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DuelArenaAtmosphere
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.FoilFinishStyle
import com.example.ui.theme.HolographicArenaFloorCanvas
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.chameleonAccentFor
import com.example.ui.theme.duelDiskPlate
import com.example.ui.theme.foilShimmerOverlay
import com.example.ui.theme.rememberChamferedDuelShape
import com.example.viewmodel.UiState

private enum class TabletopDrawer(val label: String) {
    EVENT_LOG("Event Log & Telemetry"),
    CHAIN_STACK("LIFO Chain"),
    CALC_21("21-Digit Calc"),
    SENSORS("Sensors & Tiers"),
    VG_MECHANICS("VG Mechanics"),
    SYNC_HUB("Firestore Sync"),
    DUAL_CHAT("IC / OOC Chat"),
    ARENA_HOSTING("Format & Atmosphere")
}

private enum class ChatChannelMode(val label: String) {
    IC_MEMORIAL("IC (Memorial Feed)"),
    OOC_NORMAL("OOC Normal"),
    OOC_RULING("OOC Ruling Call")
}

/**
 * Section 3: ONE Consolidated Playmat-First Solid Vision Tabletop (~80%+ viewport).
 *
 * - Yu-Gi-Oh! Anime / Manga / Video-Game Core Foundation:
 *   * Chamfered Battle City Duel Disk Rolling LP Counter (single source of truth — zero duplicate HP bars)
 *   * Illuminated Duel Disk Phase Strip (`DP -> SP -> MP1 -> BP -> MP2 -> EP`)
 *   * Solid Vision 3D Perspective Toggle over animated Holographic Arena Floor (7 switchable Atmospheres)
 *   * Boss Summon Cut-In Banner, Chain Link Spiral (`CL1->CL2->CL3`), Phase Sweep, Attack Plasma Beam
 *   * Fanned Interactive Hand Dock with Foil Shimmer (`Royal Finish`/`Prismatic`/`Manga Rare`/`Starlight`)
 * - Dynamically layers signature flourishes from ALL other TCGs via `TcgVisualModuleOverlay`.
 * - Integrates live Custom Card Usage Telemetry reporting directly back to Card Maker.
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
    telemetrySnapshot: CustomCardUsageReportSnapshot = CustomCardUsageReportSnapshot(),
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
    onRecordIcMemorialEntry: (String) -> Unit = {},
    onSendUsageReportToCardMaker: () -> Unit = {},
    onStatusMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val matches = (matchesState as? UiState.Success)?.data.orEmpty()
    val currentMatch = matches.firstOrNull { it.id == activeMatchId } ?: matches.firstOrNull()
    val effectiveBaseId = currentMatch?.baseVariantId ?: selectedBaseVariantId
    val uiProfile = BuiltInTcgCatalog.systemUiProfileFor(effectiveBaseId, activeClientNode)
    val baseSpec = BuiltInTcgCatalog.findBaseSpec(effectiveBaseId)
    val activeArchetype = BoardArchetypeTemplate.fromId(
        activeFormatBlueprint?.boardArchetype ?: "zone-rows"
    )
    val chameleonAccent = chameleonAccentFor(activeFormatBlueprint, effectiveBaseId)

    // Solid Vision 3D Perspective & Arena Atmosphere state
    var solidVision3dEnabled by rememberSaveable { mutableStateOf(false) }
    var arenaAtmosphereName by rememberSaveable(activeFormatBlueprint?.id, effectiveBaseId) {
        mutableStateOf(DuelArenaAtmosphere.defaultForBlueprint(activeFormatBlueprint, effectiveBaseId).name)
    }
    val activeAtmosphere = remember(arenaAtmosphereName) {
        DuelArenaAtmosphere.entries.firstOrNull { it.name == arenaAtmosphereName }
            ?: DuelArenaAtmosphere.BATTLE_CITY_STADIUM
    }
    var activeFoilStyleName by rememberSaveable { mutableStateOf(FoilFinishStyle.ROYAL_FINISH.name) }
    val activeFoilStyle = remember(activeFoilStyleName) {
        FoilFinishStyle.entries.firstOrNull { it.name == activeFoilStyleName }
            ?: FoilFinishStyle.ROYAL_FINISH
    }

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
            Triple(ChatChannelMode.IC_MEMORIAL, "Tabletop Arbiter", "Solid Vision Playmat synchronized for ${activeFormatBlueprint?.name ?: uiProfile.systemTitle}."),
            Triple(ChatChannelMode.OOC_RULING, "Judge", "LIFO Chain & strict zero-time-travel match rules active.")
        )
    }

    // Live VFX State (Boss Cut-In, Phase Sweep, Attack Plasma Beam)
    var activeBossCutIn by remember { mutableStateOf<BossSummonCutInEvent?>(null) }
    var activePhaseBanner by remember { mutableStateOf<PhaseSweepBannerEvent?>(null) }
    var activeAttackImpact by remember { mutableStateOf<AttackImpactVfxEvent?>(null) }
    var lastObservedPhase by remember { mutableStateOf(gameEngineState.currentPhase) }

    LaunchedEffect(gameEngineState.currentPhase, gameEngineState.turnNumber) {
        if (gameEngineState.currentPhase != lastObservedPhase) {
            lastObservedPhase = gameEngineState.currentPhase
            activePhaseBanner = PhaseSweepBannerEvent(
                phaseTitle = gameEngineState.currentPhase.displayName,
                phaseCode = gameEngineState.clientPhaseCode,
                turnNumber = gameEngineState.turnNumber
            )
        }
    }

    // Active Drawer + Modals
    var openDrawer by remember { mutableStateOf<TabletopDrawer?>(null) }
    var zoomedHandCard by remember { mutableStateOf<CardTemplate?>(null) }
    var zoomedCardDefenseRotated by remember { mutableStateOf(false) }
    var showNewMatchDialog by remember { mutableStateOf(false) }
    var newMatchTitle by remember { mutableStateOf("") }

    val hostUnits = remember(currentMatch?.hostBoardCards, effectiveBaseId) {
        currentMatch?.hostBoardCards.orEmpty().map { CardTemplate.fromBoardDescriptor(it, effectiveBaseId) }
    }
    val opponentUnits = remember(currentMatch?.opponentBoardCards, effectiveBaseId) {
        currentMatch?.opponentBoardCards.orEmpty().map { CardTemplate.fromBoardDescriptor(it, effectiveBaseId) }
    }
    val effectiveHostLife = if (realtimeGameSyncState.isAttached) realtimeGameSyncState.hostLife else (currentMatch?.hostLife ?: gameEngineState.hostLife)
    val effectiveOpponentLife = if (realtimeGameSyncState.isAttached) realtimeGameSyncState.opponentLife else (currentMatch?.opponentLife ?: gameEngineState.opponentLife)
    val isMatchFinished = currentMatch?.status == "FINISHED" || gameEngineState.status == "FINISHED"
    val matchWinnerLabel = currentMatch?.winnerName?.ifBlank { gameEngineState.winnerName } ?: gameEngineState.winnerName

    val perspectiveTilt by animateFloatAsState(
        targetValue = if (solidVision3dEnabled) 18f else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "solidVisionTilt"
    )

    fun fireSummonVfxIfAce(card: CardTemplate) {
        if (card.attack >= 2000 || card.cost >= 4 || card.cardOrigin != CardOrigin.CANON_ORIGINAL) {
            activeBossCutIn = BossSummonCutInEvent(
                cardName = card.name,
                cardType = card.cardType,
                atkText = card.attack21,
                defText = card.defense21,
                origin = card.cardOrigin,
                linkedCanonCardId = card.linkedCanonCardId,
                accentColor = chameleonAccent
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050A14))
            .testTag("unified_tabletop_screen")
    ) {
        // 1. Compact Duel Disk Top Toolbar: Format/Atmosphere + 3D Solid Vision Toggle + Drawers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A1120))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = openDrawer == TabletopDrawer.ARENA_HOSTING,
                onClick = { openDrawer = TabletopDrawer.ARENA_HOSTING },
                label = {
                    Text(
                        text = activeFormatBlueprint?.name?.take(16) ?: uiProfile.hudBadgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.testTag("tabletop_format_chip")
            )
            FilterChip(
                selected = solidVision3dEnabled,
                onClick = { solidVision3dEnabled = !solidVision3dEnabled },
                label = { Text(if (solidVision3dEnabled) "3D Vision ON" else "3D Vision", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.testTag("tabletop_solid_vision_toggle")
            )
            DrawerChip("Chain (${gameEngineState.effectStack.size})", Icons.Default.Layers, openDrawer == TabletopDrawer.CHAIN_STACK) { openDrawer = TabletopDrawer.CHAIN_STACK }
            DrawerChip("Log (${telemetrySnapshot.totalPlaysRecorded})", Icons.Default.History, openDrawer == TabletopDrawer.EVENT_LOG) { openDrawer = TabletopDrawer.EVENT_LOG }
            DrawerChip("Sync", Icons.Default.Sync, openDrawer == TabletopDrawer.SYNC_HUB) { openDrawer = TabletopDrawer.SYNC_HUB }
            DrawerChip("21d Calc", Icons.Default.Calculate, openDrawer == TabletopDrawer.CALC_21) { openDrawer = TabletopDrawer.CALC_21 }
            DrawerChip("Sensors", Icons.Default.Sensors, openDrawer == TabletopDrawer.SENSORS) { openDrawer = TabletopDrawer.SENSORS }
            DrawerChip("VG Mod", Icons.Default.SportsEsports, openDrawer == TabletopDrawer.VG_MECHANICS) { openDrawer = TabletopDrawer.VG_MECHANICS }
            DrawerChip("IC/OOC", Icons.AutoMirrored.Filled.Chat, openDrawer == TabletopDrawer.DUAL_CHAT) { openDrawer = TabletopDrawer.DUAL_CHAT }
            OutlinedButton(
                onClick = { showNewMatchDialog = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("tabletop_new_match_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Match", modifier = Modifier.size(14.dp))
                Text("Room", style = MaterialTheme.typography.labelSmall)
            }
        }

        // 2. Iconic Anime/Manga Rolling LP Counter Bar (Single source of truth — ZERO duplicate HP bars)
        DuelDiskLpCounterBar(
            hostName = currentMatch?.hostName ?: "Commander",
            opponentName = currentMatch?.connectedClients?.firstOrNull() ?: "Peer Client",
            hostLife = effectiveHostLife,
            opponentLife = effectiveOpponentLife,
            startingLife = baseSpec.startingLife,
            turnNumber = gameEngineState.turnNumber,
            activePhaseCode = gameEngineState.clientPhaseCode,
            accentColor = chameleonAccent
        )

        // 3. Illuminated Duel Disk Phase Strip (`DP -> SP -> MP1 -> BP -> MP2 -> EP`)
        IlluminatedDuelDiskPhaseStrip(
            activePhase = gameEngineState.currentPhase,
            accentColor = chameleonAccent,
            onSelectPhase = { phase ->
                onSelectGameEnginePhase(phase, currentMatch)
                activePhaseBanner = PhaseSweepBannerEvent(
                    phaseTitle = phase.displayName,
                    phaseCode = phase.clientPhaseCodeFor(effectiveBaseId),
                    turnNumber = gameEngineState.turnNumber
                )
            }
        )

        // Post-Match Summary + Direct "Send Usage Report to Card Maker" Banner when duel finishes
        if (isMatchFinished) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .duelDiskPlate(accentColor = RadiantGold, secondaryColor = EmeraldReady, cutSize = 8.dp, pulseEnergy = true)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("post_match_summary_banner"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DUEL COMPLETE • WINNER: ${matchWinnerLabel.ifBlank { "Commander" }}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = RadiantGold
                    )
                    Text(
                        text = "Tracked ${telemetrySnapshot.cards.size} card(s), ${telemetrySnapshot.totalPlaysRecorded} play(s), ${telemetrySnapshot.totalInteractionsRecorded} interaction(s).",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                Button(
                    onClick = onSendUsageReportToCardMaker,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("post_match_send_usage_report_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send Usage Report", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 4. ONE UNIFIED SOLID VISION PLAYMAT VIEWPORT (~80% height)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("tabletop_main_playmat_container")
        ) {
            // Animated Holographic Arena Floor underneath the unified playmat
            HolographicArenaFloorCanvas(
                atmosphere = activeAtmosphere,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Modular TCG Visual Flourish Strip layered cleanly on top of the Yu-Gi-Oh! core
                TcgVisualModuleOverlay(
                    blueprint = activeFormatBlueprint,
                    baseVariantId = effectiveBaseId,
                    stackDepth = gameEngineState.effectStack.size,
                    hostUnitCount = hostUnits.size,
                    opponentUnitCount = opponentUnits.size,
                    magicPulsePct = magicPulsePct,
                    forcePulsePct = forcePulsePct,
                    outsiderVitalityMode = outsiderVitalityMode,
                    pxzFrontlineCount = pxzFrontlineCount,
                    pxzXpGaugePct = pxzXpGaugePct,
                    crossHitActive = crossHitActive,
                    bossIntentText = bossIntentText,
                    programAdvanceReady = programAdvanceReady,
                    is3dGridOverlay = is3dGridOverlay,
                    onTriggerQuickFeedback = { msg -> onStatusMessage(msg) }
                )

                // ONE single CanvasGameBoard with optional Solid Vision 3D Perspective tilt
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            rotationX = perspectiveTilt
                            cameraDistance = 14f * density
                        }
                ) {
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
                            fireSummonVfxIfAce(card)
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
                            activeAttackImpact = AttackImpactVfxEvent(
                                attackerName = attacker.name,
                                targetLabel = targetZone,
                                damageCallout = "-${attacker.attack} LP!"
                            )
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

            // Live Non-Blocking Duel VFX Overlay (Chain Link Spiral, Boss Cut-In, Phase Sweep, Attack Beam)
            DuelVfxOverlay(
                effectStack = gameEngineState.effectStack,
                bossCutIn = activeBossCutIn,
                phaseBanner = activePhaseBanner,
                attackImpact = activeAttackImpact,
                onDismissBossCutIn = { activeBossCutIn = null },
                onDismissPhaseBanner = { activePhaseBanner = null },
                onDismissAttackImpact = { activeAttackImpact = null }
            )
        }

        // 5. Fanned Interactive Hand Dock with Holographic Foil Sheen & Quick Duel Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A1120))
                .border(1.dp, chameleonAccent.copy(alpha = 0.45f))
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .testTag("tabletop_fanned_hand_dock"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hand (${playerHand.size}) • ${activeFoilStyle.label} • ${activeAtmosphere.badgeTag}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = chameleonAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedButton(
                        onClick = { onStepGameEnginePhase(currentMatch) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("tabletop_next_phase_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Next Phase", modifier = Modifier.size(13.dp))
                        Text("Step", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onDrawCard(effectiveBaseId, currentMatch) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("hand_dock_draw_button")
                    ) {
                        Text("Draw", style = MaterialTheme.typography.labelSmall)
                    }
                    if (currentMatch != null) {
                        Button(
                            onClick = {
                                val lead = hostUnits.firstOrNull()
                                activeAttackImpact = AttackImpactVfxEvent(
                                    attackerName = lead?.name ?: "Duelist Vanguard",
                                    targetLabel = "Opponent LP",
                                    damageCallout = "-${lead?.attack ?: 1800} LP!"
                                )
                                onExecuteCombatAndAdvanceTurn(currentMatch)
                            },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = Color.White),
                            modifier = Modifier.testTag("hand_dock_combat_button")
                        ) {
                            Text("Battle!", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 3.dp)
            ) {
                items(playerHand.indices.toList(), key = { idx -> "${playerHand[idx].id}_$idx" }) { idx ->
                    val card = playerHand[idx]
                    val fanTilt = ((idx - (playerHand.size - 1) / 2f) * 2.8f).coerceIn(-9f, 9f)
                    val cardShape = rememberChamferedDuelShape(7.dp)
                    Box(
                        modifier = Modifier
                            .width(134.dp)
                            .graphicsLayer { rotationZ = fanTilt }
                            .clip(cardShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF10192D),
                                        chameleonAccent.copy(alpha = 0.22f),
                                        Color(0xFF1C1333)
                                    )
                                )
                            )
                            .foilShimmerOverlay(style = activeFoilStyle, intensity = 0.24f)
                            .border(1.3.dp, RadiantGold.copy(alpha = 0.8f), cardShape)
                            .clickable {
                                zoomedCardDefenseRotated = false
                                zoomedHandCard = card
                            }
                            .padding(8.dp)
                            .testTag("hand_card_${card.id}")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (card.cardOrigin != CardOrigin.CANON_ORIGINAL) {
                                CardOriginBadge(origin = card.cardOrigin, linkedCanonCardId = card.linkedCanonCardId)
                            } else {
                                Text(
                                    text = "${card.cardType} • C${card.cost}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RadiantGold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "ATK ${card.attack} / DEF ${card.defense}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFBAE6FD)
                            )
                        }
                    }
                }
            }
        }
    }

    // Card Zoom Preview Modal with Foil Sheen, Origin Badge & Smooth 90° Defense/Tapped Rotation
    zoomedHandCard?.let { card ->
        val cardRotation by animateFloatAsState(
            targetValue = if (zoomedCardDefenseRotated) 90f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 240f),
            label = "cardDefenseRotate"
        )
        AlertDialog(
            onDismissRequest = { zoomedHandCard = null },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.name, fontWeight = FontWeight.Black)
                    CardOriginBadge(origin = card.cardOrigin, linkedCanonCardId = card.linkedCanonCardId)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { rotationZ = cardRotation * 0.12f }
                            .duelDiskPlate(accentColor = chameleonAccent, secondaryColor = RadiantGold, cutSize = 10.dp)
                            .foilShimmerOverlay(style = activeFoilStyle, intensity = 0.30f)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${card.cardType} • ${if (zoomedCardDefenseRotated) "DEFENSE / TAPPED (90°)" else "ATTACK / UNTAPPED (0°)"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = RadiantGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ATK ${card.attack21} / DEF ${card.defense21} • Cost ${card.cost} • ${card.pipCost}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            if (card.splitOffSetId.isNotBlank()) {
                                Text(
                                    text = "Split-Off Set: ${card.splitOffSetId} (Parent: ${card.parentOfficialSetId})",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(card.abilityText, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2E8F0))
                        }
                    }
                    OutlinedButton(
                        onClick = { zoomedCardDefenseRotated = !zoomedCardDefenseRotated },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (zoomedCardDefenseRotated) "Switch to Attack / Untap (0°)" else "Switch to Defense / Tap (90°)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        fireSummonVfxIfAce(card)
                        if (currentMatch != null) {
                            onDeployCard(currentMatch, card)
                        }
                        onLogQuickCardPlay(card.name)
                        zoomedHandCard = null
                    }
                ) {
                    Text("Summon / Deploy")
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
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(drawer.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)

                when (drawer) {
                    TabletopDrawer.ARENA_HOSTING -> {
                        Text("Solid Vision Arena Atmosphere (7 Holographic Floors):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = RadiantGold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DuelArenaAtmosphere.entries.forEach { atmo ->
                                FilterChip(
                                    selected = activeAtmosphere == atmo,
                                    onClick = { arenaAtmosphereName = atmo.name },
                                    label = { Text(atmo.displayName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Text("Card Foil Finish:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ElectricCyan)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FoilFinishStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = activeFoilStyle == style,
                                    onClick = { activeFoilStyleName = style.name },
                                    label = { Text(style.label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }

                        Text("Arena Hosting Mode (${activeArchetype.displayLabel}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                            Text("Solid Vision 3D Perspective Tilt", style = MaterialTheme.typography.bodySmall)
                            Switch(checked = solidVision3dEnabled, onCheckedChange = { solidVision3dEnabled = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("3D Combat Positioning Grid Overlay", style = MaterialTheme.typography.bodySmall)
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
                        // Real Live Card Usage & Interaction Telemetry Card + 1-Tap Send to Card Maker
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .duelDiskPlate(accentColor = ElectricCyan, secondaryColor = RadiantGold, cutSize = 8.dp)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "LIVE CARD USAGE & INTERACTION TELEMETRY (v${telemetrySnapshot.schemaVersion})",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = RadiantGold
                            )
                            Text(
                                text = "Matches: ${telemetrySnapshot.totalMatchesTracked} • Tracked Cards: ${telemetrySnapshot.cards.size} • Real Plays: ${telemetrySnapshot.totalPlaysRecorded} • Card Interactions: ${telemetrySnapshot.totalInteractionsRecorded}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onSendUsageReportToCardMaker,
                                    modifier = Modifier.testTag("drawer_send_usage_report_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Send Usage Report to Card Maker")
                                }
                            }
                        }

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
                                        Text("CL${gameEngineState.effectStack.size - idx}: ${item.sourceCard.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
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
                        onLaunchMatch(newMatchTitle.trim().ifEmpty { "${activeFormatBlueprint?.name ?: uiProfile.systemTitle} Room" })
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
    }
}
