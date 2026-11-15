package com.gothwad.computer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.gothwad.computer.data.AppRepository
import com.gothwad.computer.data.BitmapSampling
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File

/** Emulator smoke coverage; does not replace a real TV remote or OEM Home test. */
@RunWith(AndroidJUnit4::class)
class HomeSmokeTest {
    @Test fun launcherAdvertisesHomeAndNormalLaunch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pm = context.packageManager
        fun hasOwnActivity(intent: Intent, flags: Int): Boolean =
            pm.queryIntentActivities(intent, flags).any {
                it.activityInfo.packageName == context.packageName && it.activityInfo.name == MainActivity::class.java.name
            }
        assertTrue(hasOwnActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY))
        assertTrue(hasOwnActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0))
    }

    @Test fun concurrentAppScansProduceConsistentSnapshots() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshots = withTimeout(60_000) {
            coroutineScope { List(4) { async { AppRepository.scan(context) } }.awaitAll() }
        }
        val packages = snapshots.first().map { it.pkg }.toSet()
        assertTrue(snapshots.all { it.map { app -> app.pkg }.toSet() == packages })
    }

    @Test fun corruptWallpaperDecodeReturnsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("corrupt_wallpaper", ".jpg", context.cacheDir)
        try {
            file.writeText("not an image")
            assertNull(BitmapSampling.decodeFile(file.absolutePath, 1920, 1080))
        } finally {
            file.delete()
        }
    }

    @Test fun repeatedStartupFailuresOfferRecoveryWithoutExitingOnBack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("startup_recovery", Context.MODE_PRIVATE)
            .edit().putInt("startup_crashes", 2).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var recoveryReady = false
                repeat(80) {
                    if (!recoveryReady) {
                        scenario.onActivity { recoveryReady = it.findViewById<View>(R.id.safe_retry) != null }
                        if (!recoveryReady) SystemClock.sleep(250)
                    }
                }
                assertTrue("Recovery screen did not load", recoveryReady)
                scenario.onActivity { activity ->
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.safe_retry).visibility)
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.safe_default_home).visibility)
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertFalse("Back must not finish recovery", activity.isFinishing)
                }
            }
        } finally {
            context.getSharedPreferences("startup_recovery", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun remoteCanOpenStartAndBackDoesNotExitHome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("startup_recovery", Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var desktopReady = false
            repeat(80) {
                if (!desktopReady) {
                    scenario.onActivity { desktopReady = it.findViewById<View>(R.id.btn_start) != null }
                    if (!desktopReady) SystemClock.sleep(250)
                }
            }
            assertTrue("Desktop did not load within 20 seconds", desktopReady)
            // Key injection puts the emulator in non-touch mode before testing focus.
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            scenario.onActivity { activity ->
                val start = activity.findViewById<View>(R.id.btn_start)
                val menu = activity.findViewById<View>(R.id.container_start_menu)
                assertTrue(start.requestFocus())
                start.performClick()
                assertEquals(View.VISIBLE, menu.visibility)
                val focused = requireNotNull(menu.findFocus()) { "Start menu should receive remote focus" }
                val candidate = (menu as ViewGroup).focusSearch(focused, View.FOCUS_UP)
                var parent = candidate?.parent
                while (parent != null && parent !== menu) parent = parent.parent
                assertTrue("Start menu focus should not escape behind the flyout", candidate === menu || parent === menu)
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(View.GONE, menu.visibility)
                activity.onBackPressedDispatcher.onBackPressed()
                assertFalse("Back must not finish the Home activity", activity.isFinishing)
            }
        }
    }
}
