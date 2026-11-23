package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class DiscordBroadcastEventType(val label: String, val badgeCode: String, val embedColorInt: Int) {
    GAME_START("Game Start", "GAME_START", 0x00E5FF),
    DECK_SUMMARY("Deck Summary", "DECK_SUMMARY", 0x7C4DFF),
    MATCH_WINNER("Match Winner", "MATCH_WINNER", 0xFFB300)
}

data class DiscordWebhookBridgeConfig(
    val designatedChannelName: String = "#duel-broadcasts",
    val autoBroadcastGameStart: Boolean = true,
    val autoBroadcastMatchWinner: Boolean = true,
    val autoBroadcastDeckOnForge: Boolean = false
)

data class DiscordWebhookBroadcastRecord(
    val id: String = UUID.randomUUID().toString(),
    val eventType: DiscordBroadcastEventType,
    val channelLabel: String,
    val title: String,
    val summary: String,
    val deliveryStatus: String,
    val httpCode: Int? = null,
    val timestampLabel: String
)

data class DiscordRemoteGuild(
    val guildId: String,
    val serverName: String,
    val defaultChannelId: String,
    val defaultChannelName: String,
    val approxMemberCount: Long
)

data class DiscordRemoteMessage(
    val messageId: String,
    val authorName: String,
    val isWebhookOrBot: Boolean,
    val content: String,
    val embedSummary: String
)

data class DiscordEnvSettingsState(
    val integrationEnabled: Boolean = true,
    val botToken: String = "",
    val webhookUrl: String = "",
    val botClientId: String = "1089000000000000000",
    val envBotTokenDetected: Boolean = false,
    val envWebhookUrlDetected: Boolean = false,
    val usingRuntimeOverride: Boolean = false,
    val lastVerificationStatus: String = "Ready — Discord Webhook Bridge & OAuth2 Account-Lock (zero client-side Bot Tokens)"
) {
    val hasValidBotToken: Boolean
        get() = false

    val hasValidWebhookUrl: Boolean
        get() = integrationEnabled && isValidDiscordWebhookUrl(webhookUrl)

    val effectiveConnectionMode: String
        get() = when {
            !integrationEnabled -> "CLOUD_RP_RELAY"
            hasValidWebhookUrl -> "WEBHOOK_PROXY"
            else -> "CLOUD_RP_RELAY"
        }

    fun toDotEnvPreview(maskSecrets: Boolean): String {
        val displayedWebhook = when {
            webhookUrl.isBlank() -> "YOUR_DISCORD_WEBHOOK_URL"
            maskSecrets -> maskSecretValue(webhookUrl)
            else -> webhookUrl
        }
        return buildString {
            appendLine("# Discord Webhook Broadcast Bridge (.env structure)")
            appendLine("# Client-side Discord Bot Tokens and client_secret are strictly prohibited.")
            append("DISCORD_WEBHOOK_URL=$displayedWebhook")
        }
    }

    companion object {
        fun isValidDiscordWebhookUrl(url: String): Boolean {
            val trimmed = url.trim()
            return trimmed.startsWith("https://discord.com/api/webhooks/") ||
                trimmed.startsWith("https://discordapp.com/api/webhooks/") ||
                trimmed.startsWith("https://ptb.discord.com/api/webhooks/") ||
                trimmed.startsWith("https://canary.discord.com/api/webhooks/")
        }

        fun maskSecretValue(raw: String): String {
            val clean = raw.trim()
            if (clean.length <= 10) return "••••••••"
            return "${clean.take(6)}••••••••••••${clean.takeLast(4)}"
        }
    }
}

/**
 * Discord Webhook Broadcast & Roleplay Client for NexusTCG.
 *
 * Strictly zero client-side Discord Bot Tokens or `client_secret`:
 * uses `BuildConfig.DISCORD_WEBHOOK_URL` (or runtime Webhook URL in Settings)
 * plus public OAuth2 server invite URLs.
 */
object DiscordApiService {

