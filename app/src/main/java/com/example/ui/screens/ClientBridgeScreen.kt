package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ClientWindowLauncher
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.ClientNode
import com.example.data.TcgVariant
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import com.example.viewmodel.HandshakeDiagnostic
import com.example.viewmodel.UiState

@Composable
fun ClientBridgeScreen(
    selectedBaseVariantId: String,
    activeCustomVariant: TcgVariant?,
    clientsState: UiState<List<ClientNode>>,
    lastHandshakeDiagnostic: HandshakeDiagnostic?,
    onIncludeClient: (String, String, String, String, Boolean) -> Unit,
    onConnectClient: (ClientNode, Boolean) -> Unit,
    onDisconnectClient: (ClientNode) -> Unit,
    onDeleteClient: (String) -> Unit,
    onSelectBaseVariant: (String) -> Unit,
    onCloneEdoProProject: (String, String, String, Long, String, String, String, String, String) -> Unit,
    onCloneForgeProject: (String, String, String, Long, String, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onSpawnMixedForgeEdoProClient: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onAddExtensionToMixedClient: (ClientNode?, String, String, String) -> Unit = { _, _, _, _ -> },
    onExecuteClient21DigitFunction: (ClientNode, String, String, String, String, String) -> Unit,
    onSyncBaseClientFromGitHubSource: (ClientNode) -> Unit = {},
    onResetToBaseStandard: () -> Unit,
    onRequestConfirmOpenClientWindow: (ClientNode?, String) -> Unit = { _, _ -> }
) {
    var showIncludeDialog by remember { mutableStateOf(false) }
    var showCloneEdoProDialog by remember { mutableStateOf(false) }
    var showCloneForgeDialog by remember { mutableStateOf(false) }
    var showSpawnMixedDialog by remember { mutableStateOf(false) }
    var extendingMixedClient by remember { mutableStateOf<ClientNode?>(null) }
    var showAddExtensionDialog by remember { mutableStateOf(false) }
    var editing21DigitClient by remember { mutableStateOf<ClientNode?>(null) }

    val context = LocalContext.current
    val activeBaseSpec = BuiltInTcgCatalog.findBaseSpec(selectedBaseVariantId)
    val activeUiProfile = BuiltInTcgCatalog.systemUiProfileFor(selectedBaseVariantId)
    val isUnmodifiedBase = activeCustomVariant == null || activeCustomVariant.isBaseStandard
    val allClients = (clientsState as? UiState.Success)?.data.orEmpty()
    val primaryMixedClient = allClients.firstOrNull { it.baseVariantId == "HYBRID_FORGE_EDOPRO_MIX" }
    val activeMixedModules = BigInt21Engine.extractMixedClientModules(
        primaryMixedClient?.customCodeVarExpr21 ?: BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("client_bridge_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Base-Variant Handshake Protocol Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hub,
                                contentDescription = "Base-Variant Protocol Bridge",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ACTIVE CLIENT SYSTEM UI: ${activeUiProfile.hudBadgeLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = activeUiProfile.systemTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Button(
                            onClick = { showIncludeDialog = true },
                            modifier = Modifier.testTag("include_client_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Include Client",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Include Client")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Active Signature: ${activeCustomVariant?.protocolHash ?: activeBaseSpec.protocolHash} • Layout: ${activeUiProfile.boardLayoutType} • Max Precision: ${activeBaseSpec.maxDigitPrecision} Digits",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUnmodifiedBase) EmeraldReady else RadiantGold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Selecting any TCG client below automatically switches the app's UI theme, zones (${activeUiProfile.fieldSlotCodes.joinToString(", ")}), and terminology (${activeUiProfile.lifeLabel} / ${activeUiProfile.deployVerb}) to that client's system. You can also launch any client in a separate Android window/task.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val activeNode = allClients.firstOrNull { it.baseVariantId == selectedBaseVariantId }
                            onRequestConfirmOpenClientWindow(activeNode, selectedBaseVariantId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_bridge_active_window_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DesktopWindows,
                            contentDescription = "Open Active Client System in Separate Window",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open ${activeUiProfile.windowModeTitle} in Separate Window")
                    }

                    if (!isUnmodifiedBase) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onResetToBaseStandard,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Revert to Base Variant",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Revert Custom Mod to ${activeBaseSpec.baseVariantId} Base Standard")
                        }
                    }
                }
            }
        }

        // Dedicated Card-Forge (MTG) Standalone + Custom Clone + Extensible Mixed Client (Forge + EDOPro Custom Clone) Card
        item {
            CardForgeAndHybridArchitectureCard(
                selectedBaseVariantId = selectedBaseVariantId,
                activeMixedModules = activeMixedModules,
                onSelectForgeBase = {
                    val node = allClients.firstOrNull { it.baseVariantId == "FORGE_MTG_BASE" }
                    onSelectBaseVariant("FORGE_MTG_BASE")
                    onRequestConfirmOpenClientWindow(node, "FORGE_MTG_BASE")
                },
                onSelectForgeCustomClone = {
                    val node = allClients.firstOrNull { it.baseVariantId == "FORGE_CUSTOM_CLONE" }
                    onSelectBaseVariant("FORGE_CUSTOM_CLONE")
                    onRequestConfirmOpenClientWindow(node, "FORGE_CUSTOM_CLONE")
                },
                onSelectHybridMix = {
                    onSelectBaseVariant("HYBRID_FORGE_EDOPRO_MIX")
                    onRequestConfirmOpenClientWindow(primaryMixedClient, "HYBRID_FORGE_EDOPRO_MIX")
                },
                onOpenForgeBaseWindow = {
                    val node = allClients.firstOrNull { it.baseVariantId == "FORGE_MTG_BASE" }
                    onRequestConfirmOpenClientWindow(node, "FORGE_MTG_BASE")
                },
                onOpenForgeCloneWindow = {
                    val node = allClients.firstOrNull { it.baseVariantId == "FORGE_CUSTOM_CLONE" }
                    onRequestConfirmOpenClientWindow(node, "FORGE_CUSTOM_CLONE")
                },
                onOpenHybridMixWindow = {
                    onRequestConfirmOpenClientWindow(primaryMixedClient, "HYBRID_FORGE_EDOPRO_MIX")
                },
                onSpawnForgeCustomClone = { showCloneForgeDialog = true },
                onSpawnMixedClient = { showSpawnMixedDialog = true },
                onAddModuleToMixedClient = {
                    extendingMixedClient = primaryMixedClient
                    showAddExtensionDialog = true
                }
            )
        }

        // Dedicated ProjectIgnis/EDOPro Dual-Client Architecture Card
        item {
            EdoProDualArchitectureCard(
                selectedBaseVariantId = selectedBaseVariantId,
                onSelectEdoProBase = {
                    val baseClient = allClients.firstOrNull { it.baseVariantId == "EDOPRO_BASE_MR5" }
                    onSelectBaseVariant("EDOPRO_BASE_MR5")
                    onRequestConfirmOpenClientWindow(baseClient, "EDOPRO_BASE_MR5")
                },
                onSelectEdoProCustomClone = {
                    val cloneClient = allClients.firstOrNull { it.baseVariantId == "EDOPRO_CUSTOM_FORK" }
                    onSelectBaseVariant("EDOPRO_CUSTOM_FORK")
                    onRequestConfirmOpenClientWindow(cloneClient, "EDOPRO_CUSTOM_FORK")
                },
                onOpenEdoProBaseWindow = {
                    val baseClient = allClients.firstOrNull { it.baseVariantId == "EDOPRO_BASE_MR5" }
                    onRequestConfirmOpenClientWindow(baseClient, "EDOPRO_BASE_MR5")
                },
                onOpenEdoProCloneWindow = {
                    val cloneClient = allClients.firstOrNull { it.baseVariantId == "EDOPRO_CUSTOM_FORK" }
                    onRequestConfirmOpenClientWindow(cloneClient, "EDOPRO_CUSTOM_FORK")
                },
                onSpawnNewCustomClone = { showCloneEdoProDialog = true }
            )
        }

        // Latest Handshake Verification Diagnostic
        if (lastHandshakeDiagnostic != null) {
            item {
                HandshakeDiagnosticCard(diagnostic = lastHandshakeDiagnostic)
            }
        }

        // Client Node List
        when (clientsState) {
            is UiState.Loading -> {
                item {
                    Text(
                        text = "Scanning registered client nodes...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = clientsState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success -> {
                items(clientsState.data, key = { it.id }) { client ->
                    val matchesBaseVariant = selectedBaseVariantId == client.baseVariantId &&
                        (!client.requiresStrictBase || isUnmodifiedBase)
                    ClientNodeCard(
                        client = client,
                        isEngineAlignedWithClientBase = matchesBaseVariant,
                        onTestHandshake = { onConnectClient(client, false) },
                        onSyncBaseAndConnect = {
                            onConnectClient(client, true)
                            onRequestConfirmOpenClientWindow(client, client.baseVariantId)
                        },
                        onOpenInSeparateWindow = {
                            onConnectClient(client, true)
                            onRequestConfirmOpenClientWindow(client, client.baseVariantId)
                        },
                        onOpen21DigitRegisters = { editing21DigitClient = client },
                        onAddExtensionToMixed = {
                            extendingMixedClient = client
                            showAddExtensionDialog = true
                        },
                        onSyncFromGitHubSource = { onSyncBaseClientFromGitHubSource(client) },
                        onDisconnect = { onDisconnectClient(client) },
                        onDelete = { onDeleteClient(client.id) }
                    )
                }
            }
        }
    }

    if (showIncludeDialog) {
        IncludeClientDialog(
            initialBaseVariantId = selectedBaseVariantId,
            onDismiss = { showIncludeDialog = false },
            onConfirm = { name, type, uri, baseId, strict ->
                onIncludeClient(name, type, uri, baseId, strict)
                showIncludeDialog = false
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
            onConfirm = { projName, folder, life21, maxMana, svar21, fn, code21 ->
                onCloneForgeProject(projName, folder, life21, maxMana, svar21, fn, code21)
                showCloneForgeDialog = false
            }
        )
    }

    if (showSpawnMixedDialog) {
        SpawnMixedClientDialog(
            onDismiss = { showSpawnMixedDialog = false },
            onConfirm = { mixName, extraMod, lp21, var21, code21 ->
                onSpawnMixedForgeEdoProClient(mixName, extraMod, lp21, var21, code21)
                showSpawnMixedDialog = false
            }
        )
    }

    if (showAddExtensionDialog) {
        AddExtensionToMixedClientDialog(
            targetClient = extendingMixedClient ?: primaryMixedClient,
            onDismiss = {
                showAddExtensionDialog = false
                extendingMixedClient = null
            },
            onConfirm = { modName, modCategory, var21 ->
                onAddExtensionToMixedClient(extendingMixedClient ?: primaryMixedClient, modName, modCategory, var21)
                showAddExtensionDialog = false
                extendingMixedClient = null
            }
        )
    }

    editing21DigitClient?.let { client ->
        Client21DigitRegisterDialog(
            client = client,
            onDismiss = { editing21DigitClient = null },
            onExecuteAndSave = { var21, arg21, fn, codeStr21, codeVarExpr21 ->
                onExecuteClient21DigitFunction(client, var21, arg21, fn, codeStr21, codeVarExpr21)
                editing21DigitClient = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardForgeAndHybridArchitectureCard(
    selectedBaseVariantId: String,
    activeMixedModules: List<String>,
    onSelectForgeBase: () -> Unit,
    onSelectForgeCustomClone: () -> Unit,
    onSelectHybridMix: () -> Unit,
    onOpenForgeBaseWindow: () -> Unit,
    onOpenForgeCloneWindow: () -> Unit,
    onOpenHybridMixWindow: () -> Unit,
    onSpawnForgeCustomClone: () -> Unit,
    onSpawnMixedClient: () -> Unit,
    onAddModuleToMixedClient: () -> Unit
) {
    val forgeAzure = Color(0xFF29B6F6)
    val forgeEmerald = Color(0xFF66BB6A)
    val hybridMagenta = Color(0xFFFF4081)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = hybridMagenta.copy(alpha = 0.65f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("card_forge_hybrid_architecture_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "CARD-FORGE (MTG) + EDOPRO 21-DIGIT CLONE & EXTENSIBLE MIXED CLIENT",
                style = MaterialTheme.typography.labelMedium,
                color = hybridMagenta,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Standalone Forge MTG • Custom Forge Clone • Extensible Mixed Client",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Standalone Card-Forge Client for Magic: The Gathering
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selectedBaseVariantId == "FORGE_MTG_BASE") forgeAzure else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectForgeBase() }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Card-Forge Standalone",
                        tint = forgeAzure,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "1. Card-Forge Standalone Client (Magic: The Gathering)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "https://github.com/Card-Forge/forge • 20 Life • 7-Card Hand • The Stack, Lands & Mana Pool",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectForgeBase,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("switch_forge_base_button")
                    ) {
                        Text(if (selectedBaseVariantId == "FORGE_MTG_BASE") "System UI Active" else "Switch UI to Forge MTG")
                    }
                    OutlinedButton(
                        onClick = onOpenForgeBaseWindow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_forge_base_window_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Forge Standalone Window",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Window")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Clone of Card-Forge for Custom Projects
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selectedBaseVariantId == "FORGE_CUSTOM_CLONE") forgeEmerald else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectForgeCustomClone() }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CallSplit,
                        contentDescription = "Clone Card-Forge for Customs",
                        tint = forgeEmerald,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "2. Clone Card-Forge for Custom Projects (21-Digit SVar)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Separate clone of https://github.com/Card-Forge/forge for custom cardsfolder/*.txt sets & 21-digit SVar/PT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectForgeCustomClone,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("switch_forge_clone_button")
                    ) {
                        Text(if (selectedBaseVariantId == "FORGE_CUSTOM_CLONE") "System UI Active" else "Switch UI to Clone")
                    }
                    OutlinedButton(
                        onClick = onOpenForgeCloneWindow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_forge_clone_window_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Forge Clone Window",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Window")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onSpawnForgeCustomClone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spawn_forge_custom_clone_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = forgeEmerald,
                        contentColor = Color(0xFF002406)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CallSplit,
                        contentDescription = "Clone Card-Forge for Custom Project",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clone Card-Forge for Custom Project")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Extensible Mixed Client: Cloning Card-Forge + Cloning "Clone EDOPro for Custom Project"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (selectedBaseVariantId == "HYBRID_FORGE_EDOPRO_MIX") hybridMagenta else hybridMagenta.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectHybridMix() }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "Extensible Mixed Client",
                        tint = hybridMagenta,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "3. Mixed Client: Clone Forge + Clone EDOPro Custom (Extensible)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Combines Clone Card-Forge (MTG Stack/Mana/SVar) + Clone EDOPro for Custom Project (21-Digit Lua/Chain) into a unified client that can be added to at any time.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Active Mixed Client Modules (${activeMixedModules.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = hybridMagenta,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeMixedModules.forEach { mod ->
                        BadgeChip(label = mod, color = hybridMagenta)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectHybridMix,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("switch_hybrid_mix_button")
                    ) {
                        Text(if (selectedBaseVariantId == "HYBRID_FORGE_EDOPRO_MIX") "System UI Active" else "Switch UI to Mixed")
                    }
                    OutlinedButton(
                        onClick = onOpenHybridMixWindow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_hybrid_mix_window_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Mixed Client Window",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Window")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddModuleToMixedClient,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("add_extension_to_mixed_client_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = hybridMagenta,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Module to Mixed Client",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Add to Mixed Client", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onSpawnMixedClient,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("spawn_hybrid_mix_client_button")
                    ) {
                        Text("Clone New Mix", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun EdoProDualArchitectureCard(
    selectedBaseVariantId: String,
    onSelectEdoProBase: () -> Unit,
    onSelectEdoProCustomClone: () -> Unit,
    onOpenEdoProBaseWindow: () -> Unit,
    onOpenEdoProCloneWindow: () -> Unit,
    onSpawnNewCustomClone: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = RadiantGold.copy(alpha = 0.55f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("edopro_dual_client_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PROJECTIGNIS / EDOPRO DUAL-CLIENT ARCHITECTURE",
                style = MaterialTheme.typography.labelMedium,
                color = RadiantGold,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Base Client vs. 21-Digit Custom Project Clone",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Base Client Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selectedBaseVariantId == "EDOPRO_BASE_MR5") {
                            EmeraldReady
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectEdoProBase() }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Base Client",
                                tint = EmeraldReady,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "1. EDOPro Base Client (Yu-Gi-Oh!)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "https://github.com/ProjectIgnis/EDOPro • ocgcore MR5 • cards.cdb (Strict Base Variant)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectEdoProBase,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("switch_edopro_base_button")
                    ) {
                        Text(if (selectedBaseVariantId == "EDOPRO_BASE_MR5") "System UI Active" else "Switch UI to Base")
                    }
                    OutlinedButton(
                        onClick = onOpenEdoProBaseWindow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_edopro_base_window_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Base Client Window",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Window")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Separate Custom Project Clone Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selectedBaseVariantId == "EDOPRO_CUSTOM_FORK") {
                            RadiantGold
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectEdoProCustomClone() }
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CallSplit,
                                contentDescription = "Custom Project Clone",
                                tint = RadiantGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "2. EDOPro Custom Clone (21-Digit Vars/Funcs)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Handles 21-digit numbers (up to 999,999,999,999,999,999,999) for variables, functions & code strings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSelectEdoProCustomClone,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("switch_edopro_clone_button")
                    ) {
                        Text(if (selectedBaseVariantId == "EDOPRO_CUSTOM_FORK") "System UI Active" else "Switch UI to Clone")
                    }
                    OutlinedButton(
                        onClick = onOpenEdoProCloneWindow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_edopro_clone_window_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Custom Clone Window",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Window")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSpawnNewCustomClone,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("spawn_edopro_custom_clone_node_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RadiantGold,
                    contentColor = Color(0xFF1A1300)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CallSplit,
                    contentDescription = "Spawn EDOPro Custom Clone",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Clone EDOPro for Custom Project (21-Digit Inputs)")
            }
        }
    }
}

