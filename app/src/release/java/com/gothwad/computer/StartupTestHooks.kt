package com.gothwad.computer

import android.content.Intent

/** Release builds cannot inject test failures. */
internal object StartupTestHooks {
    fun beforeStartup(intent: Intent?) = Unit
}
