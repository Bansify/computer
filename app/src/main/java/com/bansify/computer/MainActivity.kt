package com.bansify.computer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.Toast
import android.widget.TextView
import android.widget.FrameLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bansify.computer.data.AppEntry
import com.bansify.computer.data.AppRepository
import com.bansify.computer.data.BluetoothDeviceStatus
import com.bansify.computer.data.ConfigStore
import com.bansify.computer.data.LauncherConfig
import com.bansify.computer.data.LockCredential
import com.bansify.computer.data.LaunchAuthorization
import com.bansify.computer.data.ForegroundShortcuts
import com.bansify.computer.data.MODE_PC
import com.bansify.computer.data.NetStatus
import com.bansify.computer.data.HomeSettings
import com.bansify.computer.data.StartupRecovery
import com.bansify.computer.data.StartupDiagnostics
import com.bansify.computer.data.StartupIssue
import com.bansify.computer.databinding.ViewSafeHomeBinding
import com.bansify.computer.databinding.ActivityMainBinding
import com.bansify.computer.ui.dialogs.NotificationBottomSheetFragment
import com.bansify.computer.ui.dialogs.PinEntryDialogFragment
import com.bansify.computer.ui.dialogs.QuickDashboardDialogFragment
import com.bansify.computer.ui.dialogs.SearchDialogFragment
import com.bansify.computer.ui.pc.settings.PcSettingsConstants
import com.bansify.computer.ui.pc.settings.PcSettingsDialogFragment
import com.bansify.computer.ui.view.DeviceLockViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var configReady = false
    private var normalDesktopStarted = false
    private var recoveryMode = false
    private var safeBinding: ViewSafeHomeBinding? = null
    private val handledShortcutKeys = mutableSetOf<Pair<Int, Int>>()

    /** Bumped whenever a package is installed/removed so the app grid rescans. */
    var rescanTick: Int = 0
        private set

    private var currentConfig: LauncherConfig = LauncherConfig(launcherMode = MODE_PC)
    private var currentNetStatus: NetStatus = NetStatus()
    private var currentBtStatus: BluetoothDeviceStatus = BluetoothDeviceStatus()
    private var allApps: List<AppEntry> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (normalDesktopStarted) {
                rescanTick++
                refreshAppsList()
            }
        }
    }

    private fun refreshAppsList() {
        lifecycleScope.launch {
            try {
                val scanned = withContext(Dispatchers.IO) { AppRepository.scan(this@MainActivity) }
                allApps = scanned
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Unable to refresh app list", e)
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = runCatching {
            newBase.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE).getString(LOCALE_KEY, "").orEmpty()
        }.getOrDefault("")
        val contextWithLocale = if (lang.isEmpty()) newBase else runCatching { applyLocale(newBase, lang) }.getOrDefault(newBase)
        val contextWithDpi = runCatching { com.bansify.computer.data.DpiHelper.wrapContext(contextWithLocale) }.getOrDefault(contextWithLocale)
        super.attachBaseContext(contextWithDpi)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        runCatching {
            if (overrideConfiguration != null && com.bansify.computer.data.DpiHelper.isCustomDpiEnabled(this)) {
                overrideConfiguration.densityDpi = com.bansify.computer.data.DpiHelper.getCustomDpiValue(this)
            }
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        recoveryMode = StartupRecovery.shouldUseSafeMode(this)
        // Do not restore a desktop fragment before configuration/lock checks. Persisted
        // launcher settings remain intact; transient windows/dialogs are not restored.
        super.onCreate(null)
        // Only the debug source set has a fault implementation; never crash recovery itself.
        if (!recoveryMode) StartupTestHooks.beforeStartup(intent)

        runCatching {
            if (com.bansify.computer.data.DpiHelper.isCustomDpiEnabled(this)) {
                com.bansify.computer.data.DpiHelper.applyToResources(
                    resources, com.bansify.computer.data.DpiHelper.getCustomDpiValue(this)
                )
            }
        }

        // Handle Back when device is locked so it cannot be bypassed
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (safeBinding != null) {
                    safeBinding?.safeRetry?.requestFocus()
                    return
                }
                if (!configReady || (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled)) {
                    // Stay on device lock screen
                    return
                }
                // Allow standard back navigation
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        runCatching { ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        ) }.onFailure { android.util.Log.w("MainActivity", "Package change listener unavailable") }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Do not attach a new desktop before config has been read and the lock satisfied.
        checkDeviceLockOnColdStart()
    }

    private var deviceLockController: DeviceLockViewController? = null

    private fun checkDeviceLockOnColdStart() {
        binding.deviceLockContainer.removeAllViews()
        binding.deviceLockContainer.addView(TextView(this).apply {
            setText(R.string.safe_home_loading)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        binding.deviceLockContainer.visibility = View.VISIBLE
        binding.deviceLockContainer.bringToFront()
        lifecycleScope.launch {
            val config = try {
                withTimeout(15_000) { ConfigStore(this@MainActivity).flow.first() }
            } catch (e: TimeoutCancellationException) {
                StartupDiagnostics.record(this@MainActivity, StartupIssue.CONFIG_TIMEOUT, e)
                showRecovery(configReadable = false)
                return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep stored bytes untouched and do not assume an unreadable lock is disabled.
                android.util.Log.w("MainActivity", "Launcher configuration unavailable")
                StartupDiagnostics.record(this@MainActivity, StartupIssue.CONFIG_READ_FAILURE, e)
                showRecovery(configReadable = false)
                return@launch
            }
            currentConfig = config
            if ((config.deviceLock.enabled && config.deviceLock.value.isEmpty()) ||
                (config.appLock.enabled && config.lockedApps.isNotEmpty() && config.appLock.value.isEmpty())) {
                // Inconsistent credentials must not be interpreted as unlocked.
                StartupDiagnostics.record(this@MainActivity, StartupIssue.CONFIG_CREDENTIAL_INCOMPLETE)
                showRecovery(configReadable = false)
                return@launch
            }
            configReady = true
            if (!GothwadApplication.hasUnlockedDeviceThisProcess &&
                config.deviceLock.enabled && config.deviceLock.value.isNotEmpty()) {
                deviceLockController = DeviceLockViewController(
                    container = binding.deviceLockContainer,
                    credential = config.deviceLock,
                    onUnlocked = {
                        GothwadApplication.hasUnlockedDeviceThisProcess = true
                        deviceLockController = null
                        afterUnlock()
                    }
                )
                deviceLockController?.show()
            } else {
                GothwadApplication.hasUnlockedDeviceThisProcess = true
                binding.deviceLockContainer.visibility = View.GONE
                afterUnlock()
            }
        }
    }

    private fun afterUnlock() {
        if (recoveryMode) {
            StartupDiagnostics.record(this, StartupIssue.PREVIOUS_STARTUP_FAILURES)
            showRecovery(configReadable = true)
        } else startNormalDesktop()
    }

    private fun startNormalDesktop() {
        if (!configReady || !GothwadApplication.hasUnlockedDeviceThisProcess) return
        if (isFinishing || isDestroyed) return
        safeBinding = null
        binding.deviceLockContainer.removeAllViews()
        binding.deviceLockContainer.visibility = View.GONE
        try {
            StartupTestHooks.beforeDesktop(intent)
            if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) == null) {
                val navHost = NavHostFragment.create(R.navigation.nav_graph)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, navHost)
                    .setPrimaryNavigationFragment(navHost)
                    .commitNowAllowingStateLoss()
            }
            normalDesktopStarted = true
            startOverlayIfAllowed()
            observeConfig()
            refreshAppsList()
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    // A foreground stability window; a pause restarts it.
                    delay(30_000)
                    StartupRecovery.markHealthy(this@MainActivity)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Desktop startup failed (${e.javaClass.name})")
            StartupDiagnostics.record(this, StartupIssue.DESKTOP_START_FAILURE, e)
            runCatching { StartupRecovery.recordUncaughtStartupCrash(this) }
            runCatching {
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commitNow()
                }
            }
            recoveryMode = true
            showRecovery(configReadable = true)
        }
    }

    private fun showRecovery(configReadable: Boolean) {
        normalDesktopStarted = false
        if (!configReadable) {
            configReady = false
            GothwadApplication.hasUnlockedDeviceThisProcess = false
            // A restored desktop or its observers must not run behind unreadable settings.
            runCatching {
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment)?.let {
                    supportFragmentManager.beginTransaction().remove(it).commitNowAllowingStateLoss()
                }
            }
        }
        runCatching { com.bansify.computer.service.FloatingTaskbarService.stop(this) }
        val view = ViewSafeHomeBinding.inflate(layoutInflater)
        safeBinding = view
        binding.deviceLockContainer.removeAllViews()
        binding.deviceLockContainer.addView(view.root)
        binding.deviceLockContainer.visibility = View.VISIBLE
        binding.deviceLockContainer.bringToFront()
        view.safeHomeMessage.setText(if (configReadable) R.string.safe_home_message else R.string.safe_home_config_error)
        view.safeRetry.setText(if (configReadable) R.string.safe_home_retry else R.string.safe_home_retry_config)
        view.safeAppsLabel.visibility = if (configReadable) View.VISIBLE else View.GONE
        view.safeRetry.setOnClickListener {
            if (configReadable) startNormalDesktop() else {
                safeBinding = null
                binding.deviceLockContainer.removeAllViews()
                checkDeviceLockOnColdStart()
            }
        }
        view.safeDefaultHome.setOnClickListener {
            if (!HomeSettings.open(this)) Toast.makeText(this, R.string.safe_home_no_settings, Toast.LENGTH_SHORT).show()
        }
        view.safeSystemSettings.setOnClickListener {
            if (!HomeSettings.openSystemSettings(this)) Toast.makeText(this, R.string.safe_home_no_settings, Toast.LENGTH_SHORT).show()
        }
        view.safeDiagnostics.setOnClickListener { shareDiagnostics() }
        view.safeRetry.requestFocus()
        if (configReadable) loadSafeApps(view)
    }

    private fun shareDiagnostics() {
        val file = File(filesDir, GothwadApplication.CRASH_LOG_FILE)
        if (!file.isFile || file.length() == 0L) {
            Toast.makeText(this, R.string.safe_home_no_diagnostics, Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("diagnostics", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, getString(R.string.safe_home_share_diagnostics)))
        }.onFailure { Toast.makeText(this, R.string.safe_home_share_unavailable, Toast.LENGTH_SHORT).show() }
    }

    private fun loadSafeApps(view: ViewSafeHomeBinding) {
        lifecycleScope.launch {
            val apps = try {
                withContext(Dispatchers.IO) {
                    val pm = packageManager
                    val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
                    categories.flatMap { category ->
                        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
                    }.distinctBy { it.activityInfo.packageName }
                        .filter { it.activityInfo.packageName != packageName && it.activityInfo.packageName !in currentConfig.hidden }
                        .map { info ->
                            val pkg = info.activityInfo.packageName
                            AppEntry(pkg, runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg), null, null, "apps", 0, 0L, 0L)
                        }.sortedBy { it.label.lowercase() }.take(100)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (safeBinding !== view) return@launch
            apps.forEach { app ->
                view.safeApps.addView(Button(this@MainActivity).apply {
                    text = app.label
                    setOnClickListener { handleAppLaunch(app) }
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startOverlayIfAllowed()
    }

    private fun startOverlayIfAllowed() {
        if (!normalDesktopStarted || !currentConfig.pcOverlayTaskbarEnabled) return
        runCatching {
            if (com.bansify.computer.service.FloatingTaskbarService.canDrawOverlays(this)) {
                com.bansify.computer.service.FloatingTaskbarService.startIfEnabled(this)
            }
        }.onFailure { android.util.Log.w("MainActivity", "Overlay unavailable", it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Explicit intents may originate from another app; never bypass lock/startup checks.
        if (!configReady || !GothwadApplication.hasUnlockedDeviceThisProcess || !normalDesktopStarted) return
        when (intent.action) {
            Intent.ACTION_MAIN -> if (intent.hasCategory(Intent.CATEGORY_HOME)) {
                val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                (host?.childFragmentManager?.primaryNavigationFragment as? com.bansify.computer.ui.pc.PcLauncherFragment)
                    ?.returnToDesktop()
            }
            "ACTION_SEARCH" -> {
                showDialogSafely(
                    SearchDialogFragment.newInstance(
                        apps = allApps,
                        config = currentConfig,
                        onLaunch = { app -> handleAppLaunch(app) }
                    ),
                    SearchDialogFragment.TAG
                )
            }
            "ACTION_NOTIFICATIONS" -> {
                showDialogSafely(
                    NotificationBottomSheetFragment.newInstance(),
                    NotificationBottomSheetFragment.TAG
                )
            }
            "ACTION_SETTINGS" -> {
                openSettingsDialog()
            }
            "ACTION_QUICK_SETTINGS" -> {
                showDialogSafely(
                    QuickDashboardDialogFragment.newInstance(
                        net = currentNetStatus,
                        bt = currentBtStatus,
                        onOpenSettings = { openSettingsDialog() }
                    ),
                    QuickDashboardDialogFragment.TAG
                )
            }
            "OPEN_APP" -> {
                val pkg = intent.getStringExtra("EXTRA_PKG")
                val app = allApps.firstOrNull { it.pkg == pkg }
                if (app != null) handleAppLaunch(app)
            }
        }
    }

    private fun showDialogSafely(dialog: androidx.fragment.app.DialogFragment, tag: String) {
        if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return
        if (supportFragmentManager.findFragmentByTag(tag) != null) return
        runCatching { dialog.show(supportFragmentManager, tag) }
    }

    private fun openSettingsDialog() {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        showDialogSafely(
            PcSettingsDialogFragment.newInstance(
                initialTab = PcSettingsConstants.TAB_SYSTEM,
                onWallpaperChanged = { /* handled by config observer in PcLauncherFragment */ }
            ),
            PcSettingsDialogFragment.TAG
        )
    }

    fun requestAppLaunch(app: AppEntry) = handleAppLaunch(app)

    private fun handleAppLaunch(app: AppEntry, authorized: LockCredential? = null) {
        if (!configReady || !GothwadApplication.hasUnlockedDeviceThisProcess) return
        when (LaunchAuthorization.decide(currentConfig, app.pkg, authorized)) {
            LaunchAuthorization.Decision.INVALID_CREDENTIAL ->
                Toast.makeText(this, "App lock configuration is incomplete; open lock settings", Toast.LENGTH_LONG).show()
            LaunchAuthorization.Decision.CHALLENGE -> {
                val credential = currentConfig.appLock
                showDialogSafely(
                    PinEntryDialogFragment.newInstance(
                        title = "App Locked",
                        subtitle = "Enter PIN/Password to launch ${app.label}",
                        credential = credential,
                        isCancelable = true,
                        onSuccess = { handleAppLaunch(app, authorized = credential) }
                    ),
                    PinEntryDialogFragment.TAG
                )
            }
            LaunchAuthorization.Decision.ALLOW -> Actions.launchApp(this, app.pkg)
        }
    }

    private fun observeConfig() {
        val configStore = ConfigStore(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                configStore.flow.catch { error ->
                    if (error is CancellationException) throw error
                    onConfigurationUnavailable(error)
                }.collectLatest { config ->
                    currentConfig = config
                }
            }
        }
    }

    fun onConfigurationUnavailable(error: Throwable? = null) {
        if (safeBinding != null && !configReady) return
        StartupDiagnostics.record(this, StartupIssue.CONFIG_OBSERVER_FAILURE, error)
        showRecovery(configReadable = false)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (safeBinding == null && (!configReady || (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled))) {
            if (deviceLockController?.handleKeyEvent(event.keyCode, event) == true) return true
            if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        }
        val key = event.deviceId to event.keyCode
        if (event.action == KeyEvent.ACTION_UP && handledShortcutKeys.remove(key)) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && key in handledShortcutKeys) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && configReady && normalDesktopStarted &&
            safeBinding == null && GothwadApplication.hasUnlockedDeviceThisProcess &&
            !event.isAltPressed && !event.isMetaPressed && !focusIsTextOrWeb()) {
            val modifiers = (if (event.isCtrlPressed) ForegroundShortcuts.CTRL else 0) or
                (if (event.isShiftPressed) ForegroundShortcuts.SHIFT else 0)
            val shortcut = ForegroundShortcuts.find(currentConfig.foregroundShortcuts, event.keyCode, modifiers, event.device?.descriptor.orEmpty())
            val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val desktop = host?.childFragmentManager?.primaryNavigationFragment as? com.bansify.computer.ui.pc.PcLauncherFragment
            if (shortcut != null && desktop?.handleForegroundShortcut(shortcut.action) == true) {
                handledShortcutKeys.add(key)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun focusIsTextOrWeb(): Boolean {
        var view: View? = currentFocus
        while (view != null) {
            if (view.onCheckIsTextEditor() || view is android.webkit.WebView) return true
            view = view.parent as? View
        }
        return false
    }

    override fun onPause() {
        handledShortcutKeys.clear()
        super.onPause()
    }

    override fun onDestroy() {
        handledShortcutKeys.clear()
        runCatching { unregisterReceiver(packageReceiver) }
        super.onDestroy()
    }

    companion object {
        private const val LOCALE_PREFS = "locale"
        private const val LOCALE_KEY = "lang"

        fun persistLocale(context: Context, lang: String) {
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .edit().putString(LOCALE_KEY, lang).apply()
        }

        fun currentLocalePref(context: Context): String =
            context.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .getString(LOCALE_KEY, "").orEmpty()

        fun applyLocale(context: Context, lang: String): Context {
            val locale = if (lang.contains('-')) {
                val parts = lang.split('-')
                Locale(parts[0], parts.getOrElse(1) { "" })
            } else Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        }
    }
}
