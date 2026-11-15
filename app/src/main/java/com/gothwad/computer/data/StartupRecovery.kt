package com.gothwad.computer.data

import android.content.Context

/** Records only uncaught crashes during startup; process kills and ANRs are not detected. */
object StartupRecovery {
    private const val PREFS = "startup_recovery"
    private const val KEY_FAILURES = "startup_crashes"
    private const val THRESHOLD = 2
    @Volatile private var starting = true

    fun shouldUseSafeMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_FAILURES, 0) >= THRESHOLD

    fun recordUncaughtStartupCrash(context: Context) {
        if (!starting) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val failures = (prefs.getInt(KEY_FAILURES, 0) + 1).coerceAtMost(THRESHOLD)
        // Synchronous because the process is about to terminate; no app data is erased.
        prefs.edit().putInt(KEY_FAILURES, failures).commit()
    }

    fun markHealthy(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_FAILURES, 0).apply()
        starting = false
    }
}
