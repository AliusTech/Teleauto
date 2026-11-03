package tech.alius.rc.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

data class TeleautoDeviceInfoUiState(
    val title: String,
    val rows: List<Pair<String, String>>,
)

data class TeleautoInputSourceUiState(
    val id: String,
    val title: String,
    val detail: String,
    val selected: Boolean,
    val enabled: Boolean,
)

/** Shared, token-backed source selector. The host only provides data and actions. */
object TeleautoDeviceSourcePopup {
    fun show(
        anchor: View,
        device: TeleautoDeviceInfoUiState,
        sources: List<TeleautoInputSourceUiState>,
        onSourceSelected: (String) -> Unit,
        onDismiss: () -> Unit = {},
    ): PopupWindow {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        fun dp(value: Float) = (value * density).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = TeleautoMaterialAssets.card(context)
            setPadding(dp(TeleautoUiTokens.SPACE_3), dp(TeleautoUiTokens.SPACE_2), dp(TeleautoUiTokens.SPACE_3), dp(TeleautoUiTokens.SPACE_2))
        }
        root.addView(text(context, device.title, TeleautoUiTokens.TEXT, TeleautoUiTokens.TYPE_TITLE, true))
        device.rows.forEach { (label, value) ->
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(text(context, label, TeleautoUiTokens.TEXT_SECONDARY, TeleautoUiTokens.TYPE_LABEL_SMALL), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(context, value, TeleautoUiTokens.TEXT, TeleautoUiTokens.TYPE_LABEL_SMALL).apply { typeface = TeleautoUiTypography.numeric })
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(TeleautoUiTokens.SPACE_1)
            })
        }
        root.addView(View(context).apply { setBackgroundColor(TeleautoUiTokens.LINE) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(TeleautoUiTokens.LIST_DIVIDER_HEIGHT)).apply {
            topMargin = dp(TeleautoUiTokens.SPACE_2); bottomMargin = dp(TeleautoUiTokens.SPACE_2)
        })
        root.addView(text(context, "输入源", TeleautoUiTokens.TEXT_SECONDARY, TeleautoUiTokens.TYPE_LABEL_SMALL))
        val popup = PopupWindow(root, dp(TeleautoUiTokens.POPUP_WIDTH), ViewGroup.LayoutParams.WRAP_CONTENT, true)
        sources.forEach { source ->
            root.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(TeleautoUiTokens.SPACE_2), dp(TeleautoUiTokens.SPACE_2), dp(TeleautoUiTokens.SPACE_2), dp(TeleautoUiTokens.SPACE_2))
                background = if (source.selected) TeleautoMaterialAssets.panelRecessed(context) else TeleautoMaterialAssets.keycap(context)
                alpha = if (source.enabled) 1f else 0.38f
                isEnabled = source.enabled
                isFocusable = source.enabled
                addView(text(context, if (source.selected) "●" else "○", if (source.selected) TeleautoUiTokens.PRIMARY else TeleautoUiTokens.TEXT_SECONDARY, TeleautoUiTokens.TYPE_LABEL_SMALL), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(TeleautoUiTokens.SPACE_2) })
                addView(text(context, source.title, TeleautoUiTokens.TEXT, TeleautoUiTokens.TYPE_BODY, source.selected), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(text(context, source.detail, TeleautoUiTokens.TEXT_SECONDARY, TeleautoUiTokens.TYPE_META_SMALL))
                setOnClickListener { popup.dismiss(); onSourceSelected(source.id) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(TeleautoUiTokens.SPACE_1) })
        }
        popup.isOutsideTouchable = true
        popup.elevation = dp(TeleautoUiTokens.SPACE_2).toFloat()
        popup.setOnDismissListener(onDismiss)
        popup.showAtLocation(anchor, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, dp(TeleautoUiTokens.POPUP_VERTICAL_OFFSET))
        return popup
    }

    private fun text(context: Context, value: String, color: Int, size: Float, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = if (bold) TeleautoUiTypography.medium(context) else TeleautoUiTypography.regular(context)
    }
}

class TeleautoBinaryStatusView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : TextView(context, attrs) {
    fun submit(label: String, connected: Boolean) {
        text = "$label · ${if (connected) "已连接" else "未连接"}"
        setTextColor(if (connected) TeleautoUiTokens.SUCCESS else TeleautoUiTokens.TEXT_SECONDARY)
        background = if (connected) TeleautoMaterialAssets.panelRecessed(context) else TeleautoMaterialAssets.keycap(context)
    }
}

class TeleautoDeviceStatusView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : TextView(context, attrs) {
    fun submit(value: String, warning: Boolean = false) {
        text = value
        setTextColor(if (warning) TeleautoUiTokens.WARNING else TeleautoUiTokens.TEXT_SECONDARY)
    }
}