@Composable
private fun HandshakeDiagnosticCard(diagnostic: HandshakeDiagnostic) {
    val borderColor = if (diagnostic.isCompatible) EmeraldReady else CrimsonAlert
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .testTag("handshake_diagnostic_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (diagnostic.isCompatible) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = "Handshake Result",
                    tint = borderColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (diagnostic.isCompatible) {
                        "BASE-VARIANT HANDSHAKE VERIFIED"
                    } else {
                        "BASE-VARIANT MISMATCH DETECTED"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = borderColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = diagnostic.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Client Expected: ${diagnostic.expectedHash}\nEngine Active:   ${diagnostic.activeHash}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientNodeCard(
    client: ClientNode,
    isEngineAlignedWithClientBase: Boolean,
    onTestHandshake: () -> Unit,
    onSyncBaseAndConnect: () -> Unit,
    onOpenInSeparateWindow: () -> Unit,
    onOpen21DigitRegisters: () -> Unit,
    onAddExtensionToMixed: () -> Unit,
    onSyncFromGitHubSource: () -> Unit = {},
    onDisconnect: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (client.connectionStatus) {
        "CONNECTED" -> EmeraldReady
        "MISMATCH_BLOCKED" -> CrimsonAlert
        else -> RadiantGold
    }
    val isCustomClone = BuiltInTcgCatalog.supports21DigitRegisters(client.baseVariantId)
    val isHybridMix = client.baseVariantId == "HYBRID_FORGE_EDOPRO_MIX"
    val isLockedGitHubBase = BuiltInTcgCatalog.isLockedGitHubBaseClientNode(client)
    val exactGitHubUrl = BuiltInTcgCatalog.exactGitHubRepoUrl(client.baseVariantId)
    val displayedEndpointOrRepo = if (isLockedGitHubBase) exactGitHubUrl else client.endpointUri
    val hybridMagenta = Color(0xFFFF4081)
    val clientUiProfile = BuiltInTcgCatalog.systemUiProfileFor(client.baseVariantId, client)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isEngineAlignedWithClientBase) 2.dp else 1.dp,
                color = if (isEngineAlignedWithClientBase) {
                    MaterialTheme.colorScheme.primary
                } else if (client.connectionStatus == "CONNECTED") {
                    EmeraldReady.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onSyncBaseAndConnect() }
            .testTag("client_node_card_${client.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = client.clientName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${client.clientType} • $displayedEndpointOrRepo",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLockedGitHubBase) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = if (isLockedGitHubBase) FontFamily.Monospace else FontFamily.Default
                        )
                    }
                }
                if (isLockedGitHubBase) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = EmeraldReady.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, EmeraldReady.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("locked_github_badge_${client.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked to GitHub Source Repository",
                                tint = EmeraldReady,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "REPO LOCKED",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldReady,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_client_${client.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove Client",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BadgeChip(
                    label = when {
                        isHybridMix -> "MIXED: FORGE+EDOPRO"
                        isCustomClone -> "CLONE: ${client.baseVariantId}"
                        else -> "BASE REPO LOCKED: ${client.baseVariantId}"
                    },
                    color = when {
                        isHybridMix -> hybridMagenta
                        isCustomClone -> RadiantGold
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                BadgeChip(
                    label = if (isLockedGitHubBase) "SOURCE REPO UPDATES ONLY" else "${client.maxDigitPrecision}-DIGIT VARS",
                    color = if (isCustomClone) RadiantGold else EmeraldReady
                )
                BadgeChip(
                    label = if (isEngineAlignedWithClientBase) "UI SYSTEM ACTIVE" else client.connectionStatus,
                    color = if (isEngineAlignedWithClientBase) EmeraldReady else statusColor
                )
            }

            val clientUiProfile = remember(client.baseVariantId) {
                BuiltInTcgCatalog.systemUiProfileFor(client.baseVariantId)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(10.dp)
                    .testTag("client_five_systems_box_${client.id}")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "INTEGRATED CLIENT ASPECTS: ${clientUiProfile.inspiredSystemsSummary.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Duel Links: Skill '${clientUiProfile.duelLinksSkillName}' + 3-Zone Speed Duel Toggle (${clientUiProfile.speedDuelSlotCodes.joinToString("/")})\n" +
                            "• Master Duel: ${clientUiProfile.masterDuelChainModes.joinToString(" / ")} • Mate '${clientUiProfile.masterDuelMateName}' (${clientUiProfile.masterDuelFoilFinish})\n" +
                            "• Cross Duel: ${clientUiProfile.crossDuelLanes.joinToString(" / ")} • 3 Ace Skills (${clientUiProfile.crossDuelAceSkills.firstOrNull() ?: ""})\n" +
                            "• MTGO: ${clientUiProfile.mtgoChessClockDefault} • ${clientUiProfile.mtgoPriorityShortcuts.joinToString(" | ")}\n" +
                            "• MTG Arena: ${clientUiProfile.mtgaFeatures.joinToString(" • ")} • Emotes (${clientUiProfile.mtgaEmotes.take(3).joinToString("/")})",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (isLockedGitHubBase) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, EmeraldReady.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                        .testTag("github_source_lock_box_${client.id}")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = EmeraldReady,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "LOCKED TO EXACT GITHUB SOURCE REPOSITORY",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldReady,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Source Repository: $exactGitHubUrl\nProtection: Immutable against edits from the app/agent and users. Updates are permitted strictly from the upstream source repository only.",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = onSyncFromGitHubSource,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sync_github_source_${client.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Pull Upstream Update from Source GitHub Repository",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pull Update from Source Repository ($exactGitHubUrl)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (isHybridMix) {
                val modules = BigInt21Engine.extractMixedClientModules(client.customCodeVarExpr21)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, hybridMagenta.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "EXTENSIBLE MIXED CLIENT MODULES (${modules.size} LOADED):",
                            style = MaterialTheme.typography.labelSmall,
                            color = hybridMagenta,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            modules.forEach { mod ->
                                BadgeChip(label = mod, color = hybridMagenta)
                            }
                        }
                    }
                }
            }

            if (isCustomClone) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "21-DIGIT CLIENT VARIABLES, FUNCTIONS & CODE STRINGS",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Code String (${client.customCodeString21.length}d): c${client.customCodeString21}\nCodeVars: ${client.customCodeVarExpr21}\nVar A (${client.customVarInput21.length}d): ${client.customVarInput21}\nArg B (${client.customFuncInput21.length}d): ${client.customFuncInput21}\n${client.customFuncName}(A, B) = ${client.customFuncResult21}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isEngineAlignedWithClientBase) {
                    "Active Client System UI: ${clientUiProfile.systemTitle} (${clientUiProfile.lifeLabel} • ${clientUiProfile.boardLayoutType})."
                } else {
                    "Tap card or 'Switch UI to Client System' to switch the app to ${clientUiProfile.systemTitle}."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEngineAlignedWithClientBase) EmeraldReady else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (client.connectionStatus == "CONNECTED") {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("disconnect_client_${client.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = "Disconnect Client",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Disconnect")
                    }
                } else {
                    OutlinedButton(
                        onClick = onTestHandshake,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("handshake_client_${client.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cable,
                            contentDescription = "Verify Handshake",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Handshake")
                    }
                }

                Button(
                    onClick = onSyncBaseAndConnect,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("sync_connect_client_${client.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Switch UI to Client System and Connect",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEngineAlignedWithClientBase) "System Active" else "Switch UI & Connect"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenInSeparateWindow,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_client_window_${client.id}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open Client in Separate Window or App",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open ${clientUiProfile.windowModeTitle} in Separate Window")
            }

            if (isHybridMix) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onAddExtensionToMixed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_to_mixed_client_button_${client.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = hybridMagenta,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Module or Extension to Mixed Client",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Module / Rule / Extension to Mixed Client")
                }
            }

            if (isCustomClone) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpen21DigitRegisters,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_21digit_registers_${client.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Functions,
                        contentDescription = "Configure 21-Digit Variables & Functions",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configure 21-Digit Variables & Execute Function")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CloneForgeProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Long, String, String, String) -> Unit
) {
    var projectName by remember { mutableStateOf("") }
    var customCardsFolder by remember { mutableStateOf("custom_cardsfolder") }
    var startingLife21 by remember { mutableStateOf("40") }
    var maxManaPool by remember { mutableStateOf("12") }
    var customSvar21 by remember { mutableStateOf("999999999999999999999") }
    var funcName by remember { mutableStateOf("MUL_21") }
    var codeString21 by remember { mutableStateOf("900002010000000000021") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone Card-Forge for Custom Project (MTG)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Clones https://github.com/Card-Forge/forge into a custom project client with custom cardsfolder/*.txt scripts and 21-digit SVar/Power/Toughness registers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Custom Forge Project Name") },
                    placeholder = { Text("e.g. Omniverse Commander Cube") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_forge_name_input")
                )
                OutlinedTextField(
                    value = customCardsFolder,
                    onValueChange = { customCardsFolder = it },
                    label = { Text("Custom Cards Folder Path") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clone_forge_folder_input")
                )
                OutlinedTextField(
                    value = startingLife21,
                    onValueChange = { startingLife21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Starting Life Total (${startingLife21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customSvar21,
                    onValueChange = { customSvar21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("21-Digit SVar Register (${customSvar21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = codeString21,
                    onValueChange = { codeString21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Custom Card Code ID (${codeString21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        projectName.ifBlank { "Card-Forge Custom Project" },
                        customCardsFolder.ifBlank { "custom_cardsfolder" },
                        startingLife21.ifBlank { "40" },
                        maxManaPool.toLongOrNull() ?: 12L,
                        customSvar21.ifBlank { "999999999999999999999" },
                        funcName,
                        codeString21.ifBlank { "900002010000000000021" }
                    )
                },
                modifier = Modifier.testTag("confirm_clone_forge_button")
            ) {
                Text("Clone Card-Forge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpawnMixedClientDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, String) -> Unit
) {
    var mixedClientName by remember { mutableStateOf("") }
    var extraModule by remember { mutableStateOf("Commander_100_EDH_Zone") }
    var startingLp21 by remember { mutableStateOf("999999999999999999999") }
    var varInput21 by remember { mutableStateOf("999999999999999999999") }
    var codeString21 by remember { mutableStateOf("900003010000000000021") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone Forge + Clone EDOPro into Mixed Client") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Clones https://github.com/Card-Forge/forge AND 'Clone EDOPro for Custom Project' into a unified extensible Mixed Client.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = mixedClientName,
                    onValueChange = { mixedClientName = it },
                    label = { Text("Mixed Client Name") },
                    placeholder = { Text("e.g. Nexus Omni-Mix (Forge + EDOPro 21d)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spawn_mixed_client_name_input")
                )
                Text(
                    text = "Initial Extension Module to Include:",
                    style = MaterialTheme.typography.labelLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BigInt21Engine.presetMixExtensions.forEach { preset ->
                        FilterChip(
                            selected = extraModule == preset,
                            onClick = { extraModule = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = extraModule,
                    onValueChange = { extraModule = it },
                    label = { Text("Custom Extension Module Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startingLp21,
                    onValueChange = { startingLp21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Hybrid Starting LP/Life (${startingLp21.length}/21d)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        mixedClientName.ifBlank { "Mixed Forge + EDOPro Custom Client" },
                        extraModule.ifBlank { "Commander_100_EDH_Zone" },
                        startingLp21.ifBlank { "8000" },
                        varInput21.ifBlank { "999999999999999999999" },
                        codeString21.ifBlank { "900003010000000000021" }
                    )
                },
                modifier = Modifier.testTag("confirm_spawn_mixed_client_button")
            ) {
                Text("Create Mixed Client")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExtensionToMixedClientDialog(
    targetClient: ClientNode?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var moduleName by remember { mutableStateOf("Pendulum_Mana_Conduit") }
    var moduleCategory by remember { mutableStateOf("RULE") }
    var varInput21 by remember {
        mutableStateOf(targetClient?.customVarInput21 ?: "999999999999999999999")
    }
    val existingModules = remember(targetClient) {
        BigInt21Engine.extractMixedClientModules(
            targetClient?.customCodeVarExpr21 ?: BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Module / Extension to Mixed Client") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Target: ${targetClient?.clientName ?: "Mixed Client: Clone Forge + Clone EDOPro Custom"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Currently Loaded (${existingModules.size}): ${existingModules.joinToString(" + ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF4081),
                    fontFamily = FontFamily.Monospace
                )
                Text("Extension Category:", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("RULE", "ZONE", "CLIENT", "LUA21", "SVAR").forEach { cat ->
                        FilterChip(
                            selected = moduleCategory == cat,
                            onClick = { moduleCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Text("Quick-Add Preset Extensions:", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BigInt21Engine.presetMixExtensions.forEach { preset ->
                        FilterChip(
                            selected = moduleName == preset,
                            onClick = { moduleName = preset },
                            label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = moduleName,
                    onValueChange = { moduleName = it },
                    label = { Text("Module / Rule / Sub-Client Name to Add") },
                    placeholder = { Text("e.g. Planeswalker_Link_Arrows") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mixed_client_new_module_input")
                )
                OutlinedTextField(
                    value = varInput21,
                    onValueChange = { varInput21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("21-Digit Hybrid Register (${varInput21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        moduleName.ifBlank { "Custom_Mix_Extension" },
                        moduleCategory,
                        varInput21
                    )
                },
                modifier = Modifier.testTag("confirm_add_mixed_extension_button")
            ) {
                Text("Add to Mixed Client")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Client21DigitRegisterDialog(
    client: ClientNode,
    onDismiss: () -> Unit,
    onExecuteAndSave: (String, String, String, String, String) -> Unit
) {
    var varInput21 by remember { mutableStateOf(client.customVarInput21) }
    var funcArg21 by remember { mutableStateOf(client.customFuncInput21) }
    var funcName by remember { mutableStateOf(client.customFuncName) }
    var codeString21 by remember { mutableStateOf(client.customCodeString21) }
    var codeVarExpr21 by remember { mutableStateOf(client.customCodeVarExpr21) }

    val computedResult21 = remember(funcName, varInput21, funcArg21) {
        BigInt21Engine.evaluateFunction21(funcName, varInput21, funcArg21)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("21-Digit Client Variables, Functions & Code Strings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Client: ${client.clientName} (Max 21 Numerical Digits: 999,999,999,999,999,999,999)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = codeString21,
                    onValueChange = { codeString21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Code String ID (${codeString21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_client_code_str21_input")
                )
                OutlinedTextField(
                    value = codeVarExpr21,
                    onValueChange = {
                        codeVarExpr21 = BigInt21Engine.filterCodeStringWith21DigitLimit(it)
                    },
                    label = { Text("Coding Variables for Code Strings (≤21 Digits)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_client_code_var_expr21_input")
                )
                OutlinedTextField(
                    value = varInput21,
                    onValueChange = { varInput21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Variable A (${varInput21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_client_var21_input")
                )
                OutlinedTextField(
                    value = funcArg21,
                    onValueChange = { funcArg21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Function Input B (${funcArg21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_client_arg21_input")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BigInt21Engine.supportedFunctions.forEach { fn ->
                        FilterChip(
                            selected = funcName == fn,
                            onClick = { funcName = fn },
                            label = { Text(fn) }
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, RadiantGold.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = "RESULT (${computedResult21.length} DIGITS) & CODE STRING:",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold
                        )
                        Text(
                            text = "c$codeString21 • $computedResult21",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = BigInt21Engine.formatWithCommas(computedResult21),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldReady
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExecuteAndSave(
                        varInput21,
                        funcArg21,
                        funcName,
                        codeString21,
                        codeVarExpr21
                    )
                },
                modifier = Modifier.testTag("confirm_client_21digit_button")
            ) {
                Text("Execute & Sync 21-Digit State")
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
private fun BadgeChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncludeClientDialog(
    initialBaseVariantId: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Boolean) -> Unit
) {
    var clientName by remember { mutableStateOf("") }
    var clientType by remember { mutableStateOf("Card-Forge MTG Standalone") }
    var endpointUri by remember { mutableStateOf("https://github.com/Card-Forge/forge") }
    var baseVariantId by remember { mutableStateOf(initialBaseVariantId) }
    var requiresStrictBase by remember { mutableStateOf(!BuiltInTcgCatalog.supports21DigitRegisters(baseVariantId)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Include External TCG Client") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text("Client Name") },
                    placeholder = { Text("e.g. Card-Forge MTG Peer / EDOPro Peer") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("client_name_input")
                )
                Text("Client Platform Type:", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Card-Forge MTG Standalone",
                        "Card-Forge Custom Clone",
                        "Mixed Forge+EDOPro Client",
                        "EDOPro Base Client",
                        "EDOPro Custom Clone"
                    ).forEach { type ->
                        FilterChip(
                            selected = clientType == type,
                            onClick = {
                                clientType = type
                                when (type) {
                                    "Card-Forge MTG Standalone" -> {
                                        baseVariantId = "FORGE_MTG_BASE"
                                        requiresStrictBase = true
                                        endpointUri = "https://github.com/Card-Forge/forge"
                                    }
                                    "Card-Forge Custom Clone" -> {
                                        baseVariantId = "FORGE_CUSTOM_CLONE"
                                        requiresStrictBase = false
                                        endpointUri = "git://custom-clone/Card-Forge/forge-CustomProjects"
                                    }
                                    "Mixed Forge+EDOPro Client" -> {
                                        baseVariantId = "HYBRID_FORGE_EDOPRO_MIX"
                                        requiresStrictBase = false
                                        endpointUri = "git://hybrid-mix/Card-Forge-forge+ProjectIgnis-EDOPro-Custom"
                                    }
                                    "EDOPro Custom Clone" -> {
                                        baseVariantId = "EDOPRO_CUSTOM_FORK"
                                        requiresStrictBase = false
                                        endpointUri = "ws://custom-clone.edopro.local:7912/expansions"
                                    }
                                    "EDOPro Base Client" -> {
                                        baseVariantId = "EDOPRO_BASE_MR5"
                                        requiresStrictBase = true
                                        endpointUri = "tcp://duel.projectignis.org:7911"
                                    }
                                }
                            },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = endpointUri,
                    onValueChange = { endpointUri = it },
                    label = { Text("Endpoint / Repository URI") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("client_endpoint_input")
                )
                Text("Client's Base Variant:", style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BuiltInTcgCatalog.baseVariants.take(5).forEach { spec ->
                        FilterChip(
                            selected = baseVariantId == spec.baseVariantId,
                            onClick = {
                                baseVariantId = spec.baseVariantId
                                requiresStrictBase = !spec.isCustomProjectClone
                            },
                            label = { Text("${spec.name.take(32)} (${spec.baseVariantId})") }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Require Strict Base Variant Lock",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = requiresStrictBase,
                        onCheckedChange = { requiresStrictBase = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        clientName.ifBlank { "External TCG Peer" },
                        clientType,
                        endpointUri,
                        baseVariantId,
                        requiresStrictBase
                    )
                },
                modifier = Modifier.testTag("confirm_include_client_button")
            ) {
                Text("Include Client")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
