package com.mundus.agenda

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.mundus.agenda.updater.UpdateCleanupReceiver
import com.mundus.agenda.updater.UpdateManager
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The self-updater leaves the downloaded APK in cache/updates after
 * install, doubling the installed footprint. Startup + MY_PACKAGE_REPLACED
 * must remove it. */
@RunWith(RobolectricTestRunner::class)
class UpdateCacheCleanupTest {

    private fun staleApk(): File {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.cacheDir, "updates")
        dir.mkdirs()
        return File(dir, "agenda-android-0.1.5-arm64-release.apk").apply { writeBytes(ByteArray(42)) }
    }

    @Test
    fun `sweep removes leftover update apks`() {
        val apk = staleApk()
        assertTrue(apk.exists())
        UpdateManager.sweepUpdateCache(ApplicationProvider.getApplicationContext())
        assertFalse(apk.exists())
        assertFalse(File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "updates").exists())
    }

    @Test
    fun `package replaced broadcast sweeps the update cache`() {
        val apk = staleApk()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        UpdateCleanupReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        // goAsync defers to a thread; poll briefly instead of sleeping.
        val deadline = System.currentTimeMillis() + 5000
        while (apk.exists() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertFalse(apk.exists())
    }

    @Test
    fun `unrelated broadcast keeps the update cache`() {
        val apk = staleApk()
        UpdateCleanupReceiver().onReceive(
            ApplicationProvider.getApplicationContext(),
            Intent(Intent.ACTION_BOOT_COMPLETED),
        )
        assertTrue(apk.exists())
    }
}
