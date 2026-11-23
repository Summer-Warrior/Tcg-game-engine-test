package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BaseClientCodeRegistry
import com.example.data.BuiltInTcgCatalog
import com.example.data.ClientNode
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold

/**
 * Represents a Client that the user has selected and is being asked to confirm
 * opening in a separate Android Window or external App.
 */
data class PendingClientLaunchSelection(
    val client: ClientNode? = null,
    val baseVariantId: String = client?.baseVariantId ?: "EDOPRO_BASE_MR5",
    val activeMatchId: String? = null,
    val sourceLabel: String = "Client Selector"
)

/**
 * Confirmation Modal shown when a TCG Client is selected, asking the user to confirm
 * whether they want to open that Client in a new standalone Window or external App.
 *
 * When confirmed:
 * - **Open in New Window**: Launches `ClientSystemWindowActivity` in a separate multi-window
 *   Android document task (`FLAG_ACTIVITY_NEW_DOCUMENT` + `FLAG_ACTIVITY_MULTIPLE_TASK`, plus
 *   optional `FLAG_ACTIVITY_LAUNCH_ADJACENT` split-screen flag).
 * - **Open External App / URI**: Dispatches `ClientWindowLauncher.openExternalOrSeparateWindow`
 *   to launch the external installed app or source repository URI in a new OS task window
 *   (falling back to the standalone Client window if no external app handles that URI).
 */
@Composable
fun ConfirmOpenClientWindowDialog(
    selection: PendingClientLaunchSelection,
    onDismissKeepInHub: () -> Unit,
    onConfirmOpenNewWindow: (openInAdjacentWindow: Boolean, openAsSeparateTask: Boolean) -> Unit,
    onConfirmOpenExternalApp: (targetUri: String) -> Unit
) {
    val baseSpec = remember(selection.baseVariantId) {
        BuiltInTcgCatalog.findBaseSpec(selection.baseVariantId)
    }
    val baseBootSpec = remember(selection.baseVariantId) {
        BaseClientCodeRegistry.findForBaseVariant(selection.baseVariantId)
    }
    val uiProfile = remember(selection.baseVariantId, selection.client?.id) {
        BuiltInTcgCatalog.systemUiProfileFor(selection.baseVariantId, selection.client)
    }
    val isLockedGitHubBase = BuiltInTcgCatalog.isLockedGitHubBaseClient(selection.baseVariantId)
    val clientDisplayName = selection.client?.clientName ?: uiProfile.clientDisplayName
    val targetEndpointOrRepo = if (isLockedGitHubBase) {
        BuiltInTcgCatalog.exactGitHubRepoUrl(selection.baseVariantId)
    } else {
        selection.client?.endpointUri?.ifBlank { uiProfile.endpointOrRepoUri }
            ?: uiProfile.endpointOrRepoUri
    }
    val externalLaunchUri = remember(targetEndpointOrRepo, baseSpec.sourceRepoUrl, baseBootSpec) {
        when {
            baseBootSpec != null -> baseBootSpec.rawBootstrapCodeUrl
            targetEndpointOrRepo.startsWith("https://", ignoreCase = true) ||
                targetEndpointOrRepo.startsWith("http://", ignoreCase = true) -> targetEndpointOrRepo
            else -> baseSpec.sourceRepoUrl
        }
    }

    val accentColor = Color(uiProfile.accentHex)
    var openInAdjacentSplit by rememberSaveable { mutableStateOf(true) }
    var openAsSeparateDocumentTask by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismissKeepInHub,
        modifier = Modifier.testTag("confirm_open_client_window_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.5.dp, accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DesktopWindows,
                    contentDescription = "Open Client in New Window or App",
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        title = {
            Column {
                Text(
                    text = "Open Client in New Window / App?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$clientDisplayName • ${uiProfile.hudBadgeLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (baseBootSpec != null) {
                        "You selected locked Base Client '$clientDisplayName'. Opening this client executes its upstream repository boot code (${baseBootSpec.primaryEntrypointFile}) in a dedicated Window / App."
                    } else {
                        "You selected '$clientDisplayName'. Confirm whether you want to open it as a separate Android Window / App task or keep working inside the current NexusTCG window."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Client System Metadata Summary Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianElevated)
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isLockedGitHubBase) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "GitHub Source Locked",
                                tint = EmeraldReady,
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Client App Window",
                                tint = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = uiProfile.windowModeTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "System UI: ${uiProfile.systemTitle}\n" +
                            "Protocol: ${selection.baseVariantId} • ${baseSpec.protocolVersion}\n" +
                            "Target URI: $targetEndpointOrRepo",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Upstream Source Code Boot Summary for Locked [[EDOPro]] & [[Forge]] Base Clients
                if (baseBootSpec != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF080C14))
                            .border(1.dp, EmeraldReady.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Locked Repository Startup Code",
                                tint = EmeraldReady,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Uses Repository Client Code (${baseBootSpec.stages.size} Boot Stages)",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldReady,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Entry: ${baseBootSpec.primaryEntrypointFile}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = RadiantGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$ ${baseBootSpec.primaryLaunchCommand}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = RadiantGold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Multi-Window Launch Options
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openInAdjacentSplit = !openInAdjacentSplit }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Launch in Adjacent Split-Screen Window",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Opens side-by-side when multi-window is available",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = openInAdjacentSplit,
                            onCheckedChange = { openInAdjacentSplit = it },
                            modifier = Modifier.testTag("confirm_dialog_adjacent_switch")
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openAsSeparateDocumentTask = !openAsSeparateDocumentTask }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Spawn Dedicated App Task in Recents",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Runs the client with its own independent window entry",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = openAsSeparateDocumentTask,
                            onCheckedChange = { openAsSeparateDocumentTask = it },
                            modifier = Modifier.testTag("confirm_dialog_new_task_switch")
                        )
                    }
                }

                // Confirmed Action Buttons inside Dialog Body for Clear Accessibility
                Button(
                    onClick = {
                        onConfirmOpenNewWindow(openInAdjacentSplit, openAsSeparateDocumentTask)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color(0xFF060A12)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_open_client_window_button")
                ) {
                    Icon(
                        imageVector = if (baseBootSpec != null) Icons.Default.Code else Icons.Default.DesktopWindows,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (baseBootSpec != null) {
                            "Yes, Boot Repo Code & Open Window"
                        } else {
                            "Yes, Open in New Window"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                if (baseBootSpec != null) {
                    OutlinedButton(
                        onClick = { onConfirmOpenExternalApp(baseBootSpec.deepLinkSchemeUri) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_open_base_client_scheme_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Client URI (${baseBootSpec.deepLinkSchemeUri})",
                            color = EmeraldReady,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onConfirmOpenExternalApp(externalLaunchUri) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_open_external_app_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (baseBootSpec != null) {
                            "View Upstream Entrypoint Code"
                        } else {
                            "Open External App / Source URI"
                        },
                        color = RadiantGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismissKeepInHub,
                modifier = Modifier.testTag("dismiss_open_client_window_button")
            ) {
                Text("Keep in Current App")
            }
        }
    )
}
