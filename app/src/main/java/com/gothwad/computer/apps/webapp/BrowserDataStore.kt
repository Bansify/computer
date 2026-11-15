package com.gothwad.computer.apps.webapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import android.widget.Toast
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

@Serializable
data class BrowserEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String
)

/** Process-wide serialization and atomic writes prevent lost updates across browser windows. */
class BrowserDataStore(private val context: Context, dataFile: File = File(context.filesDir, "browser_records.json")) {
    private val file = AtomicFile(dataFile)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private val lock = Any()
        private const val MAX_BYTES = 8 * 1024 * 1024
    }

    private fun read(): List<BrowserEntry> {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return emptyList()
        val bytes = file.openRead().use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > MAX_BYTES) throw IOException("Browser records exceed size limit")
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        return json.decodeFromString<List<BrowserEntry>>(bytes.toString(Charsets.UTF_8))
    }

    private fun write(list: List<BrowserEntry>) {
        val bytes = json.encodeToString(list).toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) throw IOException("Browser records exceed size limit")
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    private fun reportError() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, "Browser records unavailable; existing data was not reset", Toast.LENGTH_LONG).show()
        }
    }

    fun getAll(): List<BrowserEntry> = synchronized(lock) {
        try { read() } catch (_: Exception) { reportError(); emptyList() }
    }

    private fun update(transform: (List<BrowserEntry>) -> List<BrowserEntry>) = synchronized(lock) {
        try { write(transform(read())) } catch (_: Exception) { reportError() }
    }

    fun addBookmark(title: String, url: String) = update { current ->
        if (current.any { it.type == "BOOKMARK" && it.url == url }) current
        else listOf(BrowserEntry(title = title, url = url, type = "BOOKMARK")) + current
    }

    fun removeBookmark(url: String) = update { it.filterNot { entry -> entry.type == "BOOKMARK" && entry.url == url } }
    fun isBookmarked(url: String): Boolean = getAll().any { it.type == "BOOKMARK" && it.url == url }

    fun addHistory(title: String, url: String) {
        if (url == "chrome://newtab" || url.startsWith("data:")) return
        update { current ->
            val entries = listOf(BrowserEntry(title = title, url = url, type = "HISTORY")) +
                current.filterNot { it.type == "HISTORY" && it.url == url }
            entries.filter { it.type == "HISTORY" }.take(100) + entries.filterNot { it.type == "HISTORY" }
        }
    }

    fun addDownload(fileName: String, url: String) = update {
        listOf(BrowserEntry(title = fileName, url = url, type = "DOWNLOAD")) + it
    }
    fun deleteEntry(id: String) = update { it.filterNot { entry -> entry.id == id } }
    fun clearType(type: String) = update { it.filterNot { entry -> entry.type == type } }
    fun clearAll() = update { emptyList() }
}
