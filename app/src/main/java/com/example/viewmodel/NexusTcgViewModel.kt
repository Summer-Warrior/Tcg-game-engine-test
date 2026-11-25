package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BaseVariantSpec
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardEffectType
import com.example.data.CardTemplate
import com.example.data.ClientNode
import com.example.data.CustomDeck
import com.example.data.DiscordApiService
import com.example.data.DiscordEnvSettingsState
import com.example.data.DiscordRpMessage
import com.example.data.DiscordServerHub
import com.example.data.DiscordWebhookBridgeConfig
import com.example.data.DiscordWebhookBroadcastRecord
import com.example.data.FirestoreGameEventLogService
import com.example.data.FirestoreGameStateManager
import com.example.data.GameBoardSyncService
import com.example.data.GameEngine
import com.example.data.GameEngineState
import com.example.data.MatchGameEvent
import com.example.data.MatchSession
import com.example.data.NexusTcgRepository
import com.example.data.PersistentGameEventType
import com.example.data.RealtimeGameSyncState
import com.example.data.RealtimeSyncConnectionStatus
import com.example.data.RpCharacterPersona
import com.example.data.SerializedGameBoardState
import com.example.data.SerializedZonePlacement
import com.example.data.SyncClientSeat
import android.content.Context
import com.example.data.TcgVariant
import com.example.data.TurnPhase
import com.example.data.telemetry.CardInteractionType
import com.example.data.telemetry.CardUsageTelemetryTracker
import com.example.data.telemetry.CustomCardUsageReportSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class HandshakeDiagnostic(
    val clientName: String,
    val clientBaseVariantId: String,
    val activeBaseVariantId: String,
    val isActiveBaseStandard: Boolean,
    val requiresStrictBase: Boolean,
    val isCompatible: Boolean,
    val expectedHash: String,
    val activeHash: String,
    val message: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class NexusTcgViewModel(
    private val repository: NexusTcgRepository,
    private val currentUserId: String,
    private val currentUserName: String,
    private val boardSyncService: GameBoardSyncService? = null,
    private val gameStateManager: FirestoreGameStateManager? = null,
    private val eventLogService: FirestoreGameEventLogService? = null
) : ViewModel() {

    private val _selectedBaseVariantId = MutableStateFlow("EDOPRO_BASE_MR5")
    val selectedBaseVariantId: StateFlow<String> = _selectedBaseVariantId.asStateFlow()

    // Null means running the pure unmodified Base Variant for _selectedBaseVariantId.
    // Non-null means running a user-created custom TcgVariant ruleset.
    private val _activeCustomVariant = MutableStateFlow<TcgVariant?>(null)
    val activeCustomVariant: StateFlow<TcgVariant?> = _activeCustomVariant.asStateFlow()

    private val _activeClientNode = MutableStateFlow<ClientNode?>(null)
    val activeClientNode: StateFlow<ClientNode?> = _activeClientNode.asStateFlow()

    private val _lastHandshakeDiagnostic = MutableStateFlow<HandshakeDiagnostic?>(null)
    val lastHandshakeDiagnostic: StateFlow<HandshakeDiagnostic?> = _lastHandshakeDiagnostic.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _activeMatchId = MutableStateFlow<String?>(null)
    val activeMatchId: StateFlow<String?> = _activeMatchId.asStateFlow()

    private val _localSyncedBoardFallback = MutableStateFlow<SerializedGameBoardState?>(null)

    private val _localSyncSeat = MutableStateFlow(SyncClientSeat.CLIENT_1_HOST)
    val localSyncSeat: StateFlow<SyncClientSeat> = _localSyncSeat.asStateFlow()

    private val _isSyncWriteInFlight = MutableStateFlow(false)

    val realtimeGameSyncState: StateFlow<RealtimeGameSyncState> = combine(
        _activeMatchId,
        _localSyncSeat,
        _selectedBaseVariantId
    ) { matchId, seat, baseId ->
        Triple(matchId, seat, baseId)
    }.flatMapLatest { (matchId, seat, baseId) ->
        val manager = gameStateManager
        val clientLabel = _activeClientNode.value?.clientName ?: currentUserName
        if (matchId.isNullOrBlank() || manager == null) {
            val fallbackSpec = BuiltInTcgCatalog.findBaseSpec(baseId)
            flowOf(
                RealtimeGameSyncState(
                    matchId = "",
                    baseVariantId = baseId,
                    baseVariantName = fallbackSpec.name,
                    localSeat = seat,
                    localClientName = clientLabel,
                    hostLife = fallbackSpec.startingLife,
                    opponentLife = fallbackSpec.startingLife,
                    connectionStatus = RealtimeSyncConnectionStatus.DISCONNECTED
                )
            )
        } else {
            manager.observeMatchSyncState(
                matchId = matchId,
                localSeat = seat,
                localClientName = clientLabel
            ).catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "RealtimeGameSyncState observer error", firestoreError ?: error)
                emit(
                    RealtimeGameSyncState(
                        matchId = matchId,
                        localSeat = seat,
                        localClientName = clientLabel,
                        connectionStatus = RealtimeSyncConnectionStatus.ERROR,
                        errorMessage = (firestoreError ?: error).message ?: "Firestore sync error"
                    )
                )
            }
        }
    }.combine(_isSyncWriteInFlight) { syncState, writing ->
        if (writing && syncState.connectionStatus == RealtimeSyncConnectionStatus.SYNCED) {
            syncState.copy(connectionStatus = RealtimeSyncConnectionStatus.SYNCING_WRITE)
        } else {
            syncState
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = RealtimeGameSyncState(
            localClientName = currentUserName,
            connectionStatus = RealtimeSyncConnectionStatus.DISCONNECTED
        )
    )

    val persistentMatchEventsState: StateFlow<UiState<List<MatchGameEvent>>> = _activeMatchId
        .flatMapLatest { matchId ->
            val service = eventLogService
            if (matchId.isNullOrBlank() || service == null) {
                flowOf(UiState.Success(emptyList()))
            } else {
                service.observeMatchEvents(matchId)
                    .map<List<MatchGameEvent>, UiState<List<MatchGameEvent>>> { events ->
                        UiState.Success(events)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing persistent match event log", rootError)
                        emit(UiState.Error(rootError.message ?: "Failed to sync persistent event log"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Success(emptyList())
        )

    val syncedBoardState: StateFlow<SerializedGameBoardState?> = _activeMatchId
        .flatMapLatest { matchId ->
            val service = boardSyncService
            if (matchId.isNullOrBlank() || service == null) {
                _localSyncedBoardFallback
            } else {
                service.observeRealtimeBoardState(matchId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing real-time GameBoard sync state", firestoreError ?: error)
                        emit(_localSyncedBoardFallback.value)
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val _selectedDiscordServerId = MutableStateFlow<String?>(null)
    val selectedDiscordServerId: StateFlow<String?> = _selectedDiscordServerId.asStateFlow()

    val discordEnvSettings: StateFlow<DiscordEnvSettingsState> = DiscordApiService.settingsState
    val discordBridgeConfig: StateFlow<DiscordWebhookBridgeConfig> = DiscordApiService.bridgeConfig
    val discordBroadcastHistory: StateFlow<List<DiscordWebhookBroadcastRecord>> = DiscordApiService.broadcastHistory

    private val _playerHand = MutableStateFlow<List<CardTemplate>>(
        BuiltInTcgCatalog.cardsForVariant("EDOPRO_BASE_MR5")
    )
    val playerHand: StateFlow<List<CardTemplate>> = _playerHand.asStateFlow()

    val gameEngine = GameEngine("EDOPRO_BASE_MR5")
    val gameEngineState: StateFlow<GameEngineState> = gameEngine.state

    val telemetryTracker = CardUsageTelemetryTracker()
    val telemetrySnapshot: StateFlow<CustomCardUsageReportSnapshot> = telemetryTracker.snapshot

    fun attachTelemetryContext(context: Context) {
        telemetryTracker.attachLocalPersistence(context)
    }

    fun addImportedCardToHandAndTrack(card: CardTemplate, match: MatchSession? = null) {
        _playerHand.value = (listOf(card) + _playerHand.value).take(8)
        telemetryTracker.recordCardDrawn(card, match?.id ?: "local_tabletop")
        if (match != null && match.status != "FINISHED") {
            deployCardToBattlefield(match, card)
        } else {
            telemetryTracker.recordCardPlayed(card, gameEngineState.value.turnNumber, "local_tabletop")
        }
    }

    fun archiveAndResetTelemetry(): String {
        val archived = telemetryTracker.archiveAndReset()
        _statusBanner.value = "Archived and reset local Card Usage Telemetry counters."
        return archived
    }

    private var hasSeededInitialData = false
    private var hasEnsuredEdoProNodes = false
    private var hasSeededDiscordRpData = false

    val variantsState: StateFlow<UiState<List<TcgVariant>>> = repository.observeVariants(currentUserId)
        .map<List<TcgVariant>, UiState<List<TcgVariant>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing variants", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load engine variants"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val clientsState: StateFlow<UiState<List<ClientNode>>> = repository.observeClients(currentUserId)
        .map<List<ClientNode>, UiState<List<ClientNode>>> { list ->
            if (list.isEmpty() && !hasSeededInitialData) {
                hasSeededInitialData = true
                hasEnsuredEdoProNodes = true
                seedStarterNodesAndVariants()
            } else if (list.isNotEmpty() && !hasEnsuredEdoProNodes) {
                hasEnsuredEdoProNodes = true
                val hasBaseEdo = list.any { it.baseVariantId == "EDOPRO_BASE_MR5" }
                val hasCloneEdo = list.any { it.baseVariantId == "EDOPRO_CUSTOM_FORK" }
                val hasBaseForge = list.any { it.baseVariantId == "FORGE_MTG_BASE" }
                val hasCloneForge = list.any { it.baseVariantId == "FORGE_CUSTOM_CLONE" }
                val hasHybridMix = list.any { it.baseVariantId == "HYBRID_FORGE_EDOPRO_MIX" }
                if (!hasBaseEdo || !hasCloneEdo || !hasBaseForge || !hasCloneForge || !hasHybridMix) {
                    ensureCoreAndForgeAndHybridNodes(
                        hasBaseEdo = hasBaseEdo,
                        hasCloneEdo = hasCloneEdo,
                        hasBaseForge = hasBaseForge,
                        hasCloneForge = hasCloneForge,
                        hasHybridMix = hasHybridMix
                    )
                }
            }
            if (list.isNotEmpty()) {
                // Reconcile any existing Base Client so its endpointUri refers to the exact GitHub repository URL
                list.forEach { node ->
                    if ((node.baseVariantId == "EDOPRO_BASE_MR5" || node.baseVariantId == "FORGE_MTG_BASE") &&
                        node.endpointUri != BuiltInTcgCatalog.exactGitHubRepoUrl(node.baseVariantId)
                    ) {
                        viewModelScope.launch {
                            repository.syncBaseClientFromUpstreamGitHubRepo(
                                clientId = node.id,
                                baseVariantId = node.baseVariantId
                            )
                        }
                    }
                }
                val currentActiveId = _activeClientNode.value?.id
                val updatedActive = list.firstOrNull { it.id == currentActiveId }
                    ?: list.firstOrNull { it.baseVariantId == _selectedBaseVariantId.value }
                    ?: list.first()
                _activeClientNode.value = updatedActive
            }
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing clients", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load client bridge nodes"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val decksState: StateFlow<UiState<List<CustomDeck>>> = repository.observeDecks(currentUserId)
        .map<List<CustomDeck>, UiState<List<CustomDeck>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing decks", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load decks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val matchesState: StateFlow<UiState<List<MatchSession>>> = combine(
        repository.observeHostedMatches(currentUserId),
        repository.observeOpenLobbyMatches()
    ) { hosted, openLobbies ->
        val merged = (hosted + openLobbies).distinctBy { it.id }
        if (_activeMatchId.value == null && merged.isNotEmpty()) {
            _activeMatchId.value = merged.first().id
        }
        UiState.Success(merged) as UiState<List<MatchSession>>
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing matches", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load matches"))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
    )

    val discordServersState: StateFlow<UiState<List<DiscordServerHub>>> = repository.observeDiscordServers(currentUserId)
        .map<List<DiscordServerHub>, UiState<List<DiscordServerHub>>> { list ->
            if (list.isEmpty() && !hasSeededDiscordRpData) {
                hasSeededDiscordRpData = true
                seedStarterDiscordRoleplayHub()
            } else if (_selectedDiscordServerId.value == null && list.isNotEmpty()) {
                _selectedDiscordServerId.value = list.first().id
            }
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing Discord servers", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load Discord roleplay servers"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val rpCharactersState: StateFlow<UiState<List<RpCharacterPersona>>> = repository.observeRpCharacters(currentUserId)
        .map<List<RpCharacterPersona>, UiState<List<RpCharacterPersona>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing RP characters", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load RP characters"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val discordMessagesState: StateFlow<UiState<List<DiscordRpMessage>>> = repository.observeDiscordMessages(currentUserId)
        .map<List<DiscordRpMessage>, UiState<List<DiscordRpMessage>>> { list ->
            val sorted = list.sortedBy { it.createdAt?.seconds ?: 0L }
            UiState.Success(sorted)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing Discord messages", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load Discord RP chat messages"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private fun ensureCoreAndForgeAndHybridNodes(
        hasBaseEdo: Boolean,
        hasCloneEdo: Boolean,
        hasBaseForge: Boolean,
        hasCloneForge: Boolean,
        hasHybridMix: Boolean
    ) {
        viewModelScope.launch {
            if (!hasBaseEdo) {
                repository.registerClientNode(
                    clientName = "ProjectIgnis EDOPro Base Client",
                    clientType = "EDOPro Base (ProjectIgnis/EDOPro)",
                    endpointUri = "https://github.com/ProjectIgnis/EDOPro",
                    baseVariantId = "EDOPRO_BASE_MR5",
                    requiresStrictBase = true,
                    connectionStatus = "CONNECTED",
                    latencyMs = 15L,
                    maxDigitPrecision = 10L
                )
            }
            if (!hasCloneEdo) {
                repository.registerClientNode(
                    clientName = "EDOPro Custom Project Clone (IgnisForge 21-Digit)",
                    clientType = "EDOPro Custom Clone (21-Digit Vars/Funcs)",
                    endpointUri = "ws://sandbox.edopro-custom.local:7912/expansions",
                    baseVariantId = "EDOPRO_CUSTOM_FORK",
                    requiresStrictBase = false,
                    connectionStatus = "DISCONNECTED",
                    latencyMs = 19L,
                    maxDigitPrecision = 21L,
                    customVarInput21 = "123456789012345678901",
                    customFuncInput21 = "876543210987654321098",
                    customFuncName = "ADD_21",
                    customCodeString21 = BigInt21Engine.DEFAULT_CODE_STRING_21,
                    customCodeVarExpr21 = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
                )
            }
            if (!hasBaseForge) {
                repository.registerClientNode(
                    clientName = "Card-Forge: Forge MTG Standalone Client",
                    clientType = "Card-Forge Standalone (Card-Forge/forge)",
                    endpointUri = "https://github.com/Card-Forge/forge",
                    baseVariantId = "FORGE_MTG_BASE",
                    requiresStrictBase = true,
                    connectionStatus = "CONNECTED",
                    latencyMs = 17L,
                    maxDigitPrecision = 10L,
                    customCodeString21 = "500101",
                    customCodeVarExpr21 = "SVar:BaseForge=\"500101\""
                )
            }
            if (!hasCloneForge) {
                repository.registerClientNode(
                    clientName = "Clone Card-Forge for Custom Projects (21-Digit SVar)",
                    clientType = "Card-Forge Custom Clone (customcards/*.txt)",
                    endpointUri = "git://custom-clone/Card-Forge/forge-CustomProjects",
                    baseVariantId = "FORGE_CUSTOM_CLONE",
                    requiresStrictBase = false,
                    connectionStatus = "CONNECTED",
                    latencyMs = 18L,
                    maxDigitPrecision = 21L,
                    customVarInput21 = "999999999999999999999",
                    customFuncInput21 = "123456789012345678901",
                    customFuncName = "MUL_21",
                    customCodeString21 = "900002010000000000021",
                    customCodeVarExpr21 = BigInt21Engine.DEFAULT_FORGE_CUSTOM_VAR_EXPR_21
                )
            }
            if (!hasHybridMix) {
                repository.registerClientNode(
                    clientName = "Mixed Client: Clone Forge + Clone EDOPro Custom",
                    clientType = "Extensible Hybrid (Forge + EDOPro 21d)",
                    endpointUri = "git://hybrid-mix/Card-Forge-forge+ProjectIgnis-EDOPro-Custom",
                    baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
                    requiresStrictBase = false,
                    connectionStatus = "CONNECTED",
                    latencyMs = 14L,
                    maxDigitPrecision = 21L,
                    customVarInput21 = "999999999999999999999",
                    customFuncInput21 = "888888888888888888888",
                    customFuncName = "SCALE_ATK_LP_21",
                    customCodeString21 = "900003010000000000021",
                    customCodeVarExpr21 = BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21
                )
            }
        }
    }

    private fun seedStarterNodesAndVariants() {
        viewModelScope.launch {
            // 1. Official ProjectIgnis/EDOPro Base Client for Yu-Gi-Oh! (Strict Base Variant — Locked to GitHub Source)
            repository.registerClientNode(
                clientName = "ProjectIgnis EDOPro Base Client",
                clientType = "EDOPro Base (ProjectIgnis/EDOPro)",
                endpointUri = "https://github.com/ProjectIgnis/EDOPro",
                baseVariantId = "EDOPRO_BASE_MR5",
                requiresStrictBase = true,
                connectionStatus = "CONNECTED",
                latencyMs = 15L,
                maxDigitPrecision = 10L
            )
            // 2. Separate EDOPro Clone dedicated to Custom Projects (21-Digit Variable/Function/Code String Support)
            repository.registerClientNode(
                clientName = "EDOPro Custom Project Clone (IgnisForge 21-Digit)",
                clientType = "EDOPro Custom Clone (21-Digit Vars/Funcs)",
                endpointUri = "ws://sandbox.edopro-custom.local:7912/expansions",
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                requiresStrictBase = false,
                connectionStatus = "CONNECTED",
                latencyMs = 19L,
                maxDigitPrecision = 21L,
                customVarInput21 = "123456789012345678901",
                customFuncInput21 = "876543210987654321098",
                customFuncName = "ADD_21",
                customCodeString21 = BigInt21Engine.DEFAULT_CODE_STRING_21,
                customCodeVarExpr21 = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
            )
            // 3. Standalone Card-Forge Client for Magic: The Gathering (https://github.com/Card-Forge/forge — Locked to GitHub Source)
            repository.registerClientNode(
                clientName = "Card-Forge: Forge MTG Standalone Client",
                clientType = "Card-Forge Standalone (Card-Forge/forge)",
                endpointUri = "https://github.com/Card-Forge/forge",
                baseVariantId = "FORGE_MTG_BASE",
                requiresStrictBase = true,
                connectionStatus = "CONNECTED",
                latencyMs = 17L,
                maxDigitPrecision = 10L,
                customCodeString21 = "500101",
                customCodeVarExpr21 = "SVar:BaseForge=\"500101\""
            )
            // 4. Separate Clone of Card-Forge for Custom MTG Projects (21-Digit SVar/P-T)
            repository.registerClientNode(
                clientName = "Clone Card-Forge for Custom Projects (21-Digit SVar)",
                clientType = "Card-Forge Custom Clone (customcards/*.txt)",
                endpointUri = "git://custom-clone/Card-Forge/forge-CustomProjects",
                baseVariantId = "FORGE_CUSTOM_CLONE",
                requiresStrictBase = false,
                connectionStatus = "CONNECTED",
                latencyMs = 18L,
                maxDigitPrecision = 21L,
                customVarInput21 = "999999999999999999999",
                customFuncInput21 = "123456789012345678901",
                customFuncName = "MUL_21",
                customCodeString21 = "900002010000000000021",
                customCodeVarExpr21 = BigInt21Engine.DEFAULT_FORGE_CUSTOM_VAR_EXPR_21
            )
            // 5. Extensible Mixed Client Combining Clone Card-Forge + Clone EDOPro for Custom Project
            repository.registerClientNode(
                clientName = "Mixed Client: Clone Forge + Clone EDOPro Custom",
                clientType = "Extensible Hybrid (Forge + EDOPro 21d)",
                endpointUri = "git://hybrid-mix/Card-Forge-forge+ProjectIgnis-EDOPro-Custom",
                baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
                requiresStrictBase = false,
                connectionStatus = "CONNECTED",
                latencyMs = 14L,
                maxDigitPrecision = 21L,
                customVarInput21 = "999999999999999999999",
                customFuncInput21 = "888888888888888888888",
                customFuncName = "SCALE_ATK_LP_21",
                customCodeString21 = "900003010000000000021",
                customCodeVarExpr21 = BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21
            )
            // 6. Additional Multi-TCG Clients
            repository.registerClientNode(
                clientName = "AetherDesk Desktop v2.4",
                clientType = "Desktop Bridge",
                endpointUri = "wss://relay.nexustcg.io/aether-base",
                baseVariantId = "AETHER_MANA_V1",
                requiresStrictBase = true,
                connectionStatus = "DISCONNECTED",
                latencyMs = 22L
            )

            // Seed EDOPro Base MR5, EDOPro Custom Clone, Card-Forge MTG Base, Card-Forge Custom Clone & Mixed Hybrid Variants
            repository.createVariant(
                name = "ProjectIgnis EDOPro Base (MR5 Standard)",
                baseVariantId = "EDOPRO_BASE_MR5",
                resourceName = "Summon/Chain",
                startingLife = 8000L,
                startingHandSize = 5L,
                maxResourcePool = 5L,
                combatRule = "ATK_DEF_BATTLE",
                maxDigitPrecision = 10L,
                startingLp21 = "8000",
                customCodeString21 = "89631139",
                customCodeVarExpr21 = "s.code_var_21 = \"89631139\""
            )
            repository.createVariant(
                name = "EDOPro Custom Clone (21-Digit BigInt21 Boss Duel)",
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                resourceName = "Summon/Chain",
                startingLife = 999999999999999999L,
                startingHandSize = 6L,
                maxResourcePool = 6L,
                combatRule = "ATK_DEF_BATTLE",
                maxDigitPrecision = 21L,
                startingLp21 = "999999999999999999999",
                customVarInput21 = "123456789012345678901",
                customFuncInput21 = "876543210987654321098",
                customFuncName = "ADD_21",
                customCodeString21 = BigInt21Engine.DEFAULT_CODE_STRING_21,
                customCodeVarExpr21 = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
            )
            repository.createVariant(
                name = "Card-Forge Standalone (MTG 20 Life Standard)",
                baseVariantId = "FORGE_MTG_BASE",
                resourceName = "Mana",
                startingLife = 20L,
                startingHandSize = 7L,
                maxResourcePool = 10L,
                combatRule = "BLOCKER_ASSIGN",
                maxDigitPrecision = 10L,
                startingLp21 = "20",
                customCodeString21 = "500101",
                customCodeVarExpr21 = "SVar:BaseForge=\"500101\""
            )
            repository.createVariant(
                name = "Clone Card-Forge Custom Project (21-Digit SVar)",
                baseVariantId = "FORGE_CUSTOM_CLONE",
                resourceName = "Custom Mana",
                startingLife = 40L,
                startingHandSize = 7L,
                maxResourcePool = 12L,
                combatRule = "BLOCKER_ASSIGN",
                maxDigitPrecision = 21L,
                startingLp21 = "999999999999999999999",
                customVarInput21 = "999999999999999999999",
                customFuncInput21 = "123456789012345678901",
                customFuncName = "MUL_21",
                customCodeString21 = "900002010000000000021",
                customCodeVarExpr21 = BigInt21Engine.DEFAULT_FORGE_CUSTOM_VAR_EXPR_21
            )
            repository.createVariant(
                name = "Mixed Client: Forge + EDOPro Custom Clone (Extensible)",
                baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
                resourceName = "Mana/Chain",
                startingLife = 8000L,
                startingHandSize = 7L,
                maxResourcePool = 12L,
                combatRule = "ATK_DEF_BATTLE",
                maxDigitPrecision = 21L,
                startingLp21 = "999999999999999999999",
                customVarInput21 = "999999999999999999999",
                customFuncInput21 = "888888888888888888888",
                customFuncName = "SCALE_ATK_LP_21",
                customCodeString21 = "900003010000000000021",
                customCodeVarExpr21 = BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21
            )

            // Seed starter Decks for EDOPro Base, EDOPro Custom Clone, Card-Forge Standalone & Mixed Client
            repository.createDeck(
                name = "Ignis Sovereign Dragon (.ydk)",
                baseVariantId = "EDOPRO_BASE_MR5",
                strategyTag = "Master Rule 5",
                cardIds = BuiltInTcgCatalog.cardsForVariant("EDOPRO_BASE_MR5").map { it.id },
                customCardName = "Blue-Eyes Ignis Sovereign",
                customCardCost = 8L,
                customCardAttack = 3000L,
                customCardDefense = 2500L,
                customCardAbility = "[ocgcore] Cannot be destroyed by card effects. Banishes 1 opposing monster.",
                customAttack21 = "3000",
                customDefense21 = "2500",
                customVarInput21 = "8000",
                customCodeString21 = "89631139",
                customCodeVarExpr21 = "s.code_var_21 = \"89631139\""
            )
            repository.createDeck(
                name = "Custom Project Expansion (.cdb + 21-Digit Lua)",
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                strategyTag = "21-Digit Custom Expansion",
                cardIds = BuiltInTcgCatalog.cardsForVariant("EDOPRO_CUSTOM_FORK").map { it.id },
                customCardName = "Custom Project: Singularity Overlord",
                customCardCost = 12L,
                customCardAttack = 999999999999999999L,
                customCardDefense = 999999999999999999L,
                customCardAbility = "[c900001010000000000021.lua] 21-Digit Code String & Register: Evaluates ADD_21 / MUL_21 on 21-digit coding variables.",
                customAttack21 = "999999999999999999999",
                customDefense21 = "888888888888888888888",
                customVarInput21 = "999999999999999999999",
                customCodeString21 = BigInt21Engine.DEFAULT_CODE_STRING_21,
                customCodeVarExpr21 = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
            )
            repository.createDeck(
                name = "Card-Forge Planeswalker Control (.dck)",
                baseVariantId = "FORGE_MTG_BASE",
                strategyTag = "MTG Stack & Mana",
                cardIds = BuiltInTcgCatalog.cardsForVariant("FORGE_MTG_BASE").map { it.id },
                customCardName = "Serra Forge Archon",
                customCardCost = 5L,
                customCardAttack = 5L,
                customCardDefense = 5L,
                customCardAbility = "[Card-Forge] Flying, vigilance. Tap: Add {W}{U} or counter target spell on The Stack.",
                customAttack21 = "5",
                customDefense21 = "5",
                customVarInput21 = "20",
                customCodeString21 = "500101",
                customCodeVarExpr21 = "SVar:BaseForge=\"500101\""
            )
            repository.createDeck(
                name = "Mixed Forge + EDOPro Custom Hybrid (.dck + .ydk)",
                baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
                strategyTag = "Extensible Hybrid (Forge+EDOPro)",
                cardIds = BuiltInTcgCatalog.cardsForVariant("HYBRID_FORGE_EDOPRO_MIX").map { it.id },
                customCardName = "Hybrid Mix: Omniverse Lotus Accesscode",
                customCardCost = 8L,
                customCardAttack = 999999999999999999L,
                customCardDefense = 999999999999999999L,
                customCardAbility = "[Card-Forge SVar + ocgcore-bigint21 Lua] Bridges MTG Mana & Stack with 21-Digit Yu-Gi-Oh! Chain Links.",
                customAttack21 = "999999999999999999999",
                customDefense21 = "999999999999999999999",
                customVarInput21 = "999999999999999999999",
                customCodeString21 = "900003010000000000021",
                customCodeVarExpr21 = BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21
            )

            // Seed initial EDOPro Base Arena Match
            val matchRes = repository.createMatchSession(
                hostName = currentUserName,
                title = "EDOPro Base Duel Server (MR5 • 8000 LP)",
                baseVariantId = "EDOPRO_BASE_MR5",
                connectedClients = listOf("ProjectIgnis EDOPro Base Client")
            )
            matchRes.onSuccess { id -> _activeMatchId.value = id }
        }
    }

    /**
     * Spawns a dedicated separate EDOPro Clone Client + Custom Engine Variant + Custom .cdb/.lua Deck
     * with full 21-digit variable, function, and code-string coding variable capabilities
     * (0 .. 999,999,999,999,999,999,999).
     */
    fun cloneEdoProForCustomProject(
        projectName: String,
        customCdbFile: String,
        startingLp21: String,
        monsterZoneCount: Long,
        customVarInput21: String = "123456789012345678901",
        customFuncInput21: String = "876543210987654321098",
        customFuncName: String = "ADD_21",
        customCodeString21: String = BigInt21Engine.DEFAULT_CODE_STRING_21,
        customCodeVarExpr21: String = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
    ) {
        val cleanName = projectName.trim().ifBlank { "Ignis 21-Digit Custom Project" }
        val cleanCdb = customCdbFile.trim().ifBlank { "custom_project.cdb" }
        val cleanLp21 = BigInt21Engine.sanitizeTo21Digits(startingLp21, "8000")
        val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(customVarInput21, "123456789012345678901")
        val cleanArg21 = BigInt21Engine.sanitizeTo21Digits(customFuncInput21, "876543210987654321098")
        val cleanCodeStr21 = BigInt21Engine.sanitizeTo21Digits(
            customCodeString21,
            BigInt21Engine.DEFAULT_CODE_STRING_21
        )
        val cleanCodeVarExpr21 = BigInt21Engine.sanitizeCodeVarExpr21(
            customCodeVarExpr21,
            "s.code_var_21 = \"$cleanCodeStr21\"; s.setcode_21 = \"$cleanVar21\""
        )
        val funcResult21 = BigInt21Engine.evaluateFunction21(customFuncName, cleanVar21, cleanArg21)
        val safeFirestoreLife = BigInt21Engine.toSafeFirestoreLong(cleanLp21, minVal = 1L)

        viewModelScope.launch {
            repository.registerClientNode(
                clientName = "EDOPro Clone: $cleanName",
                clientType = "EDOPro 21-Digit Clone ($cleanCdb)",
                endpointUri = "ws://custom-clone.edopro.local:7912/$cleanCdb",
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                requiresStrictBase = false,
                connectionStatus = "CONNECTED",
                latencyMs = 14L,
                maxDigitPrecision = 21L,
                customVarInput21 = cleanVar21,
                customFuncInput21 = cleanArg21,
                customFuncName = customFuncName,
                customCodeString21 = cleanCodeStr21,
                customCodeVarExpr21 = cleanCodeVarExpr21
            )
            repository.createVariant(
                name = "$cleanName (21-Digit Clone)",
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                resourceName = "Summon/Chain",
                startingLife = safeFirestoreLife,
                startingHandSize = 5L,
                maxResourcePool = monsterZoneCount.coerceIn(3L, 10L),
                combatRule = "ATK_DEF_BATTLE",
                maxDigitPrecision = 21L,
                startingLp21 = cleanLp21,
                customVarInput21 = cleanVar21,
                customFuncInput21 = cleanArg21,
                customFuncName = customFuncName,
                customCodeString21 = cleanCodeStr21,
                customCodeVarExpr21 = cleanCodeVarExpr21
            )
            repository.createDeck(
                name = "$cleanName Expansion (.ydk)",
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                strategyTag = "21-Digit Custom ($cleanCdb)",
                cardIds = BuiltInTcgCatalog.cardsForVariant("EDOPRO_CUSTOM_FORK").map { it.id },
                customCardName = "$cleanName Ace Dragon",
                customCardCost = 10L,
                customCardAttack = BigInt21Engine.toSafeFirestoreLong(funcResult21, minVal = 0L),
                customCardDefense = BigInt21Engine.toSafeFirestoreLong(cleanVar21, minVal = 0L),
                customCardAbility = "[c$cleanCodeStr21.lua | $cleanCdb] 21-Digit CodeVars ($cleanCodeVarExpr21) & $customFuncName($cleanVar21, $cleanArg21) = $funcResult21",
                customAttack21 = funcResult21,
                customDefense21 = cleanVar21,
                customVarInput21 = cleanVar21,
                customCodeString21 = cleanCodeStr21,
                customCodeVarExpr21 = cleanCodeVarExpr21
            )
            _selectedBaseVariantId.value = "EDOPRO_CUSTOM_FORK"
            _activeCustomVariant.value = null
            refreshHandForVariant("EDOPRO_CUSTOM_FORK")
            _statusBanner.value = "Cloned '$cleanName' with 21-digit code string c$cleanCodeStr21 (${cleanCodeStr21.length}d) & $customFuncName -> $funcResult21!"
        }
    }

    /**
     * Clones https://github.com/Card-Forge/forge into a separate Custom Project Client (`FORGE_CUSTOM_CLONE`),
     * Custom Engine Variant, and Custom `.dck` / `cardsfolder/<card>.txt` Deck with 21-digit SVar support.
     */
    fun cloneForgeForCustomProject(
        projectName: String,
        customCardsFolder: String,
        startingLife21: String,
        maxManaPool: Long,
        customSvar21: String = "999999999999999999999",
        customFuncName: String = "MUL_21",
        customCodeString21: String = "900002010000000000021"
    ) {
        val cleanName = projectName.trim().ifBlank { "Card-Forge Custom Set Project" }
        val cleanFolder = customCardsFolder.trim().ifBlank { "custom_cardsfolder" }
        val cleanLife21 = BigInt21Engine.sanitizeTo21Digits(startingLife21, "40")
        val cleanSvar21 = BigInt21Engine.sanitizeTo21Digits(customSvar21, "999999999999999999999")
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(customCodeString21, "900002010000000000021")
        val forgeExpr21 = BigInt21Engine.filterCodeStringWith21DigitLimit(
            "SVar:X:21d=\"$cleanCode21\"; SVar:Power21=\"$cleanSvar21\""
        )
        val funcResult21 = BigInt21Engine.evaluateFunction21(customFuncName, cleanSvar21, cleanLife21)
        val safeFirestoreLife = BigInt21Engine.toSafeFirestoreLong(cleanLife21, minVal = 1L)

        viewModelScope.launch {
            repository.registerClientNode(
                clientName = "Card-Forge Clone: $cleanName",
                clientType = "Card-Forge Custom Clone ($cleanFolder)",
                endpointUri = "git://custom-clone/Card-Forge/forge/$cleanFolder",
                baseVariantId = "FORGE_CUSTOM_CLONE",
                requiresStrictBase = false,
                connectionStatus = "CONNECTED",
                latencyMs = 15L,
                maxDigitPrecision = 21L,
                customVarInput21 = cleanSvar21,
                customFuncInput21 = cleanLife21,
                customFuncName = customFuncName,
                customCodeString21 = cleanCode21,
                customCodeVarExpr21 = forgeExpr21
            )
            repository.createVariant(
                name = "$cleanName (Card-Forge Clone)",
                baseVariantId = "FORGE_CUSTOM_CLONE",
                resourceName = "Custom Mana",
                startingLife = safeFirestoreLife,
                startingHandSize = 7L,
                maxResourcePool = maxManaPool.coerceIn(3L, 20L),
                combatRule = "BLOCKER_ASSIGN",
                maxDigitPrecision = 21L,
                startingLp21 = cleanLife21,
                customVarInput21 = cleanSvar21,
                customFuncInput21 = cleanLife21,
                customFuncName = customFuncName,
                customCodeString21 = cleanCode21,
                customCodeVarExpr21 = forgeExpr21
            )
            repository.createDeck(
                name = "$cleanName Custom Forge (.dck)",
                baseVariantId = "FORGE_CUSTOM_CLONE",
                strategyTag = "Card-Forge Custom ($cleanFolder)",
                cardIds = BuiltInTcgCatalog.cardsForVariant("FORGE_CUSTOM_CLONE").map { it.id },
                customCardName = "$cleanName Planeswalker Sovereign",
                customCardCost = 5L,
                customCardAttack = BigInt21Engine.toSafeFirestoreLong(funcResult21, minVal = 0L),
                customCardDefense = BigInt21Engine.toSafeFirestoreLong(cleanSvar21, minVal = 0L),
                customCardAbility = "[Card-Forge Clone | $cleanFolder/c$cleanCode21.txt] SVar:Power21=$cleanSvar21 | $customFuncName -> $funcResult21",
                customAttack21 = funcResult21,
                customDefense21 = cleanSvar21,
                customVarInput21 = cleanSvar21,
                customCodeString21 = cleanCode21,
                customCodeVarExpr21 = forgeExpr21
            )
            _selectedBaseVariantId.value = "FORGE_CUSTOM_CLONE"
            _activeCustomVariant.value = null
            refreshHandForVariant("FORGE_CUSTOM_CLONE")
            _statusBanner.value = "Cloned Card-Forge (https://github.com/Card-Forge/forge) for Custom Project '$cleanName'!"
        }
    }

    /**
     * Clones both Card-Forge (`https://github.com/Card-Forge/forge`) AND `"Clone EDOPro for Custom Project"`
     * into an extensible Mixed Client (`HYBRID_FORGE_EDOPRO_MIX`) that users can continually add modules to.
     */
    fun spawnMixedForgeEdoProClient(
        mixedClientName: String,
        extraModuleToInclude: String = "Commander_100_EDH_Zone",
        startingLp21: String = "999999999999999999999",
        customVarInput21: String = "999999999999999999999",
        customCodeString21: String = "900003010000000000021"
    ) {
        val cleanName = mixedClientName.trim().ifBlank { "Mixed Forge + EDOPro Custom Client" }
        val cleanLp21 = BigInt21Engine.sanitizeTo21Digits(startingLp21, "8000")
        val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(customVarInput21, "999999999999999999999")
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(customCodeString21, "900003010000000000021")
        val hybridExpr21 = BigInt21Engine.appendMixedClientModule(
            currentExpr21 = BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21,
            newModuleRaw = extraModuleToInclude,
            codeString21 = cleanCode21
        )
        val modules = BigInt21Engine.extractMixedClientModules(hybridExpr21)
        val safeFirestoreLife = BigInt21Engine.toSafeFirestoreLong(cleanLp21, minVal = 1L)

        viewModelScope.launch {
            repository.registerClientNode(
                clientName = cleanName,
                clientType = "Mixed Client (Forge + EDOPro 21d • ${modules.size} Mods)",
                endpointUri = "git://hybrid-mix/Card-Forge-forge+ProjectIgnis-EDOPro-Custom",
                baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
                requiresStrictBase = false,
                connectionStatus = "CONNECTED",
                latencyMs = 13L,
                maxDigitPrecision = 21L,
                customVarInput21 = cleanVar21,
                customFuncInput21 = cleanLp21,
                customFuncName = "SCALE_ATK_LP_21",
                customCodeString21 = cleanCode21,
                customCodeVarExpr21 = hybridExpr21
            )
            repository.createVariant(
                name = "$cleanName (Extensible Mix)",
                baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
                resourceName = "Mana/Chain",
                startingLife = safeFirestoreLife,
                startingHandSize = 7L,
                maxResourcePool = 12L,
                combatRule = "ATK_DEF_BATTLE",
                maxDigitPrecision = 21L,
                startingLp21 = cleanLp21,
                customVarInput21 = cleanVar21,
                customFuncInput21 = cleanLp21,
                customFuncName = "SCALE_ATK_LP_21",
                customCodeString21 = cleanCode21,
                customCodeVarExpr21 = hybridExpr21
            )
            repository.createDeck(
                name = "$cleanName Hybrid Deck (.dck + .ydk)",
                baseVariantId = "HYBRID_FORGE_EDOPRO_MIX",
                strategyTag = "Mixed Client (${modules.size} Modules)",
                cardIds = BuiltInTcgCatalog.cardsForVariant("HYBRID_FORGE_EDOPRO_MIX").map { it.id },
                customCardName = "$cleanName Sovereign Dragon-Walker",
                customCardCost = 8L,
                customCardAttack = BigInt21Engine.toSafeFirestoreLong(cleanVar21, minVal = 0L),
                customCardDefense = BigInt21Engine.toSafeFirestoreLong(cleanVar21, minVal = 0L),
                customCardAbility = "[Hybrid Forge+EDOPro | Mods: ${modules.joinToString("+")}] Bridges MTG Stack & 21-Digit ocgcore Lua.",
                customAttack21 = cleanVar21,
                customDefense21 = cleanVar21,
                customVarInput21 = cleanVar21,
                customCodeString21 = cleanCode21,
                customCodeVarExpr21 = hybridExpr21
            )
            _selectedBaseVariantId.value = "HYBRID_FORGE_EDOPRO_MIX"
            _activeCustomVariant.value = null
            refreshHandForVariant("HYBRID_FORGE_EDOPRO_MIX")
            _statusBanner.value = "Created Mixed Client '$cleanName' combining Clone Card-Forge + Clone EDOPro with ${modules.size} modules (${modules.joinToString(", ")})!"
        }
    }

    /**
     * Dynamically adds a new custom module, rule bridge, custom zone, or script hook into an
     * extensible Mixed Client (`HYBRID_FORGE_EDOPRO_MIX`) and persists the updated module bus to Firestore.
     */
    fun addExtensionToMixedClient(
        targetClient: ClientNode?,
        newModuleName: String,
        newModuleCategory: String = "RULE_BRIDGE",
        newVarInput21: String = ""
    ) {
        val cleanModTitle = "${newModuleCategory.take(6)}_${newModuleName.trim().ifBlank { "Custom_Hook" }}"
            .replace(" ", "_")
            .take(28)
        val allClients = (clientsState.value as? UiState.Success)?.data.orEmpty()
        val resolvedMixClient = targetClient
            ?: allClients.firstOrNull { it.baseVariantId == "HYBRID_FORGE_EDOPRO_MIX" }

        if (resolvedMixClient == null) {
            spawnMixedForgeEdoProClient(
                mixedClientName = "Mixed Client: Clone Forge + Clone EDOPro Custom",
                extraModuleToInclude = cleanModTitle
            )
            return
        }

        val updatedExpr21 = BigInt21Engine.appendMixedClientModule(
            currentExpr21 = resolvedMixClient.customCodeVarExpr21,
            newModuleRaw = cleanModTitle,
            codeString21 = resolvedMixClient.customCodeString21
        )
        val updatedVar21 = if (newVarInput21.isNotBlank()) {
            BigInt21Engine.sanitizeTo21Digits(newVarInput21, resolvedMixClient.customVarInput21)
        } else {
            resolvedMixClient.customVarInput21
        }
        val modules = BigInt21Engine.extractMixedClientModules(updatedExpr21)

        viewModelScope.launch {
            val res = repository.updateClient21DigitRegisters(
                clientId = resolvedMixClient.id,
                customVarInput21 = updatedVar21,
                customFuncInput21 = resolvedMixClient.customFuncInput21,
                customFuncName = resolvedMixClient.customFuncName,
                customCodeString21 = resolvedMixClient.customCodeString21,
                customCodeVarExpr21 = updatedExpr21
            )
            res.onSuccess {
                _selectedBaseVariantId.value = "HYBRID_FORGE_EDOPRO_MIX"
                refreshHandForVariant("HYBRID_FORGE_EDOPRO_MIX")
                _statusBanner.value = "Added '$cleanModTitle' to '${resolvedMixClient.clientName}'! Active Mixed Modules (${modules.size}): ${modules.joinToString(" + ")}"
            }.onFailure { e ->
                _statusBanner.value = "Failed to add module to Mixed Client: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Executes a 21-digit custom function and updates 21-digit code-string coding variables
     * on a ClientNode's registers and persists the state to Firestore.
     * Strictly rejects edits on Base Clients locked to their upstream GitHub source repository.
     */
    fun executeClient21DigitFunction(
        client: ClientNode,
        customVarInput21: String,
        customFuncInput21: String,
        customFuncName: String,
        customCodeString21: String = client.customCodeString21,
        customCodeVarExpr21: String = client.customCodeVarExpr21
    ) {
        if (BuiltInTcgCatalog.isLockedGitHubBaseClientNode(client)) {
            val repoUrl = BuiltInTcgCatalog.exactGitHubRepoUrl(client.baseVariantId)
            _statusBanner.value = "LOCKED TO GITHUB SOURCE REPOSITORY ($repoUrl): '${client.clientName}' cannot be edited by the app or users. Only upstream source repository updates are permitted."
            return
        }
        viewModelScope.launch {
            val res = repository.updateClient21DigitRegisters(
                clientId = client.id,
                customVarInput21 = customVarInput21,
                customFuncInput21 = customFuncInput21,
                customFuncName = customFuncName,
                customCodeString21 = customCodeString21,
                customCodeVarExpr21 = customCodeVarExpr21
            )
            res.onSuccess { result21 ->
                val cleanCodeStr = BigInt21Engine.sanitizeTo21Digits(customCodeString21)
                _statusBanner.value = "21-Digit Registers & Code String (c$cleanCodeStr) Updated on '${client.clientName}': $customFuncName -> $result21"
            }.onFailure { e ->
                _statusBanner.value = "Failed to update 21-digit register: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Pulls and verifies canonical protocol updates ONLY from the official upstream GitHub source
     * repository (`https://github.com/ProjectIgnis/EDOPro` or `https://github.com/Card-Forge/forge`)
     * for a locked Base Client.
     */
    fun syncBaseClientFromUpstreamGitHub(client: ClientNode) {
        val exactRepoUrl = BuiltInTcgCatalog.exactGitHubRepoUrl(client.baseVariantId)
        val spec = BuiltInTcgCatalog.findBaseSpec(client.baseVariantId)
        viewModelScope.launch {
            val res = repository.syncBaseClientFromUpstreamGitHubRepo(
                clientId = client.id,
                baseVariantId = client.baseVariantId,
                verifiedUpstreamVersion = spec.protocolVersion
            )
            res.onSuccess { syncedUrl ->
                _statusBanner.value = "SOURCE REPOSITORY SYNC COMPLETE: Updated '${client.clientName}' strictly from upstream GitHub repository ($syncedUrl • v${spec.protocolVersion}). User/agent manual edits remain locked."
            }.onFailure { e ->
                _statusBanner.value = "GitHub source sync failed: ${e.localizedMessage}"
            }
        }
    }

    fun selectBaseVariant(baseVariantId: String) {
        _selectedBaseVariantId.value = baseVariantId
        _activeCustomVariant.value = null
        val clients = (clientsState.value as? UiState.Success)?.data.orEmpty()
        val matchingClient = clients.firstOrNull { it.baseVariantId == baseVariantId }
        if (matchingClient != null) {
            _activeClientNode.value = matchingClient
        }
        refreshHandForVariant(baseVariantId)
        val uiProfile = BuiltInTcgCatalog.systemUiProfileFor(baseVariantId, matchingClient)
        _statusBanner.value = "Switched UI to TCG Client System: ${uiProfile.systemTitle} (${uiProfile.lifeLabel} • ${uiProfile.boardLayoutType})"
    }

    /**
     * Switches the entire NexusTCG UI, theme, terminology, and active board layout to the selected
     * TCG Client's system and connects the client node.
     */
    fun selectClientAndSwitchSystem(client: ClientNode) {
        _activeClientNode.value = client
        connectClientNode(client = client, syncToClientBaseVariant = true)
    }

    fun activateCustomVariant(variant: TcgVariant) {
        _selectedBaseVariantId.value = variant.baseVariantId
        _activeCustomVariant.value = if (variant.isBaseStandard) null else variant
        refreshHandForVariant(variant.baseVariantId)
        val modeLabel = if (variant.isBaseStandard) {
            "Base Variant Standard"
        } else {
            "21-Digit Custom Override (${variant.startingLp21} LP)"
        }
        _statusBanner.value = "Activated ${variant.name} ($modeLabel)"
    }

    fun resetToActiveBaseStandard() {
        val custom = _activeCustomVariant.value
        _activeCustomVariant.value = null
        if (custom != null) {
            viewModelScope.launch {
                repository.resetVariantToBaseStandard(custom)
            }
        }
        val spec = BuiltInTcgCatalog.findBaseSpec(_selectedBaseVariantId.value)
        _statusBanner.value = "Locked engine to unmodified Base Variant: ${spec.name} (${spec.protocolHash})"
    }

    private fun refreshHandForVariant(baseVariantId: String) {
        val baseCards = BuiltInTcgCatalog.cardsForVariant(baseVariantId)
        val customDecks = (decksState.value as? UiState.Success)?.data.orEmpty()
            .filter { it.baseVariantId == baseVariantId }
        val forgedCards = customDecks.mapIndexed { idx, deck ->
            CardTemplate(
                id = "custom_${deck.id}",
                passcode = deck.customCodeString21.ifBlank { "90000${800 + idx}" },
                name = deck.customCardName,
                baseVariantId = deck.baseVariantId,
                cardType = if (BuiltInTcgCatalog.isEdoProVariant(baseVariantId)) {
                    "Monster / 21-Digit Lua (Lv.${deck.customCardCost})"
                } else {
                    "Forged Signature"
                },
                cost = if (BuiltInTcgCatalog.isEdoProVariant(baseVariantId)) 1 else deck.customCardCost.toInt(),
                attack = deck.customCardAttack.coerceIn(0L, 999999L).toInt(),
                defense = deck.customCardDefense.coerceIn(0L, 999999L).toInt(),
                attack21 = deck.customAttack21,
                defense21 = deck.customDefense21,
                abilityText = deck.customCardAbility,
                artResId = baseCards.first().artResId
            )
        }
        val nextHand = (forgedCards + baseCards).take(6)
        _playerHand.value = nextHand
        gameEngine.resetForVariant(
            baseVariantId = baseVariantId,
            hostName = currentUserName,
            opponentName = _activeClientNode.value?.clientName
                ?: BuiltInTcgCatalog.systemUiProfileFor(baseVariantId).clientDisplayName
        )
    }

    fun createNewEngineVariant(
        name: String,
        baseVariantId: String,
        resourceName: String,
        startingLife: Long,
        startingHandSize: Long,
        maxResourcePool: Long,
        combatRule: String,
        startingLp21: String = startingLife.toString(),
        customVarInput21: String = "100000000000000000000",
        customFuncInput21: String = "899999999999999999999",
        customFuncName: String = "ADD_21"
    ) {
        if (name.isBlank()) return
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseVariantId)
        val isAttemptingToMutateLockedBase = BuiltInTcgCatalog.isLockedGitHubBaseClient(baseVariantId) &&
            (startingLife != baseSpec.startingLife ||
                startingHandSize != baseSpec.startingHandSize ||
                maxResourcePool != baseSpec.maxResourcePool ||
                combatRule != baseSpec.combatRule ||
                resourceName != baseSpec.resourceName)
        if (isAttemptingToMutateLockedBase) {
            val exactRepo = BuiltInTcgCatalog.exactGitHubRepoUrl(baseVariantId)
            _statusBanner.value = "LOCKED TO GITHUB SOURCE ($exactRepo): Base client '${baseSpec.name}' is locked against edits from the app and users. Use the Custom Project Clone or Mixed Client for custom rule edits."
            return
        }
        viewModelScope.launch {
            val res = repository.createVariant(
                name = name,
                baseVariantId = baseVariantId,
                resourceName = resourceName,
                startingLife = startingLife,
                startingHandSize = startingHandSize,
                maxResourcePool = maxResourcePool,
                combatRule = combatRule,
                maxDigitPrecision = if (BuiltInTcgCatalog.supports21DigitRegisters(baseVariantId)) 21L else 10L,
                startingLp21 = startingLp21,
                customVarInput21 = customVarInput21,
                customFuncInput21 = customFuncInput21,
                customFuncName = customFuncName
            )
            res.onSuccess {
                _statusBanner.value = "Saved TCG Engine Variant '$name'"
            }.onFailure { e ->
                _statusBanner.value = "Error saving variant: ${e.localizedMessage}"
            }
        }
    }

    fun deleteVariant(variantId: String) {
        val existingVariants = (variantsState.value as? UiState.Success)?.data.orEmpty()
        val targetVariant = existingVariants.firstOrNull { it.id == variantId }
        if (targetVariant != null && targetVariant.isBaseStandard &&
            BuiltInTcgCatalog.isLockedGitHubBaseClient(targetVariant.baseVariantId)
        ) {
            val exactRepo = BuiltInTcgCatalog.exactGitHubRepoUrl(targetVariant.baseVariantId)
            _statusBanner.value = "LOCKED TO GITHUB SOURCE ($exactRepo): Base variant '${targetVariant.name}' is locked against deletion or edits."
            return
        }
        viewModelScope.launch {
            if (_activeCustomVariant.value?.id == variantId) {
                _activeCustomVariant.value = null
            }
            repository.deleteVariant(variantId)
        }
    }

    fun includeExternalClient(
        clientName: String,
        clientType: String,
        endpointUri: String,
        baseVariantId: String,
        requiresStrictBase: Boolean
    ) {
        if (clientName.isBlank()) return
        viewModelScope.launch {
            val isCompatibleNow = _selectedBaseVariantId.value == baseVariantId &&
                (!requiresStrictBase || _activeCustomVariant.value == null)
            val initialStatus = if (isCompatibleNow) "CONNECTED" else "DISCONNECTED"
            val result = repository.registerClientNode(
                clientName = clientName,
                clientType = clientType,
                endpointUri = endpointUri.ifBlank { "wss://relay.nexustcg.io/${baseVariantId.lowercase()}" },
                baseVariantId = baseVariantId,
                requiresStrictBase = requiresStrictBase,
                connectionStatus = initialStatus,
                latencyMs = (14..36).random().toLong(),
                maxDigitPrecision = if (BuiltInTcgCatalog.supports21DigitRegisters(baseVariantId)) 21L else 10L
            )
            result.onSuccess {
                _statusBanner.value = if (isCompatibleNow) {
                    "Included '$clientName' & auto-connected on Base Variant $baseVariantId"
                } else {
                    "Included '$clientName' (Requires Base Variant $baseVariantId to connect)"
                }
            }
        }
    }

    fun connectClientNode(client: ClientNode, syncToClientBaseVariant: Boolean) {
        viewModelScope.launch {
            val targetBaseSpec: BaseVariantSpec = BuiltInTcgCatalog.findBaseSpec(client.baseVariantId)
            _activeClientNode.value = client
            if (syncToClientBaseVariant) {
                _selectedBaseVariantId.value = client.baseVariantId
                if (client.requiresStrictBase) {
                    _activeCustomVariant.value = null
                }
                refreshHandForVariant(client.baseVariantId)
            }

            val currentBaseId = _selectedBaseVariantId.value
            val customOverride = _activeCustomVariant.value
            val isBaseStandard = customOverride == null || customOverride.isBaseStandard
            val activeHash = customOverride?.protocolHash
                ?: BuiltInTcgCatalog.findBaseSpec(currentBaseId).protocolHash

            val isCompatible = currentBaseId == client.baseVariantId &&
                (!client.requiresStrictBase || isBaseStandard)

            val newStatus = if (isCompatible) "CONNECTED" else "MISMATCH_BLOCKED"
            val measuredLatency = (12..34).random().toLong()

            repository.updateClientConnectionState(
                clientId = client.id,
                newStatus = newStatus,
                latencyMs = measuredLatency
            )

            val uiProfile = BuiltInTcgCatalog.systemUiProfileFor(client.baseVariantId, client)
            val diagnosticMessage = if (isCompatible) {
                when (client.baseVariantId) {
                    "HYBRID_FORGE_EDOPRO_MIX" -> {
                        val mods = BigInt21Engine.extractMixedClientModules(client.customCodeVarExpr21)
                        "UI SWITCHED TO ${uiProfile.systemTitle.uppercase()}: Connected to Extensible Mixed Client '${client.clientName}' (${mods.size} Modules: ${mods.joinToString(" + ")})."
                    }
                    "EDOPRO_CUSTOM_FORK", "FORGE_CUSTOM_CLONE" ->
                        "UI SWITCHED TO ${uiProfile.systemTitle.uppercase()}: Connected to '${client.clientName}' with 21-Digit Variable/Function Register Sandbox (${client.customFuncName} = ${client.customFuncResult21})."
                    else ->
                        "UI SWITCHED TO ${uiProfile.systemTitle.uppercase()}: Connected to '${client.clientName}' using ${targetBaseSpec.name} (${targetBaseSpec.protocolHash})."
                }
            } else if (currentBaseId != client.baseVariantId) {
                "PROTOCOL MISMATCH: '${client.clientName}' requires ${client.baseVariantId}, but engine is running $currentBaseId. Tap 'Switch UI to Client System' to align."
            } else {
                "STRICT BASE LOCK: Official '${client.clientName}' rejects custom override '${customOverride?.name}'. Switch to the Custom Project Clone or Mixed Client, or lock Base Standard."
            }

            _lastHandshakeDiagnostic.value = HandshakeDiagnostic(
                clientName = client.clientName,
                clientBaseVariantId = client.baseVariantId,
                activeBaseVariantId = currentBaseId,
                isActiveBaseStandard = isBaseStandard,
                requiresStrictBase = client.requiresStrictBase,
                isCompatible = isCompatible,
                expectedHash = targetBaseSpec.protocolHash,
                activeHash = activeHash,
                message = diagnosticMessage
            )
            _statusBanner.value = diagnosticMessage
        }
    }

    fun disconnectClientNode(client: ClientNode) {
        viewModelScope.launch {
            repository.updateClientConnectionState(
                clientId = client.id,
                newStatus = "DISCONNECTED",
                latencyMs = client.latencyMs
            )
            _statusBanner.value = "Disconnected '${client.clientName}'"
        }
    }

    fun deleteClientNode(clientId: String) {
        val existingClients = (clientsState.value as? UiState.Success)?.data.orEmpty()
        val targetClient = existingClients.firstOrNull { it.id == clientId }
        if (targetClient != null && BuiltInTcgCatalog.isLockedGitHubBaseClientNode(targetClient)) {
            val exactRepo = BuiltInTcgCatalog.exactGitHubRepoUrl(targetClient.baseVariantId)
            _statusBanner.value = "LOCKED TO GITHUB SOURCE ($exactRepo): Base client '${targetClient.clientName}' cannot be deleted or modified by users or the agent."
            return
        }
        viewModelScope.launch {
            repository.deleteClientNode(clientId).onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Cannot delete locked Base Client."
            }
        }
    }

    fun createCustomDeckAndCard(
        deckName: String,
        baseVariantId: String,
        strategyTag: String,
        customCardName: String,
        customCardCost: Long,
        customCardAttack: Long,
        customCardDefense: Long,
        customCardAbility: String,
        customAttack21: String = customCardAttack.toString(),
        customDefense21: String = customCardDefense.toString(),
        customVarInput21: String = "999999999999999999999",
        customCodeString21: String = BigInt21Engine.DEFAULT_CODE_STRING_21,
        customCodeVarExpr21: String = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
    ) {
        if (deckName.isBlank() || customCardName.isBlank()) return
        viewModelScope.launch {
            val templateIds = BuiltInTcgCatalog.cardsForVariant(baseVariantId).map { it.id }
            val result = repository.createDeck(
                name = deckName,
                baseVariantId = baseVariantId,
                strategyTag = strategyTag,
                cardIds = templateIds,
                customCardName = customCardName,
                customCardCost = customCardCost,
                customCardAttack = customCardAttack,
                customCardDefense = customCardDefense,
                customCardAbility = customCardAbility.ifBlank { "ocgcore 21-digit Lua 5.3 effect triggered on summon." },
                customAttack21 = customAttack21,
                customDefense21 = customDefense21,
                customVarInput21 = customVarInput21,
                customCodeString21 = customCodeString21,
                customCodeVarExpr21 = customCodeVarExpr21
            )
            result.onSuccess { newDeckId ->
                refreshHandForVariant(_selectedBaseVariantId.value)
                _statusBanner.value = "Forged deck '$deckName' with signature card '$customCardName' (Code: c$customCodeString21, ATK: $customAttack21)"
                if (DiscordApiService.bridgeConfig.value.autoBroadcastDeckOnForge) {
                    val forgedDeck = CustomDeck(
                        id = newDeckId,
                        userId = currentUserId,
                        name = deckName,
                        baseVariantId = baseVariantId,
                        strategyTag = strategyTag,
                        cardIds = templateIds,
                        customCardName = customCardName,
                        customCardCost = customCardCost,
                        customCardAttack = customCardAttack,
                        customCardDefense = customCardDefense,
                        customCardAbility = customCardAbility.ifBlank { "ocgcore 21-digit Lua 5.3 effect triggered on summon." },
                        customAttack21 = customAttack21,
                        customDefense21 = customDefense21,
                        customVarInput21 = customVarInput21,
                        customCodeString21 = customCodeString21,
                        customCodeVarExpr21 = customCodeVarExpr21
                    )
                    broadcastDeckSummaryToDiscord(forgedDeck, updateBannerOnUnconfigured = false)
                }
            }
        }
    }

    fun deleteDeck(deckId: String) {
        viewModelScope.launch {
            repository.deleteDeck(deckId)
        }
    }

    // --- Tabletop Arena Match Operations ---

    fun selectActiveMatch(matchId: String) {
        _activeMatchId.value = matchId
        val matches = (matchesState.value as? UiState.Success)?.data.orEmpty()
        val match = matches.firstOrNull { it.id == matchId } ?: return
        if (_selectedBaseVariantId.value != match.baseVariantId) {
            _selectedBaseVariantId.value = match.baseVariantId
            _activeCustomVariant.value = null
            refreshHandForVariant(match.baseVariantId)
            _statusBanner.value = "Synced engine to match's Base Variant: ${match.baseVariantName}"
        }
    }

    fun launchMultiClientMatch(title: String) {
        viewModelScope.launch {
            val baseId = _selectedBaseVariantId.value
            refreshHandForVariant(baseId)

            val allClients = (clientsState.value as? UiState.Success)?.data.orEmpty()
            val compatibleConnectedNames = allClients
                .filter { it.baseVariantId == baseId && it.connectionStatus == "CONNECTED" }
                .map { it.clientName }

            val resolvedTitle = title.ifBlank { "${BuiltInTcgCatalog.findBaseSpec(baseId).name} Duel Table" }
            val result = repository.createMatchSession(
                hostName = currentUserName,
                title = resolvedTitle,
                baseVariantId = baseId,
                connectedClients = compatibleConnectedNames
            )
            result.onSuccess { newId ->
                _activeMatchId.value = newId
                _statusBanner.value = "Launched Multi-Client Arena on $baseId"
                eventLogService?.appendGameEvent(
                    matchId = newId,
                    actorLabel = currentUserName,
                    eventType = PersistentGameEventType.SYSTEM_SYNC,
                    summary = "Match started: $resolvedTitle",
                    detail = "Ruleset: $baseId • Starting LP: ${BuiltInTcgCatalog.findBaseSpec(baseId).startingLife}",
                    turnNumber = 1L,
                    activePhase = "MAIN"
                )
                if (DiscordApiService.bridgeConfig.value.autoBroadcastGameStart) {
                    val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseId)
                    val matchingDeck = (decksState.value as? UiState.Success)?.data
                        ?.firstOrNull { it.baseVariantId == baseId }
                    val launchedMatch = MatchSession(
                        id = newId,
                        hostUserId = currentUserId,
                        hostName = currentUserName,
                        title = resolvedTitle,
                        baseVariantId = baseId,
                        baseVariantName = baseSpec.name,
                        connectedClients = compatibleConnectedNames.ifEmpty { listOf(currentUserName) },
                        hostLife = baseSpec.startingLife,
                        opponentLife = baseSpec.startingLife,
                        resourceName = baseSpec.resourceName
                    )
                    broadcastGameStartToDiscord(
                        match = launchedMatch,
                        deck = matchingDeck,
                        updateBannerOnUnconfigured = false
                    )
                }
            }
        }
    }

    fun bridgeConnectedClientsIntoMatch(match: MatchSession) {
        if (match.status == "FINISHED") return
        viewModelScope.launch {
            val allClients = (clientsState.value as? UiState.Success)?.data.orEmpty()
            val matchingClients = allClients.filter { it.baseVariantId == match.baseVariantId }
            matchingClients.forEach { client ->
                if (client.connectionStatus != "CONNECTED") {
                    repository.updateClientConnectionState(client.id, "CONNECTED", 16L)
                }
            }
            val names = (match.connectedClients + matchingClients.map { it.clientName } + currentUserName)
                .distinct()
                .take(10)
            val newLogs = (match.actionLog + "CTOS_JOIN_GAME: Bridged ${names.size} clients on ${match.baseVariantId}").takeLast(20)
            repository.updateMatchState(
                matchId = match.id,
                connectedClients = names,
                turnNumber = match.turnNumber,
                activePhase = match.activePhase,
                hostLife = match.hostLife,
                opponentLife = match.opponentLife,
                hostResources = match.hostResources,
                hostMaxResources = match.hostMaxResources,
                opponentResources = match.opponentResources,
                hostBoardCards = match.hostBoardCards,
                opponentBoardCards = match.opponentBoardCards,
                actionLog = newLogs,
                status = if (match.status == "OPEN_LOBBY") "ACTIVE" else match.status,
                winnerName = match.winnerName
            )
            _statusBanner.value = "Bridged ${names.size} clients into '${match.title}' via ${match.baseVariantId}"
        }
    }

    fun drawCardToHand(baseVariantId: String, match: MatchSession? = null) {
        val pool = BuiltInTcgCatalog.cardsForVariant(baseVariantId)
        val drawn = pool.random()
        _playerHand.value = (_playerHand.value + drawn).take(8)
        telemetryTracker.recordCardDrawn(drawn, match?.id ?: "local_tabletop")
        _statusBanner.value = "MSG_DRAW: Drew '${drawn.name}' (${drawn.passcode})"
        if (match != null && match.status != "FINISHED") {
            viewModelScope.launch {
                val drawLog = "MSG_DRAW [Turn ${match.turnNumber} • ${match.activePhase}]: $currentUserName drew '${drawn.name}' (c${drawn.passcode})"
                repository.updateMatchState(
                    matchId = match.id,
                    connectedClients = match.connectedClients,
                    turnNumber = match.turnNumber,
                    activePhase = match.activePhase,
                    hostLife = match.hostLife,
                    opponentLife = match.opponentLife,
                    hostResources = match.hostResources,
                    hostMaxResources = match.hostMaxResources,
                    opponentResources = match.opponentResources,
                    hostBoardCards = match.hostBoardCards,
                    opponentBoardCards = match.opponentBoardCards,
                    actionLog = (match.actionLog + drawLog).takeLast(20),
                    status = match.status,
                    winnerName = match.winnerName
                )
            }
        }
    }

    fun changeMatchPhase(match: MatchSession, newPhaseCode: String) {
        if (match.status == "FINISHED") return
        val cleanPhase = newPhaseCode.trim().uppercase().take(12)
        if (cleanPhase.isBlank()) return
        gameEngine.syncFromMatchSession(match, _playerHand.value)
        val targetTurnPhase = TurnPhase.fromRawPhaseCode(cleanPhase)
        val stepResult = gameEngine.transitionToPhase(targetTurnPhase, advanceTurnOnWrap = false)
        if (stepResult.drawnCards.isNotEmpty()) {
            _playerHand.value = stepResult.updatedState.handCards
        }
        val firestorePhase = when (targetTurnPhase) {
            TurnPhase.DRAW, TurnPhase.STANDBY -> "DRAW"
            TurnPhase.MAIN -> "MAIN"
            TurnPhase.COMBAT -> "COMBAT"
            TurnPhase.END -> "END"
        }
        viewModelScope.launch {
            val phaseLog = "MSG_NEW_PHASE [Turn ${match.turnNumber}]: $currentUserName shifted phase ${match.activePhase} -> $cleanPhase (${targetTurnPhase.displayName})"
            val mergedLogs = (match.actionLog + listOf(phaseLog) + stepResult.newLogs.drop(1)).takeLast(20)
            repository.updateMatchState(
                matchId = match.id,
                connectedClients = match.connectedClients,
                turnNumber = stepResult.updatedState.turnNumber,
                activePhase = firestorePhase,
                hostLife = stepResult.updatedState.hostLife,
                opponentLife = stepResult.updatedState.opponentLife,
                hostResources = stepResult.updatedState.hostResources,
                hostMaxResources = stepResult.updatedState.hostMaxResources,
                opponentResources = stepResult.updatedState.opponentResources,
                hostBoardCards = stepResult.updatedState.hostFieldUnits.map { it.toBoardDescriptor() },
                opponentBoardCards = stepResult.updatedState.opponentFieldUnits.map { it.toBoardDescriptor() },
                actionLog = mergedLogs,
                status = if (stepResult.updatedState.status == "FINISHED") "FINISHED" else if (match.status == "OPEN_LOBBY") "ACTIVE" else match.status,
                winnerName = stepResult.updatedState.winnerName
            )
            eventLogService?.appendGameEvent(
                matchId = match.id,
                actorLabel = currentUserName,
                eventType = PersistentGameEventType.PHASE_CHANGED,
                summary = "Phase changed to ${targetTurnPhase.displayName} ($cleanPhase)",
                detail = "${match.activePhase} -> $cleanPhase • Turn ${stepResult.updatedState.turnNumber}",
                turnNumber = stepResult.updatedState.turnNumber,
                activePhase = firestorePhase
            )
            _statusBanner.value = "GameEngine Phase -> ${targetTurnPhase.displayName} ($cleanPhase • Turn ${stepResult.updatedState.turnNumber})"
        }
    }

    /**
     * Advances the core [GameEngine] to the next sequential phase in the 5-phase turn cycle
     * (`DRAW -> STANDBY -> MAIN -> COMBAT -> END -> Next Turn DRAW`) and syncs with Firestore.
     */
    fun stepGameEnginePhase(match: MatchSession? = null) {
        if (match != null) {
            gameEngine.syncFromMatchSession(match, _playerHand.value)
        }
        val step = gameEngine.advancePhase()
        if (step.drawnCards.isNotEmpty()) {
            _playerHand.value = step.updatedState.handCards
        }
        _statusBanner.value = step.latestLogLine
        if (match != null && match.status != "FINISHED") {
            val firestorePhase = when (step.updatedState.currentPhase) {
                TurnPhase.DRAW, TurnPhase.STANDBY -> "DRAW"
                TurnPhase.MAIN -> "MAIN"
                TurnPhase.COMBAT -> "COMBAT"
                TurnPhase.END -> "END"
            }
            viewModelScope.launch {
                repository.updateMatchState(
                    matchId = match.id,
                    connectedClients = match.connectedClients,
                    turnNumber = step.updatedState.turnNumber,
                    activePhase = firestorePhase,
                    hostLife = step.updatedState.hostLife,
                    opponentLife = step.updatedState.opponentLife,
                    hostResources = step.updatedState.hostResources,
                    hostMaxResources = step.updatedState.hostMaxResources,
                    opponentResources = step.updatedState.opponentResources,
                    hostBoardCards = step.updatedState.hostFieldUnits.map { it.toBoardDescriptor() },
                    opponentBoardCards = step.updatedState.opponentFieldUnits.map { it.toBoardDescriptor() },
                    actionLog = (match.actionLog + step.newLogs).takeLast(20),
                    status = step.updatedState.status,
                    winnerName = step.updatedState.winnerName
                )
            }
        }
    }

    /**
     * Jumps the [GameEngine] directly to a canonical [TurnPhase] (`DRAW`, `STANDBY`, `MAIN`, `COMBAT`, `END`).
     */
    fun setGameEnginePhase(targetPhase: TurnPhase, match: MatchSession? = null) {
        if (match != null) {
            changeMatchPhase(match, targetPhase.code)
        } else {
            val step = gameEngine.transitionToPhase(targetPhase, advanceTurnOnWrap = false)
            if (step.drawnCards.isNotEmpty()) {
                _playerHand.value = step.updatedState.handCards
            }
            _statusBanner.value = step.latestLogLine
        }
    }

    /**
     * Pushes a card effect onto the [GameEngine]'s LIFO Chain / Stack and broadcasts the
     * Chain Link / Stack declaration to the active match log.
     */
    fun queueCardEffectOnStack(
        card: CardTemplate,
        overrideType: CardEffectType? = null,
        match: MatchSession? = null
    ) {
        if (match != null) {
            gameEngine.syncFromMatchSession(match, _playerHand.value)
        }
        val previousLink = gameEngine.state.value.effectStack.lastOrNull()
        val step = gameEngine.queueCardEffect(
            card = card,
            overrideEffectType = overrideType
        )
        val queued = step.updatedState.effectStack.lastOrNull()
        val matchKey = match?.id ?: "local_tabletop"
        telemetryTracker.recordCardActivated(card, matchId = matchKey)
        if (previousLink != null && queued != null) {
            val interactionKind = if (queued.effectType == CardEffectType.NEGATE_AND_COUNTER) {
                CardInteractionType.COUNTER_NEGATED
            } else {
                CardInteractionType.CHAIN_RESPONSE
            }
            telemetryTracker.recordCardInteraction(
                type = interactionKind,
                source = card,
                target = previousLink.sourceCard,
                phase = step.updatedState.currentPhase.code,
                baseVariantId = step.updatedState.baseVariantId,
                sampleLogLine = step.latestLogLine
            )
        }
        _statusBanner.value = step.latestLogLine
        if (match != null && match.status != "FINISHED") {
            recordMatchActionLog(match, step.latestLogLine)
        }
    }

    /**
     * Resolves the top-most card effect on the [GameEngine] LIFO Chain / Stack and synchronizes
     * resulting LP/Life, stat buffs, card draws, mana ramp, or unit removals with Firestore.
     */
    fun resolveNextStackEffect(match: MatchSession? = null) {
        val topBefore = gameEngine.state.value.effectStack.lastOrNull()
        val oppBefore = gameEngine.state.value.opponentFieldUnits.firstOrNull()
        val step = gameEngine.resolveNextEffect()
        val matchKey = match?.id ?: "local_tabletop"
        if (topBefore != null) {
            if (topBefore.effectType == CardEffectType.DIRECT_DAMAGE_BURN) {
                telemetryTracker.recordCardActivated(topBefore.sourceCard, damageDealt = topBefore.effectValue, matchId = matchKey)
            } else if (topBefore.effectType == CardEffectType.TARGET_DESTROY_REMOVAL && oppBefore != null) {
                telemetryTracker.recordCardDestroyed(oppBefore, destroyedBy = topBefore.sourceCard, phase = step.updatedState.currentPhase.code)
            }
        }
        step.drawnCards.forEach { telemetryTracker.recordCardDrawn(it, matchKey) }
        if (step.updatedState.status == "FINISHED") {
            telemetryTracker.recordMatchOutcome(
                hostWon = step.updatedState.opponentLife <= 0L,
                matchId = matchKey
            )
        }
        if (step.drawnCards.isNotEmpty()) {
            _playerHand.value = step.updatedState.handCards
        }
        _statusBanner.value = step.latestLogLine
        if (match != null && match.status != "FINISHED") {
            viewModelScope.launch {
                repository.updateMatchState(
                    matchId = match.id,
                    connectedClients = match.connectedClients,
                    turnNumber = step.updatedState.turnNumber,
                    activePhase = match.activePhase,
                    hostLife = step.updatedState.hostLife,
                    opponentLife = step.updatedState.opponentLife,
                    hostResources = step.updatedState.hostResources,
                    hostMaxResources = step.updatedState.hostMaxResources,
                    opponentResources = step.updatedState.opponentResources,
                    hostBoardCards = step.updatedState.hostFieldUnits.map { it.toBoardDescriptor() },
                    opponentBoardCards = step.updatedState.opponentFieldUnits.map { it.toBoardDescriptor() },
                    actionLog = (match.actionLog + step.newLogs).takeLast(20),
                    status = step.updatedState.status,
                    winnerName = step.updatedState.winnerName
                )
            }
        }
    }

    /**
     * Resolves all queued effects on the [GameEngine] LIFO Chain / Stack and syncs the final
     * board state and action log with Firestore.
     */
    fun resolveFullEffectStack(match: MatchSession? = null) {
        val stackBefore = gameEngine.state.value.effectStack.toList()
        val step = gameEngine.resolveAllEffects()
        val matchKey = match?.id ?: "local_tabletop"
        stackBefore.filter { it.effectType == CardEffectType.DIRECT_DAMAGE_BURN }.forEach { item ->
            telemetryTracker.recordCardActivated(item.sourceCard, damageDealt = item.effectValue, matchId = matchKey)
        }
        step.drawnCards.forEach { telemetryTracker.recordCardDrawn(it, matchKey) }
        if (step.updatedState.status == "FINISHED") {
            telemetryTracker.recordMatchOutcome(
                hostWon = step.updatedState.opponentLife <= 0L,
                matchId = matchKey
            )
        }
        if (step.drawnCards.isNotEmpty()) {
            _playerHand.value = step.updatedState.handCards
        }
        _statusBanner.value = step.latestLogLine
        if (match != null && match.status != "FINISHED") {
            viewModelScope.launch {
                repository.updateMatchState(
                    matchId = match.id,
                    connectedClients = match.connectedClients,
                    turnNumber = step.updatedState.turnNumber,
                    activePhase = match.activePhase,
                    hostLife = step.updatedState.hostLife,
                    opponentLife = step.updatedState.opponentLife,
                    hostResources = step.updatedState.hostResources,
                    hostMaxResources = step.updatedState.hostMaxResources,
                    opponentResources = step.updatedState.opponentResources,
                    hostBoardCards = step.updatedState.hostFieldUnits.map { it.toBoardDescriptor() },
                    opponentBoardCards = step.updatedState.opponentFieldUnits.map { it.toBoardDescriptor() },
                    actionLog = (match.actionLog + step.newLogs).takeLast(20),
                    status = step.updatedState.status,
                    winnerName = step.updatedState.winnerName
                )
                if (step.updatedState.status == "FINISHED" &&
                    step.updatedState.winnerName.isNotBlank() &&
                    DiscordApiService.bridgeConfig.value.autoBroadcastMatchWinner
                ) {
                    broadcastMatchWinnerToDiscord(
                        match = match.copy(
                            turnNumber = step.updatedState.turnNumber,
                            hostLife = step.updatedState.hostLife,
                            opponentLife = step.updatedState.opponentLife,
                            status = "FINISHED",
                            winnerName = step.updatedState.winnerName
                        ),
                        winnerNameOverride = step.updatedState.winnerName,
                        updateBannerOnUnconfigured = false
                    )
                }
            }
        }
    }

    fun recordMatchActionLog(match: MatchSession, logLine: String) {
        if (logLine.isBlank()) return
        viewModelScope.launch {
            repository.updateMatchState(
                matchId = match.id,
                connectedClients = match.connectedClients,
                turnNumber = match.turnNumber,
                activePhase = match.activePhase,
                hostLife = match.hostLife,
                opponentLife = match.opponentLife,
                hostResources = match.hostResources,
                hostMaxResources = match.hostMaxResources,
                opponentResources = match.opponentResources,
                hostBoardCards = match.hostBoardCards,
                opponentBoardCards = match.opponentBoardCards,
                actionLog = (match.actionLog + logLine.take(180)).takeLast(20),
                status = match.status,
                winnerName = match.winnerName
            )
        }
    }

    /**
     * Dynamically adjusts available Energy, Mana, or Action Points on the active ruleset's
     * visual resource counter and synchronizes with Firestore if a match is active.
     */
    fun adjustMatchOrEngineResources(delta: Long, match: MatchSession? = null) {
        val baseId = match?.baseVariantId ?: _selectedBaseVariantId.value
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseId)
        val ceiling = _activeCustomVariant.value
            ?.takeIf { it.baseVariantId == baseId }
            ?.maxResourcePool
            ?.coerceAtLeast(1L)
            ?: baseSpec.maxResourcePool.coerceAtLeast(1L)

        val step = gameEngine.adjustPlayerResources(delta = delta, maxCeiling = ceiling)
        _statusBanner.value = step.latestLogLine

        if (match != null && match.status != "FINISHED") {
            val nextMax = match.hostMaxResources.coerceIn(1L, ceiling)
            val nextAvail = (match.hostResources + delta).coerceIn(0L, nextMax)
            viewModelScope.launch {
                repository.updateMatchState(
                    matchId = match.id,
                    connectedClients = match.connectedClients,
                    turnNumber = match.turnNumber,
                    activePhase = match.activePhase,
                    hostLife = match.hostLife,
                    opponentLife = match.opponentLife,
                    hostResources = nextAvail,
                    hostMaxResources = nextMax,
                    opponentResources = match.opponentResources,
                    hostBoardCards = match.hostBoardCards,
                    opponentBoardCards = match.opponentBoardCards,
                    actionLog = (match.actionLog + step.latestLogLine).takeLast(20),
                    status = match.status,
                    winnerName = match.winnerName
                )
            }
        }
    }

    /**
     * Sets the exact available Energy, Mana, or Action Points when the player taps a pip on
     * the visual resource counter bar.
     */
    fun setMatchOrEngineResources(exactAmount: Long, match: MatchSession? = null) {
        val baseId = match?.baseVariantId ?: _selectedBaseVariantId.value
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseId)
        val ceiling = _activeCustomVariant.value
            ?.takeIf { it.baseVariantId == baseId }
            ?.maxResourcePool
            ?.coerceAtLeast(1L)
            ?: baseSpec.maxResourcePool.coerceAtLeast(1L)

        val step = gameEngine.setPlayerResources(exactAmount = exactAmount, maxCeiling = ceiling)
        _statusBanner.value = step.latestLogLine

        if (match != null && match.status != "FINISHED") {
            val nextMax = match.hostMaxResources.coerceIn(1L, ceiling)
            val nextAvail = exactAmount.coerceIn(0L, nextMax)
            viewModelScope.launch {
                repository.updateMatchState(
                    matchId = match.id,
                    connectedClients = match.connectedClients,
                    turnNumber = match.turnNumber,
                    activePhase = match.activePhase,
                    hostLife = match.hostLife,
                    opponentLife = match.opponentLife,
                    hostResources = nextAvail,
                    hostMaxResources = nextMax,
                    opponentResources = match.opponentResources,
                    hostBoardCards = match.hostBoardCards,
                    opponentBoardCards = match.opponentBoardCards,
                    actionLog = (match.actionLog + step.latestLogLine).takeLast(20),
                    status = match.status,
                    winnerName = match.winnerName
                )
            }
        }
    }

    /**
     * Refills Player 1's available resource pool to maximum unlocked capacity (Untap / Recharge / Reset AP).
     */
    fun refillMatchOrEngineResources(match: MatchSession? = null) {
        val step = gameEngine.refillPlayerResources()
        _statusBanner.value = step.latestLogLine

        if (match != null && match.status != "FINISHED") {
            val refilled = match.hostMaxResources.coerceAtLeast(1L)
            viewModelScope.launch {
                repository.updateMatchState(
                    matchId = match.id,
                    connectedClients = match.connectedClients,
                    turnNumber = match.turnNumber,
                    activePhase = match.activePhase,
                    hostLife = match.hostLife,
                    opponentLife = match.opponentLife,
                    hostResources = refilled,
                    hostMaxResources = refilled,
                    opponentResources = match.opponentResources,
                    hostBoardCards = match.hostBoardCards,
                    opponentBoardCards = match.opponentBoardCards,
                    actionLog = (match.actionLog + step.latestLogLine).takeLast(20),
                    status = match.status,
                    winnerName = match.winnerName
                )
            }
        }
    }

    /**
     * Expands Player 1's unlocked resource pool capacity by +1 up to the loaded ruleset's ceiling.
     */
    fun expandMatchOrEngineMaxResources(match: MatchSession? = null) {
        val baseId = match?.baseVariantId ?: _selectedBaseVariantId.value
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseId)
        val ceiling = _activeCustomVariant.value
            ?.takeIf { it.baseVariantId == baseId }
            ?.maxResourcePool
            ?.coerceAtLeast(1L)
            ?: baseSpec.maxResourcePool.coerceAtLeast(1L)

        val step = gameEngine.expandMaxResourcePool(maxCeiling = ceiling)
        _statusBanner.value = step.latestLogLine

        if (match != null && match.status != "FINISHED") {
            val nextMax = (match.hostMaxResources + 1L).coerceAtMost(ceiling)
            val nextAvail = (match.hostResources + 1L).coerceAtMost(nextMax)
            viewModelScope.launch {
                repository.updateMatchState(
                    matchId = match.id,
                    connectedClients = match.connectedClients,
                    turnNumber = match.turnNumber,
                    activePhase = match.activePhase,
                    hostLife = match.hostLife,
                    opponentLife = match.opponentLife,
                    hostResources = nextAvail,
                    hostMaxResources = nextMax,
                    opponentResources = match.opponentResources,
                    hostBoardCards = match.hostBoardCards,
                    opponentBoardCards = match.opponentBoardCards,
                    actionLog = (match.actionLog + step.latestLogLine).takeLast(20),
                    status = match.status,
                    winnerName = match.winnerName
                )
            }
        }
    }

    fun deployCardToBattlefield(match: MatchSession, card: CardTemplate) {
        if (match.status == "FINISHED") return
        if (match.hostResources < card.cost) {
            _statusBanner.value = "Not enough ${match.resourceName}! Need ${card.cost}, have ${match.hostResources}."
            return
        }
        viewModelScope.launch {
            val updatedHand = _playerHand.value.toMutableList().also { list ->
                val idx = list.indexOfFirst { it.id == card.id && it.name == card.name }
                if (idx >= 0) list.removeAt(idx)
            }
            _playerHand.value = updatedHand

            telemetryTracker.recordCardPlayed(card, match.turnNumber, match.id)
            val existingAllies = match.hostBoardCards.map { CardTemplate.fromBoardDescriptor(it, match.baseVariantId) }
            existingAllies.lastOrNull()?.let { ally ->
                if (ally.id != card.id) {
                    telemetryTracker.recordCardInteraction(
                        type = CardInteractionType.COMBO_SYNERGY,
                        source = card,
                        target = ally,
                        phase = match.activePhase,
                        baseVariantId = match.baseVariantId,
                        sampleLogLine = "${card.name} (${card.cardOrigin.badgeLabel}) deployed alongside ${ally.name} (${ally.cardOrigin.badgeLabel})."
                    )
                }
            }

            val newResources = (match.hostResources - card.cost).coerceAtLeast(0L)
            val newBoard = (match.hostBoardCards + card.toBoardDescriptor()).take(12)
            val uiProfile = BuiltInTcgCatalog.systemUiProfileFor(match.baseVariantId)
            val targetFieldSlot = uiProfile.fieldSlotCodes.getOrElse(match.hostBoardCards.size % uiProfile.fieldSlotCodes.size) { "MMZ-1" }
            val logEntry = "MOVE_HISTORY [Turn ${match.turnNumber} • ${match.activePhase}]: Player 1 ($currentUserName) moved '${card.name}' to Field [$targetFieldSlot] (${card.attack21}/${card.defense21})"
            val newLogs = (match.actionLog + logEntry).takeLast(20)

            repository.updateMatchState(
                matchId = match.id,
                connectedClients = match.connectedClients,
                turnNumber = match.turnNumber,
                activePhase = match.activePhase,
                hostLife = match.hostLife,
                opponentLife = match.opponentLife,
                hostResources = newResources,
                hostMaxResources = match.hostMaxResources,
                opponentResources = match.opponentResources,
                hostBoardCards = newBoard,
                opponentBoardCards = match.opponentBoardCards,
                actionLog = newLogs,
                status = "ACTIVE",
                winnerName = match.winnerName
            )
            eventLogService?.logCardPlayed(
                matchId = match.id,
                playerLabel = "Player 1",
                cardName = card.name,
                zoneLabel = targetFieldSlot,
                turnNumber = match.turnNumber,
                activePhase = match.activePhase,
                detail = "$currentUserName • ATK ${card.attack} / DEF ${card.defense}"
            )
        }
    }

    fun executeCombatAndAdvanceTurn(match: MatchSession) {
        if (match.status == "FINISHED") return
        viewModelScope.launch {
            val baseSpec = BuiltInTcgCatalog.findBaseSpec(match.baseVariantId)
            val isEdoPro = BuiltInTcgCatalog.isEdoProVariant(match.baseVariantId)
            val hostUnits = match.hostBoardCards.map { CardTemplate.fromBoardDescriptor(it, match.baseVariantId) }
            val oppUnits = match.opponentBoardCards.map { CardTemplate.fromBoardDescriptor(it, match.baseVariantId) }

            val defaultAttack = if (isEdoPro) 1800 else 2
            val totalHostAttack = hostUnits.sumOf { it.attack }.coerceAtLeast(defaultAttack)
            val oppMitigation = when (baseSpec.combatRule) {
                "ATK_DEF_BATTLE" -> oppUnits.maxOfOrNull { it.attack / 2 } ?: 0
                "BLOCKER_ASSIGN" -> oppUnits.sumOf { it.defense / 2 }
                else -> 0
            }
            val minDamage = if (isEdoPro) 500 else 1
            val damageToOpponent = (totalHostAttack - oppMitigation).coerceAtLeast(minDamage)
            val newOpponentLife = (match.opponentLife - damageToOpponent).coerceAtLeast(0L)

            val clientPeerName = match.connectedClients.firstOrNull() ?: "ProjectIgnis EDOPro Peer"
            val updatedOppBoard = match.opponentBoardCards.toMutableList()
            if (updatedOppBoard.size < 4 && newOpponentLife > 0) {
                val aiCard = BuiltInTcgCatalog.cardsForVariant(match.baseVariantId).random()
                updatedOppBoard.add(aiCard.toBoardDescriptor())
            }
            val counterDamage = if (newOpponentLife > 0) {
                if (isEdoPro) {
                    (oppUnits.maxOfOrNull { it.attack / 2 } ?: 1500).coerceIn(800, 3000)
                } else {
                    oppUnits.sumOf { it.attack }.coerceIn(1, 6)
                }
            } else {
                0
            }
            val newHostLife = (match.hostLife - counterDamage).coerceAtLeast(0L)

            // Record real combat telemetry for active host attackers vs opponent defenders
            val leadDefender = oppUnits.firstOrNull()
            hostUnits.forEachIndexed { idx, attacker ->
                val shareDmg = if (idx == 0) damageToOpponent.toLong() else 0L
                val defeatedDef = leadDefender != null && attacker.attack >= leadDefender.attack
                val lostClash = leadDefender != null && attacker.attack < leadDefender.attack && idx == 0
                telemetryTracker.recordCardAttacked(
                    attacker = attacker,
                    damageDealt = shareDmg,
                    defender = if (idx == 0) leadDefender else null,
                    defenderDestroyed = defeatedDef,
                    attackerDestroyed = lostClash,
                    phase = "COMBAT",
                    baseVariantId = match.baseVariantId,
                    logLine = "Turn ${match.turnNumber} Combat: ${attacker.name} (${attacker.attack} ATK) clashed for $shareDmg dmg."
                )
            }

            val nextTurn = match.turnNumber + 1L
            val nextMaxRes = (match.hostMaxResources + 1L).coerceAtMost(baseSpec.maxResourcePool)

            val isFinished = newOpponentLife <= 0L || newHostLife <= 0L
            if (isFinished) {
                telemetryTracker.recordMatchOutcome(hostWon = newOpponentLife <= 0L, matchId = match.id)
            }
            val nextStatus = if (isFinished) "FINISHED" else "ACTIVE"
            val winner = when {
                newOpponentLife <= 0L -> currentUserName
                newHostLife <= 0L -> clientPeerName
                else -> ""
            }

            val combatLogs = mutableListOf<String>()
            combatLogs.add("MSG_NEW_PHASE [Turn ${match.turnNumber}]: Entered Battle Phase -> BP")
            combatLogs.add("MSG_DAMAGE [Turn ${match.turnNumber} • ${baseSpec.combatRule}]: $currentUserName inflicted $damageToOpponent LP dmg to $clientPeerName.")
            if (!isFinished) {
                combatLogs.add("MSG_DAMAGE [Turn ${match.turnNumber}]: $clientPeerName [ocgcore Sync] counter-attacked for $counterDamage LP dmg.")
                combatLogs.add("MSG_NEW_PHASE [Turn $nextTurn]: Advanced turn & shifted phase -> MAIN")
            } else {
                combatLogs.add("DUEL COMPLETE! Winner: $winner on ${match.baseVariantId}.")
            }

            val drawnCard = BuiltInTcgCatalog.cardsForVariant(match.baseVariantId).random()
            _playerHand.value = (_playerHand.value + drawnCard).take(8)
            telemetryTracker.recordCardDrawn(drawnCard, match.id)

            repository.updateMatchState(
                matchId = match.id,
                connectedClients = match.connectedClients,
                turnNumber = nextTurn,
                activePhase = if (isFinished) "END" else "MAIN",
                hostLife = newHostLife,
                opponentLife = newOpponentLife,
                hostResources = nextMaxRes,
                hostMaxResources = nextMaxRes,
                opponentResources = nextMaxRes,
                hostBoardCards = match.hostBoardCards,
                opponentBoardCards = updatedOppBoard,
                actionLog = (match.actionLog + combatLogs).takeLast(20),
                status = nextStatus,
                winnerName = winner
            )

            eventLogService?.logLifePointChange(
                matchId = match.id,
                actorLabel = "Player 1 ($currentUserName)",
                targetPlayerLabel = clientPeerName,
                delta = -damageToOpponent.toLong(),
                newLife = newOpponentLife,
                turnNumber = match.turnNumber,
                activePhase = "COMBAT"
            )
            if (counterDamage > 0) {
                eventLogService?.logLifePointChange(
                    matchId = match.id,
                    actorLabel = clientPeerName,
                    targetPlayerLabel = "Player 1",
                    delta = -counterDamage.toLong(),
                    newLife = newHostLife,
                    turnNumber = match.turnNumber,
                    activePhase = "COMBAT"
                )
            }

            if (isFinished && winner.isNotBlank() && DiscordApiService.bridgeConfig.value.autoBroadcastMatchWinner) {
                val finishedMatch = match.copy(
                    turnNumber = nextTurn,
                    activePhase = "END",
                    hostLife = newHostLife,
                    opponentLife = newOpponentLife,
                    status = "FINISHED",
                    winnerName = winner
                )
                val winningDeck = (decksState.value as? UiState.Success)?.data
                    ?.firstOrNull { it.baseVariantId == match.baseVariantId }
                broadcastMatchWinnerToDiscord(
                    match = finishedMatch,
                    winnerNameOverride = winner,
                    deck = winningDeck,
                    updateBannerOnUnconfigured = false
                )
            }
        }
    }

    fun deleteMatch(matchId: String) {
        viewModelScope.launch {
            repository.deleteMatchSession(matchId)
            if (_activeMatchId.value == matchId) {
                _activeMatchId.value = null
            }
        }
    }

    /**
     * Broadcasts the serialized `CanvasGameBoard` zone map and metadata in real time to
     * `/matches/{matchId}` in Firebase Firestore so any connected peer client immediately
     * receives and renders the updated board state.
     */
    fun broadcastCanvasBoardState(
        match: MatchSession?,
        zonePlacements: Map<String, SerializedZonePlacement>,
        eventType: String,
        eventSummary: String
    ) {
        val currentRev = (syncedBoardState.value?.revisionNumber ?: 1) + 1
        val clientLabel = _activeClientNode.value?.clientName
            ?: "$currentUserName (${_selectedBaseVariantId.value})"

        if (match == null || boardSyncService == null) {
            _localSyncedBoardFallback.value = SerializedGameBoardState(
                matchId = "local_arena",
                baseVariantId = _selectedBaseVariantId.value,
                baseVariantName = BuiltInTcgCatalog.findBaseSpec(_selectedBaseVariantId.value).name,
                revisionNumber = currentRev,
                connectedClients = listOf(clientLabel, "Peer Client #2"),
                lastBroadcastClientId = currentUserId,
                lastBroadcastClientName = clientLabel,
                lastEventType = eventType,
                lastEventSummary = eventSummary,
                zonePlacements = zonePlacements
            )
            _statusBanner.value = "Synced GameBoard state (Rev #$currentRev): $eventSummary"
            return
        }

        viewModelScope.launch {
            val manager = gameStateManager
            if (manager != null) {
                _isSyncWriteInFlight.value = true
                val txResult = manager.syncBoardStateAtomic(
                    matchId = match.id,
                    zonePlacements = zonePlacements,
                    actorSeat = _localSyncSeat.value,
                    actorLabel = clientLabel,
                    eventType = eventType,
                    eventSummary = eventSummary,
                    updatedPhase = match.activePhase,
                    updatedTurnNumber = match.turnNumber
                )
                _isSyncWriteInFlight.value = false
                txResult.onSuccess { synced ->
                    _localSyncedBoardFallback.value = synced.toSerializedGameBoardState()
                    _statusBanner.value = "Firestore Atomic Sync [Rev #${synced.revisionNumber} • ${synced.lastSyncLatencyMs}ms]: $eventSummary"
                    eventLogService?.appendGameEvent(
                        matchId = match.id,
                        actorLabel = clientLabel,
                        eventType = if (eventType.contains("DEPLOY", ignoreCase = true)) {
                            PersistentGameEventType.CARD_PLAYED
                        } else {
                            PersistentGameEventType.BOARD_MOVE
                        },
                        summary = eventSummary,
                        detail = "Firestore Sync Rev #${synced.revisionNumber}",
                        turnNumber = synced.turnNumber,
                        activePhase = synced.activePhase
                    )
                }.onFailure { err ->
                    _statusBanner.value = "Board sync error: ${err.message}"
                }
            } else {
                val result = boardSyncService.broadcastBoardState(
                    match = match,
                    zonePlacements = zonePlacements,
                    clientLabel = clientLabel,
                    eventType = eventType,
                    eventSummary = eventSummary,
                    nextRevision = currentRev
                )
                result.onSuccess { synced ->
                    _localSyncedBoardFallback.value = synced
                    _statusBanner.value = "Firestore Broadcast [Rev #${synced.revisionNumber}]: $eventSummary"
                }
            }
        }
    }

    /**
     * Switches which client seat (`CLIENT_1_HOST` or `CLIENT_2_PEER`) this local app window
     * represents in the active Firestore synchronized match.
     */
    fun setLocalSyncSeat(seat: SyncClientSeat) {
        _localSyncSeat.value = seat
        val matchId = _activeMatchId.value
        val manager = gameStateManager
        val actorLabel = _activeClientNode.value?.clientName ?: currentUserName
        if (!matchId.isNullOrBlank() && manager != null) {
            viewModelScope.launch {
                manager.attachClientToMatch(
                    matchId = matchId,
                    clientSeat = seat,
                    clientLabel = actorLabel
                ).onSuccess { synced ->
                    _statusBanner.value = "Switched to ${seat.displayLabel} on Room ${synced.matchId.take(8)} (Rev #${synced.revisionNumber})"
                }
            }
        } else {
            _statusBanner.value = "Active sync seat set to ${seat.displayLabel}"
        }
    }

    /**
     * Attaches this client to a Firestore match room by its Match ID (typically pasted by Client 2
     * from Client 1) and subscribes to real-time life point and board state updates.
     */
    fun joinSyncedMatchById(rawMatchId: String) {
        val trimmedId = rawMatchId.trim()
        if (trimmedId.isBlank()) return
        val manager = gameStateManager ?: return
        val actorLabel = _activeClientNode.value?.clientName ?: currentUserName
        _activeMatchId.value = trimmedId
        viewModelScope.launch {
            _isSyncWriteInFlight.value = true
            val seatToUse = if (realtimeGameSyncState.value.hostUserId == currentUserId) {
                SyncClientSeat.CLIENT_1_HOST
            } else {
                SyncClientSeat.CLIENT_2_PEER
            }
            _localSyncSeat.value = seatToUse
            val result = manager.attachClientToMatch(
                matchId = trimmedId,
                clientSeat = seatToUse,
                clientLabel = actorLabel
            )
            _isSyncWriteInFlight.value = false
            result.onSuccess { synced ->
                _selectedBaseVariantId.value = synced.baseVariantId
                _localSyncedBoardFallback.value = synced.toSerializedGameBoardState()
                _statusBanner.value = "Connected as ${seatToUse.displayLabel} to Firestore Room '${synced.matchTitle}' (${synced.lastSyncLatencyMs}ms)"
            }.onFailure { err ->
                _statusBanner.value = "Failed to attach to Room '$trimmedId': ${err.message}"
            }
        }
    }

    /**
     * Atomically adjusts life points (`+delta` / `-delta`) for Client 1 or Client 2 via Firestore
     * transaction, and auto-broadcasts match winner to Discord if LP reaches 0.
     */
    fun adjustSyncedLifePoints(targetSeat: SyncClientSeat, delta: Long) {
        val matchId = _activeMatchId.value
        val manager = gameStateManager
        if (matchId.isNullOrBlank() || manager == null) {
            _statusBanner.value = "Create or join a Firestore match room first to sync life points."
            return
        }
        val actorLabel = _activeClientNode.value?.clientName ?: currentUserName
        viewModelScope.launch {
            _isSyncWriteInFlight.value = true
            val result = manager.syncLifePointsDelta(
                matchId = matchId,
                targetSeat = targetSeat,
                delta = delta,
                actorSeat = _localSyncSeat.value,
                actorLabel = actorLabel
            )
            _isSyncWriteInFlight.value = false
            result.onSuccess { synced ->
                _localSyncedBoardFallback.value = synced.toSerializedGameBoardState()
                _statusBanner.value = "Firestore LP Sync [Rev #${synced.revisionNumber} • ${synced.lastSyncLatencyMs}ms]: ${synced.lastEventSummary}"
                val targetName = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
                val newLife = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) synced.hostLife else synced.opponentLife
                eventLogService?.logLifePointChange(
                    matchId = synced.matchId,
                    actorLabel = actorLabel,
                    targetPlayerLabel = targetName,
                    delta = delta,
                    newLife = newLife,
                    turnNumber = synced.turnNumber,
                    activePhase = synced.activePhase
                )
                if (synced.isMatchFinished && synced.winnerName.isNotBlank() &&
                    DiscordApiService.bridgeConfig.value.autoBroadcastMatchWinner
                ) {
                    val finishedMatch = (matchesState.value as? UiState.Success)?.data
                        ?.firstOrNull { it.id == synced.matchId }
                        ?.copy(
                            hostLife = synced.hostLife,
                            opponentLife = synced.opponentLife,
                            status = "FINISHED",
                            winnerName = synced.winnerName
                        )
                    val deck = (decksState.value as? UiState.Success)?.data
                        ?.firstOrNull { it.baseVariantId == synced.baseVariantId }
                    broadcastMatchWinnerToDiscord(
                        match = finishedMatch,
                        winnerNameOverride = synced.winnerName,
                        deck = deck,
                        updateBannerOnUnconfigured = false
                    )
                }
            }.onFailure { err ->
                _statusBanner.value = "Life Point sync failed: ${err.message}"
            }
        }
    }

    /**
     * Atomically sets exact life points for Client 1 or Client 2 via Firestore transaction.
     */
    fun setSyncedLifePointsExact(targetSeat: SyncClientSeat, exactLife: Long) {
        val matchId = _activeMatchId.value
        val manager = gameStateManager
        if (matchId.isNullOrBlank() || manager == null) {
            _statusBanner.value = "Create or join a Firestore match room first to set life points."
            return
        }
        val actorLabel = _activeClientNode.value?.clientName ?: currentUserName
        viewModelScope.launch {
            _isSyncWriteInFlight.value = true
            val result = manager.syncLifePointsExact(
                matchId = matchId,
                targetSeat = targetSeat,
                exactLife = exactLife,
                actorSeat = _localSyncSeat.value,
                actorLabel = actorLabel
            )
            _isSyncWriteInFlight.value = false
            result.onSuccess { synced ->
                _localSyncedBoardFallback.value = synced.toSerializedGameBoardState()
                _statusBanner.value = "Firestore LP Set [Rev #${synced.revisionNumber} • ${synced.lastSyncLatencyMs}ms]: ${synced.lastEventSummary}"
                val targetName = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
                eventLogService?.appendGameEvent(
                    matchId = synced.matchId,
                    actorLabel = actorLabel,
                    eventType = PersistentGameEventType.LIFE_CHANGED,
                    summary = "Life points set to $exactLife ($targetName)",
                    detail = "P1: ${synced.hostLife} | P2: ${synced.opponentLife}",
                    turnNumber = synced.turnNumber,
                    activePhase = synced.activePhase,
                    lifeDelta = 0L
                )
            }.onFailure { err ->
                _statusBanner.value = "Exact LP sync failed: ${err.message}"
            }
        }
    }

    /**
     * Deploys the next card from the loaded ruleset catalog to the active seat's next open
     * battlefield zone (`P1_MAIN_*` for Client 1, `P2_MAIN_*` for Client 2) via atomic Firestore transaction.
     */
    fun deploySyncedCardForActiveSeat() {
        val matchId = _activeMatchId.value
        val manager = gameStateManager
        if (matchId.isNullOrBlank() || manager == null) {
            _statusBanner.value = "Create or join a Firestore match room first to deploy synced cards."
            return
        }
        val syncSnapshot = realtimeGameSyncState.value
        val seat = _localSyncSeat.value
        val baseId = syncSnapshot.baseVariantId.ifBlank { _selectedBaseVariantId.value }
        val catalog = BuiltInTcgCatalog.cardsForVariant(baseId)
        val candidateZones = if (seat == SyncClientSeat.CLIENT_1_HOST) {
            listOf("P1_MAIN_0", "P1_MAIN_1", "P1_MAIN_2", "P1_MAIN_3", "P1_MAIN_4", "P1_SUP_0", "P1_SUP_1")
        } else {
            listOf("P2_MAIN_0", "P2_MAIN_1", "P2_MAIN_2", "P2_MAIN_3", "P2_MAIN_4", "P2_SUP_0", "P2_SUP_1")
        }
        val targetZone = candidateZones.firstOrNull { !syncSnapshot.zonePlacements.containsKey(it) }
            ?: candidateZones[syncSnapshot.revisionNumber % candidateZones.size]
        val card = catalog[syncSnapshot.revisionNumber % catalog.size]
        val owner = if (seat == SyncClientSeat.CLIENT_1_HOST) "PLAYER_1_HOST" else "PLAYER_2_OPPONENT"
        val placement = SerializedZonePlacement(
            zoneId = targetZone,
            owner = owner,
            cardId = "${seat.code.lowercase()}_${card.id}_${syncSnapshot.revisionNumber}",
            cardName = card.name,
            cost = card.cost,
            attack = card.attack,
            defense = card.defense,
            cardType = card.cardType,
            abilityText = card.abilityText,
            attack21 = card.attack21,
            defense21 = card.defense21,
            passcode = card.passcode,
            isDefenseOrTapped = false,
            stackCount = 1
        )
        val actorLabel = _activeClientNode.value?.clientName ?: currentUserName
        val summary = "${seat.shortLabel} deployed '${card.name}' (${card.attack}/${card.defense}) to [$targetZone]"

        viewModelScope.launch {
            _isSyncWriteInFlight.value = true
            val result = manager.syncSingleZoneMutation(
                matchId = matchId,
                zoneId = targetZone,
                placement = placement,
                actorSeat = seat,
                actorLabel = actorLabel,
                eventType = "SYNC_ZONE_DEPLOY",
                eventSummary = summary
            )
            _isSyncWriteInFlight.value = false
            result.onSuccess { synced ->
                _localSyncedBoardFallback.value = synced.toSerializedGameBoardState()
                _statusBanner.value = "Firestore Board Sync [Rev #${synced.revisionNumber} • ${synced.lastSyncLatencyMs}ms]: $summary"
                val seatPlayerLabel = if (seat == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
                eventLogService?.logCardPlayed(
                    matchId = synced.matchId,
                    playerLabel = seatPlayerLabel,
                    cardName = card.name,
                    zoneLabel = targetZone,
                    turnNumber = synced.turnNumber,
                    activePhase = synced.activePhase,
                    detail = "$actorLabel • ATK ${card.attack} / DEF ${card.defense}"
                )
            }.onFailure { err ->
                _statusBanner.value = "Board deploy sync failed: ${err.message}"
            }
        }
    }

    /**
     * Atomically toggles `ATK/UNTAPPED` <-> `DEF/TAPPED` orientation for a synced zone across Firestore.
     */
    fun toggleSyncedZoneOrientation(zoneId: String) {
        val matchId = _activeMatchId.value ?: return
        val manager = gameStateManager ?: return
        val actorLabel = _activeClientNode.value?.clientName ?: currentUserName
        viewModelScope.launch {
            _isSyncWriteInFlight.value = true
            val result = manager.toggleZoneOrientationAtomic(
                matchId = matchId,
                zoneId = zoneId,
                actorSeat = _localSyncSeat.value,
                actorLabel = actorLabel
            )
            _isSyncWriteInFlight.value = false
            result.onSuccess { synced ->
                _localSyncedBoardFallback.value = synced.toSerializedGameBoardState()
                _statusBanner.value = "Firestore Zone Rotate [Rev #${synced.revisionNumber}]: ${synced.lastEventSummary}"
            }.onFailure { err ->
                _statusBanner.value = "Zone orientation sync failed: ${err.message}"
            }
        }
    }

    /**
     * Atomically removes a card from a synced board zone across both connected clients.
     */
    fun removeSyncedZonePlacement(zoneId: String) {
        val matchId = _activeMatchId.value ?: return
        val manager = gameStateManager ?: return
        val actorLabel = _activeClientNode.value?.clientName ?: currentUserName
        val cardName = realtimeGameSyncState.value.zonePlacements[zoneId]?.cardName ?: "Card"
        val summary = "Removed '$cardName' from [$zoneId]"
        viewModelScope.launch {
            _isSyncWriteInFlight.value = true
            val result = manager.syncSingleZoneMutation(
                matchId = matchId,
                zoneId = zoneId,
                placement = null,
                actorSeat = _localSyncSeat.value,
                actorLabel = actorLabel,
                eventType = "SYNC_ZONE_REMOVE",
                eventSummary = summary
            )
            _isSyncWriteInFlight.value = false
            result.onSuccess { synced ->
                _localSyncedBoardFallback.value = synced.toSerializedGameBoardState()
                _statusBanner.value = "Firestore Zone Clear [Rev #${synced.revisionNumber}]: $summary"
            }.onFailure { err ->
                _statusBanner.value = "Zone removal sync failed: ${err.message}"
            }
        }
    }

    /**
     * Atomically resets synced life points to starting values and clears synced battlefield zones.
     */
    fun resetSyncedLifeAndBoard() {
        val matchId = _activeMatchId.value ?: return
        val manager = gameStateManager ?: return
        val actorLabel = _activeClientNode.value?.clientName ?: currentUserName
        viewModelScope.launch {
            _isSyncWriteInFlight.value = true
            val result = manager.resetLifeAndBoardAtomic(
                matchId = matchId,
                actorSeat = _localSyncSeat.value,
                actorLabel = actorLabel
            )
            _isSyncWriteInFlight.value = false
            result.onSuccess { synced ->
                _localSyncedBoardFallback.value = synced.toSerializedGameBoardState()
                _statusBanner.value = "Firestore State Reset [Rev #${synced.revisionNumber}]: ${synced.lastEventSummary}"
                eventLogService?.appendGameEvent(
                    matchId = synced.matchId,
                    actorLabel = actorLabel,
                    eventType = PersistentGameEventType.SYSTEM_SYNC,
                    summary = "Reset synced life points and cleared board",
                    detail = "Starting LP restored: ${synced.hostLife}/${synced.opponentLife}",
                    turnNumber = synced.turnNumber,
                    activePhase = synced.activePhase
                )
            }.onFailure { err ->
                _statusBanner.value = "Reset failed: ${err.message}"
            }
        }
    }

    /**
     * Persists a quick `'Player X played <cardName>'` event directly to `/matches/{matchId}/events`.
     */
    fun logQuickCardPlayedEvent(cardName: String) {
        val matchId = _activeMatchId.value ?: return
        val service = eventLogService ?: return
        val syncSnapshot = realtimeGameSyncState.value
        val seatPlayer = if (_localSyncSeat.value == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
        val cleanCard = cardName.trim().ifBlank { "Ace Construct" }
        viewModelScope.launch {
            service.logCardPlayed(
                matchId = matchId,
                playerLabel = seatPlayer,
                cardName = cleanCard,
                turnNumber = syncSnapshot.turnNumber,
                activePhase = syncSnapshot.activePhase,
                detail = "Logged by $currentUserName via Persistent Event Log"
            ).onSuccess {
                _statusBanner.value = "Persisted event: $seatPlayer played $cleanCard"
            }.onFailure { err ->
                _statusBanner.value = "Failed to persist card play event: ${err.message}"
            }
        }
    }

    /**
     * Reduces life points by [reductionAmount] on the opponent seat (or logs directly if match is finished)
     * and persists `'Life points reduced by <reductionAmount>'` to `/matches/{matchId}/events`.
     */
    fun logQuickLifeReductionEvent(reductionAmount: Long) {
        val matchId = _activeMatchId.value ?: return
        val amount = kotlin.math.abs(reductionAmount).coerceAtLeast(1L)
        val targetSeat = if (_localSyncSeat.value == SyncClientSeat.CLIENT_1_HOST) {
            SyncClientSeat.CLIENT_2_PEER
        } else {
            SyncClientSeat.CLIENT_1_HOST
        }
        val syncSnapshot = realtimeGameSyncState.value
        if (gameStateManager != null && !syncSnapshot.isMatchFinished) {
            adjustSyncedLifePoints(targetSeat = targetSeat, delta = -amount)
        } else {
            val service = eventLogService ?: return
            val targetName = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
            viewModelScope.launch {
                service.logLifePointChange(
                    matchId = matchId,
                    actorLabel = currentUserName,
                    targetPlayerLabel = targetName,
                    delta = -amount,
                    newLife = ((if (targetSeat == SyncClientSeat.CLIENT_1_HOST) syncSnapshot.hostLife else syncSnapshot.opponentLife) - amount).coerceAtLeast(0L),
                    turnNumber = syncSnapshot.turnNumber,
                    activePhase = syncSnapshot.activePhase
                )
            }
        }
    }

    /**
     * Persists a user-entered custom game event message to `/matches/{matchId}/events`.
     */
    fun logCustomGameEvent(summary: String) {
        val matchId = _activeMatchId.value ?: return
        val service = eventLogService ?: return
        val cleanSummary = summary.trim()
        if (cleanSummary.isBlank()) return
        val syncSnapshot = realtimeGameSyncState.value
        val seatPlayer = if (_localSyncSeat.value == SyncClientSeat.CLIENT_1_HOST) "Player 1" else "Player 2"
        viewModelScope.launch {
            service.appendGameEvent(
                matchId = matchId,
                actorLabel = "$seatPlayer ($currentUserName)",
                eventType = PersistentGameEventType.CUSTOM_EVENT,
                summary = cleanSummary,
                detail = "Synced via Firestore Persistent Event Log",
                turnNumber = syncSnapshot.turnNumber,
                activePhase = syncSnapshot.activePhase
            ).onSuccess {
                _statusBanner.value = "Synced event to Firestore: $cleanSummary"
            }.onFailure { err ->
                _statusBanner.value = "Failed to log event: ${err.message}"
            }
        }
    }

    /**
     * Triggers a real-time counter-move from connected Peer Client #2 and broadcasts its
     * updated serialized `CanvasGameBoard` state through Firebase Firestore (`/matches/{matchId}`).
     */
    fun triggerPeerClientBoardSyncMove(
        match: MatchSession?,
        currentPlacements: Map<String, SerializedZonePlacement>
    ) {
        val currentRev = syncedBoardState.value?.revisionNumber ?: 1
        if (match == null || boardSyncService == null) {
            val fallbackMatch = MatchSession(
                id = "local_arena",
                hostUserId = currentUserId,
                hostName = currentUserName,
                baseVariantId = _selectedBaseVariantId.value,
                baseVariantName = BuiltInTcgCatalog.findBaseSpec(_selectedBaseVariantId.value).name,
                connectedClients = listOf(currentUserName, "Peer Client #2 (${_selectedBaseVariantId.value})")
            )
            val catalog = BuiltInTcgCatalog.cardsForVariant(fallbackMatch.baseVariantId)
            val peerCard = catalog[(currentRev + 1) % catalog.size]
            val targetZone = listOf("P2_MAIN_0", "P2_MAIN_1", "P2_MAIN_2", "P2_MAIN_3", "P2_MAIN_4")
                .firstOrNull { !currentPlacements.containsKey(it) } ?: "P2_MAIN_1"
            val updated = currentPlacements.toMutableMap()
            updated[targetZone] = SerializedZonePlacement(
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
            _localSyncedBoardFallback.value = SerializedGameBoardState(
                matchId = "local_arena",
                baseVariantId = fallbackMatch.baseVariantId,
                baseVariantName = fallbackMatch.baseVariantName,
                revisionNumber = currentRev + 1,
                connectedClients = fallbackMatch.connectedClients,
                lastBroadcastClientId = "peer_client_2",
                lastBroadcastClientName = "Peer Client #2",
                lastEventType = "PEER_CARD_DEPLOY",
                lastEventSummary = summary,
                zonePlacements = updated
            )
            _statusBanner.value = "Peer Client #2 Sync [Rev #${currentRev + 1}]: $summary"
            return
        }

        viewModelScope.launch {
            val result = boardSyncService.broadcastPeerClientCounterMove(
                match = match,
                currentPlacements = currentPlacements,
                currentRevision = currentRev
            )
            result.onSuccess { synced ->
                _localSyncedBoardFallback.value = synced
                _statusBanner.value = "Peer Client #2 Broadcast [Rev #${synced.revisionNumber}]: ${synced.lastEventSummary}"
            }
        }
    }

    // --- Discord Roleplay Servers, Character Personas & Chat Review Operations ---

    private fun seedStarterDiscordRoleplayHub() {
        viewModelScope.launch {
            val mode = DiscordApiService.activeConnectionMode()

            // 1. Seed Discord Roleplay Servers (Base MR5 & 21-Digit EDOPro Custom Clone)
            val dominoServerRes = repository.createDiscordServerHub(
                guildId = "108911002200330044",
                serverName = "Domino City Duel Academy RP",
                channelId = "108911002200330099",
                channelName = "#duel-arena-rp",
                baseVariantId = "EDOPRO_BASE_MR5",
                rpLoreSetting = "Official ProjectIgnis/EDOPro MR5 Roleplay Server (8000 LP, Chain Link Rulings & Tournament Duels)",
                connectionMode = mode,
                isConnected = true,
                memberCount = 412L
            )
            val customCloneServerRes = repository.createDiscordServerHub(
                guildId = "108922004400660088",
                serverName = "Ignis 21-Digit Boss Raid RP",
                channelId = "108922004400660099",
                channelName = "#singularity-throne",
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                rpLoreSetting = "EDOPro Custom Project Clone RP Server (21-Digit LP/ATK Variables, Functions & Code Strings c900001010000000000021)",
                connectionMode = mode,
                isConnected = true,
                memberCount = 289L
            )

            val primaryServerId = dominoServerRes.getOrNull() ?: "domino_rp_hub"
            val customServerId = customCloneServerRes.getOrNull() ?: primaryServerId
            _selectedDiscordServerId.value = primaryServerId

            // 2. Seed TCG Roleplay Character Personas
            repository.createRpCharacterPersona(
                characterName = "Kaiba-Omega",
                characterTitle = "Singularity Overseer",
                factionOrArchetype = "Blue-Eyes / 21-Digit Wyrm",
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                signatureCardName = "Custom Project: Singularity Dragon",
                signatureCodeString21 = "900001040000000000021",
                currentLp21 = "999999999999999999999",
                personalityPrompt = "Relentless, visionary, commands 21-digit ocgcore-bigint21 functions and code strings with absolute authority.",
                openingCatchphrase = "Boot up your Duel Disk! My EDOPro Custom Clone calculates up to 21 digits of power!",
                summonChant = "Shatter the 64-bit barrier and descend from the 21-digit code register! I summon Custom Project: Singularity Dragon!",
                isActivePersona = true
            )
            repository.createRpCharacterPersona(
                characterName = "Atem-Prime",
                characterTitle = "Sovereign Pharaoh of Domino",
                factionOrArchetype = "Spellcaster / Sovereign Dragon",
                baseVariantId = "EDOPRO_BASE_MR5",
                signatureCardName = "Azure-Eyes Sovereign Dragon",
                signatureCodeString21 = "89631139",
                currentLp21 = "8000",
                personalityPrompt = "Noble, calm under pressure, masters Chain Link timing and heart-of-the-cards turn reversals.",
                openingCatchphrase = "The fate of this roleplay duel rests on every card in our decks. Let the Shadow Duel begin!",
                summonChant = "Heed the ancient covenant of Domino Sanctuary! Rise, Azure-Eyes Sovereign Dragon!",
                isActivePersona = false
            )
            repository.createRpCharacterPersona(
                characterName = "Lyra Vance",
                characterTitle = "Cyberse Link Architect",
                factionOrArchetype = "Cyberse / Accesscode Link-4",
                baseVariantId = "EDOPRO_BASE_MR5",
                signatureCardName = "Ignis Accesscode Vanguard",
                signatureCodeString21 = "46986414",
                currentLp21 = "8000",
                personalityPrompt = "Tactical netrunner who narrates Extra Monster Zone co-links and Spell Speed responses in roleplay.",
                openingCatchphrase = "Circuit combine! Extra Monster Zone locked at Link-4!",
                summonChant = "Four cybernetic nodes converge into the ultimate spear! Link Summon! Ignis Accesscode Vanguard!",
                isActivePersona = false
            )

            // 3. Seed realistic Discord RP chat messages for review and roleplay interaction
            repository.createDiscordRpMessage(
                serverId = primaryServerId,
                channelName = "#duel-arena-rp",
                senderName = "Chancellor Crowler-RP",
                senderTitle = "Server Lore Arbiter",
                isCharacterProxy = false,
                messageType = "DISCORD_PEER_CHAT",
                content = "Welcome to #duel-arena-rp! Duelists, please post your opening RP stance and verify your EDOPro Base Variant (8000 LP, MR5) before declaring Chain Link 1.",
                tcgActionSummary = "RULE_CHECK: EDOPRO_BASE_MR5 • 8000 LP • Master Rule 5",
                baseVariantId = "EDOPRO_BASE_MR5",
                deliveryStatus = "REVIEWED_FROM_DISCORD",
                reviewedFlag = false
            )
            repository.createDiscordRpMessage(
                serverId = primaryServerId,
                channelName = "#duel-arena-rp",
                senderName = "Rival Zane_Cyber",
                senderTitle = "Obelisk Blue Vanguard",
                isCharacterProxy = false,
                messageType = "TCG_DUEL_ACTION",
                content = "*steps onto the neon duel platform and slots his deck into the disk* \"I Normal Summon Cyber Core Vanguard and set 1 Counter Trap face-down. Judge, can you review my Chain Link window?\"",
                tcgActionSummary = "MSG_SUMMONING: Normal Summon + Set 1 Spell/Trap (Awaiting RP Review)",
                baseVariantId = "EDOPRO_BASE_MR5",
                deliveryStatus = "SYNCED_RP_CHANNEL",
                reviewedFlag = false
            )
            repository.createDiscordRpMessage(
                serverId = customServerId,
                channelName = "#singularity-throne",
                senderName = "Kaiba-Omega",
                senderTitle = "Singularity Overseer",
                isCharacterProxy = true,
                messageType = "RP_DIALOGUE",
                content = "Boot up your Duel Disk! In #singularity-throne, our EDOPro Custom Clone runs 21-digit code strings (`c900001040000000000021.lua`) and `SCALE_ATK_LP_21` registers at 999,999,999,999,999,999,999 LP!",
                tcgActionSummary = "REGISTER_21: s.code_var_21 = \"900001040000000000021\" | LP: 999,999,999,999,999,999,999",
                baseVariantId = "EDOPRO_CUSTOM_FORK",
                deliveryStatus = "SYNCED_RP_CHANNEL",
                reviewedFlag = true
            )
        }
    }

    fun selectDiscordServer(server: DiscordServerHub) {
        _selectedDiscordServerId.value = server.id
        if (_selectedBaseVariantId.value != server.baseVariantId) {
            _selectedBaseVariantId.value = server.baseVariantId
            _activeCustomVariant.value = null
            refreshHandForVariant(server.baseVariantId)
        }
        _statusBanner.value = "Joined Discord Server '${server.serverName}' (${server.channelName}) on ${server.baseVariantId}"
    }

    fun joinOrAddDiscordServer(
        guildId: String,
        serverName: String,
        channelId: String,
        channelName: String,
        baseVariantId: String,
        rpLoreSetting: String
    ) {
        if (serverName.isBlank()) return
        viewModelScope.launch {
            val mode = DiscordApiService.activeConnectionMode()
            val res = repository.createDiscordServerHub(
                guildId = guildId.ifBlank { "1089${(100000000..999999999).random()}" },
                serverName = serverName,
                channelId = channelId.ifBlank { "1089${(100000000..999999999).random()}" },
                channelName = channelName.ifBlank { "#duel-arena-rp" },
                baseVariantId = baseVariantId,
                rpLoreSetting = rpLoreSetting.ifBlank { "Discord TCG Roleplay Server synced with $baseVariantId" },
                connectionMode = mode,
                isConnected = true,
                memberCount = (64..640).random().toLong()
            )
            res.onSuccess { newServerId ->
                _selectedDiscordServerId.value = newServerId
                repository.createDiscordRpMessage(
                    serverId = newServerId,
                    channelName = if (channelName.startsWith("#")) channelName else "#$channelName",
                    senderName = "NexusTCG Bridge Bot",
                    senderTitle = "Discord RP System",
                    isCharacterProxy = false,
                    messageType = "SYSTEM_SYNC",
                    content = "Connected to Discord Server '$serverName' (${if (channelName.startsWith("#")) channelName else "#$channelName"}) using engine variant $baseVariantId [$mode]. Ready to review chat messages and act as TCG characters.",
                    tcgActionSummary = "HANDSHAKE: $baseVariantId • Mode: $mode",
                    baseVariantId = baseVariantId,
                    deliveryStatus = "SYNCED_RP_CHANNEL",
                    reviewedFlag = true
                )
                _statusBanner.value = "Joined Discord RP Server '$serverName' ($channelName)"
            }.onFailure { e ->
                _statusBanner.value = "Failed to join Discord server: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Verifies the configured Discord Webhook URL without using any client-side Bot Token.
     */
    fun discoverLiveDiscordBotGuilds() {
        viewModelScope.launch {
            val status = DiscordApiService.verifyDiscordCredentials()
            _statusBanner.value = "Discord Webhook & Cloud RP check: $status"
        }
    }

    /**
     * Refreshes the Cloud RP Relay channel log for the selected Discord server hub.
     */
    fun reviewAndSyncChannelMessages(server: DiscordServerHub) {
        viewModelScope.launch {
            val samplePeerName = if (server.baseVariantId == "EDOPRO_CUSTOM_FORK") {
                "Challenger Vex [21-Digit Duelist]"
            } else {
                "Duelist Sora [Academy Challenger]"
            }
            val sampleContent = if (server.baseVariantId == "EDOPRO_CUSTOM_FORK") {
                "*projects a 21-digit holographic script over ${server.channelName}* \"I activate my Custom Card `c900001020000000000021.lua` and scale my variable register to 123,456,789,012,345,678,901! Does your character respond?\""
            } else {
                "*draws 1 card in ${server.channelName} and declares Battle Phase* \"I attack with Cosmic Quasar Stardust (4000 ATK)! Do you chain a Quick Effect or counter in-character?\""
            }
            val sampleSummary = if (server.baseVariantId == "EDOPRO_CUSTOM_FORK") {
                "CHAIN_CHECK_21: c900001020000000000021.lua • Var=123,456,789,012,345,678,901"
            } else {
                "CHAIN_CHECK_MR5: Battle Phase Declaration • 4000 ATK"
            }
            repository.createDiscordRpMessage(
                serverId = server.id,
                channelName = server.channelName,
                senderName = samplePeerName,
                senderTitle = "Cloud RP Challenger",
                isCharacterProxy = false,
                messageType = "DISCORD_PEER_CHAT",
                content = sampleContent,
                tcgActionSummary = sampleSummary,
                baseVariantId = server.baseVariantId,
                deliveryStatus = "SYNCED_RP_CHANNEL",
                reviewedFlag = false
            )
            _statusBanner.value = "Synced latest Cloud RP turn in ${server.channelName} for review."
        }
    }

    fun toggleDiscordServerConnection(server: DiscordServerHub) {
        viewModelScope.launch {
            val nextConnected = !server.isConnected
            repository.updateDiscordServerConnection(
                serverId = server.id,
                isConnected = nextConnected,
                connectionMode = DiscordApiService.activeConnectionMode()
            )
            _statusBanner.value = if (nextConnected) {
                "Reconnected to Discord Server '${server.serverName}' (${server.channelName})"
            } else {
                "Paused connection to '${server.serverName}'"
            }
        }
    }

    fun deleteDiscordServer(serverId: String) {
        viewModelScope.launch {
            repository.deleteDiscordServerHub(serverId)
            if (_selectedDiscordServerId.value == serverId) {
                val remaining = (discordServersState.value as? UiState.Success)?.data
                    .orEmpty()
                    .filter { it.id != serverId }
                _selectedDiscordServerId.value = remaining.firstOrNull()?.id
            }
        }
    }

    fun createRpCharacter(
        characterName: String,
        characterTitle: String,
        factionOrArchetype: String,
        baseVariantId: String,
        signatureCardName: String,
        signatureCodeString21: String,
        currentLp21: String,
        personalityPrompt: String,
        openingCatchphrase: String,
        summonChant: String
    ) {
        if (characterName.isBlank()) return
        viewModelScope.launch {
            val existingChars = (rpCharactersState.value as? UiState.Success)?.data.orEmpty()
            existingChars.filter { it.isActivePersona }.forEach { active ->
                repository.updateRpCharacterActiveState(active.id, false)
            }
            val res = repository.createRpCharacterPersona(
                characterName = characterName,
                characterTitle = characterTitle,
                factionOrArchetype = factionOrArchetype,
                baseVariantId = baseVariantId,
                signatureCardName = signatureCardName,
                signatureCodeString21 = signatureCodeString21,
                currentLp21 = currentLp21,
                personalityPrompt = personalityPrompt,
                openingCatchphrase = openingCatchphrase,
                summonChant = summonChant,
                isActivePersona = true
            )
            res.onSuccess {
                val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(signatureCodeString21)
                _statusBanner.value = "Now acting as RP Character '$characterName' ($characterTitle • Signature c$cleanCode21)"
            }.onFailure { e ->
                _statusBanner.value = "Failed to create character: ${e.localizedMessage}"
            }
        }
    }

    fun activateRpCharacter(character: RpCharacterPersona) {
        viewModelScope.launch {
            val allChars = (rpCharactersState.value as? UiState.Success)?.data.orEmpty()
            allChars.forEach { item ->
                val shouldBeActive = item.id == character.id
                if (item.isActivePersona != shouldBeActive) {
                    repository.updateRpCharacterActiveState(item.id, shouldBeActive)
                }
            }
            _statusBanner.value = "Switched active Discord RP Character to '${character.characterName} [${character.characterTitle}]'"
        }
    }

    fun updateCharacterLp21(character: RpCharacterPersona, newLp21: String) {
        viewModelScope.launch {
            val res = repository.updateRpCharacterLp21(character.id, newLp21)
            res.onSuccess { cleanLp ->
                _statusBanner.value = "Updated ${character.characterName}'s LP to ${BigInt21Engine.formatWithCommas(cleanLp)} (${cleanLp.length}/21 digits)"
            }
        }
    }

    fun deleteRpCharacter(characterId: String) {
        viewModelScope.launch {
            repository.deleteRpCharacterPersona(characterId)
        }
    }

    /**
     * Sends a message to the selected Discord server channel—either acting as the active
     * TCG Roleplay Character Persona (via Discord Webhook / Bot proxy) or as the user.
     */
    fun sendDiscordRoleplayMessage(
        server: DiscordServerHub,
        character: RpCharacterPersona?,
        actAsCharacter: Boolean,
        content: String,
        tcgActionSummary: String,
        messageType: String = "RP_DIALOGUE"
    ) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val personaToUse = if (actAsCharacter) character else null
            val senderName = personaToUse?.characterName ?: currentUserName
            val senderTitle = personaToUse?.characterTitle ?: "NexusTCG Duelist"

            val deliveryStatus = DiscordApiService.sendCharacterRoleplayMessage(
                channelId = server.channelId,
                character = personaToUse,
                senderFallbackName = currentUserName,
                content = content.trim(),
                tcgActionSummary = tcgActionSummary.trim(),
                baseVariantId = server.baseVariantId
            )

            val res = repository.createDiscordRpMessage(
                serverId = server.id,
                channelName = server.channelName,
                senderName = senderName,
                senderTitle = senderTitle,
                isCharacterProxy = personaToUse != null,
                messageType = messageType,
                content = content.trim(),
                tcgActionSummary = tcgActionSummary.trim(),
                baseVariantId = server.baseVariantId,
                deliveryStatus = deliveryStatus,
                reviewedFlag = true
            )
            res.onSuccess {
                val modeDesc = when (deliveryStatus) {
                    "DELIVERED_WEBHOOK" -> "Delivered via Discord Character Webhook"
                    "DELIVERED_DISCORD_API" -> "Delivered via Discord Bot REST API"
                    else -> "Synced to ${server.channelName}"
                }
                _statusBanner.value = "[$senderName] sent message to ${server.channelName} ($modeDesc)"
            }.onFailure { e ->
                _statusBanner.value = "Failed to send message: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Performs a 1-tap in-character roleplay performance in the active Discord server:
     * - "CATCHPHRASE": Speaks the character's opening duel catchphrase
     * - "SUMMON_ACE": Performs the character's summon chant and deploys their signature card (with 21-digit code string)
     * - "DECLARE_21D_CALC": Announces a 21-digit variable/function/LP calculation in-character
     */
    fun triggerCharacterQuickAct(
        server: DiscordServerHub,
        character: RpCharacterPersona,
        actType: String
    ) {
        val (messageType, content, tcgSummary) = when (actType) {
            "CATCHPHRASE" -> Triple(
                "RP_DIALOGUE",
                "\"${character.openingCatchphrase}\" *(${character.characterName} readies their ${character.factionOrArchetype} deck at ${BigInt21Engine.formatWithCommas(character.currentLp21)} LP!)*",
                "RP_STANCE: ${character.characterName} • LP: ${character.currentLp21} (${character.currentLp21.length}/21d)"
            )
            "SUMMON_ACE" -> Triple(
                "TCG_DUEL_ACTION",
                "\"${character.summonChant}\" **I Summon ${character.signatureCardName}** (`script/c${character.signatureCodeString21}.lua`)!",
                "MSG_SUMMONING: ${character.signatureCardName} [Code: c${character.signatureCodeString21} • ${character.signatureCodeString21.length}/21 digits]"
            )
            else -> {
                val boostedLp = BigInt21Engine.evaluateFunction21(
                    "SCALE_ATK_LP_21",
                    character.currentLp21,
                    character.signatureCodeString21
                )
                Triple(
                    "TCG_DUEL_ACTION",
                    "*executes 21-digit ocgcore-bigint21 script `c${character.signatureCodeString21}.lua` in ${server.channelName}* \"By binding coding variable `s.code_var_21 = \"${character.signatureCodeString21}\"` with `SCALE_ATK_LP_21`, my resonance reaches **${BigInt21Engine.formatWithCommas(boostedLp)}** (${boostedLp.length}/21 digits)!\"",
                    "EXEC_21D: SCALE_ATK_LP_21(${character.currentLp21}, c${character.signatureCodeString21}) -> $boostedLp"
                )
            }
        }
        sendDiscordRoleplayMessage(
            server = server,
            character = character,
            actAsCharacter = true,
            content = content,
            tcgActionSummary = tcgSummary,
            messageType = messageType
        )
    }

    fun toggleDiscordMessageReviewed(message: DiscordRpMessage) {
        viewModelScope.launch {
            val nextReviewed = !message.reviewedFlag
            repository.toggleDiscordMessageReviewed(message.id, nextReviewed)
            _statusBanner.value = if (nextReviewed) {
                "Marked message from ${message.senderName} as Reviewed & Ruling Verified"
            } else {
                "Marked message from ${message.senderName} as Needs Review"
            }
        }
    }

    fun deleteDiscordMessage(messageId: String) {
        viewModelScope.launch {
            repository.deleteDiscordRpMessage(messageId)
        }
    }

    // --- Discord .env Settings & Bot Integration Toggle Operations ---

    fun setDiscordIntegrationEnabled(enabled: Boolean) {
        DiscordApiService.setIntegrationEnabled(enabled)
        val newMode = DiscordApiService.activeConnectionMode()
        viewModelScope.launch {
            val servers = (discordServersState.value as? UiState.Success)?.data.orEmpty()
            servers.forEach { server ->
                repository.updateDiscordServerConnection(
                    serverId = server.id,
                    isConnected = if (enabled) server.isConnected else false,
                    connectionMode = newMode
                )
            }
        }
        _statusBanner.value = if (enabled) {
            "Discord Bot & Webhook Integration ENABLED ($newMode)"
        } else {
            "Discord Bot & Webhook Integration PAUSED (Operating in local/Cloud RP Relay mode)"
        }
    }

    fun saveDiscordEnvCredentials(
        botToken: String,
        webhookUrl: String,
        botClientId: String,
        integrationEnabled: Boolean
    ) {
        DiscordApiService.updateEnvCredentials(
            botToken = botToken,
            webhookUrl = webhookUrl,
            botClientId = botClientId,
            integrationEnabled = integrationEnabled
        )
        val newMode = DiscordApiService.activeConnectionMode()
        viewModelScope.launch {
            val servers = (discordServersState.value as? UiState.Success)?.data.orEmpty()
            servers.forEach { server ->
                repository.updateDiscordServerConnection(
                    serverId = server.id,
                    isConnected = integrationEnabled,
                    connectionMode = newMode
                )
            }
        }
        _statusBanner.value = "Saved .env Discord credentials (Mode: $newMode)"
    }

    fun resetDiscordEnvToBuildConfig() {
        DiscordApiService.resetToBuildConfigEnvDefaults()
        val newMode = DiscordApiService.activeConnectionMode()
        viewModelScope.launch {
            val servers = (discordServersState.value as? UiState.Success)?.data.orEmpty()
            servers.forEach { server ->
                repository.updateDiscordServerConnection(
                    serverId = server.id,
                    isConnected = server.isConnected,
                    connectionMode = newMode
                )
            }
        }
        _statusBanner.value = "Reset Discord credentials to .env BuildConfig defaults ($newMode)"
    }

    fun testDiscordEnvConnection() {
        viewModelScope.launch {
            val summary = DiscordApiService.verifyDiscordCredentials()
            _statusBanner.value = summary
        }
    }

    // --- Discord Webhook Bridge Operations (Game Start, Deck Summary, Match Winner) ---

    fun updateDiscordBridgeConfig(
        designatedChannelName: String,
        autoGameStart: Boolean,
        autoMatchWinner: Boolean,
        autoDeckOnForge: Boolean
    ) {
        DiscordApiService.updateBridgeConfig(
            designatedChannelName = designatedChannelName,
            autoBroadcastGameStart = autoGameStart,
            autoBroadcastMatchWinner = autoMatchWinner,
            autoBroadcastDeckOnForge = autoDeckOnForge
        )
        val updated = DiscordApiService.bridgeConfig.value
        _statusBanner.value = "Discord Bridge channel set to ${updated.designatedChannelName} (Auto: Start=${updated.autoBroadcastGameStart}, Winner=${updated.autoBroadcastMatchWinner}, Deck=${updated.autoBroadcastDeckOnForge})"
    }

    private suspend fun mirrorBroadcastToActiveDiscordHub(
        record: DiscordWebhookBroadcastRecord,
        baseVariantId: String,
        actionSummary: String
    ) {
        val servers = (discordServersState.value as? UiState.Success)?.data.orEmpty()
        val activeServer = servers.firstOrNull { it.id == _selectedDiscordServerId.value }
            ?: servers.firstOrNull()
            ?: return
        repository.createDiscordRpMessage(
            serverId = activeServer.id,
            channelName = record.channelLabel,
            senderName = "NexusTCG Webhook Bridge",
            senderTitle = record.eventType.label,
            isCharacterProxy = false,
            messageType = "TCG_DUEL_ACTION",
            content = "[${record.eventType.badgeCode}] ${record.title} — ${record.summary}",
            tcgActionSummary = actionSummary,
            baseVariantId = baseVariantId,
            deliveryStatus = record.deliveryStatus,
            reviewedFlag = true
        )
    }

    private fun buildBroadcastBannerMessage(record: DiscordWebhookBroadcastRecord): String = when (record.deliveryStatus) {
        "DELIVERED_WEBHOOK" -> "Discord Webhook delivered ${record.eventType.label} to ${record.channelLabel}: ${record.title}"
        "WEBHOOK_UNCONFIGURED" -> "Set DISCORD_WEBHOOK_URL in Settings or AI Studio Secrets to push ${record.eventType.label} to ${record.channelLabel} (logged locally)."
        "PAUSED" -> "Discord Bridge is paused in Settings. Enable Discord integration to deliver ${record.eventType.label}."
        else -> "Discord Webhook ${record.eventType.label} status: ${record.deliveryStatus}"
    }

    fun broadcastGameStartToDiscord(
        match: MatchSession? = null,
        deck: CustomDeck? = null,
        updateBannerOnUnconfigured: Boolean = true
    ) {
        viewModelScope.launch {
            val baseId = match?.baseVariantId ?: _selectedBaseVariantId.value
            val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseId)
            val resolvedMatchTitle = match?.title ?: "${baseSpec.name} Live Arena"
            val resolvedHost = match?.hostName?.ifBlank { currentUserName } ?: currentUserName
            val resolvedClients = match?.connectedClients?.ifEmpty { listOf(resolvedHost) }
                ?: listOfNotNull(_activeClientNode.value?.clientName, resolvedHost).distinct()
            val resolvedDeck = deck ?: (decksState.value as? UiState.Success)?.data
                ?.firstOrNull { it.baseVariantId == baseId }
            val channelLabel = DiscordApiService.bridgeConfig.value.designatedChannelName

            val record = DiscordApiService.broadcastGameStartEvent(
                matchTitle = resolvedMatchTitle,
                baseVariantId = baseId,
                baseVariantName = match?.baseVariantName ?: baseSpec.name,
                hostName = resolvedHost,
                connectedClients = resolvedClients,
                startingLife = match?.hostLife ?: baseSpec.startingLife,
                resourceName = match?.resourceName ?: baseSpec.resourceName,
                activeDeckName = resolvedDeck?.name,
                designatedChannelLabel = channelLabel
            )
            mirrorBroadcastToActiveDiscordHub(
                record = record,
                baseVariantId = baseId,
                actionSummary = "GAME_START • $resolvedMatchTitle • ${baseSpec.protocolVersion}"
            )
            if (updateBannerOnUnconfigured || record.deliveryStatus == "DELIVERED_WEBHOOK") {
                _statusBanner.value = buildBroadcastBannerMessage(record)
            }
        }
    }

    fun broadcastDeckSummaryToDiscord(
        deck: CustomDeck,
        updateBannerOnUnconfigured: Boolean = true
    ) {
        viewModelScope.launch {
            val channelLabel = DiscordApiService.bridgeConfig.value.designatedChannelName
            val record = DiscordApiService.broadcastDeckSummary(
                deck = deck,
                ownerName = currentUserName,
                designatedChannelLabel = channelLabel
            )
            mirrorBroadcastToActiveDiscordHub(
                record = record,
                baseVariantId = deck.baseVariantId,
                actionSummary = "DECK_SUMMARY • ${deck.name} • Ace: ${deck.customCardName} (c${deck.customCodeString21})"
            )
            if (updateBannerOnUnconfigured || record.deliveryStatus == "DELIVERED_WEBHOOK") {
                _statusBanner.value = buildBroadcastBannerMessage(record)
            }
        }
    }

    fun broadcastMatchWinnerToDiscord(
        match: MatchSession? = null,
        winnerNameOverride: String? = null,
        deck: CustomDeck? = null,
        updateBannerOnUnconfigured: Boolean = true
    ) {
        viewModelScope.launch {
            val baseId = match?.baseVariantId ?: _selectedBaseVariantId.value
            val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseId)
            val resolvedMatchTitle = match?.title ?: "${baseSpec.name} Live Arena"
            val hostName = match?.hostName?.ifBlank { currentUserName } ?: currentUserName
            val peerName = match?.connectedClients?.firstOrNull { it != hostName }
                ?: _activeClientNode.value?.clientName
                ?: "Connected Peer"
            val resolvedWinner = winnerNameOverride?.trim()?.takeIf { it.isNotBlank() }
                ?: match?.winnerName?.takeIf { it.isNotBlank() }
                ?: if ((match?.opponentLife ?: 0L) <= (match?.hostLife ?: baseSpec.startingLife)) hostName else peerName
            val runnerUp = if (resolvedWinner.equals(hostName, ignoreCase = true)) peerName else hostName
            val resolvedDeck = deck ?: (decksState.value as? UiState.Success)?.data
                ?.firstOrNull { it.baseVariantId == baseId }
            val channelLabel = DiscordApiService.bridgeConfig.value.designatedChannelName

            // Persist winner to Firestore match if declaring victory on an active match
            if (match != null && match.status != "FINISHED") {
                val winnerLog = "DUEL COMPLETE! Winner: $resolvedWinner (Broadcasted to Discord $channelLabel)."
                repository.updateMatchState(
                    matchId = match.id,
                    connectedClients = match.connectedClients,
                    turnNumber = match.turnNumber,
                    activePhase = "END",
                    hostLife = match.hostLife,
                    opponentLife = match.opponentLife,
                    hostResources = match.hostResources,
                    hostMaxResources = match.hostMaxResources,
                    opponentResources = match.opponentResources,
                    hostBoardCards = match.hostBoardCards,
                    opponentBoardCards = match.opponentBoardCards,
                    actionLog = (match.actionLog + winnerLog).takeLast(20),
                    status = "FINISHED",
                    winnerName = resolvedWinner
                )
            }

            val record = DiscordApiService.broadcastMatchWinner(
                matchTitle = resolvedMatchTitle,
                winnerName = resolvedWinner,
                runnerUpName = runnerUp,
                baseVariantId = baseId,
                baseVariantName = match?.baseVariantName ?: baseSpec.name,
                finalHostLife = match?.hostLife ?: gameEngine.state.value.hostLife,
                finalOpponentLife = match?.opponentLife ?: gameEngine.state.value.opponentLife,
                totalTurns = match?.turnNumber ?: gameEngine.state.value.turnNumber,
                winningDeckName = resolvedDeck?.name,
                designatedChannelLabel = channelLabel
            )
            mirrorBroadcastToActiveDiscordHub(
                record = record,
                baseVariantId = baseId,
                actionSummary = "MATCH_WINNER • $resolvedWinner defeated $runnerUp (${match?.turnNumber ?: 1}T)"
            )
            if (updateBannerOnUnconfigured || record.deliveryStatus == "DELIVERED_WEBHOOK") {
                _statusBanner.value = buildBroadcastBannerMessage(record)
            }
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    companion object {
        private const val TAG = "NexusTcgVM"
    }
}
