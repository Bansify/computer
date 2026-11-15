package com.gothwad.computer.data

import org.junit.Assert.*
import org.junit.Test

class ForegroundShortcutsTest {
    @Test fun permitsOnlyConservativeKeys() {
        assertTrue(ForegroundShortcuts.allowed(29, ForegroundShortcuts.CTRL))
        assertTrue(ForegroundShortcuts.allowed(131, 0))
        assertTrue(ForegroundShortcuts.allowed(183, 0))
        listOf(3, 4, 19, 20, 21, 22, 23, 24, 25, 26, 66).forEach { assertFalse(ForegroundShortcuts.allowed(it, 0)) }
        assertFalse(ForegroundShortcuts.allowed(29, 0))
        assertFalse(ForegroundShortcuts.allowed(29, 4))
    }
    @Test fun matchingIsPerDeviceAndDoesNotOverwriteConflicts() {
        val binding = ForegroundShortcut(29, ForegroundShortcuts.CTRL, "remote-a", ForegroundShortcuts.START)
        assertEquals(binding, ForegroundShortcuts.find(listOf(binding), 29, ForegroundShortcuts.CTRL, "remote-a"))
        assertNull(ForegroundShortcuts.find(listOf(binding), 29, ForegroundShortcuts.CTRL, "remote-b"))
        assertTrue(ForegroundShortcuts.conflicts(listOf(binding), binding.copy(action = ForegroundShortcuts.SEARCH)))
        assertFalse(ForegroundShortcuts.conflicts(listOf(binding), binding.copy(deviceDescriptor = "remote-b")))
        assertNull(ForegroundShortcuts.find(listOf(binding.copy(action = "unknown")), 29, ForegroundShortcuts.CTRL, "remote-a"))
    }
    @Test fun legacyMappingSurvivesCodecRoundTrip() {
        val original = LauncherConfig(buttonMap = mapOf(100 to "legacy"), foregroundShortcuts = listOf(ForegroundShortcut(131, 0, "kbd", ForegroundShortcuts.START)))
        val restored = LauncherConfigCodec.decode(LauncherConfigCodec.encode(original))
        assertEquals(original.buttonMap, restored.buttonMap)
        assertEquals(original.foregroundShortcuts, restored.foregroundShortcuts)
    }
}
