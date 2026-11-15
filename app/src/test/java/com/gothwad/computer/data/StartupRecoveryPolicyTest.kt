package com.gothwad.computer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupRecoveryPolicyTest {
    @Test fun onlyRepeatedStartupFailuresTriggerRecovery() {
        assertFalse(StartupRecoveryPolicy.needsRecovery(0))
        assertFalse(StartupRecoveryPolicy.needsRecovery(StartupRecoveryPolicy.nextFailures(0)))
        assertTrue(StartupRecoveryPolicy.needsRecovery(StartupRecoveryPolicy.nextFailures(1)))
    }

    @Test fun countIsBounded() {
        assertEquals(2, StartupRecoveryPolicy.nextFailures(Int.MAX_VALUE))
        assertEquals(1, StartupRecoveryPolicy.nextFailures(-1))
    }
}
