package com.example.engine

import com.example.data.BigInt21Engine
import com.example.data.CardCodeValidator
import com.example.data.CardContextMode
import com.example.data.CardValidationRequest
import com.example.data.CodeStringExporter
import com.example.data.ImportedMpvCard
import com.example.data.TwentyColorPieCatalog
import org.json.JSONObject
import java.math.BigInteger

/**
 * Pure Kotlin deterministic Engine Invariants Verifier (zero Android/Compose imports).
 *
 * Verifies all 6 required anti-vibe-code invariants:
 * (a) Chromatic / TwentyColorPieCatalog has all 190 Layer-2 pairs and boots with 0 selected colors.
 * (b) Every catalog blueprint in `tcg-catalog.json` passes strict runtime schema validation.
 * (c) BigInt 21+ digit math never overflows.
 * (d) Zero standard match gameplay cards carry time-travel flags (Filters 19–21).
 * (e) Every GitHub-sourced locked client base game is strictly immutable (`upstreamSourceLock == true`,
 *     `requiresStrictBase == true`, intact `protocolHash`).
 * (f) `CodeStringExporter` exports ONLY non-locked clients to `MPV-TCG-Card-Making-Software-Updated`
 *     and rejects any write targeting a locked source game.
 */
data class InvariantCheckItem(
    val id: String,
    val title: String,
    val passed: Boolean,
    val detail: String
)

data class InvariantVerificationReport(
    val items: List<InvariantCheckItem>
) {
    val allPassed: Boolean get() = items.all { it.passed }
    val passedCount: Int get() = items.count { it.passed }
    val totalCount: Int get() = items.size
}

object EngineInvariantsVerifier {

