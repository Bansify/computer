package com.gothwad.computer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupDiagnosticFormatterTest {
    @Test fun reportsCauseAndCodeLocationsWithoutSensitiveMessages() {
        val cause = IllegalArgumentException("private token and file path")
        cause.stackTrace = arrayOf(StackTraceElement("com.gothwad.Fake", "load", "Fake.kt", 42))
        val error = IllegalStateException("password and URL", cause)
        val report = StartupDiagnosticFormatter.format(StartupIssue.DESKTOP_START_FAILURE, error)
        assertTrue(report.contains("DESKTOP_START_FAILURE"))
        assertTrue(report.contains("java.lang.IllegalArgumentException"))
        assertTrue(report.contains("com.gothwad.Fake.load:42"))
        assertFalse(report.contains("password"))
        assertFalse(report.contains("token"))
        assertFalse(report.contains("Fake.kt"))
    }

    @Test fun longTraceIsBounded() {
        val error = IllegalStateException("sensitive")
        error.stackTrace = Array(200) { StackTraceElement("C".repeat(1000), "method", null, 1) }
        assertTrue(StartupDiagnosticFormatter.format(StartupIssue.CONFIG_READ_FAILURE, error).length <= 16 * 1024)
    }
}
