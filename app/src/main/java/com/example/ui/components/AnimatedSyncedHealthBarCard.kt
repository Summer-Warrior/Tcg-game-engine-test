package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BuiltInTcgCatalog
import com.example.data.RealtimeGameSyncState
import com.example.data.RealtimeSyncConnectionStatus
import com.example.data.SyncClientSeat
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Dual-player animated visual health bar HUD reflecting live life points synchronized from
 * Firebase Firestore ([RealtimeGameSyncState]).
 *
 * Features smooth rolling life counters, fighting-game delayed damage/heal trail bars,
 * critical-health heartbeat glow, micro-shake impact feedback on damage, floating delta pills,
 * and a tug-of-war advantage balance bar.
 */
@Composable
fun AnimatedSyncedHealthBarCard(
    syncState: RealtimeGameSyncState,
    fallbackHostLife: Long = syncState.hostLife,
    fallbackOpponentLife: Long = syncState.opponentLife,
    onQuickAdjustLife: ((SyncClientSeat, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val effectiveHostLife = if (syncState.isAttached) syncState.hostLife else fallbackHostLife
    val effectiveOpponentLife = if (syncState.isAttached) syncState.opponentLife else fallbackOpponentLife

    val baseSpec = remember(syncState.baseVariantId) {
        BuiltInTcgCatalog.findBaseSpec(syncState.baseVariantId)
    }
    val uiProfile = remember(syncState.baseVariantId) {
        BuiltInTcgCatalog.systemUiProfileFor(syncState.baseVariantId)
    }
    val startingLife = baseSpec.startingLife.coerceAtLeast(1L)
    val quickStep = if (startingLife >= 1000L) 500L else 5L

    val hostDisplayName = syncState.hostName.ifBlank { "Player 1 (Host)" }
    val opponentDisplayName = syncState.connectedClients.getOrNull(1)
        ?.substringAfter(": ")
        ?.ifBlank { "Player 2 (Peer)" }
        ?: "Player 2 (Peer)"

    val syncAccent = when (syncState.connectionStatus) {
        RealtimeSyncConnectionStatus.SYNCED -> EmeraldReady
        RealtimeSyncConnectionStatus.SYNCING_WRITE,
        RealtimeSyncConnectionStatus.CONNECTING -> RadiantGold
        RealtimeSyncConnectionStatus.FINISHED -> MaterialTheme.colorScheme.secondary
        RealtimeSyncConnectionStatus.ERROR -> CrimsonAlert
        RealtimeSyncConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.primary
    }

    // Relative tug-of-war ratio between P1 and P2 remaining life
    val totalLifeSum = (effectiveHostLife.coerceAtLeast(0L) + effectiveOpponentLife.coerceAtLeast(0L))
    val targetHostAdvantage = if (totalLifeSum > 0L) {
        (effectiveHostLife.coerceAtLeast(0L).toFloat() / totalLifeSum.toFloat()).coerceIn(0.04f, 0.96f)
    } else {
        0.5f
    }
    val animatedHostAdvantage by animateFloatAsState(
        targetValue = targetHostAdvantage,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "hostAdvantageBalance"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = syncAccent.copy(alpha = 0.6f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("animated_synced_health_bar_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Live Vital Telemetry Badge + Firestore Sync Revision
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
                        imageVector = Icons.Default.MonitorHeart,
                        contentDescription = stringResource(id = R.string.animated_health_hud_title),
                        tint = syncAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.animated_health_hud_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (syncState.connectionStatus == RealtimeSyncConnectionStatus.SYNCING_WRITE) {
                                    Icons.Default.CloudSync
                                } else {
                                    Icons.Default.CloudDone
                                },
                                contentDescription = "Firestore Health Sync",
                                tint = syncAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val subtitle = if (syncState.isAttached) {
                                "FIRESTORE LIVE • Rev #${syncState.revisionNumber} • Base $startingLife ${uiProfile.lifeLabel}"
                            } else {
                                "LOCAL PREVIEW • Base $startingLife ${uiProfile.lifeLabel}"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = syncAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "T${syncState.turnNumber} • ${syncState.activePhase}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Player 1 (Host) Animated Health Bar
            AnimatedPlayerHealthBarSection(
                seat = SyncClientSeat.CLIENT_1_HOST,
                playerName = hostDisplayName,
                seatTag = if (syncState.localSeat == SyncClientSeat.CLIENT_1_HOST) "P1 HOST • YOU" else "P1 HOST",
                currentLife = effectiveHostLife,
                startingLife = startingLife,
                lifeUnitLabel = uiProfile.lifeLabel,
                primarySeatColor = MaterialTheme.colorScheme.primary,
                canMutate = syncState.isAttached && !syncState.isMatchFinished && onQuickAdjustLife != null,
                quickStep = quickStep,
                onQuickDelta = { delta -> onQuickAdjustLife?.invoke(SyncClientSeat.CLIENT_1_HOST, delta) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Center Tug-of-War Balance Bar (P1 vs P2 Life Advantage)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "P1 ${(animatedHostAdvantage * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                ) {
                    val splitX = size.width * animatedHostAdvantage
                    drawRect(
                        color = CrimsonAlert.copy(alpha = 0.75f),
                        topLeft = Offset.Zero,
                        size = size
                    )
                    drawRect(
                        color = EmeraldReady.copy(alpha = 0.85f),
                        topLeft = Offset.Zero,
                        size = Size(width = splitX, height = size.height)
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.9f),
                        start = Offset(splitX, 0f),
                        end = Offset(splitX, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                Text(
                    text = "P2 ${((1f - animatedHostAdvantage) * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = RadiantGold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Player 2 (Peer) Animated Health Bar
            AnimatedPlayerHealthBarSection(
                seat = SyncClientSeat.CLIENT_2_PEER,
                playerName = opponentDisplayName,
                seatTag = if (syncState.localSeat == SyncClientSeat.CLIENT_2_PEER) "P2 PEER • YOU" else "P2 PEER",
                currentLife = effectiveOpponentLife,
                startingLife = startingLife,
                lifeUnitLabel = uiProfile.lifeLabel,
                primarySeatColor = RadiantGold,
                canMutate = syncState.isAttached && !syncState.isMatchFinished && onQuickAdjustLife != null,
                quickStep = quickStep,
                onQuickDelta = { delta -> onQuickAdjustLife?.invoke(SyncClientSeat.CLIENT_2_PEER, delta) }
            )
        }
    }
}

/**
 * Individual player's animated health bar section featuring:
 * - Rolling numeric life point display
 * - Delayed damage/heal trail bar (`Canvas`)
 * - Dynamic color transitions (Emerald -> Gold -> Crimson) + critical low-HP pulse
 * - Horizontal micro-shake on damage
 * - Animated floating delta callout (`-5`, `-500`, `+500`)
 */
@Composable
fun AnimatedPlayerHealthBarSection(
    seat: SyncClientSeat,
    playerName: String,
    seatTag: String,
    currentLife: Long,
    startingLife: Long,
    lifeUnitLabel: String,
    primarySeatColor: Color,
    canMutate: Boolean = false,
    quickStep: Long = 5L,
    onQuickDelta: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val safeStart = startingLife.coerceAtLeast(1L)
    val clampedLife = currentLife.coerceAtLeast(0L)
    val effectiveScaleMax = maxOf(safeStart, clampedLife)
    val targetFraction = (clampedLife.toFloat() / effectiveScaleMax.toFloat()).coerceIn(0f, 1f)
    val rawPercent = ((clampedLife.toFloat() / safeStart.toFloat()) * 100f).roundToInt()
    val isOverhealed = clampedLife > safeStart
    val isCritical = clampedLife in 1L..((safeStart * 0.25f).roundToLong().coerceAtLeast(1L))
    val isKnockedOut = clampedLife <= 0L

    // Track previous life value to compute live delta, damage/heal trail, and shake trigger
    var previousLife by remember(seat) { mutableLongStateOf(clampedLife) }
    var lastDelta by remember(seat) { mutableLongStateOf(0L) }
    var showDeltaPill by remember(seat) { mutableStateOf(false) }

    // Rolling counter animation
    val animatedDisplayLife = remember(seat) { Animatable(clampedLife.toFloat()) }
    // Foreground bar & delayed ghost trail bar
    val animatedMainBar = remember(seat) { Animatable(targetFraction) }
    val animatedGhostBar = remember(seat) { Animatable(targetFraction) }
    // Horizontal micro-shake offset in dp
    val shakeOffsetDp = remember(seat) { Animatable(0f) }

    LaunchedEffect(clampedLife, effectiveScaleMax) {
        val delta = clampedLife - previousLife
        val isDamage = delta < 0L
        val isHeal = delta > 0L

        if (delta != 0L) {
            lastDelta = delta
            showDeltaPill = true
        }
        previousLife = clampedLife

        // Launch rolling numeric counter smoothly
        launch {
            animatedDisplayLife.animateTo(
                targetValue = clampedLife.toFloat(),
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }

        if (isDamage) {
            // Damage impact: snap main bar briskly, micro-shake, then drain ghost damage trail
            launch {
                animatedMainBar.animateTo(
                    targetValue = targetFraction,
                    animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                )
            }
            launch {
                val shakePattern = listOf(-7f, 6f, -4.5f, 3f, -1.5f, 0f)
                for (offset in shakePattern) {
                    shakeOffsetDp.animateTo(offset, animationSpec = tween(durationMillis = 36))
                }
            }
            launch {
                delay(240L)
                animatedGhostBar.animateTo(
                    targetValue = targetFraction,
                    animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
                )
            }
        } else if (isHeal) {
            // Heal effect: jump ghost preview ahead, then smoothly fill main bar
            launch {
                animatedGhostBar.animateTo(
                    targetValue = targetFraction,
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing)
                )
            }
            launch {
                delay(120L)
                animatedMainBar.animateTo(
                    targetValue = targetFraction,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        } else {
            // Initial / scale sync
            animatedMainBar.snapTo(targetFraction)
            animatedGhostBar.snapTo(targetFraction)
        }

        if (delta != 0L) {
            delay(1800L)
            showDeltaPill = false
        }
    }

    // Critical low-HP heartbeat pulse
    val infiniteTransition = rememberInfiniteTransition(label = "criticalHealthPulse_${seat.code}")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha_${seat.code}"
    )

    val targetBarColor = when {
        isKnockedOut || isCritical -> CrimsonAlert
        rawPercent <= 55 -> RadiantGold
        else -> EmeraldReady
    }
    val animatedBarColor by animateColorAsState(
        targetValue = targetBarColor,
        animationSpec = tween(durationMillis = 320),
        label = "barColor_${seat.code}"
    )
    val barHighlightEnd = when {
        isKnockedOut || isCritical -> Color(0xFFFF5252)
        rawPercent <= 55 -> Color(0xFFFFD740)
        isOverhealed -> Color(0xFF64FFDA)
        else -> Color(0xFF00E5FF)
    }

    val effectiveAlpha = if (isCritical) pulseAlpha else 1f
    val borderColor = if (isCritical || isKnockedOut) {
        CrimsonAlert.copy(alpha = 0.7f * effectiveAlpha)
    } else {
        primarySeatColor.copy(alpha = 0.42f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffsetDp.value.dp.roundToPx(), 0) }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(10.dp)
            .testTag("animated_health_section_${seat.code}")
    ) {
        // Player Label + Rolling LP Readout + Floating Delta Callout Pill
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
                            .background(primarySeatColor, CircleShape)
                    )
                    Text(
                        text = seatTag,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = primarySeatColor
                    )
                    if (isOverhealed) {
                        Text(
                            text = "OVERHEAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = RadiantGold
                        )
                    } else if (isKnockedOut) {
                        Text(
                            text = "KO",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = CrimsonAlert
                        )
                    } else if (isCritical) {
                        Text(
                            text = "CRITICAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = CrimsonAlert.copy(alpha = effectiveAlpha)
                        )
                    }
                }
                Text(
                    text = playerName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Animated Floating Delta Pill
                AnimatedVisibility(
                    visible = showDeltaPill && lastDelta != 0L,
                    enter = fadeIn(tween(150)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                    exit = fadeOut(tween(300)) + scaleOut(tween(220))
                ) {
                    val deltaColor = if (lastDelta < 0L) CrimsonAlert else EmeraldReady
                    val deltaText = if (lastDelta < 0L) "$lastDelta" else "+$lastDelta"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(deltaColor.copy(alpha = 0.22f))
                            .border(1.dp, deltaColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                            .testTag("animated_health_delta_pill_${seat.code}")
                    ) {
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            color = deltaColor
                        )
                    }
                }

                Icon(
                    imageVector = if (isOverhealed) Icons.Default.Shield else Icons.Default.Favorite,
                    contentDescription = "$playerName $lifeUnitLabel",
                    tint = animatedBarColor.copy(alpha = effectiveAlpha),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${animatedDisplayLife.value.roundToLong()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = animatedBarColor,
                    modifier = Modifier.testTag("animated_health_value_${seat.code}")
                )
                Text(
                    text = "/ $safeStart $lifeUnitLabel ($rawPercent%)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dual-Layer Animated Visual Health Bar Track (Canvas)
        AnimatedHealthBarTrack(
            mainFraction = animatedMainBar.value,
            ghostFraction = animatedGhostBar.value,
            mainStartColor = animatedBarColor.copy(alpha = effectiveAlpha),
            mainEndColor = barHighlightEnd.copy(alpha = effectiveAlpha),
            isHealingTrail = animatedGhostBar.value > animatedMainBar.value && lastDelta > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .testTag("animated_health_bar_track_${seat.code}")
        )

        if (canMutate && onQuickDelta != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onQuickDelta(-quickStep) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonAlert.copy(alpha = 0.16f),
                        contentColor = CrimsonAlert
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("animated_health_damage_${seat.code}")
                ) {
                    Text(
                        text = "-$quickStep $lifeUnitLabel",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Button(
                    onClick = { onQuickDelta(quickStep) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldReady.copy(alpha = 0.16f),
                        contentColor = EmeraldReady
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("animated_health_heal_${seat.code}")
                ) {
                    Text(
                        text = "+$quickStep $lifeUnitLabel",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Reusable Compose `Canvas` health bar track featuring:
 * - Dark recessed background track
 * - Delayed damage trail (warm amber/white) or heal preview trail (soft emerald)
 * - Smooth linear-gradient main fill
 * - Glossy top edge highlight & 10-segment tactical tick marks
 */
@Composable
fun AnimatedHealthBarTrack(
    mainFraction: Float,
    ghostFraction: Float,
    mainStartColor: Color,
    mainEndColor: Color,
    isHealingTrail: Boolean,
    modifier: Modifier = Modifier
) {
    val trackBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    val trackBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val damageTrailColor = Color(0xFFFFAB40).copy(alpha = 0.8f)
    val healTrailColor = EmeraldReady.copy(alpha = 0.45f)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, trackBorder, RoundedCornerShape(10.dp))
    ) {
        val cornerPx = 10.dp.toPx()
        val corner = CornerRadius(cornerPx, cornerPx)

        // 1. Track Background
        drawRoundRect(
            color = trackBackground,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = corner
        )

        val clampedMain = mainFraction.coerceIn(0f, 1f)
        val clampedGhost = ghostFraction.coerceIn(0f, 1f)

        // 2. Trailing Ghost Bar (damage drain trail or heal preview lead)
        if (clampedGhost > clampedMain) {
            val ghostWidth = size.width * clampedGhost
            if (ghostWidth > 0f) {
                drawRoundRect(
                    color = if (isHealingTrail) healTrailColor else damageTrailColor,
                    topLeft = Offset.Zero,
                    size = Size(width = ghostWidth, height = size.height),
                    cornerRadius = corner
                )
            }
        }

        // 3. Main Gradient Health Fill
        val mainWidth = size.width * clampedMain
        if (mainWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(mainStartColor, mainEndColor),
                    startX = 0f,
                    endX = mainWidth.coerceAtLeast(1f)
                ),
                topLeft = Offset.Zero,
                size = Size(width = mainWidth, height = size.height),
                cornerRadius = corner
            )

            // Glossy top specular highlight strip
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0f, 1.dp.toPx()),
                size = Size(width = mainWidth, height = (size.height * 0.34f)),
                cornerRadius = corner
            )
        }

        // 4. 10-Segment Tactical Scale Dividers
        val segments = 10
        val tickStroke = 1.dp.toPx()
        for (i in 1 until segments) {
            val x = (size.width / segments) * i
            drawLine(
                color = Color.Black.copy(alpha = 0.28f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = tickStroke
            )
        }
    }
}
