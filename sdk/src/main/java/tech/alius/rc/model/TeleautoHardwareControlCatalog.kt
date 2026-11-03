package tech.alius.rc.model

/**
 * DJI 遥控器硬件控件的稳定身份目录。
 *
 * Profile 只组合这里的控件并实现各自读取方式。相同物理控件跨机型必须复用同一个定义与 ID，
 * 不能因为 evdev、ADB、TCP 等采集后端不同而重新命名。
 */
object TeleautoHardwareControlCatalog {
    val LEFT_CENTERING_DIAL_01 = TeleautoCenteringDialControl(
        "LEFT_CENTERING_DIAL_01", "S1", TeleautoHardwareControlSide.LEFT, 0, TeleautoPhysicalKey.LEFT_DIAL,
    )
    val LEFT_JOYSTICK_01 = TeleautoJoystickControl(
        "LEFT_JOYSTICK_01", "Left stick", TeleautoHardwareControlSide.LEFT, 1,
        TeleautoPhysicalKey.LEFT_STICK_X, TeleautoPhysicalKey.LEFT_STICK_Y,
    )
    val RIGHT_CENTERING_DIAL_01 = TeleautoCenteringDialControl(
        "RIGHT_CENTERING_DIAL_01", "S2", TeleautoHardwareControlSide.RIGHT, 0, TeleautoPhysicalKey.RIGHT_DIAL,
    )
    val RIGHT_JOYSTICK_01 = TeleautoJoystickControl(
        "RIGHT_JOYSTICK_01", "Right stick", TeleautoHardwareControlSide.RIGHT, 1,
        TeleautoPhysicalKey.RIGHT_STICK_X, TeleautoPhysicalKey.RIGHT_STICK_Y,
    )
    val RIGHT_FIVE_WAY_01 = TeleautoFiveWayControl(
        "RIGHT_FIVE_WAY_01", "5D", TeleautoHardwareControlSide.RIGHT, 2,
        TeleautoPhysicalKey.D_UP, TeleautoPhysicalKey.D_DOWN, TeleautoPhysicalKey.D_LEFT, TeleautoPhysicalKey.D_RIGHT, TeleautoPhysicalKey.D_MIDDLE,
    )

