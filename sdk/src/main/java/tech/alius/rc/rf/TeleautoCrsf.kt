package tech.alius.rc.rf

/**
 * CRSF (Crossfire / TBS / ExpressLRS / mLRS) 协议常量。
 *
 * 帧格式: [sync=0xC8] [len] [type] [payload...] [crc8]
 * len = type + payload + crc(不含 sync 和 len 本身)
 * CRC8: 多项式 0xD5, 初始值 0x00, 计算 type + payload
 */
object TeleautoCrsf {
    const val SYNC_BYTE = 0xC8.toByte()
    const val CRC_POLY = 0xD5
    const val MAX_FRAME_SIZE = 64
    const val MIN_FRAME_SIZE = 4 // sync + len + type + crc

    /** 常用波特率(mLRS/ELRS 422k, TBS 420k, 兼容 115.2k) */
    object Baud {
        const val CRSF_420K = 420000
        const val CRSF_422K = 422000
        const val DEFAULT_115K = 115200
    }

    /** 帧类型 */
    object Type {
        const val GPS = 0x00
        const val VARIO = 0x01
        const val BATTERY = 0x02
        const val HEARTBEAT = 0x03
        const val LINK_STATS = 0x08
        const val RC_CHANNELS_PACKED = 0x0B
        const val SUBSET_RC_CHANNELS = 0x0C
        const val ALTITUDE = 0x10
        const val FLIGHT_MODE = 0x14
        const val SET_PGP = 0x1E
        const val PARAM_SETTINGS_ENTRY = 0x21
        const val PARAM_READ = 0x22
        const val PARAM_WRITE = 0x23
        const val PARAM_COMMAND = 0x28
        const val PARAM_COMMAND_RESPONSE = 0x2A
        const val MSP_CMD_WRITE = 0x2B
        const val DEVICE_INFO = 0x30
        const val MSP_RESPONSE = 0x7F
        const val ARDUPILOT_ALTITUDE = 0x7B
        const val ARDUPILOT_GPS = 0x7C
        const val ARDUPILOT_VARIO = 0x7D
        const val ARDUPILOT_BATTERY = 0x7E
        const val ARDUPILOT_FLIGHT_MODE = 0x7F
    }

    /** RC 通道数(CRSF 固定 16 通道 × 11bit = 22 字节 payload) */
    const val RC_CHANNEL_COUNT = 16
    const val RC_PAYLOAD_SIZE = 22
    const val RC_MIN_VALUE = 172    // CRSF 原始值域 172..1811 → 988..2012 µs
    const val RC_MAX_VALUE = 1811

    fun rawToUs(raw: Int): Int = 988 + (raw - RC_MIN_VALUE) * 1024 / (RC_MAX_VALUE - RC_MIN_VALUE)
    fun usToRaw(us: Int): Int = RC_MIN_VALUE + (us - 988) * (RC_MAX_VALUE - RC_MIN_VALUE) / 1024
}
