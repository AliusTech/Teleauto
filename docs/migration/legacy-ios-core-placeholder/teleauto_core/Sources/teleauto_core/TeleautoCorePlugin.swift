import Flutter
import UIKit

/// iOS exposes capabilities explicitly; Android-only RC/USB transports are never simulated.
public class TeleautoCorePlugin: NSObject, FlutterPlugin, FlutterStreamHandler {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let instance = TeleautoCorePlugin()
    registrar.addMethodCallDelegate(instance, channel: FlutterMethodChannel(name: "tech.alius.teleauto/core", binaryMessenger: registrar.messenger()))
    FlutterEventChannel(name: "tech.alius.teleauto/events", binaryMessenger: registrar.messenger()).setStreamHandler(instance)
  }
  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "capabilities": result(["platform":"ios","hardwareInput":false,"video":false,"bluetoothHid":false])
    case "initialize", "state": result(["platform":"ios","initialized":false,"license":"UNSUPPORTED_PLATFORM","deviceName":"Teleauto","axes":[0,0,0,0,0,0,0],"held":[],"locked":false,"mode":"N"] as [String:Any])
    case "release": result(nil)
    default: result(FlutterError(code:"UNSUPPORTED_PLATFORM",message:"This hardware capability requires an iOS implementation.",details:nil))
    }
  }
  public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? { nil }
  public func onCancel(withArguments arguments: Any?) -> FlutterError? { nil }
}
