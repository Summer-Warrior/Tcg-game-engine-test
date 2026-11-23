package com.example.data

import kotlin.math.roundToInt

/**
 * Canonical 20-Color Pie Full Progression (Layers 0 to 20 & Unique Omni Pip {Ω}).
 *
 * Design invariants:
 * - Zero default colors selected on load (`defaultSelectedCodes = emptySet()`).
 * - Always displays all pips unless the user actively filters by selecting 1..20 colors.
 * - Strictly scientific angular spin-polarization vectors under UMFT — zero invented
 *   philosophies, emotions, or moral alignments are attached to any color.
 * - Layers 1–19 follow Diamond / Option B Format; Layer 20 uses 6 bespoke sacred celestial
 *   geometries for Omnichroma {Ω}: {Ω·M}, {Ω·C}, {Ω·S}, {Ω·V}, {Ω·E}, {Ω·A}.
 */
enum class PairCategory(val displayName: String, val expectedCount: Int) {
    ALLIED_ANCHORS("Allied Anchors", 5),
    ENEMY_ANCHORS("Enemy Anchors", 5),
    OPPOSITES_180("180° Opposites", 10),
    ANCHOR_BRIDGE("Anchor–Bridge", 70),
    BRIDGE_BRIDGE("Bridge–Bridge", 100)
}

data class PieColorEntry(
    val wheelIndex: Int,
    val code: String,
    val name: String,
    val isAnchor: Boolean,
    val hexArgb: Long
) {
    val pip: String get() = "{$code}"
    val tintPip: String get() = "{$code:T}"
    val shadePip: String get() = "{$code:S}"
    val mutePip: String get() = "{$code:M}"
    val hexRgbString: String get() = String.format("#%06X", (hexArgb and 0xFFFFFFL).toInt())
}

data class OptionBFusedPair(
    val first: PieColorEntry,
    val second: PieColorEntry,
    val category: PairCategory,
    val wheelDistance: Int
) {
    val pip: String get() = "{${first.code}/${second.code}}"
    val dotPip: String get() = "{${first.code}·${second.code}}"
    val label: String get() = "${first.name} / ${second.name}"
    val codes: Set<String> get() = setOf(first.code, second.code)
    val mixedHexArgb: Long get() = TwentyColorPieCatalog.mixHex(first.hexArgb, second.hexArgb)
}

data class CuratedTrioEntry(
    val index: Int,
    val codes: List<String>
) {
    val pip: String get() = "{${codes.joinToString("/")}}"
}

data class MidLayerSampleEntry(
    val layerCount: Int,
    val sampleIndex: Int,
    val codes: List<String>,
    val label: String
) {
    val pip: String get() = "{${codes.joinToString("/")}}"
}

data class Layer18SubtractionEntry(
    val omittedA: PieColorEntry,
    val omittedB: PieColorEntry,
    val includedCodes: List<String>
) {
    val pip: String get() = "{-(${omittedA.code},${omittedB.code})}"
    val label: String get() = "All Minus ${omittedA.name} & ${omittedB.name}"
}

data class Layer19SubtractionEntry(
    val omittedColor: PieColorEntry,
    val includedCodes: List<String>
) {
    val pip: String get() = "{-${omittedColor.code}}"
    val label: String get() = "All Minus ${omittedColor.name}"
}

data class OmniGeometryVariant(
    val code: String,
    val shortSuffix: String,
    val geometryName: String,
    val description: String
)

object TwentyColorPieCatalog {
    const val COLORLESS_PIP: String = "{C}"
    const val OMNICHROMA_PIP: String = "{Ω}"
    const val OMNICHROMA_NAME: String = "Omnichroma"

    val defaultSelectedCodes: Set<String> = emptySet()

    /**
     * Exact 6 bespoke Layer 20 Omnichroma {Ω} sacred celestial geometries.
     */
    val omniGeometryVariants: List<OmniGeometryVariant> = listOf(
        OmniGeometryVariant("{Ω·M}", "M", "Hexagram Star", "Interlaced 6-point celestial star prism"),
        OmniGeometryVariant("{Ω·C}", "C", "Celestial Octagram", "8-point orbital star harmonic"),
        OmniGeometryVariant("{Ω·S}", "S", "Sacred Halo Ring", "Concentric iridescent spectral ring"),
        OmniGeometryVariant("{Ω·V}", "V", "Astral Merkaba", "Dual counter-rotating tetrahedron vector"),
        OmniGeometryVariant("{Ω·E}", "E", "Primordial Sunburst", "20-ray full-wheel corona burst"),
        OmniGeometryVariant("{Ω·A}", "A", "Solar Astrolabe", "Calibrated angular spin-vector astrolabe")
    )

