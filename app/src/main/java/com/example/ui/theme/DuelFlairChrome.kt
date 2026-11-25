package com.example.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.CardOrigin
import com.example.engine.CatalogBlueprint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sharp chamfered Battle City / Duel Disk beveled corner shape.
 */
@Composable
fun rememberChamferedDuelShape(cutSize: Dp = 10.dp): Shape {
    val cutPx = with(LocalDensity.current) { cutSize.toPx() }
    return GenericShape { size, _ ->
        val c = cutPx.coerceAtMost(minOf(size.width, size.height) * 0.35f)
        moveTo(c, 0f)
        lineTo(size.width - c, 0f)
        lineTo(size.width, c)
        lineTo(size.width, size.height - c)
        lineTo(size.width - c, size.height)
        lineTo(c, size.height)
        lineTo(0f, size.height - c)
        lineTo(0f, c)
        close()
    }
}

enum class FoilFinishStyle(val label: String) {
    ROYAL_FINISH("Royal Finish"),
    PRISMATIC("Prismatic"),
    MANGA_RARE("Manga Rare"),
    STARLIGHT("Starlight")
}

/**
 * Switchable Solid Vision holographic arena floor atmospheres.
 */
enum class DuelArenaAtmosphere(
    val displayName: String,
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val floorTop: Color,
    val floorBottom: Color,
    val badgeTag: String
) {
    BATTLE_CITY_STADIUM(
        displayName = "Battle City KaibaCorp Stadium",
        primaryGlow = Color(0xFF00E5FF),
        secondaryGlow = Color(0xFFFFB300),
        floorTop = Color(0xFF060E1F),
        floorBottom = Color(0xFF0B1C36),
        badgeTag = "KAIBACORP SOLID VISION"
    ),
    DUELIST_KINGDOM_TABLET(
        displayName = "Duelist Kingdom Stone Tablet",
        primaryGlow = Color(0xFFFFCA28),
        secondaryGlow = Color(0xFFFF7043),
        floorTop = Color(0xFF171109),
        floorBottom = Color(0xFF281D0F),
        badgeTag = "MILLENNIUM TABLET"
    ),
    NEO_DOMINO_HIGHWAY(
        displayName = "Neo-Domino Turbo Highway",
        primaryGlow = Color(0xFF00F0FF),
        secondaryGlow = Color(0xFFFF3D71),
        floorTop = Color(0xFF080A1D),
        floorBottom = Color(0xFF160E2E),
        badgeTag = "SPEED WORLD 5D'S"
    ),
    VRAINS_LINK_CIRCUIT(
        displayName = "VRAINS Link Circuit",
        primaryGlow = Color(0xFF18FFFF),
        secondaryGlow = Color(0xFF7C4DFF),
        floorTop = Color(0xFF030B1A),
        floorBottom = Color(0xFF081B33),
        badgeTag = "LINK VRAINS CIRCUIT"
    ),
    PLANAR_SANCTUM(
        displayName = "Planar Sanctum",
        primaryGlow = Color(0xFF38BDF8),
        secondaryGlow = Color(0xFF34D399),
        floorTop = Color(0xFF07131C),
        floorBottom = Color(0xFF0D232A),
        badgeTag = "NESTED PLANAR MANA"
    ),
    PLANET_CRAY_STARFIELD(
        displayName = "Planet Cray Starfield",
        primaryGlow = Color(0xFFFFD54F),
        secondaryGlow = Color(0xFF60A5FA),
        floorTop = Color(0xFF090D22),
        floorBottom = Color(0xFF14183A),
        badgeTag = "CRAY VANGUARD ORBIT"
    ),
    ULTIMATE_TCG_COSMOLOGY(
        displayName = "Ultimate TCG Cosmology",
        primaryGlow = Color(0xFFFFB300),
        secondaryGlow = Color(0xFFE040FB),
        floorTop = Color(0xFF0D081C),
        floorBottom = Color(0xFF1C102B),
        badgeTag = "20-COLOR OMNI COSMOS"
    );

    companion object {
        fun defaultForBlueprint(blueprint: CatalogBlueprint?, baseVariantId: String): DuelArenaAtmosphere {
            val id = (blueprint?.id ?: baseVariantId).uppercase()
            val name = blueprint?.name.orEmpty().lowercase()
            return when {
                id == "ULTIMATE_TCG_WIP" || name.contains("ultimate tcg") -> ULTIMATE_TCG_COSMOLOGY
                name.contains("vanguard") || name.contains("planet cray") -> PLANET_CRAY_STARFIELD
                id.contains("FORGE") || name.contains("magic") || name.contains("planar") -> PLANAR_SANCTUM
                name.contains("vrains") || name.contains("link") || name.contains("cyber") -> VRAINS_LINK_CIRCUIT
                name.contains("turbo") || name.contains("5d") || name.contains("speed") -> NEO_DOMINO_HIGHWAY
                name.contains("duelist kingdom") || name.contains("forbidden") -> DUELIST_KINGDOM_TABLET
                else -> BATTLE_CITY_STADIUM
            }
        }
    }
}

