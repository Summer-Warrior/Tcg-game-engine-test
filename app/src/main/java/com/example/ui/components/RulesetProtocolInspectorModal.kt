package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BaseVariantSpec
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.ClientNode
import com.example.data.ClientSystemUiProfile
import com.example.data.GameEngineState
import com.example.data.SerializedGameBoardState
import com.example.data.TcgVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RadiantGold
import com.example.viewmodel.HandshakeDiagnostic
import kotlin.math.roundToInt

private enum class InspectorDebugTab(val code: String, val label: String) {
    RULESET("RULESET", "Engine Ruleset"),
    PROTOCOL("PROTOCOL", "Protocol Status"),
    STATE_MACHINE("STATE_MACHINE", "GameEngine State"),
    REGISTERS_SYNC("REGISTERS_SYNC", "21-Digit & Sync")
}

/**
 * Floating Debug Inspector Trigger Pill & Draggable / Expandable Floating Modal that reads live
 * from the active engine ruleset definitions ([BaseVariantSpec], [TcgVariant], [ClientSystemUiProfile]),
 * current variant protocol status ([HandshakeDiagnostic], GitHub source lock state, connected clients),
 * and live [GameEngineState] / [SerializedGameBoardState] for real-time debugging.
 */
@Composable
fun FloatingRulesetProtocolInspectorHost(
    isOpen: Boolean,
    onToggleOpen: (Boolean) -> Unit,
    activeBaseSpec: BaseVariantSpec,
    activeCustomVariant: TcgVariant?,
    uiProfile: ClientSystemUiProfile,
    activeClientNode: ClientNode?,
    connectedClients: List<ClientNode>,
    lastHandshakeDiagnostic: HandshakeDiagnostic?,
    gameEngineState: GameEngineState,
    syncedBoardState: SerializedGameBoardState?,
    onSelectBaseVariant: (String) -> Unit = {},
    onResetToBaseStandard: () -> Unit = {},
    onStepGameEnginePhase: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accentColor = Color(uiProfile.accentHex)
    val isUnmodifiedBase = activeCustomVariant == null || activeCustomVariant.isBaseStandard
    val isLockedGitHubBase = BuiltInTcgCatalog.isLockedGitHubBaseClient(activeBaseSpec.baseVariantId)
    var showFullDialogModal by rememberSaveable { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier) {
        // 1. Persistent Floating Debug Trigger Pill (Bottom-End Overlay)
        Surface(
            color = ObsidianSurface.copy(alpha = 0.94f),
            shape = RoundedCornerShape(50),
            tonalElevation = 8.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp)
                .border(
                    width = 1.5.dp,
                    color = if (isOpen) RadiantGold else accentColor,
                    shape = RoundedCornerShape(50)
                )
                .clickable { onToggleOpen(!isOpen) }
                .testTag("floating_inspector_trigger_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (isUnmodifiedBase) EmeraldReady else RadiantGold,
                            shape = CircleShape
                        )
                )
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Open Ruleset & Protocol Debug Inspector",
                    tint = if (isOpen) RadiantGold else accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isOpen) {
                        "HIDE INSPECTOR"
                    } else {
                        "INSPECTOR • ${activeBaseSpec.baseVariantId} (v${activeBaseSpec.protocolVersion})"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Draggable Floating Inspector HUD Modal Window
        AnimatedVisibility(
            visible = isOpen && !showFullDialogModal,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 12.dp, end = 12.dp, bottom = 58.dp, top = 12.dp)
                .offset { IntOffset(dragOffsetX.roundToInt(), dragOffsetY.roundToInt()) }
        ) {
            RulesetProtocolInspectorCardContent(
                activeBaseSpec = activeBaseSpec,
                activeCustomVariant = activeCustomVariant,
                uiProfile = uiProfile,
                activeClientNode = activeClientNode,
                connectedClients = connectedClients,
                lastHandshakeDiagnostic = lastHandshakeDiagnostic,
                gameEngineState = gameEngineState,
                syncedBoardState = syncedBoardState,
                isLockedGitHubBase = isLockedGitHubBase,
                isUnmodifiedBase = isUnmodifiedBase,
                isExpandedDialog = false,
                onExpandOrCollapseDialog = { showFullDialogModal = true },
                onClose = { onToggleOpen(false) },
                onDragDelta = { dx, dy ->
                    dragOffsetX = (dragOffsetX + dx).coerceIn(-260f, 120f)
                    dragOffsetY = (dragOffsetY + dy).coerceIn(-420f, 120f)
                },
                onSelectBaseVariant = onSelectBaseVariant,
                onResetToBaseStandard = onResetToBaseStandard,
                onStepGameEnginePhase = onStepGameEnginePhase
            )
        }

        // 3. Full-Screen / Expanded Modal Dialog Option
        if (isOpen && showFullDialogModal) {
            Dialog(
                onDismissRequest = { showFullDialogModal = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RulesetProtocolInspectorCardContent(
                        activeBaseSpec = activeBaseSpec,
                        activeCustomVariant = activeCustomVariant,
                        uiProfile = uiProfile,
                        activeClientNode = activeClientNode,
                        connectedClients = connectedClients,
                        lastHandshakeDiagnostic = lastHandshakeDiagnostic,
                        gameEngineState = gameEngineState,
                        syncedBoardState = syncedBoardState,
                        isLockedGitHubBase = isLockedGitHubBase,
                        isUnmodifiedBase = isUnmodifiedBase,
                        isExpandedDialog = true,
                        onExpandOrCollapseDialog = { showFullDialogModal = false },
                        onClose = {
                            showFullDialogModal = false
                            onToggleOpen(false)
                        },
                        onDragDelta = { _, _ -> },
                        onSelectBaseVariant = onSelectBaseVariant,
                        onResetToBaseStandard = onResetToBaseStandard,
                        onStepGameEnginePhase = onStepGameEnginePhase
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesetProtocolInspectorCardContent(
    activeBaseSpec: BaseVariantSpec,
    activeCustomVariant: TcgVariant?,
    uiProfile: ClientSystemUiProfile,
    activeClientNode: ClientNode?,
    connectedClients: List<ClientNode>,
    lastHandshakeDiagnostic: HandshakeDiagnostic?,
    gameEngineState: GameEngineState,
    syncedBoardState: SerializedGameBoardState?,
    isLockedGitHubBase: Boolean,
    isUnmodifiedBase: Boolean,
    isExpandedDialog: Boolean,
    onExpandOrCollapseDialog: () -> Unit,
    onClose: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onSelectBaseVariant: (String) -> Unit,
    onResetToBaseStandard: () -> Unit,
    onStepGameEnginePhase: () -> Unit
) {
    val accentColor = Color(uiProfile.accentHex)
    var selectedTab by rememberSaveable { mutableStateOf(InspectorDebugTab.RULESET) }

    val effectiveResourceName = activeCustomVariant?.resourceName ?: activeBaseSpec.resourceName
    val effectiveStartingLife = activeCustomVariant?.startingLife ?: activeBaseSpec.startingLife
    val effectiveStartingHand = activeCustomVariant?.startingHandSize ?: activeBaseSpec.startingHandSize
    val effectiveMaxResources = activeCustomVariant?.maxResourcePool ?: activeBaseSpec.maxResourcePool
    val effectiveCombatRule = activeCustomVariant?.combatRule ?: activeBaseSpec.combatRule
    val effectivePrecision = activeCustomVariant?.maxDigitPrecision ?: activeBaseSpec.maxDigitPrecision.toLong()
    val effectiveProtocolHash = if (activeCustomVariant != null && !activeCustomVariant.isBaseStandard) {
        "${activeCustomVariant.baseVariantId}:${activeCustomVariant.startingLife}:${activeCustomVariant.startingHandSize}:${activeCustomVariant.maxResourcePool}:${activeCustomVariant.combatRule}:21d"
    } else {
        activeBaseSpec.protocolHash
    }

    Card(
        modifier = Modifier
            .widthIn(max = if (isExpandedDialog) 740.dp else 500.dp)
            .fillMaxWidth()
            .border(1.5.dp, accentColor, RoundedCornerShape(20.dp))
            .testTag("floating_ruleset_inspector_modal"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0E1526),
                            ObsidianSurface
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Draggable Floating Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(isExpandedDialog) {
                        if (!isExpandedDialog) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.x, dragAmount.y)
                            }
                        }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isExpandedDialog) Icons.Default.BugReport else Icons.Default.DragIndicator,
                        contentDescription = "Drag Floating Inspector",
                        tint = RadiantGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "FLOATING ENGINE RULESET & PROTOCOL INSPECTOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${activeBaseSpec.name} • Hash: $effectiveProtocolHash",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExpandOrCollapseDialog,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("inspector_toggle_modal_size_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = if (isExpandedDialog) "Dock Floating Overlay" else "Expand Modal",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("inspector_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Inspector Modal",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Status Badges Strip (Protocol Status, Base Standard vs Custom Override, GitHub Source Lock)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InspectorStatusChip(
                    label = if (isUnmodifiedBase) "PROTOCOL: BASE_STANDARD_OK" else "PROTOCOL: CUSTOM_OVERRIDE",
                    color = if (isUnmodifiedBase) EmeraldReady else RadiantGold
                )
                InspectorStatusChip(
                    label = if (isLockedGitHubBase) {
                        "REPO LOCKED: ${BuiltInTcgCatalog.exactGitHubRepoUrl(activeBaseSpec.baseVariantId).substringAfter("github.com/")}"
                    } else {
                        "CLONE/MIX: ${effectivePrecision}-DIGIT ENGINE"
                    },
                    color = if (isLockedGitHubBase) EmeraldReady else accentColor
                )
                InspectorStatusChip(
                    label = "CLIENTS: ${connectedClients.size} CONNECTED",
                    color = accentColor
                )
                InspectorStatusChip(
                    label = "TURN ${gameEngineState.turnNumber} • ${gameEngineState.currentPhase.code}",
                    color = RadiantGold
                )
            }

            // Inspector Debug Section Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InspectorDebugTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("inspector_tab_${tab.code.lowercase()}")
                    )
                }
            }

            // Scrollable Debug Telemetry Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isExpandedDialog) 460.dp else 290.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianElevated)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("inspector_body_content"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedTab) {
                    InspectorDebugTab.RULESET -> {
                        Text(
                            text = "LIVE ENGINE RULESET DEFINITIONS (${activeBaseSpec.baseVariantId})",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontWeight = FontWeight.Bold
                        )
                        InspectorKeyValueRow("Ruleset Name", activeCustomVariant?.name ?: activeBaseSpec.name)
                        InspectorKeyValueRow("Engine Core Runtime", activeBaseSpec.engineCoreLabel)
                        InspectorKeyValueRow("Starting Life / LP", "$effectiveStartingLife ${uiProfile.lifeLabel} (21d: ${activeCustomVariant?.startingLp21 ?: gameEngineState.hostLife21})")
                        InspectorKeyValueRow("Starting Hand Size", "$effectiveStartingHand Cards")
                        InspectorKeyValueRow("Resource System", "$effectiveResourceName (Max Pool: $effectiveMaxResources)")
                        InspectorKeyValueRow("Combat Resolution Rule", effectiveCombatRule)
                        InspectorKeyValueRow("Board Layout Profile", "${uiProfile.boardLayoutType} • ${uiProfile.deployVerb} / ${uiProfile.combatVerb}")
                        InspectorKeyValueRow("Active Main Zone Codes", uiProfile.fieldSlotCodes.joinToString(", "))
                        InspectorKeyValueRow("Support / Spell / Land Codes", uiProfile.spellOrSupportSlotCodes.joinToString(", "))
                        InspectorKeyValueRow("Duel Links Speed Duel Zones", uiProfile.speedDuelSlotCodes.joinToString(", "))
                        InspectorKeyValueRow("Canonical Turn Phases", "DRAW -> STANDBY -> MAIN -> COMBAT -> END (${uiProfile.phaseStripCodes.joinToString(" / ")})")
                        InspectorKeyValueRow("Canonical Ruleset Zones (${activeBaseSpec.zones.size})", activeBaseSpec.zones.joinToString(" • "))

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "INTEGRATED 5-TCG CLIENT ASPECTS IN RULESET:",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        InspectorKeyValueRow("Duel Links Skill", "${uiProfile.duelLinksSkillName} — ${uiProfile.duelLinksSkillEffect}")
                        InspectorKeyValueRow("Master Duel Chain & Mate", "Chain [${uiProfile.masterDuelChainModes.joinToString("/")}] • Mate '${uiProfile.masterDuelMateName}' (${uiProfile.masterDuelFoilFinish})")
                        InspectorKeyValueRow("Cross Duel 3-Lane & Ace", "${uiProfile.crossDuelLanes.joinToString(" | ")} • ${uiProfile.crossDuelAceSkills.joinToString(", ")}")
                        InspectorKeyValueRow("MTGO Clock & F-Keys", "${uiProfile.mtgoChessClockDefault} • ${uiProfile.mtgoPriorityShortcuts.joinToString(" | ")}")
                        InspectorKeyValueRow("MTG Arena Controls", "${uiProfile.mtgaFeatures.joinToString(" • ")}")
                    }

                    InspectorDebugTab.PROTOCOL -> {
                        Text(
                            text = "VARIANT PROTOCOL STATUS & HANDSHAKE DIAGNOSTICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontWeight = FontWeight.Bold
                        )
                        InspectorKeyValueRow("Active Base Variant ID", activeBaseSpec.baseVariantId)
                        InspectorKeyValueRow("Protocol Version", "v${activeBaseSpec.protocolVersion}")
                        InspectorKeyValueRow("Base Protocol Hash", activeBaseSpec.protocolHash)
                        InspectorKeyValueRow("Effective Runtime Hash", effectiveProtocolHash)
                        InspectorKeyValueRow(
                            "Source Repository URL",
                            if (isLockedGitHubBase) {
                                BuiltInTcgCatalog.exactGitHubRepoUrl(activeBaseSpec.baseVariantId)
                            } else {
                                activeBaseSpec.sourceRepoUrl
                            }
                        )
                        InspectorKeyValueRow(
                            "Immutability Lock Policy",
                            if (isLockedGitHubBase) {
                                "STRICT LOCK: Immutable against user/agent edits; updates permitted from upstream GitHub repository only"
                            } else {
                                "UNLOCKED CUSTOM CLONE / HYBRID: Supports 21-digit custom variables, functions & code strings"
                            }
                        )
                        InspectorKeyValueRow(
                            "Active Client Node",
                            activeClientNode?.let { "${it.clientName} (${it.connectionStatus} • ${it.latencyMs}ms)" }
                                ?: "Built-in ${uiProfile.clientDisplayName}"
                        )

                        lastHandshakeDiagnostic?.let { diag ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (diag.isCompatible) EmeraldReady.copy(alpha = 0.14f)
                                        else CrimsonAlert.copy(alpha = 0.16f)
                                    )
                                    .border(
                                        1.dp,
                                        if (diag.isCompatible) EmeraldReady else CrimsonAlert,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "LAST CLIENT HANDSHAKE: ${if (diag.isCompatible) "COMPATIBLE" else "MISMATCH"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (diag.isCompatible) EmeraldReady else CrimsonAlert,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Expected Hash: ${diag.expectedHash}\nActive Hash:   ${diag.activeHash}\nDiagnostic:    ${diag.message}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "QUICK SWITCH PROTOCOL VARIANT (DEBUG):",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BuiltInTcgCatalog.baseVariants.forEach { spec ->
                                FilterChip(
                                    selected = spec.baseVariantId == activeBaseSpec.baseVariantId,
                                    onClick = { onSelectBaseVariant(spec.baseVariantId) },
                                    label = {
                                        Text(
                                            text = spec.baseVariantId,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    modifier = Modifier.testTag("inspector_switch_variant_${spec.baseVariantId}")
                                )
                            }
                        }
                    }

                    InspectorDebugTab.STATE_MACHINE -> {
                        Text(
                            text = "LIVE GAMEENGINE TURN STATE MACHINE & LIFO STACK",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontWeight = FontWeight.Bold
                        )
                        InspectorKeyValueRow("Turn Number & Status", "Turn ${gameEngineState.turnNumber} • ${gameEngineState.status}")
                        InspectorKeyValueRow("Canonical Phase", "${gameEngineState.currentPhase.code} (${gameEngineState.currentPhase.displayName})")
                        InspectorKeyValueRow("Client Phase Code", gameEngineState.clientPhaseCode)
                        InspectorKeyValueRow("Active Turn Player", gameEngineState.activePlayer.label)
                        InspectorKeyValueRow("Priority Holder", gameEngineState.priorityHolder.label)
                        InspectorKeyValueRow("Player 1 (Host) Life", "${gameEngineState.hostLife} ${uiProfile.lifeLabel} (21d: ${gameEngineState.hostLife21})")
                        InspectorKeyValueRow("Player 2 (Peer) Life", "${gameEngineState.opponentLife} ${uiProfile.lifeLabel} (21d: ${gameEngineState.opponentLife21})")
                        InspectorKeyValueRow("Player 1 Resources", "${gameEngineState.hostResources}/${gameEngineState.hostMaxResources} ${uiProfile.resourceLabel}")
                        InspectorKeyValueRow("Field Units Count", "P1: ${gameEngineState.hostFieldUnits.size} unit(s) • P2: ${gameEngineState.opponentFieldUnits.size} unit(s)")
                        InspectorKeyValueRow("Pending Effect Stack", "${gameEngineState.effectStack.size} queued Chain Link(s) • ${gameEngineState.resolvedEffectsHistory.size} resolved")

                        if (gameEngineState.effectStack.isNotEmpty()) {
                            Text(
                                text = "QUEUED CHAIN / STACK ITEMS (LIFO):",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Bold
                            )
                            gameEngineState.effectStack.asReversed().forEach { item ->
                                Text(
                                    text = "• [CL${item.chainLinkNumber} • SS${item.spellSpeed}] ${item.sourceCard.name} -> ${item.effectType.code} (${item.targetDescription})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    InspectorDebugTab.REGISTERS_SYNC -> {
                        Text(
                            text = "21-DIGIT REGISTERS & FIRESTORE BOARD SYNC PAYLOAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontWeight = FontWeight.Bold
                        )
                        val codeStr21 = activeCustomVariant?.customCodeString21
                            ?: activeClientNode?.customCodeString21
                            ?: BigInt21Engine.DEFAULT_CODE_STRING_21
                        val codeVarExpr21 = activeCustomVariant?.customCodeVarExpr21
                            ?: activeClientNode?.customCodeVarExpr21
                            ?: BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
                        val varA21 = activeCustomVariant?.customVarInput21
                            ?: activeClientNode?.customVarInput21
                            ?: "999999999999999999999"
                        val funcRes21 = activeCustomVariant?.customFuncResult21
                            ?: activeClientNode?.customFuncResult21
                            ?: "999999999999999999999"

                        InspectorKeyValueRow("Max Digit Precision", "$effectivePrecision digits (Max: ${BigInt21Engine.MAX_21_DIGIT_STRING})")
                        InspectorKeyValueRow("Code String (21d)", "c$codeStr21 (${codeStr21.length}/21 digits)")
                        InspectorKeyValueRow("Code Variable Expression", codeVarExpr21)
                        InspectorKeyValueRow("Register Var A (21d)", varA21)
                        InspectorKeyValueRow("Last Function Result (21d)", funcRes21)

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "REAL-TIME FIRESTORE GAMEBOARD SYNC TELEMETRY:",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldReady,
                            fontWeight = FontWeight.Bold
                        )
                        if (syncedBoardState != null) {
                            InspectorKeyValueRow("Match ID & Revision", "${syncedBoardState.matchId} • Rev #${syncedBoardState.revisionNumber}")
                            InspectorKeyValueRow("Last Broadcast Client", syncedBoardState.lastBroadcastClientName)
                            InspectorKeyValueRow("Last Sync Event", "[${syncedBoardState.lastEventType}] ${syncedBoardState.lastEventSummary}")
                            InspectorKeyValueRow("Occupied Synced Zones", "${syncedBoardState.zonePlacements.size} zone(s) serialized")
                        } else {
                            InspectorKeyValueRow("Firestore Sync Status", "Standby — Drag or deploy any card on the GameBoard to broadcast Rev #1")
                        }
                    }
                }
            }

            // Bottom Quick Debug Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onStepGameEnginePhase,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inspector_step_phase_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Step Turn Phase",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Step Phase (${gameEngineState.currentPhase.nextPhase().code})",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isUnmodifiedBase) {
                    Button(
                        onClick = onResetToBaseStandard,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inspector_reset_base_standard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Lock to Base Standard",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Lock Base Standard",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorStatusChip(
    label: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InspectorKeyValueRow(
    key: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0A0F1C))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = key.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
