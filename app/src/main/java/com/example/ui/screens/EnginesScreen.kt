package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ClientWindowLauncher
import com.example.R
import com.example.data.BaseVariantSpec
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.TcgVariant
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnginesScreen(
    selectedBaseVariantId: String,
    activeCustomVariant: TcgVariant?,
    variantsState: UiState<List<TcgVariant>>,
    connectedClientCount: Int,
    onSelectBaseVariant: (String) -> Unit,
    onActivateCustomVariant: (TcgVariant) -> Unit,
    onResetToBaseStandard: () -> Unit,
    onCreateVariant: (String, String, String, Long, Long, Long, String) -> Unit,
    onCloneEdoProProject: (String, String, String, Long, String, String, String, String, String) -> Unit,
    onCloneForgeProject: (String, String, String, Long, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onSpawnMixedForgeEdoProClient: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onAddExtensionToMixedClient: (String, String, String) -> Unit = { _, _, _ -> },
    onDeleteVariant: (String) -> Unit,
    onNavigateToClients: () -> Unit,
    onRequestConfirmOpenClientWindow: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCloneEdoProDialog by remember { mutableStateOf(false) }
    var showCloneForgeDialog by remember { mutableStateOf(false) }
    var showSpawnMixedDialog by remember { mutableStateOf(false) }
    var showAddExtensionDialog by remember { mutableStateOf(false) }
    val activeBaseSpec = BuiltInTcgCatalog.findBaseSpec(selectedBaseVariantId)
    val activeUiProfile = BuiltInTcgCatalog.systemUiProfileFor(selectedBaseVariantId)
    val isUnmodifiedBase = activeCustomVariant == null || activeCustomVariant.isBaseStandard

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("engines_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner & Active Protocol Status Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isUnmodifiedBase) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else RadiantGold.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(22.dp)
                    ),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_nexus),
                            contentDescription = "Multi-TCG Engine Hero",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (activeBaseSpec.isCustomProjectClone) {
                                    Icons.Default.CallSplit
                                } else if (isUnmodifiedBase) {
                                    Icons.Default.Lock
                                } else {
                                    Icons.Default.Warning
                                },
                                contentDescription = "Protocol Status",
                                tint = if (activeBaseSpec.isCustomProjectClone) {
                                    RadiantGold
                                } else if (isUnmodifiedBase) {
                                    EmeraldReady
                                } else {
                                    RadiantGold
                                },
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    activeBaseSpec.isCustomProjectClone -> "21-DIGIT CUSTOM CLONE"
                                    isUnmodifiedBase -> "BASE VARIANT LOCKED"
                                    else -> "CUSTOM MOD OVERRIDE"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (activeBaseSpec.isCustomProjectClone) {
                                    RadiantGold
                                } else if (isUnmodifiedBase) {
                                    EmeraldReady
                                } else {
                                    RadiantGold
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = activeBaseSpec.engineCoreLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = activeCustomVariant?.name ?: activeBaseSpec.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Source Repository: ${activeBaseSpec.sourceRepoUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Protocol Hash: ${activeCustomVariant?.protocolHash ?: activeBaseSpec.protocolHash} • Max Variable/Function Precision: ${activeBaseSpec.maxDigitPrecision} Digits",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                activeBaseSpec.baseVariantId == "EDOPRO_BASE_MR5" && isUnmodifiedBase ->
                                    "Running the official ProjectIgnis/EDOPro Base Client variant (8000 LP, Master Rule 5, cards.cdb). Official EDOPro clients connect with strict base verification ($connectedClientCount clients bridged)."
                                activeBaseSpec.baseVariantId == "EDOPRO_CUSTOM_FORK" ->
                                    "Running the separate EDOPro Custom Project Clone (IgnisForge 21-Digit). Supports 21-digit numeric inputs (up to 999,999,999,999,999,999,999) for custom variables, functions, and coding variables for code strings."
                                activeBaseSpec.baseVariantId == "FORGE_MTG_BASE" ->
                                    "Running the standalone Card-Forge (https://github.com/Card-Forge/forge) Magic: The Gathering rules engine (20 Life, 7-card hand, Lands & Mana Pool, The Stack, Command Zone)."
                                activeBaseSpec.baseVariantId == "FORGE_CUSTOM_CLONE" ->
                                    "Running the Clone Card-Forge for Custom Projects (git://custom-clone/Card-Forge/forge-CustomProjects) with 21-digit SVar:, custom cardsfolder/*.txt scripting, and custom Mana Pool scaling."
                                activeBaseSpec.baseVariantId == "HYBRID_FORGE_EDOPRO_MIX" ->
                                    "Running the extensible Mixed Client combining Clone Card-Forge + Clone EDOPro for Custom Project. Unifies MTG Stack + Yu-Gi-Oh! Chain Links with pluggable custom modules that can be added to at any time."
                                isUnmodifiedBase ->
                                    "Running the canonical ${activeBaseSpec.baseVariantId} base variant. External clients requiring this base variant can connect and sync immediately ($connectedClientCount clients bridged)."
                                else ->
                                    "Running custom variant rules (${activeCustomVariant?.startingLp21 ?: activeCustomVariant?.startingLife} LP). Clients with strict base-variant locks require locking Base Standard or switching to a Custom Project Clone."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showCloneEdoProDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clone_edopro_project_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RadiantGold,
                                    contentColor = Color(0xFF1A1300)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallSplit,
                                    contentDescription = "Clone EDOPro for Custom Project",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clone EDOPro (21d)", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { showCloneForgeDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("engines_clone_forge_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF8A50),
                                    contentColor = Color(0xFF1F0900)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallSplit,
                                    contentDescription = "Clone Card-Forge for Custom Project",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clone Card-Forge", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showSpawnMixedDialog = true },
                                modifier = Modifier
                                    .weight(1.15f)
                                    .testTag("engines_spawn_mixed_client_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE040FB),
                                    contentColor = Color(0xFF1A0026)
                                )
                            ) {
                                Text("Clone Mixed (Forge+EDOPro)", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { showAddExtensionDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("engines_add_to_mixed_client_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Extension to Mixed Client",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ Add to Mixed", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onNavigateToClients,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_client_bridge_button")
                        ) {
                            Text("Open Client Bridge & Mixed Client Hub ($connectedClientCount Connected)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                onRequestConfirmOpenClientWindow(selectedBaseVariantId)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_engine_active_window_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DesktopWindows,
                                contentDescription = "Open Active Client System in Separate Window",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open ${activeUiProfile.windowModeTitle} in Separate Window")
                        }
                        if (!isUnmodifiedBase) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onResetToBaseStandard,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reset_base_variant_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Restore Base Variant",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Lock Unmodified Base Variant")
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Canonical Base Variants (EDOPro Base + EDOPro Custom Clone + Multi-TCG)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EDOPro, Card-Forge MTG, Custom Clones & Mixed Client",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "ProjectIgnis/EDOPro + Card-Forge/forge + Custom Clones + Extensible Mixed Client",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(BuiltInTcgCatalog.baseVariants, key = { it.baseVariantId }) { spec ->
            BaseVariantCard(
                spec = spec,
                isSelected = selectedBaseVariantId == spec.baseVariantId && isUnmodifiedBase,
                onSelect = {
                    onSelectBaseVariant(spec.baseVariantId)
                    onRequestConfirmOpenClientWindow(spec.baseVariantId)
                },
                onOpenSeparateWindow = {
                    onSelectBaseVariant(spec.baseVariantId)
                    onRequestConfirmOpenClientWindow(spec.baseVariantId)
                }
            )
        }

        // Section 2: User-Saved & Custom Engine Variants
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saved Engine Profiles & 21-Digit Clones",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Switch between locked EDOPro Base profiles and 21-Digit Custom Clones",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("new_variant_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Engine Variant",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Variant")
                }
            }
        }

        when (variantsState) {
            is UiState.Loading -> {
                item {
                    Text(
                        text = "Loading saved engine variants...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = variantsState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success -> {
                if (variantsState.data.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "No custom variants",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No custom engine profiles saved yet. Tap 'Clone EDOPro (21-Digit)' or 'New Variant' to create one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(variantsState.data, key = { it.id }) { variant ->
                        SavedVariantItemCard(
                            variant = variant,
                            isActive = activeCustomVariant?.id == variant.id ||
                                (activeCustomVariant == null && variant.isBaseStandard && variant.baseVariantId == selectedBaseVariantId),
                            onActivate = { onActivateCustomVariant(variant) },
                            onDelete = { onDeleteVariant(variant.id) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateVariantDialog(
            initialBaseVariantId = selectedBaseVariantId,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, baseId, resName, life, hand, maxRes, combat ->
                onCreateVariant(name, baseId, resName, life, hand, maxRes, combat)
                showCreateDialog = false
            }
        )
    }

    if (showCloneEdoProDialog) {
        CloneEdoProProjectDialog(
            onDismiss = { showCloneEdoProDialog = false },
            onConfirm = { projectName, cdbFile, startingLp21, monsterZones, varInput21, funcArg21, funcName, codeStr21, codeVarExpr21 ->
                onCloneEdoProProject(
                    projectName,
                    cdbFile,
                    startingLp21,
                    monsterZones,
                    varInput21,
                    funcArg21,
                    funcName,
                    codeStr21,
                    codeVarExpr21
                )
                showCloneEdoProDialog = false
            }
        )
    }

    if (showCloneForgeDialog) {
        CloneForgeProjectDialog(
            onDismiss = { showCloneForgeDialog = false },
            onConfirm = { projectName, cardsFolder, startingLife21, maxManaPool, svarInput21, funcArg21, funcName ->
                onCloneForgeProject(
                    projectName,
                    cardsFolder,
                    startingLife21,
                    maxManaPool,
                    svarInput21,
                    funcArg21,
                    funcName
                )
                showCloneForgeDialog = false
            }
        )
    }

    if (showSpawnMixedDialog) {
        SpawnMixedClientDialog(
            onDismiss = { showSpawnMixedDialog = false },
            onConfirm = { mixName, startingLife21, extraModule, varInput21, funcArg21 ->
                onSpawnMixedForgeEdoProClient(
                    mixName,
                    startingLife21,
                    extraModule,
                    varInput21,
                    funcArg21
                )
                showSpawnMixedDialog = false
            }
        )
    }

    if (showAddExtensionDialog) {
        AddExtensionToMixedClientDialog(
            targetClient = null,
            onDismiss = { showAddExtensionDialog = false },
            onConfirm = { moduleName, moduleCategory, varInput21 ->
                onAddExtensionToMixedClient(moduleName, moduleCategory, varInput21)
                showAddExtensionDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaseVariantCard(
    spec: BaseVariantSpec,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onOpenSeparateWindow: () -> Unit
) {
    val accent = Color(spec.accentHex)
    val uiProfile = BuiltInTcgCatalog.systemUiProfileFor(spec.baseVariantId)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) accent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onSelect() }
            .testTag("base_variant_card_${spec.baseVariantId}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = spec.baseVariantId,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                        Text(
                            text = "v${spec.protocolVersion}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (spec.isCustomProjectClone) {
                                        RadiantGold.copy(alpha = 0.18f)
                                    } else {
                                        EmeraldReady.copy(alpha = 0.16f)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = when (spec.baseVariantId) {
                                    "EDOPRO_BASE_MR5" -> "OFFICIAL EDOPRO BASE"
                                    "EDOPRO_CUSTOM_FORK" -> "21-DIGIT EDOPRO CLONE"
                                    "FORGE_MTG_BASE" -> "CARD-FORGE MTG BASE"
                                    "FORGE_CUSTOM_CLONE" -> "CARD-FORGE CUSTOM CLONE"
                                    "HYBRID_FORGE_EDOPRO_MIX" -> "EXTENSIBLE MIXED CLIENT"
                                    else -> "STANDARD BASE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (spec.isCustomProjectClone) RadiantGold else EmeraldReady,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = spec.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isSelected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = accent.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active Base Variant",
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SYSTEM ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = spec.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (!spec.isCustomProjectClone) Icons.Default.Lock else Icons.Default.Code,
                    contentDescription = "Source Repo",
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (!spec.isCustomProjectClone) {
                        "LOCKED TO GITHUB REPO (SOURCE UPDATES ONLY): ${spec.sourceRepoUrl} • ${spec.engineCoreLabel}"
                    } else {
                        "${spec.sourceRepoUrl} • ${spec.engineCoreLabel}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpecStatBadge("${uiProfile.lifeLabel}: ${spec.startingLife}")
                SpecStatBadge(
                    if (!spec.isCustomProjectClone) "GitHub Repo Locked (Read-Only)" else "Vars/Funcs: ${spec.maxDigitPrecision}-Digit Max"
                )
                SpecStatBadge("Max ${spec.resourceName}: ${spec.maxResourcePool}")
                SpecStatBadge("UI Layout: ${uiProfile.boardLayoutType}")
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Zones: ${spec.zones.joinToString(" • ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) accent else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("switch_ui_variant_${spec.baseVariantId}")
                ) {
                    Text(
                        text = if (isSelected) "Client System Active" else "Switch UI to System",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                OutlinedButton(
                    onClick = onOpenSeparateWindow,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_variant_window_${spec.baseVariantId}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open Client System in Separate Window",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Separate Window",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecStatBadge(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SavedVariantItemCard(
    variant: TcgVariant,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    val isCustomClone = BuiltInTcgCatalog.supports21DigitRegisters(variant.baseVariantId)
    val isMixedClient = BuiltInTcgCatalog.isHybridMixedVariant(variant.baseVariantId)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = variant.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            isMixedClient -> "EXTENSIBLE MIXED CLIENT"
                            variant.baseVariantId == "FORGE_CUSTOM_CLONE" -> "FORGE 21D CLONE"
                            variant.baseVariantId == "FORGE_MTG_BASE" -> "CARD-FORGE MTG"
                            isCustomClone -> "21-DIGIT CLONE"
                            variant.isBaseStandard -> "BASE STANDARD"
                            else -> "CUSTOM MOD"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (variant.isBaseStandard && !isCustomClone) EmeraldReady else RadiantGold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Base: ${variant.baseVariantId} • LP: ${BigInt21Engine.formatWithCommas(variant.startingLp21)} (${variant.startingLp21.length}d) • Precision: ${variant.maxDigitPrecision}-Digit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isMixedClient) {
                    val modules = BigInt21Engine.extractMixedClientModules(variant.customCodeVarExpr21)
                    Text(
                        text = "Loaded Mix Modules (${modules.size}): ${modules.joinToString(" + ")}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE040FB)
                    )
                }
                if (isCustomClone) {
                    Text(
                        text = "Func ${variant.customFuncName}(${variant.customVarInput21.length}d, ${variant.customFuncInput21.length}d) = ${variant.customFuncResult21}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = RadiantGold
                    )
                    Text(
                        text = "Code String (${variant.customCodeString21.length}d): c${variant.customCodeString21} • ${variant.customCodeVarExpr21}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = EmeraldReady
                    )
                } else {
                    val exactRepo = BuiltInTcgCatalog.exactGitHubRepoUrl(variant.baseVariantId)
                    Text(
                        text = "GitHub Source Locked ($exactRepo) • Hash: ${variant.protocolHash}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldReady
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onActivate,
                    modifier = Modifier.testTag("activate_variant_${variant.id}")
                ) {
                    Text(if (isActive) "Active" else "Load")
                }
                if (variant.isBaseStandard && !isCustomClone) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked to GitHub Source Repository",
                        tint = EmeraldReady,
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("locked_variant_icon_${variant.id}")
                    )
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_variant_${variant.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Variant",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog for "Clone EDOPro for Custom Project" upgraded with 21-Digit Variable, Function &
 * Coding Variables for Code Strings Capabilities (1–21 numerical digits).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloneEdoProProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        projectName: String,
        customCdbFile: String,
        startingLp21: String,
        monsterZones: Long,
        customVarInput21: String,
        customFuncInput21: String,
        customFuncName: String,
        customCodeString21: String,
        customCodeVarExpr21: String
    ) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var customCdbFile by remember { mutableStateOf("custom_expansion.cdb") }
    var startingLp21 by remember { mutableStateOf("999999999999999999999") }
    var monsterZones by remember { mutableFloatStateOf(6f) }
    var customVarInput21 by remember { mutableStateOf("123456789012345678901") }
    var customFuncInput21 by remember { mutableStateOf("876543210987654321098") }
    var selectedFuncName by remember { mutableStateOf("ADD_21") }
    var customCodeString21 by remember { mutableStateOf(BigInt21Engine.DEFAULT_CODE_STRING_21) }
    var customCodeVarExpr21 by remember { mutableStateOf(BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21) }

    val computedResult21 = remember(selectedFuncName, customVarInput21, customFuncInput21) {
        BigInt21Engine.evaluateFunction21(selectedFuncName, customVarInput21, customFuncInput21)
    }
    val parsedCodeVariables = remember(customCodeVarExpr21) {
        BigInt21Engine.extractCodingVariables21(customCodeVarExpr21)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Functions,
                    contentDescription = "21-Digit EDOPro Clone",
                    tint = RadiantGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clone EDOPro for Custom Project (21-Digit)")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Spawns a separate ProjectIgnis/EDOPro Custom Clone upgraded with a 21-numerical-digit limit (up to 999,999,999,999,999,999,999) for variables, functions, and coding variables for code strings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Custom Project Name") },
                    placeholder = { Text("e.g. Genesis 21-Digit Boss Project") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_project_name_input")
                )
                OutlinedTextField(
                    value = customCdbFile,
                    onValueChange = { customCdbFile = it },
                    label = { Text("Expansion SQLite Database (.cdb)") },
                    placeholder = { Text("e.g. genesis_bigint21.cdb") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_cdb_file_input")
                )

                // 21-Digit Code String Passcode Identifier (s.code_string_21)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "21-Digit Code String ID (s.code_string_21):",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${customCodeString21.length} / 21 digits",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold
                    )
                }
                OutlinedTextField(
                    value = customCodeString21,
                    onValueChange = { customCodeString21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Code String Numeric ID (1–21 Digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_code_string21_input")
                )

                // Coding Variables for Code Strings (21 Numerical Digit Limit Enforced)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Coding Variables for Code Strings (Max 21 Digits):",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "21-Digit Cap Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldReady
                    )
                }
                OutlinedTextField(
                    value = customCodeVarExpr21,
                    onValueChange = {
                        customCodeVarExpr21 = BigInt21Engine.filterCodeStringWith21DigitLimit(it)
                    },
                    label = { Text("Code String Variable Assignments (≤21 Digits per Variable)") },
                    placeholder = { Text("s.code_var_21 = \"900001010000000000021\"; s.setcode_21 = \"123456789012345678901\"") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_code_var_expr21_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = customCodeVarExpr21 == BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21,
                        onClick = {
                            customCodeString21 = BigInt21Engine.DEFAULT_CODE_STRING_21
                            customCodeVarExpr21 = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
                        },
                        label = { Text("21-Digit CodeVars") }
                    )
                    FilterChip(
                        selected = customCodeString21 == BigInt21Engine.MAX_21_DIGIT_STRING,
                        onClick = {
                            customCodeString21 = BigInt21Engine.MAX_21_DIGIT_STRING
                            customCodeVarExpr21 =
                                "s.code_var_21 = \"999999999999999999999\"; s.alias_code_21 = \"900001010000000000021\""
                        },
                        label = { Text("Max 21-Digit Code") }
                    )
                }

                // Live Parsed 21-Digit Coding Variables Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, EmeraldReady.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                        .testTag("clone_code_vars_preview")
                ) {
                    Column {
                        Text(
                            text = "PARSED CODE-STRING CODING VARIABLES (21-DIGIT LIMIT):",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldReady,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Script File: script/c${customCodeString21.ifBlank { BigInt21Engine.DEFAULT_CODE_STRING_21 }}.lua",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = RadiantGold
                        )
                        if (parsedCodeVariables.isEmpty()) {
                            Text(
                                text = "s.code_var_21 = \"${customCodeString21.ifBlank { BigInt21Engine.DEFAULT_CODE_STRING_21 }}\"",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            parsedCodeVariables.forEach { (varName, digitVal) ->
                                Text(
                                    text = "$varName = \"$digitVal\" (${digitVal.length}/21 digits)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // 21-Digit Starting LP Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Starting LP (Up to 21 Digits):",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${startingLp21.length} / 21 digits",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold
                    )
                }
                OutlinedTextField(
                    value = startingLp21,
                    onValueChange = { startingLp21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Starting LP Input (1–21 Digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_lp21_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = startingLp21 == "8000",
                        onClick = { startingLp21 = "8000" },
                        label = { Text("8000 LP") }
                    )
                    FilterChip(
                        selected = startingLp21 == "1000000000000",
                        onClick = { startingLp21 = "1000000000000" },
                        label = { Text("13-Digit (1T)") }
                    )
                    FilterChip(
                        selected = startingLp21 == BigInt21Engine.MAX_21_DIGIT_STRING,
                        onClick = { startingLp21 = BigInt21Engine.MAX_21_DIGIT_STRING },
                        label = { Text("Max 21-Digit") }
                    )
                }

                // 21-Digit Variable Register A
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Variable Register A (s.var_input_21):",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${customVarInput21.length} / 21 digits",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldReady
                    )
                }
                OutlinedTextField(
                    value = customVarInput21,
                    onValueChange = { customVarInput21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Variable Input A (Up to 21 Digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_var21_input")
                )

                // 21-Digit Function Argument B
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Function Input B (s.func_arg_21):",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${customFuncInput21.length} / 21 digits",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldReady
                    )
                }
                OutlinedTextField(
                    value = customFuncInput21,
                    onValueChange = { customFuncInput21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Function Input B (Up to 21 Digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_func_arg21_input")
                )

                // 21-Digit Function Selector
                Text(
                    text = "21-Digit Client Function (Duel.ExecuteBigInt21Function):",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BigInt21Engine.supportedFunctions.forEach { fn ->
                        FilterChip(
                            selected = selectedFuncName == fn,
                            onClick = { selectedFuncName = fn },
                            label = { Text(fn) }
                        )
                    }
                }

                // Live 21-Digit Function Output Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, RadiantGold.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                        .testTag("clone_21digit_result_preview")
                ) {
                    Column {
                        Text(
                            text = "LIVE 21-DIGIT FUNCTION EVALUATION (${computedResult21.length} DIGITS):",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$selectedFuncName(A, B) = $computedResult21",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Formatted: ${BigInt21Engine.formatWithCommas(computedResult21)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldReady
                        )
                    }
                }

                Text(
                    text = "Custom Monster Zones: ${monsterZones.toInt()} Zones",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = monsterZones,
                    onValueChange = { monsterZones = it },
                    valueRange = 5f..8f
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        projectName.ifBlank { "Ignis 21-Digit Custom Project" },
                        customCdbFile.ifBlank { "custom_expansion.cdb" },
                        BigInt21Engine.sanitizeTo21Digits(startingLp21, "8000"),
                        monsterZones.toLong(),
                        BigInt21Engine.sanitizeTo21Digits(customVarInput21, "123456789012345678901"),
                        BigInt21Engine.sanitizeTo21Digits(customFuncInput21, "876543210987654321098"),
                        selectedFuncName,
                        BigInt21Engine.sanitizeTo21Digits(
                            customCodeString21,
                            BigInt21Engine.DEFAULT_CODE_STRING_21
                        ),
                        BigInt21Engine.sanitizeCodeVarExpr21(
                            customCodeVarExpr21,
                            BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
                        )
                    )
                },
                modifier = Modifier.testTag("confirm_clone_edopro_button")
            ) {
                Text("Create 21-Digit Clone")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CreateVariantDialog(
    initialBaseVariantId: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Long, Long, Long, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseVariantId by remember { mutableStateOf(initialBaseVariantId) }
    val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseVariantId)
    val isEdoPro = BuiltInTcgCatalog.isEdoProVariant(baseVariantId)
    var resourceName by remember(baseVariantId) { mutableStateOf(baseSpec.resourceName) }
    var startingLife by remember(baseVariantId) { mutableFloatStateOf(baseSpec.startingLife.toFloat()) }
    var startingHand by remember(baseVariantId) { mutableFloatStateOf(baseSpec.startingHandSize.toFloat()) }
    var maxRes by remember(baseVariantId) { mutableFloatStateOf(baseSpec.maxResourcePool.toFloat()) }
    var combatRule by remember(baseVariantId) { mutableStateOf(baseSpec.combatRule) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Engine Variant") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Variant Name") },
                    placeholder = { Text("e.g. EDOPro Custom Speed Duel") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("variant_name_input")
                )
                Text(
                    text = "Parent Base Variant:",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BuiltInTcgCatalog.baseVariants.take(3).forEach { spec ->
                        FilterChip(
                            selected = baseVariantId == spec.baseVariantId,
                            onClick = { baseVariantId = spec.baseVariantId },
                            label = {
                                Text(
                                    when (spec.baseVariantId) {
                                        "EDOPRO_BASE_MR5" -> "EDOPro Base"
                                        "EDOPRO_CUSTOM_FORK" -> "EDOPro Clone"
                                        else -> spec.name.substringBefore(" ")
                                    }
                                )
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = resourceName,
                    onValueChange = { resourceName = it },
                    label = { Text("Resource / Zone Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Starting LP/Life: ${startingLife.toInt()} (Base: ${baseSpec.startingLife})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = startingLife,
                    onValueChange = { startingLife = it },
                    valueRange = if (isEdoPro) 2000f..16000f else 10f..60f
                )
                Text(
                    text = "Max $resourceName Pool: ${maxRes.toInt()} (Base: ${baseSpec.maxResourcePool})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = maxRes,
                    onValueChange = { maxRes = it },
                    valueRange = 3f..12f
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("ATK_DEF_BATTLE", "BLOCKER_ASSIGN", "DIRECT_STRIKE").forEach { rule ->
                        FilterChip(
                            selected = combatRule == rule,
                            onClick = { combatRule = rule },
                            label = { Text(rule.substringBefore("_")) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.ifBlank { "${baseSpec.name} Fork" },
                        baseVariantId,
                        resourceName.ifBlank { baseSpec.resourceName },
                        startingLife.toLong(),
                        startingHand.toLong(),
                        maxRes.toLong(),
                        combatRule
                    )
                },
                modifier = Modifier.testTag("confirm_create_variant_button")
            ) {
                Text("Save Variant")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
