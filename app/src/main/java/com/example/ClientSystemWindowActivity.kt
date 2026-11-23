package com.example

import android.app.Activity
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BaseClientCodeBootSpec
import com.example.data.BaseClientCodeRegistry
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardTemplate
import com.example.data.ClientNode
import com.example.data.FirestoreGameEventLogService
import com.example.data.FirestoreGameStateManager
import com.example.data.GameBoardSyncService
import com.example.data.MatchGameEvent
import com.example.data.MatchSession
import com.example.data.PersistentGameEventType
import com.example.data.RealtimeGameSyncState
import com.example.data.RealtimeSyncConnectionStatus
import com.example.data.SerializedGameBoardState
import com.example.data.SerializedZonePlacement
import com.example.data.SyncClientSeat
import com.example.ui.components.AnimatedSyncedHealthBarCard
import com.example.ui.components.BaseClientCodeBootPanel
import com.example.ui.components.CanvasGameBoard
import com.example.ui.components.D3BoardStateVisualizer
import com.example.ui.components.GameplayActionSidebarLog
import com.example.ui.components.PersistentGameEventLogCard
import com.example.ui.components.RealtimeGameStateSyncPanel
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Helper for launching a TCG Client's system in a separate Android window / multi-task document,
 * or dispatching to an external installed client / repository URI.
 */
object ClientWindowLauncher {
    const val EXTRA_CLIENT_ID = "extra_client_id"
    const val EXTRA_CLIENT_NAME = "extra_client_name"
    const val EXTRA_CLIENT_TYPE = "extra_client_type"
    const val EXTRA_ENDPOINT_URI = "extra_endpoint_uri"
    const val EXTRA_BASE_VARIANT_ID = "extra_base_variant_id"
    const val EXTRA_PROTOCOL_VERSION = "extra_protocol_version"
    const val EXTRA_VAR_INPUT_21 = "extra_var_input_21"
    const val EXTRA_FUNC_INPUT_21 = "extra_func_input_21"
    const val EXTRA_FUNC_NAME = "extra_func_name"
    const val EXTRA_FUNC_RESULT_21 = "extra_func_result_21"
    const val EXTRA_CODE_STRING_21 = "extra_code_string_21"
    const val EXTRA_CODE_VAR_EXPR_21 = "extra_code_var_expr_21"
    const val EXTRA_ACTIVE_MATCH_ID = "extra_active_match_id"
    const val EXTRA_BOOT_WITH_SOURCE_CODE = "extra_boot_with_source_code"
    const val EXTRA_SOURCE_ENTRYPOINT = "extra_source_entrypoint"

