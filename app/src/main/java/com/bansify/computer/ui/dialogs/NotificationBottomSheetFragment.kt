package com.bansify.computer.ui.dialogs

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bansify.computer.Actions
import com.bansify.computer.R
import com.bansify.computer.data.HomeSettings
import com.bansify.computer.databinding.SheetNotificationsBinding
import com.bansify.computer.service.NotificationManagerBridge
import com.bansify.computer.ui.AppIcons
import com.bansify.computer.ui.pc.settings.PcSettingsDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NotificationBottomSheetFragment : DialogFragment() {

    private var _binding: SheetNotificationsBinding? = null
    private val binding get() = _binding!!

    private var adapter: NotificationAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_LiteTV_Dialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setGravity(Gravity.END)
            window.setWindowAnimations(R.style.Animation_TvSettingsPanel)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Responsive right-side panel width (360dp max, fits cleanly on compact phones too)
        val dm = resources.displayMetrics
        val targetWidth = (360 * dm.density).toInt().coerceAtMost((dm.widthPixels * 0.92f).toInt())
        binding.panelContainer.layoutParams = binding.panelContainer.layoutParams.apply {
            width = targetWidth
        }

        binding.imgSheetBell.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL_ACTIVE, 0xFF4FA7FA.toInt()))
        binding.btnCloseSheet.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_CLOSE, 0xE6FFFFFF.toInt()))
        binding.imgPermIcon.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_SHIELD, 0xFFFFB300.toInt()))
        binding.imgEmptyBell.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BELL, 0x4DFFFFFF.toInt()))

        // Bottom Windows 10 Quick Action Tiles
        binding.imgAcWifi.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_WIFI, 0xFF4FA7FA.toInt()))
        binding.imgAcBluetooth.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_BLUETOOTH, 0xFF4FA7FA.toInt()))
        binding.imgAcSettings.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_GEAR, 0xE6FFFFFF.toInt()))
        binding.imgAcDefaultHome.setImageDrawable(AppIcons.createDrawable(AppIcons.PATH_HOME, 0xFF4FA7FA.toInt()))

        binding.viewBackdrop.setOnClickListener {
            dismissAllowingStateLoss()
        }

        adapter = NotificationAdapter(
            onClick = { item ->
                val appCtx = context?.applicationContext ?: return@NotificationAdapter
                dismissAllowingStateLoss()
                NotificationManagerBridge.launchNotification(appCtx, item)
            },
            onDismiss = { item ->
                NotificationManagerBridge.dismissNotification(item.key)
            }
        )

        binding.recyclerNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerNotifications.adapter = adapter

        binding.btnClearAll.setOnClickListener {
            NotificationManagerBridge.clearAll()
        }

        binding.btnCloseSheet.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.btnEnablePerm.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
            dismissAllowingStateLoss()
        }

        // Quick Action Tile Handlers
        binding.tileAcWifi.setOnClickListener {
            val ctx = context?.applicationContext ?: return@setOnClickListener
            dismissAllowingStateLoss()
            Actions.openNetworkSettings(ctx)
        }

        binding.tileAcBluetooth.setOnClickListener {
            val ctx = context?.applicationContext ?: return@setOnClickListener
            dismissAllowingStateLoss()
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }

        binding.tileAcSettings.setOnClickListener {
            val fm = parentFragmentManager
            dismissAllowingStateLoss()
            if (!fm.isStateSaved && fm.findFragmentByTag(PcSettingsDialogFragment.TAG) == null) {
                runCatching {
                    PcSettingsDialogFragment.newInstance().show(fm, PcSettingsDialogFragment.TAG)
                }
            }
        }

        binding.tileAcDefaultHome.setOnClickListener {
            val ctx = context?.applicationContext ?: return@setOnClickListener
            dismissAllowingStateLoss()
            runCatching { HomeSettings.open(ctx) }
        }

        observeData()
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    private fun checkPermission() {
        val ctx = context ?: return
        val b = _binding ?: return
        val granted = NotificationManagerBridge.isNotificationAccessGranted(ctx)
        b.cardPermissionWarning.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationManagerBridge.notifications.collectLatest { list ->
                    val ctx = context ?: return@collectLatest
                    val b = _binding ?: return@collectLatest
                    val hasPerm = NotificationManagerBridge.isNotificationAccessGranted(ctx)
                    b.cardPermissionWarning.visibility = if (hasPerm) View.GONE else View.VISIBLE

                    b.tvNotifHeading.text = if (list.isNotEmpty()) "Action Center (${list.size})" else "Action Center"
                    b.btnClearAll.visibility = if (list.isNotEmpty()) View.VISIBLE else View.GONE

                    if (list.isEmpty()) {
                        b.recyclerNotifications.visibility = View.GONE
                        b.layoutEmptyState.visibility = View.VISIBLE
                    } else {
                        b.recyclerNotifications.visibility = View.VISIBLE
                        b.layoutEmptyState.visibility = View.GONE
                        adapter?.submitList(list)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "NotificationBottomSheet"

        fun newInstance(): NotificationBottomSheetFragment {
            return NotificationBottomSheetFragment()
        }
    }
}
