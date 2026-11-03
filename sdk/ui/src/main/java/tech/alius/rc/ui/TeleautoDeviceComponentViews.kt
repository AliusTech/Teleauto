package tech.alius.rc.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import tech.alius.rc.device.*
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoStickSnapshot
import tech.alius.rc.model.TeleautoThreePositionSwitchMode
import tech.alius.rc.model.TeleautoFiveWayControl
import tech.alius.rc.model.TeleautoTransmitModuleInfo
import tech.alius.rc.model.TeleautoTransmitModuleState
import tech.alius.rc.video.TeleautoVideoTransmissionModuleInfo
import tech.alius.rc.video.TeleautoWfbAdapterState
import kotlin.math.min

internal fun Context.dp(value: Float) = value * resources.displayMetrics.density

open class TeleautoStatusComponentView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    protected val density = resources.displayMetrics.density
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val bounds = RectF()
    var title: String = "状态"; set(value) { field = value; invalidate() }
    var valueText: String = "—"; set(value) { field = value; invalidate() }
    var titleColor: Int = TeleautoUiTokens.TEXT_SECONDARY; set(value) { field = value; invalidate() }
    var accentColor: Int = TeleautoUiTokens.ACCENT; set(value) { field = value; invalidate() }
    init {
        minimumWidth = context.dp(112f).toInt()
        minimumHeight = context.dp(TeleautoUiTokens.MODULE_STATUS_HEIGHT).toInt()
        isClickable = false
        background = TeleautoMaterialAssets.card(context)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(suggestedMinimumHeight, heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        paint.typeface = TeleautoUiTypography.medium(context); paint.textSize = 11f * density; paint.color = titleColor
        canvas.drawText(title, 12f * density, 21f * density, paint)
        paint.textSize = 15f * density; paint.color = accentColor
        canvas.drawText(valueText, 12f * density, height - 13f * density, paint)
    }
}

class TeleautoBatteryStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    var levelPercent: Int? = null; private set
    fun submit(component: TeleautoBatteryComponent?) {
        levelPercent = component?.levelPercent
        title = "电量"; valueText = component?.levelPercent?.let { "$it%" } ?: "—"
        accentColor = when (component?.levelPercent) { null -> TeleautoUiTokens.TEXT_SECONDARY; in 0..15 -> TeleautoUiTokens.DANGER; in 16..30 -> TeleautoUiTokens.WARNING; else -> TeleautoUiTokens.TEXT }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = width - 47f * density; val top = 17f * density
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * density; paint.color = accentColor
        canvas.drawRoundRect(left, top, left + 31f * density, top + 16f * density, 3f * density, 3f * density, paint)
        canvas.drawLine(left + 33f * density, top + 5f * density, left + 33f * density, top + 11f * density, paint)
        paint.style = Paint.Style.FILL
        val ratio = (levelPercent ?: 0).coerceIn(0, 100) / 100f
        canvas.drawRoundRect(left + 3f * density, top + 3f * density, left + (3f + 25f * ratio) * density, top + 13f * density, 1.5f * density, 1.5f * density, paint)
    }
}

