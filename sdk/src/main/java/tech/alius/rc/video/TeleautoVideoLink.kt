package tech.alius.rc.video

/**
 * 图传链路抽象:不同传输(RTP/UDP、WFB-NG 原生)实现同一接口,
 * 上层(渲染/统计/状态)不感知传输细节。
 */
interface TeleautoVideoLink {
    /** 开始接收并解码到 [surface]。 */
    fun start(surface: android.view.Surface)

    /** 停止接收并释放资源。 */
    fun stop()

    val stats: TeleautoVideoStats
}

/** 实时统计(1Hz 刷新)。 */
data class TeleautoVideoStats(
    val framesPerSecond: Int = 0,
    val kilobitsPerSecond: Int = 0,
    /** 最近一个统计窗口（约 1 秒）的 RTP 丢包数。 */
    val lostPackets: Long = 0,
    /** 自启动起解码渲染的总帧数。 */
    val renderedFrames: Long = 0,
    /** 解码输出尺寸(格式变化时更新;未知为 0)。 */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val decoderMode: TeleautoVideoDecoderMode = TeleautoVideoDecoderMode.WAITING_KEYFRAME,
    val decoderName: String? = null,
    /** Access units submitted to MediaCodec in the latest statistics window. */
    val inputFps: Int = 0,
    /** Frames released to the Surface in the latest statistics window. */
    val renderedFps: Int = 0,
    /** Decoded pictures skipped because a newer output picture was already ready. */
    val discardedStaleOutputFrames: Long = 0,
    val droppedAccessUnits: Long = 0,
    val reorderedPackets: Long = 0,
    val latePackets: Long = 0,
    val fecRecoveredPackets: Long = 0,
    val damagedAccessUnits: Long = 0,
    val completeAccessUnits: Long = 0,
    val discardedDamagedAccessUnits: Long = 0,
    val codecFatalErrors: Long = 0,
    val codecRestarts: Long = 0,
    val lastFatalAccessUnitSequence: Long = -1,
    val fatalRecoveryMs: Long = -1,
    /** Native USB/WFB stages are reported separately until an on-wire trace id is available. */
    val usbToWfbLatencyMs: TeleautoVideoLatencySummary = TeleautoVideoLatencySummary.UNAVAILABLE,
    val wfbToRtpLatencyMs: TeleautoVideoLatencySummary = TeleautoVideoLatencySummary.UNAVAILABLE,
    val rtpToCodecLatencyMs: TeleautoVideoLatencySummary = TeleautoVideoLatencySummary.UNAVAILABLE,
    val codecOutputLatencyMs: TeleautoVideoLatencySummary = TeleautoVideoLatencySummary.UNAVAILABLE,
    val groundPipelineLatencyMs: TeleautoVideoLatencySummary = TeleautoVideoLatencySummary.UNAVAILABLE,
)

/** Bounded latency distribution in milliseconds; negative values mean unavailable. */
data class TeleautoVideoLatencySummary(
    val p50: Double = -1.0,
    val p95: Double = -1.0,
    val max: Double = -1.0,
) {
    companion object { val UNAVAILABLE = TeleautoVideoLatencySummary() }
}

enum class TeleautoVideoDecoderMode { HARDWARE, WAITING_KEYFRAME, FAILED }

enum class TeleautoVideoState { IDLE, WAITING, DECODING, ERROR }

data class TeleautoVideoReceiverConfig(
    val channel: Int = 161,
    val bandwidthMHz: Int = 20,
) {
    init {
        require(channel in 1..196) { "channel must be 1..196" }
        require(bandwidthMHz == 20 || bandwidthMHz == 40) { "bandwidth must be 20 or 40 MHz" }
    }
}

/** OpenIPC/WFB video receiver hardware state; unrelated to command-link modules. */
enum class TeleautoWfbAdapterState {
    DISABLED,
    ABSENT,
    USB_PRESENT,
    PERMISSION_REQUIRED,
    PERMISSION_GRANTED,
    INITIALIZING,
    READY,
    RECEIVING,
    ERROR_USB_PROTOCOL,
    /** Legacy states retained for source compatibility. */
    CONNECTING,
    CONNECTED,
    ERROR,
}

fun interface TeleautoWfbAdapterStateListener {
    fun onWfbAdapterStateChanged(state: TeleautoWfbAdapterState, detail: String?)
}

fun interface TeleautoVideoStateListener {
    fun onVideoStateChanged(state: TeleautoVideoState, detail: String?)
}
