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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.NexusHubBridgeService
import com.example.data.WikiEntry
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold

data class NonCardAvatarDossier(
    val id: String,
    val name: String,
    val level: Int,
    val gridStats: String,
    val flavorFiltersSummary: String,
    val aspectPenalty1a: Boolean,
    val flavorDriftNote: String,
    val timeTravelFiltersEnabled: Set<Int>, // ONLY allowed here (19, 20, 21) — NEVER on match cards!
    val causalQuarantineActive: Boolean,
    val offlineNpcThoughtStream: String,
    val twoGateAuthorized: Boolean
)

data class MemorialForgeRecord(
    val id: String,
    val title: String,
    val eraTag: String,
    val icSummary: String,
    val eventFlagged: Boolean,
    val playerVotes: Int
)

/**
 * Section 4: Lore, Avatars & Memorial Forge
 * - Cosmology & Wikis (`wikis.json`)
 * - Multi-Avatar Roster (Non-Card Avatar = 0 deck slots) + Avatar-Only Time-Travel Dossier (Filters 19–21)
 *   + Setting-Driven Tournament Mode toggles
 * - Event-Flagged + Player-Voted Full-Lifespan Memorial Forge Archive with Copy/Export Markdown
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoreAvatarsMemorialScreen(
    wikis: List<WikiEntry>,
    icFeedEntries: List<String>,
    onStatusMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var subTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Cosmology Wikis", "Avatars & Rules", "Memorial Forge")

    // Setting-Driven Tournament Mode Toggles
    var isLoreCombatMode by rememberSaveable { mutableStateOf(false) }
    var isPermadeathEnabled by rememberSaveable { mutableStateOf(false) }
    var manualInputCheckLockout by rememberSaveable { mutableStateOf(true) }
    var mandatoryMilitaryDeployment by rememberSaveable { mutableStateOf(false) }
    var sameAccountMultiAvatar by rememberSaveable { mutableStateOf(true) }

    // Multi-Avatar Roster (Non-Card Avatars — 0 Deck Slots)
    val avatars = remember {
        mutableStateListOf(
            NonCardAvatarDossier(
                id = "AV_01",
                name = "Vanguard Envoy Kael (Non-Card Avatar)",
                level = 14,
                gridStats = "3D Grid: MOV 4 • RNG 3 • MAT Tier 5",
                flavorFiltersSummary = "Filters 1–85 & Xa Openly Active",
                aspectPenalty1a = false,
                flavorDriftNote = "Drift 0.04 (Stable UMFT Spin)",
                timeTravelFiltersEnabled = setOf(19, 20),
                causalQuarantineActive = true,
                offlineNpcThoughtStream = "Server-Synced Clock active; standby garrison holding northern planar gate.",
                twoGateAuthorized = true
            ),
            NonCardAvatarDossier(
                id = "AV_02",
                name = "Archivist Lyra (Non-Card Avatar)",
                level = 9,
                gridStats = "3D Grid: MOV 3 • RNG 5 • MAT Tier 4",
                flavorFiltersSummary = "Filters 1–42 & Xa Openly Active",
                aspectPenalty1a = true,
                flavorDriftNote = "Drift 0.12 (Observational)",
                timeTravelFiltersEnabled = emptySet(),
                causalQuarantineActive = false,
                offlineNpcThoughtStream = "Cataloging Planet Cray & Nested-Planar duel telemetry.",
                twoGateAuthorized = false
            )
        )
    }

    // Memorial Forge Archive
    val memorials = remember {
        mutableStateListOf(
            MemorialForgeRecord(
                id = "MEM_01",
                title = "Founding of the Nexus 20-Color Meridian",
                eraTag = "Cycle I • Full-Lifespan Archive",
                icSummary = "Inaugural cross-realm duel bridging Yumi Muto's Maximum-Power Mat with the Nested-Planar 5-Anchor Stack.",
                eventFlagged = true,
                playerVotes = 14
            ),
            MemorialForgeRecord(
                id = "MEM_02",
                title = "Defense of the PXZ Frontline Bridge",
                eraTag = "Cycle II • Event-Flagged Chronicle",
                icSummary = "Five frontline avatars synchronized a 150% XP Cross-Hit finisher without breaking Causal Quarantine.",
                eventFlagged = true,
                playerVotes = 9
            )
        )
    }

    var newAvatarName by rememberSaveable { mutableStateOf("") }
    var newMemorialTitle by rememberSaveable { mutableStateOf("") }
    var newMemorialText by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("lore_avatars_memorial_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { idx, label ->
                FilterChip(
                    selected = subTab == idx,
                    onClick = { subTab = idx },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("lore_subtab_$idx")
                )
            }
        }

        when (subTab) {
            0 -> {
                // Cosmology & Wikis
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(wikis, key = { it.id }) { wiki ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = wiki.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = wiki.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = wiki.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RadiantGold
                                )
                                HorizontalDivider()
                                Text(
                                    text = wiki.markdownBody,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            1 -> {
                // Multi-Avatar Roster + Avatar-Only Time-Travel Dossier + Tournament Mode Rules
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Setting-Driven Tournament Rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                RuleSwitchRow("Mode: Lore-Combat (OFF = Standard TCG)", isLoreCombatMode) { isLoreCombatMode = it }
                                RuleSwitchRow("Stakes: Permadeath (OFF = Non-Lethal)", isPermadeathEnabled) { isPermadeathEnabled = it }
                                RuleSwitchRow("Input-Check Manual Lockout", manualInputCheckLockout) { manualInputCheckLockout = it }
                                RuleSwitchRow("Mandatory Military Deployment", mandatoryMilitaryDeployment) { mandatoryMilitaryDeployment = it }
                                RuleSwitchRow("Same-Account Multi-Avatar Roster", sameAccountMultiAvatar) { sameAccountMultiAvatar = it }
                            }
                        }
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Create Non-Card Avatar (0 Deck Slots)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newAvatarName,
                                        onValueChange = { newAvatarName = it },
                                        placeholder = { Text("Avatar name...") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val clean = newAvatarName.trim()
                                            if (clean.isNotEmpty()) {
                                                avatars.add(
                                                    NonCardAvatarDossier(
                                                        id = "AV_${avatars.size + 1}",
                                                        name = clean,
                                                        level = 1,
                                                        gridStats = "3D Grid: MOV 3 • RNG 2 • MAT Tier 1",
                                                        flavorFiltersSummary = "Filters 1–85 & Xa Openly Active",
                                                        aspectPenalty1a = false,
                                                        flavorDriftNote = "Drift 0.00 (Fresh)",
                                                        timeTravelFiltersEnabled = emptySet(),
                                                        causalQuarantineActive = true,
                                                        offlineNpcThoughtStream = "Server-Synced Clock initialized.",
                                                        twoGateAuthorized = false
                                                    )
                                                )
                                                newAvatarName = ""
                                                onStatusMessage("Created Non-Card Avatar '$clean' (0 deck slots).")
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    }

                    items(avatars, key = { it.id }) { av ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(av.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Lv.${av.level} • 0 Deck Slots", style = MaterialTheme.typography.labelSmall, color = RadiantGold)
                                }
                                Text(av.gridStats, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                Text(
                                    text = "${av.flavorFiltersSummary} • Aspect-Penalty 1a=${av.aspectPenalty1a} • ${av.flavorDriftNote}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Avatar-Only Time-Travel Dossier Block (NEVER on match cards!)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CrimsonAlert.copy(alpha = 0.08f))
                                        .border(1.dp, CrimsonAlert.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "AVATAR-ONLY TIME-TRAVEL DOSSIER (Filters 19–21 • Blocked on Match Cards)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CrimsonAlert,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Active Filters: ${if (av.timeTravelFiltersEnabled.isEmpty()) "None" else av.timeTravelFiltersEnabled.joinToString(", ")} • Causal Quarantine=${av.causalQuarantineActive} • Two-Gate Auth=${av.twoGateAuthorized}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Offline NPC AI Thought-Stream: ${av.offlineNpcThoughtStream}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Memorial Forge Archive
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                Text("Memorial Forge — Full-Lifespan Chronicle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Event-Flagged + Player-Voted canon archive fed strictly by In-Character (IC) play. Export clean Markdown anytime.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                val fullMarkdown = remember(memorials.size, icFeedEntries.size) {
                                    buildString {
                                        appendLine("# NexusTCG Memorial Forge Archive")
                                        appendLine()
                                        memorials.forEach { m ->
                                            appendLine("## ${m.title} (${m.eraTag})")
                                            appendLine("- Votes: ${m.playerVotes} | Event-Flagged: ${m.eventFlagged}")
                                            appendLine()
                                            appendLine(m.icSummary)
                                            appendLine()
                                        }
                                        if (icFeedEntries.isNotEmpty()) {
                                            appendLine("## Recent Tabletop IC Feed")
                                            icFeedEntries.forEach { appendLine("- $it") }
                                        }
                                    }
                                }

                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(fullMarkdown))
                                            onStatusMessage("Copied Memorial Forge Markdown to clipboard.")
                                        },
                                        modifier = Modifier.testTag("memorial_copy_md_button")
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Markdown")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            NexusHubBridgeService.shareTextArtifact(
                                                context = context,
                                                title = "memorial-forge-archive.md",
                                                content = fullMarkdown,
                                                mimeType = "text/markdown"
                                            )
                                        },
                                        modifier = Modifier.testTag("memorial_share_md_button")
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export .md")
                                    }
                                }

                                OutlinedTextField(
                                    value = newMemorialTitle,
                                    onValueChange = { newMemorialTitle = it },
                                    label = { Text("New Memorial Entry Title") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = newMemorialText,
                                    onValueChange = { newMemorialText = it },
                                    label = { Text("In-Character Chronicle Summary") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        if (newMemorialTitle.isNotBlank() && newMemorialText.isNotBlank()) {
                                            memorials.add(
                                                0,
                                                MemorialForgeRecord(
                                                    id = "MEM_${System.currentTimeMillis()}",
                                                    title = newMemorialTitle.trim(),
                                                    eraTag = "Player-Logged Chronicle",
                                                    icSummary = newMemorialText.trim(),
                                                    eventFlagged = true,
                                                    playerVotes = 1
                                                )
                                            )
                                            newMemorialTitle = ""
                                            newMemorialText = ""
                                            onStatusMessage("Recorded new Memorial Forge chronicle.")
                                        }
                                    }
                                ) {
                                    Text("Record Memorial Entry")
                                }
                            }
                        }
                    }

                    if (icFeedEntries.isNotEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = EmeraldReady.copy(alpha = 0.08f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Live IC Feed from Tabletop (${icFeedEntries.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = EmeraldReady)
                                    icFeedEntries.take(5).forEach { line ->
                                        Text("• $line", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    items(memorials, key = { it.id }) { m ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                        Text(m.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(m.eraTag, style = MaterialTheme.typography.labelSmall, color = RadiantGold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val idx = memorials.indexOfFirst { it.id == m.id }
                                            if (idx >= 0) {
                                                memorials[idx] = m.copy(playerVotes = m.playerVotes + 1)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.ThumbUp, contentDescription = "Vote", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${m.playerVotes}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Text(m.icSummary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleSwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