class TeleautoComponentJoystickView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    enum class Side { LEFT, RIGHT }
    var side = Side.LEFT; set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frameRect = RectF()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.SCREEN_LINE; strokeWidth = context.dp(.7f) }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.JOYSTICK_GRID; strokeWidth = context.dp(1.2f) }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.STICK_HEAD }
    private val headRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.TEXT
        style = Paint.Style.STROKE
        strokeWidth = context.dp(4f)
    }
    private var staticLayer: Bitmap? = null
    @Volatile private var pending = TeleautoStickSnapshot()
    private var shown = TeleautoStickSnapshot()
    private var framePending = false
    private val frame = Choreographer.FrameCallback { framePending = false; shown = pending; invalidate() }
    init { minimumWidth = context.dp(120f).toInt(); minimumHeight = minimumWidth; isClickable = false }
    fun submit(snapshot: TeleautoStickSnapshot) {
        pending = snapshot
        if (!framePending) { framePending = true; Choreographer.getInstance().postFrameCallback(frame) }
    }
    override fun onMeasure(w: Int, h: Int) {
        val size = min(resolveSize(suggestedMinimumWidth, w), resolveSize(suggestedMinimumHeight, h))
        setMeasuredDimension(size, size)
    }
    override fun onTouchEvent(event: MotionEvent?) = false
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        staticLayer?.recycle()
        if (w <= 0 || h <= 0) return
        staticLayer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bitmap ->
            val target = Canvas(bitmap)
            val inset = context.dp(8f)
            frameRect.set(inset, inset, w - inset, h - inset)
            paint.color = TeleautoUiTokens.SURFACE
            target.drawRoundRect(frameRect, context.dp(12f), context.dp(12f), paint)
            for (index in 1 until 8) {
                val x = frameRect.left + frameRect.width() * index / 8f
                val y = frameRect.top + frameRect.height() * index / 8f
                target.drawLine(x, frameRect.top, x, frameRect.bottom, gridPaint)
                target.drawLine(frameRect.left, y, frameRect.right, y, gridPaint)
            }
            target.drawLine(frameRect.centerX(), frameRect.top, frameRect.centerX(), frameRect.bottom, centerPaint)
            target.drawLine(frameRect.left, frameRect.centerY(), frameRect.right, frameRect.centerY(), centerPaint)
        }
    }
    override fun onDraw(canvas: Canvas) {
        staticLayer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        if (frameRect.isEmpty) frameRect.set(context.dp(8f), context.dp(8f), width - context.dp(8f), height - context.dp(8f))
        val x = if (side == Side.LEFT) shown.leftX else shown.rightX
        val y = if (side == Side.LEFT) shown.leftY else shown.rightY
        val cx = frameRect.centerX() + x.coerceIn(-1f, 1f) * frameRect.width() * .34f
        val cy = frameRect.centerY() - y.coerceIn(-1f, 1f) * frameRect.height() * .34f
        val radius = frameRect.width() * .105f
        canvas.drawCircle(cx, cy, radius, headPaint)
        canvas.drawCircle(cx, cy, radius - headRingPaint.strokeWidth / 2f, headRingPaint)
    }
    override fun onDetachedFromWindow() { staticLayer?.recycle(); staticLayer = null; super.onDetachedFromWindow() }
}

class TeleautoJoystickPairView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val left = TeleautoComponentJoystickView(context).apply { side = TeleautoComponentJoystickView.Side.LEFT }
    private val right = TeleautoComponentJoystickView(context).apply { side = TeleautoComponentJoystickView.Side.RIGHT }
    init {
        orientation = HORIZONTAL
        addView(left, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = context.dp(8f).toInt() })
        addView(right, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }
    fun submit(component: TeleautoJoystickComponent?) {
        component?.snapshot?.let { left.submit(it); right.submit(it) }
    }
}

class TeleautoHardwareButtonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var label: String = "KEY"; set(value) { field = value; invalidate() }
    var hardwareActive: Boolean = false; set(value) {
        field = value
        background = if (value) TeleautoMaterialAssets.keycapPressed(context) else TeleautoMaterialAssets.keycap(context)
        invalidate()
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init {
        minimumWidth = context.dp(56f).toInt(); minimumHeight = context.dp(40f).toInt(); isClickable = false
        background = TeleautoMaterialAssets.keycap(context)
    }
    fun submit(key: TeleautoPhysicalKey, held: Set<TeleautoPhysicalKey>) { label = key.label; hardwareActive = key in held }
    override fun onTouchEvent(event: MotionEvent?) = false
    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        paint.color = TeleautoUiTokens.TEXT; paint.textSize = 12f * d; paint.typeface = TeleautoUiTypography.medium(context); paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, width / 2f, height / 2f - (paint.ascent() + paint.descent()) / 2f, paint); paint.textAlign = Paint.Align.LEFT
    }
}

class TeleautoHardwareButtonsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    init { orientation = VERTICAL }
    fun submit(component: TeleautoButtonComponent?) {
        removeAllViews()
        val value = component ?: return
        value.descriptors.filterNot { it is TeleautoFiveWayControl }.chunked(4).forEach { rowItems ->
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                rowItems.forEach { descriptor ->
                    val key = descriptor.inputs.firstOrNull() ?: return@forEach
                    addView(TeleautoHardwareButtonView(context).apply {
                        label = descriptor.displayName; hardwareActive = key in value.held
                    }, LayoutParams(0, context.dp(42f).toInt(), 1f).apply { marginEnd = context.dp(8f).toInt() })
                }
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = context.dp(8f).toInt() })
        }
    }
}

class TeleautoFiveWayButtonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG); private var held = emptySet<TeleautoPhysicalKey>()
    private val directionIcon = context.getDrawable(R.drawable.ic_dji_chevron_up)?.mutate()
    private val centerIcon = context.getDrawable(R.drawable.ic_dji_center)?.mutate()
    init { minimumWidth = context.dp(104f).toInt(); minimumHeight = context.dp(104f).toInt(); isClickable = false }
    fun submit(component: TeleautoFiveWayButtonComponent?) { held = component?.held.orEmpty(); invalidate() }
    override fun onTouchEvent(event: MotionEvent?) = false
    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density; val cx = width / 2f; val cy = height / 2f; val step = min(width, height) * .27f; val radius = 15f * d
        drawKey(canvas, cx, cy - step, radius, TeleautoPhysicalKey.D_UP, directionIcon, 0f)
        drawKey(canvas, cx, cy + step, radius, TeleautoPhysicalKey.D_DOWN, directionIcon, 180f)
        drawKey(canvas, cx - step, cy, radius, TeleautoPhysicalKey.D_LEFT, directionIcon, -90f)
        drawKey(canvas, cx + step, cy, radius, TeleautoPhysicalKey.D_RIGHT, directionIcon, 90f)
        drawKey(canvas, cx, cy, radius, TeleautoPhysicalKey.D_MIDDLE, centerIcon, 0f)
    }
    private fun drawKey(c: Canvas, x: Float, y: Float, r: Float, key: TeleautoPhysicalKey, icon: Drawable?, rotation: Float) {
        paint.color = if (key in held) TeleautoUiTokens.SURFACE_PRESSED else TeleautoUiTokens.SURFACE; c.drawCircle(x, y, r, paint)
        icon ?: return
        val half = (r * .52f).toInt()
        icon.setTint(TeleautoUiTokens.TEXT)
        icon.setBounds((x - half).toInt(), (y - half).toInt(), (x + half).toInt(), (y + half).toInt())
        c.save(); c.rotate(rotation, x, y); icon.draw(c); c.restore()
    }
}

open class TeleautoComponentDialView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    protected var dialValue = 0f
    fun submit(component: TeleautoDialComponent?) { dialValue = component?.value?.coerceIn(-1f, 1f) ?: 0f; valueText = "%+.0f".format(dialValue * 100); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val d = resources.displayMetrics.density; val y = height - 10f * d; val left = 12f * d; val right = width - 12f * d
        paint.color = TeleautoUiTokens.LINE; canvas.drawRoundRect(left, y - 2f * d, right, y + 2f * d, 2f * d, 2f * d, paint)
        paint.color = TeleautoUiTokens.TEXT; val x = (left + right) / 2f + dialValue * (right - left) / 2f; canvas.drawCircle(x, y, 4f * d, paint)
    }
}
class TeleautoComponentCenteringDialView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoComponentDialView(context, attrs)
class TeleautoComponentContinuousDialView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoComponentDialView(context, attrs)

class TeleautoThreePositionSwitchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG); private var selected = 1
    init { minimumWidth = context.dp(142f).toInt(); minimumHeight = context.dp(48f).toInt(); isClickable = false }
    fun submit(component: TeleautoThreePositionSwitchComponent?) { selected = when (component?.state?.mode) { TeleautoThreePositionSwitchMode.CINE -> 0; TeleautoThreePositionSwitchMode.SPORT -> 2; else -> 1 }; invalidate() }
    override fun onTouchEvent(event: MotionEvent?) = false
    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density; val labels = arrayOf("C", "N", "S"); val cell = width / 3f
        labels.forEachIndexed { index, label ->
            paint.color = if (index == selected) TeleautoUiTokens.TEXT else TeleautoUiTokens.SURFACE; canvas.drawRoundRect(index * cell + 2f*d, 4f*d, (index+1)*cell-2f*d, height-4f*d, 8f*d, 8f*d, paint)
            paint.color = if (index == selected) TeleautoUiTokens.BACKGROUND else TeleautoUiTokens.TEXT_SECONDARY; paint.textAlign = Paint.Align.CENTER; paint.textSize = 13f*d; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(label, index*cell+cell/2f, height/2f-(paint.ascent()+paint.descent())/2f, paint)
        }; paint.textAlign = Paint.Align.LEFT
    }
}

/** Shared presentation state for every hot-pluggable SDK module. */
enum class TeleautoModuleAvailabilityState {
    UNAVAILABLE,
    PREPARING,
    AVAILABLE,
}

class TeleautoTransmitModuleStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    var availabilityState: TeleautoModuleAvailabilityState = TeleautoModuleAvailabilityState.UNAVAILABLE
        private set

    fun submit(component: TeleautoTransmitModuleComponent?) {
        title = component?.displayName ?: "高频头"
        availabilityState = when (component?.connectionState) {
            TeleautoComponentConnectionState.CONNECTED -> TeleautoModuleAvailabilityState.AVAILABLE
            TeleautoComponentConnectionState.CONNECTING -> TeleautoModuleAvailabilityState.PREPARING
            else -> TeleautoModuleAvailabilityState.UNAVAILABLE
        }
        valueText = component?.connectionState?.name ?: "未接入"
        applyModuleAvailability()
    }

    fun submit(modules: List<TeleautoTransmitModuleInfo>) {
        val module = modules.firstOrNull()
        title = "发射模块"
        availabilityState = when (module?.state) {
            TeleautoTransmitModuleState.CONNECTED -> TeleautoModuleAvailabilityState.AVAILABLE
            TeleautoTransmitModuleState.CONNECTING -> TeleautoModuleAvailabilityState.PREPARING
            else -> TeleautoModuleAvailabilityState.UNAVAILABLE
        }
        valueText = when (module?.state) {
            TeleautoTransmitModuleState.CONNECTED -> "发射模块可用"
            TeleautoTransmitModuleState.CONNECTING -> "正在准备发射模块"
            TeleautoTransmitModuleState.ERROR -> "发射模块不可用"
            null -> "请安装发射模块"
        }
        applyModuleAvailability()
    }

    private fun applyModuleAvailability() {
        accentColor = moduleAvailabilityColor(availabilityState)
        titleColor = accentColor
        background = TeleautoMaterialAssets.moduleStatus(context, availabilityState)
    }
}
class TeleautoVideoReceiverStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    fun submit(component: TeleautoVideoReceiverComponent?) {
        title = component?.displayName ?: "图传接收端"
        valueText = component?.detail ?: component?.connectionState?.name ?: "未接入"
        accentColor = when (component?.connectionState) {
            TeleautoComponentConnectionState.CONNECTED -> TeleautoUiTokens.SUCCESS
            TeleautoComponentConnectionState.ERROR -> TeleautoUiTokens.DANGER
            else -> TeleautoUiTokens.TEXT_SECONDARY
        }
    }
}

/** Settings-page presentation of the receiver hot-plug state. USB remains Core-owned. */
class TeleautoVideoModuleStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TeleautoStatusComponentView(context, attrs) {
    var availabilityState: TeleautoModuleAvailabilityState = TeleautoModuleAvailabilityState.UNAVAILABLE
        private set

    fun submit(modules: List<TeleautoVideoTransmissionModuleInfo>) {
        val module = modules.firstOrNull()
        title = "图传接收端"
        val state = module?.state ?: TeleautoWfbAdapterState.ABSENT
        availabilityState = when (state) {
            TeleautoWfbAdapterState.READY,
            TeleautoWfbAdapterState.RECEIVING,
            TeleautoWfbAdapterState.CONNECTED -> TeleautoModuleAvailabilityState.AVAILABLE
            TeleautoWfbAdapterState.USB_PRESENT,
            TeleautoWfbAdapterState.PERMISSION_REQUIRED,
            TeleautoWfbAdapterState.PERMISSION_GRANTED,
            TeleautoWfbAdapterState.INITIALIZING,
            TeleautoWfbAdapterState.CONNECTING -> TeleautoModuleAvailabilityState.PREPARING
            else -> TeleautoModuleAvailabilityState.UNAVAILABLE
        }
        valueText = when (state) {
            TeleautoWfbAdapterState.ABSENT, TeleautoWfbAdapterState.DISABLED -> "请安装图传接收端"
            TeleautoWfbAdapterState.USB_PRESENT,
            TeleautoWfbAdapterState.PERMISSION_GRANTED,
            TeleautoWfbAdapterState.INITIALIZING,
            TeleautoWfbAdapterState.CONNECTING -> "正在准备图传接收端"
            TeleautoWfbAdapterState.READY,
            TeleautoWfbAdapterState.RECEIVING,
            TeleautoWfbAdapterState.CONNECTED -> "图传接收端可用"
            TeleautoWfbAdapterState.PERMISSION_REQUIRED -> "请允许访问图传接收端"
            TeleautoWfbAdapterState.ERROR_USB_PROTOCOL,
            TeleautoWfbAdapterState.ERROR -> "图传接收端连接异常"
        }
        accentColor = moduleAvailabilityColor(availabilityState)
        titleColor = accentColor
        background = TeleautoMaterialAssets.moduleStatus(context, availabilityState)
    }
}

