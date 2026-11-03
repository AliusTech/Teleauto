package tech.alius.rc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Shared central-panel geometry. Flat frames leave live video pixels untouched. */
internal class TeleautoCentralSurface(context: Context, private val raised: Boolean) : Drawable() {
    private val density = context.resources.displayMetrics.density * TeleautoResponsiveMetrics.scale(context)
    private val radius = TeleautoUiTokens.FIGMA_VIDEO_RADIUS * density
    private val edge = TeleautoUiTokens.LIST_DIVIDER_HEIGHT * density
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        if (raised) {
            // Uniform face and one constant-color edge, without directional relief.
            paint.shader = null
            paint.style = Paint.Style.FILL
            paint.color = TeleautoUiTokens.CRATER_FLOOR
            canvas.drawRoundRect(rect, radius, radius, paint)
            rect.inset(edge / 2f, edge / 2f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = edge
            paint.color = TeleautoUiTokens.VIDEO_FRAME_EDGE
            canvas.drawRoundRect(rect, radius - edge / 2f, radius - edge / 2f, paint)
        } else {
            rect.inset(edge / 2f, edge / 2f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = edge
            paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
                TeleautoUiTokens.GLASS_BORDER, TeleautoUiTokens.SURFACE, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, radius - edge / 2f, radius - edge / 2f, paint)
        }
        paint.shader = null
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
