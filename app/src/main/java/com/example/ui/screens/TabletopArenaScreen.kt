package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ClientWindowLauncher
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardEffectType
import com.example.data.CardTemplate
import com.example.data.ClientNode
import com.example.data.ClientSystemUiProfile
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
import com.example.ui.components.AnimatedSyncedHealthBarCard
import com.example.ui.components.CanvasGameBoard
import com.example.ui.components.ComposeGameBoardLayout
import com.example.ui.components.D3BoardStateVisualizer
import com.example.ui.components.GameplayActionSidebarLog
import com.example.ui.components.InGameDebugHudPanel
import com.example.ui.components.PersistentGameEventLogCard
import com.example.ui.components.RealtimeGameStateSyncPanel
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import com.example.viewmodel.UiState

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
    onLogCustomGameEvent: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showNewMatchDialog by remember { mutableStateOf(false) }
    val matches = (matchesState as? UiState.Success)?.data.orEmpty()
    val currentMatch = matches.firstOrNull { it.id == activeMatchId } ?: matches.firstOrNull()
    val effectiveBaseId = currentMatch?.baseVariantId ?: selectedBaseVariantId
    val activeBaseSpec = BuiltInTcgCatalog.findBaseSpec(effectiveBaseId)
    val uiProfile = BuiltInTcgCatalog.systemUiProfileFor(effectiveBaseId)

    // Fallback live log list when previewing without an active Firestore match
    val fallbackActionLogs = remember(effectiveBaseId) {
        mutableStateListOf(
            "CTOS_JOIN_GAME: Initialized ${uiProfile.systemTitle} tabletop on $effectiveBaseId",
            "MSG_NEW_PHASE [Turn 1]: Entered Opening Phase -> ${uiProfile.phaseStripCodes.firstOrNull() ?: "MAIN"}",
            "MOVE_HISTORY [Turn 1 • MAIN]: Player 1 moved '${playerHand.firstOrNull()?.name ?: "Ace Unit"}' to Field [${uiProfile.fieldSlotCodes.firstOrNull() ?: "MMZ-1"}] (${playerHand.firstOrNull()?.attack21 ?: "3000"} ATK)"
        )
    }
    var fallbackPhase by rememberSaveable(effectiveBaseId) {
        mutableStateOf(uiProfile.phaseStripCodes.firstOrNull() ?: "MAIN")
    }

    val effectiveActionLogs: List<String> = currentMatch?.actionLog?.ifEmpty { fallbackActionLogs }
        ?: fallbackActionLogs
    val effectiveTurn: Long = currentMatch?.turnNumber ?: 1L
    val effectivePhase: String = currentMatch?.activePhase ?: fallbackPhase

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideArena = maxWidth >= 680.dp
        var isSidebarSplitDocked by rememberSaveable(isWideArena) { mutableStateOf(isWideArena) }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left / Main Column: Live Tabletop Arena + D3 Board State Visualizer
            LazyColumn(
                modifier = Modifier
                    .weight(if (isSidebarSplitDocked) 1.35f else 1f)
                    .fillMaxHeight()
                    .testTag("tabletop_arena_list"),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Match Selector & Client System UI Header
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(18.dp)
                            ),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "CLIENT SYSTEM UI: ${uiProfile.hudBadgeLabel} (${uiProfile.boardLayoutType})",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = currentMatch?.title ?: uiProfile.systemTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Button(
                                    onClick = { showNewMatchDialog = true },
                                    modifier = Modifier.testTag("new_arena_match_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "New Arena Match",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Arena")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onRequestConfirmOpenClientWindow(effectiveBaseId, currentMatch?.id)
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("open_arena_client_window_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DesktopWindows,
                                        contentDescription = "Open Client System in Separate Window",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Separate Window",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                OutlinedButton(
                                    onClick = { isSidebarSplitDocked = !isSidebarSplitDocked },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("toggle_arena_sidebar_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerticalSplit,
                                        contentDescription = "Toggle Docked Sidebar Log",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isSidebarSplitDocked) "Undock Log (${effectiveActionLogs.size})" else "Dock Sidebar (${effectiveActionLogs.size})",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onBroadcastGameStartToDiscord(currentMatch) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("arena_discord_broadcast_start_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = "Broadcast Game Start to Discord",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Discord Start ($discordDesignatedChannel)",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = { onBroadcastMatchWinnerToDiscord(currentMatch) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("arena_discord_broadcast_winner_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EmojiEvents,
                                        contentDescription = "Broadcast Match Winner to Discord",
                                        tint = RadiantGold,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (currentMatch?.winnerName?.isNotBlank() == true) {
                                            "Post Winner (${currentMatch.winnerName.take(10)})"
                                        } else {
                                            "Declare & Post Winner"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RadiantGold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (matches.size > 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(matches, key = { it.id }) { m ->
                                        FilterChip(
                                            selected = m.id == currentMatch?.id,
                                            onClick = { onSelectMatch(m.id) },
                                            label = { Text("${m.title} (${m.baseVariantId})") }
                                        )
                                    }
                                }
                            }

                            if (currentMatch != null) {
                                Spacer(modifier = Modifier.height(8.dp))
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
                                            imageVector = Icons.Default.Hub,
                                            contentDescription = "Connected Clients",
                                            tint = EmeraldReady,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Clients (${currentMatch.baseVariantId}): ${currentMatch.connectedClients.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldReady,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onBridgeClientsIntoMatch(currentMatch) },
                                        modifier = Modifier.testTag("bridge_clients_button"),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Sync Clients",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sync Clients", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Dynamic Animated Visual Health Bars synced from Firestore Game State
                item {
                    AnimatedSyncedHealthBarCard(
                        syncState = realtimeGameSyncState,
                        fallbackHostLife = currentMatch?.hostLife ?: activeBaseSpec.startingLife,
                        fallbackOpponentLife = currentMatch?.opponentLife ?: activeBaseSpec.startingLife,
                        onQuickAdjustLife = onAdjustSyncedLifePoints
                    )
                }

                // Real-Time 2-Client Firestore Life Points & Board State Sync Manager Console
                item {
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
                }

                // Persistent Scrollable Firestore Game Event Log (/matches/{matchId}/events)
                item {
                    val persistentEvents = (persistentEventsState as? UiState.Success)?.data.orEmpty()
                    val persistentError = (persistentEventsState as? UiState.Error)?.message
                    val quickCardName = playerHand.firstOrNull()?.name ?: "Ace Construct"
                    val quickLpStep = if (activeBaseSpec.startingLife >= 1000L) 500L else 5L
                    PersistentGameEventLogCard(
                        matchId = realtimeGameSyncState.matchId.ifBlank { currentMatch?.id.orEmpty() },
                        events = persistentEvents,
                        isFirestoreConnected = realtimeGameSyncState.isAttached || currentMatch != null,
                        errorMessage = persistentError,
                        sampleCardName = quickCardName,
                        defaultLifeDeltaStep = quickLpStep,
                        onLogQuickCardPlay = onLogQuickCardPlay,
                        onLogQuickLifeReduction = onLogQuickLifeReduction,
                        onLogCustomEvent = onLogCustomGameEvent
                    )
                }

                // Live In-Game Ruleset Parameters & Game-State Variables Debug HUD Panel
                item {
                    val hudHostUnits = currentMatch?.hostBoardCards?.map {
                        CardTemplate.fromBoardDescriptor(it, currentMatch.baseVariantId)
                    } ?: playerHand.take(2)
                    val hudOppUnits = currentMatch?.opponentBoardCards?.map {
                        CardTemplate.fromBoardDescriptor(it, currentMatch.baseVariantId)
                    } ?: playerHand.drop(2).take(1)

                    InGameDebugHudPanel(
                        baseSpec = activeBaseSpec,
                        activeCustomVariant = activeCustomVariant,
                        uiProfile = uiProfile,
                        activeClientNode = activeClientNode,
                        engineState = gameEngineState,
                        currentMatch = currentMatch,
                        syncedBoardState = syncedBoardState,
                        handCards = playerHand,
                        hostUnits = hudHostUnits,
                        opponentUnits = hudOppUnits,
                        onStepPhase = { onStepGameEnginePhase(currentMatch) },
                        onQueueCardEffect = { card, effectType ->
                            onQueueCardEffect(card, effectType, currentMatch)
                        },
                        onResolveNextEffect = { onResolveNextEffect(currentMatch) },
                        onResolveAllEffects = { onResolveAllEffects(currentMatch) }
                    )
                }

                if (currentMatch == null) {
                    // Responsive 2-Player Canvas GameBoard (Touch Drag-and-Drop + Firestore Sync)
                    item {
                        CanvasGameBoard(
                            uiProfile = uiProfile,
                            hostName = "Commander (You)",
                            opponentName = uiProfile.clientDisplayName,
                            hostLife = activeBaseSpec.startingLife,
                            opponentLife = activeBaseSpec.startingLife,
                            hostUnits = playerHand.take(2),
                            opponentUnits = playerHand.drop(2).take(1),
                            handCards = playerHand,
                            turnNumber = 1L,
                            activePhase = fallbackPhase,
                            syncedBoardState = syncedBoardState,
                            onDeployCardFromHand = { card, targetZone ->
                                fallbackActionLogs.add(
                                    "MOVE_HISTORY [Turn 1 • $fallbackPhase]: Player 1 moved '${card.name}' to Field [$targetZone] (${card.attack21}/${card.defense21})"
                                )
                            },
                            onMoveCardBetweenZones = { card, fromZone, toZone ->
                                fallbackActionLogs.add(
                                    "MOVE_HISTORY [Turn 1 • $fallbackPhase]: Player 1 moved '${card.name}' from [$fromZone] to Field [$toZone]"
                                )
                            },
                            onAttackOpponentZone = { attacker, targetZone, defender ->
                                fallbackActionLogs.add(
                                    "MSG_DAMAGE [Turn 1 • BP]: '${attacker.name}' (${attacker.attack} ATK) attacked P2 [$targetZone] (${defender?.name ?: "Direct"})!"
                                )
                            },
                            onDrawCardRequest = {
                                onDrawCard(effectiveBaseId, null)
                                fallbackActionLogs.add("MSG_DRAW [Turn 1 • $fallbackPhase]: Drew card from P1 Deck Stack")
                            },
                            onBroadcastBoardState = { placements, eventType, eventSummary ->
                                onBroadcastBoardState(null, placements, eventType, eventSummary)
                                fallbackActionLogs.add("MOVE_HISTORY [Turn 1 • $eventType]: $eventSummary")
                            },
                            onTriggerPeerClientSyncMove = { placements ->
                                onTriggerPeerClientSyncMove(null, placements)
                                fallbackActionLogs.add("MOVE_HISTORY [Turn 1]: Player 2 (Peer Client #2) moved card to Field via Firebase Firestore")
                            }
                        )
                    }
                    item {
                        ComposeGameBoardLayout(
                            uiProfile = uiProfile,
                            engineState = gameEngineState,
                            handCards = playerHand,
                            hostUnits = playerHand.take(2),
                            opponentUnits = playerHand.drop(2).take(1),
                            activeCustomVariant = activeCustomVariant,
                            onDrawFromDeckPlaceholder = {
                                onDrawCard(effectiveBaseId, null)
                                fallbackActionLogs.add(
                                    "MSG_DRAW [Turn ${gameEngineState.turnNumber} • ${gameEngineState.currentPhase.code}]: Player 1 drew card from Deck Placeholder to Hand"
                                )
                            },
                            onDeployCardToActiveZone = { card, targetZone ->
                                onAdjustResources(-card.cost.toLong().coerceAtLeast(1L), null)
                                fallbackActionLogs.add(
                                    "MOVE_HISTORY [Turn ${gameEngineState.turnNumber} • ${gameEngineState.currentPhase.code}]: Player 1 moved '${card.name}' from Hand Placeholder to Active Zone [$targetZone]"
                                )
                            },
                            onQueueCardEffectFromZone = { card ->
                                onQueueCardEffect(card, null, null)
                                fallbackActionLogs.add(
                                    "MSG_CHAINING [Turn ${gameEngineState.turnNumber} • ${gameEngineState.currentPhase.code}]: Player 1 queued effect of '${card.name}' from Compose GameBoard"
                                )
                            },
                            onStepPhase = {
                                onStepGameEnginePhase(null)
                                fallbackPhase = gameEngineState.currentPhase.nextPhase().code
                            },
                            onSelectPhase = { phase ->
                                onSelectGameEnginePhase(phase, null)
                                fallbackPhase = phase.code
                                fallbackActionLogs.add(
                                    "MSG_NEW_PHASE [Turn ${gameEngineState.turnNumber}]: TurnPhaseIndicator transitioned -> ${phase.displayName} (${phase.code})"
                                )
                            },
                            onSelectClientPhaseCode = { code ->
                                fallbackPhase = code
                                fallbackActionLogs.add(
                                    "MSG_NEW_PHASE [Turn ${gameEngineState.turnNumber}]: Client sub-phase selected -> $code"
                                )
                            },
                            onSpendResource = { amount ->
                                onAdjustResources(-amount, null)
                                fallbackActionLogs.add(
                                    "RESOURCE_POOL [Turn ${gameEngineState.turnNumber}]: Spent $amount ${activeBaseSpec.resourceName}"
                                )
                            },
                            onGainResource = { amount ->
                                onAdjustResources(amount, null)
                                fallbackActionLogs.add(
                                    "RESOURCE_POOL [Turn ${gameEngineState.turnNumber}]: Gained +$amount ${activeBaseSpec.resourceName}"
                                )
                            },
                            onRefillResources = {
                                onRefillResources(null)
                                fallbackActionLogs.add(
                                    "RESOURCE_REFILL [Turn ${gameEngineState.turnNumber}]: Refilled ${activeBaseSpec.resourceName} pool"
                                )
                            },
                            onExpandMaxResourceCap = {
                                onExpandMaxResources(null)
                                fallbackActionLogs.add(
                                    "RESOURCE_CAP [Turn ${gameEngineState.turnNumber}]: Expanded ${activeBaseSpec.resourceName} capacity +1"
                                )
                            },
                            onSetResourceExact = { exact ->
                                onSetResourcesExact(exact, null)
                            }
                        )
                    }
                    item {
                        GameEngineTurnAndEffectPanel(
                            engineState = gameEngineState,
                            uiProfile = uiProfile,
                            handCards = playerHand,
                            onStepPhase = {
                                onStepGameEnginePhase(null)
                                val nextPhase = gameEngineState.currentPhase.nextPhase()
                                fallbackPhase = nextPhase.code
                                fallbackActionLogs.add(
                                    "MSG_NEW_PHASE [Turn ${gameEngineState.turnNumber}]: GameEngine stepped -> ${nextPhase.displayName} (${nextPhase.code})"
                                )
                            },
                            onSelectPhase = { phase ->
                                onSelectGameEnginePhase(phase, null)
                                fallbackPhase = phase.code
                                fallbackActionLogs.add(
                                    "MSG_NEW_PHASE [Turn ${gameEngineState.turnNumber}]: GameEngine transitioned -> ${phase.displayName} (${phase.code})"
                                )
                            },
                            onQueueEffect = { card, effectType ->
                                onQueueCardEffect(card, effectType, null)
                                fallbackActionLogs.add(
                                    "MSG_CHAINING [Turn ${gameEngineState.turnNumber} • ${gameEngineState.currentPhase.code}]: Queued '${card.name}' (${effectType?.displayTitle ?: "Auto Effect"}) on Stack"
                                )
                            },
                            onResolveNextEffect = {
                                onResolveNextEffect(null)
                                fallbackActionLogs.add(
                                    "MSG_CHAIN_SOLVED [Turn ${gameEngineState.turnNumber} • ${gameEngineState.currentPhase.code}]: Resolved top Chain Link / Stack item"
                                )
                            },
                            onResolveAllEffects = {
                                onResolveAllEffects(null)
                                fallbackActionLogs.add(
                                    "MSG_CHAIN_END [Turn ${gameEngineState.turnNumber} • ${gameEngineState.currentPhase.code}]: Resolved full LIFO Chain / Stack"
                                )
                            }
                        )
                    }
                    item {
                        D3BoardStateVisualizer(
                            uiProfile = uiProfile,
                            hostUnits = playerHand.take(2),
                            opponentUnits = playerHand.drop(2).take(1),
                            handCards = playerHand,
                            turnNumber = 1L,
                            activePhase = fallbackPhase,
                            onQuickDeployFromVisualizer = {
                                onDrawCard(effectiveBaseId, null)
                                fallbackActionLogs.add("MSG_DRAW [Turn 1 • $fallbackPhase]: Drew card from Deck Stack")
                            }
                        )
                    }
                    if (!isSidebarSplitDocked) {
                        item {
                            GameplayActionSidebarLog(
                                uiProfile = uiProfile,
                                rawActionLogs = effectiveActionLogs,
                                turnNumber = effectiveTurn,
                                activePhase = effectivePhase,
                                isDockedSidebar = false,
                                syncedBoardState = syncedBoardState,
                                onToggleDockedMode = { isSidebarSplitDocked = true },
                                onSelectPhaseFromSidebar = { phaseCode ->
                                    fallbackActionLogs.add(
                                        "MSG_NEW_PHASE [Turn 1]: Shifted phase $fallbackPhase -> $phaseCode"
                                    )
                                    fallbackPhase = phaseCode
                                },
                                onRecordSyncedMoveFromSidebar = { moveLine ->
                                    fallbackActionLogs.add(moveLine)
                                    onBroadcastBoardState(
                                        null,
                                        syncedBoardState?.zonePlacements.orEmpty(),
                                        "MOVE_HISTORY",
                                        moveLine
                                    )
                                }
                            )
                        }
                    }
                } else {
                    val opponentUnits = currentMatch.opponentBoardCards.map {
                        CardTemplate.fromBoardDescriptor(it, currentMatch.baseVariantId)
                    }
                    val hostUnits = currentMatch.hostBoardCards.map {
                        CardTemplate.fromBoardDescriptor(it, currentMatch.baseVariantId)
                    }

                    // 0A. Responsive 2-Player Canvas GameBoard (Touch Drag-and-Drop Card Movement + Real-Time Firestore Sync)
                    item {
                        CanvasGameBoard(
                            uiProfile = uiProfile,
                            hostName = currentMatch.hostName,
                            opponentName = currentMatch.connectedClients.firstOrNull() ?: uiProfile.clientDisplayName,
                            hostLife = currentMatch.hostLife,
                            opponentLife = currentMatch.opponentLife,
                            hostUnits = hostUnits,
                            opponentUnits = opponentUnits,
                            handCards = playerHand,
                            turnNumber = currentMatch.turnNumber,
                            activePhase = currentMatch.activePhase,
                            syncedBoardState = syncedBoardState,
                            onDeployCardFromHand = { card, targetZone ->
                                onDeployCard(currentMatch, card)
                                onRecordMatchActionLog(
                                    currentMatch,
                                    "MOVE_HISTORY [Turn ${currentMatch.turnNumber} • ${currentMatch.activePhase}]: Player 1 (${currentMatch.hostName}) moved '${card.name}' to Field [$targetZone]"
                                )
                            },
                            onMoveCardBetweenZones = { card, fromZone, toZone ->
                                onRecordMatchActionLog(
                                    currentMatch,
                                    "MOVE_HISTORY [Turn ${currentMatch.turnNumber} • ${currentMatch.activePhase}]: Player 1 (${currentMatch.hostName}) moved '${card.name}' from [$fromZone] to Field [$toZone]"
                                )
                            },
                            onAttackOpponentZone = { attacker, targetZone, defender ->
                                onRecordMatchActionLog(
                                    currentMatch,
                                    "MSG_DAMAGE [Turn ${currentMatch.turnNumber} • BP]: '${attacker.name}' (${attacker.attack} ATK) attacked [$targetZone] (${defender?.name ?: "Direct"})"
                                )
                            },
                            onDrawCardRequest = {
                                onDrawCard(currentMatch.baseVariantId, currentMatch)
                            },
                            onBroadcastBoardState = { placements, eventType, eventSummary ->
                                onBroadcastBoardState(currentMatch, placements, eventType, eventSummary)
                            },
                            onTriggerPeerClientSyncMove = { placements ->
                                onTriggerPeerClientSyncMove(currentMatch, placements)
                            }
                        )
                    }

                    // 0A-1. Compose UI GameBoard Layout (Placeholders for Player's Hand, Deck & Active Zones + Visual Resource Counter)
                    item {
                        ComposeGameBoardLayout(
                            uiProfile = uiProfile,
                            engineState = gameEngineState,
                            handCards = playerHand,
                            hostUnits = hostUnits,
                            opponentUnits = opponentUnits,
                            activeCustomVariant = activeCustomVariant,
                            overrideAvailableResources = currentMatch.hostResources,
                            overrideMaxResources = currentMatch.hostMaxResources,
                            overrideOpponentResources = currentMatch.opponentResources,
                            onDrawFromDeckPlaceholder = {
                                onDrawCard(currentMatch.baseVariantId, currentMatch)
                            },
                            onDeployCardToActiveZone = { card, targetZone ->
                                onDeployCard(currentMatch, card)
                                onRecordMatchActionLog(
                                    currentMatch,
                                    "MOVE_HISTORY [Turn ${currentMatch.turnNumber} • ${currentMatch.activePhase}]: Player 1 (${currentMatch.hostName}) moved '${card.name}' from Hand to Active Zone [$targetZone]"
                                )
                            },
                            onQueueCardEffectFromZone = { card ->
                                onQueueCardEffect(card, null, currentMatch)
                            },
                            onStepPhase = {
                                onStepGameEnginePhase(currentMatch)
                            },
                            onSelectPhase = { phase ->
                                onSelectGameEnginePhase(phase, currentMatch)
                            },
                            onSelectClientPhaseCode = { code ->
                                onChangePhase(currentMatch, code)
                            },
                            onSpendResource = { amount ->
                                onAdjustResources(-amount, currentMatch)
                            },
                            onGainResource = { amount ->
                                onAdjustResources(amount, currentMatch)
                            },
                            onRefillResources = {
                                onRefillResources(currentMatch)
                            },
                            onExpandMaxResourceCap = {
                                onExpandMaxResources(currentMatch)
                            },
                            onSetResourceExact = { exact ->
                                onSetResourcesExact(exact, currentMatch)
                            }
                        )
                    }

                    // 0A-2. Core GameEngine Turn State Machine & LIFO Card Effect Resolution Console
                    item {
                        GameEngineTurnAndEffectPanel(
                            engineState = gameEngineState,
                            uiProfile = uiProfile,
                            handCards = playerHand,
                            onStepPhase = { onStepGameEnginePhase(currentMatch) },
                            onSelectPhase = { phase -> onSelectGameEnginePhase(phase, currentMatch) },
                            onQueueEffect = { card, effectType ->
                                onQueueCardEffect(card, effectType, currentMatch)
                            },
                            onResolveNextEffect = { onResolveNextEffect(currentMatch) },
                            onResolveAllEffects = { onResolveAllEffects(currentMatch) }
                        )
                    }

                    // 0B. D3.js Based Live Board State & Card Stack Visualizer
                    item {
                        D3BoardStateVisualizer(
                            uiProfile = uiProfile,
                            hostUnits = hostUnits,
                            opponentUnits = opponentUnits,
                            handCards = playerHand,
                            turnNumber = currentMatch.turnNumber,
                            activePhase = currentMatch.activePhase,
                            onQuickDeployFromVisualizer = {
                                onDrawCard(currentMatch.baseVariantId, currentMatch)
                            }
                        )
                    }

                    // 1. Opponent / Connected Client Zone (System-Specific Layout)
                    item {
                        ArenaPlayerBoardZone(
                            title = "PEER CLIENT: ${currentMatch.connectedClients.firstOrNull() ?: uiProfile.clientDisplayName}",
                            zoneSubtitle = uiProfile.fieldSlotsTopLabel,
                            lifeTotal = currentMatch.opponentLife,
                            lifeLabel = uiProfile.lifeLabel,
                            resources = currentMatch.opponentResources,
                            maxResources = currentMatch.hostMaxResources,
                            resourceName = uiProfile.resourceLabel,
                            uiProfile = uiProfile,
                            units = opponentUnits,
                            isOpponent = true
                        )
                    }

                    // 2. Turn & Interactive Client-System Phase Strip (Records Phase Changes in Real Time)
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = if (currentMatch.status == "FINISHED") {
                                                "MATCH FINISHED • WINNER: ${currentMatch.winnerName}"
                                            } else {
                                                "TURN ${currentMatch.turnNumber} • PHASE: ${currentMatch.activePhase} (${uiProfile.boardLayoutType})"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (currentMatch.status == "FINISHED") RadiantGold else MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Tap any Phase Pill below to switch phases & record in the live Sidebar Log",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Interactive Client-System Phase Strip Pills
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    uiProfile.phaseStripCodes.forEach { phaseCode ->
                                        val isCurrentPhase = phaseCode.equals(currentMatch.activePhase, ignoreCase = true) ||
                                            (currentMatch.activePhase == "MAIN" && (phaseCode == "MP1" || phaseCode == "MAIN-1" || phaseCode == "DEPLOY" || phaseCode == "MATERIALIZE"))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isCurrentPhase) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .clickable {
                                                    onChangePhase(currentMatch, phaseCode)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                                .testTag("arena_phase_chip_$phaseCode")
                                        ) {
                                            Text(
                                                text = phaseCode,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isCurrentPhase) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onDrawCard(currentMatch.baseVariantId, currentMatch) },
                                        enabled = currentMatch.status != "FINISHED",
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("draw_card_button")
                                    ) {
                                        Text("Draw (${activeBaseSpec.zones.first()})")
                                    }
                                    Button(
                                        onClick = { onExecuteCombatAndAdvanceTurn(currentMatch) },
                                        enabled = currentMatch.status != "FINISHED",
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .testTag("combat_end_turn_button"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Execute Combat and Sync Turn",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(uiProfile.combatVerb, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    // 3. Host Player Battlefield Zone (System-Specific Slots)
                    item {
                        ArenaPlayerBoardZone(
                            title = "COMMANDER: ${currentMatch.hostName} (YOU)",
                            zoneSubtitle = uiProfile.fieldSlotsBottomLabel,
                            lifeTotal = currentMatch.hostLife,
                            lifeLabel = uiProfile.lifeLabel,
                            resources = currentMatch.hostResources,
                            maxResources = currentMatch.hostMaxResources,
                            resourceName = uiProfile.resourceLabel,
                            uiProfile = uiProfile,
                            units = hostUnits,
                            isOpponent = false
                        )
                    }

                    // 4. Interactive Player Hand (Tap Card to Deploy/Summon)
                    item {
                        Text(
                            text = "Your Hand — Tap a Card to ${uiProfile.deployVerb} (${currentMatch.hostResources}/${currentMatch.hostMaxResources} ${uiProfile.resourceLabel})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(playerHand) { card ->
                                val canAfford = currentMatch.hostResources >= card.cost && currentMatch.status != "FINISHED"
                                HandPlayableCard(
                                    card = card,
                                    deployVerb = uiProfile.deployVerb,
                                    resourceName = currentMatch.resourceName,
                                    canAfford = canAfford,
                                    onDeploy = { onDeployCard(currentMatch, card) }
                                )
                            }
                        }
                    }

                    // 5. Scrollable Real-Time Gameplay Action Sidebar Log (Inline when undocked)
                    if (!isSidebarSplitDocked) {
                        item {
                            GameplayActionSidebarLog(
                                uiProfile = uiProfile,
                                rawActionLogs = effectiveActionLogs,
                                turnNumber = effectiveTurn,
                                activePhase = effectivePhase,
                                isDockedSidebar = false,
                                syncedBoardState = syncedBoardState,
                                onToggleDockedMode = { isSidebarSplitDocked = true },
                                onSelectPhaseFromSidebar = { phaseCode ->
                                    onChangePhase(currentMatch, phaseCode)
                                },
                                onRecordSyncedMoveFromSidebar = { moveLine ->
                                    onRecordMatchActionLog(currentMatch, moveLine)
                                    onBroadcastBoardState(
                                        currentMatch,
                                        syncedBoardState?.zonePlacements.orEmpty(),
                                        "MOVE_HISTORY",
                                        moveLine
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Right Column: Docked Scrollable Real-Time Gameplay Action Sidebar Log
            if (isSidebarSplitDocked) {
                GameplayActionSidebarLog(
                    uiProfile = uiProfile,
                    rawActionLogs = effectiveActionLogs,
                    turnNumber = effectiveTurn,
                    activePhase = effectivePhase,
                    isDockedSidebar = true,
                    syncedBoardState = syncedBoardState,
                    onToggleDockedMode = { isSidebarSplitDocked = false },
                    onSelectPhaseFromSidebar = { phaseCode ->
                        if (currentMatch != null) {
                            onChangePhase(currentMatch, phaseCode)
                        } else {
                            fallbackActionLogs.add(
                                "MSG_NEW_PHASE [Turn 1]: Shifted phase $fallbackPhase -> $phaseCode"
                            )
                            fallbackPhase = phaseCode
                        }
                    },
                    onRecordSyncedMoveFromSidebar = { moveLine ->
                        if (currentMatch != null) {
                            onRecordMatchActionLog(currentMatch, moveLine)
                            onBroadcastBoardState(
                                currentMatch,
                                syncedBoardState?.zonePlacements.orEmpty(),
                                "MOVE_HISTORY",
                                moveLine
                            )
                        } else {
                            fallbackActionLogs.add(moveLine)
                            onBroadcastBoardState(
                                null,
                                syncedBoardState?.zonePlacements.orEmpty(),
                                "MOVE_HISTORY",
                                moveLine
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                )
            }
        }
    }

    if (showNewMatchDialog) {
        NewMatchDialog(
            baseVariantName = activeBaseSpec.name,
            onDismiss = { showNewMatchDialog = false },
            onConfirm = { title ->
                onLaunchMatch(title)
                showNewMatchDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArenaPlayerBoardZone(
    title: String,
    zoneSubtitle: String,
    lifeTotal: Long,
    lifeLabel: String,
    resources: Long,
    maxResources: Long,
    resourceName: String,
    uiProfile: ClientSystemUiProfile,
    units: List<CardTemplate>,
    isOpponent: Boolean
) {
    val accent = if (isOpponent) CrimsonAlert else EmeraldReady
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = zoneSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Life Total",
                            tint = CrimsonAlert,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$lifeTotal $lifeLabel",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = resourceName,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "$resources/$maxResources $resourceName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Client-System Specific Zone Slots (MMZ 1..5 / EXP 1..6 / SANCTUM / GRID / RIFT)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                uiProfile.fieldSlotCodes.forEachIndexed { idx, slotCode ->
                    val unitInSlot = units.getOrNull(idx)
                    Box(
                        modifier = Modifier
                            .width(104.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (unitInSlot != null) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.background
                            )
                            .border(
                                width = 1.dp,
                                color = if (unitInSlot != null) RadiantGold.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = slotCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            if (unitInSlot != null) {
                                Text(
                                    text = unitInSlot.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${unitInSlot.attack}/${unitInSlot.defense}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RadiantGold,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    text = "Empty Zone",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandPlayableCard(
    card: CardTemplate,
    deployVerb: String,
    resourceName: String,
    canAfford: Boolean,
    onDeploy: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(165.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (canAfford) 1.5.dp else 1.dp,
                color = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = canAfford) { onDeploy() }
            .testTag("hand_card_${card.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${card.cost}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${card.attack}/${card.defense}",
                    style = MaterialTheme.typography.labelMedium,
                    color = RadiantGold,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = card.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = card.abilityText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (canAfford) "$deployVerb (${card.cost} $resourceName)" else "NEED ${card.cost} $resourceName",
                style = MaterialTheme.typography.labelSmall,
                color = if (canAfford) EmeraldReady else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun NewMatchDialog(
    baseVariantName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf("$baseVariantName Client Table") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Launch Multi-Client Arena") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Creates a live tabletop match locked to the active Base Variant ($baseVariantName) and bridges compatible connected clients.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Arena Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("arena_title_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title) },
                modifier = Modifier.testTag("confirm_launch_arena_button")
            ) {
                Text("Launch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameEngineTurnAndEffectPanel(
    engineState: GameEngineState,
    uiProfile: ClientSystemUiProfile,
    handCards: List<CardTemplate>,
    onStepPhase: () -> Unit,
    onSelectPhase: (TurnPhase) -> Unit,
    onQueueEffect: (CardTemplate, CardEffectType?) -> Unit,
    onResolveNextEffect: () -> Unit,
    onResolveAllEffects: () -> Unit
) {
    val accent = Color(uiProfile.accentHex)
    var selectedCardIdx by remember(handCards.size, engineState.baseVariantId) { mutableStateOf(0) }
    var selectedEffectType by remember(engineState.baseVariantId) { mutableStateOf<CardEffectType?>(null) }
    val activeCard = handCards.getOrNull(selectedCardIdx)
        ?: BuiltInTcgCatalog.cardsForVariant(engineState.baseVariantId).first()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .testTag("game_engine_turn_and_effect_panel"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CORE GAMEENGINE • TURN-BASED STATE MACHINE & EFFECT STACK",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Turn ${engineState.turnNumber} • ${engineState.currentPhase.displayName} (${engineState.clientPhaseCode}) • Priority: ${engineState.priorityHolder.label}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = onStepPhase,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("game_engine_step_phase_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Advance Phase",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Step -> ${engineState.currentPhase.nextPhase().code}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 5 Canonical Turn Phases Strip (DRAW -> STANDBY -> MAIN -> COMBAT -> END)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TurnPhase.entries.forEach { phase ->
                    val isCurrent = engineState.currentPhase == phase
                    FilterChip(
                        selected = isCurrent,
                        onClick = { onSelectPhase(phase) },
                        label = {
                            Text(
                                text = "${phase.code} (${phase.clientPhaseCodeFor(engineState.baseVariantId)})",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("game_engine_phase_${phase.code.lowercase()}")
                    )
                }
            }

            Text(
                text = engineState.currentPhase.phaseDescription,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Card Effect Resolution Builder (Queue onto LIFO Chain / Stack & Resolve)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CARD EFFECT RESOLUTION (LIFO CHAIN / STACK: ${engineState.effectStack.size} QUEUED)",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "P1: ${engineState.hostLife} ${uiProfile.lifeLabel} • P2: ${engineState.opponentLife} ${uiProfile.lifeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldReady,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Select Source Card from Hand
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        handCards.take(5).forEachIndexed { idx, card ->
                            FilterChip(
                                selected = selectedCardIdx == idx,
                                onClick = { selectedCardIdx = idx },
                                label = {
                                    Text(
                                        text = card.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag("engine_effect_card_chip_$idx")
                            )
                        }
                    }

                    // Select Effect Type Override (or Auto-Parse from Card Text)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedEffectType == null,
                            onClick = { selectedEffectType = null },
                            label = { Text("Auto-Parse Text", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("engine_effect_type_auto")
                        )
                        CardEffectType.entries.forEach { effectType ->
                            FilterChip(
                                selected = selectedEffectType == effectType,
                                onClick = { selectedEffectType = effectType },
                                label = {
                                    Text(
                                        text = effectType.displayTitle.substringBefore(" ("),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("engine_effect_type_${effectType.code.lowercase()}")
                            )
                        }
                    }

                    // Action Buttons: Queue Effect on Chain/Stack, Resolve Top (LIFO), Resolve All
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onQueueEffect(activeCard, selectedEffectType) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("game_engine_queue_effect_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Queue Card Effect on Chain/Stack",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Queue Effect (CL${engineState.effectStack.size + 1})",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onResolveNextEffect,
                            enabled = engineState.effectStack.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("game_engine_resolve_next_button")
                        ) {
                            Text(
                                text = "Resolve Top (${engineState.effectStack.size})",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = onResolveAllEffects,
                            enabled = engineState.effectStack.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("game_engine_resolve_all_button")
                        ) {
                            Text(
                                text = "Resolve All",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }

                    // Active LIFO Chain / Stack Queue Preview
                    if (engineState.effectStack.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Pending Chain / Stack (Resolves Last-In, First-Out):",
                                style = MaterialTheme.typography.labelSmall,
                                color = RadiantGold,
                                fontWeight = FontWeight.Bold
                            )
                            engineState.effectStack.asReversed().forEach { item ->
                                Text(
                                    text = "• [CL${item.chainLinkNumber} • SS${item.spellSpeed}] ${item.sourceCard.name}: ${item.effectType.displayTitle} -> ${item.targetDescription}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Recently Resolved Card Effects History
                    if (engineState.resolvedEffectsHistory.isNotEmpty()) {
                        val latestResolved = engineState.resolvedEffectsHistory.first()
                        Text(
                            text = "Last Resolved: ${latestResolved.summary}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldReady
                        )
                    }
                }
            }
        }
    }
}

