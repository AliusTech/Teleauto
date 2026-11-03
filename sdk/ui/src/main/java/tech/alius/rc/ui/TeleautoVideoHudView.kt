package tech.alius.rc.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import tech.alius.rc.ui.TeleautoUiTokens
import tech.alius.rc.ui.TeleautoUiTypography
import tech.alius.rc.model.TeleautoKeyAction
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoRcKeyEvent

/**
 * 图传屏：始终跟随当前设备屏幕宽高比，窗口与全屏之间不会拉伸。
 * 高光层、左上等宽元信息、右上全屏钮；
 * 底部悬浮五键操作条(地图/回放/拍照/录像/设置),录像键可切换按压态。
 */
class TeleautoVideoHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** The HUD reports intent; the host owns media persistence and state. */
    interface MediaActionHandler {
        fun onFocusRequested()
        fun onPhotoSaveRequested()
        fun onRecordingSaveRequested(recording: Boolean)
    }

    enum class Action { MAP, REPLAY, PHOTO, RECORD, SETTINGS, FULLSCREEN, STREAM_TOGGLE }

    var onAction: ((Action) -> Unit)? = null
    var onUiAction: ((TeleautoUiAction) -> Unit)? = null
    var mediaActionHandler: MediaActionHandler? = null

    /** 图传视频态:底改半透明(露出下层 TextureView 画面),元信息显示实时统计。 */
    @Volatile var videoActive: Boolean = false
        set(value) { field = value; invalidate() }
    @Volatile var videoStatsText: String = ""
        set(value) { field = value; invalidate() }
    @Volatile var statusMessage: String = ""
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    @Volatile var signalUnavailable: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** 录像键按压态(与 RECORD 键联动)。 */
    var recordPressed = false
        set(value) {
            if (value == field) return
            field = value
            invalidate()
        }

    var expandedMode = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var streamEnabled = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density * TeleautoResponsiveMetrics.scale(context)
    private val scaledDensity = resources.displayMetrics.scaledDensity * TeleautoResponsiveMetrics.scale(context)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.SCREEN_BG }
    private val frameMaterial = TeleautoMaterialAssets.centralFrame(context)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = TeleautoUiTokens.VIDEO_FRAME_STROKE * density
        color = TeleautoUiTokens.VIDEO_FRAME_EDGE
    }
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.TEXT
        textSize = 7f * scaledDensity
        typeface = TeleautoUiTypography.numeric
        letterSpacing = 0.04f
    }
    private val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.TEXT
        textSize = TeleautoUiTokens.TYPE_LABEL * scaledDensity
        typeface = TeleautoUiTypography.label
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.04f
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.ACTION_BAR_BG }
    private val barBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = TeleautoUiTokens.GLASS_BORDER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = TeleautoUiTokens.BRAND_700
    }
    private val fullBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.ACTION_BAR_BG }
    private val recordingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE34A4A.toInt() }
    private val path = Path()
    private val rect = RectF()

    private var barRect = RectF()
    private val buttonRects = ArrayList<RectF>()
    private val screenRect = RectF()
    private var pressedAction: Action? = null
    private var focusFeedbackVisible = false
    private var focusFeedbackScale = FOCUS_START_SCALE
    private var focusFeedbackAlpha = 0f
    private var focusEnterAnimator: ValueAnimator? = null
    private var focusExitAnimator: ValueAnimator? = null
    private val focusEaseOut = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = TeleautoUiTokens.BRAND_700
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // 取景框固定 16:9(OpenIPC 相机标准输出形态;不随屏幕比例漂移,RC Plus 16:10 屏上同样 16:9)
        val targetHeight = (width * 9f / 16f).toInt()
        val maxHeight = MeasureSpec.getSize(heightMeasureSpec)
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> maxHeight
            MeasureSpec.AT_MOST -> minOf(targetHeight, maxHeight)
            else -> targetHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 悬浮五键条:宽 min(92%, 252dp),高 31dp,底部居中悬浮
        val barW = minOf(w * 0.92f, 252 * density)
        val barH = 31 * density
        barRect = RectF((w - barW) / 2f, h - barH - 7 * density, (w + barW) / 2f, h - 7 * density)
        buttonRects.clear()
        val bw = barW / 5f
        for (i in 0 until 5) {
            buttonRects.add(
                RectF(barRect.left + bw * i, barRect.top, barRect.left + bw * (i + 1), barRect.bottom)
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // 圆角:与区域背景同心等距(区域 16dp − 内边距 8dp = 8dp)
        val r = if (expandedMode) 0f else TeleautoUiTokens.FIGMA_VIDEO_RADIUS * density
        val inset = borderPaint.strokeWidth / 2f
        screenRect.set(inset, inset, w - inset, h - inset)

        bgPaint.color = when {
            signalUnavailable -> Color.BLACK
            !streamEnabled -> TeleautoUiTokens.SCREEN_BG
            videoActive -> 0x66000000.toInt()
            else -> TeleautoUiTokens.SCREEN_BG
        }
        canvas.drawRoundRect(screenRect, r, r, bgPaint)
        bgPaint.color = TeleautoUiTokens.SCREEN_BG

        // Both header elements share an inset and vertical center inside the frame.
        val headerInset = TeleautoUiTokens.VIDEO_HUD_INSET * density
        val fbR = TeleautoUiTokens.VIDEO_HUD_BUTTON_SIZE * density / 2f
        val fx = w - headerInset - fbR
        val fy = headerInset + fbR
        val headerBaseline = fy - (metaPaint.ascent() + metaPaint.descent()) / 2f
        val metadata = if (videoActive && videoStatsText.isNotEmpty()) {
            "LIVE · $videoStatsText"
        } else {
            "VIDEO · STANDBY"
        }
        val metadataWidth = (fx - fbR - headerInset * 2f).coerceAtLeast(0f)
        val headerText = android.text.TextUtils.ellipsize(
            metadata, metaPaint, metadataWidth, android.text.TextUtils.TruncateAt.END,
        )
        canvas.drawText(headerText.toString(), headerInset, headerBaseline, metaPaint)
        if (recordPressed) {
            val recordY = headerBaseline + TeleautoUiTokens.SPACE_4 * density
            canvas.drawCircle(headerInset + 2 * density, recordY - 2 * density, 2.6f * density, recordingPaint)
            canvas.drawText("REC", headerInset + TeleautoUiTokens.SPACE_2 * density, recordY, metaPaint)
        }
        if (focusFeedbackVisible && videoActive && streamEnabled) {
            drawFocusFeedback(canvas, w / 2f, h / 2f)
        }

        if (!videoActive && streamEnabled && statusMessage.isNotEmpty()) {
            canvas.drawText(statusMessage, w / 2f, h / 2f + 30 * density, statusPaint)
        }

        // 右上全屏钮
        rect.set(fx - fbR, fy - fbR, fx + fbR, fy + fbR)
        canvas.drawRoundRect(rect, 5 * density, 5 * density, fullBtnPaint)
        path.reset()
        val g = 3.4f * density
        val s = fbR - 4 * density
        if (expandedMode) {
            // 缩小：四个直角从外侧指向中心。
            addFullscreenCorner(fx, fy, -1f, -1f, g, s - g, inward = true)
            addFullscreenCorner(fx, fy, 1f, -1f, g, s - g, inward = true)
            addFullscreenCorner(fx, fy, -1f, 1f, g, s - g, inward = true)
            addFullscreenCorner(fx, fy, 1f, 1f, g, s - g, inward = true)
        } else {
            // 放大：四个直角位于外角，开口朝向画面中心。
            addFullscreenCorner(fx, fy, -1f, -1f, s, s - g, inward = false)
            addFullscreenCorner(fx, fy, 1f, -1f, s, s - g, inward = false)
            addFullscreenCorner(fx, fy, -1f, 1f, s, s - g, inward = false)
            addFullscreenCorner(fx, fy, 1f, 1f, s, s - g, inward = false)
        }
        canvas.drawPath(path, iconPaint)

        // Paused state is the only persistent center affordance. During live
        // video the center stays unobstructed; tapping it still pauses.
        if (!streamEnabled) {
            val centerX = w / 2f
            val centerY = h / 2f
            val buttonRadius = 20f * density
            rect.set(
                centerX - buttonRadius,
                centerY - buttonRadius,
                centerX + buttonRadius,
                centerY + buttonRadius,
            )
            canvas.drawRoundRect(rect, buttonRadius, buttonRadius, fullBtnPaint)
            path.reset()
            val playSize = 7f * density
            path.moveTo(centerX - playSize * 0.55f, centerY - playSize)
            path.lineTo(centerX + playSize, centerY)
            path.lineTo(centerX - playSize * 0.55f, centerY + playSize)
            path.close()
            canvas.drawPath(path, iconPaint)
            val pausedText = "已暂停"
            canvas.drawText(
                pausedText,
                centerX - metaPaint.measureText(pausedText) / 2f,
                centerY + buttonRadius + 13f * density,
                metaPaint,
            )
        }
        // Compact video and device covers share a shallow, transparent frame.
        if (!expandedMode) {
            frameMaterial.setBounds(0, 0, width, height)
            frameMaterial.draw(canvas)
        }

        // 操作条只属于放大后的沉浸视图。紧凑取景框保持纯净，且下方
        // 不残留不可见的操作热区。
        if (expandedMode) {
            val barR = 8.4f * density
            canvas.drawRoundRect(barRect, barR, barR, barPaint)
            canvas.drawRoundRect(barRect, barR, barR, barBorderPaint)
            for (i in 0 until 5) {
                drawActionIcon(canvas, Action.entries[i], buttonRects[i])
            }
        }
    }

    private fun addFullscreenCorner(
        cx: Float,
        cy: Float,
        directionX: Float,
        directionY: Float,
        distance: Float,
        arm: Float,
        inward: Boolean,
    ) {
        val cornerX = cx + directionX * distance
        val cornerY = cy + directionY * distance
        val armDirection = if (inward) directionX else -directionX
        val verticalDirection = if (inward) directionY else -directionY
        path.moveTo(cornerX, cornerY)
        path.lineTo(cornerX + armDirection * arm, cornerY)
        path.moveTo(cornerX, cornerY)
        path.lineTo(cornerX, cornerY + verticalDirection * arm)
    }

    private fun drawActionIcon(canvas: Canvas, action: Action, b: RectF) {
        val cx = b.centerX()
        val cy = b.centerY()
        val s = 5f * density
        path.reset()
        when (action) {
            Action.MAP -> { // 折叠地图:矩形 + 折线
                path.moveTo(cx - s, cy - s * 0.7f)
                path.lineTo(cx - s, cy + s * 0.7f)
                path.lineTo(cx, cy + s * 0.3f)
                path.lineTo(cx + s, cy + s * 0.7f)
                path.lineTo(cx + s, cy - s * 0.7f)
                path.lineTo(cx, cy - s * 0.3f)
                path.close()
            }
            Action.REPLAY -> { // 回放:环形箭头
                rect.set(cx - s, cy - s, cx + s, cy + s)
                canvas.drawArc(rect, 300f, 280f, false, iconPaint)
                path.moveTo(cx + s * 0.94f, cy - s * 0.35f)
                path.lineTo(cx + s * 0.94f, cy + s * 0.2f)
                path.lineTo(cx + s * 0.4f, cy - s * 0.05f)
            }
            Action.PHOTO -> { // 拍照:相机
                rect.set(cx - s, cy - s * 0.65f, cx + s, cy + s * 0.65f)
                path.moveTo(cx - s * 0.4f, cy - s * 0.65f)
                path.lineTo(cx - s * 0.25f, cy - s)
                path.lineTo(cx + s * 0.25f, cy - s)
                path.lineTo(cx + s * 0.4f, cy - s * 0.65f)
                path.close()
                canvas.drawPath(path, iconPaint)
                canvas.drawRoundRect(rect, 1.5f * density, 1.5f * density, iconPaint)
                canvas.drawCircle(cx, cy, s * 0.4f, iconPaint)
                return
            }
            Action.RECORD -> { // 录像:双圆环
                val paint = if (recordPressed) recordingPaint else iconPaint
                if (!recordPressed) canvas.drawCircle(cx, cy, s * 0.85f, iconPaint)
                canvas.drawCircle(cx, cy, if (recordPressed) s * 0.62f else s * 0.38f, paint)
                return
            }
            Action.SETTINGS -> { // 设置:齿轮
                canvas.drawCircle(cx, cy, s * 0.5f, iconPaint)
                for (a in 0 until 8) {
                    val rad = Math.toRadians(a * 45.0)
                    val x1 = cx + Math.cos(rad).toFloat() * s * 0.75f
                    val y1 = cy + Math.sin(rad).toFloat() * s * 0.75f
                    val x2 = cx + Math.cos(rad).toFloat() * s
                    val y2 = cy + Math.sin(rad).toFloat() * s
                    canvas.drawLine(x1, y1, x2, y2, iconPaint)
                }
                return
            }
            Action.FULLSCREEN, Action.STREAM_TOGGLE -> Unit
        }
        canvas.drawPath(path, iconPaint)
    }

    /**
     * Camera-style focus confirmation: the reticle contracts into the sampled
     * area, holds briefly, then fades. Repeated presses replace the current
     * feedback immediately so focus input is never queued behind animation.
     */
    fun showFocusFeedback() {
        focusEnterAnimator?.cancel()
        focusExitAnimator?.cancel()
        focusFeedbackVisible = true
        focusFeedbackScale = FOCUS_START_SCALE
        focusFeedbackAlpha = 0f
        invalidate()

        focusEnterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FOCUS_ENTER_MS
            interpolator = focusEaseOut
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                focusFeedbackScale = FOCUS_START_SCALE + (1f - FOCUS_START_SCALE) * progress
                focusFeedbackAlpha = progress
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animation !== focusEnterAnimator) return
                    startFocusExit()
                }
            })
            start()
        }
    }

    fun submit(state: TeleautoVideoUiState) {
        signalUnavailable = !state.hasSource
        streamEnabled = state.streaming
        recordPressed = state.recording
        expandedMode = state.expanded
        statusMessage = state.status
        videoStatsText = state.diagnostics
    }

    private fun startFocusExit() {
        focusExitAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            startDelay = FOCUS_HOLD_MS
            duration = FOCUS_EXIT_MS
            interpolator = focusEaseOut
            addUpdateListener { animator ->
                focusFeedbackAlpha = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animation !== focusExitAnimator) return
                    focusFeedbackVisible = false
                    focusFeedbackAlpha = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    /** Routes the controller's camera controls through the video component. */
    fun onPhysicalMediaKey(event: TeleautoRcKeyEvent): Boolean {
        when (event.key) {
            TeleautoPhysicalKey.FOCUS -> if (event.action == TeleautoKeyAction.DOWN || event.action == TeleautoKeyAction.TRIGGER) {
                mediaActionHandler?.onFocusRequested()
            }
            TeleautoPhysicalKey.SHUTTER -> if (event.action == TeleautoKeyAction.DOWN || event.action == TeleautoKeyAction.TRIGGER) {
                mediaActionHandler?.onPhotoSaveRequested()
            }
            TeleautoPhysicalKey.RECORD -> when (event.action) {
                TeleautoKeyAction.TOGGLED_ON -> mediaActionHandler?.onRecordingSaveRequested(true)
                TeleautoKeyAction.TOGGLED_OFF -> mediaActionHandler?.onRecordingSaveRequested(false)
                else -> Unit
            }
            else -> return false
        }
        return true
    }

    private fun drawFocusFeedback(canvas: Canvas, cx: Float, cy: Float) {
        val half = 19f * density * focusFeedbackScale
        val arm = 7f * density
        path.reset()
        path.moveTo(cx - half, cy - half + arm)
        path.lineTo(cx - half, cy - half)
        path.lineTo(cx - half + arm, cy - half)
        path.moveTo(cx + half - arm, cy - half)
        path.lineTo(cx + half, cy - half)
        path.lineTo(cx + half, cy - half + arm)
        path.moveTo(cx + half, cy + half - arm)
        path.lineTo(cx + half, cy + half)
        path.lineTo(cx + half - arm, cy + half)
        path.moveTo(cx - half + arm, cy + half)
        path.lineTo(cx - half, cy + half)
        path.lineTo(cx - half, cy + half - arm)
        focusPaint.alpha = (focusFeedbackAlpha * 255f).toInt().coerceIn(0, 255)
        canvas.drawPath(path, focusPaint)

        // A small center pulse makes the moment of focus lock unambiguous
        // without leaving a persistent reticle over the video.
        val lockProgress = ((1f - focusFeedbackScale) / (FOCUS_START_SCALE - 1f)).coerceIn(0f, 1f)
        focusPaint.alpha = (focusFeedbackAlpha * lockProgress * 210f).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, 1.7f * density, focusPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedAction = actionAt(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedAction = null
                return true
            }
            MotionEvent.ACTION_UP -> {
                val action = pressedAction
                pressedAction = null
                if (action == null) return true

                // Keep the action selected on ACTION_DOWN. In particular, a
                // finger that starts on the expand affordance and drifts a few
                // pixels must never fall through to the stream pause action.
                performClick()
                when (action) {
                    Action.PHOTO -> mediaActionHandler?.onPhotoSaveRequested()
                        ?: emitAction(action)
                    Action.RECORD -> mediaActionHandler?.onRecordingSaveRequested(!recordPressed)
                        ?: emitAction(action)
                    else -> emitAction(action)
                }
                return true
            }
            else -> return true
        }
    }

    private fun emitAction(action: Action) {
        onAction?.invoke(action)
        val semantic = when (action) {
            Action.SETTINGS -> TeleautoUiAction.OpenSettings
            Action.FULLSCREEN -> TeleautoUiAction.ToggleVideoExpansion
            Action.STREAM_TOGGLE -> TeleautoUiAction.ToggleVideoStream
            Action.PHOTO -> TeleautoUiAction.CapturePhoto
            Action.RECORD -> TeleautoUiAction.ToggleRecording
            else -> return
        }
        onUiAction?.invoke(semantic)
    }

    private fun actionAt(x: Float, y: Float): Action {
        // The visible button stays compact, while its independent touch target
        // is 52dp and follows the same inset as the rendered button.
        val fbR = TeleautoUiTokens.VIDEO_HUD_BUTTON_SIZE * density / 2f
        val inset = TeleautoUiTokens.VIDEO_HUD_INSET * density
        val fx = width.toFloat() - inset - fbR
        val fy = inset + fbR
        val hitRadius = TeleautoUiTokens.VIDEO_HUD_TOUCH_SIZE * density / 2f
        if (x >= fx - hitRadius && x <= fx + hitRadius &&
            y >= fy - hitRadius && y <= fy + hitRadius) {
            return Action.FULLSCREEN
        }
        if (expandedMode) {
            buttonRects.forEachIndexed { i, b ->
                if (b.contains(x, y)) {
                    return Action.entries[i]
                }
            }
        }
        return Action.STREAM_TOGGLE
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        private const val FOCUS_START_SCALE = 1.28f
        private const val FOCUS_ENTER_MS = 180L
        private const val FOCUS_HOLD_MS = 180L
        private const val FOCUS_EXIT_MS = 140L
    }
}
