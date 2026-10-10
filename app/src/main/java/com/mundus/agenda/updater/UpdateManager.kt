package com.mundus.agenda.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.mundus.agenda.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mirrors Cortex `state.rs` update states. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val feed: UpdateFeed) : UpdateState
    data class Downloading(val feed: UpdateFeed, val percent: Int) : UpdateState
    data class ReadyToInstall(val feed: UpdateFeed, val file: File) : UpdateState
    data object NotAvailable : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * The Android mirror of the Cortex updater service
 * (runtime/src/updater/service.rs): check the feed, download the APK,
 * verify size + sha256 + signing certificate, hand it to the system
 * installer. Unlike desktop, the OS owns the actual install — the user
 * confirms in the system dialog.
 */
class UpdateManager(
    private val context: Context,
    private val feedBase: String = BuildConfig.UPDATE_FEED_BASE,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** Session snooze — desktop «Позже» (update_overlay.rs:230-234). */
    var snoozedVersion by mutableStateOf<String?>(null)
        private set

    private val prefs by lazy { context.getSharedPreferences("agenda_updates", Context.MODE_PRIVATE) }
    private val updatesDir by lazy { File(context.cacheDir, "updates") }

    /** Cortex `run_startup_check_after_grace` — one automatic check ~5 s
     * after start (service.rs:275-283). */
    fun checkOnStart() {
        scope.launch {
            delay(5000)
            check()
        }
    }

    /** Resume-time re-check, throttled. Cortex has no periodic timer; a
     * long-lived mobile app gets a cheap >4h-stale refresh instead. */
    fun checkIfStale() {
        val last = prefs.getLong("last_check", 0L)
        if (System.currentTimeMillis() - last > STALE_MS) check()
    }

    fun check() {
        if (state is UpdateState.Checking) return
        scope.launch { doCheck() }
    }

    private suspend fun doCheck() {
        if (feedBase.isBlank()) return
        state = UpdateState.Checking
        try {
            val feed = withContext(io) { resolveFeed(feedBase) }
            prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
            state =
                if (feed != null && isNewer(feed, BuildConfig.VERSION_CODE)) {
                    UpdateState.Available(feed)
                } else {
                    UpdateState.NotAvailable
                }
        } catch (e: Exception) {
            state = UpdateState.Error(e.message ?: "check failed")
        }
    }

    /** Download + verify, ending ReadyToInstall — caller launches
     * [installIntent]. Mirrors download.rs: hash-verify the `.part` file
     * before renaming it over the final name. */
    fun download(feed: UpdateFeed) {
        if (state is UpdateState.Downloading) return
        scope.launch {
            state = UpdateState.Downloading(feed, 0)
            try {
                val file =
                    withContext(io) {
                        downloadApk(feed, feedBase, updatesDir) { p ->
                            scope.launch { state = UpdateState.Downloading(feed, p) }
                        }
                    }
                state =
                    if (sameSigner(file)) UpdateState.ReadyToInstall(feed, file)
                    else {
                        file.delete()
                        UpdateState.Error("Сертификат обновления не совпадает")
                    }
            } catch (e: Exception) {
                state = UpdateState.Error(e.message ?: "download failed")
            }
        }
    }

    fun snooze(version: String) {
        snoozedVersion = version
    }

    fun canRequestInstalls(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** The unknown-sources permission screen; after granting, state stays
     * ReadyToInstall and the user taps install again. */
    fun openInstallPermissionSettings() {
        val i =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    fun installIntent(file: File): Intent {
        val uri =
            FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The authenticity check sha256 can't give: the downloaded APK must be
     * signed by the same certificate as the installed app. */
    internal fun sameSigner(apk: File): Boolean {
        val pm = context.packageManager
        val flags =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES.toLong()
            else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES.toLong()
        val apkInfo = pm.getPackageArchiveInfo(apk.absolutePath, flags.toInt()) ?: return false
        val mine = pm.getPackageInfo(context.packageName, flags.toInt()) ?: return false
        val a = signatureBytes(apkInfo)
        val b = signatureBytes(mine)
        return a.isNotEmpty() && a == b
    }

    private fun signatureBytes(info: android.content.pm.PackageInfo): Set<String> {
        val sigs =
            if (Build.VERSION.SDK_INT >= 28) {
                val s = info.signingInfo ?: return emptySet()
                (s.apkContentsSigners ?: s.signingCertificateHistory).toList()
            } else {
                @Suppress("DEPRECATION") info.signatures?.toList() ?: emptyList()
            }
        return sigs.map { it.toCharsString() }.toSet()
    }

    companion object {
        const val STALE_MS = 4L * 60 * 60 * 1000

        /** Cortex feed.rs:15,63-93 order: GET {base}/manifest.json first;
         * on fetch/parse failure fall back to the legacy yml channel. A
         * valid manifest without platforms.android means not-available. */
        internal fun resolveFeed(feedBase: String): UpdateFeed? {
            val base = feedBase.trimEnd('/')
            httpGet("$base/manifest.json")?.let { body ->
                when (val r = parseUpdateManifest(body)) {
                    is ManifestLookup.Feed -> return r.feed
                    ManifestLookup.NoAndroid -> return null
                    ManifestLookup.Invalid -> Unit
                }
            }
            return httpGet("$base/latest-android.yml")?.let(::parseUpdateFeed)
        }

        internal fun httpGet(url: String): String? {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000 // engine-base http.rs client timeout
            conn.readTimeout = 30_000
            return try {
                if (conn.responseCode != 200) null
                else conn.inputStream.bufferedReader().readText()
            } catch (_: Exception) {
                null
            } finally {
                conn.disconnect()
            }
        }

        internal fun downloadApk(
            feed: UpdateFeed,
            feedBase: String,
            dir: File,
            onProgress: (Int) -> Unit,
        ): File {
            dir.mkdirs()
            // Sweep like Cortex cleanup.rs: only the pending file survives.
            dir.listFiles()?.forEach { it.delete() }
            val part = File(dir, feed.path + ".part")
            val final = File(dir, feed.path)
            val conn = URL(feed.apkUrl(feedBase)).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            try {
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            onProgress((done * 100 / feed.size).toInt().coerceIn(0, 100))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (part.length() != feed.size) {
                part.delete()
                error("size mismatch: ${part.length()} != ${feed.size}")
            }
            if (sha256Hex(part) != feed.sha256) {
                part.delete()
                error("sha256 mismatch")
            }
            part.renameTo(final)
            return final
        }
    }
}
