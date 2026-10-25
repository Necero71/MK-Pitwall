package app.flowtv.launcher

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    fun store(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("flow", Context.MODE_PRIVATE)

    fun theme(c: Context): ThemeMode =
        runCatching { ThemeMode.valueOf(store(c).getString("theme", null) ?: "AUTO") }
            .getOrDefault(ThemeMode.AUTO)

    fun setTheme(c: Context, mode: ThemeMode) = store(c).edit().putString("theme", mode.name).apply()

    /** Package names on the Favourites row, in order. Null until the first run picks a set. */
    fun favourites(c: Context): List<String>? =
        store(c).getString("favs", null)?.split('|')?.filter { it.isNotEmpty() }

    fun setFavourites(c: Context, ids: List<String>) =
        store(c).edit().putString("favs", ids.joinToString("|")).putInt("favStamp", favStamp(c) + 1).apply()

    fun clearFavourites(c: Context) =
        store(c).edit().remove("favs").putInt("favStamp", favStamp(c) + 1).apply()

    /** Bumped on every Favourites change so the home screen knows to rebuild when it resumes. */
    fun favStamp(c: Context) = store(c).getInt("favStamp", 0)
}
