package com.example.data.telemetry

import android.content.Context
import com.example.data.CardOrigin
import com.example.data.CardTemplate
import com.example.data.CodeStringExporter
import com.example.data.NexusHubBridgeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class CardInteractionType(val wireValue: String, val displayLabel: String) {
    CHAIN_RESPONSE("CHAIN_RESPONSE", "Chain Response"),
    COMBO_SYNERGY("COMBO_SYNERGY", "Combo Synergy"),
    COUNTER_NEGATED("COUNTER_NEGATED", "Counter / Negated"),
    COMBAT_DEFEATED("COMBAT_DEFEATED", "Combat Defeated"),
    COMBAT_DESTROYED_BY("COMBAT_DESTROYED_BY", "Destroyed By"),
    STACK_CONFLICT("STACK_CONFLICT", "Stack Conflict"),
    RULE_VALIDATION_ERROR("RULE_VALIDATION_ERROR", "Rule Validation Error"),
    SCRIPT_EXCEPTION("SCRIPT_EXCEPTION", "Script Exception");

    companion object {
        fun fromRaw(raw: String?): CardInteractionType {
            val clean = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.wireValue == clean || it.name == clean }
                ?: CHAIN_RESPONSE
        }
    }
}

data class CardUsageMetric(
    val cardId: String,
    val register21Digit: String,
    val cardName: String,
    val targetClientId: String,
    val splitOffSetId: String,
    val parentOfficialSetId: String,
    val cardOrigin: CardOrigin,
    val linkedCanonCardId: String,
    val matchesIncluded: Int = 0,
    val timesDrawn: Int = 0,
    val timesPlayed: Int = 0,
    val timesActivated: Int = 0,
    val timesAttacked: Int = 0,
    val timesDestroyed: Int = 0,
    val totalDamageDealt: Long = 0L,
    val winsWhenPlayed: Int = 0,
    val lossesWhenPlayed: Int = 0,
    val turnPlayedTotal: Long = 0L,
    val ruleOrScriptErrorCount: Int = 0
) {
    val averageTurnPlayed: Double
        get() = if (timesPlayed > 0) {
            Math.round((turnPlayedTotal.toDouble() / timesPlayed.toDouble()) * 100.0) / 100.0
        } else {
            0.0
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("cardId", cardId)
        put("register21Digit", register21Digit)
        put("cardName", cardName)
        put("targetClientId", targetClientId)
        put("splitOffSetId", splitOffSetId)
        put("parentOfficialSetId", parentOfficialSetId)
        put("cardOrigin", cardOrigin.wireValue)
        put("linkedCanonCardId", linkedCanonCardId)
        put("matchesIncluded", matchesIncluded)
        put("timesDrawn", timesDrawn)
        put("timesPlayed", timesPlayed)
        put("timesActivated", timesActivated)
        put("timesAttacked", timesAttacked)
        put("timesDestroyed", timesDestroyed)
        put("totalDamageDealt", totalDamageDealt)
        put("winsWhenPlayed", winsWhenPlayed)
        put("lossesWhenPlayed", lossesWhenPlayed)
        put("averageTurnPlayed", averageTurnPlayed)
        put("ruleOrScriptErrorCount", ruleOrScriptErrorCount)
    }

    companion object {
        fun fromJson(obj: JSONObject): CardUsageMetric {
            val played = obj.optInt("timesPlayed", 0)
            val avgTurn = obj.optDouble("averageTurnPlayed", 0.0)
            return CardUsageMetric(
                cardId = obj.optString("cardId", ""),
                register21Digit = obj.optString("register21Digit", ""),
                cardName = obj.optString("cardName", ""),
                targetClientId = obj.optString("targetClientId", "ULTIMATE_TCG_WIP"),
                splitOffSetId = obj.optString("splitOffSetId", ""),
                parentOfficialSetId = obj.optString("parentOfficialSetId", ""),
                cardOrigin = CardOrigin.fromRaw(obj.optString("cardOrigin", "")),
                linkedCanonCardId = obj.optString("linkedCanonCardId", ""),
                matchesIncluded = obj.optInt("matchesIncluded", 0),
                timesDrawn = obj.optInt("timesDrawn", 0),
                timesPlayed = played,
                timesActivated = obj.optInt("timesActivated", 0),
                timesAttacked = obj.optInt("timesAttacked", 0),
                timesDestroyed = obj.optInt("timesDestroyed", 0),
                totalDamageDealt = obj.optLong("totalDamageDealt", 0L),
                winsWhenPlayed = obj.optInt("winsWhenPlayed", 0),
                lossesWhenPlayed = obj.optInt("lossesWhenPlayed", 0),
                turnPlayedTotal = Math.round(avgTurn * played),
                ruleOrScriptErrorCount = obj.optInt("ruleOrScriptErrorCount", 0)
            )
        }
    }
}

