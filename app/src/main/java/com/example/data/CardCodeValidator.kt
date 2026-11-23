package com.example.data

import java.math.BigInteger

/**
 * Pure Kotlin card-code and client-target validator (zero Android/Compose imports).
 *
 * Enforces:
 * 1. Strict Locked-Client Firewall: blocks writing, 21-digit register binding, or pack exports
 *    targeting locked upstream GitHub clients (`upstreamSourceLock == true`), suggesting a
 *    non-locked 21-digit fork target instead.
 * 2. Strict Time-Travel Client Rule: `allowTimeTravelFlagsOnMatchCards == false` on standard
 *    match cards (only "End Turn" and "Extra Turn" temporal mechanics permitted; Filters 19–21
 *    allowed ONLY in Avatar Profile / Memorial Forge Dossier mode).
 * 3. Arbitrary-length string IDs (8-digit, 21-digit numeric `^[0-9]{1,21}$`, UUIDs) with
 *    native BigInteger math (zero overflow) and strict 22-digit overflow rejection.
 * 4. Unified 20-Color Pie pip grammar accepting `{C}`, `{W}..{Aq}`, BOTH `{A/B}` and `{A·B}`,
 *    `:T/:S/:M` value states, `{A|B}` splits, `{A>B}` & `{2A/B}` weighted leads, `{-X}`,
 *    `{-(A,B)}`, and `{Ω}` + all 6 `{Ω·M..A}` celestial geometries.
 */
enum class CardContextMode(val displayLabel: String) {
    MATCH_CARD("Standard Match Card"),
    AVATAR_DOSSIER("Avatar Profile / Memorial Forge Dossier")
}

data class ClientTargetDescriptor(
    val clientId: String,
    val displayName: String,
    val upstreamSourceLock: Boolean,
    val upstreamRepo: String = "",
    val suggestedNonLockedForkId: String? = null
)

data class CardValidationRequest(
    val cardId: String,
    val cardName: String,
    val targetClient: ClientTargetDescriptor,
    val contextMode: CardContextMode = CardContextMode.MATCH_CARD,
    val pipExpression: String = TwentyColorPieCatalog.COLORLESS_PIP,
    val codingVariableExpression: String = "",
    val requestedFilterNumbers: Set<Int> = emptySet(),
    val temporalEffects: Set<String> = emptySet(),
    val atkRegisterValue: String = "0",
    val defRegisterValue: String = "0"
)

data class CardValidationResult(
    val isValid: Boolean,
    val lockedClientBlocked: Boolean,
    val suggestedForkClientId: String?,
    val normalizedIdKind: String,
    val parsedRegisterBigInt: BigInteger?,
    val errors: List<String>,
    val warnings: List<String>
)

object CardCodeValidator {
    private val NUMERIC_21_REGEX = Regex("^[0-9]{1,21}$")
    private val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val OVERFLOW_22_DIGITS_REGEX = Regex(".*[0-9]{22}.*")
    private val WEIGHTED_PREFIX_REGEX = Regex("^([2-9])([A-Z][a-z]?)$")
    private val ALLOWED_MATCH_TEMPORAL_EFFECTS = setOf("End Turn", "Extra Turn")
    private val DOSSIER_ONLY_FILTERS = setOf(19, 20, 21)

    private val LOCKED_UPSTREAM_IDS = setOf(
        "EDOPRO_BASE_MR5",
        "FORGE_MTG_BASE",
        "COCKATRICE_LOCKED_BASE",
        "XMAGE_LOCKED_BASE",
        "TALISHAR_LOCKED_BASE",
        "KARABAST_LOCKED_BASE",
        "JINTEKI_LOCKED_BASE",
        "PROJECTIGNIS/EDOPRO",
        "CARD-FORGE/FORGE",
        "COCKATRICE/COCKATRICE",
        "MAGEFREE/MAGE",
        "TALISHAR/TALISHAR",
        "SWU-KARABAST/FORCETEKI",
        "MTGRED/NETRUNNER"
    )

