# Teleauto core — Flutter adapter

Version **0.1.0**. Dart package `teleauto_core`; Android core namespace `tech.alius.rc`.

The Android adapter consumes `tech.alius:teleauto-core:0.1.0` from Maven. It does not embed or compile the core source tree. Shared visual components are distributed separately as `teleauto_ui`, which has no dependency on this plugin.

`TeleautoCore` initializes and releases the native session and exposes state/events. `TeleautoCoreOperations` provides typed key actions, input source selection, device connection, video textures, capture and recording. `TeleautoCapabilities` declares platform support. Method/Event Channels are transport details; the native SDK remains authoritative for licensing, routing, lock interception and physical unlock. Display frames may be coalesced; key edges are delivered individually. The Android host extends `TeleautoFlutterActivity` to route physical Android events through `TeleautoInputHost`.

Android initialization requires `assets/sdk/license.json` signed for the application's package and APK signing certificate. No issuer private key is distributed with the plugin or AAR. See the repository's License Generator documentation.

This is an Android-only plugin. iOS applications compose `teleauto_ui` without registering or initializing this plugin, without an Android AAR and without Android license checks. Battery and other iOS application services belong to the iOS host.

Run `flutter analyze` and `flutter test` here. Build the consuming Android application against a locally published AAR for development. Release consumers must pin the SDK Git revision for Dart packages and the corresponding Maven artifact version.
