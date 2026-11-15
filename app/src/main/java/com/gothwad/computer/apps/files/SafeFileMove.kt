package com.gothwad.computer.apps.files

import java.io.File

/** Rename only; never replace a known destination or copy/delete on failure. */
object SafeFileMove {
    fun move(source: File, targetDir: File): Boolean = try {
        val from = source.canonicalFile
        val target = targetDir.canonicalFile
        if (!from.exists() || !target.isDirectory || from.parentFile == target ||
            (from.isDirectory && (target == from || target.path.startsWith(from.path + File.separator)))) {
            false
        } else {
            val dest = File(target, from.name)
            !dest.exists() && from.renameTo(dest)
        }
    } catch (_: Exception) { false }
}
