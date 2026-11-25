package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CardEffectStackItem
import com.example.data.CardOrigin
import com.example.data.TurnPhase
import com.example.ui.theme.CardOriginBadge
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.rememberChamferedDuelShape
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class BossSummonCutInEvent(
    val id: Long = System.currentTimeMillis(),
    val cardName: String,
    val cardType: String,
    val atkText: String,
    val defText: String,
    val origin: CardOrigin = CardOrigin.CANON_ORIGINAL,
    val linkedCanonCardId: String = "",
    val accentColor: Color = RadiantGold
)

data class PhaseSweepBannerEvent(
    val id: Long = System.currentTimeMillis(),
    val phaseTitle: String,
    val phaseCode: String,
    val turnNumber: Long
)

data class AttackImpactVfxEvent(
    val id: Long = System.currentTimeMillis(),
    val attackerName: String,
    val targetLabel: String,
    val damageCallout: String
)

/**
 * Compact illuminated Duel Disk Phase Strip (`DP -> SP -> MP1 -> BP -> MP2 -> EP`)
 * mounted cleanly at the edge of the unified playmat.
 */
@Composable
fun IlluminatedDuelDiskPhaseStrip(
    activePhase: TurnPhase,
    accentColor: Color = ElectricCyan,
    onSelectPhase: (TurnPhase) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        Triple("DP", TurnPhase.DRAW, "Draw Phase"),
        Triple("SP", TurnPhase.STANDBY, "Standby Phase"),
        Triple("MP1", TurnPhase.MAIN, "Main Phase 1"),
        Triple("BP", TurnPhase.COMBAT, "Battle Phase"),
        Triple("MP2", TurnPhase.MAIN, "Main Phase 2"),
        Triple("EP", TurnPhase.END, "End Phase")
    )
    val pillShape = rememberChamferedDuelShape(6.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF070C17))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("duel_disk_phase_strip"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEachIndexed { idx, (code, phase, _) ->
            val selected = (phase == activePhase) && !(code == "MP2" && activePhase == TurnPhase.MAIN)
            val glowColor = when {
                code == "BP" && selected -> CrimsonAlert
                selected -> accentColor
                else -> Color(0xFF334155)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(pillShape)
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(
                                listOf(glowColor.copy(alpha = 0.40f), glowColor.copy(alpha = 0.18f))
                            )
                        } else {
                            Brush.horizontalGradient(listOf(Color(0xFF101828), Color(0xFF0C1322)))
                        }
                    )
                    .border(
                        width = if (selected) 1.4.dp else 0.8.dp,
                        color = glowColor.copy(alpha = if (selected) 0.95f else 0.45f),
                        shape = pillShape
                    )
                    .clickable { onSelectPhase(phase) }
                    .testTag("phase_strip_${code.lowercase()}_$idx")
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                    color = if (selected) Color.White else Color(0xFF94A3B8)
                )
            }
        }
    }
}

/**
 * Pure Compose Canvas VFX overlay layered cleanly over the single unified playmat:
 * - Animated Chain Link Spiral (`CL1 -> CL2 -> CL3...`) while effects are queued on the LIFO stack.
 * - Full-width anime/Master Duel Phase Sweep Banner.
 * - Manga/Anime Boss Summon diagonal cut-in slash (1.1s, tap-to-skip).
 * - Attack Declaration jagged plasma beam + spark burst + floating damage callout.
 */
