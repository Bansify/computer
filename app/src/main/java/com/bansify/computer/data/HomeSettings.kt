package com.bansify.computer.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

object HomeSettings {
    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** Never selects a launcher silently; the system and user decide. */
    fun open(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return intents.any { intent ->
            runCatching {
                if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.isSuccess
        }
    }

    fun openSystemSettings(context: Context): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_SETTINGS)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess
}
