import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:teleauto_ui/teleauto_ui.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    final materialIcons = FontLoader('MaterialIcons');
    materialIcons.addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'));
    await materialIcons.load();
    for (final entry in {
      'Inter': 'inter',
      'SourceHanSans': 'source_han_sans_sc',
    }.entries) {
      final loader = FontLoader('packages/teleauto_ui/${entry.key}');
      loader.addFont(
        rootBundle.load(
          'packages/teleauto_ui/src/main/assets/fonts/${entry.value}_regular.otf',
        ),
      );
      loader.addFont(
        rootBundle.load(
          'packages/teleauto_ui/src/main/assets/fonts/${entry.value}_bold.otf',
        ),
      );
      await loader.load();
    }
  });
  Future<void> settle(WidgetTester tester) async {
    await tester.pumpAndSettle();
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 300)),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('panel navigation reverses in place and keeps console mounted', (
    tester,
  ) async {
    late StateSetter update;
    var settings = false;
    var reduced = false;
    final consoleKey = GlobalKey();
    await tester.pumpWidget(
      MaterialApp(
        home: StatefulBuilder(
          builder: (context, setState) {
            update = setState;
            return MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
              child: TeleautoPanelTransition(
                showSettings: settings,
                console: SizedBox(key: consoleKey),
                settings: const Text('settings panel'),
              ),
            );
          },
        ),
      ),
    );
    final original = consoleKey.currentContext;
    update(() => settings = true);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 70));
    final motion = tester.widget<TeleautoConsoleMotion>(
      find.byType(TeleautoConsoleMotion),
    );
    expect(motion.progress, inExclusiveRange(0, 1));
    update(() => settings = false);
    await tester.pump();
    expect(
      tester
          .widget<TeleautoConsoleMotion>(find.byType(TeleautoConsoleMotion))
          .progress,
      motion.progress,
    );
    await tester.pumpAndSettle();
    expect(consoleKey.currentContext, same(original));
    expect(find.text('settings panel'), findsNothing);
    update(() {
      settings = true;
      reduced = true;
    });
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<TeleautoConsoleMotion>(find.byType(TeleautoConsoleMotion))
          .reduced,
      isTrue,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('negative Y points up and touch uses the same axis convention', (
    tester,
  ) async {
    Offset? reported;
    await tester.pumpWidget(
      MaterialApp(
        home: Center(
          child: SizedBox(
            width: 200,
            height: 200,
            child: TeleautoJoystick(
              x: 0,
              y: -1,
              onChanged: (value) => reported = value,
            ),
          ),
        ),
      ),
    );
    await settle(tester);
    final stick = find.byType(TeleautoJoystick);
    final pointer = find.byWidgetPredicate(
      (widget) => widget is TeleautoIcon && widget.name == 'stick_pointer',
    );
    expect(tester.getCenter(pointer).dy, lessThan(tester.getCenter(stick).dy));
    final gesture = await tester.startGesture(tester.getCenter(stick));
    await gesture.moveBy(const Offset(0, -40));
    await tester.pump();
    expect(reported!.dy, lessThan(0));
    await gesture.cancel();
    expect(reported, Offset.zero);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'battery menu selects available devices and falls back on removal',
    (tester) async {
      var entries = const [
        TeleautoBatteryEntry(id: 'main', name: '主电池', level: 90),
        TeleautoBatteryEntry(id: 'backup', name: '备用电池', level: 60),
        TeleautoBatteryEntry(id: 'rf', name: '高频头', level: 40, present: false),
      ];
      late StateSetter update;
      await tester.pumpWidget(
        MaterialApp(
          theme: TeleautoTokens.theme,
          home: Scaffold(
            body: StatefulBuilder(
              builder: (context, setState) {
                update = setState;
                return Align(
                  alignment: Alignment.topRight,
                  child: TeleautoBatterySelector(entries: entries),
                );
              },
            ),
          ),
        ),
      );
      await tester.tap(find.byType(TeleautoBatteryIndicator));
      await tester.pumpAndSettle();
      expect(find.text('主电池'), findsOneWidget);
      expect(find.text('备用电池'), findsOneWidget);
      expect(find.text('高频头'), findsNothing);
      await tester.tap(find.text('备用电池'));
      await tester.pumpAndSettle();
      expect(find.text('60%'), findsOneWidget);
      await tester.tap(find.byType(TeleautoBatteryIndicator));
      await tester.pumpAndSettle();
      update(() => entries = [entries.first]);
      await tester.pumpAndSettle();
      expect(find.byType(MenuItemButton), findsNothing);
      expect(find.text('90%'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  for (final height in [1080, 1200, 1280]) {
    testWidgets('console and expanded menus fit 1920 x $height', (
      tester,
    ) async {
      tester.view.physicalSize = Size(1920, height.toDouble());
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final key = GlobalKey();
      Future<void> show(Widget child, String name) async {
        await tester.pumpWidget(
          MaterialApp(
            theme: TeleautoTokens.theme,
            home: RepaintBoundary(
              key: key,
              child: Scaffold(body: child),
            ),
          ),
        );
        await settle(tester);
        expect(tester.takeException(), isNull);
        await tester.runAsync(() async {
          final image =
              await (key.currentContext!.findRenderObject()!
                      as RenderRepaintBoundary)
                  .toImage();
          final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
          final directory = Directory('build/visuals')
            ..createSync(recursive: true);
          File(
            '${directory.path}/$name-$height.png',
          ).writeAsBytesSync(bytes!.buffer.asUint8List());
          image.dispose();
        });
      }

      await show(
        TeleautoConsole(
          state: const TeleautoConsoleState(
            battery: 86,
            axes: [0, 0, 0, 0, -.55, .55, .35],
          ),
          onKey: (_, _) {},
          onDial: (_) {},
          onLock: () {},
          onSettings: () {},
          onDevices: () {},
        ),
        'console',
      );
      final consoleHeaderHeight = tester
          .getRect(find.byType(TeleautoTopBar))
          .height;
      await show(
        TeleautoControlCenter(
          key: UniqueKey(),
          devices: const [],
          initialPage: '新增设备',
          onAction: (_, _) {},
          onBack: () {},
        ),
        'devices',
      );
      expect(
        tester.getRect(find.byType(TeleautoTopBar)).height,
        closeTo(consoleHeaderHeight, 0.01),
      );
      await tester.tap(find.text('航空设备').first);
      await settle(tester);
      final types = TeleautoDeviceType.all
          .where((t) => t.category == '航空设备')
          .take(5)
          .toList();
      final top = tester.getTopLeft(find.text(types.first.name)).dy;
      for (final type in types.take(4)) {
        expect(tester.getTopLeft(find.text(type.name)).dy, top);
      }
      expect(tester.getTopLeft(find.text(types[4].name)).dy, greaterThan(top));
      expect(tester.takeException(), isNull);

      String? selectedSource;
      await show(
        TeleautoControlCenter(
          key: UniqueKey(),
          devices: const [],
          initialPage: '输入来源',
          inputSources: const [
            {
              'id': 'embedded',
              'name': '内置摇杆',
              'description': 'RC Plus 2',
              'selected': true,
              'enabled': true,
            },
            {
              'id': 'external:1',
              'name': '外接摇杆',
              'description': 'USB / Bluetooth 控制器',
              'selected': false,
              'enabled': true,
            },
            {
              'id': 'unavailable',
              'name': '待准备设备',
              'selected': false,
              'enabled': false,
            },
          ],
          onAction: (name, args) {
            if (name == 'selectInputSource') {
              selectedSource = args['id'] as String;
            }
          },
          onBack: () {},
        ),
        'input-sources',
      );
      expect(find.text('内置摇杆'), findsOneWidget);
      expect(find.text('外接摇杆'), findsNothing);
      expect(find.text('选择'), findsNothing);
      expect(find.text('已选择'), findsNothing);
      expect(selectedSource, isNull);
      expect(tester.takeException(), isNull);

      await show(
        TeleautoControlCenter(
          key: UniqueKey(),
          devices: const [],
          initialPage: '存储管理',
          storage: const {
            'selected': 'internal',
            'cardPresent': true,
            'cardWritable': true,
            'capacity': '内部存储 20 GB / 64 GB',
          },
          onAction: (_, _) {},
          onBack: () {},
        ),
        'storage',
      );
      await tester.tap(find.byType(PopupMenuButton<String>));
      await settle(tester);
      expect(find.text('存储卡'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  }
  testWidgets(
    'held buttons release on cancel and cannot activate while disabled',
    (tester) async {
      final events = <bool>[];
      Future<void> show(bool enabled) => tester.pumpWidget(
        MaterialApp(
          home: Center(
            child: SizedBox(
              width: 100,
              height: 50,
              child: TeleautoButton(
                label: 'L1',
                enabled: enabled,
                onHeldChanged: events.add,
              ),
            ),
          ),
        ),
      );
      await show(true);
      final gesture = await tester.startGesture(
        tester.getCenter(find.text('L1')),
      );
      await tester.pump(const Duration(milliseconds: 150));
      await gesture.cancel();
      await tester.pump();
      expect(events, [true, false]);
      events.clear();
      await show(false);
      await tester.tap(find.text('L1'));
      await tester.pump();
      expect(events, isEmpty);
    },
  );
  testWidgets(
    'no card has no dropdown and locked slider cannot unlock by touch',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: TeleautoControlCenter(
            devices: const [],
            initialPage: '存储管理',
            storage: const {'selected': 'internal', 'cardPresent': false},
            onAction: (_, _) {},
            onBack: () {},
          ),
        ),
      );
      await settle(tester);
      expect(find.byType(PopupMenuButton<String>), findsNothing);
      var invoked = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Center(
            child: SizedBox(
              width: 300,
              height: 40,
              child: TeleautoSlideLock(
                locked: true,
                progress: 1,
                onLock: () => invoked++,
              ),
            ),
          ),
        ),
      );
      await tester.drag(find.byType(TeleautoSlideLock), const Offset(280, 0));
      await tester.pump();
      expect(invoked, 0);
      expect(tester.takeException(), isNull);
    },
  );
}
