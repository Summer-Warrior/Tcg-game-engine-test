package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.TwentyColorPieCatalog
import com.example.engine.CatalogBlueprint
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import com.example.ui.theme.duelDiskPlate
import com.example.ui.theme.rememberChamferedDuelShape

enum class ActiveTcgFlourishFamily(val title: String) {
    YUGIOH_DUEL_CORE("Yu-Gi-Oh! Solid Vision Zones"),
    MTG_PLANAR("MTG Mana / 20-Color Pie / Stack"),
    POKEMON_ARENA("Pokémon Active, Bench, Prizes & Status"),
    DIGIMON_MEMORY("Digimon Shared Memory Gauge"),
    ONE_PIECE_DON("One Piece DON!! & Life Stack"),
    VANGUARD_CRAY("Vanguard 6-Circle & Drive Check"),
    LORCANA_LORE("Lorcana 20-Lore Dial & Inkwell"),
    FLESH_AND_BLOOD("Flesh & Blood Combat Chain & Pitch"),
    STAR_WARS_UNLIMITED("Star Wars: Unlimited Space / Ground"),
    SNAP_CROSS_DUEL("3-Lane Location Plates & Boss Center"),
    VIDEO_GAME_MINI_GAME("Video-Game Grid, Intent & Chips×Mult"),
    ULTIMATE_TCG_WIP("Ultimate TCG Dual-Pulse, World & PXZ");

    companion object {
        fun resolve(blueprint: CatalogBlueprint?, baseVariantId: String): ActiveTcgFlourishFamily {
            val id = (blueprint?.id ?: baseVariantId).uppercase()
            val name = blueprint?.name.orEmpty().lowercase()
            val arch = blueprint?.boardArchetype.orEmpty().lowercase()
            val cat = blueprint?.category.orEmpty().uppercase()
            return when {
                id == "ULTIMATE_TCG_WIP" || name.contains("ultimate tcg") -> ULTIMATE_TCG_WIP
                name.contains("pokémon") || name.contains("pokemon") -> POKEMON_ARENA
                name.contains("digimon") -> DIGIMON_MEMORY
                name.contains("one piece") -> ONE_PIECE_DON
                name.contains("vanguard") || name.contains("planet cray") -> VANGUARD_CRAY
                name.contains("lorcana") -> LORCANA_LORE
                name.contains("flesh and blood") || name.contains("talishar") -> FLESH_AND_BLOOD
                name.contains("star wars") || name.contains("karabast") -> STAR_WARS_UNLIMITED
                name.contains("marvel snap") || name.contains("cross duel") -> SNAP_CROSS_DUEL
                cat == "TCG_VIDEO_GAME" || cat == "MINI_GAME" ||
                    arch.contains("video-game") || arch.contains("directional") || arch.contains("grid") -> VIDEO_GAME_MINI_GAME
                id.contains("FORGE") || name.contains("magic") || name.contains("planar") || arch == "battlefield-stack" -> MTG_PLANAR
                else -> YUGIOH_DUEL_CORE
            }
        }
    }
}

/**
 * Modular clean-room TCG Visual Flourish Overlay layered dynamically ON TOP of the single
 * Yu-Gi-Oh! anime/manga/video-game core playmat — equipping authentic tactile mechanics
 * for whichever TCG, Notebook Format, or AI Combo is active (ZERO duplicate boards).
 */
