package com.bansify.computer.data

import kotlinx.serialization.Serializable

@Serializable
data class ForegroundShortcut(
    val keyCode: Int,
    val modifiers: Int = 0,
    val deviceDescriptor: String = "",
    val action: String,
)

/** Conservative foreground-only mapping. OS keys, navigation, media, Alt and Meta are reserved. */
object ForegroundShortcuts {
    const val SHIFT = 1
    const val CTRL = 2
    const val START = "start"
    const val SEARCH = "search"
    const val QUICK_SETTINGS = "quick_settings"
    val actions = listOf(START, SEARCH, QUICK_SETTINGS)

    fun label(action: String): String = when (action) {
        START -> "Start menu"
        SEARCH -> "Search apps"
        QUICK_SETTINGS -> "Quick settings"
        else -> "Unknown action"
    }

    fun allowed(keyCode: Int, modifiers: Int): Boolean {
        if (modifiers and (SHIFT or CTRL).inv() != 0) return false
        val functionOrColour = keyCode in 131..142 || keyCode in 183..186
        val alphanumeric = keyCode in 7..16 || keyCode in 29..54
        return (functionOrColour && modifiers == 0) || (alphanumeric && modifiers and CTRL != 0)
    }

    fun find(bindings: List<ForegroundShortcut>, keyCode: Int, modifiers: Int, descriptor: String): ForegroundShortcut? =
        bindings.firstOrNull {
            it.keyCode == keyCode && it.modifiers == modifiers && it.deviceDescriptor == descriptor &&
                it.action in actions && allowed(keyCode, modifiers)
        }

    fun conflicts(bindings: List<ForegroundShortcut>, candidate: ForegroundShortcut): Boolean =
        bindings.any { it.keyCode == candidate.keyCode && it.modifiers == candidate.modifiers && it.deviceDescriptor == candidate.deviceDescriptor }
}
