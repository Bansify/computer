package com.bansify.computer.apps.webapp

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.PopupWindow
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import com.bansify.computer.R
import com.bansify.computer.databinding.ItemChromeTabBinding
import com.bansify.computer.databinding.MenuChromeBrowserBinding
import com.bansify.computer.databinding.ViewWebAppBinding
import com.bansify.computer.ui.AppIcons
import java.io.File
import java.net.URLEncoder

/**
 * Authentic Chrome Browser Clone & Desktop Web App Engine.
 * Features:
 * - Real Chrome Multi-Tab System with Tab Strip, dynamic favicons, titles, and '+' button
 * - Chrome Omnibox with Instant Google Search, SSL status, Bookmark Star, Clear button
 * - Speed Dial / Chrome New Tab Page with search box and quick shortcuts
 * - Browser menu: bookmarks, history, downloads and web shortcuts; private mode is unavailable.
 * - In-Page Search ("Find in Page") with active match counter and previous/next navigation
 * - File uploads and file-drop injection are disabled until a safe picker/result flow is implemented.
 * - Shared Web Storage & Cookie Session (Telegram Web, WhatsApp, Discord stay logged in permanently across windows with zero lag)
 * - Website camera, microphone and geolocation requests are denied by default.
 * - Fullscreen video playback (HTML5 / YouTube)
 * - 100% Native Android Views & ViewBinding, zero Jetpack Compose.
 */
