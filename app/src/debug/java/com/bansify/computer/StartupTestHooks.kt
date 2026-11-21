package com.bansify.computer

import android.content.Intent

/** Debug builds only: explicit ADB fault injection for disposable test devices. */
internal object StartupTestHooks {
    const val ACTION_CRASH_STARTUP = "com.bansify.computer.DEBUG_CRASH_STARTUP"
    private const val ACTION_FAIL_DESKTOP = "com.bansify.computer.DEBUG_FAIL_DESKTOP"
    private var lastDesktopFaultIntent: Intent? = null
    fun beforeDesktop(intent: Intent?) {
        if (intent?.action == ACTION_FAIL_DESKTOP && intent !== lastDesktopFaultIntent) {
            lastDesktopFaultIntent = intent
            throw IllegalStateException("Debug desktop fault")
        }
    }

    fun beforeStartup(intent: Intent?) {
        if (intent?.action == ACTION_CRASH_STARTUP) {
            throw IllegalStateException("Debug startup fault")
        }
    }
}
