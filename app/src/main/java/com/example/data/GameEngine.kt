package com.example.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canonical 5-Phase Turn State Machine supported by [GameEngine]:
 * - [DRAW]: Draw Phase (`DP` / `UNTAP` / `DRAW`)
 * - [STANDBY]: Standby / Upkeep Phase (`SP` / `UPKEEP` / `STANDBY`)
 * - [MAIN]: Main Phase (`MP1` / `MAIN-1` / `MP2` / `DEPLOY`)
 * - [COMBAT]: Combat / Battle Phase (`BP` / `COMBAT` / `CLASH` / `BREACH`)
 * - [END]: End Phase (`EP` / `END` / `REWIND`)
 */
enum class TurnPhase(
    val code: String,
    val displayName: String,
    val edoProCode: String,
    val mtgForgeCode: String,
    val phaseDescription: String
) {
    DRAW(
        code = "DRAW",
        displayName = "Draw Phase",
        edoProCode = "DP",
        mtgForgeCode = "UNTAP/DRAW",
        phaseDescription = "Untap / refresh active units, draw 1 card from Deck, and trigger Draw Phase effects."
    ),
    STANDBY(
        code = "STANDBY",
        displayName = "Standby Phase",
        edoProCode = "SP",
        mtgForgeCode = "UPKEEP",
        phaseDescription = "Resolve Standby / Upkeep maintenance triggers, continuous LP/Life effects, and 21-digit register ticks."
    ),
    MAIN(
        code = "MAIN",
        displayName = "Main Phase",
        edoProCode = "MP1",
        mtgForgeCode = "MAIN-1",
        phaseDescription = "Summon/Deploy units, activate Spell/Sorcery cards, set Trap/Instant responses, and queue card effects."
    ),
    COMBAT(
        code = "COMBAT",
        displayName = "Combat Phase",
        edoProCode = "BP",
        mtgForgeCode = "COMBAT",
        phaseDescription = "Declare attackers and blockers/targets, trigger Battle Step effects, and calculate combat damage."
    ),
    END(
        code = "END",
        displayName = "End Phase",
        edoProCode = "EP",
        mtgForgeCode = "END",
        phaseDescription = "Resolve End-of-Turn triggers, clear temporary turn buffs, and pass turn priority to next player."
    );

    fun nextPhase(): TurnPhase = when (this) {
        DRAW -> STANDBY
        STANDBY -> MAIN
        MAIN -> COMBAT
        COMBAT -> END
        END -> DRAW
    }

    fun clientPhaseCodeFor(baseVariantId: String): String {
        return when {
            BuiltInTcgCatalog.isEdoProVariant(baseVariantId) -> edoProCode
            BuiltInTcgCatalog.isForgeVariant(baseVariantId) -> when (this) {
                DRAW -> "UNTAP"
                STANDBY -> "UPKEEP"
                MAIN -> "MAIN-1"
                COMBAT -> "COMBAT"
                END -> "END"
            }
            baseVariantId == "HYBRID_FORGE_EDOPRO_MIX" -> when (this) {
                DRAW -> "DP/UNTAP"
                STANDBY -> "SP/UPKEEP"
                MAIN -> "MP1/MAIN1"
                COMBAT -> "BP/COMBAT"
                END -> "EP/END"
            }
            else -> code
        }
    }

    companion object {
        fun fromRawPhaseCode(raw: String): TurnPhase {
            val normalized = raw.trim().uppercase()
            return when {
                normalized == "STANDBY" || normalized == "SP" || normalized.contains("UPKEEP") ||
                    normalized == "SP/UPKEEP" || normalized == "BOOT" -> STANDBY
                normalized == "DRAW" || normalized == "DP" || normalized.contains("UNTAP") ||
                    normalized == "SHIFT" -> DRAW
                normalized == "COMBAT" || normalized == "BP" || normalized.contains("BATTLE") ||
                    normalized.contains("CLASH") || normalized.contains("BREACH") -> COMBAT
                normalized == "END" || normalized == "EP" || normalized.contains("REWIND") ||
                    normalized.contains("OVERDRIVE") -> END
                else -> MAIN
            }
        }
    }
}

/**
 * Turn-based active player & priority owner.
 */
enum class EnginePlayerId(val label: String) {
    PLAYER_1_HOST("Player 1 (Host)"),
    PLAYER_2_OPPONENT("Player 2 (Peer Client)");

    fun opponent(): EnginePlayerId = when (this) {
        PLAYER_1_HOST -> PLAYER_2_OPPONENT
        PLAYER_2_OPPONENT -> PLAYER_1_HOST
    }
}

/**
 * Core Card Effect Resolution archetypes supported by [GameEngine].
 */
