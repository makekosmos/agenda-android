package com.mundus.agenda

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `get_sync_snapshot` peers[] entry (ark-core service/sync.rs). */
@Serializable
data class SyncPeer(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val status: String? = null,
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class SyncLocalDevice(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String? = null,
)

/** `incoming_pairing_requests[]` — a device asking us to accept it. */
@Serializable
data class IncomingPairing(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String? = null,
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

/** `outgoing_pairing` — our pending/finished dial-out to a peer. */
@Serializable
data class OutgoingPairing(
    val status: String,
    @SerialName("expires_in_ms") val expiresInMs: Long? = null,
)

/** `get_sync_snapshot` data payload. */
@Serializable
data class SyncSnapshot(
    val running: Boolean = false,
    val transport: String? = null,
    val peers: List<SyncPeer> = emptyList(),
    @SerialName("local_device") val localDevice: SyncLocalDevice? = null,
    @SerialName("incoming_pairing_requests")
    val incomingPairingRequests: List<IncomingPairing> = emptyList(),
    @SerialName("outgoing_pairing") val outgoingPairing: OutgoingPairing? = null,
)

/**
 * Device sync + pairing surface (desktop Manager sync view ops,
 * manager-gpui views/sync.rs + sync/pairing.rs + modals.rs pairing
 * prompt). The engine underneath is engine-mobile's UniFFI surface;
 * accept/decline/cancel/disconnect go through the generic `call` op
 * tunnel because the pinned ark-core exposes them only as ops.
 */
interface SyncApi {
    /** `start_sync` — engine-mobile fills space/device defaults. */
    suspend fun startSync(deviceName: String)

    /** `stop_sync` + `start_sync`: redials now instead of waiting out backoff. */
    suspend fun restartSync(deviceName: String)

    /** `get_sync_snapshot`. */
    suspend fun syncSnapshot(): SyncSnapshot

    /** `show_pairing_code` — escalates bind and returns our iroh ticket. */
    suspend fun showPairingCode(): String?

    /** `get_own_iroh_ticket` — passive read, null until iroh runs. */
    suspend fun ownIrohTicket(): String?

    /** `connect_with_pairing_code` — returns the outgoing status. */
    suspend fun connectWithPairingCode(code: String): String

    /** `accept_pairing {device_id}` — consent to an incoming request. */
    suspend fun acceptPairing(deviceId: String)

    /** `decline_pairing {device_id}`. */
    suspend fun declinePairing(deviceId: String)

    /** `cancel_pairing {}` — drop our pending outgoing attempt. */
    suspend fun cancelPairing()

    /** `disconnect_peer {device_id}`. */
    suspend fun disconnectPeer(deviceId: String)
}

private fun SyncSnapshot.peerOnline() = peers.any { it.status == "online" }

/**
 * One background pass for the periodic worker. The engine has no "caught
 * up" event and the OS freezes the process once the worker returns, so the
 * pass redials, waits for a paired device to come online, then stays until
 * [changes] has been quiet for [quiet] (or [cap] runs out). Returns whether
 * a device was reached; with none online there is nobody to sync with.
 */
@OptIn(FlowPreview::class)
suspend fun SyncApi.syncPass(
    deviceName: String,
    changes: Flow<*>,
    connectTimeout: Duration = 1.minutes,
    quiet: Duration = 5.seconds,
    cap: Duration = 90.seconds,
    poll: Duration = 1.seconds,
): Boolean {
    val snapshot = syncSnapshot()
    when {
        !snapshot.running -> startSync(deviceName)
        !snapshot.peerOnline() -> restartSync(deviceName)
    }
    withTimeoutOrNull(connectTimeout) {
        while (!syncSnapshot().peerOnline()) delay(poll)
    } ?: return false
    withTimeoutOrNull(cap) { changes.debounce(quiet).first() }
    return true
}

/** Background sync intervals offered in settings, minutes; each divides an hour. */
val SYNC_INTERVALS = listOf(5, 15, 30, 60)

const val DEFAULT_SYNC_INTERVAL = 15

/**
 * The next background sync slot strictly after [now]: wall-clock multiples
 * of [intervalMinutes] from local midnight (10:00, 10:15, ...), so every
 * device on the same interval wakes at the same minute and finds the
 * others online.
 */
fun nextSyncSlot(now: java.time.ZonedDateTime, intervalMinutes: Int): java.time.ZonedDateTime {
    val local = now.toLocalDateTime()
    val minuteOfDay = local.hour * 60 + local.minute
    val next = (minuteOfDay / intervalMinutes + 1) * intervalMinutes
    return local.toLocalDate().atStartOfDay().plusMinutes(next.toLong()).atZone(now.zone)
}
