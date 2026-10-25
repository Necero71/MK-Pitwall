package app.flowtv.launcher

import android.app.Activity
import android.view.View
import app.flowtv.launcher.remote.Remote
import app.flowtv.launcher.remote.RemoteButton
import app.flowtv.launcher.remote.Teach
import app.flowtv.launcher.ui.Sheet
import app.flowtv.launcher.ui.tinted

/** The hold-OK menu for an app, used on the home screen and in All apps. */
object AppMenu {
    fun show(
        act: Activity,
        pal: Palette,
        a: AppInfo,
        from: View?,
        onFavChanged: () -> Unit,
        onMove: (() -> Unit)? = null,
    ) {
        val favs = (Prefs.favourites(act) ?: emptyList()).toMutableList()
        fun icon(res: Int) = tinted(act, res, pal.text)

        val s = Sheet(act, pal, a.label, a.id)
        s.item("Open", icon = icon(R.drawable.ic_play)) { Launch.app(act, a, from) }
        if (a.id in favs) {
            if (onMove != null) s.item("Move", "Slide it along the row with ← →", icon(R.drawable.ic_move)) { onMove() }
            s.item("Remove from Favourites", icon = icon(R.drawable.ic_star_off)) {
                favs.remove(a.id)
                Prefs.setFavourites(act, favs)
                onFavChanged()
            }
        } else {
            s.item("Add to Favourites", icon = icon(R.drawable.ic_star)) {
                favs.add(a.id)
                Prefs.setFavourites(act, favs)
                onFavChanged()
            }
        }
        s.item("Put on a remote button", "Netflix, Prime Video, Apps or Voice", icon(R.drawable.ic_remote)) {
            pickButton(act, pal, a)
        }
        s.item("App info", icon = icon(R.drawable.ic_info)) { Launch.appInfo(act, a.id) }
        s.item("Uninstall", icon = icon(R.drawable.ic_delete)) { Launch.uninstall(act, a.id) }
        s.show()
    }

    private fun pickButton(act: Activity, pal: Palette, a: AppInfo) {
        val s = Sheet(act, pal, "Put ${a.label} on…", "The button will open ${a.label} from anywhere.")
        for (b in RemoteButton.values()) {
            s.item(b.label, "Now opens: " + Remote.describe(act, Remote.action(act, b))) {
                Remote.setAction(act, b, Remote.appAction(a.id))
                if (Remote.isTaught(act, b)) Launch.toast(act, "${b.label} now opens ${a.label}")
                else Teach.show(act, pal, b)
            }
        }
        s.show()
    }
}
