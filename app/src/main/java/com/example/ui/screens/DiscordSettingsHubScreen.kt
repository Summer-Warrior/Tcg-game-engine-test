package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BotDirectoryEntry
import com.example.data.CustomDeck
import com.example.data.DiscordEnvSettingsState
import com.example.data.DiscordRpMessage
import com.example.data.DiscordServerHub
import com.example.data.DiscordWebhookBridgeConfig
import com.example.data.DiscordWebhookBroadcastRecord
import com.example.data.ImportedMpvCard
import com.example.data.MatchSession
import com.example.data.RpCharacterPersona
import com.example.engine.CatalogBlueprint
import com.example.ui.components.DiagnosticsInvariantsModal
import com.example.viewmodel.UiState

/**
 * Section 5: Discord RP & Settings
 * Combines Discord Roleplay & OAuth2 Bot Directory (zero Bot Tokens) with Hub Settings,
 * Handshake launcher, and discreet Diagnostics & Invariants verification modal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscordSettingsHubScreen(
    initialSubTab: Int,
    selectedBaseVariantId: String,
    signedInAccountLabel: String,
    catalogBlueprints: List<CatalogBlueprint>,
    loadedMatchCards: List<ImportedMpvCard>,
    botsDirectory: List<BotDirectoryEntry>,
    discordServersState: UiState<List<DiscordServerHub>>,
    rpCharactersState: UiState<List<RpCharacterPersona>>,
    discordMessagesState: UiState<List<DiscordRpMessage>>,
    selectedDiscordServerId: String?,
    discordEnvSettings: DiscordEnvSettingsState,
    discordBridgeConfig: DiscordWebhookBridgeConfig,
    discordBroadcastHistory: List<DiscordWebhookBroadcastRecord>,
    activeMatch: MatchSession?,
    availableDecks: List<CustomDeck>,
    onSelectServer: (DiscordServerHub) -> Unit,
    onJoinOrAddServer: (String, String, String, String, String, String) -> Unit,
    onDiscoverBotGuilds: () -> Unit,
    onReviewAndSyncMessages: (DiscordServerHub) -> Unit,
    onToggleServerConnection: (DiscordServerHub) -> Unit,
    onDeleteServer: (String) -> Unit,
    onCreateCharacter: (String, String, String, String, String, String, String, String, String, String) -> Unit,
    onActivateCharacter: (RpCharacterPersona) -> Unit,
    onUpdateCharacterLp21: (RpCharacterPersona, String) -> Unit,
    onDeleteCharacter: (String) -> Unit,
    onSendRoleplayMessage: (DiscordServerHub, RpCharacterPersona?, Boolean, String, String, String) -> Unit,
    onTriggerCharacterQuickAct: (DiscordServerHub, RpCharacterPersona, String) -> Unit,
    onToggleMessageReviewed: (DiscordRpMessage) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onUpdateBridgeConfig: (String, Boolean, Boolean, Boolean) -> Unit,
    onBroadcastGameStart: (MatchSession?, CustomDeck?) -> Unit,
    onBroadcastDeckSummary: (CustomDeck) -> Unit,
    onBroadcastMatchWinner: (MatchSession?, String?, CustomDeck?) -> Unit,
    onToggleDiscordIntegration: (Boolean) -> Unit,
    onSaveDiscordEnvCredentials: (String, String, String, Boolean) -> Unit,
    onResetToBuildConfigDefaults: () -> Unit,
    onTestDiscordConnection: () -> Unit,
    onOpenHandshakeArea: () -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var activeTab by rememberSaveable { mutableIntStateOf(initialSubTab.coerceIn(0, 1)) }
    var showDiagnosticsModal by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(initialSubTab) {
        if (initialSubTab in 0..1) activeTab = initialSubTab
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("discord_settings_hub_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                label = { Text("Discord RP & Bots", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("section5_tab_discord")
            )
            FilterChip(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                label = { Text("Settings & Hub", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("section5_tab_settings")
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { showDiagnosticsModal = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("open_diagnostics_invariants_button")
            ) {
                Icon(Icons.Default.Verified, contentDescription = "Diagnostics & Invariants", modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Diagnostics", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (activeTab == 0) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (botsDirectory.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "OAuth2 Bot Directory (Zero Client-Side Bot Tokens)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                botsDirectory.forEach { bot ->
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(bot.inviteUrl))
                                            onStatusMessage("Copied OAuth2 invite URL for ${bot.name}.")
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(bot.name, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                DiscordRoleplayScreen(
                    selectedBaseVariantId = selectedBaseVariantId,
                    discordServersState = discordServersState,
                    rpCharactersState = rpCharactersState,
                    discordMessagesState = discordMessagesState,
                    selectedDiscordServerId = selectedDiscordServerId,
                    onSelectServer = onSelectServer,
                    onJoinOrAddServer = onJoinOrAddServer,
                    onDiscoverBotGuilds = onDiscoverBotGuilds,
                    onReviewAndSyncMessages = onReviewAndSyncMessages,
                    onToggleServerConnection = onToggleServerConnection,
                    onDeleteServer = onDeleteServer,
                    onCreateCharacter = onCreateCharacter,
                    onActivateCharacter = onActivateCharacter,
                    onUpdateCharacterLp21 = onUpdateCharacterLp21,
                    onDeleteCharacter = onDeleteCharacter,
                    onSendRoleplayMessage = onSendRoleplayMessage,
                    onTriggerCharacterQuickAct = onTriggerCharacterQuickAct,
                    onToggleMessageReviewed = onToggleMessageReviewed,
                    onDeleteMessage = onDeleteMessage,
                    onOpenSettings = { activeTab = 1 },
                    discordEnvSettings = discordEnvSettings,
                    discordBridgeConfig = discordBridgeConfig,
                    discordBroadcastHistory = discordBroadcastHistory,
                    activeMatch = activeMatch,
                    availableDecks = availableDecks,
                    onUpdateBridgeConfig = onUpdateBridgeConfig,
                    onBroadcastGameStart = onBroadcastGameStart,
                    onBroadcastDeckSummary = onBroadcastDeckSummary,
                    onBroadcastMatchWinner = onBroadcastMatchWinner
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenHandshakeArea,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open MPV Handshake Area", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { showDiagnosticsModal = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run 6 Invariants", style = MaterialTheme.typography.labelSmall)
                    }
                }
                SettingsScreen(
                    discordEnvSettings = discordEnvSettings,
                    signedInAccountLabel = signedInAccountLabel,
                    onToggleDiscordIntegration = onToggleDiscordIntegration,
                    onSaveDiscordEnvCredentials = onSaveDiscordEnvCredentials,
                    onResetToBuildConfigDefaults = onResetToBuildConfigDefaults,
                    onTestDiscordConnection = onTestDiscordConnection,
                    onNavigateToDiscordRp = { activeTab = 0 }
                )
            }
        }
    }

    if (showDiagnosticsModal) {
        DiagnosticsInvariantsModal(
            catalogBlueprints = catalogBlueprints,
            loadedMatchCards = loadedMatchCards,
            onDismiss = { showDiagnosticsModal = false }
        )
    }
}
