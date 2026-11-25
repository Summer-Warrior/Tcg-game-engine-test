package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.duelDiskPlate
import com.example.ui.theme.rememberChamferedDuelShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Iconic Yu-Gi-Oh! Anime / Manga / Master Duel Rolling LP Counter strip for both duelists.
 *
 * - Oversized slanted/chamfered digital-segment LP display with metallic Duel Disk trim
 *   and glowing vitality gauge.
 * - Rapid ticking/rolling digits via `Animatable` whenever LP changes.
 * - Crimson shockwave flash + horizontal micro-shake on LP damage.
 * - Radiant gold flare on LP gain.
 * - Pulsing critical-LP warning below 25% vitality.
 */
@Composable
fun DuelDiskLpCounterBar(
    hostName: String,
    opponentName: String,
    hostLife: Long,
    opponentLife: Long,
    startingLife: Long = 8000L,
    turnNumber: Long,
    activePhaseCode: String,
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    val safeMaxLife = startingLife.coerceAtLeast(maxOf(hostLife, opponentLife, 1L))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("duel_disk_lp_counter_bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SingleDuelistLpModule(
            playerLabel = hostName,
            lifePoints = hostLife,
            maxLife = safeMaxLife,
            accentColor = accentColor,
            alignEnd = false,
            testTagId = "lp_counter_host",
            modifier = Modifier.weight(1f)
        )

        // Central Turn & Phase Emblem
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .duelDiskPlate(
                    accentColor = RadiantGold,
                    secondaryColor = accentColor,
                    cutSize = 7.dp,
                    halftoneAccents = false
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "T$turnNumber",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = RadiantGold
            )
            Text(
                text = activePhaseCode.take(6).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
        }

        SingleDuelistLpModule(
            playerLabel = opponentName,
            lifePoints = opponentLife,
            maxLife = safeMaxLife,
            accentColor = CrimsonAlert,
            alignEnd = true,
            testTagId = "lp_counter_opponent",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SingleDuelistLpModule(
    playerLabel: String,
    lifePoints: Long,
    maxLife: Long,
    accentColor: Color,
    alignEnd: Boolean,
    testTagId: String,
    modifier: Modifier = Modifier
) {
    val animatedLp = remember { Animatable(lifePoints.toFloat()) }
    val shakeOffsetX = remember { Animatable(0f) }
    var previousLp by remember { mutableLongStateOf(lifePoints) }
    var flashKind by remember { mutableStateOf(0) } // -1 = damage crimson, +1 = gain gold, 0 = neutral
    var lastDeltaText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lifePoints) {
        val delta = lifePoints - previousLp
        previousLp = lifePoints
        if (delta != 0L) {
            flashKind = if (delta < 0L) -1 else 1
            lastDeltaText = if (delta < 0L) "$delta LP" else "+$delta LP"
            launch {
                animatedLp.animateTo(
                    targetValue = lifePoints.toFloat(),
                    animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
                )
            }
            if (delta < 0L) {
                launch {
                    val shakes = listOf(-10f, 10f, -7f, 7f, -4f, 4f, 0f)
                    shakes.forEach { target ->
                        shakeOffsetX.animateTo(target, tween(durationMillis = 36))
                    }
                }
            }
            delay(950L)
            flashKind = 0
            lastDeltaText = null
        } else {
            animatedLp.snapTo(lifePoints.toFloat())
        }
    }

    val ratio = (animatedLp.value / maxLife.toFloat()).coerceIn(0f, 1f)
    val isCritical = ratio < 0.25f

    val criticalPulse = if (isCritical) {
        val inf = rememberInfiniteTransition(label = "criticalLpPulse")
        val p by inf.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(460), RepeatMode.Reverse),
            label = "critAlpha"
        )
        p
    } else {
        1f
    }

    val targetFrameColor = when {
        flashKind < 0 -> CrimsonAlert
        flashKind > 0 -> RadiantGold
        isCritical -> CrimsonAlert.copy(alpha = criticalPulse)
        else -> accentColor
    }
    val frameColor by animateColorAsState(
        targetValue = targetFrameColor,
        animationSpec = tween(180),
        label = "lpFrameColor"
    )

    val digitColor = when {
        flashKind < 0 -> Color(0xFFFF8A80)
        flashKind > 0 -> Color(0xFFFFE57F)
        isCritical -> CrimsonAlert
        else -> Color(0xFFF8FAFC)
    }

    val gaugeColors = when {
        isCritical -> listOf(CrimsonAlert, Color(0xFFFF8A65))
        ratio < 0.55f -> listOf(RadiantGold, Color(0xFFFF7043))
        else -> listOf(EmeraldReady, accentColor)
    }

    val barShape = rememberChamferedDuelShape(4.dp)

    Column(
        modifier = modifier
            .offset { IntOffset(shakeOffsetX.value.roundToInt(), 0) }
            .duelDiskPlate(
                accentColor = frameColor,
                secondaryColor = if (flashKind > 0) RadiantGold else accentColor,
                cutSize = 8.dp,
                halftoneAccents = true,
                pulseEnergy = isCritical || flashKind != 0
            )
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .testTag(testTagId),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = playerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCBD5E1),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (lastDeltaText != null) {
                Text(
                    text = lastDeltaText.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (flashKind < 0) CrimsonAlert else RadiantGold
                )
            } else if (isCritical) {
                Text(
                    text = "CRITICAL",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = CrimsonAlert.copy(alpha = criticalPulse)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "LP",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.ExtraBold,
                color = frameColor
            )
            Text(
                text = animatedLp.value.roundToInt().coerceAtLeast(0).toString(),
                fontSize = 22.sp,
                lineHeight = 24.sp,
                fontFamily = FontFamily.Monospace,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Black,
                color = digitColor,
                maxLines = 1
            )
        }

        // Glowing beveled vitality gauge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(barShape)
                .background(Color(0xFF0A0F1A))
                .border(0.75.dp, frameColor.copy(alpha = 0.45f), barShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = ratio.coerceAtLeast(0.02f))
                    .height(6.dp)
                    .background(Brush.horizontalGradient(gaugeColors))
            )
        }
    }
}