/**
 * Dynamic Chameleon accent color for the active TCG / Format / Combo.
 */
fun chameleonAccentFor(blueprint: CatalogBlueprint?, baseVariantId: String): Color {
    val id = (blueprint?.id ?: baseVariantId).uppercase()
    val name = blueprint?.name.orEmpty().lowercase()
    return when {
        id == "ULTIMATE_TCG_WIP" -> RadiantGold
        name.contains("pokemon") || name.contains("pokémon") -> Color(0xFFFFD600)
        name.contains("digimon") -> Color(0xFF38BDF8)
        name.contains("one piece") -> Color(0xFFFF5252)
        name.contains("vanguard") || name.contains("cray") -> Color(0xFFFFB74D)
        name.contains("lorcana") -> Color(0xFFC084FC)
        name.contains("flesh and blood") || name.contains("talishar") -> Color(0xFFFB7185)
        name.contains("star wars") || name.contains("karabast") -> Color(0xFF22D3EE)
        name.contains("marvel snap") || name.contains("cross duel") -> Color(0xFFF43F5E)
        id.contains("FORGE") || name.contains("magic") -> Color(0xFF29B6F6)
        id == "EDOPRO_CUSTOM_FORK" -> RadiantGold
        id == "HYBRID_FORGE_EDOPRO_MIX" -> Color(0xFFFF4081)
        else -> ElectricCyan
    }
}

/**
 * Battle City / Duel Disk metallic chrome modifier with chamfered corners,
 * luminous plasma border, subtle manga halftone screen-tone accents, and energy pulse.
 */
fun Modifier.duelDiskPlate(
    accentColor: Color = ElectricCyan,
    secondaryColor: Color = RadiantGold,
    cutSize: Dp = 10.dp,
    halftoneAccents: Boolean = true,
    pulseEnergy: Boolean = false
): Modifier = composed {
    val shape = rememberChamferedDuelShape(cutSize)
    val pulseAlpha = if (pulseEnergy) {
        val transition = rememberInfiniteTransition(label = "duelDiskPulse")
        val p by transition.animateFloat(
            initialValue = 0.48f,
            targetValue = 0.96f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        p
    } else {
        0.72f
    }

    this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF0E1525),
                    Color(0xFF162137),
                    Color(0xFF0A101D)
                )
            )
        )
        .drawBehind {
            val cut = cutSize.toPx().coerceAtMost(minOf(size.width, size.height) * 0.35f)
            // Subtle manga halftone screen-tone dots in top-right & bottom-left corners
            if (halftoneAccents) {
                val dotColor = accentColor.copy(alpha = 0.14f)
                for (r in 0..3) {
                    for (c in 0..4) {
                        val radius = (2.1f - r * 0.35f).coerceAtLeast(0.7f)
                        drawCircle(
                            color = dotColor,
                            radius = radius,
                            center = Offset(size.width - 14f - c * 8f, 10f + r * 7f)
                        )
                        drawCircle(
                            color = secondaryColor.copy(alpha = 0.10f),
                            radius = radius,
                            center = Offset(14f + c * 8f, size.height - 10f - r * 7f)
                        )
                    }
                }
            }
            // Chamfered plasma border path
            val path = Path().apply {
                moveTo(cut, 1f)
                lineTo(size.width - cut, 1f)
                lineTo(size.width - 1f, cut)
                lineTo(size.width - 1f, size.height - cut)
                lineTo(size.width - cut, size.height - 1f)
                lineTo(cut, size.height - 1f)
                lineTo(1f, size.height - cut)
                lineTo(1f, cut)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = pulseAlpha),
                        secondaryColor.copy(alpha = pulseAlpha * 0.65f),
                        accentColor.copy(alpha = pulseAlpha)
                    )
                ),
                style = Stroke(width = 2.4f)
            )
            // Top-left luminous metallic energy notch
            drawLine(
                color = accentColor.copy(alpha = 0.92f),
                start = Offset(cut + 2f, 3f),
                end = Offset((cut + 38f).coerceAtMost(size.width - cut), 3f),
                strokeWidth = 3f
            )
        }
}

