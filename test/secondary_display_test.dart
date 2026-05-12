import 'package:flutter_test/flutter_test.dart';
import 'package:secondary_display/secondary_display.dart';
import 'package:secondary_display/secondary_display_platform_interface.dart';
import 'package:secondary_display/secondary_display_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockSecondaryDisplayPlatform
    with MockPlatformInterfaceMixin
    implements SecondaryDisplayPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final SecondaryDisplayPlatform initialPlatform = SecondaryDisplayPlatform.instance;

  test('$MethodChannelSecondaryDisplay is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelSecondaryDisplay>());
  });

  test('getPlatformVersion', () async {
    SecondaryDisplay secondaryDisplayPlugin = SecondaryDisplay();
    MockSecondaryDisplayPlatform fakePlatform = MockSecondaryDisplayPlatform();
    SecondaryDisplayPlatform.instance = fakePlatform;

    expect(await secondaryDisplayPlugin.getPlatformVersion(), '42');
  });
}
