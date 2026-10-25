package app.flowtv.launcher.remote

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Sees remote key presses before Android's own shortcut handling, so the Netflix,
 * Prime Video, Apps and Voice buttons can open whatever you picked, from any app.
 */
class RemoteKeyService : AccessibilityService() {
    override fun onServiceConnected() {
        running = true
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = Remote.filter(this, event)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        var running = false
            private set
    }
}
