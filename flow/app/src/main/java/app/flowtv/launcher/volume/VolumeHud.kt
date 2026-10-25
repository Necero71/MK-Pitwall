package app.flowtv.launcher.volume

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.flowtv.launcher.Palette
import app.flowtv.launcher.R
import app.flowtv.launcher.Themes
import app.flowtv.launcher.ui.Meter
import app.flowtv.launcher.ui.Motion
import app.flowtv.launcher.ui.alpha
import app.flowtv.launcher.ui.dp
import app.flowtv.launcher.ui.look
import app.flowtv.launcher.ui.roundRect
import app.flowtv.launcher.ui.tinted

/**
 * A pill at the top of the screen with a speaker icon, a row of 20 ticks like a
 * rev counter, and the level in tan. It drops in, follows the level smoothly, and
 * lifts away after a moment. Muted turns the icon and number to the alert colour.
 *
 * Inside Flow it lives in the screen's own window. From the remote helper it is an
 * accessibility overlay, so it also shows over other apps.
 */
class VolumeHud private constructor(
    private val host: Context,
    private val window: Window?,
    val pal: Palette,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val view = LinearLayout(host)
    private val icon = ImageView(host)
    private val meter = Meter(host, trackFor(pal, muted = false), pal.accent, thumb = false, segments = 20)
    private val number = TextView(host).look(17f, pal.highlight, "sans-serif-condensed", android.graphics.Typeface.BOLD)
    private var attached = false
    private var visible = false
    private val hide = Runnable { sink() }

    init {
        view.orientation = LinearLayout.HORIZONTAL
        view.gravity = Gravity.CENTER_VERTICAL
        view.setPadding(host.dp(20), 0, host.dp(20), 0)
        view.background = roundRect(pal.raised.alpha(0.92f), host.dp(28).toFloat(), host.dp(1), pal.line)
        view.alpha = 0f
        view.addView(icon, LinearLayout.LayoutParams(host.dp(22), host.dp(22)))
        view.addView(
            meter,
            LinearLayout.LayoutParams(0, host.dp(16), 1f).apply {
                marginStart = host.dp(14)
                marginEnd = host.dp(14)
            },
        )
        number.gravity = Gravity.END
        view.addView(number, LinearLayout.LayoutParams(host.dp(36), WRAP_CONTENT))
    }

    private fun show(s: VolumeState) {
        attach()
        val alert = s.muted
        icon.setImageDrawable(tinted(host, s.icon, if (alert) pal.alert else pal.text))
        number.setTextColor(if (alert) pal.alert else pal.highlight)
        number.text = if (s.muted) "–" else s.percent.toString()
        meter.colors(trackFor(pal, alert), if (alert) pal.accent.alpha(0.4f) else pal.accent)
        meter.set(s.fraction, animate = visible)
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, 1800)
        if (!visible) {
            visible = true
            view.translationY = -host.dp(24).toFloat()
            view.animate().setStartDelay(0).alpha(1f).translationY(0f)
                .setDuration(300).setInterpolator(Motion.glide).start()
        }
    }

    private fun sink() {
        visible = false
        view.animate().setStartDelay(0).alpha(0f).translationY(-host.dp(16).toFloat())
            .setDuration(240).setInterpolator(Motion.standard)
            .withEndAction { if (window == null) detach() }
            .start()
    }

    private fun attach() {
        if (attached) return
        val w = host.dp(360)
        val h = host.dp(56)
        if (window != null) {
            (window.decorView as ViewGroup).addView(
                view,
                FrameLayout.LayoutParams(w, h, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                    .apply { topMargin = host.dp(28) },
            )
        } else {
            val lp = WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = host.dp(28)
            }
            host.getSystemService(WindowManager::class.java).addView(view, lp)
        }
        attached = true
    }

    private fun detach() {
        handler.removeCallbacks(hide)
        if (!attached) return
        attached = false
        if (window == null) runCatching { host.getSystemService(WindowManager::class.java).removeView(view) }
        else (view.parent as? ViewGroup)?.removeView(view)
    }

    companion object {
        private fun trackFor(pal: Palette, muted: Boolean) =
            if (pal.dark) 0xFF5C4630.toInt().alpha(if (muted) 0.4f else 1f) else pal.line.alpha(if (muted) 0.4f else 1f)

        private var overlay: VolumeHud? = null

        fun show(c: Context, s: VolumeState) {
            val window = (c as? Activity)?.window
            val pal = Themes.current(c)
            var hud = if (window != null) window.decorView.getTag(R.id.volume_hud) as? VolumeHud
            else overlay?.takeIf { it.host === c }
            // The theme changed since it was built: start over in the new colours.
            if (hud != null && hud.pal != pal) {
                hud.detach()
                hud = null
            }
            if (hud == null) {
                hud = VolumeHud(c, window, pal)
                if (window != null) window.decorView.setTag(R.id.volume_hud, hud)
                else {
                    overlay?.detach()
                    overlay = hud
                }
            }
            hud.show(s)
        }
    }
}
