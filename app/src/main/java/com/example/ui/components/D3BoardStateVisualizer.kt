package com.example.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.BuiltInTcgCatalog
import com.example.data.CardTemplate
import com.example.data.ClientSystemUiProfile
import com.example.ui.theme.ArcaneViolet
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.RadiantGold
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Represents a single card layer inside a board zone's card stack (e.g., Top Monster,
 * Xyz Overlay Unit, Equip Spell, Graveyard Card, or Deck Card).
 */
data class StackedCardEntry(
    val cardId: String,
    val name: String,
    val passcode: String,
    val roleLabel: String, // e.g., "TOP UNIT", "OVERLAY UNIT #1", "EQUIP / LINK", "GY TOP", "DECK CARD"
    val attackDisplay: String,
    val defenseDisplay: String,
    val isFaceDown: Boolean = false
)

/**
 * Represents a positioned zone node on the D3 board coordinate system (`domainX, domainY` in `0f..100f`)
 * along with its ordered stack of cards (bottom-to-top) and battle stance.
 */
data class BoardCardStackNode(
    val zoneId: String,
    val zoneLabel: String,
    val owner: String, // "HOST", "OPPONENT", "SHARED"
    val zoneCategory: String, // "MAIN_UNIT", "SUPPORT_SPELL", "EXTRA_EMZ", "DECK_PILE", "GY_PILE", "CHAIN_STACK"
    val domainX: Float, // D3 linear scale domain [0..100]
    val domainY: Float, // D3 linear scale domain [0..100]
    val stance: String, // "ATK_UPRIGHT", "DEF_HORIZONTAL", "FACE_DOWN_SET", "STACK_PILE"
    val stackCards: List<StackedCardEntry>,
    val totalStackCount: Int = stackCards.size
) {
    val topCard: StackedCardEntry?
        get() = stackCards.lastOrNull()
}

/**
 * Represents a directed vector edge in the D3 force/scene graph (e.g., Attack Vector, Chain Link Arc, Co-Link).
 */
data class BoardVectorLink(
    val sourceZoneId: String,
    val targetZoneId: String,
    val linkType: String, // "ATTACK_VECTOR", "CHAIN_LINK", "CO_LINK"
    val label: String
)

enum class D3RenderMode(val label: String) {
    CANVAS_D3_GRAPH("D3 Canvas Force/Grid"),
    WEBVIEW_D3_SVG("D3.js SVG Renderer"),
    D3_SCENE_INSPECTOR("D3 Scene & Stack Data")
}

enum class D3LayoutTopology(val label: String) {
    TACTICAL_GRID("2D Tactical Grid"),
    FORCE_CLUSTER("D3 Force Radial"),
    ISOMETRIC_STACKS("Isometric Stack Depth")
}

