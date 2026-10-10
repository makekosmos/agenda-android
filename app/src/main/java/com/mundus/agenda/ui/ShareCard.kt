package com.mundus.agenda.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.mundus.agenda.stats.Statistics
import com.mundus.agenda.stats.heatmap
import java.time.LocalDate

/** ARGB colors handed in from AgendaTheme (Compose Color → toArgb). */
class ShareCardColors(
    val cardBg: Int,
    val border: Int,
    val fg: Int,
    val muted: Int,
    val accent: Int,
)

/**
 * The desktop share card (pages/statistics/share.rs:80-115) rendered to a
 * Bitmap: avatar + «Имя Фамилия» / «Agenda» header, a 28-week mini-heatmap
 * (CCELL/CGAP proportions, same accent-alpha cell colors), then the 4-stat
 * divided row. Desktop captures its on-screen card via BitBlt; Android has
 * no window-blit, so the same layout is drawn onto an offscreen canvas —
 * identical contents, independent renderer.
 */
fun renderShareCard(stats: Statistics, today: LocalDate, colors: ShareCardColors): Bitmap {
    val s = 2f // ~xhdpi
    val w = (560 * s).toInt()
    val pad = 20 * s
    val avatar = 40 * s
    val heat = heatmap(stats.days, today, 28)

    val cellH = 14 * s
    val cellGap = 4 * s
    val heatH = cellH * 7 + cellGap * 6
    val statsH = 44 * s
    val contentH = avatar + 16 * s + heatH + 16 * s + statsH
    val h = (pad * 2 + contentH).toInt()

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1 * s
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG)

    // Card body.
    fill.color = colors.cardBg
    stroke.color = colors.border
    val body = RectF(0f, 0f, w.toFloat(), h.toFloat())
    cv.drawRoundRect(body, 12 * s, 12 * s, fill)
    cv.drawRoundRect(body, 12 * s, 12 * s, stroke)

    var y = pad

    // Header: 40px avatar ring + name left, «Agenda» right.
    fill.color = withAlpha(colors.fg, 0.06f)
    stroke.color = withAlpha(colors.fg, 0.12f)
    val acx = pad + avatar / 2
    val acy = y + avatar / 2
    cv.drawCircle(acx, acy, avatar / 2, fill)
    cv.drawCircle(acx, acy, avatar / 2, stroke)
    text.color = colors.fg
    text.textSize = 15 * s
    text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    cv.drawText("Имя Фамилия", pad + avatar + 12 * s, acy + 5 * s, text)
    text.color = colors.muted
    text.textSize = 14 * s
    val brand = "Agenda"
    cv.drawText(brand, w - pad - text.measureText(brand), acy + 5 * s, text)

    y += avatar + 16 * s

    // Mini heatmap: 28 ISO-week columns stretched across the card,
    // 7 weekday rows; cells above today are skipped (share.rs:18-35).
    val avail = w - pad * 2
    val cellW = (avail - cellGap * 27) / 28
    for (week in 0 until 28) {
        val x = pad + week * (cellW + cellGap)
        for (row in 0 until 7) {
            val date = heat.date(week, row)
            if (date.isAfter(today)) break
            val v = heat.value(week, row)
            fill.color =
                if (v <= 0) withAlpha(colors.fg, 0.06f)
                else withAlpha(colors.accent, heat.alpha(v))
            val top = y + row * (cellH + cellGap)
            cv.drawRoundRect(RectF(x, top, x + cellW, top + cellH), 3 * s, 3 * s, fill)
        }
    }

    y += heatH + 16 * s

    // Stats row: 4 centered columns with 1px dividers (share.rs:37-69).
    val statPairs =
        listOf(
            "${stats.total}" to "всего",
            "${stats.last7}" to "за неделю",
            "${stats.streak} дн." to "серия",
            "${stats.bestStreak} дн." to "лучшая",
        )
    val colW = avail / 4
    statPairs.forEachIndexed { i, (value, label) ->
        if (i > 0) {
            fill.color = withAlpha(colors.fg, 0.08f)
            cv.drawRect(pad + i * colW, y + 4 * s, pad + i * colW + 1 * s, y + statsH - 4 * s, fill)
        }
        text.textAlign = Paint.Align.CENTER
        text.color = colors.fg
        text.textSize = 16 * s
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        cv.drawText(value, pad + i * colW + colW / 2, y + 18 * s, text)
        text.color = colors.muted
        text.textSize = 11 * s
        text.typeface = Typeface.DEFAULT
        cv.drawText(label, pad + i * colW + colW / 2, y + 36 * s, text)
    }
    text.textAlign = Paint.Align.LEFT

    return bmp
}

private fun withAlpha(color: Int, alpha: Float): Int =
    Color.argb(
        (alpha.coerceIn(0f, 1f) * 255).toInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
