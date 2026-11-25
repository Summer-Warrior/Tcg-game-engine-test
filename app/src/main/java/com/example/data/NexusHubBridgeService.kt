package com.example.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.engine.CatalogBlueprint
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class MutualLinkStatus(val displayLabel: String) {
    UNLINKED("UNLINKED"),
    ONE_WAY_ONLY("ONE_WAY_ONLY"),
    LINKED_OUT_OF_SYNC("LINKED_OUT_OF_SYNC"),
    LINKED_AND_CURRENT("LINKED_AND_CURRENT")
}

data class InstalledCardMakerInfo(
    val isInstalled: Boolean,
    val packageName: String,
    val versionName: String,
    val versionCode: Long
)

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

data class LocalDualConsentState(
    val gameEngineConsented: Boolean,
    val cardMakerConsentedObserved: Boolean,
    val schemaVersion: String,
    val recordedAtUtc: String,
    val recordedBy: String
)

data class GitHubCommitResult(
    val success: Boolean,
    val targetRepo: String,
    val filePath: String,
    val commitSha: String,
    val httpCode: Int,
    val retriedConflict: Boolean,
    val message: String
)

data class PulledCardPacksResult(
    val sourceRepo: String,
    val branch: String,
    val importedPacks: List<FederatedCardPack>,
    val rejectedNotes: List<String>,
    val summaryMessage: String
)

/**
 * Live Master Hub & MPV Card Maker Bidirectional GitHub Bridge service.
 *
 * Uses GitHub REST API (`X-GitHub-Api-Version: 2026-03-10`, `Accept: application/vnd.github+json`).
 * Strictly enforces:
 * - Zero public Google Play Store URLs anywhere (uses local PackageManager detection for private install).
 * - Zero client-side Discord Bot Tokens or `client_secret`.
 * - Zero hardcoded GitHub PATs (PAT supplied at runtime only, never persisted unless explicitly opted in).
 * - Strict Locked-Client Firewall (`upstreamSourceLock == false` for all writable exports & imports).
 */
object NexusHubBridgeService {
    const val GITHUB_API_VERSION = "2026-03-10"
    const val BRIDGE_SCHEMA_VERSION = "1.1.0"
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
    const val CARD_MAKER_ANDROID_PACKAGE = "com.aistudio.tcgstudio.kqrmvx"
    const val CARD_MAKER_DEEP_LINK_BASE = "tcgstudio://nexus-hub"
    const val CARD_MAKER_DEEP_LINK_SYNC = "tcgstudio://nexus-hub?hubSync=1&from=nexustcg"
    const val CARD_MAKER_DEEP_LINK_USAGE_REPORT = "tcgstudio://nexus-hub?importUsageReport=1&from=nexustcg"
    const val CARD_MAKER_WEB_URL = "https://summer-warrior.github.io/MPV-TCG-Card-Making-Software-Updated/"

    const val HUB_INDEX_PATH = "client-codestring-index.json"
    const val MIRROR_INDEX_PATH = "nexus-bridge/client-codestring-index.json"
    const val HANDSHAKE_CONTRACT_PATH = "handshake-contract.json"
    const val EXPORTED_PACKS_DIR = "nexus-bridge/card-packs/"
    const val USAGE_REPORTS_DIR = "nexus-bridge/usage-reports/"

