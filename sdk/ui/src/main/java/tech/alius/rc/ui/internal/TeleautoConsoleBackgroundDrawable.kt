package tech.alius.rc.ui.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import tech.alius.rc.ui.TeleautoUiTokens

/** Dark anodized-metal base with restrained woven micro-texture. */
internal class TeleautoConsoleBackgroundDrawable : Drawable() {
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val weavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x02FFFFFF; strokeWidth = 0.55f }
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x0A000000 }
    private var shader: Shader? = null
    private var shaderWidth = -1
    private var shaderHeight = -1
    private var cache: Bitmap? = null
    private val cachePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) return
        if (cache == null || cache?.width != width || cache?.height != height) {
            cache?.recycle()
            cache = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                drawTexture(Canvas(bitmap), width.toFloat(), height.toFloat())
            }
        }
        canvas.drawBitmap(cache!!, bounds.left.toFloat(), bounds.top.toFloat(), cachePaint)
    }

    private fun drawTexture(canvas: Canvas, w: Float, h: Float) {
        if (shader == null || shaderWidth != w.toInt() || shaderHeight != h.toInt()) {
            shaderWidth = w.toInt()
            shaderHeight = h.toInt()
            shader = LinearGradient(
                0f, 0f, w, h,
                intArrayOf(TeleautoUiTokens.CONSOLE_MATTE_TOP, TeleautoUiTokens.CONSOLE_MATTE_BOTTOM, TeleautoUiTokens.CONSOLE_MATTE_BOTTOM),
                floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP,
            )
        }
        basePaint.shader = shader
        canvas.drawRect(0f, 0f, w, h, basePaint)
        drawLines(canvas, w, h, 24f, 8.5f)
        drawLines(canvas, w, h, -24f, 13f)
        repeat(96) { index ->
            val x = ((index * 47) % 101) / 101f * w
            val y = ((index * 71) % 97) / 97f * h
            canvas.drawCircle(x, y, if (index % 3 == 0) 0.7f else 0.45f, grainPaint)
        }
    }

    private fun drawLines(canvas: Canvas, w: Float, h: Float, angle: Float, spacing: Float) {
        val save = canvas.save()
        canvas.rotate(angle, w / 2f, h / 2f)
        val span = kotlin.math.hypot(w.toDouble(), h.toDouble()).toFloat()
        var x = -span
        while (x < w + span) {
            canvas.drawLine(x, -span, x, h + span, weavePaint)
            x += spacing
        }
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) { cachePaint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { basePaint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
