package com.example.data

import com.example.engine.CatalogBlueprint
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure Kotlin deterministic Code-String Exporter & MPV Card-Pack Validator
 * (ZERO Android/Compose imports).
 *
 * Compiles `client-codestring-index.json`, `nexus-repo-link.json`, `handshake-contract.json`,
 * and `.github/workflows/nexus-mpv-sync.yml` strictly for NON-LOCKED clients
 * (`upstreamSourceLock == false`), while isolating locked GitHub-sourced base games into
 * `lockedClientsReadOnly` with a Copy-on-Fork helper.
 */
data class ExportableClientSpec(
    val clientId: String,
    val displayName: String,
    val boardArchetype: String,
    val originType: String,
    val upstreamSourceLock: Boolean,
    val protocolHash: String,
    val schemaVersion: String = "1.1.0",
    val upstreamRepoUrl: String = "",
    val derivedFromLockedIds: List<String> = emptyList(),
    val suggestedNonLockedForkId: String? = null,
    val zones: List<Pair<String, Int>> = emptyList(),
    val piles: List<String> = emptyList(),
    val resourcePools: List<String> = emptyList(),
    val turnPhases: List<String> = emptyList(),
    val attributes: List<String> = emptyList()
)

data class ImportedMpvCard(
    val cardId: String,
    val name: String,
    val targetClientId: String,
    val cardType: String,
    val pipCost: String,
    val atk21: String,
    val def21: String,
    val effectType: String,
    val temporalEffect: String,
    val ability: String
)

data class CardPackImportReport(
    val packId: String,
    val packName: String,
    val acceptedCards: List<ImportedMpvCard>,
    val rejectedReasons: List<String>
) {
    val isFullyValid: Boolean get() = rejectedReasons.isEmpty() && acceptedCards.isNotEmpty()
}

object CodeStringExporter {
    const val BRIDGE_SCHEMA_VERSION = "1.1.0"
    const val GAME_ENGINE_REPO = "Summer-Warrior/Tcg-game-engine-test"
    const val CARD_MAKER_REPO = "Summer-Warrior/MPV-TCG-Card-Making-Software-Updated"
    const val MASTER_HUB_REPO = "Summer-Warrior/Project-MPV-RP-Master-Hub"
    const val GAME_ENGINE_PACKAGE = "com.aistudio.nexustcg.vxqrmk"
    const val CARD_MAKER_PACKAGE = "com.aistudio.tcgstudio.kqrmvx"

