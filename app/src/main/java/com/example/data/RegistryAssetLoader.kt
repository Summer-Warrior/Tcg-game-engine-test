package com.example.data

import android.content.Context
import com.example.engine.CatalogBlueprint
import org.json.JSONArray
import org.json.JSONObject

data class CompanionAppEntry(
    val id: String,
    val name: String,
    val category: String,
    val repo: String,
    val androidPackageName: String,
    val deepLinkScheme: String,
    val webUrl: String,
    val description: String,
    val isArcadeGame: Boolean
)

data class FederatedCardPack(
    val packId: String,
    val packName: String,
    val targetClientId: String,
    val upstreamSourceLock: Boolean,
    val cards: List<ImportedMpvCard>
)

data class WikiEntry(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val markdownBody: String
)

data class BotDirectoryEntry(
    val id: String,
    val name: String,
    val role: String,
    val clientId: String,
    val authMode: String,
    val description: String,
    val inviteUrl: String
)

data class RepoBridgeManifest(
    val thisRepo: String,
    val thisBranch: String,
    val linkedHubRepo: String,
    val linkedHubBranch: String,
    val linkedCardMakerRepo: String,
    val linkedCardMakerBranch: String,
    val cardMakerAndroidPackage: String,
    val cardMakerDeepLinkScheme: String,
    val gameEngineAndroidPackage: String,
    val gameEngineDeepLinkScheme: String,
    val bridgeSchemaVersion: String,
    val hubIndexPath: String,
    val mirrorIndexPath: String,
    val handshakeContractPath: String,
    val cardPackImportPaths: List<String>
)

data class LoadedRegistryBundle(
    val tcgCatalog: List<CatalogBlueprint>,
    val companionApps: List<CompanionAppEntry>,
    val federatedCardPacks: List<FederatedCardPack>,
    val wikis: List<WikiEntry>,
    val bots: List<BotDirectoryEntry>,
    val repoBridge: RepoBridgeManifest,
    val loadWarnings: List<String>
)

/**
 * Loads and runtime-validates the 6 modular JSON registries from `app/src/main/assets/registry/`:
 * - `tcg-catalog.json`
 * - `apps.json`
 * - `card-packs.json`
 * - `wikis.json`
 * - `bots.json`
 * - `repo-bridge.json`
 *
 * Pure JSON parser functions are exposed separately so JVM unit tests can validate
 * asset JSON files directly without requiring an Android Context.
 */
object RegistryAssetLoader {
    private const val REGISTRY_DIR = "registry"

    @Volatile
    private var cachedBundle: LoadedRegistryBundle? = null

    fun loadAll(context: Context, forceReload: Boolean = false): LoadedRegistryBundle {
        val existing = cachedBundle
        if (!forceReload && existing != null) return existing

        val warnings = mutableListOf<String>()

        val catalogRaw = readAssetSafely(context, "$REGISTRY_DIR/tcg-catalog.json", warnings)
        val appsRaw = readAssetSafely(context, "$REGISTRY_DIR/apps.json", warnings)
        val packsRaw = readAssetSafely(context, "$REGISTRY_DIR/card-packs.json", warnings)
        val wikisRaw = readAssetSafely(context, "$REGISTRY_DIR/wikis.json", warnings)
        val botsRaw = readAssetSafely(context, "$REGISTRY_DIR/bots.json", warnings)
        val bridgeRaw = readAssetSafely(context, "$REGISTRY_DIR/repo-bridge.json", warnings)

        val catalog = if (catalogRaw != null) parseTcgCatalogJson(catalogRaw, warnings) else emptyList()
        val apps = if (appsRaw != null) parseAppsJson(appsRaw, warnings) else emptyList()
        val packs = if (packsRaw != null) parseCardPacksJson(packsRaw, warnings) else emptyList()
        val wikis = if (wikisRaw != null) parseWikisJson(wikisRaw, warnings) else emptyList()
        val bots = if (botsRaw != null) parseBotsJson(botsRaw, warnings) else emptyList()
        val bridge = if (bridgeRaw != null) parseRepoBridgeJson(bridgeRaw, warnings) else defaultRepoBridgeManifest()

        return LoadedRegistryBundle(
            tcgCatalog = catalog,
            companionApps = apps,
            federatedCardPacks = packs,
            wikis = wikis,
            bots = bots,
            repoBridge = bridge,
            loadWarnings = warnings.toList()
        ).also { cachedBundle = it }
    }

