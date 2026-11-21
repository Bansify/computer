package com.bansify.computer

import android.app.Application
import android.content.Context
import android.util.Log
import com.bansify.computer.data.StartupDiagnostics
import com.bansify.computer.data.StartupIssue

/**
 * Custom Application class for Gothwad Computer PC Emulator.
 */
class GothwadApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(runCatching { com.bansify.computer.data.DpiHelper.wrapContext(base) }.getOrDefault(base))
    }

    override fun onCreate() {
        super.onCreate()
        val currentProc = com.bansify.computer.data.ProcessHelper.currentProcessName()
        // Install as early as possible, before optional display configuration.
        if (currentProc.isEmpty() || currentProc == packageName) setupCrashLogging()
        Log.i(TAG, "GothwadApplication initialized in process: $currentProc")
        runCatching {
            if (com.bansify.computer.data.DpiHelper.isCustomDpiEnabled(this)) {
                com.bansify.computer.data.DpiHelper.applyToResources(
                    resources, com.bansify.computer.data.DpiHelper.getCustomDpiValue(this)
                )
            }
        }.onFailure { Log.w(TAG, "Custom display density unavailable") }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            // Release only our disposable cache; never terminate other apps.
            com.bansify.computer.data.AppRepository.clearMemoryCache()
        }
    }

    private fun setupCrashLogging() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Do not write exception messages/stack traces to logcat: they may contain user data.
                Log.e(TAG, "Uncaught exception during app execution (${throwable.javaClass.name})")
                // Record diagnostic even if saving the recovery counter fails.
                runCatching { com.bansify.computer.data.StartupRecovery.recordUncaughtStartupCrash(this) }
                StartupDiagnostics.record(this, StartupIssue.UNCAUGHT_EXCEPTION, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error logging crash", e)
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        private const val TAG = "GothwadApplication"
        // New file avoids exporting old unredacted logs written by earlier versions.
        const val CRASH_LOG_FILE = "gothwad_crash_v2.log"

        /** In-memory flag: true once device lock is satisfied for the current process lifetime */
        @Volatile
        var hasUnlockedDeviceThisProcess: Boolean = false
    }
}