    val defaultExportCatalog: List<ExportableClientSpec> = listOf(
        ExportableClientSpec(
            clientId = "ULTIMATE_TCG_WIP",
            displayName = "Ultimate TCG (WIP Foundational System)",
            boardArchetype = "multi-lane-arena",
            originType = "FLAGSHIP_CUSTOM",
            upstreamSourceLock = false,
            protocolHash = "sha256:ultimate-tcg-wip-v1.1",
            zones = listOf("PXZ_FRONTLINE" to 5, "HIDDEN_STANDBY_ROW" to 3, "COMBINED_WORLD_ZONE" to 1, "SHIELD_ROW" to 6, "BACKLINE_3D_ANCHORS" to 15),
            piles = listOf("MAIN_DECK", "EXTRA_APEX_DECK", "GRAVEYARD", "EXILE", "RESOURCE_SUBDECKS_15"),
            resourcePools = listOf("MAGIC_PULSE", "FORCE_PULSE", "PXZ_XP_GAUGE_150"),
            turnPhases = listOf("Draw", "Resource/Setup", "Action/Battle", "End"),
            attributes = listOf("20_COLOR_ANGULAR_SPIN", "MATERIAL_TIER_1_TO_8", "FLAVOR_FILTER_INT")
        ),
        ExportableClientSpec(
            clientId = "EDOPRO_CUSTOM_FORK",
            displayName = "21-Digit EDOPro Custom Clone (Non-Locked Fork)",
            boardArchetype = "zone-rows",
            originType = "CUSTOM_CLONE_21",
            upstreamSourceLock = false,
            derivedFromLockedIds = listOf("EDOPRO_BASE_MR5"),
            protocolHash = "sha256:edopro-custom-21d-v1.1",
            zones = listOf("MAIN_MONSTER" to 5, "EXTRA_MONSTER" to 2, "SPELL_TRAP" to 5, "DEDICATED_PENDULUM" to 2, "FIELD_SPELL" to 1, "SKILL_CARD" to 1, "SOUL_ZONE" to 1),
            piles = listOf("MAIN_DECK", "EXTRA_DECK", "GRAVEYARD", "BANISHED"),
            resourcePools = listOf("LP_21_DIGIT", "SPEED_COUNTERS", "REGISTER_21"),
            turnPhases = listOf("Draw", "Standby", "Main 1", "Battle", "Main 2", "End"),
            attributes = listOf("LIGHT", "DARK", "FIRE", "WATER", "WIND", "EARTH", "DIVINE")
        ),
        ExportableClientSpec(
            clientId = "FORGE_CUSTOM_CLONE",
            displayName = "21-Digit Nested-Planar Forge Clone (Non-Locked Fork)",
            boardArchetype = "battlefield-stack",
            originType = "CUSTOM_CLONE_21",
            upstreamSourceLock = false,
            derivedFromLockedIds = listOf("FORGE_MTG_BASE"),
            protocolHash = "sha256:forge-custom-21d-v1.1",
            zones = listOf("BATTLEFIELD_PERMANENTS" to 30, "LIFO_STACK" to 20, "COMMAND_ZONE" to 2, "PLANAR_DECK_ZONE" to 1),
            piles = listOf("LIBRARY", "GRAVEYARD", "EXILE", "SIDEBOARD"),
            resourcePools = listOf("MANA_POOL_20_COLOR", "LIFE_TOTAL_21"),
            turnPhases = listOf("Beginning", "Precombat Main", "Combat", "Postcombat Main", "End"),
            attributes = listOf("20_COLOR_PIE_PIPS", "POWER_21", "TOUGHNESS_21")
        ),
        ExportableClientSpec(
            clientId = "HYBRID_FORGE_EDOPRO_MIX",
            displayName = "Hybrid Forge + EDOPro 21-Digit Mix (Non-Locked)",
            boardArchetype = "zone-rows",
            originType = "USER_HYBRID",
            upstreamSourceLock = false,
            derivedFromLockedIds = listOf("EDOPRO_BASE_MR5", "FORGE_MTG_BASE"),
            protocolHash = "sha256:hybrid-forge-edopro-v1.1",
            zones = listOf("MAIN_MONSTER" to 5, "SPELL_TRAP" to 5, "DEDICATED_PENDULUM" to 2, "STACK_CHAIN_BRIDGE" to 20),
            piles = listOf("MAIN_DECK", "EXTRA_DECK", "GRAVEYARD", "EXILE_BANISHED"),
            resourcePools = listOf("LP_21_DIGIT", "MANA_POOL_20_COLOR"),
            turnPhases = listOf("Draw", "Main 1", "Combat", "Main 2", "End"),
            attributes = listOf("20_COLOR_PIE_PIPS", "ATK_21", "DEF_21")
        ),
        ExportableClientSpec(
            clientId = "NEXUS_WEB_TABLETOP",
            displayName = "Built-In Web Tabletop Blueprint",
            boardArchetype = "multi-lane-arena",
            originType = "BUILT_IN_TABLETOP",
            upstreamSourceLock = false,
            protocolHash = "sha256:nexus-web-tabletop-v1.1",
            zones = listOf("HOST_BOARD" to 30, "OPPONENT_BOARD" to 30, "WORLD_FIELD" to 1),
            piles = listOf("MAIN_DECK", "DISCARD", "EXILE"),
            resourcePools = listOf("LIFE", "MANA_POOL"),
            turnPhases = listOf("Draw", "Main", "Combat", "End"),
            attributes = listOf("20_COLOR_PIE_PIPS", "COST", "ATK", "DEF")
        ),
        ExportableClientSpec(
            clientId = "NOTEBOOK_DUEL_MONSTERS",
            displayName = "Notebook Duel Monsters Formats",
            boardArchetype = "zone-rows",
            originType = "NOTEBOOK_REALM",
            upstreamSourceLock = false,
            protocolHash = "sha256:notebook-duel-monsters-v1.1",
            zones = listOf("MAIN_MONSTER" to 5, "EXTRA_MONSTER" to 2, "SPELL_TRAP" to 5, "DEDICATED_PENDULUM" to 2, "FIELD_SPELL" to 1, "SPEED_SPELL" to 1, "ACTION_SPELL" to 3, "SKILL_CARD" to 1, "SOUL_ZONE" to 1),
            piles = listOf("MAIN_DECK", "EXTRA_DECK", "GY", "BANISHED"),
            resourcePools = listOf("LP", "SPEED_COUNTERS", "ITEM_COUNTERS_20"),
            turnPhases = listOf("Draw", "Standby", "Main 1", "Battle", "Main 2", "End"),
            attributes = listOf("LIGHT", "DARK", "FIRE", "WATER", "WIND", "EARTH", "DIVINE")
        ),
        ExportableClientSpec(
            clientId = "NOTEBOOK_NESTED_PLANAR_MTG",
            displayName = "Notebook Nested-Planar MTG Realm",
            boardArchetype = "battlefield-stack",
            originType = "NOTEBOOK_REALM",
            upstreamSourceLock = false,
            protocolHash = "sha256:notebook-nested-planar-mtg-v1.1",
            zones = listOf("BATTLEFIELD" to 30, "STACK" to 20, "COMMAND_ZONE" to 2),
            piles = listOf("LIBRARY", "GRAVEYARD", "EXILE"),
            resourcePools = listOf("MANA_20_COLOR", "LIFE"),
            turnPhases = listOf("Beginning", "Precombat Main", "Combat", "Postcombat Main", "Ending"),
            attributes = listOf("20_COLOR_PIE_PIPS", "POWER", "TOUGHNESS")
        ),
        ExportableClientSpec(
            clientId = "NOTEBOOK_PLANET_CRAY_VANGUARD",
            displayName = "Notebook Planet Cray Vanguard Realm",
            boardArchetype = "formation-and-ranks",
            originType = "NOTEBOOK_REALM",
            upstreamSourceLock = false,
            protocolHash = "sha256:notebook-planet-cray-v1.1",
            zones = listOf("VANGUARD_CIRCLE" to 1, "REAR_GUARD_CIRCLES" to 5, "DAMAGE_ZONE" to 6),
            piles = listOf("MAIN_DECK", "SOUL_STACK", "DROP_ZONE", "BIND_ZONE"),
            resourcePools = listOf("COUNTER_BLAST", "SOUL_BLAST", "DRIVE_CHECKS"),
            turnPhases = listOf("Stand", "Draw", "Ride", "Main", "Battle", "End"),
            attributes = listOf("GRADE_0_TO_4", "POWER", "SHIELD", "CRITICAL")
        ),
        ExportableClientSpec(
            clientId = "TCG_VIDEO_GAME_BLUEPRINT",
            displayName = "TCG Video Games Clean-Room Blueprint",
            boardArchetype = "video-game-action-or-roguelike",
            originType = "VIDEO_GAME_BLUEPRINT",
            upstreamSourceLock = false,
            protocolHash = "sha256:tcg-video-game-v1.1",
            zones = listOf("PANEL_GRID_3X6" to 18, "SLEIGHT_QUEUE" to 3, "RELIC_BAR" to 12),
            piles = listOf("CHIP_FOLDER", "DRAW_PILE", "DISCARD_PILE"),
            resourcePools = listOf("CUSTOM_GAUGE", "ENERGY", "CHIPS_X_MULT"),
            turnPhases = listOf("Intent Telegraph", "Custom/Draw", "Action/Sleight", "End"),
            attributes = listOf("CHIP_CODE", "CARD_VALUE_0_9", "INTENT_DAMAGE")
        ),
        ExportableClientSpec(
            clientId = "MINI_GAMES_BLUEPRINT",
            displayName = "Video-Game Card Mini-Games Blueprint",
            boardArchetype = "directional-and-territory-grid",
            originType = "MINI_GAME_BLUEPRINT",
            upstreamSourceLock = false,
            protocolHash = "sha256:mini-games-v1.1",
            zones = listOf("DIRECTIONAL_GRID" to 16, "ELEMENTAL_DICE_TRAY" to 8),
            piles = listOf("MINI_DECK", "CAPTURED_PILE"),
            resourcePools = listOf("ELEMENTAL_DICE_8", "PAWNS", "CARAVAN_SCORE"),
            turnPhases = listOf("Roll/Setup", "Placement/Capture", "Resolution", "End"),
            attributes = listOf("NORTH_SOUTH_EAST_WEST_RANKS", "TERRITORY_TILES")
        ),
        ExportableClientSpec(
            clientId = "NICHE_DEFUNCT_BLUEPRINT",
            displayName = "Niche, Indie, LCG & Defunct Blueprint",
            boardArchetype = "tug-of-war-track",
            originType = "NICHE_DEFUNCT_BLUEPRINT",
            upstreamSourceLock = false,
            protocolHash = "sha256:niche-defunct-v1.1",
            zones = listOf("MEMORY_TRACK" to 21, "TRIANGLE_FORMATION" to 6, "SERVERS_LANES" to 5),
            piles = listOf("MAIN_DECK", "ARCHIVE_DISCARD"),
            resourcePools = listOf("MEMORY_GAUGE", "MUGIC", "CREDITS"),
            turnPhases = listOf("Start", "Action", "Combat", "End"),
            attributes = listOf("COST", "POWER", "ELEMENTS")
        ),
        ExportableClientSpec(
            clientId = "AI_USER_COMBO_SANDBOX",
            displayName = "AI & User Cross-TCG Combo Sandbox",
            boardArchetype = "multi-lane-arena",
            originType = "AI_COMBO",
            upstreamSourceLock = false,
            protocolHash = "sha256:ai-user-combo-sandbox-v1.1",
            zones = listOf("MERGED_HYBRID_BOARD" to 30, "COMBINED_STACK_CHAIN" to 20),
            piles = listOf("HYBRID_MAIN", "HYBRID_EXTRA", "GRAVEYARD", "EXILE"),
            resourcePools = listOf("HYBRID_LIFE_21", "HYBRID_MANA_20_COLOR", "MODULE_XP"),
            turnPhases = listOf("Draw", "Setup", "Action/Battle", "End"),
            attributes = listOf("20_COLOR_PIE_PIPS", "COMBO_MODULES", "ATK_21", "DEF_21")
        ),
        // Locked Upstream GitHub Clients (Read-Only Firewall)
        ExportableClientSpec("EDOPRO_BASE_MR5", "EDOPro (ProjectIgnis/EDOPro)", "zone-rows", "GITHUB_LOCKED_BASE", true, "sha256:edopro-mr5-40.1.4-locked", upstreamRepoUrl = "https://github.com/ProjectIgnis/EDOPro", suggestedNonLockedForkId = "EDOPRO_CUSTOM_FORK"),
        ExportableClientSpec("FORGE_MTG_BASE", "Forge MTG (Card-Forge/forge)", "battlefield-stack", "GITHUB_LOCKED_BASE", true, "sha256:forge-mtg-2.0.09-locked", upstreamRepoUrl = "https://github.com/Card-Forge/forge", suggestedNonLockedForkId = "FORGE_CUSTOM_CLONE"),
        ExportableClientSpec("COCKATRICE_LOCKED_BASE", "Cockatrice", "battlefield-stack", "GITHUB_LOCKED_BASE", true, "sha256:cockatrice-upstream-locked", upstreamRepoUrl = "https://github.com/Cockatrice/Cockatrice", suggestedNonLockedForkId = "FORGE_CUSTOM_CLONE"),
        ExportableClientSpec("XMAGE_LOCKED_BASE", "XMage", "battlefield-stack", "GITHUB_LOCKED_BASE", true, "sha256:xmage-upstream-locked", upstreamRepoUrl = "https://github.com/magefree/mage", suggestedNonLockedForkId = "FORGE_CUSTOM_CLONE"),
        ExportableClientSpec("TALISHAR_LOCKED_BASE", "Talishar", "deck-as-life-or-combat-chain", "GITHUB_LOCKED_BASE", true, "sha256:talishar-upstream-locked", upstreamRepoUrl = "https://github.com/Talishar/Talishar", suggestedNonLockedForkId = "NICHE_DEFUNCT_BLUEPRINT"),
        ExportableClientSpec("KARABAST_LOCKED_BASE", "Karabast", "multi-lane-arena", "GITHUB_LOCKED_BASE", true, "sha256:karabast-upstream-locked", upstreamRepoUrl = "https://github.com/SWU-Karabast/forceteki", suggestedNonLockedForkId = "NEXUS_WEB_TABLETOP"),
        ExportableClientSpec("JINTEKI_LOCKED_BASE", "Jinteki.net", "asymmetric-or-path", "GITHUB_LOCKED_BASE", true, "sha256:jinteki-upstream-locked", upstreamRepoUrl = "https://github.com/mtgred/netrunner", suggestedNonLockedForkId = "NICHE_DEFUNCT_BLUEPRINT")
    )

