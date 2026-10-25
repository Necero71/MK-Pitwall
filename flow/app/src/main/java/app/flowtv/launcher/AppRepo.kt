package app.flowtv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class AppInfo(
    val component: ComponentName,
    val label: String,
    val leanback: Boolean,
    val banner: Drawable?,
    val icon: Drawable,
    /** Dominant colour of the banner (or icon), used to tint the home screen backdrop. */
    val tint: Int,
) {
    val id: String get() = component.packageName
}

/** A private copy, so one drawable can sit in several views at different sizes. */
fun Drawable.fresh(res: Resources): Drawable = constantState?.newDrawable(res)?.mutate() ?: this

object AppRepo {
    @Volatile
    var cache: List<AppInfo> = emptyList()
        private set

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Loads installed apps off the main thread; [done] runs on the main thread. */
    fun load(c: Context, done: (List<AppInfo>) -> Unit) {
        val app = c.applicationContext
        worker.execute {
            val list = runCatching { query(app) }.getOrDefault(cache)
            cache = list
            main.post { done(list) }
        }
    }

    private fun query(c: Context): List<AppInfo> {
        val pm = c.packageManager
        val out = LinkedHashMap<String, AppInfo>()
        fun collect(category: String, leanback: Boolean) {
            val found = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
            for (ri in found) {
                val ai = ri.activityInfo
                if (ai.packageName == c.packageName || out.containsKey(ai.packageName)) continue
                val banner = if (leanback) ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) else null
                val icon = ri.loadIcon(pm)
                out[ai.packageName] = AppInfo(
                    ComponentName(ai.packageName, ai.name),
                    ri.loadLabel(pm).toString().trim(),
                    leanback,
                    banner,
                    icon,
                    dominant(banner ?: icon),
                )
            }
        }
        // TV apps first so they keep their banners; phone-only apps fill in after.
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, true)
        collect(Intent.CATEGORY_LAUNCHER, false)
        return out.values.sortedBy { it.label.lowercase() }
    }

    /** Average colour, weighted towards saturated pixels so logos win over white or black backgrounds. */
    private fun dominant(d: Drawable): Int {
        val w = 24
        val h = 14
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val copy = d.constantState?.newDrawable()?.mutate() ?: d
        copy.setBounds(0, 0, w, h)
        copy.draw(Canvas(bmp))
        val hsv = FloatArray(3)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var sum = 0.0
        for (y in 0 until h) for (x in 0 until w) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) < 128) continue
            Color.colorToHSV(p, hsv)
            val weight = 0.08 + hsv[1] * hsv[2] * hsv[2]
            r += Color.red(p) * weight
            g += Color.green(p) * weight
            b += Color.blue(p) * weight
            sum += weight
        }
        bmp.recycle()
        if (sum == 0.0) return 0xFF7A8496.toInt()
        return Color.rgb((r / sum).toInt(), (g / sum).toInt(), (b / sum).toInt())
    }
}
