package com.example.data

import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.math.BigInteger

/**
 * 21-Digit Arbitrary-Precision Variable, Function & Code-String Engine for the EDOPro Custom Project Clone.
 * Enforces a 21-numerical-digit limit (0 .. 999,999,999,999,999,999,999 = 10^21 - 1) on:
 *  - Numerical variables (s.var_input_21)
 *  - Function inputs & outputs (s.func_arg_21, s.func_result_21)
 *  - Coding variables for code strings (customCodeString21 & customCodeVarExpr21)
 */
object BigInt21Engine {
    const val MAX_DIGITS = 21
    const val MAX_21_DIGIT_STRING = "999999999999999999999"
    const val DEFAULT_CODE_STRING_21 = "900001010000000000021"
    const val DEFAULT_CODE_VAR_EXPR_21 =
        "s.code_var_21 = \"900001010000000000021\"; s.setcode_21 = \"123456789012345678901\""
    const val DEFAULT_FORGE_CUSTOM_VAR_EXPR_21 =
        "SVar:X:21d=\"900001010000000000021\"; SVar:Power21=\"123456789012345678901\""
    const val DEFAULT_HYBRID_MIX_VAR_EXPR_21 =
        "s.code_var_21 = \"900001010000000000021\"; s.mix_mods = \"CardForge_MTG_Core|EDOPro_21d_Clone|Stack_Chain_Bridge\""

    val MAX_21_BIGINT: BigInteger = BigInteger(MAX_21_DIGIT_STRING)
    val MIN_21_BIGINT: BigInteger = BigInteger.ZERO
    private val MAX_SAFE_FIRESTORE_INT: BigInteger = BigInteger("999999999999999999")
    private val DIGIT_RUN_REGEX = Regex("[0-9]+")
    private val CODE_VAR_ASSIGN_REGEX = Regex("([a-zA-Z_][a-zA-Z0-9_.]*)\\s*=\\s*\"?([0-9]{1,21})\"?")
    private val MIX_MODS_REGEX = Regex("s\\.mix_mods\\s*=\\s*\"([^\"]+)\"")

    val defaultHybridModules: List<String> = listOf(
        "CardForge_MTG_Core",
        "EDOPro_21d_Clone",
        "Stack_Chain_Bridge"
    )

    val presetMixExtensions: List<String> = listOf(
        "DuelLinks_Character_Skills",
        "MasterDuel_ChainToggle_Mate",
        "CrossDuel_3Lane_Boss_Skills",
        "MTGO_ChessClock_F6_Stops",
        "MTGArena_AutoTap_FullControl",
        "Commander_100_EDH_Zone",
        "Pendulum_Mana_Conduit",
        "Planeswalker_Link_Arrows"
    )

    val supportedFunctions: List<String> = listOf(
        "ADD_21",
        "MUL_21",
        "SUB_21",
        "MOD_21",
        "SCALE_ATK_LP_21"
    )

    fun filterDigitsInput(raw: String): String {
        return raw.filter { it.isDigit() }.take(MAX_DIGITS)
    }

    fun sanitizeTo21Digits(raw: String, fallback: String = "8000"): String {
        val digitsOnly = raw.filter { it.isDigit() }.take(MAX_DIGITS)
        if (digitsOnly.isEmpty()) return fallback
        val normalized = digitsOnly.trimStart('0').ifEmpty { "0" }
        return normalized.take(MAX_DIGITS)
    }

    /**
     * Enforces the 21-numerical-digit limit on every coding variable / numerical literal
     * inside a code string (e.g. `s.code_var_21 = "999999999999999999999"`).
     * Any contiguous sequence of digits longer than 21 digits is truncated to 21 digits.
     */
    fun filterCodeStringWith21DigitLimit(rawCodeStr: String): String {
        val cappedNumbers = DIGIT_RUN_REGEX.replace(rawCodeStr) { match ->
            match.value.take(MAX_DIGITS)
        }
        return cappedNumbers.take(240)
    }

    fun sanitizeCodeVarExpr21(
        rawCodeStr: String,
        fallbackCode21: String = DEFAULT_CODE_STRING_21
    ): String {
        val trimmed = filterCodeStringWith21DigitLimit(rawCodeStr).trim()
        if (trimmed.isEmpty()) {
            val cleanFallback = sanitizeTo21Digits(fallbackCode21, DEFAULT_CODE_STRING_21)
            return "s.code_var_21 = \"$cleanFallback\"; s.setcode_21 = \"123456789012345678901\""
        }
        return trimmed
    }

    /**
     * Extracts the list of extensible modules added to a Mixed (Forge + EDOPro Custom Clone) Client.
     */
    fun extractMixedClientModules(codeVarExpr21: String): List<String> {
        val match = MIX_MODS_REGEX.find(codeVarExpr21)
        if (match != null) {
            val parsed = match.groupValues[1]
                .split("|")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (parsed.isNotEmpty()) return parsed
        }
        return defaultHybridModules
    }

    /**
     * Appends a new module, custom zone, rule hook, or client extension into the Mixed Client's
     * `customCodeVarExpr21` while strictly respecting Firestore's 240-char & 21-digit rule limits.
     */
    fun appendMixedClientModule(
        currentExpr21: String,
        newModuleRaw: String,
        codeString21: String = DEFAULT_CODE_STRING_21
    ): String {
        val cleanModule = newModuleRaw
            .trim()
            .replace("\"", "")
            .replace("|", "_")
            .replace(";", "_")
            .replace(" ", "_")
            .take(32)
            .ifBlank { "Custom_Mix_Ext" }
        val cleanCode = sanitizeTo21Digits(codeString21, DEFAULT_CODE_STRING_21)
        val existing = extractMixedClientModules(currentExpr21).toMutableList()
        if (cleanModule !in existing) {
            existing.add(cleanModule)
        }
        while (existing.size > 2) {
            val candidate = "s.code_var_21 = \"$cleanCode\"; s.mix_mods = \"${existing.joinToString("|")}\""
            if (candidate.length <= 235) {
                return filterCodeStringWith21DigitLimit(candidate)
            }
            // Keep the two foundational clones (CardForge_MTG_Core & EDOPro_21d_Clone) and trim oldest extra if needed
            if (existing.size > 3) {
                existing.removeAt(2)
            } else {
                break
            }
        }
        val built = "s.code_var_21 = \"$cleanCode\"; s.mix_mods = \"${existing.joinToString("|")}\""
        return filterCodeStringWith21DigitLimit(built)
    }

    /**
     * Extracts coding variable names and their 1–21 digit numerical values from a code string.
     */
    fun extractCodingVariables21(codeStr: String): List<Pair<String, String>> {
        val sanitized = filterCodeStringWith21DigitLimit(codeStr)
        val matches = CODE_VAR_ASSIGN_REGEX.findAll(sanitized).map { m ->
            m.groupValues[1] to m.groupValues[2].take(MAX_DIGITS)
        }.toList()
        if (matches.isNotEmpty()) return matches
        val rawNumbers = DIGIT_RUN_REGEX.findAll(sanitized).mapIndexed { idx, m ->
            "code_var_${idx + 1}" to m.value.take(MAX_DIGITS)
        }.toList()
        return rawNumbers
    }

    fun parse21DigitBigInt(raw: String, fallback: String = "8000"): BigInteger {
        val clean = sanitizeTo21Digits(raw, fallback)
        return runCatching { BigInteger(clean).coerceIn(MIN_21_BIGINT, MAX_21_BIGINT) }
            .getOrDefault(BigInteger(fallback))
    }

    fun evaluateFunction21(funcName: String, varA21: String, argB21: String): String {
        val a = parse21DigitBigInt(varA21, "0")
        val b = parse21DigitBigInt(argB21, "0")
        val rawResult: BigInteger = when (funcName) {
            "ADD_21" -> a.add(b)
            "MUL_21" -> a.multiply(b)
            "SUB_21" -> a.subtract(b).max(MIN_21_BIGINT)
            "MOD_21" -> {
                val divisor = if (b <= MIN_21_BIGINT) BigInteger.ONE else b
                a.mod(divisor)
            }
            "SCALE_ATK_LP_21" -> a.multiply(BigInteger.valueOf(2L)).add(b)
            else -> a.add(b)
        }
        return rawResult.coerceIn(MIN_21_BIGINT, MAX_21_BIGINT).toString()
    }

    fun formatWithCommas(raw21: String): String {
        val clean = sanitizeTo21Digits(raw21, "0")
        return clean.reversed().chunked(3).joinToString(",").reversed()
    }

    fun toSafeFirestoreLong(raw21: String, minVal: Long = 1L): Long {
        val big = parse21DigitBigInt(raw21, minVal.toString())
        val clamped = big.coerceIn(BigInteger.valueOf(minVal), MAX_SAFE_FIRESTORE_INT)
        return clamped.toLong()
    }
}

