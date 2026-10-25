package app.flowtv.launcher.remote

import android.app.Activity
import android.view.KeyEvent
import app.flowtv.launcher.Launch
import app.flowtv.launcher.Palette
import app.flowtv.launcher.ui.Sheet

/** "Press the Netflix button now": records whatever code the remote really sends. */
object Teach {
    fun show(act: Activity, pal: Palette, b: RemoteButton, done: (() -> Unit)? = null) {
        val sheet = Sheet(
            act, pal, "Teach the ${b.label}",
            "Press the ${b.short} button on your remote now. Back cancels.",
        )
        if (!Remote.serviceOn(act)) sheet.note(
            "Tip: turn on Remote helper in Flow settings first. Otherwise the TV may grab this button before Flow sees it.",
        )
        sheet.item("Cancel") {}
        Remote.onLearned = { learned, code ->
            sheet.dismiss()
            val name = if (code == 0) "scan code ${Remote.learnedScan(act, learned)}"
            else KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            Launch.toast(act, "Got it: ${learned.label} learned ($name)")
            done?.invoke()
        }
        sheet.onDismiss = {
            if (Remote.learning == b) Remote.learning = null
            Remote.onLearned = null
        }
        Remote.learning = b
        sheet.show()
    }
}