    /**
     * Strictly filters writable clients (`upstreamSourceLock == false`) and returns an error if
     * a caller attempts to export a write payload targeting a locked upstream client.
     */
    fun validateWritableExportTarget(targetClientId: String): Result<ExportableClientSpec> {
        val found = defaultExportCatalog.firstOrNull { it.clientId.equals(targetClientId.trim(), ignoreCase = true) }
        if (found != null && found.upstreamSourceLock) {
            return Result.failure(
                IllegalArgumentException(
                    "LOCKED_CLIENT_FIREWALL: Cannot export writable coding spec for locked GitHub client '${found.clientId}'. " +
                        "Fork to '${found.suggestedNonLockedForkId ?: "ULTIMATE_TCG_WIP"}' instead."
                )
            )
        }
        if (CardCodeValidator.isClientIdLocked(targetClientId)) {
            return Result.failure(
                IllegalArgumentException("LOCKED_CLIENT_FIREWALL: '$targetClientId' is locked to an upstream GitHub source.")
            )
        }
        val spec = found ?: ExportableClientSpec(
            clientId = targetClientId.trim(),
            displayName = targetClientId.trim(),
            boardArchetype = "multi-lane-arena",
            originType = "USER_HYBRID",
            upstreamSourceLock = false,
            protocolHash = "sha256:custom-${targetClientId.trim().lowercase()}"
        )
        return Result.success(spec)
    }

