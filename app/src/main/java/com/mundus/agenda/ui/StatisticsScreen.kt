package com.mundus.agenda.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
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
 * Statistics page — desktop `src/pages/statistics.rs:199-296` layout
 * minus the share button (moved to the top bar, [rememberStatisticsShare])
 * and the profile stub: summary strip, «Активность» heatmap + month
 * captions. `weeks` is fixed at 53 with horizontal scroll (mobile has no
 * viewport to adapt to; same data as desktop).
 */
private const val HEAT_WEEKS = 53

private val CELL = 12.dp
private val GAP = 3.dp

/** Share action for the top-bar button: renders the desktop share card
 * to a PNG ([renderShareCard]) and sends it via `ACTION_SEND` with the
 * desktop copy-text string as EXTRA_TEXT fallback (share.rs:128-130).
 * [onShare] intercepts the text for tests/screenshots. */
@Composable
fun rememberStatisticsShare(
    board: Board,
    today: LocalDate = LocalDate.now(),
    onShare: ((String) -> Unit)? = null,
): () -> Unit {
    val c = AgendaTheme.colors
    val context = LocalContext.current
    val stats = remember(board.todos, today) { statistics(board.todos, today) }
    return {
        if (onShare != null) {
            onShare(stats.shareText)
        } else {
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, stats.shareText)
                    try {
                        val file =
                            File(context.cacheDir, "share/card.png").also { f ->
                                f.parentFile?.mkdirs()
                                renderShareCard(
                                        context,
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
    }
}

@Composable
fun StatisticsScreen(
    board: Board,
    today: LocalDate = LocalDate.now(),
) {
    val c = AgendaTheme.colors
    val stats = remember(board.todos, today) { statistics(board.todos, today) }
    val heat = remember(stats.days, today) { heatmap(stats.days, today, HEAT_WEEKS) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(c.bg)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomClearance())
    ) {
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

        // Heatmap + month captions, lazy-scrolled (statistics.rs:100-197).
        // Each item is one week column with its caption under it.
        LazyRow(
            Modifier.padding(top = 12.dp).height(CELL * 7 + GAP * 6 + 8.dp + 14.dp),
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            items(heat.weeks) { w ->
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    for (row in 0 until 7) {
                        val date = heat.date(w, row)
                        if (!date.isAfter(today)) {
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
                        } else {
                            // Above today the column shrinks — the grid ends at today.
                            Box(Modifier.size(CELL))
                        }
                    }
                    // Cell-width anchor; the label spills right into the
                    // following columns (months are ≥4 weeks apart).
                    Box(Modifier.width(CELL).padding(top = 8.dp - GAP)) {
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

/** contentDescription on a cell, kept simple (no tooltip framework). */
private fun Modifier.semanticsTooltip(text: String): Modifier =
    this.then(Modifier.semantics { contentDescription = text })
