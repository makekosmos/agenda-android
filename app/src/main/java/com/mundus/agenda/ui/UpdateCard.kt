package com.mundus.agenda.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mundus.agenda.BuildConfig
import com.mundus.agenda.updater.UpdateManager
import com.mundus.agenda.updater.UpdateState

/** What the single card button does on tap (updates.rs:20-52). */
enum class UpdateAction {
    /** «Проверить обновления» — idle / not-available / error. */
    Check,

    /** «Установить обновление» — downloads first, then installs. */
    DownloadOrInstall,
}

/** Stateless view model for the settings updates card — the pure part of
 * updates.rs:13-140 so a unit test can pin every state mapping. */
data class UpdateCardModel(
    val versionLine: String,
    val statusLine: String,
    val buttonLabel: String,
    val buttonEnabled: Boolean,
    val action: UpdateAction,
    val downloadPercent: Int?,
    val isError: Boolean = false,
)

fun updateCardModel(state: UpdateState, current: String): UpdateCardModel {
    val next =
        when (val s = state) {
            is UpdateState.Available -> s.feed.version
            is UpdateState.Downloading -> s.feed.version
            is UpdateState.ReadyToInstall -> s.feed.version
            else -> null
        }
    val versionLine = if (next == null) current else "$current → $next"
    return when (val s = state) {
        is UpdateState.Idle ->
            UpdateCardModel(
                versionLine, "Не проверялось", "Проверить обновления",
                buttonEnabled = true, UpdateAction.Check, null,
            )
        is UpdateState.Checking ->
            UpdateCardModel(
                versionLine, "Проверяем…", "Проверяем…",
                buttonEnabled = false, UpdateAction.Check, null,
            )
        is UpdateState.NotAvailable ->
            UpdateCardModel(
                versionLine, "Актуальная версия", "Проверить обновления",
                buttonEnabled = true, UpdateAction.Check, null,
            )
        is UpdateState.Available ->
            UpdateCardModel(
                versionLine, "Доступно обновление ${s.feed.version}",
                "Установить обновление",
                buttonEnabled = true, UpdateAction.DownloadOrInstall, null,
            )
        is UpdateState.Downloading ->
            UpdateCardModel(
                versionLine, "Скачивание ${s.percent}%",
                "Установить обновление",
                buttonEnabled = false, UpdateAction.DownloadOrInstall, s.percent,
            )
        is UpdateState.ReadyToInstall ->
            UpdateCardModel(
                versionLine, "Готово к установке", "Установить обновление",
                buttonEnabled = true, UpdateAction.DownloadOrInstall, null,
            )
        is UpdateState.Error ->
            UpdateCardModel(
                versionLine, s.message, "Проверить обновления",
                buttonEnabled = true, UpdateAction.Check, null, isError = true,
            )
    }
}

/** «Обновления» section for the settings page — desktop updates.rs
 * card (title + muted version line + one action button, progress under
 * the row while downloading). Stateless so Roborazzi pins every state. */
@Composable
fun UpdateSection(
    state: UpdateState,
    currentVersion: String,
    onCheck: () -> Unit,
    onDownloadOrInstall: () -> Unit,
) {
    val model = updateCardModel(state, currentVersion)
    SettingsGroup { UpdateCardBody(model, onCheck, onDownloadOrInstall) }
}

@Composable
private fun UpdateCardBody(
    model: UpdateCardModel,
    onCheck: () -> Unit,
    onDownloadOrInstall: () -> Unit,
) {
    val c = AgendaTheme.colors
    val onAction =
        when (model.action) {
            UpdateAction.Check -> onCheck
            UpdateAction.DownloadOrInstall -> onDownloadOrInstall
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Обновления",
                style = AgendaType.body.copy(fontWeight = FontWeight.Medium),
                color = c.fg,
            )
            Spacer(Modifier.height(2.dp))
            Text(model.versionLine, style = AgendaType.meta, color = c.mutedFg)
            Text(
                model.statusLine,
                style = AgendaType.meta,
                color = if (model.isError) c.destructive else c.mutedFg,
            )
        }
        CardButton(
            label = model.buttonLabel,
            enabled = model.buttonEnabled,
            color =
                if (model.action == UpdateAction.DownloadOrInstall) c.success
                else c.accent,
            onClick = onAction,
        )
    }
    model.downloadPercent?.let { percent ->
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            // updates.rs thin bar: fg 12% track, accent fill.
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    .background(c.fg(0.12f))
            ) {
                Box(
                    Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                        .height(4.dp).clip(CircleShape).background(c.accent)
                )
            }
        }
    }
}

/** Flat text button in the banner's language — no stock Material. */
@Composable
private fun CardButton(
    label: String,
    enabled: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = AgendaType.body.copy(fontWeight = FontWeight.Medium),
        color = if (enabled) color else color.copy(alpha = 0.45f),
        maxLines = 1,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** Live wrapper over the shared [UpdateManager] — same instance the home
 * banner reads, so check results show in both places. */
@Composable
fun UpdateSection(mgr: UpdateManager) {
    val context = LocalContext.current
    UpdateSection(
        state = mgr.state,
        currentVersion = BuildConfig.VERSION_NAME,
        onCheck = mgr::check,
        onDownloadOrInstall = {
            when (val s = mgr.state) {
                is UpdateState.Available -> mgr.download(s.feed)
                is UpdateState.Downloading -> mgr.download(s.feed) // no-op while running
                is UpdateState.ReadyToInstall -> installUpdate(context, mgr, s)
                else -> mgr.check()
            }
        },
    )
}

/** Hands the verified APK to the system installer; routes through the
 * unknown-sources permission screen first when needed (API 26+). */
internal fun installUpdate(
    context: Context,
    mgr: UpdateManager,
    state: UpdateState.ReadyToInstall,
) {
    if (mgr.canRequestInstalls()) {
        context.startActivity(mgr.installIntent(state.file))
    } else {
        mgr.openInstallPermissionSettings()
    }
}
