import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shadow/main.dart';
import 'package:shadow/shadow/models.dart';
import 'package:shadow/shadow/protection_store.dart';
import 'package:shadow/shadow/shadow_platform.dart';
import 'package:theme/theme.dart';

class FakePlatform implements ShadowPlatform {
  final calls = <String>[];
  ShadowConfig config = const ShadowConfig();
  PlatformStatus statusValue = const PlatformStatus(bluetoothPermission: true, bluetoothOn: true);
  PlatformStatus afterRequest = const PlatformStatus(bluetoothPermission: true, bluetoothOn: true);
  List<AlertRecord> alertList = const [];

  @override
  Future<ShadowConfig> getConfig() async => config;

  @override
  Future<void> setProtection(bool on) async {
    calls.add('protection $on');
    config = ShadowConfig(protection: on, running: on);
  }

  @override
  Future<PlatformStatus> status() async => statusValue;

  @override
  Future<PlatformStatus> requestPermissions() async {
    calls.add('request');
    statusValue = afterRequest;
    return afterRequest;
  }

  @override
  Future<void> openPermission(String name) async => calls.add('open $name');

  @override
  Future<List<AlertRecord>> alerts() async => alertList;

  @override
  Future<void> ignore(List<String> ids) async => calls.add('ignore ${ids.join(',')}');

  @override
  Future<int> ignoredCount() async => 0;

  @override
  Future<void> clearIgnored() async => calls.add('clearIgnored');

  @override
  Future<void> clearData({bool includeIgnored = false}) async {
    calls.add('clear $includeIgnored');
    alertList = const [];
  }

  @override
  Stream<ScanUpdate> scan({int seconds = 90}) => const Stream.empty();
}

AlertRecord alert({bool ignored = false}) => AlertRecord(
      ts: DateTime(2026, 3, 9, 7, 5),
      entryId: 't1',
      type: 'apple_find_my',
      label: 'Apple Find My (AirTag or accessory)',
      durationMs: 45 * 60000,
      places: 2,
      rssi: -65,
      ignored: ignored,
      ids: const ['k:abc', 'a:AA'],
    );

void main() {
  setUp(() {
    UiTypography.useGoogleFonts = false;
  });

  group('ProtectionStore', () {
    test('turning on asks for permissions first and starts the service', () async {
      final p = FakePlatform()
        ..statusValue = const PlatformStatus()
        ..afterRequest = const PlatformStatus(bluetoothPermission: true, locationPermission: true, notifications: true);
      final s = ProtectionStore(p);
      await s.load();
      expect(await s.setProtection(true), isTrue);
      expect(p.calls, ['request', 'protection true']);
      expect(s.error, isNull);
    });

    test('no Bluetooth permission means no service and a message', () async {
      final p = FakePlatform()
        ..statusValue = const PlatformStatus()
        ..afterRequest = const PlatformStatus();
      final s = ProtectionStore(p);
      await s.load();
      expect(await s.setProtection(true), isFalse);
      expect(p.calls, ['request']);
      expect(s.error, contains('Nearby devices'));
    });

    test('Bluetooth off is reported', () async {
      final p = FakePlatform()
        ..statusValue = const PlatformStatus(
          bluetoothOn: false,
          bluetoothPermission: true,
          locationPermission: true,
          notifications: true,
        )
        ..afterRequest = const PlatformStatus(
          bluetoothOn: false,
          bluetoothPermission: true,
          locationPermission: true,
          notifications: true,
        );
      final s = ProtectionStore(p);
      await s.load();
      expect(await s.setProtection(true), isFalse);
      expect(s.error, contains('Bluetooth'));
    });

    test('turning off needs no permissions', () async {
      final p = FakePlatform()..config = const ShadowConfig(protection: true, running: true);
      final s = ProtectionStore(p);
      await s.load();
      expect(await s.setProtection(false), isFalse);
      expect(p.calls, ['protection false']);
    });

    test('ignoring an alert marks every alert with a shared id', () async {
      final p = FakePlatform()..alertList = [alert()];
      final s = ProtectionStore(p);
      await s.load();
      await s.ignoreAlert(s.alerts.single);
      expect(p.calls, ['ignore k:abc,a:AA']);
      expect(s.alerts.single.ignored, isTrue);
    });

    test('clearData reloads', () async {
      final p = FakePlatform()..alertList = [alert()];
      final s = ProtectionStore(p);
      await s.load();
      await s.clearData();
      expect(s.alerts, isEmpty);
      expect(p.calls, ['clear false']);
    });
  });

  group('screens', () {
    testWidgets('home shows the switch, Scan now and the empty state', (tester) async {
      await tester.pumpWidget(ShadowApp(platform: FakePlatform()));
      await tester.pumpAndSettle();
      expect(find.text('Watch for trackers'), findsOneWidget);
      expect(find.text('Scan now'), findsOneWidget);
      expect(find.text('No warnings'), findsOneWidget);
      expect(find.byIcon(Icons.settings_rounded), findsOneWidget);
    });

    testWidgets('settings ends with Appearance, Updates, Developer', (tester) async {
      await tester.pumpWidget(ShadowApp(platform: FakePlatform()));
      await tester.pumpAndSettle();
      await tester.tap(find.byIcon(Icons.settings_rounded));
      // The update row keeps a spinner running, so pumpAndSettle would never finish.
      await tester.pump(const Duration(milliseconds: 600));
      await tester.pump(const Duration(milliseconds: 600));
      await tester.scrollUntilVisible(find.text('Developer'), 400, scrollable: find.byType(Scrollable).first);
      expect(find.text('Developer'), findsOneWidget);
      expect(find.byType(UiAppearanceSection), findsOneWidget);
      expect(find.byType(UiUpdateSection), findsOneWidget);
      expect(find.byType(UiDeveloperSection), findsOneWidget);
    });

    testWidgets('home lists a warning and history opens', (tester) async {
      final p = FakePlatform()..alertList = [alert()];
      await tester.pumpWidget(ShadowApp(platform: p));
      await tester.pumpAndSettle();
      expect(find.text('Apple Find My (AirTag or accessory)'), findsOneWidget);
      await tester.tap(find.text('Apple Find My (AirTag or accessory)'));
      await tester.pumpAndSettle();
      expect(find.text('Warnings'), findsOneWidget);
    });
  });
}
