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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.annotation.DrawableRes
import com.mundus.agenda.BuildConfig
import com.mundus.agenda.R
import com.mundus.agenda.SyncApi
import com.mundus.agenda.SyncPeer
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

/** Desktop `device_info::Platform` (manager-gpui device_info.rs): label
 * and icon come only from metadata the peer sent, never from its name. */
private enum class Platform(val label: String?, @DrawableRes val icon: Int) {
    Mac("macOS", R.drawable.ic_device_laptop),
    Windows("Windows", R.drawable.ic_device_monitor),
    Linux("Linux", R.drawable.ic_device_monitor),
    Android("Android", R.drawable.ic_device_android),
    Ios("iOS", R.drawable.ic_device_iphone),
    Unknown(null, R.drawable.ic_device_monitor);

    companion object {
        fun parse(value: String?): Platform =
            when (value?.trim()?.lowercase()) {
                "macos", "mac", "darwin" -> Mac
                "windows", "win32", "win" -> Windows
                "linux" -> Linux
                "android" -> Android
                "ios", "iphone", "ipados" -> Ios
                else -> Unknown
            }
    }
}

/** Desktop `peer_caption`: «Android / 0.1.10», whatever half is known. */
private fun caption(platform: Platform, version: String?): String {
    val v = version?.trim()?.ifEmpty { null }
    return when {
        platform.label != null && v != null -> "${platform.label} / $v"
        platform.label != null -> platform.label
        v != null -> "Версия $v"
        else -> "ОС и версия не переданы"
    }
}

private fun peerName(peer: SyncPeer) = peer.deviceName?.trim()?.ifEmpty { null } ?: "Устройство"

/** RFC 3339 `last_seen` → «только что» / «5 мин назад» / «сегодня в 14:05» / «3 окт. в 09:12». */
private fun lastSeenLabel(raw: String?): String? {
    val instant = raw?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() } ?: return null
    val now = java.time.Instant.now()
    val minutes = java.time.Duration.between(instant, now).toMinutes()
    if (minutes < 1) return "только что"
    if (minutes < 60) return "$minutes мин назад"
    val zone = java.time.ZoneId.systemDefault()
    val at = instant.atZone(zone)
    val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(at)
    val today = java.time.LocalDate.now(zone)
    return when (at.toLocalDate()) {
        today -> "сегодня в $time"
        today.minusDays(1) -> "вчера в $time"
        else ->
            java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale("ru")).format(at) + " в $time"
    }
}

/** Desktop `device_avatar`: 32dp tile, fg 4% fill, icon at fg 70%. */
@Composable
private fun DeviceAvatar(platform: Platform, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val c = AgendaTheme.colors
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(c.fg(0.04f)),
        contentAlignment = Alignment.Center,
    ) {
        AIcon(platform.icon, tint = c.fg(0.7f), modifier = Modifier.size(size * 0.69f))
    }
}

/** Desktop `presence`: 6dp dot + 12sp label in the same color. */
@Composable
private fun Presence(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = AgendaType.meta, color = color)
    }
}

