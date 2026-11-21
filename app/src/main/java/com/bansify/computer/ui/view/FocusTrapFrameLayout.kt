package com.bansify.computer.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.View
import android.view.ViewParent
import android.widget.FrameLayout

/** Keep directional focus inside an open flyout; Back dismisses it in the host. */
class FocusTrapFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    override fun focusSearch(focused: View, direction: Int): View? {
        var ancestor: ViewParent? = focused.parent
        while (ancestor != null && ancestor !== this) ancestor = ancestor.parent
        if (ancestor !== this || !isShown) return super.focusSearch(focused, direction)
        return runCatching { FocusFinder.getInstance().findNextFocus(this, focused, direction) }.getOrNull() ?: focused
    }
}
