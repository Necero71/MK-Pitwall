package app.flowtv.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/** The Mavro Kinetic outline: a rectangle with the top-left and bottom-right corners cut at 45°. */
fun chamferPath(path: Path, left: Float, top: Float, right: Float, bottom: Float, cut: Float): Path {
    val c = minOf(cut, (right - left) / 2, (bottom - top) / 2)
    return path.apply {
        reset()
        moveTo(left + c, top)
        lineTo(right, top)
        lineTo(right, bottom - c)
        lineTo(right - c, bottom)
        lineTo(left, bottom)
        lineTo(left, top + c)
        close()
    }
}

/**
 * A chamfered plate, optionally with an outline and a solid strip down its left edge
 * (the focused-row marker in lists and flyouts).
 */
class Chamfer(
    private val color: Int,
    private val cut: Float,
    private val strokeWidth: Int = 0,
    private val strokeColor: Int = 0,
    private val edgeWidth: Int = 0,
    private val edgeColor: Int = 0,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val inner = Path()
    private var alphaMod = 255

    override fun onBoundsChange(bounds: Rect) {
        val h = strokeWidth / 2f
        chamferPath(path, bounds.left + h, bounds.top + h, bounds.right - h, bounds.bottom - h, cut)
        chamferPath(inner, bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), cut)
    }

    override fun draw(canvas: Canvas) {
        if (color ushr 24 != 0) {
            fill.color = color
            fill.alpha = (fill.alpha * alphaMod) / 255
            canvas.drawPath(inner, fill)
        }
        if (edgeWidth > 0) {
            canvas.save()
            canvas.clipPath(inner)
            fill.color = edgeColor
            fill.alpha = (fill.alpha * alphaMod) / 255
            val b = bounds
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), (b.left + edgeWidth).toFloat(), b.bottom.toFloat(), fill)
            canvas.restore()
        }
        if (strokeWidth > 0) {
            stroke.color = strokeColor
            stroke.alpha = (stroke.alpha * alphaMod) / 255
            stroke.strokeWidth = strokeWidth.toFloat()
            canvas.drawPath(path, stroke)
        }
    }

    override fun getOutline(outline: Outline) {
        @Suppress("DEPRECATION")
        outline.setConvexPath(inner)
    }

    override fun setAlpha(alpha: Int) {
        alphaMod = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        stroke.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Casts a chamfered shadow. */
class ChamferOutline(private val cut: Float) : ViewOutlineProvider() {
    private val path = Path()
    override fun getOutline(view: View, outline: Outline) {
        chamferPath(path, 0f, 0f, view.width.toFloat(), view.height.toFloat(), cut)
        @Suppress("DEPRECATION")
        outline.setConvexPath(path)
    }
}

/**
 * A frame that clips its children to the chamfered outline. Android 9 can only clip
 * views to rectangles and rounded rectangles, so this clips the canvas instead.
 */
class ChamferFrame(context: Context, private val cut: Float) : FrameLayout(context) {
    private val path = Path()

    init {
        outlineProvider = ChamferOutline(cut)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        chamferPath(path, 0f, 0f, w.toFloat(), h.toFloat(), cut)
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(path)
        super.dispatchDraw(canvas)
        canvas.restore()
    }
}
