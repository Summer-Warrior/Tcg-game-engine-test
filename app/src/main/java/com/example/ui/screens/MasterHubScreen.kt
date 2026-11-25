package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AlignmentCheckReport
import com.example.data.AlignmentHealthState
import com.example.data.CodeStringExporter
import com.example.data.CompanionAppEntry
import com.example.data.NexusHubBridgeService
import com.example.engine.BoardArchetypeTemplate
import com.example.engine.CatalogBlueprint
import com.example.engine.ComboSynthesizer
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.chameleonAccentFor
import com.example.ui.theme.duelDiskPlate
import com.example.ui.theme.rememberChamferedDuelShape

/**
 * Section 1: Master Hub — 160+ Multi-TCG Catalog Browser, 4-Tier Detail Sheet,
 * AI Combo Architect (Copy-on-Fork), Companion Apps & Pegasus Arcade, and MPV Handshake Pill.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MasterHubScreen(
    catalogBlueprints: List<CatalogBlueprint>,
    companionApps: List<CompanionAppEntry>,
    alignmentReport: AlignmentCheckReport?,
    activeFormatBlueprintId: String,
    onSelectBlueprintForTabletop: (CatalogBlueprint) -> Unit,
    onAddSynthesizedCombo: (CatalogBlueprint) -> Unit,
    onOpenSeparateClientWindow: (String) -> Unit,
    onOpenHandshakeArea: () -> Unit,
    onStatusMessage: (String) -> Unit,
    splitOffSetCount: Int = 0,
    trackedUsagePlays: Int = 0
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("ALL") }
    var inspectedBlueprint by remember { mutableStateOf<CatalogBlueprint?>(null) }
    var showComboArchitect by remember { mutableStateOf(false) }

    val categories = listOf(
        "ALL" to "All (${catalogBlueprints.size})",
        "FLAGSHIP_CUSTOM" to "Ultimate TCG",
        "AI_COMBO" to "AI Combos",
        "LOCKED_GITHUB_CLIENT" to "Locked GitHub",
        "CUSTOM_CLONE" to "21-Digit Clones",
        "NOTEBOOK_FORMAT" to "Notebook Realms",
        "ACTIVE_PHYSICAL" to "Physical TCGs",
        "ACTIVE_DIGITAL" to "Digital TCGs",
        "TCG_VIDEO_GAME" to "Video Games",
        "MINI_GAME" to "Mini-Games",
        "NICHE_INDIE" to "Niche / LCGs",
        "DEFUNCT_PHYSICAL" to "Defunct Physical",
        "DEFUNCT_DIGITAL" to "Defunct Digital"
    )

    val filteredBlueprints = remember(catalogBlueprints, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        catalogBlueprints.filter { bp ->
            val catOk = selectedCategory == "ALL" || bp.category == selectedCategory
            val qOk = q.isEmpty() ||
                bp.name.lowercase().contains(q) ||
                bp.summary.lowercase().contains(q) ||
                bp.boardArchetype.lowercase().contains(q) ||
                bp.id.lowercase().contains(q)
            catOk && qOk
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("master_hub_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Spotlight + MPV Handshake Pill (Battle City Chamfered Duel Disk Chrome)
        item {
            val activeBlueprint = remember(catalogBlueprints, activeFormatBlueprintId) {
                catalogBlueprints.firstOrNull { it.id == activeFormatBlueprintId }
            }
            val heroAccent = chameleonAccentFor(activeBlueprint, activeFormatBlueprintId)
            val heroPillShape = rememberChamferedDuelShape(6.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .duelDiskPlate(
                        accentColor = heroAccent,
                        cutSize = 14.dp,
                        pulseEnergy = true
                    )
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    heroAccent.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NEXUS TCG MASTER HUB • SOLID VISION v4.0",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = heroAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${catalogBlueprints.size} TCG Formats • 11 Archetypes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (splitOffSetCount > 0 || trackedUsagePlays > 0) {
                                Text(
                                    text = "$splitOffSetCount Split-Off Set(s) (100% Canon Retained) • $trackedUsagePlays Tracked Card Plays",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = RadiantGold,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clip(heroPillShape)
                                        .background(RadiantGold.copy(alpha = 0.14f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val status = alignmentReport?.status
                        val statusColor = when (status) {
                            AlignmentHealthState.LINKED_AND_CURRENT -> EmeraldReady
                            AlignmentHealthState.BREAKING_INCOMPATIBLE, AlignmentHealthState.DEGRADED -> CrimsonAlert
                            else -> RadiantGold
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(statusColor.copy(alpha = 0.16f))
                                .border(1.dp, statusColor, RoundedCornerShape(50))
                                .clickable { onOpenHandshakeArea() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("hub_handshake_status_pill")
                        ) {
                            Icon(
                                imageVector = if (status == AlignmentHealthState.LINKED_AND_CURRENT) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.Default.Sync
                                },
                                contentDescription = "MPV Handshake Status",
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = status?.badgeLabel ?: "CHECKING",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }

                    Text(
                        text = "Flagship Ultimate TCG (WIP), 6 curated AI Combos, 7 locked upstream GitHub references (immutable), Notebook Realms, and full physical/digital/video-game TCG catalog.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showComboArchitect = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RadiantGold,
                                contentColor = Color(0xFF0A0E17)
                            ),
                            modifier = Modifier.testTag("open_ai_combo_architect_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Combo Architect", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val res = NexusHubBridgeService.openCardMakerAppForHandshake(context)
                                onStatusMessage(res.statusMessage)
                            },
                            modifier = Modifier.testTag("hub_open_card_maker_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("MPV Card Maker")
                        }

                        OutlinedButton(
                            onClick = onOpenHandshakeArea,
                            modifier = Modifier.testTag("hub_open_handshake_button")
                        ) {
                            Text("Handshake Area")
                        }
                    }
                }
            }
        }

        // Companion Apps & Pegasus Games Arcade Strip
        if (companionApps.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Companion Apps & Pegasus Games Arcade (${companionApps.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(companionApps, key = { it.id }) { app ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .width(210.dp)
                                    .clickable {
                                        if (app.androidPackageName == NexusHubBridgeService.CARD_MAKER_ANDROID_PACKAGE) {
                                            val res = NexusHubBridgeService.openCardMakerAppForHandshake(context)
                                            onStatusMessage(res.statusMessage)
                                        } else if (app.deepLinkScheme.startsWith("nexus-master-hub")) {
                                            val res = NexusHubBridgeService.openMasterHub(context)
                                            onStatusMessage(res.statusMessage)
                                        } else {
                                            val target = app.webUrl.ifBlank { "https://github.com/${app.repo}" }
                                            if (target.isNotBlank()) {
                                                runCatching {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                    )
                                                }
                                                onStatusMessage("Opened ${app.name}")
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (app.isArcadeGame) "PEGASUS ARCADE" else app.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (app.isArcadeGame) RadiantGold else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search Bar + Category Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search 160+ TCGs, archetypes, Notebook formats, AI Combos...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search catalog") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hub_catalog_search_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCategory == key,
                            onClick = { selectedCategory = key },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("hub_category_$key")
                        )
                    }
                }
            }
        }

        // Catalog Cards
        items(filteredBlueprints, key = { it.id }) { bp ->
            val isActive = bp.id == activeFormatBlueprintId
            val archetype = BoardArchetypeTemplate.fromId(bp.boardArchetype)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isActive) 1.5.dp else 1.dp,
                        color = when {
                            isActive -> MaterialTheme.colorScheme.primary
                            bp.upstreamSourceLock -> EmeraldReady.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { inspectedBlueprint = bp }
                    .testTag("catalog_card_${bp.id}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (bp.upstreamSourceLock) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked Upstream Source",
                                        tint = EmeraldReady,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = bp.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Tier ${bp.clientTier} • ${archetype.displayLabel} • ${bp.platforms.joinToString("/")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (bp.upstreamSourceLock) EmeraldReady.copy(alpha = 0.16f)
                                    else RadiantGold.copy(alpha = 0.16f)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (bp.upstreamSourceLock) "LOCKED REF" else "WRITABLE",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (bp.upstreamSourceLock) EmeraldReady else RadiantGold
                            )
                        }
                    }

                    Text(
                        text = bp.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (bp.mechanicModules.isNotEmpty()) {
                        Text(
                            text = "Modules: ${bp.mechanicModules.joinToString(" • ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // 4-Tier Client Detail Bottom Sheet
    inspectedBlueprint?.let { bp ->
        ModalBottomSheet(onDismissRequest = { inspectedBlueprint = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = bp.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ID: ${bp.id} • Tier ${bp.clientTier} • ${BoardArchetypeTemplate.fromId(bp.boardArchetype).displayLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Status: ${if (bp.upstreamSourceLock) "LOCKED TO UPSTREAM GITHUB SOURCE (Immutable)" else "NON-LOCKED WRITABLE (Eligible for MPV Card Maker)"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (bp.upstreamSourceLock) EmeraldReady else RadiantGold
                )
                if (bp.upstreamRepoUrl.isNotBlank()) {
                    Text(
                        text = "Upstream Repo: ${bp.upstreamRepoUrl}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "Protocol Hash: ${bp.protocolHash}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = bp.summary,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (bp.derivedFromLockedIds.isNotEmpty()) {
                    Text(
                        text = "Copy-on-Fork Lineage: derived from ${bp.derivedFromLockedIds.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold
                    )
                }

                HorizontalDivider()

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onSelectBlueprintForTabletop(bp)
                            inspectedBlueprint = null
                        },
                        modifier = Modifier.testTag("sheet_launch_tabletop_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Load on Tabletop")
                    }

                    if (bp.upstreamSourceLock) {
                        OutlinedButton(
                            onClick = {
                                val fork = CatalogBlueprint(
                                    id = "${bp.id}_21D_FORK",
                                    name = "${bp.name} — 21-Digit Non-Locked Fork",
                                    category = "CUSTOM_CLONE",
                                    originType = "CUSTOM_CLONE_21",
                                    boardArchetype = bp.boardArchetype,
                                    upstreamSourceLock = false,
                                    requiresStrictBase = false,
                                    isBaseStandard = false,
                                    upstreamRepoUrl = "",
                                    protocolHash = "sha256:fork-${bp.id.lowercase()}-v1.1",
                                    clientTier = 3,
                                    platforms = listOf("Android", "Web"),
                                    summary = "Copy-on-Fork non-locked 21-digit clone derived from ${bp.name}. Writable in MPV Card Maker.",
                                    derivedFromLockedIds = listOf(bp.id)
                                )
                                onAddSynthesizedCombo(fork)
                                onSelectBlueprintForTabletop(fork)
                                inspectedBlueprint = null
                            },
                            modifier = Modifier.testTag("sheet_fork_locked_client_button")
                        ) {
                            Icon(Icons.Default.ForkRight, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fork as Non-Locked 21-Digit Clone")
                        }
                    }

                    if (bp.id in setOf("EDOPRO_BASE_MR5", "FORGE_MTG_BASE", "EDOPRO_CUSTOM_FORK", "FORGE_CUSTOM_CLONE", "HYBRID_FORGE_EDOPRO_MIX")) {
                        OutlinedButton(
                            onClick = {
                                onOpenSeparateClientWindow(bp.id)
                                inspectedBlueprint = null
                            }
                        ) {
                            Text("Open Client Window")
                        }
                    }
                }
            }
        }
    }

    // AI Combo Architect Dialog
    if (showComboArchitect) {
        AiComboArchitectDialog(
            catalog = catalogBlueprints,
            onDismiss = { showComboArchitect = false },
            onComboCreated = { created ->
                onAddSynthesizedCombo(created)
                onSelectBlueprintForTabletop(created)
                showComboArchitect = false
            },
            onCopyExportJson = { combo ->
                val json = CodeStringExporter.compileClientCodeStringIndexJson(listOf(combo))
                clipboard.setText(AnnotatedString(json))
                onStatusMessage("Copied non-locked code-string index including '${combo.name}'.")
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiComboArchitectDialog(
    catalog: List<CatalogBlueprint>,
    onDismiss: () -> Unit,
    onComboCreated: (CatalogBlueprint) -> Unit,
    onCopyExportJson: (CatalogBlueprint) -> Unit
) {
    var comboTitle by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }
    val selectedModules = remember { mutableStateListOf<String>() }
    var lastSynthesized by remember { mutableStateOf<CatalogBlueprint?>(null) }
    var autoSeed by remember { mutableStateOf(0) }
    var errorNote by remember { mutableStateOf<String?>(null) }

    val topSelectable = remember(catalog) { catalog.take(36) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI & User Combo Architect", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pick 2–4 games/formats + optional Video-Game Mechanic Modules. Locked GitHub base clients are cloned via Copy-on-Fork (originals stay immutable).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = comboTitle,
                    onValueChange = { comboTitle = it },
                    label = { Text("Custom Combo Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val auto = ComboSynthesizer.autoGenerateBalancedAiCombo(catalog, seedIndex = autoSeed)
                        autoSeed += 1
                        lastSynthesized = auto
                        errorNote = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RadiantGold,
                        contentColor = Color(0xFF0A0E17)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_generate_ai_combo_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Generate Balanced AI Combo", fontWeight = FontWeight.Bold)
                }

                Text("Select 2–4 Source Games (${selectedIds.size}/4):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    topSelectable.forEach { bp ->
                        val isPicked = bp.id in selectedIds
                        FilterChip(
                            selected = isPicked,
                            onClick = {
                                if (isPicked) selectedIds.remove(bp.id)
                                else if (selectedIds.size < 4) selectedIds.add(bp.id)
                            },
                            label = {
                                Text(
                                    text = bp.name.substringBefore(" (").take(20),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                Text("Pluggable Video-Game Mechanic Modules:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ComboSynthesizer.pluggableVideoGameModules.forEach { mod ->
                        val active = mod in selectedModules
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (active) selectedModules.remove(mod) else selectedModules.add(mod)
                            },
                            label = { Text(mod, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                errorNote?.let {
                    Text(text = it, color = CrimsonAlert, style = MaterialTheme.typography.labelSmall)
                }

                lastSynthesized?.let { created ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Preview: ${created.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Text("Archetype: ${created.boardArchetype} • Locked: ${created.upstreamSourceLock}", style = MaterialTheme.typography.labelSmall)
                            Text(created.summary, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onComboCreated(created) }) {
                                    Text("Play Combo")
                                }
                                OutlinedButton(onClick = { onCopyExportJson(created) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy JSON")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = catalog.filter { it.id in selectedIds }
                    val res = ComboSynthesizer.synthesizeCustomCombo(
                        comboTitle = comboTitle,
                        selectedSources = chosen,
                        selectedModules = selectedModules.toList()
                    )
                    res.onSuccess {
                        lastSynthesized = it
                        errorNote = null
                    }.onFailure {
                        errorNote = it.message
                    }
                },
                modifier = Modifier.testTag("synthesize_custom_combo_button")
            ) {
                Text("Synthesize")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
