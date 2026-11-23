package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BaseVariantSpec
import com.example.data.BuiltInTcgCatalog
import com.example.data.ClientSystemUiProfile
import com.example.data.TcgVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RadiantGold

/**
 * Classification of the active ruleset's primary resource system so the visual counter
 * adapts its iconography, pip geometry (Mana Crystals vs Reactor Energy Cells vs Action Diamonds),
 * and secondary sub-counters.
 */
enum class RulesetResourceArchetype(
    val code: String,
    val badgeTitle: String,
    val unitNoun: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long
) {
    MANA_CRYSTALS(
        code = "MANA",
        badgeTitle = "ARCANE MANA POOL",
        unitNoun = "Mana Crystal",
        primaryColorHex = 0xFF29B6F6,
        secondaryColorHex = 0xFF00E676
    ),
    ENERGY_CELLS(
        code = "ENERGY",
        badgeTitle = "REACTOR ENERGY MATRIX",
        unitNoun = "Energy Cell",
        primaryColorHex = 0xFFB388FF,
        secondaryColorHex = 0xFF00E5FF
    ),
    ACTION_POINTS(
        code = "ACTION_POINTS",
        badgeTitle = "TACTICAL ACTION & SUMMON POINTS",
        unitNoun = "Action Point (AP)",
        primaryColorHex = 0xFF00E5FF,
        secondaryColorHex = 0xFFFFB300
    ),
    HYBRID_MANA_ACTION(
        code = "HYBRID_MANA_AP",
        badgeTitle = "HYBRID MANA & CHAIN ACTION POOL",
        unitNoun = "Hybrid Mana/AP",
        primaryColorHex = 0xFFFF4081,
        secondaryColorHex = 0xFFFFB300
    );

    companion object {
        fun fromRuleset(
            baseSpec: BaseVariantSpec,
            customVariant: TcgVariant? = null
        ): RulesetResourceArchetype {
            val effectiveName = (customVariant?.resourceName ?: baseSpec.resourceName).lowercase()
            return when {
                baseSpec.baseVariantId == "HYBRID_FORGE_EDOPRO_MIX" ||
                    (effectiveName.contains("mana") && effectiveName.contains("chain")) ->
                    HYBRID_MANA_ACTION
                effectiveName.contains("energy") ||
                    effectiveName.contains("flux") ||
                    baseSpec.baseVariantId == "CYBER_OVERDRIVE_V2" ||
                    baseSpec.baseVariantId == "CHRONOS_RIFT_V1" ->
                    ENERGY_CELLS
                effectiveName.contains("mana") ||
                    baseSpec.baseVariantId == "FORGE_MTG_BASE" ||
                    baseSpec.baseVariantId == "FORGE_CUSTOM_CLONE" ||
                    baseSpec.baseVariantId == "AETHER_MANA_V1" ->
                    MANA_CRYSTALS
                else ->
                    ACTION_POINTS
            }
        }
    }
}

/**
 * Visual Resource Counter component for the Game Board (`VisualResourceCounterBar`) that
 * dynamically tracks available **Energy**, **Mana**, or **Action Points (Summon/Chain)**
 * based on the currently loaded ruleset ([BaseVariantSpec] and optional [TcgVariant] override).
 */
