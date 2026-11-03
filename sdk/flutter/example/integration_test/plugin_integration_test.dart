import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:teleauto_core/teleauto_core.dart';
void main(){IntegrationTestWidgetsFlutterBinding.ensureInitialized();
 testWidgets('platform adapter reports actual capabilities',(tester)async{
  final capabilities=await const TeleautoCore().capabilities();
  expect(capabilities.platform,anyOf('android','ios'));
  if(capabilities.platform=='ios')expect(capabilities.hardwareInput,isFalse);
 });
}
