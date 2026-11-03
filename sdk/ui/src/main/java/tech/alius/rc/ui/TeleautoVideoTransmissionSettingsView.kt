package tech.alius.rc.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import tech.alius.rc.video.TeleautoVideoReceiverConfig
import tech.alius.rc.video.TeleautoVideoStats
import tech.alius.rc.video.TeleautoVideoTransmissionModuleInfo
import tech.alius.rc.video.TeleautoWfbAdapterState

/** Compact receiver configuration and live link test backed by the Core video pipeline. */
class TeleautoVideoTransmissionSettingsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    var onConfigChanged: ((TeleautoVideoReceiverConfig) -> Unit)? = null
    var onTestChanged: ((Boolean) -> Unit)? = null
    private var config = TeleautoVideoReceiverConfig()
    private var testing = false
    private var receiverAvailable = false
    private var showTestArea = false
    private var retainTestAreaDuringReconfigure = false
    private val moduleStatus = TeleautoVideoModuleStatusView(context)
    private val channelValue = valueLabel()
    private lateinit var bandwidth20Button: Button
    private lateinit var bandwidth40Button: Button
    private val quality = TextView(context).apply {
        text = "等待接收端"; setTextColor(TeleautoUiTokens.MODULE_UNAVAILABLE); textSize = 24f
        gravity = Gravity.CENTER; typeface = TeleautoUiTypography.bold(context)
    }
    private val previewMetrics = TextView(context).apply {
        text = "输入/输出 — fps   延迟 — ms\n丢包 —   损坏帧 —"
        setTextColor(TeleautoUiTokens.TEXT); textSize = 14f; typeface = TeleautoUiTypography.numeric(context)
        setPadding(context.dp(TeleautoUiTokens.SPACE_3).toInt(), context.dp(TeleautoUiTokens.SPACE_2).toInt(), context.dp(TeleautoUiTokens.SPACE_3).toInt(), context.dp(TeleautoUiTokens.SPACE_2).toInt())
        background = ColorDrawable(0xB0000000.toInt())
    }
    private val previewPlaceholder = TextView(context).apply {
        text = "图传预览\n等待视频流"
        setTextColor(TeleautoUiTokens.TEXT_SECONDARY)
        textSize = 16f
        gravity = Gravity.CENTER
        typeface = TeleautoUiTypography.medium(context)
    }
    val preview = TeleautoVideoView(context).apply {
        setCornerRadius(context.dp(TeleautoUiTokens.RADIUS_CARD - 2f))
    }
    private val testButton = actionButton("链路测试", ::toggleTest)
    private val previewFrame = AspectPreview(context).apply {
        addView(preview, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(previewPlaceholder, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(previewMetrics, FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.BOTTOM).apply {
            leftMargin = context.dp(TeleautoUiTokens.SPACE_3).toInt(); bottomMargin = context.dp(TeleautoUiTokens.SPACE_3).toInt()
        })
    }
    private val contentRow = EqualHeightVideoRow(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(previewFrame, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.35f))
        addView(configurationPanel(), LayoutParams(0, LayoutParams.MATCH_PARENT, 0.85f).apply {
            marginStart = context.dp(TeleautoUiTokens.SPACE_3).toInt()
        })
    }

    init {
        orientation = VERTICAL
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(moduleStatus, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(testButton, LayoutParams(context.dp(108f).toInt(), LayoutParams.MATCH_PARENT).apply { marginStart = context.dp(TeleautoUiTokens.SPACE_2).toInt() })
        }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(TeleautoUiTokens.MODULE_STATUS_HEIGHT).toInt()))
        addView(contentRow, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = context.dp(TeleautoUiTokens.SPACE_2).toInt() })
        refreshConfig(); refreshAvailability()
    }

    private fun configurationPanel() = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL; background = TeleautoMaterialAssets.card(context)
        setPadding(context.dp(TeleautoUiTokens.SPACE_4).toInt(), context.dp(TeleautoUiTokens.SPACE_3).toInt(), context.dp(TeleautoUiTokens.SPACE_4).toInt(), context.dp(TeleautoUiTokens.SPACE_3).toInt())
        addView(quality, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(configRow("信道").apply {
            addView(stepButton("−") { updateChannel(-1) })
            addView(channelValue, LayoutParams(context.dp(56f).toInt(), context.dp(TeleautoUiTokens.CONTROL_HEIGHT).toInt()))
            addView(stepButton("+") { updateChannel(1) })
        }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(TeleautoUiTokens.CONTROL_HEIGHT).toInt()).apply { topMargin = context.dp(TeleautoUiTokens.SPACE_2).toInt() })
        addView(configRow("带宽").apply {
            bandwidth20Button = bandwidthButton(20)
            bandwidth40Button = bandwidthButton(40)
            addView(bandwidth20Button, LayoutParams(context.dp(76f).toInt(), context.dp(TeleautoUiTokens.CONTROL_HEIGHT).toInt()).apply {
                marginEnd = context.dp(TeleautoUiTokens.SPACE_1).toInt()
            })
            addView(bandwidth40Button, LayoutParams(context.dp(76f).toInt(), context.dp(TeleautoUiTokens.CONTROL_HEIGHT).toInt()))
        }, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(TeleautoUiTokens.CONTROL_HEIGHT).toInt()).apply { topMargin = context.dp(TeleautoUiTokens.SPACE_2).toInt() })
    }

    private fun configRow(label: String) = LinearLayout(context).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = label; setTextColor(TeleautoUiTokens.TEXT_SECONDARY); textSize = 12f; typeface = TeleautoUiTypography.medium(context)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    fun submitModules(modules: List<TeleautoVideoTransmissionModuleInfo>) {
        moduleStatus.submit(modules)
        val state = modules.firstOrNull()?.state ?: TeleautoWfbAdapterState.ABSENT
        receiverAvailable = moduleStatus.availabilityState == TeleautoModuleAvailabilityState.AVAILABLE
        val preparing = state in setOf(
            TeleautoWfbAdapterState.USB_PRESENT,
            TeleautoWfbAdapterState.PERMISSION_GRANTED,
            TeleautoWfbAdapterState.INITIALIZING,
            TeleautoWfbAdapterState.CONNECTING,
        )
        if (receiverAvailable || !preparing) retainTestAreaDuringReconfigure = false
        showTestArea = receiverAvailable || (retainTestAreaDuringReconfigure && preparing)
        if (!receiverAvailable) stopTest()
        refreshAvailability()
    }

    fun submitConfig(value: TeleautoVideoReceiverConfig) { config = value; refreshConfig() }

    fun submitStats(stats: TeleautoVideoStats) {
        if (!testing) return
        val latency = stats.groundPipelineLatencyMs.p95
        val loss = stats.lostPackets
        val input = stats.inputFps
        val output = stats.renderedFps
        previewPlaceholder.visibility = if (output > 0) View.GONE else View.VISIBLE
        val (label, color) = when {
            output <= 0 -> "无视频" to TeleautoUiTokens.MODULE_UNAVAILABLE
            loss == 0L && (latency < 0 || latency <= 35.0) && output >= (input * 0.9f).toInt() -> "优秀" to TeleautoUiTokens.MODULE_AVAILABLE
            loss <= 3L && (latency < 0 || latency <= 60.0) -> "良好" to TeleautoUiTokens.MODULE_AVAILABLE
            loss <= 12L && (latency < 0 || latency <= 100.0) -> "一般" to TeleautoUiTokens.MODULE_PREPARING
            else -> "较差" to TeleautoUiTokens.DANGER
        }
        quality.text = label; quality.setTextColor(color)
        val latencyText = if (latency >= 0) "%.1f".format(latency) else "—"
        previewMetrics.text = "输入/输出 ${input}/${output} fps   延迟 $latencyText ms\n丢包 $loss   损坏帧 ${stats.damagedAccessUnits}"
    }

    fun stopTest() {
        if (!testing) return
        testing = false; testButton.text = "链路测试"
        previewPlaceholder.visibility = View.VISIBLE
        quality.text = if (receiverAvailable) "等待测试" else "等待接收端"
        quality.setTextColor(if (receiverAvailable) TeleautoUiTokens.TEXT_SECONDARY else TeleautoUiTokens.MODULE_UNAVAILABLE)
        onTestChanged?.invoke(false)
    }

    private fun updateChannel(delta: Int) {
        config = config.copy(channel = (config.channel + delta).coerceIn(1, 196))
        retainTestAreaDuringReconfigure = receiverAvailable
        refreshConfig(); onConfigChanged?.invoke(config)
    }

    private fun toggleTest() {
        if (!receiverAvailable) return
        testing = !testing; testButton.text = if (testing) "结束测试" else "链路测试"
        previewPlaceholder.visibility = View.VISIBLE
        quality.text = if (testing) "检测中" else "等待测试"; quality.setTextColor(TeleautoUiTokens.TEXT_SECONDARY)
        onTestChanged?.invoke(testing)
    }

    private fun refreshAvailability() {
        testButton.visibility = if (receiverAvailable) View.VISIBLE else View.GONE
        contentRow.visibility = if (showTestArea) View.VISIBLE else View.GONE
        if (!receiverAvailable) previewPlaceholder.visibility = View.VISIBLE
        quality.text = if (receiverAvailable) if (testing) "检测中" else "等待测试" else "等待接收端"
        quality.setTextColor(if (receiverAvailable) TeleautoUiTokens.TEXT_SECONDARY else TeleautoUiTokens.MODULE_UNAVAILABLE)
    }

    private fun refreshConfig() {
        channelValue.text = config.channel.toString()
        if (::bandwidth20Button.isInitialized) {
            bandwidth20Button.isSelected = config.bandwidthMHz == 20
            bandwidth40Button.isSelected = config.bandwidthMHz == 40
        }
    }

    private fun bandwidthButton(value: Int) = actionButton("$value MHz") {
        if (config.bandwidthMHz == value) return@actionButton
        config = config.copy(bandwidthMHz = value)
        retainTestAreaDuringReconfigure = receiverAvailable
        refreshConfig()
        onConfigChanged?.invoke(config)
    }

    private fun valueLabel() = TextView(context).apply {
        setTextColor(TeleautoUiTokens.TEXT); textSize = 13f; gravity = Gravity.CENTER; typeface = TeleautoUiTypography.numeric(context)
    }

    private fun stepButton(textValue: String, click: () -> Unit) = actionButton(textValue, click).apply {
        layoutParams = LayoutParams(context.dp(TeleautoUiTokens.CONTROL_HEIGHT).toInt(), context.dp(TeleautoUiTokens.CONTROL_HEIGHT).toInt())
    }

    private fun actionButton(label: String, click: () -> Unit) = Button(context).apply {
        text = label; isAllCaps = false; textSize = 11f; setTextColor(TeleautoUiTokens.TEXT)
        stateListAnimator = null; background = TeleautoMaterialAssets.keycapSelector(context); isFocusable = true
        setOnClickListener { click() }
    }

    private class AspectPreview(context: Context) : FrameLayout(context) {
        init {
            background = TeleautoMaterialAssets.card(context)
            val edge = context.dp(2f).toInt(); setPadding(edge, edge, edge, edge); clipToOutline = true
        }
    }

    private class EqualHeightVideoRow(context: Context) : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val maxHeight = MeasureSpec.getSize(heightMeasureSpec)
            val gap = context.dp(TeleautoUiTokens.SPACE_3).toInt()
            val previewWidth = ((width - gap) * 1.35f / (1.35f + 0.85f)).toInt()
            val height = minOf(maxHeight, (previewWidth * 9f / 16f).toInt())
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        }
    }
}
