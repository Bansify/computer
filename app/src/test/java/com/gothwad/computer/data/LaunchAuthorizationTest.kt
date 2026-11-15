package com.gothwad.computer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchAuthorizationTest {
    @Test fun checksAllPackageKindsAndFailsClosedForEmptyCredentials() {
        val locked = LauncherConfig(appLock = LockCredential(enabled = true, value = ""), lockedApps = setOf("pwa://https://example.com", "com.example.app"))
        assertEquals(LaunchAuthorization.Decision.INVALID_CREDENTIAL, LaunchAuthorization.decide(locked, "pwa://https://example.com"))
        assertEquals(LaunchAuthorization.Decision.INVALID_CREDENTIAL, LaunchAuthorization.decide(locked, "com.example.app"))
        assertEquals(LaunchAuthorization.Decision.ALLOW, LaunchAuthorization.decide(locked, "other.app"))
    }

    @Test fun challengesAndRejectsStaleAuthorization() {
        val credential = LockCredential(enabled = true, value = "1234")
        val config = LauncherConfig(appLock = credential, lockedApps = setOf("com.example.app"))
        assertEquals(LaunchAuthorization.Decision.CHALLENGE, LaunchAuthorization.decide(config, "com.example.app"))
        assertEquals(LaunchAuthorization.Decision.ALLOW, LaunchAuthorization.decide(config, "com.example.app", credential))
        val updated = config.copy(appLock = credential.copy(value = "5678"))
        assertEquals(LaunchAuthorization.Decision.CHALLENGE, LaunchAuthorization.decide(updated, "com.example.app", credential))
    }
}
