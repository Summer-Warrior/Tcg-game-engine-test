package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class MutualLinkStatus(val displayLabel: String) {
    UNLINKED("UNLINKED"),
    ONE_WAY_ONLY("ONE_WAY_ONLY"),
    LINKED_OUT_OF_SYNC("LINKED_OUT_OF_SYNC"),
    LINKED_AND_CURRENT("LINKED_AND_CURRENT")
}

data class HubHandshakeReport(
    val status: MutualLinkStatus,
    val checkedAtUtcMillis: Long,
    val masterHubReachable: Boolean,
    val cardMakerReachable: Boolean,
    val hubIndexFound: Boolean,
    val discoveredNonLockedClients: List<String>,
    val summaryMessage: String
)

data class MasterHubLaunchResult(
    val launchedNativeApp: Boolean,
    val launchedUrlOrUri: String,
    val statusMessage: String
)

/**
 * Live Master Hub & Card Maker GitHub Bridge service.
 *
 * Uses the GitHub REST API with `X-GitHub-Api-Version: 2026-03-10` and
 * `Accept: application/vnd.github+json`. Strictly obeys:
 * - Public distribution via GitHub Releases & Web only (zero Play Store links).
 * - Zero client-side Discord Bot Tokens or client_secret.
 * - Strict Locked-Client Firewall (`upstreamSourceLock == false` for all exported targets).
 */
object NexusHubBridgeService {
    const val GITHUB_API_VERSION = "2026-03-10"
    const val THIS_REPO = "Summer-Warrior/Tcg-game-engine-test"
    const val DISPLAY_NAME = "NexusTCG Engine"
    const val CATEGORY = "TCG_CLIENT"
    const val DISTRIBUTION_POLICY = "GITHUB_RELEASES_PUBLIC_PLAY_STORE_PERSONAL_ONLY"
    const val ANDROID_PACKAGE_NAME = "com.aistudio.nexustcg.vxqrmk"
    const val DEEP_LINK_SCHEME = "nexus-tcg-engine://open"
    const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/Summer-Warrior/Tcg-game-engine-test/releases/latest"
    const val WEB_URL = "https://summer-warrior.github.io/Tcg-game-engine-test/"

    const val LINKED_MASTER_HUB = "Summer-Warrior/Project-MPV-RP-Master-Hub"
    const val MASTER_HUB_WEB_URL = "https://summer-warrior.github.io/Project-MPV-RP-Master-Hub/"
    const val MASTER_HUB_DEEP_LINK = "nexus-master-hub://open"
    const val LINKED_ASSET_VAULT = "Summer-Warrior/Asset-Holding-Software"
    const val LINKED_CARD_MAKER = "Summer-Warrior/MPV-TCG-Card-Making-Software-Updated"
    const val BRIDGE_SCHEMA_VERSION = "1.0.0"
    const val HUB_INDEX_PATH = "nexus-bridge/client-codestring-index.json"
    const val EXPORTED_PACKS_DIR = "nexus-bridge/card-packs/"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Exact JSON representation of the root `nexus-app.json` contract.
     */
    fun buildNexusAppManifestJson(): String {
        val root = JSONObject().apply {
            put("repo", THIS_REPO)
            put("displayName", DISPLAY_NAME)
            put("category", CATEGORY)
            put("distributionPolicy", DISTRIBUTION_POLICY)
            put("androidPackageName", ANDROID_PACKAGE_NAME)
            put("deepLinkScheme", DEEP_LINK_SCHEME)
            put("githubLatestReleaseApi", GITHUB_LATEST_RELEASE_API)
            put("githubReleaseTag", "latest")
            put("installablePlatforms", JSONArray(listOf("android")))
            put("browserOnlyFallback", true)
            put("webUrl", WEB_URL)
            put("linkedMasterHub", LINKED_MASTER_HUB)
            put("masterHubWebUrl", MASTER_HUB_WEB_URL)
            put("masterHubDeepLinkScheme", MASTER_HUB_DEEP_LINK)
            put("masterHubAndroidPackage", "")
            put("linkedAssetVault", LINKED_ASSET_VAULT)
            put("linkedCardMaker", LINKED_CARD_MAKER)
            put("upstreamSourceLock", false)
            put("bridgeSchemaVersion", BRIDGE_SCHEMA_VERSION)
            put("hubIndexPath", HUB_INDEX_PATH)
            put("exportedPacksDir", EXPORTED_PACKS_DIR)
            put(
                "requiredDiscordLock",
                JSONObject().apply {
                    put("mode", "DISCORD_SIGNED_IN")
                    put("guildId", "")
                    put("allowedRoleIds", JSONArray())
                    put("allowedUserIds", JSONArray())
                }
            )
        }
        return root.toString(2)
    }