    val canonicalTargetCatalog: List<ClientTargetDescriptor> = listOf(
        ClientTargetDescriptor("ULTIMATE_TCG_WIP", "Ultimate TCG (WIP Foundational System)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("EDOPRO_CUSTOM_FORK", "21-Digit EDOPro Custom Clone (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("FORGE_CUSTOM_CLONE", "21-Digit Nested-Planar Forge Clone (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("HYBRID_FORGE_EDOPRO_MIX", "Hybrid Forge + EDOPro 21-Digit Mix (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("NEXUS_WEB_TABLETOP", "Built-In Web Tabletop Blueprint (Non-Locked)", false, "Summer-Warrior/Project-MPV-RP-Master-Hub"),
        ClientTargetDescriptor("NOTEBOOK_DUEL_MONSTERS", "Notebook Duel Monsters Realms (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("NOTEBOOK_NESTED_PLANAR_MTG", "Notebook Nested-Planar MTG Realm (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("NOTEBOOK_PLANET_CRAY_VANGUARD", "Notebook Planet Cray Vanguard Realm (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("TCG_VIDEO_GAME_BLUEPRINT", "TCG Video Games Blueprint (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("MINI_GAMES_BLUEPRINT", "Video-Game Card Mini-Games Blueprint (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("NICHE_DEFUNCT_BLUEPRINT", "Niche, Indie, LCG & Defunct Blueprint (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        ClientTargetDescriptor("AI_USER_COMBO_SANDBOX", "AI & User Cross-TCG Combo Sandbox (Non-Locked)", false, "Summer-Warrior/Tcg-game-engine-test"),
        // Locked Upstream GitHub References (Read-Only)
        ClientTargetDescriptor("EDOPRO_BASE_MR5", "EDOPro (Locked Upstream Reference)", true, "ProjectIgnis/EDOPro", "EDOPRO_CUSTOM_FORK"),
        ClientTargetDescriptor("FORGE_MTG_BASE", "Forge MTG (Locked Upstream Reference)", true, "Card-Forge/forge", "FORGE_CUSTOM_CLONE"),
        ClientTargetDescriptor("COCKATRICE_LOCKED_BASE", "Cockatrice (Locked Upstream Reference)", true, "Cockatrice/Cockatrice", "FORGE_CUSTOM_CLONE"),
        ClientTargetDescriptor("XMAGE_LOCKED_BASE", "XMage (Locked Upstream Reference)", true, "magefree/mage", "FORGE_CUSTOM_CLONE"),
        ClientTargetDescriptor("TALISHAR_LOCKED_BASE", "Talishar (Locked Upstream Reference)", true, "Talishar/Talishar", "NICHE_DEFUNCT_BLUEPRINT"),
        ClientTargetDescriptor("KARABAST_LOCKED_BASE", "Karabast (Locked Upstream Reference)", true, "SWU-Karabast/forceteki", "NEXUS_WEB_TABLETOP"),
        ClientTargetDescriptor("JINTEKI_LOCKED_BASE", "Jinteki.net (Locked Upstream Reference)", true, "mtgred/netrunner", "NICHE_DEFUNCT_BLUEPRINT")
    )

    fun isClientLocked(target: ClientTargetDescriptor): Boolean {
        if (target.upstreamSourceLock) return true
        val normalizedId = target.clientId.trim().uppercase()
        val normalizedRepo = target.upstreamRepo.trim().uppercase()
        return normalizedId in LOCKED_UPSTREAM_IDS || normalizedRepo in LOCKED_UPSTREAM_IDS
    }

    fun isClientIdLocked(clientId: String): Boolean {
        val normalized = clientId.trim().uppercase()
        if (normalized in LOCKED_UPSTREAM_IDS) return true
        return canonicalTargetCatalog.any { it.clientId.equals(normalized, ignoreCase = true) && it.upstreamSourceLock }
    }

    fun validate(request: CardValidationRequest): CardValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Strict Locked-Client Firewall
        val locked = isClientLocked(request.targetClient)
        val suggestedFork = if (locked) {
            request.targetClient.suggestedNonLockedForkId ?: "ULTIMATE_TCG_WIP"
        } else {
            null
        }
        if (locked) {
            errors.add(
                "LOCKED_CLIENT_FIREWALL: '${request.targetClient.displayName}' has upstreamSourceLock=true. " +
                    "Direct card coding and pack export are blocked. Fork to '$suggestedFork' instead."
            )
        }

        // 2. Arbitrary-length ID validation (8-digit, 21-digit numeric ^[0-9]{1,21}$, or UUID) + BigInteger math
        val cleanId = request.cardId.trim()
        var idKind = "INVALID"
        var parsedBigInt: BigInteger? = null
        when {
            cleanId.isEmpty() -> errors.add("Card ID must not be blank.")
            OVERFLOW_22_DIGITS_REGEX.matches(cleanId) -> {
                errors.add("Card ID contains 22+ consecutive digits, violating the 21-digit register boundary.")
            }
            NUMERIC_21_REGEX.matches(cleanId) -> {
                parsedBigInt = BigInteger(cleanId)
                idKind = if (cleanId.length <= 8) "NUMERIC_8_DIGIT_COMPAT" else "NUMERIC_21_DIGIT_REGISTER"
            }
            UUID_REGEX.matches(cleanId) -> {
                idKind = "UUID_V4_STRING"
            }
            else -> {
                errors.add("Card ID must match ^[0-9]{1,21}$ (8-digit or 21-digit numeric) or canonical UUID format.")
            }
        }

        if (request.cardName.isBlank()) {
            errors.add("Card name must not be blank.")
        } else if (request.cardName.length > 120) {
            errors.add("Card name exceeds aligned Firestore bound of 120 characters.")
        }

        // 3. 21-digit coding variable overflow guard (!/.*[0-9]{22}.*/)
        if (request.codingVariableExpression.isNotBlank() &&
            OVERFLOW_22_DIGITS_REGEX.matches(request.codingVariableExpression)
        ) {
            errors.add("Coding variable contains 22+ consecutive digits (forbidden by 21-digit register contract).")
        }

        val cleanAtk = request.atkRegisterValue.trim().ifEmpty { "0" }
        val cleanDef = request.defRegisterValue.trim().ifEmpty { "0" }
        if (!NUMERIC_21_REGEX.matches(cleanAtk)) {
            errors.add("ATK register value must be a non-negative integer up to 21 digits.")
        }
        if (!NUMERIC_21_REGEX.matches(cleanDef)) {
            errors.add("DEF register value must be a non-negative integer up to 21 digits.")
        }

        // 4. Strict Time-Travel Client Rule (allowTimeTravelFlagsOnMatchCards == false)
        if (request.contextMode == CardContextMode.MATCH_CARD) {
            val forbiddenFilters = request.requestedFilterNumbers.intersect(DOSSIER_ONLY_FILTERS)
            if (forbiddenFilters.isNotEmpty()) {
                errors.add(
                    "TIME_TRAVEL_RULE_VIOLATION: Filters ${forbiddenFilters.sorted()} are strictly blocked on Match Cards " +
                        "(allowTimeTravelFlagsOnMatchCards=false). Switch to 'Avatar Profile / Memorial Forge Dossier' mode."
                )
            }
            val forbiddenTemporal = request.temporalEffects.filter { effect ->
                effect.isNotBlank() && ALLOWED_MATCH_TEMPORAL_EFFECTS.none { it.equals(effect.trim(), ignoreCase = true) }
            }
            if (forbiddenTemporal.isNotEmpty()) {
                errors.add(
                    "TIME_TRAVEL_RULE_VIOLATION: Match Cards only permit 'End Turn' and 'Extra Turn' temporal effects " +
                        "(rejected: ${forbiddenTemporal.joinToString(", ")})."
                )
            }
        } else if (request.requestedFilterNumbers.any { it in DOSSIER_ONLY_FILTERS }) {
            warnings.add("Dossier Mode active: Filters 19–21 permitted strictly for Avatar Profile / Memorial Forge Dossier metadata.")
        }

        // 5. Unified 20-Color Pie pip grammar validation
        val pipTrim = request.pipExpression.trim()
        if (pipTrim.isNotEmpty() && !isValidPipGrammar(pipTrim)) {
            errors.add(
                "Invalid 20-Color Pie pip syntax: '$pipTrim'. Use {C}, {W}..{Aq}, {A/B}, {A·B}, {A|B}, {A>B}, {2A/B}, {-X}, {-(A,B)}, or {Ω}/{Ω·M..A}."
            )
        }

        return CardValidationResult(
            isValid = errors.isEmpty(),
            lockedClientBlocked = locked,
            suggestedForkClientId = suggestedFork,
            normalizedIdKind = idKind,
            parsedRegisterBigInt = parsedBigInt,
            errors = errors,
            warnings = warnings
        )
    }

    fun isValidPipGrammar(expression: String): Boolean {
        val trimmed = expression.trim()
        val tokens = Regex("\\{[^}]+\\}").findAll(trimmed).map { it.value }.toList()
        if (tokens.isEmpty()) return false
        // Ensure no stray unbraced garbage outside whitespace
        val stripped = trimmed.replace(Regex("\\{[^}]+\\}"), "").trim()
        if (stripped.isNotEmpty()) return false

        return tokens.all { token ->
            val inner = token.removePrefix("{").removeSuffix("}")
            when {
                inner == "C" -> true
                inner == "Ω" -> true
                inner.startsWith("Ω·") -> {
                    val suffix = inner.removePrefix("Ω·")
                    suffix in TwentyColorPieCatalog.validOmniSuffixes
                }
                inner.startsWith("-(") && inner.endsWith(")") -> {
                    val parts = inner.removePrefix("-(").removeSuffix(")").split(",")
                    parts.size == 2 && parts.all { it in TwentyColorPieCatalog.validColorCodes }
                }
                inner.startsWith("-") -> inner.removePrefix("-") in TwentyColorPieCatalog.validColorCodes
                else -> {
                    val parts = inner.split("/", "·", "|", ">")
                    parts.isNotEmpty() && parts.all { part ->
                        val basePart = part.substringBefore(":")
                        val suffix = if (":" in part) part.substringAfter(":") else null
                        val suffixValid = suffix == null || suffix in setOf("T", "S", "M")
                        val colorValid = if (basePart in TwentyColorPieCatalog.validColorCodes) {
                            true
                        } else {
                            val match = WEIGHTED_PREFIX_REGEX.matchEntire(basePart)
                            match != null && match.groupValues[2] in TwentyColorPieCatalog.validColorCodes
                        }
                        colorValid && suffixValid
                    }
                }
            }
        }
    }

    fun generateLuaScriptOrError(request: CardValidationRequest): Result<Pair<String, String>> {
        val validation = validate(request)
        if (!validation.isValid) {
            return Result.failure(IllegalStateException(validation.errors.joinToString("\n")))
        }
        val numericId = validation.parsedRegisterBigInt?.toString()
            ?: request.cardId.replace("-", "").take(21)
        val padded21 = numericId.padStart(21, '0')
        val filename = "c$padded21.lua"
        val safeName = request.cardName.replace("\"", "'")
        val script = buildString {
            appendLine("-- NexusTCG Non-Locked 21-Digit Card Script")
            appendLine("-- Target Client: ${request.targetClient.clientId} (upstreamSourceLock = false)")
            appendLine("-- Card Name: $safeName | Pip Cost: ${request.pipExpression}")
            appendLine("-- allowTimeTravelFlagsOnMatchCards = false")
            appendLine("local s, id = GetID()")
            appendLine("s.card_id_string = \"${request.cardId.trim()}\"")
            appendLine("s.register_21_digit = \"$padded21\"")
            appendLine("s.pip_cost = \"${request.pipExpression.trim()}\"")
            appendLine("s.atk_register = \"${request.atkRegisterValue.trim().ifEmpty { "0" }}\"")
            appendLine("s.def_register = \"${request.defRegisterValue.trim().ifEmpty { "0" }}\"")
            appendLine("function s.initial_effect(c)")
            appendLine("    -- Non-locked effect registration bound to ${request.targetClient.clientId}")
            appendLine("end")
        }
        return Result.success(filename to script)
    }
}
