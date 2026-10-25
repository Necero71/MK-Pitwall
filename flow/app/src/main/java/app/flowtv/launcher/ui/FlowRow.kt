package app.flowtv.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/**
 * A horizontal shelf where the focused tile glides to a fixed spot on the left
 * and the row flows underneath it, instead of the stock jump-scroll.
 */
class FlowRow(context: Context) : HorizontalScrollView(context) {
    val strip = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    /** Where the focused tile settles, in pixels from the left edge. */
    var anchor: Int = 0
        set(v) {
            field = v
            strip.setPadding(v, dp(14), v, dp(14))
        }

    private var anim: ValueAnimator? = null

    init {
        isHorizontalScrollBarEnabled = false
        isSmoothScrollingEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        addView(strip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    // We do all the scrolling ourselves.
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?) = 0
    override fun executeKeyEvent(event: KeyEvent) = false

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (focused != null) glideTo(focused)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val left = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
        val right = event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        if ((left || right) && event.action == KeyEvent.ACTION_DOWN) {
            val tile = findFocus()?.let(::tileOf) ?: return super.dispatchKeyEvent(event)
            val next = strip.indexOfChild(tile) + if (left) -1 else 1
            // Stay inside the row: a little bump at the ends instead of a diagonal jump.
            if (next in 0 until strip.childCount) strip.getChildAt(next).requestFocus()
            else Motion.bump(tile, if (left) -1 else 1)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun glideTo(v: View, animate: Boolean = true) {
        val tile = tileOf(v) ?: return
        if (strip.isLayoutRequested || !tile.isLaidOut) {
            afterLayout { glideTo(tile, animate) }
            return
        }
        val max = maxOf(0, strip.width - width)
        val x = (tile.left - anchor).coerceIn(0, max)
        anim?.cancel()
        if (!animate || x == scrollX) {
            scrollTo(x, 0)
            return
        }
        anim = ValueAnimator.ofInt(scrollX, x).apply {
            duration = 380
            interpolator = Motion.glide
            addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
            start()
        }
    }

    private fun tileOf(v: View): View? {
        var c: View = v
        while (c.parent !== strip) c = c.parent as? View ?: return null
        return c
    }
}
