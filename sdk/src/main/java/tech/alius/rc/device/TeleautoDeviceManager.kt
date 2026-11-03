package tech.alius.rc.device

import tech.alius.rc.TeleautoRemoteControllerStatusManager
import tech.alius.rc.TeleautoJoystickManager
import tech.alius.rc.TeleautoConfig
import tech.alius.rc.TeleautoTransmitModuleManager
import tech.alius.rc.model.TeleautoCenteringDialControl
import tech.alius.rc.model.TeleautoConnectionState
import tech.alius.rc.model.TeleautoContinuousDialControl
import tech.alius.rc.model.TeleautoFiveWayControl
import tech.alius.rc.model.TeleautoPhysicalKey
import tech.alius.rc.model.TeleautoStickSnapshot
import tech.alius.rc.model.TeleautoThreePositionSwitchState
import tech.alius.rc.model.TeleautoTransmitModuleState
import tech.alius.rc.usb.TeleautoByteStreamListener
import tech.alius.rc.usb.TeleautoCdcMavlinkTransport
import tech.alius.rc.video.TeleautoVideoTransmissionManager
import tech.alius.rc.video.TeleautoVideoState
import tech.alius.rc.video.TeleautoWfbAdapterState
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Internal registry behind the single public [tech.alius.rc.TeleautoSdk] facade. */
internal object TeleautoDeviceManager {
    private lateinit var context: android.content.Context
    private val devices = linkedMapOf<TeleautoDeviceId, TeleautoMutableDevice>()
    private val listeners = mutableListOf<TeleautoDeviceListObserver>()
    private var executor: Executor = TeleautoConfig.mainExecutor()
    private var remote: TeleautoMutableDevice? = null
    private var target: TeleautoMutableDevice? = null
    private var joystickSubscription: TeleautoJoystickManager.Subscription? = null
    private var transmitSubscription: TeleautoTransmitModuleManager.Subscription? = null
    private var videoSubscription: TeleautoVideoTransmissionManager.Subscription? = null
    private val batteryListener = TeleautoRemoteControllerStatusManager.BatteryListener { rebuildRemoteComponents() }
    private val listNotificationLock = Any()
    private var listNotificationPending = false
    private var latestDeviceList = emptyList<TeleautoDeviceSnapshot>()

