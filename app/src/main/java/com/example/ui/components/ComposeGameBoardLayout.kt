package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardTemplate
import com.example.data.ClientSystemUiProfile
import com.example.data.GameEngineState
import com.example.data.TcgVariant
import com.example.data.TurnPhase
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.RadiantGold

/**
 * Represents a slot in the Compose Game Board layout (Active Main Zone, Active Support/Spell Zone,
 * Field Zone, Extra/Command Zone, Graveyard, Banished, or Hand Placeholder).
 */
data class ComposeActiveZoneSlotState(
    val zoneCode: String,
    val zoneTitle: String,
    val zoneCategory: String,
    val card: CardTemplate? = null,
    val isDefenseOrTapped: Boolean = false,
    val faceDownSet: Boolean = false
)

/**
 * Structured Jetpack Compose UI Layout for the Game Board (`ComposeGameBoardLayout`), providing
 * dedicated, interactive Material 3 placeholders and card slots for:
 *  1. **Player's Active Zones**:
 *     - Main Monster / Creature Active Zones (`MMZ-1..5` / `BF-1..6`)
 *     - Spell / Trap / Mana Support Active Zones (`STZ-1..5` / `LAND-1..5`)
 *     - Center Extra Monster / Command / Stack Active Zones (`EMZ-L`, `EMZ-R`)
 *     - Peripheral Field, Graveyard (`GY`), Extra Deck, and Banished/Exile Zone Placeholders
 *  2. **Player's Deck Placeholder**:
 *     - Layered 3D-styled Deck Stack card counter with 1-tap Draw action and top-card sleeve preview
 *  3. **Player's Hand Placeholders**:
 *     - Interactive Hand Dock with both populated card slots and empty Hand Slot Placeholders
 *       (`HAND SLOT 1..7`) supporting 1-tap deployment into any selected Active Zone placeholder.
 */
