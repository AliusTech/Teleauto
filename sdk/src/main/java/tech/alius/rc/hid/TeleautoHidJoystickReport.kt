package tech.alius.rc.hid

/**
 * 标准 joystick HID 输入报告(10 字节,与 [TeleautoHidJoystickDescriptor] 严格对应):
 *   byte 0    : Button 1-6(A/B/X/Y/LB/RB),高 2 bit padding
 *   byte 1    : 模拟 Button 7(LT),无符号 uint8
 *   byte 2    : 模拟 Button 8(RT),无符号 uint8
 *   byte 3    : Button 9-16(View/Menu/LS/RS/D-pad 上下左右)
 *   byte 4    : Button 17(Home)/18(Aux),高 6 bit padding
 *   byte 9    : continuous dial Slider,0..255 (center 128)
 *   byte 5-8  : 左摇杆 X/Y、右摇杆 Rx/Ry,无符号 uint8,128 中位
 */
data class TeleautoHidJoystickReport(
    val digitalButtonBits: Int,
    val leftTrigger: Byte,
    val rightTrigger: Byte,
    val leftStickX: Byte,
    val leftStickY: Byte,
    val rightStickX: Byte,
    val rightStickY: Byte,
    val freeDial: Byte,
) {
    /** 复用调用方缓冲写入,避免 100Hz 循环每帧分配 ByteArray。 */
    fun fillInto(buf: ByteArray) {
        buf[0] = (digitalButtonBits and 0xFF).toByte()
        buf[1] = leftTrigger
        buf[2] = rightTrigger
        buf[3] = ((digitalButtonBits ushr 8) and 0xFF).toByte()
        buf[4] = ((digitalButtonBits ushr 16) and 0x03).toByte()
        buf[5] = leftStickX
        buf[6] = leftStickY
        buf[7] = rightStickX
        buf[8] = rightStickY
        buf[9] = freeDial
    }

    fun toPayload(): ByteArray = byteArrayOf(
        (digitalButtonBits and 0xFF).toByte(),
        leftTrigger,
        rightTrigger,
        ((digitalButtonBits ushr 8) and 0xFF).toByte(),
        ((digitalButtonBits ushr 16) and 0x03).toByte(),
        leftStickX,
        leftStickY,
        rightStickX,
        rightStickY,
        freeDial,
    )

    companion object {
        val NEUTRAL = TeleautoHidJoystickReport(
            digitalButtonBits = 0,
            leftTrigger = 0,
            rightTrigger = 0,
            leftStickX = 0x80.toByte(),
            leftStickY = 0x80.toByte(),
            rightStickX = 0x80.toByte(),
            rightStickY = 0x80.toByte(),
            freeDial = 0x80.toByte(),
        )
    }
}
