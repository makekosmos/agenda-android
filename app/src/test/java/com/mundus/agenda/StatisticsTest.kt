package com.mundus.agenda

import com.mundus.agenda.stats.heatmap
import com.mundus.agenda.stats.statistics
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mundus.agenda.stats.completedDay as completedDayFn

/**
 * Pins the desktop statistics rules (pages/statistics.rs:15-55) against the
 * Kotlin port: day attribution with the device UTC offset, Done-only
 * counting, 7/30-day windows, streak-from-yesterday, share text.
 * Fixture day is Monday 2026-10-12 at UTC+3, like FakeRepository.
 */
class StatisticsTest {
    private val today = LocalDate.of(2026, 10, 12)
    private val offset = ZoneOffset.ofHours(3)

    private fun done(
        id: String,
        completedAt: String?,
        trashed: Boolean = false,
    ) = Task(
        id = id,
        title = id,
        status = "done",
        isCompleted = true,
        completedAt = completedAt,
        isTrashed = trashed,
        createdAt = "2026-10-01T09:00:00",
    )

    @Test
    fun `rfc3339 stamp is attributed by device offset, not UTC`() {
        // 23:30 UTC on the 11th is already the 12th at +3.
        val t = done("t", "2026-10-11T23:30:00+00:00")
        assertEquals(LocalDate.of(2026, 10, 12), completedDayFn(t, offset))
        // And the same wall time at -5 is still the 11th locally.
        assertEquals(LocalDate.of(2026, 10, 11), completedDayFn(t, ZoneOffset.ofHours(-5)))
    }

    @Test
    fun `naive and date-key stamps fall back to the YYYY-MM-DD prefix`() {
        assertEquals(LocalDate.of(2026, 10, 11), completedDayFn(done("a", "2026-10-11T14:00:00"), offset))
        assertEquals(LocalDate.of(2026, 10, 11), completedDayFn(done("b", "2026-10-11"), offset))
        assertNull(completedDayFn(done("c", "2026-13-99junk"), offset))
        assertNull(completedDayFn(done("d", null), offset))
    }

    @Test
    fun `canceled and trashed tasks never count`() {
        val canceled =
            done("c", "2026-10-12T11:00:00").copy(status = "canceled", isCancelled = true)
        assertNull(completedDayFn(canceled, offset))
        assertNull(completedDayFn(done("x", "2026-10-12T11:00:00", trashed = true), offset))
        // status "done" counts even without the boolean (task_status checks both).
        val s = done("s", "2026-10-12T11:00:00").copy(isCompleted = false)
        assertEquals(today, completedDayFn(s, offset))
    }

    @Test
    fun `totals and windows`() {
        val tasks =
            listOf(
                done("today", "2026-10-12T09:00:00"),
                done("d1", "2026-10-11T10:00:00"),
                done("d2", "2026-10-10T10:00:00"),
                done("edge7", "2026-10-06T10:00:00"), // today-7 is 10-05, strictly-greater keeps this
                done("out7", "2026-10-05T10:00:00"), // boundary: excluded from last7
                done("in30", "2026-09-20T10:00:00"),
                done("out30", "2026-09-10T10:00:00"), // before today-30 (09-12): nowhere
                done("can", "2026-10-12T11:00:00").copy(status = "canceled", isCancelled = true),
                done("trash", "2026-10-12T11:00:00", trashed = true),
            )
        val s = statistics(tasks, today, offset)
        assertEquals(7, s.total) // done: 12, 11, 10, 06, 05, 09-20, 09-10
        assertEquals(4, s.last7) // strictly after 10-05: 12, 11, 10, 06
        assertEquals(6, s.last30) // strictly after 09-12: all but 09-10
    }

    @Test
    fun `streak starts from yesterday when today is empty`() {
        val tasks =
            listOf(
                done("d1", "2026-10-11T10:00:00"),
                done("d2", "2026-10-10T10:00:00"),
                done("old", "2026-10-01T10:00:00"),
            )
        assertEquals(2, statistics(tasks, today, offset).streak)
        // With today filled the same run counts 3.
        val s2 = statistics(tasks + done("now", "2026-10-12T09:00:00"), today, offset)
        assertEquals(3, s2.streak)
        // A gap before yesterday still yields 0.
        assertEquals(0, statistics(listOf(done("old", "2026-10-05T10:00:00")), today, offset).streak)
    }

    @Test
    fun `best streak spans the longest consecutive run`() {
        val tasks =
            listOf(
                done("a", "2026-09-01T10:00:00"),
                done("b", "2026-09-02T10:00:00"),
                done("c", "2026-09-03T10:00:00"),
                done("g1", "2026-10-01T10:00:00"),
                done("g2", "2026-10-02T10:00:00"),
                done("solo", "2026-10-12T10:00:00"),
            )
        val s = statistics(tasks, today, offset)
        assertEquals(3, s.bestStreak)
        assertEquals(1, s.streak)
    }

    @Test
    fun `share text matches the desktop copy string`() {
        val tasks =
            listOf(
                done("today", "2026-10-12T09:00:00"),
                done("d1", "2026-10-11T10:00:00"),
                done("d2", "2026-10-10T10:00:00"),
                done("in30", "2026-09-20T10:00:00"),
            )
        assertEquals(
            "Agenda: выполнено 4 задач, за неделю 3, серия 3 дн., лучшая серия 3 дн.",
            statistics(tasks, today, offset).shareText,
        )
    }

    @Test
    fun `heatmap layout is ISO weeks ending today`() {
        val tasks = listOf(done("today", "2026-10-12T09:00:00"), done("w0", "2026-09-21T10:00:00"))
        val h = heatmap(statistics(tasks, today, offset).days, today, weeks = 4)
        assertEquals(LocalDate.of(2026, 9, 21), h.start)
        assertEquals(1, h.value(3, 0)) // today = Monday of the last week
        assertEquals(1, h.value(0, 0)) // first cell = start Monday
        assertTrue(h.date(3, 6).isAfter(today)) // future cells skipped by the caller
        assertEquals(1, h.max)
        assertEquals(1f, h.alpha(1), 1e-6f)
        assertEquals(0f, h.alpha(0), 1e-6f)
    }

    @Test
    fun `month captions label the week containing the 1st`() {
        val h = heatmap(emptyMap(), today, weeks = 4)
        // Weeks: 09-21..09-27, 09-28..10-04, 10-05..10-11, 10-12..10-18.
        assertEquals(listOf("", "окт", "", ""), h.monthLabels)
        // Normalized alpha: mid value lands halfway between 0.25 and 1.0.
        val days = mapOf(LocalDate.of(2026, 10, 12) to 4, LocalDate.of(2026, 10, 11) to 2)
        val h2 = heatmap(days, today, weeks = 4)
        assertEquals(0.625f, h2.alpha(2), 1e-6f)
    }
}
