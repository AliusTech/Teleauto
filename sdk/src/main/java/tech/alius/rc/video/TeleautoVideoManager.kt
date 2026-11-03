package tech.alius.rc.video

import android.content.Context
import android.view.Surface
import java.util.concurrent.Executor
import tech.alius.rc.usb.TeleautoUsbModuleAdapter

/**
 * 图传组件门面:管理链路生命周期(随 [tech.alius.rc.TeleautoSdk] initialize/release)、
 * Surface 挂载与使能开关。RTP/UDP 传输先行,WFB-NG(8812AU 用户态)后接同一接口。
 */
object TeleautoVideoTransmissionManager {
    fun interface ModuleListener {
        /** 当前实际接入的图传模块；模块拔出时回调空列表。 */
        fun onVideoTransmissionModulesChanged(modules: List<TeleautoVideoTransmissionModuleInfo>)
    }

    fun interface Subscription : AutoCloseable {
        override fun close()
    }

    @Volatile var state: TeleautoVideoState = TeleautoVideoState.IDLE
        private set

    @Volatile var enabled: Boolean = false
        private set

    @Volatile var decodingPaused: Boolean = false
        private set

    @Volatile var wfbAdapterState: TeleautoWfbAdapterState = TeleautoWfbAdapterState.DISABLED
        private set

    @Volatile var wfbAdapterDetail: String? = null
        private set

    @Volatile var port: Int = 5600
        private set

    val stats: TeleautoVideoStats get() = link?.stats ?: TeleautoVideoStats()

    fun getReceiverConfig(): TeleautoVideoReceiverConfig = TeleautoVideoReceiverConfig(wfbChannel, wfbBandwidth)

    /** Applies receiver radio settings without rebuilding the video Surface. */
    fun configureReceiver(config: TeleautoVideoReceiverConfig) {
        wfbChannel = config.channel
        wfbBandwidth = config.bandwidthMHz
        wfbReceiver?.configure(config)
    }

    @Volatile var recording: Boolean = false
        private set

    /** Starts lossless local MP4 recording from the compressed video stream. */
    fun startRecording(outputPath: String): Boolean {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return false
        if (state != TeleautoVideoState.DECODING || outputPath.isBlank()) return false
        val started = link?.startRecording(outputPath) == true
        if (started) recording = true
        return started
    }

    /** Finalizes the current MP4. Returns false when no playable sample was written. */
    fun stopRecording(): Boolean {
        val saved = link?.stopRecording() == true
        recording = false
        return saved
    }

    @Volatile var latencyDiagnosticsEnabled: Boolean = false
        private set

    /** Enables five-second aggregate pipeline logs. It never changes decode behavior. */
    fun setLatencyDiagnosticsEnabled(enabled: Boolean) {
        latencyDiagnosticsEnabled = enabled
        link?.diagnosticsEnabled = enabled
    }

    /** Physically discovered VT modules only. An absent optional adapter is omitted. */
    fun getVideoTransmissionModules(): List<TeleautoVideoTransmissionModuleInfo> {
        if (wfbAdapterState == TeleautoWfbAdapterState.DISABLED || wfbAdapterState == TeleautoWfbAdapterState.ABSENT) {
            return emptyList()
        }
        return listOf(
            TeleautoVideoTransmissionModuleInfo(
                id = "openipc-wfb",
                type = TeleautoVideoTransmissionModuleType.OPENIPC_WFB_RECEIVER,
                displayName = "图传接收端",
                state = wfbAdapterState,
                detail = wfbAdapterDetail,
            )
        )
    }

    private var appContext: Context? = null
    private var executor: Executor = tech.alius.rc.TeleautoConfig.mainExecutor()
    private val listeners = mutableListOf<TeleautoVideoStateListener>()
    private val adapterListeners = mutableListOf<TeleautoWfbAdapterStateListener>()
    private val moduleListeners = mutableListOf<ModuleListener>()
    private var link: TeleautoRtpUdpVideoLink? = null
    private var wfbReceiver: TeleautoWfbVideoReceiver? = null
    private var wfbEnabled = true
    private var wfbChannel = 161
    private var wfbBandwidth = 20
    @Volatile private var surface: Surface? = null

    fun addStateListener(l: TeleautoVideoStateListener): Boolean = synchronized(listeners) {
        if (listeners.contains(l)) false else listeners.add(l)
    }

    fun removeStateListener(l: TeleautoVideoStateListener) { synchronized(listeners) { listeners.remove(l) } }

    fun addWfbAdapterStateListener(l: TeleautoWfbAdapterStateListener): Boolean {
        val added = synchronized(adapterListeners) {
            if (adapterListeners.contains(l)) false else adapterListeners.add(l)
        }
        if (added) executor.execute { l.onWfbAdapterStateChanged(wfbAdapterState, wfbAdapterDetail) }
        return added
    }

    fun removeWfbAdapterStateListener(l: TeleautoWfbAdapterStateListener) {
        synchronized(adapterListeners) { adapterListeners.remove(l) }
    }

    /**
     * 订阅图传设备热插拔与工作状态。订阅后立即返回当前快照；同一物理模块
     * 初始化状态变化时更新条目，拔出时返回空列表。
     */
    fun observeVideoTransmissionModules(listener: ModuleListener): Subscription {
        synchronized(moduleListeners) {
            if (!moduleListeners.contains(listener)) moduleListeners.add(listener)
        }
        executor.execute { listener.onVideoTransmissionModulesChanged(getVideoTransmissionModules()) }
        return Subscription { synchronized(moduleListeners) { moduleListeners.remove(listener) } }
    }

