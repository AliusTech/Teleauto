import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:teleauto_ui/teleauto_ui.dart';

void main() {
  setUpAll(() async {
    final icons = FontLoader('MaterialIcons')
      ..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'));
    await icons.load();
    for (final entry in {
      'Inter': 'inter',
      'SourceHanSans': 'source_han_sans_sc',
    }.entries) {
      final loader = FontLoader('packages/teleauto_ui/${entry.key}');
      for (final weight in ['regular', 'bold']) {
        loader.addFont(
          rootBundle.load(
            'packages/teleauto_ui/src/main/assets/fonts/${entry.value}_$weight.otf',
          ),
        );
      }
      await loader.load();
    }
  });
  testWidgets('PC pairing, connection, disconnect and retry stay in the panel', (
    tester,
  ) async {
    final boundary = GlobalKey();
    var connects = 0;
    var disconnects = 0;
    Future<void> show(String phase, {bool enabled = true}) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: TeleautoTokens.theme,
          home: Scaffold(
            body: Center(
              child: SizedBox(
                width: 420,
                height: 236,
                child: RepaintBoundary(
                  key: boundary,
                  child: TeleautoHidConnectionPanel(
                    state: TeleautoConsoleState(
                      presentation: 'xbox',
                      connected:
                          true, // A stale target flag must not override HID readiness.
                      hidState: phase,
                      hidDeviceName: 'DJI RC Plus',
                      hidHostName: 'Office PC',
                      enabled: enabled,
                    ),
                    onConnect: () => connects++,
                    onDisconnect: () => disconnects++,
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }

    await show('OFF');
    expect(find.text('PC 未连接'), findsOneWidget);
    await tester.tap(find.text('配对 / 连接 PC'));
    expect(connects, 1);

    await show('ADVERTISING');
    expect(find.textContaining('DJI RC Plus'), findsOneWidget);
    expect(find.text('PC 已连接'), findsNothing);
    await tester.tap(find.text('取消连接'));
    expect(disconnects, 1);

    await show('PAIRING');
    expect(find.text('正在建立手柄连接'), findsOneWidget);
    expect(find.text('PC 已连接'), findsNothing);

    await show('CONNECTED');
    expect(find.text('PC 已连接'), findsOneWidget);
    expect(find.text('Office PC · 已就绪'), findsOneWidget);
    expect(find.byIcon(Icons.link), findsOneWidget);
    expect(find.byIcon(Icons.desktop_windows_outlined), findsOneWidget);
    if (const bool.fromEnvironment('HID_SCREENSHOT')) {
      await tester.runAsync(() async {
        final image =
            await (boundary.currentContext!.findRenderObject()!
                    as RenderRepaintBoundary)
                .toImage(pixelRatio: 2);
        final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
        File(
          '/tmp/teleauto-hid-connected.png',
        ).writeAsBytesSync(bytes!.buffer.asUint8List());
        image.dispose();
      });
    }
    await tester.tap(find.text('断开 PC 连接'));
    expect(disconnects, 2);
    await show('OFF');
    expect(find.byIcon(Icons.link), findsNothing);
    expect(find.text('Office PC · 已就绪'), findsNothing);
    expect(find.text('配对 / 连接 PC'), findsOneWidget);

    await show('ERROR');
    expect(find.text('连接失败，请重试'), findsOneWidget);
    await tester.tap(find.text('配对 / 连接 PC'));
    expect(connects, 2);

    await show('OFF', enabled: false);
    await tester.tap(find.text('配对 / 连接 PC'));
    expect(connects, 2);
  });
}