    private const val PREFS_NAME = "nexus_tcg_discord_env_settings"
    private const val KEY_INTEGRATION_ENABLED = "DISCORD_INTEGRATION_ENABLED"
    private const val KEY_WEBHOOK_URL = "DISCORD_WEBHOOK_URL"
    private const val KEY_BOT_CLIENT_ID = "DISCORD_CLIENT_ID"
    private const val KEY_HAS_RUNTIME_OVERRIDE = "HAS_RUNTIME_OVERRIDE"
    private const val KEY_BRIDGE_CHANNEL_NAME = "DISCORD_BRIDGE_CHANNEL_NAME"
    private const val KEY_AUTO_BROADCAST_GAME_START = "DISCORD_AUTO_GAME_START"
    private const val KEY_AUTO_BROADCAST_MATCH_WINNER = "DISCORD_AUTO_MATCH_WINNER"
    private const val KEY_AUTO_BROADCAST_DECK_FORGE = "DISCORD_AUTO_DECK_FORGE"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private var prefs: SharedPreferences? = null

    private val _settingsState = MutableStateFlow(createInitialState())
    val settingsState: StateFlow<DiscordEnvSettingsState> = _settingsState.asStateFlow()

    private val _bridgeConfig = MutableStateFlow(DiscordWebhookBridgeConfig())
    val bridgeConfig: StateFlow<DiscordWebhookBridgeConfig> = _bridgeConfig.asStateFlow()

    private val _broadcastHistory = MutableStateFlow<List<DiscordWebhookBroadcastRecord>>(emptyList())
    val broadcastHistory: StateFlow<List<DiscordWebhookBroadcastRecord>> = _broadcastHistory.asStateFlow()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private fun readBuildConfigBotToken(): String {
        // Client-side Discord Bot Tokens are strictly prohibited.
        return ""
    }

    private fun readBuildConfigWebhookUrl(): String {
        val raw = runCatching { BuildConfig.DISCORD_WEBHOOK_URL }.getOrDefault("").trim()
        return if (
            raw.isEmpty() ||
            raw.equals("null", ignoreCase = true) ||
            raw.startsWith("YOUR_") ||
            !DiscordEnvSettingsState.isValidDiscordWebhookUrl(raw)
        ) {
            ""
        } else {
            raw
        }
    }

    private fun createInitialState(): DiscordEnvSettingsState {
        val envWebhook = readBuildConfigWebhookUrl()
        return DiscordEnvSettingsState(
            integrationEnabled = true,
            botToken = "",
            webhookUrl = envWebhook,
            botClientId = "1089000000000000000",
            envBotTokenDetected = false,
            envWebhookUrlDetected = envWebhook.isNotEmpty(),
            usingRuntimeOverride = false
        )
    }

    fun init(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        val envWebhook = readBuildConfigWebhookUrl()

        val enabled = sharedPrefs.getBoolean(KEY_INTEGRATION_ENABLED, true)
        val hasOverride = sharedPrefs.getBoolean(KEY_HAS_RUNTIME_OVERRIDE, false)
        val storedWebhook = sharedPrefs.getString(KEY_WEBHOOK_URL, null)?.trim().orEmpty()
        val storedClientId = sharedPrefs.getString(KEY_BOT_CLIENT_ID, "1089000000000000000")
            ?.trim()
            .orEmpty()
            .ifBlank { "1089000000000000000" }

        val activeWebhook = if (hasOverride) storedWebhook else envWebhook

        _settingsState.value = DiscordEnvSettingsState(
            integrationEnabled = enabled,
            botToken = "",
            webhookUrl = activeWebhook,
            botClientId = storedClientId,
            envBotTokenDetected = false,
            envWebhookUrlDetected = envWebhook.isNotEmpty(),
            usingRuntimeOverride = hasOverride,
            lastVerificationStatus = if (!enabled) {
                "Discord Webhook integration is toggled OFF (Cloud RP Relay only)."
            } else if (hasOverride && activeWebhook.isNotEmpty()) {
                "Active: Using custom Discord Webhook URL from Settings (zero client-side Bot Tokens)."
            } else if (envWebhook.isNotEmpty()) {
                "Active: Loaded DISCORD_WEBHOOK_URL from .env BuildConfig."
            } else {
                "Cloud RP Relay active — configure DISCORD_WEBHOOK_URL for live channel broadcasts."
            }
        )

        val storedChannel = sharedPrefs.getString(KEY_BRIDGE_CHANNEL_NAME, "#duel-broadcasts")
            ?.trim()
            .orEmpty()
            .ifBlank { "#duel-broadcasts" }
        _bridgeConfig.value = DiscordWebhookBridgeConfig(
            designatedChannelName = if (storedChannel.startsWith("#")) storedChannel else "#$storedChannel",
            autoBroadcastGameStart = sharedPrefs.getBoolean(KEY_AUTO_BROADCAST_GAME_START, true),
            autoBroadcastMatchWinner = sharedPrefs.getBoolean(KEY_AUTO_BROADCAST_MATCH_WINNER, true),
            autoBroadcastDeckOnForge = sharedPrefs.getBoolean(KEY_AUTO_BROADCAST_DECK_FORGE, false)
        )
    }

