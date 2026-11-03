package tech.alius.rc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import tech.alius.rc.model.*

data class TeleautoHardwareControlSection(val label: String, val keys: List<TeleautoPhysicalKey>)

/** Shared hardware-shaped keys. Physical feedback and touch ownership are independent. */
class TeleautoHardwareControlsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val density = resources.displayMetrics.density * TeleautoResponsiveMetrics.scale(context)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TeleautoUiTokens.FIGMA_KEY_TEXT_SIZE * density
        textAlign = Paint.Align.CENTER
        typeface = TeleautoUiTypography.bold(context)
    }
    private val surface = TeleautoHardwareKeySurface(context, density)
    private val illuminated = mutableSetOf<TeleautoPhysicalKey>()
    private val afterglow = mutableMapOf<TeleautoPhysicalKey, Float>()
    private val fades = mutableMapOf<TeleautoPhysicalKey, ValueAnimator>()
    private val releaseCurve = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    private val hintPaint = Paint(labelPaint).apply { textSize = TeleautoUiTokens.TYPE_META_SMALL * density }
    private val held = mutableSetOf<TeleautoPhysicalKey>()
    private val pulses = mutableSetOf<TeleautoPhysicalKey>()
    private val pulseTasks = mutableMapOf<TeleautoPhysicalKey, Runnable>()
    private val touches = mutableMapOf<Int, TeleautoPhysicalKey>()
    private val rects = linkedMapOf<TeleautoPhysicalKey, RectF>()
    private var slots = emptyList<TeleautoHardwareControlSlot>()
    private var holdKeys = TeleautoRcTransmissionChannels.holdButtons.toSet()
    private val homeIcon by lazy { context.getDrawable(R.drawable.ic_rc_return_home)!!.mutate() }
    private val authIcon by lazy { context.getDrawable(R.drawable.ic_rc_auth)!!.mutate() }
    private val pauseIcon by lazy { context.getDrawable(R.drawable.ic_rc_pause)!!.mutate() }
    var onKeyClicked: ((TeleautoPhysicalKey) -> Unit)? = null
    var onKeyAction: ((TeleautoPhysicalKey, TeleautoKeyAction) -> Unit)? = null

    init { isClickable = true; contentDescription = "遥控按钮；带短线标记的按钮支持按住" }

    fun configureHoldKeys(keys: Set<TeleautoPhysicalKey>) { releaseTouches(); holdKeys = keys; invalidate() }
    fun configureSlots(value: List<TeleautoHardwareControlSlot>) {
        val next = value.distinctBy { it.key }
        if (slots == next) return
        releaseTouches()
        slots = next
        held.retainAll(next.map { it.key }.toSet())
        requestLayout(); invalidate()
    }
    fun configureGroups(value: List<TeleautoHardwareControlSection>) = configureSlots(value.flatMap { it.keys }.distinct().mapNotNull { key ->
        TeleautoHardwareControlCatalog.all.firstOrNull { key in it.inputs }?.placement?.let { TeleautoHardwareControlSlot(key, it.slot) }
    })
    fun configure(panelTitle: String, panelKeys: List<TeleautoPhysicalKey>, modeSwitch: Any? = null) = configureGroups(listOf(TeleautoHardwareControlSection(panelTitle, panelKeys)))
    fun submit(state: TeleautoHardwareControlsUiState) { configureGroups(state.sections); setHeldKeys(state.heldKeys) }
    fun setHeldKeys(value: Set<TeleautoPhysicalKey>) { held.clear(); held.addAll(value); refreshFeedback() }
    fun setKeyEvent(event: TeleautoRcKeyEvent) {
        when (event.action) {
            TeleautoKeyAction.DOWN, TeleautoKeyAction.TOGGLED_ON -> held.add(event.key)
            TeleautoKeyAction.UP, TeleautoKeyAction.TOGGLED_OFF -> held.remove(event.key)
            TeleautoKeyAction.TRIGGER -> pulse(event.key)
        }
        refreshFeedback()
    }
    private fun refreshFeedback() {
        val next = held + pulses + touches.values
        next.forEach { key -> fades.remove(key)?.cancel(); afterglow.remove(key) }
        (illuminated - next).forEach { key ->
            fades.remove(key)?.cancel()
            afterglow[key] = 1f
            fades[key] = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = TeleautoUiTokens.MOTION_STATE
                interpolator = releaseCurve
                addUpdateListener { afterglow[key] = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        illuminated.clear(); illuminated.addAll(next)
        invalidate()
    }
    private fun pulse(key: TeleautoPhysicalKey) {
        pulseTasks.remove(key)?.let(::removeCallbacks)
        pulses.add(key)
        val task = Runnable { pulses.remove(key); pulseTasks.remove(key); refreshFeedback() }
        pulseTasks[key] = task
        postDelayed(task, TeleautoUiTokens.MOTION_STATE)
    }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(resolveSize(suggestedMinimumWidth, w), resolveSize((TeleautoUiTokens.HARDWARE_PANEL_HEIGHT * density).toInt(), h))
    private fun layoutKeys() {
        rects.clear()
        val scale = minOf(density, height / TeleautoUiTokens.HARDWARE_PANEL_HEIGHT)
        val cellW = minOf(TeleautoUiTokens.FIGMA_KEY_WIDTH * density, (width - TeleautoUiTokens.SPACE_4 * density) / 2).coerceAtLeast(1f)
        val cellH = TeleautoUiTokens.FIGMA_KEY_HEIGHT * scale
        val step = (TeleautoUiTokens.FIGMA_KEY_HEIGHT + TeleautoUiTokens.COMPACT_KEY_ROW_GAP) * scale
        val roundSize = TeleautoUiTokens.ROUND_KEY_DIAMETER * scale
        val columns = TeleautoCompactControlColumns.calculate(width.toFloat(), cellW)
        val firstColumn = columns.first
        val secondColumn = columns.second
        slots.forEach { item ->
            val key = item.key
            val rect = when (key) {
                TeleautoPhysicalKey.GOHOME, TeleautoPhysicalKey.PAUSE, TeleautoPhysicalKey.AUTH_LED -> {
                    val index = item.slot.ordinal - TeleautoControlSlot.LEFT_1.ordinal
                    val rowTop = if (index in 0..5) index % 3 * step else if (item.slot == TeleautoControlSlot.ROUND_2) step else 0f
                    val top = (rowTop + (cellH - roundSize) / 2f).coerceAtLeast(0f)
                    val columnLeft = if (index in 0..5) {
                        if (index / 3 == 0) firstColumn else secondColumn
                    } else if (key == TeleautoPhysicalKey.PAUSE) secondColumn else firstColumn
                    val left = columnLeft + (cellW-roundSize)/2
                    RectF(left, top, left+roundSize, top+roundSize)
                }
                else -> {
                    val index = item.slot.ordinal - TeleautoControlSlot.LEFT_1.ordinal
                    if (index !in 0..5) return@forEach
                    val left = if (index / 3 == 0) firstColumn else secondColumn
                    val top = index % 3 * step
                    RectF(left, top, left+cellW, top+cellH)
                }
            }
            rects[key] = rect
        }
    }
    override fun onDraw(canvas: Canvas) {
        layoutKeys()
        rects.forEach { (key, rect) ->
            val active = key in held || key in touches.values
            val light = if (key in illuminated) 1f else afterglow[key] ?: 0f
            val isRound = key == TeleautoPhysicalKey.GOHOME || key == TeleautoPhysicalKey.PAUSE || key == TeleautoPhysicalKey.AUTH_LED
            val tint = when (key) {
                TeleautoPhysicalKey.AUTH_LED -> TeleautoUiTokens.AUTH_KEY_COLOR
                TeleautoPhysicalKey.PAUSE -> TeleautoUiTokens.PAUSE_KEY_COLOR
                else -> null
            }
            val face = surface.draw(canvas, rect, isRound, active, light, tint)
            labelPaint.color = if (light > 0f) TeleautoUiTokens.TEXT else TeleautoUiTokens.TEXT_SECONDARY
            val icon = when (key) { TeleautoPhysicalKey.AUTH_LED -> authIcon; TeleautoPhysicalKey.GOHOME -> homeIcon; TeleautoPhysicalKey.PAUSE -> pauseIcon; else -> null }
            if (icon != null) {
                val half = minOf(rect.width(), rect.height()) / 4
                icon.setTint(labelPaint.color)
                icon.setBounds((face.centerX()-half).toInt(), (face.centerY()-half).toInt(), (face.centerX()+half).toInt(), (face.centerY()+half).toInt())
                icon.draw(canvas)
            } else {
                val label = if (key == TeleautoPhysicalKey.AUTH_LED) "AUTH" else key.name
                labelPaint.textSize = TeleautoUiTokens.FIGMA_KEY_TEXT_SIZE * density
                val available = face.width() - TeleautoUiTokens.SPACE_2 * density
                if (labelPaint.measureText(label) > available) labelPaint.textSize *= available / labelPaint.measureText(label)
                canvas.drawText(label, face.centerX(), face.centerY()-(labelPaint.ascent()+labelPaint.descent())/2, labelPaint)
            }
            if (key in holdKeys && key != TeleautoPhysicalKey.PAUSE) {
                hintPaint.color = if (active) TeleautoUiTokens.TEXT else TeleautoUiTokens.TEXT_FAINT
                hintPaint.strokeWidth = TeleautoUiTokens.HARDWARE_KEY_EDGE * density
                val y = face.bottom-TeleautoUiTokens.SPACE_1*density
                canvas.drawLine(rect.centerX()-TeleautoUiTokens.SPACE_1*density, y, rect.centerX()+TeleautoUiTokens.SPACE_1*density, y, hintPaint)
            }
        }
    }
    private fun releasePointer(id: Int, click: Boolean) {
        val key = touches.remove(id) ?: return
        if (key in holdKeys) {
            if (key !in touches.values) onKeyAction?.invoke(key, TeleautoKeyAction.UP)
            if (click && onKeyAction == null) onKeyClicked?.invoke(key)
        } else if (click) {
            pulse(key)
            if (onKeyAction != null) onKeyAction?.invoke(key, TeleautoKeyAction.TRIGGER) else onKeyClicked?.invoke(key)
        }
        refreshFeedback()
    }
    fun releaseTouches() { touches.keys.toList().forEach { releasePointer(it, false) }; parent?.requestDisallowInterceptTouchEvent(false) }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        layoutKeys()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val key = rects.entries.firstOrNull { it.value.contains(event.getX(i), event.getY(i)) }?.key
                if (key == null) return event.actionMasked != MotionEvent.ACTION_DOWN
                val wasHeld = key in touches.values
                touches[event.getPointerId(i)] = key
                parent?.requestDisallowInterceptTouchEvent(true)
                if (key in holdKeys && !wasHeld) onKeyAction?.invoke(key, TeleautoKeyAction.DOWN)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                val id = event.getPointerId(i)
                val key = touches[id] ?: continue
                if (rects[key]?.contains(event.getX(i), event.getY(i)) != true) releasePointer(id, false)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                val key = touches[event.getPointerId(i)]
                val clicked = key != null && rects[key]?.contains(event.getX(i), event.getY(i)) == true
                releasePointer(event.getPointerId(i), clicked)
                if (clicked) performClick()
                if (event.actionMasked == MotionEvent.ACTION_UP) releaseTouches()
            }
            MotionEvent.ACTION_CANCEL -> releaseTouches()
        }
        refreshFeedback(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { if (!hasWindowFocus) releaseTouches(); super.onWindowFocusChanged(hasWindowFocus) }
    override fun onDetachedFromWindow() {
        releaseTouches()
        pulseTasks.values.forEach(::removeCallbacks); pulseTasks.clear(); pulses.clear()
        fades.values.forEach { it.cancel() }; fades.clear(); afterglow.clear(); illuminated.clear()
        super.onDetachedFromWindow()
    }
}

/** Keep the existing key size, reduce only the empty space, and center the group. */
internal object TeleautoCompactControlColumns {
    fun calculate(width: Float, keyWidth: Float): Pair<Float, Float> {
        val oldGap = (width - 2 * keyWidth).coerceAtLeast(0f)
        val gap = oldGap * TeleautoUiTokens.COMPACT_KEY_COLUMN_GAP_RATIO
        val first = (width - 2 * keyWidth - gap) / 2
        return first to first + keyWidth + gap
    }
}
