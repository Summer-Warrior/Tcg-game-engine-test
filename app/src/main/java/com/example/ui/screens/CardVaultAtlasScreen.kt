package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.AlignmentCheckReport
import com.example.data.AlignmentHealthState
import com.example.data.CodeStringExporter
import com.example.data.CustomDeck
import com.example.data.FederatedCardPack
import com.example.data.ImportedMpvCard
import com.example.data.NexusHubBridgeService
import com.example.engine.CatalogBlueprint
import com.example.ui.components.TwentyColorPieAtlasView
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import com.example.viewmodel.UiState
import kotlinx.coroutines.launch

/**
 * Section 2: Card Vault & 20-Color Atlas
 * 5 clean sub-tabs:
 * 0 -> Card Library (21-digit BigInt IDs, federated packs, imported MPV packs, Test on Tabletop)
 * 1 -> Custom Card Forge (strictly zero time-travel flags on match cards; wraps DeckForgeScreen)
 * 2 -> Deck Builder (uncapped deck ceiling, 5-copy default + overrides, Main/Extra-Apex/15+ Resource Sub-Decks)
 * 3 -> 20-Color Pie Atlas (Option B Diamond pips + 6 bespoke Layer 20 {Ω} geometries)
 * 4 -> MPV Bridge / Handshake Area (Dual-Engine Consent, Option A Direct PAT commit w/ 409/422 retry, Option B Copy/Share)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardVaultAtlasScreen(
    initialSubTab: Int,
    selectedBaseVariantId: String,
    decksState: UiState<List<CustomDeck>>,
    federatedCardPacks: List<FederatedCardPack>,
    customSynthesizedCombos: List<CatalogBlueprint>,
    alignmentReport: AlignmentCheckReport?,
    onSelectBaseVariant: (String) -> Unit,
    onCreateDeck: (String, String, String, String, Long, Long, Long, String, String, String, String, String, String) -> Unit,
    onDeleteDeck: (String) -> Unit,
    onBroadcastDeckToDiscord: (CustomDeck) -> Unit,
    onTestImportedCardOnTabletop: (ImportedMpvCard) -> Unit,
    onAddPulledCardPacks: (List<FederatedCardPack>) -> Unit,
    onRerunStartupAlignment: () -> Unit,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var activeSubTab by rememberSaveable { mutableIntStateOf(initialSubTab.coerceIn(0, 4)) }
    LaunchedEffect(initialSubTab) {
        if (initialSubTab in 0..4) activeSubTab = initialSubTab
    }

    val subTabs = listOf(
        "Card Library",
        "Custom Forge",
        "Deck Builder",
        "20-Color Atlas",
        "MPV Bridge"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("card_vault_atlas_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            subTabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = activeSubTab == idx,
                    onClick = { activeSubTab = idx },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("vault_subtab_$idx")
                )
            }
        }

        when (activeSubTab) {
            0 -> {
                // Card Library
                var libraryQuery by rememberSaveable { mutableStateOf("") }
                val allCards = remember(federatedCardPacks, libraryQuery) {
                    val q = libraryQuery.trim().lowercase()
                    federatedCardPacks.flatMap { pack ->
                        pack.cards.map { pack.packName to it }
                    }.filter { (packName, card) ->
                        q.isEmpty() ||
                            card.name.lowercase().contains(q) ||
                            card.cardId.lowercase().contains(q) ||
                            card.targetClientId.lowercase().contains(q) ||
                            packName.lowercase().contains(q)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = libraryQuery,
                            onValueChange = { libraryQuery = it },
                            placeholder = { Text("Filter by card name, 21-digit ID, or target client...") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    items(allCards, key = { (_, c) -> "${c.targetClientId}_${c.cardId}" }) { (packName, card) ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(card.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = card.pipCost,
                                        fontFamily = FontFamily.Monospace,
                                        color = RadiantGold,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Text(
                                    text = "ID: ${card.cardId} • Target: ${card.targetClientId} • Pack: $packName",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "ATK ${card.atk21} / DEF ${card.def21} • ${card.cardType} • ${card.effectType}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(card.ability, style = MaterialTheme.typography.bodySmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { onTestImportedCardOnTabletop(card) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test on Tabletop", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Custom Card Forge (Strict Time-Travel Rule enforced: zero Filters 19-21 on match cards)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RadiantGold.copy(alpha = 0.14f))
                            .border(1.dp, RadiantGold.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Strict Match-Card Rule: Zero Time-Travel Flags (Filters 19–21 are Avatar Dossier only). Only End Turn & Extra Turn temporal effects allowed on match cards.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DeckForgeScreen(
                        selectedBaseVariantId = selectedBaseVariantId,
                        decksState = decksState,
                        onSelectBaseVariant = onSelectBaseVariant,
                        onCreateDeck = onCreateDeck,
                        onDeleteDeck = onDeleteDeck,
                        onBroadcastDeckToDiscord = onBroadcastDeckToDiscord
                    )
                }
            }

            2 -> {
                // Deck Builder (Uncapped ceiling, 5-copy default + overrides, Main/Extra-Apex/15+ Resource Sub-Decks)
                val decks = (decksState as? UiState.Success)?.data.orEmpty()
                var copyLimitOverride by rememberSaveable { mutableIntStateOf(5) }
                var activeResourceSubDecks by rememberSaveable { mutableIntStateOf(15) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ultimate Deck Architecture (Uncapped Ceiling)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Default 5 copies per card (customizable override below) • Main Deck + Extra/Apex Deck + $activeResourceSubDecks Resource Sub-Decks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { copyLimitOverride = (copyLimitOverride - 1).coerceAtLeast(1) }) {
                                        Text("- Copy Cap")
                                    }
                                    Button(onClick = { copyLimitOverride += 1 }) {
                                        Text("Copy Cap: $copyLimitOverride")
                                    }
                                    OutlinedButton(onClick = { activeResourceSubDecks += 1 }) {
                                        Text("+ Sub-Deck ($activeResourceSubDecks)")
                                    }
                                }
                            }
                        }
                    }

                    items(decks, key = { it.id }) { deck ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(deck.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Variant: ${deck.baseVariantId} • Ace: ${deck.customCardName} • Cap: $copyLimitOverride copies • $activeResourceSubDecks Resource Sub-Decks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onSelectBaseVariant(deck.baseVariantId) }) {
                                        Text("Activate Variant")
                                    }
                                    OutlinedButton(onClick = { onBroadcastDeckToDiscord(deck) }) {
                                        Text("Broadcast Discord")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // 20-Color Pie Atlas
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        TwentyColorPieAtlasView(
                            onCopyPipCode = { pip ->
                                clipboard.setText(AnnotatedString(pip))
                                onStatusMessage("Copied pip syntax: $pip")
                            }
                        )
                    }
                }
            }

            4 -> {
                // MPV Bridge / Handshake Area
                var localConsent by remember { mutableStateOf(NexusHubBridgeService.getLocalConsentState(context)) }
                var cardMakerBranch by rememberSaveable { mutableStateOf("main") }
                var runtimePat by remember { mutableStateOf("") } // In-memory ONLY — never persisted!
                var selectedCommitTarget by rememberSaveable { mutableStateOf("client-codestring-index.json") }
                var isBusy by remember { mutableStateOf(false) }
                var bridgeLog by remember { mutableStateOf("Ready for Bidirectional GitHub Bridge & MPV Card Maker Handshake.") }

                val mutualVerified = alignmentReport?.status == AlignmentHealthState.LINKED_AND_CURRENT ||
                    alignmentReport?.status == AlignmentHealthState.LINKED_OUT_OF_SYNC

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("mpv_bridge_handshake_area"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status + Installed App Badge
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                    Text("MPV Card Maker <-> NexusTCG Handshake", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = alignmentReport?.status?.badgeLabel ?: "CHECKING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (alignmentReport?.status == AlignmentHealthState.LINKED_AND_CURRENT) EmeraldReady else RadiantGold
                                    )
                                }

                                val installed = alignmentReport?.installedCardMaker
                                    ?: NexusHubBridgeService.detectInstalledCardMakerApp(context)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = if (installed.isInstalled) EmeraldReady else RadiantGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (installed.isInstalled) {
                                            "Local App Detected: ${installed.packageName} v${installed.versionName} (${installed.versionCode})"
                                        } else {
                                            "Local App Not Installed: ${installed.packageName} (Web Portal fallback ready)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Text(
                                    text = bridgeLog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val res = NexusHubBridgeService.openCardMakerAppForHandshake(context)
                                            bridgeLog = res.statusMessage
                                            onStatusMessage(res.statusMessage)
                                        },
                                        modifier = Modifier.testTag("bridge_open_card_maker_button")
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Card Maker App")
                                    }

                                    OutlinedButton(
                                        onClick = onRerunStartupAlignment,
                                        modifier = Modifier.testTag("bridge_recheck_alignment_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Re-Check Alignment")
                                    }

                                    OutlinedButton(
                                        enabled = !isBusy,
                                        onClick = {
                                            isBusy = true
                                            scope.launch {
                                                val pulled = NexusHubBridgeService.pullCardPacksFromCardMaker(
                                                    branch = cardMakerBranch
                                                )
                                                if (pulled.importedPacks.isNotEmpty()) {
                                                    onAddPulledCardPacks(pulled.importedPacks)
                                                }
                                                bridgeLog = pulled.summaryMessage
                                                onStatusMessage(pulled.summaryMessage)
                                                isBusy = false
                                            }
                                        },
                                        modifier = Modifier.testTag("bridge_pull_card_packs_button")
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pull MPV Card Packs")
                                    }
                                }
                            }
                        }
                    }

                    // Dual-Engine Consent Gate
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Dual-Engine Schema Consent (v${NexusHubBridgeService.BRIDGE_SCHEMA_VERSION})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Rule: NO_SCHEMA_SYNC_OR_EXPORT_MIGRATION_WITHOUT_BOTH_CONSENTED. Both NexusTCG Engine and MPV Card Maker must record consent for v${NexusHubBridgeService.BRIDGE_SCHEMA_VERSION}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("NexusTCG Engine Consent Signed", style = MaterialTheme.typography.labelMedium)
                                    Switch(
                                        checked = localConsent.gameEngineConsented,
                                        onCheckedChange = { checked ->
                                            localConsent = NexusHubBridgeService.setLocalConsentState(
                                                context = context,
                                                gameEngineConsented = checked,
                                                cardMakerConsentedObserved = localConsent.cardMakerConsentedObserved
                                            )
                                            bridgeLog = "Updated NexusTCG Engine consent = $checked."
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Option B — Zero-Token Copy & Share Artifacts
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Option B — Zero-Token Copy / Share Sync Files", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                val indexJson = remember(customSynthesizedCombos) {
                                    CodeStringExporter.compileClientCodeStringIndexJson(customSynthesizedCombos)
                                }
                                val repoLinkJson = remember(cardMakerBranch) {
                                    CodeStringExporter.buildRepoLinkManifestJson(cardMakerBranch = cardMakerBranch)
                                }
                                val contractJson = remember(localConsent) {
                                    CodeStringExporter.buildHandshakeContractJson(
                                        gameEngineConsented = localConsent.gameEngineConsented,
                                        cardMakerConsented = alignmentReport?.cardMakerConsentSigned ?: localConsent.cardMakerConsentedObserved,
                                        recordedBy = localConsent.recordedBy,
                                        recordedAtUtc = localConsent.recordedAtUtc
                                    )
                                }
                                val workflowYaml = remember { CodeStringExporter.buildGitHubActionsSyncWorkflowYaml() }

                                val artifacts = listOf(
                                    Triple("client-codestring-index.json", indexJson, "application/json"),
                                    Triple("nexus-repo-link.json", repoLinkJson, "application/json"),
                                    Triple("handshake-contract.json", contractJson, "application/json"),
                                    Triple("nexus-mpv-sync.yml", workflowYaml, "text/yaml")
                                )

                                artifacts.forEach { (name, payload, mime) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(payload))
                                                    onStatusMessage("Copied $name to clipboard.")
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy $name", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    NexusHubBridgeService.shareTextArtifact(context, name, payload, mime)
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = "Share $name", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Share", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Option A — Direct GitHub Commit (Runtime PAT Only, Mutual Link Gated)
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Option A — Direct GitHub Commit (GET-SHA + PUT w/ 409/422 Retry)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "PAT stays strictly in memory for this session (never stored or hardcoded). Requires mutual repo link verification.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (mutualVerified) EmeraldReady else CrimsonAlert
                                )

                                OutlinedTextField(
                                    value = cardMakerBranch,
                                    onValueChange = { cardMakerBranch = it },
                                    label = { Text("Target Branch") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = runtimePat,
                                    onValueChange = { runtimePat = it },
                                    label = { Text("Fine-Grained GitHub PAT (Contents: R/W, Session Memory Only)") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        "client-codestring-index.json",
                                        "nexus-bridge/client-codestring-index.json",
                                        "handshake-contract.json",
                                        "nexus-repo-link.json"
                                    ).forEach { target ->
                                        FilterChip(
                                            selected = selectedCommitTarget == target,
                                            onClick = { selectedCommitTarget = target },
                                            label = { Text(target, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }

                                Button(
                                    enabled = !isBusy,
                                    onClick = {
                                        isBusy = true
                                        scope.launch {
                                            val body = when (selectedCommitTarget) {
                                                "handshake-contract.json" -> CodeStringExporter.buildHandshakeContractJson(
                                                    gameEngineConsented = localConsent.gameEngineConsented,
                                                    cardMakerConsented = alignmentReport?.cardMakerConsentSigned ?: localConsent.cardMakerConsentedObserved,
                                                    recordedBy = localConsent.recordedBy,
                                                    recordedAtUtc = localConsent.recordedAtUtc
                                                )
                                                "nexus-repo-link.json" -> CodeStringExporter.buildRepoLinkManifestJson(cardMakerBranch)
                                                else -> CodeStringExporter.compileClientCodeStringIndexJson(customSynthesizedCombos)
                                            }
                                            val result = NexusHubBridgeService.commitFileToGitHubRepo(
                                                ownerRepo = NexusHubBridgeService.THIS_REPO,
                                                branch = cardMakerBranch,
                                                filePath = selectedCommitTarget,
                                                contentUtf8 = body,
                                                commitMessage = "chore(nexus-bridge): sync $selectedCommitTarget (v${NexusHubBridgeService.BRIDGE_SCHEMA_VERSION})",
                                                fineGrainedPat = runtimePat,
                                                mutualLinkVerified = mutualVerified
                                            )
                                            bridgeLog = result.message
                                            onStatusMessage(result.message)
                                            isBusy = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color(0xFF060A12)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("bridge_direct_commit_button")
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Commit $selectedCommitTarget", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