@Composable
fun TcgVisualModuleOverlay(
    blueprint: CatalogBlueprint?,
    baseVariantId: String,
    stackDepth: Int,
    hostUnitCount: Int,
    opponentUnitCount: Int,
    magicPulsePct: Int,
    forcePulsePct: Int,
    outsiderVitalityMode: Boolean,
    pxzFrontlineCount: Int,
    pxzXpGaugePct: Int,
    crossHitActive: Boolean,
    bossIntentText: String,
    programAdvanceReady: Boolean,
    is3dGridOverlay: Boolean,
    onTriggerQuickFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val family = remember(blueprint?.id, baseVariantId) {
        ActiveTcgFlourishFamily.resolve(blueprint, baseVariantId)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tcg_visual_module_overlay"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Always-available Yu-Gi-Oh! Holographic Zone Perimeter Strip (Pendulum Crystals, EMZ Link Chevrons, Field Halo, GY/Banished)
        YuGiOhCoreZonePerimeterStrip(
            hostUnitCount = hostUnitCount,
            opponentUnitCount = opponentUnitCount,
            stackDepth = stackDepth
        )

        // Dynamic layered flourish module for every non-Yu-Gi-Oh! TCG & Flagship Ultimate TCG
        when (family) {
            ActiveTcgFlourishFamily.YUGIOH_DUEL_CORE -> Unit
            ActiveTcgFlourishFamily.MTG_PLANAR -> MtgPlanarOverlayStrip(stackDepth = stackDepth, onFeedback = onTriggerQuickFeedback)
            ActiveTcgFlourishFamily.POKEMON_ARENA -> PokemonArenaOverlayStrip(onFeedback = onTriggerQuickFeedback)
            ActiveTcgFlourishFamily.DIGIMON_MEMORY -> DigimonMemoryGaugeStrip(onFeedback = onTriggerQuickFeedback)
            ActiveTcgFlourishFamily.ONE_PIECE_DON -> OnePieceDonOverlayStrip(onFeedback = onTriggerQuickFeedback)
            ActiveTcgFlourishFamily.VANGUARD_CRAY -> VanguardSixCircleStrip(onFeedback = onTriggerQuickFeedback)
            ActiveTcgFlourishFamily.LORCANA_LORE -> LorcanaLoreDialStrip(onFeedback = onTriggerQuickFeedback)
            ActiveTcgFlourishFamily.FLESH_AND_BLOOD -> FleshAndBloodChainStrip(onFeedback = onTriggerQuickFeedback)
            ActiveTcgFlourishFamily.STAR_WARS_UNLIMITED -> StarWarsArenaSplitStrip(onFeedback = onTriggerQuickFeedback)
            ActiveTcgFlourishFamily.SNAP_CROSS_DUEL -> ThreeLaneLocationStrip(hostUnitCount, opponentUnitCount)
            ActiveTcgFlourishFamily.VIDEO_GAME_MINI_GAME -> VideoGameMiniGameStrip(
                bossIntentText = bossIntentText,
                programAdvanceReady = programAdvanceReady,
                onFeedback = onTriggerQuickFeedback
            )
            ActiveTcgFlourishFamily.ULTIMATE_TCG_WIP -> UltimateTcgCosmologyStrip(
                magicPulsePct = magicPulsePct,
                forcePulsePct = forcePulsePct,
                outsiderVitalityMode = outsiderVitalityMode,
                pxzFrontlineCount = pxzFrontlineCount,
                pxzXpGaugePct = pxzXpGaugePct,
                crossHitActive = crossHitActive,
                is3dGridOverlay = is3dGridOverlay,
                onFeedback = onTriggerQuickFeedback
            )
        }
    }
}

@Composable
private fun YuGiOhCoreZonePerimeterStrip(
    hostUnitCount: Int,
    opponentUnitCount: Int,
    stackDepth: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = ElectricCyan, secondaryColor = RadiantGold, cutSize = 7.dp, halftoneAccents = false)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoloBadge("PENDULUM ‹1 | 8›", Color(0xFF38BDF8))
        HoloBadge("EMZ LINK ↙↓↘", ElectricCyan)
        HoloBadge("FIELD HALO", EmeraldReady)
        HoloBadge("GY VORTEX ($opponentUnitCount)", Color(0xFF94A3B8))
        HoloBadge("BANISH RIFT", Color(0xFFC084FC))
        if (stackDepth > 0) {
            HoloBadge("CHAIN CL$stackDepth", RadiantGold)
        }
        HoloBadge("MMZ $hostUnitCount/5", RadiantGold)
    }
}

