package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Identifies which of the two connected client seats this local instance is operating as
 * during a real-time Firebase Firestore synchronized match (`/matches/{matchId}`).
 */
enum class SyncClientSeat(
    val code: String,
    val displayLabel: String,
    val shortLabel: String
) {
    CLIENT_1_HOST(
        code = "CLIENT_1_HOST",
        displayLabel = "Client 1 • Host",
        shortLabel = "Client 1 (Host)"
    ),
    CLIENT_2_PEER(
        code = "CLIENT_2_PEER",
        displayLabel = "Client 2 • Peer",
        shortLabel = "Client 2 (Peer)"
    )
}

/**
 * Real-time connection & transaction lifecycle status for [FirestoreGameStateManager].
 */
enum class RealtimeSyncConnectionStatus(val label: String) {
    DISCONNECTED("OFFLINE"),
    CONNECTING("CONNECTING"),
    SYNCED("FIRESTORE LIVE"),
    SYNCING_WRITE("SYNCING TX"),
    FINISHED("MATCH FINISHED"),
    ERROR("SYNC ERROR")
}

/**
 * Unified real-time snapshot emitted by [FirestoreGameStateManager] representing the live
 * Firestore-synchronized life points, board zone placements, connected client roster, and
 * revision telemetry shared between two connected clients.
 */
data class RealtimeGameSyncState(
    val matchId: String = "",
    val matchTitle: String = "No Active Sync Room",
    val hostUserId: String = "",
    val hostName: String = "Client 1 (Host)",
    val baseVariantId: String = "EDOPRO_BASE_MR5",
    val baseVariantName: String = "ProjectIgnis EDOPro Base",
    val localSeat: SyncClientSeat = SyncClientSeat.CLIENT_1_HOST,
    val localClientName: String = "Client 1",
    val connectionStatus: RealtimeSyncConnectionStatus = RealtimeSyncConnectionStatus.DISCONNECTED,
    val revisionNumber: Int = 0,
    val turnNumber: Long = 1L,
    val activePhase: String = "MAIN",
    val hostLife: Long = 8000L,
    val opponentLife: Long = 8000L,
    val hostResources: Long = 5L,
    val hostMaxResources: Long = 10L,
    val opponentResources: Long = 5L,
    val connectedClients: List<String> = emptyList(),
    val zonePlacements: Map<String, SerializedZonePlacement> = emptyMap(),
    val hostBoardCount: Int = 0,
    val opponentBoardCount: Int = 0,
    val lastBroadcastClientId: String = "",
    val lastBroadcastClientName: String = "",
    val lastEventType: String = "INIT",
    val lastEventSummary: String = "Attach to an Arena match room to sync life points & board state in real time.",
    val lastSyncLatencyMs: Long = 0L,
    val status: String = "OPEN_LOBBY",
    val winnerName: String = "",
    val errorMessage: String? = null,
    val recentSyncEvents: List<String> = emptyList(),
    val syncedTimestampMillis: Long = System.currentTimeMillis()
) {
    val isAttached: Boolean
        get() = matchId.isNotBlank() &&
            (connectionStatus == RealtimeSyncConnectionStatus.SYNCED ||
                connectionStatus == RealtimeSyncConnectionStatus.SYNCING_WRITE ||
                connectionStatus == RealtimeSyncConnectionStatus.FINISHED)

    val isMatchFinished: Boolean
        get() = status.equals("FINISHED", ignoreCase = true) ||
            connectionStatus == RealtimeSyncConnectionStatus.FINISHED

    fun toSerializedGameBoardState(): SerializedGameBoardState {
        return SerializedGameBoardState(
            matchId = matchId,
            baseVariantId = baseVariantId,
            baseVariantName = baseVariantName,
            revisionNumber = revisionNumber.coerceAtLeast(1),
            turnNumber = turnNumber,
            activePhase = activePhase,
            hostLife = hostLife,
            opponentLife = opponentLife,
            hostResources = hostResources,
            opponentResources = opponentResources,
            connectedClients = connectedClients,
            lastBroadcastClientId = lastBroadcastClientId,
            lastBroadcastClientName = lastBroadcastClientName.ifBlank { localClientName },
            lastEventType = lastEventType,
            lastEventSummary = lastEventSummary,
            zonePlacements = zonePlacements,
            syncedTimestampMillis = syncedTimestampMillis
        )
    }
}

/**
 * Production real-time game state manager backed by Firebase Firestore (`/matches/{matchId}`).
 *
 * Uses Firestore `snapshots()` for sub-second push synchronization and `runTransaction` for
 * atomic, conflict-free updates to **life points** (`hostLife` / `opponentLife`) and **board state**
 * (`hostBoardCards` / `opponentBoardCards`) between two connected clients (`CLIENT_1_HOST` and
 * `CLIENT_2_PEER`). All writes strictly conform to the deployed `/matches/{matchId}` schema
 * and `firestore.rules` security constraints.
 */
