package com.gothwad.computer.apps.webapp

import java.net.URI
import java.util.Locale

/** Accept only ordinary absolute web URLs, never credentials or executable/local schemes. */
object WebUrlPolicy {
    fun isWebUrl(value: String): Boolean {
        if (value.length !in 1..2048 || value.any { it.isISOControl() || it.isWhitespace() || it == '\\' }) return false
        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            (scheme == "https" || scheme == "http") && !uri.isOpaque &&
                !uri.host.isNullOrBlank() && uri.userInfo == null && uri.port in -1..65535
        } catch (_: Exception) { false }
    }
}
