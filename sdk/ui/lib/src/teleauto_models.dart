import 'package:flutter/widgets.dart';

@immutable
class TeleautoConsoleState {
  const TeleautoConsoleState({
    this.deviceName = 'Teleauto',
    this.status = '系统运行中',
    this.mode = 'N',
    this.axes = const [0, 0, 0, 0, 0, 0, 0],
    this.held = const {},
    this.locked = false,
    this.unlockProgress = 0,
    this.battery,
    this.batteries = const [],
    this.presentation = 'video',
    this.connected = false,
    this.enabled = true,
    this.canLock = true,
    this.touchUnlock = false,
    this.connectionLabel = '等待连接',
    this.hidState = 'OFF',
    this.hidHostName,
    this.hidDeviceName,
  });
  final String deviceName, status, mode, presentation, connectionLabel;
  final String hidState;
  final String? hidHostName, hidDeviceName;
  final List<double> axes;
  final Set<String> held;
  final bool locked, connected, enabled, canLock, touchUnlock;
  final double unlockProgress;
  final int? battery;
  final List<TeleautoBatteryEntry> batteries;
  double axis(int index) =>
      index < axes.length ? axes[index].clamp(-1.0, 1.0) : 0;
}

@immutable
class TeleautoDeviceEntry {
  const TeleautoDeviceEntry({
    required this.id,
    required this.name,
    required this.type,
    this.connected = false,
    this.active = false,
  });
  final String id, name, type;
  final bool connected, active;
}

@immutable
class TeleautoDeviceType {
  const TeleautoDeviceType(this.id, this.name, this.category, this.icon);
  final String id, name, category, icon;
  static const all = [
    TeleautoDeviceType(
      'GENERIC_AIRCRAFT',
      '通用飞行器',
      '航空设备',
      'device-generic-aircraft',
    ),
    TeleautoDeviceType('FIXED_WING', '固定翼', '航空设备', 'device-fixed-wing'),
    TeleautoDeviceType('QUADROTOR', '四旋翼', '航空设备', 'device-quadrotor'),
    TeleautoDeviceType(
      'COAXIAL_HELICOPTER',
      '共轴直升机',
      '航空设备',
      'device-coaxial-helicopter',
    ),
    TeleautoDeviceType('HELICOPTER', '直升机', '航空设备', 'device-helicopter'),
    TeleautoDeviceType('AIRSHIP', '飞艇', '航空设备', 'device-airship'),
    TeleautoDeviceType('FREE_BALLOON', '自由气球', '航空设备', 'device-free-balloon'),
    TeleautoDeviceType('ROCKET', '火箭', '航空设备', 'device-rocket'),
    TeleautoDeviceType('GROUND_ROVER', '遥控车', '地面设备', 'device-ground-rover'),
    TeleautoDeviceType('SURFACE_BOAT', '水面船艇', '水域设备', 'device-surface-boat'),
    TeleautoDeviceType('SUBMARINE', '水下执行器', '水域设备', 'device-submarine'),
    TeleautoDeviceType('HEXAROTOR', '六旋翼', '航空设备', 'device-hexarotor'),
    TeleautoDeviceType('OCTOROTOR', '八旋翼', '航空设备', 'device-octorotor'),
    TeleautoDeviceType('TRICOPTER', '三旋翼', '航空设备', 'device-tricopter'),
    TeleautoDeviceType('FLAPPING_WING', '扑翼机', '航空设备', 'device-flapping-wing'),
    TeleautoDeviceType('KITE', '风筝', '航空设备', 'device-kite'),
    TeleautoDeviceType(
      'VTOL_TAILSITTER_DUOROTOR',
      '双旋翼尾座 VTOL',
      '航空设备',
      'device-vtol-tailsitter-duorotor',
    ),
    TeleautoDeviceType(
      'VTOL_TAILSITTER_QUADROTOR',
      '四旋翼尾座 VTOL',
      '航空设备',
      'device-vtol-tailsitter-quadrotor',
    ),
    TeleautoDeviceType(
      'VTOL_TILTROTOR',
      '倾转旋翼 VTOL',
      '航空设备',
      'device-vtol-tiltrotor',
    ),
    TeleautoDeviceType(
      'VTOL_FIXEDROTOR',
      '复合翼 VTOL',
      '航空设备',
      'device-vtol-fixedrotor',
    ),
    TeleautoDeviceType(
      'VTOL_TAILSITTER',
      '尾座式 VTOL',
      '航空设备',
      'device-vtol-tailsitter',
    ),
    TeleautoDeviceType(
      'VTOL_TILTWING',
      '倾转机翼 VTOL',
      '航空设备',
      'device-vtol-tiltwing',
    ),
    TeleautoDeviceType('PARAFOIL', '翼伞', '航空设备', 'device-parafoil'),
    TeleautoDeviceType('DODECAROTOR', '十二旋翼', '航空设备', 'device-dodecarotor'),
    TeleautoDeviceType('DECAROTOR', '十旋翼', '航空设备', 'device-decarotor'),
    TeleautoDeviceType(
      'GENERIC_MULTIROTOR',
      '通用多旋翼',
      '航空设备',
      'device-generic-multirotor',
    ),
    TeleautoDeviceType('GROUND_QUADRUPED', '四足机器人', '地面设备', 'device-quadruped'),
    TeleautoDeviceType('HUMANOID_ROBOT', '人形机器人', '机器人', 'device-humanoid'),
    TeleautoDeviceType(
      'VTOL_GYRODYNE',
      '旋翼 VTOL',
      '航空设备',
      'device-vtol-gyrodyne',
    ),
    TeleautoDeviceType('CYBERBRICK', 'CyberBrick', '虚拟设备', 'cyberbrick'),
    TeleautoDeviceType('VIRTUAL_GAMEPAD', 'Xbox 手柄', '虚拟设备', 'xbox'),
    TeleautoDeviceType('VIRTUAL_JOYSTICK', 'Joystick 摇杆', '虚拟设备', 'joystick'),
  ];
}

@immutable
class TeleautoBatteryEntry {
  const TeleautoBatteryEntry({
    required this.id,
    required this.name,
    this.level,
    this.present = true,
  });
  final String id, name;
  final int? level;
  final bool present;
}