    private const val PREFS_CONSENT = "nexus_tcg_dual_consent_prefs"
    private const val KEY_ENGINE_CONSENTED = "engine_consented"
    private const val KEY_CARD_MAKER_OBSERVED = "card_maker_consented_observed"
    private const val KEY_CONSENT_SCHEMA = "consent_schema_version"
    private const val KEY_CONSENT_AT = "consent_recorded_at_utc"
    private const val KEY_CONSENT_BY = "consent_recorded_by"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks local device installation of the private Play Store companion app
     * (`com.aistudio.tcgstudio.kqrmvx`) using explicit `<queries>` declaration in AndroidManifest.
     * NEVER constructs or opens any Google Play Store URL.
     */
    @Suppress("DEPRECATION")
    fun detectInstalledCardMakerApp(context: Context): InstalledCardMakerInfo {
        return runCatching {
            val pm = context.packageManager
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(CARD_MAKER_ANDROID_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(CARD_MAKER_ANDROID_PACKAGE, 0)
            }
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                pkgInfo.versionCode.toLong()
            }
            InstalledCardMakerInfo(
                isInstalled = true,
                packageName = CARD_MAKER_ANDROID_PACKAGE,
                versionName = pkgInfo.versionName ?: "installed",
                versionCode = vCode
            )
        }.getOrElse {
            InstalledCardMakerInfo(
                isInstalled = false,
                packageName = CARD_MAKER_ANDROID_PACKAGE,
                versionName = "",
                versionCode = 0L
            )
        }
    }

    /**
     * Opens the installed MPV Card Maker Android app directly via `tcgstudio://nexus-hub?hubSync=1&from=nexustcg`
     * (or package launch intent), falling back cleanly to the GitHub Pages web app URL.
     */
    fun openCardMakerAppForHandshake(context: Context): MasterHubLaunchResult {
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(CARD_MAKER_DEEP_LINK_SYNC)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (deepLinkIntent.resolveActivity(context.packageManager) != null) {
            if (runCatching { context.startActivity(deepLinkIntent) }.isSuccess) {
                return MasterHubLaunchResult(
                    launchedNativeApp = true,
                    launchedUrlOrUri = CARD_MAKER_DEEP_LINK_SYNC,
                    statusMessage = "Opened MPV Card Maker app via $CARD_MAKER_DEEP_LINK_SYNC."
                )
            }
        }

        val pkgLaunch = context.packageManager.getLaunchIntentForPackage(CARD_MAKER_ANDROID_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("hubSync", "1")
            putExtra("sourceRepo", THIS_REPO)
        }
        if (pkgLaunch != null && runCatching { context.startActivity(pkgLaunch) }.isSuccess) {
            return MasterHubLaunchResult(
                launchedNativeApp = true,
                launchedUrlOrUri = CARD_MAKER_ANDROID_PACKAGE,
                statusMessage = "Launched installed MPV Card Maker package ($CARD_MAKER_ANDROID_PACKAGE)."
            )
        }

        val webUrl = "$CARD_MAKER_WEB_URL?hubSync=1&from=nexustcg"
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webOk = runCatching { context.startActivity(webIntent) }.isSuccess
        return MasterHubLaunchResult(
            launchedNativeApp = false,
            launchedUrlOrUri = webUrl,
            statusMessage = if (webOk) {
                "MPV Card Maker Android app not detected locally; opened Web Portal at $webUrl."
            } else {
                "Could not launch browser for $webUrl."
            }
        )
    }

    /**
     * Sends a `CustomCardUsageReport` v1.0.0 JSON directly to the private Android Play Store
     * install of MPV Card Maker (`com.aistudio.tcgstudio.kqrmvx`) via
     * `tcgstudio://nexus-hub?importUsageReport=1&from=nexustcg` with a FileProvider JSON URI
     * and automatic clipboard fallback. Never uses any Google Play Store URL.
     */
    fun sendUsageReportToCardMaker(
        context: Context,
        reportJson: String
    ): MasterHubLaunchResult {
        // Always stage a clipboard fallback so Card Maker or Web Portal can paste instantaneously
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("card-usage-report.json", reportJson))
        }

        val reportUri: Uri? = runCatching {
            val dir = File(context.cacheDir, "telemetry_reports").apply { mkdirs() }
            val file = File(dir, "card-usage-report.json")
            file.writeText(reportJson, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()

        val deepLinkIntent = Intent(Intent.ACTION_VIEW, Uri.parse(CARD_MAKER_DEEP_LINK_USAGE_REPORT)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("importUsageReport", "1")
            putExtra("from", "nexustcg")
            putExtra("sourceRepo", THIS_REPO)
            putExtra("usageReportJson", reportJson)
            if (reportUri != null) {
                putExtra(Intent.EXTRA_STREAM, reportUri)
                putExtra("usageReportUri", reportUri.toString())
                clipData = ClipData.newUri(context.contentResolver, "card-usage-report.json", reportUri)
                runCatching {
                    context.grantUriPermission(
                        CARD_MAKER_ANDROID_PACKAGE,
                        reportUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        }
        if (deepLinkIntent.resolveActivity(context.packageManager) != null &&
            runCatching { context.startActivity(deepLinkIntent) }.isSuccess
        ) {
            return MasterHubLaunchResult(
                launchedNativeApp = true,
                launchedUrlOrUri = CARD_MAKER_DEEP_LINK_USAGE_REPORT,
                statusMessage = "Sent CustomCardUsageReport to installed Card Maker ($CARD_MAKER_ANDROID_PACKAGE) + copied JSON to clipboard."
            )
        }

        val pkgLaunch = context.packageManager.getLaunchIntentForPackage(CARD_MAKER_ANDROID_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra("importUsageReport", "1")
            putExtra("from", "nexustcg")
            putExtra("usageReportJson", reportJson)
            if (reportUri != null) {
                putExtra(Intent.EXTRA_STREAM, reportUri)
                putExtra("usageReportUri", reportUri.toString())
            }
        }
        if (pkgLaunch != null && runCatching { context.startActivity(pkgLaunch) }.isSuccess) {
            return MasterHubLaunchResult(
                launchedNativeApp = true,
                launchedUrlOrUri = CARD_MAKER_ANDROID_PACKAGE,
                statusMessage = "Opened Card Maker ($CARD_MAKER_ANDROID_PACKAGE) with Usage Report + clipboard fallback."
            )
        }

        val webUrl = "$CARD_MAKER_WEB_URL?importUsageReport=1&from=nexustcg"
        val webOk = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.isSuccess
        return MasterHubLaunchResult(
            launchedNativeApp = false,
            launchedUrlOrUri = webUrl,
            statusMessage = if (webOk) {
                "Card Maker app not installed locally; copied Usage Report JSON to clipboard & opened Web Portal."
            } else {
                "Copied Usage Report JSON to clipboard (install Card Maker or open Web Portal to import)."
            }
        )
    }

    suspend fun pushUsageReportToGitHub(
        reportJson: String,
        branch: String = "main",
        fineGrainedPat: String,
        mutualLinkVerified: Boolean,
        targetOwnerRepo: String = LINKED_CARD_MAKER
    ): GitHubCommitResult {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val filePath = "${USAGE_REPORTS_DIR}report-$stamp.json"
        return commitFileToGitHubRepo(
            ownerRepo = targetOwnerRepo,
            branch = branch,
            filePath = filePath,
            contentUtf8 = reportJson,
            commitMessage = "chore(telemetry): push CustomCardUsageReport v1.0.0 ($stamp)",
            fineGrainedPat = fineGrainedPat,
            mutualLinkVerified = mutualLinkVerified
        )
    }

    fun getLocalConsentState(context: Context): LocalDualConsentState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_CONSENT, Context.MODE_PRIVATE)
        return LocalDualConsentState(
            gameEngineConsented = prefs.getBoolean(KEY_ENGINE_CONSENTED, true),
            cardMakerConsentedObserved = prefs.getBoolean(KEY_CARD_MAKER_OBSERVED, false),
            schemaVersion = prefs.getString(KEY_CONSENT_SCHEMA, BRIDGE_SCHEMA_VERSION) ?: BRIDGE_SCHEMA_VERSION,
            recordedAtUtc = prefs.getString(KEY_CONSENT_AT, currentIsoUtc()) ?: currentIsoUtc(),
            recordedBy = prefs.getString(KEY_CONSENT_BY, "NexusTCG Engine Operator") ?: "NexusTCG Engine Operator"
        )
    }

    fun setLocalConsentState(
        context: Context,
        gameEngineConsented: Boolean,
        cardMakerConsentedObserved: Boolean = getLocalConsentState(context).cardMakerConsentedObserved,
        recordedBy: String = "NexusTCG Engine Operator"
    ): LocalDualConsentState {
        val now = currentIsoUtc()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_CONSENT, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENGINE_CONSENTED, gameEngineConsented)
            .putBoolean(KEY_CARD_MAKER_OBSERVED, cardMakerConsentedObserved)
            .putString(KEY_CONSENT_SCHEMA, BRIDGE_SCHEMA_VERSION)
            .putString(KEY_CONSENT_AT, now)
            .putString(KEY_CONSENT_BY, recordedBy.trim().ifEmpty { "NexusTCG Engine Operator" })
            .apply()
        return getLocalConsentState(context)
    }

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
            put("cardMakerAndroidPackage", CARD_MAKER_ANDROID_PACKAGE)
            put("cardMakerDeepLinkScheme", CARD_MAKER_DEEP_LINK_BASE)
            put("upstreamSourceLock", false)
            put("bridgeSchemaVersion", BRIDGE_SCHEMA_VERSION)
            put("hubIndexPath", HUB_INDEX_PATH)
            put("mirrorIndexPath", MIRROR_INDEX_PATH)
            put("handshakeContractPath", HANDSHAKE_CONTRACT_PATH)
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

    fun buildNonLockedCodeStringExportJson(
        additionalNonLockedCombos: List<CatalogBlueprint> = emptyList()
    ): String {
        return CodeStringExporter.compileClientCodeStringIndexJson(additionalNonLockedCombos)
    }

    /**
     * Option A — Direct GitHub Commit (GET existing file SHA + Base64 PUT) with 409/422 conflict retry.
     * PAT is used strictly in-memory for this HTTP call and NEVER hardcoded or logged.
     */
    suspend fun commitFileToGitHubRepo(
        ownerRepo: String,
        branch: String,
        filePath: String,
        contentUtf8: String,
        commitMessage: String,
        fineGrainedPat: String,
        mutualLinkVerified: Boolean
    ): GitHubCommitResult = withContext(Dispatchers.IO) {
        val cleanPat = fineGrainedPat.trim()
        if (!mutualLinkVerified) {
            return@withContext GitHubCommitResult(
                success = false,
                targetRepo = ownerRepo,
                filePath = filePath,
                commitSha = "",
                httpCode = 0,
                retriedConflict = false,
                message = "Direct commit blocked: mutual repository link must be verified first (use Option B Copy/Export or verify handshake)."
            )
        }
        if (cleanPat.isEmpty()) {
            return@withContext GitHubCommitResult(
                success = false,
                targetRepo = ownerRepo,
                filePath = filePath,
                commitSha = "",
                httpCode = 0,
                retriedConflict = false,
                message = "Enter a Fine-Grained GitHub PAT (Contents: Read & Write) to commit directly, or use zero-token Copy/Share."
            )
        }

        val cleanPath = filePath.trimStart('/')
        val cleanBranch = branch.trim().ifEmpty { "main" }
        val encodedContent = Base64.encodeToString(contentUtf8.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        suspend fun attemptPut(existingSha: String?): Pair<Int, String> {
            val payload = JSONObject().apply {
                put("message", commitMessage.trim().ifEmpty { "chore(nexus-bridge): sync $cleanPath" })
                put("content", encodedContent)
                put("branch", cleanBranch)
                if (!existingSha.isNullOrBlank()) {
                    put("sha", existingSha)
                }
            }
            val req = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo/contents/$cleanPath")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("Authorization", "Bearer $cleanPat")
                .header("User-Agent", "NexusTCG-Engine-Android/3.0")
                .put(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            return httpClient.newCall(req).execute().use { resp ->
                resp.code to (resp.body?.string().orEmpty())
            }
        }

        runCatching {
            val firstSha = fetchFileBlobSha(ownerRepo, cleanBranch, cleanPath, cleanPat)
            val (code1, body1) = attemptPut(firstSha)
            if (code1 in 200..299) {
                val commitSha = runCatching {
                    JSONObject(body1).optJSONObject("commit")?.optString("sha", "").orEmpty()
                }.getOrDefault("")
                return@withContext GitHubCommitResult(
                    success = true,
                    targetRepo = ownerRepo,
                    filePath = cleanPath,
                    commitSha = commitSha,
                    httpCode = code1,
                    retriedConflict = false,
                    message = "Committed $cleanPath to $ownerRepo@$cleanBranch (${commitSha.take(7)})."
                )
            }

            // 409/422 Conflict recovery: re-fetch latest blob SHA and retry once
            if (code1 == 409 || code1 == 422) {
                val refreshedSha = fetchFileBlobSha(ownerRepo, cleanBranch, cleanPath, cleanPat)
                val (code2, body2) = attemptPut(refreshedSha)
                if (code2 in 200..299) {
                    val commitSha = runCatching {
                        JSONObject(body2).optJSONObject("commit")?.optString("sha", "").orEmpty()
                    }.getOrDefault("")
                    return@withContext GitHubCommitResult(
                        success = true,
                        targetRepo = ownerRepo,
                        filePath = cleanPath,
                        commitSha = commitSha,
                        httpCode = code2,
                        retriedConflict = true,
                        message = "Resolved HTTP $code1 conflict and committed $cleanPath (${commitSha.take(7)})."
                    )
                }
                return@withContext GitHubCommitResult(
                    success = false,
                    targetRepo = ownerRepo,
                    filePath = cleanPath,
                    commitSha = "",
                    httpCode = code2,
                    retriedConflict = true,
                    message = "Conflict retry failed (HTTP $code2). Use Option B Copy/Export JSON instead."
                )
            }

            GitHubCommitResult(
                success = false,
                targetRepo = ownerRepo,
                filePath = cleanPath,
                commitSha = "",
                httpCode = code1,
                retriedConflict = false,
                message = "GitHub commit returned HTTP $code1 for $ownerRepo/$cleanPath."
            )
        }.getOrElse { err ->
            GitHubCommitResult(
                success = false,
                targetRepo = ownerRepo,
                filePath = cleanPath,
                commitSha = "",
                httpCode = 0,
                retriedConflict = false,
                message = "Network error during GitHub commit: ${err.javaClass.simpleName}"
            )
        }
    }

    /**
     * Pulls coded Card Packs JSON files from `Summer-Warrior/MPV-TCG-Card-Making-Software-Updated`
     * (checking `nexus-bridge/card-packs` and `card-packs`) and validates every card through
     * `CodeStringExporter.validateAndParseCardPackJson(...)`.
     */
    suspend fun pullCardPacksFromCardMaker(
        ownerRepo: String = LINKED_CARD_MAKER,
        branch: String = "main",
        searchDirs: List<String> = listOf("nexus-bridge/card-packs", "card-packs")
    ): PulledCardPacksResult = withContext(Dispatchers.IO) {
        val imported = mutableListOf<FederatedCardPack>()
        val rejected = mutableListOf<String>()

        for (dir in searchDirs) {
            val cleanDir = dir.trim().trim('/')
            val listingJson = fetchGitHubApiJson(
                "https://api.github.com/repos/$ownerRepo/contents/$cleanDir?ref=$branch"
            ) ?: continue

            val itemsArr = runCatching { JSONArray(listingJson) }.getOrNull() ?: continue
            for (i in 0 until itemsArr.length().coerceAtMost(20)) {
                val entry = itemsArr.optJSONObject(i) ?: continue
                val fileName = entry.optString("name", "")
                val filePath = entry.optString("path", "")
                if (!fileName.endsWith(".json", ignoreCase = true) || filePath.isBlank()) continue

                val rawPack = fetchGitHubRawFile(ownerRepo, filePath, branch) ?: continue
                val report = CodeStringExporter.validateAndParseCardPackJson(rawPack)
                if (report.rejectedReasons.isNotEmpty()) {
                    rejected.addAll(report.rejectedReasons.map { "$fileName: $it" })
                }
                if (report.acceptedCards.isNotEmpty()) {
                    val rootObj = runCatching { JSONObject(rawPack) }.getOrNull()
                    imported.add(
                        FederatedCardPack(
                            packId = report.packId,
                            packName = report.packName,
                            targetClientId = rootObj?.optString("targetClientId", "ULTIMATE_TCG_WIP") ?: "ULTIMATE_TCG_WIP",
                            upstreamSourceLock = false,
                            cards = report.acceptedCards,
                            splitOff = report.splitOffMetadata
                        )
                    )
                }
            }
        }

        val totalCards = imported.sumOf { it.cards.size }
        val summary = when {
            imported.isNotEmpty() ->
                "Pulled ${imported.size} validated card pack(s) ($totalCards non-locked card(s)) from $ownerRepo@$branch."
            rejected.isNotEmpty() ->
                "Pull completed with ${rejected.size} firewall/validation rejection(s); 0 packs accepted."
            else ->
                "No remote JSON card packs found yet in $ownerRepo@$branch (${searchDirs.joinToString(", ")}). Bundled starter packs remain active."
        }
        PulledCardPacksResult(
            sourceRepo = ownerRepo,
            branch = branch,
            importedPacks = imported,
            rejectedNotes = rejected,
            summaryMessage = summary
        )
    }

    /**
     * Option B — Zero-Token mobile export via Android Share Sheet (JSON / YAML / Markdown).
     */
    fun shareTextArtifact(
        context: Context,
        title: String,
        content: String,
        mimeType: String = "text/plain"
    ): Boolean {
        return runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_TEXT, content)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Export $title").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    suspend fun verifyMutualRepoHandshake(): HubHandshakeReport = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val hubRepoExists = checkGitHubEndpointOk("https://api.github.com/repos/$LINKED_MASTER_HUB")
        val cardMakerExists = checkGitHubEndpointOk("https://api.github.com/repos/$LINKED_CARD_MAKER")
        val hubIndexBody = fetchGitHubRawFile(LINKED_MASTER_HUB, HUB_INDEX_PATH)
            ?: fetchGitHubRawFile(LINKED_MASTER_HUB, MIRROR_INDEX_PATH)
        val hubManifestBody = fetchGitHubRawFile(LINKED_MASTER_HUB, "nexus-repo-link.json")
            ?: fetchGitHubRawFile(LINKED_MASTER_HUB, "nexus-app.json")
            ?: fetchGitHubRawFile(LINKED_MASTER_HUB, "public/registry/repo-bridge.json")
        val cardMakerManifestBody = fetchGitHubRawFile(LINKED_CARD_MAKER, "nexus-repo-link.json")
            ?: fetchGitHubRawFile(LINKED_CARD_MAKER, "handshake-contract.json")

        val discoveredClients = mutableListOf<String>()
        if (hubIndexBody != null) {
            runCatching {
                val obj = JSONObject(hubIndexBody)
                val clients = obj.optJSONArray("nonLockedClients")
                    ?: obj.optJSONArray("clients")
                    ?: obj.optJSONArray("nonLockedWritableClients")
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
        val cardMakerLinksBack = cardMakerManifestBody?.contains(THIS_REPO, ignoreCase = true) == true

        val status = when {
            !hubRepoExists && !cardMakerExists -> MutualLinkStatus.UNLINKED
            !hubLinksBack && !cardMakerLinksBack -> MutualLinkStatus.ONE_WAY_ONLY
            hubIndexBody == null && !cardMakerLinksBack -> MutualLinkStatus.LINKED_OUT_OF_SYNC
            else -> MutualLinkStatus.LINKED_AND_CURRENT
        }

        val message = when (status) {
            MutualLinkStatus.UNLINKED ->
                "Unable to reach linked repositories via GitHub REST API (offline, private, or rate-limited)."
            MutualLinkStatus.ONE_WAY_ONLY ->
                "Linked repos reachable; waiting for $LINKED_CARD_MAKER or $LINKED_MASTER_HUB to reference $THIS_REPO."
            MutualLinkStatus.LINKED_OUT_OF_SYNC ->
                "Mutual repo link detected, but index or Card Maker manifest needs sync."
            MutualLinkStatus.LINKED_AND_CURRENT ->
                "Mutual link verified (${discoveredClients.size} non-locked client entries indexed)."
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

    private fun fetchFileBlobSha(
        ownerRepo: String,
        branch: String,
        path: String,
        pat: String
    ): String? {
        return runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo/contents/$path?ref=$branch")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("Authorization", "Bearer $pat")
                .header("User-Agent", "NexusTCG-Engine-Android/3.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    JSONObject(resp.body?.string().orEmpty()).optString("sha", "").ifBlank { null }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun checkGitHubEndpointOk(url: String): Boolean {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", "NexusTCG-Engine-Android/3.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun fetchGitHubApiJson(url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", "NexusTCG-Engine-Android/3.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    private fun fetchGitHubRawFile(ownerRepo: String, path: String, branch: String = "main"): String? {
        return runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$ownerRepo/contents/$path?ref=$branch")
                .header("Accept", "application/vnd.github.raw+json")
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", "NexusTCG-Engine-Android/3.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull()
    }

    fun currentIsoUtc(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}