    /**
     * Generates the non-locked code-string metadata export payload for
     * `Summer-Warrior/MPV-TCG-Card-Making-Software-Updated`.
     *
     * Locked upstream GitHub clients (`EDOPRO_BASE_MR5`, `FORGE_MTG_BASE`) are placed strictly
     * inside `readOnlyLockedReferences` with `upstreamSourceLock = true` and excluded from
     * `nonLockedWritableClients`.
     */
    fun buildNonLockedCodeStringExportJson(): String {
        val nonLockedArray = JSONArray()
        val lockedRefArray = JSONArray()

        CardCodeValidator.canonicalTargetCatalog.forEach { target ->
            val obj = JSONObject().apply {
                put("clientId", target.clientId)
                put("displayName", target.displayName)
                put("upstreamSourceLock", target.upstreamSourceLock)
                put("upstreamRepo", target.upstreamRepo)
                if (!target.upstreamSourceLock) {
                    put("idFormat", "^[0-9]{1,21}$ | UUID")
                    put("maxRegisterDigits", 21)
                    put("allowTimeTravelFlagsOnMatchCards", false)
                    put("allowedMatchTemporalEffects", JSONArray(listOf("End Turn", "Extra Turn")))
                    put("dossierOnlyFilters", JSONArray(listOf(19, 20, 21)))
                    put("scriptTemplate", "c<21-digits>.lua")
                } else {
                    put("writable", false)
                    put("suggestedNonLockedForkId", target.suggestedNonLockedForkId ?: "ULTIMATE_TCG_21DIGIT_FORK")
                }
            }
            if (target.upstreamSourceLock) {
                lockedRefArray.put(obj)
            } else {
                nonLockedArray.put(obj)
            }
        }

        val pieColorsArray = JSONArray()
        TwentyColorPieCatalog.wheelColors.forEach { c ->
            pieColorsArray.put(
                JSONObject().apply {
                    put("index", c.wheelIndex)
                    put("code", c.code)
                    put("name", c.name)
                    put("anchor", c.isAnchor)
                    put("pip", c.pip)
                }
            )
        }

        val payload = JSONObject().apply {
            put("bridgeSchemaVersion", BRIDGE_SCHEMA_VERSION)
            put("sourceRepo", THIS_REPO)
            put("targetCardMakerRepo", LINKED_CARD_MAKER)
            put("linkedMasterHub", LINKED_MASTER_HUB)
            put("upstreamSourceLock", false)
            put("allowTimeTravelFlagsOnMatchCards", false)
            put("nonLockedWritableClients", nonLockedArray)
            put("readOnlyLockedReferences", lockedRefArray)
            put(
                "twentyColorPieGrammar",
                JSONObject().apply {
                    put("defaultSelectedCount", 0)
                    put("colorlessPip", TwentyColorPieCatalog.COLORLESS_PIP)
                    put("layer20OmniPip", TwentyColorPieCatalog.OMNICHROMA_PIP)
                    put("layer20OmniName", TwentyColorPieCatalog.OMNICHROMA_NAME)
                    put("pairCount", TwentyColorPieCatalog.all190FusedPairs.size)
                    put("curatedTrioCount", TwentyColorPieCatalog.curatedLayer3Trios.size)
                    put("layer19Count", TwentyColorPieCatalog.layer19AllMinusOne.size)
                    put("valueStates", JSONArray(listOf(":T", ":S", ":M")))
                    put("wheelColors", pieColorsArray)
                }
            )
        }
        return payload.toString(2)
    }

    /**
     * Checks live mutual repository links between this repo, Project-MPV-RP-Master-Hub,
     * and MPV-TCG-Card-Making-Software-Updated using GitHub REST API (`2026-03-10`).
     */
    suspend fun verifyMutualRepoHandshake(): HubHandshakeReport = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val hubRepoExists = checkGitHubEndpointOk("https://api.github.com/repos/$LINKED_MASTER_HUB")
        val cardMakerExists = checkGitHubEndpointOk("https://api.github.com/repos/$LINKED_CARD_MAKER")
        val hubIndexBody = fetchGitHubRawFile(LINKED_MASTER_HUB, HUB_INDEX_PATH)
        val hubManifestBody = fetchGitHubRawFile(LINKED_MASTER_HUB, "nexus-app.json")
            ?: fetchGitHubRawFile(LINKED_MASTER_HUB, "nexus-repo-link.json")