    /**
     * Copy-on-Fork helper: clones a locked GitHub base client into a writable 21-digit non-locked fork.
     */
    fun forkLockedClientToNonLockedClone(
        lockedSpec: ExportableClientSpec,
        customSuffix: String = "21D_FORK"
    ): ExportableClientSpec {
        val cleanSuffix = customSuffix.uppercase().replace(Regex("[^A-Z0-9_]"), "_").take(24).ifEmpty { "21D_FORK" }
        val newId = "${lockedSpec.clientId.removeSuffix("_BASE_MR5").removeSuffix("_BASE")}_$cleanSuffix"
        return ExportableClientSpec(
            clientId = newId,
            displayName = "${lockedSpec.displayName} — Non-Locked 21-Digit Fork",
            boardArchetype = lockedSpec.boardArchetype,
            originType = "CUSTOM_CLONE_21",
            upstreamSourceLock = false,
            derivedFromLockedIds = listOf(lockedSpec.clientId),
            protocolHash = "sha256:fork-${newId.lowercase()}-v1.1",
            zones = lockedSpec.zones.ifEmpty { listOf("HOST_BOARD" to 30, "OPPONENT_BOARD" to 30) },
            piles = lockedSpec.piles.ifEmpty { listOf("MAIN_DECK", "EXTRA_DECK", "GRAVEYARD", "EXILE") },
            resourcePools = lockedSpec.resourcePools.ifEmpty { listOf("LP_21_DIGIT", "MANA_POOL_20_COLOR") },
            turnPhases = lockedSpec.turnPhases.ifEmpty { listOf("Draw", "Main", "Combat", "End") },
            attributes = lockedSpec.attributes.ifEmpty { listOf("20_COLOR_PIE_PIPS", "ATK_21", "DEF_21") }
        )
    }

