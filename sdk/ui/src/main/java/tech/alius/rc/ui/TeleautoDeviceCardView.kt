package tech.alius.rc.ui

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import tech.alius.rc.device.TeleautoDeviceCategory
import tech.alius.rc.device.TeleautoDeviceType

/** Shared device card used by both device selection and device management grids. */
internal class TeleautoDeviceCardView(
    context: Context,
) : LinearLayout(context) {
    private val normalBackground = TeleautoMaterialAssets.card(context)
    private val selectedBackground = TeleautoMaterialAssets.panelRecessed(context)
    private val iconView = ImageView(context).apply {
        imageTintList = ColorStateList.valueOf(TeleautoUiTokens.TEXT)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val titleView = cardText(TeleautoUiTokens.TYPE_CARD_TITLE, TeleautoUiTokens.TEXT).apply {
        typeface = TeleautoUiTypography.bold(context)
    }
    private val metaView = cardText(TeleautoUiTokens.TYPE_LABEL, TeleautoUiTokens.TEXT_SECONDARY)
    private val statusView = cardText(TeleautoUiTokens.TYPE_META, TeleautoUiTokens.TEXT_FAINT)
    private val footer = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }
    private val selectionLabel = cardText(TeleautoUiTokens.TYPE_LABEL, TeleautoUiTokens.TEXT_SECONDARY).apply {
        gravity = Gravity.CENTER
        background = TeleautoMaterialAssets.keycapSelector(context)
        typeface = TeleautoUiTypography.bold(context)
    }
    private var focused = false

    var chosen = false
        set(value) {
            field = value
            selectionLabel.text = if (value) "已选择" else "选择"
            selectionLabel.setTextColor(if (value) TeleautoUiTokens.TEXT else TeleautoUiTokens.TEXT_SECONDARY)
            updateBackground()
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(
            context.dp(TeleautoUiTokens.SPACE_2).toInt(),
            context.dp(TeleautoUiTokens.SPACE_3).toInt(),
            context.dp(TeleautoUiTokens.SPACE_2).toInt(),
            context.dp(TeleautoUiTokens.SPACE_2).toInt(),
        )
        background = normalBackground
        isClickable = true
        isFocusable = true
        // Hardware focus remains available, but touch must activate on ACTION_UP.
        // Focusable-in-touch-mode makes old Android builds consume the first tap.
        isFocusableInTouchMode = false
        setOnFocusChangeListener { _, hasFocus ->
            focused = hasFocus
            updateBackground()
        }

        addView(iconView, LayoutParams(
            context.dp(TeleautoUiTokens.DEVICE_CARD_ICON_SIZE).toInt(),
            context.dp(TeleautoUiTokens.DEVICE_CARD_ICON_SIZE).toInt(),
        ))
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(TeleautoUiTokens.SPACE_1).toInt()
        })
        addView(metaView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(statusView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(View(context), LayoutParams(1, 0, 1f))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(TeleautoUiTokens.DEVICE_CARD_ACTION_HEIGHT).toInt()).apply {
            topMargin = context.dp(TeleautoUiTokens.SPACE_1).toInt()
        })
    }

    fun bindSelection(type: TeleautoDeviceType, onSelect: () -> Unit) {
        iconView.setImageResource(deviceIconResource(type))
        titleView.text = type.displayName
        metaView.text = type.category?.displayName.orEmpty()
        statusView.text = if (type.isVirtualHid) "虚拟 HID 设备" else if (type == TeleautoDeviceType.CYBERBRICK) "蓝牙 BLE 设备" else "MAVLink 设备"
        footer.removeAllViews()
        footer.addView(selectionLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnClickListener { onSelect() }
        setOnLongClickListener(null)
        chosen = chosen
    }

    fun bindManagement(
        type: TeleautoDeviceType,
        title: String,
        meta: String,
        status: String,
        active: Boolean,
        onOpen: () -> Unit,
        onLongPress: () -> Unit,
    ) {
        iconView.setImageResource(deviceIconResource(type))
        titleView.text = title
        metaView.text = meta
        statusView.text = status
        chosen = active
        selectionLabel.text = if (active) "当前设备" else "点击切换"
        selectionLabel.setTextColor(if (active) TeleautoUiTokens.TEXT else TeleautoUiTokens.TEXT_SECONDARY)
        if (selectionLabel.parent !== footer) {
            footer.removeAllViews()
            footer.addView(selectionLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        setOnClickListener { onOpen() }
        setOnLongClickListener {
            onLongPress()
            true
        }
    }

    fun bindAdd(onAdd: () -> Unit) {
        iconView.setImageResource(R.drawable.ic_add)
        titleView.text = "新增设备"
        metaView.text = "设备管理"
        statusView.text = "选择类型并创建"
        footer.removeAllViews()
        chosen = false
        selectionLabel.text = "添加"
        selectionLabel.setTextColor(TeleautoUiTokens.TEXT)
        footer.addView(selectionLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnClickListener { onAdd() }
        setOnLongClickListener(null)
    }

    private fun cardText(sizeSp: Float, color: Int) = TextView(context).apply {
        textSize = sizeSp
        setTextColor(color)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        typeface = TeleautoUiTypography.regular(context)
    }

    private fun updateBackground() {
        background = if (chosen || focused) selectedBackground else normalBackground
    }
}
