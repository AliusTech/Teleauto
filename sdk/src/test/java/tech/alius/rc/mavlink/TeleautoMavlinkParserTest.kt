package tech.alius.rc.mavlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import tech.alius.rc.device.*

class TeleautoMavlinkParserTest {
    @Test fun `selectable device types preserve MAVLink vehicle identities and categories`() {
        assertEquals(TeleautoDeviceType.QUADROTOR, TeleautoDeviceType.fromMavType(2))
        assertEquals(TeleautoDeviceCategory.MARINE, TeleautoDeviceType.fromMavType(12)?.category)
        assertEquals(TeleautoDeviceType.GROUND_QUADRUPED, TeleautoDeviceType.fromMavType(46))
        assertEquals(TeleautoDeviceCategory.GROUND, TeleautoDeviceType.GROUND_QUADRUPED.category)
        assertEquals(TeleautoDeviceCategory.ROBOT, TeleautoDeviceType.HUMANOID_ROBOT.category)
        assertEquals(TeleautoDeviceCategory.VIRTUAL, TeleautoDeviceType.VIRTUAL_GAMEPAD.category)
        assertEquals(TeleautoDeviceCategory.VIRTUAL, TeleautoDeviceType.VIRTUAL_JOYSTICK.category)
        assertEquals(true, TeleautoDeviceType.VIRTUAL_GAMEPAD in TeleautoDeviceType.selectable())
        assertEquals(null, TeleautoDeviceType.fromMavType(30)) // Camera is a discovered component.
    }

    @Test fun `target snapshot exposes configured capabilities`() {
        val device = TeleautoMavlinkTargetDevice(
            TeleautoDeviceType.FIXED_WING,
            TeleautoTargetDeviceConfig(capabilities = setOf(TeleautoDeviceCapability.VIDEO_TRANSMISSION)),
            ExecutorDirect,
        ) {}
        assertEquals(setOf(TeleautoDeviceCapability.VIDEO_TRANSMISSION), device.snapshot.capabilities)
        device.dispose()
    }

    @Test fun `target capabilities can be added without reconnecting device`() {
        val device = TeleautoMavlinkTargetDevice(
            TeleautoDeviceType.GROUND_ROVER,
            TeleautoTargetDeviceConfig(),
            ExecutorDirect,
        ) {}
        device.connect()
        val connectionBefore = device.snapshot.connectionState
        device.updateCapabilities(setOf(TeleautoDeviceCapability.VIDEO_TRANSMISSION))
        assertEquals(connectionBefore, device.snapshot.connectionState)
        assertEquals(setOf(TeleautoDeviceCapability.VIDEO_TRANSMISSION), device.snapshot.capabilities)
        device.dispose()
    }

    @Test fun `mavlink2 heartbeat accepts arbitrary fragmentation`() {
        val frames = mutableListOf<TeleautoMavlinkFrame>()
        val parser = TeleautoMavlinkParser(frames::add)
        val bytes = frame(0, heartbeatPayload(mavType = 1), crcExtra = 50, systemId = 7)
        bytes.forEach { parser.offer(byteArrayOf(it), 1) }
        assertEquals(1, frames.size)
        assertEquals(7, frames.single().systemId)
        assertEquals(1, TeleautoMavlinkDecoder.heartbeat(frames.single())?.mavType)
    }

    @Test fun `bad crc is discarded and next frame recovers`() {
        val frames = mutableListOf<TeleautoMavlinkFrame>()
        val parser = TeleautoMavlinkParser(frames::add)
        val bad = frame(0, heartbeatPayload(1), 50).also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val good = frame(0, heartbeatPayload(10), 50)
        val joined = bad + good
        parser.offer(joined, joined.size)
        assertEquals(1, frames.size)
        assertEquals(10, TeleautoMavlinkDecoder.heartbeat(frames.single())?.mavType)
    }

    @Test fun `fixed wing rejects rover heartbeat`() {
        val device = TeleautoMavlinkTargetDevice(TeleautoDeviceType.FIXED_WING, TeleautoTargetDeviceConfig(), ExecutorDirect) {}
        device.connect()
        val bytes = frame(0, heartbeatPayload(10), 50, systemId = 3)
        device.onBytes(bytes, bytes.size)
        assertEquals(TeleautoDeviceConnectionState.ERROR, device.snapshot.connectionState)
        val error = device.snapshot.error as TeleautoDeviceConnectionError.TypeMismatch
        assertEquals(TeleautoDeviceType.FIXED_WING, error.expected)
        assertEquals(10, error.actualMavType)
        device.disconnect()
    }