    private fun readAssetSafely(
        context: Context,
        assetPath: String,
        warnings: MutableList<String>
    ): String? {
        return runCatching {
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.onFailure { err ->
            warnings.add("Asset read failed for $assetPath: ${err.message ?: err.javaClass.simpleName}")
        }.getOrNull()
    }

    fun parseTcgCatalogJson(
        rawJson: String,
        warnings: MutableList<String> = mutableListOf()
    ): List<CatalogBlueprint> {
        val root = runCatching { JSONObject(rawJson) }.getOrElse { err ->
            warnings.add("Invalid tcg-catalog.json: ${err.message}")
            return emptyList()
        }
        val arr = root.optJSONArray("games") ?: root.optJSONArray("catalog") ?: JSONArray()
        val results = mutableListOf<CatalogBlueprint>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) {
                warnings.add("tcg-catalog[$i] skipped: missing id or name.")
                continue
            }
            val category = obj.optString("category", "ACTIVE_PHYSICAL").trim()
            val originType = obj.optString("originType", "CLEAN_ROOM_BLUEPRINT").trim()
            val boardArchetype = obj.optString("boardArchetype", "zone-rows").trim()
            val upstreamSourceLock = obj.optBoolean("upstreamSourceLock", false)
            val requiresStrictBase = obj.optBoolean("requiresStrictBase", upstreamSourceLock)
            val isBaseStandard = obj.optBoolean("isBaseStandard", upstreamSourceLock)
            val upstreamRepoUrl = obj.optString("upstreamRepoUrl", "").trim()
            val protocolHash = obj.optString("protocolHash", "sha256:${id.lowercase()}-v1.1").trim()
            val clientTier = obj.optInt("clientTier", if (upstreamSourceLock) 2 else 4).coerceIn(1, 4)
            val platforms = jsonArrayToStringList(obj.optJSONArray("platforms")).ifEmpty { listOf("Android", "Web") }
            val summary = obj.optString("summary", "").trim()
            val wipStatus = obj.optString("wipStatus", "").trim()
            val suggestedForkId = obj.optString("suggestedForkId", "").trim()
            val derivedFrom = jsonArrayToStringList(obj.optJSONArray("derivedFromLockedIds"))
            val modules = jsonArrayToStringList(obj.optJSONArray("mechanicModules"))

            results.add(
                CatalogBlueprint(
                    id = id,
                    name = name,
                    category = category,
                    originType = originType,
                    boardArchetype = boardArchetype,
                    upstreamSourceLock = upstreamSourceLock,
                    requiresStrictBase = requiresStrictBase,
                    isBaseStandard = isBaseStandard,
                    upstreamRepoUrl = upstreamRepoUrl,
                    protocolHash = protocolHash,
                    clientTier = clientTier,
                    platforms = platforms,
                    summary = summary,
                    wipStatus = wipStatus,
                    suggestedForkId = suggestedForkId,
                    derivedFromLockedIds = derivedFrom,
                    mechanicModules = modules
                )
            )
        }
        return results
    }

    fun parseAppsJson(
        rawJson: String,
        warnings: MutableList<String> = mutableListOf()
    ): List<CompanionAppEntry> {
        val root = runCatching { JSONObject(rawJson) }.getOrElse { err ->
            warnings.add("Invalid apps.json: ${err.message}")
            return emptyList()
        }
        val list = mutableListOf<CompanionAppEntry>()
        val companionArr = root.optJSONArray("companionApps") ?: JSONArray()
        for (i in 0 until companionArr.length()) {
            val o = companionArr.optJSONObject(i) ?: continue
            list.add(
                CompanionAppEntry(
                    id = o.optString("id", "app_$i"),
                    name = o.optString("name", "Companion App"),
                    category = o.optString("category", "COMPANION"),
                    repo = o.optString("repo", ""),
                    androidPackageName = o.optString("androidPackageName", ""),
                    deepLinkScheme = o.optString("deepLinkScheme", ""),
                    webUrl = o.optString("webUrl", ""),
                    description = o.optString("description", ""),
                    isArcadeGame = false
                )
            )
        }
        val arcadeArr = root.optJSONArray("pegasusGamesArcade") ?: JSONArray()
        for (i in 0 until arcadeArr.length()) {
            val o = arcadeArr.optJSONObject(i) ?: continue
            list.add(
                CompanionAppEntry(
                    id = o.optString("id", "arcade_$i"),
                    name = o.optString("name", "Pegasus Arcade Game"),
                    category = o.optString("category", "PEGASUS_ARCADE"),
                    repo = o.optString("repo", ""),
                    androidPackageName = o.optString("androidPackageName", ""),
                    deepLinkScheme = o.optString("deepLinkScheme", ""),
                    webUrl = o.optString("webUrl", ""),
                    description = o.optString("description", ""),
                    isArcadeGame = true
                )
            )
        }
        return list
    }

    fun parseCardPacksJson(
        rawJson: String,
        warnings: MutableList<String> = mutableListOf()
    ): List<FederatedCardPack> {
        val root = runCatching { JSONObject(rawJson) }.getOrElse { err ->
            warnings.add("Invalid card-packs.json: ${err.message}")
            return emptyList()
        }
        val arr = root.optJSONArray("packs") ?: JSONArray()
        val packs = mutableListOf<FederatedCardPack>()
        for (i in 0 until arr.length()) {
            val packObj = arr.optJSONObject(i) ?: continue
            val report = CodeStringExporter.validateAndParseCardPackJson(packObj.toString())
            if (report.rejectedReasons.isNotEmpty()) {
                warnings.addAll(report.rejectedReasons.map { "card-packs[${report.packId}]: $it" })
            }
            if (report.acceptedCards.isNotEmpty()) {
                packs.add(
                    FederatedCardPack(
                        packId = report.packId,
                        packName = report.packName,
                        targetClientId = packObj.optString("targetClientId", "ULTIMATE_TCG_WIP"),
                        upstreamSourceLock = false,
                        cards = report.acceptedCards
                    )
                )
            }
        }
        return packs
    }

    fun parseWikisJson(
        rawJson: String,
        warnings: MutableList<String> = mutableListOf()
    ): List<WikiEntry> {
        val root = runCatching { JSONObject(rawJson) }.getOrElse { err ->
            warnings.add("Invalid wikis.json: ${err.message}")
            return emptyList()
        }
        val arr = root.optJSONArray("entries") ?: root.optJSONArray("wikis") ?: JSONArray()
        val entries = mutableListOf<WikiEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            entries.add(
                WikiEntry(
                    id = o.optString("id", "wiki_$i"),
                    title = o.optString("title", "Cosmology Entry"),
                    category = o.optString("category", "LORE"),
                    summary = o.optString("summary", ""),
                    markdownBody = o.optString("markdown", o.optString("body", ""))
                )
            )
        }
        return entries
    }

    fun parseBotsJson(
        rawJson: String,
        warnings: MutableList<String> = mutableListOf()
    ): List<BotDirectoryEntry> {
        val root = runCatching { JSONObject(rawJson) }.getOrElse { err ->
            warnings.add("Invalid bots.json: ${err.message}")
            return emptyList()
        }
        val arr = root.optJSONArray("bots") ?: JSONArray()
        val list = mutableListOf<BotDirectoryEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val clientId = o.optString("clientId", "1089000000000000000").filter { it.isDigit() }
            val invite = o.optString("oauth2InviteUrl", "").ifBlank {
                DiscordApiService.buildBotInviteUrl(clientId)
            }
            list.add(
                BotDirectoryEntry(
                    id = o.optString("id", "bot_$i"),
                    name = o.optString("name", "Discord Companion Bot"),
                    role = o.optString("role", "RP & Match Bridge"),
                    clientId = clientId,
                    authMode = o.optString("authMode", "OAUTH2_SERVER_INVITE_ONLY"),
                    description = o.optString("description", ""),
                    inviteUrl = invite
                )
            )
        }
        return list
    }

    fun parseRepoBridgeJson(
        rawJson: String,
        warnings: MutableList<String> = mutableListOf()
    ): RepoBridgeManifest {
        val obj = runCatching { JSONObject(rawJson) }.getOrElse { err ->
            warnings.add("Invalid repo-bridge.json: ${err.message}")
            return defaultRepoBridgeManifest()
        }
        return RepoBridgeManifest(
            thisRepo = obj.optString("thisRepo", NexusHubBridgeService.THIS_REPO),
            thisBranch = obj.optString("thisBranch", "main"),
            linkedHubRepo = obj.optString("linkedHubRepo", NexusHubBridgeService.LINKED_MASTER_HUB),
            linkedHubBranch = obj.optString("linkedHubBranch", "main"),
            linkedCardMakerRepo = obj.optString("linkedCardMakerRepo", NexusHubBridgeService.LINKED_CARD_MAKER),
            linkedCardMakerBranch = obj.optString("linkedCardMakerBranch", "main"),
            cardMakerAndroidPackage = obj.optString("cardMakerAndroidPackage", NexusHubBridgeService.CARD_MAKER_ANDROID_PACKAGE),
            cardMakerDeepLinkScheme = obj.optString("cardMakerDeepLinkScheme", NexusHubBridgeService.CARD_MAKER_DEEP_LINK_BASE),
            gameEngineAndroidPackage = obj.optString("gameEngineAndroidPackage", NexusHubBridgeService.ANDROID_PACKAGE_NAME),
            gameEngineDeepLinkScheme = obj.optString("gameEngineDeepLinkScheme", NexusHubBridgeService.DEEP_LINK_SCHEME),
            bridgeSchemaVersion = obj.optString("bridgeSchemaVersion", NexusHubBridgeService.BRIDGE_SCHEMA_VERSION),
            hubIndexPath = obj.optString("hubIndexPath", NexusHubBridgeService.HUB_INDEX_PATH),
            mirrorIndexPath = obj.optString("mirrorIndexPath", NexusHubBridgeService.MIRROR_INDEX_PATH),
            handshakeContractPath = obj.optString("handshakeContractPath", NexusHubBridgeService.HANDSHAKE_CONTRACT_PATH),
            cardPackImportPaths = jsonArrayToStringList(obj.optJSONArray("cardPackImportPaths"))
                .ifEmpty { listOf("nexus-bridge/card-packs/") }
        )
    }

    fun defaultRepoBridgeManifest(): RepoBridgeManifest = RepoBridgeManifest(
        thisRepo = NexusHubBridgeService.THIS_REPO,
        thisBranch = "main",
        linkedHubRepo = NexusHubBridgeService.LINKED_MASTER_HUB,
        linkedHubBranch = "main",
        linkedCardMakerRepo = NexusHubBridgeService.LINKED_CARD_MAKER,
        linkedCardMakerBranch = "main",
        cardMakerAndroidPackage = NexusHubBridgeService.CARD_MAKER_ANDROID_PACKAGE,
        cardMakerDeepLinkScheme = NexusHubBridgeService.CARD_MAKER_DEEP_LINK_BASE,
        gameEngineAndroidPackage = NexusHubBridgeService.ANDROID_PACKAGE_NAME,
        gameEngineDeepLinkScheme = NexusHubBridgeService.DEEP_LINK_SCHEME,
        bridgeSchemaVersion = NexusHubBridgeService.BRIDGE_SCHEMA_VERSION,
        hubIndexPath = NexusHubBridgeService.HUB_INDEX_PATH,
        mirrorIndexPath = NexusHubBridgeService.MIRROR_INDEX_PATH,
        handshakeContractPath = NexusHubBridgeService.HANDSHAKE_CONTRACT_PATH,
        cardPackImportPaths = listOf("nexus-bridge/card-packs/")
    )

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }
}
