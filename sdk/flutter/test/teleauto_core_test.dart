import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:teleauto_core/teleauto_core.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'initialize forwards vendor binding and reports authorization errors',
    () async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(TeleautoCore.methods, (call) async {
            expect(call.method, 'initialize');
            expect((call.arguments as Map)['vendorId'], 'developer-123');
            return {'license': 'LICENSE_NOT_FOUND', 'initialized': false};
          });
      final status = await const TeleautoCore().initialize(
        vendorId: 'developer-123',
      );
      expect(status['initialized'], false);
      expect(status['license'], 'LICENSE_NOT_FOUND');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(TeleautoCore.methods, null);
    },
  );
  test('native blocking errors are not converted to success', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(TeleautoCore.methods, (call) async {
          throw PlatformException(code: 'INPUT_BLOCKED');
        });
    expect(
      const TeleautoCore().invoke('submitKey', {'key': 'L1', 'action': 'DOWN'}),
      throwsA(isA<PlatformException>()),
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(TeleautoCore.methods, null);
  });
}
