package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BaseVariantSpec
import com.example.data.BuiltInTcgCatalog
import com.example.data.ClientSystemUiProfile
import com.example.data.EnginePlayerId
import com.example.data.GameEngineState
import com.example.data.TcgVariant
import com.example.data.TurnPhase
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RadiantGold

/**
 * Metadata descriptor mapping a canonical [TurnPhase] (`DRAW`, `STANDBY`, `MAIN`, `COMBAT`, `END`)
 * to ruleset-aware nomenclature, colors, icons, and automatic engine behaviors.
 */
data class RulesetPhaseVisualSpec(
    val phase: TurnPhase,
    val shortTitle: String,
    val rulesetCode: String,
    val accentColor: Color,
    val icon: ImageVector,
    val allowedSpeedLabel: String,
    val rulesetRuleSummary: String
)

/**
 * Turn Phase Indicator Component (`TurnPhaseIndicatorBar`) that visually displays the current
 * game phase (**Draw**, **Standby**, **Main**, **Combat**, **End**) and automatically updates
 * based on the live [GameEngineState] and loaded ruleset ([BaseVariantSpec] / [TcgVariant]).
 *
 * Features:
 * - **Animated Canvas Phase Progression Track**: Visualizes completed phases, the active glowing
 *   phase node, and upcoming phases in the turn cycle.
 * - **Interactive Phase Stepper Cards**: Displays Draw, Standby, Main, Combat, and End phase cards
 *   with ruleset-specific protocol codes (e.g., `DP/SP/MP1/BP/EP` for ProjectIgnis EDOPro vs
 *   `UNTAP/UPKEEP/MAIN/COMBAT/END` for Card-Forge MTG) and 1-tap phase switching.
 * - **Client System Sub-Phase Strip**: Synchronizes with `uiProfile.phaseStripCodes` so sub-phases
 *   like `MP2` or `MAIN-2` reflect accurately alongside the core engine state.
 * - **Automatic Ruleset Phase Mechanics Readout**: Live status banner detailing priority ownership,
 *   permitted Spell Speed / Stack window, remaining Normal Summons, and automatic phase triggers.
 */
