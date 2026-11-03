package tech.alius.rc

import android.app.Activity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Host Activity that forwards Android's standard Joystick events to the SDK backend.
 * The public contract is independent from the concrete external-input implementation.
 */
open class TeleautoJoystickActivity : Activity() {

    protected fun setJoystickContentView(layoutResId: Int) {
        setContentView(layoutResId)
    }

    override fun onResume() {
        super.onResume()
        TeleautoJoystickManager.onExternalForegroundChanged(true)
    }

    override fun onPause() {
        TeleautoJoystickManager.onExternalForegroundChanged(false)
        super.onPause()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (joystick) {
            if (TeleautoJoystickManager.inputSource == tech.alius.rc.input.TeleautoJoystickInputSource.EXTERNAL_JOYSTICK) {
                TeleautoJoystickManager.onExternalMotionEvent(event)
            } else {
                TeleautoJoystickManager.onEmbeddedMotionEvent(event)
            }
            // The selected profile consumes embedded axes; never let
            // Android reinterpret them as focus navigation or scrolling.
            return true
        }
        if (TeleautoJoystickManager.checkScreenInput() != tech.alius.rc.model.TeleautoScreenInputResult.ACCEPTED) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Collect first, even while locked; the SDK output gate suppresses delivery.
        if (TeleautoJoystickManager.inputSource == tech.alius.rc.input.TeleautoJoystickInputSource.EXTERNAL_JOYSTICK &&
            TeleautoJoystickManager.onExternalKeyEvent(event)
        ) return true
        if (TeleautoJoystickManager.handleAndroidKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /** Navigation generated from an already-authorized SDK callback must not loop back as raw input. */
    protected fun dispatchSdkNavigationEvent(event: KeyEvent): Boolean = super.dispatchKeyEvent(event)
}