data class CardInteractionMetric(
    val interactionType: CardInteractionType,
    val sourceCardId: String,
    val sourceCardName: String,
    val sourceCardOrigin: CardOrigin,
    val targetCardId: String,
    val targetCardName: String,
    val targetCardOrigin: CardOrigin,
    val occurrenceCount: Int,
    val phase: String,
    val baseVariantId: String,
    val lastOccurredAt: String,
    val sampleLogLine: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("interactionType", interactionType.wireValue)
        put("sourceCardId", sourceCardId)
        put("sourceCardName", sourceCardName)
        put("sourceCardOrigin", sourceCardOrigin.wireValue)
        put("targetCardId", targetCardId)
        put("targetCardName", targetCardName)
        put("targetCardOrigin", targetCardOrigin.wireValue)
        put("occurrenceCount", occurrenceCount)
        put("phase", phase)
        put("baseVariantId", baseVariantId)
        put("lastOccurredAt", lastOccurredAt)
        put("sampleLogLine", sampleLogLine)
    }

    companion object {
        fun fromJson(obj: JSONObject): CardInteractionMetric = CardInteractionMetric(
            interactionType = CardInteractionType.fromRaw(obj.optString("interactionType", "")),
            sourceCardId = obj.optString("sourceCardId", ""),
            sourceCardName = obj.optString("sourceCardName", ""),
            sourceCardOrigin = CardOrigin.fromRaw(obj.optString("sourceCardOrigin", "")),
            targetCardId = obj.optString("targetCardId", ""),
            targetCardName = obj.optString("targetCardName", ""),
            targetCardOrigin = CardOrigin.fromRaw(obj.optString("targetCardOrigin", "")),
            occurrenceCount = obj.optInt("occurrenceCount", 1).coerceAtLeast(1),
            phase = obj.optString("phase", "MAIN"),
            baseVariantId = obj.optString("baseVariantId", "ULTIMATE_TCG_WIP"),
            lastOccurredAt = obj.optString("lastOccurredAt", ""),
            sampleLogLine = obj.optString("sampleLogLine", "")
        )
    }
}

data class CustomCardUsageReportSnapshot(
    val schemaVersion: String = CardUsageTelemetryTracker.REPORT_SCHEMA_VERSION,
    val sourceEngineRepo: String = CodeStringExporter.GAME_ENGINE_REPO,
    val sourceEnginePackage: String = CodeStringExporter.GAME_ENGINE_PACKAGE,
    val targetCardMakerRepo: String = CodeStringExporter.CARD_MAKER_REPO,
    val targetCardMakerPackage: String = CodeStringExporter.CARD_MAKER_PACKAGE,
    val generatedAtUtc: String = "",
    val totalMatchesTracked: Int = 0,
    val archivedReportCount: Int = 0,
    val cards: List<CardUsageMetric> = emptyList(),
    val interactions: List<CardInteractionMetric> = emptyList()
) {
    val totalPlaysRecorded: Int get() = cards.sumOf { it.timesPlayed }
    val totalInteractionsRecorded: Int get() = interactions.sumOf { it.occurrenceCount }
    val hasRealTelemetry: Boolean get() = cards.isNotEmpty() || interactions.isNotEmpty()

    fun toJsonString(): String {
        val root = JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("reportType", "CustomCardUsageReport")
            put("sourceEngineRepo", sourceEngineRepo)
            put("sourceEnginePackage", sourceEnginePackage)
            put("targetCardMakerRepo", targetCardMakerRepo)
            put("targetCardMakerPackage", targetCardMakerPackage)
            put("generatedAtUtc", generatedAtUtc.ifBlank { currentUtcIso() })
            put("totalMatchesTracked", totalMatchesTracked)
            put(
                "originBreakdown",
                JSONObject().apply {
                    put("canonOriginalCards", cards.count { it.cardOrigin == CardOrigin.CANON_ORIGINAL })
                    put("fanAlternateVariantCards", cards.count { it.cardOrigin == CardOrigin.FAN_ALTERNATE_VARIANT })
                    put("originalOcCards", cards.count { it.cardOrigin == CardOrigin.ORIGINAL_OC_CARD })
                }
            )
            put("cardMetrics", JSONArray().apply { cards.forEach { put(it.toJson()) } })
            put("interactionMetrics", JSONArray().apply { interactions.forEach { put(it.toJson()) } })
        }
        return root.toString(2)
    }

    companion object {
        private fun currentUtcIso(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.format(Date())
        }
    }
}

