package app.flowtv.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * A level bar (volume), optionally with a slider thumb. Glides between values.
 * With [segments] it draws a row of ticks instead, like a rev counter.
 */
class Meter(
    context: Context,
    private var trackColor: Int,
    private var fillColor: Int,
    private val thumb: Boolean,
    private val thumbColor: Int = fillColor,
    private val segments: Int = 0,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tick = Path()
    private val bar = dp(4).toFloat()
    private val thumbRadius = dp(7).toFloat()
    private var shown = 0f
    private var anim: ValueAnimator? = null

    fun colors(track: Int, fill: Int) {
        trackColor = track
        fillColor = fill
        invalidate()
    }

    fun set(fraction: Float, animate: Boolean) {
        val f = fraction.coerceIn(0f, 1f)
        anim?.cancel()
        if (!animate) {
            shown = f
            invalidate()
            return
        }
        anim = ValueAnimator.ofFloat(shown, f).apply {
            duration = 200
            interpolator = Motion.glide
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (segments > 0) return drawTicks(canvas)
        val cy = height / 2f
        val inset = if (thumb) thumbRadius else 0f
        val left = inset
        val right = width - inset
        val r = bar / 2
        rect.set(left, cy - r, right, cy + r)
        paint.color = trackColor
        canvas.drawRoundRect(rect, r, r, paint)

        val x = left + (right - left) * shown
        paint.color = fillColor
        if (shown > 0f) {
            rect.set(left, cy - r, maxOf(x, left + bar), cy + r)
            canvas.drawRoundRect(rect, r, r, paint)
        }
        if (thumb) {
            paint.color = thumbColor
            canvas.drawCircle(x, cy, thumbRadius, paint)
        }
    }

    /** Slanted ticks; a tick lights once the level reaches its middle. */
    private fun drawTicks(canvas: Canvas) {
        val gap = dp(3).toFloat()
        val w = (width - gap * (segments - 1)) / segments
        val slant = minOf(w * 0.4f, dp(3).toFloat())
        val lit = shown * segments
        for (i in 0 until segments) {
            val x = i * (w + gap)
            paint.color = if (lit >= i + 0.5f) fillColor else trackColor
            tick.reset()
            tick.moveTo(x + slant, 0f)
            tick.lineTo(x + w, 0f)
            tick.lineTo(x + w - slant, height.toFloat())
            tick.lineTo(x, height.toFloat())
            tick.close()
            canvas.drawPath(tick, paint)
        }
    }
}
