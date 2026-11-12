import 'package:flutter/foundation.dart';

import 'models.dart';
import 'shadow_platform.dart';

/// State behind the Background protection switch, shared by Home and Settings.
class ProtectionStore extends ChangeNotifier {
  ProtectionStore(this._platform);

  final ShadowPlatform _platform;

  ShadowPlatform get platform => _platform;

  ShadowConfig config = const ShadowConfig();
  PlatformStatus status = const PlatformStatus();
  List<AlertRecord> alerts = const [];
  bool loaded = false;
  bool busy = false;

  /// A short message for the user when turning protection on did not work.
  String? error;

  Future<void> load() async {
    try {
      final r = await Future.wait<Object>([_platform.getConfig(), _platform.status(), _platform.alerts()]);
      config = r[0] as ShadowConfig;
      status = r[1] as PlatformStatus;
      alerts = r[2] as List<AlertRecord>;
    } catch (_) {
      // Keep the previous state; the screen shows what it has.
    }
    loaded = true;
    notifyListeners();
  }

  /// Asks for permissions if needed, then switches the service. Returns whether protection is on afterwards.
  Future<bool> setProtection(bool on) async {
    if (busy) return config.protection;
    busy = true;
    error = null;
    notifyListeners();
    try {
      if (on) {
        if (!status.bluetoothPermission || !status.locationPermission || !status.notifications) {
          status = await _platform.requestPermissions();
        }
        if (!status.bluetoothSupported) {
          error = 'This phone has no Bluetooth LE.';
        } else if (!status.bluetoothPermission) {
          error = 'Allow Nearby devices (Bluetooth) for Shadow in the app settings to scan for trackers.';
        } else if (!status.bluetoothOn) {
          error = 'Turn Bluetooth on first.';
        }
        if (error != null) return false;
      }
      await _platform.setProtection(on);
      config = await _platform.getConfig();
      return config.protection;
    } catch (e) {
      error = 'Could not change protection. Try again.';
      return false;
    } finally {
      busy = false;
      notifyListeners();
    }
  }

  Future<void> requestPermissions() async {
    status = await _platform.requestPermissions();
    notifyListeners();
  }

  Future<void> ignoreAlert(AlertRecord a) async {
    await _platform.ignore(a.ids);
    alerts = [for (final x in alerts) x.ids.any(a.ids.contains) ? x.asIgnored() : x];
    notifyListeners();
  }

  Future<void> clearData({bool includeIgnored = false}) async {
    await _platform.clearData(includeIgnored: includeIgnored);
    await load();
  }
}
