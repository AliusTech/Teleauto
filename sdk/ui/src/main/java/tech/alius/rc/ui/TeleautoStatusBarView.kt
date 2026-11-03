package tech.alius.rc.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import tech.alius.rc.ui.TeleautoUiTokens
import tech.alius.rc.ui.TeleautoUiTypography
import tech.alius.rc.model.TeleautoThreePositionSwitchMode
import java.util.Locale

/**
 * 控制首页状态栏(42dp):半透明磨砂蒙层 + 三段。
 * 左:app logo + 通用运行状态;
 * 中:纯文字设备名称;
 * 右:低电量优先的遥控器/高频头电量 / 锁定 / 设置齿轮。
 * 锁定按钮为操作安全设计:锁定后由宿主屏蔽全部屏幕触摸,仅状态栏可解锁。
 */
class TeleautoStatusBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var brand = "TELEAUTO"
    private var rcBattery = -1
    private var transmitBattery = -1
    private var batteryPage = 0
    private var batterySelectionManual = false
    private var controlMode = TeleautoThreePositionSwitchMode.NORMAL
    private var controlModeText = "N · 稳定模式"

    /** 齿轮按钮回调(打开飞行设置)。 */
    var onSettings: (() -> Unit)? = null

    /** 锁定按钮回调(参数为新状态)。 */
    var onLockToggle: ((Boolean) -> Unit)? = null

    /** 设备名铭牌点击回调(下拉设备信息/模式切换)。 */
    var onBrandClick: (() -> Unit)? = null

    /** 左侧 DJI 标识点击回调；设置页用它返回控制台。 */
    var onLogoClick: (() -> Unit)? = null

    /** 屏幕锁定态:锁定时锁体高亮、锁扣闭合。 */
    var locked = false
        set(value) { field = value; invalidate() }

    private val density = resources.displayMetrics.density * TeleautoResponsiveMetrics.scale(context)
    private val scaledDensity = resources.displayMetrics.scaledDensity * TeleautoResponsiveMetrics.scale(context)

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.TOP_BAR_BACKGROUND }
    private val scrimLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
        color = TeleautoUiTokens.GLASS_BORDER
    }
    private val centerPlatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101010.toInt() }
    private val centerPlateEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = .4f * density
        color = TeleautoUiTokens.GLASS_BORDER
    }
    private val centerPlatePath = Path()
    private val topBarSlice by lazy {
        resources.getDrawable(R.drawable.djirc_ui_top_bar, context.theme).mutate()
    }
    private val topPlateSlice by lazy {
        resources.getDrawable(R.drawable.djirc_ui_top_plate, context.theme).mutate()
    }

    private val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.TEXT
        textSize = TeleautoUiTokens.TYPE_APP_HEADER * scaledDensity
        typeface = TeleautoUiTypography.bold(context)
    }
    private val statusSmallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.TEXT_SECONDARY
        textSize = 7.5f * scaledDensity
    }

    // 顶部设备名称：纯大写文字，不使用标签底板或装饰边框。
    private val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.TEXT
        textSize = TeleautoUiTokens.TYPE_APP_HEADER * scaledDensity
        typeface = TeleautoUiTypography.bold(context)
        letterSpacing = -0.01f
        textAlign = Paint.Align.CENTER
    }
    private val batteryFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x52FFFFFF }
    private val plateRect = RectF()
    private val logoHitRect = RectF()
    private val batteryHitRect = RectF()

    private val batteryTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.TEXT
        textSize = 7.5f * scaledDensity
        typeface = TeleautoUiTypography.numericStrong
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = TeleautoUiTokens.TEXT_SECONDARY
    }

    /** 透明 Alius 标识；颜色直接表达当前三档状态。 */
    private val modeLogo by lazy {
        resources.getDrawable(R.drawable.ic_alius_logo, context.theme).mutate()
    }

    private val lockBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.BRAND_600 }
    private val lockBodyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.9f * density
        color = TeleautoUiTokens.TEXT_SECONDARY
    }
    private val lockShacklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val path = Path()
    private val settingsIcon by lazy { resources.getDrawable(R.drawable.djirc_ui_ic_settings_top, context.theme).mutate() }
    private val lockIcon by lazy { resources.getDrawable(R.drawable.djirc_ui_ic_lock, context.theme).mutate() }
    private val unlockIcon by lazy { resources.getDrawable(R.drawable.djirc_ui_ic_unlock, context.theme).mutate() }
    private val remoteBatteryIcon by lazy { resources.getDrawable(R.drawable.djirc_ui_ic_remote_controller, context.theme).mutate() }
    private val transmitBatteryIcon by lazy { resources.getDrawable(R.drawable.djirc_ui_ic_transmit_module, context.theme).mutate() }

    private var gearCx = 0f
    private var gearCy = 0f
    private val gearR get() = 12 * density
    private val settingsHitRect = RectF()
    private var settingsPressed = false
    private val settingsPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TeleautoUiTokens.ring(0x18)
    }
    private var lockCx = 0f
    private var lockCy = 0f
    private val lockR get() = 12 * density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize((TeleautoUiTokens.TOP_BAR_HEIGHT * density).toInt(), heightMeasureSpec)
        )
    }

    fun setBrand(text: String) {
        val uppercase = text.uppercase(Locale.ROOT)
        if (brand != uppercase) { brand = uppercase; invalidate() }
    }
    fun setRcBattery(percent: Int) {
        val value = percent.takeIf { it in 0..100 } ?: -1
        if (rcBattery != value) {
            rcBattery = value
            reconcileBatterySelection()
            invalidate()
        }
    }
    fun setTransmitBattery(percent: Int?) {
        val value = percent?.takeIf { it in 0..100 } ?: -1
        if (transmitBattery == value) return
        transmitBattery = value
        reconcileBatterySelection()
        invalidate()
    }

    private fun reconcileBatterySelection() {
        val currentAvailable = if (batteryPage == 0) rcBattery >= 0 else transmitBattery >= 0
        if (!currentAvailable) batterySelectionManual = false
        if (!batterySelectionManual) {
            batteryPage = when {
                rcBattery >= 0 && transmitBattery >= 0 -> if (transmitBattery < rcBattery) 1 else 0
                transmitBattery >= 0 -> 1
                else -> 0
            }
        }
    }
    fun setControlMode(mode: TeleautoThreePositionSwitchMode) {
        val resolvedMode = if (mode == TeleautoThreePositionSwitchMode.UNKNOWN) {
            TeleautoThreePositionSwitchMode.NORMAL
        } else {
            mode
        }
        val text = when (resolvedMode) {
            TeleautoThreePositionSwitchMode.CINE -> "C · 慢速模式"
            TeleautoThreePositionSwitchMode.NORMAL -> "N · 稳定模式"
            TeleautoThreePositionSwitchMode.SPORT -> "S · 运动模式"
            TeleautoThreePositionSwitchMode.UNKNOWN -> error("resolved above")
        }
        if (controlMode != resolvedMode || controlModeText != text) {
            controlMode = resolvedMode
            controlModeText = text
            invalidate()
        }
    }

    fun submit(state: TeleautoStatusBarUiState) {
        setBrand(state.deviceName)
        setRcBattery(state.remoteBatteryPercent ?: -1)
        setTransmitBattery(state.transmitBatteryPercent)
        setControlMode(state.mode)
        locked = state.locked
    }

    private val topBarSurface by lazy { TeleautoMaterialAssets.topBar(context) }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val padH = TeleautoUiTokens.TOP_BAR_HORIZONTAL_INSET * density
        val cy = h / 2f
        val cx = w / 2f

        topBarSurface.setBounds(0, 0, width, height)
        topBarSurface.draw(canvas)
        // ── 左:品牌 Logo + 模式文字同步使用档位色。
        var x = padH
        val modeColor = TeleautoUiTokens.modeColor(controlMode)
        val logoHeight = TeleautoUiTokens.TOP_BAR_LOGO_HEIGHT * density
        val logoWidth = logoHeight * TeleautoUiTokens.TOP_BAR_LOGO_ASPECT
        modeLogo.setBounds(
            x.toInt(),
            (cy - logoHeight / 2f).toInt(),
            (x + logoWidth).toInt(),
            (cy + logoHeight / 2f).toInt(),
        )
        modeLogo.setTint(modeColor)
        modeLogo.draw(canvas)
        logoHitRect.set(
            x - 8f * density,
            0f,
            x + logoWidth + 8f * density,
            h,
        )
        x += logoWidth + 9 * density

        canvas.drawText("系统运行中", x, cy - 0.5f * density, statusPaint)
        statusSmallPaint.color = modeColor
        canvas.drawText(controlModeText, x, cy + 9.5f * density, statusSmallPaint)

        // ── 中:纯大写设备名称；保留透明点击热区，不绘制任何标签容器。
        val brandW = brandPaint.measureText(brand)
        plateRect.set(cx - brandW / 2f - 16 * density, 0f, cx + brandW / 2f + 16 * density, h)
        val brandY = cy - (brandPaint.descent() + brandPaint.ascent()) / 2f
        canvas.drawText(brand, cx, brandY, brandPaint)

        // ── 右:低电量优先，可点击切换的设备电量 / 锁定 / 齿轮
        var rx = w - padH
        // 齿轮
        gearCx = rx - gearR
        gearCy = cy
        rx = gearCx - gearR - TeleautoUiTokens.TOP_BAR_BATTERY_SETTINGS_GAP * density
        settingsHitRect.set(gearCx - 14.5f * density, 0f, w, h)
        if (settingsPressed) {
            canvas.drawCircle(gearCx, gearCy, 13f * density, settingsPressedPaint)
        }
        drawIcon(canvas, settingsIcon, gearCx, gearCy, 18f)
        if (rcBattery >= 0 || transmitBattery >= 0) {
            val batteryWidth = 31f * density
            val batteryLeft = rx - batteryWidth - 2f * density
            val currentPercent = if (batteryPage == 0) rcBattery else transmitBattery
            drawBattery(canvas, batteryLeft, cy, currentPercent)
            val iconCx = batteryLeft - 13f * density
            drawBatterySourceIcon(canvas, batteryPage, iconCx, cy)
            batteryHitRect.set(iconCx - 12f * density, 0f, rx + 3f * density, h)
        } else {
            batteryHitRect.setEmpty()
        }
    }

    private fun drawIcon(canvas: Canvas, drawable: android.graphics.drawable.Drawable, cx: Float, cy: Float, sizeDp: Float) {
        val half = sizeDp * density / 2f
        drawable.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
        drawable.draw(canvas)
    }

    /** 挂锁图标:未锁定=描边锁体+锁扣右开;锁定=brand-600 实心锁体+锁扣闭合。 */
    private fun drawPadlock(canvas: Canvas, cx: Float, cy: Float) {
        val shackleColor = if (locked) TeleautoUiTokens.BRAND_700 else TeleautoUiTokens.TEXT_SECONDARY
        lockShacklePaint.color = shackleColor
        // 锁体 10×8dp,圆角 1.8dp
        val bodyL = cx - 5 * density
        val bodyR = cx + 5 * density
        val bodyT = cy - 1f * density
        val bodyB = cy + 7 * density
        if (locked) {
            canvas.drawRoundRect(RectF(bodyL, bodyT, bodyR, bodyB), 1.8f * density, 1.8f * density, lockBodyPaint)
        } else {
            canvas.drawRoundRect(RectF(bodyL, bodyT, bodyR, bodyB), 1.8f * density, 1.8f * density, lockBodyBorderPaint)
        }
        // 锁扣:上半圆;未锁定时右移并抬高(开扣)
        val shR = 3.4f * density
        val scx = if (locked) cx else cx + 1.8f * density
        val scy = if (locked) bodyT else bodyT - 1.2f * density
        path.reset()
        path.addArc(RectF(scx - shR, scy - shR * 2f, scx + shR, scy), 180f, 180f)
        canvas.drawPath(path, lockShacklePaint)
        // 锁定态钥匙孔
        if (locked) {
            val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TeleautoUiTokens.BRAND_50 }
            canvas.drawCircle(cx, cy + 3 * density, 1.1f * density, hole)
        }
    }

    private fun drawBatterySourceIcon(canvas: Canvas, page: Int, cx: Float, cy: Float) {
        val sourceIcon = if (page == 0) remoteBatteryIcon else transmitBatteryIcon
        sourceIcon.setTint(TeleautoUiTokens.TEXT_SECONDARY)
        drawIcon(canvas, sourceIcon, cx, cy, 18f)
    }

    /** 电池图标固定不移动；当前轮播来源提交后刷新填充和内部数值。 */
    private fun drawBattery(canvas: Canvas, left: Float, cy: Float, percent: Int) {
        val w = 31f * density
        val h = 14f * density
        canvas.drawRoundRect(
            RectF(left, cy - h / 2f, left + w, cy + h / 2f),
            3f * density, 3f * density, iconPaint,
        )
        canvas.drawRect(
            RectF(left + w + 0.7f * density, cy - 2.3f * density, left + w + 2f * density, cy + 2.3f * density),
            iconPaint,
        )
        if (percent >= 0) {
            canvas.drawRect(
                RectF(
                    left + 2f * density,
                    cy - h / 2f + 2f * density,
                    left + 2f * density + (w - 4f * density) * percent / 100f,
                    cy + h / 2f - 2f * density,
                ),
                batteryFillPaint,
            )
        }
        val value = if (percent >= 0) "$percent%" else "—"
        canvas.drawText(
            value,
            left + w / 2f,
            cy - (batteryTextPaint.descent() + batteryTextPaint.ascent()) / 2f,
            batteryTextPaint,
        )
    }

    /** 线性小飞机图标。 */
    private fun drawPlane(canvas: Canvas, cx: Float, cy: Float) {
        val s = 3.8f * density
        path.reset()
        path.moveTo(cx - s, cy - s * 0.15f)
        path.lineTo(cx + s * 0.9f, cy - s * 0.15f)
        path.moveTo(cx - s * 0.5f, cy - s * 0.15f)
        path.lineTo(cx - s * 0.5f, cy + s * 0.35f)
        path.moveTo(cx, cy - s * 0.5f)
        path.lineTo(cx, cy + s * 0.6f)
        canvas.drawPath(path, iconPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            settingsPressed = !locked && settingsHitRect.contains(event.x, event.y)
            if (settingsPressed) invalidate()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (settingsPressed) {
                settingsPressed = false
                invalidate()
            }
            return true
        }
        if (event.actionMasked != MotionEvent.ACTION_UP) {
            return true
        }
        val activateSettings = settingsPressed && settingsHitRect.contains(event.x, event.y)
        if (settingsPressed) {
            settingsPressed = false
            invalidate()
        }
        if (activateSettings) {
            performClick()
            onSettings?.invoke()
            return true
        }
        fun hit(cx: Float, cy: Float, radius: Float): Boolean {
            val dx = event.x - cx
            val dy = event.y - cy
            return dx * dx + dy * dy <= (radius + 4 * density) * (radius + 4 * density)
        }
        when {
            logoHitRect.contains(event.x, event.y) && !locked -> {
                performClick()
                onLogoClick?.invoke()
                return true
            }
            batteryHitRect.contains(event.x, event.y) && !locked -> {
                val rcAvailable = rcBattery >= 0
                val transmitAvailable = transmitBattery >= 0
                if (rcAvailable && transmitAvailable) {
                    performClick()
                    batteryPage = 1 - batteryPage
                    batterySelectionManual = true
                    invalidate()
                }
                return true
            }
            // 设备名铭牌区域 → 下拉设备信息
            event.x > plateRect.left && event.x < plateRect.right && event.y <= plateRect.bottom -> {
                performClick()
                if (!locked) onBrandClick?.invoke()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
