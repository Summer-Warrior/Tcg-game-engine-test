package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BuiltInTcgCatalog
import com.example.data.RealtimeGameSyncState
import com.example.data.RealtimeSyncConnectionStatus
import com.example.data.SerializedZonePlacement
import com.example.data.SyncClientSeat
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.RadiantGold

/**
 * Interactive 2-Client Real-Time Firestore Game State Sync Console.
 *
 * Displays live connection status, Match Room ID copy/join controls, Client 1 (Host) vs Client 2 (Peer)
 * seat selector, atomic transactional Life Point controls for both players, and interactive synced
 * board zone controls (deploy, rotate `ATK`/`DEF`, remove, reset) powered by [com.example.data.FirestoreGameStateManager].
 */
@Composable
fun RealtimeGameStateSyncPanel(
    syncState: RealtimeGameSyncState,
    onSelectSeat: (SyncClientSeat) -> Unit,
    onJoinMatchById: (String) -> Unit,
    onAdjustLifePoints: (SyncClientSeat, Long) -> Unit,
    onSetLifePointsExact: (SyncClientSeat, Long) -> Unit,
    onDeploySyncedCardForActiveSeat: () -> Unit,
    onToggleZoneOrientation: (String) -> Unit,
    onRemoveZonePlacement: (String) -> Unit,
    onResetSyncedState: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var showJoinRoomDialog by remember { mutableStateOf(false) }
    var joinRoomInput by remember { mutableStateOf(syncState.matchId) }
    var editingLifeSeat by remember { mutableStateOf<SyncClientSeat?>(null) }
    var exactLifeInput by remember { mutableStateOf("") }
    var copiedBadgeVisible by remember { mutableStateOf(false) }
    var showSyncHistory by rememberSaveable { mutableStateOf(false) }

    val baseSpec = remember(syncState.baseVariantId) {
        BuiltInTcgCatalog.findBaseSpec(syncState.baseVariantId)
    }
    val uiProfile = remember(syncState.baseVariantId) {
        BuiltInTcgCatalog.systemUiProfileFor(syncState.baseVariantId)
    }

    // Variant-aware life delta steps (EDOPro 8000 LP vs Forge/MTG 20 Life vs 30-40 Life)
    val deltaSteps = remember(baseSpec.startingLife) {
        when {
            baseSpec.startingLife >= 1000L -> listOf(-1000L, -500L, 500L, 1000L)
            baseSpec.startingLife >= 30L -> listOf(-5L, -1L, 1L, 5L)
            else -> listOf(-5L, -1L, 1L, 5L)
        }
    }

    val statusColor = when (syncState.connectionStatus) {
        RealtimeSyncConnectionStatus.SYNCED -> EmeraldReady
        RealtimeSyncConnectionStatus.SYNCING_WRITE,
        RealtimeSyncConnectionStatus.CONNECTING -> RadiantGold
        RealtimeSyncConnectionStatus.FINISHED -> MaterialTheme.colorScheme.secondary
        RealtimeSyncConnectionStatus.ERROR -> CrimsonAlert
        RealtimeSyncConnectionStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when (syncState.connectionStatus) {
        RealtimeSyncConnectionStatus.SYNCED -> Icons.Default.CloudDone
        RealtimeSyncConnectionStatus.SYNCING_WRITE,
        RealtimeSyncConnectionStatus.CONNECTING -> Icons.Default.CloudSync
        else -> Icons.Default.CloudOff
    }

    val canMutate = syncState.matchId.isNotBlank() && !syncState.isMatchFinished

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = statusColor.copy(alpha = 0.65f),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("realtime_firestore_sync_panel"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 1. Header: Live Firestore Sync Badge + Room ID Copy / Join Controls
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
                            .size(10.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.realtime_sync_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = syncState.connectionStatus.label,
                                tint = statusColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val telemetryText = buildString {
                                append(syncState.connectionStatus.label)
                                if (syncState.revisionNumber > 0) {
                                    append(" • Rev #${syncState.revisionNumber}")
                                }
                                if (syncState.lastSyncLatencyMs > 0L) {
                                    append(" • ${syncState.lastSyncLatencyMs}ms")
                                }
                            }
                            Text(
                                text = telemetryText,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = statusColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        joinRoomInput = syncState.matchId
                        showJoinRoomDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sync_join_room_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = stringResource(id = R.string.realtime_sync_join_room),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(id = R.string.realtime_sync_join_room),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Match Room ID Bar (Copyable so Client 2 can connect immediately)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SYNC ROOM ID (${syncState.connectedClients.size.coerceAtLeast(1)} client(s) • ${syncState.status})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = syncState.matchId.ifBlank { "No active Firestore room — tap 'New Arena' or 'Join Room'" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("sync_active_room_id_text")
                    )
                }
                if (syncState.matchId.isNotBlank()) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(syncState.matchId))
                            copiedBadgeVisible = true
                        },
                        modifier = Modifier.testTag("sync_copy_room_id_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Match Room ID",
                            tint = if (copiedBadgeVisible) EmeraldReady else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (syncState.errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = syncState.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonAlert,
                    modifier = Modifier.testTag("sync_error_message")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Local Client Seat Selector (Client 1 Host vs Client 2 Peer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SyncClientSeat.entries.forEach { seat ->
                    val isSelected = syncState.localSeat == seat
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectSeat(seat) },
                        label = {
                            Text(
                                text = seat.displayLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("sync_seat_chip_${seat.code}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Synchronized Life Points Cards (Client 1 Host & Client 2 Peer)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SyncedLifeCounterCard(
                    seat = SyncClientSeat.CLIENT_1_HOST,
                    playerLabel = syncState.hostName.ifBlank { "Client 1 (Host)" },
                    lifeValue = syncState.hostLife,
                    startingLife = baseSpec.startingLife,
                    lifeUnitLabel = uiProfile.lifeLabel,
                    isLocalSeat = syncState.localSeat == SyncClientSeat.CLIENT_1_HOST,
                    canMutate = canMutate,
                    deltaSteps = deltaSteps,
                    onDelta = { delta -> onAdjustLifePoints(SyncClientSeat.CLIENT_1_HOST, delta) },
                    onEditExact = {
                        editingLifeSeat = SyncClientSeat.CLIENT_1_HOST
                        exactLifeInput = syncState.hostLife.toString()
                    },
                    modifier = Modifier.weight(1f)
                )

                SyncedLifeCounterCard(
                    seat = SyncClientSeat.CLIENT_2_PEER,
                    playerLabel = syncState.connectedClients.getOrNull(1)
                        ?.substringAfter(": ")
                        ?.ifBlank { "Client 2 (Peer)" }
                        ?: "Client 2 (Peer)",
                    lifeValue = syncState.opponentLife,
                    startingLife = baseSpec.startingLife,
                    lifeUnitLabel = uiProfile.lifeLabel,
                    isLocalSeat = syncState.localSeat == SyncClientSeat.CLIENT_2_PEER,
                    canMutate = canMutate,
                    deltaSteps = deltaSteps,
                    onDelta = { delta -> onAdjustLifePoints(SyncClientSeat.CLIENT_2_PEER, delta) },
                    onEditExact = {
                        editingLifeSeat = SyncClientSeat.CLIENT_2_PEER
                        exactLifeInput = syncState.opponentLife.toString()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (syncState.isMatchFinished && syncState.winnerName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RadiantGold.copy(alpha = 0.16f))
                        .border(1.dp, RadiantGold.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "MATCH CONCLUDED • SYNCED WINNER: ${syncState.winnerName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = RadiantGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // 4. Synchronized Board State Strip (P1/P2 Zone Chips + Atomic Deploy / Reset)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SYNCED BOARD STATE (P1: ${syncState.hostBoardCount}/12 • P2: ${syncState.opponentBoardCount}/12)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap any synced card to toggle ATK/DEF across both clients",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onDeploySyncedCardForActiveSeat,
                        enabled = canMutate,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("sync_deploy_card_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "Deploy Synced Card",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (syncState.localSeat == SyncClientSeat.CLIENT_1_HOST) "Deploy P1" else "Deploy P2",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    OutlinedButton(
                        onClick = onResetSyncedState,
                        enabled = canMutate,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("sync_reset_state_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Synced Life and Board",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val sortedZones = remember(syncState.zonePlacements) {
                syncState.zonePlacements.values.sortedBy { it.zoneId }
            }

            if (sortedZones.isEmpty()) {
                Text(
                    text = "No cards currently synced on battlefield zones. Tap 'Deploy' or drag cards on the Canvas Board below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedZones.forEach { placement ->
                        SyncedZoneChip(
                            placement = placement,
                            canMutate = canMutate,
                            onToggleOrientation = { onToggleZoneOrientation(placement.zoneId) },
                            onRemove = { onRemoveZonePlacement(placement.zoneId) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. Live 2-Client Sync Event Ticker & Collapsible Transaction Log
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .clickable { showSyncHistory = !showSyncHistory }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("sync_event_ticker_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "[${syncState.lastEventType}] ${syncState.lastBroadcastClientName.ifBlank { "Firestore Engine" }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = syncState.lastEventSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (showSyncHistory) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle Sync Transaction History",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = showSyncHistory && syncState.recentSyncEvents.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    syncState.recentSyncEvents.take(5).forEach { logLine ->
                        Text(
                            text = "• $logLine",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // Join Room Dialog
    if (showJoinRoomDialog) {
        AlertDialog(
            onDismissRequest = { showJoinRoomDialog = false },
            title = { Text(stringResource(id = R.string.realtime_sync_join_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste an active Firestore Match Room ID from Client 1 to synchronize Life Points and Board State in real time.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = joinRoomInput,
                        onValueChange = { joinRoomInput = it },
                        label = { Text("Firestore Match Room ID") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sync_join_room_id_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = joinRoomInput.trim()
                        if (trimmed.isNotBlank()) {
                            onJoinMatchById(trimmed)
                            showJoinRoomDialog = false
                        }
                    },
                    modifier = Modifier.testTag("sync_confirm_join_room_button")
                ) {
                    Text("Attach & Sync")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinRoomDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Exact Life Points Setter Dialog
    val seatBeingEdited = editingLifeSeat
    if (seatBeingEdited != null) {
        AlertDialog(
            onDismissRequest = { editingLifeSeat = null },
            title = {
                Text("Set ${seatBeingEdited.shortLabel} ${uiProfile.lifeLabel}")
            },
            text = {
                OutlinedTextField(
                    value = exactLifeInput,
                    onValueChange = { input -> exactLifeInput = input.filter { it.isDigit() } },
                    label = { Text("Exact ${uiProfile.lifeLabel} Value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_exact_life_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = exactLifeInput.toLongOrNull()
                        if (parsed != null) {
                            onSetLifePointsExact(seatBeingEdited, parsed)
                            editingLifeSeat = null
                        }
                    },
                    modifier = Modifier.testTag("sync_confirm_exact_life_button")
                ) {
                    Text("Sync to Firestore")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLifeSeat = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SyncedLifeCounterCard(
    seat: SyncClientSeat,
    playerLabel: String,
    lifeValue: Long,
    startingLife: Long,
    lifeUnitLabel: String,
    isLocalSeat: Boolean,
    canMutate: Boolean,
    deltaSteps: List<Long>,
    onDelta: (Long) -> Unit,
    onEditExact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val healthRatio = if (startingLife > 0L) lifeValue.toFloat() / startingLife.toFloat() else 1f
    val lifeColor = when {
        lifeValue <= 0L || healthRatio <= 0.25f -> CrimsonAlert
        healthRatio <= 0.55f -> RadiantGold
        else -> EmeraldReady
    }
    val borderColor = if (isLocalSeat) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isLocalSeat) "${seat.shortLabel} (YOU)" else seat.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isLocalSeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onEditExact,
                enabled = canMutate,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("sync_edit_life_${seat.code}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Set exact ${seat.shortLabel} life points",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Text(
            text = playerLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = lifeUnitLabel,
                tint = lifeColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$lifeValue",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = lifeColor,
                modifier = Modifier.testTag("sync_life_value_${seat.code}")
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = lifeUnitLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2x2 Grid of Atomic Delta Buttons
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                deltaSteps.take(2).forEach { step ->
                    LifeDeltaChipButton(
                        delta = step,
                        enabled = canMutate,
                        onClick = { onDelta(step) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_life_btn_${seat.code}_$step")
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                deltaSteps.drop(2).forEach { step ->
                    LifeDeltaChipButton(
                        delta = step,
                        enabled = canMutate,
                        onClick = { onDelta(step) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_life_btn_${seat.code}_+$step")
                    )
                }
            }
        }
    }
}

@Composable
private fun LifeDeltaChipButton(
    delta: Long,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDamage = delta < 0L
    val containerColor = if (isDamage) {
        CrimsonAlert.copy(alpha = 0.18f)
    } else {
        EmeraldReady.copy(alpha = 0.18f)
    }
    val contentColor = if (isDamage) CrimsonAlert else EmeraldReady
    val label = if (isDamage) "$delta" else "+$delta"

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.heightIn(min = 38.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SyncedZoneChip(
    placement: SerializedZonePlacement,
    canMutate: Boolean,
    onToggleOrientation: () -> Unit,
    onRemove: () -> Unit
) {
    val isP1 = placement.zoneId.startsWith("P1_", ignoreCase = true) || placement.owner == "PLAYER_1_HOST"
    val accent = if (isP1) MaterialTheme.colorScheme.primary else RadiantGold
    val orientationLabel = if (placement.isDefenseOrTapped) "DEF/TAP" else "ATK/UP"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable(enabled = canMutate) { onToggleOrientation() }
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag("sync_zone_chip_${placement.zoneId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "[${placement.zoneId}] • $orientationLabel",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = "${placement.cardName} (${placement.attack}/${placement.defense})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.ScreenRotation,
            contentDescription = "Toggle orientation",
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        IconButton(
            onClick = onRemove,
            enabled = canMutate,
            modifier = Modifier
                .size(32.dp)
                .testTag("sync_zone_remove_${placement.zoneId}")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove ${placement.cardName} from ${placement.zoneId}",
                tint = CrimsonAlert,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
