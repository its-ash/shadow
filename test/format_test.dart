import 'package:flutter_test/flutter_test.dart';
import 'package:shadow/shadow/format.dart';
import 'package:shadow/shadow/models.dart';

void main() {
  test('durations', () {
    expect(formatDuration(20000), 'under a minute');
    expect(formatDuration(42 * 60000), '42 min');
    expect(formatDuration(60 * 60000), '1 h');
    expect(formatDuration(135 * 60000), '2 h 15 min');
  });

  test('stamp', () {
    expect(formatStamp(DateTime(2026, 3, 9, 7, 5)), '9 Mar, 07:05');
  });

  test('signal words', () {
    expect(signalWord(-50), 'Very strong');
    expect(signalWord(-65), 'Strong');
    expect(signalWord(-80), 'Medium');
    expect(signalWord(-95), 'Weak');
  });

  test('alert summary and model parsing', () {
    final a = AlertRecord.fromMap({
      'ts': DateTime(2026, 3, 9, 7, 5).millisecondsSinceEpoch,
      'entryId': 't1',
      'type': 'tile',
      'label': 'Tile',
      'durationMs': 45 * 60000,
      'places': 2,
      'rssi': -70,
      'ignored': false,
      'ids': ['a:AA'],
    });
    expect(alertSummary(a), '9 Mar, 07:05 · with you 45 min, 2 places');
    expect(a.asIgnored().ignored, isTrue);
    expect(alertSummary(AlertRecord.fromMap({...{'ts': 0, 'entryId': 'x', 'type': 't', 'label': 'L', 'durationMs': 7200000, 'places': 0, 'rssi': 0}})), contains('location unknown'));
  });

  test('scan update parsing', () {
    final u = ScanUpdate.fromMap({
      'elapsedMs': 30000,
      'totalMs': 90000,
      'done': false,
      'devices': [
        {
          'id': 'a:AA',
          'address': 'AA',
          'type': 'generic',
          'label': 'Unknown',
          'certain': false,
          'rssi': -60,
          'avgRssi': -61.5,
          'count': 9,
          'ageMs': 800,
        },
      ],
    });
    expect(u.progress, closeTo(1 / 3, 1e-9));
    expect(u.devices.single.isGeneric, isTrue);
    expect(u.devices.single.avgRssi, -61.5);
  });
}
