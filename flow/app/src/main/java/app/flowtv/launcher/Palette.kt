package app.flowtv.launcher

import android.content.Context
import java.util.Calendar

data class Palette(
    val dark: Boolean,
    val bg: Int,
    /** Sheets and dialogs. */
    val raised: Int,
    /** Tiles and pills at rest. */
    val surface: Int,
    /** Focused row in lists and sheets. */
    val focus: Int,
    val text: Int,
    val dim: Int,
    val accent: Int,
    /** Focus ring around tiles. */
    val ring: Int,
    /** Hairlines and panel edges. */
    val line: Int,
    /** Headings, the clock and labels that should stand out (tan in the dark theme). */
    val highlight: Int,
    /** Mute and anything destructive. */
    val alert: Int,
)

enum class ThemeMode(val label: String, val detail: String) {
    AUTO("Auto", "Sand by day, Tactical from 7 pm"),
    LIGHT("Sand", "Light tan, for daytime"),
    DARK("Tactical", "Olive and tan on black"),
}

object Themes {
    /** Mavro Kinetic "MK Tactical": olive, coyote and tan on near-black. */
    val DARK = Palette(
        dark = true,
        bg = 0xFF0F110C.toInt(),
        raised = 0xFF252A1F.toInt(),
        surface = 0xFF1A1E16.toInt(),
        focus = 0xFF34402A.toInt(),
        text = 0xFFECE4D0.toInt(),
        dim = 0xFFA39C88.toInt(),
        accent = 0xFF6E8B3D.toInt(),
        ring = 0xFFD2B48C.toInt(),
        line = 0xFF3A4030.toInt(),
        highlight = 0xFFD2B48C.toInt(),
        alert = 0xFFC0532F.toInt(),
    )

    /** Daytime "Sand": the same family on a tan base. */
    val LIGHT = Palette(
        dark = false,
        bg = 0xFFE6DDC8.toInt(),
        raised = 0xFFF3EEE2.toInt(),
        surface = 0xFFF3EEE2.toInt(),
        focus = 0xFFD9CFB6.toInt(),
        text = 0xFF1A1E16.toInt(),
        dim = 0xFF5C5546.toInt(),
        accent = 0xFF556B2F.toInt(),
        ring = 0xFF5C4630.toInt(),
        line = 0xFFCFC4AA.toInt(),
        highlight = 0xFF5C4630.toInt(),
        alert = 0xFFA8401F.toInt(),
    )

    fun current(c: Context): Palette = when (Prefs.theme(c)) {
        ThemeMode.LIGHT -> LIGHT
        ThemeMode.DARK -> DARK
        ThemeMode.AUTO -> if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 7..18) LIGHT else DARK
    }
}