/**
 * Live, zero-mock Custom Card Usage & Interaction Telemetry Tracker matching
 * `MPV-TCG-Card-Making-Software-Updated`'s `CustomCardUsageReport` `schemaVersion: "1.0.0"`.
 *
 * Design guarantees:
 * - Starts 100% empty (ZERO fake, seeded, or hardcoded stats).
 * - Tracks real per-card metrics & Canon Original <-> AU Variant <-> Fan-Fiction OC interactions.
 * - Persists locally via SharedPreferences when attached to Android Context, with Reset & Archive.
 * - Pure JVM-safe core so unit tests can exercise every method deterministically.
 */
class CardUsageTelemetryTracker {

    companion object {
        const val REPORT_SCHEMA_VERSION = "1.0.0"
        private const val PREFS_NAME = "nexus_tcg_card_usage_telemetry_v1"
        private const val KEY_ACTIVE_JSON = "active_report_json"
        private const val KEY_ARCHIVED_COUNT = "archived_report_count"
    }

    private val cardMetricsMap = LinkedHashMap<String, CardUsageMetric>()
    private val interactionMetricsMap = LinkedHashMap<String, CardInteractionMetric>()
    private val activeMatchPlayedCardKeys = LinkedHashSet<String>()
    private val activeMatchIncludedCardKeys = LinkedHashSet<String>()
    private var activeMatchId: String = "local_session"
    private var totalMatchesTracked: Int = 0
    private var archivedReportCount: Int = 0
    private var appContext: Context? = null

    private val _snapshot = MutableStateFlow(CustomCardUsageReportSnapshot())
    val snapshot: StateFlow<CustomCardUsageReportSnapshot> = _snapshot.asStateFlow()

    @Synchronized
    fun attachLocalPersistence(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        archivedReportCount = prefs.getInt(KEY_ARCHIVED_COUNT, 0)
        val savedJson = prefs.getString(KEY_ACTIVE_JSON, null)
        if (!savedJson.isNullOrBlank()) {
            restoreFromJson(savedJson)
        } else {
            publishSnapshot()
        }
    }

    @Synchronized
    fun beginMatchTracking(matchId: String, initialDeckOrHand: List<CardTemplate> = emptyList()) {
        val cleanId = matchId.trim().ifEmpty { "match_${System.currentTimeMillis()}" }
        if (cleanId != activeMatchId) {
            activeMatchId = cleanId
            activeMatchPlayedCardKeys.clear()
            activeMatchIncludedCardKeys.clear()
            totalMatchesTracked += 1
        }
        initialDeckOrHand.forEach { card ->
            markIncludedInMatch(card)
        }
        publishSnapshot()
    }