private fun moduleAvailabilityColor(state: TeleautoModuleAvailabilityState): Int = when (state) {
    TeleautoModuleAvailabilityState.AVAILABLE -> TeleautoUiTokens.MODULE_AVAILABLE
    TeleautoModuleAvailabilityState.PREPARING -> TeleautoUiTokens.MODULE_PREPARING
    TeleautoModuleAvailabilityState.UNAVAILABLE -> TeleautoUiTokens.MODULE_UNAVAILABLE
}
class TeleautoDeviceSummaryView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    fun submit(snapshot: TeleautoDeviceSnapshot?) { title = snapshot?.type?.displayName ?: "设备"; valueText = snapshot?.info?.displayName ?: "未连接"; accentColor = if (snapshot?.connectionState == TeleautoDeviceConnectionState.CONNECTED) TeleautoUiTokens.SUCCESS else TeleautoUiTokens.TEXT_SECONDARY }
}
class TeleautoGpsStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    fun submit(c: TeleautoGpsComponent?) { title = "GPS"; valueText = c?.let { "FIX ${it.fixType} · ${it.satellitesVisible ?: 0} SAT" } ?: "—" }
}
class TeleautoPositionStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    fun submit(c: TeleautoPositionComponent?) { title = "位置"; valueText = c?.let { "%.5f, %.5f · %.1fm".format(it.latitudeDegrees, it.longitudeDegrees, it.altitudeM) } ?: "—" }
}
class TeleautoAttitudeStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    fun submit(c: TeleautoAttitudeComponent?) { title = "姿态"; valueText = c?.let { "R %.0f°  P %.0f°  Y %.0f°".format(it.rollDegrees, it.pitchDegrees, it.yawDegrees) } ?: "—" }
}
class TeleautoImuStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    fun submit(c: TeleautoImuComponent?) { title = "IMU"; valueText = c?.let { "A %.1f/%.1f/%.1f".format(it.accelerationX, it.accelerationY, it.accelerationZ) } ?: "—" }
}
class TeleautoControlLinkStatusView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    fun submit(c: TeleautoControlLinkComponent?) { title = "控制链路"; valueText = c?.let { "RSSI ${it.localRssi ?: 0} · BUF ${it.txBufferPercent ?: 0}%" } ?: "—" }
}

class TeleautoDeviceTypeSelectorView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    var onDeviceTypeSelected: ((TeleautoDeviceType) -> Unit)? = null
    private val categoryRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val typeRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val typeScroller = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(typeRow) }
    private var availableTypes: List<TeleautoDeviceType> = emptyList()
    init {
        orientation = VERTICAL
        addView(categoryRow, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(42f).toInt()))
        addView(typeScroller, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(46f).toInt()).apply { topMargin = context.dp(6f).toInt() })
        setDeviceTypes(TeleautoDeviceType.selectable())
    }
    fun setDeviceTypes(types: List<TeleautoDeviceType>) {
        availableTypes = types.filter { it.category != null }
        categoryRow.removeAllViews()
        TeleautoDeviceCategory.entries.filter { category -> availableTypes.any { it.category == category } }.forEach { category ->
            categoryRow.addView(selectorButton(category.displayName) { showCategory(category) }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = context.dp(6f).toInt()
            })
        }
        availableTypes.firstOrNull()?.category?.let(::showCategory)
    }
    private fun showCategory(category: TeleautoDeviceCategory) {
        typeRow.removeAllViews()
        availableTypes.filter { it.category == category }.forEach { type ->
            typeRow.addView(selectorButton("＋ ${type.displayName}") { onDeviceTypeSelected?.invoke(type) }, LayoutParams(context.dp(148f).toInt(), LayoutParams.MATCH_PARENT).apply {
                marginEnd = context.dp(6f).toInt()
            })
        }
    }
    private fun selectorButton(label: String, click: () -> Unit) = Button(context).apply {
        text = label; setTextColor(TeleautoUiTokens.TEXT); isAllCaps = false; textSize = 11f
        background = TeleautoMaterialAssets.keycapSelector(context)
        stateListAnimator = null
        isFocusable = true
        setOnClickListener { click() }
    }
}

class TeleautoDeviceConnectionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoStatusComponentView(context, attrs) {
    private var device: TeleautoDevice? = null
    init {
        isClickable = true
        setOnClickListener {
            val target = device ?: return@setOnClickListener
            when (target.snapshot.connectionState) {
                TeleautoDeviceConnectionState.CONNECTED, TeleautoDeviceConnectionState.CONNECTING -> target.disconnect()
                else -> target.connect()
            }
        }
    }
    fun bind(device: TeleautoDevice?) { this.device = device; submit(device?.snapshot) }
    fun submit(snapshot: TeleautoDeviceSnapshot?) {
        title = "连接"
        valueText = when (snapshot?.connectionState) {
            TeleautoDeviceConnectionState.CONNECTED -> "已连接 · 点击断开"
            TeleautoDeviceConnectionState.CONNECTING -> "正在连接"
            TeleautoDeviceConnectionState.ERROR -> "连接失败 · 点击重试"
            null -> "请先选择设备"
            else -> "未连接 · 点击连接"
        }
        accentColor = when (snapshot?.connectionState) { TeleautoDeviceConnectionState.CONNECTED -> TeleautoUiTokens.SUCCESS; TeleautoDeviceConnectionState.ERROR -> TeleautoUiTokens.DANGER; else -> TeleautoUiTokens.TEXT_SECONDARY }
    }
}

