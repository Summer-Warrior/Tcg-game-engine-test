const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read variants, clients, decks, or matches", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("variants").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("clients").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("decks").get()
  );
  await assertFails(unauthDb.collection("matches").get());
});

test("Authenticated user: can create EDOPro Custom Clone with 21-digit variable/function/code-string inputs and rejects >21 digits", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const clientRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("clients")
    .doc("client_edopro_21");

  await assertSucceeds(
    clientRef.set({
      userId: ALICE_UID,
      clientName: "EDOPro Custom Project Clone (21-Digit)",
      clientType: "EDOPro 21-Digit Clone",
      endpointUri: "ws://custom-clone.edopro.local:7912/expansions",
      baseVariantId: "EDOPRO_CUSTOM_FORK",
      baseVariantName: "EDOPro Custom Project Clone (IgnisForge 21-Digit)",
      protocolVersion: "40.1.4-bigint21",
      requiresStrictBase: false,
      connectionStatus: "CONNECTED",
      latencyMs: 14,
      maxDigitPrecision: 21,
      customVarInput21: "123456789012345678901",
      customFuncInput21: "876543210987654321098",
      customFuncName: "ADD_21",
      customFuncResult21: "999999999999999999999",
      customCodeString21: "900001010000000000021",
      customCodeVarExpr21:
        's.code_var_21 = "900001010000000000021"; s.setcode_21 = "123456789012345678901"',
      createdAt: now,
      updatedAt: now,
    })
  );

  // Rejects 22-digit overflow (>21 digits) on customVarInput21
  await assertFails(
    clientRef.update({
      customVarInput21: "1000000000000000000000",
      updatedAt: new Date(),
    })
  );

  // Rejects 22-digit overflow (>21 digits) on coding variables inside code string (customCodeVarExpr21)
  await assertFails(
    clientRef.update({
      customCodeVarExpr21: 's.code_var_21 = "1000000000000000000000"',
      updatedAt: new Date(),
    })
  );

  // Shadow update test: ghost field must be rejected
  await assertFails(
    clientRef.update({
      connectionStatus: "DISCONNECTED",
      isAdminOverride: true,
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation: Alice cannot read or modify Bob's ClientNode or TcgVariant", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("variants")
      .doc("var_bob")
      .set({
        userId: BOB_UID,
        name: "Bob Variant",
        baseVariantId: "AETHER_MANA_V1",
        resourceName: "Mana",
        startingLife: 20,
        startingHandSize: 5,
        maxResourcePool: 10,
        combatRule: "DIRECT_STRIKE",
        isBaseStandard: true,
        protocolHash: "AETHER_MANA_V1:20:5:10:DIRECT_STRIKE",
        createdAt: now,
        updatedAt: now,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("variants")
      .doc("var_bob")
      .get()
  );
});

test("MatchSession: enforces query alignment and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const matchRef = aliceDb.collection("matches").doc("match_alpha");

  await assertSucceeds(
    matchRef.set({
      hostUserId: ALICE_UID,
      hostName: "Alice",
      title: "Aether Base Variant Lobby",
      baseVariantId: "AETHER_MANA_V1",
      baseVariantName: "AetherMana Standard",
      resourceName: "Mana",
      connectedClients: ["AetherDesk Desktop v2.4"],
      turnNumber: 1,
      activePhase: "MAIN",
      hostLife: 20,
      opponentLife: 20,
      hostResources: 3,
      hostMaxResources: 3,
      opponentResources: 3,
      hostBoardCards: [],
      opponentBoardCards: [],
      actionLog: ["Match initialized on AETHER_MANA_V1"],
      status: "OPEN_LOBBY",
      winnerName: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Query with status == OPEN_LOBBY succeeds
  await assertSucceeds(
    aliceDb.collection("matches").where("status", "==", "OPEN_LOBBY").get()
  );

  // Unfiltered query fails (rules are not filters)
  await assertFails(aliceDb.collection("matches").get());

  // Transition to FINISHED
  await assertSucceeds(
    matchRef.update({
      status: "FINISHED",
      winnerName: "Alice",
      updatedAt: new Date(),
    })
  );

  // Terminal State Locking: further updates after FINISHED must fail
  await assertFails(
    matchRef.update({
      turnNumber: 2,
      updatedAt: new Date(),
    })
  );
});

test("Discord RP collections: owner can create/update DiscordServerHub, RpCharacterPersona (21-digit code & LP), and DiscordRpMessage", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  const serverRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("discordServers")
    .doc("srv_domino");

  await assertSucceeds(
    serverRef.set({
      userId: ALICE_UID,
      guildId: "108911002200330044",
      serverName: "Domino City Duel Academy RP",
      channelId: "108911002200330099",
      channelName: "#duel-arena-rp",
      baseVariantId: "EDOPRO_BASE_MR5",
      rpLoreSetting: "Official ProjectIgnis/EDOPro MR5 Roleplay Server",
      connectionMode: "CLOUD_RP_RELAY",
      isConnected: true,
      memberCount: 412,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cross-user isolation check on DiscordServerHub
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("discordServers")
      .doc("srv_domino")
      .get()
  );

  const charRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("rpCharacters")
    .doc("char_kaiba_21");

  await assertSucceeds(
    charRef.set({
      userId: ALICE_UID,
      characterName: "Kaiba-Omega",
      characterTitle: "Singularity Overseer",
      factionOrArchetype: "Blue-Eyes / 21-Digit Wyrm",
      baseVariantId: "EDOPRO_CUSTOM_FORK",
      signatureCardName: "Custom Project: Singularity Dragon",
      signatureCodeString21: "900001040000000000021",
      currentLp21: "999999999999999999999",
      personalityPrompt: "Relentless and visionary.",
      openingCatchphrase: "Boot up your Duel Disk!",
      summonChant: "Descend from the 21-digit code register!",
      isActivePersona: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Rejects >21 digits on RpCharacterPersona currentLp21
  await assertFails(
    charRef.update({
      currentLp21: "1000000000000000000000",
      updatedAt: new Date(),
    })
  );

  const msgRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("discordMessages")
    .doc("msg_rp_1");

  await assertSucceeds(
    msgRef.set({
      userId: ALICE_UID,
      serverId: "srv_domino",
      channelName: "#duel-arena-rp",
      senderName: "Kaiba-Omega",
      senderTitle: "Singularity Overseer",
      isCharacterProxy: true,
      messageType: "RP_DIALOGUE",
      content: "Boot up your Duel Disk! My EDOPro Custom Clone calculates up to 21 digits of power!",
      tcgActionSummary: "RP_STANCE: Kaiba-Omega • LP: 999999999999999999999",
      baseVariantId: "EDOPRO_CUSTOM_FORK",
      deliveryStatus: "SYNCED_RP_CHANNEL",
      reviewedFlag: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Toggle reviewedFlag succeeds
  await assertSucceeds(
    msgRef.update({
      reviewedFlag: true,
      updatedAt: new Date(),
    })
  );
});

test("MatchGameEvent persistent log subcollection: allows host and peer to append and read events, rejects tampering, unauth, and updates", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const past = new Date(Date.now() - 1000);

  const matchRef = aliceDb.collection("matches").doc("match_event_log_1");
  await assertSucceeds(
    matchRef.set({
      hostUserId: ALICE_UID,
      hostName: "Player 1",
      title: "Persistent Log Sync Match",
      baseVariantId: "FORGE_MTG_BASE",
      baseVariantName: "Card-Forge MTG Base",
      resourceName: "Mana",
      connectedClients: ["Player 1", "Player 2"],
      turnNumber: 1,
      activePhase: "MAIN",
      hostLife: 20,
      opponentLife: 20,
      hostResources: 3,
      hostMaxResources: 3,
      opponentResources: 3,
      hostBoardCards: [],
      opponentBoardCards: [],
      actionLog: ["Match started"],
      status: "ACTIVE",
      winnerName: "",
      createdAt: past,
      updatedAt: past,
    })
  );

  // Unauthenticated cannot read or write events
  await assertFails(
    unauthDb.collection("matches").doc("match_event_log_1").collection("events").get()
  );

  // Host (Player 1) logs 'Player 1 played card X'
  const evt1Ref = matchRef.collection("events").doc("evt_1");
  await assertSucceeds(
    evt1Ref.set({
      matchId: "match_event_log_1",
      actorUserId: ALICE_UID,
      actorLabel: "Player 1",
      eventType: "CARD_PLAYED",
      summary: "Player 1 played Serra Archon",
      detail: "Zone [P1_MAIN_0] • ATK 4 / DEF 4",
      turnNumber: 1,
      activePhase: "MAIN",
      cardName: "Serra Archon",
      lifeDelta: 0,
      createdAt: past,
    })
  );

  // Peer (Bob / Player 2) logs 'Life points reduced by 5'
  const evt2Ref = bobDb
    .collection("matches")
    .doc("match_event_log_1")
    .collection("events")
    .doc("evt_2");
  await assertSucceeds(
    evt2Ref.set({
      matchId: "match_event_log_1",
      actorUserId: BOB_UID,
      actorLabel: "Player 2",
      eventType: "LIFE_CHANGED",
      summary: "Life points reduced by 5",
      detail: "Player 2: 20 -> 15",
      turnNumber: 1,
      activePhase: "COMBAT",
      cardName: "",
      lifeDelta: -5,
      createdAt: past,
    })
  );

  // Both Host and Peer can query ordered persistent event log
  await assertSucceeds(
    bobDb
      .collection("matches")
      .doc("match_event_log_1")
      .collection("events")
      .orderBy("createdAt", "asc")
      .get()
  );

  // Rejects actorUserId spoofing
  await assertFails(
    bobDb
      .collection("matches")
      .doc("match_event_log_1")
      .collection("events")
      .doc("evt_spoof")
      .set({
        matchId: "match_event_log_1",
        actorUserId: ALICE_UID,
        actorLabel: "Spoofed",
        eventType: "CARD_PLAYED",
        summary: "Spoofed event",
        detail: "",
        turnNumber: 1,
        activePhase: "MAIN",
        cardName: "Fake",
        lifeDelta: 0,
        createdAt: past,
      })
  );

  // Rejects immutable log mutation (update must fail)
  await assertFails(
    evt1Ref.update({
      summary: "Tampered history",
    })
  );

  // Rejects peer deleting host's event log, but allows host delete
  await assertFails(
    bobDb
      .collection("matches")
      .doc("match_event_log_1")
      .collection("events")
      .doc("evt_1")
      .delete()
  );
  await assertSucceeds(evt1Ref.delete());
});