    fun initialize(context: android.content.Context, callbackExecutor: Executor) {
        if (!tech.alius.rc.license.TeleautoLicenseRuntime.isUsable()) return
        this.context = context.applicationContext
        executor = callbackExecutor
        if (remote != null) return
        val rcInfo = TeleautoJoystickManager.getDeviceInfo() ?: return
        remote = TeleautoMutableDevice(
            id = TeleautoDeviceId("remote-controller:${rcInfo.modelCode}"),
            type = TeleautoDeviceType.REMOTE_CONTROLLER,
            connectionMode = TeleautoDeviceConnectionMode.AUTOMATIC,
            initialInfo = TeleautoDeviceInfo(
                displayName = rcInfo.displayName,
                manufacturer = rcInfo.manufacturer,
                model = rcInfo.modelCode,
                firmwareVersion = rcInfo.firmwareVersion,
                remoteControllerInfo = rcInfo,
            ),
            executor = executor,
            onChange = ::onDeviceChanged,
        ).also {
            devices[it.id] = it
            it.updateConnection(mapConnection(TeleautoJoystickManager.connection))
            rebuildRemoteComponents(it, TeleautoJoystickManager.getStickSnapshot(), TeleautoJoystickManager.getKeyStates().filterValues { value -> value }.keys, TeleautoJoystickManager.getThreePositionSwitchState())
        }
        joystickSubscription = TeleautoJoystickManager.observe(object : TeleautoJoystickManager.Observer {
            override fun onStateChanged(state: TeleautoConnectionState) {
                remote?.updateConnection(mapConnection(state)); rebuildRemoteComponents()
            }
            override fun onStickChanged(snapshot: TeleautoStickSnapshot) = updateRemoteInputFrame(snapshot)
            override fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) = rebuildRemoteComponents(held = held)
            override fun onThreePositionSwitchChanged(state: TeleautoThreePositionSwitchState) = rebuildRemoteComponents(switch = state)
        })
        TeleautoRemoteControllerStatusManager.addBatteryListener(batteryListener)
        transmitSubscription = TeleautoTransmitModuleManager.observe { rebuildRemoteComponents() }
        videoSubscription = TeleautoVideoTransmissionManager.observeVideoTransmissionModules { rebuildRemoteComponents() }
        notifyList()
    }

    fun supportedTypes(): List<TeleautoDeviceType> = listOf(TeleautoDeviceType.REMOTE_CONTROLLER) + TeleautoDeviceType.selectable()
    fun snapshots(): List<TeleautoDeviceSnapshot> = synchronized(devices) { devices.values.map { it.snapshot } }
    fun remoteController(): TeleautoDevice? = remote
    fun activeTarget(): TeleautoDevice? = target

    fun create(type: TeleautoDeviceType, config: TeleautoTargetDeviceConfig): TeleautoDevice {
        require(type != TeleautoDeviceType.REMOTE_CONTROLLER) { "Remote controller is created automatically" }
        target?.let { remove(it.id) }
        val device = if (type.isVirtualHid) {
            require(config.capabilities.isEmpty()) { "Virtual HID devices do not support target capabilities" }
            TeleautoVirtualHidDevice(type, executor, ::onDeviceChanged)
        } else if (type == TeleautoDeviceType.CYBERBRICK) {
            require(config.capabilities.isEmpty()) { "CyberBrick does not support MAVLink/video capabilities" }
            TeleautoCyberBrickDevice(context, executor, ::onDeviceChanged)
        } else {
            require(type.mavType != null) { "Unsupported target device type: $type" }
            TeleautoTransmitModuleManager.configureTarget(config)
            TeleautoMavlinkTargetDevice(type, config, executor, ::onDeviceChanged)
        }
        target = device
        synchronized(devices) { devices[device.id] = device }
        notifyList()
        return device
    }

    fun remove(id: TeleautoDeviceId): Boolean {
        if (remote?.id == id) return false
        val removed = synchronized(devices) { devices.remove(id) } ?: return false
        when (removed) {
            is TeleautoMavlinkTargetDevice -> removed.dispose()
            is TeleautoVirtualHidDevice -> removed.dispose()
            else -> removed.disconnect()
        }
        if (target === removed) target = null
        notifyList()
        return true
    }

    /** Updates host-selected target capabilities without interrupting its MAVLink session. */
    fun updateCapabilities(id: TeleautoDeviceId, capabilities: Set<TeleautoDeviceCapability>): Boolean {
        val device = synchronized(devices) { devices[id] } ?: return false
        if (device === remote || device.type == TeleautoDeviceType.REMOTE_CONTROLLER) return false
        if (device.type.isBluetoothTarget && capabilities.isNotEmpty()) return false
        device.updateCapabilities(capabilities)
        return true
    }

    fun observe(observer: TeleautoDeviceListObserver): TeleautoDeviceSubscription {
        synchronized(listeners) { listeners.add(observer) }
        executor.execute { observer.onDevicesChanged(snapshots()) }
        return TeleautoDeviceSubscription { synchronized(listeners) { listeners.remove(observer) } }
    }

    fun release() {
        joystickSubscription?.close(); joystickSubscription = null
        transmitSubscription?.close(); transmitSubscription = null
        videoSubscription?.close(); videoSubscription = null
        TeleautoRemoteControllerStatusManager.removeBatteryListener(batteryListener)
        synchronized(devices) { devices.values.toList() }.forEach {
            when (it) {
                is TeleautoMavlinkTargetDevice -> it.dispose()
                is TeleautoVirtualHidDevice -> it.dispose()
                else -> it.disconnect()
            }
        }
        synchronized(devices) { devices.clear() }
        synchronized(listeners) { listeners.clear() }
        synchronized(listNotificationLock) {
            listNotificationPending = false
            latestDeviceList = emptyList()
        }
        remote = null
        target = null
    }

    private fun rebuildRemoteComponents(
        device: TeleautoMutableDevice? = remote,
        snapshot: TeleautoStickSnapshot = TeleautoJoystickManager.getStickSnapshot(),
        held: Set<TeleautoPhysicalKey> = TeleautoJoystickManager.getKeyStates().filterValues { it }.keys,
        switch: TeleautoThreePositionSwitchState = TeleautoJoystickManager.getThreePositionSwitchState(),
    ) {
        val target = device ?: return
        val info = TeleautoJoystickManager.getDeviceInfo() ?: return
        val state = mapComponentConnection(TeleautoJoystickManager.connection)
        val components = mutableListOf<TeleautoDeviceComponent>()
        components += TeleautoRemoteControllerComponent(connectionState = state, info = info)
        TeleautoRemoteControllerStatusManager.getBatteryState()?.let {
            components += TeleautoBatteryComponent(TeleautoComponentId("remote-controller-battery"), levelPercent = it.levelPercent, voltageMv = it.voltageMv, sourceState = it)
        }
        components += TeleautoJoystickComponent(connectionState = state, snapshot = snapshot)
        components += TeleautoButtonComponent(connectionState = state, supported = info.supportedInputs, held = held, descriptors = info.hardwareControls)
        info.hardwareControls.filterIsInstance<TeleautoFiveWayControl>().firstOrNull()?.let {
            components += TeleautoFiveWayButtonComponent(connectionState = state, held = held.intersect(it.inputs))
        }
        info.hardwareControls.filterIsInstance<TeleautoCenteringDialControl>().forEach { dial ->
            components += TeleautoDialComponent(TeleautoComponentId(dial.id), TeleautoComponentType.CENTERING_DIAL, state, snapshot.axis(dial.axis))
        }
        info.hardwareControls.filterIsInstance<TeleautoContinuousDialControl>().forEach { dial ->
            components += TeleautoDialComponent(TeleautoComponentId(dial.id), TeleautoComponentType.CONTINUOUS_DIAL, state, snapshot.axis(dial.axis))
        }
        info.threePositionSwitch?.let { components += TeleautoThreePositionSwitchComponent(connectionState = state, state = switch) }
        TeleautoTransmitModuleManager.getTransmitModules().forEach { module ->
            components += TeleautoTransmitModuleComponent(
                id = TeleautoComponentId(module.id),
                connectionState = when (module.state) {
                    TeleautoTransmitModuleState.CONNECTING -> TeleautoComponentConnectionState.CONNECTING
                    TeleautoTransmitModuleState.CONNECTED -> TeleautoComponentConnectionState.CONNECTED
                    TeleautoTransmitModuleState.ERROR -> TeleautoComponentConnectionState.ERROR
                },
                displayName = module.displayName,
                hardwareId = module.hardwareId,
                firmwareVersion = module.firmwareVersion,
                batteryLevelPercent = module.batteryLevelPercent,
            )
        }
        TeleautoVideoTransmissionManager.getVideoTransmissionModules().forEach { module ->
            components += TeleautoVideoReceiverComponent(
                TeleautoComponentId(module.id),
                when (module.state) {
                    TeleautoWfbAdapterState.READY, TeleautoWfbAdapterState.RECEIVING, TeleautoWfbAdapterState.CONNECTED -> TeleautoComponentConnectionState.CONNECTED
                    TeleautoWfbAdapterState.ERROR, TeleautoWfbAdapterState.ERROR_USB_PROTOCOL -> TeleautoComponentConnectionState.ERROR
                    TeleautoWfbAdapterState.DISABLED, TeleautoWfbAdapterState.ABSENT -> TeleautoComponentConnectionState.DISCONNECTED
                    else -> TeleautoComponentConnectionState.CONNECTING
                },
                module.displayName,
                module.detail,
            )
        }
        if (TeleautoVideoTransmissionManager.state != TeleautoVideoState.IDLE) {
            val stats = TeleautoVideoTransmissionManager.stats
            components += TeleautoVideoTransmissionComponent(
                connectionState = when (TeleautoVideoTransmissionManager.state) {
                    TeleautoVideoState.DECODING -> TeleautoComponentConnectionState.CONNECTED
                    TeleautoVideoState.IDLE -> TeleautoComponentConnectionState.DISCONNECTED
                    else -> TeleautoComponentConnectionState.CONNECTING
                },
                inputFps = stats.inputFps.toFloat(),
                renderedFps = stats.renderedFps.toFloat(),
                width = stats.videoWidth,
                height = stats.videoHeight,
            )
        }
        target.replaceComponents(components)
    }

    private fun onDeviceChanged(device: TeleautoMutableDevice) {
        notifyList()
    }

    private fun notifyList() {
        if (synchronized(listeners) { listeners.isEmpty() }) return
        synchronized(listNotificationLock) {
            latestDeviceList = snapshots()
            if (listNotificationPending) return
            listNotificationPending = true
        }
        executor.execute(::deliverLatestDeviceList)
    }

    private fun deliverLatestDeviceList() {
        val value = synchronized(listNotificationLock) { latestDeviceList }
        val targets = synchronized(listeners) { listeners.toList() }
        targets.forEach { it.onDevicesChanged(value) }
        val repeat = synchronized(listNotificationLock) {
            if (latestDeviceList === value) {
                listNotificationPending = false
                false
            } else true
        }
        if (repeat) executor.execute(::deliverLatestDeviceList)
    }

    private fun updateRemoteInputFrame(snapshot: TeleautoStickSnapshot) {
        val device = remote ?: return
        val connection = mapComponentConnection(TeleautoJoystickManager.connection)
        val info = TeleautoJoystickManager.getDeviceInfo() ?: return
        val replacements = mutableListOf<TeleautoDeviceComponent>(TeleautoJoystickComponent(connectionState = connection, snapshot = snapshot))
        info.hardwareControls.filterIsInstance<TeleautoCenteringDialControl>().forEach { dial ->
            replacements += TeleautoDialComponent(TeleautoComponentId(dial.id), TeleautoComponentType.CENTERING_DIAL, connection, snapshot.axis(dial.axis))
        }
        info.hardwareControls.filterIsInstance<TeleautoContinuousDialControl>().forEach { dial ->
            replacements += TeleautoDialComponent(TeleautoComponentId(dial.id), TeleautoComponentType.CONTINUOUS_DIAL, connection, snapshot.axis(dial.axis))
        }
        device.replaceComponentSubset(replacements)
    }

    private fun mapConnection(state: TeleautoConnectionState) = when (state) {
        TeleautoConnectionState.CONNECTED -> TeleautoDeviceConnectionState.CONNECTED
        TeleautoConnectionState.CONNECTING -> TeleautoDeviceConnectionState.CONNECTING
        TeleautoConnectionState.DISCONNECTED -> TeleautoDeviceConnectionState.DISCONNECTED
    }
    private fun mapComponentConnection(state: TeleautoConnectionState) = when (state) {
        TeleautoConnectionState.CONNECTED -> TeleautoComponentConnectionState.CONNECTED
        TeleautoConnectionState.CONNECTING -> TeleautoComponentConnectionState.CONNECTING
        TeleautoConnectionState.DISCONNECTED -> TeleautoComponentConnectionState.DISCONNECTED
    }
    private fun TeleautoStickSnapshot.axis(key: TeleautoPhysicalKey) = when (key) {
        TeleautoPhysicalKey.LEFT_DIAL -> leftDial
        TeleautoPhysicalKey.RIGHT_DIAL -> rightDial
        TeleautoPhysicalKey.FREE_DIAL -> freeDial
        else -> 0f
    }
}

