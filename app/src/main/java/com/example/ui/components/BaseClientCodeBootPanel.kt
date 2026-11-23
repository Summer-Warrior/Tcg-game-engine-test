package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.sp
import com.example.data.BaseClientCodeBootSpec
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold

/**
 * Displays and animates the locked upstream repository startup code execution for the canonical
 * Base Clients (**[[EDOPro]]** and **[[Forge]]**) when opened in their dedicated client window.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BaseClientCodeBootPanel(
    bootSpec: BaseClientCodeBootSpec,
    completedStageCount: Int,
    isBooting: Boolean,
    bootElapsedMs: Long,
    accentColor: Color,
    onRerunRepoBoot: () -> Unit,
    onLaunchSchemeOrExternalRepo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalStages = bootSpec.stages.size.coerceAtLeast(1)
    val clampedCompleted = completedStageCount.coerceIn(0, totalStages)
    val allStagesComplete = !isBooting && clampedCompleted >= totalStages

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.2.dp,
                color = if (allStagesComplete) EmeraldReady.copy(alpha = 0.7f) else accentColor.copy(alpha = 0.7f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("base_client_code_boot_panel_${bootSpec.baseVariantId}")
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
            // Header: Upstream Lock + Status Pill
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
                            imageVector = Icons.Default.Code,
                            contentDescription = "Repository Code Boot",
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
                                contentDescription = "Locked Upstream GitHub Source",
                                tint = EmeraldReady,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "UPSTREAM CODE CLIENT BOOT",
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
                        .background(
                            if (allStagesComplete) EmeraldReady.copy(alpha = 0.18f)
                            else RadiantGold.copy(alpha = 0.18f)
                        )
                        .border(
                            1.dp,
                            if (allStagesComplete) EmeraldReady else RadiantGold,
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (allStagesComplete) {
                            "BOOTED (${bootElapsedMs}ms)"
                        } else {
                            "BOOTING $clampedCompleted/$totalStages"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (allStagesComplete) EmeraldReady else RadiantGold,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Runtime & Entrypoint Summary
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
                    text = bootSpec.languageRuntime,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Entrypoint: ${bootSpec.primaryEntrypointFile}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = RadiantGold,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "$ ${bootSpec.primaryLaunchCommand}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Locked Repo: ${bootSpec.exactGitHubRepoUrl}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Ordered Repository Boot Stages
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Executed Source Startup Sequence (${clampedCompleted}/${totalStages}):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                bootSpec.stages.forEachIndexed { index, stage ->
                    val isDone = index < clampedCompleted
                    val isActive = isBooting && index == clampedCompleted
                    val borderColor by animateColorAsState(
                        targetValue = when {
                            isDone -> EmeraldReady.copy(alpha = 0.65f)
                            isActive -> RadiantGold
                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                        },
                        label = "stage_border_${stage.stageOrder}"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isDone) EmeraldReady.copy(alpha = 0.08f)
                                else if (isActive) RadiantGold.copy(alpha = 0.10f)
                                else Color(0xFF0A0E17)
                            )
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isDone -> Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Stage Complete",
                                    tint = EmeraldReady,
                                    modifier = Modifier.size(16.dp)
                                )
                                isActive -> CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = RadiantGold
                                )
                                else -> Text(
                                    text = "${stage.stageOrder}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "[${stage.stageOrder}] ${stage.sourceFilePath} • ${stage.symbolOrEntrypoint}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDone) MaterialTheme.colorScheme.onSurface else accentColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stage.codeSnippet,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = RadiantGold.copy(alpha = 0.9f),
                                fontFamily = FontFamily.Monospace
                            )
                            if (isDone) {
                                Text(
                                    text = "-> ${stage.runtimeResult}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldReady
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Upstream Bootstrap Code Snippet Box
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Client Bootstrap Code (${bootSpec.primaryEntrypointFile}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF070A11))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = bootSpec.bootstrapCodeBlock,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFD6E4FF)
                    )
                }
            }

            // Action Buttons: Re-run Code Boot / Dispatch Native URI / View Upstream Source
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRerunRepoBoot,
                    enabled = !isBooting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color(0xFF060A12)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("base_client_rerun_boot_button")
                ) {
                    Icon(
                        imageVector = if (allStagesComplete) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBooting) "Running Boot Code..." else "Re-Run Boot Code",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { onLaunchSchemeOrExternalRepo(bootSpec.deepLinkSchemeUri) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("base_client_launch_scheme_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dispatch ${bootSpec.deepLinkSchemeUri}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                OutlinedButton(
                    onClick = { onLaunchSchemeOrExternalRepo(bootSpec.rawBootstrapCodeUrl) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("base_client_view_source_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Upstream Entrypoint Blob",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