    /**
     * Deterministically compiles `client-codestring-index.json` including any additional non-locked
     * AI/User Combos while strictly excluding locked upstream GitHub clients from `nonLockedClients`.
     */
    fun compileClientCodeStringIndexJson(
        additionalNonLockedCombos: List<CatalogBlueprint> = emptyList()
    ): String {
        val nonLockedArray = JSONArray()
        val lockedArray = JSONArray()

        val mergedSpecs = defaultExportCatalog.toMutableList()
        additionalNonLockedCombos
            .filter { !it.upstreamSourceLock }
            .forEach { combo ->
                if (mergedSpecs.none { it.clientId.equals(combo.id, ignoreCase = true) }) {
                    mergedSpecs.add(
                        ExportableClientSpec(
                            clientId = combo.id,
                            displayName = combo.name,
                            boardArchetype = combo.boardArchetype,
                            originType = combo.originType,
                            upstreamSourceLock = false,
                            derivedFromLockedIds = combo.derivedFromLockedIds,
                            protocolHash = combo.protocolHash,
                            zones = listOf("MERGED_HYBRID_BOARD" to 30, "COMBINED_STACK_CHAIN" to 20),
                            piles = listOf("HYBRID_MAIN", "HYBRID_EXTRA", "GRAVEYARD", "EXILE"),
                            resourcePools = listOf("HYBRID_LIFE_21", "HYBRID_MANA_20_COLOR"),
                            turnPhases = listOf("Draw", "Setup", "Action/Battle", "End"),
                            attributes = listOf("20_COLOR_PIE_PIPS", "COMBO_MODULES")
                        )
                    )
                }
            }

        mergedSpecs.forEach { spec ->
            if (spec.upstreamSourceLock) {
                lockedArray.put(
                    JSONObject().apply {
                        put("clientId", spec.clientId)
                        put("displayName", spec.displayName)
                        put("upstreamSourceLock", true)
                        put("requiresStrictBase", true)
                        put("upstreamRepoUrl", spec.upstreamRepoUrl)
                        put("protocolHash", spec.protocolHash)
                        put("suggestedNonLockedForkId", spec.suggestedNonLockedForkId ?: "ULTIMATE_TCG_WIP")
                    }
                )
            } else {
                val zonesArr = JSONArray()
                spec.zones.forEach { (zid, cap) ->
                    zonesArr.put(JSONObject().apply {
                        put("id", zid)
                        put("capacity", cap)
                    })
                }
                nonLockedArray.put(
                    JSONObject().apply {
                        put("clientId", spec.clientId)
                        put("displayName", spec.displayName)
                        put("boardArchetype", spec.boardArchetype)
                        put("originType", spec.originType)
                        put("upstreamSourceLock", false)
                        if (spec.derivedFromLockedIds.isNotEmpty()) {
                            put("derivedFromLockedIds", JSONArray(spec.derivedFromLockedIds))
                        }
                        put("protocolHash", spec.protocolHash)
                        put("schemaVersion", spec.schemaVersion)
                        put("allowTimeTravelFlagsOnMatchCards", false)
                        put(
                            "codeStringRules",
                            JSONObject().apply {
                                put("allowedIdFormats", JSONArray(listOf("^[0-9]{1,8}$", "^[0-9]{1,21}$", "UUID")))
                                put("scriptTemplate", if (spec.boardArchetype == "zone-rows") "c<code_string>.lua" else "c<code_string>.json")
                                put("maxDigitPrecision", 21)
                                put("customCodeVarExpr21Regex", "^(?!.*[0-9]{22}).{1,240}$")
                            }
                        )
                        put("zones", zonesArr)
                        put("piles", JSONArray(spec.piles))
                        put("resourcePools", JSONArray(spec.resourcePools))
                        put("turnPhases", JSONArray(spec.turnPhases))
                        put("attributes", JSONArray(spec.attributes))
                    }
                )
            }
        }

        val omniArray = JSONArray()
        TwentyColorPieCatalog.omniGeometryVariants.forEach { v ->
            omniArray.put(JSONObject().apply {
                put("code", v.code)
                put("geometry", v.geometryName)
            })
        }

        val root = JSONObject().apply {
            put("bridgeSchemaVersion", BRIDGE_SCHEMA_VERSION)
            put("sourceRepo", GAME_ENGINE_REPO)
            put("linkedCardMakerRepo", CARD_MAKER_REPO)
            put("linkedMasterHubRepo", MASTER_HUB_REPO)
            put("upstreamSourceLock", false)
            put("allowTimeTravelFlagsOnMatchCards", false)
            put("allowedInMatchTemporalEffects", JSONArray(listOf("End Turn", "Extra Turn")))
            put("dossierOnlyFilters", JSONArray(listOf(19, 20, 21)))
            put(
                "firestoreBounds",
                JSONObject().apply {
                    put("customCardNameMaxLength", 120)
                    put("customCardAbilityMaxLength", 2000)
                    put("deckCardIdsMaxCount", 100)
                    put("hostBoardCardsMaxCount", 30)
                    put("opponentBoardCardsMaxCount", 30)
                }
            )
            put(
                "boardDescriptorFormats",
                JSONArray(
                    listOf(
                        "name|cost|atk|def|type|ability",
                        "@V2|baseVariantId|code|name|type|pips|atk/def|effectType|ability"
                    )
                )
            )
            put(
                "twentyColorPieGrammar",
                JSONObject().apply {
                    put("defaultSelectedCount", 0)
                    put("colorlessPip", TwentyColorPieCatalog.COLORLESS_PIP)
                    put("wheelOrder", JSONArray(TwentyColorPieCatalog.wheelColors.map { it.code }))
                    put("anchorIndices", JSONArray(listOf(0, 4, 8, 12, 16)))
                    put("layer2PairCount", TwentyColorPieCatalog.all190FusedPairs.size)
                    put("layer2AcceptedSeparators", JSONArray(listOf("/", "·")))
                    put("valueStateSuffixes", JSONArray(listOf(":T", ":S", ":M")))
                    put("choiceSplitSeparator", "|")
                    put("weightedLeadSyntax", JSONArray(listOf("{A>B}", "{2A/B}")))
                    put("layer18Syntax", "{-(A,B)}")
                    put("layer19Syntax", "{-X}")
                    put("layer20OmniPip", TwentyColorPieCatalog.OMNICHROMA_PIP)
                    put("layer20OmniVariants", omniArray)
                }
            )
            put("nonLockedClients", nonLockedArray)
            put("lockedClientsReadOnly", lockedArray)
        }
        return root.toString(2)
    }