@Composable
fun TurnPhaseIndicatorBar(
    engineState: GameEngineState,
    baseSpec: BaseVariantSpec,
    uiProfile: ClientSystemUiProfile,
    activeCustomVariant: TcgVariant? = null,
    overrideTurnNumber: Long? = null,
    overrideClientPhaseCode: String? = null,
    onSelectPhase: (TurnPhase) -> Unit = {},
    onSelectClientPhaseCode: (String) -> Unit = {},
    onStepNextPhase: () -> Unit = {},
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val allPhases = TurnPhase.entries
    val currentPhase = engineState.currentPhase
    val activePhaseIndex = allPhases.indexOf(currentPhase).coerceAtLeast(0)
    val effectiveTurn = overrideTurnNumber ?: engineState.turnNumber
    val effectiveClientCode = overrideClientPhaseCode ?: engineState.clientPhaseCode
    val isEdoPro = BuiltInTcgCatalog.isEdoProVariant(baseSpec.baseVariantId)
    val isForgeMtg = BuiltInTcgCatalog.isForgeVariant(baseSpec.baseVariantId)
    val effectiveResourceName = activeCustomVariant?.resourceName?.ifBlank { baseSpec.resourceName }
        ?: baseSpec.resourceName
    val effectiveCombatRule = activeCustomVariant?.combatRule?.ifBlank { baseSpec.combatRule }
        ?: baseSpec.combatRule

    val phaseSpecs = remember(
        baseSpec.baseVariantId,
        activeCustomVariant?.id,
        effectiveResourceName,
        effectiveCombatRule,
        engineState.normalSummonsRemaining,
        engineState.effectStack.size
    ) {
        buildRulesetPhaseSpecs(
            baseSpec = baseSpec,
            isEdoPro = isEdoPro,
            isForgeMtg = isForgeMtg,
            resourceName = effectiveResourceName,
            combatRule = effectiveCombatRule,
            normalSummonsRemaining = engineState.normalSummonsRemaining,
            stackCount = engineState.effectStack.size
        )
    }

    val currentPhaseSpec = phaseSpecs.getOrElse(activePhaseIndex) { phaseSpecs.first() }
    val nextPhase = currentPhase.nextPhase()
    val animatedProgress by animateFloatAsState(
        targetValue = if (allPhases.size > 1) {
            activePhaseIndex.toFloat() / (allPhases.size - 1).toFloat()
        } else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "turnPhaseProgress"
    )
    val headerBorderColor by animateColorAsState(
        targetValue = currentPhaseSpec.accentColor,
        animationSpec = tween(durationMillis = 280),
        label = "phaseHeaderBorderColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, headerBorderColor.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .testTag("turn_phase_indicator_component"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            currentPhaseSpec.accentColor.copy(alpha = 0.12f),
                            Color(0xFF0B1120),
                            ObsidianSurface
                        )
                    )
                )
                .padding(if (compactMode) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Top Header Bar: Turn Counter, Active Phase Badge, Active Player Priority & Step Controls
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(currentPhaseSpec.accentColor.copy(alpha = 0.2f))
                            .border(1.5.dp, currentPhaseSpec.accentColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = currentPhaseSpec.icon,
                            contentDescription = currentPhase.displayName,
                            tint = currentPhaseSpec.accentColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TURN $effectiveTurn • ${currentPhase.displayName.uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = currentPhaseSpec.accentColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("turn_phase_active_title")
                            )
                            Surface(
                                color = currentPhaseSpec.accentColor.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.border(
                                    1.dp,
                                    currentPhaseSpec.accentColor.copy(alpha = 0.65f),
                                    RoundedCornerShape(6.dp)
                                )
                            ) {
                                Text(
                                    text = currentPhaseSpec.rulesetCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = currentPhaseSpec.accentColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Active: ${engineState.activePlayer.label} • Priority: ${engineState.priorityHolder.label} • Window: ${currentPhaseSpec.allowedSpeedLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Next Phase Action Button
                Button(
                    onClick = onStepNextPhase,
                    enabled = engineState.status != "FINISHED",
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = currentPhaseSpec.accentColor,
                        contentColor = Color(0xFF060A12)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("turn_phase_next_button")
                ) {
                    Text(
                        text = if (currentPhase == TurnPhase.END) {
                            "Next Turn (${nextPhase.code})"
                        } else {
                            "Next: ${nextPhase.shortLabel()}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Advance to Next Phase",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 2. Animated Canvas Phase Timeline Bar (Visualizes Draw -> Standby -> Main -> Combat -> End)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .padding(horizontal = 18.dp)
                    .testTag("turn_phase_canvas_timeline")
            ) {
                val totalNodes = phaseSpecs.size
                if (totalNodes < 2) return@Canvas
                val centerY = size.height / 2f
                val stepWidth = size.width / (totalNodes - 1).toFloat()

                // Background track line
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Active animated fill line
                val fillEndX = (size.width * animatedProgress).coerceIn(0f, size.width)
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            EmeraldReady,
                            currentPhaseSpec.accentColor,
                            RadiantGold
                        )
                    ),
                    start = Offset(0f, centerY),
                    end = Offset(fillEndX, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Draw each phase node along the timeline
                phaseSpecs.forEachIndexed { index, spec ->
                    val nodeX = index * stepWidth
                    val isCompleted = index < activePhaseIndex
                    val isCurrent = index == activePhaseIndex

                    if (isCurrent) {
                        // Outer pulsing halo ring for active phase
                        drawCircle(
                            color = spec.accentColor.copy(alpha = 0.28f),
                            radius = 11.dp.toPx(),
                            center = Offset(nodeX, centerY)
                        )
                        drawCircle(
                            color = spec.accentColor,
                            radius = 7.dp.toPx(),
                            center = Offset(nodeX, centerY)
                        )
                        drawCircle(
                            color = Color(0xFF060A12),
                            radius = 3.dp.toPx(),
                            center = Offset(nodeX, centerY)
                        )
                    } else if (isCompleted) {
                        drawCircle(
                            color = EmeraldReady,
                            radius = 5.5.dp.toPx(),
                            center = Offset(nodeX, centerY)
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFF0F172A),
                            radius = 5.5.dp.toPx(),
                            center = Offset(nodeX, centerY)
                        )
                        drawCircle(
                            color = Color(0xFF475569),
                            radius = 5.5.dp.toPx(),
                            center = Offset(nodeX, centerY),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            // 3. Interactive Canonical Phase Nodes Row (Draw • Standby • Main • Combat • End)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("turn_phase_nodes_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                phaseSpecs.forEachIndexed { idx, spec ->
                    val isCurrent = spec.phase == currentPhase
                    val isCompleted = idx < activePhaseIndex
                    val isNext = spec.phase == nextPhase

                    CanonicalPhaseStepCard(
                        spec = spec,
                        stepIndex = idx + 1,
                        isCurrent = isCurrent,
                        isCompleted = isCompleted,
                        isNext = isNext,
                        onClick = { onSelectPhase(spec.phase) },
                        modifier = Modifier.testTag("turn_phase_node_${spec.phase.name}")
                    )
                }
            }

            // 4. Ruleset Client System Sub-Phase Strip + Fast-Forward End Turn Action
            if (!compactMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiProfile.hudBadgeLabel} Sub-Phases:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        uiProfile.phaseStripCodes.forEach { code ->
                            val mappedCanonical = TurnPhase.fromRawPhaseCode(code)
                            val isSubPhaseSelected = code.equals(effectiveClientCode, ignoreCase = true) ||
                                (mappedCanonical == currentPhase && effectiveClientCode.isBlank())
                            Surface(
                                color = if (isSubPhaseSelected) {
                                    currentPhaseSpec.accentColor.copy(alpha = 0.24f)
                                } else {
                                    ObsidianElevated
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = if (isSubPhaseSelected) {
                                            currentPhaseSpec.accentColor
                                        } else {
                                            Color(0xFF334155)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onSelectPhase(mappedCanonical)
                                        onSelectClientPhaseCode(code)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("turn_subphase_chip_$code")
                            ) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSubPhaseSelected) {
                                        currentPhaseSpec.accentColor
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSubPhaseSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { onSelectPhase(TurnPhase.END) },
                        enabled = engineState.status != "FINISHED" && currentPhase != TurnPhase.END,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("turn_phase_pass_turn_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Jump to End Phase",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("End Phase", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 5. Automatic Ruleset Phase Mechanics & Priority Readout Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianElevated)
                    .border(
                        width = 1.dp,
                        color = currentPhaseSpec.accentColor.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("turn_phase_mechanics_banner"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ENGINE STATE RULE [${baseSpec.baseVariantId} • ${currentPhase.code}]:",
                        style = MaterialTheme.typography.labelSmall,
                        color = currentPhaseSpec.accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentPhaseSpec.rulesetRuleSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Stack / Chain Link & Normal Summon Live Badges
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isEdoPro) {
                            "Chain: ${engineState.effectStack.size} CL"
                        } else {
                            "Stack: ${engineState.effectStack.size}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (engineState.effectStack.isNotEmpty()) RadiantGold else EmeraldReady,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isEdoPro) {
                            "Normal Summon: ${engineState.normalSummonsRemaining}/1"
                        } else {
                            "Land/Deploy: ${engineState.normalSummonsRemaining}/1"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun CanonicalPhaseStepCard(
    spec: RulesetPhaseVisualSpec,
    stepIndex: Int,
    isCurrent: Boolean,
    isCompleted: Boolean,
    isNext: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            isCurrent -> spec.accentColor.copy(alpha = 0.22f)
            isCompleted -> EmeraldReady.copy(alpha = 0.10f)
            else -> Color(0xFF0B111E)
        },
        label = "phaseNodeContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isCurrent -> spec.accentColor
            isNext -> RadiantGold.copy(alpha = 0.65f)
            isCompleted -> EmeraldReady.copy(alpha = 0.5f)
            else -> Color(0xFF26334D)
        },
        label = "phaseNodeBorder"
    )

    Column(
        modifier = modifier
            .width(114.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = spec.shortTitle,
                    tint = when {
                        isCurrent -> spec.accentColor
                        isCompleted -> EmeraldReady
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "$stepIndex.${spec.shortTitle}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isCurrent -> spec.accentColor
                        isCompleted -> EmeraldReady
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // State badge pill (ACTIVE / DONE / NEXT)
            val statusLabel = when {
                isCurrent -> "ACTIVE"
                isCompleted -> "DONE"
                isNext -> "NEXT"
                else -> spec.phase.code
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isCurrent -> spec.accentColor
                    isCompleted -> EmeraldReady
                    isNext -> RadiantGold
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = spec.rulesetCode,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = spec.allowedSpeedLabel,
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun TurnPhase.shortLabel(): String = when (this) {
    TurnPhase.DRAW -> "Draw"
    TurnPhase.STANDBY -> "Standby"
    TurnPhase.MAIN -> "Main"
    TurnPhase.COMBAT -> "Combat"
    TurnPhase.END -> "End"
}

private fun buildRulesetPhaseSpecs(
    baseSpec: BaseVariantSpec,
    isEdoPro: Boolean,
    isForgeMtg: Boolean,
    resourceName: String,
    combatRule: String,
    normalSummonsRemaining: Int,
    stackCount: Int
): List<RulesetPhaseVisualSpec> {
    return listOf(
        RulesetPhaseVisualSpec(
            phase = TurnPhase.DRAW,
            shortTitle = "DRAW",
            rulesetCode = when {
                isForgeMtg -> "UNTAP / DRAW"
                baseSpec.baseVariantId == "CYBER_OVERDRIVE_V2" -> "BOOT / DRAW"
                else -> "DP (Draw)"
            },
            accentColor = Color(0xFF00E5FF),
            icon = Icons.Default.Layers,
            allowedSpeedLabel = if (isEdoPro) "Spell Speed 2+" else "Instant Window",
            rulesetRuleSummary = "Automatically draws 1 card from Main Deck, increments max $resourceName pool (up to ${baseSpec.maxResourcePool}), and refreshes turn counters."
        ),
        RulesetPhaseVisualSpec(
            phase = TurnPhase.STANDBY,
            shortTitle = "STANDBY",
            rulesetCode = when {
                isForgeMtg -> "UPKEEP"
                baseSpec.baseVariantId == "CHRONOS_RIFT_V1" -> "TIME SHIFT"
                else -> "SP (Standby)"
            },
            accentColor = Color(0xFFB388FF),
            icon = Icons.Default.HourglassTop,
            allowedSpeedLabel = if (isEdoPro) "Trigger / SS2+" else "Upkeep Triggers",
            rulesetRuleSummary = "Resolves start-of-turn maintenance costs, lingering phase triggers, and fast-effect responses ($stackCount queued on stack)."
        ),
        RulesetPhaseVisualSpec(
            phase = TurnPhase.MAIN,
            shortTitle = "MAIN",
            rulesetCode = when {
                isForgeMtg -> "MAIN-1 / MAIN-2"
                baseSpec.baseVariantId == "CYBER_OVERDRIVE_V2" -> "DEPLOY"
                else -> "MP1 / MP2"
            },
            accentColor = EmeraldReady,
            icon = Icons.Default.AutoAwesome,
            allowedSpeedLabel = if (isEdoPro) "SS1 / Summon Open" else "Sorcery + Land Open",
            rulesetRuleSummary = "Primary deployment window: Summon/Cast units from Hand ($normalSummonsRemaining Normal Summon/Land drop ready), set support cards, and activate effects."
        ),
        RulesetPhaseVisualSpec(
            phase = TurnPhase.COMBAT,
            shortTitle = "COMBAT",
            rulesetCode = when {
                isForgeMtg -> "COMBAT (Declare)"
                baseSpec.baseVariantId == "CYBER_OVERDRIVE_V2" -> "BREACH"
                else -> "BP (Battle)"
            },
            accentColor = CrimsonAlert,
            icon = Icons.Default.Bolt,
            allowedSpeedLabel = if (isEdoPro) "Damage Step / SS2+" else "Combat Tricks",
            rulesetRuleSummary = "Executes battle calculation under ruleset '$combatRule' — attackers strike opponent units or inflict direct LP/Life breach damage."
        ),
        RulesetPhaseVisualSpec(
            phase = TurnPhase.END,
            shortTitle = "END",
            rulesetCode = when {
                isForgeMtg -> "END / CLEANUP"
                baseSpec.baseVariantId == "CHRONOS_RIFT_V1" -> "REWIND / EP"
                else -> "EP (End Phase)"
            },
            accentColor = RadiantGold,
            icon = Icons.Default.Flag,
            allowedSpeedLabel = if (isEdoPro) "End Triggers / SS2+" else "End Step Window",
            rulesetRuleSummary = "Resolves end-of-turn cleanup, verifies hand size limit (${baseSpec.startingHandSize}+), and advances turn counter to Turn -> DP."
        )
    )
}