    @Test fun `rover dynamically adds imu and battery components`() {
        val device = TeleautoMavlinkTargetDevice(TeleautoDeviceType.GROUND_ROVER, TeleautoTargetDeviceConfig(), ExecutorDirect) {}
        device.connect()
        listOf(
            frame(0, heartbeatPayload(10), 50, systemId = 4),
            frame(105, imuPayload(), 93, systemId = 4),
            frame(1, sysStatusPayload(62), 124, systemId = 4),
        ).forEach { device.onBytes(it, it.size) }
        assertEquals(TeleautoDeviceConnectionState.CONNECTED, device.snapshot.connectionState)
        assertNotNull(device.snapshot.components.filterIsInstance<TeleautoImuComponent>().firstOrNull())
        assertEquals(62, device.snapshot.components.filterIsInstance<TeleautoBatteryComponent>().first().levelPercent)
        device.disconnect()
    }

    @Test fun `component information remains protocol identity instead of UI telemetry`() {
        val device = TeleautoMavlinkTargetDevice(TeleautoDeviceType.FIXED_WING, TeleautoTargetDeviceConfig(), ExecutorDirect) {}
        device.connect()
        listOf(
            frame(0, heartbeatPayload(1), 50, systemId = 7),
            frame(396, componentInformationPayload(), 50, systemId = 7),
        ).forEach { device.onBytes(it, it.size) }
        val protocol = device.snapshot.protocolComponents.single()
        assertEquals("Alius", protocol.vendorName)
        assertEquals("Fixed Wing FC", protocol.modelName)
        assertEquals("Fixed Wing FC", device.snapshot.info.displayName)
        assertEquals(1, device.snapshot.components.filterIsInstance<TeleautoAutopilotComponent>().size)
        device.dispose()
    }

    private fun heartbeatPayload(mavType: Int) = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(0, 0); put(4, mavType.toByte()); put(5, 3); put(6, 0); put(7, 4); put(8, 3)
    }.array()

    private fun imuPayload() = ByteBuffer.allocate(62).order(ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(8, 1f); putFloat(12, 2f); putFloat(16, 3f)
        putFloat(20, 4f); putFloat(24, 5f); putFloat(28, 6f); putFloat(56, 25f)
    }.array()

    private fun sysStatusPayload(level: Int) = ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN).apply {
        putShort(14, 12_000.toShort()); putShort(16, 123); put(30, level.toByte())
    }.array()

    private fun componentInformationPayload() = ByteBuffer.allocate(160).order(ByteOrder.LITTLE_ENDIAN).apply {
        putLong(0, 3L)
        "Alius".toByteArray().copyInto(array(), 16)
        "Fixed Wing FC".toByteArray().copyInto(array(), 48)
        "1.2.3".toByteArray().copyInto(array(), 80)
        "A1".toByteArray().copyInto(array(), 104)
        "SN0001".toByteArray().copyInto(array(), 128)
    }.array()

    private fun frame(messageId: Int, payload: ByteArray, crcExtra: Int, systemId: Int = 1): ByteArray {
        val result = ByteArray(12 + payload.size)
        result[0] = 0xfd.toByte(); result[1] = payload.size.toByte(); result[2] = 0; result[3] = 0
        result[4] = 1; result[5] = systemId.toByte(); result[6] = 1
        result[7] = (messageId and 0xff).toByte(); result[8] = ((messageId ushr 8) and 0xff).toByte(); result[9] = ((messageId ushr 16) and 0xff).toByte()
        payload.copyInto(result, 10)
        var crc = 0xffff
        for (index in 1 until 10 + payload.size) crc = accumulate(result[index].toInt() and 0xff, crc)
        crc = accumulate(crcExtra, crc)
        result[10 + payload.size] = crc.toByte(); result[11 + payload.size] = (crc ushr 8).toByte()
        return result
    }

    private fun accumulate(value: Int, current: Int): Int {
        var tmp = value xor (current and 0xff); tmp = tmp xor (tmp shl 4)
        return ((current ushr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp ushr 4)) and 0xffff
    }

    private object ExecutorDirect : java.util.concurrent.Executor { override fun execute(command: Runnable) = command.run() }
}
