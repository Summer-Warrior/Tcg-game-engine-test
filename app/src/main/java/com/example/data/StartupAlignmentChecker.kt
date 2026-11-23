package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class AlignmentHealthState(val badgeLabel: String) {
    UNLINKED("UNLINKED"),
    ONE_WAY_ONLY("ONE_WAY_ONLY"),
    LINKED_OUT_OF_SYNC("LINKED_OUT_OF_SYNC"),
    DEGRADED("DEGRADED"),
    BREAKING_INCOMPATIBLE("BREAKING_INCOMPATIBLE"),
    LINKED_AND_CURRENT("LINKED_AND_CURRENT")
}

data class AlignmentEvaluationInput(
    val localSchemaVersion: String = NexusHubBridgeService.BRIDGE_SCHEMA_VERSION,
    val remoteCardMakerSchemaVersion: String? = null,
    val cardMakerRepoReachable: Boolean = false,
    val masterHubRepoReachable: Boolean = false,
    val cardMakerLinksToEngine: Boolean = false,
    val masterHubLinksToEngine: Boolean = false,
    val remoteHealthDegraded: Boolean = false,
    val remoteHealthNote: String = "",
    val cardMakerConsentSigned: Boolean = false,
    val gameEngineConsentSigned: Boolean = true,
    val cardMakerCommitSha: String = "",
    val masterHubCommitSha: String = "",
    val dismissedCommitSha: String = "",
    val missingFiles: List<String> = emptyList(),
    val isOfflineFallback: Boolean = false
)

data class AlignmentCheckReport(
    val status: AlignmentHealthState,
    val checkedAtUtcMillis: Long,
    val cardMakerCommitSha: String,
    val masterHubCommitSha: String,
    val installedCardMaker: InstalledCardMakerInfo,
    val cardMakerConsentSigned: Boolean,
    val gameEngineConsentSigned: Boolean,
    val localSchemaVersion: String,
    val remoteSchemaVersion: String,
    val missingOrMismatchedItems: List<String>,
    val shouldShowPopup: Boolean,
    val effectiveCommitKey: String,
    val summaryMessage: String,
    val isOfflineFallback: Boolean
) {
    val isDualConsentComplete: Boolean
        get() = cardMakerConsentSigned && gameEngineConsentSigned
}

/**
 * Automatic On-Open Alignment & Update Checker for:
 * - Local private Play Store install of `com.aistudio.tcgstudio.kqrmvx` (MPV Card Maker)
 * - Remote `Summer-Warrior/MPV-TCG-Card-Making-Software-Updated` manifests, handshake contract,
 *   health status, sync snapshot, and latest `main` commit SHA
 * - Remote `Summer-Warrior/Project-MPV-RP-Master-Hub` manifests & commit SHA
 *
 * Uses ETag (`If-None-Match`) caching in SharedPreferences, `X-GitHub-Api-Version: 2026-03-10`,
 * and degrades gracefully when offline or rate-limited.
 */
