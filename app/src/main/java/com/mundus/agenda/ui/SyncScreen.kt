package com.mundus.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mundus.agenda.SyncApi
import com.mundus.agenda.SyncSnapshot
import kotlinx.coroutines.delay

/** Debug-only injection slot for the e2e pairing flow (MainActivity
 * reads `--es pair_ticket` and deposits the ticket here; release builds
 * never set it). */
object DebugPairing {
    var pendingTicket: String? = null
}

@Composable
private fun SyncButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    val c = AgendaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by
        animateColorAsState(
            if (pressed) c.fg(0.08f) else Color.Transparent,
            animationSpec = tween(120, easing = EaseStandard),
            label = "sync-btn",
        )
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style =
                AgendaType.body.copy(
                    fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal
                ),
            color = if (primary) c.accent else c.mutedFg,
        )
    }
}

/**
 * Sync + pairing (KOS-380) — the phone twin of the desktop Manager sync
 * view (manager-gpui views/sync.rs:65-110 device list, sync/pairing.rs
 * «Подключить устройство» card, modals.rs:91-190 incoming consent).
 * Status polls `get_sync_snapshot` every 3 s like app_polls.rs:21-35.
 */
@Composable
fun SyncScreen(
    sync: SyncApi?,
    deviceName: String = android.os.Build.MODEL,
    run: (suspend () -> Unit) -> Unit = { block -> },
) {
    val c = AgendaTheme.colors
    var snap by remember { mutableStateOf<SyncSnapshot?>(null) }
    var myCode by remember { mutableStateOf<String?>(null) }
    var peerCode by remember {
        mutableStateOf(DebugPairing.pendingTicket?.also { DebugPairing.pendingTicket = null } ?: "")
    }
    var connectError by remember { mutableStateOf(false) }

    LaunchedEffect(sync) {
        while (true) {
            snap = sync?.let { runCatching { it.syncSnapshot() }.getOrNull() }
            delay(3000)
        }
    }

    fun act(block: suspend () -> Unit) {
        run {
            block()
            snap = sync?.let { runCatching { it.syncSnapshot() }.getOrNull() }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = bottomClearance())
    ) {
        ASectionHeader("Статус")
        SettingsGroup {
            val running = snap?.running == true
            ARow(onClick = {}) { glyph ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Синхронизация",
                        style = AgendaType.body,
                        color = glyph,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (running) "Вкл · ${snap?.transport ?: ""}".trim()
                        else if (snap == null) "…"
                        else "Выкл",
                        style = AgendaType.body,
                        color = if (running) c.success else c.mutedFg,
                    )
                }
            }
            if (snap?.localDevice?.deviceName != null) {
                ADivider(indent = 44.dp)
                ARow(onClick = {}) { glyph ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Это устройство", style = AgendaType.body, color = glyph)
                        Spacer(Modifier.weight(1f))
                        Text(
                            snap?.localDevice?.deviceName ?: "",
                            style = AgendaType.body,
                            color = c.mutedFg,
                        )
                    }
                }
            }
            ADivider(indent = 44.dp)
            ARow(onClick = { act { if (sync?.syncSnapshot()?.running != true) sync?.startSync(deviceName) } }) { glyph ->
                Text("Обновить", style = AgendaType.body, color = glyph)
            }
        }

        ASectionHeader("Подключение устройств")
        SettingsGroup {
            // «Мой код» — desktop «Скопировать код» row (pairing.rs:44-75).
            ARow(
                onClick = {
                    if (myCode == null) act { myCode = sync?.showPairingCode() }
                }
            ) { glyph ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Мой код", style = AgendaType.body, color = glyph)
                    Spacer(Modifier.weight(1f))
                    if (myCode == null) {
                        Text("Показать", style = AgendaType.body, color = c.accent)
                    }
                }
            }
            myCode?.let { code ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        code,
                        style = AgendaType.meta,
                        color = c.mutedFg,
                        maxLines = 4,
                    )
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SyncButton("Скопировать", primary = true) {
                            clipboard.setText(AnnotatedString(code))
                        }
                    }
                }
            }
            ADivider(indent = 44.dp)
            // Peer code entry — desktop «Подключить» (pairing.rs:100-109).
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                SheetField(
                    value = peerCode,
                    onValueChange = { peerCode = it },
                    placeholder = "Код другого устройства",
                    style = AgendaType.body,
                    singleLine = true,
                )
                if (connectError) {
                    Text(
                        "Не удалось подключиться",
                        style = AgendaType.meta,
                        color = c.destructive,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SyncButton("Подключить", primary = true) {
                        val code = peerCode.trim()
                        if (code.isNotEmpty()) {
                            act {
                                connectError =
                                    runCatching {
                                        sync?.connectWithPairingCode(code)
                                    }.isFailure
                            }
                        }
                    }
                }
            }
            // Outgoing attempt status (worktree pairing.rs:115-168).
            snap?.outgoingPairing?.let { out ->
                ADivider(indent = 44.dp)
                ARow(onClick = {}) { glyph ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (out.status) {
                                "pending" ->
                                    if (out.expiresInMs == 0L) "Время вышло"
                                    else "Ожидание подтверждения…"
                                "declined" -> "Отклонено"
                                "connected" -> "Подключено"
                                else -> out.status
                            },
                            style = AgendaType.body,
                            color = glyph,
                        )
                        Spacer(Modifier.weight(1f))
                        if (out.status == "pending" || out.expiresInMs == 0L || out.status == "declined") {
                            SyncButton(if (out.status == "pending") "Отмена" else "Скрыть") {
                                act { sync?.cancelPairing() }
                            }
                        }
                    }
                }
            }
            // Incoming consent (worktree modals.rs:91-190, inline here).
            for (req in snap?.incomingPairingRequests.orEmpty()) {
                ADivider(indent = 44.dp)
                ARow(onClick = {}) { glyph ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            req.deviceName ?: req.deviceId,
                            style = AgendaType.body,
                            color = glyph,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        SyncButton("Отклонить") {
                            act { sync?.declinePairing(req.deviceId) }
                        }
                        SyncButton("Принять", primary = true) {
                            act { sync?.acceptPairing(req.deviceId) }
                        }
                    }
                }
            }
        }

        ASectionHeader("Устройства")
        SettingsGroup {
            val peers = snap?.peers.orEmpty()
            if (peers.isEmpty()) {
                ARow(onClick = {}) { glyph ->
                    Text("Нет подключённых устройств", style = AgendaType.body, color = c.mutedFg)
                }
            }
            peers.forEachIndexed { i, peer ->
                if (i > 0) ADivider(indent = 44.dp)
                ARow(onClick = {}) { glyph ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                peer.deviceName ?: peer.deviceId,
                                style = AgendaType.body,
                                color = glyph,
                                maxLines = 1,
                            )
                            if (peer.status == "online") {
                                Text("В сети", style = AgendaType.meta, color = c.success)
                            }
                        }
                        SyncButton("Отключить") {
                            act { sync?.disconnectPeer(peer.deviceId) }
                        }
                    }
                }
            }
        }
    }
}