class FirestoreGameStateManager(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireAuthenticatedUid(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In is required to synchronize match state with Firestore.")
    }

    private fun normalizeFirestorePhase(rawPhase: String): String {
        val upper = rawPhase.trim().uppercase()
        return when {
            upper in VALID_PHASES -> upper
            upper.contains("DRAW") || upper == "DP" || upper == "SP" ||
                upper.contains("UNTAP") || upper.contains("UPKEEP") ||
                upper == "SHIFT" || upper == "BOOT" -> "DRAW"
            upper.contains("BP") || upper.contains("COMBAT") ||
                upper.contains("BREACH") || upper.contains("CLASH") -> "COMBAT"
            upper.contains("EP") || upper.contains("END") ||
                upper.contains("REWIND") || upper.contains("OVERDRIVE") -> "END"
            else -> "MAIN"
        }
    }

    private fun partitionSyncablePlacements(
        zonePlacements: Map<String, SerializedZonePlacement>
    ): Pair<List<String>, List<String>> {
        val syncable = zonePlacements.filterKeys { key ->
            !key.startsWith("P1_HAND_", ignoreCase = true) &&
                key != "P1_DECK" &&
                key != "P2_DECK"
        }

        val hostDescriptors = syncable.values
            .filter {
                it.owner == "PLAYER_1_HOST" ||
                    it.owner == "SHARED_NEXUS" ||
                    it.zoneId.startsWith("P1_", ignoreCase = true) ||
                    it.zoneId.startsWith("SHARED_", ignoreCase = true)
            }
            .sortedBy { it.zoneId }
            .take(MAX_BOARD_CARDS_PER_PLAYER)
            .map { it.toZoneBoardDescriptor() }

        val opponentDescriptors = syncable.values
            .filter {
                it.owner == "PLAYER_2_OPPONENT" ||
                    it.zoneId.startsWith("P2_", ignoreCase = true)
            }
            .sortedBy { it.zoneId }
            .take(MAX_BOARD_CARDS_PER_PLAYER)
            .map { it.toZoneBoardDescriptor() }

        return hostDescriptors to opponentDescriptors
    }

    private fun buildSyncStateFromMatch(
        match: MatchSession,
        localSeat: SyncClientSeat,
        localClientName: String,
        latencyMs: Long = 0L,
        statusOverride: RealtimeSyncConnectionStatus? = null
    ): RealtimeGameSyncState {
        val boardSnapshot = SerializedGameBoardState.fromMatchSession(match)
        val humanLogs = match.actionLog
            .filterNot { it.startsWith(SerializedGameBoardState.SYNC_ENVELOPE_PREFIX) }
            .takeLast(8)
            .reversed()

        val computedStatus = statusOverride ?: when {
            match.status.equals("FINISHED", ignoreCase = true) -> RealtimeSyncConnectionStatus.FINISHED
            else -> RealtimeSyncConnectionStatus.SYNCED
        }

        return RealtimeGameSyncState(
            matchId = match.id,
            matchTitle = match.title.ifBlank { "Arena Sync Room" },
            hostUserId = match.hostUserId,
            hostName = match.hostName.ifBlank { "Client 1 (Host)" },
            baseVariantId = match.baseVariantId,
            baseVariantName = match.baseVariantName,
            localSeat = localSeat,
            localClientName = localClientName,
            connectionStatus = computedStatus,
            revisionNumber = boardSnapshot.revisionNumber,
            turnNumber = match.turnNumber,
            activePhase = match.activePhase,
            hostLife = match.hostLife,
            opponentLife = match.opponentLife,
            hostResources = match.hostResources,
            hostMaxResources = match.hostMaxResources,
            opponentResources = match.opponentResources,
            connectedClients = match.connectedClients,
            zonePlacements = boardSnapshot.zonePlacements,
            hostBoardCount = match.hostBoardCards.size,
            opponentBoardCount = match.opponentBoardCards.size,
            lastBroadcastClientId = boardSnapshot.lastBroadcastClientId,
            lastBroadcastClientName = boardSnapshot.lastBroadcastClientName,
            lastEventType = boardSnapshot.lastEventType,
            lastEventSummary = boardSnapshot.lastEventSummary,
            lastSyncLatencyMs = latencyMs.coerceAtLeast(0L),
            status = match.status,
            winnerName = match.winnerName,
            errorMessage = null,
            recentSyncEvents = humanLogs,
            syncedTimestampMillis = boardSnapshot.syncedTimestampMillis
        )
    }

    /**
     * Subscribes to real-time updates for `/matches/{matchId}` so both connected clients
     * immediately reflect life point changes, board placements, rotations, and match finishes.
     */
    fun observeMatchSyncState(
        matchId: String,
        localSeat: SyncClientSeat,
        localClientName: String
    ): Flow<RealtimeGameSyncState> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId"
        return db.collection(COLLECTION_MATCHES)
            .document(trimmedId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) {
                    RealtimeGameSyncState(
                        matchId = trimmedId,
                        localSeat = localSeat,
                        localClientName = localClientName,
                        connectionStatus = RealtimeSyncConnectionStatus.ERROR,
                        errorMessage = "Match room '$trimmedId' does not exist or was deleted."
                    )
                } else {
                    val match = snapshot.toObject(
                        MatchSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    if (match == null) {
                        RealtimeGameSyncState(
                            matchId = trimmedId,
                            localSeat = localSeat,
                            localClientName = localClientName,
                            connectionStatus = RealtimeSyncConnectionStatus.ERROR,
                            errorMessage = "Failed to deserialize Firestore match snapshot."
                        )
                    } else {
                        val serverMillis = match.updatedAt?.toDate()?.time
                        val estimatedLatency = if (serverMillis != null) {
                            (System.currentTimeMillis() - serverMillis).coerceIn(0L, 9999L)
                        } else {
                            0L
                        }
                        buildSyncStateFromMatch(
                            match = match,
                            localSeat = localSeat,
                            localClientName = localClientName,
                            latencyMs = estimatedLatency
                        )
                    }
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootException = firestoreError ?: (error as? Exception)
                if (rootException != null) {
                    handleFirestoreError(rootException, OperationType.GET, path)
                }
                Log.w(TAG, "Real-time sync observer error for $path", rootException ?: error)
                emit(
                    RealtimeGameSyncState(
                        matchId = trimmedId,
                        localSeat = localSeat,
                        localClientName = localClientName,
                        connectionStatus = RealtimeSyncConnectionStatus.ERROR,
                        errorMessage = (rootException ?: error).message ?: "Firestore listener interrupted."
                    )
                )
            }
    }

    /**
     * Atomically attaches a client (`CLIENT_1_HOST` or `CLIENT_2_PEER`) to an existing match room
     * in Firestore, updating `connectedClients` and promoting `OPEN_LOBBY` -> `ACTIVE`.
     */
    suspend fun attachClientToMatch(
        matchId: String,
        clientSeat: SyncClientSeat,
        clientLabel: String
    ): Result<RealtimeGameSyncState> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId"
        return try {
            val uid = requireAuthenticatedUid()
            val docRef = db.collection(COLLECTION_MATCHES).document(trimmedId)
            val startMs = System.currentTimeMillis()
            val cleanClientName = clientLabel.trim().ifBlank { clientSeat.shortLabel }.take(44)
            val rosterTag = "${clientSeat.shortLabel}: $cleanClientName".take(60)

            val updatedMatch = db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Match room '$trimmedId' not found in Firestore.")
                }
                val current = snapshot.toObject(
                    MatchSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Match room '$trimmedId' payload is invalid.")

                if (current.status.equals("FINISHED", ignoreCase = true)) {
                    return@runTransaction current
                }

                val updatedClients = (current.connectedClients + rosterTag)
                    .map { it.trim().take(60) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(MAX_CONNECTED_CLIENTS)

                val nextStatus = if (current.status == "OPEN_LOBBY" &&
                    (clientSeat == SyncClientSeat.CLIENT_2_PEER || updatedClients.size >= 2)
                ) {
                    "ACTIVE"
                } else {
                    current.status
                }

                val currentBoard = SerializedGameBoardState.fromMatchSession(current)
                val nextRev = currentBoard.revisionNumber + 1
                val summary = "$rosterTag joined Firestore sync room ($nextStatus)"
                val nextEnvelope = currentBoard.copy(
                    revisionNumber = nextRev,
                    connectedClients = updatedClients,
                    lastBroadcastClientId = uid,
                    lastBroadcastClientName = rosterTag,
                    lastEventType = "CLIENT_ATTACH",
                    lastEventSummary = summary,
                    syncedTimestampMillis = System.currentTimeMillis()
                ).toCompactSyncEnvelopeLogEntry()

                val humanLog = "SYNC_ATTACH [$rosterTag • Rev #$nextRev]: $summary".take(280)
                val nextLogs = buildUpdatedActionLog(current.actionLog, humanLog, nextEnvelope)

                transaction.update(
                    docRef,
                    mapOf(
                        "connectedClients" to updatedClients,
                        "actionLog" to nextLogs,
                        "status" to nextStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                current.copy(
                    connectedClients = updatedClients,
                    actionLog = nextLogs,
                    status = nextStatus
                )
            }.await()

            val elapsedMs = System.currentTimeMillis() - startMs
            Result.success(
                buildSyncStateFromMatch(
                    match = updatedMatch,
                    localSeat = clientSeat,
                    localClientName = cleanClientName,
                    latencyMs = elapsedMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically applies a life point delta (`+delta` or `-delta`) to either Client 1 (Host) or
     * Client 2 (Peer) inside a Firestore transaction so concurrent edits from both connected
     * clients never overwrite each other or clobber board placements.
     */
    suspend fun syncLifePointsDelta(
        matchId: String,
        targetSeat: SyncClientSeat,
        delta: Long,
        actorSeat: SyncClientSeat,
        actorLabel: String
    ): Result<RealtimeGameSyncState> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId"
        return try {
            val uid = requireAuthenticatedUid()
            val docRef = db.collection(COLLECTION_MATCHES).document(trimmedId)
            val startMs = System.currentTimeMillis()
            val cleanActor = actorLabel.trim().ifBlank { actorSeat.shortLabel }.take(56)

            val updatedMatch = db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Match room '$trimmedId' not found.")
                }
                val current = snapshot.toObject(
                    MatchSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Invalid match document.")

                if (current.status.equals("FINISHED", ignoreCase = true)) {
                    throw IllegalStateException("Match is already FINISHED (Winner: ${current.winnerName.ifBlank { "Declared" }}).")
                }

                val newHostLife = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) {
                    (current.hostLife + delta).coerceIn(0L, MAX_LIFE_BOUND)
                } else {
                    current.hostLife.coerceIn(MIN_LIFE_BOUND, MAX_LIFE_BOUND)
                }

                val newOpponentLife = if (targetSeat == SyncClientSeat.CLIENT_2_PEER) {
                    (current.opponentLife + delta).coerceIn(0L, MAX_LIFE_BOUND)
                } else {
                    current.opponentLife.coerceIn(MIN_LIFE_BOUND, MAX_LIFE_BOUND)
                }

                val isFinished = newHostLife <= 0L || newOpponentLife <= 0L
                val nextStatus = when {
                    isFinished -> "FINISHED"
                    current.status == "OPEN_LOBBY" -> "ACTIVE"
                    else -> current.status
                }
                val winnerName = when {
                    newOpponentLife <= 0L && newHostLife > 0L ->
                        current.hostName.ifBlank { "Client 1 (Host)" }.take(60)
                    newHostLife <= 0L && newOpponentLife > 0L ->
                        current.connectedClients.getOrNull(1)?.take(60) ?: "Client 2 (Peer)"
                    newHostLife <= 0L && newOpponentLife <= 0L -> "Double KO Draw"
                    else -> current.winnerName.take(60)
                }

                val updatedClients = ensureClientInRoster(current.connectedClients, cleanActor)
                val currentBoard = SerializedGameBoardState.fromMatchSession(current)
                val nextRev = currentBoard.revisionNumber + 1
                val signedDelta = if (delta >= 0L) "+$delta" else "$delta"
                val targetName = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) "P1 Host" else "P2 Peer"
                val summary = if (isFinished) {
                    "$targetName LP $signedDelta -> KO! Winner: $winnerName"
                } else {
                    "$targetName LP $signedDelta (P1: $newHostLife | P2: $newOpponentLife)"
                }

                val envelope = currentBoard.copy(
                    revisionNumber = nextRev,
                    hostLife = newHostLife,
                    opponentLife = newOpponentLife,
                    connectedClients = updatedClients,
                    lastBroadcastClientId = uid,
                    lastBroadcastClientName = cleanActor,
                    lastEventType = if (isFinished) "LP_MATCH_WIN" else "LP_SYNC_DELTA",
                    lastEventSummary = summary,
                    syncedTimestampMillis = System.currentTimeMillis()
                ).toCompactSyncEnvelopeLogEntry()

                val humanLog = "LP_SYNC [$cleanActor • Rev #$nextRev]: $summary".take(280)
                val nextLogs = buildUpdatedActionLog(current.actionLog, humanLog, envelope)

                transaction.update(
                    docRef,
                    mapOf(
                        "connectedClients" to updatedClients,
                        "hostLife" to newHostLife,
                        "opponentLife" to newOpponentLife,
                        "actionLog" to nextLogs,
                        "status" to nextStatus,
                        "winnerName" to winnerName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                current.copy(
                    connectedClients = updatedClients,
                    hostLife = newHostLife,
                    opponentLife = newOpponentLife,
                    actionLog = nextLogs,
                    status = nextStatus,
                    winnerName = winnerName
                )
            }.await()

            val elapsedMs = System.currentTimeMillis() - startMs
            Result.success(
                buildSyncStateFromMatch(
                    match = updatedMatch,
                    localSeat = actorSeat,
                    localClientName = cleanActor,
                    latencyMs = elapsedMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically sets an exact life point value for Client 1 (Host) or Client 2 (Peer) via
     * Firestore transaction.
     */
    suspend fun syncLifePointsExact(
        matchId: String,
        targetSeat: SyncClientSeat,
        exactLife: Long,
        actorSeat: SyncClientSeat,
        actorLabel: String
    ): Result<RealtimeGameSyncState> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId"
        return try {
            val uid = requireAuthenticatedUid()
            val docRef = db.collection(COLLECTION_MATCHES).document(trimmedId)
            val startMs = System.currentTimeMillis()
            val cleanActor = actorLabel.trim().ifBlank { actorSeat.shortLabel }.take(56)
            val clampedTarget = exactLife.coerceIn(0L, MAX_LIFE_BOUND)

            val updatedMatch = db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Match room '$trimmedId' not found.")
                }
                val current = snapshot.toObject(
                    MatchSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Invalid match document.")

                if (current.status.equals("FINISHED", ignoreCase = true)) {
                    throw IllegalStateException("Match is already FINISHED.")
                }

                val newHostLife = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) clampedTarget else current.hostLife
                val newOpponentLife = if (targetSeat == SyncClientSeat.CLIENT_2_PEER) clampedTarget else current.opponentLife
                val isFinished = newHostLife <= 0L || newOpponentLife <= 0L
                val nextStatus = when {
                    isFinished -> "FINISHED"
                    current.status == "OPEN_LOBBY" -> "ACTIVE"
                    else -> current.status
                }
                val winnerName = when {
                    newOpponentLife <= 0L && newHostLife > 0L ->
                        current.hostName.ifBlank { "Client 1 (Host)" }.take(60)
                    newHostLife <= 0L && newOpponentLife > 0L ->
                        current.connectedClients.getOrNull(1)?.take(60) ?: "Client 2 (Peer)"
                    newHostLife <= 0L && newOpponentLife <= 0L -> "Double KO Draw"
                    else -> current.winnerName.take(60)
                }

                val updatedClients = ensureClientInRoster(current.connectedClients, cleanActor)
                val currentBoard = SerializedGameBoardState.fromMatchSession(current)
                val nextRev = currentBoard.revisionNumber + 1
                val targetName = if (targetSeat == SyncClientSeat.CLIENT_1_HOST) "P1 Host" else "P2 Peer"
                val summary = "Set $targetName LP = $clampedTarget (P1: $newHostLife | P2: $newOpponentLife)"

                val envelope = currentBoard.copy(
                    revisionNumber = nextRev,
                    hostLife = newHostLife,
                    opponentLife = newOpponentLife,
                    connectedClients = updatedClients,
                    lastBroadcastClientId = uid,
                    lastBroadcastClientName = cleanActor,
                    lastEventType = if (isFinished) "LP_MATCH_WIN" else "LP_SYNC_SET",
                    lastEventSummary = summary,
                    syncedTimestampMillis = System.currentTimeMillis()
                ).toCompactSyncEnvelopeLogEntry()

                val humanLog = "LP_SET [$cleanActor • Rev #$nextRev]: $summary".take(280)
                val nextLogs = buildUpdatedActionLog(current.actionLog, humanLog, envelope)

                transaction.update(
                    docRef,
                    mapOf(
                        "connectedClients" to updatedClients,
                        "hostLife" to newHostLife,
                        "opponentLife" to newOpponentLife,
                        "actionLog" to nextLogs,
                        "status" to nextStatus,
                        "winnerName" to winnerName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                current.copy(
                    connectedClients = updatedClients,
                    hostLife = newHostLife,
                    opponentLife = newOpponentLife,
                    actionLog = nextLogs,
                    status = nextStatus,
                    winnerName = winnerName
                )
            }.await()

            val elapsedMs = System.currentTimeMillis() - startMs
            Result.success(
                buildSyncStateFromMatch(
                    match = updatedMatch,
                    localSeat = actorSeat,
                    localClientName = cleanActor,
                    latencyMs = elapsedMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically synchronizes the full board zone map (`hostBoardCards` & `opponentBoardCards`)
     * via Firestore transaction while preserving the latest concurrent `hostLife`/`opponentLife`
     * already committed by the remote client.
     */
    suspend fun syncBoardStateAtomic(
        matchId: String,
        zonePlacements: Map<String, SerializedZonePlacement>,
        actorSeat: SyncClientSeat,
        actorLabel: String,
        eventType: String,
        eventSummary: String,
        updatedPhase: String? = null,
        updatedTurnNumber: Long? = null
    ): Result<RealtimeGameSyncState> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId"
        return try {
            val uid = requireAuthenticatedUid()
            val docRef = db.collection(COLLECTION_MATCHES).document(trimmedId)
            val startMs = System.currentTimeMillis()
            val cleanActor = actorLabel.trim().ifBlank { actorSeat.shortLabel }.take(56)
            val (hostDescriptors, opponentDescriptors) = partitionSyncablePlacements(zonePlacements)

            val updatedMatch = db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Match room '$trimmedId' not found.")
                }
                val current = snapshot.toObject(
                    MatchSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Invalid match document.")

                if (current.status.equals("FINISHED", ignoreCase = true)) {
                    return@runTransaction current
                }

                val updatedClients = ensureClientInRoster(current.connectedClients, cleanActor)
                val currentBoard = SerializedGameBoardState.fromMatchSession(current)
                val nextRev = currentBoard.revisionNumber + 1
                val safePhase = normalizeFirestorePhase(updatedPhase ?: current.activePhase)
                val safeTurn = (updatedTurnNumber ?: current.turnNumber).coerceIn(1L, MAX_TURN_NUMBER)
                val nextStatus = if (current.status == "OPEN_LOBBY") "ACTIVE" else current.status

                val envelope = currentBoard.copy(
                    revisionNumber = nextRev,
                    turnNumber = safeTurn,
                    activePhase = safePhase,
                    connectedClients = updatedClients,
                    lastBroadcastClientId = uid,
                    lastBroadcastClientName = cleanActor,
                    lastEventType = eventType.take(24),
                    lastEventSummary = eventSummary.take(120),
                    zonePlacements = zonePlacements,
                    syncedTimestampMillis = System.currentTimeMillis()
                ).toCompactSyncEnvelopeLogEntry()

                val humanLog = "BOARD_SYNC [$cleanActor • Rev #$nextRev]: $eventSummary".take(280)
                val nextLogs = buildUpdatedActionLog(current.actionLog, humanLog, envelope)

                transaction.update(
                    docRef,
                    mapOf(
                        "connectedClients" to updatedClients,
                        "turnNumber" to safeTurn,
                        "activePhase" to safePhase,
                        "hostBoardCards" to hostDescriptors,
                        "opponentBoardCards" to opponentDescriptors,
                        "actionLog" to nextLogs,
                        "status" to nextStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                current.copy(
                    connectedClients = updatedClients,
                    turnNumber = safeTurn,
                    activePhase = safePhase,
                    hostBoardCards = hostDescriptors,
                    opponentBoardCards = opponentDescriptors,
                    actionLog = nextLogs,
                    status = nextStatus
                )
            }.await()

            val elapsedMs = System.currentTimeMillis() - startMs
            Result.success(
                buildSyncStateFromMatch(
                    match = updatedMatch,
                    localSeat = actorSeat,
                    localClientName = cleanActor,
                    latencyMs = elapsedMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically mutates a single zone on the synced board (deploy, update, or remove if
     * `placement == null`) inside a Firestore transaction, merging cleanly with any concurrent
     * placements made by the peer client on other zones.
     */
    suspend fun syncSingleZoneMutation(
        matchId: String,
        zoneId: String,
        placement: SerializedZonePlacement?,
        actorSeat: SyncClientSeat,
        actorLabel: String,
        eventType: String,
        eventSummary: String
    ): Result<RealtimeGameSyncState> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId"
        return try {
            val uid = requireAuthenticatedUid()
            val docRef = db.collection(COLLECTION_MATCHES).document(trimmedId)
            val startMs = System.currentTimeMillis()
            val cleanActor = actorLabel.trim().ifBlank { actorSeat.shortLabel }.take(56)
            val normalizedZoneId = zoneId.trim().uppercase()

            val updatedMatch = db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Match room '$trimmedId' not found.")
                }
                val current = snapshot.toObject(
                    MatchSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Invalid match document.")

                if (current.status.equals("FINISHED", ignoreCase = true)) {
                    throw IllegalStateException("Match is already FINISHED.")
                }

                val currentBoard = SerializedGameBoardState.fromMatchSession(current)
                val mergedZones = currentBoard.zonePlacements.toMutableMap()
                if (placement == null) {
                    mergedZones.remove(normalizedZoneId)
                } else {
                    mergedZones[normalizedZoneId] = placement.copy(zoneId = normalizedZoneId)
                }

                val (hostDescriptors, opponentDescriptors) = partitionSyncablePlacements(mergedZones)
                val updatedClients = ensureClientInRoster(current.connectedClients, cleanActor)
                val nextRev = currentBoard.revisionNumber + 1
                val nextStatus = if (current.status == "OPEN_LOBBY") "ACTIVE" else current.status

                val envelope = currentBoard.copy(
                    revisionNumber = nextRev,
                    connectedClients = updatedClients,
                    lastBroadcastClientId = uid,
                    lastBroadcastClientName = cleanActor,
                    lastEventType = eventType.take(24),
                    lastEventSummary = eventSummary.take(120),
                    zonePlacements = mergedZones,
                    syncedTimestampMillis = System.currentTimeMillis()
                ).toCompactSyncEnvelopeLogEntry()

                val humanLog = "ZONE_SYNC [$cleanActor • Rev #$nextRev]: $eventSummary".take(280)
                val nextLogs = buildUpdatedActionLog(current.actionLog, humanLog, envelope)

                transaction.update(
                    docRef,
                    mapOf(
                        "connectedClients" to updatedClients,
                        "hostBoardCards" to hostDescriptors,
                        "opponentBoardCards" to opponentDescriptors,
                        "actionLog" to nextLogs,
                        "status" to nextStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                current.copy(
                    connectedClients = updatedClients,
                    hostBoardCards = hostDescriptors,
                    opponentBoardCards = opponentDescriptors,
                    actionLog = nextLogs,
                    status = nextStatus
                )
            }.await()

            val elapsedMs = System.currentTimeMillis() - startMs
            Result.success(
                buildSyncStateFromMatch(
                    match = updatedMatch,
                    localSeat = actorSeat,
                    localClientName = cleanActor,
                    latencyMs = elapsedMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically toggles a synced board card's orientation (`ATK/UNTAPPED` <-> `DEF/TAPPED`)
     * inside a Firestore transaction.
     */
    suspend fun toggleZoneOrientationAtomic(
        matchId: String,
        zoneId: String,
        actorSeat: SyncClientSeat,
        actorLabel: String
    ): Result<RealtimeGameSyncState> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId"
        return try {
            val uid = requireAuthenticatedUid()
            val docRef = db.collection(COLLECTION_MATCHES).document(trimmedId)
            val startMs = System.currentTimeMillis()
            val cleanActor = actorLabel.trim().ifBlank { actorSeat.shortLabel }.take(56)
            val normalizedZoneId = zoneId.trim().uppercase()

            val updatedMatch = db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Match room '$trimmedId' not found.")
                }
                val current = snapshot.toObject(
                    MatchSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Invalid match document.")

                if (current.status.equals("FINISHED", ignoreCase = true)) {
                    throw IllegalStateException("Match is already FINISHED.")
                }

                val currentBoard = SerializedGameBoardState.fromMatchSession(current)
                val mergedZones = currentBoard.zonePlacements.toMutableMap()
                val existing = mergedZones[normalizedZoneId]
                    ?: throw IllegalStateException("Zone [$normalizedZoneId] is empty.")

                val toggled = existing.copy(isDefenseOrTapped = !existing.isDefenseOrTapped)
                mergedZones[normalizedZoneId] = toggled
                val posLabel = if (toggled.isDefenseOrTapped) "DEF/TAPPED" else "ATK/UNTAPPED"
                val summary = "Rotated '${toggled.cardName}' at [$normalizedZoneId] -> $posLabel"

                val (hostDescriptors, opponentDescriptors) = partitionSyncablePlacements(mergedZones)
                val updatedClients = ensureClientInRoster(current.connectedClients, cleanActor)
                val nextRev = currentBoard.revisionNumber + 1
                val nextStatus = if (current.status == "OPEN_LOBBY") "ACTIVE" else current.status

                val envelope = currentBoard.copy(
                    revisionNumber = nextRev,
                    connectedClients = updatedClients,
                    lastBroadcastClientId = uid,
                    lastBroadcastClientName = cleanActor,
                    lastEventType = "ZONE_ORIENTATION",
                    lastEventSummary = summary,
                    zonePlacements = mergedZones,
                    syncedTimestampMillis = System.currentTimeMillis()
                ).toCompactSyncEnvelopeLogEntry()

                val humanLog = "ZONE_ROTATE [$cleanActor • Rev #$nextRev]: $summary".take(280)
                val nextLogs = buildUpdatedActionLog(current.actionLog, humanLog, envelope)

                transaction.update(
                    docRef,
                    mapOf(
                        "connectedClients" to updatedClients,
                        "hostBoardCards" to hostDescriptors,
                        "opponentBoardCards" to opponentDescriptors,
                        "actionLog" to nextLogs,
                        "status" to nextStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                current.copy(
                    connectedClients = updatedClients,
                    hostBoardCards = hostDescriptors,
                    opponentBoardCards = opponentDescriptors,
                    actionLog = nextLogs,
                    status = nextStatus
                )
            }.await()

            val elapsedMs = System.currentTimeMillis() - startMs
            Result.success(
                buildSyncStateFromMatch(
                    match = updatedMatch,
                    localSeat = actorSeat,
                    localClientName = cleanActor,
                    latencyMs = elapsedMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Atomically resets life points to the active variant's starting value and clears both
     * synced board card zones (valid while match is not `FINISHED`).
     */
    suspend fun resetLifeAndBoardAtomic(
        matchId: String,
        actorSeat: SyncClientSeat,
        actorLabel: String
    ): Result<RealtimeGameSyncState> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId"
        return try {
            val uid = requireAuthenticatedUid()
            val docRef = db.collection(COLLECTION_MATCHES).document(trimmedId)
            val startMs = System.currentTimeMillis()
            val cleanActor = actorLabel.trim().ifBlank { actorSeat.shortLabel }.take(56)

            val updatedMatch = db.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Match room '$trimmedId' not found.")
                }
                val current = snapshot.toObject(
                    MatchSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: throw IllegalStateException("Invalid match document.")

                if (current.status.equals("FINISHED", ignoreCase = true)) {
                    throw IllegalStateException("Cannot reset a FINISHED match. Launch a new Arena match room.")
                }

                val baseSpec = BuiltInTcgCatalog.findBaseSpec(current.baseVariantId)
                val resetLife = baseSpec.startingLife.coerceIn(1L, MAX_LIFE_BOUND)
                val updatedClients = ensureClientInRoster(current.connectedClients, cleanActor)
                val currentBoard = SerializedGameBoardState.fromMatchSession(current)
                val nextRev = currentBoard.revisionNumber + 1
                val summary = "Reset synced LP to $resetLife/$resetLife & cleared battlefield zones"

                val envelope = currentBoard.copy(
                    revisionNumber = nextRev,
                    turnNumber = 1L,
                    activePhase = "MAIN",
                    hostLife = resetLife,
                    opponentLife = resetLife,
                    connectedClients = updatedClients,
                    lastBroadcastClientId = uid,
                    lastBroadcastClientName = cleanActor,
                    lastEventType = "SYNC_RESET",
                    lastEventSummary = summary,
                    zonePlacements = emptyMap(),
                    syncedTimestampMillis = System.currentTimeMillis()
                ).toCompactSyncEnvelopeLogEntry()

                val humanLog = "SYNC_RESET [$cleanActor • Rev #$nextRev]: $summary".take(280)
                val nextLogs = buildUpdatedActionLog(current.actionLog, humanLog, envelope)

                transaction.update(
                    docRef,
                    mapOf(
                        "connectedClients" to updatedClients,
                        "turnNumber" to 1L,
                        "activePhase" to "MAIN",
                        "hostLife" to resetLife,
                        "opponentLife" to resetLife,
                        "hostBoardCards" to emptyList<String>(),
                        "opponentBoardCards" to emptyList<String>(),
                        "actionLog" to nextLogs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                current.copy(
                    connectedClients = updatedClients,
                    turnNumber = 1L,
                    activePhase = "MAIN",
                    hostLife = resetLife,
                    opponentLife = resetLife,
                    hostBoardCards = emptyList(),
                    opponentBoardCards = emptyList(),
                    actionLog = nextLogs
                )
            }.await()

            val elapsedMs = System.currentTimeMillis() - startMs
            Result.success(
                buildSyncStateFromMatch(
                    match = updatedMatch,
                    localSeat = actorSeat,
                    localClientName = cleanActor,
                    latencyMs = elapsedMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun ensureClientInRoster(existing: List<String>, actorLabel: String): List<String> {
        val clean = actorLabel.trim().take(60)
        if (clean.isBlank()) return existing.take(MAX_CONNECTED_CLIENTS)
        return (existing + clean).distinct().take(MAX_CONNECTED_CLIENTS)
    }

    private fun buildUpdatedActionLog(
        existingLogs: List<String>,
        newHumanLog: String,
        newSyncEnvelope: String
    ): List<String> {
        val preservedHumanLogs = existingLogs
            .filterNot { it.startsWith(SerializedGameBoardState.SYNC_ENVELOPE_PREFIX) }
            .takeLast(MAX_ACTION_LOG_ITEMS - 2)
        return (preservedHumanLogs + newHumanLog + newSyncEnvelope).takeLast(MAX_ACTION_LOG_ITEMS)
    }

    companion object {
        private const val TAG = "FirestoreGameStateMgr"
        private const val COLLECTION_MATCHES = "matches"
        private const val MAX_CONNECTED_CLIENTS = 10
        private const val MAX_BOARD_CARDS_PER_PLAYER = 12
        private const val MAX_ACTION_LOG_ITEMS = 20
        private const val MAX_TURN_NUMBER = 500L
        private const val MIN_LIFE_BOUND = -999999999999999999L
        private const val MAX_LIFE_BOUND = 999999999999999999L
        private val VALID_PHASES = setOf("DRAW", "MAIN", "COMBAT", "END")
    }
}