    /**
     * Opens the selected TCG Client's native system UI in a separate Android task / adjacent window
     * using a client-scoped Document URI (`nexustcg://client/{clientId}/{baseVariantId}`) so each
     * confirmed Client runs in its own distinct Android Window/App entry.
     *
     * For the locked Base Clients (**[[EDOPro]]** and **[[Forge]]**), also binds their immutable
     * upstream repository startup code metadata so the opened window boots through their code.
     */
    fun openClientSystemWindow(
        context: Context,
        client: ClientNode? = null,
        baseVariantId: String = client?.baseVariantId ?: "EDOPRO_BASE_MR5",
        activeMatchId: String? = null,
        openInAdjacentWindow: Boolean = true,
        openAsSeparateDocumentTask: Boolean = true
    ) {
        val profile = BuiltInTcgCatalog.systemUiProfileFor(baseVariantId, client)
        val spec = BuiltInTcgCatalog.findBaseSpec(baseVariantId)
        val baseBootSpec = BaseClientCodeRegistry.findForBaseVariant(baseVariantId)
        val resolvedClientId = client?.id ?: "standalone_${baseVariantId.lowercase()}"
        val intent = Intent(context, ClientSystemWindowActivity::class.java).apply {
            data = Uri.parse("nexustcg://client/$resolvedClientId/$baseVariantId")
            putExtra(EXTRA_CLIENT_ID, resolvedClientId)
            putExtra(EXTRA_CLIENT_NAME, client?.clientName ?: profile.clientDisplayName)
            putExtra(EXTRA_CLIENT_TYPE, client?.clientType ?: profile.systemTitle)
            putExtra(EXTRA_ENDPOINT_URI, client?.endpointUri ?: profile.endpointOrRepoUri)
            putExtra(EXTRA_BASE_VARIANT_ID, baseVariantId)
            putExtra(EXTRA_PROTOCOL_VERSION, client?.protocolVersion ?: spec.protocolVersion)
            putExtra(EXTRA_VAR_INPUT_21, client?.customVarInput21 ?: "123456789012345678901")
            putExtra(EXTRA_FUNC_INPUT_21, client?.customFuncInput21 ?: "876543210987654321098")
            putExtra(EXTRA_FUNC_NAME, client?.customFuncName ?: "ADD_21")
            putExtra(EXTRA_FUNC_RESULT_21, client?.customFuncResult21 ?: "999999999999999999999")
            putExtra(EXTRA_CODE_STRING_21, client?.customCodeString21 ?: BigInt21Engine.DEFAULT_CODE_STRING_21)
            putExtra(EXTRA_CODE_VAR_EXPR_21, client?.customCodeVarExpr21 ?: BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21)
            putExtra(EXTRA_BOOT_WITH_SOURCE_CODE, baseBootSpec != null)
            if (baseBootSpec != null) {
                putExtra(EXTRA_SOURCE_ENTRYPOINT, baseBootSpec.primaryEntrypointFile)
            }
            if (!activeMatchId.isNullOrBlank()) {
                putExtra(EXTRA_ACTIVE_MATCH_ID, activeMatchId)
            }

            if (openAsSeparateDocumentTask) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            if (openInAdjacentWindow) {
                addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    /**
     * Attempts to launch an external URI/app (`https://`, `http://`, `edopro://`, `forge://`) in a
     * separate OS task/window; if no external app handles it, opens `ClientSystemWindowActivity` in
     * code-driven boot mode instead.
     */
    fun openExternalOrSeparateWindow(
        context: Context,
        uriString: String,
        fallbackClient: ClientNode? = null,
        fallbackBaseVariantId: String = fallbackClient?.baseVariantId ?: "EDOPRO_BASE_MR5"
    ) {
        val trimmed = uriString.trim()
        val isWebOrDeepLink = trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("edopro://", ignoreCase = true) ||
            trimmed.startsWith("forge://", ignoreCase = true)
        if (isWebOrDeepLink) {
            try {
                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                }
                context.startActivity(viewIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // Fall back to the built-in separate ClientSystemWindowActivity
            } catch (_: Exception) {
                // Fall back to the built-in separate ClientSystemWindowActivity
            }
        }
        openClientSystemWindow(
            context = context,
            client = fallbackClient,
            baseVariantId = fallbackBaseVariantId,
            openInAdjacentWindow = true
        )
    }

    /**
     * Attempts to dispatch the locked Base Client's native scheme URI (`edopro://...` or
     * `forge://...`) or upstream entrypoint blob (`rawBootstrapCodeUrl`) via standard `ACTION_VIEW`.
     * Returns `true` if an external app/browser handled the URI, or `false` if handled in-window.
     */
    fun tryDispatchSchemeOrSourceUri(
        context: Context,
        targetUri: String
    ): Boolean {
        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUri.trim())).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }
            context.startActivity(viewIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Standalone multi-window Activity that hosts the selected TCG Client's native system UI
 * in a separate Android task/window (`android:resizeableActivity="true"`, `FLAG_ACTIVITY_NEW_DOCUMENT`).
 */
class ClientSystemWindowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialBaseVariantId = intent.getStringExtra(ClientWindowLauncher.EXTRA_BASE_VARIANT_ID)
            ?: "EDOPRO_BASE_MR5"
        val initialClientName = intent.getStringExtra(ClientWindowLauncher.EXTRA_CLIENT_NAME)
            ?: BuiltInTcgCatalog.systemUiProfileFor(initialBaseVariantId).clientDisplayName
        val initialClientType = intent.getStringExtra(ClientWindowLauncher.EXTRA_CLIENT_TYPE)
            ?: BuiltInTcgCatalog.systemUiProfileFor(initialBaseVariantId).systemTitle
        val initialEndpoint = intent.getStringExtra(ClientWindowLauncher.EXTRA_ENDPOINT_URI)
            ?: BuiltInTcgCatalog.systemUiProfileFor(initialBaseVariantId).endpointOrRepoUri
        val initialProtocolVer = intent.getStringExtra(ClientWindowLauncher.EXTRA_PROTOCOL_VERSION)
            ?: BuiltInTcgCatalog.findBaseSpec(initialBaseVariantId).protocolVersion
        val initialVar21 = intent.getStringExtra(ClientWindowLauncher.EXTRA_VAR_INPUT_21)
            ?: "123456789012345678901"
        val initialArg21 = intent.getStringExtra(ClientWindowLauncher.EXTRA_FUNC_INPUT_21)
            ?: "876543210987654321098"
        val initialFuncName = intent.getStringExtra(ClientWindowLauncher.EXTRA_FUNC_NAME)
            ?: "ADD_21"
        val initialCodeStr21 = intent.getStringExtra(ClientWindowLauncher.EXTRA_CODE_STRING_21)
            ?: BigInt21Engine.DEFAULT_CODE_STRING_21
        val initialCodeVarExpr21 = intent.getStringExtra(ClientWindowLauncher.EXTRA_CODE_VAR_EXPR_21)
            ?: BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
        val initialActiveMatchId = intent.getStringExtra(ClientWindowLauncher.EXTRA_ACTIVE_MATCH_ID)

        setContent {
            var activeBaseVariantId by rememberSaveable { mutableStateOf(initialBaseVariantId) }
            LaunchedEffect(activeBaseVariantId, initialClientName) {
                val profile = BuiltInTcgCatalog.systemUiProfileFor(activeBaseVariantId)
                val taskTitle = "${initialClientName} (${profile.hudBadgeLabel})"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setTaskDescription(
                        ActivityManager.TaskDescription.Builder()
                            .setLabel(taskTitle)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setTaskDescription(ActivityManager.TaskDescription(taskTitle))
                }
            }
            MyApplicationTheme(baseVariantId = activeBaseVariantId) {
                ClientSystemStandaloneScreen(
                    activeBaseVariantId = activeBaseVariantId,
                    initialClientName = initialClientName,
                    initialClientType = initialClientType,
                    initialEndpoint = initialEndpoint,
                    initialProtocolVersion = initialProtocolVer,
                    initialVar21 = initialVar21,
                    initialArg21 = initialArg21,
                    initialFuncName = initialFuncName,
                    initialCodeString21 = initialCodeStr21,
                    initialCodeVarExpr21 = initialCodeVarExpr21,
                    activeMatchId = initialActiveMatchId,
                    onSwitchSystem = { newBaseId ->
                        activeBaseVariantId = newBaseId
                    },
                    onCloseWindow = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClientSystemStandaloneScreen(
    activeBaseVariantId: String,
    initialClientName: String,
    initialClientType: String,
    initialEndpoint: String,
    initialProtocolVersion: String,
    initialVar21: String,
    initialArg21: String,
    initialFuncName: String,
    initialCodeString21: String,
    initialCodeVarExpr21: String,
    activeMatchId: String? = null,
    onSwitchSystem: (String) -> Unit,
    onCloseWindow: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val boardSyncService = remember(context) { GameBoardSyncService(context) }
    val gameStateManager = remember(context) { FirestoreGameStateManager(context) }
    val eventLogService = remember(context) { FirestoreGameEventLogService(context) }
    var currentSyncMatchId by rememberSaveable { mutableStateOf(activeMatchId.orEmpty()) }
    var localSyncSeat by rememberSaveable { mutableStateOf(SyncClientSeat.CLIENT_2_PEER) }
    var syncedBoardState by remember { mutableStateOf<SerializedGameBoardState?>(null) }
    var persistentMatchEvents by remember { mutableStateOf<List<MatchGameEvent>>(emptyList()) }
    var persistentEventsError by remember { mutableStateOf<String?>(null) }

    val baseSpec = BuiltInTcgCatalog.findBaseSpec(activeBaseVariantId)
    val baseBootSpec = remember(activeBaseVariantId) {
        BaseClientCodeRegistry.findForBaseVariant(activeBaseVariantId)
    }
    val uiProfile = BuiltInTcgCatalog.systemUiProfileFor(activeBaseVariantId)
    val accentColor = Color(uiProfile.accentHex)

    var realtimeSyncState by remember(activeBaseVariantId) {
        mutableStateOf(
            RealtimeGameSyncState(
                matchId = currentSyncMatchId,
                baseVariantId = activeBaseVariantId,
                baseVariantName = baseSpec.name,
                localSeat = localSyncSeat,
                localClientName = "$initialClientName (Window)",
                hostLife = baseSpec.startingLife,
                opponentLife = baseSpec.startingLife,
                connectionStatus = if (currentSyncMatchId.isBlank()) {
                    RealtimeSyncConnectionStatus.DISCONNECTED
                } else {
                    RealtimeSyncConnectionStatus.CONNECTING
                }
            )
        )
    }

    var bootRunNonce by rememberSaveable(activeBaseVariantId) { mutableIntStateOf(0) }
    var completedBootStages by rememberSaveable(activeBaseVariantId) { mutableIntStateOf(0) }
    var isBootingRepoCode by rememberSaveable(activeBaseVariantId) { mutableStateOf(baseBootSpec != null) }
    var bootElapsedMs by rememberSaveable(activeBaseVariantId) { mutableLongStateOf(0L) }

    var activePhaseCode by rememberSaveable(activeBaseVariantId) {
        mutableStateOf(uiProfile.phaseStripCodes.firstOrNull() ?: "MAIN")
    }
    var chainLinkDepth by rememberSaveable(activeBaseVariantId) { mutableIntStateOf(0) }
    var hostLife by rememberSaveable(activeBaseVariantId) { mutableLongStateOf(baseSpec.startingLife) }
    var opponentLife by rememberSaveable(activeBaseVariantId) { mutableLongStateOf(baseSpec.startingLife) }

    LaunchedEffect(currentSyncMatchId, localSyncSeat, initialClientName) {
        val trimmedRoomId = currentSyncMatchId.trim()
        if (trimmedRoomId.isNotBlank()) {
            gameStateManager.attachClientToMatch(
                matchId = trimmedRoomId,
                clientSeat = localSyncSeat,
                clientLabel = "$initialClientName (Window)"
            )
            gameStateManager.observeMatchSyncState(
                matchId = trimmedRoomId,
                localSeat = localSyncSeat,
                localClientName = "$initialClientName (Window)"
            ).catch { /* Ignore offline/unauthenticated snapshot errors in standalone preview */ }
                .collect { remoteSync ->
                    realtimeSyncState = remoteSync
                    if (remoteSync.isAttached) {
                        hostLife = remoteSync.hostLife
                        opponentLife = remoteSync.opponentLife
                        syncedBoardState = remoteSync.toSerializedGameBoardState()
                    }
                }
        }
    }

    LaunchedEffect(currentSyncMatchId) {
        val trimmedRoomId = currentSyncMatchId.trim()
        if (trimmedRoomId.isNotBlank()) {
            persistentEventsError = null
            eventLogService.observeMatchEvents(trimmedRoomId)
                .catch { err ->
                    persistentEventsError = err.message ?: "Failed to sync persistent event log"
                }
                .collect { remoteEvents ->
                    persistentMatchEvents = remoteEvents
                    persistentEventsError = null
                }
        } else {
            persistentMatchEvents = emptyList()
        }
    }

    var var21Input by rememberSaveable { mutableStateOf(initialVar21) }
    var arg21Input by rememberSaveable { mutableStateOf(initialArg21) }
    var func21Mode by rememberSaveable { mutableStateOf(initialFuncName) }
    var codeString21 by rememberSaveable { mutableStateOf(initialCodeString21) }
    var codeVarExpr21 by rememberSaveable { mutableStateOf(initialCodeVarExpr21) }
    val liveResult21 = remember(func21Mode, var21Input, arg21Input) {
        BigInt21Engine.evaluateFunction21(func21Mode, var21Input, arg21Input)
    }

    val handCards = remember(activeBaseVariantId) {
        BuiltInTcgCatalog.cardsForVariant(activeBaseVariantId)
    }
    val occupiedMainZones = remember(activeBaseVariantId) {
        mutableStateListOf<Pair<String, CardTemplate>>().apply {
            handCards.firstOrNull()?.let { firstCard ->
                add(uiProfile.fieldSlotCodes.first() to firstCard)
            }
        }
    }
    val occupiedSupportZones = remember(activeBaseVariantId) {
        mutableStateListOf<Pair<String, String>>().apply {
            add(uiProfile.spellOrSupportSlotCodes.first() to "Set: Protocol Counter / Conduit")
        }
    }
    val protocolConsoleLogs = remember(activeBaseVariantId) {
        mutableStateListOf(
            "[WINDOW_INIT] Launched ${uiProfile.windowModeTitle} in separate Android task/window.",
            "[HANDSHAKE] Endpoint=${initialEndpoint} | Protocol=${baseSpec.protocolVersion} | Hash=${baseSpec.protocolHash}",
            "[SYSTEM_UI] Loaded ${uiProfile.boardLayoutType} zones: ${uiProfile.fieldSlotCodes.joinToString(", ")}"
        )
    }

    // For locked Base Clients ([[EDOPro]] & [[Forge]]), load their upstream reference metadata immediately (zero fake boot animations)
    LaunchedEffect(activeBaseVariantId, bootRunNonce) {
        val spec = baseBootSpec
        if (spec == null) {
            completedBootStages = 0
            isBootingRepoCode = false
            bootElapsedMs = 0L
            return@LaunchedEffect
        }
        isBootingRepoCode = false
        completedBootStages = spec.stages.size
        bootElapsedMs = 0L
        protocolConsoleLogs.add(
            0,
            "[LOCKED_UPSTREAM_REF] ${spec.shortTitle} (${spec.exactGitHubRepoUrl}) • Entrypoint: ${spec.primaryEntrypointFile} (Read-only upstream reference; direct launch via external URI/repo)."
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onCloseWindow,
                        modifier = Modifier.testTag("close_client_window_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Return to NexusTCG Hub",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DesktopWindows,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = uiProfile.windowModeTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "SEPARATE APP WINDOW • ${uiProfile.hudBadgeLabel} • v$initialProtocolVersion",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = {
                            ClientWindowLauncher.openExternalOrSeparateWindow(
                                context = context,
                                uriString = baseSpec.sourceRepoUrl,
                                fallbackBaseVariantId = activeBaseVariantId
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("window_open_external_repo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open Source / External App",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Repo / App", style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("client_system_window_root"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. Locked Base Client Repository Code Boot Panel ([[EDOPro]] / [[Forge]])
            if (baseBootSpec != null) {
                item(key = "base_client_boot_${baseBootSpec.baseVariantId}") {
                    BaseClientCodeBootPanel(
                        bootSpec = baseBootSpec,
                        completedStageCount = completedBootStages,
                        isBooting = isBootingRepoCode,
                        bootElapsedMs = bootElapsedMs,
                        accentColor = accentColor,
                        onRerunRepoBoot = { bootRunNonce += 1 },
                        onLaunchSchemeOrExternalRepo = { uri ->
                            val dispatched = ClientWindowLauncher.tryDispatchSchemeOrSourceUri(
                                context = context,
                                targetUri = uri
                            )
                            if (dispatched) {
                                protocolConsoleLogs.add(
                                    0,
                                    "[URI_DISPATCH] Launched external handler for $uri"
                                )
                            } else {
                                bootRunNonce += 1
                                protocolConsoleLogs.add(
                                    0,
                                    "[URI_FALLBACK] No external handler for $uri; re-running ${baseBootSpec.primaryEntrypointFile} in window."
                                )
                            }
                        }
                    )
                }
            }

            // 1. Separate Window Status & System Switcher Banner
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        accentColor.copy(alpha = 0.18f),
                                        MaterialTheme.colorScheme.surfaceVariant
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = initialClientName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$initialClientType • Endpoint: $initialEndpoint",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = EmeraldReady.copy(alpha = 0.16f),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .border(1.dp, EmeraldReady, RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "MULTI-WINDOW ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldReady,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Integrated Systems: ${uiProfile.inspiredSystemsSummary} • DL Skill: ${uiProfile.duelLinksSkillName} • MD Mate: ${uiProfile.masterDuelMateName} • ${uiProfile.mtgoChessClockDefault}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Switch Active Client System UI Inside Window:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BuiltInTcgCatalog.baseVariants.forEach { spec ->
                                val selected = spec.baseVariantId == activeBaseVariantId
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSwitchSystem(spec.baseVariantId) },
                                    label = {
                                        Text(
                                            text = when (spec.baseVariantId) {
                                                "EDOPRO_BASE_MR5" -> "EDOPro Base (MR5)"
                                                "EDOPRO_CUSTOM_FORK" -> "EDOPro 21d Clone"
                                                "FORGE_MTG_BASE" -> "Card-Forge MTG"
                                                "FORGE_CUSTOM_CLONE" -> "Forge 21d Clone"
                                                "HYBRID_FORGE_EDOPRO_MIX" -> "Mixed Forge+EDOPro"
                                                "AETHER_MANA_V1" -> "AetherMana"
                                                "CYBER_OVERDRIVE_V2" -> "CyberOverdrive"
                                                else -> "ChronosRift"
                                            },
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("window_switch_system_${spec.baseVariantId}")
                                )
                            }
                        }
                    }
                }
            }

            // 1A-HP. Dynamic Animated Visual Health Bars synced from Firestore Game State
            item {
                AnimatedSyncedHealthBarCard(
                    syncState = realtimeSyncState,
                    fallbackHostLife = hostLife,
                    fallbackOpponentLife = opponentLife,
                    onQuickAdjustLife = { targetSeat, delta ->
                        val roomId = currentSyncMatchId.trim()
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.syncLifePointsDelta(
                                    matchId = roomId,
                                    targetSeat = targetSeat,
                                    delta = delta,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)"
                                ).onSuccess { updated ->
                                    realtimeSyncState = updated
                                    hostLife = updated.hostLife
                                    opponentLife = updated.opponentLife
                                    syncedBoardState = updated.toSerializedGameBoardState()
                                    val targetName = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
                                    val newLife = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) updated.hostLife else updated.opponentLife
                                    eventLogService.logLifePointChange(
                                        matchId = roomId,
                                        actorLabel = "$initialClientName (Window)",
                                        targetPlayerLabel = targetName,
                                        delta = delta,
                                        newLife = newLife,
                                        turnNumber = updated.turnNumber,
                                        activePhase = updated.activePhase
                                    )
                                }
                            }
                        } else {
                            if (targetSeat == SyncClientSeat.CLIENT_1_HOST) {
                                hostLife = (hostLife + delta).coerceAtLeast(0L)
                            } else {
                                opponentLife = (opponentLife + delta).coerceAtLeast(0L)
                            }
                            realtimeSyncState = realtimeSyncState.copy(hostLife = hostLife, opponentLife = opponentLife)
                        }
                    }
                )
            }

            // 1A-SYNC. Real-Time 2-Client Firestore Life Points & Board State Sync Console (Paired with Main Arena)
            item {
                RealtimeGameStateSyncPanel(
                    syncState = realtimeSyncState,
                    onSelectSeat = { seat ->
                        localSyncSeat = seat
                        protocolConsoleLogs.add(0, "[FIRESTORE_SEAT] Switched separate window to ${seat.displayLabel}")
                    },
                    onJoinMatchById = { roomId ->
                        currentSyncMatchId = roomId.trim()
                        protocolConsoleLogs.add(0, "[FIRESTORE_JOIN] Attaching window client to room '$roomId'")
                    },
                    onAdjustLifePoints = { targetSeat, delta ->
                        val roomId = currentSyncMatchId.trim()
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.syncLifePointsDelta(
                                    matchId = roomId,
                                    targetSeat = targetSeat,
                                    delta = delta,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)"
                                ).onSuccess { updated ->
                                    realtimeSyncState = updated
                                    hostLife = updated.hostLife
                                    opponentLife = updated.opponentLife
                                    syncedBoardState = updated.toSerializedGameBoardState()
                                    protocolConsoleLogs.add(0, "[LP_SYNC Rev #${updated.revisionNumber}] ${updated.lastEventSummary}")
                                    val targetName = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
                                    val newLife = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) updated.hostLife else updated.opponentLife
                                    eventLogService.logLifePointChange(
                                        matchId = roomId,
                                        actorLabel = "$initialClientName (Window)",
                                        targetPlayerLabel = targetName,
                                        delta = delta,
                                        newLife = newLife,
                                        turnNumber = updated.turnNumber,
                                        activePhase = updated.activePhase
                                    )
                                }
                            }
                        } else {
                            if (targetSeat == SyncClientSeat.CLIENT_1_HOST) {
                                hostLife = (hostLife + delta).coerceAtLeast(0L)
                            } else {
                                opponentLife = (opponentLife + delta).coerceAtLeast(0L)
                            }
                            realtimeSyncState = realtimeSyncState.copy(hostLife = hostLife, opponentLife = opponentLife)
                        }
                    },
                    onSetLifePointsExact = { targetSeat, exactLife ->
                        val roomId = currentSyncMatchId.trim()
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.syncLifePointsExact(
                                    matchId = roomId,
                                    targetSeat = targetSeat,
                                    exactLife = exactLife,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)"
                                ).onSuccess { updated ->
                                    realtimeSyncState = updated
                                    hostLife = updated.hostLife
                                    opponentLife = updated.opponentLife
                                    syncedBoardState = updated.toSerializedGameBoardState()
                                    protocolConsoleLogs.add(0, "[LP_SET Rev #${updated.revisionNumber}] ${updated.lastEventSummary}")
                                }
                            }
                        } else {
                            if (targetSeat == SyncClientSeat.CLIENT_1_HOST) hostLife = exactLife else opponentLife = exactLife
                            realtimeSyncState = realtimeSyncState.copy(hostLife = hostLife, opponentLife = opponentLife)
                        }
                    },
                    onDeploySyncedCardForActiveSeat = {
                        val roomId = currentSyncMatchId.trim()
                        val candidateZones = if (localSyncSeat == SyncClientSeat.CLIENT_1_HOST) {
                            listOf("P1_MAIN_0", "P1_MAIN_1", "P1_MAIN_2", "P1_MAIN_3", "P1_MAIN_4")
                        } else {
                            listOf("P2_MAIN_0", "P2_MAIN_1", "P2_MAIN_2", "P2_MAIN_3", "P2_MAIN_4")
                        }
                        val rev = realtimeSyncState.revisionNumber
                        val targetZone = candidateZones.firstOrNull { !realtimeSyncState.zonePlacements.containsKey(it) }
                            ?: candidateZones[rev % candidateZones.size]
                        val card = handCards[rev % handCards.size]
                        val placement = SerializedZonePlacement(
                            zoneId = targetZone,
                            owner = if (localSyncSeat == SyncClientSeat.CLIENT_1_HOST) "PLAYER_1_HOST" else "PLAYER_2_OPPONENT",
                            cardId = "${localSyncSeat.code.lowercase()}_${card.id}_$rev",
                            cardName = card.name,
                            cost = card.cost,
                            attack = card.attack,
                            defense = card.defense,
                            cardType = card.cardType,
                            abilityText = card.abilityText,
                            attack21 = card.attack21,
                            defense21 = card.defense21,
                            passcode = card.passcode
                        )
                        val summary = "${localSyncSeat.shortLabel} deployed '${card.name}' to [$targetZone]"
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.syncSingleZoneMutation(
                                    matchId = roomId,
                                    zoneId = targetZone,
                                    placement = placement,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)",
                                    eventType = "SYNC_ZONE_DEPLOY",
                                    eventSummary = summary
                                ).onSuccess { updated ->
                                    realtimeSyncState = updated
                                    syncedBoardState = updated.toSerializedGameBoardState()
                                    protocolConsoleLogs.add(0, "[ZONE_SYNC Rev #${updated.revisionNumber}] $summary")
                                    val seatPlayer = if (localSyncSeat == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
                                    eventLogService.logCardPlayed(
                                        matchId = roomId,
                                        playerLabel = seatPlayer,
                                        cardName = card.name,
                                        zoneLabel = targetZone,
                                        turnNumber = updated.turnNumber,
                                        activePhase = updated.activePhase,
                                        detail = "$initialClientName (Window) • ATK ${card.attack} / DEF ${card.defense}"
                                    )
                                }
                            }
                        }
                    },
                    onToggleZoneOrientation = { zoneId ->
                        val roomId = currentSyncMatchId.trim()
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.toggleZoneOrientationAtomic(
                                    matchId = roomId,
                                    zoneId = zoneId,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)"
                                ).onSuccess { updated ->
                                    realtimeSyncState = updated
                                    syncedBoardState = updated.toSerializedGameBoardState()
                                    protocolConsoleLogs.add(0, "[ZONE_ROTATE Rev #${updated.revisionNumber}] ${updated.lastEventSummary}")
                                }
                            }
                        }
                    },
                    onRemoveZonePlacement = { zoneId ->
                        val roomId = currentSyncMatchId.trim()
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.syncSingleZoneMutation(
                                    matchId = roomId,
                                    zoneId = zoneId,
                                    placement = null,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)",
                                    eventType = "SYNC_ZONE_REMOVE",
                                    eventSummary = "Cleared zone [$zoneId]"
                                ).onSuccess { updated ->
                                    realtimeSyncState = updated
                                    syncedBoardState = updated.toSerializedGameBoardState()
                                    protocolConsoleLogs.add(0, "[ZONE_CLEAR Rev #${updated.revisionNumber}] Cleared [$zoneId]")
                                }
                            }
                        }
                    },
                    onResetSyncedState = {
                        val roomId = currentSyncMatchId.trim()
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.resetLifeAndBoardAtomic(
                                    matchId = roomId,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)"
                                ).onSuccess { updated ->
                                    realtimeSyncState = updated
                                    hostLife = updated.hostLife
                                    opponentLife = updated.opponentLife
                                    syncedBoardState = updated.toSerializedGameBoardState()
                                    protocolConsoleLogs.add(0, "[SYNC_RESET Rev #${updated.revisionNumber}] ${updated.lastEventSummary}")
                                    coroutineScope.launch {
                                        eventLogService.appendGameEvent(
                                            matchId = roomId,
                                            actorLabel = "$initialClientName (Window)",
                                            eventType = PersistentGameEventType.SYSTEM_SYNC,
                                            summary = "Reset synced life points and cleared board",
                                            detail = "Restored LP: ${updated.hostLife}/${updated.opponentLife}",
                                            turnNumber = updated.turnNumber,
                                            activePhase = updated.activePhase
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }

            // 1A-LOG. Persistent Scrollable Firestore Game Event Log (/matches/{matchId}/events)
            item {
                val quickCardName = handCards.firstOrNull()?.name ?: "Ace Construct"
                val quickLpStep = if (baseSpec.startingLife >= 1000L) 500L else 5L
                val windowPlayerName = if (localSyncSeat == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
                PersistentGameEventLogCard(
                    matchId = currentSyncMatchId.trim(),
                    events = persistentMatchEvents,
                    isFirestoreConnected = realtimeSyncState.isAttached,
                    errorMessage = persistentEventsError,
                    sampleCardName = quickCardName,
                    defaultLifeDeltaStep = quickLpStep,
                    onLogQuickCardPlay = { cardName ->
                        val roomId = currentSyncMatchId.trim()
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                eventLogService.logCardPlayed(
                                    matchId = roomId,
                                    playerLabel = windowPlayerName,
                                    cardName = cardName,
                                    turnNumber = realtimeSyncState.turnNumber,
                                    activePhase = activePhaseCode,
                                    detail = "$initialClientName (Window)"
                                )
                            }
                        }
                    },
                    onLogQuickLifeReduction = { reductionAmount ->
                        val roomId = currentSyncMatchId.trim()
                        val targetSeat = if (localSyncSeat == SyncClientSeat.CLIENT_1_HOST) {
                            SyncClientSeat.CLIENT_2_PEER
                        } else {
                            SyncClientSeat.CLIENT_1_HOST
                        }
                        val targetName = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.syncLifePointsDelta(
                                    matchId = roomId,
                                    targetSeat = targetSeat,
                                    delta = -reductionAmount,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)"
                                ).onSuccess { updated ->
                                    realtimeSyncState = updated
                                    hostLife = updated.hostLife
                                    opponentLife = updated.opponentLife
                                    syncedBoardState = updated.toSerializedGameBoardState()
                                    val newLife = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) updated.hostLife else updated.opponentLife
                                    eventLogService.logLifePointChange(
                                        matchId = roomId,
                                        actorLabel = "$initialClientName (Window)",
                                        targetPlayerLabel = targetName,
                                        delta = -reductionAmount,
                                        newLife = newLife,
                                        turnNumber = updated.turnNumber,
                                        activePhase = updated.activePhase
                                    )
                                }
                            }
                        }
                    },
                    onLogCustomEvent = { summary ->
                        val roomId = currentSyncMatchId.trim()
                        if (roomId.isNotBlank()) {
                            coroutineScope.launch {
                                eventLogService.appendGameEvent(
                                    matchId = roomId,
                                    actorLabel = "$windowPlayerName ($initialClientName)",
                                    eventType = PersistentGameEventType.CUSTOM_EVENT,
                                    summary = summary,
                                    detail = "Separate Client Window",
                                    turnNumber = realtimeSyncState.turnNumber,
                                    activePhase = activePhaseCode
                                )
                            }
                        }
                    }
                )
            }

            // 1B. Responsive 2-Player Canvas GameBoard (Touch Drag-and-Drop Card Movement + Real-Time Firestore Sync)
            item {
                CanvasGameBoard(
                    uiProfile = uiProfile,
                    hostName = "Host (${uiProfile.clientDisplayName})",
                    opponentName = "Peer Client",
                    hostLife = hostLife,
                    opponentLife = opponentLife,
                    hostUnits = occupiedMainZones.map { it.second },
                    opponentUnits = handCards.takeLast(1),
                    handCards = handCards,
                    turnNumber = realtimeSyncState.turnNumber,
                    activePhase = activePhaseCode,
                    syncedBoardState = syncedBoardState,
                    onDeployCardFromHand = { card, targetZone ->
                        occupiedMainZones.removeAll { it.first == targetZone }
                        occupiedMainZones.add(targetZone to card)
                        protocolConsoleLogs.add(
                            0,
                            "[MOVE_HISTORY] Player 1 moved '${card.name}' (${card.attack21}/${card.defense21}) to Field [$targetZone]"
                        )
                    },
                    onMoveCardBetweenZones = { card, fromZone, toZone ->
                        protocolConsoleLogs.add(
                            0,
                            "[MOVE_HISTORY] Player 1 moved '${card.name}' from [$fromZone] to Field [$toZone]"
                        )
                    },
                    onAttackOpponentZone = { attacker, targetZone, defender ->
                        val dmg = if (BuiltInTcgCatalog.isEdoProVariant(activeBaseVariantId)) 1500L else 4L
                        val targetSeat = if (localSyncSeat == SyncClientSeat.CLIENT_1_HOST) {
                            SyncClientSeat.CLIENT_2_PEER
                        } else {
                            SyncClientSeat.CLIENT_1_HOST
                        }
                        if (currentSyncMatchId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.syncLifePointsDelta(
                                    matchId = currentSyncMatchId.trim(),
                                    targetSeat = targetSeat,
                                    delta = -dmg,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)"
                                ).onSuccess { updated ->
                                    realtimeSyncState = updated
                                    hostLife = updated.hostLife
                                    opponentLife = updated.opponentLife
                                    syncedBoardState = updated.toSerializedGameBoardState()
                                }
                            }
                        } else {
                            opponentLife = (opponentLife - dmg).coerceAtLeast(0L)
                        }
                        protocolConsoleLogs.add(
                            0,
                            "[MSG_DAMAGE] '${attacker.name}' attacked [$targetZone] (${defender?.name ?: "Direct"}) for -$dmg ${uiProfile.lifeLabel}!"
                        )
                    },
                    onDrawCardRequest = {
                        protocolConsoleLogs.add(
                            0,
                            "[MSG_DRAW] Drew card from P1 Deck Stack on ${uiProfile.clientDisplayName}"
                        )
                    },
                    onBroadcastBoardState = { placements, eventType, eventSummary ->
                        val nextRev = (syncedBoardState?.revisionNumber ?: 1) + 1
                        if (currentSyncMatchId.isNotBlank()) {
                            coroutineScope.launch {
                                gameStateManager.syncBoardStateAtomic(
                                    matchId = currentSyncMatchId.trim(),
                                    zonePlacements = placements,
                                    actorSeat = localSyncSeat,
                                    actorLabel = "$initialClientName (Window)",
                                    eventType = eventType,
                                    eventSummary = eventSummary,
                                    updatedPhase = activePhaseCode,
                                    updatedTurnNumber = realtimeSyncState.turnNumber
                                ).onSuccess { synced ->
                                    realtimeSyncState = synced
                                    syncedBoardState = synced.toSerializedGameBoardState()
                                }
                            }
                        } else {
                            syncedBoardState = SerializedGameBoardState(
                                matchId = "window_${activeBaseVariantId.lowercase()}",
                                baseVariantId = activeBaseVariantId,
                                baseVariantName = baseSpec.name,
                                revisionNumber = nextRev,
                                turnNumber = 1L,
                                activePhase = activePhaseCode,
                                hostLife = hostLife,
                                opponentLife = opponentLife,
                                connectedClients = listOf("$initialClientName (Window)", "Peer Client #2"),
                                lastBroadcastClientName = "$initialClientName (Window)",
                                lastEventType = eventType,
                                lastEventSummary = eventSummary,
                                zonePlacements = placements
                            )
                        }
                        protocolConsoleLogs.add(
                            0,
                            "[FIRESTORE_SYNC Rev #$nextRev] Broadcasted '$eventType': $eventSummary"
                        )
                    },
                    onTriggerPeerClientSyncMove = { placements ->
                        val currentRev = syncedBoardState?.revisionNumber ?: 1
                        val matchStub = MatchSession(
                            id = activeMatchId ?: "window_${activeBaseVariantId.lowercase()}",
                            hostName = initialClientName,
                            baseVariantId = activeBaseVariantId,
                            baseVariantName = baseSpec.name,
                            connectedClients = listOf("$initialClientName (Window)", "Peer Client #2 ($activeBaseVariantId)"),
                            turnNumber = 1L,
                            activePhase = activePhaseCode,
                            hostLife = hostLife,
                            opponentLife = opponentLife
                        )
                        if (!activeMatchId.isNullOrBlank()) {
                            coroutineScope.launch {
                                boardSyncService.broadcastPeerClientCounterMove(
                                    match = matchStub,
                                    currentPlacements = placements,
                                    currentRevision = currentRev
                                ).onSuccess { synced ->
                                    syncedBoardState = synced
                                    protocolConsoleLogs.add(
                                        0,
                                        "[PEER_SYNC Rev #${synced.revisionNumber}] ${synced.lastEventSummary}"
                                    )
                                }
                            }
                        } else {
                            val catalog = BuiltInTcgCatalog.cardsForVariant(activeBaseVariantId)
                            val peerCard = catalog[(currentRev + 1) % catalog.size]
                            val targetZone = listOf("P2_MAIN_0", "P2_MAIN_1", "P2_MAIN_2", "P2_MAIN_3", "P2_MAIN_4")
                                .firstOrNull { !placements.containsKey(it) } ?: "P2_MAIN_1"
                            val updated = placements.toMutableMap()
                            updated[targetZone] = com.example.data.SerializedZonePlacement(
                                zoneId = targetZone,
                                owner = "PLAYER_2_OPPONENT",
                                cardId = peerCard.id,
                                cardName = peerCard.name,
                                cost = peerCard.cost,
                                attack = peerCard.attack,
                                defense = peerCard.defense,
                                cardType = peerCard.cardType,
                                abilityText = peerCard.abilityText,
                                attack21 = peerCard.attack21,
                                defense21 = peerCard.defense21,
                                passcode = peerCard.passcode
                            )
                            val summary = "Peer Client #2 deployed '${peerCard.name}' to [$targetZone]"
                            syncedBoardState = SerializedGameBoardState(
                                matchId = matchStub.id,
                                baseVariantId = activeBaseVariantId,
                                baseVariantName = baseSpec.name,
                                revisionNumber = currentRev + 1,
                                connectedClients = matchStub.connectedClients,
                                lastBroadcastClientName = "Peer Client #2",
                                lastEventType = "PEER_CARD_DEPLOY",
                                lastEventSummary = summary,
                                zonePlacements = updated
                            )
                            protocolConsoleLogs.add(0, "[PEER_SYNC Rev #${currentRev + 1}] $summary")
                        }
                    }
                )
            }

            // 1C. D3.js Based Board State & Card Stack Visualizer inside Separate Client Window
            item {
                D3BoardStateVisualizer(
                    uiProfile = uiProfile,
                    hostUnits = occupiedMainZones.map { it.second },
                    opponentUnits = handCards.takeLast(1),
                    handCards = handCards,
                    turnNumber = 1L,
                    activePhase = activePhaseCode
                )
            }

            // 2. Client System Duel HUD (LP / Integrity / Sanctum Life + Phase Strip + Chain Link)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "HOST (${uiProfile.lifeLabel})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (uiProfile.supports21DigitEngine) {
                                        BigInt21Engine.formatWithCommas(liveResult21)
                                    } else {
                                        "$hostLife ${uiProfile.lifeLabel}"
                                    },
                                    style = if (uiProfile.supports21DigitEngine) {
                                        MaterialTheme.typography.titleSmall
                                    } else {
                                        MaterialTheme.typography.headlineSmall
                                    },
                                    color = EmeraldReady,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = uiProfile.boardLayoutType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (chainLinkDepth > 0) "CHAIN LINK $chainLinkDepth" else "OPEN GAME STATE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (chainLinkDepth > 0) RadiantGold else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "PEER (${uiProfile.lifeLabel})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$opponentLife ${uiProfile.lifeLabel}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = CrimsonAlert,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Client-System Native Phase Strip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiProfile.phaseStripCodes.forEach { phaseCode ->
                                val isCurrent = phaseCode == activePhaseCode
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCurrent) accentColor else ObsidianElevated
                                        )
                                        .clickable {
                                            activePhaseCode = phaseCode
                                            protocolConsoleLogs.add(
                                                0,
                                                "[MSG_NEW_PHASE] Client switched phase -> $phaseCode on ${uiProfile.clientDisplayName}"
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("window_phase_$phaseCode")
                                ) {
                                    Text(
                                        text = phaseCode,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isCurrent) Color.Black else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Chain Link & Combat Action Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    chainLinkDepth = (chainLinkDepth + 1).coerceAtMost(9)
                                    protocolConsoleLogs.add(
                                        0,
                                        "[MSG_CHAINING] Declared Chain Link $chainLinkDepth in ${uiProfile.systemTitle}"
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("window_add_chain_link_button")
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Chain +1 (CL${chainLinkDepth + 1})", style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    val dmg = if (BuiltInTcgCatalog.isEdoProVariant(activeBaseVariantId)) 1500L else 4L
                                    opponentLife = (opponentLife - dmg).coerceAtLeast(0L)
                                    val resolvedLinks = chainLinkDepth
                                    chainLinkDepth = 0
                                    protocolConsoleLogs.add(
                                        0,
                                        "[MSG_DAMAGE] Executed ${uiProfile.combatVerb} (-$dmg ${uiProfile.lifeLabel}, resolved $resolvedLinks chain links)"
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("window_execute_combat_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(uiProfile.combatVerb, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 3. Client System Specific Zone Matrix (MMZ 1..5 / EXP 1..6 / Sanctum / Shield Matrix / Continuum)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = uiProfile.fieldSlotsBottomLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )

                        // Primary Monster / Unit Zones
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            uiProfile.fieldSlotCodes.forEach { slotCode ->
                                val occupied = occupiedMainZones.firstOrNull { it.first == slotCode }?.second
                                Box(
                                    modifier = Modifier
                                        .width(104.dp)
                                        .height(86.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (occupied != null) {
                                                accentColor.copy(alpha = 0.16f)
                                            } else {
                                                ObsidianElevated
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (occupied != null) accentColor else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            if (occupied != null) {
                                                protocolConsoleLogs.add(
                                                    0,
                                                    "[ZONE_ACTIVATE] Triggered effect of ${occupied.name} in [$slotCode] (Code: c${occupied.passcode})"
                                                )
                                            } else {
                                                val cardToPlace = handCards.random()
                                                occupiedMainZones.add(slotCode to cardToPlace)
                                                protocolConsoleLogs.add(
                                                    0,
                                                    "[MSG_SUMMONING] ${uiProfile.deployVerb}: ${cardToPlace.name} -> Zone [$slotCode]"
                                                )
                                            }
                                        }
                                        .padding(8.dp)
                                        .testTag("window_zone_$slotCode")
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = slotCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (occupied != null) {
                                            Text(
                                                text = occupied.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${occupied.attack}/${occupied.defense}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = RadiantGold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        } else {
                                            Text(
                                                text = "Tap to ${uiProfile.deployVerb}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        // Spell/Trap / Mana / Reactor / Flux Zones
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            uiProfile.spellOrSupportSlotCodes.forEach { supportSlot ->
                                val occupiedText = occupiedSupportZones.firstOrNull { it.first == supportSlot }?.second
                                Box(
                                    modifier = Modifier
                                        .width(104.dp)
                                        .height(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (occupiedText != null) {
                                                RadiantGold.copy(alpha = 0.14f)
                                            } else {
                                                ObsidianElevated.copy(alpha = 0.7f)
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (occupiedText != null) RadiantGold else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (occupiedText != null) {
                                                occupiedSupportZones.removeAll { it.first == supportSlot }
                                                chainLinkDepth = (chainLinkDepth + 1).coerceAtMost(9)
                                                protocolConsoleLogs.add(
                                                    0,
                                                    "[MSG_CHAINING] Activated $supportSlot ($occupiedText) as Chain Link $chainLinkDepth!"
                                                )
                                            } else {
                                                occupiedSupportZones.add(
                                                    supportSlot to "Set: ${baseSpec.resourceName} Card"
                                                )
                                                protocolConsoleLogs.add(
                                                    0,
                                                    "[MSG_SET] Set card in support zone [$supportSlot]"
                                                )
                                            }
                                        }
                                        .padding(6.dp)
                                        .testTag("window_support_zone_$supportSlot")
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = supportSlot,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = RadiantGold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = occupiedText ?: "Empty Slot",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Hand Cards for the Active Client System
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${uiProfile.clientDisplayName} Hand — Tap to ${uiProfile.deployVerb}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(handCards, key = { it.id }) { card ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier
                                    .width(175.dp)
                                    .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        val openSlot = uiProfile.fieldSlotCodes.firstOrNull { code ->
                                            occupiedMainZones.none { it.first == code }
                                        } ?: uiProfile.fieldSlotCodes.first()
                                        occupiedMainZones.removeAll { it.first == openSlot }
                                        occupiedMainZones.add(openSlot to card)
                                        protocolConsoleLogs.add(
                                            0,
                                            "[MSG_SUMMONING] ${uiProfile.deployVerb} '${card.name}' (c${card.passcode}) -> [$openSlot]"
                                        )
                                    }
                                    .testTag("window_hand_card_${card.id}")
                            ) {
                                Column {
                                    Image(
                                        painter = painterResource(id = card.artResId),
                                        contentDescription = card.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(78.dp)
                                    )
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = card.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "c${card.passcode} • ATK ${card.attack21}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accentColor,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. 21-Digit Variable, Function & Code-String Sandbox (Shown when EDOPro 21-Digit Custom Clone is active)
            if (uiProfile.supports21DigitEngine) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, RadiantGold.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null,
                                    tint = RadiantGold
                                )
                                Text(
                                    text = when (activeBaseVariantId) {
                                        "HYBRID_FORGE_EDOPRO_MIX" ->
                                            "Mixed Forge + EDOPro 21-Digit Registers & Extensible Modules"
                                        "FORGE_CUSTOM_CLONE" ->
                                            "Card-Forge 21-Digit Custom SVar Runtime Registers"
                                        else ->
                                            "21-Digit EDOPro Custom Clone Runtime Registers"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = RadiantGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (BuiltInTcgCatalog.isHybridMixedVariant(activeBaseVariantId)) {
                                val loadedMods = BigInt21Engine.extractMixedClientModules(codeVarExpr21)
                                Text(
                                    text = "Loaded Mixed Client Modules (${loadedMods.size}): ${loadedMods.joinToString(" + ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE040FB),
                                    fontFamily = FontFamily.Monospace
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    BigInt21Engine.presetMixExtensions.forEach { presetMod ->
                                        FilterChip(
                                            selected = loadedMods.contains(presetMod),
                                            onClick = {
                                                codeVarExpr21 = BigInt21Engine.appendMixedClientModule(
                                                    currentExpr21 = codeVarExpr21,
                                                    newModuleRaw = presetMod,
                                                    codeString21 = codeString21
                                                )
                                                protocolConsoleLogs.add(
                                                    0,
                                                    "[HYBRID_MIX_EXTEND] Added module '$presetMod' to Mixed Client -> $codeVarExpr21"
                                                )
                                            },
                                            label = { Text("+ $presetMod", style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = codeString21,
                                onValueChange = { codeString21 = BigInt21Engine.filterDigitsInput(it) },
                                label = { Text("21-Digit Code String (c########.lua) — ${codeString21.length}/21 digits") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("window_code_string_21_input")
                            )

                            OutlinedTextField(
                                value = codeVarExpr21,
                                onValueChange = { codeVarExpr21 = BigInt21Engine.filterCodeStringWith21DigitLimit(it) },
                                label = { Text("Coding Variables for Code Strings (Max 21 Numerical Digits)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("window_code_var_expr_21_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = var21Input,
                                    onValueChange = { var21Input = BigInt21Engine.filterDigitsInput(it) },
                                    label = { Text("Var A (${var21Input.length}/21d)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = arg21Input,
                                    onValueChange = { arg21Input = BigInt21Engine.filterDigitsInput(it) },
                                    label = { Text("Arg B (${arg21Input.length}/21d)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BigInt21Engine.supportedFunctions.forEach { fn ->
                                    FilterChip(
                                        selected = func21Mode == fn,
                                        onClick = {
                                            func21Mode = fn
                                            val computed = BigInt21Engine.evaluateFunction21(fn, var21Input, arg21Input)
                                            protocolConsoleLogs.add(
                                                0,
                                                "[OCGCORE_BIGINT21] c$codeString21.lua executed $fn -> $computed (${computed.length}/21 digits)"
                                            )
                                        },
                                        label = { Text(fn, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianElevated, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "21-Digit Register Output ($func21Mode • ${liveResult21.length}/21 digits):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = BigInt21Engine.formatWithCommas(liveResult21),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldReady,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Scrollable Real-Time Gameplay Action Sidebar Log & Packet Console (Synced via Firebase)
            item {
                GameplayActionSidebarLog(
                    uiProfile = uiProfile,
                    rawActionLogs = protocolConsoleLogs.asReversed(),
                    turnNumber = 1L,
                    activePhase = activePhaseCode,
                    isDockedSidebar = false,
                    syncedBoardState = syncedBoardState,
                    onSelectPhaseFromSidebar = { phaseCode ->
                        activePhaseCode = phaseCode
                        protocolConsoleLogs.add(
                            0,
                            "[MSG_NEW_PHASE] Client switched phase -> $phaseCode on ${uiProfile.clientDisplayName}"
                        )
                    },
                    onRecordSyncedMoveFromSidebar = { moveLine ->
                        protocolConsoleLogs.add(0, moveLine)
                        if (!activeMatchId.isNullOrBlank()) {
                            val nextRev = (syncedBoardState?.revisionNumber ?: 1) + 1
                            val matchStub = MatchSession(
                                id = activeMatchId,
                                hostName = initialClientName,
                                baseVariantId = activeBaseVariantId,
                                baseVariantName = baseSpec.name,
                                connectedClients = syncedBoardState?.connectedClients
                                    ?: listOf(initialClientName, "Connected Peer Window"),
                                turnNumber = 1L,
                                activePhase = activePhaseCode,
                                hostLife = hostLife,
                                opponentLife = opponentLife
                            )
                            coroutineScope.launch {
                                boardSyncService.broadcastBoardState(
                                    match = matchStub,
                                    zonePlacements = syncedBoardState?.zonePlacements.orEmpty(),
                                    clientLabel = "$initialClientName (Window)",
                                    eventType = "MOVE_HISTORY",
                                    eventSummary = moveLine,
                                    nextRevision = nextRev,
                                    updatedPhase = activePhaseCode,
                                    updatedHostLife = hostLife,
                                    updatedOpponentLife = opponentLife
                                ).onSuccess { synced ->
                                    syncedBoardState = synced
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}
