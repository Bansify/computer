package com.gothwad.computer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LauncherConfigCodecTest {
    @Test fun missingConfigUsesDefaults() {
        assertEquals(MODE_PC, LauncherConfigCodec.decode(null).launcherMode)
        assertFalse(LauncherConfigCodec.decode(null).deviceLock.enabled)
    }

    @Test fun roundTripPreservesLocksAndDesktop() {
        val config = LauncherConfig(
            deviceLock = LockCredential(enabled = true, value = "1234"),
            lockedApps = setOf("example.app"), pcDesktopOrder = listOf("example.app")
        )
        assertEquals(config, LauncherConfigCodec.decode(LauncherConfigCodec.encode(config)))
    }

    @Test fun malformedOrWrongTypeNeverFallsBackToUnlockedDefaults() {
        assertThrows(InvalidLauncherConfigException::class.java) { LauncherConfigCodec.decode("{bad json") }
        assertThrows(InvalidLauncherConfigException::class.java) { LauncherConfigCodec.decode("{\"deviceLock\":42}") }
    }
}
