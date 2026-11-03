import 'package:flutter/services.dart';

/// Hardware and authorization API. The UI package has no dependency on this library.
class TeleautoCore {
  const TeleautoCore();
  static const methods = MethodChannel('tech.alius.teleauto/core');
  static const events = EventChannel('tech.alius.teleauto/events');
  Stream<Map<String, dynamic>> get changes => events
      .receiveBroadcastStream()
      .map((event) => Map<String, dynamic>.from(event as Map));
  Future<Map<String, dynamic>> state() async => Map<String, dynamic>.from(
    await methods.invokeMapMethod<String, dynamic>('state') ?? {},
  );
  Future<Map<String, dynamic>> initialize({
    String? vendorId,
    bool videoEnabled = true,
  }) async => Map<String, dynamic>.from(
    await methods.invokeMapMethod<String, dynamic>('initialize', {
          'vendorId': vendorId,
          'videoEnabled': videoEnabled,
        }) ??
        {},
  );
  Future<T?> invoke<T>(
    String method, [
    Map<String, dynamic> arguments = const {},
  ]) => methods.invokeMethod<T>(method, arguments);
  Future<void> release() => methods.invokeMethod<void>('release');
}

/// Stable key/action identities shared with the native SDK; media keys are not control channels.
enum TeleautoControlKey {
  l1,
  l2,
  l3,
  r1,
  r2,
  r3,
  c1,
  c2,
  c3,
  gohome,
  pause,
  authLed,
}

enum TeleautoControlAction { down, up, trigger }

enum TeleautoInputSource { embedded, external }

class TeleautoCapabilities {
  const TeleautoCapabilities({
    required this.platform,
    required this.hardwareInput,
    required this.video,
    required this.bluetoothHid,
  });
  factory TeleautoCapabilities.fromMap(Map<String, dynamic> map) =>
      TeleautoCapabilities(
        platform: map['platform'] as String? ?? 'unknown',
        hardwareInput: map['hardwareInput'] == true,
        video: map['video'] == true,
        bluetoothHid: map['bluetoothHid'] == true,
      );
  final String platform;
  final bool hardwareInput, video, bluetoothHid;
}

extension TeleautoCoreOperations on TeleautoCore {
  Future<TeleautoCapabilities> capabilities() async =>
      TeleautoCapabilities.fromMap(
        Map<String, dynamic>.from(await invoke<Map>('capabilities') ?? {}),
      );
  Future<String?> submitKey(
    TeleautoControlKey key,
    TeleautoControlAction action,
  ) => invoke<String>('submitKey', {
    'key': key == TeleautoControlKey.authLed
        ? 'AUTH_LED'
        : key.name.toUpperCase(),
    'action': action.name.toUpperCase(),
  });
  Future<String?> setDial(double value) =>
      invoke<String>('setDial', {'value': value.clamp(-1.0, 1.0)});
  Future<void> lock() => invoke<void>('lock');
  Future<bool?> selectControllerSource(String id) =>
      invoke<bool>('selectInputSource', {'id': id});
  Future<void> selectInputSource(TeleautoInputSource source) =>
      invoke<void>('inputSource', {
        'source': source == TeleautoInputSource.embedded
            ? 'EMBEDDED_JOYSTICK'
            : 'EXTERNAL_JOYSTICK',
      });
  Future<bool> requestBluetoothPermissions() async =>
      await invoke<bool>('requestBluetoothPermissions') ?? false;
  Future<Map?> createDevice(String type, {bool video = false}) =>
      invoke<Map>('createDevice', {'type': type, 'video': video});
  Future<bool?> removeDevice(String id) =>
      invoke<bool>('removeDevice', {'id': id});
  Future<void> scanReceivers() => invoke<void>('scanReceivers');
  Future<void> connectReceiver(String address) =>
      invoke<void>('connectReceiver', {'address': address});
  Future<void> disconnectReceiver() => invoke<void>('disconnectReceiver');

  /// Start advertising for PC pairing/reconnection; existing bonds are retained.
  Future<void> connectHidDevice() => invoke<void>('connectHidDevice');
  Future<void> disconnectDevice() => invoke<void>('disconnectDevice');
  Future<void> setVideoEnabled(bool enabled) =>
      invoke<void>('videoEnabled', {'enabled': enabled});
  Future<void> configureVideo({required int channel, required int bandwidth}) =>
      invoke<void>('videoConfig', {'channel': channel, 'bandwidth': bandwidth});
  Future<int?> createVideoTexture() => invoke<int>('createVideoTexture');
  Future<void> releaseVideoTexture() => invoke<void>('releaseVideoTexture');
  Future<Uint8List?> captureFrame() => invoke<Uint8List>('captureFrame');
  Future<bool> startRecording(String path) async =>
      await invoke<bool>('startRecording', {'path': path}) ?? false;
  Future<bool> stopRecording() async =>
      await invoke<bool>('stopRecording') ?? false;
}
