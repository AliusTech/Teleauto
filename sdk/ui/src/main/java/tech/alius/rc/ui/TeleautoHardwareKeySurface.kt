package tech.alius.rc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Raised key face with a fixed base. Pressing moves only the face, never its hit area. */
internal class TeleautoHardwareKeySurface(context: Context, private val density: Float) {
    private val face = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val raised = context.getDrawable(R.drawable.djirc_v2_key_raised)!!.mutate()
    private val pressed = context.getDrawable(R.drawable.djirc_v2_key_pressed)!!.mutate()
    private val roundRaised = context.getDrawable(R.drawable.djirc_v2_round_raised)!!.mutate()
    private val roundPressed = context.getDrawable(R.drawable.djirc_v2_round_pressed)!!.mutate()

    fun draw(canvas: Canvas, bounds: RectF, round: Boolean, down: Boolean, light: Float, tint: Int? = null): RectF {
        val edge = TeleautoUiTokens.HARDWARE_KEY_EDGE * density
        val depth = TeleautoUiTokens.HARDWARE_KEY_DEPTH * density
        val radius = if (round) bounds.width() / 2 else TeleautoUiTokens.RADIUS_CONTROL * density
        val material = if (round) {
            if (down) roundPressed else roundRaised
        } else if (down) pressed else raised
        face.set(bounds.left + edge, bounds.top + edge + if (down) depth - edge else 0f,
            bounds.right - edge, bounds.bottom - if (down) edge else depth)
        if (round) {
            val half = minOf(face.width(), face.height()) / 2
            face.set(face.centerX() - half, face.centerY() - half,
                face.centerX() + half, face.centerY() + half)
        }
        if (tint != null) {
            drawMatteFace(canvas, bounds, radius, round, down, light, tint, edge)
            return face
        }
        material.colorFilter = null
        material.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        material.draw(canvas)
        if (!down && light > 0f) {
            val illuminated = if (round) roundPressed else pressed
            illuminated.colorFilter = null
            illuminated.bounds = material.bounds
            illuminated.alpha = (255 * light.coerceIn(0f, 1f)).toInt()
            illuminated.draw(canvas)
            illuminated.alpha = 255
        }
        return face
    }
    private fun drawMatteFace(canvas: Canvas, bounds: RectF, radius: Float, round: Boolean,
        down: Boolean, light: Float, color: Int, edge: Float) {
        paint.style = Paint.Style.FILL
        paint.color = TeleautoUiTokens.CRATER_INNER
        canvas.drawRoundRect(bounds, radius, radius, paint)
        val shade = (if (down) TeleautoUiTokens.MATTE_KEY_PRESSED_SHADE else TeleautoUiTokens.MATTE_KEY_SHADE) +
            light.coerceIn(0f, 1f) * TeleautoUiTokens.MATTE_KEY_LIGHT
        fun shaded(factor: Float) = android.graphics.Color.rgb(
            (android.graphics.Color.red(color) * factor).toInt().coerceIn(0, 255),
            (android.graphics.Color.green(color) * factor).toInt().coerceIn(0, 255),
            (android.graphics.Color.blue(color) * factor).toInt().coerceIn(0, 255),
        )
        val faceRadius = if (round) face.width() / 2f else radius
        paint.color = shaded(shade)
        canvas.drawRoundRect(face, faceRadius, faceRadius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = edge
        paint.color = shaded(shade + TeleautoUiTokens.MATTE_KEY_EDGE_LIGHT)
        canvas.drawRoundRect(face, faceRadius, faceRadius, paint)
        paint.style = Paint.Style.FILL
    }

}
