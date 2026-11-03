package tech.alius.rc.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** Raised, read-only telemetry surface shared by video and virtual-device pages. */
class TeleautoStatusPlatformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    init {
        background = TeleautoMaterialAssets.centralPlatform(context)
        isClickable = false
        isFocusable = false
    }
}
