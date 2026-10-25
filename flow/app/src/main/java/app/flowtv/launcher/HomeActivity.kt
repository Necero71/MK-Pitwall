package app.flowtv.launcher

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import app.flowtv.launcher.ui.AppTile
import app.flowtv.launcher.ui.FlowRow
import app.flowtv.launcher.ui.Motion
import app.flowtv.launcher.ui.Sheet
import app.flowtv.launcher.ui.alpha
import app.flowtv.launcher.ui.blend
import app.flowtv.launcher.ui.dp
import app.flowtv.launcher.ui.focusBackground
import app.flowtv.launcher.ui.look
import app.flowtv.launcher.ui.roundRect
import app.flowtv.launcher.ui.srgbEvaluator
import app.flowtv.launcher.ui.tinted
import java.util.Collections

/**
 * A calm home screen: the clock, three quiet buttons, the focused app's name and one
 * row of favourites. Everything else lives one press away in All apps.
 */
class HomeActivity : BaseActivity() {
    private lateinit var root: FrameLayout
    private lateinit var heroA: ImageView
    private lateinit var heroB: ImageView
    private lateinit var heroFront: ImageView
    private val scrimH = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0, 0))
    private val scrimV = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0))
    private lateinit var titleBlock: LinearLayout
    private lateinit var title: TextView
    private lateinit var sub: TextView
    private lateinit var favRow: FlowRow

    private var apps: List<AppInfo> = emptyList()
    private var favIds = mutableListOf<String>()
    private var favStamp = -1
    private var moving: AppTile? = null
    private var entered = false
    private var margin = 0
    private var tileW = 0
    private var tileH = 0

    private var ambient = 0
    private var ambientAnim: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var settle: Runnable? = null

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        margin = dp(64)
        tileW = dp(160)
        tileH = dp(90)
        setContentView(buildUi())
        applyAmbient(pal.bg)
        if (AppRepo.cache.isNotEmpty()) onApps(AppRepo.cache)
        reload()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packages, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(packages, filter)
    }

    override fun onResume() {
        super.onResume()
        if (apps.isNotEmpty() && favStamp != Prefs.favStamp(this)) onApps(apps)
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) goHome(hasWindowFocus())
    }

    @Deprecated("Back on the home screen glides back to the start instead of leaving.")
    override fun onBackPressed() {
        if (moving != null) endMove() else goHome(true)
    }

    override fun onSearchRequested(): Boolean {
        Launch.voice(this)
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val m = moving
        if (m != null) when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) shift(m, if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) endMove()
                return true
            }
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            (currentFocus as? AppTile)?.let {
                menu(it)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---- Layout ---------------------------------------------------------------------------

    private fun buildUi(): View {
        root = FrameLayout(this)
        heroA = heroView()
        heroB = heroView()
        heroFront = heroA
        root.addView(heroA)
        root.addView(heroB)
        root.addView(View(this).apply { background = LayerDrawable(arrayOf(scrimH, scrimV)) }, heroParams())

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(40), 0, dp(48))
        }
        content.addView(topBar())
        content.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, 0, margin, dp(6))
        }
        title = TextView(this).look(26f, pal.text, "sans-serif-medium").apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        sub = TextView(this).look(14f, pal.dim).apply { setPadding(0, dp(8), 0, 0) }
        titleBlock.addView(title)
        titleBlock.addView(sub)
        content.addView(titleBlock)

        favRow = FlowRow(this).apply { anchor = margin }
        content.addView(favRow, LinearLayout.LayoutParams(MATCH_PARENT, tileH + dp(36)))

        root.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now -> if (now is AppTile) onTileFocus(now) }
        return root
    }

    private fun heroParams() = FrameLayout.LayoutParams(dp(600), dp(338), Gravity.TOP or Gravity.END)

    private fun heroView() = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
        layoutParams = heroParams()
    }

    private fun topBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            clipChildren = false
            setPadding(margin, 0, margin - dp(8), 0)
        }
        val clock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clock.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            look(44f, pal.highlight, "sans-serif-condensed")
            letterSpacing = 0.04f
        })
        clock.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            look(14f, pal.dim)
            setPadding(dp(2), dp(8), 0, 0)
        })
        bar.addView(clock)
        bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val pills = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
        }
        pills.addView(pill(pills, R.drawable.ic_mic, "Search") { Launch.voice(this) })
        pills.addView(pill(pills, R.drawable.ic_tv, "TV settings") { Launch.tvSettings(this) })
        pills.addView(pill(pills, R.drawable.ic_settings, "Settings") { Launch.flowSettings(this) })
        bar.addView(pills)
        return bar
    }

    /** A quiet icon that becomes a labelled pill when focused. */
    private fun pill(group: ViewGroup, icon: Int, label: String, onClick: () -> Unit): View {
        val r = dp(20).toFloat()
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            contentDescription = label
            setPadding(dp(10), 0, dp(10), 0)
            background = focusBackground(roundRect(0, r), roundRect(pal.text, r))
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, dp(40)).apply { marginStart = dp(6) }
        }
        val iv = ImageView(this).apply {
            setImageDrawable(tinted(this@HomeActivity, icon, pal.dim))
        }
        val tv = TextView(this).look(14f, pal.bg, "sans-serif-medium").apply {
            text = label
            visibility = View.GONE
            setPadding(dp(8), 0, dp(4), 0)
        }
        p.addView(iv, LinearLayout.LayoutParams(dp(20), dp(20)))
        p.addView(tv)
        p.setOnFocusChangeListener { _, focused ->
            TransitionManager.beginDelayedTransition(
                group,
                TransitionSet().addTransition(ChangeBounds()).addTransition(Fade())
                    .setDuration(220).setInterpolator(Motion.standard),
            )
            tv.visibility = if (focused) View.VISIBLE else View.GONE
            iv.setColorFilter(if (focused) pal.bg else pal.dim)
        }
        p.setOnClickListener { onClick() }
        return p
    }

    // ---- Row ------------------------------------------------------------------------------

    private fun reload() = AppRepo.load(this) { if (!isDestroyed) onApps(it) }

    private fun onApps(list: List<AppInfo>) {
        apps = list
        val installed = list.mapTo(HashSet()) { it.id }
        val stored = Prefs.favourites(this)
        favIds = (stored ?: defaultFavourites(list)).filter { it in installed }.toMutableList()
        if (stored == null) Prefs.setFavourites(this, favIds)
        favStamp = Prefs.favStamp(this)

        val focused = currentFocus
        val keepId = (focused as? AppTile)?.app?.id
        // Before the first build Android parks focus on a top button; start on the row instead.
        val pillFocused = entered && focused != null && focused !is AppTile
        buildRow()
        if (!entered) {
            entered = true
            entrance()
        }
        if (!pillFocused) (keepId?.let(::findTile) ?: favRow.strip.getChildAt(0))?.requestFocus()
    }

    private fun buildRow() {
        val byId = apps.associateBy { it.id }
        favRow.strip.removeAllViews()
        favIds.forEach { id -> byId[id]?.let { favRow.strip.addView(tileFor(it)) } }
        // The row ends in a door to everything else.
        favRow.strip.addView(AppTile(this, pal, tileW, tileH).apply {
            bindAction(R.drawable.ic_apps, "All apps")
            spaced()
            setOnClickListener { Launch.allApps(this@HomeActivity) }
        })
    }

    private fun tileFor(a: AppInfo) = AppTile(this, pal, tileW, tileH).apply {
        bind(a)
        spaced()
        setOnClickListener {
            pulse()
            Launch.app(this@HomeActivity, a, this)
        }
        setOnLongClickListener {
            menu(this)
            true
        }
    }

    private fun AppTile.spaced() = apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(28) }

    private fun findTile(id: String): View? =
        (0 until favRow.strip.childCount).map(favRow.strip::getChildAt).firstOrNull { (it as? AppTile)?.app?.id == id }

    private fun defaultFavourites(list: List<AppInfo>): List<String> {
        val preferred = listOf(
            "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.google.android.youtube.tv",
            "com.disney.disneyplus", "com.spotify.tv.android", "com.plexapp.android", "org.xbmc.kodi",
            "com.android.vending",
        )
        val ids = list.map { it.id }
        val picks = preferred.filter { it in ids }.toMutableList()
        for (id in ids) {
            if (picks.size >= 6) break
            if (id !in picks) picks.add(id)
        }
        return picks
    }

    /** The row rises into place one tile after another. */
    private fun entrance() {
        val strip = favRow.strip
        for (i in 0 until minOf(strip.childCount, 7)) {
            val v = strip.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(28).toFloat()
            v.animate().setStartDelay(140L + i * 45L).alpha(1f).translationY(0f)
                .setDuration(560).setInterpolator(Motion.glide).start()
        }
    }

    private fun goHome(animate: Boolean) {
        Sheet.open?.dismiss()
        if (moving != null) endMove()
        val first = favRow.strip.getChildAt(0) ?: return
        first.requestFocus()
        favRow.glideTo(first, animate)
    }

    // ---- Focus, hero and ambient colour ---------------------------------------------------

    private fun onTileFocus(t: AppTile) {
        val a = t.app
        if (a == null) {
            showTitle("All apps", "Hold OK on any app there to pin it here")
            return
        }
        showTitle(a.label, if (moving === t) "← → to move · OK to drop it here" else "")
        // Let fast scrolling settle before swapping the big artwork.
        settle?.let(handler::removeCallbacks)
        settle = Runnable {
            setHero(a)
            setAmbient(a.tint)
        }.also { handler.postDelayed(it, 120) }
    }

    private fun showTitle(t: String, s: String) {
        titleBlock.animate().cancel()
        if (title.text.toString() == t) {
            sub.text = s
            titleBlock.animate().setStartDelay(0).alpha(1f).translationY(0f).setDuration(200).start()
            return
        }
        val shift = dp(8).toFloat()
        titleBlock.animate().setStartDelay(0).alpha(0f).translationY(shift).setDuration(90)
            .setInterpolator(Motion.standard).withEndAction {
                title.text = t
                sub.text = s
                titleBlock.translationY = -shift
                titleBlock.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(Motion.glide).start()
            }.start()
    }

    private fun setHero(a: AppInfo) {
        val back = if (heroFront === heroA) heroB else heroA
        val art = a.banner?.fresh(resources)
        back.animate().cancel()
        heroFront.animate().cancel()
        back.setImageDrawable(art)
        back.alpha = 0f
        back.scaleX = 1.06f
        back.scaleY = 1.06f
        if (art != null) {
            back.animate().setStartDelay(0).alpha(if (pal.dark) 0.22f else 0.18f).scaleX(1f).scaleY(1f)
                .setDuration(600).setInterpolator(Motion.glide).start()
        }
        heroFront.animate().setStartDelay(0).alpha(0f).setDuration(380).start()
        heroFront = back
    }

    private fun setAmbient(tint: Int) {
        val target = blend(pal.bg, tint, if (pal.dark) 0.14f else 0.08f)
        ambientAnim?.cancel()
        ambientAnim = ValueAnimator.ofObject(srgbEvaluator, ambient, target).apply {
            duration = 700
            interpolator = Motion.glide
            addUpdateListener { applyAmbient(it.animatedValue as Int) }
            start()
        }
    }

    private fun applyAmbient(c: Int) {
        ambient = c
        root.setBackgroundColor(c)
        scrimH.colors = intArrayOf(c, c.alpha(0f))
        scrimV.colors = intArrayOf(c.alpha(0f), c)
    }

    // ---- Menus and moving -----------------------------------------------------------------

    private fun menu(t: AppTile) {
        val a = t.app ?: return
        AppMenu.show(this, pal, a, t, onFavChanged = { onApps(apps) }, onMove = { startMove(t) })
    }

    private fun startMove(t: AppTile) {
        moving = t
        t.requestFocus()
        t.setMoving(true)
        onTileFocus(t)
    }

    private fun endMove() {
        val t = moving ?: return
        moving = null
        t.setMoving(false)
        onTileFocus(t)
    }

    private fun shift(t: AppTile, dir: Int) {
        val strip = favRow.strip
        val i = strip.indexOfChild(t)
        val j = i + dir
        if (j < 0 || j >= favIds.size) {
            Motion.bump(t, dir)
            return
        }
        val other = strip.getChildAt(j)
        val step = (other.left - t.left).toFloat()
        strip.removeViewAt(j)
        strip.addView(other, i)
        Collections.swap(favIds, i, j)
        Prefs.setFavourites(this, favIds)
        favStamp = Prefs.favStamp(this)
        // Both tiles start where they were and slide into their new places.
        t.translationX = -step
        other.translationX = step
        t.animate().setStartDelay(0).translationX(0f).setDuration(300).setInterpolator(Motion.glide).start()
        other.animate().setStartDelay(0).translationX(0f).setDuration(300).setInterpolator(Motion.glide).start()
        favRow.glideTo(t)
        onTileFocus(t)
    }
}
