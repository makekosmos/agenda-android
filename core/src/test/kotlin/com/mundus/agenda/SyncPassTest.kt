package com.mundus.agenda

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPassTest {
    private class FakeSync(var running: Boolean, private val onlineAfterPolls: Int?) : SyncApi {
        val calls = mutableListOf<String>()
        private var polls = 0

        override suspend fun startSync(deviceName: String) {
            calls += "start"
            running = true
        }

        override suspend fun restartSync(deviceName: String) {
            calls += "restart"
        }

        override suspend fun syncSnapshot(): SyncSnapshot {
            val online = onlineAfterPolls != null && polls++ >= onlineAfterPolls
            val peer = SyncPeer("desk", status = if (online) "online" else "offline")
            return SyncSnapshot(running = running, peers = listOf(peer))
        }

        override suspend fun showPairingCode(): String? = null
        override suspend fun ownIrohTicket(): String? = null
        override suspend fun connectWithPairingCode(code: String) = "idle"
        override suspend fun acceptPairing(deviceId: String) {}
        override suspend fun declinePairing(deviceId: String) {}
        override suspend fun cancelPairing() {}
        override suspend fun disconnectPeer(deviceId: String) {}
    }

    private suspend fun SyncApi.pass(changes: kotlinx.coroutines.flow.Flow<*> = flowOf(Unit)) =
        syncPass(
            "phone",
            changes,
            connectTimeout = 300.milliseconds,
            quiet = 50.milliseconds,
            cap = 2000.milliseconds,
            poll = 10.milliseconds,
        )

    @Test
    fun startsStoppedSyncAndReportsReachedPeer() = runBlocking {
        val sync = FakeSync(running = false, onlineAfterPolls = 3)
        assertTrue(sync.pass())
        assertEquals(listOf("start"), sync.calls)
    }

    @Test
    fun redialsWhenNoPeerOnlineAndGivesUpAfterTimeout() = runBlocking {
        val sync = FakeSync(running = true, onlineAfterPolls = null)
        assertFalse(sync.pass())
        assertEquals(listOf("restart"), sync.calls)
    }

    @Test
    fun leavesConnectedSyncAlone() = runBlocking {
        val sync = FakeSync(running = true, onlineAfterPolls = 0)
        assertTrue(sync.pass())
        assertEquals(emptyList<String>(), sync.calls)
    }

    @Test
    fun staysWhileChangesKeepArriving() = runBlocking {
        val sync = FakeSync(running = true, onlineAfterPolls = 0)
        val busy =
            flow {
                repeat(20) {
                    emit(it)
                    delay(20)
                }
            }
        val start = TimeSource.Monotonic.markNow()
        assertTrue(sync.pass(busy))
        val took = start.elapsedNow()
        assertTrue("returned during the burst after $took", took >= 400.milliseconds)
        assertTrue("outlived the quiet window: $took", took < 1500.milliseconds)
    }
}
