package com.mundus.agenda

import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Golden dump produced by `cargo run -p agenda-core --bin seed-parity`
 * (scripts/parity-golden.sh): every smart list populated for the fixed
 * LocalDay it carries. */
internal fun loadGolden(json: Json) =
    json.parseToJsonElement(
            SeedParityTest::class.java.getResource("/parity-golden.json")!!.readText()
        )
        .jsonObject

internal fun goldenDay(golden: kotlinx.serialization.json.JsonObject) =
    golden["today"]!!.jsonPrimitive.content to
        golden["utcOffsetSeconds"]!!.jsonPrimitive.content.toInt()

/**
 * A fresh temp-dir engine seeded with every golden object — the same setup
 * SeedParityTest proves against. Caller owns `close()` and dir cleanup.
 */
internal fun seededRepository(
    json: Json,
    golden: kotlinx.serialization.json.JsonObject,
): Pair<EngineAgendaRepository, String> {
    val dataDir = Files.createTempDirectory("agenda-write-test").toString()
    val repository = EngineAgendaRepository(dataDir) { goldenDay(golden) }
    // `com.kosmos.agenda.references` is a non-canonical type the desktop DB
    // carries from before the canonical registry; a fresh phone DB must
    // register it before upsert.
    repository.call(
        "upsert_object_type",
        buildJsonObject {
            put(
                "object_type",
                buildJsonObject {
                    put("id", "com.kosmos.agenda.references")
                    put("name", "Agenda references")
                    put("schemaJson", "{}")
                    put("uiSchemaJson", "{}")
                    put("createdAt", golden["today"]!!.jsonPrimitive.content)
                    put("updatedAt", golden["today"]!!.jsonPrimitive.content)
                    put("systemLocked", false)
                },
            )
        },
    )
    for (obj in golden["objects"]!!.jsonArray) {
        repository.call("upsert_object", buildJsonObject { put("object", obj) })
    }
    return repository to dataDir
}

internal suspend fun board(repository: EngineAgendaRepository): Board {
    val state = withTimeout(15_000) { repository.boardFlow().first() }
    return (state as? BoardState.Ready)?.board
        ?: throw AssertionError("boardFlow emitted $state, expected Ready")
}

internal fun Board.ids(list: SmartList) = list(list).map { it.id }
