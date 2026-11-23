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
    private val ALLOWED_MATCH_TEMPORAL_EFFECTS = setOf("End Turn", "Extra Turn")
    private val DOSSIER_ONLY_FILTERS = setOf(19, 20, 21)

    private val LOCKED_UPSTREAM_IDS = setOf(
        "EDOPRO_BASE_MR5",
        "FORGE_MTG_BASE",
        "PROJECTIGNIS/EDOPRO",
        "CARD-FORGE/FORGE"
    )

    val canonicalTargetCatalog: List<ClientTargetDescriptor> = listOf(
        ClientTargetDescriptor(
            clientId = "ULTIMATE_TCG_21DIGIT_FORK",
            displayName = "Ultimate TCG (21-Digit Non-Locked Fork)",
            upstreamSourceLock = false,
            upstreamRepo = "Summer-Warrior/Tcg-game-engine-test"
        ),
        ClientTargetDescriptor(
            clientId = "NEXUS_21DIGIT_YGO_CLONE",
            displayName = "21-Digit Custom YGO Clone (Non-Locked)",
            upstreamSourceLock = false,
            upstreamRepo = "Summer-Warrior/Tcg-game-engine-test"
        ),
        ClientTargetDescriptor(
            clientId = "NEXUS_21DIGIT_MTG_CLONE",
            displayName = "21-Digit Custom Nested-Planar MTG Clone (Non-Locked)",
            upstreamSourceLock = false,
            upstreamRepo = "Summer-Warrior/Tcg-game-engine-test"
        ),
        ClientTargetDescriptor(
            clientId = "NEXUS_BUILT_IN_WEB_TABLETOP",
            displayName = "Built-In Web Tabletop Blueprint (Non-Locked)",
            upstreamSourceLock = false,
            upstreamRepo = "Summer-Warrior/Project-MPV-RP-Master-Hub"
        ),
        ClientTargetDescriptor(
            clientId = "EDOPRO_BASE_MR5",
            displayName = "EDOPro (Locked Upstream Reference Only)",
            upstreamSourceLock = true,
            upstreamRepo = "ProjectIgnis/EDOPro",
            suggestedNonLockedForkId = "NEXUS_21DIGIT_YGO_CLONE"
        ),
        ClientTargetDescriptor(
            clientId = "FORGE_MTG_BASE",
            displayName = "Forge MTG (Locked Upstream Reference Only)",
            upstreamSourceLock = true,
            upstreamRepo = "Card-Forge/forge",
            suggestedNonLockedForkId = "NEXUS_21DIGIT_MTG_CLONE"
        )
    )

    fun isClientLocked(target: ClientTargetDescriptor): Boolean {
        if (target.upstreamSourceLock) return true
        val normalizedId = target.clientId.trim().uppercase()
        val normalizedRepo = target.upstreamRepo.trim().uppercase()
        return normalizedId in LOCKED_UPSTREAM_IDS || normalizedRepo in LOCKED_UPSTREAM_IDS
    }

    fun validate(request: CardValidationRequest): CardValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Strict Locked-Client Firewall
        val locked = isClientLocked(request.targetClient)
        val suggestedFork = if (locked) {
            request.targetClient.suggestedNonLockedForkId ?: "ULTIMATE_TCG_21DIGIT_FORK"
        } else {
            null
        }
        if (locked) {
            errors.add(
                "LOCKED_CLIENT_FIREWALL: '${request.targetClient.displayName}' has upstreamSourceLock=true. " +
                    "Direct card coding and pack export are blocked. Target non-locked fork '$suggestedFork' instead."
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
                idKind = when {
                    cleanId.length <= 8 -> "NUMERIC_8_DIGIT_COMPAT"
                    else -> "NUMERIC_21_DIGIT_REGISTER"
                }
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
        }

        // 3. 21-digit coding variable overflow guard (!/.*[0-9]{22}.*/)
        if (request.codingVariableExpression.isNotBlank() &&
            OVERFLOW_22_DIGITS_REGEX.matches(request.codingVariableExpression)
        ) {
            errors.add("Coding variable contains 22+ consecutive digits (forbidden by 21-digit register contract).")
        }

        // Validate ATK / DEF as BigInteger with zero overflow
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

        // 5. Pip grammar validation
        val pipTrim = request.pipExpression.trim()
        if (pipTrim.isNotEmpty() && !isValidPipGrammar(pipTrim)) {
            errors.add("Invalid 20-Color Pie pip syntax: '$pipTrim'. Use {C}, {W}..{Aq}, {A/B}, {A|B}, {A>B}, {-X}, or {Ω}.")
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
        val tokens = Regex("\\{[^}]+\\}").findAll(expression).map { it.value }.toList()
        if (tokens.isEmpty()) return false
        return tokens.all { token ->
            val inner = token.removePrefix("{").removeSuffix("}")
            when {
                inner == "C" -> true
                inner == "Ω" || inner.startsWith("Ω·") -> true
                inner.startsWith("-(") && inner.endsWith(")") -> {
                    val parts = inner.removePrefix("-(").removeSuffix(")").split(",")
                    parts.size == 2 && parts.all { it in TwentyColorPieCatalog.validColorCodes }
                }
                inner.startsWith("-") -> inner.removePrefix("-") in TwentyColorPieCatalog.validColorCodes
                else -> {
                    val parts = inner.split("/", "|", ">")
                    parts.isNotEmpty() && parts.all { part ->
                        val base = part.substringBefore(":")
                        val suffix = if (":" in part) part.substringAfter(":") else null
                        base in TwentyColorPieCatalog.validColorCodes && (suffix == null || suffix in setOf("T", "S", "M"))
                    }
                }
            }
        }
    }

    /**
     * Generates a non-locked `c<21-digits>.lua` script stub only when validation passes.
     */
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
