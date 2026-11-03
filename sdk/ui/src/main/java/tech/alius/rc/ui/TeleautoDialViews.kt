package tech.alius.rc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import tech.alius.rc.ui.TeleautoUiTokens
import tech.alius.rc.ui.TeleautoUiTypography
import kotlin.math.abs
import kotlin.math.roundToInt

/** 紧凑只读拨轮状态。数据直接跟随硬件，不插值、不消费触摸。 */
open class TeleautoDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var label: String = "S1"
        set(value) { if (field != value) { field = value; invalidate() } }

    /** 两类拨轮都以 0 为中心；无限拨轮仅锁存位置，不绘制回中滑块。 */
    var centered: Boolean = true
        set(value) { if (field != value) { field = value; invalidate() } }

    /** Centering dials communicate state spatially; continuous dials also expose a value. */
    var showValue: Boolean = true
        set(value) { if (field != value) { field = value; invalidate() } }

    var position: Float = 0f
        set(value) {
            val next = value.coerceIn(-1f, 1f).let { if (abs(it) < .025f) 0f else it }
            if (field != next) {
                field = next
                valueText = (next * 100).roundToInt().toString()
                invalidate()
            }
        }

    fun submit(state: TeleautoDialUiState) {
        label = state.label
        position = state.position
    }

    private val density = resources.displayMetrics.density * TeleautoResponsiveMetrics.scale(context)
    private val scaledDensity = resources.displayMetrics.scaledDensity * TeleautoResponsiveMetrics.scale(context)
    private val track = RectF()
    private val fill = RectF()
    private val fillClip = Path()
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.SURFACE_RAISED }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.DIAL_TICK_COLOR
        strokeWidth = TeleautoUiTokens.LIST_DIVIDER_HEIGHT * density
        strokeCap = Paint.Cap.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.TRACK_BASE }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.PRIMARY }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.TEXT_FAINT }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f * scaledDensity
        color = TeleautoUiTokens.TEXT; textAlign = Paint.Align.RIGHT; typeface = TeleautoUiTypography.numericStrong
    }
    private val trackDrawable by lazy {
        context.getDrawable(R.drawable.djirc_v2_dial_track)!!.mutate()
    }
    private val knobDrawable by lazy {
        resources.getDrawable(R.drawable.djirc_v2_dial_thumb, context.theme).mutate()
    }
    private var valueText = "0"

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(
        resolveSize((TeleautoUiTokens.FIGMA_DIAL_WIDTH * density).toInt(), widthMeasureSpec),
        resolveSize(((if (!centered) TeleautoUiTokens.CONTROL_HEIGHT else if (showValue) 22f else TeleautoUiTokens.CENTERING_DIAL_HEIGHT) * density).toInt(), heightMeasureSpec),
    )

    override fun onDraw(canvas: Canvas) {
        if (showValue) {
            val textY = 8f * density - (valueTextPaint.ascent() + valueTextPaint.descent()) / 2f
            canvas.drawText(valueText, width.toFloat(), textY, valueTextPaint)
        }

        val top = if (!centered && !showValue) height - TeleautoUiTokens.FIGMA_DIAL_HEIGHT * density else if (showValue) 12f * density else (height / 2f - TeleautoUiTokens.FIGMA_DIAL_HEIGHT * density / 2f)
        val trackHeight = TeleautoUiTokens.JOYSTICK_SIDE_TRACK_THICKNESS * density
        track.set(0f, top, width.toFloat(), top + trackHeight)
        trackDrawable.setBounds(track.left.toInt(), track.top.toInt(), track.right.toInt(), track.bottom.toInt())
        trackDrawable.draw(canvas)
        val centerX = width / 2f
        val markerX = centerX + position * centerX
        // Use the same position as the thumb for both hardware and touch updates.
        // Leave the material's recessed rim visible, then draw the thumb above it.
        if (position != 0f) {
            val inset = TeleautoUiTokens.LIST_DIVIDER_HEIGHT * density
            fill.set(track)
            fill.inset(inset, inset)
            if (fill.width() > 0f && fill.height() > 0f) {
                fillClip.rewind()
                fillClip.addRoundRect(fill, fill.height() / 2f, fill.height() / 2f, Path.Direction.CW)
                val save = canvas.save()
                canvas.clipPath(fillClip)
                canvas.drawRect(minOf(centerX, markerX), fill.top,
                    maxOf(centerX, markerX), fill.bottom, valuePaint)
                canvas.restoreToCount(save)
            }
        }
        // Etched scale belongs to this component, above progress and below its thumb.
        val tickInset = trackHeight / 2f
        val tickSpan = (width - 2f * tickInset).coerceAtLeast(0f)
        for (index in 0..TeleautoUiTokens.DIAL_TICK_INTERVALS) {
            val tickX = tickInset + tickSpan * index / TeleautoUiTokens.DIAL_TICK_INTERVALS
            val ratio = if (index == TeleautoUiTokens.DIAL_TICK_INTERVALS / 2)
                TeleautoUiTokens.DIAL_CENTER_TICK_HEIGHT_RATIO else TeleautoUiTokens.DIAL_TICK_HEIGHT_RATIO
            val halfTick = trackHeight * ratio / 2f
            canvas.drawLine(tickX, track.centerY() - halfTick, tickX, track.centerY() + halfTick, tickPaint)
        }
        run {
            val knobHalf = 12.8f * density
            val x = markerX.coerceIn(knobHalf, width - knobHalf)
            knobDrawable.setBounds(
                (x - knobHalf).toInt(), top.toInt(),
                (x + knobHalf).toInt(), (top + trackHeight).toInt(),
            )
            knobDrawable.draw(canvas)
        }
    }

    var onPositionChanged: ((Float) -> Unit)? = null
    private var dragPointer = -1
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (centered || !isEnabled || event == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragPointer = event.getPointerId(0)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_CANCEL -> { dragPointer = -1; parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        val index = event.findPointerIndex(dragPointer)
        if (index >= 0 && (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_UP)) {
            position = (event.getX(index) / width.coerceAtLeast(1) * 2f - 1f).coerceIn(-1f, 1f)
            onPositionChanged?.invoke(position)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || (event.actionMasked == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.actionIndex) == dragPointer)) {
            dragPointer = -1; parent?.requestDisallowInterceptTouchEvent(false); performClick()
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

/** Read-only spring-centered hardware dial. */
class TeleautoCenteringDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TeleautoDialView(context, attrs) {
    init { centered = true; showValue = false }
}

/** Latched hardware/touch dial normalized to -1..1. */
class TeleautoContinuousDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TeleautoDialView(context, attrs) {
    init { centered = false; showValue = false; isClickable = true; contentDescription = "连续拨轮，可左右拖动" }
}
