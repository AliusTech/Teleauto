package tech.alius.rc.ui

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.*
import tech.alius.rc.device.*

/** Xbox, Joystick and CyberBrick share exactly one icon/status/action presentation. */
class TeleautoDeviceTypeCoverView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TeleautoVideoFrameLayout(context, attrs) {
    var onConnectVideoTransmission: (() -> Unit)? = null
    var onConnectionAction: (() -> Unit)? = null
    var onSearchReceivers: (() -> Unit)? = null
    var onReceiverSelected: ((String) -> Unit)? = null
    private val density = resources.displayMetrics.density
    private fun px(value: Float) = (value*density).toInt()
    private fun text(size: Float, color: Int) = TextView(context).apply {
        textSize = size
        typeface = TeleautoUiTypography.regular(context)
        setTextColor(color); gravity = Gravity.CENTER; includeFontPadding = false
    }
    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
    private val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; imageTintList = ColorStateList.valueOf(TeleautoUiTokens.TEXT) }
    private val title = text(TeleautoUiTokens.TYPE_CARD_TITLE, TeleautoUiTokens.TEXT)
    private val status = text(TeleautoUiTokens.TYPE_LABEL, TeleautoUiTokens.TEXT_SECONDARY)
    private val detail = text(TeleautoUiTokens.TYPE_META, TeleautoUiTokens.TEXT_FAINT)
    private val action = text(TeleautoUiTokens.TYPE_LABEL, TeleautoUiTokens.TEXT).apply {
        isClickable = true; isFocusable = true
        background = TeleautoMaterialAssets.navigationItem(context)
        setOnClickListener { onConnectionAction?.invoke() }
    }
    private val video = text(TeleautoUiTokens.TYPE_LABEL, TeleautoUiTokens.TEXT).apply {
        text = "连接图传"; isClickable = true; isFocusable = true
        background = TeleautoMaterialAssets.navigationItem(context)
        setOnClickListener { onConnectVideoTransmission?.invoke() }
    }
    private val receivers = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(context).apply { addView(receivers); isFillViewport = false }
    private val search = text(TeleautoUiTokens.TYPE_LABEL, TeleautoUiTokens.TEXT_SECONDARY).apply {
        text = "重新搜索"; isClickable = true; isFocusable = true; background = TeleautoMaterialAssets.navigationItem(context)
        setOnClickListener { onSearchReceivers?.invoke() }
    }
    private var receiverSignature: Pair<List<TeleautoCyberBrickReceiver>, String?>? = null
    init {
        background = TeleautoMaterialAssets.videoPlaceholder(context)
        setPadding(px(TeleautoUiTokens.SPACE_4), px(TeleautoUiTokens.SPACE_2), px(TeleautoUiTokens.SPACE_4), px(TeleautoUiTokens.SPACE_2))
        column.addView(icon, LinearLayout.LayoutParams(px(TeleautoUiTokens.LIST_ROW_HEIGHT), px(TeleautoUiTokens.LIST_ROW_HEIGHT)))
        fun add(view: View, height: Int = LayoutParams.WRAP_CONTENT, margin: Float = TeleautoUiTokens.SPACE_2) {
            column.addView(view, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, height).apply { topMargin = px(margin) })
        }
        add(title); add(status, margin = TeleautoUiTokens.SPACE_1); add(detail, margin = TeleautoUiTokens.SPACE_1)
        column.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = px(TeleautoUiTokens.SPACE_1) })
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(search, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(action, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        add(actions, px(TeleautoUiTokens.CONTROL_HEIGHT), TeleautoUiTokens.SPACE_2)
        add(video, px(TeleautoUiTokens.CONTROL_HEIGHT), 0f)
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    fun submit(snapshot: TeleautoDeviceSnapshot?) {
        if (snapshot == null) return
        val type = snapshot.type
        val connected = snapshot.connectionState == TeleautoDeviceConnectionState.CONNECTED
        val connecting = snapshot.connectionState == TeleautoDeviceConnectionState.CONNECTING
        val ble = snapshot.cyberBrick
        val selecting = ble != null && !connected && !connecting
        icon.setImageResource(deviceIconResource(type))
        icon.contentDescription = type.displayName
        icon.alpha = if (snapshot.connectionState == TeleautoDeviceConnectionState.DISCONNECTED) .5f else 1f
        title.text = type.displayName
        status.text = ble?.message ?: when (snapshot.connectionState) {
            TeleautoDeviceConnectionState.CONNECTED -> "已连接"
            TeleautoDeviceConnectionState.CONNECTING -> if (type.isVirtualHid) "等待主机蓝牙连接" else "正在连接…"
            TeleautoDeviceConnectionState.DISCONNECTED -> "连接已断开"
            TeleautoDeviceConnectionState.ERROR -> "连接失败"
            TeleautoDeviceConnectionState.DISCONNECTING -> "正在断开…"
            else -> "等待连接"
        }
        detail.text = ble?.selectedName ?: (snapshot.error as? TeleautoDeviceConnectionError.Protocol)?.detail.orEmpty()
        detail.visibility = if (detail.text.isEmpty() || selecting) View.GONE else View.VISIBLE
        val hasReceivers = selecting && ble!!.receivers.isNotEmpty()
        scroll.visibility = if (hasReceivers) View.VISIBLE else View.GONE
        icon.visibility = if (hasReceivers) View.GONE else View.VISIBLE
        search.visibility = if (hasReceivers && ble?.scanning != true) View.VISIBLE else View.GONE
        action.text = when {
            connected -> "断开连接"
            connecting -> "取消连接"
            ble?.scanning == true -> "停止搜索"
            hasReceivers -> "连接设备"
            ble != null && ble.selectedAddress == null -> "搜索接收端"
            else -> "重新连接"
        }
        action.isEnabled = !hasReceivers || ble?.selectedAddress != null
        action.alpha = if (action.isEnabled) 1f else .5f
        video.visibility = if (!type.isBluetoothTarget && connected) View.VISIBLE else View.GONE
        if (ble != null) {
            val signature = ble.receivers to ble.selectedAddress
            if (signature != receiverSignature) {
                receiverSignature = signature; receivers.removeAllViews()
                ble.receivers.forEach { receiver ->
                    val row = RadioButton(context).apply {
                        text = "${receiver.name} · ${receiver.address.takeLast(5)}"
                        textSize = TeleautoUiTokens.TYPE_LABEL * TeleautoResponsiveMetrics.scale(context)
                        setTextColor(TeleautoUiTokens.TEXT)
                        buttonTintList = ColorStateList.valueOf(TeleautoUiTokens.TEXT_SECONDARY)
                        isChecked = receiver.address == ble.selectedAddress
                        maxLines = 1
                        setOnClickListener { onReceiverSelected?.invoke(receiver.address) }
                    }
                    receivers.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, (px(TeleautoUiTokens.CONTROL_HEIGHT) * TeleautoResponsiveMetrics.scale(context)).toInt()))
                }
            }
        }
        contentDescription = "${type.displayName}，${status.text}"
    }
}
