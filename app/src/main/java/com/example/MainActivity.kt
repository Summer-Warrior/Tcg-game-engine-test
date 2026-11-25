package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.data.AlignmentCheckReport
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardTemplate
import com.example.data.DiscordApiService
import com.example.data.FederatedCardPack
import com.example.data.FirestoreGameEventLogService
import com.example.data.FirestoreGameStateManager
import com.example.data.GameBoardSyncService
import com.example.data.NexusHubBridgeService
import com.example.data.NexusTcgRepository
import com.example.data.RegistryAssetLoader
import com.example.data.StartupAlignmentChecker
import com.example.engine.CatalogBlueprint
import com.example.ui.components.ConfirmOpenClientWindowDialog
import com.example.ui.components.FloatingRulesetProtocolInspectorHost
import com.example.ui.components.PendingClientLaunchSelection
import com.example.ui.components.StartupAlignmentDialog
import com.example.ui.screens.CardVaultAtlasScreen
import com.example.ui.screens.DiscordSettingsHubScreen
import com.example.ui.screens.LoreAvatarsMemorialScreen
import com.example.ui.screens.MasterHubScreen
import com.example.ui.screens.TabletopArenaScreen
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.chameleonAccentFor
import com.example.viewmodel.NexusTcgViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

data class DeepLinkNavigationTarget(
    val section: NexusSection = NexusSection.HUB,
    val vaultSubTab: Int = 0,
    val discordSubTab: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiscordApiService.init(this)
        val initialTarget = parseDeepLinkTarget(intent?.data)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppNavigation(initialTarget = initialTarget)
            }
        }
    }

    private fun parseDeepLinkTarget(uri: android.net.Uri?): DeepLinkNavigationTarget {
        if (uri == null || uri.scheme != "nexus-tcg-engine" || uri.host != "open") {
            return DeepLinkNavigationTarget()
        }
        val hubSync = uri.getQueryParameter("hubSync") == "1"
        val exportClient = uri.getQueryParameter("exportClientInfo") == "1"
        val fromCardMaker = uri.getQueryParameter("from").equals("tcgstudio", ignoreCase = true)
        if (hubSync || exportClient || fromCardMaker) {
            return DeepLinkNavigationTarget(section = NexusSection.VAULT, vaultSubTab = 4)
        }
        return when (uri.getQueryParameter("section")?.trim()?.lowercase().orEmpty()) {
            "vault", "decks", "atlas", "bridge", "handshake" -> DeepLinkNavigationTarget(NexusSection.VAULT, vaultSubTab = if (uri.getQueryParameter("section") == "handshake") 4 else 0)
            "tabletop", "arena" -> DeepLinkNavigationTarget(NexusSection.TABLETOP)
            "lore", "avatars", "memorial" -> DeepLinkNavigationTarget(NexusSection.LORE)
            "discord", "discord_rp" -> DeepLinkNavigationTarget(NexusSection.DISCORD_SETTINGS, discordSubTab = 0)
            "settings" -> DeepLinkNavigationTarget(NexusSection.DISCORD_SETTINGS, discordSubTab = 1)
            else -> DeepLinkNavigationTarget(NexusSection.HUB)
        }
    }
}

/**
 * Exact 5 Clean Navigation Sections:
 * 1. Master Hub
 * 2. Card Vault & 20-Color Atlas (includes MPV Bridge / Handshake Area)
 * 3. Tabletop (ONE consolidated playmat-first tabletop)
 * 4. Lore, Avatars & Memorial Forge
 * 5. Discord RP & Settings
 */