    fun updateBridgeConfig(
        designatedChannelName: String = _bridgeConfig.value.designatedChannelName,
        autoBroadcastGameStart: Boolean = _bridgeConfig.value.autoBroadcastGameStart,
        autoBroadcastMatchWinner: Boolean = _bridgeConfig.value.autoBroadcastMatchWinner,
        autoBroadcastDeckOnForge: Boolean = _bridgeConfig.value.autoBroadcastDeckOnForge
    ) {
        val rawChan = designatedChannelName.trim().ifBlank { "#duel-broadcasts" }
        val normalizedChan = if (rawChan.startsWith("#")) rawChan else "#$rawChan"
        val updated = DiscordWebhookBridgeConfig(
            designatedChannelName = normalizedChan,
            autoBroadcastGameStart = autoBroadcastGameStart,
            autoBroadcastMatchWinner = autoBroadcastMatchWinner,
            autoBroadcastDeckOnForge = autoBroadcastDeckOnForge
        )
        prefs?.edit()?.apply {
            putString(KEY_BRIDGE_CHANNEL_NAME, updated.designatedChannelName)
            putBoolean(KEY_AUTO_BROADCAST_GAME_START, updated.autoBroadcastGameStart)
            putBoolean(KEY_AUTO_BROADCAST_MATCH_WINNER, updated.autoBroadcastMatchWinner)
            putBoolean(KEY_AUTO_BROADCAST_DECK_FORGE, updated.autoBroadcastDeckOnForge)
            apply()
        }
        _bridgeConfig.value = updated
    }

