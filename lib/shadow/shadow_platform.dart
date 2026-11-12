import 'package:flutter/services.dart';

import 'models.dart';

/// Everything the UI needs from the Kotlin side. Fake it in tests.
abstract class ShadowPlatform {
  Future<ShadowConfig> getConfig();
  Future<void> setProtection(bool on);
  Future<PlatformStatus> status();

  /// Shows the system permission dialog (Bluetooth, location, notifications) and returns the new status.
  Future<PlatformStatus> requestPermissions();

  /// `app` | `bluetooth` | `battery`.
  Future<void> openPermission(String name);

  Future<List<AlertRecord>> alerts();
  Future<void> ignore(List<String> ids);
  Future<int> ignoredCount();
  Future<void> clearIgnored();
  Future<void> clearData({bool includeIgnored = false});

  /// A foreground scan. Cancel the subscription to stop it. Errors carry code `scan_failed`
  /// with message `permission` or `bluetooth_off`.
  Stream<ScanUpdate> scan({int seconds = 90});
}

class MethodChannelShadowPlatform implements ShadowPlatform {
  static const _control = MethodChannel('com.itsash.shadow/control');
  static const _scan = EventChannel('com.itsash.shadow/scan');

  @override
  Future<ShadowConfig> getConfig() async =>
      ShadowConfig.fromMap(await _control.invokeMapMethod<Object?, Object?>('getConfig') ?? const {});

  @override
  Future<void> setProtection(bool on) => _control.invokeMethod<void>('setProtection', {'value': on});

  @override
  Future<PlatformStatus> status() async =>
      PlatformStatus.fromMap(await _control.invokeMapMethod<Object?, Object?>('status') ?? const {});

  @override
  Future<PlatformStatus> requestPermissions() async =>
      PlatformStatus.fromMap(await _control.invokeMapMethod<Object?, Object?>('requestPermissions') ?? const {});

  @override
  Future<void> openPermission(String name) => _control.invokeMethod<void>('openPermission', {'name': name});

  @override
  Future<List<AlertRecord>> alerts() async {
    final raw = await _control.invokeListMethod<Object?>('alerts') ?? const [];
    return [for (final m in raw) AlertRecord.fromMap(m! as Map<Object?, Object?>)];
  }

  @override
  Future<void> ignore(List<String> ids) => _control.invokeMethod<void>('ignore', {'ids': ids});

  @override
  Future<int> ignoredCount() async => await _control.invokeMethod<int>('ignoredCount') ?? 0;

  @override
  Future<void> clearIgnored() => _control.invokeMethod<void>('clearIgnored');

  @override
  Future<void> clearData({bool includeIgnored = false}) =>
      _control.invokeMethod<void>('clearData', {'includeIgnored': includeIgnored});

  @override
  Stream<ScanUpdate> scan({int seconds = 90}) => _scan
      .receiveBroadcastStream({'seconds': seconds})
      .map((e) => ScanUpdate.fromMap(e as Map<Object?, Object?>));
}
