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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a single zone placement on the 2-Player Canvas GameBoard for real-time
 * Firebase Firestore state serialization and broadcast between two connected clients.
 */
data class SerializedZonePlacement(
    val zoneId: String = "",
    val owner: String = "PLAYER_1_HOST", // PLAYER_1_HOST, PLAYER_2_OPPONENT, SHARED_NEXUS
    val cardId: String = "",
    val cardName: String = "",
    val cost: Int = 0,
    val attack: Int = 0,
    val defense: Int = 0,
    val cardType: String = "Monster / Effect",
    val abilityText: String = "",
    val attack21: String = "0",
    val defense21: String = "0",
    val passcode: String = "89631139",
    val isDefenseOrTapped: Boolean = false,
    val stackCount: Int = 1,
    val rawDescriptor: String = ""
) {
    /**
     * Encodes the zone placement into a Firestore-safe board descriptor string (`<= 295` chars)
     * that preserves full backwards compatibility with `CardTemplate.fromBoardDescriptor(...)`
     * while embedding exact `CanvasGameBoard` zone coordinates (`P1_MAIN_0`, `P2_MAIN_1`, `SHARED_STACK`, etc.),
     * orientation (`ATK`/`DEF`), and stack depth.
     *
     * Format: `@Z:<zoneId>:<ATK|DEF>:<stackCount>|<name>|<cost>|<atk>|<def>|<type>|<ability>|<atk21>|<def21>|<passcode>`
     */
    fun toZoneBoardDescriptor(): String {
        val posCode = if (isDefenseOrTapped) "DEF" else "ATK"
        val cleanName = cardName.replace("|", "/").replace("@Z:", "").take(42)
        val cleanAbility = abilityText.replace("|", "/").take(68)
        val cleanAtk21 = attack21.filter { it.isDigit() }.ifEmpty { attack.coerceAtLeast(0).toString() }.take(21)
        val cleanDef21 = defense21.filter { it.isDigit() }.ifEmpty { defense.coerceAtLeast(0).toString() }.take(21)
        val cleanPasscode = passcode.filter { it.isDigit() }.ifEmpty { "89631139" }.take(21)
        return "@Z:$zoneId:$posCode:$stackCount|$cleanName|$cost|$attack|$defense|$cardType|$cleanAbility|$cleanAtk21|$cleanDef21|$cleanPasscode"
            .take(295)
    }

    fun toCardTemplate(baseVariantId: String): CardTemplate {
        return CardTemplate.fromBoardDescriptor(toZoneBoardDescriptor(), baseVariantId)
    }

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("zoneId", zoneId)
            put("owner", owner)
            put("cardId", cardId)
            put("cardName", cardName)
            put("cost", cost)
            put("attack", attack)
            put("defense", defense)
            put("cardType", cardType)
            put("abilityText", abilityText.take(80))
            put("attack21", attack21)
            put("defense21", defense21)
            put("passcode", passcode)
            put("isDefenseOrTapped", isDefenseOrTapped)
            put("stackCount", stackCount)
        }
    }

    companion object {
        fun fromZoneBoardDescriptor(
            descriptor: String,
            defaultOwner: String,
            baseVariantId: String,
            indexFallback: Int
        ): SerializedZonePlacement {
            var zoneId = if (defaultOwner == "PLAYER_1_HOST") {
                "P1_MAIN_${indexFallback % 5}"
            } else {
                "P2_MAIN_${indexFallback % 5}"
            }
            var isDef = false
            var stackCount = 1
            var coreDescriptor = descriptor

            if (descriptor.startsWith("@Z:")) {
                val pipeIdx = descriptor.indexOf('|')
                if (pipeIdx > 3) {
                    val header = descriptor.substring(3, pipeIdx)
                    val headerParts = header.split(":")
                    if (headerParts.isNotEmpty() && headerParts[0].isNotBlank()) {
                        zoneId = headerParts[0]
                    }
                    if (headerParts.size >= 2) {
                        isDef = headerParts[1].equals("DEF", ignoreCase = true) ||
                            headerParts[1].equals("TAP", ignoreCase = true)
                    }
                    if (headerParts.size >= 3) {
                        stackCount = headerParts[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
                    }
                    coreDescriptor = descriptor.substring(pipeIdx + 1)
                }
            }

            val resolvedOwner = when {
                zoneId.startsWith("P1_", ignoreCase = true) -> "PLAYER_1_HOST"
                zoneId.startsWith("P2_", ignoreCase = true) -> "PLAYER_2_OPPONENT"
                zoneId.startsWith("SHARED_", ignoreCase = true) -> "SHARED_NEXUS"
                else -> defaultOwner
            }

            val card = CardTemplate.fromBoardDescriptor(coreDescriptor, baseVariantId)
            val resolvedZoneId = if (!descriptor.startsWith("@Z:")) {
                val isBackrow = card.cardType.contains("Spell", ignoreCase = true) ||
                    card.cardType.contains("Trap", ignoreCase = true)
                val prefix = if (resolvedOwner == "PLAYER_1_HOST") "P1" else "P2"
                val row = if (isBackrow) "SUP" else "MAIN"
                "${prefix}_${row}_${indexFallback % 5}"
            } else {
                zoneId.uppercase()
            }

            return SerializedZonePlacement(
                zoneId = resolvedZoneId,
                owner = resolvedOwner,
                cardId = card.id,
                cardName = card.name,
                cost = card.cost,
                attack = card.attack,
                defense = card.defense,
                cardType = card.cardType,
                abilityText = card.abilityText,
                attack21 = card.attack21,
                defense21 = card.defense21,
                passcode = card.passcode,
                isDefenseOrTapped = isDef,
                stackCount = stackCount,
                rawDescriptor = descriptor
            )
        }

        fun fromJsonObject(obj: JSONObject): SerializedZonePlacement {
            return SerializedZonePlacement(
                zoneId = obj.optString("zoneId", "P1_MAIN_0"),
                owner = obj.optString("owner", "PLAYER_1_HOST"),
                cardId = obj.optString("cardId", ""),
                cardName = obj.optString("cardName", "Synced Construct"),
                cost = obj.optInt("cost", 4),
                attack = obj.optInt("attack", 2500),
                defense = obj.optInt("defense", 2000),
                cardType = obj.optString("cardType", "Monster / Effect"),
                abilityText = obj.optString("abilityText", ""),
                attack21 = obj.optString("attack21", "2500"),
                defense21 = obj.optString("defense21", "2000"),
                passcode = obj.optString("passcode", "89631139"),
                isDefenseOrTapped = obj.optBoolean("isDefenseOrTapped", false),
                stackCount = obj.optInt("stackCount", 1)
            )
        }
    }
}

