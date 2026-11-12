/// Pure helpers for the hot/cold finder (tested in `test/finder_math_test.dart`).
library;

/// How close the tracker feels. Thresholds are rough: RSSI depends on the phone, the tag and what is in between.
enum Heat { cold, cool, warm, hot, onTop }

extension HeatText on Heat {
  String get label => switch (this) {
        Heat.cold => 'Cold',
        Heat.cool => 'Cool',
        Heat.warm => 'Warm',
        Heat.hot => 'Hot',
        Heat.onTop => 'Very close',
      };

  String get hint => switch (this) {
        Heat.cold => 'Weak signal. Walk around slowly.',
        Heat.cool => 'Getting somewhere. Keep moving.',
        Heat.warm => 'Close. Check bags, pockets and the car.',
        Heat.hot => 'Very near. Look in small hiding places.',
        Heat.onTop => 'Within arm\'s reach.',
      };
}

/// Exponential smoothing of RSSI samples (dBm). Higher [alpha] reacts faster but jitters more.
class RssiSmoother {
  RssiSmoother({this.alpha = 0.3}) : assert(alpha > 0 && alpha <= 1);

  final double alpha;
  double? _value;
  final List<double> _history = [];

  double? get value => _value;

  /// The last smoothed values, oldest first (at most [maxHistory]).
  List<double> get history => List.unmodifiable(_history);

  static const maxHistory = 40;

  double add(int rssi) {
    final v = _value == null ? rssi.toDouble() : _value! + alpha * (rssi - _value!);
    _value = v;
    _history.add(v);
    if (_history.length > maxHistory) _history.removeAt(0);
    return v;
  }

  void reset() {
    _value = null;
    _history.clear();
  }

  /// Is the signal getting stronger (> 0), weaker (< 0) or flat (0) over the last few samples?
  /// Compares the mean of the last three smoothed values with the three before, with a 1.5 dB dead band.
  int get trend {
    if (_history.length < 6) return 0;
    double mean(List<double> l) => l.reduce((a, b) => a + b) / l.length;
    final n = _history.length;
    final diff = mean(_history.sublist(n - 3)) - mean(_history.sublist(n - 6, n - 3));
    if (diff > 1.5) return 1;
    if (diff < -1.5) return -1;
    return 0;
  }
}

Heat heatFor(double smoothedRssi) {
  if (smoothedRssi >= -45) return Heat.onTop;
  if (smoothedRssi >= -60) return Heat.hot;
  if (smoothedRssi >= -72) return Heat.warm;
  if (smoothedRssi >= -85) return Heat.cool;
  return Heat.cold;
}

/// 0 (-100 dBm or weaker) to 1 (-40 dBm or stronger), for a progress bar.
double heatFraction(double smoothedRssi) => ((smoothedRssi + 100) / 60).clamp(0.0, 1.0);

/// A rough distance label. Honest about being a guess.
String distanceHint(double smoothedRssi) => switch (heatFor(smoothedRssi)) {
      Heat.onTop => 'about 1 m or less',
      Heat.hot => 'about 1 to 3 m',
      Heat.warm => 'about 3 to 8 m',
      Heat.cool => 'about 8 to 20 m',
      Heat.cold => 'farther than 20 m, or blocked',
    };