    internal fun initialize(context: Context, cfg: tech.alius.rc.TeleautoConfig) {
        val firstInitialization = appContext == null
        appContext = context.applicationContext
        executor = cfg.callbackExecutor
        port = cfg.videoPort
        wfbEnabled = cfg.wfbVideoEnabled
        wfbChannel = cfg.wfbChannel
        wfbBandwidth = cfg.wfbBandwidth
        if (wfbEnabled && wfbReceiver == null) {
            wfbReceiver = createWfbReceiver(context.applicationContext).also { it.start() }
        }
        // Activity and foreground service may initialize the SDK concurrently.
        // A later default-false config must not switch off a link explicitly
        // enabled by the UI and persisted user preference.
        if (cfg.videoEnabled) setEnabled(true) else if (firstInitialization) setEnabled(false)
    }

    internal fun release() {
        setEnabled(false)
        wfbReceiver?.stop()
        wfbReceiver = null
        setWfbAdapterState(TeleautoWfbAdapterState.DISABLED, null)
        synchronized(listeners) { listeners.clear() }
        synchronized(adapterListeners) { adapterListeners.clear() }
        synchronized(moduleListeners) { moduleListeners.clear() }
        appContext = null
    }

    internal fun usbAdapter(): TeleautoUsbModuleAdapter? = wfbReceiver?.usbAdapter

    /**
     * 挂载/更换渲染 Surface。
     *
     * Surface 是 Activity/View 生命周期资源，而 WFB USB 接收端是 SDK 生命周期资源。
     * Android 7 会在窗口重建、前后台切换等场景短暂销毁 TextureView；此时只停止
     * UDP/MediaCodec 显示链路，必须保持 RTL8812 已初始化状态，否则 RM500 无法在同一
     * USB attach generation 中可靠地再次执行硬件初始化。
     */
    fun setSurface(s: Surface?) {
        surface = s
        if (s == null) {
            if (recording) stopRecording()
            link?.stop(); link = null; setState(TeleautoVideoState.IDLE, null)
        } else if (enabled) {
            val l = link
            if (l == null) startLink() else l.setSurface(s)
        }
    }

    /** 当前生效的渲染 Surface(供视图判断归属,避免误停链路)。 */
    val currentSurface: Surface? get() = surface

    /** 使能开关:开启即按 [port] 收流。 */
    fun setEnabled(value: Boolean) {
        if (value && !tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        if (enabled == value) return
        enabled = value
        if (value) {
            surface?.let { startLink() } ?: setState(TeleautoVideoState.WAITING, "等待渲染表面")
        } else {
            if (recording) stopRecording()
            link?.stop(); link = null; setState(TeleautoVideoState.IDLE, null)
            // USB discovery belongs to the SDK lifecycle. Disabling rendering
            // must not discard the receiver or break subsequent hot-plug recovery.
        }
    }

    /** Pause only MediaCodec rendering while transport and WFB reception stay live. */
    fun setDecodingPaused(paused: Boolean) {
        if (decodingPaused == paused) return
        decodingPaused = paused
        link?.setDecodingPaused(paused)
        tech.alius.rc.TeleautoRemoteControlStatusManager.refresh()
    }

    private fun startLink() {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        link?.stop()
        link = TeleautoRtpUdpVideoLink(port) { s, d -> setState(s, d) }.also {
            it.diagnosticsEnabled = latencyDiagnosticsEnabled
            it.start(surface!!)
            if (decodingPaused) it.setDecodingPaused(true)
        }
        if (wfbEnabled) {
            // Receiver is created during SDK initialization so discovery is
            // independent from Surface and decoder state.
        } else {
            setWfbAdapterState(TeleautoWfbAdapterState.DISABLED, null)
        }
    }

    private fun createWfbReceiver(context: Context) =
        TeleautoWfbVideoReceiver(context, wfbChannel, wfbBandwidth) { adapterState, detail ->
            setWfbAdapterState(adapterState, detail)
            // USB 接收端离线时必须离开 DECODING，通知宿主遮住 Surface 中
            // MediaCodec 保留的最后一帧；重新接入后由新码流再次进入 DECODING。
            when (adapterState) {
                TeleautoWfbAdapterState.DISABLED -> Unit
                TeleautoWfbAdapterState.READY,
                TeleautoWfbAdapterState.RECEIVING,
                TeleautoWfbAdapterState.CONNECTED -> if (state != TeleautoVideoState.DECODING) setState(TeleautoVideoState.WAITING, detail)
                else -> setState(TeleautoVideoState.WAITING, detail)
            }
        }

    private fun setWfbAdapterState(s: TeleautoWfbAdapterState, detail: String?) {
        if (wfbAdapterState == s && wfbAdapterDetail == detail) return
        wfbAdapterState = s
        wfbAdapterDetail = detail
        val snapshot = synchronized(adapterListeners) { adapterListeners.toList() }
        executor.execute { snapshot.forEach { it.onWfbAdapterStateChanged(s, detail) } }
        val modules = getVideoTransmissionModules()
        val moduleSnapshot = synchronized(moduleListeners) { moduleListeners.toList() }
        executor.execute {
            moduleSnapshot.forEach { it.onVideoTransmissionModulesChanged(modules) }
        }
    }

    private fun setState(s: TeleautoVideoState, d: String?) {
        if (state == s && d == stateDetail) return
        state = s
        stateDetail = d
        val ls = synchronized(listeners) { listeners.toList() }
        executor.execute { ls.forEach { it.onVideoStateChanged(s, d) } }
    }

    @Volatile private var stateDetail: String? = null
}