internal open class TeleautoMutableDevice(
    final override val id: TeleautoDeviceId,
    final override val type: TeleautoDeviceType,
    final override val connectionMode: TeleautoDeviceConnectionMode,
    initialInfo: TeleautoDeviceInfo,
    protected val executor: Executor,
    private val onChange: (TeleautoMutableDevice) -> Unit,
    capabilities: Set<TeleautoDeviceCapability> = emptySet(),
) : TeleautoDevice {
    private val observers = mutableListOf<TeleautoDeviceObserver>()
    @Volatile private var current = TeleautoDeviceSnapshot(
        id, type, connectionMode, TeleautoDeviceConnectionState.CREATED, initialInfo, emptyList(), capabilities,
    )
    override val snapshot: TeleautoDeviceSnapshot get() = current
    override fun connect() {}
    override fun disconnect() { updateConnection(TeleautoDeviceConnectionState.DISCONNECTED) }
    override fun observe(observer: TeleautoDeviceObserver): TeleautoDeviceSubscription {
        synchronized(observers) { observers.add(observer) }
        executor.execute { observer.onDeviceChanged(current) }
        return TeleautoDeviceSubscription { synchronized(observers) { observers.remove(observer) } }
    }
    fun updateConnection(state: TeleautoDeviceConnectionState, error: TeleautoDeviceConnectionError? = null) = update(current.copy(connectionState = state, error = error))
    fun updateCapabilities(capabilities: Set<TeleautoDeviceCapability>) = update(current.copy(capabilities = capabilities))
    fun replaceComponents(components: List<TeleautoDeviceComponent>) = update(current.copy(components = components))
    fun replaceComponentSubset(replacements: List<TeleautoDeviceComponent>) {
        val ids = replacements.mapTo(hashSetOf()) { it.id }
        update(current.copy(components = current.components.filterNot { it.id in ids } + replacements))
    }
    protected fun updateInfo(info: TeleautoDeviceInfo) = update(current.copy(info = info))
    protected fun upsertProtocolComponent(component: TeleautoMavlinkProtocolComponentInfo) {
        val next = current.protocolComponents.filterNot {
            it.systemId == component.systemId && it.componentId == component.componentId
        } + component
        update(current.copy(protocolComponents = next))
    }
    protected fun clearProtocolComponents() = update(current.copy(protocolComponents = emptyList()))
    protected fun upsert(component: TeleautoDeviceComponent) {
        val next = current.components.filterNot { it.id == component.id } + component
        update(current.copy(components = next))
    }
    protected fun update(value: TeleautoDeviceSnapshot) {
        current = value.copy(updatedAtMs = System.currentTimeMillis())
        val targets = synchronized(observers) { observers.toList() }
        executor.execute { targets.forEach { it.onDeviceChanged(current) } }
        onChange(this)
    }
}