/**
 * D3.js-based Board State Visualizer component representing live card positions (`d3.scaleLinear`
 * coordinates, upright ATK vs horizontal DEF stances) and multi-card stacks (Main Deck, Extra Deck,
 * Graveyard, Xyz/Overlay Material Stacks, and Chain Link Stacks) during live gameplay.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun D3BoardStateVisualizer(
    uiProfile: ClientSystemUiProfile,
    hostUnits: List<CardTemplate>,
    opponentUnits: List<CardTemplate>,
    handCards: List<CardTemplate>,
    turnNumber: Long,
    activePhase: String,
    onQuickDeployFromVisualizer: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accentColor = Color(uiProfile.accentHex)
    var renderMode by rememberSaveable { mutableStateOf(D3RenderMode.CANVAS_D3_GRAPH) }
    var layoutTopology by rememberSaveable { mutableStateOf(D3LayoutTopology.TACTICAL_GRID) }
    var selectedZoneId by rememberSaveable(uiProfile.baseVariantId) {
        mutableStateOf(uiProfile.fieldSlotCodes.firstOrNull() ?: "MMZ-1")
    }

    // Interactive per-zone stack overrides (extra overlay units, stance changes, chain links)
    val extraStackedCardsByZone = remember(uiProfile.baseVariantId) {
        mutableStateMapOf<String, List<StackedCardEntry>>()
    }
    val zoneStanceOverrides = remember(uiProfile.baseVariantId) {
        mutableStateMapOf<String, String>()
    }
    var simulatedGyCount by rememberSaveable(uiProfile.baseVariantId) { mutableIntStateOf(3) }
    var simulatedDeckCount by rememberSaveable(uiProfile.baseVariantId) { mutableIntStateOf(35) }
    var activeChainStackCount by rememberSaveable(uiProfile.baseVariantId) { mutableIntStateOf(1) }

    val catalogCards = remember(uiProfile.baseVariantId) {
        BuiltInTcgCatalog.cardsForVariant(uiProfile.baseVariantId)
    }

    // Build the D3 scene graph nodes (card positions + multi-card stacks) and links
    val (nodes, links) = remember(
        uiProfile.baseVariantId,
        hostUnits,
        opponentUnits,
        handCards,
        layoutTopology,
        extraStackedCardsByZone.toMap(),
        zoneStanceOverrides.toMap(),
        simulatedGyCount,
        simulatedDeckCount,
        activeChainStackCount
    ) {
        buildD3BoardScene(
            uiProfile = uiProfile,
            hostUnits = hostUnits,
            opponentUnits = opponentUnits,
            catalogCards = catalogCards,
            layoutTopology = layoutTopology,
            extraStacks = extraStackedCardsByZone,
            stanceOverrides = zoneStanceOverrides,
            gyCount = simulatedGyCount,
            deckCount = simulatedDeckCount,
            chainCount = activeChainStackCount
        )
    }

    val selectedNode = nodes.firstOrNull { it.zoneId == selectedZoneId } ?: nodes.firstOrNull()
    val totalCardsOnBoard = nodes.sumOf { it.totalStackCount }
    val occupiedUnitPositions = nodes.count {
        (it.zoneCategory == "MAIN_UNIT" || it.zoneCategory == "EXTRA_EMZ") && it.stackCards.isNotEmpty()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("d3_board_state_visualizer_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Title + Live Telemetry Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.16f))
                            .border(1.dp, accentColor, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "D3 Board State Visualizer",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "D3.JS BOARD STATE & CARD STACK VISUALIZER",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiProfile.systemTitle} • Live Positions & Stacks",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = EmeraldReady.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        )
                        .border(1.dp, EmeraldReady, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$occupiedUnitPositions Units • $totalCardsOnBoard Stacked",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldReady,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Mode & Layout Topology Switcher Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                D3RenderMode.entries.forEach { mode ->
                    FilterChip(
                        selected = renderMode == mode,
                        onClick = { renderMode = mode },
                        label = {
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (mode) {
                                    D3RenderMode.CANVAS_D3_GRAPH -> Icons.Default.Hub
                                    D3RenderMode.WEBVIEW_D3_SVG -> Icons.Default.Visibility
                                    D3RenderMode.D3_SCENE_INSPECTOR -> Icons.Default.Code
                                },
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.testTag("d3_mode_chip_${mode.name.lowercase()}")
                    )
                }

                D3LayoutTopology.entries.forEach { topology ->
                    FilterChip(
                        selected = layoutTopology == topology,
                        onClick = { layoutTopology = topology },
                        label = {
                            Text(
                                text = topology.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("d3_topology_chip_${topology.name.lowercase()}")
                    )
                }
            }

            // Main Visualization Viewport
            when (renderMode) {
                D3RenderMode.CANVAS_D3_GRAPH -> {
                    D3ComposeCanvasBoardViewport(
                        nodes = nodes,
                        links = links,
                        selectedZoneId = selectedNode?.zoneId ?: "",
                        accentColor = accentColor,
                        layoutTopology = layoutTopology,
                        turnNumber = turnNumber,
                        activePhase = activePhase,
                        onSelectZone = { clickedZoneId ->
                            selectedZoneId = clickedZoneId
                        }
                    )
                }

                D3RenderMode.WEBVIEW_D3_SVG -> {
                    D3WebViewSvgBoardViewport(
                        uiProfile = uiProfile,
                        nodes = nodes,
                        links = links,
                        selectedZoneId = selectedNode?.zoneId ?: "",
                        turnNumber = turnNumber,
                        activePhase = activePhase,
                        onSelectZoneFromJs = { clickedZoneId ->
                            selectedZoneId = clickedZoneId
                        }
                    )
                }

                D3RenderMode.D3_SCENE_INSPECTOR -> {
                    D3SceneGraphAndStackCodeInspector(
                        uiProfile = uiProfile,
                        nodes = nodes,
                        links = links,
                        accentColor = accentColor
                    )
                }
            }

            // Quick Zone & Stack Selector Pills (Accessible & Testable)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Select Board Position or Card Stack to Inspect / Modify:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    nodes.forEach { node ->
                        val isSelected = node.zoneId == selectedNode?.zoneId
                        val chipAccent = when {
                            node.owner == "OPPONENT" -> CrimsonAlert
                            node.zoneCategory == "CHAIN_STACK" -> RadiantGold
                            node.totalStackCount > 1 -> RadiantGold
                            node.stackCards.isNotEmpty() -> EmeraldReady
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) chipAccent.copy(alpha = 0.22f)
                                    else ObsidianElevated
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) chipAccent else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedZoneId = node.zoneId }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("d3_stack_node_${node.zoneId}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(chipAccent, CircleShape)
                                )
                                Text(
                                    text = "${node.zoneLabel} [×${node.totalStackCount}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // Selected Position & Card Stack Inspector Panel
            selectedNode?.let { node ->
                SelectedCardStackInspector(
                    node = node,
                    uiProfile = uiProfile,
                    accentColor = accentColor,
                    onOverlayCardToStack = {
                        val sample = catalogCards.random()
                        val currentList = extraStackedCardsByZone[node.zoneId].orEmpty()
                        val newEntry = StackedCardEntry(
                            cardId = "${sample.id}_stack_${currentList.size + 1}",
                            name = sample.name,
                            passcode = sample.passcode,
                            roleLabel = if (node.zoneCategory == "CHAIN_STACK") {
                                "CHAIN LINK #${node.totalStackCount + 1}"
                            } else {
                                "OVERLAY / EQUIP #${currentList.size + 1}"
                            },
                            attackDisplay = sample.attack21,
                            defenseDisplay = sample.defense21,
                            isFaceDown = false
                        )
                        extraStackedCardsByZone[node.zoneId] = (currentList + newEntry).takeLast(6)
                        if (node.zoneCategory == "CHAIN_STACK") {
                            activeChainStackCount = (activeChainStackCount + 1).coerceAtMost(6)
                        }
                    },
                    onRotatePositionStance = {
                        val nextStance = when (node.stance) {
                            "ATK_UPRIGHT" -> "DEF_HORIZONTAL"
                            "DEF_HORIZONTAL" -> "FACE_DOWN_SET"
                            else -> "ATK_UPRIGHT"
                        }
                        zoneStanceOverrides[node.zoneId] = nextStance
                    },
                    onPopTopToGraveyard = {
                        val currentExtras = extraStackedCardsByZone[node.zoneId].orEmpty()
                        if (currentExtras.isNotEmpty()) {
                            extraStackedCardsByZone[node.zoneId] = currentExtras.dropLast(1)
                            simulatedGyCount = (simulatedGyCount + 1).coerceAtMost(60)
                        } else if (node.zoneId == "HOST_DECK" && simulatedDeckCount > 0) {
                            simulatedDeckCount = (simulatedDeckCount - 1).coerceAtLeast(0)
                            onQuickDeployFromVisualizer?.invoke()
                        } else {
                            simulatedGyCount = (simulatedGyCount + 1).coerceAtMost(60)
                        }
                    }
                )
            }
        }
    }
}

/**
 * High-precision Jetpack Compose Canvas D3 Board Viewport.
 * Uses `DrawScope.size.width` and `DrawScope.size.height` exclusively for responsive scaling,
 * renders D3 coordinate grids, isometric multi-card stacks, orientation stances, and vector links.
 */