enum class CardEffectType(
    val code: String,
    val displayTitle: String,
    val defaultSpellSpeed: Int
) {
    DIRECT_DAMAGE_BURN(
        code = "EFFECT_BURN",
        displayTitle = "Direct Effect Damage (Burn)",
        defaultSpellSpeed = 2
    ),
    LIFE_RESTORE_HEAL(
        code = "EFFECT_HEAL",
        displayTitle = "Restore Life / LP",
        defaultSpellSpeed = 1
    ),
    ATK_DEF_BUFF(
        code = "EFFECT_STAT_BUFF",
        displayTitle = "Boost Unit ATK / DEF",
        defaultSpellSpeed = 2
    ),
    DRAW_CARDS(
        code = "EFFECT_DRAW",
        displayTitle = "Draw Cards from Deck",
        defaultSpellSpeed = 1
    ),
    RESOURCE_RAMP(
        code = "EFFECT_RESOURCE_RAMP",
        displayTitle = "Generate Mana / Resource Pool",
        defaultSpellSpeed = 1
    ),
    TARGET_DESTROY_REMOVAL(
        code = "EFFECT_DESTROY_UNIT",
        displayTitle = "Destroy / Banish Target Unit",
        defaultSpellSpeed = 2
    ),
    NEGATE_AND_COUNTER(
        code = "EFFECT_NEGATE_CHAIN",
        displayTitle = "Negate & Counter Top Stack/Chain Link",
        defaultSpellSpeed = 3
    )
}

/**
 * Represents a queued card effect waiting on the LIFO Chain / Stack inside [GameEngine].
 */
data class CardEffectStackItem(
    val stackId: String,
    val chainLinkNumber: Int,
    val sourceCard: CardTemplate,
    val controller: EnginePlayerId,
    val effectType: CardEffectType,
    val effectValue: Long,
    val effectValue21: String = effectValue.toString(),
    val targetDescription: String,
    val spellSpeed: Int = effectType.defaultSpellSpeed,
    val triggerPhase: TurnPhase = TurnPhase.MAIN
)

/**
 * Immutable audit record emitted whenever [GameEngine] resolves a card effect or phase trigger.
 */
data class ResolvedEffectRecord(
    val resolutionOrder: Int,
    val chainLinkNumber: Int,
    val turnNumber: Long,
    val phase: TurnPhase,
    val sourceCardName: String,
    val sourcePasscode: String,
    val effectType: CardEffectType,
    val summary: String,
    val wasNegated: Boolean = false
)

/**
 * Complete snapshot of the turn-based state managed by [GameEngine].
 */
data class GameEngineState(
    val baseVariantId: String = "EDOPRO_BASE_MR5",
    val turnNumber: Long = 1L,
    val currentPhase: TurnPhase = TurnPhase.MAIN,
    val clientPhaseCode: String = "MP1",
    val activePlayer: EnginePlayerId = EnginePlayerId.PLAYER_1_HOST,
    val priorityHolder: EnginePlayerId = EnginePlayerId.PLAYER_1_HOST,
    val hostName: String = "Player 1 (Host)",
    val opponentName: String = "Player 2 (Peer Client)",
    val hostLife: Long = 8000L,
    val opponentLife: Long = 8000L,
    val hostLife21: String = "8000",
    val opponentLife21: String = "8000",
    val hostResources: Long = 5L,
    val hostMaxResources: Long = 5L,
    val opponentResources: Long = 5L,
    val opponentMaxResources: Long = 5L,
    val hostFieldUnits: List<CardTemplate> = emptyList(),
    val opponentFieldUnits: List<CardTemplate> = emptyList(),
    val handCards: List<CardTemplate> = emptyList(),
    val effectStack: List<CardEffectStackItem> = emptyList(),
    val resolvedEffectsHistory: List<ResolvedEffectRecord> = emptyList(),
    val actionLogs: List<String> = emptyList(),
    val normalSummonsRemaining: Int = 1,
    val hasConductedCombatThisTurn: Boolean = false,
    val status: String = "ACTIVE",
    val winnerName: String = ""
)

/**
 * Result of a turn phase transition, combat execution, or effect resolution in [GameEngine].
 */
data class EngineStepResult(
    val updatedState: GameEngineState,
    val drawnCards: List<CardTemplate> = emptyList(),
    val latestLogLine: String,
    val newLogs: List<String>
)

/**
 * Core `GameEngine` class handling turn-based state management, phase transitions
 * (`DRAW`, `STANDBY`, `MAIN`, `COMBAT`, `END`), combat calculation, and LIFO Stack/Chain
 * card effect resolution across all supported TCG base variants and 21-digit custom clones.
 */
