package com.bansify.computer.ui.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusRestorePolicyTest {
    @Test fun retainsPackageAfterReorder() {
        assertEquals(2, FocusRestorePolicy.position(listOf("c", "a", "b"), "b", 1))
    }
    @Test fun removedItemFallsBackToNearestPosition() {
        assertEquals(1, FocusRestorePolicy.position(listOf("a", "c"), "b", 1))
        assertEquals(1, FocusRestorePolicy.position(listOf("a", "b"), "c", 2))
        assertEquals(0, FocusRestorePolicy.position(listOf("a"), null, -1))
    }
    @Test fun emptyListHasNoSelection() {
        assertNull(FocusRestorePolicy.position(emptyList(), "x", 0))
    }
}
