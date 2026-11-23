package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BaseVariantSpec
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardEffectType
import com.example.data.CardTemplate
import com.example.data.ClientNode
import com.example.data.ClientSystemUiProfile
import com.example.data.GameEngine
import com.example.data.GameEngineState
import com.example.data.MatchSession
import com.example.data.SerializedGameBoardState
import com.example.data.TcgVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RadiantGold

private enum class HudDebugSection(val label: String) {
    RULESET("Ruleset Params"),
    GAME_VARS("Game-State Vars"),
    CARD_LOGIC("Card Logic Debug"),
    STACK_TRACE("Chain / Trace")
}

/**
 * In-Game HUD Telemetry Panel (`InGameDebugHudPanel`) pinned directly inside the live Tabletop Arena.
 *
 * Displays:
 * 1. **Active Ruleset Parameters** (reading dynamically from [BaseVariantSpec] and optional [TcgVariant] override,
 *    including protocol hash, GitHub source lock status, starting life/hand, max resource cap, combat rule,
 *    and digit precision).
 * 2. **Live Game-State Variables** (turn, canonical + client phase, active player & priority owner,
 *    LP/Life 21-digit registers, resource pool, board unit sums, normal summon counter, stack depth,
 *    and Firestore sync revision).
 * 3. **Custom Card Logic Debugger** (select any hand/field card to inspect inferred [CardEffectType],
 *    coding variables extracted via [BigInt21Engine.extractCodingVariables21], 21-digit function evaluation,
 *    and 1-tap dry-run / queue & resolve controls).
 * 4. **LIFO Chain / Stack & Engine Trace** (real-time stack items, resolved effects history, and engine logs).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InGameDebugHudPanel(
    baseSpec: BaseVariantSpec,
    activeCustomVariant: TcgVariant?,
    uiProfile: ClientSystemUiProfile,
    activeClientNode: ClientNode?,
    engineState: GameEngineState,
    currentMatch: MatchSession?,
    syncedBoardState: SerializedGameBoardState?,
    handCards: List<CardTemplate>,
    hostUnits: List<CardTemplate>,
    opponentUnits: List<CardTemplate>,
    onStepPhase: () -> Unit = {},
    onQueueCardEffect: (CardTemplate, CardEffectType?) -> Unit = { _, _ -> },
    onResolveNextEffect: () -> Unit = {},
    onResolveAllEffects: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    var selectedSection by rememberSaveable { mutableStateOf(HudDebugSection.GAME_VARS) }
    var selectedCardIdx by rememberSaveable(baseSpec.baseVariantId) { mutableIntStateOf(0) }
    var selectedEffectOverrideIdx by rememberSaveable(baseSpec.baseVariantId) { mutableIntStateOf(0) }

    val customOverride = activeCustomVariant?.takeIf { it.baseVariantId == baseSpec.baseVariantId && !it.isBaseStandard }
    val isLockedGitHubBase = BuiltInTcgCatalog.isLockedGitHubBaseClient(baseSpec.baseVariantId) && customOverride == null

    val effectiveName = customOverride?.name ?: baseSpec.name
    val effectiveResourceName = customOverride?.resourceName ?: baseSpec.resourceName
    val effectiveStartingLife = customOverride?.startingLife ?: baseSpec.startingLife
    val effectiveStartingLp21 = customOverride?.startingLp21 ?: baseSpec.startingLife.toString()
    val effectiveStartingHand = customOverride?.startingHandSize ?: baseSpec.startingHandSize
    val effectiveMaxRes = customOverride?.maxResourcePool ?: baseSpec.maxResourcePool
    val effectiveCombatRule = customOverride?.combatRule ?: baseSpec.combatRule
    val effectivePrecision = customOverride?.maxDigitPrecision ?: baseSpec.maxDigitPrecision.toLong()
    val effectiveProtocolHash = customOverride?.protocolHash ?: baseSpec.protocolHash

    val liveTurn = currentMatch?.turnNumber ?: engineState.turnNumber
    val livePhaseCode = currentMatch?.activePhase ?: engineState.currentPhase.code
    val liveHostLife = currentMatch?.hostLife ?: engineState.hostLife
    val liveOppLife = currentMatch?.opponentLife ?: engineState.opponentLife
    val liveHostRes = currentMatch?.hostResources ?: engineState.hostResources
    val liveHostMaxRes = currentMatch?.hostMaxResources ?: engineState.hostMaxResources
    val liveOppRes = currentMatch?.opponentResources ?: engineState.opponentResources
    val liveRevision = syncedBoardState?.revisionNumber ?: 0

    val allInspectableCards = remember(handCards, hostUnits, opponentUnits) {
        (handCards + hostUnits + opponentUnits).distinctBy { "${it.id}_${it.name}" }
    }
    val inspectedCard = allInspectableCards.getOrNull(selectedCardIdx.coerceIn(0, (allInspectableCards.size - 1).coerceAtLeast(0)))

    val totalHostAtk = remember(hostUnits) { hostUnits.sumOf { it.attack.toLong() } }
    val totalOppAtk = remember(opponentUnits) { opponentUnits.sumOf { it.attack.toLong() } }
    val netLifeDelta = liveHostLife - liveOppLife

    val accentColor = Color(uiProfile.accentHex)
    val hudBorderColor by animateColorAsState(
        targetValue = if (engineState.effectStack.isNotEmpty()) RadiantGold else accentColor,
        label = "hudBorderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, hudBorderColor.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .testTag("in_game_debug_hud_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A111F),
                            ObsidianSurface
                        )
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Live Telemetry HUD Header Bar + Compact Always-Visible Ticker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
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
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(hudBorderColor.copy(alpha = 0.18f))
                            .border(1.dp, hudBorderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "In-Game Debug HUD",
                            tint = hudBorderColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "IN-GAME DEBUG HUD",
                                style = MaterialTheme.typography.labelMedium,
                                color = hudBorderColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = if (isLockedGitHubBase) EmeraldReady.copy(alpha = 0.16f) else RadiantGold.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    text = if (isLockedGitHubBase) "LOCKED BASE" else "${effectivePrecision}d CUSTOM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLockedGitHubBase) EmeraldReady else RadiantGold,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "T$liveTurn • $livePhaseCode • LP $liveHostLife/$liveOppLife • $effectiveResourceName $liveHostRes/$liveHostMaxRes • Stack ${engineState.effectStack.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("in_game_hud_telemetry_ticker")
                        )
                    }
                }

                OutlinedButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("in_game_hud_toggle_button")
                ) {
                    Text(
                        text = if (isExpanded) "Minimize" else "Expand",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Minimize HUD" else "Expand HUD",
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 2. Section Switcher Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HudDebugSection.entries.forEach { section ->
                            val selected = selectedSection == section
                            FilterChip(
                                selected = selected,
                                onClick = { selectedSection = section },
                                label = {
                                    Text(
                                        text = if (section == HudDebugSection.STACK_TRACE) {
                                            "${section.label} (${engineState.effectStack.size})"
                                        } else {
                                            section.label
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag("hud_tab_${section.name}")
                            )
                        }
                    }

                    // 3. Section Body
                    when (selectedSection) {
                        HudDebugSection.RULESET -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianElevated)
                                    .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                                    .testTag("hud_ruleset_params_box"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isLockedGitHubBase) Icons.Default.Lock else Icons.Default.Gavel,
                                        contentDescription = null,
                                        tint = if (isLockedGitHubBase) EmeraldReady else RadiantGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "ACTIVE RULESET PARAMETERS ($effectiveName)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HudParamChip("baseVariantId", baseSpec.baseVariantId, accentColor)
                                    HudParamChip("protocolVersion", baseSpec.protocolVersion, EmeraldReady)
                                    HudParamChip("startingLife", "$effectiveStartingLife", EmeraldReady)
                                    HudParamChip("startingLp21", effectiveStartingLp21, RadiantGold)
                                    HudParamChip("resourceName", effectiveResourceName, accentColor)
                                    HudParamChip("maxResourcePool", "$effectiveMaxRes", accentColor)
                                    HudParamChip("startingHandSize", "$effectiveStartingHand", MaterialTheme.colorScheme.secondary)
                                    HudParamChip("combatRule", effectiveCombatRule, CrimsonAlert)
                                    HudParamChip("digitPrecision", "${effectivePrecision}-bit/d", RadiantGold)
                                    HudParamChip("uiLayout", uiProfile.boardLayoutType, accentColor)
                                }

                                Text(
                                    text = "protocolHash = $effectiveProtocolHash\n" +
                                        "zones = [${baseSpec.zones.joinToString(", ")}]\n" +
                                        "fieldSlots = [${uiProfile.fieldSlotCodes.joinToString(", ")}]\n" +
                                        "sourceRepo = ${baseSpec.sourceRepoUrl}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HudDebugSection.GAME_VARS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianElevated)
                                    .border(1.dp, EmeraldReady.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                                    .testTag("hud_game_vars_box"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LIVE ENGINE GAME-STATE VARIABLES",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldReady,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedButton(
                                        onClick = onStepPhase,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("hud_step_phase_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Step Phase",
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Step Phase", style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HudParamChip("turnNumber", "$liveTurn", RadiantGold)
                                    HudParamChip("canonicalPhase", engineState.currentPhase.name, accentColor)
                                    HudParamChip("clientPhase", livePhaseCode, accentColor)
                                    HudParamChip("activePlayer", engineState.activePlayer.name, EmeraldReady)
                                    HudParamChip("priority", engineState.priorityHolder.name, RadiantGold)
                                    HudParamChip("hostLife", "$liveHostLife", EmeraldReady)
                                    HudParamChip("oppLife", "$liveOppLife", CrimsonAlert)
                                    HudParamChip("netLpDelta", "${if (netLifeDelta >= 0) "+" else ""}$netLifeDelta", if (netLifeDelta >= 0) EmeraldReady else CrimsonAlert)
                                    HudParamChip("hostRes", "$liveHostRes/$liveHostMaxRes", accentColor)
                                    HudParamChip("oppRes", "$liveOppRes/$liveHostMaxRes", CrimsonAlert)
                                    HudParamChip("normalSummons", "${engineState.normalSummonsRemaining}", RadiantGold)
                                    HudParamChip("combatDone", "${engineState.hasConductedCombatThisTurn}", MaterialTheme.colorScheme.secondary)
                                    HudParamChip("handCount", "${handCards.size}", accentColor)
                                    HudParamChip("p1UnitsATK", "${hostUnits.size} ($totalHostAtk)", EmeraldReady)
                                    HudParamChip("p2UnitsATK", "${opponentUnits.size} ($totalOppAtk)", CrimsonAlert)
                                    HudParamChip("stackDepth", "${engineState.effectStack.size}", RadiantGold)
                                    HudParamChip("firestoreRev", "#$liveRevision", EmeraldReady)
                                }

                                // 21-Digit Live State Registers
                                Text(
                                    text = "hostLife21 = ${BigInt21Engine.formatWithCommas(engineState.hostLife21)}\n" +
                                        "oppLife21  = ${BigInt21Engine.formatWithCommas(engineState.opponentLife21)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = RadiantGold
                                )
                            }
                        }

                        HudDebugSection.CARD_LOGIC -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianElevated)
                                    .border(1.dp, RadiantGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                                    .testTag("hud_card_logic_debug_box"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = null,
                                        tint = RadiantGold,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "CUSTOM CARD LOGIC & 21-DIGIT SCRIPT WATCHER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RadiantGold,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Inspectable Card Selector Row
                                if (allInspectableCards.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        allInspectableCards.forEachIndexed { idx, card ->
                                            FilterChip(
                                                selected = idx == selectedCardIdx,
                                                onClick = { selectedCardIdx = idx },
                                                label = {
                                                    Text(
                                                        text = card.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1
                                                    )
                                                },
                                                modifier = Modifier.testTag("hud_inspect_card_$idx")
                                            )
                                        }
                                    }
                                }

                                // Card Inspection + Script Vars + Live Formula Eval
                                if (inspectedCard != null) {
                                    val inferredEffect = remember(inspectedCard.id, inspectedCard.abilityText) {
                                        GameEngine.inferCardEffect(inspectedCard)
                                    }
                                    val effectEntries = CardEffectType.entries
                                    val chosenOverride = if (selectedEffectOverrideIdx == 0) {
                                        null
                                    } else {
                                        effectEntries.getOrNull(selectedEffectOverrideIdx - 1)
                                    }
                                    val activeEvalType = chosenOverride ?: inferredEffect

                                    val scriptExprSource = activeClientNode?.customCodeVarExpr21
                                        ?: activeCustomVariant?.customVarInput21?.let {
                                            "s.var_21 = \"$it\"; s.code_str_21 = \"${inspectedCard.passcode}\""
                                        }
                                        ?: BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
                                    val extractedVars = remember(scriptExprSource, inspectedCard.passcode) {
                                        BigInt21Engine.extractCodingVariables21(scriptExprSource)
                                    }
                                    val activeFn = activeClientNode?.customFuncName
                                        ?: activeCustomVariant?.customFuncName
                                        ?: "ADD_21"
                                    val varA = activeClientNode?.customVarInput21 ?: inspectedCard.attack21
                                    val argB = activeClientNode?.customFuncInput21 ?: inspectedCard.passcode
                                    val fnEvalResult = remember(activeFn, varA, argB) {
                                        BigInt21Engine.evaluateFunction21(activeFn, varA, argB)
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF070C17))
                                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${inspectedCard.name} • [${inspectedCard.cardType}]",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "passcode/code_str = c${inspectedCard.passcode} (${inspectedCard.passcode.length}d) | cost = ${inspectedCard.cost} | affordable = ${liveHostRes >= inspectedCard.cost}\n" +
                                                "ATK/DEF (64b) = ${inspectedCard.attack}/${inspectedCard.defense} | ATK21 = ${inspectedCard.attack21} | DEF21 = ${inspectedCard.defense21}\n" +
                                                "inferredEffect = ${inferredEffect.code} (SS${inferredEffect.defaultSpellSpeed})\n" +
                                                "ability = ${inspectedCard.abilityText}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        HorizontalDivider(color = Color(0xFF1E293B))
                                        Text(
                                            text = "ACTIVE SCRIPT REGISTERS & 21-DIGIT FUNCTION EVAL:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldReady,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            extractedVars.forEach { (varName, varValue) ->
                                                HudParamChip(varName, varValue, RadiantGold)
                                            }
                                            HudParamChip("$activeFn(A,B)", fnEvalResult, EmeraldReady)
                                        }
                                    }

                                    // Effect Override Chips + Queue / Resolve Controls for Dry-Run Debugging
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedEffectOverrideIdx == 0,
                                            onClick = { selectedEffectOverrideIdx = 0 },
                                            label = { Text("Auto (${inferredEffect.code})", style = MaterialTheme.typography.labelSmall) }
                                        )
                                        effectEntries.forEachIndexed { idx, effectType ->
                                            FilterChip(
                                                selected = selectedEffectOverrideIdx == idx + 1,
                                                onClick = { selectedEffectOverrideIdx = idx + 1 },
                                                label = { Text(effectType.code, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onQueueCardEffect(inspectedCard, chosenOverride) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = RadiantGold,
                                                contentColor = Color(0xFF1A1300)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .testTag("hud_queue_debug_effect_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BugReport,
                                                contentDescription = "Test Queue Card Logic",
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Test ${activeEvalType.code}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = onResolveNextEffect,
                                            enabled = engineState.effectStack.isNotEmpty(),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("hud_resolve_next_effect_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Resolve Top Link",
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Resolve (${engineState.effectStack.size})",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HudDebugSection.STACK_TRACE -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianElevated)
                                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                                    .testTag("hud_stack_trace_box"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Functions,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "LIFO CHAIN / STACK (${engineState.effectStack.size} PENDING)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (engineState.effectStack.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = onResolveAllEffects,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("hud_resolve_all_effects_button")
                                        ) {
                                            Text("Resolve All", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                if (engineState.effectStack.isEmpty()) {
                                    Text(
                                        text = "Stack empty — queue any card effect in 'Card Logic Debug' or the GameBoard to inspect LIFO resolution.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    engineState.effectStack.asReversed().forEach { item ->
                                        Text(
                                            text = "CL${item.chainLinkNumber} [SS${item.spellSpeed}] ${item.sourceCard.name} -> ${item.effectType.code} (${item.effectValue21}) | ${item.targetDescription}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = RadiantGold
                                        )
                                    }
                                }

                                if (engineState.resolvedEffectsHistory.isNotEmpty()) {
                                    HorizontalDivider(color = Color(0xFF1E293B))
                                    Text(
                                        text = "RECENT RESOLVED EFFECTS:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldReady,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    engineState.resolvedEffectsHistory.take(4).forEach { rec ->
                                        Text(
                                            text = "#${rec.resolutionOrder} [${rec.phase.code} CL${rec.chainLinkNumber}] ${rec.summary}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudParamChip(
    key: String,
    value: String,
    accentColor: Color
) {
    Surface(
        color = Color(0xFF070C17),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$key:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
