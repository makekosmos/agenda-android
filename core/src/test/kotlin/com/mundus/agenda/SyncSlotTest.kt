package com.mundus.agenda

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncSlotTest {
    private val zone = ZoneId.of("Europe/Moscow")

    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone)

    @Test
    fun alignsToWallClockNotToLastRun() {
        assertEquals(at("2026-10-12T10:15"), nextSyncSlot(at("2026-10-12T10:07:42"), 15))
        assertEquals(at("2026-10-12T10:05"), nextSyncSlot(at("2026-10-12T10:00:01"), 5))
        assertEquals(at("2026-10-12T11:00"), nextSyncSlot(at("2026-10-12T10:31"), 30))
        assertEquals(at("2026-10-12T11:00"), nextSyncSlot(at("2026-10-12T10:59:59"), 60))
    }

    @Test
    fun slotItselfSchedulesTheFollowingOne() {
        assertEquals(at("2026-10-12T10:30"), nextSyncSlot(at("2026-10-12T10:15"), 15))
    }

    @Test
    fun rollsOverMidnight() {
        assertEquals(at("2026-10-13T00:00"), nextSyncSlot(at("2026-10-12T23:50"), 15))
    }

    @Test
    fun keepsWallClockAcrossDaylightSavingShift() {
        val berlin = ZoneId.of("Europe/Berlin")
        // 2026-03-29 02:00 jumps to 03:00: the 03:15 slot stays at 03:15.
        val before = ZonedDateTime.of(java.time.LocalDateTime.parse("2026-03-29T03:05"), berlin)
        assertEquals(java.time.LocalTime.of(3, 15), nextSyncSlot(before, 15).toLocalTime())
    }
}
