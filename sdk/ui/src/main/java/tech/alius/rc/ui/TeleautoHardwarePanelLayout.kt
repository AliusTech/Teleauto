package tech.alius.rc.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.view.View

/** Full-width continuous dial below the three aligned right-side button rows. */
class TeleautoHardwarePanelLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, h)
        if (childCount < 2) return
        val dial = getChildAt(1)
        dial.measure(View.MeasureSpec.makeMeasureSpec(measuredWidth.coerceAtLeast(1), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(measuredHeight, View.MeasureSpec.AT_MOST))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (childCount < 2) return
        val footer = getChildAt(1)
        val top = (height - paddingBottom - footer.measuredHeight).coerceAtLeast(0)
        footer.layout(0, top, footer.measuredWidth, top + footer.measuredHeight)
    }
}