@Composable
private fun DeviceRow(
    platform: Platform,
    name: String,
    detail: String,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    ARow(onClick = onClick) { glyph ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            DeviceAvatar(platform)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = AgendaType.body, color = glyph, maxLines = 1)
                Text(detail, style = AgendaType.meta, color = AgendaTheme.colors.mutedFg, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

private fun peerStatus(status: String?) =
    when (status) {
        "online" -> "В сети"
        "offline" -> "Не в сети"
        else -> "Статус неизвестен"
    }

/**
 * Sync + pairing (KOS-380) — the phone twin of the desktop Manager sync
 * view (manager-gpui views/sync.rs device blocks, sync/pairing.rs
 * «Подключить устройство», modals.rs incoming consent). A peer row opens
 * its details in a sheet. Status polls `get_sync_snapshot` every 3 s like
 * app_polls.rs:21-35.
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
    var connectError by remember { mutableStateOf<String?>(null) }
    var openPeerId by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

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

    fun connect(code: String) {
        if (code.isEmpty()) {
            connectError = "В буфере обмена нет кода"
            return
        }
        connectError = null
        act {
            if (runCatching { sync?.connectWithPairingCode(code) }.isFailure) {
                connectError = "Не удалось подключиться по этому коду"
            }
        }
    }

    LaunchedEffect(Unit) {
        DebugPairing.pendingTicket?.let {
            DebugPairing.pendingTicket = null
            connect(it.trim())
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 96.dp)
    ) {
        ASectionHeader("Устройства")
        SettingsGroup {
            val running = snap?.running == true
            DeviceRow(
                platform = Platform.Android,
                name = snap?.localDevice?.deviceName ?: deviceName,
                detail = caption(Platform.Android, BuildConfig.VERSION_NAME),
                onClick = null,
            ) {
                Presence(
                    when {
                        snap == null -> "…"
                        running || syncing -> "Я"
                        else -> "Синхронизация остановлена"
                    },
                    if (running || syncing || snap == null) c.mutedFg else c.warn,
                )
            }
            for (peer in snap?.peers.orEmpty()) {
                ADivider(indent = 56.dp)
                val platform = Platform.parse(peer.platform)
                DeviceRow(
                    platform = platform,
                    name = peerName(peer),
                    detail = caption(platform, peer.appVersion),
                    onClick = { openPeerId = peer.deviceId },
                ) {
                    val online = peer.status == "online"
                    Presence(peerStatus(peer.status), if (online) c.success else c.mutedFg)
                }
            }
            ADivider(indent = 12.dp)
            // The other device's code is always copied, never typed:
            // one tap reads the clipboard and dials.
            ARow(onClick = { connect(clipboard.getText()?.text?.trim().orEmpty()) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AIcon(R.drawable.ic_clipboard, tint = c.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Подключить устройство",
                        style = AgendaType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.accent,
                    )
                }
            }
            connectError?.let {
                Text(
                    it,
                    style = AgendaType.meta,
                    color = c.destructive,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            // Outgoing attempt status (worktree pairing.rs:115-168).
            snap?.outgoingPairing?.let { out ->
                ADivider(indent = 12.dp)
                ARow(onClick = null) { glyph ->
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
                ADivider(indent = 12.dp)
                ARow(onClick = null) { glyph ->
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

        ASectionHeader("Синхронизация")
        SettingsGroup {
            // No engine «sync now»: a restart redials every peer and the
            // Hello/VersionVector exchange pulls whatever was missed.
            ARow(
                onClick = {
                    if (!syncing) {
                        syncing = true
                        act {
                            sync?.restartSync(deviceName)
                            syncing = false
                        }
                    }
                }
            ) {
                Text(
                    if (syncing) "Синхронизация…" else "Синхронизировать",
                    style = AgendaType.body,
                    color = c.accent,
                )
            }
            ADivider(indent = 12.dp)
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
        }
    }

    snap?.peers?.firstOrNull { it.deviceId == openPeerId }?.let { peer ->
        PeerSheet(
            peer = peer,
            onDismiss = { openPeerId = null },
            onDisconnect = {
                openPeerId = null
                act { sync?.disconnectPeer(peer.deviceId) }
            },
        )
    }
}

/** Peer details: who it is, when we last heard from it, and the
 * desktop «Отключить» (confirmed in place, like its confirm modal). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PeerSheet(peer: SyncPeer, onDismiss: () -> Unit, onDisconnect: () -> Unit) {
    val c = AgendaTheme.colors
    val platform = Platform.parse(peer.platform)
    val online = peer.status == "online"
    var confirming by remember { mutableStateOf(false) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.popover) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(platform, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        peerName(peer),
                        style = AgendaType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.fg,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Presence(peerStatus(peer.status), if (online) c.success else c.mutedFg)
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                val rows =
                    listOfNotNull(
                        lastSeenLabel(peer.lastSeen)?.let { "Последний раз в сети" to it },
                        "Система" to (platform.label ?: "Не передана"),
                        "Версия" to (peer.appVersion?.ifBlank { null } ?: "Не передана"),
                        "ID устройства" to peer.deviceId,
                    )
                rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) ADivider(indent = 12.dp)
                    ARow(onClick = null) { glyph ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, style = AgendaType.body, color = glyph)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                value,
                                style = AgendaType.body,
                                color = c.mutedFg,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                ARow(onClick = { if (confirming) onDisconnect() else confirming = true }) {
                    Text(
                        if (confirming) "Нажмите ещё раз, чтобы отключить" else "Отключить устройство",
                        style = AgendaType.body,
                        color = c.destructive,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (confirming) {
                Text(
                    "«${peerName(peer)}» будет удалено из списка синхронизации.",
                    style = AgendaType.meta,
                    color = c.mutedFg,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 12.dp),
                )
            }
        }
    }
}
