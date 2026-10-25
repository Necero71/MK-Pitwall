package app.flowtv.launcher

import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.flowtv.launcher.remote.Remote
import app.flowtv.launcher.remote.RemoteButton
import app.flowtv.launcher.remote.RemoteKeyService
import app.flowtv.launcher.remote.Teach
import app.flowtv.launcher.ui.FlowScroll
import app.flowtv.launcher.ui.Meter
import app.flowtv.launcher.ui.Motion
import app.flowtv.launcher.ui.Sheet
import app.flowtv.launcher.ui.alpha
import app.flowtv.launcher.ui.dp
import app.flowtv.launcher.ui.focusBackground
import app.flowtv.launcher.ui.look
import app.flowtv.launcher.ui.chamfer
import app.flowtv.launcher.ui.heading
import app.flowtv.launcher.ui.tinted
import app.flowtv.launcher.volume.Volume

/** Quick settings in a panel that slides in from the right, like a notification centre. */
class SettingsActivity : FlyoutActivity() {
    private lateinit var list: LinearLayout
    private var meter: Meter? = null
    private var volumeIcon: ImageView? = null
    private var volumeText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = FlowScroll(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(22), dp(24), dp(22), dp(24))
        }
        scroll.addView(list)
        setFlyout(scroll, Edge.END, dp(400), 0, entering = savedInstanceState == null)
        if (AppRepo.cache.isEmpty()) AppRepo.load(this) {}
    }

    override fun onStart() {
        super.onStart()
        Volume.onChange = { showVolume(animate = true) }
    }

    override fun onStop() {
        Volume.onChange = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val keep = currentFocus?.tag as? String
        list.removeAllViews()
        list.addView(TextView(this).look(22f, pal.highlight).apply {
            heading("Quick settings", pal.accent, pal.highlight)
            setPadding(dp(4), 0, 0, dp(16))
        })

        val theme = Prefs.theme(this)
        val helper = Remote.serviceOn(this)
        val online = isOnline()
        val home = isDefaultHome()
        tileRow(
            quick("theme", R.drawable.ic_contrast, "Theme", theme.label, false) { cycleTheme() },
            quick("helper", R.drawable.ic_remote, "Remote helper", if (helper) "On" else "Off", helper) { helperSheet(helper) },
            quick("wifi", R.drawable.ic_wifi, "Wi-Fi", if (online) "Connected" else "Offline", online) { openWifi() },
        )
        tileRow(
            quick("home", R.drawable.ic_home, "Home app", if (home) "Flow" else "Other", home) { homeSheet() },
            quick("saver", R.drawable.ic_moon, "Screensaver", null, false) {
                Launch.start(this, Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            quick("tv", R.drawable.ic_tv, "TV settings", null, false) { Launch.tvSettings(this) },
        )
        volumeRow()

        section("Remote buttons")
        RemoteButton.values().forEach { b -> row(b.key, b.label, buttonDetail(b)) { buttonSheet(b) } }

        section("Home screen")
        row("favs", "Reset Favourites", "Back to the starting set of apps") {
            Prefs.clearFavourites(this)
            Launch.toast(this, "Favourites reset")
        }
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        row("about", "About Flow", "Version $version") {}

        val focusable = mutableListOf<View>()
        fun collect(v: View) {
            if (v.isFocusable && v.tag is String) focusable.add(v)
            if (v is LinearLayout) for (i in 0 until v.childCount) collect(v.getChildAt(i))
        }
        collect(list)
        (focusable.firstOrNull { it.tag == keep } ?: focusable.firstOrNull())?.requestFocus()
    }

    // ---- Quick tiles ----------------------------------------------------------------------

    private fun quick(key: String, icon: Int, label: String, state: String?, active: Boolean, onClick: () -> Unit): View {
        val r = dp(10).toFloat()
        val bg = if (active) pal.accent else pal.text.alpha(if (pal.dark) 0.07f else 0.05f)
        val fg = if (active) pal.bg else pal.text
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            tag = key
            isFocusable = true
            isClickable = true
            contentDescription = label
            setPadding(dp(14), dp(10), dp(10), dp(10))
            background = focusBackground(chamfer(bg, r), chamfer(bg, r, dp(3), pal.ring))
        }
        v.addView(ImageView(this).apply { setImageDrawable(tinted(this@SettingsActivity, icon, fg)) }, LinearLayout.LayoutParams(dp(22), dp(22)))
        v.addView(TextView(this).look(13f, fg, "sans-serif-medium").apply {
            text = label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(10), 0, 0)
        })
        if (state != null) v.addView(TextView(this).look(11f, fg.alpha(0.7f)).apply {
            text = state
            setPadding(0, dp(3), 0, 0)
        })
        v.setOnFocusChangeListener { view, f -> Motion.focus(view, f, 1.05f, 0f) }
        v.setOnClickListener { onClick() }
        return v
    }

    private fun tileRow(vararg tiles: View) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        tiles.forEachIndexed { i, t ->
            row.addView(t, LinearLayout.LayoutParams(0, dp(86), 1f).apply { if (i < tiles.size - 1) marginEnd = dp(10) })
        }
        list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(10) })
    }

    /** A slider: ← → change the volume, OK mutes. */
    private fun volumeRow() {
        val r = dp(10).toFloat()
        val rest = pal.text.alpha(if (pal.dark) 0.07f else 0.05f)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            tag = "volume"
            isFocusable = true
            contentDescription = "Volume"
            setPadding(dp(16), 0, dp(16), 0)
            background = focusBackground(chamfer(rest, r), chamfer(rest, r, dp(3), pal.ring))
        }
        val icon = ImageView(this)
        row.addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)))
        val m = Meter(this, pal.text.alpha(0.15f), pal.accent, thumb = true, thumbColor = pal.ring)
        row.addView(m, LinearLayout.LayoutParams(0, dp(24), 1f).apply {
            marginStart = dp(14)
            marginEnd = dp(12)
        })
        val number = TextView(this).look(14f, pal.text, "sans-serif-medium").apply { gravity = Gravity.END }
        row.addView(number, LinearLayout.LayoutParams(dp(40), WRAP_CONTENT))
        row.setOnKeyListener { _, code, e ->
            val mine = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
            if (mine && e.action == KeyEvent.ACTION_DOWN) when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> Volume.adjust(this, AudioManager.ADJUST_LOWER)
                KeyEvent.KEYCODE_DPAD_RIGHT -> Volume.adjust(this, AudioManager.ADJUST_RAISE)
                else -> if (e.repeatCount == 0) Volume.adjust(this, AudioManager.ADJUST_TOGGLE_MUTE)
            }
            mine
        }
        row.setOnFocusChangeListener { v, f -> Motion.focus(v, f, 1.03f, 0f) }
        list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, dp(60)).apply { bottomMargin = dp(6) })
        meter = m
        volumeIcon = icon
        volumeText = number
        showVolume(animate = false)
    }

    private fun showVolume(animate: Boolean) {
        val m = meter ?: return
        val s = Volume.state(this)
        m.set(s.fraction, animate)
        volumeText?.text = if (s.muted) "Mute" else s.percent.toString()
        volumeIcon?.setImageDrawable(tinted(this, s.icon, pal.text))
    }

    private fun cycleTheme() {
        val modes = ThemeMode.values()
        Prefs.setTheme(this, modes[(Prefs.theme(this).ordinal + 1) % modes.size])
        if (Themes.current(this) != pal) recreateSmooth() else render()
    }

    @Suppress("DEPRECATION")
    private fun isOnline(): Boolean =
        getSystemService(ConnectivityManager::class.java)?.activeNetworkInfo?.isConnected == true

    private fun isDefaultHome(): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == packageName
    }

    private fun openWifi() {
        if (!Launch.start(this, Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            Launch.tvSettings(this)
        }
    }

    // ---- List rows ------------------------------------------------------------------------

    private fun section(text: String) {
        list.addView(TextView(this).look(13f, pal.highlight, "sans-serif-condensed", android.graphics.Typeface.BOLD).apply {
            this.text = text.uppercase()
            letterSpacing = 0.08f
            setPadding(dp(6), dp(22), 0, dp(8))
        })
    }

    private fun row(key: String, title: String, detail: String, onClick: () -> Unit) {
        val r = dp(8).toFloat()
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = key
            isFocusable = true
            isClickable = true
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = focusBackground(chamfer(0, r), chamfer(pal.focus, r, edgeWidth = dp(4), edgeColor = pal.ring))
        }
        v.addView(TextView(this).look(16f, pal.text).apply { text = title })
        v.addView(TextView(this).look(12f, pal.dim).apply {
            text = detail
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(5), 0, 0)
        })
        v.setOnFocusChangeListener { view, f -> Motion.focus(view, f, 1.02f, 0f) }
        v.setOnClickListener { onClick() }
        list.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(2) })
    }

    private fun buttonDetail(b: RemoteButton): String {
        val opens = Remote.describe(this, Remote.action(this, b))
        val k = Remote.learnedKey(this, b)
        val taught = when {
            k > 0 -> "taught"
            k == 0 -> "taught (scan code)"
            b.defaultKeys.isNotEmpty() -> "standard key"
            else -> "not taught yet"
        }
        return "Opens $opens · $taught"
    }

    // ---- Sheets ---------------------------------------------------------------------------

    private fun helperSheet(on: Boolean) {
        val s = Sheet(
            this, pal, "Remote helper",
            "Lets Flow see the Netflix, Prime Video, Apps, Voice and volume buttons in every app, before the TV grabs them. It only reads button presses.",
        )
        s.item(
            if (on) "Open Accessibility settings" else "Turn it on",
            "Find “Flow remote helper” and switch it on",
        ) {
            if (!Launch.start(this, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
                Launch.toast(this, "This TV hides Accessibility settings. Use the computer steps instead.")
            }
        }
        s.note(
            "No Accessibility menu? From a computer, run:\n" +
                "adb shell settings put secure enabled_accessibility_services " +
                "$packageName/${RemoteKeyService::class.java.name}\n" +
                "adb shell settings put secure accessibility_enabled 1",
        )
        s.show()
    }

    private fun buttonSheet(b: RemoteButton) {
        val s = Sheet(this, pal, b.label, buttonDetail(b))
        s.item("Choose what it opens", Remote.describe(this, Remote.action(this, b))) { actionPicker(b) }
        s.item("Teach the button", "Press it once so Flow can recognise it") { Teach.show(this, pal, b) { render() } }
        if (Remote.learnedKey(this, b) >= 0) s.item("Forget the taught key") {
            Remote.forget(this, b)
            render()
        }
        s.item("Back to remote default", "Let the TV handle it like before") {
            Remote.setAction(this, b, Remote.DEFAULT)
            render()
        }
        s.show()
    }

    private fun actionPicker(b: RemoteButton) {
        val current = Remote.action(this, b)
        val s = Sheet(this, pal, "${b.label} opens…")
        s.item("Remote default", "Whatever it did before Flow", checked = current == Remote.DEFAULT) { set(b, Remote.DEFAULT) }
        s.item("All apps", "Flow's app menu", checked = current == Remote.ALL_APPS) { set(b, Remote.ALL_APPS) }
        s.item("Home screen", checked = current == Remote.HOME) { set(b, Remote.HOME) }
        s.item("Voice search", checked = current == Remote.VOICE) { set(b, Remote.VOICE) }
        AppRepo.cache.forEach { a ->
            val action = Remote.appAction(a.id)
            s.item(a.label, icon = a.icon.fresh(resources), checked = current == action) { set(b, action) }
        }
        s.show()
    }

    private fun set(b: RemoteButton, action: String) {
        Remote.setAction(this, b, action)
        render()
        if (action != Remote.DEFAULT && !Remote.isTaught(this, b)) Teach.show(this, pal, b) { render() }
    }

    private fun homeSheet() {
        val s = Sheet(this, pal, "Home app", "Make the Home button always come back to Flow.")
        s.item("Open Home app settings", "Pick Flow as the default") {
            val ok = Launch.start(this, Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) ||
                Launch.start(
                    this,
                    Intent.createChooser(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), "Home screen")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            if (!ok) Launch.toast(this, "This TV has no home picker. Use the computer step instead.")
        }
        s.note(
            "If Home still opens the old screen, run this from a computer:\n" +
                "adb shell cmd package set-home-activity $packageName/${HomeActivity::class.java.name}",
        )
        s.show()
    }
}
