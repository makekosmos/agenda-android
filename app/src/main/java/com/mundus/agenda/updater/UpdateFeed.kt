package com.mundus.agenda.updater

import java.io.File
import java.security.MessageDigest

/**
 * One entry of `latest-android.yml` — the flat electron-builder feed shape
 * Cortex publishes as `latest-mac.yml`/`latest.yml`
 * (desktop/scripts/release-manifest.mjs:227-240), with sha256 in place of
 * sha512 and `versionCode` added (Android's monotonic version).
 */
data class UpdateFeed(
    val version: String,
    val versionCode: Int,
    val path: String,
    val sha256: String,
    val size: Long,
    val releaseDate: String? = null,
) {
    /** Asset URL is rebuilt from the feed base + file name — mirroring
     * Cortex `feed.rs:97-117`, which ignores the feed's own absolute `url`. */
    fun apkUrl(feedBase: String): String =
        feedBase.trimEnd('/') + "/" + path
}

/**
 * Result of reading `manifest.json` — the Cortex-shaped primary feed
 * (KOS-350, release_manifest.rs:38-82). Additive keys are allowed at
 * schema_version 1 (release-manifest.mjs:34-35), so `platforms.android`
 * carries {file, url, size, sha256, sha512, versionCode, target, commit,
 * released_at}; the client reads file/size/sha256/versionCode and ignores
 * the rest, like release_manifest.rs ignores unknown keys.
 */
sealed interface ManifestLookup {
    data class Feed(val feed: UpdateFeed) : ManifestLookup

    /** Valid manifest without a `platforms.android` entry →
     * not-available (release_manifest.rs:67-68). */
    data object NoAndroid : ManifestLookup

    /** Not a parseable Mundus manifest → caller falls back to
     * latest-android.yml (feed.rs:63-93). */
    data object Invalid : ManifestLookup
}

fun parseUpdateManifest(json: String): ManifestLookup {
    val root = runCatching { org.json.JSONObject(json) }.getOrNull()
        ?: return ManifestLookup.Invalid
    if (root.optString("schema") != "mundus-release-manifest") {
        return ManifestLookup.Invalid
    }
    val version = root.optString("version").takeIf { it.isNotBlank() }
        ?: return ManifestLookup.Invalid
    val android = root.optJSONObject("platforms")?.optJSONObject("android")
        ?: return ManifestLookup.NoAndroid
    val file = android.optString("file")
    val sha = android.optString("sha256").lowercase()
    val size = android.optLong("size", 0)
    val versionCode = android.optInt("versionCode", 0)
    if (file.isBlank() || sha.isBlank() || size <= 0 || versionCode <= 0 ||
        !validApkFileName(file)
    ) {
        // An android entry we can't trust is unusable, not absent.
        return ManifestLookup.Invalid
    }
    return ManifestLookup.Feed(
        UpdateFeed(
            version = version,
            versionCode = versionCode,
            path = file,
            sha256 = sha,
            size = size,
            releaseDate = android.optString("released_at").ifBlank { null },
        )
    )
}

/**
 * Hand-rolled flat-yml parse, like Cortex `runtime/src/updater/manifest.rs`
 * (also hand-rolled — no yaml dependency anywhere). Reads only the keys the
 * client needs; unknown keys are ignored so the feed can grow additively.
 */
fun parseUpdateFeed(yaml: String): UpdateFeed? {
    var version: String? = null
    var versionCode: Int? = null
    var path: String? = null
    var sha256: String? = null
    var size: Long? = null
    var releaseDate: String? = null
    // First `files:` entry — fallback source of url/sha256/size like
    // Cortex manifest.rs reads files[0].
    var filesUrl: String? = null
    var filesSha: String? = null
    var filesSize: Long? = null
    var inFiles = false
    var filesSeen = false

    for (raw in yaml.lineSequence()) {
        val line = raw.trimEnd()
        if (line.isBlank() || line.trimStart().startsWith("#")) continue
        if (line.startsWith("-") || line.trimStart() != line) {
            if (inFiles && !filesSeen && line.trimStart().startsWith("-")) {
                filesSeen = true
                val (k, v) = splitKey(line.trimStart().removePrefix("-").trim()) ?: continue
                if (k == "url") filesUrl = v
            } else if (inFiles && filesSeen) {
                val (k, v) = splitKey(line.trim()) ?: continue
                when (k) {
                    "sha256" -> filesSha = v
                    "sha512" -> {} // sha256-only feed
                    "size" -> filesSize = v.toLongOrNull()
                    "url" -> if (filesUrl == null) filesUrl = v
                }
            }
            continue
        }
        val (k, v) = splitKey(line) ?: continue
        inFiles = k == "files"
        when (k) {
            "version" -> version = v
            "versionCode" -> versionCode = v.toIntOrNull()
            "path" -> path = v
            "sha256" -> sha256 = v
            "size" -> size = v.toLongOrNull()
            "releaseDate" -> releaseDate = v
        }
    }

    val file = path ?: filesUrl
    val sha = sha256 ?: filesSha
    val sz = size ?: filesSize
    if (version.isNullOrBlank() || versionCode == null || file.isNullOrBlank() ||
        sha.isNullOrBlank() || sz == null || sz <= 0
    ) return null
    if (!validApkFileName(file)) return null
    return UpdateFeed(version, versionCode, file, sha.lowercase(), sz, releaseDate)
}

private fun splitKey(line: String): Pair<String, String>? {
    val i = line.indexOf(':')
    if (i <= 0) return null
    val k = line.substring(0, i).trim()
    var v = line.substring(i + 1).trim()
    if (v.length >= 2 && v.first() == v.last() && (v.first() == '\'' || v.first() == '"')) {
        v = v.substring(1, v.length - 1)
    }
    if (k.isEmpty() || v.isEmpty()) return null
    return k to v
}

/** Same rule Cortex applies to manifest file names (`feed.rs:97-117`),
 * with `.apk` as the platform extension. */
fun validApkFileName(name: String): Boolean =
    name.isNotEmpty() &&
        !name.startsWith(".") &&
        name.endsWith(".apk") &&
        name.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }

/** Integer compare — Android's versionCode is the monotonic version; `>`
 * only, never downgrade (Cortex version.rs:22-30 strict semver `>`). */
fun isNewer(feed: UpdateFeed, currentVersionCode: Int): Boolean =
    feed.versionCode > currentVersionCode

fun sha256Hex(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