@Composable
private fun MtgPlanarOverlayStrip(
    stackDepth: Int,
    onFeedback: (String) -> Unit
) {
    var fullControl by rememberSaveable { mutableStateOf(false) }
    var loyalty by rememberSaveable { mutableIntStateOf(4) }
    var omniIdx by rememberSaveable { mutableIntStateOf(0) }
    val omni = TwentyColorPieCatalog.omniGeometryVariants[omniIdx % 6]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = Color(0xFF38BDF8), secondaryColor = RadiantGold, cutSize = 8.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 5-Anchor + 20-Color Diamond crystals + Layer-20 {Ω} halo
        listOf(
            "W" to Color(0xFFF8F5E6),
            "U" to Color(0xFF38BDF8),
            "B" to Color(0xFF94A3B8),
            "R" to Color(0xFFEF4444),
            "G" to Color(0xFF22C55E),
            "Cy" to ElectricCyan
        ).forEach { (code, tint) ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.25f))
                    .border(1.2.dp, tint, CircleShape)
            ) {
                Text(code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        HoloBadge(
            text = "${omni.code} ${omni.geometryName}",
            accent = RadiantGold,
            onClick = {
                omniIdx = (omniIdx + 1) % 6
                onFeedback("Cycled Layer-20 Omnichroma halo -> ${TwentyColorPieCatalog.omniGeometryVariants[omniIdx].code}")
            }
        )
        HoloBadge("STACK ($stackDepth)", Color(0xFF818CF8))
        HoloBadge(
            text = "LOYALTY [$loyalty]",
            accent = EmeraldReady,
            onClick = { loyalty = (loyalty % 9) + 1 }
        )
        HoloBadge(
            text = if (fullControl) "MTGA FULL CONTROL ON" else "MTGO CLOCK 24:58 • F2",
            accent = if (fullControl) RadiantGold else ElectricCyan,
            onClick = {
                fullControl = !fullControl
                onFeedback(if (fullControl) "MTGA Full Control Priority Ring ENABLED" else "Switched to MTGO Auto-Yield (F2)")
            }
        )
    }
}

@Composable
private fun PokemonArenaOverlayStrip(onFeedback: (String) -> Unit) {
    var prizesLeft by rememberSaveable { mutableIntStateOf(6) }
    var statusIdx by rememberSaveable { mutableIntStateOf(0) }
    val statuses = listOf("CLEAR", "BURN", "POISON", "PARALYZED", "ASLEEP", "CONFUSED")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = Color(0xFFFFD600), secondaryColor = ElectricCyan, cutSize = 8.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoloBadge("ACTIVE SPOTLIGHT", Color(0xFFFFD600))
        HoloBadge("BENCH 5 SLOTS", ElectricCyan)
        HoloBadge("PRIZES: $prizesLeft/6", EmeraldReady, onClick = {
            prizesLeft = if (prizesLeft > 1) prizesLeft - 1 else 6
            onFeedback("Prize Cards remaining: $prizesLeft")
        })
        HoloBadge("ENERGY ORBS ⚡⚡🔥", RadiantGold)
        HoloBadge("STATUS: ${statuses[statusIdx]}", CrimsonAlert, onClick = {
            statusIdx = (statusIdx + 1) % statuses.size
        })
    }
}

@Composable
private fun DigimonMemoryGaugeStrip(onFeedback: (String) -> Unit) {
    var memoryPos by rememberSaveable { mutableIntStateOf(3) } // -10..+10
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = Color(0xFF38BDF8), secondaryColor = RadiantGold, cutSize = 8.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("DIGIMON SHARED MEMORY GAUGE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = ElectricCyan)
            Text(
                text = if (memoryPos >= 0) "YOUR TURN (+$memoryPos)" else "OPPONENT TURN ($memoryPos)",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (memoryPos >= 0) EmeraldReady else CrimsonAlert
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            (-10..10).forEach { v ->
                val isMarker = v == memoryPos
                val label = kotlin.math.abs(v).toString()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isMarker -> RadiantGold
                                v == 0 -> ElectricCyan.copy(alpha = 0.35f)
                                v > 0 -> EmeraldReady.copy(alpha = 0.18f)
                                else -> CrimsonAlert.copy(alpha = 0.18f)
                            }
                        )
                        .border(1.dp, if (isMarker) Color.White else Color(0xFF475569), CircleShape)
                        .clickable {
                            memoryPos = v
                            onFeedback("Memory Gauge shifted to $v (${if (v < 0) "Turn Handoff across 0!" else "Active"})")
                        }
                ) {
                    Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (isMarker) Color(0xFF090D16) else Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun OnePieceDonOverlayStrip(onFeedback: (String) -> Unit) {
    var activeDon by rememberSaveable { mutableIntStateOf(6) }
    var attachedDon by rememberSaveable { mutableIntStateOf(2) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = CrimsonAlert, secondaryColor = RadiantGold, cutSize = 8.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoloBadge("DON!! DECK $activeDon/10", RadiantGold, onClick = {
            activeDon = (activeDon % 10) + 1
            onFeedback("Active DON!! pool: $activeDon/10")
        })
        HoloBadge("ATTACHED +${attachedDon * 1000} DON!!", CrimsonAlert, onClick = {
            attachedDon = (attachedDon + 1) % 6
        })
        HoloBadge("LIFE STACK: 5", EmeraldReady)
    }
}

@Composable
private fun VanguardSixCircleStrip(onFeedback: (String) -> Unit) {
    var soulCount by rememberSaveable { mutableIntStateOf(3) }
    var damageZone by rememberSaveable { mutableIntStateOf(2) }
    var lastTrigger by rememberSaveable { mutableStateOf("Ready") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = RadiantGold, secondaryColor = Color(0xFF60A5FA), cutSize = 8.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoloBadge("6-CIRCLE: [VG ★] + 5 RG", RadiantGold)
        HoloBadge("SOUL: $soulCount", ElectricCyan, onClick = { soulCount += 1 })
        HoloBadge("DAMAGE: $damageZone/6", CrimsonAlert, onClick = { damageZone = (damageZone + 1) % 7 })
        HoloBadge("DRIVE CHECK: $lastTrigger", EmeraldReady, onClick = {
            val triggers = listOf("CRITICAL +10K!", "DRAW +1!", "HEAL -1 DMG!", "OVER TRIGGER +100M!")
            lastTrigger = triggers.random()
            onFeedback("Vanguard Twin Drive Trigger Flip: $lastTrigger")
        })
    }
}

@Composable
private fun LorcanaLoreDialStrip(onFeedback: (String) -> Unit) {
    var lore by rememberSaveable { mutableIntStateOf(7) }
    var inkwell by rememberSaveable { mutableIntStateOf(5) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = Color(0xFFC084FC), secondaryColor = RadiantGold, cutSize = 8.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoloBadge("LORE $lore / 20", RadiantGold, onClick = {
            lore = (lore + 2).coerceAtMost(20)
            onFeedback("Quest Lore Dial -> $lore / 20")
        })
        LinearProgressIndicator(
            progress = { (lore / 20f).coerceIn(0f, 1f) },
            color = RadiantGold,
            trackColor = Color(0xFF1E293B),
            modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp))
        )
        HoloBadge("INKWELL $inkwell", Color(0xFFC084FC), onClick = { inkwell += 1 })
    }
}

