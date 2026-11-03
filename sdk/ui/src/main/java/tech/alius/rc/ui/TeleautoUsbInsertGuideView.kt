package tech.alius.rc.ui

import android.animation.Keyframe
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator

/** Small, isolated explanation animation for inserting the USB-A video receiver. */
class TeleautoUsbInsertGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val port = context.getDrawable(R.drawable.djirc_ui_ic_usb_a_port)
    private val plug = context.getDrawable(R.drawable.djirc_ui_ic_usb_a_plug)
    private var insertion = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val animator by lazy {
        val enter = PathInterpolator(0.77f, 0f, 0.175f, 1f)
        val exit = PathInterpolator(0.23f, 1f, 0.32f, 1f)
        val frames = arrayOf(
            Keyframe.ofFloat(0f, 0f),
            Keyframe.ofFloat(0.18f, 0f),
            Keyframe.ofFloat(0.42f, 1f).apply { interpolator = enter },
            Keyframe.ofFloat(0.68f, 1f),
            Keyframe.ofFloat(0.82f, 0f).apply { interpolator = exit },
            Keyframe.ofFloat(1f, 0f),
        )
        ValueAnimator.ofPropertyValuesHolder(
            PropertyValuesHolder.ofKeyframe("insertion", *frames),
        ).apply {
            duration = 1_600L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { insertion = it.getAnimatedValue("insertion") as Float }
        }
    }

    fun setGuideVisible(visible: Boolean) {
        if (visible) {
            if (visibility != VISIBLE) visibility = VISIBLE
            if (!animator.isStarted) animator.start()
        } else {
            animator.cancel()
            insertion = 0f
            visibility = GONE
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val iconSize = (42 * density).toInt()
        val left = (width - iconSize) / 2
        val portTop = (height * 0.50f).toInt()
        port?.setBounds(left, portTop, left + iconSize, portTop + iconSize)
        port?.draw(canvas)

        val travel = 16f * density
        val plugTop = (height * 0.02f + insertion * travel).toInt()
        plug?.alpha = (150 + insertion * 105).toInt()
        plug?.setBounds(left, plugTop, left + iconSize, plugTop + iconSize)
        plug?.draw(canvas)
    }
}