    fun buildRepoLinkManifestJson(
        cardMakerBranch: String = "main",
        importPaths: List<String> = listOf("nexus-bridge/card-packs/")
    ): String {
        return JSONObject().apply {
            put("thisRepo", GAME_ENGINE_REPO)
            put("thisBranch", "main")
            put("linkedHubRepo", MASTER_HUB_REPO)
            put("linkedHubBranch", "main")
            put("linkedCardMakerRepo", CARD_MAKER_REPO)
            put("linkedCardMakerBranch", cardMakerBranch.trim().ifEmpty { "main" })
            put("cardMakerAndroidPackage", CARD_MAKER_PACKAGE)
            put("cardMakerDeepLinkScheme", "tcgstudio://nexus-hub")
            put("gameEngineAndroidPackage", GAME_ENGINE_PACKAGE)
            put("gameEngineDeepLinkScheme", "nexus-tcg-engine://open")
            put("bridgeSchemaVersion", BRIDGE_SCHEMA_VERSION)
            put("syncTargetPath", "client-codestring-index.json")
            put("hubIndexPath", "client-codestring-index.json")
            put("mirrorIndexPath", "nexus-bridge/client-codestring-index.json")
            put("cardPackImportPaths", JSONArray(importPaths.ifEmpty { listOf("nexus-bridge/card-packs/") }))
            put("handshakeContractPath", "handshake-contract.json")
            put("linkVerificationMode", "MUTUAL_MANIFEST_REQUIRED")
        }.toString(2)
    }

