package tech.alius.rc.video

import com.openipc.wfbngrtl8812.WfbUsbCompatibilityProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class TeleautoWfbUsbCompatibilitySelectorTest {
    @Test
    fun `ag501 Android 7 rk30board selects RM500 workaround`() {
        assertEquals(
            WfbUsbCompatibilityProfile.RM500_ROCKCHIP,
            TeleautoWfbUsbCompatibilitySelector.select("ag501", "rk30board", 25),
        )
    }

    @Test
    fun `rm500 alias selects RM500 workaround`() {
        assertEquals(
            WfbUsbCompatibilityProfile.RM500_ROCKCHIP,
            TeleautoWfbUsbCompatibilitySelector.select("RM500", "RK30BOARD", 25),
        )
    }

    @Test
    fun `rm700 and rc701 always keep standard path`() {
        assertEquals(
            WfbUsbCompatibilityProfile.STANDARD,
            TeleautoWfbUsbCompatibilitySelector.select("rm700", "rk30board", 25),
        )
        assertEquals(
            WfbUsbCompatibilityProfile.STANDARD,
            TeleautoWfbUsbCompatibilitySelector.select("rc701", "qcom", 29),
        )
    }

    @Test
    fun `similar Rockchip devices do not receive RM500 workaround`() {
        assertEquals(
            WfbUsbCompatibilityProfile.STANDARD,
            TeleautoWfbUsbCompatibilitySelector.select("unknown", "rk30board", 25),
        )
        assertEquals(
            WfbUsbCompatibilityProfile.STANDARD,
            TeleautoWfbUsbCompatibilitySelector.select("ag501", "rk30board", 29),
        )
    }
}
