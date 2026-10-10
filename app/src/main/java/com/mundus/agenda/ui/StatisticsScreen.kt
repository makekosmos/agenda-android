package com.mundus.agenda.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mundus.agenda.Board
import com.mundus.agenda.stats.Heatmap
import com.mundus.agenda.stats.MONTH_LONG
import com.mundus.agenda.stats.heatmap
import com.mundus.agenda.stats.statistics
import java.io.File
import java.time.LocalDate

/**
 * Statistics page — desktop `src/pages/statistics.rs:199-296` layout order:
 * share button, profile stub, summary strip, «Активность» heatmap + month
 * captions. `weeks` is fixed at 53 with horizontal scroll (mobile has no
 * viewport to adapt to; same data as desktop).
 *
 * Share renders the desktop share card to a PNG ([renderShareCard]) and
 * sends it via `ACTION_SEND` with the desktop copy-text string as
 * EXTRA_TEXT fallback (share.rs:128-130).
 */
private const val HEAT_WEEKS = 53

private val CELL = 12.dp
private val GAP = 3.dp

@Composable
fun StatisticsScreen(
    board: Board,
    today: LocalDate = LocalDate.now(),
    onShare: ((String) -> Unit)? = null,
) {
    val c = AgendaTheme.colors
    val context = LocalContext.current
    val stats = remember(board.todos, today) { statistics(board.todos, today) }
    val heat = remember(stats.days, today) { heatmap(stats.days, today, HEAT_WEEKS) }

    fun share() {
        if (onShare != null) {
            onShare(stats.shareText)
            return
        }
        val send =
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, stats.shareText)
                try {
                    val file =
                        File(context.cacheDir, "share/card.png").also { f ->
                            f.parentFile?.mkdirs()
                            renderShareCard(
                                    stats,
                                    today,
                                    ShareCardColors(
                                        cardBg = c.sidebarBg.toArgb(),
                                        border = c.border.toArgb(),
                                        fg = c.fg.toArgb(),
                                        muted = c.mutedFg.toArgb(),
                                        accent = c.accent.toArgb(),
                                    ),
                                )
                                .compress(Bitmap.CompressFormat.PNG, 100, f.outputStream())
                        }
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.updates",
                            file,
                        )
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                    type = "text/plain" // image render failed — text-only fallback
                }
            }
        context.startActivity(Intent.createChooser(send, "Поделиться"))
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 48.dp)
    ) {
        // Share affordance, right-aligned (statistics.rs:201-223).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = ::share,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = "Поделиться",
                    tint = c.fg.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Profile stub: empty avatar + placeholder name (statistics.rs:225-248).
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(1.dp, c.fg(0.12f), CircleShape)
                    .background(c.fg(0.06f))
            )
            Text(
                "Имя Фамилия",
                style = AgendaType.input.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                color = c.fg,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        // Summary: a 2-column card grid — full labels, no truncation
        // (same five metrics as the desktop strip, statistics.rs:58-98).
        val items =
            listOf(
                "${stats.total}" to "Всего выполнено",
                "${stats.last7}" to "За 7 дней",
                "${stats.last30}" to "За 30 дней",
                "${stats.streak}" to "Текущая серия",
                "${stats.bestStreak}" to "Лучшая серия",
            )
        Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { (value, label) ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.card)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Text(
                                value,
                                style = AgendaType.body.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                                color = c.fg,
                                maxLines = 1,
                            )
                            Text(
                                label,
                                style = AgendaType.meta.copy(fontSize = 12.sp),
                                color = c.mutedFg,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Text(
            "Активность",
            style = AgendaType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            color = c.fg,
            modifier = Modifier.padding(top = 28.dp),
        )

        // Heatmap + month captions, scrollable (statistics.rs:100-197).
        Column(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState())) {
            HeatmapGrid(heat, today)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                for (w in 0 until heat.weeks) {
                    // Cell-width anchor; the label spills right into the
                    // following columns (months are ≥4 weeks apart).
                    Box(Modifier.width(CELL)) {
                        Text(
                            heat.monthLabels[w],
                            style = AgendaType.label.copy(fontSize = 10.sp),
                            color = c.mutedFg,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(heat: Heatmap, today: LocalDate) {
    val c = AgendaTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
        for (w in 0 until heat.weeks) {
            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                for (row in 0 until 7) {
                    val date = heat.date(w, row)
                    if (date.isAfter(today)) break // the grid ends at today
                    val v = heat.value(w, row)
                    val bg =
                        if (v <= 0) c.fg(0.06f) else c.accent.copy(alpha = heat.alpha(v))
                    val tip = "$v задач ${date.dayOfMonth} ${MONTH_LONG[date.monthValue - 1]}"
                    Box(
                        Modifier
                            .size(CELL)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(bg)
                            .semanticsTooltip(tip)
                    )
                }
            }
        }
    }
}

/** contentDescription on a cell, kept simple (no tooltip framework). */
private fun Modifier.semanticsTooltip(text: String): Modifier =
    this.then(Modifier.semantics { contentDescription = text })
