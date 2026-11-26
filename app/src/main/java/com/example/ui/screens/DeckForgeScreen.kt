package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BigInt21Engine
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardTemplate
import com.example.data.CustomDeck
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold
import com.example.viewmodel.UiState

@Composable
fun DeckForgeScreen(
    selectedBaseVariantId: String,
    decksState: UiState<List<CustomDeck>>,
    onSelectBaseVariant: (String) -> Unit,
    onCreateDeck: (String, String, String, String, Long, Long, Long, String, String, String, String, String, String) -> Unit,
    onDeleteDeck: (String) -> Unit,
    onBroadcastDeckToDiscord: (CustomDeck) -> Unit = {}
) {
    var showForgeDialog by remember { mutableStateOf(false) }
    var inspectedDeckForLua by remember { mutableStateOf<CustomDeck?>(null) }
    val activeSpec = BuiltInTcgCatalog.findBaseSpec(selectedBaseVariantId)
    val catalogCards = BuiltInTcgCatalog.cardsForVariant(selectedBaseVariantId)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("deck_forge_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Variant Filter Strip + Forge Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    BuiltInTcgCatalog.isHybridMixedVariant(selectedBaseVariantId) ->
                                        "MIXED FORGE (.DCK + SVAR) + EDOPRO (.YDK + LUA) FORGE"
                                    BuiltInTcgCatalog.isForgeVariant(selectedBaseVariantId) ->
                                        "CARD-FORGE .DCK & CARDSFOLDER/*.TXT SCRIPT FORGE"
                                    BuiltInTcgCatalog.isEdoProVariant(selectedBaseVariantId) ->
                                        "EDOPRO .YDK & 21-DIGIT OCGCORE LUA FORGE"
                                    else ->
                                        "MULTI-TCG DECK FORGE"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${activeSpec.name} Pool",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${activeSpec.engineCoreLabel} • Max ${activeSpec.maxDigitPrecision}-Digit Inputs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showForgeDialog = true },
                            modifier = Modifier.testTag("forge_deck_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Forge Deck",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Forge Deck")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(BuiltInTcgCatalog.baseVariants, key = { it.baseVariantId }) { spec ->
                            FilterChip(
                                selected = selectedBaseVariantId == spec.baseVariantId,
                                onClick = { onSelectBaseVariant(spec.baseVariantId) },
                                label = {
                                    Text(
                                        when (spec.baseVariantId) {
                                            "EDOPRO_BASE_MR5" -> "EDOPro Base (.cdb)"
                                            "EDOPRO_CUSTOM_FORK" -> "EDOPro 21-Digit Clone"
                                            "FORGE_MTG_BASE" -> "Card-Forge MTG (.dck)"
                                            "FORGE_CUSTOM_CLONE" -> "Forge 21d Clone"
                                            "HYBRID_FORGE_EDOPRO_MIX" -> "Mixed Forge+EDOPro"
                                            else -> spec.name.substringBefore(" ")
                                        }
                                    )
                                },
                                modifier = Modifier.testTag("deck_filter_${spec.baseVariantId}")
                            )
                        }
                    }
                }
            }
        }

        // Base Variant Card Pool Carousel
        item {
            Text(
                text = "Card Pool (${activeSpec.baseVariantId} • ${activeSpec.sourceRepoUrl})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(catalogCards, key = { it.id }) { card ->
                    TcgVisualCardItem(
                        card = card,
                        resourceName = activeSpec.resourceName
                    )
                }
            }
        }

        // User-Forged Custom Decks & Signature Cards
        item {
            Text(
                text = "Cloud-Synced .YDK / .DCK Decks & 21-Digit Scripts",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when (decksState) {
            is UiState.Loading -> {
                item {
                    Text(
                        text = "Loading decks...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is UiState.Error -> {
                item {
                    Text(
                        text = decksState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success -> {
                if (decksState.data.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "No forged decks",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No custom decks forged yet. Tap 'Forge Deck' to design a .ydk deck and 21-digit ocgcore Lua script.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(decksState.data, key = { it.id }) { deck ->
                        CustomDeckItemCard(
                            deck = deck,
                            onInspectLuaAndYdk = { inspectedDeckForLua = deck },
                            onBroadcastToDiscord = { onBroadcastDeckToDiscord(deck) },
                            onDelete = { onDeleteDeck(deck.id) }
                        )
                    }
                }
            }
        }
    }

    if (showForgeDialog) {
        ForgeDeckDialog(
            initialBaseVariantId = selectedBaseVariantId,
            onDismiss = { showForgeDialog = false },
            onConfirm = { deckName, baseId, tag, cardName, cost, atk, def, ability, atk21, def21, var21, codeStr21, codeVarExpr21 ->
                onCreateDeck(deckName, baseId, tag, cardName, cost, atk, def, ability, atk21, def21, var21, codeStr21, codeVarExpr21)
                showForgeDialog = false
            }
        )
    }

    inspectedDeckForLua?.let { deck ->
        LuaAndYdkInspectorDialog(
            deck = deck,
            onDismiss = { inspectedDeckForLua = null }
        )
    }
}

@Composable
fun TcgVisualCardItem(
    card: CardTemplate,
    resourceName: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(225.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                val resolvedArtResId = card.artResId?.takeIf {
                    it != 0 && it != com.example.R.drawable.ic_launcher_foreground
                }
                if (resolvedArtResId != null) {
                    Image(
                        painter = painterResource(id = resolvedArtResId),
                        contentDescription = card.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0B111E),
                                        Color(0xFF151F34),
                                        Color(0xFF0E1424)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = card.pipCost.ifBlank { "{C}" },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = card.cardType.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(30.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${card.cost}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${card.passcode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (card.attack21.length > 6) {
                            "${card.attack21.take(6)}.. (${card.attack21.length}d) ATK"
                        } else {
                            "${card.attack} ATK / ${card.defense} DEF"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${card.cardType} • ${card.cost} $resourceName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = card.abilityText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CustomDeckItemCard(
    deck: CustomDeck,
    onInspectLuaAndYdk: () -> Unit,
    onBroadcastToDiscord: () -> Unit,
    onDelete: () -> Unit
) {
    val isCustomClone = BuiltInTcgCatalog.supports21DigitRegisters(deck.baseVariantId)
    val isForge = BuiltInTcgCatalog.isForgeVariant(deck.baseVariantId)
    val isHybridMix = BuiltInTcgCatalog.isHybridMixedVariant(deck.baseVariantId)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .testTag("custom_deck_card_${deck.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Variant: ${deck.baseVariantId} • Format: ${deck.strategyTag}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCustomClone || isForge) RadiantGold else EmeraldReady
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_deck_${deck.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Deck",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Signature Card",
                    tint = RadiantGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ace Card: ${deck.customCardName} (Cost/Lv ${deck.customCardCost})",
                        style = MaterialTheme.typography.titleMedium,
                        color = RadiantGold
                    )
                    Text(
                        text = "ATK/P (${deck.customAttack21.length}d): ${deck.customAttack21} • DEF/T (${deck.customDefense21.length}d): ${deck.customDefense21}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = EmeraldReady
                    )
                    Text(
                        text = "Code String (${deck.customCodeString21.length}d): c${deck.customCodeString21} • ${deck.customCodeVarExpr21}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = RadiantGold
                    )
                    Text(
                        text = deck.customCardAbility,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onInspectLuaAndYdk,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inspect_lua_ydk_${deck.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = "Inspect Script and Deck File",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isHybridMix -> "Inspect Mixed Forge (.txt/.dck) + EDOPro (c${deck.customCodeString21.take(6)}..lua/.ydk)"
                        isForge -> "Inspect Card-Forge Script (cardsfolder/*.txt) & .DCK"
                        else -> "Inspect 21-Digit ocgcore Lua (c${deck.customCodeString21.take(8)}..lua) & .YDK"
                    }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = onBroadcastToDiscord,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("broadcast_deck_discord_${deck.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = "Broadcast Deck Summary to Discord",
                    tint = RadiantGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Broadcast Deck Summary to Discord Webhook",
                    color = RadiantGold
                )
            }
        }
    }
}

@Composable
private fun LuaAndYdkInspectorDialog(
    deck: CustomDeck,
    onDismiss: () -> Unit
) {
    val isForge = BuiltInTcgCatalog.isForgeVariant(deck.baseVariantId)
    val isHybridMix = BuiltInTcgCatalog.isHybridMixedVariant(deck.baseVariantId)
    val passcode21 = deck.customCodeString21.ifBlank {
        if (BuiltInTcgCatalog.supports21DigitRegisters(deck.baseVariantId)) {
            BigInt21Engine.DEFAULT_CODE_STRING_21
        } else {
            "89631139"
        }
    }
    val scriptContent = when {
        isHybridMix -> BuiltInTcgCatalog.generateHybridForgeEdoProScript(
            cardName = deck.customCardName,
            passcode21 = passcode21,
            levelOrManaCost = deck.customCardCost,
            atkOrPower21 = deck.customAttack21,
            defOrToughness21 = deck.customDefense21,
            varInput21 = deck.customVarInput21,
            codeVarExpr21 = deck.customCodeVarExpr21,
            funcName = "ADD_21",
            abilityText = deck.customCardAbility
        )
        isForge -> BuiltInTcgCatalog.generateForgeCardScript(
            cardName = deck.customCardName,
            passcode21 = passcode21,
            manaCostVal = deck.customCardCost,
            power21 = deck.customAttack21,
            toughness21 = deck.customDefense21,
            varInput21 = deck.customVarInput21,
            codeVarExpr21 = deck.customCodeVarExpr21,
            abilityText = deck.customCardAbility,
            isCustom21Clone = deck.baseVariantId == "FORGE_CUSTOM_CLONE"
        )
        else -> BuiltInTcgCatalog.generateOcgcoreLuaScript(
            cardName = deck.customCardName,
            passcode21 = passcode21,
            levelOrRank = deck.customCardCost,
            atk21 = deck.customAttack21,
            def21 = deck.customDefense21,
            varInput21 = deck.customVarInput21,
            codeVarExpr21 = deck.customCodeVarExpr21,
            funcName = "ADD_21",
            abilityText = deck.customCardAbility
        )
    }
    val deckFileContent = BuiltInTcgCatalog.generateYdkContent(
        deckName = deck.name,
        baseVariantId = deck.baseVariantId,
        customPasscode21 = passcode21
    )
    val deckExtension = when {
        isHybridMix -> "dck.ydk"
        isForge -> "dck"
        else -> "ydk"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    isHybridMix -> "Mixed Forge + EDOPro 21-Digit Script & Deck Inspector"
                    isForge -> "Card-Forge Script (cardsfolder/*.txt) & .DCK Inspector"
                    else -> "EDOPro 21-Digit Script & .YDK Inspector"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = when {
                        isHybridMix -> "hybrid/c$passcode21.forge.lua (${deck.baseVariantId} • Extensible Mixed Client)"
                        isForge -> "res/cardsfolder/${deck.customCardName.lowercase().replace(" ", "_")}.txt (${deck.baseVariantId})"
                        else -> "script/c$passcode21.lua (${deck.baseVariantId} • ${passcode21.length}-Digit Code String)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = RadiantGold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = scriptContent,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "deck/${deck.name.lowercase().replace(" ", "_")}.$deckExtension",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldReady
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = deckFileContent,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ForgeDeckDialog(
    initialBaseVariantId: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Long, Long, Long, String, String, String, String, String, String) -> Unit
) {
    var deckName by remember { mutableStateOf("") }
    var baseVariantId by remember { mutableStateOf(initialBaseVariantId) }
    val isEdoPro = BuiltInTcgCatalog.isEdoProVariant(baseVariantId)
    val isCustomClone = BuiltInTcgCatalog.supports21DigitRegisters(baseVariantId)
    var strategyTag by remember(baseVariantId) {
        mutableStateOf(
            when (baseVariantId) {
                "EDOPRO_CUSTOM_FORK" -> "21-Digit Custom Expansion"
                "EDOPRO_BASE_MR5" -> "Master Rule 5"
                "FORGE_MTG_BASE" -> "MTG Legacy / Commander"
                "FORGE_CUSTOM_CLONE" -> "Card-Forge 21-Digit Custom"
                "HYBRID_FORGE_EDOPRO_MIX" -> "Mixed Forge + EDOPro Custom"
                else -> "Tempo"
            }
        )
    }
    var customCardName by remember { mutableStateOf("") }
    var customCardCost by remember(baseVariantId) { mutableFloatStateOf(if (isEdoPro) 8f else 3f) }
    var customAttack21 by remember(baseVariantId) {
        mutableStateOf(if (isCustomClone) "999999999999999999999" else if (isEdoPro) "3000" else "5")
    }
    var customDefense21 by remember(baseVariantId) {
        mutableStateOf(if (isCustomClone) "888888888888888888888" else if (isEdoPro) "2500" else "4")
    }
    var customVarInput21 by remember(baseVariantId) {
        mutableStateOf("999999999999999999999")
    }
    var customCodeString21 by remember(baseVariantId) {
        mutableStateOf(if (isCustomClone) BigInt21Engine.DEFAULT_CODE_STRING_21 else "89631139")
    }
    var customCodeVarExpr21 by remember(baseVariantId) {
        mutableStateOf(
            when (baseVariantId) {
                "HYBRID_FORGE_EDOPRO_MIX" -> BigInt21Engine.DEFAULT_HYBRID_MIX_VAR_EXPR_21
                "FORGE_CUSTOM_CLONE" -> BigInt21Engine.DEFAULT_FORGE_CUSTOM_VAR_EXPR_21
                "EDOPRO_CUSTOM_FORK" -> BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
                else -> "s.code_var_21 = \"89631139\""
            }
        )
    }
    var customCardAbility by remember(baseVariantId) {
        mutableStateOf(
            when (baseVariantId) {
                "HYBRID_FORGE_EDOPRO_MIX" ->
                    "[Hybrid Forge+EDOPro] Tap Mana or Synchro-Summon: Resolves MTG Stack SVar + 21-digit ocgcore ADD_21."
                "FORGE_CUSTOM_CLONE" ->
                    "[Card-Forge 21-Digit SVar] AB$ Mana | Produced$ W U B R G | SVar:X:Count$21DigitRegister.999999999999999999999"
                "FORGE_MTG_BASE" ->
                    "[Card-Forge MTG] Flying, Vigilance. Whenever this attacks, add {U}{R} and scry 2."
                "EDOPRO_CUSTOM_FORK" ->
                    "[c900001010000000000021.lua] 21-Digit CodeVars & Function: Computes ADD_21 / MUL_21 on 21-digit variable inputs."
                "EDOPRO_BASE_MR5" ->
                    "[ocgcore] Quick Effect: Target 1 card on the field; negate its effects."
                else ->
                    "Resonance: Triggers +2 power when connected to a peer client."
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forge Deck & Signature Card (Up to 21 Digits)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = deckName,
                    onValueChange = { deckName = it },
                    label = { Text("Deck Name (.ydk / .dck)") },
                    placeholder = { Text("e.g. Ignis-Forge 21-Digit Singularity") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deck_name_input")
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(BuiltInTcgCatalog.baseVariants.take(5), key = { it.baseVariantId }) { spec ->
                        FilterChip(
                            selected = baseVariantId == spec.baseVariantId,
                            onClick = { baseVariantId = spec.baseVariantId },
                            label = {
                                Text(
                                    when (spec.baseVariantId) {
                                        "EDOPRO_BASE_MR5" -> "EDOPro Base"
                                        "EDOPRO_CUSTOM_FORK" -> "EDOPro 21d"
                                        "FORGE_MTG_BASE" -> "Card-Forge MTG"
                                        "FORGE_CUSTOM_CLONE" -> "Forge 21d Clone"
                                        "HYBRID_FORGE_EDOPRO_MIX" -> "Mixed Forge+EDO"
                                        else -> spec.name.substringBefore(" ")
                                    }
                                )
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = customCardName,
                    onValueChange = { customCardName = it },
                    label = { Text("Signature Card Name") },
                    placeholder = { Text("e.g. Ignis Singularity Dragon") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_card_name_input")
                )
                OutlinedTextField(
                    value = customCodeString21,
                    onValueChange = { customCodeString21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Code String Passcode ID (${customCodeString21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_card_code_str21_input")
                )
                OutlinedTextField(
                    value = customCodeVarExpr21,
                    onValueChange = {
                        customCodeVarExpr21 = BigInt21Engine.filterCodeStringWith21DigitLimit(it)
                    },
                    label = { Text("Coding Variables for Code Strings (≤21 Digits)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_card_code_var_expr21_input")
                )
                OutlinedTextField(
                    value = customAttack21,
                    onValueChange = { customAttack21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Card ATK Input (${customAttack21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_card_atk21_input")
                )
                OutlinedTextField(
                    value = customDefense21,
                    onValueChange = { customDefense21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("Card DEF Input (${customDefense21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_card_def21_input")
                )
                OutlinedTextField(
                    value = customVarInput21,
                    onValueChange = { customVarInput21 = BigInt21Engine.filterDigitsInput(it) },
                    label = { Text("21-Digit Lua Variable Input (${customVarInput21.length}/21 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_card_var21_input")
                )
                OutlinedTextField(
                    value = customCardAbility,
                    onValueChange = { customCardAbility = it },
                    label = { Text("Card Ability / 21-Digit Lua Effect") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanAtk21 = BigInt21Engine.sanitizeTo21Digits(customAttack21, "3000")
                    val cleanDef21 = BigInt21Engine.sanitizeTo21Digits(customDefense21, "2500")
                    val cleanVar21 = BigInt21Engine.sanitizeTo21Digits(customVarInput21, "999999999999999999999")
                    val cleanCodeStr21 = BigInt21Engine.sanitizeTo21Digits(
                        customCodeString21,
                        BigInt21Engine.DEFAULT_CODE_STRING_21
                    )
                    val cleanCodeVarExpr21 = BigInt21Engine.sanitizeCodeVarExpr21(
                        customCodeVarExpr21,
                        BigInt21Engine.DEFAULT_CODE_VAR_EXPR_21
                    )
                    onConfirm(
                        deckName.ifBlank { "Ignis 21-Digit Deck (.ydk)" },
                        baseVariantId,
                        strategyTag,
                        customCardName.ifBlank { "Ignis Singularity Dragon" },
                        customCardCost.toLong(),
                        BigInt21Engine.toSafeFirestoreLong(cleanAtk21, minVal = 0L),
                        BigInt21Engine.toSafeFirestoreLong(cleanDef21, minVal = 0L),
                        customCardAbility,
                        cleanAtk21,
                        cleanDef21,
                        cleanVar21,
                        cleanCodeStr21,
                        cleanCodeVarExpr21
                    )
                },
                modifier = Modifier.testTag("confirm_forge_deck_button")
            ) {
                Text("Forge & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
