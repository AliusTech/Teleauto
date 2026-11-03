package tech.alius.rc.ui

import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.min

/** Responsive scale shared by XML geometry and all Canvas widgets. */
object TeleautoResponsiveMetrics {
    private const val BASE_WIDTH_DP = TeleautoUiTokens.FIGMA_BASE_WIDTH

    /** Fullscreen consoles must not size against the transient system-bar-inset configuration. */
    @Suppress("DEPRECATION")
    fun scale(context: android.content.Context): Float {
        val metrics = android.util.DisplayMetrics()
        val manager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        manager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels / metrics.density
        val height = metrics.heightPixels / metrics.density
        return calculateScale(maxOf(width, height), minOf(width, height))
    }

    fun scale(resources: Resources): Float {
        val configuration = resources.configuration
        val width = configuration.screenWidthDp.toFloat().takeIf { it > 0f }
            ?: resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val height = configuration.screenHeightDp.toFloat().takeIf { it > 0f }
            ?: resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val longSide = maxOf(width, height)
        val shortSide = minOf(width, height)
        return calculateScale(longSide, shortSide)
    }

    fun calculateScale(longSideDp: Float, shortSideDp: Float): Float {
        // Size both supported displays against the shorter 16:9 canvas. The
        // square sticks follow column width, so reserve their actual height
        // before fitting the dials, buttons and spacing (including rounding slack).
        val referenceHeight = min(shortSideDp, longSideDp / TeleautoUiTokens.DASHBOARD_MIN_ASPECT)
        val sideFraction = TeleautoUiTokens.FIGMA_SIDE_COLUMN_WIDTH /
            (2 * TeleautoUiTokens.FIGMA_SIDE_COLUMN_WIDTH + TeleautoUiTokens.FIGMA_CENTER_COLUMN_WIDTH)
        val horizontalInsets = 2 * (TeleautoUiTokens.PAGE_INSET + TeleautoUiTokens.COLUMN_GAP)
        val fixedHeight = TeleautoUiTokens.TOP_BAR_HEIGHT + TeleautoUiTokens.DASHBOARD_TOP_INSET +
            TeleautoUiTokens.DASHBOARD_BOTTOM_INSET + TeleautoUiTokens.CENTERING_DIAL_HEIGHT +
            TeleautoUiTokens.DASHBOARD_DIAL_TO_STICK_GAP + TeleautoUiTokens.COLUMN_GAP +
            TeleautoUiTokens.HARDWARE_PANEL_HEIGHT + TeleautoUiTokens.SPACE_1
        val fit = (referenceHeight - longSideDp * sideFraction) /
            (fixedHeight - horizontalInsets * sideFraction)
        return min(longSideDp / BASE_WIDTH_DP, fit).coerceIn(0.1f, 1.35f)
    }

    data class DashboardSpacing(val top: Int, val controls: Int, val bottom: Int, val dialToStick: Int = 0, val footerGap: Int = 0)

    /** Extra height separates controls; the footer always retains the same bottom inset. */
    fun dashboardSpacing(widthPx: Int, heightPx: Int): DashboardSpacing {
        val extra = (heightPx - widthPx / TeleautoUiTokens.DASHBOARD_MIN_ASPECT).toInt().coerceAtLeast(0)
        val edge = extra / 4
        return DashboardSpacing(edge, edge, 0, edge, extra - 3 * edge)
    }
}

/** Scales only explicit XML dimensions; match/weight/wrap dimensions remain fluid. */
object TeleautoResponsiveLayout {
    /** Capture scaled base dimensions once; repeated layouts never accumulate gaps. */
    fun adaptDashboardSpacing(root: View, dashboard: View, controls: List<View>) {
        val left = dashboard.paddingLeft
        val top = dashboard.paddingTop
        val right = dashboard.paddingRight
        val bottom = dashboard.paddingBottom
        val dials = listOfNotNull(root.findViewById<View>(R.id.dialLeft), root.findViewById<View>(R.id.dialRight))
        val dialMargins = dials.map { (it.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin }
        val margins = controls.map { (it.layoutParams as ViewGroup.MarginLayoutParams).topMargin }
        fun update() {
            if (root.width == 0 || root.height == 0) return
            val extra = TeleautoResponsiveMetrics.dashboardSpacing(root.width, root.height)
            dashboard.setPadding(left, top + extra.top, right, bottom + extra.bottom)
            dials.forEachIndexed { index, view ->
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                val margin = dialMargins[index] + extra.dialToStick
                if (params.bottomMargin != margin) { params.bottomMargin = margin; view.layoutParams = params }
            }
            // The fourth share is naturally left inside the weighted controls panel,
            // above the footer pinned by TeleautoHardwarePanelLayout.
            controls.forEachIndexed { index, view ->
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                val margin = margins[index] + extra.controls
                if (params.topMargin != margin) {
                    params.topMargin = margin
                    view.layoutParams = params
                }
            }
        }
        root.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) update()
        }
        update()
    }

    fun apply(root: View, scale: Float) {
        if (scale == 1f) return
        scaleView(root, scale)
    }

    private fun scaleView(view: View, scale: Float) {
        view.layoutParams?.let { params ->
            if (params.width > 0) params.width = scaled(params.width, scale)
            if (params.height > 0) params.height = scaled(params.height, scale)
            if (params is ViewGroup.MarginLayoutParams) {
                params.leftMargin = scaled(params.leftMargin, scale)
                params.topMargin = scaled(params.topMargin, scale)
                params.rightMargin = scaled(params.rightMargin, scale)
                params.bottomMargin = scaled(params.bottomMargin, scale)
            }
            view.layoutParams = params
        }
        view.setPadding(
            scaled(view.paddingLeft, scale), scaled(view.paddingTop, scale),
            scaled(view.paddingRight, scale), scaled(view.paddingBottom, scale),
        )
        view.translationX *= scale
        view.translationY *= scale
        if (view is TextView) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, view.textSize * scale)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) scaleView(view.getChildAt(index), scale)
        }
    }

    private fun scaled(value: Int, scale: Float): Int = (value * scale + 0.5f).toInt()
}