class TeleautoVideoTransmissionView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    val videoView = TeleautoVideoView(context)
    private val overlay = VideoOverlay(context)
    private var uiState = TeleautoVideoUiState()
    var onAction: ((TeleautoUiAction) -> Unit)? = null
    var onFullscreenRequested: ((Boolean) -> Unit)? = null
    var onPhotoRequested: ((android.graphics.Bitmap) -> Unit)? = null
    var onRecordToggleRequested: ((Boolean) -> Unit)? = null
    var onFocusRequested: (() -> Unit)? = null
    var onVideoControlUnavailable: ((VideoControl) -> Unit)? = null
    var expanded = false; set(value) { field = value; overlay.expanded = value; clipToOutline = !value }
    enum class VideoControl { PHOTO, RECORD, FOCUS }
    init {
        setBackgroundColor(Color.BLACK); addView(videoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)); addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        overlay.onCenter = { onAction?.invoke(TeleautoUiAction.ToggleVideoStream) }
        overlay.onCorner = { expanded = !expanded; onFullscreenRequested?.invoke(expanded) }
        overlay.onPhoto = {
            val bitmap = if (uiState.hasSource) captureBitmap() else null
            if (bitmap == null) onVideoControlUnavailable?.invoke(VideoControl.PHOTO) else onPhotoRequested?.invoke(bitmap)
        }
        overlay.onRecord = {
            if (!uiState.hasSource) onVideoControlUnavailable?.invoke(VideoControl.RECORD)
            else {
                overlay.recording = !overlay.recording
                onRecordToggleRequested?.invoke(overlay.recording)
            }
        }
        overlay.onFocus = {
            if (!uiState.hasSource) onVideoControlUnavailable?.invoke(VideoControl.FOCUS)
            else { showFocusFeedback(); onFocusRequested?.invoke() }
        }
    }
    fun captureBitmap() = videoView.captureBitmap()
    fun showFocusFeedback() = overlay.focus()
    fun setRecording(recording: Boolean) { overlay.recording = recording }
    fun submit(state: TeleautoVideoUiState) {
        uiState = state
        overlay.paused = !state.streaming
        overlay.recording = state.recording
        expanded = state.expanded
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        val measuredWidth: Int
        val measuredHeight: Int
        if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
            measuredWidth = availableWidth
            measuredHeight = (availableWidth * 9f / 16f).toInt().coerceAtMost(availableHeight.takeIf { heightMode == MeasureSpec.AT_MOST } ?: Int.MAX_VALUE)
        } else if (heightMode == MeasureSpec.EXACTLY && widthMode != MeasureSpec.EXACTLY) {
            measuredHeight = availableHeight
            measuredWidth = (availableHeight * 16f / 9f).toInt().coerceAtMost(availableWidth.takeIf { widthMode == MeasureSpec.AT_MOST } ?: Int.MAX_VALUE)
        } else {
            measuredWidth = availableWidth
            measuredHeight = availableHeight
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY),
        )
    }

    private class VideoOverlay(context: Context) : View(context) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG); var onCenter: (() -> Unit)? = null; var onCorner: (() -> Unit)? = null
        var onPhoto: (() -> Unit)? = null; var onRecord: (() -> Unit)? = null; var onFocus: (() -> Unit)? = null
        private val expandIcon = context.getDrawable(R.drawable.ic_dji_expand)?.mutate()
        private val collapseIcon = context.getDrawable(R.drawable.ic_dji_collapse)?.mutate()
        private val playIcon = context.getDrawable(R.drawable.ic_dji_play)?.mutate()
        private val photoIcon = context.getDrawable(R.drawable.ic_dji_photo)?.mutate()
        private val recordIcon = context.getDrawable(R.drawable.ic_dji_record)?.mutate()
        private val focusIcon = context.getDrawable(R.drawable.ic_dji_focus)?.mutate()
        private var focusAnimator: ValueAnimator? = null
        var paused = false; set(v) { field = v; invalidate() }; var expanded = false; set(v) { field = v; invalidate() }
        var recording = false; set(v) { field = v; invalidate() }; var focusAlpha = 0f
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action != MotionEvent.ACTION_UP) return true
            val d = resources.displayMetrics.density
            val hit = 64f * d
            if (e.x >= width-hit && e.y <= hit) onCorner?.invoke()
            else if (e.y >= height - 58f*d) {
                val start = width - 168f*d
                when {
                    e.x >= start + 112f*d -> onPhoto?.invoke()
                    e.x >= start + 56f*d -> onRecord?.invoke()
                    e.x >= start -> onFocus?.invoke()
                    else -> onCenter?.invoke()
                }
            } else onCenter?.invoke()
            return true
        }
        fun focus() {
            focusAnimator?.cancel()
            focusAlpha = 1f
            invalidate()
            focusAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                startDelay = 100L
                duration = 140L
                interpolator = PathInterpolator(.23f, 1f, .32f, 1f)
                addUpdateListener { focusAlpha = it.animatedValue as Float; invalidate() }
                start()
            }
        }
        override fun onDetachedFromWindow() {
            focusAnimator?.cancel()
            focusAnimator = null
            super.onDetachedFromWindow()
        }
        override fun onDraw(c: Canvas) {
            val d = resources.displayMetrics.density
            (if (expanded) collapseIcon else expandIcon)?.apply {
                setTint(Color.WHITE)
                setBounds(width - (44f*d).toInt(), (12f*d).toInt(), width - (12f*d).toInt(), (44f*d).toInt())
                draw(c)
            }
            if (paused) playIcon?.apply {
                setTint(Color.WHITE)
                val half = (18f*d).toInt()
                setBounds(width/2-half, height/2-half, width/2+half, height/2+half)
                draw(c)
            }
            val iconSize = (24f*d).toInt(); val bottom = height - (14f*d).toInt(); val top = bottom - iconSize
            val icons = arrayOf(focusIcon, recordIcon, photoIcon)
            icons.forEachIndexed { index, icon -> icon?.apply {
                setTint(if (index == 1 && recording) TeleautoUiTokens.DANGER else Color.WHITE)
                val right = width - (14f*d).toInt() - (2-index)*(56f*d).toInt()
                setBounds(right-iconSize, top, right, bottom); draw(c)
            } }
            if (focusAlpha>0f) { p.color=Color.WHITE; p.strokeWidth=2f*d; p.alpha=(255*focusAlpha).toInt(); p.style=Paint.Style.STROKE; c.drawRect(width*.4f,height*.32f,width*.6f,height*.68f,p); p.alpha=255 }
        }
    }
}

