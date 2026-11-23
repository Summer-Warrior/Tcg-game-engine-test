package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.DiscordApiService
import com.example.data.DiscordEnvSettingsState
import com.example.ui.theme.ArcaneViolet
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    discordEnvSettings: DiscordEnvSettingsState,
    onToggleDiscordIntegration: (Boolean) -> Unit,
    onSaveDiscordEnvCredentials: (
        botToken: String,
        webhookUrl: String,
        botClientId: String,
        integrationEnabled: Boolean
    ) -> Unit,
    onResetToBuildConfigDefaults: () -> Unit,
    onTestDiscordConnection: () -> Unit,
    onNavigateToDiscordRp: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    var integrationEnabled by rememberSaveable(discordEnvSettings.integrationEnabled) {
        mutableStateOf(discordEnvSettings.integrationEnabled)
    }
    var botTokenInput by rememberSaveable(discordEnvSettings.botToken) {
        mutableStateOf(discordEnvSettings.botToken)
    }
    var webhookUrlInput by rememberSaveable(discordEnvSettings.webhookUrl) {
        mutableStateOf(discordEnvSettings.webhookUrl)
    }
    var botClientIdInput by rememberSaveable(discordEnvSettings.botClientId) {
        mutableStateOf(discordEnvSettings.botClientId)
    }
    var rawDotEnvSnippetInput by rememberSaveable {
        mutableStateOf("")
    }
    var showRawEnvParser by rememberSaveable { mutableStateOf(false) }

    var revealBotToken by rememberSaveable { mutableStateOf(false) }
    var revealWebhookUrl by rememberSaveable { mutableStateOf(false) }
    var revealEnvPreview by rememberSaveable { mutableStateOf(false) }
    var copiedEnvNotice by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(discordEnvSettings) {
        integrationEnabled = discordEnvSettings.integrationEnabled
        botTokenInput = discordEnvSettings.botToken
        webhookUrlInput = discordEnvSettings.webhookUrl
        botClientIdInput = discordEnvSettings.botClientId
    }

    val isWebhookFormatValid = webhookUrlInput.isBlank() ||
        DiscordEnvSettingsState.isValidDiscordWebhookUrl(webhookUrlInput)

    val previewState = discordEnvSettings.copy(
        integrationEnabled = integrationEnabled,
        botToken = botTokenInput,
        webhookUrl = webhookUrlInput,
        botClientId = botClientIdInput
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Master Discord Bot Integration Toggle & Live Status Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (integrationEnabled) EmeraldReady.copy(alpha = 0.55f) else BorderSubtle,
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.16f),
                                    ArcaneViolet.copy(alpha = 0.14f),
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
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(ElectricCyan.copy(alpha = 0.18f), CircleShape)
                                    .border(1.dp, ElectricCyan, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Discord Integration Settings",
                                    tint = ElectricCyan
                                )
                            }
                            Column {
                                Text(
                                    text = "Discord Bot & Webhook Integration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (integrationEnabled) {
                                        "Enabled — Active Mode: ${previewState.effectiveConnectionMode}"
                                    } else {
                                        "Paused — Operating in local/Cloud RP Relay mode only"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (integrationEnabled) EmeraldReady else RadiantGold
                                )
                            }
                        }

                        Switch(
                            checked = integrationEnabled,
                            onCheckedChange = { enabled ->
                                integrationEnabled = enabled
                                onToggleDiscordIntegration(enabled)
                            },
                            modifier = Modifier.testTag("settings_discord_integration_switch")
                        )
                    }

                    HorizontalDivider(color = BorderSubtle)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BuildConfig / .env Detection Status",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append("DISCORD_BOT_TOKEN: ")
                                    append(if (discordEnvSettings.envBotTokenDetected) "Detected in .env" else "Placeholder/Unset in .env")
                                    append(" • DISCORD_WEBHOOK_URL: ")
                                    append(if (discordEnvSettings.envWebhookUrlDetected) "Detected in .env" else "Placeholder/Unset in .env")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (discordEnvSettings.usingRuntimeOverride) {
                                RadiantGold.copy(alpha = 0.18f)
                            } else {
                                ElectricCyan.copy(alpha = 0.16f)
                            },
                            modifier = Modifier.border(
                                1.dp,
                                if (discordEnvSettings.usingRuntimeOverride) RadiantGold else ElectricCyan,
                                RoundedCornerShape(50)
                            )
                        ) {
                            Text(
                                text = if (discordEnvSettings.usingRuntimeOverride) {
                                    "CUSTOM .ENV OVERRIDE"
                                } else {
                                    "BUILDCONFIG .ENV"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (discordEnvSettings.usingRuntimeOverride) RadiantGold else ElectricCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (integrationEnabled) EmeraldReady else RadiantGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = discordEnvSettings.lastVerificationStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("settings_verification_status_text")
                            )
                        }
                    }
                }
            }
        }

        // 2. Secure .env Token & Webhook Input Card
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = RadiantGold
                            )
                            Column {
                                Text(
                                    text = "Discord .env Credentials (API Token & Webhook URL)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Maps directly to DISCORD_BOT_TOKEN and DISCORD_WEBHOOK_URL in .env",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        TextButton(
                            onClick = { showRawEnvParser = !showRawEnvParser },
                            modifier = Modifier.testTag("settings_toggle_raw_env_parser_button")
                        ) {
                            Text(if (showRawEnvParser) "Hide .env Paste" else "Paste .env Block")
                        }
                    }

                    if (showRawEnvParser) {
                        OutlinedTextField(
                            value = rawDotEnvSnippetInput,
                            onValueChange = { raw ->
                                rawDotEnvSnippetInput = raw
                                raw.lineSequence().forEach { line ->
                                    val trimmed = line.trim()
                                    if (trimmed.startsWith("DISCORD_BOT_TOKEN=")) {
                                        botTokenInput = trimmed.substringAfter("DISCORD_BOT_TOKEN=")
                                            .trim()
                                            .removeSurrounding("\"")
                                    } else if (trimmed.startsWith("DISCORD_WEBHOOK_URL=")) {
                                        webhookUrlInput = trimmed.substringAfter("DISCORD_WEBHOOK_URL=")
                                            .trim()
                                            .removeSurrounding("\"")
                                    } else if (trimmed.startsWith("DISCORD_CLIENT_ID=")) {
                                        botClientIdInput = trimmed.substringAfter("DISCORD_CLIENT_ID=")
                                            .trim()
                                            .removeSurrounding("\"")
                                    }
                                }
                            },
                            label = {
                                Text("Paste .env Lines (DISCORD_BOT_TOKEN=... & DISCORD_WEBHOOK_URL=...)")
                            },
                            placeholder = {
                                Text("DISCORD_BOT_TOKEN=MT...\nDISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/...")
                            },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_raw_dotenv_input")
                        )
                    }

                    // DISCORD_BOT_TOKEN input
                    OutlinedTextField(
                        value = botTokenInput,
                        onValueChange = { botTokenInput = it.trim() },
                        enabled = integrationEnabled,
                        label = { Text("DISCORD_BOT_TOKEN (Discord Bot API v10 Token)") },
                        placeholder = { Text("YOUR_DISCORD_BOT_TOKEN") },
                        singleLine = true,
                        visualTransformation = if (revealBotToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(
                                onClick = { revealBotToken = !revealBotToken },
                                modifier = Modifier.testTag("toggle_bot_token_visibility_button")
                            ) {
                                Icon(
                                    imageVector = if (revealBotToken) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (revealBotToken) "Hide Bot Token" else "Show Bot Token"
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                text = "Used for joining Discord servers (GET /users/@me/guilds), reviewing channel chat history, and posting Bot embeds.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_bot_token_input")
                    )

                    // DISCORD_WEBHOOK_URL input
                    OutlinedTextField(
                        value = webhookUrlInput,
                        onValueChange = { webhookUrlInput = it.trim() },
                        enabled = integrationEnabled,
                        isError = !isWebhookFormatValid,
                        label = { Text("DISCORD_WEBHOOK_URL (Discord Channel Webhook URL)") },
                        placeholder = { Text("https://discord.com/api/webhooks/123456789/abcdef...") },
                        singleLine = true,
                        visualTransformation = if (revealWebhookUrl) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        trailingIcon = {
                            IconButton(
                                onClick = { revealWebhookUrl = !revealWebhookUrl },
                                modifier = Modifier.testTag("toggle_webhook_visibility_button")
                            ) {
                                Icon(
                                    imageVector = if (revealWebhookUrl) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (revealWebhookUrl) "Hide Webhook URL" else "Show Webhook URL"
                                )
                            }
                        },
                        supportingText = {
                            if (!isWebhookFormatValid) {
                                Text(
                                    text = "Webhook URL must start with https://discord.com/api/webhooks/...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrimsonAlert
                                )
                            } else {
                                Text(
                                    text = "Used to act like TCG Roleplay Characters in Discord servers with custom character usernames and 21-digit card embeds.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_webhook_url_input")
                    )

                    // Optional DISCORD_CLIENT_ID for OAuth2 Bot Invite URL
                    OutlinedTextField(
                        value = botClientIdInput,
                        onValueChange = { botClientIdInput = it.filter { ch -> ch.isDigit() }.take(24) },
                        enabled = integrationEnabled,
                        label = { Text("Discord Application / Bot Client ID (For OAuth2 Server Invite)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_client_id_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSaveDiscordEnvCredentials(
                                    botTokenInput,
                                    webhookUrlInput,
                                    botClientIdInput,
                                    integrationEnabled
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("settings_save_credentials_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save .env Settings",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save & Apply .env Credentials", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                onSaveDiscordEnvCredentials(
                                    botTokenInput,
                                    webhookUrlInput,
                                    botClientIdInput,
                                    integrationEnabled
                                )
                                onTestDiscordConnection()
                            },
                            enabled = integrationEnabled,
                            modifier = Modifier.testTag("settings_test_connection_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = "Test Discord API",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Live Connection")
                        }

                        OutlinedButton(
                            onClick = onResetToBuildConfigDefaults,
                            modifier = Modifier.testTag("settings_reset_env_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Reset to BuildConfig",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset to BuildConfig .env")
                        }
                    }
                }
            }
        }

        // 3. .env Structure Preview & AI Studio Secrets Sync Helper
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ArcaneViolet.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = ArcaneViolet
                            )
                            Column {
                                Text(
                                    text = ".env Structure & AI Studio Secrets Export",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Matches the project's .env / .env.example Secrets Gradle Plugin format",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        TextButton(
                            onClick = { revealEnvPreview = !revealEnvPreview },
                            modifier = Modifier.testTag("toggle_env_preview_mask_button")
                        ) {
                            Text(if (revealEnvPreview) "Mask Values" else "Show Raw .env")
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = previewState.toDotEnvPreview(maskSecrets = !revealEnvPreview),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                            modifier = Modifier
                                .padding(12.dp)
                                .testTag("dotenv_structure_preview_box")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(previewState.toDotEnvPreview(maskSecrets = false))
                                )
                                copiedEnvNotice = true
                            },
                            modifier = Modifier.testTag("settings_copy_env_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy .env Structure",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedEnvNotice) "Copied .env Block!" else "Copy .env Block for Secrets Panel")
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(DiscordApiService.buildBotInviteUrl(botClientIdInput))
                                )
                            },
                            modifier = Modifier.testTag("settings_copy_invite_url_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Copy Bot Invite URL",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Bot Invite URL")
                        }

                        Button(
                            onClick = onNavigateToDiscordRp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArcaneViolet,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("settings_open_discord_rp_button")
                        ) {
                            Text("Open Discord RP Hub", fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Security Note: Credentials saved here are stored in private app storage for immediate runtime use. For persistent compile-time injection via BuildConfig, paste the .env values into the Secrets panel in AI Studio. Never commit real tokens to public repositories.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
