package com.example.data

/**
 * Canonical 20-Color Pie Full Progression (Layers 0 to 20 & Unique Omni Pip {Ω}).
 *
 * Design invariants:
 * - Zero default colors selected on load (`defaultSelectedCodes = emptySet()`).
 * - Always displays all pips unless the user actively filters by selecting 1..20 colors.
 * - Strictly color geometry and pip syntax only — zero invented philosophies, emotions,
 *   or moral alignments are attached to any color.
 * - Layers 1–19 follow Diamond / Option B Format; Layer 20 uses the bespoke Unique Omni Pip
 *   "Omnichroma" {Ω}.
 */
enum class PairCategory(val displayName: String, val expectedCount: Int) {
    ALLIED_ANCHORS("Allied Anchors", 5),
    ENEMY_ANCHORS("Enemy Anchors", 5),
    OPPOSITES_180("180° Opposites", 10),
    ANCHOR_BRIDGE("Anchor–Bridge", 75),
    BRIDGE_BRIDGE("Bridge–Bridge", 105)
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
}

data class OptionBFusedPair(
    val first: PieColorEntry,
    val second: PieColorEntry,
    val category: PairCategory,
    val wheelDistance: Int
) {
    val pip: String get() = "{${first.code}/${second.code}}"
    val label: String get() = "${first.name} / ${second.name}"
    val codes: Set<String> get() = setOf(first.code, second.code)
}

data class CuratedTrioEntry(
    val index: Int,
    val codes: List<String>
) {
    val pip: String get() = "{${codes.joinToString("/")}}"
}

data class Layer19SubtractionEntry(
    val omittedColor: PieColorEntry,
    val includedCodes: List<String>
) {
    val pip: String get() = "{-${omittedColor.code}}"
    val label: String get() = "All Minus ${omittedColor.name}"
}

object TwentyColorPieCatalog {
    const val COLORLESS_PIP: String = "{C}"
    const val OMNICHROMA_PIP: String = "{Ω}"
    const val OMNICHROMA_NAME: String = "Omnichroma"

    val defaultSelectedCodes: Set<String> = emptySet()

    val omniVariants: List<Pair<String, String>> = listOf(
        "{Ω}" to "Omnichroma Base Prism",
        "{Ω·M}" to "Omnichroma Mandala",
        "{Ω·P}" to "Omnichroma Prism",
        "{Ω·S}" to "Omnichroma Solar",
        "{Ω·E}" to "Omnichroma Eclipse"
    )

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