@Composable
private fun D3ComposeCanvasBoardViewport(
    nodes: List<BoardCardStackNode>,
    links: List<BoardVectorLink>,
    selectedZoneId: String,
    accentColor: Color,
    layoutTopology: D3LayoutTopology,
    turnNumber: Long,
    activePhase: String,
    onSelectZone: (String) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianBg)
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .pointerInput(nodes) {
                detectTapGestures { tapOffset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val padX = w * 0.08f
                    val padY = h * 0.10f
                    val usableW = (w - padX * 2f).coerceAtLeast(1f)
                    val usableH = (h - padY * 2f).coerceAtLeast(1f)

                    val nearest = nodes.minByOrNull { node ->
                        val nx = padX + (node.domainX / 100f) * usableW
                        val ny = padY + (node.domainY / 100f) * usableH
                        hypot(tapOffset.x - nx, tapOffset.y - ny)
                    }
                    if (nearest != null) {
                        onSelectZone(nearest.zoneId)
                    }
                }
            }
            .testTag("d3_board_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height
            val padX = canvasW * 0.08f
            val padY = canvasH * 0.10f
            val usableW = (canvasW - padX * 2f).coerceAtLeast(1f)
            val usableH = (canvasH - padY * 2f).coerceAtLeast(1f)

            fun d3ScaleX(domainX: Float): Float = padX + (domainX / 100f) * usableW
            fun d3ScaleY(domainY: Float): Float = padY + (domainY / 100f) * usableH

            // 1. Draw D3 Coordinate Grid & Field Divider
            val gridSteps = 10
            for (i in 0..gridSteps) {
                val gx = padX + (i.toFloat() / gridSteps) * usableW
                val gy = padY + (i.toFloat() / gridSteps) * usableH
                drawLine(
                    color = Color(0xFF1E2942).copy(alpha = 0.65f),
                    start = Offset(gx, padY * 0.5f),
                    end = Offset(gx, canvasH - padY * 0.5f),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color(0xFF1E2942).copy(alpha = 0.65f),
                    start = Offset(padX * 0.5f, gy),
                    end = Offset(canvasW - padX * 0.5f, gy),
                    strokeWidth = 1f
                )
            }

            // Center tactical horizon line (Y = 50 in D3 domain)
            val midY = d3ScaleY(50f)
            drawLine(
                color = accentColor.copy(alpha = 0.45f),
                start = Offset(padX * 0.4f, midY),
                end = Offset(canvasW - padX * 0.4f, midY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )

            // 2. Draw Directed D3 Force / Tactical Links (Attack Vectors, Chain Links, Co-Links)
            val nodeMap = nodes.associateBy { it.zoneId }
            links.forEach { link ->
                val src = nodeMap[link.sourceZoneId]
                val dst = nodeMap[link.targetZoneId]
                if (src != null && dst != null) {
                    val sx = d3ScaleX(src.domainX)
                    val sy = d3ScaleY(src.domainY)
                    val tx = d3ScaleX(dst.domainX)
                    val ty = d3ScaleY(dst.domainY)

                    val linkColor = when (link.linkType) {
                        "ATTACK_VECTOR" -> CrimsonAlert
                        "CHAIN_LINK" -> RadiantGold
                        else -> ElectricCyan
                    }

                    val controlX = (sx + tx) / 2f + (if (sy > ty) 22f else -22f)
                    val controlY = (sy + ty) / 2f
                    val path = Path().apply {
                        moveTo(sx, sy)
                        quadraticTo(controlX, controlY, tx, ty)
                    }
                    drawPath(
                        path = path,
                        color = linkColor.copy(alpha = 0.8f),
                        style = Stroke(
                            width = 3f,
                            pathEffect = if (link.linkType == "CHAIN_LINK") {
                                PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            } else {
                                null
                            }
                        )
                    )
                    // Target arrowhead dot
                    drawCircle(
                        color = linkColor,
                        radius = 5f,
                        center = Offset(tx, ty)
                    )
                }
            }

            // 3. Draw Each Zone Node & Multi-Card Stack Layers
            val baseSlotW = (usableW / 7.2f).coerceIn(38f, 76f)
            val baseSlotH = (usableH / 5.4f).coerceIn(32f, 58f)

            nodes.forEach { node ->
                val cx = d3ScaleX(node.domainX)
                val cy = d3ScaleY(node.domainY)
                val isSelected = node.zoneId == selectedZoneId

                drawCardStackNode(
                    node = node,
                    cx = cx,
                    cy = cy,
                    baseSlotW = baseSlotW,
                    baseSlotH = baseSlotH,
                    isSelected = isSelected,
                    accentColor = accentColor,
                    layoutTopology = layoutTopology,
                    textMeasurer = textMeasurer
                )
            }
        }

        // Top-Left & Top-Right D3 Viewport Overlay Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "d3.scaleLinear([0,100]) • Turn $turnNumber ($activePhase)",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontFamily = FontFamily.Monospace
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = EmeraldReady, label = "Host")
                LegendDot(color = CrimsonAlert, label = "Peer")
                LegendDot(color = RadiantGold, label = "Stack/Chain")
            }
        }
    }
}

