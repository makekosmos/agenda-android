package com.mundus.agenda

import com.mundus.engine.ChangeListener
import com.mundus.engine.MobileEngine
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNotNull
import org.junit.Test

/**
 * End-to-end sync test: a host-JVM `MobileEngine` ("phone") pairs with a
 * live desktop `mundus-engine` over iroh/loopback and tasks flow both ways.
 *
 * Gated by `SYNC_E2E_DESKTOP_LOCK` — path to the desktop's
 * `engine.lock.json`. Plain `./gradlew :core:test` leaves it unset and the
 * test skips; `scripts/sync-e2e.sh` launches the desktop engine and sets it.
 */
class SyncE2eTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient.newHttpClient()

    private lateinit var lockPath: Path
    private var httpPort: Int = 0
    private lateinit var authToken: String
    private var desktopPid: Long = 0

    private var lastDesktopSnapshot: String = "<none>"
    private var lastPhoneSnapshot: String = "<none>"

    private fun log(message: String) {
        System.out.println("[sync-e2e ${Instant.now()}] $message")
        System.out.flush()
    }

    // ------------------------------------------------------------------
    // Desktop /v1/rpc client (same shape as agenda-gpui transport.rs)
    // ------------------------------------------------------------------

    private fun readLock() {
        val text = Files.readString(lockPath)
        val obj = json.parseToJsonElement(text).jsonObject
        httpPort = obj.getValue("http_port").jsonPrimitive.content.toInt()
        authToken = obj.getValue("auth_token").jsonPrimitive.content
        desktopPid = obj.getValue("pid").jsonPrimitive.content.toLong()
        log("desktop lock: pid=$desktopPid http_port=$httpPort")
    }

    private fun desktopRpc(op: String, params: JsonObject): JsonElement {
        val body = buildJsonObject {
            params.forEach { (k, v) -> put(k, v) }
            put("operation", op)
            put("_req_id", UUID.randomUUID().toString())
        }
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:$httpPort/v1/rpc"))
                .header("Authorization", "Bearer $authToken")
                .header("X-Kosmos-Api-Version", "1.0.0")
                .header("X-Kosmos-Client-Pid", ProcessHandle.current().pid().toString())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        val parsed = json.parseToJsonElement(response.body()).jsonObject
        if (parsed["ok"]?.jsonPrimitive?.boolean != true) {
            fail("desktop rpc $op failed (http ${response.statusCode()}): ${parsed["error"]} / raw=${response.body()}")
        }
        return parsed["data"] ?: JsonNull
    }

    // ------------------------------------------------------------------
    // Phone-side helpers
    // ------------------------------------------------------------------

    private fun phoneCall(engine: MobileEngine, op: String, params: JsonObject): JsonElement {
        val raw = engine.call(op, params.toString())
        val parsed = json.parseToJsonElement(raw).jsonObject
        if (parsed["ok"]?.jsonPrimitive?.boolean != true) {
            fail("phone op $op failed: ${parsed["error"]} / raw=$raw")
        }
        return parsed["data"] ?: JsonNull
    }

    private fun phoneSnapshot(engine: MobileEngine): JsonObject {
        val raw = engine.syncStatus()
        val parsed = json.parseToJsonElement(raw).jsonObject
        lastPhoneSnapshot = raw
        return (parsed["data"] as? JsonObject) ?: JsonObject(emptyMap())
    }

    private fun desktopSnapshot(): JsonObject {
        val data = desktopRpc("get_sync_snapshot", buildJsonObject {})
        lastDesktopSnapshot = data.toString()
        return data as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun phoneTaskObjects(engine: MobileEngine): JsonArray =
        phoneCall(
                engine,
                "list_objects_by_type",
                buildJsonObject { put("type_id", "com.kosmos.task") },
            )
            .jsonArray

    private fun desktopTaskObjects(): JsonArray =
        desktopRpc(
                "list_objects_by_type",
                buildJsonObject { put("type_id", "com.kosmos.task") },
            )
            .jsonArray

    private fun todoTitleOf(engineObjectJson: String): String? {
        val todo = json.parseToJsonElement(taskFromEngineObject(engineObjectJson)).jsonObject
        return todo["title"]?.jsonPrimitive?.content
    }

    private fun todoIsCompletedOf(engineObjectJson: String): Boolean {
        val todo = json.parseToJsonElement(taskFromEngineObject(engineObjectJson)).jsonObject
        return todo["is_completed"]?.jsonPrimitive?.boolean == true
    }

    private fun buildTaskObjectJson(
        title: String,
        id: String = "task-${UUID.randomUUID()}",
    ): Pair<String, String> {
        val todoJson = newTaskJson(id, title)
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val objectJson =
            taskToEngineObject(
                todoJson,
                null,
                null,
                now.toString(),
                LocalDate.now(zone).toString(),
                zone.rules.getOffset(now).totalSeconds,
            )
        return todoJson to objectJson
    }

    private fun freeTcpPort(): Int = ServerSocket(0).use { it.localPort }

    // ------------------------------------------------------------------
    // Poll helper
    // ------------------------------------------------------------------

    private fun pollUntil(
        what: String,
        timeoutMs: Long = 30_000,
        intervalMs: Long = 400,
        condition: () -> Boolean,
    ) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        var lastError: String? = null
        while (System.nanoTime() < deadline) {
            try {
                if (condition()) {
                    log("OK: $what")
                    return
                }
            } catch (e: Exception) {
                lastError = e.toString()
            }
            Thread.sleep(intervalMs)
        }
        fail(
            "timeout waiting for: $what" +
                (lastError?.let { " (last error: $it)" } ?: "") +
                "\nlast desktop snapshot: $lastDesktopSnapshot" +
                "\nlast phone snapshot: $lastPhoneSnapshot"
        )
    }

    // ------------------------------------------------------------------
    // The scenario
    // ------------------------------------------------------------------

    @Test
    fun phoneDesktopSyncE2e() {
        val lock = System.getenv("SYNC_E2E_DESKTOP_LOCK")
        assumeNotNull("SYNC_E2E_DESKTOP_LOCK unset — run scripts/sync-e2e.sh", lock)
        lockPath = Path.of(lock)
        readLock()

        val phoneDir = Files.createTempDirectory("sync-e2e-phone").toString()
        log("phone data dir: $phoneDir")

        var phone = MobileEngine.open(phoneDir)
        val events = LinkedBlockingQueue<String>()
        var subscription =
            phone.subscribe(
                object : ChangeListener {
                    override fun onChange(eventJson: String) {
                        events.offer(eventJson)
                    }
                }
            )

        try {
            // --- Pairing --------------------------------------------------
            val phonePort = freeTcpPort()
            log("phone startSync on port $phonePort")
            val startResult =
                phone.startSync(
                    """{"device_name":"e2e-phone","bind":"loopback",""" +
                        """"discovery_enabled":false,"port":$phonePort}"""
                )
            log("startSync -> $startResult")

            var ticket: String? = null
            pollUntil("desktop iroh ticket", 45_000) {
                val data = desktopRpc("get_own_iroh_ticket", buildJsonObject {})
                ticket = data.jsonPrimitive.takeIf { it.isString }?.content
                ticket != null
            }
            log("desktop ticket: ${ticket!!.take(24)}...")

            val connectRaw = phone.connectWithPairingCode(ticket!!)
            log("connectWithPairingCode -> $connectRaw")
            val connectData = json.parseToJsonElement(connectRaw).jsonObject["data"]
            assertNotNull("connectWithPairingCode returned no data: $connectRaw", connectData)

            var pairingDeviceId: String? = null
            pollUntil("desktop sees incoming pairing request", 30_000) {
                val snapshot = desktopSnapshot()
                val requests = snapshot["incoming_pairing_requests"]?.jsonArray
                pairingDeviceId =
                    requests?.firstOrNull()?.jsonObject?.get("device_id")?.jsonPrimitive?.content
                pairingDeviceId != null
            }
            log("accepting pairing from $pairingDeviceId")
            val accept = desktopRpc(
                "accept_pairing",
                buildJsonObject { put("device_id", pairingDeviceId) },
            )
            assertEquals(
                "accept_pairing did not connect: $accept",
                "connected",
                accept.jsonObject["status"]?.jsonPrimitive?.content,
            )

            pollUntil("phone reports connected pairing", 30_000) {
                val snapshot = phoneSnapshot(phone)
                val outgoing = snapshot["outgoing_pairing"]?.jsonObject
                val outgoingOk =
                    outgoing?.get("status")?.jsonPrimitive?.content == "connected"
                val peerOk =
                    snapshot["peers"]?.jsonArray?.any {
                        it.jsonObject["status"]?.jsonPrimitive?.content == "connected"
                    } == true
                outgoingOk || peerOk
            }
            log("paired")

            // --- (a) phone -> desktop ------------------------------------
            val (milkTodo, milkObject) = buildTaskObjectJson("Купить молоко")
            val milkId =
                json.parseToJsonElement(milkTodo).jsonObject["id"]!!.jsonPrimitive.content
            phoneCall(
                phone,
                "upsert_object",
                buildJsonObject { put("object", json.parseToJsonElement(milkObject)) },
            )
            log("phone upserted $milkId (Купить молоко)")
            pollUntil("desktop sees 'Купить молоко'", 45_000) {
                desktopTaskObjects().any { todoTitleOf(it.toString()) == "Купить молоко" }
            }

            // --- (b) desktop -> phone ------------------------------------
            val (_, breadObject) = buildTaskObjectJson("Купить хлеб")
            val breadObjectId =
                json.parseToJsonElement(breadObject).jsonObject["id"]!!.jsonPrimitive.content
            desktopRpc(
                "upsert_object",
                buildJsonObject { put("object", json.parseToJsonElement(breadObject)) },
            )
            log("desktop upserted $breadObjectId (Купить хлеб)")

            pollUntil("phone sees 'Купить хлеб'", 45_000) {
                phoneTaskObjects(phone).any { todoTitleOf(it.toString()) == "Купить хлеб" }
            }

            var sawBreadEvent = false
            pollUntil("change listener saw object_upserted $breadObjectId", 30_000) {
                while (true) {
                    val event = events.poll() ?: break
                    if (event.contains("\"object_upserted\"") && event.contains(breadObjectId)) {
                        sawBreadEvent = true
                    }
                }
                sawBreadEvent
            }

            // --- (c) phone completes the task ----------------------------
            val milkEngineObject =
                phoneTaskObjects(phone).firstOrNull {
                    it.jsonObject["id"]?.jsonPrimitive?.content == milkId ||
                        todoTitleOf(it.toString()) == "Купить молоко"
                }
            assertNotNull("phone lost its own milk task object", milkEngineObject)
            val beforeTodoJson = taskFromEngineObject(milkEngineObject!!.toString())
            val doneTodo = buildJsonObject {
                json.parseToJsonElement(beforeTodoJson).jsonObject.forEach { (k, v) -> put(k, v) }
                put("is_completed", JsonPrimitive(true))
                put("status", JsonPrimitive("done"))
            }
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val doneObject =
                taskToEngineObject(
                    doneTodo.toString(),
                    milkEngineObject.toString(),
                    beforeTodoJson,
                    now.toString(),
                    LocalDate.now(zone).toString(),
                    zone.rules.getOffset(now).totalSeconds,
                )
            phoneCall(
                phone,
                "upsert_object",
                buildJsonObject { put("object", json.parseToJsonElement(doneObject)) },
            )
            log("phone completed $milkId")
            pollUntil("desktop sees milk task completed", 45_000) {
                desktopTaskObjects().any {
                    (it.jsonObject["id"]?.jsonPrimitive?.content == milkId ||
                        todoTitleOf(it.toString()) == "Купить молоко") &&
                        (it.jsonObject["propsJson"]?.jsonObject?.get("status")
                            ?.jsonPrimitive
                            ?.content == "done" ||
                            todoIsCompletedOf(it.toString()))
                }
            }

            // --- (d) process death / reopen ------------------------------
            subscription.close()
            phone.shutdown()
            log("phone engine shut down (simulated death); reopening same dir")

            phone = MobileEngine.open(phoneDir)
            events.clear()
            subscription =
                phone.subscribe(
                    object : ChangeListener {
                        override fun onChange(eventJson: String) {
                            events.offer(eventJson)
                        }
                    }
                )

            val (_, cheeseObject) = buildTaskObjectJson("Купить сыр")
            phoneCall(
                phone,
                "upsert_object",
                buildJsonObject { put("object", json.parseToJsonElement(cheeseObject)) },
            )
            log("phone upserted 'Купить сыр' while offline")

            val restartPort = freeTcpPort()
            phone.startSync(
                """{"device_name":"e2e-phone","bind":"loopback",""" +
                    """"discovery_enabled":false,"port":$restartPort}"""
            )
            val reconnectRaw = phone.connectWithPairingCode(ticket!!)
            log("re-dial after death -> $reconnectRaw")

            pollUntil("desktop sees 'Купить сыр' after phone restart", 45_000) {
                desktopTaskObjects().any { todoTitleOf(it.toString()) == "Купить сыр" }
            }
            log("E2E PASS")
        } finally {
            try {
                subscription.close()
            } catch (_: Exception) {}
            try {
                phone.shutdown()
            } catch (_: Exception) {}
            Files.walk(Path.of(phoneDir)).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { it.toFile().delete() }
            }
        }
    }
}