    @Synchronized
    fun recordCardDrawn(card: CardTemplate, matchId: String = activeMatchId) {
        ensureMatchSession(matchId)
        val key = metricKey(card)
        val existing = getOrCreateMetric(card)
        val includedInc = if (activeMatchIncludedCardKeys.add(key)) 1 else 0
        cardMetricsMap[key] = existing.copy(
            matchesIncluded = existing.matchesIncluded + includedInc,
            timesDrawn = existing.timesDrawn + 1
        )
        publishSnapshot()
    }

    @Synchronized
    fun recordCardPlayed(
        card: CardTemplate,
        turnNumber: Long,
        matchId: String = activeMatchId
    ) {
        ensureMatchSession(matchId)
        val key = metricKey(card)
        val existing = getOrCreateMetric(card)
        val includedInc = if (activeMatchIncludedCardKeys.add(key)) 1 else 0
        activeMatchPlayedCardKeys.add(key)
        cardMetricsMap[key] = existing.copy(
            matchesIncluded = existing.matchesIncluded + includedInc,
            timesPlayed = existing.timesPlayed + 1,
            turnPlayedTotal = existing.turnPlayedTotal + turnNumber.coerceAtLeast(1L)
        )
        publishSnapshot()
    }

    @Synchronized
    fun recordCardActivated(
        card: CardTemplate,
        damageDealt: Long = 0L,
        matchId: String = activeMatchId
    ) {
        ensureMatchSession(matchId)
        val key = metricKey(card)
        val existing = getOrCreateMetric(card)
        val includedInc = if (activeMatchIncludedCardKeys.add(key)) 1 else 0
        activeMatchPlayedCardKeys.add(key)
        cardMetricsMap[key] = existing.copy(
            matchesIncluded = existing.matchesIncluded + includedInc,
            timesActivated = existing.timesActivated + 1,
            totalDamageDealt = existing.totalDamageDealt + damageDealt.coerceAtLeast(0L)
        )
        publishSnapshot()
    }

    @Synchronized
    fun recordCardAttacked(
        attacker: CardTemplate,
        damageDealt: Long,
        defender: CardTemplate? = null,
        defenderDestroyed: Boolean = false,
        attackerDestroyed: Boolean = false,
        phase: String = "COMBAT",
        baseVariantId: String = attacker.baseVariantId,
        logLine: String = ""
    ) {
        ensureMatchSession(activeMatchId)
        val atkKey = metricKey(attacker)
        val existingAtk = getOrCreateMetric(attacker)
        val inc = if (activeMatchIncludedCardKeys.add(atkKey)) 1 else 0
        activeMatchPlayedCardKeys.add(atkKey)
        cardMetricsMap[atkKey] = existingAtk.copy(
            matchesIncluded = existingAtk.matchesIncluded + inc,
            timesAttacked = existingAtk.timesAttacked + 1,
            timesDestroyed = existingAtk.timesDestroyed + if (attackerDestroyed) 1 else 0,
            totalDamageDealt = existingAtk.totalDamageDealt + damageDealt.coerceAtLeast(0L)
        )

        if (defender != null) {
            val defKey = metricKey(defender)
            val existingDef = getOrCreateMetric(defender)
            val defInc = if (activeMatchIncludedCardKeys.add(defKey)) 1 else 0
            cardMetricsMap[defKey] = existingDef.copy(
                matchesIncluded = existingDef.matchesIncluded + defInc,
                timesDestroyed = existingDef.timesDestroyed + if (defenderDestroyed) 1 else 0
            )
            if (defenderDestroyed) {
                recordInteractionInternal(
                    type = CardInteractionType.COMBAT_DEFEATED,
                    source = attacker,
                    target = defender,
                    phase = phase,
                    baseVariantId = baseVariantId,
                    sampleLogLine = logLine.ifBlank { "${attacker.name} defeated ${defender.name} in combat ($damageDealt dmg)." }
                )
            }
            if (attackerDestroyed) {
                recordInteractionInternal(
                    type = CardInteractionType.COMBAT_DESTROYED_BY,
                    source = attacker,
                    target = defender,
                    phase = phase,
                    baseVariantId = baseVariantId,
                    sampleLogLine = logLine.ifBlank { "${attacker.name} was destroyed by ${defender.name} in combat." }
                )
            }
        }
        publishSnapshot()
    }