@Composable
fun VisualResourceCounterBar(
    baseSpec: BaseVariantSpec,
    uiProfile: ClientSystemUiProfile,
    activeCustomVariant: TcgVariant? = null,
    hostAvailableResources: Long,
    hostMaxResources: Long,
    opponentAvailableResources: Long,
    opponentMaxResources: Long = hostMaxResources,
    normalSummonsRemaining: Int = 1,
    onSpendResource: (Long) -> Unit = {},
    onGainResource: (Long) -> Unit = {},
    onSetExactResourceCount: (Long) -> Unit = {},
    onRefillResources: () -> Unit = {},
    onExpandMaxResourceCap: () -> Unit = {},
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val archetype = remember(baseSpec.baseVariantId, activeCustomVariant?.id, activeCustomVariant?.resourceName) {
        RulesetResourceArchetype.fromRuleset(baseSpec, activeCustomVariant)
    }
    val effectiveResourceName = activeCustomVariant?.resourceName?.ifBlank { baseSpec.resourceName }
        ?: baseSpec.resourceName
    val rulesetCeilingCap = (activeCustomVariant?.maxResourcePool ?: baseSpec.maxResourcePool)
        .coerceIn(1L, 20L)
    val effectiveMaxPool = hostMaxResources.coerceIn(1L, rulesetCeilingCap)
    val effectiveAvailable = hostAvailableResources.coerceIn(0L, rulesetCeilingCap)

    val primaryColor = Color(archetype.primaryColorHex)
    val secondaryColor = Color(archetype.secondaryColorHex)
    val fillRatio by animateFloatAsState(
        targetValue = (effectiveAvailable.toFloat() / effectiveMaxPool.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "resourceFillRatio"
    )

    // Elemental / Sub-channel breakdown for Mana or Action Points
    val subChannelCounts = remember(baseSpec.baseVariantId) {
        mutableStateMapOf(
            "PRIMARY" to 2,
            "SECONDARY" to 1,
            "SPECIAL" to 1
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, primaryColor.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .testTag("visual_resource_counter_component"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0E1628),
                            ObsidianSurface
                        )
                    )
                )
                .padding(if (compactMode) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Header Row: Ruleset Resource Archetype + Live Numeric Readout (P1 vs P2)
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
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.18f))
                            .border(1.dp, primaryColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = archetypeIcon(archetype),
                            contentDescription = effectiveResourceName,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "${archetype.badgeTitle} • RULESET: ${(activeCustomVariant?.name ?: baseSpec.name).uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Tracking '$effectiveResourceName' (Ruleset Cap: $rulesetCeilingCap • Combat: ${activeCustomVariant?.combatRule ?: baseSpec.combatRule})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Large Tactile Numeric Readout Badge
                Surface(
                    color = ObsidianElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, primaryColor, RoundedCornerShape(10.dp))
                        .testTag("visual_resource_numeric_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$effectiveAvailable / $effectiveMaxPool",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (effectiveAvailable > 0L) EmeraldReady else CrimsonAlert,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = effectiveResourceName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Animated Canvas Segmented Resource Gauge Bar (Adapts to Ruleset Max Pool)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF070B14))
                    .border(1.dp, primaryColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .testTag("visual_resource_canvas_bar")
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                    val totalWidth = size.width
                    val totalHeight = size.height
                    val filledWidth = (totalWidth * fillRatio).coerceIn(0f, totalWidth)

                    // Glowing active resource fill
                    if (filledWidth > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.85f),
                                    secondaryColor.copy(alpha = 0.95f)
                                )
                            ),
                            topLeft = Offset.Zero,
                            size = Size(filledWidth, totalHeight),
                            cornerRadius = CornerRadius(12f, 12f)
                        )
                    }

                    // Segment dividers for each unit in effectiveMaxPool (and dimmed ticks up to rulesetCeilingCap)
                    val totalSegments = rulesetCeilingCap.toInt().coerceIn(1, 20)
                    val segmentWidth = totalWidth / totalSegments.toFloat()
                    for (idx in 1 until totalSegments) {
                        val xPos = idx * segmentWidth
                        val isWithinUnlockedPool = idx <= effectiveMaxPool
                        drawLine(
                            color = if (isWithinUnlockedPool) {
                                Color.White.copy(alpha = 0.35f)
                            } else {
                                Color.White.copy(alpha = 0.12f)
                            },
                            start = Offset(xPos, 0f),
                            end = Offset(xPos, totalHeight),
                            strokeWidth = if (idx == effectiveMaxPool.toInt()) 3f else 1.5f
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "P1 AVAILABLE: $effectiveAvailable/$effectiveMaxPool (MAX CAP $rulesetCeilingCap)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "P2 PEER: $opponentAvailableResources/${opponentMaxResources.coerceAtLeast(1L)} ${effectiveResourceName.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 3. Interactive Visual Resource Pips (Mana Crystals / Energy Cells / Action Point Diamonds)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("visual_resource_pips_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalPipsToRender = rulesetCeilingCap.toInt().coerceIn(1, 20)
                for (pipIndex in 0 until totalPipsToRender) {
                    val unitNumber = (pipIndex + 1).toLong()
                    val isUnlocked = unitNumber <= effectiveMaxPool
                    val isFilled = unitNumber <= effectiveAvailable

                    ResourceUnitPipItem(
                        pipIndex = pipIndex,
                        unitNumber = unitNumber.toInt(),
                        archetype = archetype,
                        isFilled = isFilled,
                        isUnlocked = isUnlocked,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        onClick = {
                            if (isFilled && unitNumber == effectiveAvailable) {
                                onSpendResource(1L)
                            } else {
                                onSetExactResourceCount(unitNumber)
                            }
                        }
                    )
                }
            }

            // 4. Ruleset-Specific Sub-Counters (Mana Color Pips / Action Point Breakdown / Reactor Overdrive)
            if (!compactMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (archetype) {
                        RulesetResourceArchetype.MANA_CRYSTALS,
                        RulesetResourceArchetype.HYBRID_MANA_ACTION -> {
                            uiProfile.mtgoManaPoolPips.forEachIndexed { idx, manaSymbol ->
                                FilterChip(
                                    selected = idx < effectiveAvailable,
                                    onClick = { onGainResource(1L) },
                                    label = {
                                        Text(
                                            text = "Mana $manaSymbol",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    },
                                    modifier = Modifier.testTag("visual_resource_mana_pip_$idx")
                                )
                            }
                        }

                        RulesetResourceArchetype.ACTION_POINTS -> {
                            FilterChip(
                                selected = normalSummonsRemaining > 0,
                                onClick = { onGainResource(1L) },
                                label = {
                                    Text(
                                        text = "Normal Summon AP: $normalSummonsRemaining/1",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                modifier = Modifier.testTag("visual_resource_ap_normal_summon")
                            )
                            FilterChip(
                                selected = effectiveAvailable >= 2L,
                                onClick = { onSpendResource(1L) },
                                label = {
                                    Text(
                                        text = "Special / Link AP: $effectiveAvailable",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                modifier = Modifier.testTag("visual_resource_ap_special_summon")
                            )
                            FilterChip(
                                selected = effectiveAvailable >= 1L,
                                onClick = { onSpendResource(1L) },
                                label = {
                                    Text(
                                        text = "Fast-Effect Chain AP: Ready",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                modifier = Modifier.testTag("visual_resource_ap_chain_point")
                            )
                        }

                        RulesetResourceArchetype.ENERGY_CELLS -> {
                            val pct = ((effectiveAvailable.toFloat() / rulesetCeilingCap.toFloat()) * 100).toInt()
                            FilterChip(
                                selected = effectiveAvailable > 0L,
                                onClick = { onGainResource(1L) },
                                label = {
                                    Text(
                                        text = "Reactor Charge: $pct% ($effectiveAvailable/$rulesetCeilingCap $effectiveResourceName)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                modifier = Modifier.testTag("visual_resource_energy_charge_chip")
                            )
                            FilterChip(
                                selected = effectiveAvailable >= effectiveMaxPool,
                                onClick = { onRefillResources() },
                                label = {
                                    Text(
                                        text = if (effectiveAvailable >= effectiveMaxPool) "OVERDRIVE READY" else "CHARGING CELLS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 5. Interactive Resource Counter Action Controls (Spend -1, Charge +1, Refill Pool, Expand Max Cap)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onSpendResource(1L) },
                    enabled = effectiveAvailable > 0L,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("visual_resource_spend_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Spend 1 $effectiveResourceName",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Spend 1",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = { onGainResource(1L) },
                    enabled = effectiveAvailable < rulesetCeilingCap,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("visual_resource_gain_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Gain 1 $effectiveResourceName",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "+1 ${effectiveResourceName.substringBefore("/")}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onRefillResources,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("visual_resource_refill_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refill $effectiveResourceName Pool",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Refill ($effectiveMaxPool)",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }

                if (effectiveMaxPool < rulesetCeilingCap) {
                    OutlinedButton(
                        onClick = onExpandMaxResourceCap,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(0.95f)
                            .testTag("visual_resource_expand_cap_button")
                    ) {
                        Text(
                            text = "Max +1 ($rulesetCeilingCap)",
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
private fun ResourceUnitPipItem(
    pipIndex: Int,
    unitNumber: Int,
    archetype: RulesetResourceArchetype,
    isFilled: Boolean,
    isUnlocked: Boolean,
    primaryColor: Color,
    secondaryColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isFilled -> primaryColor.copy(alpha = 0.28f)
            isUnlocked -> Color(0xFF0E1525)
            else -> Color(0xFF080C14)
        },
        label = "pipBgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFilled -> secondaryColor
            isUnlocked -> primaryColor.copy(alpha = 0.45f)
            else -> Color.White.copy(alpha = 0.14f)
        },
        label = "pipBorderColor"
    )

    val shape = when (archetype) {
        RulesetResourceArchetype.MANA_CRYSTALS -> CircleShape
        RulesetResourceArchetype.ENERGY_CELLS -> RoundedCornerShape(6.dp)
        RulesetResourceArchetype.ACTION_POINTS -> RoundedCornerShape(10.dp)
        RulesetResourceArchetype.HYBRID_MANA_ACTION -> RoundedCornerShape(50)
    }

    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 36.dp)
            .clip(shape)
            .background(bgColor)
            .border(width = if (isFilled) 1.5.dp else 1.dp, color = borderColor, shape = shape)
            .clickable { onClick() }
            .testTag("visual_resource_pip_$pipIndex"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2.2f
            if (isFilled) {
                when (archetype) {
                    RulesetResourceArchetype.MANA_CRYSTALS -> {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(secondaryColor, primaryColor)
                            ),
                            radius = radius,
                            center = center
                        )
                    }
                    RulesetResourceArchetype.ACTION_POINTS -> {
                        val diamond = Path().apply {
                            moveTo(center.x, center.y - radius)
                            lineTo(center.x + radius, center.y)
                            lineTo(center.x, center.y + radius)
                            lineTo(center.x - radius, center.y)
                            close()
                        }
                        drawPath(
                            path = diamond,
                            brush = Brush.linearGradient(listOf(primaryColor, secondaryColor))
                        )
                    }
                    RulesetResourceArchetype.ENERGY_CELLS -> {
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(secondaryColor, primaryColor)),
                            topLeft = Offset(center.x - radius * 0.8f, center.y - radius * 0.8f),
                            size = Size(radius * 1.6f, radius * 1.6f),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                    RulesetResourceArchetype.HYBRID_MANA_ACTION -> {
                        drawCircle(
                            color = primaryColor,
                            radius = radius,
                            center = center
                        )
                        drawCircle(
                            color = secondaryColor,
                            radius = radius * 0.55f,
                            center = center,
                            style = Stroke(width = 2.5f)
                        )
                    }
                }
            }
        }
        Text(
            text = "$unitNumber",
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isFilled -> Color.White
                isUnlocked -> primaryColor
                else -> Color.White.copy(alpha = 0.35f)
            },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun archetypeIcon(archetype: RulesetResourceArchetype): ImageVector {
    return when (archetype) {
        RulesetResourceArchetype.MANA_CRYSTALS -> Icons.Default.AutoAwesome
        RulesetResourceArchetype.ENERGY_CELLS -> Icons.Default.ElectricBolt
        RulesetResourceArchetype.ACTION_POINTS -> Icons.Default.Bolt
        RulesetResourceArchetype.HYBRID_MANA_ACTION -> Icons.Default.Stars
    }
}