enum class NexusSection(
    val routeId: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HUB(
        routeId = "hub",
        labelResId = R.string.nav_hub,
        selectedIcon = Icons.Filled.DashboardCustomize,
        unselectedIcon = Icons.Outlined.DashboardCustomize
    ),
    VAULT(
        routeId = "vault",
        labelResId = R.string.nav_vault,
        selectedIcon = Icons.Filled.Style,
        unselectedIcon = Icons.Outlined.Style
    ),
    TABLETOP(
        routeId = "tabletop",
        labelResId = R.string.nav_tabletop,
        selectedIcon = Icons.Filled.SportsEsports,
        unselectedIcon = Icons.Outlined.SportsEsports
    ),
    LORE(
        routeId = "lore",
        labelResId = R.string.nav_lore,
        selectedIcon = Icons.Filled.AutoStories,
        unselectedIcon = Icons.Outlined.AutoStories
    ),
    DISCORD_SETTINGS(
        routeId = "discord_settings",
        labelResId = R.string.nav_discord_settings,
        selectedIcon = Icons.Filled.Forum,
        unselectedIcon = Icons.Outlined.Forum
    )
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    initialTarget: DeepLinkNavigationTarget = DeepLinkNavigationTarget()
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
                initialTarget = initialTarget
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexusTcgMainScreen(
    currentUserId: String,
    currentUserName: String,
    initialTarget: DeepLinkNavigationTarget = DeepLinkNavigationTarget()
) {
    val context = LocalContext.current
    remember(context) {
        DiscordApiService.init(context)
        true
    }

    val registryBundle = remember(context) { RegistryAssetLoader.loadAll(context) }

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

    var currentSection by rememberSaveable { mutableStateOf(initialTarget.section) }
    var vaultSubTab by rememberSaveable { mutableIntStateOf(initialTarget.vaultSubTab) }
    var discordSubTab by rememberSaveable { mutableIntStateOf(initialTarget.discordSubTab) }
    var localBannerMessage by remember { mutableStateOf<String?>(null) }

    // Dynamic catalog state (bundled 160+ blueprints + user/AI synthesized combos)
    val synthesizedCombos = remember { mutableStateListOf<CatalogBlueprint>() }
    val allBlueprints = remember(registryBundle.tcgCatalog, synthesizedCombos.size) {
        synthesizedCombos + registryBundle.tcgCatalog
    }
    var activeFormatBlueprint by remember {
        mutableStateOf(registryBundle.tcgCatalog.firstOrNull())
    }

    // Dynamic federated card packs (bundled + pulled from MPV Card Maker)
    val pulledPacks = remember { mutableStateListOf<FederatedCardPack>() }
    val allCardPacks = remember(registryBundle.federatedCardPacks, pulledPacks.size) {
        pulledPacks + registryBundle.federatedCardPacks
    }

    // Live IC Memorial feed entries bridged from Tabletop IC Chat -> Section 4 Memorial Forge
    val icMemorialFeed = remember { mutableStateListOf<String>() }

    // Automatic On-Open Alignment & Update Check + Pop-Up State
    var alignmentReport by remember { mutableStateOf<AlignmentCheckReport?>(null) }
    var showAlignmentPopup by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        viewModel.attachTelemetryContext(context)
    }

    LaunchedEffect(Unit) {
        val report = StartupAlignmentChecker.runStartupCheck(context)
        alignmentReport = report
        if (report.shouldShowPopup) {
            showAlignmentPopup = true
        }
    }

    if (currentSection != NexusSection.HUB) {
        BackHandler {
            currentSection = NexusSection.HUB
        }
    }

    val selectedBaseVariantId by viewModel.selectedBaseVariantId.collectAsStateWithLifecycle()
    val activeCustomVariant by viewModel.activeCustomVariant.collectAsStateWithLifecycle()
    val activeClientNode by viewModel.activeClientNode.collectAsStateWithLifecycle()
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
    val vmStatusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val telemetrySnapshot by viewModel.telemetrySnapshot.collectAsStateWithLifecycle()

    val chameleonAccent by animateColorAsState(
        targetValue = chameleonAccentFor(activeFormatBlueprint, selectedBaseVariantId),
        label = "chameleonAccent"
    )

    val sendUsageReportToCardMaker: () -> Unit = {
        val reportJson = telemetrySnapshot.toJsonString()
        val result = NexusHubBridgeService.sendUsageReportToCardMaker(context, reportJson)
        localBannerMessage = result.statusMessage
    }

    val activeBanner = localBannerMessage ?: vmStatusBanner
    val activeBaseSpec = BuiltInTcgCatalog.findBaseSpec(selectedBaseVariantId)
    val activeUiProfile = BuiltInTcgCatalog.systemUiProfileFor(selectedBaseVariantId, activeClientNode)
    val registeredClients = (clientsState as? UiState.Success)?.data.orEmpty()
    val availableDecksList = (decksState as? UiState.Success)?.data.orEmpty()
    val availableMatchesList = (matchesState as? UiState.Success)?.data.orEmpty()
    val currentActiveMatch = availableMatchesList.firstOrNull { it.id == activeMatchId }
        ?: availableMatchesList.firstOrNull()
    val connectedClientsList = registeredClients.filter { it.connectionStatus == "CONNECTED" }

    var isRulesetInspectorOpen by rememberSaveable { mutableStateOf(false) }
    var pendingClientLaunch by remember { mutableStateOf<PendingClientLaunchSelection?>(null) }

    MyApplicationTheme(baseVariantId = selectedBaseVariantId) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
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
                                                    color = if (activeFormatBlueprint?.upstreamSourceLock == true) EmeraldReady else chameleonAccent,
                                                    shape = CircleShape
                                                )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = activeFormatBlueprint?.name ?: activeUiProfile.systemTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = chameleonAccent,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { isRulesetInspectorOpen = !isRulesetInspectorOpen },
                                    modifier = Modifier.testTag("top_inspector_debug_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BugReport,
                                        contentDescription = "Toggle Floating Ruleset & Protocol Inspector",
                                        tint = if (isRulesetInspectorOpen) RadiantGold else chameleonAccent
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
                                        contentDescription = "Open Active Client Window",
                                        tint = chameleonAccent
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        signOut(credentialManager = credentialManager, scope = scope)
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(chameleonAccent.copy(alpha = 0.65f))
                        )
                    }
                },
                bottomBar = {
                    if (!isExpanded) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .background(chameleonAccent.copy(alpha = 0.55f))
                            )
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = chameleonAccent,
                                            selectedTextColor = chameleonAccent,
                                            indicatorColor = chameleonAccent.copy(alpha = 0.18f)
                                        ),
                                        modifier = Modifier.testTag("nav_tab_${section.routeId}")
                                    )
                                }
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
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = chameleonAccent,
                                        selectedTextColor = chameleonAccent,
                                        indicatorColor = chameleonAccent.copy(alpha = 0.18f)
                                    ),
                                    modifier = Modifier.testTag("rail_tab_${section.routeId}")
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(visible = activeBanner != null) {
                            activeBanner?.let { bannerText ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
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
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            localBannerMessage = null
                                            viewModel.dismissBanner()
                                        },
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
                                NexusSection.HUB -> {
                                    MasterHubScreen(
                                        catalogBlueprints = allBlueprints,
                                        companionApps = registryBundle.companionApps,
                                        alignmentReport = alignmentReport,
                                        activeFormatBlueprintId = activeFormatBlueprint?.id ?: "",
                                        onSelectBlueprintForTabletop = { bp ->
                                            activeFormatBlueprint = bp
                                            if (bp.id in setOf("EDOPRO_BASE_MR5", "FORGE_MTG_BASE", "EDOPRO_CUSTOM_FORK", "FORGE_CUSTOM_CLONE", "HYBRID_FORGE_EDOPRO_MIX")) {
                                                viewModel.selectBaseVariant(bp.id)
                                            }
                                            localBannerMessage = "Loaded '${bp.name}' (${bp.boardArchetype}) on Tabletop."
                                            currentSection = NexusSection.TABLETOP
                                        },
                                        onAddSynthesizedCombo = { combo ->
                                            if (synthesizedCombos.none { it.id == combo.id }) {
                                                synthesizedCombos.add(0, combo)
                                            }
                                        },
                                        onOpenSeparateClientWindow = { baseId ->
                                            val matchingClient = registeredClients.firstOrNull { it.baseVariantId == baseId }
                                            pendingClientLaunch = PendingClientLaunchSelection(
                                                client = matchingClient,
                                                baseVariantId = baseId,
                                                activeMatchId = activeMatchId,
                                                sourceLabel = "Master Hub"
                                            )
                                        },
                                        onOpenHandshakeArea = {
                                            vaultSubTab = 4
                                            currentSection = NexusSection.VAULT
                                        },
                                        onStatusMessage = { localBannerMessage = it },
                                        splitOffSetCount = allCardPacks.count { it.isSplitOffSet },
                                        trackedUsagePlays = telemetrySnapshot.totalPlaysRecorded
                                    )
                                }

                                NexusSection.VAULT -> {
                                    CardVaultAtlasScreen(
                                        initialSubTab = vaultSubTab,
                                        selectedBaseVariantId = selectedBaseVariantId,
                                        decksState = decksState,
                                        federatedCardPacks = allCardPacks,
                                        customSynthesizedCombos = synthesizedCombos.toList(),
                                        alignmentReport = alignmentReport,
                                        onSelectBaseVariant = { viewModel.selectBaseVariant(it) },
                                        onCreateDeck = { deckName, baseId, tag, cardName, cost, atk, def, ability, atk21, def21, var21, codeStr21, codeVarExpr21 ->
                                            viewModel.createCustomDeckAndCard(
                                                deckName, baseId, tag, cardName, cost, atk, def, ability, atk21, def21, var21, codeStr21, codeVarExpr21
                                            )
                                        },
                                        onDeleteDeck = { viewModel.deleteDeck(it) },
                                        onBroadcastDeckToDiscord = { viewModel.broadcastDeckSummaryToDiscord(it) },
                                        onTestImportedCardOnTabletop = { card ->
                                            val parentPack = allCardPacks.firstOrNull { pack ->
                                                pack.cards.any { it.cardId == card.cardId && it.targetClientId == card.targetClientId }
                                            }
                                            val template = CardTemplate(
                                                id = card.cardId,
                                                passcode = card.cardId.filter { it.isDigit() }.ifBlank { "89631139" },
                                                name = card.name,
                                                baseVariantId = card.targetClientId.ifBlank { selectedBaseVariantId },
                                                cardType = card.cardType,
                                                cost = 1,
                                                attack = card.atk21.toIntOrNull() ?: 3000,
                                                defense = card.def21.toIntOrNull() ?: 2500,
                                                attack21 = card.atk21,
                                                defense21 = card.def21,
                                                abilityText = "${card.pipCost} • ${card.ability}",
                                                artResId = R.drawable.ic_launcher_foreground,
                                                cardOrigin = card.cardOrigin,
                                                linkedCanonCardId = card.linkedCanonCardId,
                                                splitOffSetId = parentPack?.splitOff?.splitOffId.orEmpty(),
                                                parentOfficialSetId = parentPack?.splitOff?.parentOfficialSetId.orEmpty(),
                                                pipCost = card.pipCost
                                            )
                                            viewModel.addImportedCardToHandAndTrack(template, currentActiveMatch)
                                            localBannerMessage = "Sent '${card.name}' (${card.cardOrigin.wireValue}) to Tabletop & live telemetry!"
                                            currentSection = NexusSection.TABLETOP
                                        },
                                        onAddPulledCardPacks = { packs ->
                                            packs.forEach { pack ->
                                                if (pulledPacks.none { it.packId == pack.packId }) {
                                                    pulledPacks.add(0, pack)
                                                }
                                            }
                                        },
                                        onRerunStartupAlignment = {
                                            scope.launch {
                                                val fresh = StartupAlignmentChecker.runStartupCheck(
                                                    context = context,
                                                    forceShowEvenIfDismissed = true
                                                )
                                                alignmentReport = fresh
                                                localBannerMessage = fresh.summaryMessage
                                            }
                                        },
                                        onStatusMessage = { localBannerMessage = it },
                                        telemetrySnapshot = telemetrySnapshot,
                                        onSendUsageReportToCardMaker = sendUsageReportToCardMaker,
                                        onArchiveResetTelemetry = {
                                            viewModel.archiveAndResetTelemetry()
                                            localBannerMessage = "Archived & reset local Card Usage Telemetry counters."
                                        }
                                    )
                                }

                                NexusSection.TABLETOP -> {
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
                                        onStepGameEnginePhase = { viewModel.stepGameEnginePhase(it) },
                                        onSelectGameEnginePhase = { phase, match -> viewModel.setGameEnginePhase(phase, match) },
                                        onQueueCardEffect = { card, effectType, match -> viewModel.queueCardEffectOnStack(card, effectType, match) },
                                        onResolveNextEffect = { viewModel.resolveNextStackEffect(it) },
                                        onResolveAllEffects = { viewModel.resolveFullEffectStack(it) },
                                        onAdjustResources = { delta, match -> viewModel.adjustMatchOrEngineResources(delta, match) },
                                        onSetResourcesExact = { exact, match -> viewModel.setMatchOrEngineResources(exact, match) },
                                        onRefillResources = { viewModel.refillMatchOrEngineResources(it) },
                                        onExpandMaxResources = { viewModel.expandMatchOrEngineMaxResources(it) },
                                        onRecordMatchActionLog = { match, logEntry -> viewModel.recordMatchActionLog(match, logEntry) },
                                        onBroadcastBoardState = { match, placements, note, actor ->
                                            viewModel.broadcastCanvasBoardState(match, placements, note, actor)
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
                                        onBroadcastGameStartToDiscord = { viewModel.broadcastGameStartToDiscord(match = it) },
                                        onBroadcastMatchWinnerToDiscord = { viewModel.broadcastMatchWinnerToDiscord(match = it) },
                                        realtimeGameSyncState = realtimeGameSyncState,
                                        onSelectSyncSeat = { viewModel.setLocalSyncSeat(it) },
                                        onJoinSyncMatchById = { viewModel.joinSyncedMatchById(it) },
                                        onAdjustSyncedLifePoints = { seat, delta -> viewModel.adjustSyncedLifePoints(seat, delta) },
                                        onSetSyncedLifePointsExact = { seat, exact -> viewModel.setSyncedLifePointsExact(seat, exact) },
                                        onDeploySyncedCardForActiveSeat = { viewModel.deploySyncedCardForActiveSeat() },
                                        onToggleSyncedZoneOrientation = { viewModel.toggleSyncedZoneOrientation(it) },
                                        onRemoveSyncedZonePlacement = { viewModel.removeSyncedZonePlacement(it) },
                                        onResetSyncedLifeAndBoard = { viewModel.resetSyncedLifeAndBoard() },
                                        persistentEventsState = persistentMatchEventsState,
                                        onLogQuickCardPlay = { viewModel.logQuickCardPlayedEvent(it) },
                                        onLogQuickLifeReduction = { viewModel.logQuickLifeReductionEvent(it) },
                                        onLogCustomGameEvent = { viewModel.logCustomGameEvent(it) },
                                        activeFormatBlueprint = activeFormatBlueprint,
                                        onRecordIcMemorialEntry = { entry -> icMemorialFeed.add(0, entry) },
                                        telemetrySnapshot = telemetrySnapshot,
                                        onSendUsageReportToCardMaker = sendUsageReportToCardMaker,
                                        onStatusMessage = { localBannerMessage = it }
                                    )
                                }

                                NexusSection.LORE -> {
                                    LoreAvatarsMemorialScreen(
                                        wikis = registryBundle.wikis,
                                        icFeedEntries = icMemorialFeed.toList(),
                                        onStatusMessage = { localBannerMessage = it },
                                        federatedCardPacks = allCardPacks
                                    )
                                }

                                NexusSection.DISCORD_SETTINGS -> {
                                    DiscordSettingsHubScreen(
                                        initialSubTab = discordSubTab,
                                        selectedBaseVariantId = selectedBaseVariantId,
                                        signedInAccountLabel = currentUserName,
                                        catalogBlueprints = allBlueprints,
                                        loadedMatchCards = allCardPacks.flatMap { it.cards },
                                        botsDirectory = registryBundle.bots,
                                        discordServersState = discordServersState,
                                        rpCharactersState = rpCharactersState,
                                        discordMessagesState = discordMessagesState,
                                        selectedDiscordServerId = selectedDiscordServerId,
                                        discordEnvSettings = discordEnvSettings,
                                        discordBridgeConfig = discordBridgeConfig,
                                        discordBroadcastHistory = discordBroadcastHistory,
                                        activeMatch = currentActiveMatch,
                                        availableDecks = availableDecksList,
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
                                        onUpdateBridgeConfig = { channel, autoStart, autoWinner, autoDeck ->
                                            viewModel.updateDiscordBridgeConfig(channel, autoStart, autoWinner, autoDeck)
                                        },
                                        onBroadcastGameStart = { match, deck -> viewModel.broadcastGameStartToDiscord(match = match, deck = deck) },
                                        onBroadcastDeckSummary = { deck -> viewModel.broadcastDeckSummaryToDiscord(deck = deck) },
                                        onBroadcastMatchWinner = { match, winnerOverride, deck ->
                                            viewModel.broadcastMatchWinnerToDiscord(match = match, winnerNameOverride = winnerOverride, deck = deck)
                                        },
                                        onToggleDiscordIntegration = { viewModel.setDiscordIntegrationEnabled(it) },
                                        onSaveDiscordEnvCredentials = { token, webhook, clientId, enabled ->
                                            viewModel.saveDiscordEnvCredentials(token, webhook, clientId, enabled)
                                        },
                                        onResetToBuildConfigDefaults = { viewModel.resetDiscordEnvToBuildConfig() },
                                        onTestDiscordConnection = { viewModel.testDiscordEnvConnection() },
                                        onOpenHandshakeArea = {
                                            vaultSubTab = 4
                                            currentSection = NexusSection.VAULT
                                        },
                                        onStatusMessage = { localBannerMessage = it }
                                    )
                                }
                            }

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
                                onStepGameEnginePhase = { viewModel.stepGameEnginePhase(currentActiveMatch) }
                            )
                        }
                    }
                }
            }

            // Automatic On-Open Alignment & Update Check Pop-Up
            val currentReport = alignmentReport
            if (showAlignmentPopup && currentReport != null) {
                StartupAlignmentDialog(
                    report = currentReport,
                    onOpenHandshakeArea = {
                        showAlignmentPopup = false
                        vaultSubTab = 4
                        currentSection = NexusSection.VAULT
                    },
                    onOpenCardMakerApp = {
                        showAlignmentPopup = false
                        val res = NexusHubBridgeService.openCardMakerAppForHandshake(context)
                        localBannerMessage = res.statusMessage
                    },
                    onDismissForCommitSha = {
                        StartupAlignmentChecker.dismissForCommitKey(context, currentReport.effectiveCommitKey)
                        showAlignmentPopup = false
                    }
                )
            }

            pendingClientLaunch?.let { selection ->
                ConfirmOpenClientWindowDialog(
                    selection = selection,
                    onDismissKeepInHub = { pendingClientLaunch = null },
                    onConfirmOpenNewWindow = { openAdjacent, openSeparateTask ->
                        pendingClientLaunch = null
                        if (selection.client != null) {
                            viewModel.selectClientAndSwitchSystem(selection.client)
                        } else {
                            viewModel.selectBaseVariant(selection.baseVariantId)
                        }
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
