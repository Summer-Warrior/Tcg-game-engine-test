package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NexusTcgRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun registerEdoProBaseAnd21DigitCustomCloneNodes_authenticatedOwner_succeeds() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repository = NexusTcgRepository(firestore)

    val baseClientResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.registerClientNode(
        clientName = "ProjectIgnis EDOPro Base Client",
        clientType = "EDOPro Base (ProjectIgnis/EDOPro)",
        endpointUri = "tcp://duel.projectignis.org:7911",
        baseVariantId = "EDOPRO_BASE_MR5",
        requiresStrictBase = true,
        connectionStatus = "CONNECTED",
        latencyMs = 15L,
        maxDigitPrecision = 10L
      )
    }
    assertTrue("Expected EDOPro Base client registration to succeed: ${baseClientResult.exceptionOrNull()}", baseClientResult.isSuccess)

    val cloneClientResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.registerClientNode(
        clientName = "EDOPro Custom Project Clone (21-Digit)",
        clientType = "EDOPro 21-Digit Clone",
        endpointUri = "ws://sandbox.edopro-custom.local:7912/expansions",
        baseVariantId = "EDOPRO_CUSTOM_FORK",
        requiresStrictBase = false,
        connectionStatus = "CONNECTED",
        latencyMs = 19L,
        maxDigitPrecision = 21L,
        customVarInput21 = "123456789012345678901",
        customFuncInput21 = "876543210987654321098",
        customFuncName = "ADD_21",
        customCodeString21 = "900001010000000000021",
        customCodeVarExpr21 = "s.code_var_21 = \"900001010000000000021\"; s.setcode_21 = \"123456789012345678901\""
      )
    }
    assertTrue("Expected 21-Digit EDOPro Custom Clone registration to succeed: ${cloneClientResult.exceptionOrNull()}", cloneClientResult.isSuccess)
    val cloneClientId = cloneClientResult.getOrThrow()

    // Verify updating 21-digit registers and 21-digit code-string coding variables on the EDOPro Custom Clone
    val update21Result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.updateClient21DigitRegisters(
        clientId = cloneClientId,
        customVarInput21 = "500000000000000000000",
        customFuncInput21 = "499999999999999999999",
        customFuncName = "ADD_21",
        customCodeString21 = "999999999999999999999",
        customCodeVarExpr21 = "s.code_var_21 = \"999999999999999999999\"; s.setcode_21 = \"500000000000000000000\""
      )
    }
    assertTrue("Expected 21-digit register update to succeed: ${update21Result.exceptionOrNull()}", update21Result.isSuccess)
    assertEquals("999999999999999999999", update21Result.getOrThrow())

    val variantResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createVariant(
        name = "EDOPro Custom Project Clone (21-Digit LP)",
        baseVariantId = "EDOPRO_CUSTOM_FORK",
        resourceName = "Summon/Chain",
        startingLife = 999999999999999999L,
        startingHandSize = 5L,
        maxResourcePool = 6L,
        combatRule = "ATK_DEF_BATTLE",
        maxDigitPrecision = 21L,
        startingLp21 = "999999999999999999999",
        customVarInput21 = "123456789012345678901",
        customFuncInput21 = "876543210987654321098",
        customFuncName = "ADD_21",
        customCodeString21 = "900001010000000000021",
        customCodeVarExpr21 = "s.code_var_21 = \"900001010000000000021\""
      )
    }
    assertTrue("Expected 21-digit variant creation to succeed: ${variantResult.exceptionOrNull()}", variantResult.isSuccess)

    val deckResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createDeck(
        name = "Custom Project Expansion (.ydk)",
        baseVariantId = "EDOPRO_CUSTOM_FORK",
        strategyTag = "21-Digit Custom Expansion",
        cardIds = listOf("edopro_custom_genesis_wyrm"),
        customCardName = "Genesis Overlord Dragon",
        customCardCost = 10L,
        customCardAttack = 999999999999999999L,
        customCardDefense = 999999999999999999L,
        customCardAbility = "[c900001010000000000021.lua] 21-digit variable, function, and code string execution.",
        customAttack21 = "999999999999999999999",
        customDefense21 = "888888888888888888888",
        customVarInput21 = "123456789012345678901",
        customCodeString21 = "900001010000000000021",
        customCodeVarExpr21 = "s.code_var_21 = \"900001010000000000021\"; s.setcode_21 = \"123456789012345678901\""
      )
    }
    assertTrue("Expected 21-digit ATK/DEF/CodeString deck creation to succeed: ${deckResult.exceptionOrNull()}", deckResult.isSuccess)

    val emitted = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeClients(aliceUid).first { list -> list.size >= 2 }
    }
    assertTrue(emitted.any { it.baseVariantId == "EDOPRO_BASE_MR5" && it.requiresStrictBase })
    assertTrue(emitted.any {
      it.baseVariantId == "EDOPRO_CUSTOM_FORK" &&
        it.maxDigitPrecision == 21L &&
        it.customFuncResult21 == "999999999999999999999" &&
        it.customCodeString21 == "999999999999999999999"
    })
  }

  @Test
  fun createMatchSession_andObserveOpenLobby_succeeds() = runBlocking {
    signInTestUser(ALICE_EMAIL)
    val repository = NexusTcgRepository(firestore)

    val matchResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createMatchSession(
        hostName = "Alice",
        title = "EDOPro Base Duel Server (8000 LP)",
        baseVariantId = "EDOPRO_BASE_MR5",
        connectedClients = listOf("ProjectIgnis EDOPro Base Client")
      )
    }
    assertTrue("Expected createMatchSession to succeed: ${matchResult.exceptionOrNull()}", matchResult.isSuccess)
    val matchId = matchResult.getOrThrow()

    val openLobbies = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeOpenLobbyMatches().first { list -> list.any { it.id == matchId } }
    }
    assertTrue(openLobbies.any { it.id == matchId && it.hostLife == 8000L })
  }

  @Test
  fun getClientNodeById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = NexusTcgRepository(firestore)
    val clientId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.registerClientNode(
        clientName = "Private Node",
        clientType = "CLI Engine",
        endpointUri = "local://node-1",
        baseVariantId = "CYBER_OVERDRIVE_V2",
        requiresStrictBase = true
      ).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = NexusTcgRepository(firestore)
    val crossResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getClientNodeById(aliceUid, clientId)
    }
    assertTrue("Expected cross-user read to fail", crossResult.isFailure)
    val firestoreException = crossResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException", firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  @Test
  fun observeOpenLobbyMatches_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repository = NexusTcgRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeOpenLobbyMatches().first()
      }
      fail("Expected unauthenticated query to throw PERMISSION_DENIED")
    } catch (e: Exception) {
      val firestoreException = e.unwrapFirestoreException()
      assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }
  }

  @Test
  fun createDiscordServer_rpCharacter_andReviewMessage_succeeds() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repository = NexusTcgRepository(firestore)

    val serverResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createDiscordServerHub(
        guildId = "108911002200330044",
        serverName = "Domino City Duel Academy RP",
        channelId = "108911002200330099",
        channelName = "#duel-arena-rp",
        baseVariantId = "EDOPRO_CUSTOM_FORK",
        rpLoreSetting = "EDOPro 21-Digit Custom Project Roleplay Server",
        connectionMode = "CLOUD_RP_RELAY",
        isConnected = true,
        memberCount = 412L
      )
    }
    assertTrue("Expected Discord server creation to succeed: ${serverResult.exceptionOrNull()}", serverResult.isSuccess)
    val serverId = serverResult.getOrThrow()

    val charResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createRpCharacterPersona(
        characterName = "Kaiba-Omega",
        characterTitle = "Singularity Overseer",
        factionOrArchetype = "Blue-Eyes / 21-Digit Wyrm",
        baseVariantId = "EDOPRO_CUSTOM_FORK",
        signatureCardName = "Custom Project: Singularity Dragon",
        signatureCodeString21 = "900001040000000000021",
        currentLp21 = "999999999999999999999",
        personalityPrompt = "Commanding and strategic.",
        openingCatchphrase = "Boot up your Duel Disk!",
        summonChant = "Descend from the 21-digit code register!",
        isActivePersona = true
      )
    }
    assertTrue("Expected RP character creation to succeed: ${charResult.exceptionOrNull()}", charResult.isSuccess)

    val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createDiscordRpMessage(
        serverId = serverId,
        channelName = "#duel-arena-rp",
        senderName = "Kaiba-Omega",
        senderTitle = "Singularity Overseer",
        isCharacterProxy = true,
        messageType = "RP_DIALOGUE",
        content = "I summon Custom Project: Singularity Dragon (c900001040000000000021.lua)!",
        tcgActionSummary = "MSG_SUMMONING: c900001040000000000021",
        baseVariantId = "EDOPRO_CUSTOM_FORK",
        deliveryStatus = "SYNCED_RP_CHANNEL",
        reviewedFlag = false
      )
    }
    assertTrue("Expected Discord RP message creation to succeed: ${msgResult.exceptionOrNull()}", msgResult.isSuccess)
    val msgId = msgResult.getOrThrow()

    val toggleReviewResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.toggleDiscordMessageReviewed(msgId, true)
    }
    assertTrue("Expected message review toggle to succeed: ${toggleReviewResult.exceptionOrNull()}", toggleReviewResult.isSuccess)

    val messages = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeDiscordMessages(aliceUid).first { list -> list.any { it.id == msgId && it.reviewedFlag } }
    }
    assertTrue(messages.any { it.id == msgId && it.senderName == "Kaiba-Omega" && it.reviewedFlag })
  }

  private companion object {
    const val ALICE_EMAIL = "alice@nexustcg.test"
    const val BOB_EMAIL = "bob@nexustcg.test"
    const val DEFAULT_TIMEOUT_MS = 8000L
    const val FLOW_TIMEOUT_MS = 8000L
  }
}
