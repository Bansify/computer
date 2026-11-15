package com.gothwad.computer

import android.content.Intent

/** Debug builds only: explicit ADB fault injection for disposable test devices. */
internal object StartupTestHooks {
    const val ACTION_CRASH_STARTUP = "com.gothwad.computer.DEBUG_CRASH_STARTUP"
    fun beforeStartup(intent: Intent?) {
        if (intent?.action == ACTION_CRASH_STARTUP) {
            throw IllegalStateException("Debug startup fault")
        }
    }
}
