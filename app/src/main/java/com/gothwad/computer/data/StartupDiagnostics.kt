package com.gothwad.computer.data

import android.content.Context
import com.gothwad.computer.GothwadApplication
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fixed reason codes avoid persisting user-provided values or exception messages. */
internal enum class StartupIssue {
    UNCAUGHT_EXCEPTION,
    CONFIG_TIMEOUT,
    CONFIG_READ_FAILURE,
    CONFIG_CREDENTIAL_INCOMPLETE,
    CONFIG_OBSERVER_FAILURE,
    DESKTOP_START_FAILURE,
    PREVIOUS_STARTUP_FAILURES,
}

internal object StartupDiagnosticFormatter {
    fun format(issue: StartupIssue, error: Throwable?): String = buildString {
        append("Issue: ").append(issue.name).append('\n')
        var cause = error
        repeat(5) {
            val current = cause ?: return@repeat
            append(current.javaClass.name).append('\n')
            current.stackTrace.take(40).forEach { frame ->
                append("  ").append(frame.className.take(256)).append('.')
                    .append(frame.methodName.take(128)).append(':').append(frame.lineNumber).append('\n')
            }
            cause = current.cause?.takeUnless { it === current }
        }
        append("--------------------\n")
    }.take(16 * 1024)
}

/** Best-effort local diagnostics. No messages, input values, URLs or paths are collected. */
internal object StartupDiagnostics {
    @Synchronized
    fun record(context: Context, issue: StartupIssue, error: Throwable? = null) {
        runCatching {
            val file = File(context.filesDir, GothwadApplication.CRASH_LOG_FILE)
            if (file.exists() && file.length() > 64 * 1024) file.delete()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            file.appendText("[$timestamp] " + StartupDiagnosticFormatter.format(issue, error))
        }
    }
}
