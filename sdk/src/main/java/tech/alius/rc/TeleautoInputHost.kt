package tech.alius.rc

import android.view.KeyEvent
import android.view.MotionEvent

/** Android host lifecycle/input bridge. All routing, authorization and lock policy stay in core. */
object TeleautoInputHost {
    fun setForeground(foreground: Boolean) = TeleautoJoystickManager.onExternalForegroundChanged(foreground)
    fun dispatchEmbeddedMotion(event: MotionEvent): Boolean = TeleautoJoystickManager.onEmbeddedMotionEvent(event)
    fun dispatchExternalMotion(event: MotionEvent): Boolean = TeleautoJoystickManager.onExternalMotionEvent(event)
    fun dispatchExternalKey(event: KeyEvent): Boolean = TeleautoJoystickManager.onExternalKeyEvent(event)
}
