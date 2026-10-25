package app.flowtv.launcher.ui

import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(v: Int): Int = context.dp(v)

fun roundRect(color: Int, radius: Float, strokeWidth: Int = 0, strokeColor: Int = 0, dash: Float = 0f) =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor, dash, dash)
    }

/** A Mavro Kinetic plate: [Chamfer] with the top-left and bottom-right corners cut at 45°. */
fun chamfer(color: Int, cut: Float, strokeWidth: Int = 0, strokeColor: Int = 0, edgeWidth: Int = 0, edgeColor: Int = 0) =
    Chamfer(color, cut, strokeWidth, strokeColor, edgeWidth, edgeColor)

/** A "/ TITLE" heading: an olive slash, then the title in condensed capitals. */
fun TextView.heading(text: String, slash: Int, color: Int): TextView = apply {
    val t = SpannableStringBuilder("/  ").append(text.uppercase())
    t.setSpan(ForegroundColorSpan(slash), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    this.text = t
    setTextColor(color)
    typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    letterSpacing = 0.08f
}

/** A background that cross-fades between its resting and focused look. */
fun focusBackground(rest: Drawable, focused: Drawable) = StateListDrawable().apply {
    setEnterFadeDuration(160)
    setExitFadeDuration(220)
    addState(intArrayOf(android.R.attr.state_focused), focused)
    addState(intArrayOf(), rest)
}

fun tinted(c: Context, res: Int, color: Int): Drawable =
    c.getDrawable(res)!!.mutate().apply { setTint(color) }

fun Int.alpha(a: Float): Int = (this and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

/** Straight sRGB mix. ArgbEvaluator mixes in linear light, which washes dark colours out. */
fun blend(from: Int, to: Int, t: Float): Int {
    fun ch(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return ((a + (b - a) * t) + 0.5f).toInt().coerceIn(0, 255) shl shift
    }
    return ch(24) or ch(16) or ch(8) or ch(0)
}

/** Animates colours with [blend] rather than ArgbEvaluator's linear-light mix. */
val srgbEvaluator = TypeEvaluator<Int> { t, from, to -> blend(from, to, t) }

fun <T : TextView> T.look(sp: Float, color: Int, family: String = "sans-serif", style: Int = Typeface.NORMAL): T =
    apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = Typeface.create(family, style)
        includeFontPadding = false
    }

/** Runs [block] once, right after the next layout pass. */
fun View.afterLayout(block: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            block()
        }
    })
}

object Motion {
    /** Quick start, long soft landing: the "flow" in everything that moves. */
    val glide = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val standard = PathInterpolator(0.2f, 0f, 0f, 1f)
    /** Speeds up as it leaves: for things going off screen. */
    val exit = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    fun focus(v: View, focused: Boolean, scale: Float, lift: Float) {
        // ViewPropertyAnimator keeps the last start delay, so always reset it.
        v.animate().setStartDelay(0)
            .scaleX(if (focused) scale else 1f)
            .scaleY(if (focused) scale else 1f)
            .translationZ(if (focused) lift else 0f)
            .setDuration(if (focused) 240L else 200L)
            .setInterpolator(glide)
            .start()
    }

    /** A little nudge when you hit the end of a row. */
    fun bump(v: View, dir: Int) {
        ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, v.dp(10) * dir.toFloat(), 0f).apply {
            duration = 260
            interpolator = standard
        }.start()
    }
}