/**
 * Animated diagonal holographic sheen (`Royal Finish`, `Prismatic`, `Manga Rare`, `Starlight`).
 */
fun Modifier.foilShimmerOverlay(
    style: FoilFinishStyle = FoilFinishStyle.ROYAL_FINISH,
    intensity: Float = 0.28f
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "cardFoilShimmer")
    val sweep by transition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "foilSweep"
    )
    this.drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        val xStart = w * sweep
        val shimmerColors = when (style) {
            FoilFinishStyle.ROYAL_FINISH -> listOf(
                Color.Transparent,
                Color(0xFF00E5FF).copy(alpha = intensity),
                Color(0xFFFFD54F).copy(alpha = intensity * 1.15f),
                Color(0xFFE040FB).copy(alpha = intensity * 0.9f),
                Color.Transparent
            )
            FoilFinishStyle.PRISMATIC -> listOf(
                Color.Transparent,
                Color(0xFF38BDF8).copy(alpha = intensity),
                Color(0xFFFFFFFF).copy(alpha = intensity * 1.1f),
                Color(0xFF34D399).copy(alpha = intensity * 0.9f),
                Color.Transparent
            )
            FoilFinishStyle.MANGA_RARE -> listOf(
                Color.Transparent,
                Color(0xFFFFFFFF).copy(alpha = intensity * 1.25f),
                Color(0xFFFFB300).copy(alpha = intensity * 0.85f),
                Color.Transparent
            )
            FoilFinishStyle.STARLIGHT -> listOf(
                Color.Transparent,
                Color(0xFFFFE082).copy(alpha = intensity),
                Color(0xFF80DEEA).copy(alpha = intensity * 1.1f),
                Color(0xFFF48FB1).copy(alpha = intensity),
                Color.Transparent
            )
        }
        drawRect(
            brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(xStart - w * 0.4f, 0f),
                end = Offset(xStart + w * 0.4f, h)
            )
        )
    }
}

/**
 * Animated Solid Vision holographic duel arena floor canvas with perspective plasma grid,
 * central duel crest glow, and switchable Arena Atmosphere signatures.
 */
