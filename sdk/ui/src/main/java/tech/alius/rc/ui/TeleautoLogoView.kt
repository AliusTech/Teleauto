package tech.alius.rc.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import tech.alius.rc.ui.TeleautoUiTokens

/** User-provided Alius mark, fitted without stretching. */
class TeleautoLogoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density * TeleautoResponsiveMetrics.scale(context)
    private val wordmark by lazy { resources.getDrawable(R.drawable.ic_alius_logo, context.theme).mutate().apply { setTint(TeleautoUiTokens.TEXT_SECONDARY) } }

    var compact = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthDp = if (compact) 132 else 150
        val heightDp = if (compact) 76 else 86
        setMeasuredDimension(
            resolveSize((widthDp * density).toInt(), widthMeasureSpec),
            resolveSize((heightDp * density).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val inset = 2f * density
        val availableW = width - inset * 2f
        val availableH = height - inset * 2f
        val aspect = wordmark.intrinsicWidth.toFloat() / wordmark.intrinsicHeight
        val drawW = minOf(availableW, availableH * aspect)
        val drawH = drawW / aspect
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        wordmark.setBounds(left.toInt(), top.toInt(), (left + drawW).toInt(), (top + drawH).toInt())
        wordmark.draw(canvas)
    }
}
