package app.flowtv.launcher.ui

import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.flowtv.launcher.AppInfo
import app.flowtv.launcher.Palette
import app.flowtv.launcher.fresh

/** A chamfered banner card that lifts, grows and rings itself in tan when focused. */
class AppTile(context: Context, private val pal: Palette, w: Int, h: Int) : FrameLayout(context) {
    var app: AppInfo? = null
        private set

    private val cut = dp(8).toFloat()
    private val card = ChamferFrame(context, cut)
    private val image = ImageView(context)
    private val icon = ImageView(context)
    private val label = TextView(context)
    private val ring = View(context)
    private var moving = false

    init {
        layoutParams = LinearLayout.LayoutParams(w, h)
        isFocusable = true
        isClickable = true
        isLongClickable = true
        clipChildren = false
        elevation = dp(2).toFloat()
        outlineProvider = ChamferOutline(cut)

        card.background = chamfer(pal.surface, cut, dp(1), pal.line)
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        image.scaleType = ImageView.ScaleType.CENTER_CROP
        card.addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val s = minOf(h / 2, dp(46))
        card.addView(icon, LayoutParams(s, s, Gravity.CENTER).apply { bottomMargin = h / 6 })

        label.look(12f, pal.text, "sans-serif-condensed", android.graphics.Typeface.BOLD)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER
        card.addView(
            label,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
                .apply { setMargins(dp(8), 0, dp(8), h / 8) },
        )

        ring.background = chamfer(0, cut, dp(3), pal.ring)
        ring.alpha = 0f
        addView(ring, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun bind(a: AppInfo) {
        app = a
        contentDescription = a.label
        val banner = a.banner
        if (banner != null) {
            image.setImageDrawable(banner.fresh(resources))
            image.visibility = VISIBLE
            icon.visibility = GONE
            label.visibility = GONE
            card.background = chamfer(pal.surface, cut, dp(1), pal.line)
        } else {
            image.visibility = GONE
            icon.setImageDrawable(a.icon.fresh(resources))
            icon.visibility = VISIBLE
            label.text = a.label
            label.visibility = VISIBLE
            card.background = chamfer(blend(pal.surface, a.tint, if (pal.dark) 0.22f else 0.12f), cut, dp(1), pal.line)
        }
    }

    /** A non-app tile, like "Add". */
    fun bindAction(iconRes: Int, text: String) {
        app = null
        contentDescription = text
        image.visibility = GONE
        icon.setImageDrawable(tinted(context, iconRes, pal.dim))
        icon.visibility = VISIBLE
        label.text = text
        label.setTextColor(pal.dim)
        label.visibility = VISIBLE
        card.background = chamfer(pal.text.alpha(0.03f), cut, dp(2), pal.dim.alpha(0.5f))
        elevation = 0f
    }

    /** Move mode: the tile floats higher with an accent ring while you slide it. */
    fun setMoving(on: Boolean) {
        moving = on
        ring.background = chamfer(0, cut, dp(if (on) 4 else 3), if (on) pal.accent else pal.ring)
        animateFocus(isFocused)
    }

    fun pulse() {
        animate().setStartDelay(0).scaleX(1.03f).scaleY(1.03f).setDuration(90).setInterpolator(Motion.standard)
            .withEndAction { animateFocus(isFocused) }.start()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateFocus(gainFocus)
    }

    private fun animateFocus(focused: Boolean) {
        Motion.focus(this, focused, if (moving) 1.1f else 1.06f, dp(if (moving) 18 else 12).toFloat())
        ring.animate().setStartDelay(0).alpha(if (focused) 1f else 0f)
            .setDuration(if (focused) 180L else 140L).start()
    }
}
