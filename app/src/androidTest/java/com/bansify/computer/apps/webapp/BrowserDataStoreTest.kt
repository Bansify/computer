package com.bansify.computer.apps.webapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserDataStoreTest {
    @Test fun concurrentInstancesDoNotLoseUpdates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "browser-test-${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, "records.json")
        val stores = List(2) { BrowserDataStore(context, file) }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = (0 until 40).map { n -> pool.submit { start.await(); stores[n % 2].addBookmark("title", "https://example.com/$n") } }
            start.countDown()
            jobs.forEach { it.get(30, TimeUnit.SECONDS) }
            assertEquals(40, stores[0].getAll().size)
        } finally { pool.shutdownNow(); dir.deleteRecursively() }
    }

    @Test fun corruptRecordsAreNotOverwrittenByUpdates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "browser-test-${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, "records.json")
        try {
            file.writeText("{broken")
            BrowserDataStore(context, file).addBookmark("x", "https://example.com")
            assertEquals("{broken", file.readText())
            assertTrue(file.exists())
        } finally { dir.deleteRecursively() }
    }
}
