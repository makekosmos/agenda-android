package com.mundus.agenda

import com.mundus.agenda.updater.ManifestLookup
import com.mundus.agenda.updater.UpdateFeed
import com.mundus.agenda.updater.UpdateManager
import com.mundus.agenda.updater.isNewer
import com.mundus.agenda.updater.parseUpdateFeed
import com.mundus.agenda.updater.parseUpdateManifest
import com.mundus.agenda.updater.sha256Hex
import com.mundus.agenda.updater.validApkFileName
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val YML = """
version: "0.1.2"
versionCode: 3
files:
  - url: agenda-android-0.1.2-arm64-release.apk
    sha256: aaaa
    size: 100
path: agenda-android-0.1.2-arm64-release.apk
sha256: bb1234
size: 42
releaseDate: '2026-10-11T00:00:00Z'
"""

private fun manifest(file: String = "agenda-0.1.2.apk") = """
{
  "schema": "mundus-release-manifest",
  "schema_version": 1,
  "product": "mundus",
  "version": "0.1.2",
  "platforms": {
    "win": { "file": "Mundus-Setup-0.1.2.exe", "size": 1, "sha512": "x" },
    "android": {
      "file": "$file",
      "url": "https://example.invalid/$file",
      "size": 42,
      "sha256": "BB1234",
      "sha512": "ignored",
      "versionCode": 3,
      "target": "aarch64-linux-android",
      "commit": "deadbeef",
      "released_at": "2026-10-11T00:00:00Z"
    }
  }
}
"""

/** 20-line HTTP/1.0 file server for feed tests (jdk.httpserver is not on
 * the unit-test classpath). */
private class StubServer {
    private val socket = ServerSocket(0)
    private val routes = mutableMapOf<String, ByteArray>()
    @Volatile private var running = true

    val base get() = "http://127.0.0.1:${socket.localPort}"

    init {
        thread {
            while (running) {
                val s = runCatching { socket.accept() }.getOrNull() ?: break
                thread {
                    s.use { conn ->
                        val path =
                            conn.getInputStream().bufferedReader().readLine()
                                ?.split(" ")?.getOrNull(1)
                        val body = routes[path]
                        val out = conn.getOutputStream()
                        if (body == null) {
                            out.write("HTTP/1.0 404\r\nContent-Length: 0\r\n\r\n".toByteArray())
                        } else {
                            out.write(
                                "HTTP/1.0 200\r\nContent-Length: ${body.size}\r\n\r\n"
                                    .toByteArray()
                            )
                            out.write(body)
                        }
                        out.flush()
                    }
                }
            }
        }
    }

    fun serve(path: String, body: ByteArray) {
        routes[path] = body
    }

    fun remove(path: String) = routes.remove(path)

    fun close() {
        running = false
        socket.close()
    }
}

@RunWith(RobolectricTestRunner::class)
class UpdateFeedTest {

    private lateinit var server: StubServer

    @Before
    fun startServer() {
        server = StubServer()
    }

    @After
    fun stopServer() = server.close()

    @Test
    fun `yml feed parses flat electron-builder shape`() {
        val f = parseUpdateFeed(YML)!!
        assertEquals("0.1.2", f.version)
        assertEquals(3, f.versionCode)
        assertEquals("agenda-android-0.1.2-arm64-release.apk", f.path)
        assertEquals("bb1234", f.sha256)
        assertEquals(42, f.size)
    }

    @Test
    fun `yml rejects junk and unsafe names`() {
        assertNull(parseUpdateFeed("version: 0.1.2"))
        assertNull(parseUpdateFeed(YML.replace("versionCode: 3", "")))
        assertFalse(validApkFileName("../evil.apk"))
        assertFalse(validApkFileName(".hidden.apk"))
        assertFalse(validApkFileName("app.apk/inner"))
        assertFalse(validApkFileName("app.exe"))
    }

    @Test
    fun `manifest android entry parses with additive keys`() {
        val r = parseUpdateManifest(manifest())
        assertTrue(r is ManifestLookup.Feed)
        val f = (r as ManifestLookup.Feed).feed
        assertEquals("0.1.2", f.version)
        assertEquals(3, f.versionCode)
        assertEquals("agenda-0.1.2.apk", f.path)
        assertEquals("bb1234", f.sha256)
        assertEquals("2026-10-11T00:00:00Z", f.releaseDate)
    }

    @Test
    fun `manifest without android is NoAndroid, garbage is Invalid`() {
        val noAndroid =
            """{"schema":"mundus-release-manifest","version":"0.1.2","platforms":{"win":{"file":"w.exe","size":1,"sha512":"x"}}}"""
        assertEquals(ManifestLookup.NoAndroid, parseUpdateManifest(noAndroid))
        assertEquals(ManifestLookup.Invalid, parseUpdateManifest("not json"))
        assertEquals(ManifestLookup.Invalid, parseUpdateManifest("{\"version\":\"1\"}"))
        // A manifest without `platforms` is malformed → Invalid → yml
        // fallback (release_manifest.rs:60-64), not NoAndroid.
        val noPlatforms =
            """{"schema":"mundus-release-manifest","version":"0.1.2"}"""
        assertEquals(ManifestLookup.Invalid, parseUpdateManifest(noPlatforms))
    }

    @Test
    fun `cert equality requires same non-empty signer set`() {
        assertTrue(UpdateManager.sameSignatures(setOf("AAA"), setOf("AAA")))
        assertFalse(UpdateManager.sameSignatures(setOf("AAA"), setOf("BBB")))
        assertFalse(UpdateManager.sameSignatures(emptySet(), emptySet()))
        assertFalse(UpdateManager.sameSignatures(setOf("AAA"), setOf("AAA", "BBB")))
    }

    @Test
    fun `versionCode compare is strict greater-than`() {
        val f = parseUpdateFeed(YML)!!
        assertTrue(isNewer(f, 2))
        assertFalse(isNewer(f, 3))
        assertFalse(isNewer(f, 4))
    }

    @Test
    fun `resolveFeed prefers manifest then falls back to yml`() {
        // Manifest path.
        server.serve("/manifest.json", manifest().toByteArray())
        assertEquals(3, UpdateManager.resolveFeed(server.base)!!.versionCode)
        // Manifest 404 → yml fallback (feed.rs:63-93).
        server.remove("/manifest.json")
        server.serve("/latest-android.yml", YML.toByteArray())
        assertEquals(3, UpdateManager.resolveFeed(server.base)!!.versionCode)
        // Neither → null.
        server.remove("/latest-android.yml")
        assertNull(UpdateManager.resolveFeed(server.base))
    }

    @Test
    fun `download verifies size and sha256 before rename`() {
        val payload = "fake-apk-bytes".toByteArray()
        val sha = sha256Hex(File.createTempFile("tmp", ".bin").also { it.writeBytes(payload) })
        val good = UpdateFeed("0.1.2", 3, "good.apk", sha, payload.size.toLong())
        val dir = java.nio.file.Files.createTempDirectory("upd").toFile()

        server.serve("/good.apk", payload)
        val file = UpdateManager.downloadApk(good, server.base, dir) {}
        assertEquals("good.apk", file.name)
        assertEquals(payload.size.toLong(), file.length())

        val bad = good.copy(path = "bad.apk", sha256 = "0".repeat(64))
        server.serve("/bad.apk", payload)
        val e =
            runCatching { UpdateManager.downloadApk(bad, server.base, dir) {} }
                .exceptionOrNull()
        assertNotNull(e)
        assertFalse(File(dir, "bad.apk").exists())
        assertFalse(File(dir, "bad.apk.part").exists())
    }
}
