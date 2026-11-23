package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured categories for persistent game events synchronized via Firestore
 * under `/matches/{matchId}/events/{eventId}`.
 */
enum class PersistentGameEventType(
    val code: String,
    val displayLabel: String,
    val shortBadge: String
) {
    CARD_PLAYED("CARD_PLAYED", "Card Plays", "CARD PLAY"),
    LIFE_CHANGED("LIFE_CHANGED", "Life Points", "LP DELTA"),
    BOARD_MOVE("BOARD_MOVE", "Board Moves", "BOARD MOVE"),
    COMBAT_ATTACK("COMBAT_ATTACK", "Combat", "COMBAT"),
    PHASE_CHANGED("PHASE_CHANGED", "Phases", "PHASE"),
    SYSTEM_SYNC("SYSTEM_SYNC", "System Sync", "SYNC"),
    CUSTOM_EVENT("CUSTOM_EVENT", "Custom", "LOG");

    companion object {
        fun fromCode(code: String): PersistentGameEventType {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: CUSTOM_EVENT
        }
    }
}

/**
 * Persistent immutable game event document stored in Firestore at `/matches/{matchId}/events/{eventId}`.
 *
 * Examples of synchronized `summary` strings:
 * - `"Player 1 played Blue-Eyes White Dragon"`
 * - `"Life points reduced by 5 (Player 2: 20 -> 15)"`
 * - `"Player 1 moved Serra Archon to [P1_MAIN_0]"`
 */
data class MatchGameEvent(
    @DocumentId val id: String = "",
    val matchId: String = "",
    val actorUserId: String = "",
    val actorLabel: String = "Player 1",
    val eventType: String = PersistentGameEventType.CUSTOM_EVENT.code,
    val summary: String = "",
    val detail: String = "",
    val turnNumber: Long = 1L,
    val activePhase: String = "MAIN",
    val cardName: String = "",
    val lifeDelta: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedType: PersistentGameEventType
        get() = PersistentGameEventType.fromCode(eventType)

    fun formattedTime(): String {
        val date = createdAt?.toDate() ?: Date()
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(date)
    }

    /**
     * Exact Firestore create payload matching `/matches/{matchId}/events/{eventId}` in `firestore.rules`.
     * Explicitly omits `@DocumentId` (`id`) and stamps `createdAt` with `FieldValue.serverTimestamp()`.
     */
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "matchId" to matchId.trim().take(128),
            "actorUserId" to actorUserId.trim().take(128),
            "actorLabel" to actorLabel.trim().ifBlank { "Player 1" }.take(60),
            "eventType" to PersistentGameEventType.fromCode(eventType).code,
            "summary" to summary.trim().ifBlank { "Game event recorded" }.take(240),
            "detail" to detail.trim().take(240),
            "turnNumber" to turnNumber.coerceIn(1L, 500L),
            "activePhase" to normalizePhase(activePhase),
            "cardName" to cardName.trim().take(80),
            "lifeDelta" to lifeDelta.coerceIn(-999999999999999999L, 999999999999999999L),
            "createdAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun normalizePhase(raw: String): String {
            val upper = raw.trim().uppercase()
            return when {
                upper in setOf("DRAW", "MAIN", "COMBAT", "END") -> upper
                upper.contains("DRAW") || upper == "DP" || upper == "SP" ||
                    upper.contains("UNTAP") || upper.contains("UPKEEP") -> "DRAW"
                upper.contains("COMBAT") || upper == "BP" || upper.contains("BATTLE") -> "COMBAT"
                upper.contains("END") || upper == "EP" -> "END"
                else -> "MAIN"
            }
        }
    }
}

/**
 * Firestore-backed persistent game event log service for `/matches/{matchId}/events`.
 *
 * Persists unlimited append-only game events (card plays, life point changes, board moves,
 * combat resolutions, phase shifts, and custom notes) and streams them chronologically in real time
 * across all connected clients.
 */
