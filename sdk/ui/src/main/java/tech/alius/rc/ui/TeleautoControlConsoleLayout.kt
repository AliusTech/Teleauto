package tech.alius.rc.ui

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/** Shared console shell. Locking suppresses touch dispatch, never hardware events. */
class TeleautoControlConsoleLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    var onLockChanged: ((Boolean) -> Unit)? = null
    var onLockedTouch: (() -> Unit)? = null
    private lateinit var slider: TeleautoSlideLockView

    override fun onFinishInflate() {
        super.onFinishInflate()
        slider = findViewById(R.id.slideLock)
        slider.onLockChanged = { locked ->
            if (locked) {
                findViewById<TeleautoHardwareControlsView>(R.id.specialControlsLeft).releaseTouches()
                findViewById<TeleautoHardwareControlsView>(R.id.specialControlsRight).releaseTouches()
            }
            onLockChanged?.invoke(locked)
        }
        TeleautoUiSurfaces.applyConsoleBackground(this)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (slider.locked) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) onLockedTouch?.invoke()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    /** The host clears touch ownership on lock before forwarding input here. */
    fun onLockedKeyEvent(event: tech.alius.rc.model.TeleautoRcKeyEvent) = slider.onLockedKeyEvent(event)
    fun onLockedHeldKeys(keys: Set<tech.alius.rc.model.TeleautoPhysicalKey>) = slider.onLockedHeldKeys(keys)
    fun cancelUnlock() = slider.cancelUnlock()
    fun submitSdkLockState(locked: Boolean, progress: Float) = slider.submitSdkLockState(locked, progress)

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelUnlock()
    }

    override fun onSaveInstanceState(): Parcelable = Bundle().apply {
        putParcelable("super", super.onSaveInstanceState())
        putBoolean("locked", slider.locked)
    }
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable("super"))
            slider.setLocked(state.getBoolean("locked"))
        } else super.onRestoreInstanceState(state)
    }
}