    val RTH_BUTTON = TeleautoHoldButtonControl(
        "RTH_BUTTON", "RTH", TeleautoHardwareControlSide.LEFT, 2, TeleautoPhysicalKey.GOHOME,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.LEFT, TeleautoControlSlot.ROUND_2),
    )
    val PAUSE_BUTTON = TeleautoHoldButtonControl(
        "PAUSE_BUTTON", "Pause", TeleautoHardwareControlSide.LEFT, 3, TeleautoPhysicalKey.PAUSE,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.RIGHT, TeleautoControlSlot.ROUND_1),
    )
    val CUSTOM_BUTTON_C1 = TeleautoHoldButtonControl(
        "CUSTOM_BUTTON_C1", "C1", TeleautoHardwareControlSide.RIGHT, 4, TeleautoPhysicalKey.C1,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.LEFT, TeleautoControlSlot.LEFT_3),
    )
    val CUSTOM_BUTTON_C2 = TeleautoHoldButtonControl(
        "CUSTOM_BUTTON_C2", "C2", TeleautoHardwareControlSide.RIGHT, 5, TeleautoPhysicalKey.C2,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.RIGHT, TeleautoControlSlot.RIGHT_2),
    )
    val CUSTOM_BUTTON_C3 = TeleautoHoldButtonControl(
        "CUSTOM_BUTTON_C3", "C3", TeleautoHardwareControlSide.RIGHT, 6, TeleautoPhysicalKey.C3,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.RIGHT, TeleautoControlSlot.RIGHT_3),
    )
    val FOCUS_BUTTON = TeleautoClickButtonControl(
        "FOCUS_BUTTON", "Focus", TeleautoHardwareControlSide.RIGHT, 5, TeleautoPhysicalKey.FOCUS,
    )
    val PHOTO_BUTTON = TeleautoClickButtonControl(
        "PHOTO_BUTTON", "Photo", TeleautoHardwareControlSide.RIGHT, 6, TeleautoPhysicalKey.SHUTTER,
    )
    val RECORD_BUTTON = TeleautoClickButtonControl(
        "RECORD_BUTTON", "Record", TeleautoHardwareControlSide.RIGHT, 7, TeleautoPhysicalKey.RECORD,
    )
    val AUTH_BUTTON = TeleautoClickButtonControl(
        "AUTH_BUTTON", "Auth", TeleautoHardwareControlSide.RIGHT, 11, TeleautoPhysicalKey.AUTH_LED,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.LEFT, TeleautoControlSlot.ROUND_1),
    )

    val LEFT_HOLD_BUTTON_L1 = TeleautoHoldButtonControl(
        "LEFT_HOLD_BUTTON_L1", "L1", TeleautoHardwareControlSide.LEFT, 5, TeleautoPhysicalKey.L1,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.LEFT, TeleautoControlSlot.RIGHT_1),
    )
    val LEFT_HOLD_BUTTON_L2 = TeleautoHoldButtonControl(
        "LEFT_HOLD_BUTTON_L2", "L2", TeleautoHardwareControlSide.LEFT, 6, TeleautoPhysicalKey.L2,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.LEFT, TeleautoControlSlot.RIGHT_2),
    )
    val LEFT_HOLD_BUTTON_L3 = TeleautoHoldButtonControl(
        "LEFT_HOLD_BUTTON_L3", "L3", TeleautoHardwareControlSide.LEFT, 7, TeleautoPhysicalKey.L3,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.LEFT, TeleautoControlSlot.RIGHT_3),
    )
    val RIGHT_HOLD_BUTTON_R1 = TeleautoHoldButtonControl(
        "RIGHT_HOLD_BUTTON_R1", "R1", TeleautoHardwareControlSide.RIGHT, 8, TeleautoPhysicalKey.R1,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.RIGHT, TeleautoControlSlot.LEFT_1),
    )
    val RIGHT_HOLD_BUTTON_R2 = TeleautoHoldButtonControl(
        "RIGHT_HOLD_BUTTON_R2", "R2", TeleautoHardwareControlSide.RIGHT, 9, TeleautoPhysicalKey.R2,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.RIGHT, TeleautoControlSlot.LEFT_2),
    )
    val RIGHT_HOLD_BUTTON_R3 = TeleautoHoldButtonControl(
        "RIGHT_HOLD_BUTTON_R3", "R3", TeleautoHardwareControlSide.RIGHT, 10, TeleautoPhysicalKey.R3,
        TeleautoHardwareControlPlacement(TeleautoHardwareControlSide.RIGHT, TeleautoControlSlot.LEFT_3),
    )
    val RIGHT_CONTINUOUS_DIAL_01 = TeleautoContinuousDialControl(
        "RIGHT_CONTINUOUS_DIAL_01", "Free dial", TeleautoHardwareControlSide.RIGHT, 12, TeleautoPhysicalKey.FREE_DIAL,
    )
    val RIGHT_THREE_POSITION_SWITCH_01 = TeleautoThreePositionSwitchControl(
        "RIGHT_THREE_POSITION_SWITCH_01", "C/N/S", TeleautoHardwareControlSide.RIGHT, 13,
        listOf(TeleautoThreePositionSwitchMode.CINE, TeleautoThreePositionSwitchMode.NORMAL, TeleautoThreePositionSwitchMode.SPORT),
        listOf(TeleautoPhysicalKey.MODE_1, TeleautoPhysicalKey.MODE_2, TeleautoPhysicalKey.MODE_3),
    )

    val all: List<TeleautoHardwareControlDescriptor> = listOf(
        LEFT_CENTERING_DIAL_01, LEFT_JOYSTICK_01, RIGHT_CENTERING_DIAL_01, RIGHT_JOYSTICK_01,
        RIGHT_FIVE_WAY_01, RTH_BUTTON, PAUSE_BUTTON, CUSTOM_BUTTON_C1, CUSTOM_BUTTON_C2,
        CUSTOM_BUTTON_C3, FOCUS_BUTTON, PHOTO_BUTTON, RECORD_BUTTON, AUTH_BUTTON,
        LEFT_HOLD_BUTTON_L1, LEFT_HOLD_BUTTON_L2, LEFT_HOLD_BUTTON_L3,
        RIGHT_HOLD_BUTTON_R1, RIGHT_HOLD_BUTTON_R2, RIGHT_HOLD_BUTTON_R3,
        RIGHT_CONTINUOUS_DIAL_01, RIGHT_THREE_POSITION_SWITCH_01,
    )

    private val byId = all.associateBy(TeleautoHardwareControlDescriptor::id)
    fun get(id: String): TeleautoHardwareControlDescriptor? = byId[id]
}