class FirestoreGameEventLogService(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUid(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Google Sign-In is required to persist game events to Firestore.")
    }

    /**
     * Streams persistent game events for [matchId] in chronological order (`createdAt ASC`) via
     * Firestore real-time snapshot listeners (`snapshots()`).
     */
    fun observeMatchEvents(matchId: String): Flow<List<MatchGameEvent>> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId/events"
        return db.collection(COLLECTION_MATCHES)
            .document(trimmedId)
            .collection(SUBCOLLECTION_EVENTS)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(MAX_STREAMED_EVENTS)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(
                        MatchGameEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootException = firestoreError ?: (error as? Exception)
                if (rootException != null) {
                    handleFirestoreError(rootException, OperationType.LIST, path)
                }
                Log.w(TAG, "Persistent event log observer error on $path", rootException ?: error)
                throw error
            }
    }

    /**
     * Persists a structured [MatchGameEvent] into `/matches/{matchId}/events/{eventId}`.
     */
    suspend fun appendGameEvent(
        matchId: String,
        actorLabel: String,
        eventType: PersistentGameEventType,
        summary: String,
        detail: String = "",
        turnNumber: Long = 1L,
        activePhase: String = "MAIN",
        cardName: String = "",
        lifeDelta: Long = 0L
    ): Result<String> {
        val trimmedId = matchId.trim()
        val path = "matches/$trimmedId/events"
        return try {
            if (trimmedId.isBlank()) {
                throw IllegalArgumentException("Match ID cannot be blank when appending a persistent game event.")
            }
            val uid = requireUid()
            val event = MatchGameEvent(
                matchId = trimmedId,
                actorUserId = uid,
                actorLabel = actorLabel.trim().ifBlank { "Player 1" }.take(60),
                eventType = eventType.code,
                summary = summary.trim().ifBlank { "Game event recorded" }.take(240),
                detail = detail.trim().take(240),
                turnNumber = turnNumber.coerceIn(1L, 500L),
                activePhase = MatchGameEvent.normalizePhase(activePhase),
                cardName = cardName.trim().take(80),
                lifeDelta = lifeDelta.coerceIn(-999999999999999999L, 999999999999999999L)
            )
            val docRef = db.collection(COLLECTION_MATCHES)
                .document(trimmedId)
                .collection(SUBCOLLECTION_EVENTS)
                .add(event.toCreateMap())
                .await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun logCardPlayed(
        matchId: String,
        playerLabel: String,
        cardName: String,
        zoneLabel: String = "",
        turnNumber: Long = 1L,
        activePhase: String = "MAIN",
        detail: String = ""
    ): Result<String> {
        val cleanPlayer = playerLabel.trim().ifBlank { "Player 1" }
        val cleanCard = cardName.trim().ifBlank { "Card" }
        val headline = if (zoneLabel.isNotBlank()) {
            "$cleanPlayer played $cleanCard to [$zoneLabel]"
        } else {
            "$cleanPlayer played $cleanCard"
        }
        return appendGameEvent(
            matchId = matchId,
            actorLabel = cleanPlayer,
            eventType = PersistentGameEventType.CARD_PLAYED,
            summary = headline,
            detail = detail,
            turnNumber = turnNumber,
            activePhase = activePhase,
            cardName = cleanCard,
            lifeDelta = 0L
        )
    }

    suspend fun logLifePointChange(
        matchId: String,
        actorLabel: String,
        targetPlayerLabel: String,
        delta: Long,
        newLife: Long,
        turnNumber: Long = 1L,
        activePhase: String = "MAIN"
    ): Result<String> {
        val absDelta = kotlin.math.abs(delta)
        val previousLife = (newLife - delta).coerceAtLeast(0L)
        val headline = when {
            delta < 0L -> "Life points reduced by $absDelta ($targetPlayerLabel)"
            delta > 0L -> "Life points increased by $absDelta ($targetPlayerLabel)"
            else -> "Life points set to $newLife ($targetPlayerLabel)"
        }
        val detail = "$targetPlayerLabel LP: $previousLife -> $newLife"
        return appendGameEvent(
            matchId = matchId,
            actorLabel = actorLabel,
            eventType = PersistentGameEventType.LIFE_CHANGED,
            summary = headline,
            detail = detail,
            turnNumber = turnNumber,
            activePhase = activePhase,
            cardName = "",
            lifeDelta = delta
        )
    }

    companion object {
        private const val TAG = "FirestoreEventLogSvc"
        private const val COLLECTION_MATCHES = "matches"
        private const val SUBCOLLECTION_EVENTS = "events"
        private const val MAX_STREAMED_EVENTS = 250L
    }
}
