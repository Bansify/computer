package com.gothwad.computer

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gothwad.computer.data.ConfigStore
import com.gothwad.computer.data.ForegroundShortcut
import com.gothwad.computer.data.ForegroundShortcuts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForegroundShortcutSmokeTest {
    @Test fun configuredFunctionKeyOpensStart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ConfigStore(context)
        val original = store.flow.first().foregroundShortcuts
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F1)
        try {
            context.getSharedPreferences("startup_recovery", Context.MODE_PRIVATE).edit().clear().commit()
            store.update { it.copy(foregroundShortcuts = listOf(ForegroundShortcut(KeyEvent.KEYCODE_F1, 0, down.device?.descriptor.orEmpty(), ForegroundShortcuts.START))) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var ready = false
                repeat(80) {
                    if (!ready) {
                        scenario.onActivity { ready = it.findViewById<View>(R.id.btn_start) != null }
                        if (!ready) SystemClock.sleep(250)
                    }
                }
                assertTrue("Desktop not ready", ready)
                scenario.onActivity { activity ->
                    val start = activity.findViewById<View>(R.id.btn_start)
                    val menu = activity.findViewById<View>(R.id.container_start_menu)
                    start.requestFocus()
                    assertTrue(activity.dispatchKeyEvent(down))
                    assertEquals(View.VISIBLE, menu.visibility)
                    assertTrue(activity.dispatchKeyEvent(up))
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertEquals(View.GONE, menu.visibility)
                    val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    val editor = EditText(activity)
                    root.addView(editor)
                    editor.requestFocus()
                    assertTrue(editor.hasFocus())
                    activity.dispatchKeyEvent(down)
                    activity.dispatchKeyEvent(up)
                    assertEquals("Text entry must not trigger launcher shortcuts", View.GONE, menu.visibility)
                    root.removeView(editor)
                }
            }
        } finally { store.update { it.copy(foregroundShortcuts = original) } }
    }
}
