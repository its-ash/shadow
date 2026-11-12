/// Small text formatters (pure; tested in `test/format_test.dart`).
library;

import 'models.dart';

String formatDuration(int ms) {
  final mins = (ms / 60000).round();
  if (mins < 1) return 'under a minute';
  if (mins < 60) return '$mins min';
  final h = mins ~/ 60;
  final m = mins % 60;
  return m == 0 ? '$h h' : '$h h $m min';
}

const _months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/// `12 Mar, 14:05`
String formatStamp(DateTime t) =>
    '${t.day} ${_months[t.month - 1]}, ${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

/// One line for the history list: how long it was near you and across how many places.
String alertSummary(AlertRecord a) {
  final where = a.places >= 2 ? ', ${a.places} places' : ', location unknown';
  return '${formatStamp(a.ts)} · with you ${formatDuration(a.durationMs)}$where';
}

/// Qualitative signal strength from RSSI (dBm).
String signalWord(int rssi) {
  if (rssi >= -60) return 'Very strong';
  if (rssi >= -72) return 'Strong';
  if (rssi >= -85) return 'Medium';
  return 'Weak';
}