@Composable
private fun FleshAndBloodChainStrip(onFeedback: (String) -> Unit) {
    var chainLinks by rememberSaveable { mutableIntStateOf(2) }
    var pitchVal by rememberSaveable { mutableIntStateOf(3) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = Color(0xFFFB7185), secondaryColor = RadiantGold, cutSize = 8.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoloBadge("COMBAT CHAIN: $chainLinks LINKS", CrimsonAlert, onClick = {
            chainLinks = (chainLinks % 6) + 1
            onFeedback("Opened Combat Chain Link #$chainLinks")
        })
        HoloBadge("EQUIP: HEAD • CHEST • ARMS • LEGS", Color(0xFF94A3B8))
        HoloBadge("ARSENAL [1]", ElectricCyan)
        HoloBadge("PITCH ZONE: $pitchVal RES", RadiantGold, onClick = { pitchVal = (pitchVal % 3) + 1 })
    }
}

@Composable
private fun StarWarsArenaSplitStrip(onFeedback: (String) -> Unit) {
    var hasInitiative by rememberSaveable { mutableStateOf(true) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = Color(0xFF22D3EE), secondaryColor = RadiantGold, cutSize = 8.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoloBadge("✦ SPACE ARENA (STARFIELD)", Color(0xFF38BDF8))
        HoloBadge("▲ GROUND ARENA (TERRAIN)", EmeraldReady)
        HoloBadge(
            text = if (hasInitiative) "INITIATIVE: HOST" else "INITIATIVE: PEER",
            accent = RadiantGold,
            onClick = {
                hasInitiative = !hasInitiative
                onFeedback("Claimed Star Wars: Unlimited Initiative token.")
            }
        )
    }
}

@Composable
private fun ThreeLaneLocationStrip(hostUnits: Int, oppUnits: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = Color(0xFFF43F5E), secondaryColor = ElectricCyan, cutSize = 8.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            Triple("LEFT LANE", hostUnits * 4, oppUnits * 3),
            Triple("BOSS CENTER ★", hostUnits * 7 + 5, oppUnits * 5),
            Triple("RIGHT LANE", hostUnits * 3, oppUnits * 4)
        ).forEach { (title, p1, p2) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFF0E172A), RoundedCornerShape(6.dp))
                    .border(1.dp, if (title.contains("BOSS")) RadiantGold else ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = if (title.contains("BOSS")) RadiantGold else ElectricCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$p1 vs $p2", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = Color.White)
            }
        }
    }
}