    fun setIntegrationEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_INTEGRATION_ENABLED, enabled)?.apply()
        val current = _settingsState.value
        _settingsState.value = current.copy(
            integrationEnabled = enabled,
            lastVerificationStatus = if (enabled) {
                "Discord Bot & Webhook integration ENABLED (${current.copy(integrationEnabled = true).effectiveConnectionMode})."
            } else {
                "Discord Bot & Webhook integration PAUSED — operating in local/Cloud RP Relay mode."
            }
        )
    }

    /**
     * Saves user-provided `DISCORD_WEBHOOK_URL` and optional public `DISCORD_CLIENT_ID`.
     * Never stores any client-side Discord Bot Token or client_secret.
     */
    fun updateEnvCredentials(
        botToken: String,
        webhookUrl: String,
        botClientId: String = _settingsState.value.botClientId,
        integrationEnabled: Boolean = _settingsState.value.integrationEnabled
    ) {
        val cleanWebhook = webhookUrl.trim()
            .removePrefix("DISCORD_WEBHOOK_URL=")
            .trim()
            .removeSurrounding("\"")
        val cleanClientId = botClientId.filter { it.isDigit() }.ifEmpty { "1089000000000000000" }

        prefs?.edit()?.apply {
            putBoolean(KEY_INTEGRATION_ENABLED, integrationEnabled)
            putBoolean(KEY_HAS_RUNTIME_OVERRIDE, true)
            putString(KEY_WEBHOOK_URL, cleanWebhook)
            putString(KEY_BOT_CLIENT_ID, cleanClientId)
            apply()
        }

        val envWebhook = readBuildConfigWebhookUrl()
        val nextState = DiscordEnvSettingsState(
            integrationEnabled = integrationEnabled,
            botToken = "",
            webhookUrl = cleanWebhook,
            botClientId = cleanClientId,
            envBotTokenDetected = false,
            envWebhookUrlDetected = envWebhook.isNotEmpty(),
            usingRuntimeOverride = true,
            lastVerificationStatus = ""
        )
        val statusMsg = buildString {
            append("Saved Webhook configuration -> Mode: ${nextState.effectiveConnectionMode}.")
            if (cleanWebhook.isNotEmpty() && !DiscordEnvSettingsState.isValidDiscordWebhookUrl(cleanWebhook)) {
                append(" Note: Webhook URL should start with https://discord.com/api/webhooks/...")
            }
        }
        _settingsState.value = nextState.copy(lastVerificationStatus = statusMsg)
    }

    /**
     * Resets runtime overrides back to the compile-time `.env` / `BuildConfig` Webhook default.
     */
    fun resetToBuildConfigEnvDefaults() {
        val envWebhook = readBuildConfigWebhookUrl()
        prefs?.edit()?.apply {
            putBoolean(KEY_HAS_RUNTIME_OVERRIDE, false)
            remove(KEY_WEBHOOK_URL)
            apply()
        }
        _settingsState.value = _settingsState.value.copy(
            botToken = "",
            webhookUrl = envWebhook,
            envBotTokenDetected = false,
            envWebhookUrlDetected = envWebhook.isNotEmpty(),
            usingRuntimeOverride = false,
            lastVerificationStatus = "Reset to .env BuildConfig defaults (Webhook: ${if (envWebhook.isNotEmpty()) "Detected" else "Unset"})."
        )
    }

    fun getConfiguredBotToken(): String = ""

    fun getConfiguredWebhookUrl(): String {
        val state = _settingsState.value
        return if (state.integrationEnabled && DiscordEnvSettingsState.isValidDiscordWebhookUrl(state.webhookUrl)) {
            state.webhookUrl
        } else {
            ""
        }
    }

    fun isIntegrationEnabled(): Boolean = _settingsState.value.integrationEnabled
    fun isBotTokenConfigured(): Boolean = getConfiguredBotToken().isNotEmpty()
    fun isWebhookConfigured(): Boolean = getConfiguredWebhookUrl().isNotEmpty()

    fun activeConnectionMode(): String = _settingsState.value.effectiveConnectionMode

    fun buildBotInviteUrl(clientId: String = _settingsState.value.botClientId): String {
        val cleanId = clientId.filter { it.isDigit() }.ifEmpty { "1089000000000000000" }
        // Permissions: View Channels + Send Messages + Manage Webhooks + Embed Links + Read Message History (536964096)
        return "https://discord.com/oauth2/authorize?client_id=$cleanId&permissions=536964096&scope=bot%20applications.commands"
    }

    /**
     * Verifies the currently configured `DISCORD_WEBHOOK_URL` via GET request
     * and updates `lastVerificationStatus`. Zero client-side Bot Tokens are used.
     */
    suspend fun verifyDiscordCredentials(): String = withContext(Dispatchers.IO) {
        val state = _settingsState.value
        if (!state.integrationEnabled) {
            val msg = "Discord Webhook Integration is currently toggled OFF. Enable it first to test live connectivity."
            _settingsState.value = state.copy(lastVerificationStatus = msg)
            return@withContext msg
        }

        val webhook = getConfiguredWebhookUrl()
        if (webhook.isEmpty()) {
            val msg = "No valid DISCORD_WEBHOOK_URL configured. Operating in Cloud RP Relay mode (zero client-side Bot Tokens)."
            _settingsState.value = state.copy(lastVerificationStatus = msg)
            return@withContext msg
        }

        val webhookCheck = runCatching {
            val req = Request.Builder()
                .url(webhook)
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val obj = JSONObject(resp.body?.string().orEmpty())
                    val hookName = obj.optString("name", "RP Webhook")
                    "DISCORD_WEBHOOK_URL Verified ($hookName)"
                } else {
                    "DISCORD_WEBHOOK_URL HTTP ${resp.code}"
                }
            }
        }.getOrElse { e ->
            "DISCORD_WEBHOOK_URL Error (${e.javaClass.simpleName})"
        }

        _settingsState.value = _settingsState.value.copy(lastVerificationStatus = webhookCheck)
        webhookCheck
    }

    /**
     * Sends an in-character roleplay message or TCG duel action to Discord.
     */
    suspend fun sendCharacterRoleplayMessage(
        channelId: String,
        character: RpCharacterPersona?,
        senderFallbackName: String,
        content: String,
        tcgActionSummary: String,
        baseVariantId: String
    ): String = withContext(Dispatchers.IO) {
        if (!isIntegrationEnabled()) {
            return@withContext "SYNCED_RP_CHANNEL"
        }

        val webhookUrl = getConfiguredWebhookUrl()
        val botToken = getConfiguredBotToken()

        val displayUsername = if (character != null) {
            "${character.characterName} [${character.characterTitle}]".take(80)
        } else {
            senderFallbackName.take(80)
        }

        val embedObj = JSONObject().apply {
            put(
                "title",
                if (character != null) {
                    "${character.characterName} • ${character.factionOrArchetype} (${character.currentLp21} LP)"
                } else {
                    "NexusTCG Roleplay Relay • $baseVariantId"
                }
            )
            val embedDesc = buildString {
                if (tcgActionSummary.isNotBlank()) {
                    appendLine("**TCG Engine Action:** $tcgActionSummary")
                }
                if (character != null) {
                    appendLine("**Signature Card:** ${character.signatureCardName} (`c${character.signatureCodeString21}`)")
                }
                append("**Engine Variant:** `$baseVariantId`")
            }
            put("description", embedDesc)
            put("color", if (baseVariantId == "EDOPRO_CUSTOM_FORK") 0xFFB300 else 0x00E5FF)
        }

        // 1. Try Webhook Character Proxy (best for acting like RP characters in Discord servers)
        if (webhookUrl.isNotEmpty()) {
            val payload = JSONObject().apply {
                put("username", displayUsername)
                put("content", content)
                put("embeds", JSONArray().put(embedObj))
            }
            val urlWithWait = if (webhookUrl.contains("?")) "$webhookUrl&wait=true" else "$webhookUrl?wait=true"
            val req = Request.Builder()
                .url(urlWithWait)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val delivered = runCatching {
                httpClient.newCall(req).execute().use { resp -> resp.isSuccessful }
            }.getOrDefault(false)

            if (delivered) {
                return@withContext "DELIVERED_WEBHOOK"
            }
        }

        // Cloud RP Relay fallback (Webhook-only + Firestore sync; zero client-side Bot Tokens)
        "SYNCED_RP_CHANNEL"
    }

    // --- Discord Webhook Bridge: Game Start, Deck Summary & Match Winner Broadcasters ---

    private fun currentIsoUtcTimestamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date())
    }

    private fun currentDisplayClock(): String {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
        return formatter.format(Date())
    }

    private fun buildEmbedField(name: String, value: String, inline: Boolean = true): JSONObject {
        return JSONObject().apply {
            put("name", name.take(256))
            put("value", value.ifBlank { "—" }.take(1024))
            put("inline", inline)
        }
    }

    private fun appendBroadcastRecord(record: DiscordWebhookBroadcastRecord) {
        _broadcastHistory.value = (listOf(record) + _broadcastHistory.value).take(25)
    }

    /**
     * Dispatches a structured Discord Webhook JSON payload (`username`, `content`, `embeds`) to the
     * configured `DISCORD_WEBHOOK_URL` (`?wait=true`) and records the HTTP delivery result.
     */
    private suspend fun dispatchWebhookEmbed(
        eventType: DiscordBroadcastEventType,
        channelLabel: String,
        webhookUsername: String,
        contentLine: String,
        embedTitle: String,
        embedDescription: String,
        fields: List<JSONObject>,
        footerText: String,
        summaryLine: String
    ): DiscordWebhookBroadcastRecord = withContext(Dispatchers.IO) {
        val normalizedChannel = channelLabel.trim().ifBlank { _bridgeConfig.value.designatedChannelName }
        val clock = currentDisplayClock()

        if (!isIntegrationEnabled()) {
            val pausedRecord = DiscordWebhookBroadcastRecord(
                eventType = eventType,
                channelLabel = normalizedChannel,
                title = embedTitle,
                summary = summaryLine,
                deliveryStatus = "PAUSED",
                timestampLabel = clock
            )
            appendBroadcastRecord(pausedRecord)
            return@withContext pausedRecord
        }

        val webhookUrl = getConfiguredWebhookUrl()
        if (webhookUrl.isEmpty()) {
            val unsetRecord = DiscordWebhookBroadcastRecord(
                eventType = eventType,
                channelLabel = normalizedChannel,
                title = embedTitle,
                summary = summaryLine,
                deliveryStatus = "WEBHOOK_UNCONFIGURED",
                timestampLabel = clock
            )
            appendBroadcastRecord(unsetRecord)
            return@withContext unsetRecord
        }

        val fieldsArray = JSONArray().apply {
            fields.forEach { put(it) }
        }
        val embedObj = JSONObject().apply {
            put("title", embedTitle.take(256))
            put("description", embedDescription.take(4000))
            put("color", eventType.embedColorInt)
            put("fields", fieldsArray)
            put("timestamp", currentIsoUtcTimestamp())
            put("footer", JSONObject().put("text", footerText.take(2048)))
        }
        val payload = JSONObject().apply {
            put("username", webhookUsername.take(80))
            put("content", contentLine.take(1900))
            put("embeds", JSONArray().put(embedObj))
        }

        val urlWithWait = if (webhookUrl.contains("?")) "$webhookUrl&wait=true" else "$webhookUrl?wait=true"
        val request = Request.Builder()
            .url(urlWithWait)
            .header("Content-Type", "application/json")
            .header("User-Agent", "NexusTCG-DiscordBridge (https://nexustcg.io, 2.0)")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        var responseCode: Int? = null
        val status = runCatching {
            httpClient.newCall(request).execute().use { resp ->
                responseCode = resp.code
                if (resp.isSuccessful) {
                    "DELIVERED_WEBHOOK"
                } else {
                    "HTTP_${resp.code}"
                }
            }
        }.getOrElse { error ->
            "NETWORK_ERROR (${error.javaClass.simpleName})"
        }

        val record = DiscordWebhookBroadcastRecord(
            eventType = eventType,
            channelLabel = normalizedChannel,
            title = embedTitle,
            summary = summaryLine,
            deliveryStatus = status,
            httpCode = responseCode,
            timestampLabel = clock
        )
        appendBroadcastRecord(record)
        record
    }

    /**
     * Broadcasts a **Game Start** event embed to the designated Discord channel via Webhook URL.
     */
    suspend fun broadcastGameStartEvent(
        matchTitle: String,
        baseVariantId: String,
        baseVariantName: String,
        hostName: String,
        connectedClients: List<String>,
        startingLife: Long,
        resourceName: String,
        activeDeckName: String? = null,
        designatedChannelLabel: String = _bridgeConfig.value.designatedChannelName
    ): DiscordWebhookBroadcastRecord {
        val clientsText = connectedClients.ifEmpty { listOf(hostName) }.joinToString(", ")
        val deckText = activeDeckName?.takeIf { it.isNotBlank() } ?: "Standard Variant Pool"
        val fields = listOf(
            buildEmbedField("Channel", designatedChannelLabel, inline = true),
            buildEmbedField("Host Commander", hostName, inline = true),
            buildEmbedField("Engine / Ruleset", "$baseVariantName (`$baseVariantId`)", inline = false),
            buildEmbedField("Starting Life & Resource", "$startingLife LP/Life • $resourceName", inline = true),
            buildEmbedField("Featured Deck", deckText, inline = true),
            buildEmbedField("Connected Clients", clientsText, inline = false)
        )
        val summary = "Match '$matchTitle' started ($baseVariantId • $startingLife LP) with ${connectedClients.size.coerceAtLeast(1)} client(s)"
        return dispatchWebhookEmbed(
            eventType = DiscordBroadcastEventType.GAME_START,
            channelLabel = designatedChannelLabel,
            webhookUsername = "NexusTCG Match Announcer",
            contentLine = "**GAME START** in $designatedChannelLabel — **$matchTitle** is now live!",
            embedTitle = "Game Started: $matchTitle",
            embedDescription = "A new multi-client TCG match has launched and is synced on **$baseVariantName**.",
            fields = fields,
            footerText = "NexusTCG Discord Bridge • Game Start Event",
            summaryLine = summary
        )
    }

    /**
     * Broadcasts a structured **Deck Summary** embed (archetype, card pool count, ace card, 21-digit
     * stats/code string, and ability text) to the designated Discord channel via Webhook URL.
     */
    suspend fun broadcastDeckSummary(
        deck: CustomDeck,
        ownerName: String,
        designatedChannelLabel: String = _bridgeConfig.value.designatedChannelName
    ): DiscordWebhookBroadcastRecord {
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(deck.baseVariantId)
        val catalogCount = BuiltInTcgCatalog.cardsForVariant(deck.baseVariantId).size
        val totalCards = (deck.cardIds.size.takeIf { it > 0 } ?: catalogCount) + 1
        val atkDisplay = BigInt21Engine.formatWithCommas(deck.customAttack21.ifBlank { deck.customCardAttack.toString() })
        val defDisplay = BigInt21Engine.formatWithCommas(deck.customDefense21.ifBlank { deck.customCardDefense.toString() })
        val codeDisplay = deck.customCodeString21.ifBlank { "89631139" }

        val fields = listOf(
            buildEmbedField("Channel", designatedChannelLabel, inline = true),
            buildEmbedField("Duelist", ownerName, inline = true),
            buildEmbedField("Engine Variant", "${baseSpec.name} (`${deck.baseVariantId}`)", inline = false),
            buildEmbedField("Strategy / Format", deck.strategyTag.ifBlank { "Custom Build" }, inline = true),
            buildEmbedField("Deck Size", "$totalCards Core Cards + Signature Ace", inline = true),
            buildEmbedField("Signature Ace Card", "${deck.customCardName} (Cost/Lv ${deck.customCardCost})", inline = false),
            buildEmbedField("ATK / DEF", "$atkDisplay / $defDisplay", inline = true),
            buildEmbedField("Script Code", "`c$codeDisplay`", inline = true),
            buildEmbedField("Signature Effect", deck.customCardAbility.ifBlank { "Standard ruleset card effect." }, inline = false)
        )
        val summary = "Deck '${deck.name}' (${deck.baseVariantId}) • Ace: ${deck.customCardName} ($atkDisplay/$defDisplay)"
        return dispatchWebhookEmbed(
            eventType = DiscordBroadcastEventType.DECK_SUMMARY,
            channelLabel = designatedChannelLabel,
            webhookUsername = "NexusTCG Deck Archivist",
            contentLine = "**DECK SUMMARY** posted to $designatedChannelLabel — **${deck.name}** by $ownerName",
            embedTitle = "Deck Summary: ${deck.name}",
            embedDescription = "Official decklist telemetry and signature card profile for **${baseSpec.name}**.",
            fields = fields,
            footerText = "NexusTCG Discord Bridge • Deck Summary (${deck.customCodeVarExpr21.take(48)})",
            summaryLine = summary
        )
    }

    /**
     * Broadcasts a **Match Winner** victory embed (champion, runner-up, final life totals, turns
     * played, engine variant, and winning deck) to the designated Discord channel via Webhook URL.
     */
    suspend fun broadcastMatchWinner(
        matchTitle: String,
        winnerName: String,
        runnerUpName: String,
        baseVariantId: String,
        baseVariantName: String,
        finalHostLife: Long,
        finalOpponentLife: Long,
        totalTurns: Long,
        winningDeckName: String? = null,
        designatedChannelLabel: String = _bridgeConfig.value.designatedChannelName
    ): DiscordWebhookBroadcastRecord {
        val deckText = winningDeckName?.takeIf { it.isNotBlank() } ?: "Active Variant Deck"
        val fields = listOf(
            buildEmbedField("Channel", designatedChannelLabel, inline = true),
            buildEmbedField("Champion", "**$winnerName**", inline = true),
            buildEmbedField("Opponent", runnerUpName.ifBlank { "Connected Peer" }, inline = true),
            buildEmbedField("Final Score (LP/Life)", "$finalHostLife vs $finalOpponentLife", inline = true),
            buildEmbedField("Turns Played", "$totalTurns Turn(s)", inline = true),
            buildEmbedField("Winning Deck", deckText, inline = true),
            buildEmbedField("Engine / Protocol", "$baseVariantName (`$baseVariantId`)", inline = false)
        )
        val summary = "Winner: $winnerName defeated $runnerUpName in '$matchTitle' ($finalHostLife vs $finalOpponentLife • $totalTurns turns)"
        return dispatchWebhookEmbed(
            eventType = DiscordBroadcastEventType.MATCH_WINNER,
            channelLabel = designatedChannelLabel,
            webhookUsername = "NexusTCG Tournament Arbiter",
            contentLine = "**MATCH WINNER** in $designatedChannelLabel — **$winnerName** wins **$matchTitle**!",
            embedTitle = "Match Victory: $winnerName",
            embedDescription = "**$winnerName** secured victory in **$matchTitle** after $totalTurns turns on **$baseVariantName**.",
            fields = fields,
            footerText = "NexusTCG Discord Bridge • Official Match Result",
            summaryLine = summary
        )
    }
}
