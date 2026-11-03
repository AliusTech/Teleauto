package tech.alius.rc

import android.content.Context
import tech.alius.rc.model.TeleautoConnectionState
import tech.alius.rc.model.TeleautoHardwareProfile
import tech.alius.rc.model.TeleautoInputConnectionIssue
import tech.alius.rc.model.TeleautoRuntimeEnvironmentState
import tech.alius.rc.model.TeleautoRuntimeEnvironmentStatus
import java.util.concurrent.Executor

/**
 * Environment state follows the selected input backend's terminal result.
 * It deliberately does not race an asynchronous backend with a localhost
 * socket probe during application startup.
 */
object TeleautoRuntimeEnvironmentManager {
    fun interface Listener { fun onEnvironmentChanged(status: TeleautoRuntimeEnvironmentStatus) }
    fun interface Subscription : AutoCloseable { override fun close() }

    private val listeners = mutableListOf<Listener>()
    private var executor: Executor = TeleautoConfig.mainExecutor()
    @Volatile private var activeProfile: TeleautoHardwareProfile? = null
    @Volatile private var status = checking()

    fun getStatus(): TeleautoRuntimeEnvironmentStatus = status

    fun observe(listener: Listener): Subscription {
        synchronized(listeners) { if (!listeners.contains(listener)) listeners.add(listener) }
        executor.execute { listener.onEnvironmentChanged(status) }
        return Subscription { synchronized(listeners) { listeners.remove(listener) } }
    }

    internal fun initialize(context: Context, profile: TeleautoHardwareProfile?, callbackExecutor: Executor) {
        executor = callbackExecutor
        activeProfile = profile
        if (profile == null) {
            publish(TeleautoRuntimeEnvironmentStatus(TeleautoRuntimeEnvironmentState.UNSUPPORTED, "当前设备尚未适配"))
            return
        }
        onInputStateChanged(
            profile,
            TeleautoJoystickManager.connection == TeleautoConnectionState.CONNECTED,
            TeleautoJoystickManager.inputConnectionIssue,
        )
    }

    internal fun refresh(context: Context, profile: TeleautoHardwareProfile?) {
        onInputStateChanged(
            profile,
            TeleautoJoystickManager.connection == TeleautoConnectionState.CONNECTED,
            TeleautoJoystickManager.inputConnectionIssue,
        )
    }

    internal fun onInputStateChanged(
        profile: TeleautoHardwareProfile?,
        connected: Boolean,
        issue: TeleautoInputConnectionIssue?,
    ) {
        if (profile == null || (activeProfile != null && activeProfile != profile)) return
        activeProfile = profile
        val next = resolveInputEnvironmentStatus(connected, issue)
        publish(next)
    }

    internal fun release() {
        synchronized(listeners) { listeners.clear() }
        activeProfile = null
        status = checking()
    }

    private fun checking() = TeleautoRuntimeEnvironmentStatus(TeleautoRuntimeEnvironmentState.CHECKING, "正在启动内置输入")

    private fun preparationRequired() = TeleautoRuntimeEnvironmentStatus(
        TeleautoRuntimeEnvironmentState.PREPARATION_REQUIRED,
        "设备需要准备，请使用 Teleauto Assistant",
        ASSISTANT_PREPARE_DEVICE,
    )

    private fun publish(value: TeleautoRuntimeEnvironmentStatus) {
        if (status.state == value.state && status.userMessage == value.userMessage) return
        status = value
        val targets = synchronized(listeners) { listeners.toList() }
        executor.execute { targets.forEach { it.onEnvironmentChanged(value) } }
    }

    const val ASSISTANT_PREPARE_DEVICE = "prepare_device"
}

internal fun resolveInputEnvironmentStatus(
    connected: Boolean,
    issue: TeleautoInputConnectionIssue?,
): TeleautoRuntimeEnvironmentStatus = when {
    connected -> TeleautoRuntimeEnvironmentStatus(TeleautoRuntimeEnvironmentState.READY, "设备环境已就绪")
    issue == TeleautoInputConnectionIssue.DIRECT_INPUT_PERMISSION_REQUIRED ||
        issue == TeleautoInputConnectionIssue.ADB_TRANSPORT_PREPARATION_REQUIRED -> TeleautoRuntimeEnvironmentStatus(
            TeleautoRuntimeEnvironmentState.PREPARATION_REQUIRED,
            "设备需要准备，请使用 Teleauto Assistant",
            TeleautoRuntimeEnvironmentManager.ASSISTANT_PREPARE_DEVICE,
        )
    issue == TeleautoInputConnectionIssue.ADB_AUTHORIZATION_REQUIRED -> TeleautoRuntimeEnvironmentStatus(
        TeleautoRuntimeEnvironmentState.PREPARATION_REQUIRED,
        "请在遥控器上允许内置摇杆访问",
        TeleautoRuntimeEnvironmentManager.ASSISTANT_PREPARE_DEVICE,
    )
    else -> TeleautoRuntimeEnvironmentStatus(TeleautoRuntimeEnvironmentState.CHECKING, "正在启动内置输入")
}
