package app.flowtv.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.widget.ScrollView

/** Vertical counterpart of [FlowRow]: keeps the focused item gliding towards the middle. */
class FlowScroll(context: Context) : ScrollView(context) {
    /** Caps the height when wrapping content (used by sheets). 0 means no cap. */
    var maxHeight = 0

    private var anim: ValueAnimator? = null

    init {
        isVerticalScrollBarEnabled = false
        isSmoothScrollingEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        isFocusable = false
    }

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?) = 0
    override fun executeKeyEvent(event: KeyEvent) = false

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        if (focused != null) glideTo(focused)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val spec = if (maxHeight > 0) MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST) else heightMeasureSpec
        super.onMeasure(widthMeasureSpec, spec)
    }

    fun glideTo(v: View, animate: Boolean = true) {
        val content = getChildAt(0) ?: return
        if (isLayoutRequested || !v.isLaidOut) {
            afterLayout { if (v.isAttachedToWindow) glideTo(v, animate) }
            return
        }
        val r = Rect()
        v.getDrawingRect(r)
        offsetDescendantRectToMyCoords(v, r)
        val max = maxOf(0, content.height - height)
        val y = (r.centerY() - height / 2).coerceIn(0, max)
        anim?.cancel()
        if (!animate || y == scrollY) {
            scrollTo(0, y)
            return
        }
        anim = ValueAnimator.ofInt(scrollY, y).apply {
            duration = 380
            interpolator = Motion.glide
            addUpdateListener { scrollTo(0, it.animatedValue as Int) }
            start()
        }
    }
}
