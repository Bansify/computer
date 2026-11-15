package com.gothwad.computer.ui.pc.settings

import android.app.Dialog
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.gothwad.computer.data.ConfigStore
import com.gothwad.computer.data.ForegroundShortcut
import com.gothwad.computer.data.ForegroundShortcuts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Launcher-foreground bindings only. No accessibility service or system key interception. */
class ShortcutSettingsDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        return when (requireArguments().getString("mode")) {
            "capture" -> {
                val action = requireArguments().getString("action").orEmpty()
                builder.setTitle("Press a shortcut key")
                    .setMessage("Use Ctrl + a letter or digit, F1–F12, or a remote colour key. Mapping applies only on Home, for this input device. Back cancels. Android may reserve keys.")
                    .setNegativeButton("Cancel", null)
                    .create().apply {
                        setOnKeyListener { _, keyCode, event ->
                            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT || keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) return@setOnKeyListener false
                            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return@setOnKeyListener false
                            val mods = (if (event.isCtrlPressed) ForegroundShortcuts.CTRL else 0) or
                                (if (event.isShiftPressed) ForegroundShortcuts.SHIFT else 0)
                            if (event.isAltPressed || event.isMetaPressed || !ForegroundShortcuts.allowed(keyCode, mods)) {
                                Toast.makeText(context, "Use Ctrl + letter/digit, F1–F12 or a remote colour key", Toast.LENGTH_SHORT).show()
                                return@setOnKeyListener true
                            }
                            val candidate = ForegroundShortcut(keyCode, mods, event.device?.descriptor.orEmpty(), action)
                            save(candidate)
                            dismiss()
                            true
                        }
                    }
            }
            "manage" -> builder.setTitle("Saved foreground shortcuts")
                .setMessage("Loading…")
                .setPositiveButton("Close", null).create()
            "clear" -> builder.setTitle("Clear foreground shortcuts?")
                .setMessage("Only new foreground bindings are removed; legacy button mappings are preserved.")
                .setPositiveButton("Clear") { _, _ -> clear() }
                .setNegativeButton("Cancel", null).create()
            else -> builder.setTitle("Foreground shortcuts")
                .setItems((ForegroundShortcuts.actions.map { ForegroundShortcuts.label(it) } + "View saved mappings").toTypedArray()) { _, index ->
                    if (index < ForegroundShortcuts.actions.size) capture(ForegroundShortcuts.actions[index]).show(parentFragmentManager, "shortcut_capture")
                    else manageDialog().show(parentFragmentManager, "shortcut_manage")
                }
                .setNeutralButton("Clear all") { _, _ -> clearDialog().show(parentFragmentManager, "shortcut_clear") }
                .setNegativeButton("Close", null).create()
        }
    }

    override fun onStart() {
        super.onStart()
        if (arguments?.getString("mode") != "manage") return
        lifecycleScope.launch {
            val text = try {
                val shortcuts = ConfigStore(requireContext()).flow.first().foregroundShortcuts
                if (shortcuts.isEmpty()) "No shortcuts configured." else shortcuts.joinToString("\n\n") { binding ->
                    val prefix = (if (binding.modifiers and ForegroundShortcuts.CTRL != 0) "Ctrl + " else "") +
                        (if (binding.modifiers and ForegroundShortcuts.SHIFT != 0) "Shift + " else "")
                    "${ForegroundShortcuts.label(binding.action)}: $prefix${KeyEvent.keyCodeToString(binding.keyCode).removePrefix("KEYCODE_")} (device ${binding.deviceDescriptor.hashCode().toUInt().toString(16)})"
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { "Settings could not be read." }
            (dialog as? AlertDialog)?.setMessage(text)
        }
    }

    private fun save(candidate: ForegroundShortcut) {
        val owner = activity ?: return
        owner.lifecycleScope.launch {
            try {
                var conflict = false
                ConfigStore(owner).update { config ->
                    conflict = ForegroundShortcuts.conflicts(config.foregroundShortcuts, candidate)
                    if (conflict) config else config.copy(foregroundShortcuts = config.foregroundShortcuts + candidate)
                }
                Toast.makeText(owner, if (conflict) "Key already mapped on this device; clear mappings to change it" else "Shortcut saved for this input device", Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Toast.makeText(owner, "Could not save shortcuts; settings were not reset", Toast.LENGTH_LONG).show() }
        }
    }

    private fun clear() {
        val owner = activity ?: return
        owner.lifecycleScope.launch {
            try {
                ConfigStore(owner).update { it.copy(foregroundShortcuts = emptyList()) }
                Toast.makeText(owner, "Foreground shortcuts cleared", Toast.LENGTH_SHORT).show()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Toast.makeText(owner, "Could not clear shortcuts", Toast.LENGTH_LONG).show() }
        }
    }

    companion object {
        fun create() = ShortcutSettingsDialogFragment().apply { arguments = Bundle().apply { putString("mode", "list") } }
        private fun capture(action: String) = ShortcutSettingsDialogFragment().apply {
            arguments = Bundle().apply { putString("mode", "capture"); putString("action", action) }
        }
        private fun manageDialog() = ShortcutSettingsDialogFragment().apply { arguments = Bundle().apply { putString("mode", "manage") } }
        private fun clearDialog() = ShortcutSettingsDialogFragment().apply { arguments = Bundle().apply { putString("mode", "clear") } }
    }
}
