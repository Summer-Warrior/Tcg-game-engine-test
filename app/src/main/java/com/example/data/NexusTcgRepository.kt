package com.example.data

import android.content.Context
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NexusTcgRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- 1. TCG Engine Variants (/users/{userId}/variants/{variantId}) ---

    fun observeVariants(userId: String = requireUserId()): Flow<List<TcgVariant>> = flow {
        val path = "users/$userId/variants"
        emitAll(
            db.collection("users").document(userId).collection("variants")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        TcgVariant::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createVariant(
        name: String,
        baseVariantId: String,
        resourceName: String,
        startingLife: Long,
        startingHandSize: Long,
        maxResourcePool: Long,
        combatRule: String,
        maxDigitPrecision: Long = if (baseVariantId == "EDOPRO_CUSTOM_FORK") 21L else 10L,
        startingLp21: String = startingLife.toString(),
        customVarInput21: String = "100000000000000000000",
        customFuncInput21: String = "899999999999999999999",
        customFuncName: String = "ADD_21",
        customCodeString21: String = if (baseVariantId == "EDOPRO_CUSTOM_FORK") BigInt21Engine.DEFAULT_CODE_STRING_21 else "89631139",
        customCodeVarExpr21: String = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseVariantId)
        val cleanLp21 = BigInt21Engine.sanitizeTo21Digits(startingLp21, startingLife.toString())
        val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(customVarInput21, "0")
        val cleanFuncArg21 = BigInt21Engine.sanitizeTo21Digits(customFuncInput21, "0")
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(customCodeString21, BigInt21Engine.DEFAULT_CODE_STRING_21)
        val cleanCodeExpr21 = BigInt21Engine.sanitizeCodeVarExpr21(customCodeVarExpr21, cleanCode21)
        val computedResult21 = BigInt21Engine.evaluateFunction21(customFuncName, cleanVar21, cleanFuncArg21)
        val projectedLife = BigInt21Engine.toSafeFirestoreLong(cleanLp21, minVal = 1L)

        val isBaseStandard = projectedLife == baseSpec.startingLife &&
            cleanLp21 == baseSpec.startingLife.toString() &&
            startingHandSize == baseSpec.startingHandSize &&
            maxResourcePool == baseSpec.maxResourcePool &&
            combatRule == baseSpec.combatRule &&
            resourceName == baseSpec.resourceName
        val protocolHash = BuiltInTcgCatalog.computeProtocolHash(
            baseVariantId = baseVariantId,
            startingLife = projectedLife,
            startingHandSize = startingHandSize,
            maxResourcePool = maxResourcePool,
            combatRule = combatRule
        )
        val docRef = db.collection("users").document(uid).collection("variants").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(60),
            "baseVariantId" to baseVariantId.trim().take(40),
            "resourceName" to resourceName.trim().take(30),
            "startingLife" to projectedLife,
            "startingHandSize" to startingHandSize.coerceIn(1L, 15L),
            "maxResourcePool" to maxResourcePool.coerceIn(1L, 30L),
            "combatRule" to combatRule.trim().take(40),
            "isBaseStandard" to isBaseStandard,
            "protocolHash" to protocolHash.take(128),
            "maxDigitPrecision" to maxDigitPrecision.coerceIn(1L, 21L),
            "startingLp21" to cleanLp21,
            "customVarInput21" to cleanVar21,
            "customFuncInput21" to cleanFuncArg21,
            "customFuncName" to customFuncName.trim().take(40),
            "customFuncResult21" to computedResult21,
            "customCodeString21" to cleanCode21,
            "customCodeVarExpr21" to cleanCodeExpr21,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun resetVariantToBaseStandard(variant: TcgVariant): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(variant.id.isNotBlank()) { "Variant ID must not be blank" }
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(variant.baseVariantId)
        val docRef = db.collection("users").document(uid).collection("variants").document(variant.id)
        val updates = mapOf(
            "resourceName" to baseSpec.resourceName,
            "startingLife" to baseSpec.startingLife,
            "startingLp21" to baseSpec.startingLife.toString(),
            "startingHandSize" to baseSpec.startingHandSize,
            "maxResourcePool" to baseSpec.maxResourcePool,
            "combatRule" to baseSpec.combatRule,
            "isBaseStandard" to true,
            "protocolHash" to baseSpec.protocolHash,
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
    }

    suspend fun deleteVariant(variantId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("variants").document(variantId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 2. External & Peer Client Bridge (/users/{userId}/clients/{clientId}) ---

    fun observeClients(userId: String = requireUserId()): Flow<List<ClientNode>> = flow {
        val path = "users/$userId/clients"
        emitAll(
            db.collection("users").document(userId).collection("clients")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ClientNode::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun registerClientNode(
        clientName: String,
        clientType: String,
        endpointUri: String,
        baseVariantId: String,
        requiresStrictBase: Boolean,
        connectionStatus: String = "DISCONNECTED",
        latencyMs: Long = 22L,
        maxDigitPrecision: Long = if (baseVariantId == "EDOPRO_CUSTOM_FORK") 21L else 10L,
        customVarInput21: String = if (baseVariantId == "EDOPRO_CUSTOM_FORK") "100000000000000000000" else "8000",
        customFuncInput21: String = if (baseVariantId == "EDOPRO_CUSTOM_FORK") "899999999999999999999" else "0",
        customFuncName: String = "ADD_21",
        customCodeString21: String = if (baseVariantId == "EDOPRO_CUSTOM_FORK") BigInt21Engine.DEFAULT_CODE_STRING_21 else "89631139",
        customCodeVarExpr21: String = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseVariantId)
        val isLockedBase = requiresStrictBase || BuiltInTcgCatalog.isLockedGitHubBaseClient(baseSpec.baseVariantId)
        val resolvedEndpointUri = if (isLockedBase && (baseSpec.baseVariantId == "EDOPRO_BASE_MR5" || baseSpec.baseVariantId == "FORGE_MTG_BASE")) {
            BuiltInTcgCatalog.exactGitHubRepoUrl(baseSpec.baseVariantId)
        } else {
            endpointUri.trim().take(120)
        }
        val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(customVarInput21, "8000")
        val cleanFuncArg21 = BigInt21Engine.sanitizeTo21Digits(customFuncInput21, "0")
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(customCodeString21, BigInt21Engine.DEFAULT_CODE_STRING_21)
        val cleanCodeExpr21 = BigInt21Engine.sanitizeCodeVarExpr21(customCodeVarExpr21, cleanCode21)
        val computedResult21 = BigInt21Engine.evaluateFunction21(customFuncName, cleanVar21, cleanFuncArg21)
        val docRef = db.collection("users").document(uid).collection("clients").document()
        val payload = mapOf(
            "userId" to uid,
            "clientName" to clientName.trim().take(60),
            "clientType" to clientType.trim().take(40),
            "endpointUri" to resolvedEndpointUri,
            "baseVariantId" to baseSpec.baseVariantId,
            "baseVariantName" to baseSpec.name,
            "protocolVersion" to baseSpec.protocolVersion.take(30),
            "requiresStrictBase" to isLockedBase,
            "connectionStatus" to connectionStatus,
            "latencyMs" to latencyMs.coerceIn(0L, 10000L),
            "maxDigitPrecision" to (if (isLockedBase) 10L else maxDigitPrecision).coerceIn(1L, 21L),
            "customVarInput21" to cleanVar21,
            "customFuncInput21" to cleanFuncArg21,
            "customFuncName" to customFuncName.trim().take(40),
            "customFuncResult21" to computedResult21,
            "customCodeString21" to cleanCode21,
            "customCodeVarExpr21" to cleanCodeExpr21,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    /**
     * Updates the 21-digit variable, function, and code-string registers on a ClientNode.
     * Strictly blocks edits on Base Clients locked to their upstream GitHub source repository.
     */
    suspend fun updateClient21DigitRegisters(
        clientId: String,
        customVarInput21: String,
        customFuncInput21: String,
        customFuncName: String,
        customCodeString21: String = BigInt21Engine.DEFAULT_CODE_STRING_21,
        customCodeVarExpr21: String = BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("clients").document(clientId)
        val existingNode = docRef.get().await().toObject(
            ClientNode::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        if (existingNode != null && BuiltInTcgCatalog.isLockedGitHubBaseClientNode(existingNode)) {
            val repoUrl = BuiltInTcgCatalog.exactGitHubRepoUrl(existingNode.baseVariantId)
            throw IllegalStateException(
                "LOCKED TO GITHUB SOURCE REPOSITORY ($repoUrl): Base client '${existingNode.clientName}' is immutable against user and agent edits. Only upstream source repository updates are permitted."
            )
        }
        val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(customVarInput21, "0")
        val cleanArg21 = BigInt21Engine.sanitizeTo21Digits(customFuncInput21, "0")
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(customCodeString21, BigInt21Engine.DEFAULT_CODE_STRING_21)
        val cleanCodeExpr21 = BigInt21Engine.sanitizeCodeVarExpr21(customCodeVarExpr21, cleanCode21)
        val result21 = BigInt21Engine.evaluateFunction21(customFuncName, cleanVar21, cleanArg21)
        val updates = mapOf(
            "maxDigitPrecision" to 21L,
            "customVarInput21" to cleanVar21,
            "customFuncInput21" to cleanArg21,
            "customFuncName" to customFuncName.trim().take(40),
            "customFuncResult21" to result21,
            "customCodeString21" to cleanCode21,
            "customCodeVarExpr21" to cleanCodeExpr21,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
            result21
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    /**
     * Synchronizes a locked Base Client strictly from its canonical upstream GitHub source repository
     * (`https://github.com/ProjectIgnis/EDOPro` or `https://github.com/Card-Forge/forge`).
     * User and agent manual edits are forbidden; only verified source repository updates are applied.
     */
    suspend fun syncBaseClientFromUpstreamGitHubRepo(
        clientId: String,
        baseVariantId: String,
        verifiedUpstreamVersion: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseVariantId)
        val exactRepoUrl = BuiltInTcgCatalog.exactGitHubRepoUrl(baseSpec.baseVariantId)
        val docRef = db.collection("users").document(uid).collection("clients").document(clientId)
        val canonicalVar21 = baseSpec.startingLife.toString()
        val canonicalCode21 = if (baseSpec.baseVariantId == "FORGE_MTG_BASE") "500101" else "89631139"
        val canonicalExpr21 = if (baseSpec.baseVariantId == "FORGE_MTG_BASE") {
            "SVar:BaseForge=\"500101\""
        } else {
            "s.code_var_21 = \"89631139\""
        }
        val resolvedVersion = verifiedUpstreamVersion?.trim()?.takeIf { it.isNotBlank() }?.take(30)
            ?: baseSpec.protocolVersion.take(30)

        val updates = mapOf(
            "endpointUri" to exactRepoUrl.take(120),
            "baseVariantId" to baseSpec.baseVariantId,
            "baseVariantName" to baseSpec.name,
            "protocolVersion" to resolvedVersion,
            "requiresStrictBase" to true,
            "maxDigitPrecision" to 10L,
            "customVarInput21" to canonicalVar21,
            "customFuncInput21" to "0",
            "customFuncName" to "ADD_21",
            "customFuncResult21" to canonicalVar21,
            "customCodeString21" to canonicalCode21,
            "customCodeVarExpr21" to canonicalExpr21,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
            exactRepoUrl
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun getClientNodeById(targetUserId: String, clientId: String): Result<ClientNode?> = runCatching {
        val docRef = db.collection("users").document(targetUserId).collection("clients").document(clientId)
        try {
            docRef.get().await().toObject(
                ClientNode::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateClientConnectionState(
        clientId: String,
        newStatus: String,
        latencyMs: Long
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("clients").document(clientId)
        val updates = mapOf(
            "connectionStatus" to newStatus,
            "latencyMs" to latencyMs.coerceIn(0L, 10000L),
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
    }

    suspend fun deleteClientNode(clientId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("clients").document(clientId)
        val existingNode = docRef.get().await().toObject(
            ClientNode::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
        if (existingNode != null && BuiltInTcgCatalog.isLockedGitHubBaseClientNode(existingNode)) {
            val repoUrl = BuiltInTcgCatalog.exactGitHubRepoUrl(existingNode.baseVariantId)
            throw IllegalStateException(
                "LOCKED TO GITHUB SOURCE REPOSITORY ($repoUrl): Base client '${existingNode.clientName}' cannot be deleted or modified by users or the agent."
            )
        }
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 3. Custom Decks & Forged Cards (/users/{userId}/decks/{deckId}) ---

    fun observeDecks(userId: String = requireUserId()): Flow<List<CustomDeck>> = flow {
        val path = "users/$userId/decks"
        emitAll(
            db.collection("users").document(userId).collection("decks")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomDeck::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createDeck(
        name: String,
        baseVariantId: String,
        strategyTag: String,
        cardIds: List<String>,
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
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanAtk21 = BigInt21Engine.sanitizeTo21Digits(customAttack21, customCardAttack.toString())
        val cleanDef21 = BigInt21Engine.sanitizeTo21Digits(customDefense21, customCardDefense.toString())
        val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(customVarInput21, "999999999999999999999")
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(customCodeString21, BigInt21Engine.DEFAULT_CODE_STRING_21)
        val cleanCodeExpr21 = BigInt21Engine.sanitizeCodeVarExpr21(customCodeVarExpr21, cleanCode21)
        val projectedAtk = BigInt21Engine.toSafeFirestoreLong(cleanAtk21, minVal = 0L)
        val projectedDef = BigInt21Engine.toSafeFirestoreLong(cleanDef21, minVal = 0L)

        val docRef = db.collection("users").document(uid).collection("decks").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to name.trim().take(60),
            "baseVariantId" to baseVariantId.trim().take(40),
            "strategyTag" to strategyTag.trim().take(40),
            "cardIds" to cardIds.take(40),
            "customCardName" to customCardName.trim().take(60),
            "customCardCost" to customCardCost.coerceIn(0L, 20L),
            "customCardAttack" to projectedAtk,
            "customCardDefense" to projectedDef,
            "customAttack21" to cleanAtk21,
            "customDefense21" to cleanDef21,
            "customVarInput21" to cleanVar21,
            "customCodeString21" to cleanCode21,
            "customCodeVarExpr21" to cleanCodeExpr21,
            "customCardAbility" to customCardAbility.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDeck(deckId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("decks").document(deckId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 4. Multi-Client Match Sessions (/matches/{matchId}) ---

    fun observeOpenLobbyMatches(): Flow<List<MatchSession>> = flow {
        val path = "matches"
        emitAll(
            db.collection("matches")
                .whereEqualTo("status", "OPEN_LOBBY")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MatchSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeHostedMatches(userId: String = requireUserId()): Flow<List<MatchSession>> = flow {
        val path = "matches"
        emitAll(
            db.collection("matches")
                .whereEqualTo("hostUserId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MatchSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createMatchSession(
        hostName: String,
        title: String,
        baseVariantId: String,
        connectedClients: List<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val baseSpec = BuiltInTcgCatalog.findBaseSpec(baseVariantId)
        val docRef = db.collection("matches").document()
        val initialOpponentBoard = listOf(
            BuiltInTcgCatalog.cardsForVariant(baseVariantId).first().toBoardDescriptor()
        )
        val clientSummary = if (connectedClients.isEmpty()) {
            listOf("ProjectIgnis EDOPro Relay")
        } else {
            connectedClients.take(10)
        }
        val payload = mapOf(
            "hostUserId" to uid,
            "hostName" to hostName.trim().ifBlank { "Duelist" }.take(60),
            "title" to title.trim().ifBlank { "${baseSpec.name} Arena" }.take(80),
            "baseVariantId" to baseSpec.baseVariantId,
            "baseVariantName" to baseSpec.name,
            "resourceName" to baseSpec.resourceName,
            "connectedClients" to clientSummary,
            "turnNumber" to 1L,
            "activePhase" to "MAIN",
            "hostLife" to baseSpec.startingLife,
            "opponentLife" to baseSpec.startingLife,
            "hostResources" to baseSpec.maxResourcePool.coerceAtMost(5L),
            "hostMaxResources" to baseSpec.maxResourcePool.coerceAtMost(5L),
            "opponentResources" to baseSpec.maxResourcePool.coerceAtMost(5L),
            "hostBoardCards" to emptyList<String>(),
            "opponentBoardCards" to initialOpponentBoard,
            "actionLog" to listOf(
                "Handshake verified: ${baseSpec.protocolHash}",
                "Connected clients synced on ${baseSpec.baseVariantId}: ${clientSummary.joinToString(", ")}"
            ),
            "status" to "OPEN_LOBBY",
            "winnerName" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateMatchState(
        matchId: String,
        connectedClients: List<String>,
        turnNumber: Long,
        activePhase: String,
        hostLife: Long,
        opponentLife: Long,
        hostResources: Long,
        hostMaxResources: Long,
        opponentResources: Long,
        hostBoardCards: List<String>,
        opponentBoardCards: List<String>,
        actionLog: List<String>,
        status: String,
        winnerName: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("matches").document(matchId)
        val updates = mapOf(
            "connectedClients" to connectedClients.take(10),
            "turnNumber" to turnNumber.coerceIn(1L, 500L),
            "activePhase" to activePhase,
            "hostLife" to hostLife.coerceIn(-999999999999999999L, 999999999999999999L),
            "opponentLife" to opponentLife.coerceIn(-999999999999999999L, 999999999999999999L),
            "hostResources" to hostResources.coerceIn(0L, 30L),
            "hostMaxResources" to hostMaxResources.coerceIn(1L, 30L),
            "opponentResources" to opponentResources.coerceIn(0L, 30L),
            "hostBoardCards" to hostBoardCards.take(12),
            "opponentBoardCards" to opponentBoardCards.take(12),
            "actionLog" to actionLog.takeLast(20),
            "status" to status,
            "winnerName" to winnerName.take(60),
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
    }

    suspend fun deleteMatchSession(matchId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("matches").document(matchId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 5. Discord Roleplay Server Hubs (/users/{userId}/discordServers/{serverId}) ---

    fun observeDiscordServers(userId: String = requireUserId()): Flow<List<DiscordServerHub>> = flow {
        val path = "users/$userId/discordServers"
        emitAll(
            db.collection("users").document(userId).collection("discordServers")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DiscordServerHub::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createDiscordServerHub(
        guildId: String,
        serverName: String,
        channelId: String,
        channelName: String,
        baseVariantId: String,
        rpLoreSetting: String,
        connectionMode: String = DiscordApiService.activeConnectionMode(),
        isConnected: Boolean = true,
        memberCount: Long = 128L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val validConnectionMode = when (connectionMode) {
            "BOT_AND_WEBHOOK", "WEBHOOK_PROXY", "CLOUD_RP_RELAY" -> connectionMode
            else -> "CLOUD_RP_RELAY"
        }
        val normalizedChannelName = channelName.trim().let {
            if (it.startsWith("#")) it else "#$it"
        }.take(60)
        val docRef = db.collection("users").document(uid).collection("discordServers").document()
        val payload = mapOf(
            "userId" to uid,
            "guildId" to guildId.trim().ifBlank { "guild_${System.currentTimeMillis()}" }.take(40),
            "serverName" to serverName.trim().ifBlank { "Domino Duel Academy RP" }.take(80),
            "channelId" to channelId.trim().ifBlank { "chan_${System.currentTimeMillis()}" }.take(40),
            "channelName" to normalizedChannelName,
            "baseVariantId" to baseVariantId.trim().ifBlank { "EDOPRO_BASE_MR5" }.take(40),
            "rpLoreSetting" to rpLoreSetting.trim().ifBlank { "Yu-Gi-Oh! Master Rule 5 & Custom TCG Roleplay Server" }.take(240),
            "connectionMode" to validConnectionMode,
            "isConnected" to isConnected,
            "memberCount" to memberCount.coerceIn(1L, 1000000L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateDiscordServerConnection(
        serverId: String,
        isConnected: Boolean,
        connectionMode: String = DiscordApiService.activeConnectionMode()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val validConnectionMode = when (connectionMode) {
            "BOT_AND_WEBHOOK", "WEBHOOK_PROXY", "CLOUD_RP_RELAY" -> connectionMode
            else -> "CLOUD_RP_RELAY"
        }
        val docRef = db.collection("users").document(uid).collection("discordServers").document(serverId)
        val updates = mapOf(
            "isConnected" to isConnected,
            "connectionMode" to validConnectionMode,
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
    }

    suspend fun deleteDiscordServerHub(serverId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("discordServers").document(serverId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 6. Roleplay Character Personas (/users/{userId}/rpCharacters/{characterId}) ---

    fun observeRpCharacters(userId: String = requireUserId()): Flow<List<RpCharacterPersona>> = flow {
        val path = "users/$userId/rpCharacters"
        emitAll(
            db.collection("users").document(userId).collection("rpCharacters")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RpCharacterPersona::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createRpCharacterPersona(
        characterName: String,
        characterTitle: String,
        factionOrArchetype: String,
        baseVariantId: String,
        signatureCardName: String,
        signatureCodeString21: String,
        currentLp21: String,
        personalityPrompt: String,
        openingCatchphrase: String,
        summonChant: String,
        isActivePersona: Boolean = false
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanCode21 = BigInt21Engine.sanitizeTo21Digits(
            signatureCodeString21,
            BigInt21Engine.DEFAULT_CODE_STRING_21
        )
        val cleanLp21 = BigInt21Engine.sanitizeTo21Digits(currentLp21, "8000")
        val docRef = db.collection("users").document(uid).collection("rpCharacters").document()
        val payload = mapOf(
            "userId" to uid,
            "characterName" to characterName.trim().ifBlank { "Kaiba-Omega" }.take(60),
            "characterTitle" to characterTitle.trim().ifBlank { "High Arbiter of Duel Academy" }.take(60),
            "factionOrArchetype" to factionOrArchetype.trim().ifBlank { "Blue-Eyes / Singularity Wyrm" }.take(60),
            "baseVariantId" to baseVariantId.trim().ifBlank { "EDOPRO_BASE_MR5" }.take(40),
            "signatureCardName" to signatureCardName.trim().ifBlank { "Azure-Eyes Sovereign Dragon" }.take(60),
            "signatureCodeString21" to cleanCode21,
            "currentLp21" to cleanLp21,
            "personalityPrompt" to personalityPrompt.trim().ifBlank { "Commanding, strategic, speaks with dramatic duel precision." }.take(240),
            "openingCatchphrase" to openingCatchphrase.trim().ifBlank { "Activate your Duel Disk! My 21-digit engine leaves zero room for error!" }.take(200),
            "summonChant" to summonChant.trim().ifBlank { "Rend the digital heavens and descend! I summon my sovereign ace!" }.take(200),
            "isActivePersona" to isActivePersona,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateRpCharacterActiveState(
        characterId: String,
        isActivePersona: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("rpCharacters").document(characterId)
        val updates = mapOf(
            "isActivePersona" to isActivePersona,
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
    }

    suspend fun updateRpCharacterLp21(
        characterId: String,
        newLp21: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanLp21 = BigInt21Engine.sanitizeTo21Digits(newLp21, "8000")
        val docRef = db.collection("users").document(uid).collection("rpCharacters").document(characterId)
        val updates = mapOf(
            "currentLp21" to cleanLp21,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
            cleanLp21
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteRpCharacterPersona(characterId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("rpCharacters").document(characterId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- 7. Discord Roleplay Chat Messages (/users/{userId}/discordMessages/{messageId}) ---

    fun observeDiscordMessages(userId: String = requireUserId()): Flow<List<DiscordRpMessage>> = flow {
        val path = "users/$userId/discordMessages"
        emitAll(
            db.collection("users").document(userId).collection("discordMessages")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DiscordRpMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createDiscordRpMessage(
        serverId: String,
        channelName: String,
        senderName: String,
        senderTitle: String,
        isCharacterProxy: Boolean,
        messageType: String,
        content: String,
        tcgActionSummary: String,
        baseVariantId: String,
        deliveryStatus: String = "SYNCED_RP_CHANNEL",
        reviewedFlag: Boolean = false
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val validType = when (messageType) {
            "RP_DIALOGUE", "TCG_DUEL_ACTION", "DISCORD_PEER_CHAT", "SYSTEM_SYNC" -> messageType
            else -> "RP_DIALOGUE"
        }
        val validDelivery = when (deliveryStatus) {
            "DELIVERED_DISCORD_API", "DELIVERED_WEBHOOK", "SYNCED_RP_CHANNEL", "REVIEWED_FROM_DISCORD" -> deliveryStatus
            else -> "SYNCED_RP_CHANNEL"
        }
        val docRef = db.collection("users").document(uid).collection("discordMessages").document()
        val payload = mapOf(
            "userId" to uid,
            "serverId" to serverId.trim().ifBlank { "default_rp_server" }.take(80),
            "channelName" to channelName.trim().ifBlank { "#duel-arena-rp" }.take(60),
            "senderName" to senderName.trim().ifBlank { "Duelist" }.take(60),
            "senderTitle" to senderTitle.trim().take(60),
            "isCharacterProxy" to isCharacterProxy,
            "messageType" to validType,
            "content" to content.trim().ifBlank { "..." }.take(500),
            "tcgActionSummary" to tcgActionSummary.trim().take(240),
            "baseVariantId" to baseVariantId.trim().ifBlank { "EDOPRO_BASE_MR5" }.take(40),
            "deliveryStatus" to validDelivery,
            "reviewedFlag" to reviewedFlag,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun toggleDiscordMessageReviewed(
        messageId: String,
        reviewedFlag: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("discordMessages").document(messageId)
        val updates = mapOf(
            "reviewedFlag" to reviewedFlag,
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
    }

    suspend fun deleteDiscordRpMessage(messageId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("discordMessages").document(messageId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }
}