@Composable
fun HolographicArenaFloorCanvas(
    atmosphere: DuelArenaAtmosphere,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "arenaFloorAnim")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gridDrift"
    )
    val glowPulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "crestPulse"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f

        // Deep stadium floor gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(atmosphere.floorTop, atmosphere.floorBottom, atmosphere.floorTop)
            )
        )

        // Central radial Duel Crest hologram glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    atmosphere.primaryGlow.copy(alpha = 0.20f * glowPulse),
                    atmosphere.secondaryGlow.copy(alpha = 0.08f * glowPulse),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = minOf(w, h) * 0.58f
            ),
            radius = minOf(w, h) * 0.58f,
            center = Offset(cx, cy)
        )

        // Perspective drifting plasma grid lines
        val rows = 10
        for (i in 0..rows) {
            val t = ((i + drift) / rows.toFloat()).coerceIn(0f, 1f)
            val y = h * t
            drawLine(
                color = atmosphere.primaryGlow.copy(alpha = 0.12f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.2f
            )
        }
        val cols = 10
        for (i in 0..cols) {
            val frac = i / cols.toFloat()
            val topX = cx + (frac - 0.5f) * w * 0.72f
            val botX = cx + (frac - 0.5f) * w * 1.28f
            drawLine(
                color = atmosphere.primaryGlow.copy(alpha = 0.11f),
                start = Offset(topX, 0f),
                end = Offset(botX, h),
                strokeWidth = 1.1f
            )
        }

        // Atmosphere-specific holographic floor signature
        val crestR = minOf(w, h) * 0.22f
        drawCircle(
            color = atmosphere.primaryGlow.copy(alpha = 0.25f * glowPulse),
            radius = crestR,
            center = Offset(cx, cy),
            style = Stroke(width = 2.2f)
        )
        drawCircle(
            color = atmosphere.secondaryGlow.copy(alpha = 0.18f * glowPulse),
            radius = crestR * 0.72f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.6f)
        )

        when (atmosphere) {
            DuelArenaAtmosphere.VRAINS_LINK_CIRCUIT -> {
                // Octagonal Link Circuit nodes around the center crest
                val hex = Path()
                for (i in 0 until 6) {
                    val angle = (i * 60.0 + drift * 30.0) * PI / 180.0
                    val px = cx + cos(angle).toFloat() * crestR * 1.15f
                    val py = cy + sin(angle).toFloat() * crestR * 1.15f
                    if (i == 0) hex.moveTo(px, py) else hex.lineTo(px, py)
                }
                hex.close()
                drawPath(hex, color = atmosphere.primaryGlow.copy(alpha = 0.26f), style = Stroke(2f))
            }
            DuelArenaAtmosphere.NEO_DOMINO_HIGHWAY -> {
                // High-speed neon turbo stripes along left and right rails
                drawLine(atmosphere.secondaryGlow.copy(alpha = 0.45f), Offset(12f, 0f), Offset(12f, h), strokeWidth = 3f)
                drawLine(atmosphere.secondaryGlow.copy(alpha = 0.45f), Offset(w - 12f, 0f), Offset(w - 12f, h), strokeWidth = 3f)
            }
            DuelArenaAtmosphere.ULTIMATE_TCG_COSMOLOGY -> {
                // 20-ray Omnichroma compass spokes
                for (i in 0 until 20) {
                    val angle = (i * 18.0 + drift * 18.0) * PI / 180.0
                    val sx = cx + cos(angle).toFloat() * crestR * 0.72f
                    val sy = cy + sin(angle).toFloat() * crestR * 0.72f
                    val ex = cx + cos(angle).toFloat() * crestR * 1.25f
                    val ey = cy + sin(angle).toFloat() * crestR * 1.25f
                    drawLine(
                        color = if (i % 4 == 0) atmosphere.primaryGlow.copy(alpha = 0.35f) else atmosphere.secondaryGlow.copy(alpha = 0.20f),
                        start = Offset(sx, sy),
                        end = Offset(ex, ey),
                        strokeWidth = if (i % 4 == 0) 2.2f else 1.1f
                    )
                }
            }
            else -> {
                // Diagonal manga speed accents in corners
                drawLine(atmosphere.secondaryGlow.copy(alpha = 0.22f), Offset(0f, 32f), Offset(42f, 0f), strokeWidth = 2f)
                drawLine(atmosphere.secondaryGlow.copy(alpha = 0.22f), Offset(w - 42f, h), Offset(w, h - 32f), strokeWidth = 2f)
            }
        }
    }
}

/**
 * Reusable Canon / AU Variant / Fan-Fiction OC origin badge chip.
 */
@Composable
fun CardOriginBadge(
    origin: CardOrigin,
    linkedCanonCardId: String = "",
    modifier: Modifier = Modifier
) {
    val (accent, label) = when (origin) {
        CardOrigin.CANON_ORIGINAL -> EmeraldReady to "CANON ORIGINAL"
        CardOrigin.FAN_ALTERNATE_VARIANT -> {
            val suffix = if (linkedCanonCardId.isNotBlank()) " OF #$linkedCanonCardId" else ""
            ElectricCyan to "AU VARIANT$suffix"
        }
        CardOrigin.ORIGINAL_OC_CARD -> Color(0xFFE040FB) to "FAN-FICTION OC"
    }
    val shape = rememberChamferedDuelShape(5.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = accent,
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.7f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