    @Synchronized
    fun recordCardDestroyed(card: CardTemplate, destroyedBy: CardTemplate? = null, phase: String = "MAIN") {
        ensureMatchSession(activeMatchId)
        val key = metricKey(card)
        val existing = getOrCreateMetric(card)
        val inc = if (activeMatchIncludedCardKeys.add(key)) 1 else 0
        cardMetricsMap[key] = existing.copy(
            matchesIncluded = existing.matchesIncluded + inc,
            timesDestroyed = existing.timesDestroyed + 1
        )
        if (destroyedBy != null) {
            recordInteractionInternal(
                type = CardInteractionType.COMBAT_DESTROYED_BY,
                source = card,
                target = destroyedBy,
                phase = phase,
                baseVariantId = card.baseVariantId,
                sampleLogLine = "${card.name} destroyed by ${destroyedBy.name}."
            )
        }
        publishSnapshot()
    }

    @Synchronized
    fun recordRuleOrScriptError(
        card: CardTemplate,
        interactionType: CardInteractionType = CardInteractionType.RULE_VALIDATION_ERROR,
        phase: String = "MAIN",
        detail: String
    ) {
        ensureMatchSession(activeMatchId)
        val key = metricKey(card)
        val existing = getOrCreateMetric(card)
        cardMetricsMap[key] = existing.copy(
            ruleOrScriptErrorCount = existing.ruleOrScriptErrorCount + 1
        )
        recordInteractionInternal(
            type = interactionType,
            source = card,
            target = card,
            phase = phase,
            baseVariantId = card.baseVariantId,
            sampleLogLine = detail
        )
        publishSnapshot()
    }

    @Synchronized
    fun recordCardInteraction(
        type: CardInteractionType,
        source: CardTemplate,
        target: CardTemplate,
        phase: String,
        baseVariantId: String,
        sampleLogLine: String
    ) {
        ensureMatchSession(activeMatchId)
        recordInteractionInternal(type, source, target, phase, baseVariantId, sampleLogLine)
        publishSnapshot()
    }

    @Synchronized
    fun recordMatchOutcome(hostWon: Boolean, matchId: String = activeMatchId) {
        ensureMatchSession(matchId)
        activeMatchPlayedCardKeys.forEach { key ->
            val current = cardMetricsMap[key] ?: return@forEach
            cardMetricsMap[key] = if (hostWon) {
                current.copy(winsWhenPlayed = current.winsWhenPlayed + 1)
            } else {
                current.copy(lossesWhenPlayed = current.lossesWhenPlayed + 1)
            }
        }
        activeMatchPlayedCardKeys.clear()
        publishSnapshot()
    }

    @Synchronized
    fun archiveAndReset(): String {
        val exportedJson = buildCurrentSnapshot().toJsonString()
        archivedReportCount += 1
        cardMetricsMap.clear()
        interactionMetricsMap.clear()
        activeMatchPlayedCardKeys.clear()
        activeMatchIncludedCardKeys.clear()
        activeMatchId = ""
        totalMatchesTracked = 0
        publishSnapshot()
        return exportedJson
    }

    @Synchronized
    fun exportReportJson(): String = buildCurrentSnapshot().toJsonString()

    private fun ensureMatchSession(matchId: String) {
        val cleanId = matchId.trim().ifEmpty { activeMatchId.ifBlank { "local_session" } }
        if (totalMatchesTracked == 0) {
            activeMatchId = cleanId
            activeMatchPlayedCardKeys.clear()
            activeMatchIncludedCardKeys.clear()
            totalMatchesTracked = 1
        } else if (cleanId != activeMatchId) {
            activeMatchId = cleanId
            activeMatchPlayedCardKeys.clear()
            activeMatchIncludedCardKeys.clear()
            totalMatchesTracked += 1
        }
    }

    private fun markIncludedInMatch(card: CardTemplate) {
        val key = metricKey(card)
        val existing = getOrCreateMetric(card)
        if (activeMatchIncludedCardKeys.add(key)) {
            cardMetricsMap[key] = existing.copy(matchesIncluded = existing.matchesIncluded + 1)
        }
    }

