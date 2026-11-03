package tech.alius.rc.flutter

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.flutter.embedding.android.FlutterActivity
import tech.alius.rc.TeleautoInputHost
import tech.alius.rc.TeleautoJoystickManager
import tech.alius.rc.input.TeleautoJoystickInputSource
import tech.alius.rc.model.TeleautoScreenInputResult

/** Host adapter forwards physical inputs to core before Flutter can reinterpret them as navigation. */
open class TeleautoFlutterActivity : FlutterActivity() {
    override fun onResume(){super.onResume();TeleautoInputHost.setForeground(true)}
    override fun onPause(){TeleautoInputHost.setForeground(false);super.onPause()}
    override fun dispatchGenericMotionEvent(event: MotionEvent):Boolean {
        if(event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK){
            if(TeleautoJoystickManager.inputSource==TeleautoJoystickInputSource.EXTERNAL_JOYSTICK)TeleautoInputHost.dispatchExternalMotion(event)
            else TeleautoInputHost.dispatchEmbeddedMotion(event)
            return true
        }
        if(TeleautoJoystickManager.checkScreenInput()!=TeleautoScreenInputResult.ACCEPTED)return true
        return super.dispatchGenericMotionEvent(event)
    }
    override fun dispatchKeyEvent(event: KeyEvent):Boolean {
        if(TeleautoJoystickManager.inputSource==TeleautoJoystickInputSource.EXTERNAL_JOYSTICK && TeleautoInputHost.dispatchExternalKey(event))return true
        if(TeleautoJoystickManager.handleAndroidKeyEvent(event))return true
        return super.dispatchKeyEvent(event)
    }
}
