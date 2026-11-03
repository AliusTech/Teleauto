package tech.alius.rc.rf

/**
 * CRSF 帧解析后的结构化数据模型。
 */
data class TeleautoCrsfChannels(
    /** 16 通道原始值(172..1811),索引 0..15。 */
    val raw: IntArray,
) {
    /** 转换为 µs(988..2012)。 */
    fun us(index: Int): Int = TeleautoCrsf.rawToUs(raw[index])
    /** 转换为归一化 -1..1(1500µs 为零点)。 */
    fun normalized(index: Int): Float = (us(index) - 1500) / 500f
    override fun equals(other: Any?) = this === other
    override fun hashCode() = raw.contentHashCode()
}

data class TeleautoCrsfLinkStats(
    val uplinkRssi1: Int,       // dBm × -1
    val uplinkRssi2: Int,       // dBm × -1 (分集)
    val uplinkLinkQuality: Int, // 0..100
    val uplinkSnr: Int,         // dB × 10
    val activeAntenna: Int,     // 0=ant1, 1=ant2
    val rfMode: Int,            // RF mode index
    val uplinkTxPower: Int,     // mW × 100 (0=0mW, 1=10mW, ...)
    val downlinkRssi: Int,     // dBm × -1
    val downlinkLinkQuality: Int,
    val downlinkSnr: Int,
)

data class TeleautoCrsfBattery(
    val voltage: Float,    // V(0.1V 精度 × 0.1)
    val current: Float,    // A(0.1A 精度 × 0.1)
    val mAhUsed: Int,      // mAh
)

data class TeleautoCrsfGps(
    val latitude: Double,    // 度(1e7 整数 × 1e-7)
    val longitude: Double,
    val altitudeM: Float,   // 米(有符号 16bit × 100 + 1000 偏移)
    val speedKmh: Float,    // km/h(u16 × 0.1)
    val headingDeg: Float,  // 度(u16 × 0.01)
    val satelliteCount: Int,
)

data class TeleautoCrsfFlightMode(
    val name: String,
)

data class TeleautoCrsfDeviceInfo(
    val serialNumber: String,
    val name: String,
    val hardwareVersion: Long,
    val softwareVersion: Long,
    val parameterCount: Int,
)