@Composable
fun ComposeGameBoardLayout(
    uiProfile: ClientSystemUiProfile,
    engineState: GameEngineState,
    handCards: List<CardTemplate>,
    hostUnits: List<CardTemplate>,
    opponentUnits: List<CardTemplate>,
    activeCustomVariant: TcgVariant? = null,
    overrideAvailableResources: Long? = null,
    overrideMaxResources: Long? = null,
    overrideOpponentResources: Long? = null,
    onDrawFromDeckPlaceholder: () -> Unit,
    onDeployCardToActiveZone: (CardTemplate, String) -> Unit,
    onQueueCardEffectFromZone: (CardTemplate) -> Unit = {},
    onStepPhase: () -> Unit = {},
    onSelectPhase: (TurnPhase) -> Unit = {},
    onSelectClientPhaseCode: (String) -> Unit = {},
    onSpendResource: (Long) -> Unit = {},
    onGainResource: (Long) -> Unit = {},
    onRefillResources: () -> Unit = {},
    onExpandMaxResourceCap: () -> Unit = {},
    onSetResourceExact: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val accentColor = Color(uiProfile.accentHex)
    val activeBaseSpec = remember(uiProfile.baseVariantId) {
        BuiltInTcgCatalog.findBaseSpec(uiProfile.baseVariantId)
    }
    val mainZoneCodes = uiProfile.fieldSlotCodes.take(5)
    val supportZoneCodes = uiProfile.spellOrSupportSlotCodes.take(5)

    // Interactive Compose state for Player 1's Active Main Zones and Active Support Zones
    val activeMainZoneMap = remember(uiProfile.baseVariantId) {
        mutableStateMapOf<String, ComposeActiveZoneSlotState>()
    }
    val activeSupportZoneMap = remember(uiProfile.baseVariantId) {
        mutableStateMapOf<String, ComposeActiveZoneSlotState>()
    }

    var fieldZoneCard by remember(uiProfile.baseVariantId) { mutableStateOf<CardTemplate?>(null) }
    var deckRemainingCount by remember(uiProfile.baseVariantId) { mutableIntStateOf(35) }
    var extraDeckCount by remember(uiProfile.baseVariantId) { mutableIntStateOf(15) }
    var graveyardCount by remember(uiProfile.baseVariantId) { mutableIntStateOf(2) }
    var banishedCount by remember(uiProfile.baseVariantId) { mutableIntStateOf(1) }

    var selectedActiveZoneCode by remember(uiProfile.baseVariantId) {
        mutableStateOf(mainZoneCodes.firstOrNull() ?: "MMZ-1")
    }
    var selectedHandSlotIndex by remember(uiProfile.baseVariantId, handCards.size) {
        mutableIntStateOf(0)
    }

    // Seed initial Active Zone placeholders and sync with incoming hostUnits
    LaunchedEffect(uiProfile.baseVariantId, hostUnits.size) {
        mainZoneCodes.forEachIndexed { index, code ->
            val unit = hostUnits.getOrNull(index)
            val existing = activeMainZoneMap[code]
            if (existing == null || (existing.card == null && unit != null)) {
                activeMainZoneMap[code] = ComposeActiveZoneSlotState(
                    zoneCode = code,
                    zoneTitle = "Active Main Zone ${index + 1}",
                    zoneCategory = "MAIN_UNIT_ZONE",
                    card = unit
                )
            }
        }
        supportZoneCodes.forEachIndexed { index, code ->
            if (!activeSupportZoneMap.containsKey(code)) {
                val starterSupport = if (index == 0) handCards.lastOrNull() else null
                activeSupportZoneMap[code] = ComposeActiveZoneSlotState(
                    zoneCode = code,
                    zoneTitle = "Active Support Zone ${index + 1}",
                    zoneCategory = "SUPPORT_SPELL_ZONE",
                    card = starterSupport,
                    faceDownSet = index == 0 && starterSupport != null
                )
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .testTag("compose_gameboard_layout_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianSurface,
                            Color(0xFF0A0F1D),
                            ObsidianSurface
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header Bar: Game Board Compose Layout Title + Phase & Zone Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "COMPOSE GAME BOARD LAYOUT • HAND, DECK & ACTIVE ZONES",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${uiProfile.systemTitle} • Turn ${engineState.turnNumber} (${engineState.currentPhase.displayName})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        mainZoneCodes.forEachIndexed { idx, code ->
                            activeMainZoneMap[code] = ComposeActiveZoneSlotState(
                                zoneCode = code,
                                zoneTitle = "Active Main Zone ${idx + 1}",
                                zoneCategory = "MAIN_UNIT_ZONE",
                                card = hostUnits.getOrNull(idx)
                            )
                        }
                        supportZoneCodes.forEachIndexed { idx, code ->
                            activeSupportZoneMap[code] = ComposeActiveZoneSlotState(
                                zoneCode = code,
                                zoneTitle = "Active Support Zone ${idx + 1}",
                                zoneCategory = "SUPPORT_SPELL_ZONE",
                                card = null
                            )
                        }
                        fieldZoneCard = null
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("compose_board_reset_placeholders_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Zone Placeholders",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Slots", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 2. Opponent Compact Active Zones Row (Top Mirror Placeholders)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianElevated.copy(alpha = 0.75f))
                    .border(1.dp, CrimsonAlert.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(10.dp)
                    .testTag("compose_board_opponent_zones_row"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OPPONENT ACTIVE ZONES (${engineState.opponentName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonAlert,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${engineState.opponentLife} ${uiProfile.lifeLabel} • Deck: 36 • GY: 1",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonAlert,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mainZoneCodes.forEachIndexed { idx, code ->
                        val oppCard = opponentUnits.getOrNull(idx)
                        OpponentZonePlaceholderSlot(
                            zoneCode = "OPP-$code",
                            card = oppCard
                        )
                    }
                }
            }

            // 2B. Dynamic Turn Phase Indicator Component (Draw • Standby • Main • Combat • End)
            TurnPhaseIndicatorBar(
                engineState = engineState,
                baseSpec = activeBaseSpec,
                uiProfile = uiProfile,
                activeCustomVariant = activeCustomVariant?.takeIf { it.baseVariantId == activeBaseSpec.baseVariantId },
                onSelectPhase = onSelectPhase,
                onSelectClientPhaseCode = onSelectClientPhaseCode,
                onStepNextPhase = onStepPhase
            )

            // 3. Shared Center Strip: Field Zone Placeholder, Extra/Command Zone Placeholders & Turn Phase Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0E1526))
                    .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
                    .horizontalScroll(rememberScrollState())
                    .testTag("compose_board_center_shared_zones"),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Field / Environment Zone Placeholder
                PeripheralZonePlaceholderBox(
                    zoneCode = "FIELD ZONE",
                    subtitle = fieldZoneCard?.name ?: "Tap to Set Field / World",
                    countBadge = if (fieldZoneCard != null) "ACTIVE" else "EMPTY",
                    accentColor = EmeraldReady,
                    isSelected = selectedActiveZoneCode == "FIELD_ZONE",
                    onClick = {
                        selectedActiveZoneCode = "FIELD_ZONE"
                        val candidate = handCards.getOrNull(selectedHandSlotIndex) ?: handCards.firstOrNull()
                        if (fieldZoneCard == null && candidate != null) {
                            fieldZoneCard = candidate
                            onDeployCardToActiveZone(candidate, "FIELD_ZONE")
                        }
                    },
                    modifier = Modifier.testTag("placeholder_zone_field")
                )

                // Extra Monster / Command Zone Left Placeholder
                PeripheralZonePlaceholderBox(
                    zoneCode = if (BuiltInTcgCatalog.isEdoProVariant(uiProfile.baseVariantId)) "EMZ-L" else "CMD ZONE",
                    subtitle = "Shared Link / Command Slot",
                    countBadge = "OPEN",
                    accentColor = accentColor,
                    isSelected = selectedActiveZoneCode == "EMZ_L",
                    onClick = { selectedActiveZoneCode = "EMZ_L" },
                    modifier = Modifier.testTag("placeholder_zone_emz_left")
                )

                // Active Phase & Turn Center Indicator
                Surface(
                    color = accentColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, accentColor, RoundedCornerShape(10.dp))
                        .clickable { onStepPhase() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("placeholder_center_phase_indicator")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TURN ${engineState.turnNumber} • ${engineState.currentPhase.code}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap to Advance -> ${engineState.currentPhase.nextPhase().code}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Extra Monster / Stack Zone Right Placeholder
                PeripheralZonePlaceholderBox(
                    zoneCode = if (BuiltInTcgCatalog.isEdoProVariant(uiProfile.baseVariantId)) "EMZ-R" else "THE STACK",
                    subtitle = "Chain / Stack Resolution Zone",
                    countBadge = "${engineState.effectStack.size} CL",
                    accentColor = RadiantGold,
                    isSelected = selectedActiveZoneCode == "EMZ_R",
                    onClick = { selectedActiveZoneCode = "EMZ_R" },
                    modifier = Modifier.testTag("placeholder_zone_emz_right")
                )
            }

            // 4. Player 1 Active Zones + Deck / Extra Deck / GY / Banished Placeholders
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ObsidianElevated)
                    .border(1.dp, EmeraldReady.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
                    .testTag("compose_board_player_active_zones_section"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PLAYER ACTIVE ZONES (${uiProfile.fieldSlotsBottomLabel})",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldReady,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Selected Target Slot: [$selectedActiveZoneCode] — Tap any Active Zone placeholder to select or deploy from Hand",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${engineState.hostLife} ${uiProfile.lifeLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldReady,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Row A: Player Active Main Zones (5 Monster / Creature / Battlefield Placeholders)
                Text(
                    text = "Main Battlefield / Monster Active Zones (${mainZoneCodes.joinToString(" • ")}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mainZoneCodes.forEachIndexed { idx, code ->
                        val slotState = activeMainZoneMap[code] ?: ComposeActiveZoneSlotState(
                            zoneCode = code,
                            zoneTitle = "Active Main Zone ${idx + 1}",
                            zoneCategory = "MAIN_UNIT_ZONE"
                        )
                        ActiveBoardZonePlaceholderCard(
                            slotState = slotState,
                            isSelected = selectedActiveZoneCode == code,
                            accentColor = EmeraldReady,
                            placeholderHint = "EMPTY MAIN ZONE\nTap to Deploy Unit",
                            onSelectOrDeploy = {
                                selectedActiveZoneCode = code
                                if (slotState.card == null) {
                                    val cardToDeploy = handCards.getOrNull(selectedHandSlotIndex) ?: handCards.firstOrNull()
                                    if (cardToDeploy != null) {
                                        activeMainZoneMap[code] = slotState.copy(card = cardToDeploy)
                                        onDeployCardToActiveZone(cardToDeploy, code)
                                    }
                                }
                            },
                            onRotateCard = {
                                slotState.card?.let {
                                    activeMainZoneMap[code] = slotState.copy(
                                        isDefenseOrTapped = !slotState.isDefenseOrTapped
                                    )
                                }
                            },
                            onTriggerCardEffect = {
                                slotState.card?.let { onQueueCardEffectFromZone(it) }
                            },
                            onSendToGraveyard = {
                                if (slotState.card != null) {
                                    activeMainZoneMap[code] = slotState.copy(card = null, isDefenseOrTapped = false)
                                    graveyardCount += 1
                                }
                            },
                            modifier = Modifier.testTag("active_main_zone_placeholder_$idx")
                        )
                    }
                }

                // Row B: Player Active Support / Spell & Trap / Mana Zones (5 Support Placeholders)
                Text(
                    text = "Support / Spell & Trap / Mana Active Zones (${supportZoneCodes.joinToString(" • ")}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    supportZoneCodes.forEachIndexed { idx, code ->
                        val slotState = activeSupportZoneMap[code] ?: ComposeActiveZoneSlotState(
                            zoneCode = code,
                            zoneTitle = "Active Support Zone ${idx + 1}",
                            zoneCategory = "SUPPORT_SPELL_ZONE"
                        )
                        ActiveBoardZonePlaceholderCard(
                            slotState = slotState,
                            isSelected = selectedActiveZoneCode == code,
                            accentColor = accentColor,
                            placeholderHint = "EMPTY SUPPORT ZONE\nSet Spell / Trap / Land",
                            onSelectOrDeploy = {
                                selectedActiveZoneCode = code
                                if (slotState.card == null) {
                                    val cardToSet = handCards.getOrNull(selectedHandSlotIndex) ?: handCards.firstOrNull()
                                    if (cardToSet != null) {
                                        activeSupportZoneMap[code] = slotState.copy(
                                            card = cardToSet,
                                            faceDownSet = false
                                        )
                                        onDeployCardToActiveZone(cardToSet, code)
                                    }
                                }
                            },
                            onRotateCard = {
                                slotState.card?.let {
                                    activeSupportZoneMap[code] = slotState.copy(
                                        isDefenseOrTapped = !slotState.isDefenseOrTapped
                                    )
                                }
                            },
                            onTriggerCardEffect = {
                                slotState.card?.let { onQueueCardEffectFromZone(it) }
                            },
                            onSendToGraveyard = {
                                if (slotState.card != null) {
                                    activeSupportZoneMap[code] = slotState.copy(card = null, isDefenseOrTapped = false)
                                    graveyardCount += 1
                                }
                            },
                            modifier = Modifier.testTag("active_support_zone_placeholder_$idx")
                        )
                    }
                }

                // Row C: Deck Placeholder + Extra Deck + Graveyard (GY) + Banishment Placeholders
                Text(
                    text = "Player Deck & Pile Placeholders (Tap Deck to Draw Card):",
                    style = MaterialTheme.typography.labelSmall,
                    color = RadiantGold,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Primary Deck Stack Placeholder
                    DeckStackPlaceholderCard(
                        deckTitle = "MAIN DECK",
                        remainingCount = deckRemainingCount,
                        accentColor = RadiantGold,
                        onDrawCard = {
                            if (deckRemainingCount > 0) {
                                deckRemainingCount -= 1
                            }
                            onDrawFromDeckPlaceholder()
                        },
                        modifier = Modifier.testTag("compose_board_deck_placeholder")
                    )

                    // Extra Deck / Command Pile Placeholder
                    PeripheralZonePlaceholderBox(
                        zoneCode = if (BuiltInTcgCatalog.isEdoProVariant(uiProfile.baseVariantId)) "EXTRA DECK" else "SIDE / COMMAND",
                        subtitle = "Synchro / Xyz / Link / Cmd",
                        countBadge = "$extraDeckCount CARDS",
                        accentColor = accentColor,
                        isSelected = selectedActiveZoneCode == "EXTRA_DECK",
                        onClick = { selectedActiveZoneCode = "EXTRA_DECK" },
                        modifier = Modifier.testTag("compose_board_extra_deck_placeholder")
                    )

                    // Graveyard (GY) Pile Placeholder
                    PeripheralZonePlaceholderBox(
                        zoneCode = "GRAVEYARD (GY)",
                        subtitle = "Destroyed / Resolved Pile",
                        countBadge = "$graveyardCount CARDS",
                        accentColor = CrimsonAlert,
                        isSelected = selectedActiveZoneCode == "GRAVEYARD",
                        onClick = { selectedActiveZoneCode = "GRAVEYARD" },
                        modifier = Modifier.testTag("compose_board_graveyard_placeholder")
                    )

                    // Banished / Exile Pile Placeholder
                    PeripheralZonePlaceholderBox(
                        zoneCode = if (BuiltInTcgCatalog.isEdoProVariant(uiProfile.baseVariantId)) "BANISHED" else "EXILE ZONE",
                        subtitle = "Removed from Play",
                        countBadge = "$banishedCount CARDS",
                        accentColor = Color(0xFFE040FB),
                        isSelected = selectedActiveZoneCode == "BANISHED",
                        onClick = { selectedActiveZoneCode = "BANISHED" },
                        modifier = Modifier.testTag("compose_board_banished_placeholder")
                    )
                }
            }

            // 4B. Dynamic Ruleset Visual Resource Counter Component (Mana / Energy / Action Points)
            VisualResourceCounterBar(
                baseSpec = activeBaseSpec,
                uiProfile = uiProfile,
                activeCustomVariant = activeCustomVariant?.takeIf { it.baseVariantId == activeBaseSpec.baseVariantId },
                hostAvailableResources = overrideAvailableResources ?: engineState.hostResources,
                hostMaxResources = overrideMaxResources ?: engineState.hostMaxResources,
                opponentAvailableResources = overrideOpponentResources ?: engineState.opponentResources,
                opponentMaxResources = overrideMaxResources ?: engineState.opponentMaxResources,
                normalSummonsRemaining = engineState.normalSummonsRemaining,
                onSpendResource = onSpendResource,
                onGainResource = onGainResource,
                onSetExactResourceCount = onSetResourceExact,
                onRefillResources = onRefillResources,
                onExpandMaxResourceCap = onExpandMaxResourceCap
            )

            // 5. Player Hand Zone Placeholders (Populated Cards + Empty Hand Slot Placeholders)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF090E1A))
                    .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
                    .testTag("compose_board_hand_section"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val maxHandPlaceholders = 6
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PLAYER HAND ZONE (${handCards.size}/$maxHandPlaceholders SLOTS FILLED)",
                            style = MaterialTheme.typography.labelSmall,
                            color = RadiantGold,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select a Hand Card below and tap '${uiProfile.deployVerb} to [$selectedActiveZoneCode]' or tap an empty Hand Placeholder to Draw",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            if (deckRemainingCount > 0) deckRemainingCount -= 1
                            onDrawFromDeckPlaceholder()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                        modifier = Modifier.testTag("compose_board_hand_draw_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Draw Card to Hand Placeholder",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Draw Card", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (slotIdx in 0 until maxHandPlaceholders) {
                        val cardInSlot = handCards.getOrNull(slotIdx)
                        HandSlotPlaceholderItem(
                            slotIndex = slotIdx,
                            card = cardInSlot,
                            isSelected = selectedHandSlotIndex == slotIdx,
                            targetZoneCode = selectedActiveZoneCode,
                            deployVerb = uiProfile.deployVerb,
                            accentColor = accentColor,
                            onSelectSlot = { selectedHandSlotIndex = slotIdx },
                            onDeployToSelectedZone = { card ->
                                when {
                                    activeMainZoneMap.containsKey(selectedActiveZoneCode) -> {
                                        val current = activeMainZoneMap[selectedActiveZoneCode]
                                        if (current != null) {
                                            activeMainZoneMap[selectedActiveZoneCode] = current.copy(card = card)
                                        }
                                    }
                                    activeSupportZoneMap.containsKey(selectedActiveZoneCode) -> {
                                        val current = activeSupportZoneMap[selectedActiveZoneCode]
                                        if (current != null) {
                                            activeSupportZoneMap[selectedActiveZoneCode] = current.copy(card = card)
                                        }
                                    }
                                    selectedActiveZoneCode == "FIELD_ZONE" -> {
                                        fieldZoneCard = card
                                    }
                                    else -> {
                                        val firstEmptyMain = mainZoneCodes.firstOrNull {
                                            activeMainZoneMap[it]?.card == null
                                        } ?: mainZoneCodes.first()
                                        val current = activeMainZoneMap[firstEmptyMain]
                                        if (current != null) {
                                            activeMainZoneMap[firstEmptyMain] = current.copy(card = card)
                                        }
                                        selectedActiveZoneCode = firstEmptyMain
                                    }
                                }
                                onDeployCardToActiveZone(card, selectedActiveZoneCode)
                            },
                            onQueueEffect = { card ->
                                onQueueCardEffectFromZone(card)
                            },
                            onDrawIntoEmptySlot = {
                                if (deckRemainingCount > 0) deckRemainingCount -= 1
                                onDrawFromDeckPlaceholder()
                            },
                            modifier = Modifier.testTag("hand_slot_placeholder_$slotIdx")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpponentZonePlaceholderSlot(
    zoneCode: String,
    card: CardTemplate?
) {
    Box(
        modifier = Modifier
            .width(118.dp)
            .height(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (card != null) CrimsonAlert.copy(alpha = 0.14f) else Color(0xFF0B101B))
            .border(
                width = 1.dp,
                color = if (card != null) CrimsonAlert else CrimsonAlert.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (card == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = zoneCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonAlert.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "[EMPTY ZONE]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = zoneCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonAlert,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${card.attack}/${card.defense}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RadiantGold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ActiveBoardZonePlaceholderCard(
    slotState: ComposeActiveZoneSlotState,
    isSelected: Boolean,
    accentColor: Color,
    placeholderHint: String,
    onSelectOrDeploy: () -> Unit,
    onRotateCard: () -> Unit,
    onTriggerCardEffect: () -> Unit,
    onSendToGraveyard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> RadiantGold
            slotState.card != null -> accentColor
            else -> accentColor.copy(alpha = 0.35f)
        },
        label = "activeZoneBorder"
    )
    val card = slotState.card

    Box(
        modifier = modifier
            .width(142.dp)
            .height(136.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (card != null) accentColor.copy(alpha = 0.12f)
                else Color(0xFF0A0F1A)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelectOrDeploy() }
            .padding(8.dp)
    ) {
        if (card == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = slotState.zoneCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (isSelected) RadiantGold else accentColor.copy(alpha = 0.4f),
                                CircleShape
                            )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Empty Active Zone Placeholder",
                    tint = if (isSelected) RadiantGold else accentColor.copy(alpha = 0.65f),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = placeholderHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = slotState.zoneCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (slotState.isDefenseOrTapped) "DEF/TAP" else "ATK/UNTAP",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (slotState.isDefenseOrTapped) RadiantGold else EmeraldReady,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = card.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "ATK ${card.attack} / DEF ${card.defense}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RadiantGold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onRotateCard() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = "Rotate Orientation",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Pos", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Surface(
                        color = accentColor.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onTriggerCardEffect() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Trigger Card Effect",
                                modifier = Modifier.size(12.dp),
                                tint = RadiantGold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Effect", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Surface(
                        color = CrimsonAlert.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onSendToGraveyard() }
                    ) {
                        Text(
                            text = "GY",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonAlert,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckStackPlaceholderCard(
    deckTitle: String,
    remainingCount: Int,
    accentColor: Color,
    onDrawCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(154.dp)
            .height(96.dp)
            .clickable { onDrawCard() }
    ) {
        // Back stack shadow layers for 3D deck depth
        Box(
            modifier = Modifier
                .offset(x = 6.dp, y = 6.dp)
                .width(146.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141B2D))
                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
        )
        Box(
            modifier = Modifier
                .offset(x = 3.dp, y = 3.dp)
                .width(146.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF192238))
                .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
        )
        // Top Deck Sleeve Card
        Box(
            modifier = Modifier
                .width(146.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E2942),
                            Color(0xFF0E1525)
                        )
                    )
                )
                .border(1.5.dp, accentColor, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Style,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = deckTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "$remainingCount",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "TAP DECK PLACEHOLDER TO DRAW CARD",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldReady,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun PeripheralZonePlaceholderBox(
    zoneCode: String,
    subtitle: String,
    countBadge: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(142.dp)
            .height(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.18f) else Color(0xFF0B101D))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) RadiantGold else accentColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = zoneCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = countBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = RadiantGold,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HandSlotPlaceholderItem(
    slotIndex: Int,
    card: CardTemplate?,
    isSelected: Boolean,
    targetZoneCode: String,
    deployVerb: String,
    accentColor: Color,
    onSelectSlot: () -> Unit,
    onDeployToSelectedZone: (CardTemplate) -> Unit,
    onQueueEffect: (CardTemplate) -> Unit,
    onDrawIntoEmptySlot: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(172.dp)
            .height(166.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (card != null) Color(0xFF131B2E)
                else Color(0xFF0B101C)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = when {
                    isSelected -> RadiantGold
                    card != null -> accentColor.copy(alpha = 0.65f)
                    else -> accentColor.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .clickable {
                onSelectSlot()
                if (card == null) {
                    onDrawIntoEmptySlot()
                }
            }
            .padding(10.dp)
    ) {
        if (card == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "HAND SLOT #${slotIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Empty Hand Slot Placeholder",
                    tint = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "EMPTY HAND PLACEHOLDER\nTap to Draw Card",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HAND #${slotIndex + 1} • COST ${card.cost}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${card.attack}/${card.defense}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = card.abilityText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { onDeployToSelectedZone(card) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("hand_slot_deploy_button_$slotIndex")
                    ) {
                        Text(
                            text = "$deployVerb -> $targetZoneCode",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { onQueueEffect(card) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("hand_slot_effect_button_$slotIndex")
                    ) {
                        Text(
                            text = "Effect",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
