package app.flowtv.launcher

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import app.flowtv.launcher.remote.Remote
import app.flowtv.launcher.ui.Motion
import java.lang.ref.WeakReference

/** Theme plumbing and remote-button fallback shared by every Flow screen. */
abstract class BaseActivity : Activity() {
    lateinit var pal: Palette
        private set

    /** Flyouts are see-through, so they keep the transparent window from their theme. */
    protected open val opaque = true

    private var recreating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        pal = Themes.current(this)
        super.onCreate(savedInstanceState)
        if (opaque) window.setBackgroundDrawable(ColorDrawable(pal.bg))
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (fadeNext) {
            fadeNext = false
            val d = window.decorView
            d.alpha = 0f
            d.animate().alpha(1f).setDuration(340).setInterpolator(Motion.glide).start()
        }
    }

    override fun onResume() {
        super.onResume()
        top = WeakReference(this)
        // Auto theme crosses 7 am / 7 pm, or the theme was changed on another screen.
        if (Themes.current(this) != pal) recreateSmooth()
    }

    override fun onPause() {
        if (top?.get() === this) top = null
        super.onPause()
    }

    /** Fades out, rebuilds in the new theme, fades back in. */
    fun recreateSmooth() {
        if (recreating) return
        recreating = true
        window.decorView.animate().alpha(0f).setDuration(180).withEndAction {
            fadeNext = true
            recreate()
        }.start()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        Remote.filter(this, event) || super.dispatchKeyEvent(event)

    companion object {
        private var fadeNext = false
        private var top: WeakReference<BaseActivity>? = null

        /** The Flow screen in the foreground, if any. */
        fun resumed(): BaseActivity? = top?.get()
    }
}
