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
    interval: Int = com.mundus.agenda.DEFAULT_SYNC_INTERVAL,
    onInterval: (Int) -> Unit = {},
    exactAlarms: () -> Boolean? = { null },
    onExactAlarms: () -> Unit = {},
) {
    val c = AgendaTheme.colors
    var snap by remember { mutableStateOf<SyncSnapshot?>(null) }
    var myCode by remember { mutableStateOf<String?>(null) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var openPeerId by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var exact by remember { mutableStateOf(exactAlarms()) }

    LaunchedEffect(sync) {
        while (true) {
            snap = sync?.let { runCatching { it.syncSnapshot() }.getOrNull() }
            exact = exactAlarms()
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
                .padding(top = 8.dp, bottom = bottomClearance())
    ) {
        SettingsGroup {
            val running = snap?.running == true
            DeviceRow(
                platform = Platform.Android,
                name = snap?.localDevice?.deviceName ?: deviceName,
                detail = caption(Platform.Android, versionNameForUi),
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
                SettingsDivider()
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
            SettingsDivider()
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
                SettingsDivider()
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
        }

        Spacer(Modifier.height(24.dp))
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
            SettingsDivider()
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
        ScheduleSection(interval, onInterval, exact, onExactAlarms)
    }

    // Incoming consent (worktree modals.rs:91-190) uses the same sheet
    // as a peer's details, with «Подключить» in place of «Отключить».
    val incoming = snap?.incomingPairingRequests?.firstOrNull()
    val peer = snap?.peers?.firstOrNull { it.deviceId == openPeerId }
    if (incoming != null) {
        val platform = Platform.parse(incoming.platform)
        DeviceSheet(
            name = incoming.deviceName?.trim()?.ifEmpty { null } ?: "Устройство",
            platform = platform,
            status = { Presence("Хочет подключиться", c.accent) },
            rows =
                listOf(
                    "Система" to (platform.label ?: "Не передана"),
                    "Версия" to (incoming.appVersion?.ifBlank { null } ?: "Не передана"),
                    "ID устройства" to incoming.deviceId,
                ),
            onDismiss = { act { sync?.declinePairing(incoming.deviceId) } },
        ) {
            SheetAction("Подключить", c.accent, bold = true) {
                act { sync?.acceptPairing(incoming.deviceId) }
            }
            Spacer(Modifier.height(8.dp))
            SheetAction("Отклонить", c.mutedFg) {
                act { sync?.declinePairing(incoming.deviceId) }
            }
        }
    } else if (peer != null) {
        val platform = Platform.parse(peer.platform)
        var confirming by remember(peer.deviceId) { mutableStateOf(false) }
        DeviceSheet(
            name = peerName(peer),
            platform = platform,
            status = {
                Presence(peerStatus(peer.status), if (peer.status == "online") c.success else c.mutedFg)
            },
            rows =
                listOfNotNull(
                    lastSeenLabel(peer.lastSeen)?.let { "Последний раз в сети" to it },
                    "Система" to (platform.label ?: "Не передана"),
                    "Версия" to (peer.appVersion?.ifBlank { null } ?: "Не передана"),
                    "ID устройства" to peer.deviceId,
                ),
            onDismiss = { openPeerId = null },
        ) {
            SheetAction(
                if (confirming) "Нажмите ещё раз, чтобы отключить" else "Отключить устройство",
                c.destructive,
            ) {
                if (confirming) {
                    openPeerId = null
                    act { sync?.disconnectPeer(peer.deviceId) }
                } else {
                    confirming = true
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

@Composable
private fun SheetAction(label: String, color: Color, bold: Boolean = false, onClick: () -> Unit) {
    SettingsGroup {
        ARow(onClick = onClick) {
            Text(
                label,
                style = if (bold) AgendaType.body.copy(fontWeight = FontWeight.SemiBold) else AgendaType.body,
                color = color,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A device in a sheet: avatar, name, status, a facts table, then the
 * caller's actions (disconnect for a peer, accept for a pairing request). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSheet(
    name: String,
    platform: Platform,
    status: @Composable () -> Unit,
    rows: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val c = AgendaTheme.colors
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.popover) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(platform, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = AgendaType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.fg,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    status()
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) SettingsDivider(surface = c.popover)
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
            actions()
        }
    }
}

private fun intervalLabel(minutes: Int) = if (minutes == 60) "Каждый час" else "Каждые $minutes мин"

/** Wall-clock slot interval (sync/SyncSlots.kt) and, on Android 12+, the
 * exact-alarm access that keeps slots on the minute. */
@Composable
private fun ScheduleSection(
    interval: Int,
    onInterval: (Int) -> Unit,
    exact: Boolean?,
    onExactAlarms: () -> Unit,
) {
    val c = AgendaTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Spacer(Modifier.height(24.dp))
    SettingsGroup {
        androidx.compose.foundation.layout.Box {
            ARow(onClick = { menuOpen = true }) { glyph ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Фоновая синхронизация", style = AgendaType.body, color = glyph, modifier = Modifier.weight(1f))
                    Text(intervalLabel(interval).lowercase(), style = AgendaType.body, color = c.mutedFg)
                }
            }
            AMenu(open = menuOpen, onDismiss = { menuOpen = false }) {
                for (minutes in com.mundus.agenda.SYNC_INTERVALS) {
                    AMenuItem(intervalLabel(minutes)) {
                        menuOpen = false
                        onInterval(minutes)
                    }
                }
            }
        }
        if (exact != null) {
            SettingsDivider()
            ARow(onClick = if (exact) null else onExactAlarms) { glyph ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Точно по времени", style = AgendaType.body, color = c.fg, modifier = Modifier.weight(1f))
                    Text(
                        if (exact) "Вкл" else "Разрешить",
                        style = AgendaType.body,
                        color = if (exact) c.success else c.accent,
                    )
                }
            }
        }
    }
    Text(
        "Все устройства синхронизируются в одно время: в начале часа и дальше по интервалу. " +
            "Без точного времени запуск может опоздать на несколько минут.",
        style = AgendaType.meta,
        color = c.mutedFg,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
