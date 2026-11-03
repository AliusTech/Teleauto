package tech.alius.rc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import tech.alius.rc.model.TeleautoBatteryState
import tech.alius.rc.model.TeleautoBatterySource
import tech.alius.rc.model.TeleautoBatteryStatus
import java.util.concurrent.Executor

/**
 * 遥控器设备状态组件。
 *
 * RC Plus 可能包含多个电池节点；本组件使用 Android Battery Service 的聚合结果，
 * 聚合接口与系统状态栏保持一致；独立主、副电池使用固件广播，不猜测 sysfs 节点。
 */
object TeleautoRemoteControllerStatusManager {

    fun interface BatteryListener {
        fun onBatteryChanged(state: TeleautoBatteryState)
    }

    private var appContext: Context? = null
    private var callbackExecutor: Executor = TeleautoConfig.mainExecutor()
    private val listeners = mutableListOf<BatteryListener>()

    @Volatile
    private var state: TeleautoBatteryState? = null

    @Volatile private var mainBattery: TeleautoBatterySource? = null
    @Volatile private var backupBattery: TeleautoBatterySource? = null
    @Volatile private var mainBatteryAt = 0L
    @Volatile private var backupBatteryAt = 0L
    // Firmware protocol identifiers must remain exact; not public SDK names.
    private const val MAIN_ACTION = "com.dji.msg_dji_main_battery"
    private const val BACKUP_ACTION = "com.dji.msg_dji_second_battery"
    private const val SOURCE_TIMEOUT_MS = 5_000L

    /** Present battery sources only; invalid or missing percentages stay unknown. */
    fun getBatterySources(): List<TeleautoBatterySource> {
        val now = android.os.SystemClock.elapsedRealtime()
        val main = mainBattery?.takeIf { now - mainBatteryAt <= SOURCE_TIMEOUT_MS }
        val backup = backupBattery?.takeIf { now - backupBatteryAt <= SOURCE_TIMEOUT_MS }
        return buildList {
            if (main != null) {
                if (main.isPresent) add(main)
            } else state?.takeIf { it.isPresent }?.let {
                add(TeleautoBatterySource("controller", "遥控器电池", it.levelPercent, true))
            }
            backup?.takeIf { it.isPresent }?.let(::add)
        }
    }

    private val sourceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = android.os.SystemClock.elapsedRealtime()
            when (intent.action) {
                MAIN_ACTION -> {
                    mainBattery = TeleautoBatterySource("main", "主电池",
                        intent.getIntExtra("data_main_battery_percent", -1).takeIf { it in 0..100 }, true)
                    mainBatteryAt = now
                }
                BACKUP_ACTION -> {
                    backupBattery = TeleautoBatterySource("backup", "备用电池",
                        intent.getIntExtra("data_second_battery_percent", -1).takeIf { it in 0..100 },
                        intent.getBooleanExtra("data_second_battery_is_plugin", false))
                    backupBatteryAt = now
                }
                else -> return
            }
            state?.let { current ->
                synchronized(listeners) { listeners.toList() }.forEach { dispatch(it, current) }
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) update(parse(intent))
        }
    }

    /** 当前电池状态；SDK 尚未初始化且系统状态不可用时返回 null。 */
    fun getBatteryState(): TeleautoBatteryState? = state

    /** 当前聚合电量 0..100；SDK 尚未获取到电池状态时返回 -1。 */
    fun getBatteryLevel(): Int = state?.levelPercent ?: -1

    fun addBatteryListener(listener: BatteryListener): Boolean {
        val added = synchronized(listeners) {
            if (listeners.contains(listener)) false else listeners.add(listener)
        }
        if (added) state?.let { dispatch(listener, it) }
        return added
    }

    fun removeBatteryListener(listener: BatteryListener) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    @Synchronized
    internal fun initialize(context: Context, executor: Executor) {
        if (appContext != null) return
        appContext = context.applicationContext
        callbackExecutor = executor
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext!!.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext!!.registerReceiver(receiver, filter)
        }
        sticky?.let { update(parse(it)) }
        val sourceFilter = IntentFilter().apply { addAction(MAIN_ACTION); addAction(BACKUP_ACTION) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext!!.registerReceiver(sourceReceiver, sourceFilter, Context.RECEIVER_EXPORTED)
        } else {
            appContext!!.registerReceiver(sourceReceiver, sourceFilter)
        }
    }

    @Synchronized
    internal fun release() {
        appContext?.let { runCatching { it.unregisterReceiver(receiver) } }
        appContext?.let { runCatching { it.unregisterReceiver(sourceReceiver) } }
        mainBattery = null
        backupBattery = null
        mainBatteryAt = 0L
        backupBatteryAt = 0L
        appContext = null
        state = null
        synchronized(listeners) { listeners.clear() }
    }

    private fun parse(intent: Intent): TeleautoBatteryState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
        return TeleautoBatteryState(
            levelPercent = percent.coerceIn(0, 100),
            status = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> TeleautoBatteryStatus.CHARGING
                BatteryManager.BATTERY_STATUS_DISCHARGING -> TeleautoBatteryStatus.DISCHARGING
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> TeleautoBatteryStatus.NOT_CHARGING
                BatteryManager.BATTERY_STATUS_FULL -> TeleautoBatteryStatus.FULL
                else -> TeleautoBatteryStatus.UNKNOWN
            },
            isPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true),
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            temperatureTenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0),
        )
    }

    private fun update(next: TeleautoBatteryState) {
        val previous = state
        state = next
        if (previous?.copy(timestampMs = next.timestampMs) == next) return
        Log.d(TAG, "battery level=${next.levelPercent}% status=${next.status} voltage=${next.voltageMv}mV")
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { dispatch(it, next) }
    }

    private fun dispatch(listener: BatteryListener, value: TeleautoBatteryState) {
        runCatching { callbackExecutor.execute { listener.onBatteryChanged(value) } }
    }

    private const val TAG = "TeleautoSdk-Status"
}
