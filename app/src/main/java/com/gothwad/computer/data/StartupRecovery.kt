package com.gothwad.computer.data

import android.content.Context

/** Pure policy is shared with unit tests; failures are capped to avoid overflow. */
internal object StartupRecoveryPolicy {
    const val THRESHOLD = 2
    fun nextFailures(current: Int): Int = current.coerceIn(0, THRESHOLD - 1) + 1
    fun needsRecovery(failures: Int): Boolean = failures >= THRESHOLD
}

/** Records only uncaught crashes during startup; process kills and ANRs are not detected. */
object StartupRecovery {
    private const val PREFS = "startup_recovery"
    private const val KEY_FAILURES = "startup_crashes"
    @Volatile private var starting = true

    fun shouldUseSafeMode(context: Context): Boolean =
        StartupRecoveryPolicy.needsRecovery(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_FAILURES, 0))

    fun recordUncaughtStartupCrash(context: Context) {
        if (!starting) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val failures = StartupRecoveryPolicy.nextFailures(prefs.getInt(KEY_FAILURES, 0))
        // Synchronous because the process is about to terminate; no app data is erased.
        prefs.edit().putInt(KEY_FAILURES, failures).commit()
    }

    fun markHealthy(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_FAILURES, 0).apply()
        starting = false
    }
}