/**
 * Complete serialized snapshot of the 2-Player `CanvasGameBoard` broadcast in real time
 * between two connected clients via Firebase Firestore (`/matches/{matchId}`).
 */
data class SerializedGameBoardState(
    val matchId: String = "",
    val baseVariantId: String = "EDOPRO_BASE_MR5",
    val baseVariantName: String = "ProjectIgnis EDOPro Base",
    val revisionNumber: Int = 1,
    val turnNumber: Long = 1L,
    val activePhase: String = "MAIN",
    val hostLife: Long = 8000L,
    val opponentLife: Long = 8000L,
    val hostResources: Long = 5L,
    val opponentResources: Long = 5L,
    val connectedClients: List<String> = emptyList(),
    val lastBroadcastClientId: String = "",
    val lastBroadcastClientName: String = "Host Client",
    val lastEventType: String = "BOARD_SYNC",
    val lastEventSummary: String = "Initial GameBoard state synchronized",
    val zonePlacements: Map<String, SerializedZonePlacement> = emptyMap(),
    val syncedTimestampMillis: Long = System.currentTimeMillis()
) {
    /**
     * Deterministic JSON representation of the entire GameBoard state for cross-client
     * verification, inspection, and real-time wire serialization.
     */
    fun toJsonString(pretty: Boolean = false): String {
        val root = JSONObject().apply {
            put("protocol", "NEXUS_FIRESTORE_BOARD_SYNC_V1")
            put("matchId", matchId)
            put("baseVariantId", baseVariantId)
            put("baseVariantName", baseVariantName)
            put("revision", revisionNumber)
            put("turnNumber", turnNumber)
            put("activePhase", activePhase)
            put("hostLife", hostLife)
            put("opponentLife", opponentLife)
            put("hostResources", hostResources)
            put("opponentResources", opponentResources)
            put("connectedClients", JSONArray(connectedClients))
            put("senderClientId", lastBroadcastClientId)
            put("senderClientName", lastBroadcastClientName)
            put("eventType", lastEventType)
            put("eventSummary", lastEventSummary)
            put("timestampMs", syncedTimestampMillis)
            val zonesArray = JSONArray()
            zonePlacements.entries.sortedBy { it.key }.forEach { (_, placement) ->
                zonesArray.put(placement.toJsonObject())
            }
            put("zones", zonesArray)
        }
        return if (pretty) root.toString(2) else root.toString()
    }

    /**
     * Compact `<= 480` char metadata packet stored inside `actionLog` so connected clients
     * know the exact sender client ID, revision counter, and event type alongside the
     * zone-encoded `hostBoardCards` and `opponentBoardCards`.
     */
    fun toCompactSyncEnvelopeLogEntry(): String {
        val compact = JSONObject().apply {
            put("rev", revisionNumber)
            put("cid", lastBroadcastClientId.take(24))
            put("cname", lastBroadcastClientName.take(28))
            put("evt", lastEventType.take(24))
            put("msg", lastEventSummary.take(120))
            put("zones", zonePlacements.size)
            put("ts", syncedTimestampMillis)
        }
        return "$SYNC_ENVELOPE_PREFIX$compact"
    }

    companion object {
        const val SYNC_ENVELOPE_PREFIX = "SYNC_STATE_JSON|"

        fun fromJsonString(jsonString: String): SerializedGameBoardState? {
            return try {
                val root = JSONObject(jsonString)
                val matchId = root.optString("matchId", "")
                val zonesMap = mutableMapOf<String, SerializedZonePlacement>()
                val zonesArray = root.optJSONArray("zones")
                if (zonesArray != null) {
                    for (i in 0 until zonesArray.length()) {
                        val obj = zonesArray.optJSONObject(i) ?: continue
                        val placement = SerializedZonePlacement.fromJsonObject(obj)
                        zonesMap[placement.zoneId] = placement
                    }
                }
                val clientsList = mutableListOf<String>()
                val clientsArray = root.optJSONArray("connectedClients")
                if (clientsArray != null) {
                    for (i in 0 until clientsArray.length()) {
                        clientsList.add(clientsArray.optString(i))
                    }
                }
                SerializedGameBoardState(
                    matchId = matchId,
                    baseVariantId = root.optString("baseVariantId", "EDOPRO_BASE_MR5"),
                    baseVariantName = root.optString("baseVariantName", "ProjectIgnis EDOPro Base"),
                    revisionNumber = root.optInt("revision", 1),
                    turnNumber = root.optLong("turnNumber", 1L),
                    activePhase = root.optString("activePhase", "MAIN"),
                    hostLife = root.optLong("hostLife", 8000L),
                    opponentLife = root.optLong("opponentLife", 8000L),
                    hostResources = root.optLong("hostResources", 5L),
                    opponentResources = root.optLong("opponentResources", 5L),
                    connectedClients = clientsList,
                    lastBroadcastClientId = root.optString("senderClientId", ""),
                    lastBroadcastClientName = root.optString("senderClientName", "Connected Peer"),
                    lastEventType = root.optString("eventType", "BOARD_SYNC"),
                    lastEventSummary = root.optString("eventSummary", "Synchronized via Firestore"),
                    zonePlacements = zonesMap,
                    syncedTimestampMillis = root.optLong("timestampMs", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                Log.w("GameBoardSyncService", "Failed to parse SerializedGameBoardState JSON: ${e.message}")
                null
            }
        }

        /**
         * Reconstructs the full `SerializedGameBoardState` from a Firestore `MatchSession` document.
         */
        fun fromMatchSession(match: MatchSession): SerializedGameBoardState {
            val placements = linkedMapOf<String, SerializedZonePlacement>()

            match.hostBoardCards.forEachIndexed { idx, descriptor ->
                val placement = SerializedZonePlacement.fromZoneBoardDescriptor(
                    descriptor = descriptor,
                    defaultOwner = "PLAYER_1_HOST",
                    baseVariantId = match.baseVariantId,
                    indexFallback = idx
                )
                placements[placement.zoneId] = placement
            }

            match.opponentBoardCards.forEachIndexed { idx, descriptor ->
                val placement = SerializedZonePlacement.fromZoneBoardDescriptor(
                    descriptor = descriptor,
                    defaultOwner = "PLAYER_2_OPPONENT",
                    baseVariantId = match.baseVariantId,
                    indexFallback = idx
                )
                placements[placement.zoneId] = placement
            }

            var revision = match.turnNumber.toInt() + match.actionLog.size
            var senderClientId = match.hostUserId
            var senderClientName = match.connectedClients.lastOrNull() ?: "Client-1 (Host)"
            var eventType = "FIRESTORE_SNAPSHOT"
            var eventSummary = match.actionLog.lastOrNull { !it.startsWith(SYNC_ENVELOPE_PREFIX) }
                ?: "Live Firestore GameBoard state synchronized"
            var timestampMs = match.updatedAt?.toDate()?.time ?: System.currentTimeMillis()

            val latestEnvelope = match.actionLog.lastOrNull { it.startsWith(SYNC_ENVELOPE_PREFIX) }
            if (latestEnvelope != null) {
                try {
                    val jsonPart = latestEnvelope.removePrefix(SYNC_ENVELOPE_PREFIX)
                    val env = JSONObject(jsonPart)
                    revision = env.optInt("rev", revision)
                    senderClientId = env.optString("cid", senderClientId)
                    senderClientName = env.optString("cname", senderClientName)
                    eventType = env.optString("evt", eventType)
                    eventSummary = env.optString("msg", eventSummary)
                    timestampMs = env.optLong("ts", timestampMs)
                } catch (_: Exception) {
                }
            }

            return SerializedGameBoardState(
                matchId = match.id,
                baseVariantId = match.baseVariantId,
                baseVariantName = match.baseVariantName,
                revisionNumber = revision,
                turnNumber = match.turnNumber,
                activePhase = match.activePhase,
                hostLife = match.hostLife,
                opponentLife = match.opponentLife,
                hostResources = match.hostResources,
                opponentResources = match.opponentResources,
                connectedClients = match.connectedClients,
                lastBroadcastClientId = senderClientId,
                lastBroadcastClientName = senderClientName,
                lastEventType = eventType,
                lastEventSummary = eventSummary,
                zonePlacements = placements,
                syncedTimestampMillis = timestampMs
            )
        }
    }
}

/**
 * Real-time Firebase Firestore synchronization service that broadcasts and observes the
 * serialized 2-Player `CanvasGameBoard` state between two connected clients (`/matches/{matchId}`).
 */
class GameBoardSyncService(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun normalizeFirestorePhase(rawPhase: String): String {
        val upper = rawPhase.trim().uppercase()
        return when {
            upper in listOf("DRAW", "MAIN", "COMBAT", "END") -> upper
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

    /**
     * Observes real-time updates to a match's serialized `GameBoard` state from `/matches/{matchId}`
     * using Firestore `snapshots()` so both connected clients receive sub-second drag/drop,
     * card deployment, rotation (`ATK`/`DEF`), and combat broadcasts.
     */
    fun observeRealtimeBoardState(matchId: String): Flow<SerializedGameBoardState?> {
        val path = "matches/$matchId"
        return db.collection("matches")
            .document(matchId)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) {
                    null
                } else {
                    val match = snapshot.toObject(
                        MatchSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    match?.let { SerializedGameBoardState.fromMatchSession(it) }
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, path)
                }
                throw error
            }
    }

    /**
     * Broadcasts the caller client's serialized `GameBoard` zone map and metadata to
     * `/matches/{matchId}` in Firebase Firestore so any connected peer client immediately
     * renders the updated card positions, orientations (`ATK`/`DEF`), and stack counts.
     */
    suspend fun broadcastBoardState(
        match: MatchSession,
        zonePlacements: Map<String, SerializedZonePlacement>,
        clientLabel: String,
        eventType: String,
        eventSummary: String,
        nextRevision: Int,
        updatedTurnNumber: Long = match.turnNumber,
        updatedPhase: String = match.activePhase,
        updatedHostLife: Long = match.hostLife,
        updatedOpponentLife: Long = match.opponentLife
    ): Result<SerializedGameBoardState> = runCatching {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before broadcasting Firestore board state.")
        val docRef = db.collection("matches").document(match.id)

        // Filter out local-only Hand Dock slots and Deck/GY placeholders with 40 cards unless modified,
        // and partition Battlefield/Support/Shared zones into hostBoardCards (<= 12) & opponentBoardCards (<= 12)
        val syncableZones = zonePlacements.filterKeys { key ->
            !key.startsWith("P1_HAND_", ignoreCase = true) &&
                key != "P1_DECK" &&
                key != "P2_DECK"
        }

        val hostDescriptors = syncableZones.values
            .filter {
                it.owner == "PLAYER_1_HOST" ||
                    it.owner == "SHARED_NEXUS" ||
                    it.zoneId.startsWith("P1_", ignoreCase = true) ||
                    it.zoneId.startsWith("SHARED_", ignoreCase = true)
            }
            .sortedBy { it.zoneId }
            .take(12)
            .map { it.toZoneBoardDescriptor() }

        val opponentDescriptors = syncableZones.values
            .filter {
                it.owner == "PLAYER_2_OPPONENT" ||
                    it.zoneId.startsWith("P2_", ignoreCase = true)
            }
            .sortedBy { it.zoneId }
            .take(12)
            .map { it.toZoneBoardDescriptor() }

        val cleanClientLabel = clientLabel.trim().ifBlank { "Connected Client" }.take(60)
        val updatedClients = if (!match.connectedClients.contains(cleanClientLabel)) {
            (match.connectedClients + cleanClientLabel).distinct().take(10)
        } else {
            match.connectedClients.take(10)
        }

        val safePhase = normalizeFirestorePhase(updatedPhase)
        val safeTurn = updatedTurnNumber.coerceIn(1L, 500L)
        val safeHostLife = updatedHostLife.coerceIn(-999999999999999999L, 999999999999999999L)
        val safeOppLife = updatedOpponentLife.coerceIn(-999999999999999999L, 999999999999999999L)
        val nextStatus = when {
            safeHostLife <= 0L || safeOppLife <= 0L -> "FINISHED"
            match.status == "OPEN_LOBBY" -> "ACTIVE"
            else -> match.status
        }

        val serializedState = SerializedGameBoardState(
            matchId = match.id,
            baseVariantId = match.baseVariantId,
            baseVariantName = match.baseVariantName,
            revisionNumber = nextRevision,
            turnNumber = safeTurn,
            activePhase = safePhase,
            hostLife = safeHostLife,
            opponentLife = safeOppLife,
            hostResources = match.hostResources,
            opponentResources = match.opponentResources,
            connectedClients = updatedClients,
            lastBroadcastClientId = uid,
            lastBroadcastClientName = cleanClientLabel,
            lastEventType = eventType,
            lastEventSummary = eventSummary,
            zonePlacements = syncableZones,
            syncedTimestampMillis = System.currentTimeMillis()
        )

        val formattedSummary = if (
            !eventSummary.contains("moved ", ignoreCase = true) &&
            (eventType.contains("MOVE", ignoreCase = true) ||
                eventType.contains("DEPLOY", ignoreCase = true) ||
                eventType.contains("DRAG", ignoreCase = true))
        ) {
            "Player 1 ($cleanClientLabel) $eventSummary"
        } else {
            eventSummary
        }

        val humanReadableLog = "MOVE_HISTORY [$cleanClientLabel • Rev #$nextRevision]: $formattedSummary".take(280)
        val syncEnvelopeLog = serializedState.toCompactSyncEnvelopeLogEntry()

        val filteredExistingLogs = match.actionLog
            .filterNot { it.startsWith(SerializedGameBoardState.SYNC_ENVELOPE_PREFIX) }
            .takeLast(18)

        val updatedActionLog = (filteredExistingLogs + humanReadableLog + syncEnvelopeLog).takeLast(20)

        val updates = mapOf(
            "connectedClients" to updatedClients,
            "turnNumber" to safeTurn,
            "activePhase" to safePhase,
            "hostLife" to safeHostLife,
            "opponentLife" to safeOppLife,
            "hostResources" to match.hostResources.coerceIn(0L, 30L),
            "hostMaxResources" to match.hostMaxResources.coerceIn(1L, 30L),
            "opponentResources" to match.opponentResources.coerceIn(0L, 30L),
            "hostBoardCards" to hostDescriptors,
            "opponentBoardCards" to opponentDescriptors,
            "actionLog" to updatedActionLog,
            "status" to nextStatus,
            "winnerName" to match.winnerName.take(60),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }

        serializedState
    }

    /**
     * Simulates or executes a connected second peer client (`Client-2 Peer`) broadcasting
     * a real-time counter-move (zone drag reposition, orientation toggle, or new construct deployment)
     * directly through `/matches/{matchId}` in Firebase Firestore so two connected clients
     * can test and verify bi-directional real-time synchronization.
     */
    suspend fun broadcastPeerClientCounterMove(
        match: MatchSession,
        currentPlacements: Map<String, SerializedZonePlacement>,
        currentRevision: Int
    ): Result<SerializedGameBoardState> {
        val mutablePlacements = currentPlacements
            .filterKeys { !it.startsWith("P1_HAND_", ignoreCase = true) && it != "P1_DECK" && it != "P2_DECK" }
            .toMutableMap()

        val peerClientName = match.connectedClients.getOrNull(1)
            ?: "Peer Client #2 (${match.baseVariantId})"

        val candidateP2MainZones = listOf("P2_MAIN_0", "P2_MAIN_1", "P2_MAIN_2", "P2_MAIN_3", "P2_MAIN_4")
        val existingP2Cards = mutablePlacements.values.filter { it.zoneId.startsWith("P2_MAIN_") }

        val eventType: String
        val eventSummary: String

        val emptyP2Zone = candidateP2MainZones.firstOrNull { !mutablePlacements.containsKey(it) }
        if (existingP2Cards.size >= 2 && emptyP2Zone != null && currentRevision % 2 == 1) {
            // Connected Peer Client #2 drags one of its existing cards to an open tactical zone and rotates it
            val movingCard = existingP2Cards.first()
            mutablePlacements.remove(movingCard.zoneId)
            val newOrientation = !movingCard.isDefenseOrTapped
            val movedPlacement = movingCard.copy(
                zoneId = emptyP2Zone,
                owner = "PLAYER_2_OPPONENT",
                isDefenseOrTapped = newOrientation
            )
            mutablePlacements[emptyP2Zone] = movedPlacement
            val posLabel = if (newOrientation) "DEF/TAPPED (90°)" else "ATK/UNTAPPED (0°)"
            eventType = "PEER_DRAG_REPOSITION"
            eventSummary = "Player 2 ($peerClientName) moved '${movingCard.cardName}' from [${movingCard.zoneId}] to Field [$emptyP2Zone] ($posLabel)"
        } else {
            // Connected Peer Client #2 deploys a new construct from its hand into a P2 zone
            val targetZone = emptyP2Zone ?: candidateP2MainZones[currentRevision % candidateP2MainZones.size]
            val catalogPool = BuiltInTcgCatalog.cardsForVariant(match.baseVariantId)
            val templateCard = catalogPool[(currentRevision + 1) % catalogPool.size]
            val existingInTarget = mutablePlacements[targetZone]

            if (existingInTarget != null) {
                val updated = existingInTarget.copy(
                    stackCount = (existingInTarget.stackCount + 1).coerceAtMost(9),
                    isDefenseOrTapped = !existingInTarget.isDefenseOrTapped
                )
                mutablePlacements[targetZone] = updated
                eventType = "PEER_STACK_OVERLAY"
                eventSummary = "Player 2 ($peerClientName) moved '${updated.cardName}' to Overlay Stack at Field [$targetZone] (x${updated.stackCount})"
            } else {
                val newPlacement = SerializedZonePlacement(
                    zoneId = targetZone,
                    owner = "PLAYER_2_OPPONENT",
                    cardId = "peer_${templateCard.id}_$currentRevision",
                    cardName = templateCard.name,
                    cost = templateCard.cost,
                    attack = templateCard.attack,
                    defense = templateCard.defense,
                    cardType = templateCard.cardType,
                    abilityText = templateCard.abilityText,
                    attack21 = templateCard.attack21,
                    defense21 = templateCard.defense21,
                    passcode = templateCard.passcode,
                    isDefenseOrTapped = false,
                    stackCount = 1
                )
                mutablePlacements[targetZone] = newPlacement
                eventType = "PEER_CARD_DEPLOY"
                eventSummary = "Player 2 ($peerClientName) moved '${templateCard.name}' (${templateCard.attack}/${templateCard.defense}) to Field [$targetZone]"
            }
        }

        return broadcastBoardState(
            match = match,
            zonePlacements = mutablePlacements,
            clientLabel = peerClientName,
            eventType = eventType,
            eventSummary = eventSummary,
            nextRevision = currentRevision + 1
        )
    }
}
