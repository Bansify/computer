package com.bansify.computer.apps.webapp

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bansify.computer.MainActivity
import com.bansify.computer.apps.store.GothwadStoreView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StoreIsolationTest {
    @Test fun packagedStoreLoadsAndRejectsWebNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            lateinit var store: GothwadStoreView
            try {
                scenario.onActivity { store = GothwadStoreView(it, scope) }
                var ready = false
                repeat(80) {
                    if (!ready) {
                        val latch = CountDownLatch(1)
                        scenario.onActivity {
                            store.webView.evaluateJavascript("typeof STORE_APPS !== 'undefined' && STORE_APPS.length > 0") {
                                ready = it == "true"
                                latch.countDown()
                            }
                        }
                        latch.await(1, TimeUnit.SECONDS)
                        if (!ready) SystemClock.sleep(250)
                    }
                }
                assertTrue("Packaged store assets did not load", ready)
                val latch = CountDownLatch(1)
                scenario.onActivity {
                    store.webView.evaluateJavascript("window.location.href='https://example.com/'; true") { latch.countDown() }
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS))
                SystemClock.sleep(500)
                scenario.onActivity { assertEquals("https://store.gothwad.invalid/index.html", store.webView.url) }
            } finally {
                scenario.onActivity { store.destroy() }
                scope.cancel()
            }
        }
    }
}
