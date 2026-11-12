/// Wire models for the channels in `MainActivity.kt`. Keep the keys in sync with it.
library;

class ShadowConfig {
  const ShadowConfig({this.protection = false, this.running = false});

  final bool protection;
  final bool running;

  factory ShadowConfig.fromMap(Map<Object?, Object?> m) =>
      ShadowConfig(protection: m['protection'] == true, running: m['running'] == true);
}

class PlatformStatus {
  const PlatformStatus({
    this.bluetoothSupported = true,
    this.bluetoothOn = true,
    this.bluetoothPermission = false,
    this.locationPermission = false,
    this.notifications = false,
    this.batteryOptimizationsIgnored = false,
  });

  final bool bluetoothSupported;
  final bool bluetoothOn;
  final bool bluetoothPermission;
  final bool locationPermission;
  final bool notifications;
  final bool batteryOptimizationsIgnored;

  factory PlatformStatus.fromMap(Map<Object?, Object?> m) => PlatformStatus(
        bluetoothSupported: m['bluetoothSupported'] != false,
        bluetoothOn: m['bluetoothOn'] == true,
        bluetoothPermission: m['bluetoothPermission'] == true,
        locationPermission: m['locationPermission'] == true,
        notifications: m['notifications'] == true,
        batteryOptimizationsIgnored: m['batteryOptimizationsIgnored'] == true,
      );
}

/// A device in the Scan now list.
class DetectedDevice {
  const DetectedDevice({
    required this.id,
    required this.address,
    required this.type,
    required this.label,
    required this.certain,
    required this.rssi,
    required this.avgRssi,
    required this.count,
    required this.ageMs,
  });

  /// `k:<payload key>` or `a:<address>`; also what the ignore list stores.
  final String id;
  final String address;

  /// Wire name of the tracker type, for example `apple_find_my` or `generic`.
  final String type;
  final String label;

  /// False for low-confidence classes (generic, Fast Pair, Apple setup mode).
  final bool certain;
  final int rssi;
  final double avgRssi;
  final int count;
  final int ageMs;

  bool get isGeneric => type == 'generic';

  factory DetectedDevice.fromMap(Map<Object?, Object?> m) => DetectedDevice(
        id: m['id'] as String,
        address: m['address'] as String,
        type: m['type'] as String,
        label: m['label'] as String,
        certain: m['certain'] == true,
        rssi: (m['rssi'] as num).toInt(),
        avgRssi: (m['avgRssi'] as num).toDouble(),
        count: (m['count'] as num).toInt(),
        ageMs: (m['ageMs'] as num).toInt(),
      );
}

class ScanUpdate {
  const ScanUpdate({required this.elapsedMs, required this.totalMs, required this.done, required this.devices});

  final int elapsedMs;
  final int totalMs;
  final bool done;
  final List<DetectedDevice> devices;

  double get progress => totalMs == 0 ? 0 : (elapsedMs / totalMs).clamp(0, 1).toDouble();

  factory ScanUpdate.fromMap(Map<Object?, Object?> m) => ScanUpdate(
        elapsedMs: (m['elapsedMs'] as num).toInt(),
        totalMs: (m['totalMs'] as num).toInt(),
        done: m['done'] == true,
        devices: [for (final d in (m['devices'] as List<Object?>)) DetectedDevice.fromMap(d as Map<Object?, Object?>)],
      );
}

class AlertRecord {
  const AlertRecord({
    required this.ts,
    required this.entryId,
    required this.type,
    required this.label,
    required this.durationMs,
    required this.places,
    required this.rssi,
    required this.ignored,
    required this.ids,
  });

  final DateTime ts;
  final String entryId;
  final String type;
  final String label;
  final int durationMs;
  final int places;
  final int rssi;
  final bool ignored;
  final List<String> ids;

  AlertRecord asIgnored() => AlertRecord(
        ts: ts,
        entryId: entryId,
        type: type,
        label: label,
        durationMs: durationMs,
        places: places,
        rssi: rssi,
        ignored: true,
        ids: ids,
      );

  factory AlertRecord.fromMap(Map<Object?, Object?> m) => AlertRecord(
        ts: DateTime.fromMillisecondsSinceEpoch((m['ts'] as num).toInt()),
        entryId: m['entryId'] as String,
        type: m['type'] as String,
        label: m['label'] as String,
        durationMs: (m['durationMs'] as num).toInt(),
        places: (m['places'] as num).toInt(),
        rssi: (m['rssi'] as num).toInt(),
        ignored: m['ignored'] == true,
        ids: [for (final i in (m['ids'] as List<Object?>? ?? const [])) i as String],
      );
}
