package com.bansify.computer.ui.dialogs

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.bansify.computer.R
import com.bansify.computer.data.AppEntry
import com.bansify.computer.data.LauncherConfig
import com.bansify.computer.databinding.DialogSearchBinding
import com.bansify.computer.ui.AppIcons

class SearchDialogFragment : DialogFragment() {

    private var _binding: DialogSearchBinding? = null
    private val binding get() = _binding!!

    private var allApps: List<AppEntry> = emptyList()
    private var config: LauncherConfig = LauncherConfig()
    private var adapter: SearchAppAdapter? = null
    var onLaunchApp: ((AppEntry) -> Unit)? = null

    private var currentFilter: SearchFilter = SearchFilter.ALL
    private var lastSubmittedList: List<AppEntry> = emptyList()

    // Track if hidden apps vault has satisfied the second-layer lock in this search session
    private var isVaultUnlockedThisSession: Boolean = false
    private var isPromptingVaultLock: Boolean = false

    private enum class SearchFilter { ALL, APPS, WEB }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_LiteTV_Dialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            @Suppress("DEPRECATION")
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Responsive Windows 10 bottom-left Start Search flyout dimensions
        val dm = resources.displayMetrics
        val panelWidth = (460 * dm.density).toInt().coerceAtMost((dm.widthPixels * 0.94f).toInt())
        val panelHeight = (500 * dm.density).toInt().coerceAtMost((dm.heightPixels * 0.78f).toInt())
        val panelLp = binding.containerSearchPanel.layoutParams as FrameLayout.LayoutParams
        panelLp.width = panelWidth
        panelLp.height = panelHeight
        val isWide = dm.widthPixels >= (600 * dm.density)
        panelLp.marginStart = if (isWide) (46 * dm.density).toInt() else ((dm.widthPixels - panelWidth) / 2).coerceAtLeast(0)
        val taskbarHeightDp = config.pcTaskbarHeight.coerceIn(32, 48)
        panelLp.bottomMargin = (taskbarHeightDp * config.pcUiScale.coerceIn(0.5f, 1.3f) * dm.density).toInt()
        binding.containerSearchPanel.layoutParams = panelLp

