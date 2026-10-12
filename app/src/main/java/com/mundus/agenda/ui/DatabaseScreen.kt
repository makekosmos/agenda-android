package com.mundus.agenda.ui

import android.content.Context
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mundus.agenda.Board
import com.mundus.agenda.SmartList
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** On-disk footprint of the app, bytes. */
data class StorageUsage(val database: Long, val journal: Long, val other: Long, val deviceFree: Long) {
    val total: Long get() = database + journal + other
}

private fun File.walkSize(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

fun measureStorage(context: Context): StorageUsage {
    val files = context.filesDir
    val db = File(files, "ark.db").length()
    val journal = listOf("ark.db-wal", "ark.db-shm", "ark.db-journal").sumOf { File(files, it).length() }
    val all = files.walkSize() + context.cacheDir.walkSize()
    return StorageUsage(db, journal, (all - db - journal).coerceAtLeast(0), StatFs(files.path).availableBytes)
}

fun formatBytes(bytes: Long): String {
    val units = listOf("Б", "КБ", "МБ", "ГБ")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return if (i == 0 || v >= 100) "${v.toLong()} ${units[i]}" else "%.1f %s".format(v, units[i])
}

private class Segment(val label: String, val bytes: Long, val color: Color)

/** anytype-kotlin `SegmentLine` (space/SegmentLine.kt): one rounded box per
 * segment, 2dp gaps, width proportional with a 4dp floor. */
@Composable
private fun SegmentLine(segments: List<Segment>) {
    val total = segments.sumOf { it.bytes }.coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxWidth().height(27.dp)) {
        val gaps = 2.dp * (segments.size - 1)
        val unit = (maxWidth - gaps) / total.toFloat()
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (s in segments) {
                Box(
                    Modifier
                        .width(maxOf(unit * s.bytes.toFloat(), 4.dp))
                        .height(27.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(s.color)
                )
            }
        }
    }
}

@Composable
private fun SegmentLegend(segments: List<Segment>) {
    val c = AgendaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in segments) {
            Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(s.color))
                Text(
                    s.label,
                    style = AgendaType.body,
                    color = c.fg,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                Text(formatBytes(s.bytes), style = AgendaType.body, color = c.mutedFg)
            }
        }
    }
}

@Composable
private fun CountRow(label: String, value: Int) {
    val c = AgendaTheme.colors
    ARow(onClick = {}) { glyph ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = AgendaType.body, color = glyph, modifier = Modifier.weight(1f))
            Text(value.toString(), style = AgendaType.body, color = c.mutedFg)
        }
    }
}

/** Database page: the anytype space-storage card (SpaceStorageScreen) over
 * the engine's own files, then what is in them. */
@Composable
fun DatabaseScreen(board: Board, usage: StorageUsage? = null) {
    val c = AgendaTheme.colors
    val context = LocalContext.current
    var measured by remember { mutableStateOf(usage) }
    LaunchedEffect(usage) {
        if (usage == null) measured = withContext(Dispatchers.IO) { measureStorage(context) }
    }
    val dark = c.bg.luminance() < 0.5f
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 96.dp)
    ) {
        ASectionHeader("Хранилище")
        SettingsGroup {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                val u = measured
                Text(
                    if (u == null) "…" else formatBytes(u.total),
                    style = AgendaType.pageTitle,
                    color = c.fg,
                )
                Text(
                    if (u == null) " " else "свободно на устройстве ${formatBytes(u.deviceFree)}",
                    style = AgendaType.meta,
                    color = c.mutedFg,
                )
                Spacer(Modifier.height(16.dp))
                val segments =
                    listOf(
                        Segment("База данных", u?.database ?: 0, Color(if (dark) 0xFFFFC83C else 0xFFF09C0E)),
                        Segment("Журнал", u?.journal ?: 0, Color(if (dark) 0xFF9F6B00 else 0xFFFFD15B)),
                        Segment("Прочее", u?.other ?: 0, Color(if (dark) 0xFF303030 else 0xFFEBEBEB)),
                    )
                SegmentLine(segments)
                Spacer(Modifier.height(16.dp))
                SegmentLegend(segments)
            }
        }
        ASectionHeader("Содержимое")
        SettingsGroup {
            CountRow("Задачи", board.todos.count { !it.isTrashed })
            ADivider(indent = 16.dp)
            CountRow("Проекты", board.projects.size)
            ADivider(indent = 16.dp)
            CountRow("Теги", board.tags.size)
            ADivider(indent = 16.dp)
            CountRow("В корзине", board.count(SmartList.TRASH))
        }
    }
}
