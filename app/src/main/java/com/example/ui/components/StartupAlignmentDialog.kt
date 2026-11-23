package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AlignmentCheckReport
import com.example.data.AlignmentHealthState
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold

/**
 * Automatic On-Open Alignment & Update Pop-Up shown when `StartupAlignmentChecker`
 * detects a status other than `LINKED_AND_CURRENT` or pending Dual-Engine Consent.
 */
@Composable
fun StartupAlignmentDialog(
    report: AlignmentCheckReport,
    onOpenHandshakeArea: () -> Unit,
    onOpenCardMakerApp: () -> Unit,
    onDismissForCommitSha: () -> Unit
) {
    val badgeColor = when (report.status) {
        AlignmentHealthState.LINKED_AND_CURRENT -> EmeraldReady
        AlignmentHealthState.BREAKING_INCOMPATIBLE, AlignmentHealthState.DEGRADED -> CrimsonAlert
        else -> RadiantGold
    }

    AlertDialog(
        onDismissRequest = onDismissForCommitSha,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = if (report.status == AlignmentHealthState.LINKED_AND_CURRENT) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.SyncProblem
                },
                contentDescription = "Alignment Status",
                tint = badgeColor,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Cross-App Handshake & Sync Check",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeColor.copy(alpha = 0.16f))
                        .border(1.dp, badgeColor, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = report.status.badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = report.summaryMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Local Private Play Store Install Badge (Zero Play Store URLs)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = if (report.installedCardMaker.isInstalled) EmeraldReady else RadiantGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (report.installedCardMaker.isInstalled) {
                                "MPV Card Maker Installed Locally"
                            } else {
                                "MPV Card Maker Not Detected On Device"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (report.installedCardMaker.isInstalled) {
                                "${report.installedCardMaker.packageName} • v${report.installedCardMaker.versionName} (${report.installedCardMaker.versionCode})"
                            } else {
                                "Pkg: ${report.installedCardMaker.packageName} (Web Portal fallback ready)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Schema & Commit SHAs
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A0E17))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Schema: Local v${report.localSchemaVersion} | Card Maker v${report.remoteSchemaVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Dual Consent: Engine=${if (report.gameEngineConsentSigned) "SIGNED" else "PENDING"} • Card Maker=${if (report.cardMakerConsentSigned) "SIGNED" else "PENDING"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (report.isDualConsentComplete) EmeraldReady else RadiantGold
                    )
                    Text(
                        text = "Card Maker SHA: ${report.cardMakerCommitSha.take(10).ifBlank { "offline/unverified" }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Master Hub SHA: ${report.masterHubCommitSha.take(10).ifBlank { "offline/unverified" }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (report.missingOrMismatchedItems.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Detected Alignment Diffs (${report.missingOrMismatchedItems.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    report.missingOrMismatchedItems.forEach { diff ->
                        Text(
                            text = "• $diff",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // Direct Action Buttons (3 clear choices)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenHandshakeArea,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color(0xFF060A12)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("startup_popup_open_handshake_button")
                    ) {
                        Text("Open Handshake Area", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenCardMakerApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("startup_popup_open_card_maker_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Card Maker App (Handshake)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismissForCommitSha,
                modifier = Modifier.testTag("startup_popup_dismiss_sha_button")
            ) {
                Text("Dismiss for This Commit SHA")
            }
        }
    )
}
