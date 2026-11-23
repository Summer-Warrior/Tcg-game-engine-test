package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CardCodeValidator
import com.example.data.CardContextMode
import com.example.data.CardValidationRequest
import com.example.data.HubHandshakeReport
import com.example.data.MutualLinkStatus
import com.example.data.NexusHubBridgeService
import com.example.data.PairCategory
import com.example.data.TwentyColorPieCatalog
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import kotlinx.coroutines.launch

/**
 * Settings Panel: "Connected to Project MPV RP Master Hub".
 *
 * Displays:
 * - `nexus-app.json` routing contract & live GitHub mutual handshake verification.
 * - Discord Account-Lock OAuth2 status (zero client-side Bot Tokens or client_secret).
 * - Strict Locked-Client Firewall & Non-Locked Code-String Metadata Exporter for Card Maker.
 * - Interactive 20-Color Pie Full Progression (Layers 0–20 + {C} + {Ω} Omnichroma) with
 *   zero default colors selected and strict Match Card vs Avatar Dossier Time-Travel validation.
 * - "Return to Master Hub" launcher (opens installed `nexus-master-hub://open` app or Web fallback).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MasterHubConnectionPanel(
    signedInAccountLabel: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val isMasterHubInstalled = remember(context) {
        NexusHubBridgeService.isMasterHubAppInstalled(context)
    }

    var handshakeReport by remember { mutableStateOf<HubHandshakeReport?>(null) }
    var isCheckingHandshake by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    // 20-Color Pie interactive state: STRICTLY zero default selected on load
    var selectedPieCodes by remember { mutableStateOf(TwentyColorPieCatalog.defaultSelectedCodes) }
    var selectedPairCategory by rememberSaveable { mutableStateOf(PairCategory.ALLIED_ANCHORS.name) }
    var selectedContextModeName by rememberSaveable { mutableStateOf(CardContextMode.MATCH_CARD.name) }
    var selectedTargetId by rememberSaveable { mutableStateOf("ULTIMATE_TCG_21DIGIT_FORK") }

    val activeContextMode = remember(selectedContextModeName) {
        CardContextMode.entries.firstOrNull { it.name == selectedContextModeName } ?: CardContextMode.MATCH_CARD
    }
    val activeTarget = remember(selectedTargetId) {
        CardCodeValidator.canonicalTargetCatalog.firstOrNull { it.clientId == selectedTargetId }
            ?: CardCodeValidator.canonicalTargetCatalog.first()
    }
    val activePairCategory = remember(selectedPairCategory) {
        PairCategory.entries.firstOrNull { it.name == selectedPairCategory } ?: PairCategory.ALLIED_ANCHORS
    }

    val liveCustomPip = remember(selectedPieCodes) {
        TwentyColorPieCatalog.formatCustomLayerPip(selectedPieCodes)
    }

    val liveValidation = remember(activeTarget, activeContextMode, liveCustomPip) {
        CardCodeValidator.validate(
            CardValidationRequest(
                cardId = "000000000000000000001",
                cardName = "Nexus Bridge Sample",
                targetClient = activeTarget,
                contextMode = activeContextMode,
                pipExpression = liveCustomPip,
                requestedFilterNumbers = if (activeContextMode == CardContextMode.AVATAR_DOSSIER) setOf(19, 20, 21) else emptySet(),
                temporalEffects = setOf("Extra Turn")
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("master_hub_connection_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
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
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "Project MPV RP Master Hub",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Connected to Project MPV RP Master Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "nexus-app.json • ${NexusHubBridgeService.DISTRIBUTION_POLICY}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Contract metadata summary
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "App Repo: ${NexusHubBridgeService.THIS_REPO}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Deep Link: ${NexusHubBridgeService.DEEP_LINK_SCHEME} • Package: ${NexusHubBridgeService.ANDROID_PACKAGE_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Linked Hub: ${NexusHubBridgeService.LINKED_MASTER_HUB}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Linked Card Maker: ${NexusHubBridgeService.LINKED_CARD_MAKER}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Master Hub Native App: ${if (isMasterHubInstalled) "Detected (nexus-master-hub://open)" else "Not installed (uses Web Portal fallback)"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMasterHubInstalled) EmeraldReady else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Discord Account-Lock Verification (OAuth2 / Zero Bot Tokens)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Discord Account-Lock Verification",
                        tint = EmeraldReady
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Discord Account-Lock: DISCORD_SIGNED_IN (OAuth2 Only)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Zero client-side Discord Bot Tokens or client_secret stored. Signed-in session: $signedInAccountLabel. Public web portal enforces Discord OAuth2 Implicit Grant in memory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Handshake + Return to Master Hub + Export Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val result = NexusHubBridgeService.openMasterHub(context)
                        actionFeedback = result.statusMessage
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("return_to_master_hub_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Return to Master Hub")
                }

                OutlinedButton(
                    onClick = {
                        if (!isCheckingHandshake) {
                            isCheckingHandshake = true
                            scope.launch {
                                val report = NexusHubBridgeService.verifyMutualRepoHandshake()
                                handshakeReport = report
                                actionFeedback = "${report.status.displayLabel}: ${report.summaryMessage}"
                                isCheckingHandshake = false
                            }
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("verify_master_hub_handshake_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isCheckingHandshake) "Checking..." else "Check Link")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val json = NexusHubBridgeService.buildNonLockedCodeStringExportJson()
                        clipboard.setText(AnnotatedString(json))
                        actionFeedback = "Copied non-locked code-string metadata JSON for MPV-TCG-Card-Making-Software-Updated (${json.length} chars)."
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("copy_codestring_metadata_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Code-String JSON")
                }

                OutlinedButton(
                    onClick = {
                        val manifestJson = NexusHubBridgeService.buildNexusAppManifestJson()
                        clipboard.setText(AnnotatedString(manifestJson))
                        actionFeedback = "Copied nexus-app.json manifest to clipboard."
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("copy_nexus_app_manifest_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manifest")
                }
            }

            handshakeReport?.let { report ->
                val statusColor = when (report.status) {
                    MutualLinkStatus.LINKED_AND_CURRENT -> EmeraldReady
                    MutualLinkStatus.ONE_WAY_ONLY, MutualLinkStatus.LINKED_OUT_OF_SYNC -> RadiantGold
                    MutualLinkStatus.UNLINKED -> MaterialTheme.colorScheme.error
                }
                Text(
                    text = "GitHub Bridge Status: ${report.status.displayLabel} — ${report.summaryMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider()

            // Non-Locked Target Firewall & Time-Travel Client Rule Preview
            Text(
                text = "Non-Locked Client Firewall & 21-Digit Card Validator",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardCodeValidator.canonicalTargetCatalog.forEach { target ->
                    FilterChip(
                        selected = target.clientId == activeTarget.clientId,
                        onClick = { selectedTargetId = target.clientId },
                        label = {
                            Text(
                                if (target.upstreamSourceLock) "${target.clientId} (Locked)" else target.clientId,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = if (target.upstreamSourceLock) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked Upstream Reference",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("target_client_chip_${target.clientId}")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardContextMode.entries.forEach { mode ->
                    FilterChip(
                        selected = activeContextMode == mode,
                        onClick = { selectedContextModeName = mode.name },
                        label = { Text(mode.displayLabel, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("card_context_mode_${mode.name}")
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (liveValidation.isValid) {
                    EmeraldReady.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (liveValidation.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (liveValidation.isValid) EmeraldReady else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (liveValidation.isValid) {
                                "Valid Non-Locked Target • Pip $liveCustomPip • allowTimeTravelFlagsOnMatchCards=false"
                            } else {
                                liveValidation.errors.firstOrNull().orEmpty()
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (liveValidation.lockedClientBlocked && liveValidation.suggestedForkClientId != null) {
                        OutlinedButton(
                            onClick = { selectedTargetId = liveValidation.suggestedForkClientId },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("target_non_locked_fork_button")
                        ) {
                            Text("Target Non-Locked 21-Digit Fork Instead (${liveValidation.suggestedForkClientId})")
                        }
                    }
                }
            }

            HorizontalDivider()

            // 20-Color Pie Full Progression Picker (Zero Default Selected)
            Text(
                text = "20-Color Pie — Layers 0–20 & Omnichroma {Ω}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (selectedPieCodes.isEmpty()) {
                    "Pie Selection: None — Showing All Pips (21 Single+Colorless • 190 Option B Pairs • 17 Curated Trios • 20 Layer 19 • Layer 20 {Ω})"
                } else {
                    "Live Layer ${selectedPieCodes.size} Pip: $liveCustomPip"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { selectedPieCodes = TwentyColorPieCatalog.validColorCodes },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("pie_select_all_20_button")
                ) {
                    Text("Select All 20 (Preview {Ω})")
                }
                OutlinedButton(
                    onClick = { selectedPieCodes = emptySet() },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("pie_reset_all_button")
                ) {
                    Text("Reset All")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TwentyColorPieCatalog.wheelColors.forEach { entry ->
                    val isSelected = entry.code in selectedPieCodes
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) RadiantGold.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) RadiantGold else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedPieCodes = if (isSelected) {
                                    selectedPieCodes - entry.code
                                } else {
                                    selectedPieCodes + entry.code
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("pie_color_chip_${entry.code}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(entry.hexArgb))
                                .border(0.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        )
                        Text(
                            text = entry.pip,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (entry.isAnchor) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Layer 2 Pair Category Breakdown (All 190 pairs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PairCategory.entries.forEach { cat ->
                    val countInCat = remember(cat, selectedPieCodes) {
                        TwentyColorPieCatalog.all190FusedPairs.count { pair ->
                            pair.category == cat && (selectedPieCodes.isEmpty() || selectedPieCodes.all { it in pair.codes })
                        }
                    }
                    FilterChip(
                        selected = activePairCategory == cat,
                        onClick = { selectedPairCategory = cat.name },
                        label = { Text("${cat.displayName} ($countInCat)", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            val samplePairs = remember(activePairCategory, selectedPieCodes) {
                TwentyColorPieCatalog.all190FusedPairs
                    .filter { pair ->
                        pair.category == activePairCategory &&
                            (selectedPieCodes.isEmpty() || selectedPieCodes.all { it in pair.codes })
                    }
                    .take(8)
            }
            Text(
                text = if (samplePairs.isEmpty()) {
                    "No Layer 2 pairs match >2 selected colors — view Live Custom Layer ${selectedPieCodes.size} Pip $liveCustomPip."
                } else {
                    "Layer 2 Sample (${activePairCategory.displayName}): ${samplePairs.joinToString("  ") { it.pip }}"
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            actionFeedback?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
