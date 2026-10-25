package app.flowtv.launcher.volume

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import app.flowtv.launcher.R
import kotlin.math.roundToInt

data class VolumeState(val level: Int, val max: Int, val muted: Boolean) {
    val fraction: Float get() = if (muted || max == 0) 0f else level.toFloat() / max
    val percent: Int get() = (fraction * 100).roundToInt()
    val icon: Int
        get() = when {
            muted || level == 0 -> R.drawable.ic_volume_off
            fraction < 0.5f -> R.drawable.ic_volume_down
            else -> R.drawable.ic_volume_up
        }
}

/**
 * Takes over the volume keys so Flow can show its own indicator instead of the
 * system one. On the Mi TV Stick these keys set the stick's own media volume.
 */
object Volume {
    /** Lets an open volume slider follow changes made with the remote. */
    var onChange: (() -> Unit)? = null

    fun isKey(code: Int) = code == KeyEvent.KEYCODE_VOLUME_UP ||
        code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE

    private fun audio(c: Context) = c.getSystemService(AudioManager::class.java)

    fun state(c: Context): VolumeState {
        val am = audio(c)
        return VolumeState(
            am.getStreamVolume(AudioManager.STREAM_MUSIC),
            am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            am.isStreamMute(AudioManager.STREAM_MUSIC),
        )
    }

    /** Returns true when Flow handled the key (and showed its indicator). */
    fun handle(c: Context, e: KeyEvent): Boolean {
        val am = audio(c)
        // Fixed-volume devices pass volume to the TV over HDMI: leave those alone.
        if (am.isVolumeFixed) return false
        return try {
            if (e.action == KeyEvent.ACTION_DOWN) {
                val direction = when (e.keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
                    KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
                    else -> if (e.repeatCount == 0) AudioManager.ADJUST_TOGGLE_MUTE else AudioManager.ADJUST_SAME
                }
                adjust(c, direction)
                VolumeHud.show(c, state(c))
            }
            true
        } catch (t: RuntimeException) {
            false
        }
    }

    /** Changes the volume with no system UI or click sound. */
    fun adjust(c: Context, direction: Int) {
        audio(c).adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        onChange?.invoke()
    }
}
