package tech.alius.rc.rf

/**
 * 射频链路抽象接口(与 video.TeleautoVideoLink 同级):
 * 不同传输(USB CDC、蓝牙 SPP、网络)实现同一接口。
 */
interface TeleautoRfLink {
    fun start()
    fun stop()
    val isConnected: Boolean
    val stats: TeleautoRfStats
}

data class TeleautoRfStats(
    val framesReceived: Long = 0,
    val crcErrors: Long = 0,
    val channelsPerSecond: Int = 0,
    val telemetryPerSecond: Int = 0,
    val lastRssi: Int = 0,
)

enum class TeleautoRfLinkState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

fun interface TeleautoRfLinkStateListener {
    fun onRfLinkStateChanged(state: TeleautoRfLinkState, detail: String?)
}

/** 射频链路回调(所有回调在创建链路时指定的 Executor 上执行)。 */
interface TeleautoRfLinkListener {
    fun onChannels(channels: TeleautoCrsfChannels) {}
    fun onLinkStats(stats: TeleautoCrsfLinkStats) {}
    fun onBattery(battery: TeleautoCrsfBattery) {}
    fun onGps(gps: TeleautoCrsfGps) {}
    fun onFlightMode(mode: TeleautoCrsfFlightMode) {}
    fun onRawFrame(frame: TeleautoCrsfFrame) {}
}
