package tech.alius.rc.flutter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import io.flutter.view.TextureRegistry
import tech.alius.rc.*
import tech.alius.rc.device.*
import tech.alius.rc.model.*
import tech.alius.rc.license.*
import tech.alius.rc.video.TeleautoVideoTransmissionManager

/** Android implementation of the platform-neutral Dart API. Never implements UI. */
class TeleautoCorePlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler, ActivityAware, PluginRegistry.RequestPermissionsResultListener {
    private lateinit var context: Context
    private lateinit var channel: MethodChannel
    private lateinit var events: EventChannel
    private lateinit var textures: TextureRegistry
    private var activityBinding: ActivityPluginBinding? = null
    private var sink: EventChannel.EventSink? = null
    private var inputSubscription: TeleautoJoystickManager.Subscription? = null
    private var deviceSubscription: TeleautoDeviceSubscription? = null
    private var texture: TextureRegistry.SurfaceTextureEntry? = null
    private var surface: Surface? = null
    private var permissionResult: MethodChannel.Result? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingFrame = false
    private val poll = object : Runnable { override fun run() { if (!detached && TeleautoSdk.isInitialized) { scheduleState(); handler.postDelayed(this, 500) } } }
    private var locked = false
    private var unlockProgress = 0f
    private var detached = false
    private val frame = Runnable { pendingFrame = false; emitState() }
    private val observer = object : TeleautoJoystickManager.Observer {
        override fun onStickChanged(snapshot: TeleautoStickSnapshot) = scheduleState()
        override fun onHeldKeysChanged(held: Set<TeleautoPhysicalKey>) = scheduleState()
        override fun onKeyEvent(event: TeleautoRcKeyEvent) {
            emit(mapOf("event" to "key", "key" to event.key.name, "action" to event.action.name))
            scheduleState()
        }
        override fun onThreePositionSwitchChanged(state: TeleautoThreePositionSwitchState) = scheduleState()
        override fun onStateChanged(state: TeleautoConnectionState) = scheduleState()
        override fun onScreenLockStateChanged(value: Boolean, progress: Float) {
            locked = value; unlockProgress = progress; scheduleState()
        }
        override fun onHidStateChanged(mode: TeleautoJoystickManager.HidMode, detail: String?) = scheduleState()
    }
    private val batteryListener = TeleautoRemoteControllerStatusManager.BatteryListener { scheduleState() }
    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext; textures = binding.textureRegistry
        channel = MethodChannel(binding.binaryMessenger, "tech.alius.teleauto/core")
        events = EventChannel(binding.binaryMessenger, "tech.alius.teleauto/events")
        channel.setMethodCallHandler(this); events.setStreamHandler(this)
        detached = false
    }
    override fun onListen(arguments: Any?, eventSink: EventChannel.EventSink) { sink = eventSink; emitState() }
    override fun onCancel(arguments: Any?) { sink = null }
    private fun emit(value: Map<String, Any?>) { if (!detached) sink?.success(value) }
    private fun scheduleState() {
        if (!pendingFrame && !detached) { pendingFrame = true; handler.postDelayed(frame, 16) }
    }
    private fun emitState() = emit(mapOf("event" to "state", "state" to state()))
    private fun state(): Map<String, Any?> {
        val s = TeleautoJoystickManager.getStickSnapshot()
        val target = TeleautoSdk.getActiveTargetDevice()?.snapshot
        val license = TeleautoLicenseManager.status
        return mapOf(
            "sdkVersion" to TeleautoSdk.VERSION, "platform" to "android", "initialized" to TeleautoSdk.isInitialized,
            "license" to license.error.name, "deviceName" to (TeleautoSdk.getDeviceInfo()?.displayName ?: Build.MODEL),
            "battery" to TeleautoRemoteControllerStatusManager.getBatteryLevel().takeIf { it >= 0 },
            "batteries" to (TeleautoRemoteControllerStatusManager.getBatterySources().map {
                mapOf("id" to it.id, "name" to it.displayName, "level" to it.levelPercent, "present" to it.isPresent)
            } + TeleautoTransmitModuleManager.getTransmitModules().map {
                mapOf("id" to "transmitter:${it.id}", "name" to "高频头 · ${it.displayName}",
                    "level" to it.batteryLevelPercent?.takeIf { level -> level in 0..100 }, "present" to true)
            }),
            "axes" to listOf(s.leftX,s.leftY,s.rightX,s.rightY,s.leftDial,s.rightDial,s.freeDial),
            "inputSources" to TeleautoJoystickManager.getAvailableInputSources().map { mapOf("id" to it.id,"name" to it.displayName,"description" to it.description,"enabled" to it.enabled,"selected" to it.selected) },
            "held" to TeleautoJoystickManager.getKeyStates().filterValues { it }.keys.map { it.name },
            "mode" to TeleautoJoystickManager.getThreePositionSwitchState().mode.label,
            "locked" to locked, "unlockProgress" to unlockProgress,
            "connection" to TeleautoJoystickManager.connection.name, "hid" to TeleautoJoystickManager.hidMode.name,
            "hidHostName" to TeleautoJoystickManager.hidHostName, "hidDeviceName" to TeleautoJoystickManager.hidDeviceName,
            "videoState" to TeleautoVideoTransmissionManager.state.name,
            "recording" to TeleautoVideoTransmissionManager.recording,
            "videoEnabled" to TeleautoVideoTransmissionManager.enabled,
            "videoChannel" to TeleautoVideoTransmissionManager.getReceiverConfig().channel,
            "videoBandwidth" to TeleautoVideoTransmissionManager.getReceiverConfig().bandwidthMHz,
            "modules" to (TeleautoTransmitModuleManager.getTransmitModules().map { mapOf("name" to it.displayName,"status" to it.state.name,"kind" to "transmitter") } + TeleautoVideoTransmissionManager.getVideoTransmissionModules().map { mapOf("name" to it.displayName,"status" to it.state.name,"kind" to "video") }),
            "targetType" to target?.type?.name, "targetState" to target?.connectionState?.name,
            "targetName" to target?.info?.displayName,
            "receivers" to target?.cyberBrick?.receivers?.map { mapOf("name" to it.name,"address" to it.address,"rssi" to it.rssi) }.orEmpty(),
            "receiverMessage" to target?.cyberBrick?.message,
            "devices" to TeleautoSdk.getDevices().filter { it.type != TeleautoDeviceType.REMOTE_CONTROLLER }.map {
                mapOf("id" to it.id.value,"name" to it.info.displayName,"type" to it.type.name,"connection" to it.connectionState.name)
            },
        )
    }
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            if (call.method !in setOf("initialize","state","capabilities","release","requestBluetoothPermissions","releaseVideoTexture","stopRecording") &&
                TeleautoJoystickManager.checkScreenInput() != TeleautoScreenInputResult.ACCEPTED) {
                result.error("INPUT_BLOCKED", TeleautoJoystickManager.checkScreenInput().name, null); return
            }
            when (call.method) {
                "capabilities" -> result.success(mapOf("platform" to "android", "hardwareInput" to true, "video" to true, "bluetoothHid" to (Build.VERSION.SDK_INT >= 29)))
                "state" -> result.success(state())
                "initialize" -> {
                    detachObservers(); releaseTexture()
                    val ok = TeleautoSdk.initialize(context, TeleautoConfig(
                        licenseVendorId = call.argument<String>("vendorId"),
                        licenseListener = TeleautoLicenseListener { scheduleState() },
                        videoEnabled = call.argument<Boolean>("videoEnabled") ?: true,
                    ))
                    inputSubscription = TeleautoJoystickManager.observe(observer)
                    deviceSubscription = TeleautoSdk.observeDevices { scheduleState() }
                    TeleautoRemoteControllerStatusManager.addBatteryListener(batteryListener)
                    android.util.Log.i("TeleautoFlutter", "sdk=${TeleautoSdk.VERSION} initialized=$ok license=${TeleautoLicenseManager.status.error}")
                    handler.removeCallbacks(poll); if(ok)handler.post(poll)
                    result.success(state() + ("initialized" to ok)); scheduleState()
                }
                "submitKey" -> {
                    val key = TeleautoPhysicalKey.valueOf(requireNotNull(call.argument<String>("key")))
                    val action = TeleautoKeyAction.valueOf(requireNotNull(call.argument<String>("action")))
                    result.success(TeleautoJoystickManager.injectKeyEvent(key, action).name)
                }
                "setDial" -> result.success(TeleautoJoystickManager.submitFreeDialValue(requireNotNull(call.argument<Double>("value")).toFloat()).name)
                "lock" -> { TeleautoJoystickManager.setScreenLocked(true); result.success(null) }
                "selectInputSource" -> { result.success(TeleautoJoystickManager.selectInputSource(requireNotNull(call.argument<String>("id")))); scheduleState() }
                "inputSource" -> {
                    TeleautoJoystickManager.inputSource = tech.alius.rc.input.TeleautoJoystickInputSource.valueOf(requireNotNull(call.argument<String>("source")))
                    result.success(null)
                }
                "createDevice" -> {
                    val type = TeleautoDeviceType.valueOf(requireNotNull(call.argument<String>("type")))
                    val caps = if(type.isBluetoothTarget || call.argument<Boolean>("video") != true) emptySet() else setOf(TeleautoDeviceCapability.VIDEO_TRANSMISSION)
                    val device = TeleautoSdk.createDevice(type, TeleautoTargetDeviceConfig(capabilities = caps)); device.connect()
                    result.success(mapOf("id" to device.id.value)); scheduleState()
                }
                "connectHidDevice" -> {
                    val target = requireNotNull(TeleautoSdk.getActiveTargetDevice()) { "No active device" }
                    require(target.type.isVirtualHid) { "Active device is not a HID controller" }
                    target.disconnect()
                    target.connect()
                    result.success(null)
                    scheduleState()
                }
                "disconnectDevice" -> { TeleautoSdk.getActiveTargetDevice()?.disconnect(); result.success(null); scheduleState() }
                "removeDevice" -> { result.success(TeleautoSdk.removeDevice(TeleautoDeviceId(requireNotNull(call.argument<String>("id"))))); scheduleState() }
                "scanReceivers" -> { cyberBrick().scanReceivers(); result.success(null) }
                "connectReceiver" -> { cyberBrick().selectReceiver(requireNotNull(call.argument<String>("address"))); cyberBrick().connect(); result.success(null) }
                "disconnectReceiver" -> { cyberBrick().disconnect(); result.success(null) }
                "videoEnabled" -> { TeleautoVideoTransmissionManager.setEnabled(call.argument<Boolean>("enabled") == true); result.success(null) }
                "videoConfig" -> {
                    TeleautoVideoTransmissionManager.configureReceiver(tech.alius.rc.video.TeleautoVideoReceiverConfig(requireNotNull(call.argument<Int>("channel")),requireNotNull(call.argument<Int>("bandwidth"))))
                    result.success(null)
                }
                "createVideoTexture" -> {
                    releaseTexture()
                    texture = textures.createSurfaceTexture().also { it.surfaceTexture().setDefaultBufferSize(1920,1080) }
                    surface = Surface(texture!!.surfaceTexture())
                    TeleautoVideoTransmissionManager.setSurface(surface)
                    result.success(texture!!.id())
                }
                "releaseVideoTexture" -> { releaseTexture(); result.success(null) }
                "captureFrame" -> {
                    val currentSurface = surface ?: error("Video surface unavailable")
                    val bitmap = android.graphics.Bitmap.createBitmap(1920, 1080, android.graphics.Bitmap.Config.ARGB_8888)
                    android.view.PixelCopy.request(currentSurface, bitmap, { status ->
                        try {
                            if (status == android.view.PixelCopy.SUCCESS) {
                                val output = java.io.ByteArrayOutputStream()
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
                                result.success(output.toByteArray())
                            } else result.error("CAPTURE_FAILED", "No video frame is available", null)
                        } finally { bitmap.recycle() }
                    }, handler)
                }
                "startRecording" -> result.success(TeleautoVideoTransmissionManager.startRecording(requireNotNull(call.argument<String>("path"))))
                "stopRecording" -> result.success(TeleautoVideoTransmissionManager.stopRecording())
                "requestBluetoothPermissions" -> requestPermissions(result)
                "release" -> { detachObservers(); releaseTexture(); TeleautoSdk.release(); result.success(null) }
                else -> result.notImplemented()
            }
        } catch (e: Exception) { result.error("SDK_ERROR", e.message ?: "Operation failed", null) }
    }
    private fun cyberBrick() = TeleautoSdk.getActiveTargetDevice() as? TeleautoCyberBrickReceiverDevice ?: error("Select a CyberBrick device first")
    private fun detachObservers() {
        handler.removeCallbacks(poll)
        inputSubscription?.close(); inputSubscription = null
        deviceSubscription?.close(); deviceSubscription = null
        TeleautoRemoteControllerStatusManager.removeBatteryListener(batteryListener)
    }
    private fun releaseTexture() {
        if (surface != null) TeleautoVideoTransmissionManager.setSurface(null)
        surface?.release(); surface = null; texture?.release(); texture = null
    }
    private fun requestPermissions(result: MethodChannel.Result) {
        val activity = activityBinding?.activity ?: run { result.error("NO_ACTIVITY","Foreground activity required",null); return }
        val permissions = if(Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if(missing.isEmpty()){result.success(true); return}
        if(permissionResult != null){result.error("BUSY","Permission request pending",null); return}
        permissionResult = result; activity.requestPermissions(missing.toTypedArray(), 5811)
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray):Boolean {
        if(code != 5811) return false
        permissionResult?.success(results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }); permissionResult = null; return true
    }
    override fun onAttachedToActivity(binding: ActivityPluginBinding) { activityBinding = binding; binding.addRequestPermissionsResultListener(this) }
    override fun onDetachedFromActivityForConfigChanges(){activityBinding?.removeRequestPermissionsResultListener(this);activityBinding=null}
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding)=onAttachedToActivity(binding)
    override fun onDetachedFromActivity(){onDetachedFromActivityForConfigChanges();permissionResult?.error("NO_ACTIVITY","Activity detached",null);permissionResult=null}
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        detached=true;handler.removeCallbacks(frame);pendingFrame=false;detachObservers();releaseTexture();TeleautoSdk.release()
        sink=null;channel.setMethodCallHandler(null);events.setStreamHandler(null)
    }
}