internal class TeleautoMavlinkTargetDevice(
    type: TeleautoDeviceType,
    private val config: TeleautoTargetDeviceConfig,
    executor: Executor,
    onChange: (TeleautoMutableDevice) -> Unit,
) : TeleautoMutableDevice(
    TeleautoDeviceId("target:${type.name.lowercase()}"), type, TeleautoDeviceConnectionMode.MANUAL,
    TeleautoDeviceInfo(displayName = type.displayName), executor, onChange, config.capabilities,
), TeleautoByteStreamListener {
    private val parser = tech.alius.rc.mavlink.TeleautoMavlinkParser(::onFrame)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "djirc-heartbeat").apply { isDaemon = true } }
    private var timeout: ScheduledFuture<*>? = null
    private var targetSystemId: Int? = null
    private var targetComponentId: Int? = null
    private var connected = false
    private val requestedComponentInformation = mutableSetOf<Int>()

    override fun connect() {
        if (connected) return
        connected = true
        updateConnection(TeleautoDeviceConnectionState.CONNECTING, if (TeleautoCdcMavlinkTransport.connected) null else TeleautoDeviceConnectionError.TransportUnavailable)
        TeleautoCdcMavlinkTransport.addListener(this)
    }

    override fun disconnect() {
        connected = false
        timeout?.cancel(false); timeout = null
        TeleautoCdcMavlinkTransport.removeListener(this)
        parser.reset()
        targetSystemId = null
        targetComponentId = null
        requestedComponentInformation.clear()
        replaceComponents(emptyList())
        clearProtocolComponents()
        super.disconnect()
    }

    override fun onBytes(bytes: ByteArray, length: Int) { if (connected) parser.offer(bytes, length) }
    override fun onTransportClosed() {
        if (!connected) return
        targetSystemId = null
        targetComponentId = null
        requestedComponentInformation.clear()
        replaceComponents(emptyList())
        clearProtocolComponents()
        updateConnection(TeleautoDeviceConnectionState.DISCONNECTED, TeleautoDeviceConnectionError.TransportUnavailable)
    }

    private fun onFrame(frame: tech.alius.rc.mavlink.TeleautoMavlinkFrame) {
        if (targetSystemId != null && frame.systemId != targetSystemId) return
        val heartbeat = tech.alius.rc.mavlink.TeleautoMavlinkDecoder.heartbeat(frame)
        if (heartbeat != null) {
            val firstHeartbeat = targetSystemId == null
            if (firstHeartbeat) {
                // Only an autopilot heartbeat may bind the requested target type. Peripheral
                // component heartbeats are recorded after the target system is established.
                if (heartbeat.autopilot == MAV_AUTOPILOT_INVALID) return
                if (heartbeat.mavType != type.mavType) {
                    updateConnection(TeleautoDeviceConnectionState.ERROR, TeleautoDeviceConnectionError.TypeMismatch(type, heartbeat.mavType, frame.systemId))
                    return
                }
                targetSystemId = frame.systemId
                targetComponentId = frame.componentId
                updateInfo(snapshot.info.copy(displayName = "${type.displayName} · System ${frame.systemId}", systemId = frame.systemId, componentId = frame.componentId))
                updateConnection(TeleautoDeviceConnectionState.CONNECTED)
                TeleautoCdcMavlinkTransport.write(tech.alius.rc.mavlink.TeleautoMavlinkEncoder.requestMessage(frame.systemId, frame.componentId, 148))
            }
            if (frame.systemId != targetSystemId) return
            val existingProtocol = snapshot.protocolComponents.firstOrNull {
                it.systemId == frame.systemId && it.componentId == frame.componentId
            }
            upsertProtocolComponent(
                TeleautoMavlinkProtocolComponentInfo(
                    systemId = frame.systemId,
                    componentId = frame.componentId,
                    mavType = heartbeat.mavType,
                    autopilotType = heartbeat.autopilot,
                    vendorName = existingProtocol?.vendorName,
                    modelName = existingProtocol?.modelName,
                    softwareVersion = existingProtocol?.softwareVersion,
                    hardwareVersion = existingProtocol?.hardwareVersion,
                    serialNumber = existingProtocol?.serialNumber,
                    capabilities = existingProtocol?.capabilities,
                )
            )
            if (requestedComponentInformation.add(frame.componentId)) {
                TeleautoCdcMavlinkTransport.write(tech.alius.rc.mavlink.TeleautoMavlinkEncoder.requestMessage(frame.systemId, frame.componentId, 396))
            }
            if (frame.componentId == targetComponentId) {
                upsert(TeleautoAutopilotComponent(TeleautoComponentId("mavlink:${frame.componentId}"), systemId = frame.systemId,
                    componentId = frame.componentId, mavType = heartbeat.mavType, autopilotType = heartbeat.autopilot,
                    baseMode = heartbeat.baseMode, customMode = heartbeat.customMode, systemStatus = heartbeat.systemStatus))
                scheduleTimeout()
            }
            return
        }
        if (frame.systemId != targetSystemId) return
        tech.alius.rc.mavlink.TeleautoMavlinkDecoder.autopilotVersion(frame)?.let { version ->
            updateInfo(snapshot.info.copy(vendorId = version.vendorId, productId = version.productId,
                firmwareVersion = version.flightSoftwareVersion.toString(), uid = version.uid.toString()))
            val current = snapshot.components.filterIsInstance<TeleautoAutopilotComponent>().firstOrNull()
            if (current != null) upsert(current.copy(capabilities = version.capabilities))
        }
        tech.alius.rc.mavlink.TeleautoMavlinkDecoder.componentInformationBasic(frame)?.let { information ->
            val previous = snapshot.protocolComponents.firstOrNull {
                it.systemId == frame.systemId && it.componentId == frame.componentId
            }
            upsertProtocolComponent(
                TeleautoMavlinkProtocolComponentInfo(
                    systemId = frame.systemId,
                    componentId = frame.componentId,
                    mavType = previous?.mavType ?: 0,
                    autopilotType = previous?.autopilotType ?: MAV_AUTOPILOT_INVALID,
                    vendorName = information.vendorName,
                    modelName = information.modelName,
                    softwareVersion = information.softwareVersion,
                    hardwareVersion = information.hardwareVersion,
                    serialNumber = information.serialNumber,
                    capabilities = information.capabilities,
                )
            )
            if (frame.componentId == targetComponentId) {
                updateInfo(snapshot.info.copy(
                    displayName = information.modelName?.takeIf(String::isNotBlank)
                        ?: "${type.displayName} · System ${frame.systemId}",
                    manufacturer = information.vendorName,
                    model = information.modelName,
                    firmwareVersion = information.softwareVersion ?: snapshot.info.firmwareVersion,
                    uid = information.serialNumber ?: snapshot.info.uid,
                ))
            }
        }
        tech.alius.rc.mavlink.TeleautoMavlinkDecoder.battery(frame)?.let {
            upsert(TeleautoBatteryComponent(TeleautoComponentId("vehicle-battery"), levelPercent = it.levelPercent, voltageMv = it.voltageMv, currentMa = it.currentMa, temperatureC = it.temperatureC))
        }
        tech.alius.rc.mavlink.TeleautoMavlinkDecoder.gps(frame)?.let {
            upsert(TeleautoGpsComponent(fixType = it.fixType, satellitesVisible = it.satellites, horizontalAccuracyM = it.ephM, verticalAccuracyM = it.epvM))
            upsert(TeleautoPositionComponent(latitudeDegrees = it.latitude, longitudeDegrees = it.longitude, altitudeM = it.altitudeM))
        }
        tech.alius.rc.mavlink.TeleautoMavlinkDecoder.position(frame)?.let {
            upsert(TeleautoPositionComponent(latitudeDegrees = it.latitude, longitudeDegrees = it.longitude, altitudeM = it.altitudeM,
                relativeAltitudeM = it.relativeAltitudeM, velocityNorthMps = it.northMps, velocityEastMps = it.eastMps,
                velocityDownMps = it.downMps, headingDegrees = it.headingDegrees))
        }
        tech.alius.rc.mavlink.TeleautoMavlinkDecoder.attitude(frame)?.let {
            upsert(TeleautoAttitudeComponent(rollDegrees = it.rollDegrees, pitchDegrees = it.pitchDegrees, yawDegrees = it.yawDegrees,
                rollRate = it.rollRate, pitchRate = it.pitchRate, yawRate = it.yawRate))
        }
        tech.alius.rc.mavlink.TeleautoMavlinkDecoder.imu(frame)?.let {
            upsert(TeleautoImuComponent(accelerationX = it.ax, accelerationY = it.ay, accelerationZ = it.az,
                gyroX = it.gx, gyroY = it.gy, gyroZ = it.gz, temperatureC = it.temperatureC))
        }
        tech.alius.rc.mavlink.TeleautoMavlinkDecoder.radio(frame)?.let {
            upsert(TeleautoControlLinkComponent(localRssi = it.rssi, remoteRssi = it.remoteRssi, txBufferPercent = it.txBuffer,
                receiveErrors = it.rxErrors, correctedPackets = it.fixed))
        }
    }

    private fun scheduleTimeout() {
        timeout?.cancel(false)
        timeout = scheduler.schedule({
            if (connected) {
                targetSystemId = null
                targetComponentId = null
                requestedComponentInformation.clear()
                replaceComponents(emptyList())
                clearProtocolComponents()
                updateConnection(TeleautoDeviceConnectionState.DISCONNECTED, TeleautoDeviceConnectionError.HeartbeatTimeout)
            }
        }, config.heartbeatTimeoutMs, TimeUnit.MILLISECONDS)
    }

    fun dispose() {
        disconnect()
        scheduler.shutdownNow()
    }

    private companion object {
        const val MAV_AUTOPILOT_INVALID = 8
    }
}

