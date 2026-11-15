package com.gothwad.computer.data

/** Launcher-level UX gate, not an Android system security boundary. */
object LaunchAuthorization {
    enum class Decision { ALLOW, CHALLENGE, INVALID_CREDENTIAL }

    fun decide(config: LauncherConfig, pkg: String, authorized: LockCredential? = null): Decision {
        val lock = config.appLock
        if (!lock.enabled || pkg !in config.lockedApps) return Decision.ALLOW
        if (lock.value.isEmpty()) return Decision.INVALID_CREDENTIAL
        return if (authorized == lock) Decision.ALLOW else Decision.CHALLENGE
    }
}
