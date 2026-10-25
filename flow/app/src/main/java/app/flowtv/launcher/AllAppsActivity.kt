package app.flowtv.launcher

import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.flowtv.launcher.ui.AppTile
import app.flowtv.launcher.ui.FlowScroll
import app.flowtv.launcher.ui.Motion
import app.flowtv.launcher.ui.alpha
import app.flowtv.launcher.ui.dp
import app.flowtv.launcher.ui.focusBackground
import app.flowtv.launcher.ui.heading
import app.flowtv.launcher.ui.look
import app.flowtv.launcher.ui.roundRect
import app.flowtv.launcher.ui.tinted

/** Every app, in a Start-menu style panel that rises from the bottom of the screen. */
class AllAppsActivity : FlyoutActivity() {
    private lateinit var grid: LinearLayout
    private lateinit var count: TextView
    private lateinit var focusName: TextView
    private var shown: List<String> = emptyList()
    private val cols = 4
    private var panelW = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screen = resources.displayMetrics
        panelW = minOf(dp(780), screen.widthPixels - dp(64))
        val panelH = minOf(dp(452), screen.heightPixels - dp(64))

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dp(32), dp(26), dp(32), dp(4))
        }
        head.addView(TextView(this).look(22f, pal.highlight).apply { heading("All apps", pal.accent, pal.highlight) })
        count = TextView(this).look(14f, pal.dim).apply { setPadding(dp(10), 0, 0, dp(2)) }
        head.addView(count)
        head.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        focusName = TextView(this).look(15f, pal.dim).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(16), 0, 0, dp(2))
        }
        head.addView(focusName)
        content.addView(head)

        val scroll = FlowScroll(this)
        grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(32), dp(16), dp(32), dp(20))
        }
        scroll.addView(grid)
        content.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        content.addView(footer())

        setFlyout(content, Edge.BOTTOM, panelW, panelH, entering = savedInstanceState == null)
        content.viewTreeObserver.addOnGlobalFocusChangeListener { _, now ->
            (now as? AppTile)?.app?.let { focusName.text = it.label }
        }
        if (AppRepo.cache.isNotEmpty()) build(AppRepo.cache, animate = savedInstanceState == null)
        AppRepo.load(this) { if (!isDestroyed) build(it, animate = shown.isEmpty()) }
    }

    /** A quiet strip along the bottom, like the Start menu's: a hint and a way to Settings. */
    private fun footer(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(32), dp(10), dp(20), dp(12))
            background = roundRect(pal.text.alpha(if (pal.dark) 0.04f else 0.03f), 0f)
        }
        bar.addView(TextView(this).look(13f, pal.dim).apply { text = "Hold OK on an app for options" })
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val r = dp(20).toFloat()
        val gear = ImageView(this).apply {
            setImageDrawable(tinted(this@AllAppsActivity, R.drawable.ic_settings, pal.dim))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            isFocusable = true
            isClickable = true
            contentDescription = "Settings"
            background = focusBackground(roundRect(0, r), roundRect(pal.text, r))
            setOnFocusChangeListener { _, f -> setColorFilter(if (f) pal.bg else pal.dim) }
            setOnClickListener {
                finish()
                Launch.flowSettings(this@AllAppsActivity)
            }
        }
        bar.addView(gear, LinearLayout.LayoutParams(dp(40), dp(40)))
        return bar
    }

    private fun build(list: List<AppInfo>, animate: Boolean) {
        val ids = list.map { it.id }
        if (ids == shown) return
        shown = ids
        val keep = (currentFocus as? AppTile)?.app?.id
        grid.removeAllViews()

        val gap = dp(24)
        val w = (panelW - dp(64) - gap * (cols - 1)) / cols
        val h = w * 9 / 16
        list.chunked(cols).forEachIndexed { r, chunk ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
            }
            chunk.forEachIndexed { c, a ->
                val t = AppTile(this, pal, w, h).apply {
                    bind(a)
                    if (c < cols - 1) (layoutParams as LinearLayout.LayoutParams).marginEnd = gap
                    setOnClickListener {
                        pulse()
                        Launch.app(this@AllAppsActivity, a, this)
                    }
                    setOnLongClickListener {
                        AppMenu.show(this@AllAppsActivity, pal, a, this, onFavChanged = {})
                        true
                    }
                }
                row.addView(t)
                if (animate && r < 3) {
                    t.alpha = 0f
                    t.translationY = dp(20).toFloat()
                    t.animate().setStartDelay(140L + r * 60L + c * 25L).alpha(1f).translationY(0f)
                        .setDuration(420).setInterpolator(Motion.glide).start()
                }
            }
            grid.addView(row, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = dp(24) })
        }
        count.text = list.size.toString()

        val tiles = (0 until grid.childCount).flatMap { r ->
            val row = grid.getChildAt(r) as LinearLayout
            (0 until row.childCount).map { row.getChildAt(it) as AppTile }
        }
        (tiles.firstOrNull { it.app?.id == keep } ?: tiles.firstOrNull())?.requestFocus()
    }
}
