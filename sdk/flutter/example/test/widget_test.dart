import 'package:flutter_test/flutter_test.dart';
import 'package:teleauto_core/teleauto_core.dart';
void main(){test('unsupported platform capabilities are explicit',(){
 final capabilities=TeleautoCapabilities.fromMap({'platform':'ios','hardwareInput':false,'video':false,'bluetoothHid':false});
 expect(capabilities.hardwareInput,isFalse);expect(capabilities.video,isFalse);
});}
