package tech.alius.rc.ui

import android.content.Context
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import tech.alius.rc.ui.TeleautoUiTokens

/**
 * A single, permanently attached video presentation layer.
 *
 * Compact and expanded modes resize the same TextureView. The Surface therefore
 * stays alive and the UDP/decoder pipeline is never restarted during expansion.
 */
class TeleautoVideoPresentationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {


    init {
        background = null
        setPadding(0, 0, 0, 0)
    }

    private val easeOut = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    private val compactBounds = RectF()
    private var anchor: View? = null
    private var root: View? = null
    private var backdrop: View? = null
    private var transitionToken = 0

    var expanded: Boolean = false
        private set
    var onExpandedChanged: ((Boolean) -> Unit)? = null

    fun bind(anchor: View, root: View, backdrop: View) {
        this.anchor = anchor
        this.root = root
        this.backdrop = backdrop
        root.post {
            updateCompactBounds()
            if (!expanded) {
                applyBounds(compactBounds)
                visibility = View.VISIBLE
            }
        }
    }

    fun expand() {
        val host = root ?: return
        if (expanded || host.width <= 0 || host.height <= 0) return
        updateCompactBounds()
        if (compactBounds.width() <= 0f || compactBounds.height() <= 0f) return

        expanded = true
        setPadding(0, 0, 0, 0)
        updateChildClipping(expanded = true)
        onExpandedChanged?.invoke(true)
        val token = ++transitionToken
        animate().cancel()
        showBackdrop(true)

        val target = expandedBounds(host.width.toFloat(), host.height.toFloat())
        val current = currentVisualBounds()
        applyBounds(target)
        pivotX = 0f
        pivotY = 0f
        scaleX = current.width() / target.width()
        scaleY = current.height() / target.height()
        translationX = current.left - target.left
        translationY = current.top - target.top
        alpha = 0.96f
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .translationY(0f)
            .alpha(1f)
            .setDuration(TeleautoUiTokens.MOTION_OVERLAY_IN)
            .setInterpolator(easeOut)
            .withEndAction {
                if (transitionToken == token && expanded) resetTransform()
            }
            .start()
    }

    fun collapse(after: (() -> Unit)? = null) {
        if (!expanded) {
            after?.invoke()
            return
        }
        expanded = false
        val token = ++transitionToken
        animate().cancel()
        updateCompactBounds()

        val layout = currentLayoutBounds()
        pivotX = 0f
        pivotY = 0f
        animate()
            .scaleX(compactBounds.width() / layout.width())
            .scaleY(compactBounds.height() / layout.height())
            .translationX(compactBounds.left - layout.left)
            .translationY(compactBounds.top - layout.top)
            .alpha(0.96f)
            .setDuration(TeleautoUiTokens.MOTION_OVERLAY_OUT)
            .setInterpolator(easeOut)
            .withEndAction {
                if (transitionToken != token || expanded) return@withEndAction
                applyBounds(compactBounds)
                updateChildClipping(expanded = false)
                resetTransform()
                showBackdrop(false)
                onExpandedChanged?.invoke(false)
                after?.invoke()
            }
            .start()
    }

    fun toggle() {
        if (expanded) collapse() else expand()
    }

    private fun updateCompactBounds() {
        val target = anchor ?: return
        val host = root ?: return
        val targetLocation = IntArray(2)
        val hostLocation = IntArray(2)
        target.getLocationOnScreen(targetLocation)
        host.getLocationOnScreen(hostLocation)
        val left = (targetLocation[0] - hostLocation[0]).toFloat()
        val top = (targetLocation[1] - hostLocation[1]).toFloat()
        compactBounds.set(left, top, left + target.width, top + target.height)
    }

    /** The product viewfinder is always 16:9, independent of the incoming stream aspect ratio. */
    private fun expandedBounds(hostWidth: Float, hostHeight: Float): RectF {
        val aspect = 16f / 9f
        val width: Float
        val height: Float
        if (hostWidth / hostHeight > aspect) {
            height = hostHeight
            width = height * aspect
        } else {
            width = hostWidth
            height = width / aspect
        }
        val left = (hostWidth - width) / 2f
        val top = (hostHeight - height) / 2f
        return RectF(left, top, left + width, top + height)
    }

    private fun currentLayoutBounds() = RectF(
        left.toFloat(),
        top.toFloat(),
        left + width.toFloat(),
        top + height.toFloat(),
    )

    private fun currentVisualBounds(): RectF {
        val layout = currentLayoutBounds()
        return RectF(
            layout.left + translationX,
            layout.top + translationY,
            layout.left + translationX + layout.width() * scaleX,
            layout.top + translationY + layout.height() * scaleY,
        )
    }

    private fun applyBounds(bounds: RectF) {
        layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
            width = bounds.width().toInt().coerceAtLeast(1)
            height = bounds.height().toInt().coerceAtLeast(1)
            leftMargin = bounds.left.toInt()
            topMargin = bounds.top.toInt()
        }
    }

    private fun resetTransform() {
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
        alpha = 1f
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        updateChildClipping(expanded = false)
    }

    /** All presentation layers share one outline so HUD and TextureView edges cannot diverge. */
    private fun updateChildClipping(expanded: Boolean) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TeleautoVideoView) {
                child.setExpandedPresentation(expanded)
            } else {
                // HUD layers are transparent and do not need Android 7's jagged
                // outline clipping; their content remains inside the video bounds.
                child.clipToOutline = false
            }
        }
    }

    private fun showBackdrop(show: Boolean) {
        val view = backdrop ?: return
        view.animate().cancel()
        if (show) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().alpha(1f)
                .setDuration(TeleautoUiTokens.MOTION_OVERLAY_IN)
                .setInterpolator(easeOut)
                .start()
        } else {
            view.animate().alpha(0f)
                .setDuration(TeleautoUiTokens.MOTION_OVERLAY_OUT)
                .setInterpolator(easeOut)
                .withEndAction { if (!expanded) view.visibility = View.GONE }
                .start()
        }
    }
}
