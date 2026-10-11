package com.mundus.agenda

import com.mundus.agenda.ui.UpdateAction
import com.mundus.agenda.ui.updateCardModel
import com.mundus.agenda.updater.UpdateFeed
import com.mundus.agenda.updater.UpdateState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the updates.rs:13-140 card mapping: one action button per state
 * («Проверить обновления» vs «Установить обновление»), busy states
 * disabled, download percent shown only while downloading. */
class UpdateCardModelTest {
    private val feed =
        UpdateFeed(
            version = "0.1.3",
            versionCode = 4,
            path = "agenda-0.1.3.apk",
            sha256 = "ab12",
            size = 100,
        )

    @Test
    fun idle() {
        val m = updateCardModel(UpdateState.Idle, "0.1.2")
        assertEquals("0.1.2", m.versionLine)
        assertEquals("Не проверялось", m.statusLine)
        assertEquals("Проверить обновления", m.buttonLabel)
        assertTrue(m.buttonEnabled)
        assertEquals(UpdateAction.Check, m.action)
        assertNull(m.downloadPercent)
    }

    @Test
    fun checking() {
        val m = updateCardModel(UpdateState.Checking, "0.1.2")
        assertEquals("Проверяем…", m.statusLine)
        assertEquals("Проверяем…", m.buttonLabel)
        assertFalse(m.buttonEnabled)
    }

    @Test
    fun notAvailable() {
        val m = updateCardModel(UpdateState.NotAvailable, "0.1.2")
        assertEquals("Актуальная версия", m.statusLine)
        assertEquals("Проверить обновления", m.buttonLabel)
        assertEquals(UpdateAction.Check, m.action)
    }

    @Test
    fun available() {
        val m = updateCardModel(UpdateState.Available(feed), "0.1.2")
        assertEquals("0.1.2 → 0.1.3", m.versionLine)
        assertEquals("Доступно обновление 0.1.3", m.statusLine)
        assertEquals("Установить обновление", m.buttonLabel)
        assertTrue(m.buttonEnabled)
        assertEquals(UpdateAction.DownloadOrInstall, m.action)
    }

    @Test
    fun downloading() {
        val m = updateCardModel(UpdateState.Downloading(feed, 42), "0.1.2")
        assertEquals("0.1.2 → 0.1.3", m.versionLine)
        assertEquals("Скачивание 42%", m.statusLine)
        assertEquals(42, m.downloadPercent)
        assertFalse(m.buttonEnabled)
        assertEquals(UpdateAction.DownloadOrInstall, m.action)
    }

    @Test
    fun readyToInstall() {
        val m = updateCardModel(UpdateState.ReadyToInstall(feed, File("/tmp/x.apk")), "0.1.2")
        assertEquals("0.1.2 → 0.1.3", m.versionLine)
        assertEquals("Готово к установке", m.statusLine)
        assertEquals("Установить обновление", m.buttonLabel)
        assertTrue(m.buttonEnabled)
    }

    @Test
    fun error() {
        val m = updateCardModel(UpdateState.Error("Не удалось проверить обновления"), "0.1.2")
        assertEquals("Не удалось проверить обновления", m.statusLine)
        assertEquals("Проверить обновления", m.buttonLabel)
        assertTrue(m.buttonEnabled)
        assertTrue(m.isError)
        assertEquals(UpdateAction.Check, m.action)
    }
}
