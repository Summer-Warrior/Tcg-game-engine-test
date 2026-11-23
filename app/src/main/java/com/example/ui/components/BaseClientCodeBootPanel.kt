package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.BaseClientCodeBootSpec
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold

/**
 * Honest Read-Only Upstream Source Reference Card for locked GitHub-sourced base clients
 * (e.g., EDOPro, Card-Forge).
 *
 * Replaces simulated C++/Java boot timers with transparent read-only upstream repository
 * metadata, protocol verification, external URI dispatch, and Copy-on-Fork guidance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BaseClientCodeBootPanel(
    bootSpec: BaseClientCodeBootSpec,
    @Suppress("UNUSED_PARAMETER") completedStageCount: Int,
    @Suppress("UNUSED_PARAMETER") isBooting: Boolean,
    @Suppress("UNUSED_PARAMETER") bootElapsedMs: Long,
    accentColor: Color,
    @Suppress("UNUSED_PARAMETER") onRerunRepoBoot: () -> Unit,
    onLaunchSchemeOrExternalRepo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.2.dp,
                color = EmeraldReady.copy(alpha = 0.7f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("upstream_source_reference_card_${bootSpec.baseVariantId}")
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.14f),
                            ObsidianElevated.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Read-Only Upstream Source Reference",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked to Upstream GitHub Source",
                                tint = EmeraldReady,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "LOCKED TO UPSTREAM GITHUB SOURCE",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldReady,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = bootSpec.shortTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(EmeraldReady.copy(alpha = 0.16f))
                        .border(1.dp, EmeraldReady, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "READ-ONLY REF",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldReady,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF090D16))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Upstream Runtime: ${bootSpec.languageRuntime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Source Entrypoint: ${bootSpec.primaryEntrypointFile}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Canonical Repo: ${bootSpec.exactGitHubRepoUrl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Firewall: Native C++/Java binaries run externally. This Android client hosts a read-only ruleset reference; use a Non-Locked 21-Digit Fork for custom cards or MPV Card Maker exports.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onLaunchSchemeOrExternalRepo(bootSpec.exactGitHubRepoUrl) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("upstream_open_repo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open GitHub Repository",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = { onLaunchSchemeOrExternalRepo(bootSpec.deepLinkSchemeUri) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("upstream_dispatch_scheme_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Launch ${bootSpec.deepLinkSchemeUri}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
