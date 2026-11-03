package tech.alius.rc.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** One fine perimeter, with all shading contained inside the rounded track. */
internal class TeleautoLockTrackSurface(private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val radius = TeleautoUiTokens.RADIUS_SM * density
    private val edge = TeleautoUiTokens.LIST_DIVIDER_HEIGHT * density
    private val depth = TeleautoUiTokens.SPACE_1 * density

    override fun draw(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        // Dark at the inner edge, easing continuously into the opaque floor.
        for (step in 0..24) {
            val t = step / 24f
            val inset = edge + depth * t
            rect.set(bounds)
            rect.inset(inset, inset)
            paint.color = blend(TeleautoUiTokens.CRATER_INNER, TeleautoUiTokens.SCREEN_LOCK_SURFACE,
                1f - (1f - t) * (1f - t))
            val r = (radius - inset).coerceAtLeast(0f)
            canvas.drawRoundRect(rect, r, r, paint)
        }
        rect.set(bounds)
        rect.inset(edge / 2f, edge / 2f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = edge
        paint.color = TeleautoUiTokens.VIDEO_FRAME_EDGE
        canvas.drawRoundRect(rect, radius - edge / 2f, radius - edge / 2f, paint)
    }

    fun clipInterior(canvas: Canvas) {
        rect.set(bounds)
        rect.inset(edge + depth, edge + depth)
        val r = (radius - edge - depth).coerceAtLeast(0f)
        path.reset()
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.clipPath(path)
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int) = ((from shr shift and 255) * (1f - t) +
            (to shr shift and 255) * t).toInt()
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
