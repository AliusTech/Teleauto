package tech.alius.rc.ui

import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoControlSlot
import tech.alius.rc.model.TeleautoThreePositionSwitchMode

/** Immutable presentation state accepted by the public UI components. */
data class TeleautoJoystickUiState(
    val x: Float = 0f,
    val y: Float = 0f,
    val trimX: Float = 0f,
    val trimY: Float = 0f,
)

data class TeleautoDialUiState(
    val label: String,
    val position: Float,
)

data class TeleautoHardwareControlsUiState(
    val sections: List<TeleautoHardwareControlSection>,
    val heldKeys: Set<TeleautoPhysicalKey> = emptySet(),
)

data class TeleautoHardwareControlSlot(
    val key: TeleautoPhysicalKey,
    val slot: TeleautoControlSlot,
)

data class TeleautoStatusBarUiState(
    val deviceName: String,
    val remoteBatteryPercent: Int? = null,
    val transmitBatteryPercent: Int? = null,
    val mode: TeleautoThreePositionSwitchMode = TeleautoThreePositionSwitchMode.UNKNOWN,
    val locked: Boolean = false,
)

enum class TeleautoModuleUiAvailability { UNAVAILABLE, PREPARING, AVAILABLE, ERROR }

data class TeleautoModuleUiState(
    val title: String,
    val availability: TeleautoModuleUiAvailability,
    val detail: String = "",
    val batteryPercent: Int? = null,
)

data class TeleautoVideoUiState(
    val hasSource: Boolean = false,
    val streaming: Boolean = false,
    val recording: Boolean = false,
    val expanded: Boolean = false,
    val status: String = "",
    val diagnostics: String = "",
)

sealed interface TeleautoUiAction {
    data object OpenSettings : TeleautoUiAction
    data object ToggleLock : TeleautoUiAction
    data object ToggleVideoStream : TeleautoUiAction
    data object ToggleVideoExpansion : TeleautoUiAction
    data object FocusVideo : TeleautoUiAction
    data object CapturePhoto : TeleautoUiAction
    data object ToggleRecording : TeleautoUiAction
    data class HardwareKey(val key: TeleautoPhysicalKey) : TeleautoUiAction
}
