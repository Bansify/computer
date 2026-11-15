package com.gothwad.computer.apps.store

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.gothwad.computer.apps.webapp.WebUrlPolicy
import java.io.ByteArrayInputStream
import org.json.JSONObject
import android.widget.Toast
import com.gothwad.computer.apps.webapp.WebAppManager
import com.gothwad.computer.data.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Windows 11 Microsoft Store Web Engine for Gothwad PC.
 * Hosts an authentic Windows Store experience built with HTML/CSS/JavaScript,
 * communicating with Android native bridge to install, launch, and uninstall web apps directly.
 */
@SuppressLint("SetJavaScriptEnabled")
class GothwadStoreView(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onAppListChanged: (() -> Unit)? = null,
    private val onOpenWebApp: ((url: String, title: String) -> Unit)? = null
) {
    val webView: WebView = WebView(context).apply {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
        }

        setBackgroundColor(0xFF1A1B1E.toInt())

        addJavascriptInterface(StoreJsBridge(), "AndroidStoreBridge")

        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            // The bridge is only attached to this packaged page. Never let a web page,
            // redirect, frame, or arbitrary asset acquire it.
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse = localAsset(url)
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse = localAsset(request?.url?.toString())
        }

        loadUrl(STORE_ORIGIN + "index.html")
    }

    companion object {
        private const val STORE_ORIGIN = "https://store.gothwad.invalid/"
        private val assetTypes = mapOf("index.html" to "text/html", "store.css" to "text/css", "store.js" to "application/javascript", "apps.js" to "application/javascript")
    }

    private fun localAsset(url: String?): WebResourceResponse {
        val asset = url?.removePrefix(STORE_ORIGIN)?.takeIf { url == STORE_ORIGIN + it }
        val mime = asset?.let { assetTypes[it] }
        if (mime != null && asset != null) {
            val stream = runCatching { context.assets.open("store/$asset") }.getOrNull()
            if (stream != null) return WebResourceResponse(mime, "UTF-8", stream)
        }
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    fun getView(): View = webView

    fun destroy() {
        webView.stopLoading()
        webView.removeJavascriptInterface("AndroidStoreBridge")
        webView.destroy()
    }

    inner class StoreJsBridge {

        @JavascriptInterface
        fun getInstalledAppsJson(): String {
            val pwaUrls = runCatching {
                runBlocking {
                    val config = ConfigStore(context).flow.first()
                    (config.pcCustomLabels.keys + config.pcDesktopOrder)
                        .filter { it.startsWith("pwa://") }
                        .map { it.removePrefix("pwa://") }
                        .distinct()
                }
            }.getOrDefault(emptyList())

            val array = JSONArray()
            pwaUrls.forEach { array.put(it) }
            return array.toString()
        }

        @JavascriptInterface
        fun installApp(name: String, url: String, iconUrl: String, category: String) {
            if (!WebUrlPolicy.isWebUrl(url) || name.length !in 1..100) return
            coroutineScope.launch {
                WebAppManager.installWebApp(
                    context = context,
                    url = url,
                    title = name,
                    // No automatic network icon fetch from untrusted bridge arguments.
                    icon = WebAppManager.generateFallbackSquircleIcon(name),
                    iconUrl = null
                )
                withContext(Dispatchers.Main) {
                    onAppListChanged?.invoke()
                    webView.evaluateJavascript(
                        "window.onAppInstalled && window.onAppInstalled(${JSONObject.quote(url)});",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun uninstallApp(url: String) {
            if (!WebUrlPolicy.isWebUrl(url)) return
            coroutineScope.launch {
                WebAppManager.uninstallWebApp(context, url)
                withContext(Dispatchers.Main) {
                    onAppListChanged?.invoke()
                    webView.evaluateJavascript(
                        "window.onAppUninstalled && window.onAppUninstalled(${JSONObject.quote(url)});",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun openApp(url: String, name: String) {
            if (!WebUrlPolicy.isWebUrl(url) || name.length !in 1..100) return
            coroutineScope.launch(Dispatchers.Main) {
                onOpenWebApp?.invoke(url, name)
            }
        }

        @JavascriptInterface
        fun showToast(message: String) {
            coroutineScope.launch(Dispatchers.Main) {
                Toast.makeText(context, message.take(200), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