class WebAppView(
    private val context: Context,
    private val initialUrl: String = "https://www.google.com",
    private val isStandaloneWebApp: Boolean = false,
    private val onPinShortcut: (() -> Unit)? = null
) {
    val binding: ViewWebAppBinding = ViewWebAppBinding.inflate(
        LayoutInflater.from(context)
    )

    private val dataStore = BrowserDataStore(context)
    private val tabs = mutableListOf<ChromeTab>()
    private var activeTabIndex = 0
    private var isDesktopSiteMode = false
    @Volatile
    private var isDestroyed = false

    private val desktopUserAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    private var defaultMobileUserAgent: String = ""

    data class ChromeTab(
        val id: String,
        var title: String,
        var url: String,
        var favicon: Bitmap?,
        val webView: WebView,
        val isIncognito: Boolean = false
    )

    init {
        setupToolbarIcons()
        setupListeners()
        setupDragAndDropListener()
        setupSpeedDialShortcuts()

        if (isStandaloneWebApp) {
            // In standalone app mode, hide tab strip for a dedicated desktop window feel
            binding.layoutChromeTabStrip.visibility = View.GONE
        }

        // Initialize first tab
        addNewTab(initialUrl)
    }

    fun getView(): View = binding.root

    fun getTitle(): String {
        return getActiveTab()?.title ?: "Web Browser"
    }

    fun getFavicon(): Bitmap? {
        return getActiveTab()?.favicon
    }

    fun getCurrentUrl(): String {
        return getActiveTab()?.webView?.url ?: initialUrl
    }

    private fun getActiveTab(): ChromeTab? {
        return tabs.getOrNull(activeTabIndex)
    }

    private fun setupToolbarIcons() {
        binding.btnWebBack.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BACK, Color.WHITE))
        binding.btnWebForward.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CHEVRON_RIGHT, Color.WHITE))
        binding.btnWebRefresh.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_REFRESH, Color.WHITE))
        binding.btnWebHome.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, Color.WHITE))
        binding.btnWebClear.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, 0xFFAAAAAA.toInt()))
        binding.btnWebGo.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PLAY, 0xFF4FA7FA.toInt()))
        binding.btnWebBookmark.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BOOKMARK, Color.WHITE))
        binding.btnPinToDesktop.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PIN, 0xFF4FA7FA.toInt()))
        binding.btnChromeMenu.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_MORE_VERT, Color.WHITE))
        binding.btnChromeNewTab.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ADD, Color.WHITE))

        binding.imgWebSslIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4CAF50.toInt()))
        binding.imgNtpSearchIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xFF888888.toInt()))
        binding.imgDropIndicator.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWNLOAD, 0xFF38BDF8.toInt()))
        binding.imgWebError.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI_OFF, 0xFFE05252.toInt()))

        // Find in page icons
        binding.btnFindPrev.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_UP, Color.WHITE))
        binding.btnFindNext.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWN, Color.WHITE))
        binding.btnFindClose.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTabWebView(isIncognito: Boolean): WebView {
        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }

        val settings = webView.settings
        if (defaultMobileUserAgent.isEmpty()) {
            defaultMobileUserAgent = settings.userAgentString
        }
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = if (isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // Shared cookies and sessions
        runCatching {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (isDestroyed) return
                binding.webProgressBar.visibility = View.VISIBLE
                binding.layoutWebError.visibility = View.GONE
                url?.let {
                    if (view == getActiveTab()?.webView) {
                        updateUrlAndControls(it)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (isDestroyed) return
                binding.webProgressBar.visibility = View.GONE
                url?.let { currentUrl ->
                    val tab = tabs.firstOrNull { it.webView == view }
                    if (tab != null) {
                        tab.url = currentUrl
                        tab.title = view?.title ?: tab.title
                    }
                    if (view == getActiveTab()?.webView) {
                        updateUrlAndControls(currentUrl)
                        dataStore.addHistory(view?.title ?: currentUrl, currentUrl)
                    }
                    renderTabsStrip()
                }
                updateNavButtons()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (isDestroyed) return
                if (request?.isForMainFrame == true) {
                    binding.webProgressBar.visibility = View.GONE
                    if (view == getActiveTab()?.webView) {
                        binding.layoutWebError.visibility = View.VISIBLE
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (isDestroyed) return
                if (view == getActiveTab()?.webView) {
                    binding.webProgressBar.progress = newProgress
                    if (newProgress >= 100) {
                        binding.webProgressBar.visibility = View.GONE
                        binding.btnWebRefresh.setImageDrawable(
                            AppIcons.createDrawable(AppIcons.PATH_REFRESH, Color.WHITE)
                        )
                    } else {
                        binding.webProgressBar.visibility = View.VISIBLE
                        binding.btnWebRefresh.setImageDrawable(
                            AppIcons.createDrawable(AppIcons.PATH_CLOSE, Color.WHITE)
                        )
                    }
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (isDestroyed) return
                val tab = tabs.firstOrNull { it.webView == view }
                if (tab != null && !title.isNullOrEmpty()) {
                    tab.title = title
                    renderTabsStrip()
                }
            }

            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                if (isDestroyed) return
                val tab = tabs.firstOrNull { it.webView == view }
                if (tab != null && icon != null) {
                    tab.favicon = icon
                    renderTabsStrip()
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, false, false)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (isDestroyed) {
                    callback?.onCustomViewHidden()
                    return
                }
                if (view != null) {
                    binding.fullscreenCustomViewContainer.addView(view)
                    binding.fullscreenCustomViewContainer.visibility = View.VISIBLE
                }
            }

            override fun onHideCustomView() {
                if (isDestroyed) return
                binding.fullscreenCustomViewContainer.removeAllViews()
                binding.fullscreenCustomViewContainer.visibility = View.GONE
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // This floating browser has no activity-result owner. Never leave the callback pending.
                filePathCallback?.onReceiveValue(null)
                Toast.makeText(context, "File uploads are not supported in this browser yet", Toast.LENGTH_SHORT).show()
                return true
            }
        }

        // Attach find-in-page match counter listener to every newly created tab WebView
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
            if (isDestroyed) return@setFindListener
            binding.tvFindCount.text = if (numberOfMatches > 0) {
                "${activeMatchOrdinal + 1}/$numberOfMatches"
            } else {
                "0/0"
            }
        }

        // Downloads listener
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
            runCatching {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading file via Chrome...")
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    dataStore.addDownload(fileName, url)
                }
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }

        return webView
    }

    fun addNewTab(url: String = "https://www.google.com", isIncognito: Boolean = false) {
        if (isIncognito) {
            Toast.makeText(context, "Private browsing is unavailable: WebView sessions are shared", Toast.LENGTH_LONG).show()
            return
        }
        val tabId = "tab_${System.currentTimeMillis()}_${tabs.size}"
        val webView = createTabWebView(false)
        val initialTitle = if (url == "chrome://newtab" || url.isEmpty()) "New Tab" else "Loading..."
        val tab = ChromeTab(
            id = tabId,
            title = initialTitle,
            url = url,
            favicon = null,
            webView = webView,
            isIncognito = isIncognito
        )
        tabs.add(tab)
        binding.webViewContainer.addView(webView)
        selectTab(tabs.size - 1)

        if (url == "chrome://newtab" || url.isEmpty()) {
            showNewTabPage(true)
        } else {
            showNewTabPage(false)
            loadUrlInActiveTab(url)
        }
    }

    fun selectTab(index: Int) {
        if (index !in tabs.indices) return
        activeTabIndex = index

        // Toggle visibility of WebViews
        tabs.forEachIndexed { i, tab ->
            tab.webView.visibility = if (i == activeTabIndex) View.VISIBLE else View.GONE
        }

        val active = tabs[activeTabIndex]
        if (active.url == "chrome://newtab") {
            showNewTabPage(true)
        } else {
            showNewTabPage(false)
            updateUrlAndControls(active.webView.url ?: active.url)
        }

        updateNavButtons()
        renderTabsStrip()
    }

    fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tabToClose = tabs.removeAt(index)
        binding.webViewContainer.removeView(tabToClose.webView)
        tabToClose.webView.stopLoading()
        tabToClose.webView.destroy()

        if (tabs.isEmpty()) {
            addNewTab("chrome://newtab")
        } else {
            val nextIndex = (index - 1).coerceAtLeast(0).coerceAtMost(tabs.size - 1)
            selectTab(nextIndex)
        }
    }

    private fun renderTabsStrip() {
        val container = binding.containerChromeTabs
        container.removeAllViews()

        tabs.forEachIndexed { index, tab ->
            val tabBinding = ItemChromeTabBinding.inflate(
                LayoutInflater.from(context),
                container,
                false
            )

            val isActive = (index == activeTabIndex)
            tabBinding.tabRoot.background = if (isActive) {
                ColorDrawable(0xFF262A34.toInt())
            } else {
                ColorDrawable(0xFF181A20.toInt())
            }

            tabBinding.tvTabTitle.text = tab.title
            tabBinding.tvTabTitle.setTextColor(if (isActive) Color.WHITE else 0xFF94A3B8.toInt())

            if (tab.isIncognito) {
                tabBinding.imgTabFavicon.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_INCOGNITO, 0xFF94A3B8.toInt())
                )
            } else if (tab.favicon != null) {
                tabBinding.imgTabFavicon.setImageBitmap(tab.favicon)
            } else {
                tabBinding.imgTabFavicon.setImageDrawable(
                    AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFF94A3B8.toInt())
                )
            }

            tabBinding.btnTabClose.setImageDrawable(
                AppIcons.createDrawable(AppIcons.PATH_CLOSE, if (isActive) Color.WHITE else 0xFF888888.toInt())
            )

            tabBinding.tabRoot.setOnClickListener {
                selectTab(index)
            }

            tabBinding.btnTabClose.setOnClickListener {
                closeTab(index)
            }

            container.addView(tabBinding.root)
        }
    }

    private fun showNewTabPage(show: Boolean) {
        binding.layoutNewTabPage.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            getActiveTab()?.webView?.visibility = View.GONE
            binding.etWebUrl.setText("")
            binding.etWebUrl.hint = "Search Google or type a URL"
            binding.imgWebSslIcon.setImageDrawable(
                AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xFF888888.toInt())
            )
        } else {
            getActiveTab()?.webView?.visibility = View.VISIBLE
        }
    }

    private fun updateUrlAndControls(url: String) {
        if (url == "chrome://newtab") {
            showNewTabPage(true)
            return
        }
        showNewTabPage(false)
        binding.etWebUrl.setText(url)
        updateSslIcon(url)
        updateBookmarkIcon(url)
    }

    private fun updateSslIcon(url: String) {
        if (url.startsWith("https://")) {
            binding.imgWebSslIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK, 0xFF4CAF50.toInt()))
        } else {
            binding.imgWebSslIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK_OPEN, 0xFFE05252.toInt()))
        }
    }

    private fun updateBookmarkIcon(url: String) {
        val isBookmarked = dataStore.isBookmarked(url)
        if (isBookmarked) {
            binding.btnWebBookmark.setImageDrawable(
                AppIcons.createDrawable(AppIcons.PATH_BOOKMARK, 0xFFFBBF24.toInt()) // Gold
            )
        } else {
            binding.btnWebBookmark.setImageDrawable(
                AppIcons.createDrawable(AppIcons.PATH_BOOKMARK, Color.WHITE)
            )
        }
    }

    private fun updateNavButtons() {
        val currentWebView = getActiveTab()?.webView
        val canBack = currentWebView?.canGoBack() == true
        val canForward = currentWebView?.canGoForward() == true

        binding.btnWebBack.alpha = if (canBack) 1.0f else 0.4f
        binding.btnWebBack.isEnabled = canBack

        binding.btnWebForward.alpha = if (canForward) 1.0f else 0.4f
        binding.btnWebForward.isEnabled = canForward
    }

    private fun setupListeners() {
        // Back
        binding.btnWebBack.setOnClickListener {
            val wv = getActiveTab()?.webView
            if (wv?.canGoBack() == true) {
                wv.goBack()
            }
        }

        // Forward
        binding.btnWebForward.setOnClickListener {
            val wv = getActiveTab()?.webView
            if (wv?.canGoForward() == true) {
                wv.goForward()
            }
        }

        // Refresh / Stop
        binding.btnWebRefresh.setOnClickListener {
            val wv = getActiveTab()?.webView ?: return@setOnClickListener
            if (binding.webProgressBar.visibility == View.VISIBLE) {
                wv.stopLoading()
                binding.webProgressBar.visibility = View.GONE
            } else {
                wv.reload()
            }
        }

        // Home
        binding.btnWebHome.setOnClickListener {
            val tab = getActiveTab()
            if (tab != null) {
                tab.url = "chrome://newtab"
                tab.title = "New Tab"
                showNewTabPage(true)
                renderTabsStrip()
            }
        }

        // New Tab '+' button
        binding.btnChromeNewTab.setOnClickListener {
            addNewTab("chrome://newtab")
        }

        // Omnibox URL typing & clear
        binding.etWebUrl.doAfterTextChanged { text ->
            binding.btnWebClear.visibility = if (!text.isNullOrEmpty() && binding.etWebUrl.hasFocus()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        binding.btnWebClear.setOnClickListener {
            binding.etWebUrl.setText("")
        }

        binding.btnWebGo.setOnClickListener {
            navigateFromInput()
        }

        binding.etWebUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigateFromInput()
                hideKeyboard(binding.etWebUrl)
                true
            } else {
                false
            }
        }

        // New Tab Page Search Input
        binding.etNtpSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.etNtpSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    loadUrl(query)
                    hideKeyboard(binding.etNtpSearch)
                }
                true
            } else {
                false
            }
        }

        // Bookmark button toggle
        binding.btnWebBookmark.setOnClickListener {
            val url = getCurrentUrl()
            val title = getTitle()
            if (dataStore.isBookmarked(url)) {
                if (dataStore.removeBookmark(url)) Toast.makeText(context, "Removed bookmark", Toast.LENGTH_SHORT).show()
            } else {
                if (dataStore.addBookmark(title, url)) Toast.makeText(context, "Saved to bookmarks ★", Toast.LENGTH_SHORT).show()
            }
            updateBookmarkIcon(url)
        }

        // Install Web App button
        binding.btnPinToDesktop.setOnClickListener {
            openInstallWebAppDialog()
        }

        // Chrome 3-Dots Menu
        binding.btnChromeMenu.setOnClickListener { v ->
            showChromePopupMenu(v)
        }

        // Retry button on error
        binding.btnWebRetry.setOnClickListener {
            binding.layoutWebError.visibility = View.GONE
            getActiveTab()?.webView?.reload()
        }

        // Find in Page Listeners
        setupFindInPageListeners()
    }

    private fun setupFindInPageListeners() {
        binding.etFindQuery.doAfterTextChanged { text ->
            val query = text?.toString() ?: ""
            if (query.isNotEmpty()) {
                getActiveTab()?.webView?.findAllAsync(query)
            } else {
                getActiveTab()?.webView?.clearMatches()
                binding.tvFindCount.text = "0/0"
            }
        }

        binding.btnFindNext.setOnClickListener {
            getActiveTab()?.webView?.findNext(true)
        }

        binding.btnFindPrev.setOnClickListener {
            getActiveTab()?.webView?.findNext(false)
        }

        binding.btnFindClose.setOnClickListener {
            binding.layoutFindInPage.visibility = View.GONE
            getActiveTab()?.webView?.clearMatches()
        }

        // Set find listener to display active / total count
        tabs.forEach { tab ->
            tab.webView.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                binding.tvFindCount.text = if (numberOfMatches > 0) {
                    "${activeMatchOrdinal + 1}/$numberOfMatches"
                } else {
                    "0/0"
                }
            }
        }
    }

    private fun setupSpeedDialShortcuts() {
        binding.ntpShortcutGoogle.setOnClickListener { loadUrl("https://www.google.com") }
        binding.ntpShortcutYoutube.setOnClickListener { loadUrl("https://www.youtube.com") }
        binding.ntpShortcutTelegram.setOnClickListener { loadUrl("https://web.telegram.org") }
        binding.ntpShortcutWikipedia.setOnClickListener { loadUrl("https://www.wikipedia.org") }
        binding.ntpShortcutGithub.setOnClickListener { loadUrl("https://github.com") }
        binding.ntpShortcutReddit.setOnClickListener { loadUrl("https://www.reddit.com") }
        binding.ntpShortcutChatgpt.setOnClickListener { loadUrl("https://chatgpt.com") }
        binding.ntpShortcutWhatsapp.setOnClickListener { loadUrl("https://web.whatsapp.com") }

        // Icons for speed dial shortcuts
        binding.imgNtpGoogle.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xFF4285F4.toInt()))
        binding.imgNtpYoutube.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PLAY, 0xFFEF4444.toInt()))
        binding.imgNtpTelegram.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFF38BDF8.toInt()))
        binding.imgNtpWikipedia.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFFE2E8F0.toInt()))
        binding.imgNtpGithub.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFFFFFFFF.toInt()))
        binding.imgNtpReddit.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFFFF4500.toInt()))
        binding.imgNtpChatgpt.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BOLT, 0xFF10B981.toInt()))
        binding.imgNtpWhatsapp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_PHONE, 0xFF4ADE80.toInt()))
    }

    /**
     * Cross-Window Drag & Drop Listener:
     * When dragging files from File Manager over the Browser:
     * - Displays authentic overlay HUD
     * - Injects dropped file to HTML5 drag/drop targets & file inputs via DataTransfer!
     */
    private fun setupDragAndDropListener() {
        // Do not accept external raw-path drag data or inject file contents into websites.
        binding.root.setOnDragListener { _, _ -> false }
    }

    private fun showChromePopupMenu(anchor: View) {
        val menuBinding = MenuChromeBrowserBinding.inflate(LayoutInflater.from(context))
        val popup = PopupWindow(
            menuBinding.root,
            (210 * context.resources.displayMetrics.density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 16f
        }

        // Setup icons
        menuBinding.imgMenuNewTab.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_ADD, Color.WHITE))
        menuBinding.imgMenuIncognito.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_INCOGNITO, Color.WHITE))
        menuBinding.imgMenuBookmarks.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BOOKMARK, Color.WHITE))
        menuBinding.imgMenuHistory.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HISTORY, Color.WHITE))
        menuBinding.imgMenuDownloads.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DOWNLOAD, Color.WHITE))
        menuBinding.imgMenuDesktop.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DESKTOP, Color.WHITE))
        menuBinding.imgMenuFind.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, Color.WHITE))
        menuBinding.imgMenuShare.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SHARE, Color.WHITE))
        menuBinding.imgMenuInstallApp.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GLOBE, 0xFF4FA7FA.toInt()))
        menuBinding.imgMenuClear.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_DELETE, 0xFFEF4444.toInt()))

        menuBinding.chkDesktopSite.isChecked = isDesktopSiteMode

        // 1. New Tab
        menuBinding.menuItemNewTab.setOnClickListener {
            popup.dismiss()
            addNewTab("chrome://newtab")
        }

        // 2. New Incognito Tab
        menuBinding.menuItemIncognito.setOnClickListener {
            popup.dismiss()
            Toast.makeText(context, "Private browsing is unavailable: WebView sessions are shared", Toast.LENGTH_LONG).show()
        }

        // 3. Bookmarks
        menuBinding.menuItemBookmarks.setOnClickListener {
            popup.dismiss()
            openItemsDialog("BOOKMARK")
        }

        // 4. History
        menuBinding.menuItemHistory.setOnClickListener {
            popup.dismiss()
            openItemsDialog("HISTORY")
        }

        // 5. Downloads
        menuBinding.menuItemDownloads.setOnClickListener {
            popup.dismiss()
            openItemsDialog("DOWNLOAD")
        }

        // 6. Desktop Site Checkbox
        menuBinding.menuItemDesktopSite.setOnClickListener {
            isDesktopSiteMode = !isDesktopSiteMode
            menuBinding.chkDesktopSite.isChecked = isDesktopSiteMode
            popup.dismiss()
            toggleDesktopSite(isDesktopSiteMode)
        }

        // 7. Find in Page
        menuBinding.menuItemFind.setOnClickListener {
            popup.dismiss()
            binding.layoutFindInPage.visibility = View.VISIBLE
            binding.etFindQuery.requestFocus()
        }

        // 8. Share
        menuBinding.menuItemShare.setOnClickListener {
            popup.dismiss()
            shareCurrentPage()
        }

        // 9. Install as Desktop App
        menuBinding.menuItemInstallApp.setOnClickListener {
            popup.dismiss()
            openInstallWebAppDialog()
        }

        // 10. Clear Browsing Data
        menuBinding.menuItemClearData.setOnClickListener {
            popup.dismiss()
            clearBrowsingData()
        }

        runCatching {
            if (anchor.windowToken != null) {
                popup.showAsDropDown(anchor, 0, 4)
            }
        }
    }

    private fun toggleDesktopSite(enabled: Boolean) {
        val wv = getActiveTab()?.webView ?: return
        wv.settings.userAgentString = if (enabled) desktopUserAgent else defaultMobileUserAgent
        wv.reload()
        Toast.makeText(context, if (enabled) "Desktop site requested" else "Mobile site requested", Toast.LENGTH_SHORT).show()
    }

    private fun shareCurrentPage() {
        runCatching {
            val url = getCurrentUrl()
            val title = getTitle()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share Web Page").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
        }.onFailure {
            Toast.makeText(context, "No app available to share", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInstallWebAppDialog() {
        val act = getActivity() as? FragmentActivity ?: return
        if (act.isFinishing || act.isDestroyed || act.supportFragmentManager.isStateSaved) return
        val url = getCurrentUrl()
        val title = getTitle()
        val icon = getFavicon()
        val dialog = InstallWebAppDialog(
            prefillUrl = if (url != "chrome://newtab") url else "",
            prefillTitle = if (title != "New Tab" && title != "Web Browser") title else "",
            prefillIcon = icon,
            onInstalled = { onPinShortcut?.invoke() }
        )
        runCatching {
            if (act.supportFragmentManager.findFragmentByTag("install_web_app") == null) {
                dialog.show(act.supportFragmentManager, "install_web_app")
            }
        }
    }

    private fun openItemsDialog(type: String) {
        val act = getActivity() as? FragmentActivity ?: return
        if (act.isFinishing || act.isDestroyed || act.supportFragmentManager.isStateSaved) return
        val dialog = BrowserItemsDialog(itemType = type) { selectedUrl ->
            loadUrl(selectedUrl)
        }
        runCatching {
            val tag = "browser_items_$type"
            if (act.supportFragmentManager.findFragmentByTag(tag) == null) {
                dialog.show(act.supportFragmentManager, tag)
            }
        }
    }

    private fun clearBrowsingData() {
        runCatching {
            getActiveTab()?.webView?.clearCache(true)
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        if (dataStore.clearType("HISTORY")) {
            Toast.makeText(context, "History cleared; cookie removal requested", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateFromInput() {
        val input = binding.etWebUrl.text.toString().trim()
        if (input.isEmpty()) return
        loadUrl(input)
    }

    fun loadUrl(rawUrl: String) {
        var url = rawUrl.trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://") && url != "chrome://newtab") {
            url = if (url.contains(".") && !url.contains(" ")) {
                "https://$url"
            } else {
                "https://www.google.com/search?q=" + URLEncoder.encode(url, "UTF-8")
            }
        }
        loadUrlInActiveTab(url)
    }

    private fun loadUrlInActiveTab(url: String) {
        val tab = getActiveTab() ?: return
        if (url != "chrome://newtab" && !WebUrlPolicy.isWebUrl(url)) {
            Toast.makeText(context, "Only valid HTTP or HTTPS addresses can be opened", Toast.LENGTH_SHORT).show()
            return
        }
        tab.url = url
        if (url == "chrome://newtab") {
            showNewTabPage(true)
            return
        }
        showNewTabPage(false)
        updateUrlAndControls(url)
        tab.webView.loadUrl(url)
    }

    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun getActivity(): Context? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is AppCompatActivity || ctx is FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun destroy() {
        isDestroyed = true
        tabs.forEach { tab ->
            runCatching {
                tab.webView.stopLoading()
                tab.webView.webViewClient = WebViewClient()
                tab.webView.webChromeClient = null
                tab.webView.destroy()
            }
        }
        tabs.clear()
    }
}
