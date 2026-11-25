package com.example

import com.example.data.AlignmentEvaluationInput
import com.example.data.AlignmentHealthState
import com.example.data.CardCodeValidator
import com.example.data.CardContextMode
import com.example.data.CardOrigin
import com.example.data.CardTemplate
import com.example.data.CardValidationRequest
import com.example.data.ClientTargetDescriptor
import com.example.data.CodeStringExporter
import com.example.data.PairCategory
import com.example.data.RegistryAssetLoader
import com.example.data.StartupAlignmentChecker
import com.example.data.TwentyColorPieCatalog
import com.example.data.telemetry.CardInteractionType
import com.example.data.telemetry.CardUsageTelemetryTracker
import com.example.engine.ComboSynthesizer
import com.example.engine.EngineInvariantsVerifier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EngineAlignmentAndInvariantsTest {

    private fun loadBundledCatalogFile(): String {
        val candidates = listOf(
            File("src/main/assets/registry/tcg-catalog.json"),
            File("app/src/main/assets/registry/tcg-catalog.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate tcg-catalog.json in ${candidates.map { it.absolutePath }}")
        return file.readText(Charsets.UTF_8)
    }

    private fun loadBundledCardPacksFile(): String {
        val candidates = listOf(
            File("src/main/assets/registry/card-packs.json"),
            File("app/src/main/assets/registry/card-packs.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate card-packs.json in ${candidates.map { it.absolutePath }}")
        return file.readText(Charsets.UTF_8)
    }

    @Test
    fun allSixEngineInvariantsPassAgainstBundledCatalog() {
        val warnings = mutableListOf<String>()
        val catalog = RegistryAssetLoader.parseTcgCatalogJson(loadBundledCatalogFile(), warnings)
        assertTrue("Expected zero parse warnings, got: $warnings", warnings.isEmpty())
        assertTrue("Expected at least 150 catalog games, got ${catalog.size}", catalog.size >= 150)

        val report = EngineInvariantsVerifier.runAllChecks(catalog)
        assertEquals(6, report.totalCount)
        assertTrue(
            "Expected all 6 invariants to pass, failed: ${report.items.filter { !it.passed }}",
            report.allPassed
        )
    }

    @Test
    fun twentyColorPieParityAndSixOmniGeometries() {
        assertEquals(0, TwentyColorPieCatalog.defaultSelectedCodes.size)
        assertEquals(20, TwentyColorPieCatalog.wheelColors.size)
        assertEquals(190, TwentyColorPieCatalog.all190FusedPairs.size)
        assertEquals(5, TwentyColorPieCatalog.all190FusedPairs.count { it.category == PairCategory.ALLIED_ANCHORS })
        assertEquals(5, TwentyColorPieCatalog.all190FusedPairs.count { it.category == PairCategory.ENEMY_ANCHORS })
        assertEquals(10, TwentyColorPieCatalog.all190FusedPairs.count { it.category == PairCategory.OPPOSITES_180 })
        assertEquals(70, TwentyColorPieCatalog.all190FusedPairs.count { it.category == PairCategory.ANCHOR_BRIDGE })
        assertEquals(100, TwentyColorPieCatalog.all190FusedPairs.count { it.category == PairCategory.BRIDGE_BRIDGE })
        // Total structural Anchor-Bridge (70 non-opposite + 5 opposite) = 75; Bridge-Bridge (100 + 5) = 105
        assertEquals(75, TwentyColorPieCatalog.all190FusedPairs.count { it.first.isAnchor != it.second.isAnchor })
        assertEquals(105, TwentyColorPieCatalog.all190FusedPairs.count { !it.first.isAnchor && !it.second.isAnchor })
        assertEquals(190, PairCategory.entries.sumOf { it.expectedCount })
        assertEquals(17, TwentyColorPieCatalog.curatedLayer3Trios.size)
        assertEquals(25, TwentyColorPieCatalog.layers4To17Samples.size)
        assertEquals(20, TwentyColorPieCatalog.layer19AllMinusOne.size)
        assertEquals(6, TwentyColorPieCatalog.omniGeometryVariants.size)
        assertEquals(
            setOf("{Ω·M}", "{Ω·C}", "{Ω·S}", "{Ω·V}", "{Ω·E}", "{Ω·A}"),
            TwentyColorPieCatalog.omniGeometryVariants.map { it.code }.toSet()
        )
        // RGB midpoint & contrast helpers
        val mid = TwentyColorPieCatalog.mixHex(0xFF000000, 0xFFFFFFFF, 0.5f)
        assertEquals(0xFF808080, mid)
        assertEquals(0xFF0B101DL, TwentyColorPieCatalog.contrastTextHex(0xFFFFFFFF))
        assertEquals(0xFFF8FAFFL, TwentyColorPieCatalog.contrastTextHex(0xFF000000))
    }

    @Test
    fun pipGrammarAndStrictTimeTravelMatchCardRule() {
        val nonLockedTarget = ClientTargetDescriptor("ULTIMATE_TCG_WIP", "Ultimate TCG (WIP)", upstreamSourceLock = false)
        val validPipExpr = "{W/U} {W·U} {R:T} {B:S} {G:M} {W|U} {R>G} {2U/R} {-W} {-(W,B)} {Ω·M}"
        val okRes = CardCodeValidator.validate(
            CardValidationRequest(
                cardId = "000000000000000000042",
                cardName = "Meridian Vanguard",
                targetClient = nonLockedTarget,
                contextMode = CardContextMode.MATCH_CARD,
                pipExpression = validPipExpr,
                temporalEffects = setOf("End Turn", "Extra Turn")
            )
        )
        assertTrue("Expected valid pip & temporal card, errors=${okRes.errors}", okRes.isValid)

        // Match card with Filters 19-21 MUST fail
        val badMatchCard = CardCodeValidator.validate(
            CardValidationRequest(
                cardId = "000000000000000000043",
                cardName = "Illegal Chrono Card",
                targetClient = nonLockedTarget,
                contextMode = CardContextMode.MATCH_CARD,
                requestedFilterNumbers = setOf(19)
            )
        )
        assertFalse("Time-travel Filter 19 must be blocked on match cards", badMatchCard.isValid)

        // Avatar Dossier with Filters 19-21 MUST pass
        val okAvatarDossier = CardCodeValidator.validate(
            CardValidationRequest(
                cardId = "000000000000000000044",
                cardName = "Chrono Envoy Avatar",
                targetClient = nonLockedTarget,
                contextMode = CardContextMode.AVATAR_DOSSIER,
                requestedFilterNumbers = setOf(19, 20, 21)
            )
        )
        assertTrue("Filters 19-21 must be allowed on Avatar Dossier, errors=${okAvatarDossier.errors}", okAvatarDossier.isValid)
    }

    @Test
    fun strictLockedClientFirewallAndCopyOnFork() {
        val catalog = RegistryAssetLoader.parseTcgCatalogJson(loadBundledCatalogFile())
        val edoLocked = catalog.first { it.id == "EDOPRO_BASE_MR5" }
        val forgeLocked = catalog.first { it.id == "FORGE_MTG_BASE" }
        assertTrue(edoLocked.upstreamSourceLock)
        assertTrue(forgeLocked.upstreamSourceLock)

        val comboRes = ComboSynthesizer.synthesizeCustomCombo(
            comboTitle = "Firewall Test Mix",
            selectedSources = listOf(edoLocked, forgeLocked),
            selectedModules = listOf("PXZ Pair/Solo/XP")
        )
        assertTrue(comboRes.isSuccess)
        val combo = comboRes.getOrThrow()
        assertFalse("Synthesized combo must be non-locked", combo.upstreamSourceLock)
        assertEquals(listOf("EDOPRO_BASE_MR5", "FORGE_MTG_BASE"), combo.derivedFromLockedIds)

        // Exporter must never place locked clients into nonLockedClients
        val exportedJson = JSONObject(CodeStringExporter.compileClientCodeStringIndexJson(listOf(combo)))
        val nonLockedArr = exportedJson.getJSONArray("nonLockedClients")
        for (i in 0 until nonLockedArr.length()) {
            val item = nonLockedArr.getJSONObject(i)
            assertFalse("Locked client leaked into nonLockedClients", item.getBoolean("upstreamSourceLock"))
        }
    }

    @Test
    fun unifiedV2AndLegacyBoardDescriptorRoundTripWithPipeSanitization() {
        val original = CardTemplate(
            id = "900000000000000000123",
            passcode = "900000000000000000123",
            name = "Apex|Dragon",
            baseVariantId = "ULTIMATE_TCG_WIP",
            cardType = "Apex Unit",
            cost = 4,
            attack = 4500,
            defense = 3800,
            abilityText = "Draw 1 | Extra Turn",
            artResId = 0
        )
        val v2Wire = original.toV2BoardDescriptor(pips = "{W/U}{Ω·A}", effectType = "DAMAGE")
        assertTrue(v2Wire.startsWith("@V2|"))
        val parsedV2 = CardTemplate.fromBoardDescriptor(v2Wire, "ULTIMATE_TCG_WIP")
        assertEquals("Apex/Dragon", parsedV2.name)
        assertEquals(4500, parsedV2.attack)
        assertEquals(3800, parsedV2.defense)
        assertTrue(parsedV2.abilityText.contains("Draw 1 / Extra Turn"))

        // Legacy 6-part descriptor compatibility
        val legacy = "Blue-Eyes White Dragon|8|3000|2500|Dragon/Normal|Legendary dragon of destruction."
        val parsedLegacy = CardTemplate.fromBoardDescriptor(legacy, "EDOPRO_BASE_MR5")
        assertEquals("Blue-Eyes White Dragon", parsedLegacy.name)
        assertEquals(3000, parsedLegacy.attack)
        assertEquals(2500, parsedLegacy.defense)
    }

    @Test
    fun startupAlignmentEvaluatorTriggersAndDeduplicatesByCommitSha() {
        // Out of sync because Card Maker consent pending -> popup MUST trigger
        val pendingReport = StartupAlignmentChecker.evaluateAlignmentStatus(
            AlignmentEvaluationInput(
                localSchemaVersion = "1.1.0",
                remoteCardMakerSchemaVersion = "1.1.0",
                cardMakerRepoReachable = true,
                masterHubRepoReachable = true,
                cardMakerLinksToEngine = true,
                masterHubLinksToEngine = true,
                cardMakerConsentSigned = false,
                gameEngineConsentSigned = true,
                cardMakerCommitSha = "abc1234567",
                masterHubCommitSha = "def9876543"
            )
        )
        assertEquals(AlignmentHealthState.LINKED_OUT_OF_SYNC, pendingReport.status)
        assertTrue(pendingReport.shouldShowPopup)

        // Dismissed for exact effectiveCommitKey -> popup MUST stay suppressed
        val dismissedReport = StartupAlignmentChecker.evaluateAlignmentStatus(
            AlignmentEvaluationInput(
                localSchemaVersion = "1.1.0",
                remoteCardMakerSchemaVersion = "1.1.0",
                cardMakerRepoReachable = true,
                masterHubRepoReachable = true,
                cardMakerLinksToEngine = true,
                masterHubLinksToEngine = true,
                cardMakerConsentSigned = false,
                gameEngineConsentSigned = true,
                cardMakerCommitSha = "abc1234567",
                masterHubCommitSha = "def9876543",
                dismissedCommitSha = pendingReport.effectiveCommitKey
            )
        )
        assertFalse(dismissedReport.shouldShowPopup)

        // Breaking major schema mismatch -> BREAKING_INCOMPATIBLE
        val breakingReport = StartupAlignmentChecker.evaluateAlignmentStatus(
            AlignmentEvaluationInput(
                localSchemaVersion = "1.1.0",
                remoteCardMakerSchemaVersion = "2.0.0",
                cardMakerRepoReachable = true,
                masterHubRepoReachable = true,
                cardMakerLinksToEngine = true,
                masterHubLinksToEngine = true,
                cardMakerConsentSigned = true,
                gameEngineConsentSigned = true,
                cardMakerCommitSha = "newsha9999"
            )
        )
        assertEquals(AlignmentHealthState.BREAKING_INCOMPATIBLE, breakingReport.status)
        assertTrue(breakingReport.shouldShowPopup)

        // Fully linked & dual-consented -> LINKED_AND_CURRENT, no popup
        val healthyReport = StartupAlignmentChecker.evaluateAlignmentStatus(
            AlignmentEvaluationInput(
                localSchemaVersion = "1.1.0",
                remoteCardMakerSchemaVersion = "1.1.0",
                cardMakerRepoReachable = true,
                masterHubRepoReachable = true,
                cardMakerLinksToEngine = true,
                masterHubLinksToEngine = true,
                cardMakerConsentSigned = true,
                gameEngineConsentSigned = true,
                cardMakerCommitSha = "oksha111",
                masterHubCommitSha = "oksha222"
            )
        )
        assertEquals(AlignmentHealthState.LINKED_AND_CURRENT, healthyReport.status)
        assertFalse(healthyReport.shouldShowPopup)
    }

    @Test
    fun splitOffSetPreservesAllOfficialCanonCardsAndEnforcesFirewall() {
        val warnings = mutableListOf<String>()
        val packs = RegistryAssetLoader.parseCardPacksJson(loadBundledCardPacksFile(), warnings)
        assertTrue("Expected zero card-pack parse warnings, got: $warnings", warnings.isEmpty())

        val splitOffPack = packs.firstOrNull { it.isSplitOffSet }
        assertTrue("Expected starter Fan-Fiction Split-Off Set in card-packs.json", splitOffPack != null)
        val meta = splitOffPack!!.splitOff!!
        assertTrue(meta.preserveAllOriginalOfficialCards)
        assertTrue(splitOffPack.cards.any { it.cardOrigin == CardOrigin.CANON_ORIGINAL })
        assertTrue(splitOffPack.cards.any { it.cardOrigin == CardOrigin.FAN_ALTERNATE_VARIANT && it.linkedCanonCardId.isNotBlank() })
        assertTrue(splitOffPack.cards.any { it.cardOrigin == CardOrigin.ORIGINAL_OC_CARD })

        // Reject Split-Off Set that attempts to drop official canon cards (preserveAllOriginalOfficialCards = false)
        val strippedCanonPackJson = """
            {
              "packId": "illegal_stripped_splitoff",
              "packName": "Stripped Canon Pack",
              "targetClientId": "EDOPRO_CUSTOM_FORK",
              "splitOffId": "split_bad_01",
              "splitOffTitle": "Bad Canon Stripping Set",
              "splitOffKind": "FAN_FICTION_AU",
              "parentOfficialSetId": "YGO_LOB_2002",
              "parentOfficialSetName": "Legend of Blue Eyes",
              "preserveAllOriginalOfficialCards": false,
              "cards": [
                {
                  "cardId": "000000000000000009999",
                  "name": "Solo OC Card",
                  "targetClientId": "EDOPRO_CUSTOM_FORK",
                  "cardOrigin": "ORIGINAL_OC_CARD",
                  "pipCost": "{W}",
                  "atk21": "2500",
                  "def21": "2000"
                }
              ]
            }
        """.trimIndent()
        val strippedReport = CodeStringExporter.validateAndParseCardPackJson(strippedCanonPackJson)
        assertTrue(
            "Split-Off Set with preserveAllOriginalOfficialCards=false must be rejected",
            strippedReport.acceptedCards.isEmpty() && strippedReport.rejectedReasons.isNotEmpty()
        )

        // Reject Split-Off Set targeting locked upstream GitHub client (EDOPRO_BASE_MR5)
        val lockedTargetPackJson = """
            {
              "packId": "illegal_locked_splitoff",
              "packName": "Locked Client Split-Off",
              "targetClientId": "EDOPRO_BASE_MR5",
              "splitOffId": "split_locked_01",
              "splitOffTitle": "Illegal Locked Split-Off",
              "splitOffKind": "FAN_FICTION_AU",
              "parentOfficialSetId": "YGO_MR5",
              "parentOfficialSetName": "Master Rule 5",
              "preserveAllOriginalOfficialCards": true,
              "cards": [
                {
                  "cardId": "000000000000000008963",
                  "name": "Blue-Eyes White Dragon",
                  "targetClientId": "EDOPRO_BASE_MR5",
                  "cardOrigin": "CANON_ORIGINAL",
                  "pipCost": "{W}",
                  "atk21": "3000",
                  "def21": "2500"
                }
              ]
            }
        """.trimIndent()
        val lockedReport = CodeStringExporter.validateAndParseCardPackJson(lockedTargetPackJson)
        assertTrue(
            "Locked upstream client target must be blocked",
            lockedReport.acceptedCards.isEmpty() && lockedReport.rejectedReasons.isNotEmpty()
        )

        // Reject Split-Off Set card carrying Time-Travel Filter 19 on a match card
        val timeTravelPackJson = """
            {
              "packId": "illegal_timetravel_splitoff",
              "packName": "Time Travel Split-Off",
              "targetClientId": "ULTIMATE_TCG_WIP",
              "splitOffId": "split_tt_01",
              "splitOffTitle": "Chrono Violation Set",
              "splitOffKind": "FAN_FICTION_AU",
              "parentOfficialSetId": "YGO_BC",
              "parentOfficialSetName": "Battle City",
              "preserveAllOriginalOfficialCards": true,
              "cards": [
                {
                  "cardId": "000000000000000000001",
                  "name": "Canon Anchor",
                  "targetClientId": "ULTIMATE_TCG_WIP",
                  "cardOrigin": "CANON_ORIGINAL",
                  "pipCost": "{W}",
                  "atk21": "2000",
                  "def21": "2000"
                },
                {
                  "cardId": "000000000000000000002",
                  "name": "Illegal Time-Travel OC",
                  "targetClientId": "ULTIMATE_TCG_WIP",
                  "cardOrigin": "ORIGINAL_OC_CARD",
                  "pipCost": "{U}",
                  "atk21": "2500",
                  "def21": "2100",
                  "requestedFilterNumbers": [19]
                }
              ]
            }
        """.trimIndent()
        val ttReport = CodeStringExporter.validateAndParseCardPackJson(timeTravelPackJson)
        assertEquals(1, ttReport.acceptedCards.size)
        assertTrue("Time-travel filter 19 must be rejected on match cards", ttReport.rejectedReasons.isNotEmpty())
    }

    @Test
    fun customCardUsageTelemetryStartsEmptyAndTracksRealCanonAuOcInteractions() {
        val tracker = CardUsageTelemetryTracker()
        val initial = tracker.snapshot.value
        assertEquals("1.0.0", initial.schemaVersion)
        assertEquals(0, initial.totalMatchesTracked)
        assertEquals(0, initial.cards.size)
        assertEquals(0, initial.interactions.size)
        assertFalse(initial.hasRealTelemetry)

        val canonCard = CardTemplate(
            id = "000000000000089631139",
            passcode = "89631139",
            name = "Blue-Eyes White Dragon",
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            cardType = "Monster",
            cost = 8,
            attack = 3000,
            defense = 2500,
            abilityText = "Legendary dragon.",
            artResId = 0,
            cardOrigin = CardOrigin.CANON_ORIGINAL,
            splitOffSetId = "split_off_battle_city_neon_meridian_01",
            parentOfficialSetId = "YGO_BATTLE_CITY_CANON"
        )
        val auVariant = CardTemplate(
            id = "000000000000900000101",
            passcode = "900000101",
            name = "Blue-Eyes Neon Meridian Dragon (AU)",
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            cardType = "Monster",
            cost = 8,
            attack = 3200,
            defense = 2600,
            abilityText = "AU alternate timeline variant.",
            artResId = 0,
            cardOrigin = CardOrigin.FAN_ALTERNATE_VARIANT,
            linkedCanonCardId = "000000000000089631139",
            splitOffSetId = "split_off_battle_city_neon_meridian_01",
            parentOfficialSetId = "YGO_BATTLE_CITY_CANON"
        )
        val ocCard = CardTemplate(
            id = "000000000000900000201",
            passcode = "900000201",
            name = "Kael, Meridian Duelist Envoy",
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            cardType = "Monster",
            cost = 4,
            attack = 2100,
            defense = 1800,
            abilityText = "Original OC Duelist.",
            artResId = 0,
            cardOrigin = CardOrigin.ORIGINAL_OC_CARD,
            splitOffSetId = "split_off_battle_city_neon_meridian_01",
            parentOfficialSetId = "YGO_BATTLE_CITY_CANON"
        )

        tracker.recordCardDrawn(canonCard, "match_alpha")
        tracker.recordCardPlayed(canonCard, turnNumber = 1, matchId = "match_alpha")
        tracker.recordCardPlayed(auVariant, turnNumber = 3, matchId = "match_alpha")
        tracker.recordCardInteraction(
            type = CardInteractionType.COMBO_SYNERGY,
            source = auVariant,
            target = canonCard,
            phase = "MP1",
            baseVariantId = "EDOPRO_CUSTOM_FORK",
            sampleLogLine = "AU Variant resonated with Canon Original."
        )
        tracker.recordCardAttacked(
            attacker = ocCard,
            damageDealt = 2100L,
            defender = canonCard,
            defenderDestroyed = true,
            phase = "BP"
        )
        tracker.recordMatchOutcome(hostWon = true, matchId = "match_alpha")

        val snap = tracker.snapshot.value
        assertTrue(snap.hasRealTelemetry)
        assertEquals(1, snap.totalMatchesTracked)
        assertEquals(3, snap.cards.size)
        assertTrue(snap.totalInteractionsRecorded >= 2)

        val json = JSONObject(snap.toJsonString())
        assertEquals("1.0.0", json.getString("schemaVersion"))
        assertEquals("CustomCardUsageReport", json.getString("reportType"))
        val breakdown = json.getJSONObject("originBreakdown")
        assertEquals(1, breakdown.getInt("canonOriginalCards"))
        assertEquals(1, breakdown.getInt("fanAlternateVariantCards"))
        assertEquals(1, breakdown.getInt("originalOcCards"))
        assertEquals(3, json.getJSONArray("cardMetrics").length())
        assertTrue(json.getJSONArray("interactionMetrics").length() >= 2)

        // Archive & reset returns cleanly to empty live counters
        tracker.archiveAndReset()
        val afterReset = tracker.snapshot.value
        assertFalse(afterReset.hasRealTelemetry)
        assertEquals(0, afterReset.cards.size)
        assertEquals(1, afterReset.archivedReportCount)
    }

    @Test
    fun handshakeContractIncludesTelemetryAndSplitOffCapabilities() {
        val contract = JSONObject(
            CodeStringExporter.buildHandshakeContractJson(
                gameEngineConsented = true,
                cardMakerConsented = true,
                recordedBy = "NexusTCG Engine v4.0",
                recordedAtUtc = "2026-11-23T00:00:00Z"
            )
        )
        val caps = contract.getJSONArray("requiredCapabilities")
        val capSet = (0 until caps.length()).map { caps.getString(it) }.toSet()
        assertTrue("CUSTOM_CARD_USAGE_TELEMETRY_V1" in capSet)
        assertTrue("SPLIT_OFF_CANON_PRESERVATION_V1" in capSet)
    }
}
