package tech.alius.rc.ui

import android.view.View
import android.view.animation.PathInterpolator

/** Single interruptible motion policy shared by host page transitions and overlays. */
object TeleautoUiTransitions {
    private val easeOut = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    const val presentationResumeDelayMs = TeleautoUiTokens.MOTION_OVERLAY_IN + 80L

    fun showSettings(
        left: View,
        center: View,
        right: View,
        settings: View,
        positional: Boolean,
        immediate: Boolean,
        isCurrent: () -> Boolean,
        onReady: () -> Unit,
    ) {
        cancel(settings, left, center, right)
        settings.visibility = View.VISIBLE
        settings.isEnabled = true
        if (immediate) {
            listOf(left, center, right).forEach { it.visibility = View.INVISIBLE }
            settings.alpha = 1f; settings.scaleX = 1f; settings.scaleY = 1f
            onReady(); return
        }
        val density = settings.resources.displayMetrics.density
        listOf(left, center, right).forEach { it.visibility = View.VISIBLE }
        left.animate().alpha(0f).translationX(if (positional) -TeleautoUiTokens.SPACE_6 * density else 0f)
            .setDuration(TeleautoUiTokens.MOTION_OVERLAY_OUT).setInterpolator(easeOut).start()
        center.animate().alpha(0f).translationY(if (positional) -TeleautoUiTokens.SPACE_3 * density else 0f)
            .scaleX(if (positional) 0.96f else 1f).scaleY(if (positional) 0.96f else 1f)
            .setStartDelay(24).setDuration(TeleautoUiTokens.MOTION_OVERLAY_OUT).setInterpolator(easeOut).start()
        right.animate().alpha(0f).translationX(if (positional) TeleautoUiTokens.SPACE_6 * density else 0f)
            .setStartDelay(48).setDuration(TeleautoUiTokens.MOTION_OVERLAY_OUT).setInterpolator(easeOut)
            .withEndAction { if (isCurrent()) listOf(left, center, right).forEach { it.visibility = View.INVISIBLE } }.start()
        settings.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(48)
            .setDuration(TeleautoUiTokens.MOTION_OVERLAY_IN).setInterpolator(easeOut)
            .withEndAction { if (isCurrent()) onReady() }.start()
    }

    fun hideSettings(settings: View, columns: List<View>, showDashboard: Boolean, immediate: Boolean, isCurrent: () -> Boolean) {
        cancel(settings, *columns.toTypedArray())
        if (immediate) {
            settings.visibility = View.GONE
            resetColumns(columns, showDashboard)
            return
        }
        settings.isEnabled = false
        settings.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f)
            .setDuration(TeleautoUiTokens.MOTION_OVERLAY_OUT).setInterpolator(easeOut)
            .withEndAction { if (isCurrent()) settings.visibility = View.GONE }.start()
        columns.forEachIndexed { index, view ->
            view.visibility = if (showDashboard) View.VISIBLE else View.INVISIBLE
            view.animate().alpha(1f).translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(24L * (index + 1)).setDuration(TeleautoUiTokens.MOTION_OVERLAY_IN).setInterpolator(easeOut).start()
        }
    }

    fun setOverlay(view: View, show: Boolean, translateDp: Float) {
        val offset = translateDp * view.resources.displayMetrics.density
        view.animate().cancel()
        if (show) {
            if (view.visibility != View.VISIBLE) { view.alpha = 0f; view.translationY = offset; view.visibility = View.VISIBLE }
            view.animate().alpha(1f).translationY(0f).setDuration(TeleautoUiTokens.MOTION_OVERLAY_IN).setInterpolator(easeOut).start()
        } else if (view.visibility == View.VISIBLE) {
            view.animate().alpha(0f).translationY(offset).setDuration(TeleautoUiTokens.MOTION_OVERLAY_OUT).setInterpolator(easeOut)
                .withEndAction { if (view.alpha == 0f) view.visibility = View.GONE }.start()
        }
    }

    fun cancel(vararg views: View) = views.forEach { it.animate().cancel() }

    fun resetColumns(columns: List<View>, visible: Boolean) = columns.forEach {
        it.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        it.alpha = 1f; it.translationX = 0f; it.translationY = 0f; it.scaleX = 1f; it.scaleY = 1f
    }
}
