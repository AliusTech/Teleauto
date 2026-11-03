package tech.alius.rc.ui.internal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import tech.alius.rc.ui.TeleautoUiTokens

/** Figma 1:3 center shell. All depth is baked once per size for Android 7. */
internal class TeleautoZoneBackgroundDrawable : Drawable() {
    private val density = android.content.res.Resources.getSystem().displayMetrics.density
    private val radius = TeleautoUiTokens.FIGMA_CENTER_SHELL_RADIUS * density
    private val cachePad = (TeleautoUiTokens.SPACE_1 * density).toInt()
    private var cache: Bitmap? = null
    private var cacheW = -1
    private var cacheH = -1
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) return
        if (cache == null || cacheW != width || cacheH != height) {
            cache?.recycle()
            cache = bake(width, height)
            cacheW = width
            cacheH = height
        }
        canvas.drawBitmap(cache!!, (bounds.left - cachePad).toFloat(), (bounds.top - cachePad).toFloat(), bitmapPaint)
    }

    private fun bake(width: Int, height: Int): Bitmap {
        val pad = cachePad
        val bitmapWidth = width + pad * 2
        val bitmapHeight = height + pad * 2
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(pad.toFloat(), pad.toFloat(), (width + pad).toFloat(), (height + pad).toFloat())

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.SHADOW_DEEP }
        for (offsetDp in floatArrayOf(2.4f, 1.2f)) {
            shadowPaint.alpha = if (offsetDp > 2f) 112 else 64
            canvas.drawRoundRect(
                RectF(rect.left, rect.top + offsetDp * density, rect.right, rect.bottom + offsetDp * density),
                radius, radius, shadowPaint,
            )
        }

        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width * 0.2f, height.toFloat(),
                intArrayOf(0xFF464646.toInt(), TeleautoUiTokens.SURFACE_RAISED, 0xFF383838.toInt()),
                floatArrayOf(0f, 0.48f, 1f), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, radius, radius, gradientPaint)

        val roundPath = android.graphics.Path().apply {
            addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW)
        }
        val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x05FFFFFF
            strokeWidth = 0.55f * density
        }
        canvas.save()
        canvas.clipPath(roundPath)
        canvas.rotate(-30f, bitmapWidth / 2f, bitmapHeight / 2f)
        val span = (kotlin.math.hypot(bitmapWidth.toDouble(), bitmapHeight.toDouble()) / 2).toFloat() + 10f
        var x = bitmapWidth / 2f - span
        while (x < bitmapWidth / 2f + span) {
            canvas.drawLine(x, -span, x, span, texturePaint)
            x += TeleautoUiTokens.SPACE_1 * density
        }
        canvas.restore()

        val topLightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, 2f * density, TeleautoUiTokens.GLASS_HIGHLIGHT, 0, Shader.TileMode.CLAMP)
        }
        canvas.save()
        canvas.clipPath(roundPath)
        canvas.clipRect(0f, 0f, bitmapWidth.toFloat(), pad + 2f * density)
        canvas.drawRoundRect(rect, radius, radius, topLightPaint)
        canvas.restore()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.4f * density
            color = TeleautoUiTokens.GLASS_BORDER
        }
        canvas.drawRoundRect(
            RectF(
                rect.left + 0.5f * density, rect.top + 0.5f * density,
                rect.right - 0.5f * density, rect.bottom - 0.5f * density,
            ),
            radius, radius, borderPaint,
        )
        return bitmap
    }

    override fun setAlpha(alpha: Int) { bitmapPaint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}
