package tech.alius.rc.hid

/**
 * BLE HID Report Map(Gamepad 输出布局,自 alius_rc 项目真机验证的版本移植)。
 *
 * RC Plus 系统镜像关闭了 Android HID Device profile(profile_supported_hid_device=false,
 * 且厂商 overlay 不可变更),因此蓝牙手柄模式走**纯应用层 BLE HID over GATT(HOGP)**,
 * 不依赖 com.android.bluetooth 的 HidDeviceService。
 *
 * 输入报告布局(Report ID 1,10 字节):
 *   byte 0     : 数字按钮 1-6 + 2 bit padding
 *   byte 1-2  : 模拟按钮 7/8(LT/RT),霍尔扳机驱动 0..255
 *   byte 3    : 数字按钮 9-16(View/Menu/LS/RS/D-pad 上下左右)
 *   byte 4    : 数字按钮 17(Home,由 L1 驱动)+ 7 bit padding
 *   byte 5-8  : 左摇杆 X/Y、右摇杆 Rx/Ry(0..255,128 中位)
 *   byte 9    : 三段飞行模式 Slider(0/127/255)
 *
 * 轴的 usage 分配遵循 Gamepad API / macOS GameController 常见约定:
 * 左摇杆 X/Y、右摇杆 Rx/Ry;LT/RT 使用带模拟值的 Button 7/8;
 * D-pad 使用 Button 13-16,直接对齐 Gamepad API buttons[12..15]。
 *
 * Output report(Report ID 2):接收 host dual-rumble(遥控器不震动,静默忽略)。
 */
object TeleautoHidJoystickDescriptor {

    enum class DeviceKind(val applicationUsage: Int, val appearance: Int) {
        JOYSTICK(0x04, 0x03C3),
        GAMEPAD(0x05, 0x03C4),
    }

    const val REPORT_ID: Byte = 0x01
    const val OUTPUT_REPORT_ID: Byte = 0x02

    fun bleHidInformation(): ByteArray = byteArrayOf(
        0x11,
        0x01,
        0x00,
        0x03, // RemoteWake + NormallyConnectable
    )

    fun bleReportMap(reportId: Byte, kind: DeviceKind = DeviceKind.GAMEPAD): ByteArray =
        reportDescriptor(reportId, kind)

    private fun reportDescriptor(reportId: Byte?, kind: DeviceKind): ByteArray = buildList {
        addAll(0x05, 0x01) // Usage Page (Generic Desktop)
        addAll(0x09, kind.applicationUsage) // Usage (Joystick or Game Pad)
        addAll(0xA1, 0x01) // Collection (Application)
        if (reportId != null) {
            addAll(0x85, reportId.toInt() and 0xFF) // Report ID
        }
        // ---- byte 0: 数字按钮 1-6(A/B/X/Y/LB/RB) + 2 bit padding ----
        addAll(0x05, 0x09) // Usage Page (Button)
        addAll(0x19, 0x01) // Usage Minimum (Button 1)
        addAll(0x29, 0x06) // Usage Maximum (Button 6)
        addAll(0x15, 0x00) // Logical Minimum (0)
        addAll(0x25, 0x01) // Logical Maximum (1)
        addAll(0x75, 0x01) // Report Size (1)
        addAll(0x95, 0x06) // Report Count (6)
        addAll(0x81, 0x02) // Input (Data, Var, Abs)
        addAll(0x75, 0x02)
        addAll(0x95, 0x01)
        addAll(0x81, 0x03) // byte 0 padding
        // ---- byte 1-2: 模拟 LT/RT(Button 7/8),0..255 ----
        if (kind == DeviceKind.JOYSTICK) {
            addAll(0x05, 0x01, 0x09, 0x32, 0x09, 0x35) // Z/Rz signed-position dials
        } else {
            addAll(0x05, 0x09, 0x19, 0x07, 0x29, 0x08) // Xbox LT/RT
        }
        addAll(0x15, 0x00) // Logical Minimum (0)
        addAll(0x26, 0xFF, 0x00) // Logical Maximum (255)
        addAll(0x75, 0x08) // Report Size (8)
        addAll(0x95, 0x02) // Report Count (2)
        addAll(0x81, 0x02) // Input (Data, Var, Abs)
        // ---- byte 3: Button 9-16(View/Menu/LS/RS/D-pad) ----
        addAll(0x05, 0x09) // Usage Page (Button)
        addAll(0x19, 0x09) // Usage Minimum (Button 9)
        addAll(0x29, 0x10) // Usage Maximum (Button 16)
        addAll(0x15, 0x00) // Logical Minimum (0)
        addAll(0x25, 0x01) // Logical Maximum (1)
        addAll(0x75, 0x01) // Report Size (1)
        addAll(0x95, 0x08) // Report Count (8)
        addAll(0x81, 0x02) // Input (Data, Var, Abs)
        // ---- byte 4: Button 17(Home) + 7 bit padding ----
        addAll(0x19, 0x11, 0x29, 0x12) // Buttons 17-18: Home / auxiliary
        addAll(0x95, 0x02) // Report Count (2)
        addAll(0x81, 0x02) // Input (Data, Var, Abs)
        addAll(0x75, 0x06) // Six padding bits
        addAll(0x95, 0x01) // One padding field
        addAll(0x81, 0x03) // Input (Const, Var, Abs) padding
        // ---- byte 5-8: 摇杆轴 X/Y/Rx/Ry,0..255 ----
        addAll(0x05, 0x01) // Usage Page (Generic Desktop)
        addAll(0x09, 0x30) // Usage (X)  — 左摇杆横向
        addAll(0x09, 0x31) // Usage (Y)  — 左摇杆纵向
        addAll(0x09, 0x33) // Usage (Rx) — 右摇杆横向
        addAll(0x09, 0x34) // Usage (Ry) — 右摇杆纵向
        addAll(0x15, 0x00) // Logical Minimum (0)
        addAll(0x26, 0xFF, 0x00) // Logical Maximum (255)
        addAll(0x35, 0x00) // Physical Minimum (0)
        addAll(0x46, 0xFF, 0x00) // Physical Maximum (255)
        addAll(0x75, 0x08) // Report Size (8)
        addAll(0x95, 0x04) // Report Count (4)
        addAll(0x81, 0x02) // Input (Data, Var, Abs)
        // byte 9: independent latched continuous dial. No mode-switch channel.
        addAll(0x09, 0x36, 0x95, 0x01, 0x81, 0x02)
        // ---- Output report(Report ID 2):dual-rumble ----
        addAll(0x85, 0x02) // Report ID (2)
        addAll(0x06, 0x00, 0xFF) // Usage Page (Vendor Defined 0xFF00)
        addAll(0x09, 0x01) // Usage (Vendor 1)
        addAll(0x15, 0x00) // Logical Minimum (0)
        addAll(0x26, 0xFF, 0x00) // Logical Maximum (255)
        addAll(0x75, 0x08) // Report Size (8)
        addAll(0x95, 0x02) // Report Count (2) — strongMagnitude, weakMagnitude
        addAll(0x91, 0x02) // Output (Data, Var, Abs)
        add(0xC0) // End Collection
    }.map { it.toByte() }.toByteArray()

    private fun MutableList<Int>.addAll(vararg values: Int) {
        for (value in values) {
            add(value)
        }
    }
}
