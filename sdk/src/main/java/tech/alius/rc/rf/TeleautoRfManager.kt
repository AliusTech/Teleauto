package tech.alius.rc.rf

import android.content.Context
import android.hardware.usb.UsbManager
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * 射频模块管理器:管理 mLRS 高频头的 USB 连接生命周期、CRSF 数据分发、
 * 设备热插拔。与 TeleautoVideoManager 同级的 SDK 子系统。
 *
 * 用法(App 或前台服务):
 * ```
 * TeleautoRfManager.initialize(context)           // SDK 启动时
 * TeleautoRfManager.addListener(listener)        // 注册数据回调
 * // ... 自动发现设备并连接,数据经回调推送
 * TeleautoRfManager.release()                    // SDK 释放时
 * ```
 */
object TeleautoRfManager {

    @Volatile var state: TeleautoRfLinkState = TeleautoRfLinkState.DISCONNECTED
        private set

    @Volatile var enabled: Boolean = false
        private set

    val stats: TeleautoRfStats get() = link?.stats ?: TeleautoRfStats()

    private var appContext: Context? = null
    private var executor: Executor = Executors.newSingleThreadExecutor()
    private val listeners = mutableListOf<TeleautoRfLinkListener>()
    private val stateListeners = mutableListOf<TeleautoRfLinkStateListener>()
    private var link: TeleautoRfUsbLink? = null

    fun addListener(l: TeleautoRfLinkListener): Boolean = synchronized(listeners) {
        if (listeners.contains(l)) false else listeners.add(l)
    }

    fun removeListener(l: TeleautoRfLinkListener) { synchronized(listeners) { listeners.remove(l) } }

    fun addStateListener(l: TeleautoRfLinkStateListener): Boolean = synchronized(stateListeners) {
        if (stateListeners.contains(l)) false else stateListeners.add(l)
    }

    fun removeStateListener(l: TeleautoRfLinkStateListener) { synchronized(stateListeners) { stateListeners.remove(l) } }

    internal fun initialize(context: Context, callbackExecutor: Executor?) {
        appContext = context.applicationContext
        callbackExecutor?.let { executor = it }
    }

    internal fun release() {
        setEnabled(false)
        synchronized(listeners) { listeners.clear() }
        synchronized(stateListeners) { stateListeners.clear() }
        appContext = null
    }

    /** 使能射频链路:开启即自动发现 USB 设备并连接。 */
    fun setEnabled(value: Boolean) {
        if (value && !tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        if (enabled == value) return
        enabled = value
        val ctx = appContext ?: return
        if (value) {
            startLink(ctx)
        } else {
            stopLink()
        }
    }

    private fun startLink(ctx: Context) {
        stopLink()
        val combinedListener = object : TeleautoRfLinkListener {
            override fun onChannels(channels: TeleautoCrsfChannels) {
                synchronized(listeners) { listeners.toList() }.forEach { it.onChannels(channels) }
            }
            override fun onLinkStats(stats: TeleautoCrsfLinkStats) {
                setState(if (stats.downlinkLinkQuality > 0) TeleautoRfLinkState.CONNECTED else TeleautoRfLinkState.CONNECTING)
                synchronized(listeners) { listeners.toList() }.forEach { it.onLinkStats(stats) }
            }
            override fun onBattery(battery: TeleautoCrsfBattery) {
                synchronized(listeners) { listeners.toList() }.forEach { it.onBattery(battery) }
            }
            override fun onGps(gps: TeleautoCrsfGps) {
                synchronized(listeners) { listeners.toList() }.forEach { it.onGps(gps) }
            }
            override fun onFlightMode(mode: TeleautoCrsfFlightMode) {
                synchronized(listeners) { listeners.toList() }.forEach { it.onFlightMode(mode) }
            }
            override fun onRawFrame(frame: TeleautoCrsfFrame) {
                synchronized(listeners) { listeners.toList() }.forEach { it.onRawFrame(frame) }
            }
        }
        link = TeleautoRfUsbLink(ctx, combinedListener, executor).also { it.start() }
        setState(TeleautoRfLinkState.CONNECTING)
    }

    private fun stopLink() {
        link?.stop()
        link = null
        setState(TeleautoRfLinkState.DISCONNECTED)
    }

    private fun setState(newState: TeleautoRfLinkState) {
        if (state == newState) return
        state = newState
        executor.execute {
            synchronized(stateListeners) { stateListeners.toList() }
                .forEach { it.onRfLinkStateChanged(newState, null) }
        }
    }
}
