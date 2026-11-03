package tech.alius.rc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent
import tech.alius.rc.model.TeleautoKeyAction
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Deliberate thumb-to-end gesture; a tap or interrupted gesture never changes state. */
class TeleautoSlideLockView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var locked = false
        private set
    var onLockChanged: ((Boolean) -> Unit)? = null
    private val density = resources.displayMetrics.density * TeleautoResponsiveMetrics.scale(context)
    private val trough = TeleautoLockTrackSurface(density)
    private val thumb = context.getDrawable(R.drawable.djirc_v2_lock_thumb_raised)!!.mutate()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.TEXT_SECONDARY
        textSize = TeleautoUiTokens.TYPE_TITLE * density
        typeface = TeleautoUiTypography.numericStrong
        textAlign = Paint.Align.CENTER
    }
    private val lockedIcon = resources.getDrawable(R.drawable.djirc_ui_ic_lock, context.theme).mutate()
    private val unlockedIcon = resources.getDrawable(R.drawable.djirc_ui_ic_unlock, context.theme).mutate()
    private val bounds = RectF()
    private var pointer = -1
    private var progress = 0f
    private var grabOffset = 0f
    private val unlockHold = TeleautoScreenUnlockHold()
    private val unlockKeys = mutableSetOf<TeleautoPhysicalKey>()
    private var unlockProgress = 0f
    private val statePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = object : Runnable {
        override fun run() {
            if (!locked) return
            unlockProgress = unlockHold.progress(SystemClock.uptimeMillis())
            invalidate()
            if (unlockProgress >= 1f) setLocked(false)
            else if (unlockHold.active) postOnAnimation(this)
        }
    }
    fun onLockedKeyEvent(event: TeleautoRcKeyEvent) {
        if (!locked || !hasWindowFocus() || (event.key != TeleautoPhysicalKey.C1 && event.key != TeleautoPhysicalKey.C2)) return
        when (event.action) {
            TeleautoKeyAction.DOWN, TeleautoKeyAction.TOGGLED_ON -> unlockKeys.add(event.key)
            TeleautoKeyAction.UP, TeleautoKeyAction.TOGGLED_OFF -> unlockKeys.remove(event.key)
            else -> return
        }
        updateUnlock()
    }
    fun onLockedHeldKeys(keys: Set<TeleautoPhysicalKey>) {
        if (!locked || !hasWindowFocus()) return
        unlockKeys.clear(); unlockKeys.addAll(keys.filter { it == TeleautoPhysicalKey.C1 || it == TeleautoPhysicalKey.C2 })
        updateUnlock()
    }
    private fun updateUnlock() {
        unlockHold.update(TeleautoPhysicalKey.C1 in unlockKeys, TeleautoPhysicalKey.C2 in unlockKeys, SystemClock.uptimeMillis())
        removeCallbacks(tick); tick.run()
    }
    fun cancelUnlock() {
        removeCallbacks(tick); unlockKeys.clear(); unlockHold.reset(); unlockProgress = 0f; invalidate()
    }
    override fun onDetachedFromWindow() { cancelUnlock(); super.onDetachedFromWindow() }
    private val thumbWidth get() = height.toFloat()
    init {
        // The lock is a safety control, so its complete hit area must remain
        // visually solid instead of revealing the panel below at the rounded
        // track corners.
        setBackgroundColor(TeleautoUiTokens.SCREEN_LOCK_SURFACE)
        isClickable = true
        contentDescription = "向右滑动锁定屏幕"
    }
    fun submitSdkLockState(value: Boolean, progress: Float) {
        if (locked != value) setLocked(value)
        unlockProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }
    fun setLocked(value: Boolean) {
        cancelUnlock()
        locked = value
        progress = if (value) 1f else 0f
        contentDescription = if (value) "屏幕已锁定，同时长按物理 C1 和 C2 两秒解锁" else "向右滑动锁定屏幕"
        invalidate()
        onLockChanged?.invoke(value)
    }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(resolveSize((TeleautoUiTokens.FIGMA_SIDE_COLUMN_WIDTH * density).toInt(), w), resolveSize((TeleautoUiTokens.CONTROL_HEIGHT * density).toInt(), h))
    override fun onDraw(canvas: Canvas) {
        trough.setBounds(0, 0, width, height)
        val radius = TeleautoUiTokens.RADIUS_SM * density
        statePaint.color = TeleautoUiTokens.SCREEN_LOCK_SURFACE
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, statePaint)
        trough.draw(canvas)
        if (locked) {
            bounds.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.save()
            trough.clipInterior(canvas)
            statePaint.color = TeleautoUiTokens.SCREEN_LOCK_SURFACE
            canvas.drawRect(bounds, statePaint)
            statePaint.color = TeleautoUiTokens.SCREEN_LOCK_TINT
            canvas.drawRect(thumbWidth, 0f, thumbWidth + (width - thumbWidth) * unlockProgress, height.toFloat(), statePaint)
            canvas.restore()
            drawThumb(canvas, 0f, radius, lockedIcon)
            paint.color = TeleautoUiTokens.TEXT
            paint.textSize = TeleautoUiTokens.TYPE_BODY * density
            canvas.drawText(if (unlockProgress > 0f) "正在解锁  ${(unlockProgress * 100).toInt()}%" else "已锁定 · 长按 C1 + C2", (width + thumbWidth) / 2f, height / 2f - (paint.ascent() + paint.descent()) / 2, paint)
            return
        }
        paint.textSize = TeleautoUiTokens.TYPE_TITLE * density
        paint.color = TeleautoUiTokens.TEXT_SECONDARY
        val x = progress * (width - thumbWidth).coerceAtLeast(0f)
        drawThumb(canvas, x, radius, unlockedIcon)
        canvas.drawText("滑动锁定  ›", (width + thumbWidth) / 2f,
            height / 2f - (paint.ascent() + paint.descent()) / 2, paint)
    }
    private fun drawThumb(canvas: Canvas, x: Float, radius: Float, icon: android.graphics.drawable.Drawable) {
        bounds.set(x, 0f, x + thumbWidth, height.toFloat())
        canvas.save()
        val thumbPath = android.graphics.Path().apply { addRoundRect(bounds, radius, radius, android.graphics.Path.Direction.CW) }
        canvas.clipPath(thumbPath)
        thumb.setBounds(bounds.left.toInt(), 0, bounds.right.toInt(), height); thumb.draw(canvas)
        canvas.restore()
        val inset = (thumbWidth * .28f).toInt()
        icon.setBounds(x.toInt() + inset, inset, (x + thumbWidth).toInt() - inset, height - inset)
        icon.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (locked) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!bounds.contains(event.x, event.y)) return false
                pointer = event.getPointerId(0); grabOffset = event.x - bounds.left
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> { resetGesture(); return true }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(pointer)
                if (index < 0) return true
                progress = ((event.getX(index) - grabOffset) / (width - thumbWidth).coerceAtLeast(1f)).coerceIn(0f, 1f)
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    if (TeleautoSlideLockGesture.completed(locked, progress)) setLocked(!locked)
                    resetGesture()
                }
                invalidate()
            }
        }
        return true
    }
    private fun resetGesture() { pointer = -1; progress = if (locked) 1f else 0f; parent.requestDisallowInterceptTouchEvent(false); invalidate() }
}

internal object TeleautoSlideLockGesture {
    fun completed(locked: Boolean, progress: Float): Boolean = !locked && progress >= .95f
}

/** Monotonic simultaneous hold; repeated DOWN/snapshot events never restart it. */
internal class TeleautoScreenUnlockHold(private val durationMs: Long = TeleautoUiTokens.SCREEN_UNLOCK_HOLD_MS) {
    private var startedAt: Long? = null
    val active get() = startedAt != null
    fun update(c1: Boolean, c2: Boolean, nowMs: Long) {
        if (!c1 || !c2) reset() else if (startedAt == null) startedAt = nowMs
    }
    fun progress(nowMs: Long): Float = startedAt?.let { ((nowMs - it).toFloat() / durationMs).coerceIn(0f, 1f) } ?: 0f
    fun reset() { startedAt = null }
}
