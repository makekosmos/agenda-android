package com.mundus.agenda

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
