package tech.alius.rc.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Resolution-independent shallow moulded recess, shared by sticks and both dial types. */
internal class TeleautoCraterSurface(private val track: Boolean = false, private val cornerRadius: Float? = null, private val mouldingUnit: Float? = null) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val unit = mouldingUnit ?: if (track) rect.height() else rect.width() / 12f
        val radius = cornerRadius ?: if (track) unit / 2 else unit
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, TeleautoUiTokens.CRATER_EDGE_TOP, TeleautoUiTokens.CRATER_EDGE_BOTTOM, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        // Continuous nested gradients form a shoulder without an outlined black capsule.
        val depth = if (track) unit * .14f else unit * .48f
        for (step in 0..15) {
            val inset = depth * step / 15f
            rect.set(bounds); rect.inset(inset, inset)
            val blend = step / 15f
            val top = mix(TeleautoUiTokens.CRATER_SHOULDER, TeleautoUiTokens.CRATER_INNER, blend)
            val bottom = mix(TeleautoUiTokens.CRATER_EDGE_BOTTOM, TeleautoUiTokens.CRATER_FLOOR, blend)
            paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, top, bottom, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rect, (radius - inset).coerceAtLeast(0f), (radius - inset).coerceAtLeast(0f), paint)
        }
        paint.shader = null
    }
    /** Overlay colors belong to the floor, never the moulded shoulder or outside corners. */
    fun clipInterior(canvas: Canvas) {
        val floor = RectF(bounds)
        val unit = mouldingUnit ?: if (track) floor.height() else floor.width() / 12f
        val radius = cornerRadius ?: if (track) unit / 2 else unit
        val depth = if (track) unit * .14f else unit * .48f
        floor.inset(depth, depth)
        val path = android.graphics.Path().apply {
            addRoundRect(floor, (radius - depth).coerceAtLeast(0f),
                (radius - depth).coerceAtLeast(0f), android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(path)
    }
    private fun mix(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int) = (((a shr shift and 255) * (1-t)) + ((b shr shift and 255) * t)).toInt()
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
