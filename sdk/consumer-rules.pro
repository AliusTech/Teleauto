# 消费方开启混淆时自动生效的 keep 规则

# adb 协议库(含 RSA/序列化,全保留)
-keep class com.tananaev.adblib.** { *; }

# SDK 公有 API
-keep class tech.alius.rc.TeleautoSdk { *; }
-keep class tech.alius.rc.TeleautoSdk$* { *; }
-keep class tech.alius.rc.TeleautoJoystickManager { *; }
-keep class tech.alius.rc.TeleautoJoystickManager$* { *; }
-keep class tech.alius.rc.TeleautoConfig { *; }
-keep class tech.alius.rc.TeleautoJoystickActivity { *; }
-keep class tech.alius.rc.input.** { *; }
-keep class tech.alius.rc.hid.** { *; }
-keep class tech.alius.rc.model.** { *; }
-keep class tech.alius.rc.profile.** { *; }
-keep class tech.alius.rc.device.** { *; }
-keep class tech.alius.rc.TeleautoRemoteControllerStatusManager { *; }
-keep class tech.alius.rc.TeleautoRemoteControlStatusManager { *; }
-keep class tech.alius.rc.TeleautoTransmitModuleManager { *; }
-keep class tech.alius.rc.video.TeleautoVideoTransmissionManager { *; }
-keep class tech.alius.rc.video.TeleautoVideoStats { *; }
-keep class com.openipc.wfbngrtl8812.** { *; }

# License result API; verifier, trust anchors and runtime remain obfuscatable.
-keep class tech.alius.rc.license.TeleautoLicenseManager { public *; }
-keep class tech.alius.rc.license.TeleautoLicenseStatus { public *; }
-keep enum tech.alius.rc.license.TeleautoLicenseError { *; }
-keep interface tech.alius.rc.license.TeleautoLicenseListener { *; }
