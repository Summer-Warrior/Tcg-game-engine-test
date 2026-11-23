package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.BuiltInTcgCatalog
import com.example.data.DiscordApiService
import com.example.data.FirestoreGameEventLogService
import com.example.data.FirestoreGameStateManager
import com.example.data.GameBoardSyncService
import com.example.data.NexusTcgRepository
import com.example.ui.components.ConfirmOpenClientWindowDialog
import com.example.ui.components.FloatingRulesetProtocolInspectorHost
import com.example.ui.components.PendingClientLaunchSelection
import com.example.ui.screens.ClientBridgeScreen
import com.example.ui.screens.DeckForgeScreen
import com.example.ui.screens.DiscordRoleplayScreen
import com.example.ui.screens.EnginesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TabletopArenaScreen
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RadiantGold
import com.example.viewmodel.NexusTcgViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiscordApiService.init(this)
        val initialSection = parseDeepLinkSection(intent?.data)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation(initialSection = initialSection)
            }
        }
    }

    private fun parseDeepLinkSection(uri: android.net.Uri?): NexusSection {
        if (uri == null || uri.scheme != "nexus-tcg-engine" || uri.host != "open") {
            return NexusSection.ENGINES
        }
        val requested = uri.getQueryParameter("section")?.trim()?.lowercase().orEmpty()
        return when (requested) {
            "settings", "hub" -> NexusSection.SETTINGS
            "clients" -> NexusSection.CLIENTS
            "decks" -> NexusSection.DECKS
            "arena", "tabletop" -> NexusSection.ARENA
            "discord", "discord_rp" -> NexusSection.DISCORD_RP
            else -> if (uri.getQueryParameter("hubSync") == "1") NexusSection.SETTINGS else NexusSection.ENGINES
        }
    }
}