        binding.imgSearchStatusIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xE6D8E0E8.toInt()))
        binding.btnClearSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, 0xE6FFFFFF.toInt()))
        binding.btnCloseSearch.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, 0xE6FFFFFF.toInt()))
        binding.imgVaultLock.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK_OPEN, 0xFFFFB300.toInt()))

        binding.viewSearchBackdrop.setOnClickListener {
            dismissAllowingStateLoss()
        }

        adapter = SearchAppAdapter { app ->
            dismissAllowingStateLoss()
            onLaunchApp?.invoke(app)
        }

        binding.recyclerSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerSearchResults.adapter = adapter

        setupFilterTabs()
        updateAppList("")

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }

        binding.btnCloseSearch.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val b = _binding ?: return
                val q = s?.toString().orEmpty()
                b.btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                updateAppList(q)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Pressing Enter / Search launches the top best match (classic Windows 10 behavior)
        binding.etSearch.setOnEditorActionListener { _, actionId, event ->
            val isEnter = actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            if (isEnter && lastSubmittedList.isNotEmpty()) {
                val topMatch = lastSubmittedList.first()
                dismissAllowingStateLoss()
                onLaunchApp?.invoke(topMatch)
                true
            } else {
                false
            }
        }

        binding.etSearch.requestFocus()
    }

    private fun setupFilterTabs() {
        binding.tabSearchAll.setOnClickListener { selectFilter(SearchFilter.ALL) }
        binding.tabSearchApps.setOnClickListener { selectFilter(SearchFilter.APPS) }
        binding.tabSearchWeb.setOnClickListener { selectFilter(SearchFilter.WEB) }
        updateFilterTabVisuals()
    }

    private fun selectFilter(filter: SearchFilter) {
        if (currentFilter == filter) return
        currentFilter = filter
        updateFilterTabVisuals()
        updateAppList(_binding?.etSearch?.text?.toString().orEmpty())
    }

    private fun updateFilterTabVisuals() {
        val b = _binding ?: return
        val activeColor = Color.WHITE
        val inactiveColor = 0xFF9AA6B4.toInt()

        b.tvTabAll.setTextColor(if (currentFilter == SearchFilter.ALL) activeColor else inactiveColor)
        b.tvTabAll.setTypeface(null, if (currentFilter == SearchFilter.ALL) Typeface.BOLD else Typeface.NORMAL)
        b.indTabAll.visibility = if (currentFilter == SearchFilter.ALL) View.VISIBLE else View.INVISIBLE

        b.tvTabApps.setTextColor(if (currentFilter == SearchFilter.APPS) activeColor else inactiveColor)
        b.tvTabApps.setTypeface(null, if (currentFilter == SearchFilter.APPS) Typeface.BOLD else Typeface.NORMAL)
        b.indTabApps.visibility = if (currentFilter == SearchFilter.APPS) View.VISIBLE else View.INVISIBLE

        b.tvTabWeb.setTextColor(if (currentFilter == SearchFilter.WEB) activeColor else inactiveColor)
        b.tvTabWeb.setTypeface(null, if (currentFilter == SearchFilter.WEB) Typeface.BOLD else Typeface.NORMAL)
        b.indTabWeb.visibility = if (currentFilter == SearchFilter.WEB) View.VISIBLE else View.INVISIBLE
    }

    private fun updateAppList(query: String) {
        val b = _binding ?: return
        val trimmed = query.trim()
        val isSecretMatch = if (trimmed.isEmpty()) false else {
            config.hiddenAppsRevealCode.isNotEmpty() && trimmed.equals(config.hiddenAppsRevealCode, ignoreCase = true)
        }

        if (isSecretMatch) {
            // Distinct second-layer confirmation before hidden apps actually render and launch
            if (config.hiddenAppsLock.enabled && config.hiddenAppsLock.value.isNotEmpty() && !isVaultUnlockedThisSession) {
                promptHiddenAppsConfirmation()
                return
            }

            renderHiddenAppsVault()
        } else {
            isVaultUnlockedThisSession = false
            b.bannerSecretVault.visibility = View.GONE
            b.imgSearchStatusIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SEARCH, 0xE6D8E0E8.toInt()))

            val baseList = allApps.filter { app ->
                app.pkg !in config.hidden && when (currentFilter) {
                    SearchFilter.ALL -> true
                    SearchFilter.APPS -> !app.pkg.startsWith("pwa://")
                    SearchFilter.WEB -> app.pkg.startsWith("pwa://") || app.pkg == "com.bansify.computer.webapp"
                }
            }

            val visibleApps = if (trimmed.isEmpty()) {
                b.tvSearchSectionHeader.text = when (currentFilter) {
                    SearchFilter.ALL -> "Top apps"
                    SearchFilter.APPS -> "Installed apps"
                    SearchFilter.WEB -> "Web apps"
                }
                baseList
            } else {
                b.tvSearchSectionHeader.text = "Best match"
                baseList.filter {
                    val customLabel = config.pcCustomLabels[it.pkg] ?: it.label
                    customLabel.contains(trimmed, ignoreCase = true) ||
                        it.label.contains(trimmed, ignoreCase = true) ||
                        it.pkg.contains(trimmed, ignoreCase = true)
                }
            }

            lastSubmittedList = visibleApps
            adapter?.submitList(visibleApps)
            b.recyclerSearchResults.visibility = if (visibleApps.isEmpty()) View.GONE else View.VISIBLE
            b.tvEmptyState.visibility = if (visibleApps.isEmpty()) View.VISIBLE else View.GONE
            if (visibleApps.isEmpty()) {
                b.tvEmptyState.text = if (trimmed.isEmpty()) "No apps available in this category" else "No results for \"$trimmed\""
            }
        }
    }

    private fun promptHiddenAppsConfirmation() {
        val b = _binding ?: return
        if (isPromptingVaultLock || !isAdded || parentFragmentManager.isStateSaved) return
        if (parentFragmentManager.findFragmentByTag("HiddenAppsVaultConfirm") != null) return
        isPromptingVaultLock = true

        // Hide search results list until confirmed
        lastSubmittedList = emptyList()
        adapter?.submitList(emptyList())
        b.bannerSecretVault.visibility = View.GONE
        b.tvEmptyState.visibility = View.GONE

        runCatching {
            PinEntryDialogFragment.newInstance(
                title = "Hidden Apps Vault",
                subtitle = "Enter second-layer credential to access hidden apps",
                credential = config.hiddenAppsLock,
                isCancelable = true,
                onSuccess = {
                    isVaultUnlockedThisSession = true
                    isPromptingVaultLock = false
                    renderHiddenAppsVault()
                },
                onCancelled = {
                    isPromptingVaultLock = false
                    if (!isVaultUnlockedThisSession) {
                        _binding?.etSearch?.setText("")
                    }
                }
            ).show(parentFragmentManager, "HiddenAppsVaultConfirm")
        }.onFailure {
            isPromptingVaultLock = false
        }
    }

    private fun renderHiddenAppsVault() {
        val b = _binding ?: return
        b.bannerSecretVault.visibility = View.VISIBLE
        b.tvSearchSectionHeader.text = "Hidden apps"
        b.imgSearchStatusIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_LOCK_OPEN, 0xFFFFB300.toInt()))
        val hiddenApps = allApps.filter { it.pkg in config.hidden }
        lastSubmittedList = hiddenApps
        adapter?.submitList(hiddenApps)
        b.recyclerSearchResults.visibility = if (hiddenApps.isEmpty()) View.GONE else View.VISIBLE
        b.tvEmptyState.visibility = if (hiddenApps.isEmpty()) View.VISIBLE else View.GONE
        if (hiddenApps.isEmpty()) {
            b.tvEmptyState.text = "No apps are currently hidden"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SearchDialog"

        fun newInstance(
            apps: List<AppEntry>,
            config: LauncherConfig,
            onLaunch: (AppEntry) -> Unit
        ): SearchDialogFragment {
            return SearchDialogFragment().apply {
                this.allApps = apps
                this.config = config
                this.onLaunchApp = onLaunch
            }
        }
    }
}
