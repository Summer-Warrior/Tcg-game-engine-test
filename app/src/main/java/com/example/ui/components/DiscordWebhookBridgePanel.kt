package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CustomDeck
import com.example.data.DiscordBroadcastEventType
import com.example.data.DiscordEnvSettingsState
import com.example.data.DiscordWebhookBridgeConfig
import com.example.data.DiscordWebhookBroadcastRecord
import com.example.data.MatchSession
import com.example.ui.theme.ArcaneViolet
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.TextSecondary

/**
 * Discord Webhook Bridge Control Center for broadcasting:
 * 1. **Game Start Events**
 * 2. **Deck Summaries** (archetype, card pool, ace card, 21-digit stats/code string)
 * 3. **Match Winners** (champion, runner-up, final score, turn count)
 * to the user's designated Discord channel via `DISCORD_WEBHOOK_URL`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscordWebhookBridgePanel(
    discordEnvSettings: DiscordEnvSettingsState,
    bridgeConfig: DiscordWebhookBridgeConfig,
    broadcastHistory: List<DiscordWebhookBroadcastRecord>,
    activeMatch: MatchSession?,
    availableDecks: List<CustomDeck>,
    activeServerChannelName: String?,
    onUpdateBridgeConfig: (
        designatedChannelName: String,
        autoGameStart: Boolean,
        autoMatchWinner: Boolean,
        autoDeckOnForge: Boolean
    ) -> Unit,
    onBroadcastGameStart: (MatchSession?, CustomDeck?) -> Unit,
    onBroadcastDeckSummary: (CustomDeck) -> Unit,
    onBroadcastMatchWinner: (MatchSession?, String?, CustomDeck?) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var channelInput by rememberSaveable(bridgeConfig.designatedChannelName) {
        mutableStateOf(bridgeConfig.designatedChannelName)
    }
    var selectedDeckId by rememberSaveable { mutableStateOf(availableDecks.firstOrNull()?.id ?: "") }
    var showWinnerDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(bridgeConfig.designatedChannelName) {
        channelInput = bridgeConfig.designatedChannelName
    }
    LaunchedEffect(availableDecks) {
        if (selectedDeckId.isBlank() || availableDecks.none { it.id == selectedDeckId }) {
            selectedDeckId = availableDecks.firstOrNull()?.id ?: ""
        }
    }

    val selectedDeck = remember(availableDecks, selectedDeckId) {
        availableDecks.firstOrNull { it.id == selectedDeckId } ?: availableDecks.firstOrNull()
    }
    val webhookReady = discordEnvSettings.hasValidWebhookUrl
    val integrationEnabled = discordEnvSettings.integrationEnabled

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.2.dp,
                color = if (webhookReady) ElectricCyan.copy(alpha = 0.65f) else RadiantGold.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("discord_webhook_bridge_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            ElectricCyan.copy(alpha = 0.14f),
                            ArcaneViolet.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header & Live Webhook Readiness Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.18f))
                            .border(1.dp, ElectricCyan, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = "Discord Webhook Match & Deck Bridge",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Discord Webhook Broadcast Bridge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Broadcast Game Start, Deck Summaries & Match Winners to ${bridgeConfig.designatedChannelName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(50),
                    color = when {
                        !integrationEnabled -> CrimsonAlert.copy(alpha = 0.18f)
                        webhookReady -> EmeraldReady.copy(alpha = 0.18f)
                        else -> RadiantGold.copy(alpha = 0.18f)
                    },
                    modifier = Modifier.border(
                        1.dp,
                        when {
                            !integrationEnabled -> CrimsonAlert
                            webhookReady -> EmeraldReady
                            else -> RadiantGold
                        },
                        RoundedCornerShape(50)
                    )
                ) {
                    Text(
                        text = when {
                            !integrationEnabled -> "PAUSED"
                            webhookReady -> "WEBHOOK LIVE"
                            else -> "SET WEBHOOK"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            !integrationEnabled -> CrimsonAlert
                            webhookReady -> EmeraldReady
                            else -> RadiantGold
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Webhook URL Status Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (webhookReady) {
                                "Webhook Target: ${DiscordEnvSettingsState.maskSecretValue(discordEnvSettings.webhookUrl)}"
                            } else {
                                "DISCORD_WEBHOOK_URL not set — configure it in AI Studio Secrets or Settings"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (webhookReady) EmeraldReady else RadiantGold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Channel Tag: ${bridgeConfig.designatedChannelName} • Active Match: ${activeMatch?.title ?: "Quick Arena Preview"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onOpenSettings,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("bridge_configure_webhook_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure Webhook URL",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (webhookReady) "Webhook" else "Configure",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // 2. Designated Channel Picker & Auto-Broadcast Toggles
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = channelInput,
                        onValueChange = { channelInput = it.take(40) },
                        label = { Text("Designated Discord Channel") },
                        placeholder = { Text("#duel-broadcasts") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bridge_designated_channel_input")
                    )
                    Button(
                        onClick = {
                            onUpdateBridgeConfig(
                                channelInput,
                                bridgeConfig.autoBroadcastGameStart,
                                bridgeConfig.autoBroadcastMatchWinner,
                                bridgeConfig.autoBroadcastDeckOnForge
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("bridge_save_channel_button")
                    ) {
                        Text("Set", fontWeight = FontWeight.Bold)
                    }
                }

                // Quick channel preset chips (sync with active Discord server channel if present)
                val quickChannels = remember(activeServerChannelName) {
                    listOfNotNull(
                        activeServerChannelName?.takeIf { it.isNotBlank() },
                        "#duel-broadcasts",
                        "#match-results",
                        "#deck-lists"
                    ).distinct()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickChannels.forEach { preset ->
                        FilterChip(
                            selected = bridgeConfig.designatedChannelName.equals(preset, ignoreCase = true),
                            onClick = {
                                channelInput = preset
                                onUpdateBridgeConfig(
                                    preset,
                                    bridgeConfig.autoBroadcastGameStart,
                                    bridgeConfig.autoBroadcastMatchWinner,
                                    bridgeConfig.autoBroadcastDeckOnForge
                                )
                            },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("bridge_channel_preset_${preset.removePrefix("#")}")
                        )
                    }
                }

                // Auto-Broadcast Switches
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianElevated.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BridgeAutoToggleRow(
                            label = "Auto-Broadcast Game Start on New Arena",
                            checked = bridgeConfig.autoBroadcastGameStart,
                            testTag = "bridge_auto_game_start_switch",
                            onCheckedChange = { checked ->
                                onUpdateBridgeConfig(
                                    bridgeConfig.designatedChannelName,
                                    checked,
                                    bridgeConfig.autoBroadcastMatchWinner,
                                    bridgeConfig.autoBroadcastDeckOnForge
                                )
                            }
                        )
                        BridgeAutoToggleRow(
                            label = "Auto-Broadcast Match Winner on Victory",
                            checked = bridgeConfig.autoBroadcastMatchWinner,
                            testTag = "bridge_auto_match_winner_switch",
                            onCheckedChange = { checked ->
                                onUpdateBridgeConfig(
                                    bridgeConfig.designatedChannelName,
                                    bridgeConfig.autoBroadcastGameStart,
                                    checked,
                                    bridgeConfig.autoBroadcastDeckOnForge
                                )
                            }
                        )
                        BridgeAutoToggleRow(
                            label = "Auto-Broadcast Deck Summary When Forged",
                            checked = bridgeConfig.autoBroadcastDeckOnForge,
                            testTag = "bridge_auto_deck_forge_switch",
                            onCheckedChange = { checked ->
                                onUpdateBridgeConfig(
                                    bridgeConfig.designatedChannelName,
                                    bridgeConfig.autoBroadcastGameStart,
                                    bridgeConfig.autoBroadcastMatchWinner,
                                    checked
                                )
                            }
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderSubtle)

            // 3. Deck Selector & 1-Tap Event Broadcast Actions
            if (availableDecks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Select Deck for Summary / Match Broadcast:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableDecks.forEach { deck ->
                            FilterChip(
                                selected = deck.id == selectedDeck?.id,
                                onClick = { selectedDeckId = deck.id },
                                label = {
                                    Text(
                                        text = "${deck.name.take(24)} (${deck.baseVariantId.substringBefore("_")})",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("bridge_select_deck_${deck.id}")
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onBroadcastGameStart(activeMatch, selectedDeck) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("bridge_broadcast_game_start_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Broadcast Game Start", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        selectedDeck?.let { onBroadcastDeckSummary(it) }
                    },
                    enabled = selectedDeck != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ArcaneViolet,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("bridge_broadcast_deck_summary_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Broadcast Deck Summary", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (activeMatch != null && activeMatch.winnerName.isNotBlank()) {
                            onBroadcastMatchWinner(activeMatch, activeMatch.winnerName, selectedDeck)
                        } else {
                            showWinnerDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RadiantGold,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("bridge_broadcast_match_winner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeMatch?.winnerName?.isNotBlank() == true) {
                            "Broadcast Winner (${activeMatch.winnerName.take(12)})"
                        } else {
                            "Declare & Broadcast Winner"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4. Recent Webhook Dispatch Log
            if (broadcastHistory.isNotEmpty()) {
                HorizontalDivider(color = BorderSubtle)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Recent Webhook Broadcasts (${broadcastHistory.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    broadcastHistory.take(5).forEach { record ->
                        val eventAccent = Color(0xFF000000L or record.eventType.embedColorInt.toLong())
                        val delivered = record.deliveryStatus == "DELIVERED_WEBHOOK"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianElevated)
                                .border(
                                    1.dp,
                                    if (delivered) EmeraldReady.copy(alpha = 0.45f) else eventAccent.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (delivered) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                contentDescription = record.deliveryStatus,
                                tint = if (delivered) EmeraldReady else RadiantGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = record.eventType.badgeCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = eventAccent
                                    )
                                    Text(
                                        text = "${record.channelLabel} • ${record.timestampLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = record.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Status: ${record.deliveryStatus}" +
                                        (record.httpCode?.let { " (HTTP $it)" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (delivered) EmeraldReady else RadiantGold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWinnerDialog) {
        val defaultHost = activeMatch?.hostName?.ifBlank { "Host Commander" } ?: "Host Commander"
        val defaultOpp = activeMatch?.connectedClients?.firstOrNull { it != defaultHost }
            ?: "Peer Challenger"
        var customWinnerName by rememberSaveable { mutableStateOf(defaultHost) }

        AlertDialog(
            onDismissRequest = { showWinnerDialog = false },
            title = { Text("Broadcast Match Winner to Discord") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose or enter the champion to announce in ${bridgeConfig.designatedChannelName} via your Discord Webhook:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = customWinnerName == defaultHost,
                            onClick = { customWinnerName = defaultHost },
                            label = { Text(defaultHost.take(18)) }
                        )
                        FilterChip(
                            selected = customWinnerName == defaultOpp,
                            onClick = { customWinnerName = defaultOpp },
                            label = { Text(defaultOpp.take(18)) }
                        )
                    }
                    OutlinedTextField(
                        value = customWinnerName,
                        onValueChange = { customWinnerName = it.take(40) },
                        label = { Text("Match Winner Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bridge_winner_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWinnerDialog = false
                        onBroadcastMatchWinner(
                            activeMatch,
                            customWinnerName.trim().ifBlank { defaultHost },
                            selectedDeck
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RadiantGold,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("bridge_confirm_winner_broadcast_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Broadcast Victory", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWinnerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BridgeAutoToggleRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
