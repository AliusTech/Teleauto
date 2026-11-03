package tech.alius.rc.ui

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.widget.LinearLayout

/** Full-height settings rail. Pages add semantic navigation items but never restyle it. */
class TeleautoSettingsNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    init {
        orientation = VERTICAL
        setPadding(
            context.dp(TeleautoUiTokens.TOP_BAR_HORIZONTAL_INSET).toInt(),
            context.dp(TeleautoUiTokens.SPACE_6).toInt(),
            context.dp(TeleautoUiTokens.TOP_BAR_HORIZONTAL_INSET).toInt(),
            context.dp(TeleautoUiTokens.SPACE_4).toInt(),
        )
        background = ColorDrawable(TeleautoUiTokens.NAVIGATION_RAIL_BACKGROUND)
        isFocusable = false
    }
}

/** Generic module status card used for current and future hot-plug modules. */
class TeleautoModuleStatusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val titleView = android.widget.TextView(context).apply {
        setTextColor(TeleautoUiTokens.TEXT)
        textSize = TeleautoUiTokens.TYPE_TITLE
        typeface = TeleautoUiTypography.medium(context)
    }
    private val detailView = android.widget.TextView(context).apply {
        setTextColor(TeleautoUiTokens.TEXT_SECONDARY)
        textSize = TeleautoUiTokens.TYPE_BODY
        typeface = TeleautoUiTypography.regular(context)
    }

    init {
        orientation = VERTICAL
        setPadding(
            context.dp(TeleautoUiTokens.CARD_INSET).toInt(),
            context.dp(TeleautoUiTokens.SPACE_2).toInt(),
            context.dp(TeleautoUiTokens.CARD_INSET).toInt(),
            context.dp(TeleautoUiTokens.SPACE_2).toInt(),
        )
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(detailView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        submit(TeleautoModuleUiState("模块", TeleautoModuleUiAvailability.UNAVAILABLE))
    }

    fun submit(state: TeleautoModuleUiState) {
        titleView.text = state.batteryPercent?.let { "${state.title} · $it%" } ?: state.title
        detailView.text = state.detail
        val materialState = when (state.availability) {
            TeleautoModuleUiAvailability.AVAILABLE -> TeleautoModuleAvailabilityState.AVAILABLE
            TeleautoModuleUiAvailability.PREPARING -> TeleautoModuleAvailabilityState.PREPARING
            TeleautoModuleUiAvailability.UNAVAILABLE, TeleautoModuleUiAvailability.ERROR -> TeleautoModuleAvailabilityState.UNAVAILABLE
        }
        background = TeleautoMaterialAssets.moduleStatus(context, materialState)
        val color = when (state.availability) {
            TeleautoModuleUiAvailability.AVAILABLE -> TeleautoUiTokens.MODULE_AVAILABLE
            TeleautoModuleUiAvailability.PREPARING -> TeleautoUiTokens.MODULE_PREPARING
            TeleautoModuleUiAvailability.ERROR -> TeleautoUiTokens.DANGER
            TeleautoModuleUiAvailability.UNAVAILABLE -> TeleautoUiTokens.MODULE_UNAVAILABLE
        }
        titleView.setTextColor(color)
    }
}
