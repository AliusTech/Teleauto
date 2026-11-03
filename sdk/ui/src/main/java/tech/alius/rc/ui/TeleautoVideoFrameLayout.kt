package tech.alius.rc.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Shared 16:9 presentation slot. The device cover and live video occupy this
 * exact same rectangle and replace one another without an extra frame layer.
 * 子视图(TextureView 视频层 + HUD 层)以 EXACT 尺寸铺满,杜绝
 * wrap_content 被 match_parent 子视图撑爆导致视频溢出取景框。
 */
open class TeleautoVideoFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val target = (width / TeleautoUiTokens.FIGMA_VIDEO_ASPECT).toInt()
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
            minOf(target, MeasureSpec.getSize(heightMeasureSpec))
        } else if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec).coerceAtMost(target)
        } else target
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }
}