internal class TeleautoVirtualHidDevice(
    type: TeleautoDeviceType,
    executor: Executor,
    onChange: (TeleautoMutableDevice) -> Unit,
) : TeleautoMutableDevice(
    TeleautoDeviceId("virtual:${type.name.lowercase()}"),
    type,
    TeleautoDeviceConnectionMode.MANUAL,
    TeleautoDeviceInfo(displayName = type.displayName, manufacturer = "AliusTech"),
    executor,
    onChange,
) {
    private val kind = when (type) {
        TeleautoDeviceType.VIRTUAL_GAMEPAD -> TeleautoVirtualHidKind.GAMEPAD
        TeleautoDeviceType.VIRTUAL_JOYSTICK -> TeleautoVirtualHidKind.JOYSTICK
        else -> error("Not a virtual HID type: $type")
    }
    private var previouslyConnected = false
    private var subscription: TeleautoJoystickManager.Subscription? = null

    override fun connect() {
        previouslyConnected = false
        if (subscription != null) {
            updateConnection(TeleautoDeviceConnectionState.CONNECTING)
            return
        }
        updateConnection(TeleautoDeviceConnectionState.CONNECTING)
        replaceComponents(listOf(component(TeleautoJoystickManager.hidMode)))
        subscription = TeleautoJoystickManager.observe(object : TeleautoJoystickManager.Observer {
            override fun onHidStateChanged(mode: TeleautoJoystickManager.HidMode, detail: String?) {
                val state = when (mode) {
                    TeleautoJoystickManager.HidMode.CONNECTED -> TeleautoDeviceConnectionState.CONNECTED
                    TeleautoJoystickManager.HidMode.ERROR -> TeleautoDeviceConnectionState.ERROR
                    TeleautoJoystickManager.HidMode.OFF -> TeleautoDeviceConnectionState.DISCONNECTED
                    TeleautoJoystickManager.HidMode.ADVERTISING -> if (previouslyConnected) TeleautoDeviceConnectionState.DISCONNECTED else TeleautoDeviceConnectionState.CONNECTING
                    else -> TeleautoDeviceConnectionState.CONNECTING
                }
                if (state == TeleautoDeviceConnectionState.CONNECTED) previouslyConnected = true
                updateConnection(state, if (state == TeleautoDeviceConnectionState.ERROR) TeleautoDeviceConnectionError.Protocol(detail ?: "BLE HID unavailable") else null)
                replaceComponents(listOf(component(mode)))
            }
        })
        TeleautoJoystickManager.virtualHidKind = kind
        TeleautoJoystickManager.mode = TeleautoJoystickManager.Mode.BLE_HID
    }

    override fun disconnect() {
        subscription?.close()
        subscription = null
        TeleautoJoystickManager.mode = TeleautoJoystickManager.Mode.CONTROLLER
        replaceComponents(listOf(component(TeleautoJoystickManager.HidMode.OFF)))
        super.disconnect()
    }

    fun dispose() = disconnect()

    private fun component(mode: TeleautoJoystickManager.HidMode) = TeleautoVirtualHidComponent(
        connectionState = when (mode) {
            TeleautoJoystickManager.HidMode.CONNECTED -> TeleautoComponentConnectionState.CONNECTED
            TeleautoJoystickManager.HidMode.ERROR -> TeleautoComponentConnectionState.ERROR
            TeleautoJoystickManager.HidMode.OFF -> TeleautoComponentConnectionState.DISCONNECTED
            else -> TeleautoComponentConnectionState.CONNECTING
        },
        kind = kind,
        displayName = type.displayName,
    )
}