fun interface TeleautoDeviceComponentRenderer { fun create(context: Context, component: TeleautoDeviceComponent): View }

class TeleautoDeviceRendererRegistry {
    private val renderers = mutableMapOf<TeleautoComponentType, TeleautoDeviceComponentRenderer>()
    fun register(type: TeleautoComponentType, renderer: TeleautoDeviceComponentRenderer) = apply { renderers[type] = renderer }
    fun renderer(type: TeleautoComponentType): TeleautoDeviceComponentRenderer? = renderers[type]
}

class TeleautoDeviceDashboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    val renderers = TeleautoDeviceRendererRegistry()
    private val summary = TeleautoDeviceSummaryView(context)
    private val componentViews = linkedMapOf<TeleautoComponentId, View>()
    private var signature = emptyList<Pair<TeleautoComponentId, TeleautoComponentType>>()
    init {
        orientation = VERTICAL
        setPadding(context.dp(12f).toInt(), context.dp(12f).toInt(), context.dp(12f).toInt(), context.dp(12f).toInt())
        addView(summary, row())
    }
    fun submit(snapshot: TeleautoDeviceSnapshot) {
        summary.submit(snapshot)
        val components = snapshot.components.sortedBy(::defaultOrder).filter(::canRender)
        val nextSignature = components.map { it.id to it.type }
        val hasCustomRenderer = components.any { renderers.renderer(it.type) != null }
        if (nextSignature != signature || hasCustomRenderer) {
            removeViews(1, childCount - 1)
            componentViews.clear()
            components.forEach { component ->
                val view = renderers.renderer(component.type)?.create(context, component)
                    ?: defaultView(component) ?: return@forEach
                componentViews[component.id] = view
                addView(view, row())
            }
            signature = nextSignature
        } else {
            components.forEach { component -> componentViews[component.id]?.let { bind(it, component) } }
        }
    }
    private fun defaultView(c: TeleautoDeviceComponent): View? = when (c) {
        is TeleautoBatteryComponent -> TeleautoBatteryStatusView(context).apply { submit(c) }
        is TeleautoJoystickComponent -> TeleautoJoystickPairView(context).apply { submit(c) }
        is TeleautoButtonComponent -> TeleautoHardwareButtonsView(context).apply { submit(c) }
        is TeleautoFiveWayButtonComponent -> TeleautoFiveWayButtonView(context).apply { submit(c) }
        is TeleautoDialComponent -> if (c.type == TeleautoComponentType.CONTINUOUS_DIAL) TeleautoComponentContinuousDialView(context).apply { submit(c) } else TeleautoComponentCenteringDialView(context).apply { submit(c) }
        is TeleautoThreePositionSwitchComponent -> TeleautoThreePositionSwitchView(context).apply { submit(c) }
        is TeleautoTransmitModuleComponent -> TeleautoTransmitModuleStatusView(context).apply { submit(c) }
        is TeleautoVideoReceiverComponent -> TeleautoVideoReceiverStatusView(context).apply { submit(c) }
        is TeleautoVideoTransmissionComponent -> TeleautoVideoTransmissionView(context)
        is TeleautoGpsComponent -> TeleautoGpsStatusView(context).apply { submit(c) }
        is TeleautoPositionComponent -> TeleautoPositionStatusView(context).apply { submit(c) }
        is TeleautoAttitudeComponent -> TeleautoAttitudeStatusView(context).apply { submit(c) }
        is TeleautoImuComponent -> TeleautoImuStatusView(context).apply { submit(c) }
        is TeleautoControlLinkComponent -> TeleautoControlLinkStatusView(context).apply { submit(c) }
        else -> null
    }
    private fun canRender(c: TeleautoDeviceComponent) = renderers.renderer(c.type) != null || when (c) {
        // 5D is an interaction/navigation source. It remains available in Core
        // and can still be rendered through an explicitly registered custom
        // renderer, but the automatic dashboard must not show a hardware pad.
        is TeleautoRemoteControllerComponent, is TeleautoAutopilotComponent, is TeleautoFiveWayButtonComponent -> false
        else -> true
    }
    private fun bind(view: View, component: TeleautoDeviceComponent) {
        when {
            view is TeleautoBatteryStatusView && component is TeleautoBatteryComponent -> view.submit(component)
            view is TeleautoJoystickPairView && component is TeleautoJoystickComponent -> view.submit(component)
            view is TeleautoHardwareButtonsView && component is TeleautoButtonComponent -> view.submit(component)
            view is TeleautoFiveWayButtonView && component is TeleautoFiveWayButtonComponent -> view.submit(component)
            view is TeleautoComponentContinuousDialView && component is TeleautoDialComponent -> view.submit(component)
            view is TeleautoComponentCenteringDialView && component is TeleautoDialComponent -> view.submit(component)
            view is TeleautoThreePositionSwitchView && component is TeleautoThreePositionSwitchComponent -> view.submit(component)
            view is TeleautoTransmitModuleStatusView && component is TeleautoTransmitModuleComponent -> view.submit(component)
            view is TeleautoVideoReceiverStatusView && component is TeleautoVideoReceiverComponent -> view.submit(component)
            view is TeleautoGpsStatusView && component is TeleautoGpsComponent -> view.submit(component)
            view is TeleautoPositionStatusView && component is TeleautoPositionComponent -> view.submit(component)
            view is TeleautoAttitudeStatusView && component is TeleautoAttitudeComponent -> view.submit(component)
            view is TeleautoImuStatusView && component is TeleautoImuComponent -> view.submit(component)
            view is TeleautoControlLinkStatusView && component is TeleautoControlLinkComponent -> view.submit(component)
        }
    }
    private fun row() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = context.dp(8f).toInt() }
    private fun defaultOrder(c: TeleautoDeviceComponent) = when (c.type) {
        TeleautoComponentType.REMOTE_CONTROLLER, TeleautoComponentType.AUTOPILOT, TeleautoComponentType.VIRTUAL_HID -> 0
        TeleautoComponentType.CONTROL_LINK, TeleautoComponentType.TRANSMIT_MODULE -> 10
        TeleautoComponentType.JOYSTICK, TeleautoComponentType.BUTTONS, TeleautoComponentType.FIVE_WAY_BUTTON -> 20
        TeleautoComponentType.CENTERING_DIAL, TeleautoComponentType.CONTINUOUS_DIAL, TeleautoComponentType.THREE_POSITION_SWITCH -> 30
        TeleautoComponentType.GPS, TeleautoComponentType.POSITION -> 40
        TeleautoComponentType.ATTITUDE, TeleautoComponentType.IMU -> 50
        TeleautoComponentType.BATTERY -> 60
        TeleautoComponentType.VIDEO_RECEIVER, TeleautoComponentType.VIDEO_TRANSMISSION -> 70
    }
}