class GameEngine(
    initialBaseVariantId: String = "EDOPRO_BASE_MR5"
) {
    private val _state = MutableStateFlow(createInitialState(initialBaseVariantId))
    val state: StateFlow<GameEngineState> = _state.asStateFlow()

    companion object {
        fun createInitialState(
            baseVariantId: String,
            hostName: String = "Player 1 (Host)",
            opponentName: String = "Player 2 (Peer Client)"
        ): GameEngineState {
            val spec = BuiltInTcgCatalog.findBaseSpec(baseVariantId)
            val catalog = BuiltInTcgCatalog.cardsForVariant(baseVariantId)
            val is21DigitVariant = baseVariantId == "EDOPRO_CUSTOM_FORK" ||
                baseVariantId == "FORGE_CUSTOM_CLONE" ||
                baseVariantId == "HYBRID_FORGE_EDOPRO_MIX"
            val initialLife21 = if (is21DigitVariant) {
                BigInt21Engine.MAX_21_DIGIT_STRING
            } else {
                spec.startingLife.toString()
            }
            val startPhase = TurnPhase.MAIN
            val phaseCode = startPhase.clientPhaseCodeFor(baseVariantId)
            return GameEngineState(
                baseVariantId = baseVariantId,
                turnNumber = 1L,
                currentPhase = startPhase,
                clientPhaseCode = phaseCode,
                activePlayer = EnginePlayerId.PLAYER_1_HOST,
                priorityHolder = EnginePlayerId.PLAYER_1_HOST,
                hostName = hostName,
                opponentName = opponentName,
                hostLife = spec.startingLife,
                opponentLife = spec.startingLife,
                hostLife21 = initialLife21,
                opponentLife21 = initialLife21,
                hostResources = spec.maxResourcePool.coerceAtMost(5L),
                hostMaxResources = spec.maxResourcePool.coerceAtMost(5L),
                opponentResources = spec.maxResourcePool.coerceAtMost(5L),
                opponentMaxResources = spec.maxResourcePool.coerceAtMost(5L),
                hostFieldUnits = catalog.take(1),
                opponentFieldUnits = catalog.drop(1).take(1),
                handCards = catalog,
                actionLogs = listOf(
                    "GAME_ENGINE_INIT: Initialized ${spec.name} Turn State Machine [Turn 1 • ${startPhase.displayName} ($phaseCode)]"
                )
            )
        }

        fun inferCardEffect(card: CardTemplate): CardEffectType {
            val textLower = card.abilityText.lowercase()
            val typeLower = card.cardType.lowercase()
            return when {
                textLower.contains("negate") || textLower.contains("counter") || typeLower.contains("counter") ->
                    CardEffectType.NEGATE_AND_COUNTER
                textLower.contains("destroy") || textLower.contains("banish") || textLower.contains("exile") ->
                    CardEffectType.TARGET_DESTROY_REMOVAL
                textLower.contains("draw") ->
                    CardEffectType.DRAW_CARDS
                textLower.contains("restore") || (textLower.contains("gain") && textLower.contains("lp")) ||
                    textLower.contains("lifelink") ->
                    CardEffectType.LIFE_RESTORE_HEAL
                textLower.contains("mana") || textLower.contains("add {") || textLower.contains("resource") ->
                    CardEffectType.RESOURCE_RAMP
                textLower.contains("inflict") || textLower.contains("damage") || textLower.contains("burn") ->
                    CardEffectType.DIRECT_DAMAGE_BURN
                else ->
                    CardEffectType.ATK_DEF_BUFF
            }
        }
    }

    /**
     * Synchronizes the internal [GameEngineState] with an active [MatchSession] and player hand.
     */
    fun syncFromMatchSession(
        match: MatchSession,
        handCards: List<CardTemplate>
    ): GameEngineState {
        val current = _state.value
        val mappedPhase = TurnPhase.fromRawPhaseCode(match.activePhase)
        val hostUnits = match.hostBoardCards.map {
            CardTemplate.fromBoardDescriptor(it, match.baseVariantId)
        }
        val oppUnits = match.opponentBoardCards.map {
            CardTemplate.fromBoardDescriptor(it, match.baseVariantId)
        }
        val peerName = match.connectedClients.firstOrNull { it != match.hostName }
            ?: BuiltInTcgCatalog.systemUiProfileFor(match.baseVariantId).clientDisplayName

        val synced = current.copy(
            baseVariantId = match.baseVariantId,
            turnNumber = match.turnNumber.coerceAtLeast(1L),
            currentPhase = mappedPhase,
            clientPhaseCode = mappedPhase.clientPhaseCodeFor(match.baseVariantId),
            hostName = match.hostName.ifBlank { current.hostName },
            opponentName = peerName,
            hostLife = match.hostLife,
            opponentLife = match.opponentLife,
            hostResources = match.hostResources,
            hostMaxResources = match.hostMaxResources,
            opponentResources = match.opponentResources,
            hostFieldUnits = hostUnits,
            opponentFieldUnits = oppUnits,
            handCards = handCards,
            actionLogs = if (match.actionLog.isNotEmpty()) match.actionLog else current.actionLogs,
            status = match.status,
            winnerName = match.winnerName
        )
        _state.value = synced
        return synced
    }

    /**
     * Resets the [GameEngine] to a fresh duel state for the specified [baseVariantId].
     */
    fun resetForVariant(
        baseVariantId: String,
        hostName: String = _state.value.hostName,
        opponentName: String = _state.value.opponentName
    ): GameEngineState {
        val fresh = createInitialState(baseVariantId, hostName, opponentName)
        _state.value = fresh
        return fresh
    }

    /**
     * Advances the turn state machine to the next sequential phase in the canonical cycle:
     * `DRAW -> STANDBY -> MAIN -> COMBAT -> END -> (Next Turn) DRAW`.
     * Automatically executes phase entry triggers (card draw on `DRAW`, upkeep tick on `STANDBY`,
     * summon/mana reset on `MAIN`, combat calculation on `COMBAT`, and turn handoff on `END -> DRAW`).
     */
    fun advancePhase(): EngineStepResult {
        val current = _state.value
        val nextPhase = current.currentPhase.nextPhase()
        val wrappingToNewTurn = current.currentPhase == TurnPhase.END && nextPhase == TurnPhase.DRAW
        return transitionToPhase(
            targetPhase = nextPhase,
            advanceTurnOnWrap = wrappingToNewTurn
        )
    }

    /**
     * Transitions the [GameEngine] directly to [targetPhase] and executes that phase's
     * entry rules and triggered effects.
     */
    fun transitionToPhase(
        targetPhase: TurnPhase,
        advanceTurnOnWrap: Boolean = false
    ): EngineStepResult {
        val current = _state.value
        if (current.status == "FINISHED") {
            val doneMsg = "MATCH_COMPLETE: Match already finished. Winner: ${current.winnerName}"
            return EngineStepResult(
                updatedState = current,
                latestLogLine = doneMsg,
                newLogs = listOf(doneMsg)
            )
        }

        val spec = BuiltInTcgCatalog.findBaseSpec(current.baseVariantId)
        val isEdoPro = BuiltInTcgCatalog.isEdoProVariant(current.baseVariantId)
        val catalog = BuiltInTcgCatalog.cardsForVariant(current.baseVariantId)
        val emittedLogs = mutableListOf<String>()
        val drawnCards = mutableListOf<CardTemplate>()

        var nextTurn = current.turnNumber
        var nextActivePlayer = current.activePlayer
        var nextHostLife = current.hostLife
        var nextOpponentLife = current.opponentLife
        var nextHostLife21 = current.hostLife21
        var nextOpponentLife21 = current.opponentLife21
        var nextHostRes = current.hostResources
        var nextHostMaxRes = current.hostMaxResources
        var nextOppRes = current.opponentResources
        var nextOppMaxRes = current.opponentMaxResources
        var nextHand = current.handCards
        var nextHostUnits = current.hostFieldUnits
        var nextOppUnits = current.opponentFieldUnits
        var nextNormalSummons = current.normalSummonsRemaining
        var nextConductedCombat = current.hasConductedCombatThisTurn
        var nextStatus = current.status
        var nextWinner = current.winnerName

        if (advanceTurnOnWrap) {
            nextTurn += 1L
            nextActivePlayer = current.activePlayer.opponent()
            nextConductedCombat = false
            nextNormalSummons = 1
        }

        val clientPhaseCode = targetPhase.clientPhaseCodeFor(current.baseVariantId)
        emittedLogs.add(
            "MSG_NEW_PHASE [Turn $nextTurn]: ${current.hostName} transitioned ${current.currentPhase.code} -> ${targetPhase.code} ($clientPhaseCode)"
        )

        when (targetPhase) {
            TurnPhase.DRAW -> {
                nextHostMaxRes = (nextHostMaxRes + 1L).coerceAtMost(spec.maxResourcePool)
                nextHostRes = nextHostMaxRes
                nextOppMaxRes = (nextOppMaxRes + 1L).coerceAtMost(spec.maxResourcePool)
                nextOppRes = nextOppMaxRes
                nextNormalSummons = 1

                if (catalog.isNotEmpty()) {
                    val drawn = catalog[(nextTurn.toInt() + nextHand.size) % catalog.size]
                    drawnCards.add(drawn)
                    nextHand = (nextHand + drawn).take(8)
                    emittedLogs.add(
                        "MSG_DRAW [Turn $nextTurn • ${targetPhase.code}]: ${current.hostName} drew '${drawn.name}' (c${drawn.passcode})"
                    )
                }
            }

            TurnPhase.STANDBY -> {
                val standbyBonus = if (isEdoPro) 200L else 1L
                if (nextHostUnits.isNotEmpty()) {
                    nextHostLife += standbyBonus
                    nextHostLife21 = BigInt21Engine.evaluateFunction21(
                        funcName = "ADD_21",
                        varA21 = nextHostLife21,
                        argB21 = standbyBonus.toString()
                    )
                    emittedLogs.add(
                        "PHASE_TRIGGER [Turn $nextTurn • STANDBY]: Field aura restored +$standbyBonus ${if (isEdoPro) "LP" else "Life"} during Standby/Upkeep (${current.hostName}: $nextHostLife)"
                    )
                } else {
                    emittedLogs.add(
                        "PHASE_TRIGGER [Turn $nextTurn • STANDBY]: Checked Standby/Upkeep triggers — Priority open for Quick-Play / Instant effects."
                    )
                }
            }

            TurnPhase.MAIN -> {
                nextNormalSummons = 1
                emittedLogs.add(
                    "PHASE_TRIGGER [Turn $nextTurn • MAIN]: Main Phase active — Normal Summon ($nextNormalSummons available), Spell/Sorcery deployment, and Effect Stack open."
                )
            }

            TurnPhase.COMBAT -> {
                nextConductedCombat = true
                val defaultAtk = if (isEdoPro) 1800L else 2L
                val totalHostAtk = nextHostUnits.sumOf { it.attack.toLong() }.coerceAtLeast(defaultAtk)
                val oppMitigation = when (spec.combatRule) {
                    "ATK_DEF_BATTLE" -> nextOppUnits.maxOfOrNull { it.attack.toLong() / 2L } ?: 0L
                    "BLOCKER_ASSIGN" -> nextOppUnits.sumOf { it.defense.toLong() / 2L }
                    else -> 0L
                }
                val minDamage = if (isEdoPro) 500L else 1L
                val damageToOpponent = (totalHostAtk - oppMitigation).coerceAtLeast(minDamage)
                nextOpponentLife = (nextOpponentLife - damageToOpponent).coerceAtLeast(0L)
                nextOpponentLife21 = BigInt21Engine.evaluateFunction21(
                    funcName = "SUB_21",
                    varA21 = nextOpponentLife21,
                    argB21 = damageToOpponent.toString()
                )
                emittedLogs.add(
                    "MSG_DAMAGE [Turn $nextTurn • COMBAT (${spec.combatRule})]: ${current.hostName} attacked for $damageToOpponent dmg (${current.opponentName}: $nextOpponentLife remaining)."
                )

                if (nextOpponentLife <= 0L) {
                    nextStatus = "FINISHED"
                    nextWinner = current.hostName
                    emittedLogs.add(
                        "DUEL COMPLETE [Turn $nextTurn]: ${current.hostName} victorious via Combat Phase resolution!"
                    )
                } else {
                    if (nextOppUnits.size < 4 && catalog.isNotEmpty()) {
                        val reinforcement = catalog[(nextTurn.toInt() + nextOppUnits.size) % catalog.size]
                        nextOppUnits = (nextOppUnits + reinforcement).take(5)
                    }
                    val counterDamage = if (isEdoPro) {
                        (nextOppUnits.maxOfOrNull { it.attack.toLong() / 2L } ?: 1200L).coerceIn(600L, 2500L)
                    } else {
                        nextOppUnits.sumOf { it.attack.toLong() }.coerceIn(1L, 5L)
                    }
                    nextHostLife = (nextHostLife - counterDamage).coerceAtLeast(0L)
                    nextHostLife21 = BigInt21Engine.evaluateFunction21(
                        funcName = "SUB_21",
                        varA21 = nextHostLife21,
                        argB21 = counterDamage.toString()
                    )
                    emittedLogs.add(
                        "MSG_DAMAGE [Turn $nextTurn • COMBAT]: ${current.opponentName} counter-clashed for $counterDamage dmg (${current.hostName}: $nextHostLife remaining)."
                    )
                    if (nextHostLife <= 0L) {
                        nextStatus = "FINISHED"
                        nextWinner = current.opponentName
                        emittedLogs.add(
                            "DUEL COMPLETE [Turn $nextTurn]: ${current.opponentName} won the duel!"
                        )
                    }
                }
            }

            TurnPhase.END -> {
                emittedLogs.add(
                    "PHASE_TRIGGER [Turn $nextTurn • END]: End Phase cleanup complete — Hand size verified (${nextHand.size}/8 cards). Ready to advance to Turn ${nextTurn + 1}."
                )
            }
        }

        val combinedLogs = (current.actionLogs + emittedLogs).takeLast(25)
        val updated = current.copy(
            turnNumber = nextTurn,
            currentPhase = targetPhase,
            clientPhaseCode = clientPhaseCode,
            activePlayer = nextActivePlayer,
            priorityHolder = nextActivePlayer,
            hostLife = nextHostLife,
            opponentLife = nextOpponentLife,
            hostLife21 = nextHostLife21,
            opponentLife21 = nextOpponentLife21,
            hostResources = nextHostRes,
            hostMaxResources = nextHostMaxRes,
            opponentResources = nextOppRes,
            opponentMaxResources = nextOppMaxRes,
            hostFieldUnits = nextHostUnits,
            opponentFieldUnits = nextOppUnits,
            handCards = nextHand,
            normalSummonsRemaining = nextNormalSummons,
            hasConductedCombatThisTurn = nextConductedCombat,
            actionLogs = combinedLogs,
            status = nextStatus,
            winnerName = nextWinner
        )
        _state.value = updated
        return EngineStepResult(
            updatedState = updated,
            drawnCards = drawnCards,
            latestLogLine = emittedLogs.last(),
            newLogs = emittedLogs
        )
    }

    /**
     * Parses a [CardTemplate]'s ability text, card type, and stats to build a structured
     * [CardEffectStackItem] for the LIFO Chain / Stack.
     */
    fun parseAndBuildCardEffect(
        card: CardTemplate,
        controller: EnginePlayerId = EnginePlayerId.PLAYER_1_HOST,
        overrideEffectType: CardEffectType? = null
    ): CardEffectStackItem {
        val current = _state.value
        val isEdoPro = BuiltInTcgCatalog.isEdoProVariant(current.baseVariantId)
        val inferredType = overrideEffectType ?: inferCardEffect(card)

        val magnitude: Long = when (inferredType) {
            CardEffectType.DIRECT_DAMAGE_BURN -> if (isEdoPro) {
                (card.attack.toLong() / 2L).coerceIn(500L, 4000L)
            } else {
                card.attack.toLong().coerceIn(2L, 8L)
            }
            CardEffectType.LIFE_RESTORE_HEAL -> if (isEdoPro) 1200L else 4L
            CardEffectType.ATK_DEF_BUFF -> if (isEdoPro) 800L else 2L
            CardEffectType.DRAW_CARDS -> 1L
            CardEffectType.RESOURCE_RAMP -> 2L
            CardEffectType.TARGET_DESTROY_REMOVAL -> 1L
            CardEffectType.NEGATE_AND_COUNTER -> 1L
        }

        val nextChainLink = current.effectStack.size + 1
        val targetDesc = when (inferredType) {
            CardEffectType.DIRECT_DAMAGE_BURN -> "${current.opponentName} (-$magnitude ${if (isEdoPro) "LP" else "Life"})"
            CardEffectType.LIFE_RESTORE_HEAL -> "${current.hostName} (+$magnitude ${if (isEdoPro) "LP" else "Life"})"
            CardEffectType.ATK_DEF_BUFF -> "All Friendly Units (+$magnitude ATK/DEF)"
            CardEffectType.DRAW_CARDS -> "${current.hostName} Deck (+$magnitude Card)"
            CardEffectType.RESOURCE_RAMP -> "${current.hostName} Resource Pool (+$magnitude)"
            CardEffectType.TARGET_DESTROY_REMOVAL ->
                current.opponentFieldUnits.firstOrNull()?.name ?: "Opponent Frontline Unit"
            CardEffectType.NEGATE_AND_COUNTER ->
                current.effectStack.lastOrNull()?.sourceCard?.name ?: "Opponent Chain Link / Stack Spell"
        }

        return CardEffectStackItem(
            stackId = "chain_${current.turnNumber}_${nextChainLink}_${card.id}",
            chainLinkNumber = nextChainLink,
            sourceCard = card,
            controller = controller,
            effectType = inferredType,
            effectValue = magnitude,
            effectValue21 = if (card.attack21.length > 10) card.attack21 else magnitude.toString(),
            targetDescription = targetDesc,
            spellSpeed = inferredType.defaultSpellSpeed,
            triggerPhase = current.currentPhase
        )
    }

    /**
     * Pushes a card effect onto the LIFO Chain / Stack without resolving it immediately,
     * allowing responses (such as Fast Effects, Counter Traps, or Instant spells) to be chained.
     */
    fun queueCardEffect(
        card: CardTemplate,
        controller: EnginePlayerId = EnginePlayerId.PLAYER_1_HOST,
        overrideEffectType: CardEffectType? = null
    ): EngineStepResult {
        val current = _state.value
        val item = parseAndBuildCardEffect(card, controller, overrideEffectType)
        val updatedStack = current.effectStack + item
        val logLine = "MSG_CHAINING [Turn ${current.turnNumber} • ${current.currentPhase.code}]: Chain Link ${item.chainLinkNumber} queued — '${card.name}' [${item.effectType.displayTitle} -> ${item.targetDescription}] (Spell Speed ${item.spellSpeed})"
        val updated = current.copy(
            effectStack = updatedStack,
            priorityHolder = controller.opponent(),
            actionLogs = (current.actionLogs + logLine).takeLast(25)
        )
        _state.value = updated
        return EngineStepResult(
            updatedState = updated,
            latestLogLine = logLine,
            newLogs = listOf(logLine)
        )
    }

    /**
     * Resolves the top-most (last-in) [CardEffectStackItem] from the Chain / Stack (LIFO order)
     * and applies its state mutations to [GameEngineState].
     */
    fun resolveNextEffect(): EngineStepResult {
        val current = _state.value
        if (current.effectStack.isEmpty()) {
            val emptyLog = "EFFECT_STACK_EMPTY [Turn ${current.turnNumber} • ${current.currentPhase.code}]: No pending effects on the Chain/Stack."
            return EngineStepResult(
                updatedState = current,
                latestLogLine = emptyLog,
                newLogs = listOf(emptyLog)
            )
        }

        val topItem = current.effectStack.last()
        var remainingStack = current.effectStack.dropLast(1)
        val isEdoPro = BuiltInTcgCatalog.isEdoProVariant(current.baseVariantId)
        val spec = BuiltInTcgCatalog.findBaseSpec(current.baseVariantId)
        val catalog = BuiltInTcgCatalog.cardsForVariant(current.baseVariantId)

        var nextHostLife = current.hostLife
        var nextOpponentLife = current.opponentLife
        var nextHostLife21 = current.hostLife21
        var nextOpponentLife21 = current.opponentLife21
        var nextHostRes = current.hostResources
        var nextHostMaxRes = current.hostMaxResources
        var nextHostUnits = current.hostFieldUnits
        var nextOppUnits = current.opponentFieldUnits
        var nextHand = current.handCards
        var nextStatus = current.status
        var nextWinner = current.winnerName
        val drawnCards = mutableListOf<CardTemplate>()
        val resolutionLogs = mutableListOf<String>()

        val resolutionSummary: String = when (topItem.effectType) {
            CardEffectType.DIRECT_DAMAGE_BURN -> {
                if (topItem.controller == EnginePlayerId.PLAYER_1_HOST) {
                    nextOpponentLife = (nextOpponentLife - topItem.effectValue).coerceAtLeast(0L)
                    nextOpponentLife21 = BigInt21Engine.evaluateFunction21(
                        funcName = "SUB_21",
                        varA21 = nextOpponentLife21,
                        argB21 = topItem.effectValue21
                    )
                    if (nextOpponentLife <= 0L) {
                        nextStatus = "FINISHED"
                        nextWinner = current.hostName
                    }
                    "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): Inflicted ${topItem.effectValue} effect damage to ${current.opponentName} ($nextOpponentLife ${if (isEdoPro) "LP" else "Life"} left)"
                } else {
                    nextHostLife = (nextHostLife - topItem.effectValue).coerceAtLeast(0L)
                    nextHostLife21 = BigInt21Engine.evaluateFunction21(
                        funcName = "SUB_21",
                        varA21 = nextHostLife21,
                        argB21 = topItem.effectValue21
                    )
                    if (nextHostLife <= 0L) {
                        nextStatus = "FINISHED"
                        nextWinner = current.opponentName
                    }
                    "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): Inflicted ${topItem.effectValue} effect damage to ${current.hostName} ($nextHostLife left)"
                }
            }

            CardEffectType.LIFE_RESTORE_HEAL -> {
                nextHostLife += topItem.effectValue
                nextHostLife21 = BigInt21Engine.evaluateFunction21(
                    funcName = "ADD_21",
                    varA21 = nextHostLife21,
                    argB21 = topItem.effectValue21
                )
                "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): Restored +${topItem.effectValue} ${if (isEdoPro) "LP" else "Life"} to ${current.hostName} (Now $nextHostLife)"
            }

            CardEffectType.ATK_DEF_BUFF -> {
                val targetUnits = if (nextHostUnits.isEmpty()) listOf(topItem.sourceCard) else nextHostUnits
                val deltaInt = topItem.effectValue.coerceAtMost(100000L).toInt()
                nextHostUnits = targetUnits.map { unit ->
                    val boostedAtk = unit.attack + deltaInt
                    val boostedDef = unit.defense + deltaInt
                    val boostedAtk21 = BigInt21Engine.evaluateFunction21(
                        funcName = "ADD_21",
                        varA21 = unit.attack21,
                        argB21 = topItem.effectValue21
                    )
                    val boostedDef21 = BigInt21Engine.evaluateFunction21(
                        funcName = "ADD_21",
                        varA21 = unit.defense21,
                        argB21 = topItem.effectValue21
                    )
                    unit.copy(
                        attack = boostedAtk,
                        defense = boostedDef,
                        attack21 = boostedAtk21,
                        defense21 = boostedDef21
                    )
                }
                "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): Buffed ${nextHostUnits.size} friendly unit(s) by +${topItem.effectValue}/+${topItem.effectValue} ATK/DEF"
            }

            CardEffectType.DRAW_CARDS -> {
                val drawCount = topItem.effectValue.toInt().coerceIn(1, 3)
                repeat(drawCount) { idx ->
                    if (catalog.isNotEmpty()) {
                        val drawn = catalog[(current.turnNumber.toInt() + nextHand.size + idx) % catalog.size]
                        drawnCards.add(drawn)
                        nextHand = (nextHand + drawn).take(8)
                    }
                }
                val drawnNames = drawnCards.joinToString(", ") { "'${it.name}'" }
                "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): Drew ${drawnCards.size} card(s) ($drawnNames)"
            }

            CardEffectType.RESOURCE_RAMP -> {
                nextHostMaxRes = (nextHostMaxRes + topItem.effectValue).coerceAtMost(spec.maxResourcePool)
                nextHostRes = (nextHostRes + topItem.effectValue).coerceAtMost(spec.maxResourcePool)
                "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): Added +${topItem.effectValue} ${spec.resourceName} (Pool: $nextHostRes/$nextHostMaxRes)"
            }

            CardEffectType.TARGET_DESTROY_REMOVAL -> {
                if (nextOppUnits.isNotEmpty()) {
                    val destroyed = nextOppUnits.first()
                    nextOppUnits = nextOppUnits.drop(1)
                    "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): Destroyed opponent unit '${destroyed.name}' and sent it to Graveyard"
                } else {
                    val fallbackBurn = if (isEdoPro) 600L else 2L
                    nextOpponentLife = (nextOpponentLife - fallbackBurn).coerceAtLeast(0L)
                    "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): Opponent field empty — inflicted $fallbackBurn direct breach damage ($nextOpponentLife left)"
                }
            }

            CardEffectType.NEGATE_AND_COUNTER -> {
                if (remainingStack.isNotEmpty()) {
                    val negatedItem = remainingStack.last()
                    remainingStack = remainingStack.dropLast(1)
                    "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): NEGATED & COUNTERED Chain Link ${negatedItem.chainLinkNumber} ('${negatedItem.sourceCard.name}')"
                } else {
                    "Resolved Chain Link ${topItem.chainLinkNumber} ('${topItem.sourceCard.name}'): Counter shield active — negated opponent fast-effect window"
                }
            }
        }

        val logEntry = "MSG_CHAIN_SOLVED [Turn ${current.turnNumber} • ${current.currentPhase.code}]: $resolutionSummary"
        resolutionLogs.add(logEntry)

        val record = ResolvedEffectRecord(
            resolutionOrder = current.resolvedEffectsHistory.size + 1,
            chainLinkNumber = topItem.chainLinkNumber,
            turnNumber = current.turnNumber,
            phase = current.currentPhase,
            sourceCardName = topItem.sourceCard.name,
            sourcePasscode = topItem.sourceCard.passcode,
            effectType = topItem.effectType,
            summary = resolutionSummary
        )

        val updated = current.copy(
            hostLife = nextHostLife,
            opponentLife = nextOpponentLife,
            hostLife21 = nextHostLife21,
            opponentLife21 = nextOpponentLife21,
            hostResources = nextHostRes,
            hostMaxResources = nextHostMaxRes,
            hostFieldUnits = nextHostUnits,
            opponentFieldUnits = nextOppUnits,
            handCards = nextHand,
            effectStack = remainingStack,
            resolvedEffectsHistory = (listOf(record) + current.resolvedEffectsHistory).take(15),
            actionLogs = (current.actionLogs + resolutionLogs).takeLast(25),
            status = nextStatus,
            winnerName = nextWinner
        )
        _state.value = updated
        return EngineStepResult(
            updatedState = updated,
            drawnCards = drawnCards,
            latestLogLine = logEntry,
            newLogs = resolutionLogs
        )
    }

    /**
     * Resolves all queued effects on the Chain / Stack in LIFO order until the stack is empty.
     */
    fun resolveAllEffects(): EngineStepResult {
        if (_state.value.effectStack.isEmpty()) {
            return resolveNextEffect()
        }
        val allNewLogs = mutableListOf<String>()
        val allDrawn = mutableListOf<CardTemplate>()
        var latestResult: EngineStepResult? = null
        var safetyCounter = 0
        while (_state.value.effectStack.isNotEmpty() && safetyCounter < 20) {
            safetyCounter++
            val step = resolveNextEffect()
            latestResult = step
            allNewLogs.addAll(step.newLogs)
            allDrawn.addAll(step.drawnCards)
        }
        val finalState = _state.value
        val summaryLine = latestResult?.latestLogLine
            ?: "MSG_CHAIN_END [Turn ${finalState.turnNumber}]: Resolved full effect stack."
        return EngineStepResult(
            updatedState = finalState,
            drawnCards = allDrawn,
            latestLogLine = summaryLine,
            newLogs = allNewLogs
        )
    }

    /**
     * Adjusts Player 1's available resources (Mana, Energy, or Action Points) by [delta],
     * clamping within `0..hostMaxResources` (or expanding if [delta] exceeds current max up to [maxCeiling]).
     */
    fun adjustPlayerResources(delta: Long, maxCeiling: Long = 12L): EngineStepResult {
        val current = _state.value
        val resLabel = BuiltInTcgCatalog.findBaseSpec(current.baseVariantId).resourceName
        val ceiling = maxCeiling.coerceAtLeast(1L)
        val nextMax = current.hostMaxResources.coerceIn(1L, ceiling)
        val nextAvail = (current.hostResources + delta).coerceIn(0L, nextMax)
        val actionWord = if (delta >= 0) "gained +$delta" else "spent ${-delta}"
        val logLine = "RESOURCE_POOL [Turn ${current.turnNumber} • ${current.currentPhase.code}]: Player 1 $actionWord $resLabel -> $nextAvail/$nextMax available (Ruleset Ceiling: $ceiling)"
        val updated = current.copy(
            hostResources = nextAvail,
            hostMaxResources = nextMax,
            actionLogs = (current.actionLogs + logLine).takeLast(25)
        )
        _state.value = updated
        return EngineStepResult(
            updatedState = updated,
            latestLogLine = logLine,
            newLogs = listOf(logLine)
        )
    }

    /**
     * Sets Player 1's available resources to [exactAmount] when tapping a specific pip in the
     * visual resource counter bar.
     */
    fun setPlayerResources(exactAmount: Long, maxCeiling: Long = 12L): EngineStepResult {
        val current = _state.value
        val resLabel = BuiltInTcgCatalog.findBaseSpec(current.baseVariantId).resourceName
        val ceiling = maxCeiling.coerceAtLeast(1L)
        val nextMax = current.hostMaxResources.coerceIn(1L, ceiling)
        val nextAvail = exactAmount.coerceIn(0L, nextMax)
        val logLine = "RESOURCE_POOL [Turn ${current.turnNumber} • ${current.currentPhase.code}]: Set available $resLabel to $nextAvail/$nextMax"
        val updated = current.copy(
            hostResources = nextAvail,
            hostMaxResources = nextMax,
            actionLogs = (current.actionLogs + logLine).takeLast(25)
        )
        _state.value = updated
        return EngineStepResult(
            updatedState = updated,
            latestLogLine = logLine,
            newLogs = listOf(logLine)
        )
    }

    /**
     * Refills Player 1's available resource pool to `hostMaxResources` (Untap / Recharge / Reset AP).
     */
    fun refillPlayerResources(): EngineStepResult {
        val current = _state.value
        val resLabel = BuiltInTcgCatalog.findBaseSpec(current.baseVariantId).resourceName
        val refilled = current.hostMaxResources.coerceAtLeast(1L)
        val logLine = "RESOURCE_REFILL [Turn ${current.turnNumber} • ${current.currentPhase.code}]: Refilled $resLabel pool to $refilled/$refilled"
        val updated = current.copy(
            hostResources = refilled,
            actionLogs = (current.actionLogs + logLine).takeLast(25)
        )
        _state.value = updated
        return EngineStepResult(
            updatedState = updated,
            latestLogLine = logLine,
            newLogs = listOf(logLine)
        )
    }

    /**
     * Expands Player 1's unlocked resource pool capacity by +1 (up to [maxCeiling]) and grants +1 available resource.
     */
    fun expandMaxResourcePool(maxCeiling: Long = 12L): EngineStepResult {
        val current = _state.value
        val resLabel = BuiltInTcgCatalog.findBaseSpec(current.baseVariantId).resourceName
        val ceiling = maxCeiling.coerceAtLeast(1L)
        val nextMax = (current.hostMaxResources + 1L).coerceAtMost(ceiling)
        val nextAvail = (current.hostResources + 1L).coerceAtMost(nextMax)
        val logLine = "RESOURCE_CAP [Turn ${current.turnNumber} • ${current.currentPhase.code}]: Expanded $resLabel capacity to $nextAvail/$nextMax (Ruleset Ceiling: $ceiling)"
        val updated = current.copy(
            hostResources = nextAvail,
            hostMaxResources = nextMax,
            actionLogs = (current.actionLogs + logLine).takeLast(25)
        )
        _state.value = updated
        return EngineStepResult(
            updatedState = updated,
            latestLogLine = logLine,
            newLogs = listOf(logLine)
        )
    }
}
