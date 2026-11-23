package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.OmniGeometryVariant
import com.example.data.PairCategory
import com.example.data.PieColorEntry
import com.example.data.TwentyColorPieCatalog
import com.example.ui.theme.RadiantGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive 20-Color Pie Full Progression Atlas (Layers 0–20 & Unique Omni Pip `{Ω}`).
 *
 * Strict Invariants:
 * - Boots with ZERO selected colors (`defaultSelectedCodes = emptySet()`).
 * - Always displays all pips unless the user actively filters by selecting 1..20 colors.
 * - Strictly scientific angular spin-polarization vectors under UMFT (zero invented
 *   philosophies, emotions, or moral alignments).
 * - Layers 1–19 render Option B Diamond pips; Layer 20 renders the 6 bespoke sacred
 *   celestial geometries (`{Ω·M}`, `{Ω·C}`, `{Ω·S}`, `{Ω·V}`, `{Ω·E}`, `{Ω·A}`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TwentyColorPieAtlasView(
    onCopyPipCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // STRICT INVARIANT: Always boots with 0 selected colors
    var selectedCodes by remember { mutableStateOf(TwentyColorPieCatalog.defaultSelectedCodes) }
    var activeLayerTab by rememberSaveable { mutableIntStateOf(0) }
    var activePairCategory by rememberSaveable { mutableStateOf<PairCategory?>(null) }

    val layerTabs = listOf(
        "L0–1 (20)",
        "L2 (190 Pairs)",
        "L3 (17 Trios)",
        "L4–17 (25)",
        "L18",
        "L19 (20)",
        "L20 {Ω}"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Scientific UMFT Framing
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "20-Color Pie Atlas (UMFT Angular Spin-Polarization)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "5 Anchors (W, U, B, R, G at indices 0,4,8,12,16) + 15 Bridges. Tap colors below to filter combinations — boots with 0 selected so every pip stays visible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Interactive 20-Color Wheel Selector
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TwentyColorPieCatalog.wheelColors.forEach { entry ->
                        val isSelected = entry.code in selectedCodes
                        val chipColor = Color(entry.hexArgb)
                        val textColor = Color(TwentyColorPieCatalog.contrastTextHex(entry.hexArgb))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) chipColor else chipColor.copy(alpha = 0.22f))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) RadiantGold else chipColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedCodes = if (isSelected) {
                                        selectedCodes - entry.code
                                    } else {
                                        selectedCodes + entry.code
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("pie_color_toggle_${entry.code}")
                        ) {
                            OptionBDiamondPip(colors = listOf(chipColor), size = 14.dp)
                            Text(
                                text = entry.code,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) textColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Active Selection Status + Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            selectedCodes = TwentyColorPieCatalog.validColorCodes
                            activeLayerTab = 6
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("pie_select_all_20_button")
                    ) {
                        Text("Select All 20 (Preview {Ω})", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            selectedCodes = emptySet()
                            activePairCategory = null
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("pie_reset_all_button")
                    ) {
                        Text("Reset All (${selectedCodes.size})", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Layer Tabs Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            layerTabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = activeLayerTab == idx,
                    onClick = { activeLayerTab = idx },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("pie_layer_tab_$idx")
                )
            }
        }

        // Layer Content
        when (activeLayerTab) {
            0 -> {
                // Layer 0 {C} + Layer 1 (20 single colors + :T/:S/:M value states)
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCopyPipCode(TwentyColorPieCatalog.COLORLESS_PIP) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OptionBDiamondPip(colors = listOf(Color(0xFF94A3B8)), size = 28.dp)
                            Column {
                                Text("Layer 0 — Colorless / Unpolarized", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                Text("Tap to copy {C}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("{C}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                val filteredColors = TwentyColorPieCatalog.wheelColors.filter {
                    selectedCodes.isEmpty() || it.code in selectedCodes
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredColors.forEach { c ->
                        SingleColorCard(entry = c, onCopy = onCopyPipCode)
                    }
                }
            }

            1 -> {
                // Layer 2: All 190 Option B Fused Pairs + 5 Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = activePairCategory == null,
                        onClick = { activePairCategory = null },
                        label = { Text("All (190)", style = MaterialTheme.typography.labelSmall) }
                    )
                    PairCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = activePairCategory == cat,
                            onClick = { activePairCategory = if (activePairCategory == cat) null else cat },
                            label = { Text("${cat.displayName} (${cat.expectedCount})", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                val pairs = remember(selectedCodes, activePairCategory) {
                    TwentyColorPieCatalog.all190FusedPairs.filter { pair ->
                        val catMatch = activePairCategory == null || pair.category == activePairCategory
                        val selMatch = selectedCodes.isEmpty() || selectedCodes.all { it in pair.codes } ||
                            (selectedCodes.size > 2 && pair.codes.all { it in selectedCodes })
                        catMatch && selMatch
                    }
                }

                Text(
                    text = "Showing ${pairs.size} of 190 Option B Diamond Fused Pairs (supports {A/B} and {A·B})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pairs.forEach { pair ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, Color(pair.mixedHexArgb).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                .clickable { onCopyPipCode(pair.pip) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            OptionBDiamondPip(
                                colors = listOf(Color(pair.first.hexArgb), Color(pair.second.hexArgb)),
                                size = 20.dp
                            )
                            Column {
                                Text(
                                    text = pair.pip,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = pair.category.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                // Layer 3: 17 Curated Trios
                val trios = TwentyColorPieCatalog.curatedLayer3Trios.filter { trio ->
                    selectedCodes.isEmpty() || selectedCodes.all { it in trio.codes }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trios.forEach { trio ->
                        val colors = trio.codes.mapNotNull { TwentyColorPieCatalog.colorByCode[it]?.hexArgb }.map { Color(it) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onCopyPipCode(trio.pip) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            OptionBDiamondPip(colors = colors, size = 24.dp)
                            Text(trio.pip, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            3 -> {
                // Layers 4–17: 25 Canonical Samples
                val samples = TwentyColorPieCatalog.layers4To17Samples.filter { s ->
                    selectedCodes.isEmpty() || selectedCodes.all { it in s.codes }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    samples.forEach { s ->
                        val colors = s.codes.mapNotNull { TwentyColorPieCatalog.colorByCode[it]?.hexArgb }.map { Color(it) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onCopyPipCode(s.pip) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OptionBDiamondPip(colors = colors, size = 26.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Layer ${s.layerCount} • ${s.label}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(s.pip, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            4 -> {
                // Layer 18: {-(A,B)} Subtraction Samples
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TwentyColorPieCatalog.layer18AllMinusPairSamples.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onCopyPipCode(entry.pip) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OptionBDiamondPip(
                                    colors = listOf(Color(entry.omittedA.hexArgb), Color(entry.omittedB.hexArgb)),
                                    size = 24.dp
                                )
                                Column {
                                    Text(entry.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text("18 Active Spin Vectors", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(entry.pip, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = RadiantGold)
                        }
                    }
                }
            }

            5 -> {
                // Layer 19: All 20 {-X} Subtractions
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TwentyColorPieCatalog.layer19AllMinusOne.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, Color(entry.omittedColor.hexArgb), RoundedCornerShape(10.dp))
                                .clickable { onCopyPipCode(entry.pip) }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            OptionBDiamondPip(colors = listOf(Color(entry.omittedColor.hexArgb)), size = 18.dp)
                            Text(entry.pip, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            6 -> {
                // Layer 20: Unique Omnichroma {Ω} + 6 Bespoke Sacred Celestial Geometries
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.border(1.5.dp, RadiantGold.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
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
                            Column {
                                Text(
                                    text = "Layer 20 — Omnichroma {Ω}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = RadiantGold
                                )
                                Text(
                                    text = "All 20 UMFT angular spin vectors unified — 6 bespoke celestial geometries",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { onCopyPipCode("{Ω}") }) {
                                Text("{Ω}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TwentyColorPieCatalog.omniGeometryVariants.forEach { variant ->
                                OmniGeometryCard(variant = variant, onClick = { onCopyPipCode(variant.code) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleColorCard(entry: PieColorEntry, onCopy: (String) -> Unit) {
    val base = Color(entry.hexArgb)
    val tint = Color(TwentyColorPieCatalog.mixHex(entry.hexArgb, 0xFFFFFFFF, 0.42f))
    val shade = Color(TwentyColorPieCatalog.mixHex(entry.hexArgb, 0xFF000000, 0.45f))
    val mute = Color(TwentyColorPieCatalog.muteHex(entry.hexArgb, 0.5f))

    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, base.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .clickable { onCopy(entry.pip) }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OptionBDiamondPip(colors = listOf(base), size = 24.dp)
            Column {
                Text(
                    text = "${entry.name} ${entry.pip}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isAnchor) "Anchor #${entry.wheelIndex}" else "Bridge #${entry.wheelIndex}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ValueStatePill(label = ":T", color = tint, onClick = { onCopy(entry.tintPip) })
            ValueStatePill(label = ":S", color = shade, onClick = { onCopy(entry.shadePip) })
            ValueStatePill(label = ":M", color = mute, onClick = { onCopy(entry.mutePip) })
        }
    }
}

@Composable
private fun ValueStatePill(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color(TwentyColorPieCatalog.contrastTextHex(color.value.toLong() shr 32))
        )
    }
}

@Composable
private fun OmniGeometryCard(variant: OmniGeometryVariant, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0F1C))
            .border(1.dp, RadiantGold.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("omni_geometry_${variant.shortSuffix}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OmniCelestialGeometryBadge(variant.shortSuffix, size = 56.dp)
        Text(
            text = variant.code,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = RadiantGold
        )
        Text(
            text = variant.geometryName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Option B Diamond Pip Renderer for Layers 1–19.
 */
@Composable
fun OptionBDiamondPip(
    colors: List<Color>,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w / 2f, 1.5f)
            lineTo(w - 1.5f, h / 2f)
            lineTo(w / 2f, h - 1.5f)
            lineTo(1.5f, h / 2f)
            close()
        }
        val safeColors = colors.ifEmpty { listOf(Color(0xFF94A3B8)) }
        if (safeColors.size == 1) {
            drawPath(path = path, color = safeColors.first())
        } else {
            drawPath(
                path = path,
                brush = Brush.linearGradient(colors = safeColors, start = Offset(0f, 0f), end = Offset(w, h))
            )
        }
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.8f),
            style = Stroke(width = 1.4f)
        )
    }
}

/**
 * Bespoke Canvas Renderer for the 6 Layer 20 Omnichroma `{Ω}` Sacred Celestial Geometries:
 * - `M`: Hexagram Star
 * - `C`: Celestial Octagram
 * - `S`: Sacred Halo Ring
 * - `V`: Astral Merkaba
 * - `E`: Primordial Sunburst
 * - `A`: Solar Astrolabe
 */
@Composable
fun OmniCelestialGeometryBadge(
    shortSuffix: String,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val spectrum = remember {
        listOf(
            Color(0xFFF8F5E6),
            Color(0xFF4CC9F0),
            Color(0xFF7C3AED),
            Color(0xFFE11D48),
            Color(0xFFD97706),
            Color(0xFF16A34A),
            Color(0xFF06B6D4),
            Color(0xFFF8F5E6)
        )
    }
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = (this.size.minDimension / 2f) - 4f
        val brush = Brush.sweepGradient(colors = spectrum, center = Offset(cx, cy))

        when (shortSuffix.uppercase()) {
            "M" -> { // Hexagram Star (2 interlaced triangles)
                drawRegularPolygon(cx, cy, r, 3, -90.0, brush, 2.2f)
                drawRegularPolygon(cx, cy, r, 3, 90.0, brush, 2.2f)
                drawCircle(brush = brush, radius = r * 0.28f, center = Offset(cx, cy))
            }
            "C" -> { // Celestial Octagram (2 rotated squares)
                drawRegularPolygon(cx, cy, r, 4, 0.0, brush, 2.2f)
                drawRegularPolygon(cx, cy, r, 4, 45.0, brush, 2.2f)
                drawCircle(brush = brush, radius = r * 0.3f, center = Offset(cx, cy), style = Stroke(2f))
            }
            "S" -> { // Sacred Halo Ring (concentric rings + 4 cardinal ticks)
                drawCircle(brush = brush, radius = r, center = Offset(cx, cy), style = Stroke(3.2f))
                drawCircle(brush = brush, radius = r * 0.65f, center = Offset(cx, cy), style = Stroke(1.8f))
                drawCircle(color = Color.White, radius = r * 0.2f, center = Offset(cx, cy))
                for (deg in listOf(0, 90, 180, 270)) {
                    val rad = deg * PI / 180.0
                    drawLine(
                        brush = brush,
                        start = Offset(cx + (r * 0.65f * cos(rad)).toFloat(), cy + (r * 0.65f * sin(rad)).toFloat()),
                        end = Offset(cx + (r * cos(rad)).toFloat(), cy + (r * sin(rad)).toFloat()),
                        strokeWidth = 2.2f
                    )
                }
            }
            "V" -> { // Astral Merkaba (star tetrahedron diamond + inner apex)
                drawRegularPolygon(cx, cy, r, 3, -90.0, brush, 2.4f)
                drawRegularPolygon(cx, cy, r * 0.78f, 3, 90.0, brush, 2.0f)
                drawRegularPolygon(cx, cy, r * 0.45f, 6, 0.0, brush, 1.5f)
            }
            "E" -> { // Primordial Sunburst (20-ray full wheel corona)
                drawCircle(brush = brush, radius = r * 0.42f, center = Offset(cx, cy))
                for (i in 0 until 20) {
                    val rad = (i * 18.0) * PI / 180.0
                    val inner = r * 0.48f
                    val outer = if (i % 4 == 0) r else r * 0.82f
                    drawLine(
                        brush = brush,
                        start = Offset(cx + (inner * cos(rad)).toFloat(), cy + (inner * sin(rad)).toFloat()),
                        end = Offset(cx + (outer * cos(rad)).toFloat(), cy + (outer * sin(rad)).toFloat()),
                        strokeWidth = if (i % 4 == 0) 2.4f else 1.4f
                    )
                }
            }
            else -> { // "A": Solar Astrolabe (orbital arcs + diagonal ecliptic)
                drawCircle(brush = brush, radius = r, center = Offset(cx, cy), style = Stroke(2.2f))
                drawCircle(brush = brush, radius = r * 0.62f, center = Offset(cx, cy - r * 0.12f), style = Stroke(1.8f))
                drawLine(
                    brush = brush,
                    start = Offset(cx - r * 0.85f, cy + r * 0.5f),
                    end = Offset(cx + r * 0.85f, cy - r * 0.5f),
                    strokeWidth = 2.2f
                )
                drawCircle(color = RadiantGold, radius = r * 0.16f, center = Offset(cx, cy))
            }
        }
    }
}

private fun DrawScope.drawRegularPolygon(
    cx: Float,
    cy: Float,
    radius: Float,
    sides: Int,
    startAngleDeg: Double,
    brush: Brush,
    strokeWidth: Float
) {
    val path = Path()
    for (i in 0 until sides) {
        val rad = (startAngleDeg + (360.0 / sides) * i) * PI / 180.0
        val x = cx + (radius * cos(rad)).toFloat()
        val y = cy + (radius * sin(rad)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, brush = brush, style = Stroke(width = strokeWidth))
}
