import 'package:flutter_test/flutter_test.dart';
import 'package:shadow/shadow/finder_math.dart';

void main() {
  test('first sample is taken as is, later samples are smoothed', () {
    final s = RssiSmoother(alpha: 0.5);
    expect(s.add(-80), -80);
    expect(s.add(-60), -70);
    expect(s.add(-60), -65);
    expect(s.value, -65);
  });

  test('one wild sample moves the value only a little', () {
    final s = RssiSmoother(alpha: 0.3);
    for (var i = 0; i < 10; i++) {
      s.add(-70);
    }
    s.add(-40);
    expect(s.value!, closeTo(-61, 0.01));
  });

  test('history is capped and reset clears it', () {
    final s = RssiSmoother();
    for (var i = 0; i < 100; i++) {
      s.add(-70);
    }
    expect(s.history.length, RssiSmoother.maxHistory);
    s.reset();
    expect(s.value, isNull);
    expect(s.history, isEmpty);
  });

  test('trend needs six samples and has a dead band', () {
    final s = RssiSmoother(alpha: 1);
    for (var i = 0; i < 5; i++) {
      s.add(-80);
    }
    expect(s.trend, 0);
    s.add(-80);
    expect(s.trend, 0);
    for (final v in [-70, -66, -62]) {
      s.add(v);
    }
    expect(s.trend, 1);
    for (final v in [-70, -75, -80, -85, -90, -95]) {
      s.add(v);
    }
    expect(s.trend, -1);
  });

  test('heat levels', () {
    expect(heatFor(-30), Heat.onTop);
    expect(heatFor(-50), Heat.hot);
    expect(heatFor(-65), Heat.warm);
    expect(heatFor(-80), Heat.cool);
    expect(heatFor(-95), Heat.cold);
    expect(heatFor(-60), Heat.hot);
    expect(heatFor(-85), Heat.cool);
  });

  test('heat fraction is clamped to 0..1', () {
    expect(heatFraction(-120), 0);
    expect(heatFraction(-70), closeTo(0.5, 1e-9));
    expect(heatFraction(-20), 1);
  });

  test('every heat has text', () {
    for (final h in Heat.values) {
      expect(h.label, isNotEmpty);
      expect(h.hint, isNotEmpty);
    }
    expect(distanceHint(-40), contains('1 m'));
    expect(distanceHint(-95), contains('20 m'));
  });
}