        val discoveredClients = mutableListOf<String>()
        if (hubIndexBody != null) {
            runCatching {
                val obj = JSONObject(hubIndexBody)
                val clients = obj.optJSONArray("clients") ?: obj.optJSONArray("nonLockedWritableClients")
                if (clients != null) {
                    for (i in 0 until clients.length()) {
                        val entry = clients.optJSONObject(i) ?: continue
                        if (!entry.optBoolean("upstreamSourceLock", false)) {
                            val name = entry.optString("displayName", entry.optString("clientId", "")).trim()
                            if (name.isNotEmpty()) discoveredClients.add(name)
                        }
                    }
                }
            }
        }

        val hubLinksBack = (hubManifestBody?.contains(THIS_REPO, ignoreCase = true) == true) ||
            (hubIndexBody?.contains(THIS_REPO, ignoreCase = true) == true)

        val status = when {
            !hubRepoExists -> MutualLinkStatus.UNLINKED
            !hubLinksBack -> MutualLinkStatus.ONE_WAY_ONLY
            hubIndexBody == null -> MutualLinkStatus.LINKED_OUT_OF_SYNC
            else -> MutualLinkStatus.LINKED_AND_CURRENT
        }

        val message = when (status) {
            MutualLinkStatus.UNLINKED ->
                "Unable to verify $LINKED_MASTER_HUB via GitHub REST API (offline, private, or rate-limited)."
            MutualLinkStatus.ONE_WAY_ONLY ->
                "Master Hub repo reachable; waiting for $LINKED_MASTER_HUB to reference $THIS_REPO in its manifest/index."
            MutualLinkStatus.LINKED_OUT_OF_SYNC ->
                "Mutual repo link detected, but $HUB_INDEX_PATH was not yet found on Master Hub."
            MutualLinkStatus.LINKED_AND_CURRENT ->
                "Mutual link verified with $LINKED_MASTER_HUB (${discoveredClients.size} non-locked client entries indexed)."
        }

        HubHandshakeReport(
            status = status,
            checkedAtUtcMillis = now,
            masterHubReachable = hubRepoExists,
            cardMakerReachable = cardMakerExists,
            hubIndexFound = hubIndexBody != null,
            discoveredNonLockedClients = discoveredClients,
            summaryMessage = message
        )
    }

    /**
     * Checks whether the installed Master Hub Android app handles `nexus-master-hub://open`
     * (enabled via `<queries>` in AndroidManifest.xml) and launches it directly if present,
     * falling back cleanly to the Master Hub Web Portal URL.
     */
    fun openMasterHub(context: Context): MasterHubLaunchResult {
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(MASTER_HUB_DEEP_LINK)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolvedNative = deepLinkIntent.resolveActivity(context.packageManager)
        if (resolvedNative != null) {
            val started = runCatching { context.startActivity(deepLinkIntent) }.isSuccess
            if (started) {
                return MasterHubLaunchResult(
                    launchedNativeApp = true,
                    launchedUrlOrUri = MASTER_HUB_DEEP_LINK,
                    statusMessage = "Opened installed Project MPV RP Master Hub app via $MASTER_HUB_DEEP_LINK."
                )
            }
        }

        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(MASTER_HUB_WEB_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webStarted = runCatching { context.startActivity(webIntent) }.isSuccess
        return MasterHubLaunchResult(
            launchedNativeApp = false,
            launchedUrlOrUri = MASTER_HUB_WEB_URL,
            statusMessage = if (webStarted) {
                "Master Hub app not installed; opened Web Portal at $MASTER_HUB_WEB_URL."
            } else {
                "Could not launch browser for $MASTER_HUB_WEB_URL."
            }
        )
    }

    fun isMasterHubAppInstalled(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(MASTER_HUB_DEEP_LINK)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return intent.resolveActivity(context.packageManager) != null
    }

    private fun checkGitHubEndpointOk(url: String): Boolean {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", "NexusTCG-Engine-Android/1.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun fetchGitHubRawFile(ownerRepo: String, path: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo/contents/$path")
                .header("Accept", "application/vnd.github.raw+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", "NexusTCG-Engine-Android/1.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }
}