private fun DrawScope.drawCardStackNode(
    node: BoardCardStackNode,
    cx: Float,
    cy: Float,
    baseSlotW: Float,
    baseSlotH: Float,
    isSelected: Boolean,
    accentColor: Color,
    layoutTopology: D3LayoutTopology,
    textMeasurer: TextMeasurer
) {
    val isHorizontalDef = node.stance == "DEF_HORIZONTAL"
    val slotW = if (isHorizontalDef) baseSlotH * 1.15f else baseSlotW
    val slotH = if (isHorizontalDef) baseSlotW * 0.78f else baseSlotH

    val nodeColor = when {
        node.zoneCategory == "CHAIN_STACK" -> RadiantGold
        node.owner == "OPPONENT" -> CrimsonAlert
        node.owner == "SHARED" -> ArcaneViolet
        else -> accentColor
    }

    // Selected halo ring
    if (isSelected) {
        drawRoundRect(
            color = RadiantGold.copy(alpha = 0.28f),
            topLeft = Offset(cx - slotW / 2f - 6f, cy - slotH / 2f - 6f),
            size = Size(slotW + 12f, slotH + 12f),
            cornerRadius = CornerRadius(10f, 10f)
        )
    }

    // Base zone footprint
    drawRoundRect(
        color = ObsidianCard.copy(alpha = 0.9f),
        topLeft = Offset(cx - slotW / 2f, cy - slotH / 2f),
        size = Size(slotW, slotH),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = if (isSelected) RadiantGold else nodeColor.copy(alpha = 0.55f),
        topLeft = Offset(cx - slotW / 2f, cy - slotH / 2f),
        size = Size(slotW, slotH),
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(width = if (isSelected) 2.5f else 1.2f)
    )

    // Render 3D / Isometric Stacked Card Layers (up to 5 visible physical stack layers)
    val visibleStackLayers = node.totalStackCount.coerceIn(0, 5)
    val layerStepX = if (layoutTopology == D3LayoutTopology.ISOMETRIC_STACKS) 4.2f else 2.6f
    val layerStepY = if (layoutTopology == D3LayoutTopology.ISOMETRIC_STACKS) -4.2f else -2.6f

    for (layerIdx in 0 until visibleStackLayers) {
        val offsetX = layerIdx * layerStepX
        val offsetY = layerIdx * layerStepY
        val cardW = slotW * 0.84f
        val cardH = slotH * 0.78f
        val topLeft = Offset(
            x = cx - cardW / 2f + offsetX,
            y = cy - cardH / 2f + offsetY
        )

        val isTopLayer = layerIdx == visibleStackLayers - 1
        val fillBrush = if (node.stance == "FACE_DOWN_SET") {
            Brush.linearGradient(
                colors = listOf(Color(0xFF2B1D3D), Color(0xFF161024)),
                start = topLeft,
                end = Offset(topLeft.x + cardW, topLeft.y + cardH)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    nodeColor.copy(alpha = if (isTopLayer) 0.42f else 0.22f),
                    ObsidianElevated
                ),
                start = topLeft,
                end = Offset(topLeft.x + cardW, topLeft.y + cardH)
            )
        }

        drawRoundRect(
            brush = fillBrush,
            topLeft = topLeft,
            size = Size(cardW, cardH),
            cornerRadius = CornerRadius(5f, 5f)
        )
        drawRoundRect(
            color = if (isTopLayer && node.totalStackCount > 1) RadiantGold else nodeColor,
            topLeft = topLeft,
            size = Size(cardW, cardH),
            cornerRadius = CornerRadius(5f, 5f),
            style = Stroke(width = if (isTopLayer) 1.8f else 1f)
        )
    }

    // Zone code label at top of slot
    val labelResult = textMeasurer.measure(
        text = node.zoneLabel.take(8),
        style = TextStyle(
            color = Color.White,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    )
    drawText(
        textLayoutResult = labelResult,
        topLeft = Offset(
            x = (cx - labelResult.size.width / 2f).coerceIn(2f, (size.width - labelResult.size.width - 2f).coerceAtLeast(2f)),
            y = (cy - slotH / 2f + 2f).coerceIn(2f, (size.height - labelResult.size.height - 2f).coerceAtLeast(2f))
        )
    )

    // Stack count badge (e.g., ×35, ×3, ×1)
    if (node.totalStackCount > 0) {
        val stackBadgeText = if (node.totalStackCount > 1) {
            "×${node.totalStackCount}"
        } else {
            node.topCard?.attackDisplay?.take(5) ?: "×1"
        }
        val badgeMeasured = textMeasurer.measure(
            text = stackBadgeText,
            style = TextStyle(
                color = if (node.totalStackCount > 1) RadiantGold else EmeraldReady,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        )
        drawText(
            textLayoutResult = badgeMeasured,
            topLeft = Offset(
                x = (cx - badgeMeasured.size.width / 2f).coerceIn(2f, (size.width - badgeMeasured.size.width - 2f).coerceAtLeast(2f)),
                y = (cy + slotH / 2f - badgeMeasured.size.height - 2f).coerceIn(2f, (size.height - badgeMeasured.size.height - 2f).coerceAtLeast(2f))
            )
        )
    }
}

/**
 * Self-contained D3.js SVG Renderer inside an Android WebView.
 * Uses a built-in D3.js data-join SVG engine (`d3.select`, `d3.scaleLinear`, stack groups,
 * curved Bézier paths) so it renders offline immediately without external network dependency,
 * and bridges node clicks back to Kotlin via `@JavascriptInterface`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun D3WebViewSvgBoardViewport(
    uiProfile: ClientSystemUiProfile,
    nodes: List<BoardCardStackNode>,
    links: List<BoardVectorLink>,
    selectedZoneId: String,
    turnNumber: Long,
    activePhase: String,
    onSelectZoneFromJs: (String) -> Unit
) {
    val htmlContent = remember(uiProfile.baseVariantId, nodes, links, selectedZoneId, turnNumber, activePhase) {
        generateD3SvgHtmlDocument(
            uiProfile = uiProfile,
            nodes = nodes,
            links = links,
            selectedZoneId = selectedZoneId,
            turnNumber = turnNumber,
            activePhase = activePhase
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(uiProfile.accentHex).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("d3_board_webview_container")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.parseColor("#0A0E17"))
                    webViewClient = WebViewClient()
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onNodeSelected(zoneId: String) {
                                post { onSelectZoneFromJs(zoneId) }
                            }
                        },
                        "NexusD3Bridge"
                    )
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    "https://nexustcg.local/d3-board/",
                    htmlContent,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        )
    }
}

/**
 * Displays the live D3.js v7 data-join scene graph JSON and card stack coordinates table.
 */
@Composable
private fun D3SceneGraphAndStackCodeInspector(
    uiProfile: ClientSystemUiProfile,
    nodes: List<BoardCardStackNode>,
    links: List<BoardVectorLink>,
    accentColor: Color
) {
    val occupiedNodes = nodes.filter { it.totalStackCount > 0 }
    val d3CodeSnippet = remember(uiProfile.baseVariantId, occupiedNodes.size, links.size) {
        """
        // D3.js v7 Board State & Card Stack Data-Join (${uiProfile.boardLayoutType})
        const xScale = d3.scaleLinear().domain([0, 100]).range([36, width - 36]);
        const yScale = d3.scaleLinear().domain([0, 100]).range([28, height - 28]);

        // 1. Bind Card Stack Nodes (${nodes.size} zones, ${occupiedNodes.sumOf { it.totalStackCount }} stacked cards)
        const zoneGroups = svg.selectAll("g.card-stack-node")
          .data(boardState.nodes, d => d.zoneId)
          .join("g")
          .attr("class", "card-stack-node")
          .attr("transform", d => `translate(${'$'}{xScale(d.domainX)}, ${'$'}{yScale(d.domainY)})`);

        // 2. Render Isometric Card Stack Layers (d.stackCards)
        zoneGroups.selectAll("rect.stack-layer")
          .data(d => d.stackCards.map((c, idx) => ({ ...c, layer: idx, stance: d.stance })))
          .join("rect")
          .attr("x", d => -26 + d.layer * 3.5)
          .attr("y", d => -18 - d.layer * 3.5)
          .attr("width", d => d.stance === "DEF_HORIZONTAL" ? 56 : 48)
          .attr("height", d => d.stance === "DEF_HORIZONTAL" ? 34 : 40);
        """.trimIndent()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianBg)
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("d3_scene_inspector_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "D3.js v7 Scene Graph & Stack Coordinate Matrix",
            style = MaterialTheme.typography.labelMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = d3CodeSnippet,
            style = MaterialTheme.typography.labelSmall,
            color = EmeraldReady,
            fontFamily = FontFamily.Monospace
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        nodes.filter { it.totalStackCount > 0 }.take(6).forEach { node ->
            Text(
                text = "• [${node.zoneId}] pos=(${node.domainX.toInt()},${node.domainY.toInt()}) | stance=${node.stance} | stack=×${node.totalStackCount} | top=${node.topCard?.name ?: "Empty"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Interactive Inspector for the currently selected board position & its card stack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedCardStackInspector(
    node: BoardCardStackNode,
    uiProfile: ClientSystemUiProfile,
    accentColor: Color,
    onOverlayCardToStack: () -> Unit,
    onRotatePositionStance: () -> Unit,
    onPopTopToGraveyard: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("d3_selected_stack_inspector")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "POSITION: ${node.zoneLabel} (${node.owner}) • D3 Coord (${node.domainX.toInt()}, ${node.domainY.toInt()})",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Stack Depth: ×${node.totalStackCount} Card(s) • Stance: ${node.stance}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .background(RadiantGold.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                        .border(1.dp, RadiantGold, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "STACK ×${node.totalStackCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadiantGold,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Stack Layers Breakdown (Top-to-Bottom)
            if (node.stackCards.isEmpty()) {
                Text(
                    text = "Empty zone position. Tap '+ Stack / Overlay Card' below or deploy a card from your hand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    node.stackCards.asReversed().forEachIndexed { revIdx, entry ->
                        val layerNumber = node.stackCards.size - revIdx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianElevated, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Layer #$layerNumber [${entry.roleLabel}]: ${entry.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (revIdx == 0) EmeraldReady else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (revIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Code: c${entry.passcode} • ATK ${entry.attackDisplay} / DEF ${entry.defenseDisplay}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Stack & Position Manipulation Buttons
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onOverlayCardToStack,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("d3_stack_overlay_card_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Stack or Overlay Card",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Stack / Overlay Card", style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = onRotatePositionStance,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("d3_rotate_stance_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate Card Position Stance",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stance (${node.stance})", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onPopTopToGraveyard,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("d3_pop_stack_action_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Resolve or Send Top of Stack",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (node.zoneId == "HOST_DECK") "Draw from Deck Stack" else "Detach / Resolve Top",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Constructs the complete D3 board scene (Card Positions + Multi-Card Stacks + Vector Links)
 * for the active TCG Client's system (`uiProfile`).
 */
private fun buildD3BoardScene(
    uiProfile: ClientSystemUiProfile,
    hostUnits: List<CardTemplate>,
    opponentUnits: List<CardTemplate>,
    catalogCards: List<CardTemplate>,
    layoutTopology: D3LayoutTopology,
    extraStacks: Map<String, List<StackedCardEntry>>,
    stanceOverrides: Map<String, String>,
    gyCount: Int,
    deckCount: Int,
    chainCount: Int
): Pair<List<BoardCardStackNode>, List<BoardVectorLink>> {
    val nodes = mutableListOf<BoardCardStackNode>()
    val links = mutableListOf<BoardVectorLink>()

    val mainSlotCount = uiProfile.fieldSlotCodes.size.coerceAtLeast(1)

    // 1. Opponent Main Unit Positions (Y = 24 in Tactical Grid)
    uiProfile.fieldSlotCodes.forEachIndexed { idx, code ->
        val oppZoneId = "OPP_$code"
        val gridX = 16f + (idx.toFloat() / (mainSlotCount - 1).coerceAtLeast(1)) * 64f
        val (dx, dy) = applyTopologyTransform(gridX, 23f, idx, mainSlotCount, layoutTopology, isOpponent = true)
        val unit = opponentUnits.getOrNull(idx)
        val baseList = if (unit != null) {
            listOf(
                StackedCardEntry(
                    cardId = unit.id,
                    name = unit.name,
                    passcode = unit.passcode,
                    roleLabel = "PEER UNIT",
                    attackDisplay = unit.attack21,
                    defenseDisplay = unit.defense21
                )
            )
        } else {
            emptyList()
        }
        val combinedStack = baseList + extraStacks[oppZoneId].orEmpty()
        nodes.add(
            BoardCardStackNode(
                zoneId = oppZoneId,
                zoneLabel = "OPP-$code",
                owner = "OPPONENT",
                zoneCategory = "MAIN_UNIT",
                domainX = dx,
                domainY = dy,
                stance = stanceOverrides[oppZoneId] ?: if (idx % 2 == 1 && combinedStack.isNotEmpty()) "DEF_HORIZONTAL" else "ATK_UPRIGHT",
                stackCards = combinedStack,
                totalStackCount = combinedStack.size
            )
        )
    }

    // 2. Shared Center Extra Monster Zones & Live Chain Link Stack (Y = 48..52)
    val emzLeftCard = hostUnits.firstOrNull { it.cardType.contains("Link", ignoreCase = true) || it.cardType.contains("Synchro", ignoreCase = true) }
    val emzLeftStack = buildList {
        if (emzLeftCard != null) {
            add(
                StackedCardEntry(
                    cardId = emzLeftCard.id,
                    name = emzLeftCard.name,
                    passcode = emzLeftCard.passcode,
                    roleLabel = "EMZ LINK LEADER",
                    attackDisplay = emzLeftCard.attack21,
                    defenseDisplay = emzLeftCard.defense21
                )
            )
        }
        addAll(extraStacks["EMZ-L"].orEmpty())
    }
    nodes.add(
        BoardCardStackNode(
            zoneId = "EMZ-L",
            zoneLabel = "EMZ-L",
            owner = "SHARED",
            zoneCategory = "EXTRA_EMZ",
            domainX = 32f,
            domainY = 48f,
            stance = stanceOverrides["EMZ-L"] ?: "ATK_UPRIGHT",
            stackCards = emzLeftStack,
            totalStackCount = emzLeftStack.size
        )
    )

    val chainCards = buildList {
        repeat(chainCount) { cIdx ->
            val cCard = catalogCards.getOrElse(cIdx % catalogCards.size) { catalogCards.first() }
            add(
                StackedCardEntry(
                    cardId = "chain_${cIdx + 1}_${cCard.id}",
                    name = cCard.name,
                    passcode = cCard.passcode,
                    roleLabel = "CHAIN LINK #${cIdx + 1}",
                    attackDisplay = cCard.attack21,
                    defenseDisplay = cCard.defense21
                )
            )
        }
        addAll(extraStacks["CHAIN_STACK"].orEmpty())
    }
    nodes.add(
        BoardCardStackNode(
            zoneId = "CHAIN_STACK",
            zoneLabel = "CHAIN",
            owner = "SHARED",
            zoneCategory = "CHAIN_STACK",
            domainX = 52f,
            domainY = 48f,
            stance = stanceOverrides["CHAIN_STACK"] ?: "STACK_PILE",
            stackCards = chainCards,
            totalStackCount = chainCards.size
        )
    )

    // 3. Host Main Unit Positions (MMZ 1..5 / EXP 1..6 / SANCTUM / GRID / RIFT) (Y = 72)
    uiProfile.fieldSlotCodes.forEachIndexed { idx, slotCode ->
        val gridX = 16f + (idx.toFloat() / (mainSlotCount - 1).coerceAtLeast(1)) * 64f
        val (dx, dy) = applyTopologyTransform(gridX, 71f, idx, mainSlotCount, layoutTopology, isOpponent = false)
        val unit = hostUnits.getOrNull(idx)
        val baseStack = buildList {
            if (unit != null) {
                // If first slot has a unit, also include a sample Xyz/Overlay material under it so multi-card stacks are immediately visible
                if (idx == 0 && catalogCards.size > 1) {
                    val mat = catalogCards[1]
                    add(
                        StackedCardEntry(
                            cardId = "${mat.id}_overlay_mat",
                            name = mat.name,
                            passcode = mat.passcode,
                            roleLabel = "OVERLAY MATERIAL #1",
                            attackDisplay = mat.attack21,
                            defenseDisplay = mat.defense21
                        )
                    )
                }
                add(
                    StackedCardEntry(
                        cardId = unit.id,
                        name = unit.name,
                        passcode = unit.passcode,
                        roleLabel = "ACTIVE FIELD UNIT",
                        attackDisplay = unit.attack21,
                        defenseDisplay = unit.defense21
                    )
                )
            }
            addAll(extraStacks[slotCode].orEmpty())
        }
        nodes.add(
            BoardCardStackNode(
                zoneId = slotCode,
                zoneLabel = slotCode,
                owner = "HOST",
                zoneCategory = "MAIN_UNIT",
                domainX = dx,
                domainY = dy,
                stance = stanceOverrides[slotCode] ?: "ATK_UPRIGHT",
                stackCards = baseStack,
                totalStackCount = baseStack.size
            )
        )
    }

    // 4. Host Support / Spell-Trap / Mana / Reactor Positions (Y = 89)
    val supportCodes = uiProfile.spellOrSupportSlotCodes.take(3)
    supportCodes.forEachIndexed { idx, supCode ->
        val gridX = 20f + idx * 22f
        val sampleSupport = catalogCards.lastOrNull()
        val baseSupportStack = buildList {
            if (idx == 0 && sampleSupport != null) {
                add(
                    StackedCardEntry(
                        cardId = sampleSupport.id,
                        name = sampleSupport.name,
                        passcode = sampleSupport.passcode,
                        roleLabel = "SET SPELL/CONDUIT",
                        attackDisplay = sampleSupport.attack21,
                        defenseDisplay = sampleSupport.defense21,
                        isFaceDown = true
                    )
                )
            }
            addAll(extraStacks[supCode].orEmpty())
        }
        nodes.add(
            BoardCardStackNode(
                zoneId = supCode,
                zoneLabel = supCode,
                owner = "HOST",
                zoneCategory = "SUPPORT_SPELL",
                domainX = gridX,
                domainY = 89f,
                stance = stanceOverrides[supCode] ?: if (idx == 0) "FACE_DOWN_SET" else "ATK_UPRIGHT",
                stackCards = baseSupportStack,
                totalStackCount = baseSupportStack.size
            )
        )
    }

    // 5. Host Graveyard (GY) Stack & Main Deck Stack (Right Column X = 91)
    val gyCards = buildList {
        repeat(gyCount.coerceIn(1, 5)) { gIdx ->
            val c = catalogCards.getOrElse(gIdx % catalogCards.size) { catalogCards.first() }
            add(
                StackedCardEntry(
                    cardId = "gy_${gIdx}_${c.id}",
                    name = c.name,
                    passcode = c.passcode,
                    roleLabel = "GY CARD #${gIdx + 1}",
                    attackDisplay = c.attack21,
                    defenseDisplay = c.defense21
                )
            )
        }
        addAll(extraStacks["HOST_GY"].orEmpty())
    }
    nodes.add(
        BoardCardStackNode(
            zoneId = "HOST_GY",
            zoneLabel = "GY PILE",
            owner = "HOST",
            zoneCategory = "GY_PILE",
            domainX = 91f,
            domainY = 66f,
            stance = "STACK_PILE",
            stackCards = gyCards,
            totalStackCount = gyCount + extraStacks["HOST_GY"].orEmpty().size
        )
    )

    val topDeckPreview = catalogCards.take(3).mapIndexed { dIdx, c ->
        StackedCardEntry(
            cardId = "deck_${dIdx}_${c.id}",
            name = c.name,
            passcode = c.passcode,
            roleLabel = "DECK TOP -${3 - dIdx}",
            attackDisplay = c.attack21,
            defenseDisplay = c.defense21,
            isFaceDown = true
        )
    } + extraStacks["HOST_DECK"].orEmpty()

    nodes.add(
        BoardCardStackNode(
            zoneId = "HOST_DECK",
            zoneLabel = "DECK",
            owner = "HOST",
            zoneCategory = "DECK_PILE",
            domainX = 91f,
            domainY = 88f,
            stance = "STACK_PILE",
            stackCards = topDeckPreview,
            totalStackCount = deckCount + extraStacks["HOST_DECK"].orEmpty().size
        )
    )

    // 6. Directed Vector Links (Attack Targeting, Chain Resolution, Co-Link)
    val firstHostSlot = uiProfile.fieldSlotCodes.firstOrNull() ?: "MMZ-1"
    val firstOppSlot = "OPP_$firstHostSlot"
    if (hostUnits.isNotEmpty()) {
        links.add(
            BoardVectorLink(
                sourceZoneId = firstHostSlot,
                targetZoneId = firstOppSlot,
                linkType = "ATTACK_VECTOR",
                label = "ATK VECTOR"
            )
        )
    }
    val firstSupport = supportCodes.firstOrNull()
    if (firstSupport != null) {
        links.add(
            BoardVectorLink(
                sourceZoneId = firstSupport,
                targetZoneId = "CHAIN_STACK",
                linkType = "CHAIN_LINK",
                label = "CL1"
            )
        )
        links.add(
            BoardVectorLink(
                sourceZoneId = "CHAIN_STACK",
                targetZoneId = firstHostSlot,
                linkType = "CHAIN_LINK",
                label = "RESOLVE"
            )
        )
    }
    links.add(
        BoardVectorLink(
            sourceZoneId = "EMZ-L",
            targetZoneId = firstHostSlot,
            linkType = "CO_LINK",
            label = "LINK"
        )
    )

    return nodes to links
}

private fun applyTopologyTransform(
    gridX: Float,
    gridY: Float,
    index: Int,
    totalSlots: Int,
    topology: D3LayoutTopology,
    isOpponent: Boolean
): Pair<Float, Float> {
    return when (topology) {
        D3LayoutTopology.TACTICAL_GRID -> gridX to gridY
        D3LayoutTopology.ISOMETRIC_STACKS -> {
            val isoShift = if (isOpponent) -4f else 4f
            (gridX + isoShift).coerceIn(10f, 84f) to gridY
        }
        D3LayoutTopology.FORCE_CLUSTER -> {
            val angle = (index.toFloat() / totalSlots.coerceAtLeast(1)) * Math.PI +
                (if (isOpponent) Math.PI else 0.0)
            val rx = 46f + (cos(angle) * 28.0).toFloat()
            val ry = if (isOpponent) {
                26f + (sin(angle) * 10.0).toFloat()
            } else {
                72f + (sin(angle) * 10.0).toFloat()
            }
            rx.coerceIn(12f, 82f) to ry.coerceIn(14f, 88f)
        }
    }
}

/**
 * Generates a self-contained HTML5 + D3.js SVG document that renders card positions,
 * isometric card stacks, and vector links inside the WebView mode.
 */
private fun generateD3SvgHtmlDocument(
    uiProfile: ClientSystemUiProfile,
    nodes: List<BoardCardStackNode>,
    links: List<BoardVectorLink>,
    selectedZoneId: String,
    turnNumber: Long,
    activePhase: String
): String {
    val accentHexStr = String.format("#%06X", (0xFFFFFFL and uiProfile.accentHex))
    val nodesJson = nodes.joinToString(",") { n ->
        val safeLabel = n.zoneLabel.replace("\"", "")
        val safeTop = (n.topCard?.name ?: "Empty").replace("\"", "")
        """{"zoneId":"${n.zoneId}","label":"$safeLabel","owner":"${n.owner}","x":${n.domainX},"y":${n.domainY},"stance":"${n.stance}","stackCount":${n.totalStackCount},"topCard":"$safeTop"}"""
    }
    val linksJson = links.joinToString(",") { l ->
        """{"source":"${l.sourceZoneId}","target":"${l.targetZoneId}","type":"${l.linkType}","label":"${l.label}"}"""
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8"/>
          <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
          <style>
            body, html { margin: 0; padding: 0; width: 100%; height: 100%; background: #0A0E17; color: #F3F6FC; font-family: monospace; overflow: hidden; }
            #board-svg { width: 100%; height: 100%; display: block; }
            .node-group { cursor: pointer; }
            .zone-label { font-size: 9px; font-weight: bold; fill: #F3F6FC; text-anchor: middle; pointer-events: none; }
            .stack-badge { font-size: 9px; font-weight: bold; fill: #FFB300; text-anchor: middle; pointer-events: none; }
          </style>
        </head>
        <body>
          <svg id="board-svg" viewBox="0 0 500 320" preserveAspectRatio="xMidYMid meet"></svg>
          <script>
            // Built-in D3.js-compatible SVG Data-Join Micro-Engine
            const scene = {
              selectedZoneId: "$selectedZoneId",
              accent: "$accentHexStr",
              nodes: [$nodesJson],
              links: [$linksJson]
            };
            const svg = document.getElementById("board-svg");
            const scaleX = (v) => 36 + (v / 100.0) * 428;
            const scaleY = (v) => 26 + (v / 100.0) * 268;

            // 1. Grid & Horizon
            for (let i = 0; i <= 10; i++) {
              const gx = scaleX(i * 10);
              const gy = scaleY(i * 10);
              svg.insertAdjacentHTML("beforeend", `<line x1="${'$'}{gx}" y1="14" x2="${'$'}{gx}" y2="306" stroke="#1E2942" stroke-width="1"/>`);
              svg.insertAdjacentHTML("beforeend", `<line x1="18" y1="${'$'}{gy}" x2="482" y2="${'$'}{gy}" stroke="#1E2942" stroke-width="1"/>`);
            }
            const midY = scaleY(50);
            svg.insertAdjacentHTML("beforeend", `<line x1="18" y1="${'$'}{midY}" x2="482" y2="${'$'}{midY}" stroke="${'$'}{scene.accent}" stroke-width="1.5" stroke-dasharray="8,5"/>`);

            // 2. Links
            const nodeMap = {};
            scene.nodes.forEach(n => { nodeMap[n.zoneId] = n; });
            scene.links.forEach(l => {
              const s = nodeMap[l.source];
              const t = nodeMap[l.target];
              if (!s || !t) return;
              const sx = scaleX(s.x), sy = scaleY(s.y);
              const tx = scaleX(t.x), ty = scaleY(t.y);
              const cx = (sx + tx) / 2 + 18, cy = (sy + ty) / 2;
              const color = l.type === "ATTACK_VECTOR" ? "#FF5252" : (l.type === "CHAIN_LINK" ? "#FFB300" : "#00E5FF");
              svg.insertAdjacentHTML("beforeend", `<path d="M ${'$'}{sx} ${'$'}{sy} Q ${'$'}{cx} ${'$'}{cy} ${'$'}{tx} ${'$'}{ty}" fill="none" stroke="${'$'}{color}" stroke-width="2.2"/>`);
              svg.insertAdjacentHTML("beforeend", `<circle cx="${'$'}{tx}" cy="${'$'}{ty}" r="4" fill="${'$'}{color}"/>`);
            });

            // 3. Card Positions & Multi-Card Stacks
            scene.nodes.forEach(n => {
              const cx = scaleX(n.x), cy = scaleY(n.y);
              const isHoriz = n.stance === "DEF_HORIZONTAL";
              const w = isHoriz ? 56 : 46;
              const h = isHoriz ? 36 : 44;
              const isSel = n.zoneId === scene.selectedZoneId;
              const stroke = isSel ? "#FFB300" : (n.owner === "OPPONENT" ? "#FF5252" : scene.accent);
              const layers = Math.min(Math.max(n.stackCount, 0), 5);
              let layersSvg = "";
              for (let k = 0; k < layers; k++) {
                const lx = -w * 0.4 + k * 3.2;
                const ly = -h * 0.36 - k * 3.2;
                layersSvg += `<rect x="${'$'}{lx}" y="${'$'}{ly}" width="${'$'}{w * 0.8}" height="${'$'}{h * 0.72}" rx="4" fill="#232E47" stroke="${'$'}{stroke}" stroke-width="1.3"/>`;
              }
              const g = `<g class="node-group" transform="translate(${'$'}{cx},${'$'}{cy})" onclick="if(window.NexusD3Bridge){window.NexusD3Bridge.onNodeSelected('${'$'}{n.zoneId}');}">
                <rect x="${'$'}{-w/2}" y="${'$'}{-h/2}" width="${'$'}{w}" height="${'$'}{h}" rx="6" fill="#1A2235" stroke="${'$'}{stroke}" stroke-width="${'$'}{isSel ? 2.5 : 1.2}"/>
                ${'$'}{layersSvg}
                <text class="zone-label" y="${'$'}{-h/2 + 10}">${'$'}{n.label}</text>
                <text class="stack-badge" y="${'$'}{h/2 - 4}">×${'$'}{n.stackCount}</text>
              </g>`;
              svg.insertAdjacentHTML("beforeend", g);
            });
          </script>
        </body>
        </html>
    """.trimIndent()
}
