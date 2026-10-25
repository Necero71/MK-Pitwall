package app.flowtv.launcher.remote

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import app.flowtv.launcher.Launch
import app.flowtv.launcher.Prefs
import app.flowtv.launcher.volume.Volume

/**
 * The remote buttons Flow can take over. Netflix and Prime Video send vendor-specific
 * codes on the Mi TV Stick, so they have no defaults and are taught once instead.
 */
enum class RemoteButton(
    val key: String,
    val label: String,
    val short: String,
    val defaultKeys: IntArray,
    val defaultAction: String,
) {
    NETFLIX("netflix", "Netflix button", "Netflix", intArrayOf(), Remote.DEFAULT),
    PRIME("prime", "Prime Video button", "Prime Video", intArrayOf(), Remote.DEFAULT),
    APPS("apps", "Apps button", "Apps", intArrayOf(KeyEvent.KEYCODE_ALL_APPS), Remote.ALL_APPS),
    VOICE(
        "voice", "Voice button", "Voice / Assistant",
        intArrayOf(KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST),
        Remote.DEFAULT,
    ),
}

object Remote {
    const val DEFAULT = "default"
    const val ALL_APPS = "allapps"
    const val HOME = "home"
    const val VOICE = "voice"
    private const val PKG = "pkg:"

    /** The button being taught right now, if any. */
    @Volatile
    var learning: RemoteButton? = null
    var onLearned: ((RemoteButton, Int) -> Unit)? = null

    private var swallowUp = -1
    private val main = Handler(Looper.getMainLooper())

    // Keys that must keep their normal job and can never be taught.
    private val reserved = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER,
    )

    /** Taught key code, 0 when the remote sent no code (then the scan code is used), -1 when not taught. */
    fun learnedKey(c: Context, b: RemoteButton) = Prefs.store(c).getInt("key_${b.key}", -1)
    fun learnedScan(c: Context, b: RemoteButton) = Prefs.store(c).getInt("scan_${b.key}", 0)
    fun isTaught(c: Context, b: RemoteButton) = learnedKey(c, b) >= 0 || b.defaultKeys.isNotEmpty()

    fun forget(c: Context, b: RemoteButton) =
        Prefs.store(c).edit().remove("key_${b.key}").remove("scan_${b.key}").apply()

    fun action(c: Context, b: RemoteButton): String =
        Prefs.store(c).getString("act_${b.key}", null) ?: b.defaultAction

    fun setAction(c: Context, b: RemoteButton, action: String) =
        Prefs.store(c).edit().putString("act_${b.key}", action).apply()

    fun appAction(pkg: String) = PKG + pkg

    fun describe(c: Context, action: String): String = when {
        action == DEFAULT -> "Remote default"
        action == ALL_APPS -> "All apps"
        action == HOME -> "Home screen"
        action == VOICE -> "Voice search"
        action.startsWith(PKG) -> {
            val pkg = action.removePrefix(PKG)
            runCatching {
                val pm = c.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrElse { "$pkg (not installed)" }
        }
        else -> action
    }

    fun match(c: Context, e: KeyEvent): RemoteButton? {
        for (b in RemoteButton.values()) {
            val k = learnedKey(c, b)
            when {
                k > 0 -> if (k == e.keyCode) return b
                k == 0 -> if (e.scanCode != 0 && e.scanCode == learnedScan(c, b)) return b
                else -> if (e.keyCode in b.defaultKeys) return b
            }
        }
        return null
    }

    /**
     * Shared by the accessibility service (sees keys in every app) and Flow's own
     * screens (fallback when the service is off). Returns true when the key was used.
     */
    fun filter(c: Context, e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_UP && e.keyCode == swallowUp) {
            swallowUp = -1
            return true
        }
        if (Volume.isKey(e.keyCode)) return Volume.handle(c, e)
        val teaching = learning
        if (teaching != null) {
            if (e.keyCode in reserved) return false
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                learning = null
                swallowUp = e.keyCode
                save(c, teaching, e.keyCode, e.scanCode)
                main.post { onLearned?.invoke(teaching, e.keyCode) }
            }
            return true
        }
        val b = match(c, e) ?: return false
        if (action(c, b) == DEFAULT) return false
        // Act on the press, and swallow the repeats and release so the TV never sees them.
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) main.post { run(c, action(c, b)) }
        return true
    }

    fun run(c: Context, action: String): Boolean = when {
        action == ALL_APPS -> Launch.allApps(c)
        action == HOME -> Launch.home(c)
        action == VOICE -> Launch.voice(c)
        action.startsWith(PKG) -> Launch.pkg(c, action.removePrefix(PKG))
        else -> false
    }

    private fun save(c: Context, b: RemoteButton, code: Int, scan: Int) {
        val edit = Prefs.store(c).edit()
        // One physical button belongs to one slot.
        for (other in RemoteButton.values()) {
            if (other == b || learnedKey(c, other) != code) continue
            if (code != 0 || learnedScan(c, other) == scan) edit.remove("key_${other.key}").remove("scan_${other.key}")
        }
        edit.putInt("key_${b.key}", code).putInt("scan_${b.key}", scan).apply()
    }

    fun serviceOn(c: Context): Boolean {
        if (RemoteKeyService.running) return true
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = ComponentName(c, RemoteKeyService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
