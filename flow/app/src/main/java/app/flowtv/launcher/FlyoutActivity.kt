package app.flowtv.launcher

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import app.flowtv.launcher.ui.Motion
import app.flowtv.launcher.ui.dp
import app.flowtv.launcher.ui.ChamferFrame
import app.flowtv.launcher.ui.chamfer

/**
 * A floating panel over whatever is behind it, like the Windows Start menu
 * (rises from the bottom) or notification centre (slides in from the right).
 * Back slides it away; opening something else from it closes it.
 */
abstract class FlyoutActivity : BaseActivity() {
    protected enum class Edge { END, BOTTOM }

    override val opaque = false

    private var edge = Edge.END
    private var scrim: View? = null
    private var panel: View? = null
    private var closing = false

    private val dim get() = if (pal.dark) 0.55f else 0.32f

    /** Hosts [content] in the panel. [entering] is false when rebuilt for a theme change. */
    protected fun setFlyout(content: View, edge: Edge, width: Int, height: Int, entering: Boolean) {
        this.edge = edge
        val root = FrameLayout(this)
        val scrim = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
        }
        root.addView(scrim, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val m = dp(16)
        val cut = dp(16).toFloat()
        val panel = ChamferFrame(this, cut).apply {
            background = chamfer(pal.raised, cut, dp(1), pal.line)
            elevation = dp(16).toFloat()
            addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        val lp = when (edge) {
            Edge.END -> FrameLayout.LayoutParams(width, MATCH_PARENT, Gravity.END).apply { setMargins(m, m, m, m) }
            Edge.BOTTOM -> FrameLayout.LayoutParams(width, height, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                .apply { bottomMargin = dp(24) }
        }
        root.addView(panel, lp)
        setContentView(root)
        this.scrim = scrim
        this.panel = panel

        if (!entering) {
            scrim.alpha = dim
            return
        }
        when (edge) {
            Edge.END -> panel.translationX = (width + m).toFloat()
            Edge.BOTTOM -> {
                panel.translationY = dp(64).toFloat()
                panel.alpha = 0f
                panel.scaleX = 0.97f
                panel.scaleY = 0.97f
            }
        }
        panel.animate().translationX(0f).translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(if (edge == Edge.END) 440L else 400L).setInterpolator(Motion.glide).start()
        scrim.animate().alpha(dim).setDuration(320).start()
    }

    /** Slides the panel away, then closes. */
    override fun finish() {
        if (closing) return
        val panel = panel ?: return closeNow()
        closing = true
        val out = panel.animate().setStartDelay(0).setDuration(230).setInterpolator(Motion.exit)
        when (edge) {
            Edge.END -> out.translationX((panel.width + dp(16)).toFloat())
            Edge.BOTTOM -> out.translationY(dp(48).toFloat()).alpha(0f)
        }
        out.withEndAction { closeNow() }.start()
        scrim?.animate()?.alpha(0f)?.setDuration(230)?.start()
    }

    /** Closes without the slide, e.g. once an app has already covered the flyout. */
    protected fun closeNow() {
        closing = true
        if (isFinishing) return
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onStop() {
        super.onStop()
        // Something opened over us (an app, TV settings…): like a Start menu, we're done.
        if (!isChangingConfigurations) closeNow()
    }
}