@Composable
private fun VideoGameMiniGameStrip(
    bossIntentText: String,
    programAdvanceReady: Boolean,
    onFeedback: (String) -> Unit
) {
    var chips by rememberSaveable { mutableIntStateOf(120) }
    var mult by rememberSaveable { mutableIntStateOf(4) }
    var customGauge by rememberSaveable { mutableIntStateOf(80) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = Color(0xFF00F0FF), secondaryColor = CrimsonAlert, cutSize = 8.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HoloBadge("3×6 CYBER PANELS • 7×7 HEX", ElectricCyan)
        HoloBadge("INTENT: $bossIntentText", CrimsonAlert)
        HoloBadge("CHIPS $chips × MULT $mult = ${chips * mult}", RadiantGold, onClick = {
            chips += 40
            mult += 1
            onFeedback("Score Flare: $chips × $mult = ${chips * mult}!")
        })
        HoloBadge("CUSTOM GAUGE $customGauge%${if (programAdvanceReady) " • PA READY" else ""}", EmeraldReady, onClick = {
            customGauge = ((customGauge + 20) % 120).coerceAtLeast(20)
        })
        HoloBadge("SLEIGHT [0–9] • SCALE ⚖", Color(0xFFC084FC))
    }
}

@Composable
private fun UltimateTcgCosmologyStrip(
    magicPulsePct: Int,
    forcePulsePct: Int,
    outsiderVitalityMode: Boolean,
    pxzFrontlineCount: Int,
    pxzXpGaugePct: Int,
    crossHitActive: Boolean,
    is3dGridOverlay: Boolean,
    onFeedback: (String) -> Unit
) {
    var worldLawIdx by rememberSaveable { mutableIntStateOf(2) }
    val worldLaws = listOf("[Magic]", "[Force]", "[Dual-Law]", "[Extraversal Rift]")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .duelDiskPlate(accentColor = RadiantGold, secondaryColor = Color(0xFFE040FB), cutSize = 8.dp, pulseEnergy = crossHitActive)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HoloBadge(
                text = "WORLD ${worldLaws[worldLawIdx]} ⚷",
                accent = RadiantGold,
                onClick = {
                    worldLawIdx = (worldLawIdx + 1) % worldLaws.size
                    onFeedback("Combined World Zone shifted to ${worldLaws[worldLawIdx]}")
                }
            )
            HoloBadge("STANDBY ROW [2/3]", ElectricCyan)
            HoloBadge("PXZ PAIR+SOLO $pxzFrontlineCount/5", EmeraldReady)
            HoloBadge("XP $pxzXpGaugePct%/150%${if (crossHitActive) " ★ CROSS HIT!" else ""}", if (crossHitActive) CrimsonAlert else Color(0xFFE040FB))
            if (is3dGridOverlay) {
                HoloBadge("3D POSITIONING GRID ON", ElectricCyan)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (outsiderVitalityMode) {
                Text("OUTSIDER VITALITY BAR", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = Color(0xFFE040FB), fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { (magicPulsePct / 100f).coerceIn(0f, 1f) },
                    color = Color(0xFFE040FB),
                    trackColor = Color(0xFF1E293B),
                    modifier = Modifier.weight(1f).height(6.dp)
                )
            } else {
                Text("MAGIC $magicPulsePct%", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = ElectricCyan, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { (magicPulsePct / 100f).coerceIn(0f, 1f) },
                    color = ElectricCyan,
                    trackColor = Color(0xFF1E293B),
                    modifier = Modifier.weight(1f).height(6.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("FORCE $forcePulsePct%", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = RadiantGold, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { (forcePulsePct / 100f).coerceIn(0f, 1f) },
                    color = RadiantGold,
                    trackColor = Color(0xFF1E293B),
                    modifier = Modifier.weight(1f).height(6.dp)
                )
            }
        }
    }
}

@Composable
private fun HoloBadge(
    text: String,
    accent: Color,
    onClick: (() -> Unit)? = null
) {
    val shape = rememberChamferedDuelShape(5.dp)
    val base = Modifier
        .clip(shape)
        .background(
            Brush.horizontalGradient(
                listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.08f))
            )
        )
        .border(1.dp, accent.copy(alpha = 0.72f), shape)
    val finalMod = if (onClick != null) base.clickable { onClick() } else base
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = finalMod.padding(horizontal = 7.dp, vertical = 3.dp)
    )
}