    private fun recordInteractionInternal(
        type: CardInteractionType,
        source: CardTemplate,
        target: CardTemplate,
        phase: String,
        baseVariantId: String,
        sampleLogLine: String
    ) {
        val key = "${type.wireValue}|${source.id}|${target.id}|$baseVariantId"
        val now = NexusHubBridgeService.currentIsoUtc()
        val prev = interactionMetricsMap[key]
        interactionMetricsMap[key] = if (prev != null) {
            prev.copy(
                occurrenceCount = prev.occurrenceCount + 1,
                phase = phase,
                lastOccurredAt = now,
                sampleLogLine = sampleLogLine.take(240)
            )
        } else {
            CardInteractionMetric(
                interactionType = type,
                sourceCardId = source.id,
                sourceCardName = source.name,
                sourceCardOrigin = source.cardOrigin,
                targetCardId = target.id,
                targetCardName = target.name,
                targetCardOrigin = target.cardOrigin,
                occurrenceCount = 1,
                phase = phase,
                baseVariantId = baseVariantId,
                lastOccurredAt = now,
                sampleLogLine = sampleLogLine.take(240)
            )
        }
    }

    private fun metricKey(card: CardTemplate): String = "${card.baseVariantId}:${card.id}"

    private fun getOrCreateMetric(card: CardTemplate): CardUsageMetric {
        val key = metricKey(card)
        return cardMetricsMap.getOrPut(key) {
            val digitsOnly = card.passcode.filter { it.isDigit() }.take(21)
            CardUsageMetric(
                cardId = card.id,
                register21Digit = digitsOnly.ifEmpty { card.id.filter { it.isDigit() }.take(21) },
                cardName = card.name,
                targetClientId = card.baseVariantId,
                splitOffSetId = card.splitOffSetId,
                parentOfficialSetId = card.parentOfficialSetId,
                cardOrigin = card.cardOrigin,
                linkedCanonCardId = card.linkedCanonCardId
            )
        }
    }

    private fun buildCurrentSnapshot(): CustomCardUsageReportSnapshot {
        return CustomCardUsageReportSnapshot(
            schemaVersion = REPORT_SCHEMA_VERSION,
            generatedAtUtc = NexusHubBridgeService.currentIsoUtc(),
            totalMatchesTracked = totalMatchesTracked,
            archivedReportCount = archivedReportCount,
            cards = cardMetricsMap.values.sortedByDescending { it.timesPlayed + it.timesActivated + it.timesDrawn },
            interactions = interactionMetricsMap.values.sortedByDescending { it.occurrenceCount }
        )
    }

    private fun publishSnapshot() {
        val snap = buildCurrentSnapshot()
        _snapshot.value = snap
        val ctx = appContext ?: return
        runCatching {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_JSON, snap.toJsonString())
                .putInt(KEY_ARCHIVED_COUNT, archivedReportCount)
                .apply()
        }
    }

    private fun restoreFromJson(rawJson: String) {
        runCatching {
            val root = JSONObject(rawJson)
            totalMatchesTracked = root.optInt("totalMatchesTracked", 0)
            val cardsArr = root.optJSONArray("cardMetrics")
            if (cardsArr != null) {
                for (i in 0 until cardsArr.length()) {
                    val o = cardsArr.optJSONObject(i) ?: continue
                    val m = CardUsageMetric.fromJson(o)
                    if (m.cardId.isNotBlank()) {
                        cardMetricsMap["${m.targetClientId}:${m.cardId}"] = m
                    }
                }
            }
            val interArr = root.optJSONArray("interactionMetrics")
            if (interArr != null) {
                for (i in 0 until interArr.length()) {
                    val o = interArr.optJSONObject(i) ?: continue
                    val im = CardInteractionMetric.fromJson(o)
                    val k = "${im.interactionType.wireValue}|${im.sourceCardId}|${im.targetCardId}|${im.baseVariantId}"
                    interactionMetricsMap[k] = im
                }
            }
        }
        publishSnapshot()
    }
}
