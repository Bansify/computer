package com.bansify.computer.apps.webapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.bansify.computer.data.ConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

object WebAppManager {

    fun formatUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    fun getSafeId(url: String): String {
        return runCatching {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        }.getOrDefault("pwa_${System.currentTimeMillis()}")
    }

    /** No background requests to arbitrary URLs or third-party favicon services. */
    suspend fun fetchFaviconAndTitle(rawUrl: String, explicitIconUrl: String? = null): Pair<String?, Bitmap?> = withContext(Dispatchers.IO) {
        val formatted = formatUrl(rawUrl)
        val host = if (WebUrlPolicy.isWebUrl(formatted)) runCatching { URL(formatted).host }.getOrNull() else null
        Pair(host?.removePrefix("www.")?.substringBefore('.')?.replaceFirstChar { it.uppercase() }, null)
    }

    suspend fun installWebApp(
        context: Context,
        url: String,
        title: String,
        icon: Bitmap? = null,
        iconUrl: String? = null
    ): String = withContext(Dispatchers.IO) {
        val formattedUrl = formatUrl(url)
        require(WebUrlPolicy.isWebUrl(formattedUrl)) { "Invalid web address" }
        val finalTitle = title.trim().ifEmpty {
            URL(formattedUrl).host.removePrefix("www.").replaceFirstChar { it.uppercase() }
        }
        val safeId = getSafeId(formattedUrl)
        val pwaPkg = "pwa://$formattedUrl"

        // A caller-supplied bitmap or an offline fallback; no background network fetches.
        val targetBitmap = icon ?: generateFallbackSquircleIcon(finalTitle)

        // Save icon to app internal storage
        var iconPath: String? = null
        runCatching {
            val iconsDir = File(context.filesDir, "webapp_icons")
            if (!iconsDir.exists()) iconsDir.mkdirs()
            val iconFile = File(iconsDir, "$safeId.png")
            FileOutputStream(iconFile).use { out ->
                targetBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            iconPath = iconFile.absolutePath
        }

        // Save into LauncherConfig
        ConfigStore(context).update { cfg ->
            val updatedOrder = cfg.pcDesktopOrder.toMutableList().apply {
                if (!contains(pwaPkg)) add(pwaPkg)
            }
            val updatedLabels = cfg.pcCustomLabels.toMutableMap().apply {
                put(pwaPkg, finalTitle)
            }
            val updatedIcons = cfg.pcCustomIcons.toMutableMap().apply {
                iconPath?.let { put(pwaPkg, it) }
            }
            cfg.copy(
                pcDesktopOrder = updatedOrder,
                pcCustomLabels = updatedLabels,
                pcCustomIcons = updatedIcons
            )
        }

        pwaPkg
    }

    suspend fun uninstallWebApp(context: Context, urlOrPkg: String): Boolean = withContext(Dispatchers.IO) {
        val pwaPkg = if (urlOrPkg.startsWith("pwa://")) urlOrPkg else "pwa://${formatUrl(urlOrPkg)}"
        val safeId = getSafeId(pwaPkg.removePrefix("pwa://"))
        runCatching {
            val iconFile = File(File(context.filesDir, "webapp_icons"), "$safeId.png")
            if (iconFile.exists()) iconFile.delete()
        }
        ConfigStore(context).update { cfg ->
            val updatedOrder = cfg.pcDesktopOrder.filter { it != pwaPkg }
            val updatedLabels = cfg.pcCustomLabels.filterKeys { it != pwaPkg }
            val updatedIcons = cfg.pcCustomIcons.filterKeys { it != pwaPkg }
            val updatedPinned = cfg.pcPinnedApps.filter { it != pwaPkg }
            cfg.copy(
                pcDesktopOrder = updatedOrder,
                pcCustomLabels = updatedLabels,
                pcCustomIcons = updatedIcons,
                pcPinnedApps = updatedPinned
            )
        }
        true
    }

    fun hasSavedIcon(context: Context, pwaPkg: String): Boolean {
        val url = if (pwaPkg.startsWith("pwa://")) pwaPkg.removePrefix("pwa://") else pwaPkg
        val safeId = getSafeId(url)
        val iconFile = File(File(context.filesDir, "webapp_icons"), "$safeId.png")
        return iconFile.exists() && iconFile.length() > 100
    }

    fun saveIcon(context: Context, pwaPkg: String, bitmap: Bitmap) {
        val url = if (pwaPkg.startsWith("pwa://")) pwaPkg.removePrefix("pwa://") else pwaPkg
        val safeId = getSafeId(url)
        runCatching {
            val iconsDir = File(context.filesDir, "webapp_icons").apply { mkdirs() }
            val iconFile = File(iconsDir, "$safeId.png")
            java.io.FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    fun loadSavedIcon(context: Context, pwaPkg: String): Bitmap? {
        val url = if (pwaPkg.startsWith("pwa://")) pwaPkg.removePrefix("pwa://") else pwaPkg
        val safeId = getSafeId(url)
        val iconFile = File(File(context.filesDir, "webapp_icons"), "$safeId.png")
        if (iconFile.exists()) {
            return runCatching { BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull()
        }
        return null
    }

    fun generateFallbackSquircleIcon(title: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Generate consistent background color based on title hash
        val colors = intArrayOf(
            0xFF2563EB.toInt(),
            0xFF059669.toInt(),
            0xFFD97706.toInt(),
            0xFF7C3AED.toInt(),
            0xFFDC2626.toInt(),
            0xFF0284C7.toInt()
        )
        val color = colors[((title.hashCode().toLong() and 0xffffffffL) % colors.size).toInt()]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        val radius = size * 0.24f
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)

        // Draw initial letter
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val letter = title.trim().take(1).uppercase()
        val textY = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(letter, size / 2f, textY, textPaint)

        return bitmap
    }
}