    fun buildHandshakeContractJson(
        gameEngineConsented: Boolean,
        cardMakerConsented: Boolean,
        recordedBy: String,
        recordedAtUtc: String
    ): String {
        return JSONObject().apply {
            put("contractVersion", BRIDGE_SCHEMA_VERSION)
            put("cardMakerRepo", CARD_MAKER_REPO)
            put("gameEngineRepo", GAME_ENGINE_REPO)
            put("masterHubRepo", MASTER_HUB_REPO)
            put("dualConsentRule", "NO_SCHEMA_SYNC_OR_EXPORT_MIGRATION_WITHOUT_BOTH_CONSENTED")
            put(
                "cardMakerConsent",
                JSONObject().apply {
                    put("repo", CARD_MAKER_REPO)
                    put("packageName", CARD_MAKER_PACKAGE)
                    put("consentedSchemaVersion", BRIDGE_SCHEMA_VERSION)
                    put("consented", cardMakerConsented)
                    put("recordedBy", if (cardMakerConsented) "MPV Card Maker" else "")
                    put("recordedAtUtc", if (cardMakerConsented) recordedAtUtc else "")
                }
            )
            put(
                "gameEngineConsent",
                JSONObject().apply {
                    put("repo", GAME_ENGINE_REPO)
                    put("packageName", GAME_ENGINE_PACKAGE)
                    put("consentedSchemaVersion", BRIDGE_SCHEMA_VERSION)
                    put("consented", gameEngineConsented)
                    put("recordedBy", recordedBy)
                    put("recordedAtUtc", recordedAtUtc)
                }
            )
        }.toString(2)
    }

    fun buildGitHubActionsSyncWorkflowYaml(): String {
        return """
            name: NexusTCG <-> MPV Card Maker Bidirectional Bridge Sync

            on:
              push:
                branches: [main]
                paths:
                  - 'client-codestring-index.json'
                  - 'nexus-bridge/client-codestring-index.json'
                  - 'nexus-repo-link.json'
                  - 'handshake-contract.json'
                  - 'nexus-app.json'
              repository_dispatch:
                types: [mpv-card-pack-updated, nexus-handshake-ping]
              workflow_dispatch:

            permissions:
              contents: write

            jobs:
              sync-bridge-manifests:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout NexusTCG Engine
                    uses: actions/checkout@v7

                  - name: Mirror client-codestring-index.json to nexus-bridge/
                    run: |
                      mkdir -p nexus-bridge
                      cp client-codestring-index.json nexus-bridge/client-codestring-index.json

                  - name: Dispatch Update Notification to MPV Card Maker
                    if: ${'$'}{{ secrets.NEXUS_BRIDGE_PAT != '' }}
                    env:
                      GH_PAT: ${'$'}{{ secrets.NEXUS_BRIDGE_PAT }}
                    run: |
                      curl -fsSL -X POST \
                        -H "Accept: application/vnd.github+json" \
                        -H "X-GitHub-Api-Version: 2026-03-10" \
                        -H "Authorization: Bearer ${'$'}GH_PAT" \
                        https://api.github.com/repos/Summer-Warrior/MPV-TCG-Card-Making-Software-Updated/dispatches \
                        -d '{"event_type":"nexus-codestring-index-updated","client_payload":{"source":"Summer-Warrior/Tcg-game-engine-test","schemaVersion":"1.1.0"}}'
        """.trimIndent()
    }

