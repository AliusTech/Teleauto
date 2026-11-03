package tech.alius.rc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeleautoResponsiveMetricsTest {
    @Test fun supportedResolutionsKeepTheSameControlSizesAtEveryDensity() {
        for (density in listOf(1f, 1.5f, 2f, 2.5f, 3f)) {
            val short = TeleautoResponsiveMetrics.calculateScale(1920f / density, 1080f / density)
            val tall = TeleautoResponsiveMetrics.calculateScale(1920f / density, 1280f / density)
            assertEquals("density=$density", short, tall, 0.0001f)
        }
    }

    @Test fun extraHeightOnlyChangesSpacing() {
        assertEquals(TeleautoResponsiveMetrics.DashboardSpacing(0, 0, 0), TeleautoResponsiveMetrics.dashboardSpacing(1920, 1080))
        assertEquals(TeleautoResponsiveMetrics.DashboardSpacing(50, 50, 0, 50, 50), TeleautoResponsiveMetrics.dashboardSpacing(1920, 1280))
        val odd = TeleautoResponsiveMetrics.dashboardSpacing(1920, 1281)
        assertEquals(201, odd.top + odd.dialToStick + odd.controls + odd.footerGap)
        assertEquals(0, odd.bottom)
        assertEquals(TeleautoResponsiveMetrics.DashboardSpacing(0, 0, 0), TeleautoResponsiveMetrics.dashboardSpacing(1920, 1000))
    }

    @Test fun fullSidePanelFitsWithoutShrinkingButtonsOrClippingFooter() {
        // Independently budget the XML layout in pixels, including integer rounding.
        for ((width, height) in listOf(1920 to 1080, 1920 to 1280, 1280 to 720)) {
            for (density in listOf(1f, 1.5f, 2f, 2.5f, 3f)) {
                val unit = density * TeleautoResponsiveMetrics.calculateScale(width / density, height / density)
                fun dp(value: Float) = (value * unit + .5f).toInt()
                val extra = TeleautoResponsiveMetrics.dashboardSpacing(width, height)
                val stickSide = ((width - 4 * dp(16f)) * 420f / 1760f).toInt() + 1
                val used = dp(40f) + dp(11f) + dp(21f) + dp(14f) + dp(11f) +
                    stickSide + dp(16f) + dp(196f) + extra.top + extra.dialToStick + extra.controls + extra.bottom
                assertTrue("$width×$height density=$density used=$used", used <= height)
                val lastKeyBottom = 2 * (36f + 12f) * unit + 40f * unit
                val footerTop = (196f - 40f) * unit
                assertTrue("AUTH/dial footer must clear the third button row", lastKeyBottom < footerTop)
            }
        }
    }

    @Test fun wideScreensDoNotStretchWithoutLimit() {
        assertEquals(1.35f, TeleautoResponsiveMetrics.calculateScale(1920f, 1080f), 0.0001f)
    }
}
