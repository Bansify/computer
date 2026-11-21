package com.bansify.computer.ui.pc

/** Keep the same package focused; if removed, use its nearest remaining position. */
internal object FocusRestorePolicy {
    fun position(packages: List<String>, previousPackage: String?, previousIndex: Int): Int? {
        if (packages.isEmpty()) return null
        val same = previousPackage?.let { packages.indexOf(it) } ?: -1
        return if (same >= 0) same else previousIndex.coerceIn(0, packages.lastIndex)
    }
}