enum class NexusSection(
    val routeId: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    ENGINES(
        routeId = "engines",
        labelResId = R.string.nav_engines,
        selectedIcon = Icons.Filled.DashboardCustomize,
        unselectedIcon = Icons.Outlined.DashboardCustomize
    ),
    CLIENTS(
        routeId = "clients",
        labelResId = R.string.nav_clients,
        selectedIcon = Icons.Filled.Hub,
        unselectedIcon = Icons.Outlined.Hub
    ),
    DECKS(
        routeId = "decks",
        labelResId = R.string.nav_decks,
        selectedIcon = Icons.Filled.Style,
        unselectedIcon = Icons.Outlined.Style
    ),
    ARENA(
        routeId = "arena",
        labelResId = R.string.nav_tabletop,
        selectedIcon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports
    ),
    DISCORD_RP(
        routeId = "discord_rp",
        labelResId = R.string.nav_discord_rp,
        selectedIcon = Icons.Filled.Forum,
        unselectedIcon = Icons.Outlined.Forum
    ),
    SETTINGS(
        routeId = "settings",
        labelResId = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    initialSection: NexusSection = NexusSection.ENGINES
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Commander"
        key(uid) {
            NexusTcgMainScreen(
                currentUserId = uid,
                currentUserName = displayName,
                initialSection = initialSection
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusTcgMainScreen(
    currentUserId: String,
    currentUserName: String,
    initialSection: NexusSection = NexusSection.ENGINES
) {
    val context = LocalContext.current
    remember(context) {
        DiscordApiService.init(context)
        true
    }

    val viewModel: NexusTcgViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                NexusTcgViewModel(
                    repository = NexusTcgRepository(db),
                    currentUserId = currentUserId,
                    currentUserName = currentUserName,
                    boardSyncService = GameBoardSyncService(db),
                    gameStateManager = FirestoreGameStateManager(db),
                    eventLogService = FirestoreGameEventLogService(db)
                )
            }
        }
    )

    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentSection by rememberSaveable { mutableStateOf(initialSection) }

    if (currentSection != NexusSection.ENGINES) {
        BackHandler {
            currentSection = NexusSection.ENGINES
        }
    }

    val selectedBaseVariantId by viewModel.selectedBaseVariantId.collectAsStateWithLifecycle()
    val activeCustomVariant by viewModel.activeCustomVariant.collectAsStateWithLifecycle()
    val activeClientNode by viewModel.activeClientNode.collectAsStateWithLifecycle()
    val variantsState by viewModel.variantsState.collectAsStateWithLifecycle()
    val clientsState by viewModel.clientsState.collectAsStateWithLifecycle()
    val decksState by viewModel.decksState.collectAsStateWithLifecycle()
    val matchesState by viewModel.matchesState.collectAsStateWithLifecycle()
    val discordServersState by viewModel.discordServersState.collectAsStateWithLifecycle()
    val rpCharactersState by viewModel.rpCharactersState.collectAsStateWithLifecycle()
    val discordMessagesState by viewModel.discordMessagesState.collectAsStateWithLifecycle()
    val selectedDiscordServerId by viewModel.selectedDiscordServerId.collectAsStateWithLifecycle()
    val discordEnvSettings by viewModel.discordEnvSettings.collectAsStateWithLifecycle()
    val discordBridgeConfig by viewModel.discordBridgeConfig.collectAsStateWithLifecycle()
    val discordBroadcastHistory by viewModel.discordBroadcastHistory.collectAsStateWithLifecycle()
    val activeMatchId by viewModel.activeMatchId.collectAsStateWithLifecycle()
    val syncedBoardState by viewModel.syncedBoardState.collectAsStateWithLifecycle()
    val realtimeGameSyncState by viewModel.realtimeGameSyncState.collectAsStateWithLifecycle()
    val persistentMatchEventsState by viewModel.persistentMatchEventsState.collectAsStateWithLifecycle()
    val gameEngineState by viewModel.gameEngineState.collectAsStateWithLifecycle()
    val playerHand by viewModel.playerHand.collectAsStateWithLifecycle()
    val lastHandshakeDiagnostic by viewModel.lastHandshakeDiagnostic.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val activeBaseSpec = BuiltInTcgCatalog.findBaseSpec(selectedBaseVariantId)
    val activeUiProfile = BuiltInTcgCatalog.systemUiProfileFor(selectedBaseVariantId, activeClientNode)
    val registeredClients = (clientsState as? UiState.Success)?.data.orEmpty()
    val availableDecksList = (decksState as? UiState.Success)?.data.orEmpty()
    val availableMatchesList = (matchesState as? UiState.Success)?.data.orEmpty()
    val currentActiveMatch = availableMatchesList.firstOrNull { it.id == activeMatchId }
        ?: availableMatchesList.firstOrNull()
    val connectedClientsList = registeredClients.filter { it.connectionStatus == "CONNECTED" }
    val connectedClientCount = connectedClientsList.size
    val isUnmodifiedBase = activeCustomVariant == null || activeCustomVariant?.isBaseStandard == true
    var isRulesetInspectorOpen by rememberSaveable { mutableStateOf(false) }
    var pendingClientLaunch by remember { mutableStateOf<PendingClientLaunchSelection?>(null) }

    // Dynamically switch the entire app's MaterialTheme ColorScheme to the active TCG Client's System
    MyApplicationTheme(baseVariantId = selectedBaseVariantId) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(
                                        text = stringResource(id = R.string.app_name),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    color = if (activeBaseSpec.isCustomProjectClone) {
                                                        RadiantGold
                                                    } else if (isUnmodifiedBase) {
                                                        EmeraldReady
                                                    } else {
                                                        RadiantGold
                                                    },
                                                    shape = CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "System UI: ${activeUiProfile.hudBadgeLabel} • ${activeUiProfile.lifeLabel} • $connectedClientCount Clients",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { isRulesetInspectorOpen = !isRulesetInspectorOpen }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("header_protocol_chip")
                            ) {
                                Text(
                                    text = when (activeBaseSpec.baseVariantId) {
                                        "EDOPRO_BASE_MR5" -> "EDOPro Base"
                                        "EDOPRO_CUSTOM_FORK" -> "EDOPro 21d Clone"
                                        "FORGE_MTG_BASE" -> "Card-Forge MTG"
                                        "FORGE_CUSTOM_CLONE" -> "Forge 21d Clone"
                                        "HYBRID_FORGE_EDOPRO_MIX" -> "Mixed Forge+EDO"
                                        else -> activeBaseSpec.name.substringBefore(" ")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (activeBaseSpec.isCustomProjectClone) {
                                        RadiantGold
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(
                                onClick = { isRulesetInspectorOpen = !isRulesetInspectorOpen },
                                modifier = Modifier.testTag("top_inspector_debug_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = "Toggle Floating Ruleset & Protocol Inspector",
                                    tint = if (isRulesetInspectorOpen) {
                                        RadiantGold
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                )
                            }
                            IconButton(
                                onClick = {
                                    pendingClientLaunch = PendingClientLaunchSelection(
                                        client = activeClientNode,
                                        baseVariantId = selectedBaseVariantId,
                                        activeMatchId = activeMatchId,
                                        sourceLabel = "Top Bar Launch"
                                    )
                                },
                                modifier = Modifier.testTag("open_active_client_window_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DesktopWindows,
                                    contentDescription = "Open Active Client System in Separate Window",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { currentSection = NexusSection.SETTINGS },
                                modifier = Modifier.testTag("settings_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = stringResource(id = R.string.nav_settings),
                                    tint = if (currentSection == NexusSection.SETTINGS) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            IconButton(
                                onClick = {
                                    signOut(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = stringResource(id = R.string.sign_out),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                bottomBar = {
                    if (!isExpanded) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            NexusSection.entries.forEach { section ->
                                val selected = currentSection == section
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentSection = section },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                                            contentDescription = stringResource(id = section.labelResId)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(id = section.labelResId),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("nav_tab_${section.routeId}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpanded) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            NexusSection.entries.forEach { section ->
                                val selected = currentSection == section
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentSection = section },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) section.selectedIcon else section.unselectedIcon,
                                            contentDescription = stringResource(id = section.labelResId)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(id = section.labelResId),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    modifier = Modifier.testTag("rail_tab_${section.routeId}")
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Active TCG Client System Bar (Quick UI Switcher + Separate Window Launcher)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("active_client_system_bar"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "ACTIVE CLIENT SYSTEM: ${activeUiProfile.systemTitle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(activeUiProfile.accentHex),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Layout: ${activeUiProfile.boardLayoutType} • ${activeUiProfile.lifeLabel} • ${activeUiProfile.deployVerb}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        pendingClientLaunch = PendingClientLaunchSelection(
                                            client = activeClientNode,
                                            baseVariantId = selectedBaseVariantId,
                                            activeMatchId = activeMatchId,
                                            sourceLabel = "Active Client System Bar"
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("open_active_client_window_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open Client in Separate Window",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Open Window",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (registeredClients.isNotEmpty()) {
                                    registeredClients.forEach { client ->
                                        val isCurrentClient = selectedBaseVariantId == client.baseVariantId &&
                                            (activeClientNode?.id == client.id || activeClientNode == null)
                                        FilterChip(
                                            selected = isCurrentClient,
                                            onClick = {
                                                viewModel.selectClientAndSwitchSystem(client)
                                                pendingClientLaunch = PendingClientLaunchSelection(
                                                    client = client,
                                                    baseVariantId = client.baseVariantId,
                                                    activeMatchId = activeMatchId,
                                                    sourceLabel = "Client Quick-Switch Bar"
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = client.clientName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.SwapHoriz,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            },
                                            modifier = Modifier.testTag("quick_switch_client_${client.id}")
                                        )
                                    }
                                } else {
                                    BuiltInTcgCatalog.baseVariants.forEach { spec ->
                                        val isCurrent = selectedBaseVariantId == spec.baseVariantId
                                        FilterChip(
                                            selected = isCurrent,
                                            onClick = {
                                                viewModel.selectBaseVariant(spec.baseVariantId)
                                                pendingClientLaunch = PendingClientLaunchSelection(
                                                    client = null,
                                                    baseVariantId = spec.baseVariantId,
                                                    activeMatchId = activeMatchId,
                                                    sourceLabel = "Base System Quick-Switch"
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = spec.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1
                                                )
                                            },
                                            modifier = Modifier.testTag("quick_switch_base_${spec.baseVariantId}")
                                        )
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(visible = statusBanner != null) {
                            statusBanner?.let { bannerText ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("status_banner"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = bannerText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.dismissBanner() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss Banner",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            when (currentSection) {
                                NexusSection.ENGINES -> {
                                    EnginesScreen(
                                        selectedBaseVariantId = selectedBaseVariantId,
                                        activeCustomVariant = activeCustomVariant,
                                        variantsState = variantsState,
                                        connectedClientCount = connectedClientCount,
                                        onSelectBaseVariant = { viewModel.selectBaseVariant(it) },
                                        onActivateCustomVariant = { viewModel.activateCustomVariant(it) },
                                        onResetToBaseStandard = { viewModel.resetToActiveBaseStandard() },
                                        onCreateVariant = { name, baseId, resName, life, hand, maxRes, combat ->
                                            viewModel.createNewEngineVariant(name, baseId, resName, life, hand, maxRes, combat)
                                        },
                                        onCloneEdoProProject = { projName, cdbFile, lp21, zones, var21, arg21, fn, codeStr21, codeVarExpr21 ->
                                            viewModel.cloneEdoProForCustomProject(
                                                projName, cdbFile, lp21, zones, var21, arg21, fn, codeStr21, codeVarExpr21
                                            )
                                        },
                                        onCloneForgeProject = { projName, cardsFolder, life21, manaPool, svar21, arg21, fn ->
                                            viewModel.cloneForgeForCustomProject(
                                                projName, cardsFolder, life21, manaPool, svar21, arg21, fn
                                            )
                                        },
                                        onSpawnMixedForgeEdoProClient = { mixName, life21, extraMod, var21, arg21 ->
                                            viewModel.spawnMixedForgeEdoProClient(
                                                mixName, life21, extraMod, var21, arg21
                                            )
                                        },
                                        onAddExtensionToMixedClient = { modName, modCat, var21 ->
                                            viewModel.addExtensionToMixedClient(
                                                targetClient = null,
                                                newModuleName = modName,
                                                newModuleCategory = modCat,
                                                newVarInput21 = var21
                                            )
                                        },
                                        onDeleteVariant = { viewModel.deleteVariant(it) },
                                        onNavigateToClients = { currentSection = NexusSection.CLIENTS },
                                        onRequestConfirmOpenClientWindow = { baseId ->
                                            val matchingClient = registeredClients.firstOrNull { it.baseVariantId == baseId }
                                            pendingClientLaunch = PendingClientLaunchSelection(
                                                client = matchingClient,
                                                baseVariantId = baseId,
                                                activeMatchId = activeMatchId,
                                                sourceLabel = "Engines Hub"
                                            )
                                        }
                                    )
                                }
                                NexusSection.CLIENTS -> {
                                    ClientBridgeScreen(
                                        selectedBaseVariantId = selectedBaseVariantId,
                                        activeCustomVariant = activeCustomVariant,
                                        clientsState = clientsState,
                                        lastHandshakeDiagnostic = lastHandshakeDiagnostic,
                                        onIncludeClient = { name, type, uri, baseId, strict ->
                                            viewModel.includeExternalClient(name, type, uri, baseId, strict)
                                        },
                                        onConnectClient = { client, syncToBase ->
                                            viewModel.connectClientNode(client, syncToBase)
                                        },
                                        onDisconnectClient = { viewModel.disconnectClientNode(it) },
                                        onDeleteClient = { viewModel.deleteClientNode(it) },
                                        onSelectBaseVariant = { viewModel.selectBaseVariant(it) },
                                        onCloneEdoProProject = { projName, cdbFile, lp21, zones, var21, arg21, fn, codeStr21, codeVarExpr21 ->
                                            viewModel.cloneEdoProForCustomProject(
                                                projName, cdbFile, lp21, zones, var21, arg21, fn, codeStr21, codeVarExpr21
                                            )
                                        },
                                        onCloneForgeProject = { projName, cardsFolder, life21, manaPool, svar21, arg21, fn ->
                                            viewModel.cloneForgeForCustomProject(
                                                projName, cardsFolder, life21, manaPool, svar21, arg21, fn
                                            )
                                        },
                                        onSpawnMixedForgeEdoProClient = { mixName, life21, extraMod, var21, arg21 ->
                                            viewModel.spawnMixedForgeEdoProClient(
                                                mixName, life21, extraMod, var21, arg21
                                            )
                                        },
                                        onAddExtensionToMixedClient = { targetClient, modName, modCat, var21 ->
                                            viewModel.addExtensionToMixedClient(
                                                targetClient = targetClient,
                                                newModuleName = modName,
                                                newModuleCategory = modCat,
                                                newVarInput21 = var21
                                            )
                                        },
                                        onExecuteClient21DigitFunction = { client, var21, arg21, fn, codeStr21, codeVarExpr21 ->
                                            viewModel.executeClient21DigitFunction(
                                                client, var21, arg21, fn, codeStr21, codeVarExpr21
                                            )
                                        },
                                        onSyncBaseClientFromGitHubSource = { client ->
                                            viewModel.syncBaseClientFromUpstreamGitHub(client)
                                        },
                                        onResetToBaseStandard = { viewModel.resetToActiveBaseStandard() },
                                        onRequestConfirmOpenClientWindow = { client, baseId ->
                                            pendingClientLaunch = PendingClientLaunchSelection(
                                                client = client,
                                                baseVariantId = baseId,
                                                activeMatchId = activeMatchId,
                                                sourceLabel = "Client Bridge"
                                            )
                                        }
                                    )
                                }
                                NexusSection.DECKS -> {
                                    DeckForgeScreen(
                                        selectedBaseVariantId = selectedBaseVariantId,
                                        decksState = decksState,
                                        onSelectBaseVariant = { viewModel.selectBaseVariant(it) },
                                        onCreateDeck = { deckName, baseId, tag, cardName, cost, atk, def, ability, atk21, def21, var21, codeStr21, codeVarExpr21 ->
                                            viewModel.createCustomDeckAndCard(
                                                deckName, baseId, tag, cardName, cost, atk, def, ability, atk21, def21, var21, codeStr21, codeVarExpr21
                                            )
                                        },
                                        onDeleteDeck = { viewModel.deleteDeck(it) },
                                        onBroadcastDeckToDiscord = { deck ->
                                            viewModel.broadcastDeckSummaryToDiscord(deck)
                                        }
                                    )
                                }
                                NexusSection.ARENA -> {
                                    TabletopArenaScreen(
                                        selectedBaseVariantId = selectedBaseVariantId,
                                        activeCustomVariant = activeCustomVariant,
                                        activeClientNode = activeClientNode,
                                        matchesState = matchesState,
                                        activeMatchId = activeMatchId,
                                        playerHand = playerHand,
                                        syncedBoardState = syncedBoardState,
                                        gameEngineState = gameEngineState,
                                        onSelectMatch = { viewModel.selectActiveMatch(it) },
                                        onLaunchMatch = { viewModel.launchMultiClientMatch(it) },
                                        onBridgeClientsIntoMatch = { viewModel.bridgeConnectedClientsIntoMatch(it) },
                                        onDrawCard = { baseId, match -> viewModel.drawCardToHand(baseId, match) },
                                        onDeployCard = { match, card -> viewModel.deployCardToBattlefield(match, card) },
                                        onExecuteCombatAndAdvanceTurn = { viewModel.executeCombatAndAdvanceTurn(it) },
                                        onChangePhase = { match, phase -> viewModel.changeMatchPhase(match, phase) },
                                        onStepGameEnginePhase = { match -> viewModel.stepGameEnginePhase(match) },
                                        onSelectGameEnginePhase = { phase, match ->
                                            viewModel.setGameEnginePhase(phase, match)
                                        },
                                        onQueueCardEffect = { card, effectType, match ->
                                            viewModel.queueCardEffectOnStack(card, effectType, match)
                                        },
                                        onResolveNextEffect = { match -> viewModel.resolveNextStackEffect(match) },
                                        onResolveAllEffects = { match -> viewModel.resolveFullEffectStack(match) },
                                        onAdjustResources = { delta, match ->
                                            viewModel.adjustMatchOrEngineResources(delta, match)
                                        },
                                        onSetResourcesExact = { exact, match ->
                                            viewModel.setMatchOrEngineResources(exact, match)
                                        },
                                        onRefillResources = { match ->
                                            viewModel.refillMatchOrEngineResources(match)
                                        },
                                        onExpandMaxResources = { match ->
                                            viewModel.expandMatchOrEngineMaxResources(match)
                                        },
                                        onRecordMatchActionLog = { match, logLine -> viewModel.recordMatchActionLog(match, logLine) },
                                        onBroadcastBoardState = { match, placements, eventType, eventSummary ->
                                            viewModel.broadcastCanvasBoardState(match, placements, eventType, eventSummary)
                                        },
                                        onTriggerPeerClientSyncMove = { match, placements ->
                                            viewModel.triggerPeerClientBoardSyncMove(match, placements)
                                        },
                                        onRequestConfirmOpenClientWindow = { baseId, matchId ->
                                            val matchingClient = registeredClients.firstOrNull { it.baseVariantId == baseId }
                                            pendingClientLaunch = PendingClientLaunchSelection(
                                                client = matchingClient,
                                                baseVariantId = baseId,
                                                activeMatchId = matchId,
                                                sourceLabel = "Tabletop Arena"
                                            )
                                        },
                                        discordDesignatedChannel = discordBridgeConfig.designatedChannelName,
                                        onBroadcastGameStartToDiscord = { match ->
                                            viewModel.broadcastGameStartToDiscord(match = match)
                                        },
                                        onBroadcastMatchWinnerToDiscord = { match ->
                                            viewModel.broadcastMatchWinnerToDiscord(match = match)
                                        },
                                        realtimeGameSyncState = realtimeGameSyncState,
                                        onSelectSyncSeat = { seat -> viewModel.setLocalSyncSeat(seat) },
                                        onJoinSyncMatchById = { roomId -> viewModel.joinSyncedMatchById(roomId) },
                                        onAdjustSyncedLifePoints = { seat, delta ->
                                            viewModel.adjustSyncedLifePoints(seat, delta)
                                        },
                                        onSetSyncedLifePointsExact = { seat, exact ->
                                            viewModel.setSyncedLifePointsExact(seat, exact)
                                        },
                                        onDeploySyncedCardForActiveSeat = {
                                            viewModel.deploySyncedCardForActiveSeat()
                                        },
                                        onToggleSyncedZoneOrientation = { zoneId ->
                                            viewModel.toggleSyncedZoneOrientation(zoneId)
                                        },
                                        onRemoveSyncedZonePlacement = { zoneId ->
                                            viewModel.removeSyncedZonePlacement(zoneId)
                                        },
                                        onResetSyncedLifeAndBoard = {
                                            viewModel.resetSyncedLifeAndBoard()
                                        },
                                        persistentEventsState = persistentMatchEventsState,
                                        onLogQuickCardPlay = { cardName ->
                                            viewModel.logQuickCardPlayedEvent(cardName)
                                        },
                                        onLogQuickLifeReduction = { amount ->
                                            viewModel.logQuickLifeReductionEvent(amount)
                                        },
                                        onLogCustomGameEvent = { summary ->
                                            viewModel.logCustomGameEvent(summary)
                                        }
                                    )
                                }
                                NexusSection.DISCORD_RP -> {
                                    DiscordRoleplayScreen(
                                        selectedBaseVariantId = selectedBaseVariantId,
                                        discordServersState = discordServersState,
                                        rpCharactersState = rpCharactersState,
                                        discordMessagesState = discordMessagesState,
                                        selectedDiscordServerId = selectedDiscordServerId,
                                        onSelectServer = { viewModel.selectDiscordServer(it) },
                                        onJoinOrAddServer = { guildId, sName, chanId, chanName, baseId, lore ->
                                            viewModel.joinOrAddDiscordServer(guildId, sName, chanId, chanName, baseId, lore)
                                        },
                                        onDiscoverBotGuilds = { viewModel.discoverLiveDiscordBotGuilds() },
                                        onReviewAndSyncMessages = { viewModel.reviewAndSyncChannelMessages(it) },
                                        onToggleServerConnection = { viewModel.toggleDiscordServerConnection(it) },
                                        onDeleteServer = { viewModel.deleteDiscordServer(it) },
                                        onCreateCharacter = { cName, cTitle, faction, baseId, sigCard, sigCode21, lp21, prompt, catchphrase, chant ->
                                            viewModel.createRpCharacter(
                                                cName, cTitle, faction, baseId, sigCard, sigCode21, lp21, prompt, catchphrase, chant
                                            )
                                        },
                                        onActivateCharacter = { viewModel.activateRpCharacter(it) },
                                        onUpdateCharacterLp21 = { char, newLp -> viewModel.updateCharacterLp21(char, newLp) },
                                        onDeleteCharacter = { viewModel.deleteRpCharacter(it) },
                                        onSendRoleplayMessage = { srv, char, asChar, content, summary, mType ->
                                            viewModel.sendDiscordRoleplayMessage(srv, char, asChar, content, summary, mType)
                                        },
                                        onTriggerCharacterQuickAct = { srv, char, actType ->
                                            viewModel.triggerCharacterQuickAct(srv, char, actType)
                                        },
                                        onToggleMessageReviewed = { viewModel.toggleDiscordMessageReviewed(it) },
                                        onDeleteMessage = { viewModel.deleteDiscordMessage(it) },
                                        onOpenSettings = { currentSection = NexusSection.SETTINGS },
                                        discordEnvSettings = discordEnvSettings,
                                        discordBridgeConfig = discordBridgeConfig,
                                        discordBroadcastHistory = discordBroadcastHistory,
                                        activeMatch = currentActiveMatch,
                                        availableDecks = availableDecksList,
                                        onUpdateBridgeConfig = { channel, autoStart, autoWinner, autoDeck ->
                                            viewModel.updateDiscordBridgeConfig(channel, autoStart, autoWinner, autoDeck)
                                        },
                                        onBroadcastGameStart = { match, deck ->
                                            viewModel.broadcastGameStartToDiscord(match = match, deck = deck)
                                        },
                                        onBroadcastDeckSummary = { deck ->
                                            viewModel.broadcastDeckSummaryToDiscord(deck = deck)
                                        },
                                        onBroadcastMatchWinner = { match, winnerOverride, deck ->
                                            viewModel.broadcastMatchWinnerToDiscord(
                                                match = match,
                                                winnerNameOverride = winnerOverride,
                                                deck = deck
                                            )
                                        }
                                    )
                                }
                                NexusSection.SETTINGS -> {
                                    SettingsScreen(
                                        discordEnvSettings = discordEnvSettings,
                                        signedInAccountLabel = currentUserName,
                                        onToggleDiscordIntegration = { viewModel.setDiscordIntegrationEnabled(it) },
                                        onSaveDiscordEnvCredentials = { token, webhook, clientId, enabled ->
                                            viewModel.saveDiscordEnvCredentials(token, webhook, clientId, enabled)
                                        },
                                        onResetToBuildConfigDefaults = { viewModel.resetDiscordEnvToBuildConfig() },
                                        onTestDiscordConnection = { viewModel.testDiscordEnvConnection() },
                                        onNavigateToDiscordRp = { currentSection = NexusSection.DISCORD_RP }
                                    )
                                }
                            }

                            // Floating Ruleset & Protocol Debug Inspector Modal Overlay
                            FloatingRulesetProtocolInspectorHost(
                                isOpen = isRulesetInspectorOpen,
                                onToggleOpen = { isRulesetInspectorOpen = it },
                                activeBaseSpec = activeBaseSpec,
                                activeCustomVariant = activeCustomVariant,
                                uiProfile = activeUiProfile,
                                activeClientNode = activeClientNode,
                                connectedClients = connectedClientsList,
                                lastHandshakeDiagnostic = lastHandshakeDiagnostic,
                                gameEngineState = gameEngineState,
                                syncedBoardState = syncedBoardState,
                                onSelectBaseVariant = { viewModel.selectBaseVariant(it) },
                                onResetToBaseStandard = { viewModel.resetToActiveBaseStandard() },
                                onStepGameEnginePhase = { viewModel.stepGameEnginePhase(null) },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Confirmation Modal when a Client is selected to open in a new Window / App
                            pendingClientLaunch?.let { selection ->
                                ConfirmOpenClientWindowDialog(
                                    selection = selection,
                                    onDismissKeepInHub = { pendingClientLaunch = null },
                                    onConfirmOpenNewWindow = { openAdjacent, openSeparateTask ->
                                        pendingClientLaunch = null
                                        ClientWindowLauncher.openClientSystemWindow(
                                            context = context,
                                            client = selection.client,
                                            baseVariantId = selection.baseVariantId,
                                            activeMatchId = selection.activeMatchId,
                                            openInAdjacentWindow = openAdjacent,
                                            openAsSeparateDocumentTask = openSeparateTask
                                        )
                                    },
                                    onConfirmOpenExternalApp = { targetUri ->
                                        pendingClientLaunch = null
                                        ClientWindowLauncher.openExternalOrSeparateWindow(
                                            context = context,
                                            uriString = targetUri,
                                            fallbackClient = selection.client,
                                            fallbackBaseVariantId = selection.baseVariantId
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
