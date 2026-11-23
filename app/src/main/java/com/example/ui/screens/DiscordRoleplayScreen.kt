package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.CustomDeck
import com.example.data.DiscordApiService
import com.example.data.DiscordEnvSettingsState
import com.example.data.DiscordRpMessage
import com.example.data.DiscordServerHub
import com.example.data.DiscordWebhookBridgeConfig
import com.example.data.DiscordWebhookBroadcastRecord
import com.example.data.MatchSession
import com.example.data.RpCharacterPersona
import com.example.ui.components.DiscordWebhookBridgePanel
import com.example.ui.theme.ArcaneViolet
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.UiState

private enum class MessageReviewFilter(val label: String) {
    ALL("All Chat"),
    UNREVIEWED("Needs Review"),
    CHARACTER_RP("Character RP"),
    TCG_ACTIONS("TCG Actions")
}

@Composable
fun DiscordRoleplayScreen(
    selectedBaseVariantId: String,
    discordServersState: UiState<List<DiscordServerHub>>,
    rpCharactersState: UiState<List<RpCharacterPersona>>,
    discordMessagesState: UiState<List<DiscordRpMessage>>,
    selectedDiscordServerId: String?,
    onSelectServer: (DiscordServerHub) -> Unit,
    onJoinOrAddServer: (
        guildId: String,
        serverName: String,
        channelId: String,
        channelName: String,
        baseVariantId: String,
        rpLoreSetting: String
    ) -> Unit,
    onDiscoverBotGuilds: () -> Unit,
    onReviewAndSyncMessages: (DiscordServerHub) -> Unit,
    onToggleServerConnection: (DiscordServerHub) -> Unit,
    onDeleteServer: (String) -> Unit,
    onCreateCharacter: (
        characterName: String,
        characterTitle: String,
        factionOrArchetype: String,
        baseVariantId: String,
        signatureCardName: String,
        signatureCodeString21: String,
        currentLp21: String,
        personalityPrompt: String,
        openingCatchphrase: String,
        summonChant: String
    ) -> Unit,
    onActivateCharacter: (RpCharacterPersona) -> Unit,
    onUpdateCharacterLp21: (RpCharacterPersona, String) -> Unit,
    onDeleteCharacter: (String) -> Unit,
    onSendRoleplayMessage: (
        server: DiscordServerHub,
        character: RpCharacterPersona?,
        actAsCharacter: Boolean,
        content: String,
        tcgActionSummary: String,
        messageType: String
    ) -> Unit,
    onTriggerCharacterQuickAct: (
        server: DiscordServerHub,
        character: RpCharacterPersona,
        actType: String
    ) -> Unit,
    onToggleMessageReviewed: (DiscordRpMessage) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    discordEnvSettings: DiscordEnvSettingsState = DiscordApiService.settingsState.value,
    discordBridgeConfig: DiscordWebhookBridgeConfig = DiscordApiService.bridgeConfig.value,
    discordBroadcastHistory: List<DiscordWebhookBroadcastRecord> = emptyList(),
    activeMatch: MatchSession? = null,
    availableDecks: List<CustomDeck> = emptyList(),
    onUpdateBridgeConfig: (String, Boolean, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    onBroadcastGameStart: (MatchSession?, CustomDeck?) -> Unit = { _, _ -> },
    onBroadcastDeckSummary: (CustomDeck) -> Unit = {},
    onBroadcastMatchWinner: (MatchSession?, String?, CustomDeck?) -> Unit = { _, _, _ -> }
) {
    val clipboardManager = LocalClipboardManager.current
    val servers = (discordServersState as? UiState.Success)?.data.orEmpty()
    val characters = (rpCharactersState as? UiState.Success)?.data.orEmpty()
    val allMessages = (discordMessagesState as? UiState.Success)?.data.orEmpty()

    val activeServer = servers.firstOrNull { it.id == selectedDiscordServerId } ?: servers.firstOrNull()
    val activeCharacter = characters.firstOrNull { it.isActivePersona } ?: characters.firstOrNull()

    var showJoinServerDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateCharacterDialog by rememberSaveable { mutableStateOf(false) }
    var editingLpCharacter by remember { mutableStateOf<RpCharacterPersona?>(null) }

    var reviewFilter by rememberSaveable { mutableStateOf(MessageReviewFilter.ALL) }
    var actAsCharacter by rememberSaveable { mutableStateOf(true) }
    var messageType by rememberSaveable { mutableStateOf("RP_DIALOGUE") }
    var draftMessage by rememberSaveable { mutableStateOf("") }
    var draftTcgAction by rememberSaveable { mutableStateOf("") }
    var copiedInviteNotice by rememberSaveable { mutableStateOf(false) }

    val serverMessages = remember(allMessages, activeServer?.id, reviewFilter) {
        val forServer = if (activeServer != null) {
            allMessages.filter { it.serverId == activeServer.id }
        } else {
            allMessages
        }
        when (reviewFilter) {
            MessageReviewFilter.ALL -> forServer
            MessageReviewFilter.UNREVIEWED -> forServer.filter { !it.reviewedFlag }
            MessageReviewFilter.CHARACTER_RP -> forServer.filter { it.isCharacterProxy || it.messageType == "RP_DIALOGUE" }
            MessageReviewFilter.TCG_ACTIONS -> forServer.filter { it.messageType == "TCG_DUEL_ACTION" || it.tcgActionSummary.isNotBlank() }
        }
    }

    val unreviewedCount = remember(allMessages, activeServer?.id) {
        allMessages.count { (activeServer == null || it.serverId == activeServer.id) && !it.reviewedFlag }
    }

    val connectionMode = DiscordApiService.activeConnectionMode()
    val integrationEnabled = DiscordApiService.isIntegrationEnabled()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("discord_roleplay_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Header: Discord Server Presence, Webhook Character Proxy & Chat Review Hub
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ArcaneViolet.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    ArcaneViolet.copy(alpha = 0.22f),
                                    ElectricCyan.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                    .size(42.dp)
                                    .background(ArcaneViolet.copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, ArcaneViolet, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = "Discord RP Bridge",
                                    tint = ArcaneViolet
                                )
                            }
                            Column {
                                Text(
                                    text = "Discord TCG Roleplay & Character Bridge",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Be in Discord servers • Review chat messages • Send messages • Act like TCG characters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = when {
                                !integrationEnabled -> CrimsonAlert.copy(alpha = 0.18f)
                                connectionMode == "BOT_AND_WEBHOOK" -> EmeraldReady.copy(alpha = 0.18f)
                                connectionMode == "WEBHOOK_PROXY" -> RadiantGold.copy(alpha = 0.18f)
                                else -> ElectricCyan.copy(alpha = 0.16f)
                            },
                            modifier = Modifier.border(
                                1.dp,
                                when {
                                    !integrationEnabled -> CrimsonAlert
                                    connectionMode == "BOT_AND_WEBHOOK" -> EmeraldReady
                                    connectionMode == "WEBHOOK_PROXY" -> RadiantGold
                                    else -> ElectricCyan
                                },
                                RoundedCornerShape(50)
                            )
                        ) {
                            Text(
                                text = when {
                                    !integrationEnabled -> "BOT PAUSED"
                                    connectionMode == "BOT_AND_WEBHOOK" -> "LIVE BOT + WEBHOOK"
                                    connectionMode == "WEBHOOK_PROXY" -> "LIVE WEBHOOK PROXY"
                                    else -> "CLOUD RP RELAY"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    !integrationEnabled -> CrimsonAlert
                                    connectionMode == "BOT_AND_WEBHOOK" -> EmeraldReady
                                    connectionMode == "WEBHOOK_PROXY" -> RadiantGold
                                    else -> ElectricCyan
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Text(
                        text = "Connects your EDOPro Base Variant (MR5) and 21-Digit EDOPro Custom Clone directly to Discord Roleplay servers. Toggle Discord bot integration or configure DISCORD_BOT_TOKEN and DISCORD_WEBHOOK_URL in Settings or via the .env file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showJoinServerDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArcaneViolet,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("join_discord_server_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Join Server",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Join / Add Discord Server", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("open_discord_env_settings_button")
                        ) {
                            Text("Discord .env Settings")
                        }

                        OutlinedButton(
                            onClick = onDiscoverBotGuilds,
                            modifier = Modifier.testTag("sync_discord_guilds_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Bot Guilds",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Bot Guilds")
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(DiscordApiService.buildBotInviteUrl())
                                )
                                copiedInviteNotice = true
                            },
                            modifier = Modifier.testTag("copy_bot_invite_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Bot Invite URL",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedInviteNotice) "Invite URL Copied!" else "Copy Bot OAuth2 Invite")
                        }
                    }
                }
            }
        }

        // 1B. Discord Webhook Broadcast Bridge (Game Start, Deck Summaries & Match Winners)
        item {
            DiscordWebhookBridgePanel(
                discordEnvSettings = discordEnvSettings,
                bridgeConfig = discordBridgeConfig,
                broadcastHistory = discordBroadcastHistory,
                activeMatch = activeMatch,
                availableDecks = availableDecks,
                activeServerChannelName = activeServer?.channelName,
                onUpdateBridgeConfig = onUpdateBridgeConfig,
                onBroadcastGameStart = onBroadcastGameStart,
                onBroadcastDeckSummary = onBroadcastDeckSummary,
                onBroadcastMatchWinner = onBroadcastMatchWinner,
                onOpenSettings = onOpenSettings
            )
        }

        // 2. Joined Discord Roleplay Servers Row ("Be in Discord Servers")
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Text(
                            text = "Connected Discord RP Servers (${servers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (activeServer != null) {
                        Text(
                            text = "Active: ${activeServer.channelName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                when (discordServersState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ElectricCyan)
                        }
                    }
                    is UiState.Error -> {
                        Text(
                            text = discordServersState.message,
                            color = CrimsonAlert,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is UiState.Success -> {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(servers, key = { it.id }) { server ->
                                val isSelected = server.id == activeServer?.id
                                val is21DigitClone = server.baseVariantId == "EDOPRO_CUSTOM_FORK"
                                val accentColor = if (is21DigitClone) RadiantGold else ElectricCyan

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) ObsidianElevated else ObsidianCard
                                    ),
                                    modifier = Modifier
                                        .width(290.dp)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) accentColor else BorderSubtle,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { onSelectServer(server) }
                                        .testTag("discord_server_card_${server.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .background(
                                                            if (server.isConnected) EmeraldReady else CrimsonAlert,
                                                            CircleShape
                                                        )
                                                )
                                                Text(
                                                    text = server.serverName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteServer(server.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Leave Discord Server",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = accentColor.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = server.channelName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = accentColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = ArcaneViolet.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (is21DigitClone) "21-Digit Clone" else server.baseVariantId,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ArcaneViolet,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            Text(
                                                text = "• ${server.memberCount} RP members",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }

                                        Text(
                                            text = server.rpLoreSetting,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Guild ID: ${server.guildId.take(12)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextSecondary
                                            )
                                            TextButton(
                                                onClick = { onToggleServerConnection(server) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (server.isConnected) "Connected" else "Reconnect",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (server.isConnected) EmeraldReady else RadiantGold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Roleplay Character Personas & 1-Tap In-Character Deck ("Act Like Characters")
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RadiantGold.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TheaterComedy,
                                contentDescription = null,
                                tint = RadiantGold
                            )
                            Column {
                                Text(
                                    text = "TCG Roleplay Characters (${characters.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Act like characters in Discord servers with 21-digit LP & signature card code strings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Button(
                            onClick = { showCreateCharacterDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RadiantGold,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("create_rp_character_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Forge Character",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Character", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (characters.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(characters, key = { it.id }) { persona ->
                                val isActive = persona.id == activeCharacter?.id
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isActive) ObsidianElevated else ObsidianCard
                                    ),
                                    modifier = Modifier
                                        .width(265.dp)
                                        .border(
                                            width = if (isActive) 2.dp else 1.dp,
                                            color = if (isActive) RadiantGold else BorderSubtle,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onActivateCharacter(persona) }
                                        .testTag("rp_character_card_${persona.id}")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = persona.characterName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isActive) RadiantGold else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${persona.characterTitle} • ${persona.factionOrArchetype}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (characters.size > 1) {
                                                IconButton(
                                                    onClick = { onDeleteCharacter(persona.id) },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete Character",
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ElectricCyan.copy(alpha = 0.12f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "Ace: ${persona.signatureCardName}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ElectricCyan,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Code21: c${persona.signatureCodeString21} (${persona.signatureCodeString21.length}/21d)",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = TextSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "LP: ${BigInt21Engine.formatWithCommas(persona.currentLp21)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldReady,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            TextButton(
                                                onClick = { editingLpCharacter = persona },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                            ) {
                                                Text(
                                                    text = "Edit 21d LP",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = RadiantGold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Active Character Quick-Act Bar
                    if (activeCharacter != null && activeServer != null) {
                        HorizontalDivider(color = BorderSubtle)
                        Text(
                            text = "Act as ${activeCharacter.characterName} in ${activeServer.channelName} (1-Tap Character Actions):",
                            style = MaterialTheme.typography.labelMedium,
                            color = RadiantGold,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onTriggerCharacterQuickAct(activeServer, activeCharacter, "CATCHPHRASE")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ObsidianElevated,
                                    contentColor = RadiantGold
                                ),
                                modifier = Modifier.testTag("quick_act_catchphrase_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Speak Catchphrase")
                            }

                            Button(
                                onClick = {
                                    onTriggerCharacterQuickAct(activeServer, activeCharacter, "SUMMON_ACE")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ObsidianElevated,
                                    contentColor = ElectricCyan
                                ),
                                modifier = Modifier.testTag("quick_act_summon_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chant & Summon Ace (c${activeCharacter.signatureCodeString21.take(8)}…)")
                            }

                            Button(
                                onClick = {
                                    onTriggerCharacterQuickAct(activeServer, activeCharacter, "DECLARE_21D_CALC")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ObsidianElevated,
                                    contentColor = ArcaneViolet
                                ),
                                modifier = Modifier.testTag("quick_act_21d_calc_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Announce 21-Digit Code/LP Calc")
                            }
                        }
                    }
                }
            }
        }

        // 4. In-Character Message & TCG Action Composer ("Send Messages & Act Like Characters")
        if (activeServer != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Send Message to ${activeServer.serverName} (${activeServer.channelName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (actAsCharacter && activeCharacter != null) {
                                        "Acting as Character: ${activeCharacter.characterName} [${activeCharacter.characterTitle}]"
                                    } else {
                                        "Sending as Out-of-Character Duelist / Judge"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (actAsCharacter) RadiantGold else TextSecondary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Act as Character",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RadiantGold
                                )
                                Switch(
                                    checked = actAsCharacter,
                                    onCheckedChange = { actAsCharacter = it },
                                    modifier = Modifier.testTag("act_as_character_switch")
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = messageType == "RP_DIALOGUE",
                                onClick = { messageType = "RP_DIALOGUE" },
                                label = { Text("Roleplay Dialogue") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RadiantGold.copy(alpha = 0.22f),
                                    selectedLabelColor = RadiantGold
                                )
                            )
                            FilterChip(
                                selected = messageType == "TCG_DUEL_ACTION",
                                onClick = { messageType = "TCG_DUEL_ACTION" },
                                label = { Text("TCG Duel / Chain Action") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricCyan
                                )
                            )
                        }

                        OutlinedTextField(
                            value = draftMessage,
                            onValueChange = { draftMessage = it.take(500) },
                            label = {
                                Text(
                                    if (actAsCharacter && activeCharacter != null) {
                                        "Speak or roleplay as ${activeCharacter.characterName}…"
                                    } else {
                                        "Write Discord chat message or judge ruling…"
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("discord_message_input"),
                            minLines = 2,
                            maxLines = 4
                        )

                        OutlinedTextField(
                            value = draftTcgAction,
                            onValueChange = { draftTcgAction = it.take(240) },
                            label = {
                                Text("TCG Engine Action / 21-Digit Code String Tag (Optional)")
                            },
                            placeholder = {
                                Text(
                                    if (activeCharacter != null) {
                                        "CHAIN LINK 1: ${activeCharacter.signatureCardName} (c${activeCharacter.signatureCodeString21})"
                                    } else {
                                        "MSG_CHAINING: Spell Speed 2 Quick Effect"
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("discord_tcg_action_input"),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (draftMessage.isNotBlank()) {
                                    val defaultSummary = draftTcgAction.ifBlank {
                                        if (actAsCharacter && activeCharacter != null) {
                                            "CHARACTER_RP: ${activeCharacter.characterName} • LP: ${activeCharacter.currentLp21}"
                                        } else {
                                            "CHAT_POST: ${activeServer.baseVariantId}"
                                        }
                                    }
                                    onSendRoleplayMessage(
                                        activeServer,
                                        activeCharacter,
                                        actAsCharacter,
                                        draftMessage,
                                        defaultSummary,
                                        messageType
                                    )
                                    draftMessage = ""
                                    draftTcgAction = ""
                                }
                            },
                            enabled = draftMessage.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_discord_rp_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send to Discord",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (actAsCharacter && activeCharacter != null) {
                                    "Send to ${activeServer.channelName} as ${activeCharacter.characterName}"
                                } else {
                                    "Send Message to ${activeServer.channelName}"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 5. Discord Chat Message Review Header & Filter Bar ("Review Chat Messages")
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RateReview,
                            contentDescription = null,
                            tint = EmeraldReady
                        )
                        Column {
                            Text(
                                text = "Channel Chat Review Log (${serverMessages.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$unreviewedCount message(s) awaiting RP / TCG ruling review",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (unreviewedCount > 0) RadiantGold else EmeraldReady
                            )
                        }
                    }

                    if (activeServer != null) {
                        OutlinedButton(
                            onClick = { onReviewAndSyncMessages(activeServer) },
                            modifier = Modifier.testTag("pull_channel_messages_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Pull Channel Chat",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pull Chat")
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MessageReviewFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = reviewFilter == filter,
                            onClick = { reviewFilter = filter },
                            label = {
                                val labelText = if (filter == MessageReviewFilter.UNREVIEWED && unreviewedCount > 0) {
                                    "${filter.label} ($unreviewedCount)"
                                } else {
                                    filter.label
                                }
                                Text(labelText)
                            },
                            modifier = Modifier.testTag("review_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // 6. Chat Messages List with Review Toggle & In-Character Reply
        if (serverMessages.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No chat messages match the selected review filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        if (activeServer != null) {
                            OutlinedButton(onClick = { onReviewAndSyncMessages(activeServer) }) {
                                Text("Pull Latest Messages from ${activeServer.channelName}")
                            }
                        }
                    }
                }
            }
        } else {
            items(serverMessages, key = { it.id }) { msg ->
                val isCharProxy = msg.isCharacterProxy
                val borderCol = when {
                    !msg.reviewedFlag -> RadiantGold.copy(alpha = 0.7f)
                    isCharProxy -> ArcaneViolet.copy(alpha = 0.6f)
                    else -> BorderSubtle
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                        .testTag("discord_message_card_${msg.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(
                                            if (isCharProxy) ArcaneViolet.copy(alpha = 0.22f) else ElectricCyan.copy(alpha = 0.18f),
                                            CircleShape
                                        )
                                        .border(
                                            1.dp,
                                            if (isCharProxy) ArcaneViolet else ElectricCyan,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = msg.senderName.take(2).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCharProxy) ArcaneViolet else ElectricCyan
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = msg.senderName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCharProxy) RadiantGold else MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (isCharProxy) ArcaneViolet.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = if (isCharProxy) "CHARACTER BOT" else "SERVER CHAT",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isCharProxy) ArcaneViolet else ElectricCyan,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (msg.senderTitle.isNotBlank()) {
                                        Text(
                                            text = "${msg.senderTitle} • ${msg.channelName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onDeleteMessage(msg.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Message",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (msg.tcgActionSummary.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = msg.tcgActionSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = ElectricCyan
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${msg.deliveryStatus} • ${msg.baseVariantId}",
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = TextSecondary
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        val charName = activeCharacter?.characterName ?: "Judge"
                                        val sigCard = activeCharacter?.signatureCardName ?: "Counter Trap"
                                        val sigCode = activeCharacter?.signatureCodeString21 ?: "89631139"
                                        actAsCharacter = true
                                        messageType = "TCG_DUEL_ACTION"
                                        draftMessage = "@${msg.senderName} — As $charName, I have reviewed your action in ${msg.channelName} and chain $sigCard (`c$sigCode.lua`) in response!"
                                        draftTcgAction = "CHAIN_RESPONSE: Reviewed ${msg.senderName}'s move -> Counter with c$sigCode"
                                    },
                                    modifier = Modifier.testTag("reply_in_character_button_${msg.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = "Reply In-Character",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reply In-Character")
                                }

                                OutlinedButton(
                                    onClick = { onToggleMessageReviewed(msg) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("toggle_review_button_${msg.id}")
                                ) {
                                    Icon(
                                        imageVector = if (msg.reviewedFlag) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                        contentDescription = "Toggle Reviewed",
                                        tint = if (msg.reviewedFlag) EmeraldReady else RadiantGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (msg.reviewedFlag) "Reviewed ✓" else "Mark Reviewed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (msg.reviewedFlag) EmeraldReady else RadiantGold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog 1: Join or Add Discord Roleplay Server
    if (showJoinServerDialog) {
        var guildId by rememberSaveable { mutableStateOf("108955008800112233") }
        var serverName by rememberSaveable { mutableStateOf("Battle City Neo-Domino RP") }
        var channelId by rememberSaveable { mutableStateOf("108955008800112299") }
        var channelName by rememberSaveable { mutableStateOf("#kaiba-corp-colosseum") }
        var chosenVariant by rememberSaveable { mutableStateOf(selectedBaseVariantId) }
        var rpLore by rememberSaveable {
            mutableStateOf("High-stakes Battle City & 21-Digit Custom Expansion roleplay server with live Chain Link rulings.")
        }

        AlertDialog(
            onDismissRequest = { showJoinServerDialog = false },
            title = { Text("Join / Register Discord RP Server") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = serverName,
                        onValueChange = { serverName = it.take(80) },
                        label = { Text("Discord Server Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_discord_server_name_input")
                    )
                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it.take(60) },
                        label = { Text("Roleplay / Duel Channel Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_discord_channel_name_input")
                    )
                    OutlinedTextField(
                        value = guildId,
                        onValueChange = { guildId = it.take(40) },
                        label = { Text("Discord Guild ID (Snowflake)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = channelId,
                        onValueChange = { channelId = it.take(40) },
                        label = { Text("Discord Channel ID (Snowflake)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "TCG Engine Base Variant for Server:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BuiltInTcgCatalog.baseVariants.forEach { spec ->
                            FilterChip(
                                selected = chosenVariant == spec.baseVariantId,
                                onClick = { chosenVariant = spec.baseVariantId },
                                label = { Text(spec.baseVariantId) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = rpLore,
                        onValueChange = { rpLore = it.take(240) },
                        label = { Text("Server Roleplay Lore & Ruleset Summary") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onJoinOrAddServer(
                            guildId,
                            serverName,
                            channelId,
                            channelName,
                            chosenVariant,
                            rpLore
                        )
                        showJoinServerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_join_discord_server_button")
                ) {
                    Text("Join Server")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinServerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 2: Create TCG Roleplay Character Persona
    if (showCreateCharacterDialog) {
        var charName by rememberSaveable { mutableStateOf("Seto Kaiba (Neo-Domino)") }
        var charTitle by rememberSaveable { mutableStateOf("President of KaibaCorp") }
        var faction by rememberSaveable { mutableStateOf("Blue-Eyes / 21-Digit Dragon") }
        var charVariant by rememberSaveable { mutableStateOf("EDOPRO_CUSTOM_FORK") }
        var sigCard by rememberSaveable { mutableStateOf("Blue-Eyes Ultimate Singularity Dragon") }
        var sigCode21 by rememberSaveable { mutableStateOf("900001099999999999921") }
        var startLp21 by rememberSaveable { mutableStateOf("999999999999999999999") }
        var personality by rememberSaveable {
            mutableStateOf("Confident, theatrical, speaks with razor-sharp precision and invokes 21-digit code strings.")
        }
        var catchphrase by rememberSaveable {
            mutableStateOf("Step forward, third-rate duelist! Witness a 21-digit EDOPro Custom Clone calculation!")
        }
        var chant by rememberSaveable {
            mutableStateOf("Roar across the 21-digit horizon and obliterate every barrier! I summon my ultimate dragon!")
        }

        AlertDialog(
            onDismissRequest = { showCreateCharacterDialog = false },
            title = { Text("Forge TCG Roleplay Character") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = charName,
                        onValueChange = { charName = it.take(60) },
                        label = { Text("Character Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_rp_char_name_input")
                    )
                    OutlinedTextField(
                        value = charTitle,
                        onValueChange = { charTitle = it.take(60) },
                        label = { Text("Character Roleplay Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = faction,
                        onValueChange = { faction = it.take(60) },
                        label = { Text("Deck Archetype / Faction") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sigCard,
                        onValueChange = { sigCard = it.take(60) },
                        label = { Text("Signature Ace Card Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sigCode21,
                        onValueChange = { sigCode21 = BigInt21Engine.filterDigitsInput(it) },
                        label = { Text("Signature 21-Digit Card Code String (${sigCode21.length}/21 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_rp_char_code21_input")
                    )
                    OutlinedTextField(
                        value = startLp21,
                        onValueChange = { startLp21 = BigInt21Engine.filterDigitsInput(it) },
                        label = { Text("Starting LP (Up to 21 Digits: ${startLp21.length}/21)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_rp_char_lp21_input")
                    )
                    OutlinedTextField(
                        value = catchphrase,
                        onValueChange = { catchphrase = it.take(200) },
                        label = { Text("Opening Duel Catchphrase") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chant,
                        onValueChange = { chant = it.take(200) },
                        label = { Text("Signature Card Summon Chant") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = personality,
                        onValueChange = { personality = it.take(240) },
                        label = { Text("Roleplay Personality & Voice") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateCharacter(
                            charName,
                            charTitle,
                            faction,
                            charVariant,
                            sigCard,
                            sigCode21.ifBlank { BigInt21Engine.DEFAULT_CODE_STRING_21 },
                            startLp21.ifBlank { "8000" },
                            personality,
                            catchphrase,
                            chant
                        )
                        showCreateCharacterDialog = false
                    },
                    modifier = Modifier.testTag("confirm_create_rp_character_button")
                ) {
                    Text("Forge & Act as Character")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCharacterDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog 3: Edit Character 21-Digit LP
    editingLpCharacter?.let { targetChar ->
        var newLp21 by remember(targetChar.id) { mutableStateOf(targetChar.currentLp21) }
        AlertDialog(
            onDismissRequest = { editingLpCharacter = null },
            title = { Text("Update ${targetChar.characterName}'s 21-Digit LP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Supports 1 to 21 numerical digits (0 .. 999,999,999,999,999,999,999) for standard 8000 LP duels or 21-digit Boss Raids.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = newLp21,
                        onValueChange = { newLp21 = BigInt21Engine.filterDigitsInput(it) },
                        label = { Text("Current LP (${newLp21.length}/21 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateCharacterLp21(targetChar, newLp21.ifBlank { "8000" })
                        editingLpCharacter = null
                    }
                ) {
                    Text("Update LP")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLpCharacter = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