@Composable
fun DuelVfxOverlay(
    effectStack: List<CardEffectStackItem>,
    bossCutIn: BossSummonCutInEvent?,
    phaseBanner: PhaseSweepBannerEvent?,
    attackImpact: AttackImpactVfxEvent?,
    onDismissBossCutIn: () -> Unit,
    onDismissPhaseBanner: () -> Unit,
    onDismissAttackImpact: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(bossCutIn?.id) {
        if (bossCutIn != null) {
            delay(1150L)
            onDismissBossCutIn()
        }
    }
    LaunchedEffect(phaseBanner?.id) {
        if (phaseBanner != null) {
            delay(900L)
            onDismissPhaseBanner()
        }
    }
    LaunchedEffect(attackImpact?.id) {
        if (attackImpact != null) {
            delay(950L)
            onDismissAttackImpact()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Live Animated Chain Link Spiral (CL1 -> CL2 -> CL3...) when LIFO stack is non-empty
        if (effectStack.isNotEmpty()) {
            ChainLinkSpiralOverlay(
                effectStack = effectStack,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            )
        }

        // 2. Attack Declaration Plasma Beam & Clash Impact
        if (attackImpact != null) {
            AttackPlasmaBeamCanvas(
                event = attackImpact,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onDismissAttackImpact() }
            )
        }

        // 3. Full-Width Phase Sweep Banner
        AnimatedVisibility(
            visible = phaseBanner != null,
            enter = slideInHorizontally(initialOffsetX = { -it / 2 }) + fadeIn(tween(140)),
            exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
        ) {
            phaseBanner?.let { banner ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color(0xDD061326),
                                    ElectricCyan.copy(alpha = 0.34f),
                                    Color(0xDD061326),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.dp, ElectricCyan.copy(alpha = 0.7f))
                        .clickable { onDismissPhaseBanner() }
                        .padding(vertical = 10.dp)
                        .testTag("vfx_phase_sweep_banner")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TURN ${banner.turnNumber} • ${banner.phaseCode.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = RadiantGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = banner.phaseTitle.uppercase(),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
        }

        // 4. Manga / Anime Boss Summon Diagonal Cut-In Slash (1.1s, tap-to-skip)
        AnimatedVisibility(
            visible = bossCutIn != null,
            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(tween(130)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(160)),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
        ) {
            bossCutIn?.let { cut ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .clickable { onDismissBossCutIn() }
                        .testTag("vfx_boss_summon_cutin")
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // Angled diagonal manga slash ribbon
                        val slash = Path().apply {
                            moveTo(0f, h * 0.16f)
                            lineTo(w, 0f)
                            lineTo(w, h * 0.84f)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(
                            path = slash,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color(0xF0070D1D),
                                    cut.accentColor.copy(alpha = 0.42f),
                                    Color(0xF00B1329)
                                )
                            )
                        )
                        drawPath(slash, color = cut.accentColor, style = Stroke(width = 3.2f))
                        // Radial manga speed-lines
                        for (i in 0 until 14) {
                            val y = h * (i / 14f)
                            drawLine(
                                color = Color.White.copy(alpha = if (i % 2 == 0) 0.25f else 0.12f),
                                start = Offset(0f, y),
                                end = Offset(w * 0.34f, y - 8f),
                                strokeWidth = if (i % 3 == 0) 2.5f else 1.1f
                            )
                            drawLine(
                                color = cut.accentColor.copy(alpha = 0.28f),
                                start = Offset(w * 0.66f, y + 6f),
                                end = Offset(w, y - 4f),
                                strokeWidth = 1.6f
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SUMMON CUT-IN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    color = RadiantGold
                                )
                                CardOriginBadge(origin = cut.origin, linkedCanonCardId = cut.linkedCanonCardId)
                            }
                            Text(
                                text = cut.cardName.uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                fontStyle = FontStyle.Italic,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${cut.cardType} • TAP TO SKIP",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFBAE6FD)
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "ATK ${cut.atkText}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                color = RadiantGold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "DEF ${cut.defText}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChainLinkSpiralOverlay(
    effectStack: List<CardEffectStackItem>,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "chainSpiral")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "chainSpin"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "chainPulse"
    )

    Column(
        modifier = modifier
            .background(Color(0xCC061022), rememberChamferedDuelShape(8.dp))
            .border(1.2.dp, RadiantGold.copy(alpha = 0.85f), rememberChamferedDuelShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("vfx_chain_link_spiral"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = size.minDimension * 0.44f
                drawCircle(
                    color = ElectricCyan.copy(alpha = 0.7f * pulse),
                    radius = outerR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.5f)
                )
                drawCircle(
                    color = RadiantGold.copy(alpha = 0.85f),
                    radius = outerR * 0.68f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
                for (i in 0 until 3) {
                    val a = (spin + i * 120f) * PI / 180.0
                    val px = cx + cos(a).toFloat() * outerR
                    val py = cy + sin(a).toFloat() * outerR
                    drawCircle(color = RadiantGold, radius = 3.2f, center = Offset(px, py))
                }
            }
            Text(
                text = "CL${effectStack.size}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = Color.White
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            effectStack.takeLast(4).reversed().forEach { link ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (link == effectStack.last()) RadiantGold else ElectricCyan.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                ) {
                    Text(
                        text = "${link.chainLinkNumber}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        color = if (link == effectStack.last()) Color(0xFF0A0F1A) else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun AttackPlasmaBeamCanvas(
    event: AttackImpactVfxEvent,
    modifier: Modifier = Modifier
) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing))
    }

    Box(modifier = modifier.testTag("vfx_attack_impact_beam")) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = progress.value
            val start = Offset(size.width * 0.5f, size.height * 0.78f)
            val end = Offset(size.width * 0.5f, size.height * 0.22f)
            val curX = start.x + (end.x - start.x) * p
            val curY = start.y + (end.y - start.y) * p

            // Jagged plasma targeting bolt
            val bolt = Path().apply {
                moveTo(start.x, start.y)
                val segs = 6
                for (i in 1..segs) {
                    val t = (i / segs.toFloat()) * p
                    val jitter = if (i % 2 == 0) 18f else -18f
                    lineTo(start.x + jitter * (1f - t * 0.4f), start.y + (end.y - start.y) * t)
                }
                lineTo(curX, curY)
            }
            drawPath(bolt, color = CrimsonAlert.copy(alpha = 0.55f), style = Stroke(width = 12f))
            drawPath(bolt, color = RadiantGold, style = Stroke(width = 4.5f))
            drawPath(bolt, color = Color.White, style = Stroke(width = 2f))

            // Clash impact spark burst near destination
            if (p > 0.55f) {
                val burstScale = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)
                val maxR = 64f * burstScale
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White, RadiantGold.copy(alpha = 0.8f), CrimsonAlert.copy(alpha = 0.4f), Color.Transparent),
                        center = end,
                        radius = maxR.coerceAtLeast(4f)
                    ),
                    radius = maxR.coerceAtLeast(4f),
                    center = end
                )
                for (i in 0 until 10) {
                    val angle = i * 36.0 * PI / 180.0
                    drawLine(
                        color = RadiantGold,
                        start = end,
                        end = Offset(
                            end.x + cos(angle).toFloat() * maxR * 1.35f,
                            end.y + sin(angle).toFloat() * maxR * 1.35f
                        ),
                        strokeWidth = 2.4f
                    )
                }
            }
        }

        // Floating damage callout pill
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp)
                .background(Color(0xE6280912), rememberChamferedDuelShape(8.dp))
                .border(1.5.dp, CrimsonAlert, rememberChamferedDuelShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${event.attackerName} -> ${event.targetLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFCDD2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = event.damageCallout,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Black,
                    color = RadiantGold
                )
            }
        }
    }
}
