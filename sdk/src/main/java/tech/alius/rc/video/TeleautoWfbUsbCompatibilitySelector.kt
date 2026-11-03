package tech.alius.rc.video

import com.openipc.wfbngrtl8812.WfbUsbCompatibilityProfile
import java.util.Locale

/** Exact device-profile gate for USB workarounds that must never leak to RM700. */
internal object TeleautoWfbUsbCompatibilitySelector {
    fun select(product: String, hardware: String, sdkInt: Int): WfbUsbCompatibilityProfile {
        val normalizedProduct = product.lowercase(Locale.US)
        val normalizedHardware = hardware.lowercase(Locale.US)
        return if (
            sdkInt == 25 &&
            (normalizedProduct == "ag501" || normalizedProduct == "rm500") &&
            normalizedHardware == "rk30board"
        ) {
            WfbUsbCompatibilityProfile.RM500_ROCKCHIP
        } else {
            WfbUsbCompatibilityProfile.STANDARD
        }
    }
}
