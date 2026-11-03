package tech.alius.rc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import tech.alius.rc.ui.TeleautoUiTokens
import tech.alius.rc.ui.TeleautoUiTypography
import tech.alius.rc.model.TeleautoStickSnapshot
import kotlin.math.abs
import kotlin.math.min

/** BLE HID 模式下的本地 Xbox 风格实时输入监视器。 */
class TeleautoJoystickMonitorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = TeleautoUiTypography.label
        textAlign = Paint.Align.CENTER
    }
    // 0..16 对应 Standard Gamepad；17 仅用于本地显示 5D 中键/Share，不进入 HID。
    private val buttons = BooleanArray(18)
    private var snapshot = TeleautoStickSnapshot()

    private val background = TeleautoUiTokens.BACKGROUND
    private val bodyStroke = TeleautoUiTokens.TEXT_FAINT
    private val idle = TeleautoUiTokens.TRACK_BASE
    private val active = TeleautoUiTokens.SUCCESS
    private val leftTriggerAccent = TeleautoUiTokens.PRIMARY
    private val rightTriggerAccent = TeleautoUiTokens.WARNING
    private val text = TeleautoUiTokens.TEXT
    private val muted = TeleautoUiTokens.TEXT_SECONDARY

    fun setButton(index: Int, pressed: Boolean) {
        if (index !in buttons.indices || buttons[index] == pressed) return
        buttons[index] = pressed
        invalidate()
    }

    fun setSnapshot(value: TeleautoStickSnapshot) {
        if (snapshot == value) return
        snapshot = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(background)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        // 外围控制与状态已缩到两侧底部，监视器按实际控件边界放大绘制。
        // 920 x 520 会裁掉原设计中未使用的留白，不裁切任何手柄控件。
        val scale = min(w / 920f, h / 520f)
        val ox = (w - 1000f * scale) / 2f
        val oy = (h - 520f * scale) / 2f
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(scale, scale)

        drawSectionLabel(canvas, 205f, 12f, "LEFT TRIGGER")
        drawSectionLabel(canvas, 795f, 12f, "RIGHT TRIGGER")
        drawTrigger(canvas, 205f, 51f, "LT", abs(snapshot.leftDial), 6, true, leftTriggerAccent)
        drawTrigger(canvas, 795f, 51f, "RT", abs(snapshot.rightDial), 7, false, rightTriggerAccent)
        drawPill(canvas, 250f, 112f, 105f, 42f, "LB", buttons[4])
        drawPill(canvas, 750f, 112f, 105f, 42f, "RB", buttons[5])

        drawStick(canvas, 255f, 238f, snapshot.leftX, snapshot.leftY, buttons[10], "L")
        drawDpad(canvas, 365f, 365f)
        drawStick(canvas, 645f, 365f, snapshot.rightX, snapshot.rightY, buttons[11], "R")

        drawFace(canvas, 785f, 205f, "Y", buttons[3], Color.rgb(234, 179, 8))
        drawFace(canvas, 845f, 265f, "B", buttons[1], Color.rgb(239, 68, 68))
        drawFace(canvas, 785f, 325f, "A", buttons[0], Color.rgb(34, 197, 94))
        drawFace(canvas, 725f, 265f, "X", buttons[2], Color.rgb(59, 130, 246))

        drawSystemButton(canvas, 435f, 238f, buttons[8], SystemIcon.VIEW)
        drawSystemButton(canvas, 565f, 238f, buttons[9], SystemIcon.MENU)
        drawSystemButton(canvas, 500f, 176f, buttons[16], SystemIcon.HOME)
        drawSystemButton(canvas, 500f, 292f, buttons[17], SystemIcon.SHARE)

        canvas.restore()
    }

    private fun drawStick(c: Canvas, x: Float, y: Float, sx: Float, sy: Float, pressed: Boolean, label: String) {
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(25, 31, 42)
        c.drawCircle(x, y, 78f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = if (pressed) active else bodyStroke
        c.drawCircle(x, y, 78f, paint)
        val px = x + sx.coerceIn(-1f, 1f) * 38f
        val py = y + sy.coerceIn(-1f, 1f) * 38f
        paint.style = Paint.Style.FILL; paint.color = if (abs(sx) > .03f || abs(sy) > .03f || pressed) active else idle
        c.drawCircle(px, py, 43f, paint)
        paint.color = text; paint.textSize = 20f
        c.drawText(label, px, py + 7f, paint)
    }

    private fun drawDpad(c: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(25, 31, 42)
        c.drawCircle(x, y, 77f, paint)
        drawDpadPart(c, RectF(x - 24f, y - 70f, x + 24f, y - 8f), buttons[12])
        drawDpadPart(c, RectF(x - 24f, y + 8f, x + 24f, y + 70f), buttons[13])
        drawDpadPart(c, RectF(x - 70f, y - 24f, x - 8f, y + 24f), buttons[14])
        drawDpadPart(c, RectF(x + 8f, y - 24f, x + 70f, y + 24f), buttons[15])
        paint.color = idle; c.drawRect(x - 25f, y - 25f, x + 25f, y + 25f, paint)
    }

    private fun drawDpadPart(c: Canvas, rect: RectF, on: Boolean) {
        paint.color = if (on) active else idle
        c.drawRoundRect(rect, 8f, 8f, paint)
    }

    private fun drawFace(c: Canvas, x: Float, y: Float, label: String, on: Boolean, accent: Int) {
        paint.style = Paint.Style.FILL; paint.color = if (on) accent else Color.rgb(31, 38, 50)
        c.drawCircle(x, y, 31f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.color = accent
        c.drawCircle(x, y, 31f, paint)
        paint.style = Paint.Style.FILL; paint.color = if (on) Color.WHITE else text; paint.textSize = 25f
        c.drawText(label, x, y + 9f, paint)
    }

    private fun drawPill(c: Canvas, x: Float, y: Float, width: Float, height: Float, label: String, on: Boolean) {
        paint.style = Paint.Style.FILL; paint.color = if (on) active else idle
        c.drawRoundRect(RectF(x - width / 2, y - height / 2, x + width / 2, y + height / 2), 12f, 12f, paint)
        paint.color = text; paint.textSize = 20f; c.drawText(label, x, y + 7f, paint)
    }

    private fun drawSectionLabel(c: Canvas, x: Float, y: Float, label: String) {
        paint.style = Paint.Style.FILL; paint.color = muted; paint.textSize = 12f
        c.drawText(label, x, y, paint)
    }

    private fun drawTrigger(
        c: Canvas,
        x: Float,
        y: Float,
        label: String,
        value: Float,
        index: Int,
        reverse: Boolean,
        accent: Int,
    ) {
        val on = value > .05f || buttons[index]
        val rail = RectF(x - 75f, y - 24f, x + 75f, y + 24f)
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(28, 37, 50)
        c.drawRoundRect(rail, 14f, 14f, paint)
        if (value > .05f) {
            paint.color = accent
            val amount = 150f * value.coerceIn(0f, 1f)
            val fill = if (reverse) {
                RectF(x + 75f - amount, y - 24f, x + 75f, y + 24f)
            } else {
                RectF(x - 75f, y - 24f, x - 75f + amount, y + 24f)
            }
            c.drawRoundRect(
                fill,
                14f,
                14f,
                paint,
            )
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = if (on) 4f else 2f
        paint.color = if (on) accent else bodyStroke
        c.drawRoundRect(rail, 14f, 14f, paint)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.textSize = 20f
        c.drawText(label, x - 43f, y + 7f, paint)
        paint.textAlign = Paint.Align.RIGHT; paint.textSize = 14f
        c.drawText("${(value.coerceIn(0f, 1f) * 100).toInt()}%", x + 57f, y + 5f, paint)
        paint.textAlign = Paint.Align.CENTER
    }

    private enum class SystemIcon { VIEW, MENU, HOME, SHARE }

    private fun drawSystemButton(c: Canvas, x: Float, y: Float, on: Boolean, icon: SystemIcon) {
        val radius = if (icon == SystemIcon.HOME) 34f else 27f
        paint.style = Paint.Style.FILL; paint.color = if (on) active else idle
        c.drawCircle(x, y, radius, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f; paint.strokeCap = Paint.Cap.ROUND
        paint.color = text
        when (icon) {
            SystemIcon.VIEW -> {
                c.drawRoundRect(RectF(x - 11f, y - 9f, x + 7f, y + 9f), 2f, 2f, paint)
                c.drawRoundRect(RectF(x - 5f, y - 5f, x + 13f, y + 13f), 2f, 2f, paint)
            }
            SystemIcon.MENU -> for (dy in floatArrayOf(-8f, 0f, 8f)) c.drawLine(x - 12f, y + dy, x + 12f, y + dy, paint)
            SystemIcon.HOME -> {
                c.drawLine(x - 13f, y - 13f, x + 13f, y + 13f, paint)
                c.drawLine(x + 13f, y - 13f, x - 13f, y + 13f, paint)
            }
            SystemIcon.SHARE -> {
                c.drawRoundRect(RectF(x - 11f, y - 4f, x + 11f, y + 12f), 2f, 2f, paint)
                c.drawLine(x, y + 3f, x, y - 14f, paint)
                c.drawLine(x, y - 14f, x - 6f, y - 7f, paint)
                c.drawLine(x, y - 14f, x + 6f, y - 7f, paint)
            }
        }
        paint.strokeCap = Paint.Cap.BUTT
    }
}