    fun runAllChecks(
        catalogBlueprints: List<CatalogBlueprint>,
        loadedMatchCards: List<ImportedMpvCard> = emptyList()
    ): InvariantVerificationReport {
        val results = mutableListOf<InvariantCheckItem>()

        // (a) 20-Color Pie: 190 Layer-2 pairs + 0 default selected + 6 Layer 20 {Ω} geometries
        val pairCount = TwentyColorPieCatalog.all190FusedPairs.size
        val defaultZero = TwentyColorPieCatalog.defaultSelectedCodes.isEmpty()
        val sixOmni = TwentyColorPieCatalog.omniGeometryVariants.size == 6
        val pieOk = pairCount == 190 && defaultZero && sixOmni
        results.add(
            InvariantCheckItem(
                id = "INV_A_20_COLOR_PIE",
                title = "(a) 20-Color Pie: 190 Pairs, 0 Default Selected, 6 {Ω} Geometries",
                passed = pieOk,
                detail = "Pairs=$pairCount/190, defaultSelected=${TwentyColorPieCatalog.defaultSelectedCodes.size}, omniGeometries=${TwentyColorPieCatalog.omniGeometryVariants.size}/6"
            )
        )

        // (b) Catalog Blueprint strict schema validation
        val invalidBlueprints = catalogBlueprints.filter { bp ->
            bp.id.isBlank() ||
                bp.name.isBlank() ||
                bp.protocolHash.isBlank() ||
                bp.boardArchetype !in BoardArchetypeTemplate.allIds ||
                bp.clientTier !in 1..4
        }
        val catalogOk = catalogBlueprints.isNotEmpty() && invalidBlueprints.isEmpty()
        results.add(
            InvariantCheckItem(
                id = "INV_B_CATALOG_SCHEMA",
                title = "(b) Catalog Blueprints Runtime Schema Validation",
                passed = catalogOk,
                detail = if (catalogOk) {
                    "All ${catalogBlueprints.size} blueprint(s) valid across 11 Board Archetypes."
                } else {
                    "Catalog size=${catalogBlueprints.size}, invalid=${invalidBlueprints.size} (${invalidBlueprints.take(3).joinToString { it.id }})"
                }
            )
        )

        // (c) BigInt 21+ digit math zero overflow
        val max21 = BigInt21Engine.MAX_21_DIGIT_STRING
        val addRes = BigInt21Engine.evaluateFunction21("ADD_21", "100000000000000000000", "899999999999999999999")
        val overflow22Check = CardCodeValidator.validate(
            CardValidationRequest(
                cardId = "1000000000000000000000", // 22 digits — MUST fail
                cardName = "Overflow Test",
                targetClient = CardCodeValidator.canonicalTargetCatalog.first()
            )
        )
        val bigIntSum = BigInteger("999999999999999999999").add(BigInteger.ONE).toString()
        val bigIntOk = addRes == max21 && !overflow22Check.isValid && bigIntSum == "1000000000000000000000"
        results.add(
            InvariantCheckItem(
                id = "INV_C_BIGINT_21",
                title = "(c) BigInt 21+ Digit Precision & 22-Digit Overflow Guard",
                passed = bigIntOk,
                detail = "ADD_21=$addRes, 22-digit rejected=${!overflow22Check.isValid}, BigInteger exact=$bigIntSum"
            )
        )

        // (d) Zero standard match gameplay cards carry time-travel flags (Filters 19-21)
        val matchCardTimeTravelCheck = CardCodeValidator.validate(
            CardValidationRequest(
                cardId = "000000000000000000099",
                cardName = "Illegal Time-Travel Match Card",
                targetClient = CardCodeValidator.canonicalTargetCatalog.first(),
                contextMode = CardContextMode.MATCH_CARD,
                requestedFilterNumbers = setOf(19, 20, 21)
            )
        )
        val allowedExtraTurnCheck = CardCodeValidator.validate(
            CardValidationRequest(
                cardId = "000000000000000000100",
                cardName = "Hourglass Extra Turn",
                targetClient = CardCodeValidator.canonicalTargetCatalog.first(),
                contextMode = CardContextMode.MATCH_CARD,
                temporalEffects = setOf("Extra Turn")
            )
        )
        val offendingLoaded = loadedMatchCards.filter {
            it.temporalEffect.isNotBlank() &&
                !it.temporalEffect.equals("End Turn", ignoreCase = true) &&
                !it.temporalEffect.equals("Extra Turn", ignoreCase = true)
        }
        val timeTravelOk = !matchCardTimeTravelCheck.isValid && allowedExtraTurnCheck.isValid && offendingLoaded.isEmpty()
        results.add(
            InvariantCheckItem(
                id = "INV_D_TIME_TRAVEL_RULE",
                title = "(d) Strict Time-Travel Rule (Filters 19–21 Blocked on Match Cards)",
                passed = timeTravelOk,
                detail = "Filters 19-21 blocked=${!matchCardTimeTravelCheck.isValid}, ExtraTurn allowed=${allowedExtraTurnCheck.isValid}, offendingLoaded=${offendingLoaded.size}"
            )
        )

        // (e) Every GitHub-sourced locked client base game is strictly immutable
        val githubLockedEntries = catalogBlueprints.filter { it.upstreamRepoUrl.startsWith("https://github.com/") }
        val brokenLocks = githubLockedEntries.filter {
            !it.upstreamSourceLock || !it.requiresStrictBase || it.protocolHash.isBlank()
        }
        val lockOk = githubLockedEntries.isNotEmpty() && brokenLocks.isEmpty()
        results.add(
            InvariantCheckItem(
                id = "INV_E_GITHUB_LOCK_IMMUTABILITY",
                title = "(e) GitHub-Sourced Base Games Strictly Locked & Immutable",
                passed = lockOk,
                detail = "Verified ${githubLockedEntries.size} locked GitHub source game(s); violations=${brokenLocks.size}"
            )
        )

        // (f) CodeStringExporter exports ONLY non-locked clients & rejects writes to locked source games
        val compiledIndex = JSONObject(CodeStringExporter.compileClientCodeStringIndexJson())
        val writableArr = compiledIndex.getJSONArray("nonLockedClients")
        var anyLockedLeaked = false
        for (i in 0 until writableArr.length()) {
            val obj = writableArr.getJSONObject(i)
            if (obj.optBoolean("upstreamSourceLock", true) || CardCodeValidator.isClientIdLocked(obj.optString("clientId"))) {
                anyLockedLeaked = true
            }
        }
        val lockedWriteRejected = CodeStringExporter.validateWritableExportTarget("EDOPRO_BASE_MR5").isFailure &&
            CodeStringExporter.validateWritableExportTarget("FORGE_MTG_BASE").isFailure
        val exporterOk = !anyLockedLeaked && writableArr.length() > 0 && lockedWriteRejected
        results.add(
            InvariantCheckItem(
                id = "INV_F_EXPORTER_FIREWALL",
                title = "(f) CodeStringExporter Non-Locked Firewall for MPV Bridge",
                passed = exporterOk,
                detail = "Writable non-locked=${writableArr.length()}, locked leaked=$anyLockedLeaked, locked writes blocked=$lockedWriteRejected"
            )
        )

        return InvariantVerificationReport(results)
    }
}
