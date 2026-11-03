package tech.alius.rc.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import tech.alius.rc.ui.TeleautoUiTokens
import kotlin.math.abs

/** Read-only joystick status display using the original grid-and-ring visual. */
class TeleautoJoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    init {
        isClickable = false
        isLongClickable = false
        isFocusable = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val side = if (availableHeight > 0) minOf(width, availableHeight) else width
        setMeasuredDimension(side, side)
    }

    var posX = 0f
        private set
    var posY = 0f
        private set
    var mirrored = false
        set(value) {
            if (field == value) return
            field = value
            rebuildStaticLayer()
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.ring(0x20)
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.JOYSTICK_GRID
    }
    private val capDrawable by lazy {
        resources.getDrawable(R.drawable.djirc_v2_stick_pointer, context.theme).mutate()
    }
    private val capRect = RectF()
    private val leftWell by lazy { context.getDrawable(R.drawable.djirc_v2_joystick_well_left)!!.mutate() }
    private val rightWell by lazy { context.getDrawable(R.drawable.djirc_v2_joystick_well_right)!!.mutate() }
    private val knobDrawable by lazy {
        resources.getDrawable(R.drawable.djirc_v2_dial_thumb, context.theme).mutate()
    }
    private var staticLayer: Bitmap? = null
    private var gridLeft = 0f
    private var gridRight = 0f
    private var gridTop = 0f
    private var gridBottom = 0f
    private var verticalTop = 0f
    private var verticalBottom = 0f
    private var verticalLeft = 0f
    private var verticalRight = 0f
    private var horizontalLeft = 0f
    private var horizontalRight = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildStaticLayer()
    }

    private fun rebuildStaticLayer() {
        if (width <= 0 || height <= 0) return
        staticLayer?.recycle()
        val unit = minOf(width, height) / DESIGN_SIDE
        linePaint.strokeWidth = GRID_LINE * unit
        centerPaint.strokeWidth = CENTER_LINE * unit
        val trimWidth = TRACK_THICKNESS * unit
        val trimInset = TRACK_EDGE_INSET * unit
        gridLeft = 54f * unit
        gridRight = width - 54f * unit
        gridTop = 52f * unit
        gridBottom = height - 56f * unit
        verticalTop = 53f * unit
        verticalBottom = height - 60f * unit
        verticalLeft = if (mirrored) width - trimInset - trimWidth else trimInset
        verticalRight = verticalLeft + trimWidth
        horizontalLeft = 59f * unit
        horizontalRight = width - 59f * unit

        staticLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            val craterDrawable = if (mirrored) rightWell else leftWell
            craterDrawable.setBounds(0, 0, width, height)
            craterDrawable.draw(canvas)
            for (index in 1 until 8) {
                val x = gridLeft + (gridRight - gridLeft) * index / 8f
                val y = gridTop + (gridBottom - gridTop) * index / 8f
                canvas.drawLine(x, gridTop, x, gridBottom, linePaint)
                canvas.drawLine(gridLeft, y, gridRight, y, linePaint)
            }
            canvas.drawLine((gridLeft + gridRight) / 2f, gridTop, (gridLeft + gridRight) / 2f, gridBottom, centerPaint)
            canvas.drawLine(gridLeft, (gridTop + gridBottom) / 2f, gridRight, (gridTop + gridBottom) / 2f, centerPaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        staticLayer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        val unit = minOf(width, height) / DESIGN_SIDE
        val trimWidth = TRACK_THICKNESS * unit
        val trimInset = TRACK_EDGE_INSET * unit
        val knobSize = KNOB_LENGTH * unit / 2f
        val verticalTravel = (verticalBottom - verticalTop) / 2f - knobSize
        val knobY = (verticalTop + verticalBottom) / 2f + posY * verticalTravel
        val knobLeft = if (mirrored) width - trimInset - trimWidth + KNOB_CROSS_INSET * unit else trimInset + KNOB_CROSS_INSET * unit
        val knobRight = if (mirrored) width - trimInset - KNOB_CROSS_INSET * unit else trimInset + trimWidth - KNOB_CROSS_INSET * unit
        drawRotatedDrawable(canvas, knobDrawable, knobLeft, knobY - knobSize, knobRight, knobY + knobSize)
        val horizontalTravel = (horizontalRight - horizontalLeft) / 2f - knobSize
        val knobX = width / 2f + posX * horizontalTravel
        knobDrawable.setBounds(
            (knobX - knobSize).toInt(), (height - trimInset - trimWidth).toInt(),
            (knobX + knobSize).toInt(), (height - trimInset).toInt(),
        )
        knobDrawable.draw(canvas)

        val headX = gridLeft + (gridRight - gridLeft) * (posX + 1f) / 2f
        val headY = gridTop + (gridBottom - gridTop) * (posY + 1f) / 2f
        val capWidth = CAP_WIDTH * unit
        val capHeight = CAP_HEIGHT * unit
        val circleCenterOffset = CAP_CENTER_OFFSET * unit
        capRect.set(
            headX - circleCenterOffset,
            headY - circleCenterOffset,
            headX - circleCenterOffset + capWidth,
            headY - circleCenterOffset + capHeight,
        )
        capDrawable.setBounds(capRect.left.toInt(), capRect.top.toInt(), capRect.right.toInt(), capRect.bottom.toInt())
        capDrawable.draw(canvas)
    }

    fun setStick(x: Float, y: Float) {
        val nextX = x.coerceIn(-1f, 1f)
        val nextY = y.coerceIn(-1f, 1f)
        if (abs(nextX - posX) < 0.002f && abs(nextY - posY) < 0.002f) return
        posX = nextX
        posY = nextY
        postInvalidateOnAnimation()
    }

    fun submit(state: TeleautoJoystickUiState) {
        setStick(state.x, state.y)
    }

    private fun drawRotatedDrawable(
        canvas: Canvas,
        drawable: android.graphics.drawable.Drawable,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) {
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val rotatedWidth = bottom - top
        val rotatedHeight = right - left
        val save = canvas.save()
        canvas.rotate(90f, centerX, centerY)
        drawable.setBounds(
            (centerX - rotatedWidth / 2f).toInt(), (centerY - rotatedHeight / 2f).toInt(),
            (centerX + rotatedWidth / 2f).toInt(), (centerY + rotatedHeight / 2f).toInt(),
        )
        drawable.draw(canvas)
        canvas.restoreToCount(save)
    }

    override fun onDetachedFromWindow() {
        staticLayer?.recycle()
        staticLayer = null
        super.onDetachedFromWindow()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (staticLayer == null) rebuildStaticLayer()
    }

    companion object {
        private const val DESIGN_SIDE = 354f
        private const val GRID_INSET = 58f
        private const val GRID_LINE = 1.75f
        private const val CENTER_LINE = 3f
        private const val TRACK_THICKNESS = 16f
        // The trim tracks sit inside the recessed black crater, not in its outer glow.
        private const val TRACK_EDGE_INSET = 26f
        private const val KNOB_LENGTH = 42f
        private const val KNOB_CROSS_INSET = 0f
        private const val CAP_WIDTH = 40f
        private const val CAP_HEIGHT = 40f
        private const val CAP_CENTER_OFFSET = 20f
    }
}