    /**
     * Validates a Card Pack JSON payload pulled from `MPV-TCG-Card-Making-Software-Updated` or local storage.
     * Strictly rejects locked-client targets, 22-digit overflows, malformed pip syntax, or any match card
     * carrying time-travel flags (Filters 19–21).
     */
    fun validateAndParseCardPackJson(rawJson: String): CardPackImportReport {
        val rejected = mutableListOf<String>()
        val accepted = mutableListOf<ImportedMpvCard>()
        val root = runCatching { JSONObject(rawJson) }.getOrElse { err ->
            return CardPackImportReport("invalid", "Malformed JSON", emptyList(), listOf("JSON parse error: ${err.message}"))
        }

        val packId = root.optString("packId", "mpv_pack_${System.currentTimeMillis()}").trim()
        val packName = root.optString("packName", "MPV Card Pack").trim()
        val packTargetId = root.optString("targetClientId", "ULTIMATE_TCG_WIP").trim()
        val packUpstreamLock = root.optBoolean("upstreamSourceLock", false)

        if (packUpstreamLock || CardCodeValidator.isClientIdLocked(packTargetId)) {
            rejected.add("LOCKED_CLIENT_FIREWALL: Pack '$packName' targets locked client '$packTargetId'.")
            return CardPackImportReport(packId, packName, emptyList(), rejected)
        }

        val cardsArray = root.optJSONArray("cards") ?: return CardPackImportReport(
            packId, packName, emptyList(), listOf("Missing 'cards' array in card pack JSON.")
        )

        val targetDescriptor = CardCodeValidator.canonicalTargetCatalog.firstOrNull {
            it.clientId.equals(packTargetId, ignoreCase = true)
        } ?: ClientTargetDescriptor(packTargetId, packTargetId, upstreamSourceLock = false)

        for (i in 0 until cardsArray.length()) {
            val item = cardsArray.optJSONObject(i)
            if (item == null) {
                rejected.add("Card[$i]: Invalid JSON object.")
                continue
            }
            val cardId = item.optString("cardId", item.optString("id", "")).trim()
            val name = item.optString("name", "").trim()
            val cardTargetId = item.optString("targetClientId", packTargetId).trim()
            val cardType = item.optString("cardType", "Custom Unit").trim()
            val pipCost = item.optString("pipCost", TwentyColorPieCatalog.COLORLESS_PIP).trim()
            val atk21 = item.optString("atk21", item.optString("atk", "0")).trim()
            val def21 = item.optString("def21", item.optString("def", "0")).trim()
            val effectType = item.optString("effectType", "RESOURCE_BOOST").trim()
            val temporalEffect = item.optString("temporalEffect", "").trim()
            val ability = item.optString("ability", "").trim()

            val filters = mutableSetOf<Int>()
            val filtersArr = item.optJSONArray("filterNumbers") ?: item.optJSONArray("flavorFilters")
            if (filtersArr != null) {
                for (fIdx in 0 until filtersArr.length()) {
                    val fVal = filtersArr.optInt(fIdx, -1)
                    if (fVal > 0) filters.add(fVal)
                }
            }
            if (item.optBoolean("hasTimeTravelFlag", false)) {
                filters.add(19)
            }

            val cardTargetDesc = if (cardTargetId.equals(packTargetId, ignoreCase = true)) {
                targetDescriptor
            } else {
                CardCodeValidator.canonicalTargetCatalog.firstOrNull {
                    it.clientId.equals(cardTargetId, ignoreCase = true)
                } ?: ClientTargetDescriptor(cardTargetId, cardTargetId, upstreamSourceLock = CardCodeValidator.isClientIdLocked(cardTargetId))
            }

            val validation = CardCodeValidator.validate(
                CardValidationRequest(
                    cardId = cardId,
                    cardName = name,
                    targetClient = cardTargetDesc,
                    contextMode = CardContextMode.MATCH_CARD,
                    pipExpression = pipCost,
                    codingVariableExpression = item.optString("customCodeVarExpr21", ""),
                    requestedFilterNumbers = filters,
                    temporalEffects = if (temporalEffect.isNotEmpty()) setOf(temporalEffect) else emptySet(),
                    atkRegisterValue = atk21,
                    defRegisterValue = def21
                )
            )
            if (ability.length > 2000) {
                rejected.add("Card '$name': ability exceeds 2000-char Firestore bound.")
                continue
            }
            if (!validation.isValid) {
                rejected.add("Card '${name.ifEmpty { cardId }}': ${validation.errors.joinToString("; ")}")
                continue
            }
            accepted.add(
                ImportedMpvCard(
                    cardId = cardId,
                    name = name,
                    targetClientId = cardTargetId,
                    cardType = cardType,
                    pipCost = pipCost,
                    atk21 = atk21,
                    def21 = def21,
                    effectType = effectType,
                    temporalEffect = temporalEffect,
                    ability = ability
                )
            )
        }

        return CardPackImportReport(packId, packName, accepted, rejected)
    }
}
