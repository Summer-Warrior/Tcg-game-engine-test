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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BaseClientCodeRegistry
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardTemplate
import com.example.data.ClientNode
import com.example.data.FirestoreGameStateManager
import com.example.data.SerializedGameBoardState
import com.example.data.SyncClientSeat
import com.example.data.TurnPhase
import com.example.ui.components.AttackImpactVfxEvent
import com.example.ui.components.BaseClientCodeBootPanel
import com.example.ui.components.BossSummonCutInEvent
import com.example.ui.components.CanvasGameBoard
import com.example.ui.components.DuelDiskLpCounterBar
import com.example.ui.components.DuelVfxOverlay
import com.example.ui.components.IlluminatedDuelDiskPhaseStrip
import com.example.ui.components.PhaseSweepBannerEvent
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DuelArenaAtmosphere
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.HolographicArenaFloorCanvas
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.duelDiskPlate
import com.example.ui.theme.rememberChamferedDuelShape
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
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
 * Standalone multi-window Activity hosting the unified Solid Vision Battle City tabletop
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
        val initialActiveMatchId = intent.getStringExtra(ClientWindowLauncher.EXTRA_ACTIVE_MATCH_ID)

        setContent {
            var activeBaseVariantId by rememberSaveable { mutableStateOf(initialBaseVariantId) }
            LaunchedEffect(activeBaseVariantId, initialClientName) {
                val profile = BuiltInTcgCatalog.systemUiProfileFor(activeBaseVariantId)
                val taskTitle = "$initialClientName (${profile.hudBadgeLabel})"
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
                    activeMatchId = initialActiveMatchId,
                    onSwitchSystem = { activeBaseVariantId = it },
                    onCloseWindow = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSystemStandaloneScreen(
    activeBaseVariantId: String,
    initialClientName: String,
    initialClientType: String,
    initialEndpoint: String,
    initialProtocolVersion: String,
    activeMatchId: String? = null,
    onSwitchSystem: (String) -> Unit,
    onCloseWindow: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isSignedIn = remember { Firebase.auth.currentUser != null }
    val gameStateManager = remember(context, isSignedIn) {
        if (isSignedIn) FirestoreGameStateManager(context) else null
    }

    val baseSpec = BuiltInTcgCatalog.findBaseSpec(activeBaseVariantId)
    val baseBootSpec = remember(activeBaseVariantId) {
        BaseClientCodeRegistry.findForBaseVariant(activeBaseVariantId)
    }
    val uiProfile = BuiltInTcgCatalog.systemUiProfileFor(activeBaseVariantId)
    val accentColor = Color(uiProfile.accentHex)

    var showUpstreamBootPanel by rememberSaveable(activeBaseVariantId) { mutableStateOf(false) }
    var turnNumber by rememberSaveable(activeBaseVariantId) { mutableLongStateOf(1L) }
    var currentPhase by rememberSaveable(activeBaseVariantId) { mutableStateOf(TurnPhase.MAIN) }
    var hostLife by rememberSaveable(activeBaseVariantId) { mutableLongStateOf(baseSpec.startingLife) }
    var opponentLife by rememberSaveable(activeBaseVariantId) { mutableLongStateOf(baseSpec.startingLife) }
    var syncedBoardState by remember(activeBaseVariantId) { mutableStateOf<SerializedGameBoardState?>(null) }

    var bossCutIn by remember { mutableStateOf<BossSummonCutInEvent?>(null) }
    var phaseBanner by remember { mutableStateOf<PhaseSweepBannerEvent?>(null) }
    var attackImpact by remember { mutableStateOf<AttackImpactVfxEvent?>(null) }

    val handCards = remember(activeBaseVariantId) {
        mutableStateListOf<CardTemplate>().apply {
            addAll(BuiltInTcgCatalog.cardsForVariant(activeBaseVariantId))
        }
    }
    val hostUnits = remember(activeBaseVariantId) {
        mutableStateListOf<CardTemplate>().apply {
            BuiltInTcgCatalog.cardsForVariant(activeBaseVariantId).firstOrNull()?.let { add(it) }
        }
    }
    val opponentUnits = remember(activeBaseVariantId) {
        mutableStateListOf<CardTemplate>().apply {
            BuiltInTcgCatalog.cardsForVariant(activeBaseVariantId).getOrNull(1)?.let { add(it) }
        }
    }

    // Optional live Firestore sync ONLY when authenticated and paired with a real match ID
    LaunchedEffect(activeMatchId, isSignedIn, activeBaseVariantId) {
        val roomId = activeMatchId?.trim().orEmpty()
        val manager = gameStateManager
        if (isSignedIn && manager != null && roomId.isNotBlank() && !roomId.startsWith("local_")) {
            manager.attachClientToMatch(
                matchId = roomId,
                clientSeat = SyncClientSeat.CLIENT_2_PEER,
                clientLabel = "$initialClientName (Window)"
            )
            manager.observeMatchSyncState(
                matchId = roomId,
                localSeat = SyncClientSeat.CLIENT_2_PEER,
                localClientName = "$initialClientName (Window)"
            ).catch { /* Ignore offline/unauthenticated snapshot errors */ }
                .collect { remoteSync ->
                    if (remoteSync.isAttached) {
                        hostLife = remoteSync.hostLife
                        opponentLife = remoteSync.opponentLife
                        turnNumber = remoteSync.turnNumber
                        syncedBoardState = remoteSync.toSerializedGameBoardState()
                    }
                }
        }
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
                            text = "SOLID VISION WINDOW • ${uiProfile.hudBadgeLabel} • v$initialProtocolVersion",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (baseBootSpec != null) {
                        IconButton(
                            onClick = { showUpstreamBootPanel = !showUpstreamBootPanel },
                            modifier = Modifier.testTag("window_toggle_boot_spec_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Toggle Upstream Source Spec",
                                tint = if (showUpstreamBootPanel) RadiantGold else accentColor
                            )
                        }
                    }
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
                        Text("Repo", style = MaterialTheme.typography.labelSmall)
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
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Compact System Switcher Strip
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BuiltInTcgCatalog.baseVariants.forEach { spec ->
                        FilterChip(
                            selected = spec.baseVariantId == activeBaseVariantId,
                            onClick = { onSwitchSystem(spec.baseVariantId) },
                            label = {
                                Text(
                                    text = when (spec.baseVariantId) {
                                        "EDOPRO_BASE_MR5" -> "EDOPro MR5"
                                        "EDOPRO_CUSTOM_FORK" -> "EDOPro 21d"
                                        "FORGE_MTG_BASE" -> "Forge MTG"
                                        "FORGE_CUSTOM_CLONE" -> "Forge 21d"
                                        "HYBRID_FORGE_EDOPRO_MIX" -> "Hybrid Mix"
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

            // Optional collapsible upstream source reference for locked Base Clients
            if (baseBootSpec != null && showUpstreamBootPanel) {
                item(key = "base_boot_${baseBootSpec.baseVariantId}") {
                    AnimatedVisibility(visible = showUpstreamBootPanel) {
                        BaseClientCodeBootPanel(
                            bootSpec = baseBootSpec,
                            completedStageCount = baseBootSpec.stages.size,
                            isBooting = false,
                            bootElapsedMs = 0L,
                            accentColor = accentColor,
                            onRerunRepoBoot = {},
                            onLaunchSchemeOrExternalRepo = { uri ->
                                ClientWindowLauncher.tryDispatchSchemeOrSourceUri(context, uri)
                            }
                        )
                    }
                }
            }

            // 2. Unified Solid Vision Duel Disk LP Counter Bar
            item {
                DuelDiskLpCounterBar(
                    hostName = initialClientName,
                    opponentName = "Opponent (${uiProfile.hudBadgeLabel})",
                    hostLife = hostLife,
                    opponentLife = opponentLife,
                    startingLife = baseSpec.startingLife,
                    turnNumber = turnNumber,
                    activePhaseCode = currentPhase.code,
                    accentColor = accentColor
                )
            }

            // 3. Illuminated Duel Disk Phase Strip (DP -> SP -> MP1 -> BP -> MP2 -> EP)
            item {
                IlluminatedDuelDiskPhaseStrip(
                    activePhase = currentPhase,
                    accentColor = accentColor,
                    onSelectPhase = { nextPhase ->
                        currentPhase = nextPhase
                        phaseBanner = PhaseSweepBannerEvent(
                            phaseTitle = nextPhase.displayName,
                            phaseCode = nextPhase.code,
                            turnNumber = turnNumber
                        )
                    }
                )
            }

            // 4. Unified Solid Vision Holographic Playmat + CanvasGameBoard + DuelVfxOverlay
            item {
                val playmatShape = rememberChamferedDuelShape(10.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(playmatShape)
                        .border(1.5.dp, accentColor.copy(alpha = 0.7f), playmatShape)
                        .testTag("window_solid_vision_playmat")
                ) {
                    HolographicArenaFloorCanvas(
                        atmosphere = DuelArenaAtmosphere.BATTLE_CITY_STADIUM,
                        modifier = Modifier.matchParentSize()
                    )

                    CanvasGameBoard(
                        uiProfile = uiProfile,
                        hostName = initialClientName,
                        opponentName = "Peer (${uiProfile.hudBadgeLabel})",
                        hostLife = hostLife,
                        opponentLife = opponentLife,
                        hostUnits = hostUnits.toList(),
                        opponentUnits = opponentUnits.toList(),
                        handCards = handCards.toList(),
                        turnNumber = turnNumber,
                        activePhase = currentPhase.code,
                        syncedBoardState = syncedBoardState,
                        onDeployCardFromHand = { card, _ ->
                            hostUnits.add(card)
                            handCards.remove(card)
                            bossCutIn = BossSummonCutInEvent(
                                cardName = card.name,
                                cardType = card.cardType,
                                atkText = card.attack21,
                                defText = card.defense21,
                                origin = card.cardOrigin,
                                linkedCanonCardId = card.linkedCanonCardId,
                                accentColor = accentColor
                            )
                        },
                        onAttackOpponentZone = { attacker, zone, _ ->
                            val dmg = attacker.attack.coerceAtLeast(500).toLong()
                            opponentLife = (opponentLife - dmg).coerceAtLeast(0L)
                            attackImpact = AttackImpactVfxEvent(
                                attackerName = attacker.name,
                                targetLabel = zone,
                                damageCallout = "-$dmg LP"
                            )
                            val roomId = activeMatchId?.trim().orEmpty()
                            if (isSignedIn && gameStateManager != null && roomId.isNotBlank() && !roomId.startsWith("local_")) {
                                coroutineScope.launch {
                                    gameStateManager.syncLifePointsDelta(
                                        matchId = roomId,
                                        targetSeat = SyncClientSeat.CLIENT_2_PEER,
                                        delta = -dmg,
                                        actorSeat = SyncClientSeat.CLIENT_1_HOST,
                                        actorLabel = "$initialClientName (Window)"
                                    )
                                }
                            }
                        },
                        onDrawCardRequest = {
                            val drawn = BuiltInTcgCatalog.cardsForVariant(activeBaseVariantId).random()
                            if (handCards.size < 8) handCards.add(drawn)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    DuelVfxOverlay(
                        effectStack = emptyList(),
                        bossCutIn = bossCutIn,
                        phaseBanner = phaseBanner,
                        attackImpact = attackImpact,
                        onDismissBossCutIn = { bossCutIn = null },
                        onDismissPhaseBanner = { phaseBanner = null },
                        onDismissAttackImpact = { attackImpact = null },
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            // 5. Compact Duel Disk Action Controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val drawn = BuiltInTcgCatalog.cardsForVariant(activeBaseVariantId).random()
                            if (handCards.size < 8) handCards.add(drawn)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("window_draw_card_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Draw", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val lead = hostUnits.firstOrNull()
                            val dmg = (lead?.attack ?: if (BuiltInTcgCatalog.isEdoProVariant(activeBaseVariantId)) 1500 else 3).toLong()
                            opponentLife = (opponentLife - dmg).coerceAtLeast(0L)
                            turnNumber += 1L
                            currentPhase = TurnPhase.COMBAT
                            attackImpact = AttackImpactVfxEvent(
                                attackerName = lead?.name ?: initialClientName,
                                targetLabel = "Direct Attack",
                                damageCallout = "-$dmg LP"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("window_battle_attack_button")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Battle", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = {
                            hostLife = baseSpec.startingLife
                            opponentLife = baseSpec.startingLife
                            turnNumber = 1L
                            currentPhase = TurnPhase.MAIN
                        },
                        modifier = Modifier.testTag("window_reset_duel_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Duel", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // 6. Compact Hand Dock (clean dark frame placeholder when artResId is null/absent)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "HAND (${handCards.size}) • Tap a card to Solid Vision Summon",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(handCards, key = { "${it.id}_${it.name}" }) { card ->
                            WindowHandCardTile(
                                card = card,
                                accentColor = accentColor,
                                onSummon = {
                                    hostUnits.add(card)
                                    handCards.remove(card)
                                    bossCutIn = BossSummonCutInEvent(
                                        cardName = card.name,
                                        cardType = card.cardType,
                                        atkText = card.attack21,
                                        defText = card.defense21,
                                        origin = card.cardOrigin,
                                        linkedCanonCardId = card.linkedCanonCardId,
                                        accentColor = accentColor
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 7. Compact Footer Telemetry
            item {
                Text(
                    text = "$initialClientType • $initialEndpoint • ${if (isSignedIn) "Cloud Sync Ready" else "Local Offline Window"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun WindowHandCardTile(
    card: CardTemplate,
    accentColor: Color,
    onSummon: () -> Unit
) {
    val resolvedArtResId = card.artResId?.takeIf {
        it != 0 && it != R.drawable.ic_launcher_foreground
    }
    Column(
        modifier = Modifier
            .width(128.dp)
            .duelDiskPlate(accentColor = accentColor, cutSize = 7.dp, halftoneAccents = false)
            .clickable(onClick = onSummon)
            .padding(6.dp)
            .testTag("window_hand_card_${card.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF101829), Color(0xFF070C17))
                    )
                )
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (resolvedArtResId != null) {
                Image(
                    painter = painterResource(id = resolvedArtResId),
                    contentDescription = card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = card.pipCost.ifBlank { "CUSTOM" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = RadiantGold
                    )
                    Text(
                        text = card.cardType,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Text(
            text = card.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "ATK ${card.attack} / DEF ${card.defense}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = EmeraldReady,
            maxLines = 1
        )
    }
}
