package com.mundus.agenda.stats

import com.mundus.agenda.Task
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.TreeMap

/**
 * Pure statistics computations mirroring the desktop page verbatim:
 * aggregation `src/pages/statistics.rs:15-55`, task predicate
 * `crates/agenda-core/src/filters.rs:6-14,87-96`, day attribution
 * `crates/agenda-core/src/dates.rs:75-96`, month tables `dates.rs:134-161`.
 *
 * Everything takes `today`/`offset` as parameters so JVM tests pin them.
 */

/** agenda-core `MONTH_SHORT` (dates.rs:134-148). */
val MONTH_SHORT =
    arrayOf("янв.", "февр.", "мар.", "апр.", "мая", "июня", "июля", "авг.", "сент.", "окт.", "нояб.", "дек.")

/** agenda-core `MONTH_LONG` (dates.rs:149-161). */
val MONTH_LONG =
    arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

private fun isCanceled(t: Task): Boolean = t.isCancelled || t.status == "canceled"

/** `task_status(t) == Status::Done` (filters.rs:6-14) — canceled checked first. */
private fun isDone(t: Task): Boolean = !isCanceled(t) && (t.isCompleted || t.status == "done")

/**
 * `completed_day` (filters.rs:87-96): status Done only (canceled tasks carry
 * `completed_at` but are not completions), never trashed. The day is the
 * RFC3339 stamp read at the device offset (`stamp_day_in`); a bare
 * `YYYY-MM-DD` prefix is accepted only if it parses (`date_only`).
 */
fun completedDay(t: Task, offset: ZoneOffset): LocalDate? {
    if (!isDone(t) || t.isTrashed) return null
    val v = t.completedAt ?: return null
    try {
        return OffsetDateTime.parse(v).withOffsetSameInstant(offset).toLocalDate()
    } catch (_: Exception) {
        // not an RFC3339 stamp — try the date-key prefix
    }
    val prefix = v.take(10)
    return if (prefix.length == 10) runCatching { LocalDate.parse(prefix) }.getOrNull() else null
}

/** Aggregates shown in the summary strip (statistics.rs:22-55). */
class Statistics(val days: Map<LocalDate, Int>, val today: LocalDate) {
    val total: Int = days.values.sum()

    /** `d > today - 7` — the last 7 calendar days, today included. */
    val last7: Int = days.filterKeys { it.isAfter(today.minusDays(7)) }.values.sum()

    val last30: Int = days.filterKeys { it.isAfter(today.minusDays(30)) }.values.sum()

    /** Current streak may start yesterday when today is still empty. */
    val streak: Int =
        run {
            var n = 0
            var d = if (days.containsKey(today)) today else today.minusDays(1)
            while (days.containsKey(d)) {
                n++
                d = d.minusDays(1)
            }
            n
        }

    val bestStreak: Int =
        run {
            var best = 0
            var run = 0
            var prev: LocalDate? = null
            for (d in days.keys) { // `days` is sorted
                run = if (prev != null && d == prev!!.plusDays(1)) run + 1 else 1
                best = maxOf(best, run)
                prev = d
            }
            best
        }

    /** Desktop share fallback text (share.rs:128-130). */
    val shareText: String
        get() =
            "Agenda: выполнено $total задач, за неделю $last7, серия $streak дн., лучшая серия $bestStreak дн."
}

/** Per-day counts over the whole task snapshot (statistics.rs:15-21). */
fun statistics(
    tasks: List<Task>,
    today: LocalDate,
    offset: ZoneOffset = ZoneId.systemDefault().rules.getOffset(Instant.now()),
): Statistics {
    val days = TreeMap<LocalDate, Int>()
    for (t in tasks) {
        val d = completedDay(t, offset) ?: continue
        days[d] = (days[d] ?: 0) + 1
    }
    return Statistics(days, today)
}

/**
 * GitHub-style heatmap model (statistics.rs:104-168): columns are ISO weeks
 * (Mon–Sun), rows weekdays 0..7; the last column is the current week.
 * `vals[w*7+row]`; alpha is continuous, normalized by the visible max.
 */
class Heatmap(
    val weeks: Int,
    val start: LocalDate,
    private val vals: IntArray,
    val max: Int,
    val monthLabels: List<String>,
) {
    fun date(w: Int, row: Int): LocalDate = start.plusDays((w * 7 + row).toLong())

    fun value(w: Int, row: Int): Int = vals[w * 7 + row]

    /** `cell_bg` alpha (statistics.rs:124-133); 0 means "empty cell". */
    fun alpha(v: Int): Float =
        if (v <= 0) 0f else 0.25f + 0.75f * minOf(v.toFloat() / maxOf(max, 1), 1f)

    override fun equals(other: Any?): Boolean =
        other is Heatmap && other.start == start && other.weeks == weeks && other.vals.contentEquals(vals)

    override fun hashCode(): Int = 31 * start.hashCode() + vals.contentHashCode()
}

fun heatmap(days: Map<LocalDate, Int>, today: LocalDate, weeks: Int): Heatmap {
    val lastMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val start = lastMonday.minusDays(7L * (weeks - 1))
    val vals = IntArray(weeks * 7)
    var max = 0
    for ((d, count) in days) {
        val idx = ChronoUnit.DAYS.between(start, d).toInt()
        if (idx in vals.indices) {
            vals[idx] = count
            max = maxOf(max, count)
        }
    }
    // A week column is labelled when the 1st of a month falls inside its
    // Mon–Sun range — only if the month starts inside the grid and two
    // columns remain on the right (statistics.rs:139-168).
    val labels =
        (0 until weeks).map { w ->
            val monday = start.plusDays(7L * w)
            val sunday = monday.plusDays(6)
            val first =
                if (monday.dayOfMonth == 1) monday
                else if (sunday.monthValue != monday.monthValue) sunday.withDayOfMonth(1)
                else null
            if (first != null && first >= start && w + 2 <= weeks) {
                MONTH_SHORT[first.monthValue - 1].take(3)
            } else {
                ""
            }
        }
    return Heatmap(weeks, start, vals, max, labels)
}
