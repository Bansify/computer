package com.gothwad.computer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gothwad.computer.data.AppEntry
import com.gothwad.computer.data.AppRepository
import com.gothwad.computer.data.BluetoothDeviceStatus
import com.gothwad.computer.data.ConfigStore
import com.gothwad.computer.data.LauncherConfig
import com.gothwad.computer.data.MODE_PC
import com.gothwad.computer.data.NetStatus
import com.gothwad.computer.data.HomeSettings
import com.gothwad.computer.data.StartupRecovery
import com.gothwad.computer.databinding.ViewSafeHomeBinding
import com.gothwad.computer.databinding.ActivityMainBinding
import com.gothwad.computer.ui.dialogs.NotificationBottomSheetFragment
import com.gothwad.computer.ui.dialogs.PinEntryDialogFragment
import com.gothwad.computer.ui.dialogs.QuickDashboardDialogFragment
import com.gothwad.computer.ui.dialogs.SearchDialogFragment
import com.gothwad.computer.ui.pc.settings.PcSettingsConstants
import com.gothwad.computer.ui.pc.settings.PcSettingsDialogFragment
import com.gothwad.computer.ui.view.DeviceLockViewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var configReady = false
    private var normalDesktopStarted = false
    private var recoveryMode = false
    private var safeBinding: ViewSafeHomeBinding? = null

    /** Bumped whenever a package is installed/removed so the app grid rescans. */
    var rescanTick: Int = 0
        private set

    private var currentConfig: LauncherConfig = LauncherConfig(launcherMode = MODE_PC)
    private var currentNetStatus: NetStatus = NetStatus()
    private var currentBtStatus: BluetoothDeviceStatus = BluetoothDeviceStatus()
    private var allApps: List<AppEntry> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            val launchable = pkg != null && packageManager.getLaunchIntentForPackage(pkg) != null
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED || launchable) {
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
        val lang = newBase.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
            .getString(LOCALE_KEY, "").orEmpty()
        val contextWithLocale = if (lang.isEmpty()) newBase else applyLocale(newBase, lang)
        val contextWithDpi = com.gothwad.computer.data.DpiHelper.wrapContext(contextWithLocale)
        super.attachBaseContext(contextWithDpi)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null && com.gothwad.computer.data.DpiHelper.isCustomDpiEnabled(this)) {
            overrideConfiguration.densityDpi = com.gothwad.computer.data.DpiHelper.getCustomDpiValue(this)
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        recoveryMode = StartupRecovery.shouldUseSafeMode(this)
        // Avoid restoring the failing desktop fragment when entering recovery.
        super.onCreate(if (recoveryMode) null else savedInstanceState)

        if (com.gothwad.computer.data.DpiHelper.isCustomDpiEnabled(this)) {
            com.gothwad.computer.data.DpiHelper.applyToResources(
                resources,
                com.gothwad.computer.data.DpiHelper.getCustomDpiValue(this)
            )
        }

        // Handle Back when device is locked so it cannot be bypassed
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!configReady || (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled)) {
                    // Stay on device lock screen
                    return
                }
                // Allow standard back navigation
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Do not attach a new desktop before config has been read and the lock satisfied.
        checkDeviceLockOnColdStart()
    }

    private var deviceLockController: DeviceLockViewController? = null

    private fun checkDeviceLockOnColdStart() {
        binding.deviceLockContainer.visibility = View.VISIBLE
        binding.deviceLockContainer.bringToFront()
        lifecycleScope.launch {
            val config = try {
                ConfigStore(this@MainActivity).flow.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep stored bytes untouched and do not assume an unreadable lock is disabled.
                android.util.Log.w("MainActivity", "Launcher configuration unavailable", e)
                showRecovery(configReadable = false)
                return@launch
            }
            currentConfig = config
            if (config.deviceLock.enabled && config.deviceLock.value.isEmpty()) {
                // Inconsistent credential must not be interpreted as an unlocked device.
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
        if (recoveryMode) showRecovery(configReadable = true) else startNormalDesktop()
    }

    private fun startNormalDesktop() {
        if (!configReady || !GothwadApplication.hasUnlockedDeviceThisProcess) return
        safeBinding = null
        binding.deviceLockContainer.removeAllViews()
        binding.deviceLockContainer.visibility = View.GONE
        try {
            if (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) == null) {
                val navHost = NavHostFragment.create(R.navigation.nav_graph)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, navHost)
                    .setPrimaryNavigationFragment(navHost)
                    .commitNow()
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
            android.util.Log.e("MainActivity", "Desktop startup failed", e)
            recoveryMode = true
            showRecovery(configReadable = true)
        }
    }

    private fun showRecovery(configReadable: Boolean) {
        normalDesktopStarted = false
        val view = ViewSafeHomeBinding.inflate(layoutInflater)
        safeBinding = view
        binding.deviceLockContainer.removeAllViews()
        binding.deviceLockContainer.addView(view.root)
        binding.deviceLockContainer.visibility = View.VISIBLE
        binding.deviceLockContainer.bringToFront()
        view.safeHomeMessage.setText(if (configReadable) R.string.safe_home_message else R.string.safe_home_config_error)
        view.safeRetry.visibility = if (configReadable) View.VISIBLE else View.GONE
        view.safeAppsLabel.visibility = if (configReadable) View.VISIBLE else View.GONE
        view.safeRetry.setOnClickListener { startNormalDesktop() }
        view.safeDefaultHome.setOnClickListener {
            if (!HomeSettings.open(this)) Toast.makeText(this, R.string.safe_home_no_settings, Toast.LENGTH_SHORT).show()
        }
        view.safeSystemSettings.setOnClickListener {
            if (!HomeSettings.openSystemSettings(this)) Toast.makeText(this, R.string.safe_home_no_settings, Toast.LENGTH_SHORT).show()
        }
        (if (configReadable) view.safeRetry else view.safeDefaultHome).requestFocus()
        if (configReadable) loadSafeApps(view)
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
                        }.sortedBy { it.label.lowercase() }
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
            if (com.gothwad.computer.service.FloatingTaskbarService.canDrawOverlays(this)) {
                com.gothwad.computer.service.FloatingTaskbarService.startIfEnabled(this)
            }
        }.onFailure { android.util.Log.w("MainActivity", "Overlay unavailable", it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Explicit intents may originate from another app; never bypass lock/startup checks.
        if (!configReady || !GothwadApplication.hasUnlockedDeviceThisProcess || !normalDesktopStarted) return
        when (intent.action) {
            "ACTION_SEARCH" -> {
                SearchDialogFragment.newInstance(
                    apps = allApps,
                    config = currentConfig,
                    onLaunch = { app -> handleAppLaunch(app) }
                ).show(supportFragmentManager, SearchDialogFragment.TAG)
            }
            "ACTION_NOTIFICATIONS" -> {
                NotificationBottomSheetFragment.newInstance()
                    .show(supportFragmentManager, NotificationBottomSheetFragment.TAG)
            }
            "ACTION_SETTINGS" -> {
                openSettingsDialog()
            }
            "ACTION_QUICK_SETTINGS" -> {
                QuickDashboardDialogFragment.newInstance(
                    net = currentNetStatus,
                    bt = currentBtStatus,
                    onOpenSettings = { openSettingsDialog() }
                ).show(supportFragmentManager, QuickDashboardDialogFragment.TAG)
            }
            "OPEN_APP" -> {
                val pkg = intent.getStringExtra("EXTRA_PKG")
                val app = allApps.firstOrNull { it.pkg == pkg }
                if (app != null) handleAppLaunch(app)
            }
        }
    }

    private fun openSettingsDialog() {
        if (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled) {
            return
        }
        PcSettingsDialogFragment.newInstance(
            initialTab = PcSettingsConstants.TAB_SYSTEM,
            onWallpaperChanged = { /* handled by config observer in PcLauncherFragment */ }
        ).show(supportFragmentManager, PcSettingsDialogFragment.TAG)
    }

    private fun handleAppLaunch(app: AppEntry, skipLock: Boolean = false) {
        if (!configReady || !GothwadApplication.hasUnlockedDeviceThisProcess) return
        if (!skipLock && currentConfig.appLock.enabled && currentConfig.appLock.value.isNotEmpty() && app.pkg in currentConfig.lockedApps) {
            PinEntryDialogFragment.newInstance(
                title = "App Locked",
                subtitle = "Enter PIN/Password to launch ${app.label}",
                credential = currentConfig.appLock,
                isCancelable = true,
                onSuccess = {
                    handleAppLaunch(app, skipLock = true)
                }
            ).show(supportFragmentManager, PinEntryDialogFragment.TAG)
        } else {
            Actions.launchApp(this, app.pkg)
        }
    }

    private fun observeConfig() {
        val configStore = ConfigStore(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                configStore.flow.collectLatest { config ->
                    currentConfig = config
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (safeBinding == null && (!configReady || (!GothwadApplication.hasUnlockedDeviceThisProcess && currentConfig.deviceLock.enabled))) {
            if (deviceLockController?.handleKeyEvent(event.keyCode, event) == true) {
                return true
            }
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
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