    val validOmniSuffixes: Set<String> = omniGeometryVariants.mapTo(LinkedHashSet()) { it.shortSuffix }

    val omniVariants: List<Pair<String, String>> = omniGeometryVariants.map { it.code to it.geometryName }

    val valueStateSuffixes: List<Pair<String, String>> = listOf(
        ":T" to "Tint",
        ":S" to "Shade",
        ":M" to "Mute"
    )

    val wheelColors: List<PieColorEntry> = listOf(
        PieColorEntry(0, "W", "White", true, 0xFFF8F5E6),
        PieColorEntry(1, "Iv", "Ivory", false, 0xFFE6DFC5),
        PieColorEntry(2, "Cy", "Cyan", false, 0xFF4CC9F0),
        PieColorEntry(3, "Az", "Azure", false, 0xFF3A86FF),
        PieColorEntry(4, "U", "Blue", true, 0xFF1D4ED8),
        PieColorEntry(5, "In", "Indigo", false, 0xFF4338CA),
        PieColorEntry(6, "Vi", "Violet", false, 0xFF7C3AED),
        PieColorEntry(7, "Ob", "Obsidian", false, 0xFF3F3A4D),
        PieColorEntry(8, "B", "Black", true, 0xFF1E1B24),
        PieColorEntry(9, "Cr", "Crimson", false, 0xFF9F1239),
        PieColorEntry(10, "Mg", "Magenta", false, 0xFFDB2777),
        PieColorEntry(11, "Sc", "Scarlet", false, 0xFFE11D48),
        PieColorEntry(12, "R", "Red", true, 0xFFDC2626),
        PieColorEntry(13, "Or", "Orange", false, 0xFFEA580C),
        PieColorEntry(14, "Am", "Amber", false, 0xFFD97706),
        PieColorEntry(15, "Ch", "Chartreuse", false, 0xFF84CC16),
        PieColorEntry(16, "G", "Green", true, 0xFF16A34A),
        PieColorEntry(17, "Ja", "Jade", false, 0xFF059669),
        PieColorEntry(18, "Te", "Teal", false, 0xFF0D9488),
        PieColorEntry(19, "Aq", "Aqua", false, 0xFF06B6D4)
    )

    val validColorCodes: Set<String> = wheelColors.mapTo(LinkedHashSet()) { it.code }
    val colorByCode: Map<String, PieColorEntry> = wheelColors.associateBy { it.code }