object StartupAlignmentChecker {
    private const val PREFS_NAME = "nexus_tcg_startup_alignment_cache"
    private const val KEY_DISMISSED_SHA = "dismissed_alignment_commit_sha"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Pure deterministic evaluator (100% unit-testable on JVM without Android Context or network).
     */
    fun evaluateAlignmentStatus(
        input: AlignmentEvaluationInput,
        installedCardMaker: InstalledCardMakerInfo = InstalledCardMakerInfo(false, NexusHubBridgeService.CARD_MAKER_ANDROID_PACKAGE, "", 0L),
        checkedAtUtcMillis: Long = System.currentTimeMillis()
    ): AlignmentCheckReport {
        val diffs = mutableListOf<String>()
        val localMajor = input.localSchemaVersion.substringBefore(".").toIntOrNull() ?: 1
        val remoteSchema = input.remoteCardMakerSchemaVersion?.trim().orEmpty()
        val remoteMajor = remoteSchema.substringBefore(".").toIntOrNull()

        if (!input.cardMakerRepoReachable) {
            diffs.add("MPV Card Maker repo (${NexusHubBridgeService.LINKED_CARD_MAKER}) unreachable or offline.")
        }
        if (!input.masterHubRepoReachable) {
            diffs.add("Master Hub repo (${NexusHubBridgeService.LINKED_MASTER_HUB}) unreachable or offline.")
        }
        if (input.cardMakerRepoReachable && !input.cardMakerLinksToEngine) {
            diffs.add("MPV Card Maker manifest does not yet reference ${NexusHubBridgeService.THIS_REPO}.")
        }
        if (input.masterHubRepoReachable && !input.masterHubLinksToEngine) {
            diffs.add("Master Hub manifest does not yet reference ${NexusHubBridgeService.THIS_REPO}.")
        }
        if (remoteSchema.isNotEmpty() && remoteMajor != null && remoteMajor != localMajor) {
            diffs.add("Breaking schema mismatch: local v${input.localSchemaVersion} vs remote Card Maker v$remoteSchema.")
        } else if (remoteSchema.isNotEmpty() && remoteSchema != input.localSchemaVersion) {
            diffs.add("Schema minor/patch drift: local v${input.localSchemaVersion} vs remote v$remoteSchema.")
        }
        if (input.remoteHealthDegraded) {
            diffs.add(input.remoteHealthNote.ifBlank { "Remote engine-health-status.json reported DEGRADED health." })
        }
        if (!input.gameEngineConsentSigned || !input.cardMakerConsentSigned) {
            diffs.add(
                "Dual-Engine Consent pending (Engine=${if (input.gameEngineConsentSigned) "Signed" else "Pending"}, " +
                    "Card Maker=${if (input.cardMakerConsentSigned) "Signed" else "Pending"})."
            )
        }
        diffs.addAll(input.missingFiles)

        val status = when {
            remoteMajor != null && remoteMajor != localMajor -> AlignmentHealthState.BREAKING_INCOMPATIBLE
            input.remoteHealthDegraded -> AlignmentHealthState.DEGRADED
            !input.cardMakerRepoReachable && !input.masterHubRepoReachable -> AlignmentHealthState.UNLINKED
            !input.cardMakerLinksToEngine && !input.masterHubLinksToEngine -> AlignmentHealthState.ONE_WAY_ONLY
            remoteSchema.isNotEmpty() && remoteSchema != input.localSchemaVersion -> AlignmentHealthState.LINKED_OUT_OF_SYNC
            !input.gameEngineConsentSigned || !input.cardMakerConsentSigned || input.missingFiles.isNotEmpty() ->
                AlignmentHealthState.LINKED_OUT_OF_SYNC
            else -> AlignmentHealthState.LINKED_AND_CURRENT
        }

        val dualConsentDone = input.gameEngineConsentSigned && input.cardMakerConsentSigned
        val effectiveCommitKey = listOf(
            input.cardMakerCommitSha.ifBlank { "no-cm-sha" },
            input.masterHubCommitSha.ifBlank { "no-hub-sha" },
            status.name,
            if (dualConsentDone) "consented" else "pending"
        ).joinToString("|")

        val needsAttention = (status != AlignmentHealthState.LINKED_AND_CURRENT) || !dualConsentDone
        val shouldShowPopup = needsAttention && (effectiveCommitKey != input.dismissedCommitSha)

        val summary = when (status) {
            AlignmentHealthState.LINKED_AND_CURRENT ->
                "NexusTCG Engine, MPV Card Maker, and Master Hub are mutually linked and current (schema v${input.localSchemaVersion})."
            AlignmentHealthState.BREAKING_INCOMPATIBLE ->
                "Breaking schema version difference detected (local v${input.localSchemaVersion} vs remote v$remoteSchema). Do not sync until both apps consent."
            AlignmentHealthState.DEGRADED ->
                "Remote bridge reported DEGRADED health. Inspect diffs in the Handshake Area before syncing."
            AlignmentHealthState.LINKED_OUT_OF_SYNC ->
                "Repos are linked, but schema/files or Dual-Engine Consent require alignment (${diffs.size} item(s))."
            AlignmentHealthState.ONE_WAY_ONLY ->
                "One-way link detected: awaiting mutual repo-link manifest from MPV Card Maker / Master Hub."
            AlignmentHealthState.UNLINKED ->
                if (input.isOfflineFallback) {
                    "Offline mode: unable to reach GitHub API right now. Bundled registries and local Card Maker handshake remain available."
                } else {
                    "Linked repositories could not be verified. Open the Handshake Area to export or verify repo manifests."
                }
        }

        return AlignmentCheckReport(
            status = status,
            checkedAtUtcMillis = checkedAtUtcMillis,
            cardMakerCommitSha = input.cardMakerCommitSha,
            masterHubCommitSha = input.masterHubCommitSha,
            installedCardMaker = installedCardMaker,
            cardMakerConsentSigned = input.cardMakerConsentSigned,
            gameEngineConsentSigned = input.gameEngineConsentSigned,
            localSchemaVersion = input.localSchemaVersion,
            remoteSchemaVersion = remoteSchema.ifEmpty { "unknown" },
            missingOrMismatchedItems = diffs,
            shouldShowPopup = shouldShowPopup,
            effectiveCommitKey = effectiveCommitKey,
            summaryMessage = summary,
            isOfflineFallback = input.isOfflineFallback
        )
    }

