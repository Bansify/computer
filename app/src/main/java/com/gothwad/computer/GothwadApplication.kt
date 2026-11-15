package com.gothwad.computer

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Application class for Gothwad Computer PC Emulator.
 */
class GothwadApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(runCatching { com.gothwad.computer.data.DpiHelper.wrapContext(base) }.getOrDefault(base))
    }

    override fun onCreate() {
        super.onCreate()
        val currentProc = com.gothwad.computer.data.ProcessHelper.currentProcessName()
        // Install as early as possible, before optional display configuration.
        if (currentProc.isEmpty() || currentProc == packageName) setupCrashLogging()
        Log.i(TAG, "GothwadApplication initialized in process: $currentProc")
        runCatching {
            if (com.gothwad.computer.data.DpiHelper.isCustomDpiEnabled(this)) {
                com.gothwad.computer.data.DpiHelper.applyToResources(
                    resources, com.gothwad.computer.data.DpiHelper.getCustomDpiValue(this)
                )
            }
        }.onFailure { Log.w(TAG, "Custom display density unavailable") }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            // Release only our disposable cache; never terminate other apps.
            com.gothwad.computer.data.AppRepository.clearMemoryCache()
        }
    }

    private fun setupCrashLogging() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Do not write exception messages/stack traces to logcat: they may contain user data.
                Log.e(TAG, "Uncaught exception during app execution (${throwable.javaClass.name})")
                com.gothwad.computer.data.StartupRecovery.recordUncaughtStartupCrash(this)
                logCrashToFile(throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error logging crash", e)
            } finally {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun logCrashToFile(throwable: Throwable) {
        runCatching {
            val crashFile = File(filesDir, CRASH_LOG_FILE)
            // Keep crash file bounded: if it exceeds 64KB, truncate it
            if (crashFile.exists() && crashFile.length() > 64 * 1024) {
                crashFile.delete()
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            // Keep exception types and code locations, not exception messages or user-supplied data.
            val report = buildString {
                append("[$timestamp] EXCEPTION:\n")
                var current: Throwable? = throwable
                repeat(5) {
                    val cause = current ?: return@repeat
                    append(cause.javaClass.name).append('\n')
                    cause.stackTrace.take(40).forEach { frame ->
                        append("  ").append(frame.className).append('.').append(frame.methodName)
                            .append(':').append(frame.lineNumber).append('\n')
                    }
                    current = cause.cause?.takeUnless { it === cause }
                }
                append("--------------------\n")
            }
            crashFile.appendText(report.take(16 * 1024))
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
