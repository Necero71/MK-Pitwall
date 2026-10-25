package app.flowtv.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast

object Launch {
    private fun Intent.newTask() = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun start(c: Context, i: Intent, options: Bundle? = null): Boolean = try {
        c.startActivity(i, options)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /** Opens [a], zooming out of [from] when a tile is given. */
    fun app(c: Context, a: AppInfo, from: View? = null): Boolean {
        val category = if (a.leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
        val i = Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(a.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val options = from?.let { ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle() }
        return start(c, i, options) || pkg(c, a.id)
    }

    fun pkg(c: Context, pkg: String): Boolean {
        val pm = c.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (i == null || !start(c, i.newTask())) {
            toast(c, "Couldn't open $pkg. Is it still installed?")
            return false
        }
        return true
    }

    /** Opens the All apps flyout, or closes it if it's already showing. */
    fun allApps(c: Context) = flyout(c, AllAppsActivity::class.java)

    /** Opens the Settings flyout, or closes it if it's already showing. */
    fun flowSettings(c: Context) = flyout(c, SettingsActivity::class.java)

    private fun flyout(c: Context, cls: Class<out FlyoutActivity>): Boolean {
        val top = BaseActivity.resumed()
        if (top != null && top.javaClass == cls) {
            top.finish()
            return true
        }
        // One flyout at a time: the other one slides away as this one comes in.
        if (top is FlyoutActivity) top.finish()
        // From a Flow screen, open in its task so that screen stays behind the flyout.
        // From the remote helper, open in a task of its own, over whatever app is playing.
        val from: Context = top ?: c
        val i = Intent(from, cls)
        if (from !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        // The flyout animates itself.
        return start(from, i, ActivityOptions.makeCustomAnimation(from, 0, 0).toBundle())
    }

    fun home(c: Context) = start(
        c,
        Intent(c, HomeActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).newTask(),
    )

    fun tvSettings(c: Context) = start(c, Intent(Settings.ACTION_SETTINGS).newTask())

    /** Google Assistant / voice search, trying the intents Android TV builds answer to. */
    fun voice(c: Context): Boolean {
        val tries = listOf(
            Intent(Intent.ACTION_ASSIST),
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent("android.search.action.GLOBAL_SEARCH"),
            Intent(Intent.ACTION_WEB_SEARCH),
        )
        if (tries.any { start(c, it.newTask()) }) return true
        toast(c, "No voice search app on this TV")
        return false
    }

    fun appInfo(c: Context, pkg: String) =
        start(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)).newTask())

    fun uninstall(c: Context, pkg: String) =
        start(c, Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null)).newTask())

    fun toast(c: Context, msg: String) = Toast.makeText(c.applicationContext, msg, Toast.LENGTH_SHORT).show()
}