    fun dismissForCommitKey(context: Context, effectiveCommitKey: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_DISMISSED_SHA, effectiveCommitKey).apply()
    }

    fun clearDismissal(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_DISMISSED_SHA).apply()
    }

    /**
     * Runs the live ETag-cached startup check across MPV Card Maker and Master Hub.
     */
    suspend fun runStartupCheck(
        context: Context,
        forceShowEvenIfDismissed: Boolean = false
    ): AlignmentCheckReport = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installedCardMaker = NexusHubBridgeService.detectInstalledCardMakerApp(context)
        val localConsent = NexusHubBridgeService.getLocalConsentState(context)
        val dismissedKey = if (forceShowEvenIfDismissed) "" else prefs.getString(KEY_DISMISSED_SHA, "").orEmpty()

        val cardMakerRepo = NexusHubBridgeService.LINKED_CARD_MAKER
        val masterHubRepo = NexusHubBridgeService.LINKED_MASTER_HUB

        val cmCommitResp = fetchWithEtagCache(
            prefs = prefs,
            cacheKey = "cm_main_commit",
            url = "https://api.github.com/repos/$cardMakerRepo/commits/main",
            acceptHeader = "application/vnd.github+json"
        )
        val hubCommitResp = fetchWithEtagCache(
            prefs = prefs,
            cacheKey = "hub_main_commit",
            url = "https://api.github.com/repos/$masterHubRepo/commits/main",
            acceptHeader = "application/vnd.github+json"
        )

        val cmCommitSha = cmCommitResp.body?.let {
            runCatching { JSONObject(it).optString("sha", "") }.getOrDefault("")
        }.orEmpty()
        val hubCommitSha = hubCommitResp.body?.let {
            runCatching { JSONObject(it).optString("sha", "") }.getOrDefault("")
        }.orEmpty()

        val cmLinkBody = fetchWithEtagCache(
            prefs = prefs,
            cacheKey = "cm_repo_link",
            url = "https://api.github.com/repos/$cardMakerRepo/contents/nexus-repo-link.json",
            acceptHeader = "application/vnd.github.raw+json"
        ).body

        val cmContractBody = fetchWithEtagCache(
            prefs = prefs,
            cacheKey = "cm_handshake_contract",
            url = "https://api.github.com/repos/$cardMakerRepo/contents/handshake-contract.json",
            acceptHeader = "application/vnd.github.raw+json"
        ).body

        val cmHealthBody = fetchWithEtagCache(
            prefs = prefs,
            cacheKey = "cm_health_status",
            url = "https://api.github.com/repos/$cardMakerRepo/contents/docs/engine-health-status.json",
            acceptHeader = "application/vnd.github.raw+json"
        ).body

        val cmSyncSnapshotBody = fetchWithEtagCache(
            prefs = prefs,
            cacheKey = "cm_sync_snapshot",
            url = "https://api.github.com/repos/$cardMakerRepo/contents/engine-sync-snapshot.json",
            acceptHeader = "application/vnd.github.raw+json"
        ).body

        val hubLinkBody = fetchWithEtagCache(
            prefs = prefs,
            cacheKey = "hub_repo_link",
            url = "https://api.github.com/repos/$masterHubRepo/contents/nexus-repo-link.json",
            acceptHeader = "application/vnd.github.raw+json"
        ).body ?: fetchWithEtagCache(
            prefs = prefs,
            cacheKey = "hub_registry_bridge",
            url = "https://api.github.com/repos/$masterHubRepo/contents/public/registry/repo-bridge.json",
            acceptHeader = "application/vnd.github.raw+json"
        ).body

        val missing = mutableListOf<String>()
        if (cmCommitResp.ok && cmLinkBody == null) {
            missing.add("MPV Card Maker missing root nexus-repo-link.json.")
        }
        if (cmCommitResp.ok && cmContractBody == null) {
            missing.add("MPV Card Maker missing root handshake-contract.json.")
        }

        val remoteSchema = extractRemoteSchemaVersion(cmContractBody, cmLinkBody, cmSyncSnapshotBody)
        val cmLinksToEngine = listOfNotNull(cmLinkBody, cmContractBody, cmSyncSnapshotBody).any {
            it.contains(NexusHubBridgeService.THIS_REPO, ignoreCase = true)
        }
        val hubLinksToEngine = hubLinkBody?.contains(NexusHubBridgeService.THIS_REPO, ignoreCase = true) == true

        var remoteHealthDegraded = false
        var remoteHealthNote = ""
        if (cmHealthBody != null) {
            runCatching {
                val h = JSONObject(cmHealthBody)
                val stateStr = h.optString("status", h.optString("health", "OK")).uppercase()
                if (stateStr.contains("DEGRADED") || stateStr.contains("ERROR") || stateStr.contains("FAIL")) {
                    remoteHealthDegraded = true
                    remoteHealthNote = h.optString("summary", "Remote status: $stateStr")
                }
            }
        }

        val remoteCardMakerConsented = cmContractBody?.let { raw ->
            runCatching {
                val c = JSONObject(raw)
                val cmObj = c.optJSONObject("cardMakerConsent")
                cmObj?.optBoolean("consented", false) == true &&
                    cmObj.optString("consentedSchemaVersion", "") == NexusHubBridgeService.BRIDGE_SCHEMA_VERSION
            }.getOrDefault(false)
        } ?: localConsent.cardMakerConsentedObserved

        if (remoteCardMakerConsented && !localConsent.cardMakerConsentedObserved) {
            NexusHubBridgeService.setLocalConsentState(
                context = context,
                gameEngineConsented = localConsent.gameEngineConsented,
                cardMakerConsentedObserved = true,
                recordedBy = localConsent.recordedBy
            )
        }

        val offline = !cmCommitResp.ok && !hubCommitResp.ok && cmCommitResp.networkError && hubCommitResp.networkError

        evaluateAlignmentStatus(
            input = AlignmentEvaluationInput(
                localSchemaVersion = NexusHubBridgeService.BRIDGE_SCHEMA_VERSION,
                remoteCardMakerSchemaVersion = remoteSchema,
                cardMakerRepoReachable = cmCommitResp.ok,
                masterHubRepoReachable = hubCommitResp.ok,
                cardMakerLinksToEngine = cmLinksToEngine,
                masterHubLinksToEngine = hubLinksToEngine,
                remoteHealthDegraded = remoteHealthDegraded,
                remoteHealthNote = remoteHealthNote,
                cardMakerConsentSigned = remoteCardMakerConsented,
                gameEngineConsentSigned = localConsent.gameEngineConsented,
                cardMakerCommitSha = cmCommitSha,
                masterHubCommitSha = hubCommitSha,
                dismissedCommitSha = dismissedKey,
                missingFiles = missing,
                isOfflineFallback = offline
            ),
            installedCardMaker = installedCardMaker
        )
    }

    private fun extractRemoteSchemaVersion(vararg bodies: String?): String? {
        for (body in bodies) {
            if (body.isNullOrBlank()) continue
            val parsed = runCatching {
                val obj = JSONObject(body)
                obj.optString("bridgeSchemaVersion", "")
                    .ifBlank { obj.optString("contractVersion", "") }
                    .ifBlank { obj.optString("schemaVersion", "") }
            }.getOrDefault("")
            if (parsed.isNotBlank()) return parsed
        }
        return null
    }

    private data class CachedHttpResult(
        val ok: Boolean,
        val body: String?,
        val networkError: Boolean
    )

    private fun fetchWithEtagCache(
        prefs: SharedPreferences,
        cacheKey: String,
        url: String,
        acceptHeader: String
    ): CachedHttpResult {
        val etagKey = "etag_$cacheKey"
        val bodyKey = "body_$cacheKey"
        val cachedEtag = prefs.getString(etagKey, null)
        val cachedBody = prefs.getString(bodyKey, null)

        return runCatching {
            val builder = Request.Builder()
                .url(url)
                .header("Accept", acceptHeader)
                .header("X-GitHub-Api-Version", NexusHubBridgeService.GITHUB_API_VERSION)
                .header("User-Agent", "NexusTCG-Engine-Android/3.0")
                .get()
            if (!cachedEtag.isNullOrBlank()) {
                builder.header("If-None-Match", cachedEtag)
            }
            httpClient.newCall(builder.build()).execute().use { resp ->
                when {
                    resp.code == 304 && cachedBody != null -> {
                        CachedHttpResult(ok = true, body = cachedBody, networkError = false)
                    }
                    resp.isSuccessful -> {
                        val freshBody = resp.body?.string().orEmpty()
                        val newEtag = resp.header("ETag")
                        prefs.edit().apply {
                            if (!newEtag.isNullOrBlank()) putString(etagKey, newEtag)
                            putString(bodyKey, freshBody)
                            apply()
                        }
                        CachedHttpResult(ok = true, body = freshBody, networkError = false)
                    }
                    else -> {
                        CachedHttpResult(ok = false, body = null, networkError = false)
                    }
                }
            }
        }.getOrElse {
            CachedHttpResult(ok = cachedBody != null, body = cachedBody, networkError = true)
        }
    }
}