    /**
     * Exact RGB linear midpoint mixing (aligned with MPV Card Maker `mixHex`).
     */
    fun mixHex(argbA: Long, argbB: Long, weightB: Float = 0.5f): Long {
        val wB = weightB.coerceIn(0f, 1f)
        val wA = 1f - wB
        val rA = ((argbA shr 16) and 0xFF).toInt()
        val gA = ((argbA shr 8) and 0xFF).toInt()
        val bA = (argbA and 0xFF).toInt()
        val rB = ((argbB shr 16) and 0xFF).toInt()
        val gB = ((argbB shr 8) and 0xFF).toInt()
        val bB = (argbB and 0xFF).toInt()
        val r = (rA * wA + rB * wB).roundToInt().coerceIn(0, 255)
        val g = (gA * wA + gB * wB).roundToInt().coerceIn(0, 255)
        val b = (bA * wA + bB * wB).roundToInt().coerceIn(0, 255)
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /**
     * Desaturates an RGB color toward neutral luminance gray (`muteHex`).
     */
    fun muteHex(argb: Long, muteFactor: Float = 0.45f): Long {
        val f = muteFactor.coerceIn(0f, 1f)
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        val gray = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt()
        val outR = (r + (gray - r) * f).roundToInt().coerceIn(0, 255)
        val outG = (g + (gray - g) * f).roundToInt().coerceIn(0, 255)
        val outB = (b + (gray - b) * f).roundToInt().coerceIn(0, 255)
        return (0xFFL shl 24) or (outR.toLong() shl 16) or (outG.toLong() shl 8) or outB.toLong()
    }

    /**
     * Returns high-contrast foreground color (dark ink vs crisp white) for any ARGB background.
     */
    fun contrastTextHex(bgArgb: Long): Long {
        val r = ((bgArgb shr 16) and 0xFF).toInt()
        val g = ((bgArgb shr 8) and 0xFF).toInt()
        val b = (bgArgb and 0xFF).toInt()
        val yiq = (r * 299 + g * 587 + b * 114) / 1000
        return if (yiq >= 145) 0xFF0B101DL else 0xFFF8FAFFL
    }

    /**
     * All 190 unique unordered pairs (C(20, 2) = 190) classified strictly by wheel geometry.
     */
    val all190FusedPairs: List<OptionBFusedPair> by lazy {
        buildList(190) {
            for (i in wheelColors.indices) {
                for (j in (i + 1) until wheelColors.size) {
                    val a = wheelColors[i]
                    val b = wheelColors[j]
                    val dist = minOf(j - i, 20 - (j - i))
                    val category = when {
                        dist == 10 -> PairCategory.OPPOSITES_180
                        a.isAnchor && b.isAnchor -> if (dist == 4) PairCategory.ALLIED_ANCHORS else PairCategory.ENEMY_ANCHORS
                        a.isAnchor || b.isAnchor -> PairCategory.ANCHOR_BRIDGE
                        else -> PairCategory.BRIDGE_BRIDGE
                    }
                    add(OptionBFusedPair(a, b, category, dist))
                }
            }
        }
    }

    /**
     * 17 Curated Layer 3 Option B Showcase Trios (out of 1,140 total possible 3-color trios).
     */
    val curatedLayer3Trios: List<CuratedTrioEntry> = listOf(
        listOf("W", "U", "B"), listOf("U", "B", "R"), listOf("B", "R", "G"),
        listOf("R", "G", "W"), listOf("G", "W", "U"), listOf("W", "B", "G"),
        listOf("U", "R", "W"), listOf("B", "G", "U"), listOf("R", "W", "B"),
        listOf("G", "U", "R"), listOf("W", "Iv", "Cy"), listOf("Cy", "Az", "U"),
        listOf("U", "In", "Vi"), listOf("Vi", "Ob", "B"), listOf("B", "Cr", "Mg"),
        listOf("R", "Or", "Am"), listOf("G", "Ja", "Aq")
    ).mapIndexed { index, codes -> CuratedTrioEntry(index + 1, codes) }

    /**
     * 25 Curated Layer 4–17 Diamond / Option B Mid-to-High Mixture samples (every layer 4..17 represented).
     */
    val layers4To17Samples: List<MidLayerSampleEntry> by lazy {
        buildList(25) {
            var counter = 1
            for (layer in 4..17) {
                val baseSlice = wheelColors.take(layer).map { it.code }
                add(MidLayerSampleEntry(layer, counter++, baseSlice, "Layer $layer Sequential Arc"))
                if (layer <= 14) {
                    val anchorStride = (0 until layer).map { idx -> wheelColors[(idx * 3) % 20].code }.distinct()
                    val padded = if (anchorStride.size == layer) {
                        anchorStride
                    } else {
                        (anchorStride + wheelColors.map { it.code }).distinct().take(layer)
                    }
                    add(MidLayerSampleEntry(layer, counter++, padded, "Layer $layer Stride Harmonic"))
                }
            }
        }
    }

    /**
     * Layer 18 ("All Minus Pair") subtraction symmetry samples (10 diametrically opposed pairs).
     */
    val layer18AllMinusPairSamples: List<Layer18SubtractionEntry> by lazy {
        (0 until 10).map { k ->
            val a = wheelColors[k]
            val b = wheelColors[(k + 10) % 20]
            val included = wheelColors.filter { it.code != a.code && it.code != b.code }.map { it.code }
            Layer18SubtractionEntry(a, b, included)
        }
    }

    /**
     * All 20 Layer 19 ("All Minus One") subtraction combinations.
     */
    val layer19AllMinusOne: List<Layer19SubtractionEntry> by lazy {
        wheelColors.map { omitted ->
            val included = wheelColors.filter { it.code != omitted.code }.map { it.code }
            Layer19SubtractionEntry(omittedColor = omitted, includedCodes = included)
        }
    }

    fun formatCustomLayerPip(selectedCodes: Set<String>): String {
        if (selectedCodes.isEmpty()) return COLORLESS_PIP
        if (selectedCodes.size == wheelColors.size && selectedCodes.containsAll(validColorCodes)) {
            return OMNICHROMA_PIP
        }
        val ordered = wheelColors.filter { it.code in selectedCodes }.map { it.code }
        return if (ordered.isEmpty()) COLORLESS_PIP else "{${ordered.joinToString("/")}}"
    }

    fun formatWeightedLeadPip(leadCode: String, secondaryCode: String): String {
        return "{$leadCode>$secondaryCode}"
    }

    fun formatChoiceSplitPip(leftCode: String, rightCode: String): String {
        return "{$leftCode|$rightCode}"
    }
}