@IgnoreExtraProperties
data class TcgVariant(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val baseVariantId: String = "",
    val resourceName: String = "",
    val startingLife: Long = 8000L,
    val startingHandSize: Long = 5L,
    val maxResourcePool: Long = 5L,
    val combatRule: String = "ATK_DEF_BATTLE",
    val isBaseStandard: Boolean = true,
    val protocolHash: String = "",
    val maxDigitPrecision: Long = 21L,
    val startingLp21: String = "8000",
    val customVarInput21: String = "100000000000000000000",
    val customFuncInput21: String = "899999999999999999999",
    val customFuncName: String = "ADD_21",
    val customFuncResult21: String = "999999999999999999999",
    val customCodeString21: String = BigInt21Engine.DEFAULT_CODE_STRING_21,
    val customCodeVarExpr21: String = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ClientNode(
    @DocumentId val id: String = "",
    val userId: String = "",
    val clientName: String = "",
    val clientType: String = "EDOPro Base Client",
    val endpointUri: String = "",
    val baseVariantId: String = "",
    val baseVariantName: String = "",
    val protocolVersion: String = "40.1.4",
    val requiresStrictBase: Boolean = true,
    val connectionStatus: String = "DISCONNECTED",
    val latencyMs: Long = 18L,
    val maxDigitPrecision: Long = 21L,
    val customVarInput21: String = "8000",
    val customFuncInput21: String = "0",
    val customFuncName: String = "ADD_21",
    val customFuncResult21: String = "8000",
    val customCodeString21: String = BigInt21Engine.DEFAULT_CODE_STRING_21,
    val customCodeVarExpr21: String = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CustomDeck(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val baseVariantId: String = "",
    val strategyTag: String = "Master Rule 5",
    val cardIds: List<String> = emptyList(),
    val customCardName: String = "",
    val customCardCost: Long = 8L,
    val customCardAttack: Long = 3000L,
    val customCardDefense: Long = 2500L,
    val customAttack21: String = "3000",
    val customDefense21: String = "2500",
    val customVarInput21: String = "999999999999999999999",
    val customCodeString21: String = BigInt21Engine.DEFAULT_CODE_STRING_21,
    val customCodeVarExpr21: String = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21,
    val customCardAbility: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MatchSession(
    @DocumentId val id: String = "",
    val hostUserId: String = "",
    val hostName: String = "",
    val title: String = "",
    val baseVariantId: String = "",
    val baseVariantName: String = "",
    val resourceName: String = "",
    val connectedClients: List<String> = emptyList(),
    val turnNumber: Long = 1L,
    val activePhase: String = "MAIN",
    val hostLife: Long = 8000L,
    val opponentLife: Long = 8000L,
    val hostResources: Long = 5L,
    val hostMaxResources: Long = 5L,
    val opponentResources: Long = 5L,
    val hostBoardCards: List<String> = emptyList(),
    val opponentBoardCards: List<String> = emptyList(),
    val actionLog: List<String> = emptyList(),
    val status: String = "OPEN_LOBBY",
    val winnerName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DiscordServerHub(
    @DocumentId val id: String = "",
    val userId: String = "",
    val guildId: String = "",
    val serverName: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val baseVariantId: String = "EDOPRO_BASE_MR5",
    val rpLoreSetting: String = "",
    val connectionMode: String = "CLOUD_RP_RELAY",
    val isConnected: Boolean = true,
    val memberCount: Long = 128L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RpCharacterPersona(
    @DocumentId val id: String = "",
    val userId: String = "",
    val characterName: String = "",
    val characterTitle: String = "",
    val factionOrArchetype: String = "",
    val baseVariantId: String = "EDOPRO_BASE_MR5",
    val signatureCardName: String = "",
    val signatureCodeString21: String = BigInt21Engine.DEFAULT_CODE_STRING_21,
    val currentLp21: String = "8000",
    val personalityPrompt: String = "",
    val openingCatchphrase: String = "",
    val summonChant: String = "",
    val isActivePersona: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DiscordRpMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val serverId: String = "",
    val channelName: String = "",
    val senderName: String = "",
    val senderTitle: String = "",
    val isCharacterProxy: Boolean = false,
    val messageType: String = "RP_DIALOGUE",
    val content: String = "",
    val tcgActionSummary: String = "",
    val baseVariantId: String = "EDOPRO_BASE_MR5",
    val deliveryStatus: String = "SYNCED_RP_CHANNEL",
    val reviewedFlag: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class BaseVariantSpec(
    val baseVariantId: String,
    val name: String,
    val tagline: String,
    val sourceRepoUrl: String,
    val engineCoreLabel: String,
    val isCustomProjectClone: Boolean,
    val maxDigitPrecision: Int,
    val resourceName: String,
    val startingLife: Long,
    val startingHandSize: Long,
    val maxResourcePool: Long,
    val combatRule: String,
    val protocolVersion: String,
    val accentHex: Long,
    val zones: List<String>,
    val phases: List<String>
) {
    val protocolHash: String
        get() = "$baseVariantId:$startingLife:$startingHandSize:$maxResourcePool:$combatRule"
}

/**
 * Defines the dynamic UI chrome, board layout, terminology, and multi-window launcher profile
 * when switching the UI to a specific TCG Client's system, incorporating aspects from:
 * - Yu-Gi-Oh! Duel Links (Character Skills, Speed Duel 3-Zone toggle & Cut-in Chants)
 * - Yu-Gi-Oh! Master Duel (Chain Response AUTO/ON/OFF toggle, Companion Mate & Royal Finish)
 * - Yu-Gi-Oh! Cross Duel (3-Lane Tactical Movement [Left/Center/Right] & Ace Monster 3-Skill Slots)
 * - Magic: The Gathering Online / MTGO (25:00 Chess Clocks, Phase Stop Checkboxes, F2/F6/F8 Priority & Mana Pool Pips)
 * - Magic: The Gathering Arena / MTGA (Auto-Tapper, Full Control Hold Priority, Rope Timeouts & Emote Wheel)
 */
data class ClientSystemUiProfile(
    val baseVariantId: String,
    val clientDisplayName: String,
    val systemTitle: String,
    val hudBadgeLabel: String,
    val lifeLabel: String,
    val resourceLabel: String,
    val deployVerb: String,
    val combatVerb: String,
    val boardLayoutType: String,
    val fieldSlotsTopLabel: String,
    val fieldSlotsBottomLabel: String,
    val fieldSlotCodes: List<String>,
    val spellOrSupportSlotCodes: List<String>,
    val phaseStripCodes: List<String>,
    val windowModeTitle: String,
    val endpointOrRepoUri: String,
    val accentHex: Long,
    val supports21DigitEngine: Boolean,
    val inspiredSystemsSummary: String = "Duel Links • Master Duel • Cross Duel • MTGO • MTG Arena",
    // Yu-Gi-Oh! Duel Links Aspects
    val duelLinksSkillName: String = "Destiny Draw",
    val duelLinksSkillEffect: String = "Add 1 signature card from Deck & grant +500 ATK this turn",
    val speedDuelSlotCodes: List<String> = listOf("SD-MMZ-1", "SD-MMZ-2", "SD-MMZ-3"),
    // Yu-Gi-Oh! Master Duel Aspects
    val masterDuelMateName: String = "Blue-Eyes Sovereign Mate",
    val masterDuelFoilFinish: String = "Royal Finish (Prismatic Foil)",
    val masterDuelChainModes: List<String> = listOf("AUTO", "ON", "OFF"),
    // Yu-Gi-Oh! Cross Duel Aspects
    val crossDuelLanes: List<String> = listOf("LEFT LANE", "CENTER BOSS LANE", "RIGHT LANE"),
    val crossDuelAceSkills: List<String> = listOf(
        "Master: Awakening Pierce",
        "Slot 2: Lane Haste +1",
        "Slot 3: Shield Matrix +500"
    ),
    // Magic: The Gathering Online (MTGO) Aspects
    val mtgoChessClockDefault: String = "25:00",
    val mtgoPriorityShortcuts: List<String> = listOf(
        "F2: Pass Priority (OK)",
        "F6: Yield Through Turn",
        "F8: Auto-Yield No Play"
    ),
    val mtgoManaPoolPips: List<String> = listOf("{W}", "{U}", "{B}", "{R}", "{G}", "{C}"),
    // Magic: The Gathering Arena (MTGA) Aspects
    val mtgaFeatures: List<String> = listOf("Auto-Tapper", "Full Control (Hold Priority)", "Rope x2 Timeouts"),
    val mtgaEmotes: List<String> = listOf("Hello!", "Your Go", "Thinking...", "Nice!", "Good Game")
)


data class CardTemplate(
    val id: String,
    val passcode: String,
    val name: String,
    val baseVariantId: String,
    val cardType: String,
    val cost: Int,
    val attack: Int,
    val defense: Int,
    val attack21: String = attack.toString(),
    val defense21: String = defense.toString(),
    val abilityText: String,
    val artResId: Int,
    val cardOrigin: CardOrigin = CardOrigin.CANON_ORIGINAL,
    val linkedCanonCardId: String = "",
    val splitOffSetId: String = "",
    val parentOfficialSetId: String = "",
    val pipCost: String = TwentyColorPieCatalog.COLORLESS_PIP
) {
    fun toBoardDescriptor(): String =
        "${sanitizeField(name)}|$cost|$attack|$defense|${sanitizeField(cardType)}|${sanitizeField(abilityText)}"

    fun toV2BoardDescriptor(
        pips: String = TwentyColorPieCatalog.COLORLESS_PIP,
        effectType: String = "RESOURCE_BOOST"
    ): String {
        val safeBase = sanitizeField(baseVariantId)
        val safeCode = sanitizeField(passcode)
        val safeName = sanitizeField(name)
        val safeType = sanitizeField(cardType)
        val safePips = sanitizeField(pips)
        val safeStats = "${sanitizeField(attack21)}/${sanitizeField(defense21)}"
        val safeEffect = sanitizeField(effectType)
        val safeAbility = sanitizeField(abilityText)
        return "@V2|$safeBase|$safeCode|$safeName|$safeType|$safePips|$safeStats|$safeEffect|$safeAbility"
    }

    companion object {
        fun sanitizeField(raw: String): String =
            raw.replace("|", "/").replace("\n", " ").replace("\r", " ").trim()

        fun fromBoardDescriptor(descriptor: String, baseVariantId: String): CardTemplate {
            val cleanDescriptor = if (descriptor.startsWith("@Z:")) {
                val pipeIdx = descriptor.indexOf('|')
                if (pipeIdx >= 0 && pipeIdx + 1 < descriptor.length) {
                    descriptor.substring(pipeIdx + 1)
                } else {
                    descriptor
                }
            } else {
                descriptor
            }

            // Unified @V2 format: @V2|baseVariantId|code|name|type|pips|atk/def|effectType|ability
            if (cleanDescriptor.startsWith("@V2|")) {
                val v2Parts = cleanDescriptor.split("|")
                val v2Base = v2Parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: baseVariantId
                val v2Code = v2Parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: BigInt21Engine.DEFAULT_CODE_STRING_21
                val v2Name = v2Parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "Nexus Sovereign Card"
                val v2Type = v2Parts.getOrNull(4)?.takeIf { it.isNotBlank() } ?: "Custom Unit"
                val v2Pips = v2Parts.getOrNull(5)?.takeIf { it.isNotBlank() } ?: TwentyColorPieCatalog.COLORLESS_PIP
                val statsToken = v2Parts.getOrNull(6).orEmpty()
                val atk21 = statsToken.substringBefore("/", "2500").filter { it.isDigit() }.take(21).ifEmpty { "2500" }
                val def21 = statsToken.substringAfter("/", "2000").filter { it.isDigit() }.take(21).ifEmpty { "2000" }
                val atkInt = atk21.toLongOrNull()?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 2500
                val defInt = def21.toLongOrNull()?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 2000
                val v2Effect = v2Parts.getOrNull(7)?.takeIf { it.isNotBlank() } ?: ""
                val v2AbilityRaw = v2Parts.drop(8).joinToString(" / ").trim()
                val v2Ability = buildString {
                    if (v2Pips != TwentyColorPieCatalog.COLORLESS_PIP) append("[$v2Pips] ")
                    if (v2Effect.isNotEmpty()) append("($v2Effect) ")
                    append(v2AbilityRaw.ifEmpty { "Synced @V2 Card Descriptor" })
                }.trim()
                val art = when {
                    v2Name.contains("Dragon", ignoreCase = true) || v2Base == "EDOPRO_BASE_MR5" || v2Base == "FORGE_MTG_BASE" -> R.drawable.img_card_arcane
                    v2Base == "ULTIMATE_TCG_WIP" || v2Base == "EDOPRO_CUSTOM_FORK" || v2Base == "HYBRID_FORGE_EDOPRO_MIX" -> R.drawable.img_card_void
                    else -> R.drawable.img_card_mecha
                }
                return CardTemplate(
                    id = v2Name.lowercase().replace(" ", "_"),
                    passcode = v2Code,
                    name = v2Name,
                    baseVariantId = v2Base,
                    cardType = v2Type,
                    cost = v2Pips.count { it == '{' }.coerceIn(0, 12),
                    attack = atkInt,
                    defense = defInt,
                    attack21 = atk21,
                    defense21 = def21,
                    abilityText = v2Ability,
                    artResId = art
                )
            }

            // Legacy format: name|cost|atk|def|type|ability[|atk21|def21|passcode]
            val parts = cleanDescriptor.split("|")
            val name = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Ignis Sovereign Dragon"
            val cost = parts.getOrNull(1)?.toIntOrNull() ?: 4
            val attack = parts.getOrNull(2)?.toIntOrNull() ?: 2500
            val defense = parts.getOrNull(3)?.toIntOrNull() ?: 2000
            val type = parts.getOrNull(4)?.takeIf { it.isNotBlank() } ?: "Monster / Effect"
            val ability = parts.getOrNull(5)?.takeIf { it.isNotBlank() } ?: "Quick Effect (Chain Link 2)"
            val parsedAtk21 = parts.getOrNull(6)?.takeIf { it.isNotBlank() } ?: attack.toString()
            val parsedDef21 = parts.getOrNull(7)?.takeIf { it.isNotBlank() } ?: defense.toString()
            val parsedPasscode = parts.getOrNull(8)?.takeIf { it.isNotBlank() }
                ?: if (BuiltInTcgCatalog.supports21DigitRegisters(baseVariantId)) {
                    BigInt21Engine.DEFAULT_CODE_STRING_21
                } else {
                    "89631139"
                }
            val art = when {
                name.contains("Dragon", ignoreCase = true) ||
                    name.contains("Lotus", ignoreCase = true) ||
                    name.contains("Serra", ignoreCase = true) ||
                    baseVariantId == "EDOPRO_BASE_MR5" ||
                    baseVariantId == "FORGE_MTG_BASE" -> R.drawable.img_card_arcane
                name.contains("Monolith", ignoreCase = true) ||
                    name.contains("Spell", ignoreCase = true) ||
                    baseVariantId == "EDOPRO_CUSTOM_FORK" ||
                    baseVariantId == "HYBRID_FORGE_EDOPRO_MIX" -> R.drawable.img_card_void
                else -> R.drawable.img_card_mecha
            }
            return CardTemplate(
                id = name.lowercase().replace(" ", "_"),
                passcode = parsedPasscode,
                name = name,
                baseVariantId = baseVariantId,
                cardType = type,
                cost = cost,
                attack = attack,
                defense = defense,
                attack21 = parsedAtk21,
                defense21 = parsedDef21,
                abilityText = ability,
                artResId = art
            )
        }
    }
}

object BuiltInTcgCatalog {
    val baseVariants: List<BaseVariantSpec> = listOf(
        BaseVariantSpec(
            baseVariantId = "EDOPRO_BASE_MR5",
            name = "ProjectIgnis: EDOPro Base (Yu-Gi-Oh!)",
            tagline = "Official ProjectIgnis/EDOPro base client protocol (8000 LP, Master Rule 5, ocgcore + BabelCDB)",
            sourceRepoUrl = "https://github.com/ProjectIgnis/EDOPro",
            engineCoreLabel = "ocgcore C++ / Lua 5.3 • cards.cdb • CTOS/STOC",
            isCustomProjectClone = false,
            maxDigitPrecision = 10,
            resourceName = "Summon/Chain",
            startingLife = 8000L,
            startingHandSize = 5L,
            maxResourcePool = 5L,
            combatRule = "ATK_DEF_BATTLE",
            protocolVersion = "40.1.4",
            accentHex = 0xFF00E5FF,
            zones = listOf(
                "Main Deck (40-60)",
                "Extra Deck (0-15)",
                "Main Monster Zones (5)",
                "Spell & Trap Zones (5)",
                "Extra Monster Zones (2)",
                "Field Zone",
                "Graveyard (GY)",
                "Banishment"
            ),
            phases = listOf("DRAW", "MAIN", "COMBAT", "END")
        ),
        BaseVariantSpec(
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            name = "EDOPro Custom Project Clone (IgnisForge 21-Digit)",
            tagline = "Separate EDOPro clone for custom projects with 21-digit limits for variables, functions & code strings",
            sourceRepoUrl = "git://custom-clone/ProjectIgnis/EDOPro-CustomProjects",
            engineCoreLabel = "ocgcore-bigint21 • 21-Digit Vars/Funcs/CodeStrings • expansions/*.cdb",
            isCustomProjectClone = true,
            maxDigitPrecision = 21,
            resourceName = "Summon/Chain",
            startingLife = 8000L,
            startingHandSize = 5L,
            maxResourcePool = 6L,
            combatRule = "ATK_DEF_BATTLE",
            protocolVersion = "40.1.4-bigint21",
            accentHex = 0xFFFFB300,
            zones = listOf(
                "Custom Main Deck",
                "Custom Extra Deck",
                "Expansion Monster Zones (6)",
                "Custom Spell/Pendulum Zones",
                "21-Digit Code-String Relay",
                "Graveyard (GY)",
                "Banishment"
            ),
            phases = listOf("DRAW", "MAIN", "COMBAT", "END")
        ),
        BaseVariantSpec(
            baseVariantId = "FORGE_MTG_BASE",
            name = "Card-Forge: Forge MTG Standalone (Magic: The Gathering)",
            tagline = "Standalone Magic: The Gathering client from Card-Forge/forge (20 Life, 7-Card Hand, The Stack & Mana Pool)",
            sourceRepoUrl = "https://github.com/Card-Forge/forge",
            engineCoreLabel = "Card-Forge Rules Engine • cardsfolder/*.txt • MTG Stack & Mana",
            isCustomProjectClone = false,
            maxDigitPrecision = 10,
            resourceName = "Mana",
            startingLife = 20L,
            startingHandSize = 7L,
            maxResourcePool = 10L,
            combatRule = "BLOCKER_ASSIGN",
            protocolVersion = "1.6.62",
            accentHex = 0xFF29B6F6,
            zones = listOf(
                "Library (60/100)",
                "Hand (7)",
                "Battlefield (Creatures)",
                "Lands & Mana Pool",
                "The Stack",
                "Command Zone",
                "Graveyard",
                "Exile"
            ),
            phases = listOf("DRAW", "MAIN", "COMBAT", "END")
        ),
        BaseVariantSpec(
            baseVariantId = "FORGE_CUSTOM_CLONE",
            name = "Clone Card-Forge for Custom Projects (ForgeScript 21-Digit)",
            tagline = "Separate clone of https://github.com/Card-Forge/forge for custom MTG projects with 21-digit SVar/P-T/Mana scripting",
            sourceRepoUrl = "git://custom-clone/Card-Forge/forge-CustomProjects",
            engineCoreLabel = "Card-Forge Custom Clone • customcards/*.txt • 21-Digit SVar & P/T",
            isCustomProjectClone = true,
            maxDigitPrecision = 21,
            resourceName = "Custom Mana",
            startingLife = 40L,
            startingHandSize = 7L,
            maxResourcePool = 12L,
            combatRule = "BLOCKER_ASSIGN",
            protocolVersion = "1.6.62-custom21",
            accentHex = 0xFF66BB6A,
            zones = listOf(
                "Custom Library",
                "Custom Battlefield (6)",
                "Custom Lands & Leylines",
                "21-Digit SVar Stack",
                "Command / Emblem Zone",
                "Graveyard",
                "Exile"
            ),
            phases = listOf("DRAW", "MAIN", "COMBAT", "END")
        ),
        BaseVariantSpec(
            baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
            name = "Mixed Client: Clone Forge + Clone EDOPro Custom (Extensible)",
            tagline = "Extensible mixed client cloning Card-Forge (MTG) + Clone EDOPro for Custom Project (21-Digit Lua + Forge SVar) that can be added to",
            sourceRepoUrl = "git://hybrid-mix/Card-Forge-forge+ProjectIgnis-EDOPro-Custom",
            engineCoreLabel = "Extensible Hybrid Core • Card-Forge SVar + ocgcore-bigint21 Lua",
            isCustomProjectClone = true,
            maxDigitPrecision = 21,
            resourceName = "Mana/Chain",
            startingLife = 8000L,
            startingHandSize = 7L,
            maxResourcePool = 12L,
            combatRule = "ATK_DEF_BATTLE",
            protocolVersion = "2.0.0-hybrid21",
            accentHex = 0xFFFF4081,
            zones = listOf(
                "Hybrid Deck / Library",
                "Extra / Command Zone",
                "Monster & Creature Lanes (6)",
                "Spell/Trap & Land/Mana Row",
                "Unified Chain + Stack",
                "Graveyard (GY)",
                "Exile / Banishment",
                "Extensible Custom Module Bus"
            ),
            phases = listOf("DRAW", "MAIN", "COMBAT", "END")
        ),
        BaseVariantSpec(
            baseVariantId = "AETHER_MANA_V1",
            name = "AetherMana Standard",
            tagline = "Classic mana-curve spellcraft, blocker assignment & arcane resonance",
            sourceRepoUrl = "https://nexustcg.io/specs/aether-mana-v1",
            engineCoreLabel = "AetherCore v1.4 • Mana Pool & Stack",
            isCustomProjectClone = false,
            maxDigitPrecision = 10,
            resourceName = "Mana",
            startingLife = 20L,
            startingHandSize = 5L,
            maxResourcePool = 10L,
            combatRule = "BLOCKER_ASSIGN",
            protocolVersion = "1.4.0",
            accentHex = 0xFF00E676,
            zones = listOf("Grimoire", "Hand", "Sanctum", "Abyss", "Mana Well"),
            phases = listOf("DRAW", "MAIN", "COMBAT", "END")
        ),
        BaseVariantSpec(
            baseVariantId = "CYBER_OVERDRIVE_V2",
            name = "CyberCore Overdrive",
            tagline = "High-velocity energy matrix, direct tactical strikes & shield gates",
            sourceRepoUrl = "https://nexustcg.io/specs/cyber-overdrive-v2",
            engineCoreLabel = "OverdriveCore v2.1 • Shield Matrix",
            isCustomProjectClone = false,
            maxDigitPrecision = 10,
            resourceName = "Energy",
            startingLife = 30L,
            startingHandSize = 6L,
            maxResourcePool = 8L,
            combatRule = "SHIELD_MATRIX",
            protocolVersion = "2.1.0",
            accentHex = 0xFFB388FF,
            zones = listOf("Mainframe", "Grip", "Frontline", "ScrapHeap", "Core Reactor"),
            phases = listOf("DRAW", "MAIN", "COMBAT", "END")
        ),
        BaseVariantSpec(
            baseVariantId = "CHRONOS_RIFT_V1",
            name = "Chronos Rift",
            tagline = "Temporal flux manipulation, continuum shifts & direct dimensional clash",
            sourceRepoUrl = "https://nexustcg.io/specs/chronos-rift-v1",
            engineCoreLabel = "ChronosCore v1.2 • Timeline Flux",
            isCustomProjectClone = false,
            maxDigitPrecision = 10,
            resourceName = "Flux",
            startingLife = 25L,
            startingHandSize = 5L,
            maxResourcePool = 12L,
            combatRule = "DIRECT_STRIKE",
            protocolVersion = "1.2.0",
            accentHex = 0xFFFF5252,
            zones = listOf("Continuum", "Hand", "Rift Horizon", "Void Exile", "Flux Conduit"),
            phases = listOf("DRAW", "MAIN", "COMBAT", "END")
        )
    )

    fun findBaseSpec(baseVariantId: String): BaseVariantSpec {
        return baseVariants.firstOrNull { it.baseVariantId == baseVariantId } ?: baseVariants.first()
    }

    fun isEdoProVariant(baseVariantId: String): Boolean {
        return baseVariantId == "EDOPRO_BASE_MR5" ||
            baseVariantId == "EDOPRO_CUSTOM_FORK" ||
            baseVariantId == "HYBRID_FORGE_EDOPRO_MIX"
    }

    fun isForgeVariant(baseVariantId: String): Boolean {
        return baseVariantId == "FORGE_MTG_BASE" ||
            baseVariantId == "FORGE_CUSTOM_CLONE" ||
            baseVariantId == "HYBRID_FORGE_EDOPRO_MIX"
    }

    fun isHybridMixedVariant(baseVariantId: String): Boolean {
        return baseVariantId == "HYBRID_FORGE_EDOPRO_MIX"
    }

    fun supports21DigitRegisters(baseVariantId: String): Boolean {
        return baseVariantId == "EDOPRO_CUSTOM_FORK" ||
            baseVariantId == "FORGE_CUSTOM_CLONE" ||
            baseVariantId == "HYBRID_FORGE_EDOPRO_MIX"
    }

    /**
     * Returns true when the given `baseVariantId` represents an official Base Client
     * (such as `EDOPRO_BASE_MR5` -> `https://github.com/ProjectIgnis/EDOPro` or
     * `FORGE_MTG_BASE` -> `https://github.com/Card-Forge/forge`) that is locked to its
     * exact upstream GitHub source repository and cannot be modified by users or the agent
     * except via an upstream source repository sync.
     */
    fun isLockedGitHubBaseClient(baseVariantId: String): Boolean {
        return baseVariantId == "EDOPRO_BASE_MR5" ||
            baseVariantId == "FORGE_MTG_BASE" ||
            !findBaseSpec(baseVariantId).isCustomProjectClone
    }

    fun isLockedGitHubBaseClientNode(client: ClientNode): Boolean {
        return client.requiresStrictBase || isLockedGitHubBaseClient(client.baseVariantId)
    }

    /**
     * Returns the exact canonical GitHub repository URL for a Base Client.
     * Base clients are strictly bound to their exact source repository URL.
     */
    fun exactGitHubRepoUrl(baseVariantId: String): String {
        return when (baseVariantId) {
            "EDOPRO_BASE_MR5" -> "https://github.com/ProjectIgnis/EDOPro"
            "FORGE_MTG_BASE" -> "https://github.com/Card-Forge/forge"
            else -> findBaseSpec(baseVariantId).sourceRepoUrl
        }
    }

    fun systemUiProfileFor(
        baseVariantId: String,
        activeClient: ClientNode? = null
    ): ClientSystemUiProfile {
        val spec = findBaseSpec(baseVariantId)
        val resolvedClientName = activeClient?.clientName?.takeIf {
            it.isNotBlank() && activeClient.baseVariantId == baseVariantId
        }
        val resolvedEndpoint = activeClient?.endpointUri?.takeIf {
            it.isNotBlank() && activeClient.baseVariantId == baseVariantId
        }
        return when (baseVariantId) {
            "EDOPRO_BASE_MR5" -> ClientSystemUiProfile(
                baseVariantId = "EDOPRO_BASE_MR5",
                clientDisplayName = resolvedClientName ?: "ProjectIgnis EDOPro Base Client",
                systemTitle = "ProjectIgnis EDOPro Duel System (Master Duel • Duel Links • Cross Duel HUD)",
                hudBadgeLabel = "EDOPRO MR5 • GITHUB LOCKED",
                lifeLabel = "LP",
                resourceLabel = "Summon / Chain",
                deployVerb = "Summon / Set",
                combatVerb = "Battle Phase (ATK/DEF)",
                boardLayoutType = "EDOPRO_MASTER_SPEED_LANES",
                fieldSlotsTopLabel = "EMZ [L/R] • Master Duel Mate • Cross Duel 3-Lane Vectors & Opponent MMZ 1–5",
                fieldSlotsBottomLabel = "Main Monster Zones [MMZ 1–5 / Speed 1–3] & Spell/Trap Zones [S/T 1–5]",
                fieldSlotCodes = listOf("MMZ-1", "MMZ-2", "MMZ-3", "MMZ-4", "MMZ-5"),
                spellOrSupportSlotCodes = listOf("S/T-1", "S/T-2", "S/T-3", "S/T-4", "S/T-5"),
                phaseStripCodes = listOf("DP", "SP", "MP1", "BP", "MP2", "EP"),
                windowModeTitle = "ProjectIgnis EDOPro Duel Client Window",
                endpointOrRepoUri = "https://github.com/ProjectIgnis/EDOPro",
                accentHex = spec.accentHex,
                supports21DigitEngine = false,
                inspiredSystemsSummary = "Master Duel (Chain AUTO/ON/OFF + Mate + Royal Foil) • Duel Links (Skills + Speed Zones) • Cross Duel (3-Lane Clash)",
                duelLinksSkillName = "Ultimate Dragons / Destiny Draw",
                duelLinksSkillEffect = "Reveal Blue-Eyes or Draw 1 Signature Card & boost Field ATK by +500",
                masterDuelMateName = "Blue-Eyes Ultimate Mate",
                masterDuelFoilFinish = "Royal Finish (Master Duel Prismatic)",
                crossDuelAceSkills = listOf(
                    "Master: Blue-Eyes Burst Stream",
                    "Skill 2: Center Lane Advance",
                    "Skill 3: Destruction Immunity"
                )
            )
            "EDOPRO_CUSTOM_FORK" -> ClientSystemUiProfile(
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                clientDisplayName = resolvedClientName ?: "EDOPro Custom Project Clone (IgnisForge 21-Digit)",
                systemTitle = "IgnisForge 21-Digit EDOPro System (Master Duel + Duel Links + Cross Duel)",
                hudBadgeLabel = "EDOPRO 21D • MD/DL/CD",
                lifeLabel = "21-Digit LP",
                resourceLabel = "21d Script / Chain",
                deployVerb = "21d Cut-In Summon",
                combatVerb = "21-Digit Lane & Battle Calc",
                boardLayoutType = "EDOPRO_21D_MASTER_CROSS",
                fieldSlotsTopLabel = "Custom Expansion Zones [EXP 1–6] • Cross Duel Boss Lanes & 21-Digit Register Bus",
                fieldSlotsBottomLabel = "Expansion Monster Zones [EXP 1–6] & Custom Pendulum/Skill/Script Zones",
                fieldSlotCodes = listOf("EXP-1", "EXP-2", "EXP-3", "EXP-4", "EXP-5", "EXP-6"),
                spellOrSupportSlotCodes = listOf("LUA-1", "LUA-2", "LUA-3", "PEND-L", "PEND-R"),
                phaseStripCodes = listOf("DP", "SP", "MP1", "21D-CALC", "BP", "EP"),
                windowModeTitle = "EDOPro 21-Digit Custom Project Window",
                endpointOrRepoUri = resolvedEndpoint ?: "https://github.com/ProjectIgnis/EDOPro",
                accentHex = spec.accentHex,
                supports21DigitEngine = true,
                inspiredSystemsSummary = "Duel Links 21d Skills • Master Duel Chain Toggle & Royal Mate • Cross Duel 3-Lane Boss Raid",
                duelLinksSkillName = "Singularity Overdrive (21-Digit Skill)",
                duelLinksSkillEffect = "Multiplies active 21-Digit Register by x2 & triggers Boss Cut-in",
                masterDuelMateName = "Cyberse Singularity Dragon Mate",
                masterDuelFoilFinish = "21-Digit Singularity Royal Foil",
                crossDuelAceSkills = listOf(
                    "Master: 21-Digit Hyper-Pierce",
                    "Skill 2: 3-Lane Boss Blitz",
                    "Skill 3: Code-String Relay Shield"
                )
            )
            "FORGE_MTG_BASE" -> ClientSystemUiProfile(
                baseVariantId = "FORGE_MTG_BASE",
                clientDisplayName = resolvedClientName ?: "Card-Forge: Forge MTG Standalone Client",
                systemTitle = "Card-Forge MTG System (MTG Online Chess Clock & Stops + MTG Arena Auto-Tap)",
                hudBadgeLabel = "CARD-FORGE MTG • GITHUB LOCKED",
                lifeLabel = "Life Total",
                resourceLabel = "Mana Pool",
                deployVerb = "Cast / Auto-Tap Land",
                combatVerb = "Declare Attackers & Blockers",
                boardLayoutType = "FORGE_MTGO_MTGA_BATTLEFIELD",
                fieldSlotsTopLabel = "Opposing Permanents, Planeswalkers, MTGO Chess Clock & The Stack",
                fieldSlotsBottomLabel = "Battlefield Permanents [BTL 1–5] & Auto-Tapper Lands / Mana Pool Pips",
                fieldSlotCodes = listOf("BTL-1", "BTL-2", "BTL-3", "BTL-4", "BTL-5"),
                spellOrSupportSlotCodes = listOf("LAND-W", "LAND-U", "LAND-B", "LAND-R", "STACK"),
                phaseStripCodes = listOf("UNTAP", "UPKEEP", "DRAW", "MAIN1", "COMBAT", "MAIN2", "END"),
                windowModeTitle = "Card-Forge MTG Standalone Client Window",
                endpointOrRepoUri = "https://github.com/Card-Forge/forge",
                accentHex = spec.accentHex,
                supports21DigitEngine = false,
                inspiredSystemsSummary = "MTG Online (25:00 Chess Clock • Phase Stops • F2/F6/F8 Priority) • MTG Arena (Auto-Tapper • Full Control • Rope Timeouts)",
                duelLinksSkillName = "Planeswalker Emblem Resonance",
                duelLinksSkillEffect = "Add {W}{U}{B}{R}{G} to Mana Pool & scry 1 on The Stack",
                masterDuelMateName = "Clockwork Lotus Pet (MTGA)",
                masterDuelFoilFinish = "MTG Arena Mythic Parallax Borderless"
            )
            "FORGE_CUSTOM_CLONE" -> ClientSystemUiProfile(
                baseVariantId = "FORGE_CUSTOM_CLONE",
                clientDisplayName = resolvedClientName ?: "Clone Card-Forge for Custom Projects (21-Digit SVar)",
                systemTitle = "Card-Forge Custom Clone (MTGO Priority Stops + MTGA Full Control + 21d SVar)",
                hudBadgeLabel = "FORGE CLONE 21D • MTGO/MTGA",
                lifeLabel = "21d Life / Commander",
                resourceLabel = "Custom Mana / SVar",
                deployVerb = "Auto-Tap & Cast 21d Script",
                combatVerb = "21-Digit Blocker & Lane Calc",
                boardLayoutType = "FORGE_CUSTOM_MTGO_ARENA",
                fieldSlotsTopLabel = "Opposing Custom Battlefield, MTGO Priority Stops & 21-Digit SVar Stack",
                fieldSlotsBottomLabel = "Custom Permanents [CBTL 1–6] & Auto-Tapper Lands / Command Zone",
                fieldSlotCodes = listOf("CBTL-1", "CBTL-2", "CBTL-3", "CBTL-4", "CBTL-5", "CBTL-6"),
                spellOrSupportSlotCodes = listOf("MANA-1", "MANA-2", "SVAR-21", "CMD-ZONE", "STACK"),
                phaseStripCodes = listOf("UNTAP", "UPKEEP", "MAIN1", "SVAR-21", "COMBAT", "MAIN2", "END"),
                windowModeTitle = "Card-Forge Custom Project Clone Window",
                endpointOrRepoUri = resolvedEndpoint ?: "https://github.com/Card-Forge/forge",
                accentHex = spec.accentHex,
                supports21DigitEngine = true,
                inspiredSystemsSummary = "MTG Online (F2/F6/F8 Priority + Mana Pips) • MTG Arena (Auto-Tapper + Full Control + Emotes) • 21-Digit SVar",
                duelLinksSkillName = "Commander Command Zone Surge",
                duelLinksSkillEffect = "Cast Commander without tax & scale SVar Power21 by +100%",
                masterDuelMateName = "Aether Lotus Dragon Pet",
                masterDuelFoilFinish = "Mythic 21-Digit Borderless Foil"
            )
            "HYBRID_FORGE_EDOPRO_MIX" -> {
                val modules = BigInt21Engine.extractMixedClientModules(
                    activeClient?.customCodeVarExpr21 ?: BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21
                )
                val extraSlot = modules.lastOrNull()?.take(8)?.uppercase() ?: "EXT-BUS"
                ClientSystemUiProfile(
                    baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
                    clientDisplayName = resolvedClientName
                        ?: "Mixed Client: Clone Forge + Clone EDOPro Custom (${modules.size} Mods)",
                    systemTitle = "Unified Omni-Client: Duel Links + Master Duel + Cross Duel + MTGO + MTG Arena",
                    hudBadgeLabel = "OMNI-HYBRID (${modules.size} MODS)",
                    lifeLabel = "Hybrid LP / Life (21d)",
                    resourceLabel = "Mana + Chain + Skills",
                    deployVerb = "Auto-Tap / Cut-In Summon",
                    combatVerb = "3-Lane Stack+Chain Clash",
                    boardLayoutType = "OMNI_DL_MD_CD_MTGO_MTGA_MATRIX",
                    fieldSlotsTopLabel = "EMZ / Command • Cross Duel 3-Lanes • MTGO Clock • Master Duel Mate",
                    fieldSlotsBottomLabel = "Hybrid Lanes [MMZ-1..3 + BTL-1..3] & Auto-Tap Lands + Chain Toggle + Skills",
                    fieldSlotCodes = listOf("MMZ-1", "MMZ-2", "MMZ-3", "BTL-1", "BTL-2", "BTL-3"),
                    spellOrSupportSlotCodes = listOf("S/T-1", "LAND-1", "CHAIN-STACK", "CMD-EMZ", extraSlot),
                    phaseStripCodes = listOf("UNTAP/DP", "SP/UPKEEP", "MP1/MAIN1", "STACK/CHAIN", "BP/COMBAT", "MP2", "EP/END"),
                    windowModeTitle = "Mixed Omni-Client (DL • MD • CD • MTGO • MTGA) Window",
                    endpointOrRepoUri = resolvedEndpoint ?: "https://github.com/Card-Forge/forge",
                    accentHex = spec.accentHex,
                    supports21DigitEngine = true,
                    inspiredSystemsSummary = "Unifies Yu-Gi-Oh! Duel Links, Master Duel, Cross Duel, MTG Online (MTGO), and MTG Arena in 1 Extensible Client",
                    duelLinksSkillName = "Omniverse Convergence (DL + MTGA)",
                    duelLinksSkillEffect = "Triggers Duel Links Skill Cut-in + MTGA Auto-Tap Mana Surge + Cross Duel 3-Lane Boost",
                    masterDuelMateName = "Omniverse Lotus-Dragon Mate",
                    masterDuelFoilFinish = "Royal Mythic Parallax Finish (MD + MTGA)"
                )
            }
            "AETHER_MANA_V1" -> ClientSystemUiProfile(
                baseVariantId = "AETHER_MANA_V1",
                clientDisplayName = resolvedClientName ?: "AetherDesk Desktop v2.4",
                systemTitle = "AetherDesk Mana Grimoire System (MTGO & MTG Arena HUD)",
                hudBadgeLabel = "AETHER MANA • MTGO/MTGA",
                lifeLabel = "Sanctum Life",
                resourceLabel = "Mana Pool",
                deployVerb = "Cast / Auto-Tap Channel",
                combatVerb = "Declare Attackers / Blockers",
                boardLayoutType = "AETHER_SANCTUM",
                fieldSlotsTopLabel = "Opposing Sanctum, MTGO Priority Stops & Leyline Vanguard",
                fieldSlotsBottomLabel = "Arcane Sanctum Lanes & Auto-Tapper Mana Well Conduits",
                fieldSlotCodes = listOf("SANCTUM-1", "SANCTUM-2", "SANCTUM-3", "SANCTUM-4", "SANCTUM-5"),
                spellOrSupportSlotCodes = listOf("MANA-1", "MANA-2", "MANA-3", "LEYLINE", "GRIMOIRE"),
                phaseStripCodes = listOf("UNTAP", "UPKEEP", "MAIN-1", "COMBAT", "MAIN-2", "END"),
                windowModeTitle = "AetherDesk Grimoire Client Window",
                endpointOrRepoUri = resolvedEndpoint ?: spec.sourceRepoUrl,
                accentHex = spec.accentHex,
                supports21DigitEngine = false
            )
            "CYBER_OVERDRIVE_V2" -> ClientSystemUiProfile(
                baseVariantId = "CYBER_OVERDRIVE_V2",
                clientDisplayName = resolvedClientName ?: "CyberDeck CLI Node-09",
                systemTitle = "CyberDeck Overdrive Matrix (Cross Duel 3-Lane + Master Duel HUD)",
                hudBadgeLabel = "CYBER OVERDRIVE • 3-LANE",
                lifeLabel = "Core Integrity",
                resourceLabel = "Overdrive Energy",
                deployVerb = "Overdrive Cut-In Deploy",
                combatVerb = "3-Lane Breach Shield Matrix",
                boardLayoutType = "CYBER_MATRIX",
                fieldSlotsTopLabel = "Enemy Shield Matrix Gates & Cross Duel 3-Lane Frontline",
                fieldSlotsBottomLabel = "Frontline Strike Grid & Core Reactor Cells",
                fieldSlotCodes = listOf("GRID-01", "GRID-02", "GRID-03", "GRID-04", "GRID-05"),
                spellOrSupportSlotCodes = listOf("SHIELD-A", "SHIELD-B", "SHIELD-C", "REACTOR-1", "REACTOR-2"),
                phaseStripCodes = listOf("BOOT", "CHARGE", "DEPLOY", "BREACH", "OVERDRIVE"),
                windowModeTitle = "CyberDeck Overdrive Terminal Window",
                endpointOrRepoUri = resolvedEndpoint ?: spec.sourceRepoUrl,
                accentHex = spec.accentHex,
                supports21DigitEngine = false
            )
            else -> ClientSystemUiProfile(
                baseVariantId = "CHRONOS_RIFT_V1",
                clientDisplayName = resolvedClientName ?: "Chronos Continuum Client",
                systemTitle = "Chronos Rift Continuum System (Duel Links Skills + MTGO Clock)",
                hudBadgeLabel = "CHRONOS RIFT • FLUX",
                lifeLabel = "Timeline HP",
                resourceLabel = "Temporal Flux",
                deployVerb = "Rift Materialize",
                combatVerb = "Direct Dimensional Clash",
                boardLayoutType = "CHRONOS_CONTINUUM",
                fieldSlotsTopLabel = "Opposing Rift Horizon & Void Exile",
                fieldSlotsBottomLabel = "Rift Horizon Sectors & Flux Conduits",
                fieldSlotCodes = listOf("RIFT-I", "RIFT-II", "RIFT-III", "RIFT-IV", "RIFT-V"),
                spellOrSupportSlotCodes = listOf("FLUX-1", "FLUX-2", "FLUX-3", "EPOCH", "PARADOX"),
                phaseStripCodes = listOf("SHIFT", "DRAW", "MATERIALIZE", "CLASH", "REWIND"),
                windowModeTitle = "Chronos Rift Continuum Client Window",
                endpointOrRepoUri = resolvedEndpoint ?: spec.sourceRepoUrl,
                accentHex = spec.accentHex,
                supports21DigitEngine = false
            )
        }
    }

    fun computeProtocolHash(
        baseVariantId: String,
        startingLife: Long,
        startingHandSize: Long,
        maxResourcePool: Long,
        combatRule: String
    ): String {
        return "$baseVariantId:$startingLife:$startingHandSize:$maxResourcePool:$combatRule"
    }

    /**
     * Generates an authentic Card-Forge (https://github.com/Card-Forge/forge) `cardsfolder/<card>.txt`
     * card script, supporting standard MTG rules (`FORGE_MTG_BASE`) or 21-digit `SVar:` custom
     * project scripting (`FORGE_CUSTOM_CLONE`).
     */
    fun generateForgeCardScript(
        cardName: String,
        passcode21: String,
        manaCostVal: Long,
        power21: String,
        toughness21: String,
        varInput21: String = "999999999999999999999",
        codeVarExpr21: String = BigInt21Engine.DEFAULT_FORGE_CUSTOM_VAR_EXPR_21,
        funcName: String = "ADD_21",
        abilityText: String,
        isCustom21Clone: Boolean = true
    ): String {
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(passcode21, BigInt21Engine.DEFAULT_CODE_STRING_21)
        val cleanP21 = BigInt21Engine.sanitizeTo21Digits(power21, "5")
        val cleanT21 = BigInt21Engine.sanitizeTo21Digits(toughness21, "5")
        val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(varInput21, "999999999999999999999")
        val computed21 = BigInt21Engine.evaluateFunction21(funcName, cleanP21, cleanVar21)
        val genericCost = (manaCostVal - 2L).coerceAtLeast(1L)
        return if (isCustom21Clone) {
            """
                # Card-Forge Custom Project Clone (https://github.com/Card-Forge/forge)
                # File: res/cardsfolder/custom_${cleanCode21}.txt (21-Digit SVar Engine)
                Name:$cardName
                ManaCost:$genericCost U R
                Types:Legendary Creature Avatar Dragon
                PT:$cleanP21/$cleanT21
                K:Flying
                K:Vigilance
                SVar:CodeString21:$cleanCode21
                SVar:VarRegister21:$cleanVar21
                SVar:FuncMode21:$funcName
                SVar:FuncResult21:$computed21
                SVar:CustomExpr21:${BigInt21Engine.filterCodeStringWith21DigitLimit(codeVarExpr21)}
                T:Mode${'$'} ChangesZone | Origin${'$'} Any | Destination${'$'} Battlefield | ValidCard${'$'} Card.Self | Execute${'$'} TrigPump21 | TriggerDescription${'$'} $abilityText
                SVar:TrigPump21:DB${'$'} Pump | Defined${'$'} Self | NumAtt${'$'} +X | NumDef${'$'} +X | References${'$'} X
                SVar:X:Count${'$'}ValidCard_21DigitRegister.$computed21
                Oracle:$abilityText (21-Digit SVar $funcName = $computed21)
            """.trimIndent()
        } else {
            """
                # Card-Forge Standalone Client (https://github.com/Card-Forge/forge)
                # File: res/cardsfolder/${cardName.lowercase().replace(" ", "_")}.txt
                Name:$cardName
                ManaCost:$genericCost W U
                Types:Legendary Creature Angel Avatar
                PT:$cleanP21/$cleanT21
                K:Flying
                K:Vigilance
                A:SP${'$'} Draw | Cost${'$'} 1 U T | NumCards${'$'} 1 | SpellDescription${'$'} $abilityText
                Oracle:Flying, vigilance. $abilityText
            """.trimIndent()
        }
    }

    /**
     * Generates a unified Hybrid script for the Extensible Mixed Client (`HYBRID_FORGE_EDOPRO_MIX`)
     * that bridges Card-Forge `cardsfolder/<card>.txt` SVar definitions with ProjectIgnis/EDOPro
     * `ocgcore-bigint21` Lua 5.3 and lists all user-added extension modules.
     */
    fun generateHybridForgeEdoProScript(
        cardName: String,
        passcode21: String,
        levelOrManaCost: Long,
        atkOrPower21: String,
        defOrToughness21: String,
        varInput21: String = "999999999999999999999",
        codeVarExpr21: String = BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21,
        funcName: String = "ADD_21",
        abilityText: String
    ): String {
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(passcode21, BigInt21Engine.DEFAULT_CODE_STRING_21)
        val cleanAtk21 = BigInt21Engine.sanitizeTo21Digits(atkOrPower21, "4000")
        val cleanDef21 = BigInt21Engine.sanitizeTo21Digits(defOrToughness21, "4000")
        val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(varInput21, "999999999999999999999")
        val computed21 = BigInt21Engine.evaluateFunction21(funcName, cleanAtk21, cleanVar21)
        val modules = BigInt21Engine.extractMixedClientModules(codeVarExpr21)
        return """
            -- ============================================================================
            -- EXTENSIBLE MIXED CLIENT: Clone Card-Forge + Clone EDOPro for Custom Project
            -- Sources: https://github.com/Card-Forge/forge + https://github.com/ProjectIgnis/EDOPro
            -- Loaded Extension Modules (${modules.size}): ${modules.joinToString(" + ")}
            -- ============================================================================
            -- [PART 1: Card-Forge MTG Custom SVar Definition (cardsfolder/c$cleanCode21.txt)]
            -- Name:$cardName | ManaCost:$levelOrManaCost U R | PT:$cleanAtk21/$cleanDef21
            -- SVar:HybridCode21:$cleanCode21 | SVar:HybridResult21:$computed21
            --
            -- [PART 2: EDOPro 21-Digit ocgcore-bigint21 Lua Script (script/c$cleanCode21.lua)]
            local s,id=GetID()
            s.HYBRID_ENGINE = "CARD_FORGE_PLUS_EDOPRO_21D"
            s.code_string_21 = "$cleanCode21"
            s.mix_modules = { ${modules.joinToString(", ") { "\"$it\"" }} }
            s.var_atk_pt_21 = "$cleanAtk21"
            s.var_def_toughness_21 = "$cleanDef21"
            s.var_input_21 = "$cleanVar21"
            s.func_mode_21 = "$funcName"
            s.func_result_21 = "$computed21"

            function s.initial_effect(c)
                if Duel.RegisterHybridForgeSvar21 then
                    Duel.RegisterHybridForgeSvar21(c, s.code_string_21, s.mix_modules, s.func_result_21)
                end
                local e1=Effect.CreateEffect(c)
                e1:SetType(EFFECT_TYPE_QUICK_O)
                e1:SetCode(EVENT_FREE_CHAIN)
                e1:SetRange(LOCATION_MZONE)
                e1:SetOperation(s.hybrid_stack_chain_op)
                c:RegisterEffect(e1)
            end

            -- Resolves simultaneously on MTG Stack & Yu-Gi-Oh! Chain Link ($abilityText)
            function s.hybrid_stack_chain_op(e,tp,eg,ep,ev,re,r,rp)
                local out21 = s.func_result_21
                if ForgeBridge and ForgeBridge.ResolveStackAndChain21 then
                    ForgeBridge.ResolveStackAndChain21(e:GetHandler(), out21, s.mix_modules)
                end
            end
        """.trimIndent()
    }

    /**
     * Generates an authentic ProjectIgnis/EDOPro ocgcore Lua 5.3 script (c########.lua)
     * upgraded with the 21-Numerical-Digit Limit for Variables, Functions, and Coding Variables for Code Strings.
     */
    fun generateOcgcoreLuaScript(
        cardName: String,
        passcode21: String,
        levelOrRank: Long,
        atk21: String,
        def21: String,
        varInput21: String = "999999999999999999999",
        codeVarExpr21: String = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21,
        funcName: String = "ADD_21",
        abilityText: String
    ): String {
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(passcode21, BigInt21Engine.DEFAULT_CODE_STRING_21)
        val cleanAtk21 = BigInt21Engine.sanitizeTo21Digits(atk21, "3000")
        val cleanDef21 = BigInt21Engine.sanitizeTo21Digits(def21, "2500")
        val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(varInput21, "999999999999999999999")
        val cleanCodeVars = BigInt21Engine.sanitizeCodeVarExpr21(codeVarExpr21, cleanCode21)
        val computed21 = BigInt21Engine.evaluateFunction21(funcName, cleanAtk21, cleanVar21)
        return """
            -- $cardName
            -- Generated by NexusTCG EDOPro Custom Project Clone (21-Digit BigInt21 Engine)
            -- Script File: script/c$cleanCode21.lua (${cleanCode21.length}/21 numerical digits)
            -- SQLite CDB Entry: id=$cleanCode21 | lv=$levelOrRank | atk21=$cleanAtk21 | def21=$cleanDef21
            local s,id=GetID()
            -- 21-Digit Coding Variables for Code Strings (Max 21 Numerical Digits)
            s.MAX_CODE_STRING_DIGITS = 21
            s.code_string_21 = "$cleanCode21"
            $cleanCodeVars
            -- 21-Digit Variable & Function Input Registers (Max: 999,999,999,999,999,999,999)
            s.var_atk_21 = "$cleanAtk21"
            s.var_def_21 = "$cleanDef21"
            s.var_input_21 = "$cleanVar21"
            s.func_mode_21 = "$funcName"
            s.func_result_21 = "$computed21"

            function s.initial_effect(c)
                -- Bind 21-Digit Code String & Coding Variables in ocgcore-bigint21
                if Duel.RegisterCodeString21 then
                    Duel.RegisterCodeString21(c, s.code_string_21, "$cleanCodeVars")
                end
                if Duel.RegisterBigInt21Variable then
                    Duel.RegisterBigInt21Variable(c, id, s.var_input_21)
                end
                local e1=Effect.CreateEffect(c)
                e1:SetDescription(aux.Stringid(id,0))
                e1:SetCategory(CATEGORY_ATKCHANGE+CATEGORY_DAMAGE)
                e1:SetType(EFFECT_TYPE_IGNITION)
                e1:SetRange(LOCATION_MZONE)
                e1:SetCountLimit(1,id)
                e1:SetOperation(s.operation_bigint21)
                c:RegisterEffect(e1)
            end

            -- Card Effect: $abilityText
            -- Executes 21-digit function ($funcName) on 21-digit variable & code-string inputs
            function s.operation_bigint21(e,tp,eg,ep,ev,re,r,rp)
                local c=e:GetHandler()
                if c:IsRelateToEffect(e) and c:IsFaceup() then
                    local out21 = s.func_result_21
                    if Duel.ExecuteBigInt21Function then
                        out21 = Duel.ExecuteBigInt21Function(s.func_mode_21, s.var_atk_21, s.var_input_21)
                    end
                    local e1=Effect.CreateEffect(c)
                    e1:SetType(EFFECT_TYPE_SINGLE)
                    e1:SetCode(EFFECT_SET_ATTACK_FINAL)
                    if e1.SetBigInt21Value then
                        e1:SetBigInt21Value(out21)
                    else
                        e1:SetValue(tonumber(out21) or 999999)
                    end
                    e1:SetReset(RESET_EVENT+RESETS_STANDARD+RESET_PHASE+PHASE_END)
                    c:RegisterEffect(e1)
                end
            end
        """.trimIndent()
    }

    /**
     * Generates an EDOPro-compatible .ydk or Card-Forge .dck deck list supporting up to 21-digit card code strings.
     */
    fun generateYdkContent(deckName: String, baseVariantId: String, customPasscode21: String): String {
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(
            customPasscode21,
            if (supports21DigitRegisters(baseVariantId)) BigInt21Engine.DEFAULT_CODE_STRING_21 else "89631139"
        )
        val pool = cardsForVariant(baseVariantId)
        if (baseVariantId == "FORGE_MTG_BASE" || baseVariantId == "FORGE_CUSTOM_CLONE") {
            return buildString {
                appendLine("[metadata]")
                appendLine("Name=$deckName ($baseVariantId)")
                appendLine("Engine=https://github.com/Card-Forge/forge")
                appendLine("[Main]")
                pool.forEach { card ->
                    appendLine("4 ${card.name}|#${card.passcode}")
                }
                appendLine("4 Custom Forge Ace|#$cleanCode21")
                appendLine("[Sideboard]")
                appendLine("1 Custom Forge Ace|#$cleanCode21")
            }.trim()
        }
        val mainPasscodes = buildList {
            pool.forEach { card ->
                repeat(3) { add(card.passcode) }
            }
            repeat(3) { add(cleanCode21) }
        }
        return buildString {
            appendLine("#created by NexusTCG ($baseVariantId - $deckName)")
            appendLine("#code_string_limit=21_digits")
            appendLine("#main")
            mainPasscodes.forEach { appendLine(it) }
            appendLine("#extra")
            appendLine(cleanCode21)
            appendLine("!side")
        }.trim()
    }

    val cards: List<CardTemplate> = listOf(
        // 1. EDOPRO_BASE_MR5 (ProjectIgnis/EDOPro Base Client - Yu-Gi-Oh! MR5)
        CardTemplate(
            id = "edopro_azure_eyes_dragon",
            passcode = "89631139",
            name = "Azure-Eyes Sovereign Dragon",
            baseVariantId = "EDOPRO_BASE_MR5",
            cardType = "Monster / Dragon / Effect (Lv.8)",
            cost = 1,
            attack = 3000,
            defense = 2500,
            abilityText = "[ocgcore] Once per turn (Quick Effect): Target 1 card on the field; banish it.",
            artResId = R.drawable.img_card_arcane
        ),
        CardTemplate(
            id = "edopro_ignis_code_talker",
            passcode = "46986414",
            name = "Ignis Accesscode Vanguard",
            baseVariantId = "EDOPRO_BASE_MR5",
            cardType = "Monster / Cyberse / Link-4",
            cost = 2,
            attack = 2300,
            defense = 0,
            abilityText = "[MR5 Extra Monster Zone] Gains 3000 ATK on Link Summon. Your opponent cannot respond.",
            artResId = R.drawable.img_card_mecha
        ),
        CardTemplate(
            id = "edopro_solemn_sanctum",
            passcode = "74677422",
            name = "Solemn Judgment of Ignis",
            baseVariantId = "EDOPRO_BASE_MR5",
            cardType = "Trap / Counter (Spell Speed 3)",
            cost = 1,
            attack = 1500,
            defense = 2500,
            abilityText = "[Chain Link] Negate an opponent's Summon or Spell/Trap activation and destroy it.",
            artResId = R.drawable.img_card_void
        ),
        CardTemplate(
            id = "edopro_stardust_synchro",
            passcode = "44508094",
            name = "Cosmic Quasar Stardust",
            baseVariantId = "EDOPRO_BASE_MR5",
            cardType = "Monster / Dragon / Synchro (Lv.12)",
            cost = 2,
            attack = 4000,
            defense = 4000,
            abilityText = "[Extra Deck] Can attack twice per Battle Phase. Negates 1 activation per turn.",
            artResId = R.drawable.img_card_arcane
        ),

        // 2. EDOPRO_CUSTOM_FORK (Separate EDOPro Clone with 21-Digit Variable, Function & Code-String Support)
        CardTemplate(
            id = "edopro_custom_genesis_wyrm",
            passcode = "900001010000000000021",
            name = "Custom Project: Genesis Overlord",
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            cardType = "Custom Monster / Wyrm / BigInt21",
            cost = 1,
            attack = 3500,
            defense = 3000,
            attack21 = "999999999999999999999",
            defense21 = "500000000000000000000",
            abilityText = "[c900001010000000000021.lua] 21-Digit Code String & MUL_21 Variable Register.",
            artResId = R.drawable.img_card_arcane
        ),
        CardTemplate(
            id = "edopro_custom_cyber_valkyrie",
            passcode = "900001020000000000021",
            name = "Custom Project: Chrono-Link Striker",
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            cardType = "Custom Monster / Mecha / Link-3",
            cost = 1,
            attack = 2800,
            defense = 2200,
            attack21 = "123456789012345678901",
            defense21 = "100000000000000000000",
            abilityText = "[c900001020000000000021.lua] 21-Digit Function & Code-String Hook across zones.",
            artResId = R.drawable.img_card_mecha
        ),
        CardTemplate(
            id = "edopro_custom_babel_field",
            passcode = "900001030000000000021",
            name = "Custom Project: Babel Script Citadel",
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            cardType = "Custom Spell / Field (expansions.cdb)",
            cost = 1,
            attack = 2000,
            defense = 3000,
            attack21 = "777777777777777777777",
            defense21 = "888888888888888888888",
            abilityText = "[c900001030000000000021.lua] Unlocks 21-digit code strings & Lua function hooks.",
            artResId = R.drawable.img_card_void
        ),
        CardTemplate(
            id = "edopro_custom_paradox_emperor",
            passcode = "900001040000000000021",
            name = "Custom Project: Singularity Dragon",
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            cardType = "Custom Monster / Dragon / Pendulum",
            cost = 2,
            attack = 4500,
            defense = 3800,
            attack21 = "999999999999999999999",
            defense21 = "999999999999999999999",
            abilityText = "[c900001040000000000021.lua] Scales ATK/LP using 21-digit SCALE_ATK_LP_21.",
            artResId = R.drawable.img_card_void
        ),

        // 3. FORGE_MTG_BASE (Card-Forge Standalone Client for Magic: The Gathering)
        CardTemplate(
            id = "forge_mtg_serra_archangel",
            passcode = "500101",
            name = "Serra Vanguard Archangel",
            baseVariantId = "FORGE_MTG_BASE",
            cardType = "Creature — Angel (3WW)",
            cost = 5,
            attack = 4,
            defense = 4,
            abilityText = "[Card-Forge] Flying, vigilance. Attacking doesn't cause Serra Vanguard Archangel to tap.",
            artResId = R.drawable.img_card_arcane
        ),
        CardTemplate(
            id = "forge_mtg_shivan_hellkite",
            passcode = "500102",
            name = "Shivan Forge Hellkite",
            baseVariantId = "FORGE_MTG_BASE",
            cardType = "Creature — Dragon (4RR)",
            cost = 6,
            attack = 5,
            defense = 5,
            abilityText = "[Card-Forge] Flying. {R}: Shivan Forge Hellkite gets +1/+0 until end of turn.",
            artResId = R.drawable.img_card_mecha
        ),
        CardTemplate(
            id = "forge_mtg_counterspell_stack",
            passcode = "500103",
            name = "Arcane Counter-Stack",
            baseVariantId = "FORGE_MTG_BASE",
            cardType = "Instant — Stack Response (UU)",
            cost = 2,
            attack = 3,
            defense = 3,
            abilityText = "[The Stack] Counter target spell on The Stack. Draw 1 card if resolved.",
            artResId = R.drawable.img_card_void
        ),
        CardTemplate(
            id = "forge_mtg_lotus_bloom_artifact",
            passcode = "500104",
            name = "Gilded Lotus Relic",
            baseVariantId = "FORGE_MTG_BASE",
            cardType = "Artifact — Mana Source (0)",
            cost = 1,
            attack = 2,
            defense = 4,
            abilityText = "[Mana Pool] {T}, Sacrifice: Add three mana of any one color to your Mana Pool.",
            artResId = R.drawable.img_card_arcane
        ),

        // 4. FORGE_CUSTOM_CLONE (Clone of Card-Forge for Custom MTG Projects - 21-Digit SVar)
        CardTemplate(
            id = "forge_custom_ur_dragon_21",
            passcode = "900002010000000000021",
            name = "Forge Custom: Omniverse Ur-Avatar",
            baseVariantId = "FORGE_CUSTOM_CLONE",
            cardType = "Custom Legendary Creature — Avatar (SVar 21d)",
            cost = 3,
            attack = 12,
            defense = 12,
            attack21 = "999999999999999999999",
            defense21 = "999999999999999999999",
            abilityText = "[customcards/ur_21.txt] SVar:Power21 = 999,999,999,999,999,999,999. Eminence & Flying.",
            artResId = R.drawable.img_card_arcane
        ),
        CardTemplate(
            id = "forge_custom_planeswalker_engine",
            passcode = "900002020000000000021",
            name = "Forge Custom: Karn, 21-Digit Architect",
            baseVariantId = "FORGE_CUSTOM_CLONE",
            cardType = "Custom Legendary Planeswalker — Karn",
            cost = 2,
            attack = 8,
            defense = 10,
            attack21 = "123456789012345678901",
            defense21 = "123456789012345678901",
            abilityText = "[Card-Forge Clone] +2: Create a Construct token with 21-digit SVar Power/Toughness.",
            artResId = R.drawable.img_card_mecha
        ),
        CardTemplate(
            id = "forge_custom_mana_citadel",
            passcode = "900002030000000000021",
            name = "Forge Custom: Infinite Leyline Sanctum",
            baseVariantId = "FORGE_CUSTOM_CLONE",
            cardType = "Custom Enchantment — Leyline (SVar 21d)",
            cost = 1,
            attack = 6,
            defense = 8,
            attack21 = "777777777777777777777",
            defense21 = "888888888888888888888",
            abilityText = "[Card-Forge Clone] Multiplies Mana Pool and Creature P/T via 21-digit SVar:X.",
            artResId = R.drawable.img_card_void
        ),

        // 5. HYBRID_FORGE_EDOPRO_MIX (Mixed Client: Clone Card-Forge + Clone EDOPro for Custom Project)
        CardTemplate(
            id = "hybrid_mix_ignis_lotus_sovereign",
            passcode = "900003010000000000021",
            name = "Hybrid Mix: Lotus-Eyes Accesscode Dragon",
            baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
            cardType = "Hybrid Link-4 / Legendary Dragon [MTG+OCG 21d]",
            cost = 2,
            attack = 4500,
            defense = 4000,
            attack21 = "999999999999999999999",
            defense21 = "999999999999999999999",
            abilityText = "[Forge SVar + ocgcore Lua] Resolves on both MTG Stack & YGO Chain Link with 21-digit ATK/PT.",
            artResId = R.drawable.img_card_arcane
        ),
        CardTemplate(
            id = "hybrid_mix_planeswalker_synchro",
            passcode = "900003020000000000021",
            name = "Hybrid Mix: Jace-Quasar Mind Synchro",
            baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
            cardType = "Hybrid Synchro Planeswalker (Lv.12 / 3UU)",
            cost = 2,
            attack = 4000,
            defense = 4000,
            attack21 = "555555555555555555555",
            defense21 = "555555555555555555555",
            abilityText = "[Extensible Hybrid] Bridges Card-Forge Loyalty abilities with EDOPro Quick-Effect negations.",
            artResId = R.drawable.img_card_void
        ),
        CardTemplate(
            id = "hybrid_mix_pendulum_mox_conduit",
            passcode = "900003030000000000021",
            name = "Hybrid Mix: Mox Pendulum Scale-21",
            baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
            cardType = "Hybrid Artifact / Pendulum Spell (Scale 21)",
            cost = 1,
            attack = 3200,
            defense = 3000,
            attack21 = "123456789012345678901",
            defense21 = "123456789012345678901",
            abilityText = "[Extensible Module Bus] Taps for Mana AND Pendulum Summons 21-digit monsters across both engines.",
            artResId = R.drawable.img_card_mecha
        ),

        // 6. AETHER_MANA_V1 Cards
        CardTemplate(
            id = "aether_astral_dragon",
            passcode = "100001",
            name = "Astral Runedrake",
            baseVariantId = "AETHER_MANA_V1",
            cardType = "Creature - Dragon",
            cost = 4,
            attack = 5,
            defense = 5,
            abilityText = "Flying. On Deploy: Channel +1 Mana this turn.",
            artResId = R.drawable.img_card_arcane
        ),
        CardTemplate(
            id = "aether_sanctum_sentinel",
            passcode = "100002",
            name = "Sanctum Aegis Knight",
            baseVariantId = "AETHER_MANA_V1",
            cardType = "Creature - Guardian",
            cost = 2,
            attack = 2,
            defense = 4,
            abilityText = "Vigilance. Restores 2 Life to controller on deploy.",
            artResId = R.drawable.img_card_mecha
        ),
        CardTemplate(
            id = "aether_rift_obelisk",
            passcode = "100003",
            name = "Leyline Monolith",
            baseVariantId = "AETHER_MANA_V1",
            cardType = "Artifact - Conduit",
            cost = 3,
            attack = 3,
            defense = 4,
            abilityText = "Arcane Surge: Deals 2 bonus damage during Combat phase.",
            artResId = R.drawable.img_card_void
        ),

        // 7. CYBER_OVERDRIVE_V2 Cards
        CardTemplate(
            id = "cyber_chronos_paladin",
            passcode = "200001",
            name = "Vanguard Cyber-Paladin",
            baseVariantId = "CYBER_OVERDRIVE_V2",
            cardType = "Mecha - Striker",
            cost = 3,
            attack = 5,
            defense = 4,
            abilityText = "Overdrive: Breaches 2 Shield Matrix layers on attack.",
            artResId = R.drawable.img_card_mecha
        ),
        CardTemplate(
            id = "cyber_neon_wyvern",
            passcode = "200002",
            name = "Plasma Wing Interceptor",
            baseVariantId = "CYBER_OVERDRIVE_V2",
            cardType = "Construct - Aerial",
            cost = 2,
            attack = 4,
            defense = 2,
            abilityText = "Blitz. Can strike immediately after deployment.",
            artResId = R.drawable.img_card_arcane
        ),

        // 8. CHRONOS_RIFT_V1 Cards
        CardTemplate(
            id = "chronos_void_monolith",
            passcode = "300001",
            name = "Eternity Rift Monolith",
            baseVariantId = "CHRONOS_RIFT_V1",
            cardType = "Anomaly - Monument",
            cost = 3,
            attack = 4,
            defense = 5,
            abilityText = "Time Lock: Freezes opposing unit on deployment.",
            artResId = R.drawable.img_card_void
        ),
        CardTemplate(
            id = "chronos_epoch_serpent",
            passcode = "300002",
            name = "Epoch Star-Serpent",
            baseVariantId = "CHRONOS_RIFT_V1",
            cardType = "Entity - Leviathan",
            cost = 4,
            attack = 6,
            defense = 4,
            abilityText = "Phase Shift: Gains +2 Attack during Flux convergence.",
            artResId = R.drawable.img_card_arcane
        )
    )

    fun cardsForVariant(baseVariantId: String): List<CardTemplate> {
        val matching = cards.filter { it.baseVariantId == baseVariantId }
        return matching.ifEmpty { cards.take(4) }
    }
}
