import 'dart:async';

import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../shadow/finder_math.dart';
import '../shadow/models.dart';
import '../shadow/shadow_platform.dart';

/// Hot/cold finder: follows one device by id and shows how the smoothed signal changes as you walk.
class FinderScreen extends StatefulWidget {
  const FinderScreen({super.key, required this.platform, required this.device});

  final ShadowPlatform platform;
  final DetectedDevice device;

  @override
  State<FinderScreen> createState() => _FinderScreenState();
}

class _FinderScreenState extends State<FinderScreen> {
  final RssiSmoother _smoother = RssiSmoother(alpha: 0.35);
  StreamSubscription<ScanUpdate>? _sub;
  int? _lastCount;
  int _ageMs = 0;
  String? _error;

  @override
  void initState() {
    super.initState();
    _sub = widget.platform.scan(seconds: 600).listen(
      (u) {
        if (!mounted) return;
        final d = u.devices.where((x) => x.id == widget.device.id).firstOrNull;
        setState(() {
          if (d == null) {
            _ageMs = 99999;
          } else {
            _ageMs = d.ageMs;
            // Only a new advertisement counts as a sample; a repeated value would drag the average.
            if (d.count != _lastCount) _smoother.add(d.rssi);
            _lastCount = d.count;
          }
        });
      },
      onError: (Object e) {
        if (mounted) setState(() => _error = 'The scan stopped. Check Bluetooth and try again.');
      },
    );
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final v = _smoother.value;
    final lost = v != null && _ageMs > 6000;
    final heat = v == null ? null : heatFor(v);
    final status = switch (heat) {
      null => UiStatus.neutral,
      Heat.cold => UiStatus.info,
      Heat.cool => UiStatus.info,
      Heat.warm => UiStatus.warning,
      Heat.hot => UiStatus.danger,
      Heat.onTop => UiStatus.danger,
    };
    final trend = _smoother.trend;
    return UiScaffold(
      appBar: const UiAppBar(title: 'Find tracker'),
      scrollable: true,
      body: UiColumn(
        gap: UiSpace.xl,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(widget.device.label, style: context.text.titleMedium, textAlign: TextAlign.center),
          if (_error != null)
            UiAlert(_error!, status: UiStatus.warning)
          else if (v == null)
            const UiEmptyState(
              title: 'Listening',
              message: 'Waiting for the tracker. Hold the phone steady for a few seconds.',
              icon: Icons.hearing_rounded,
              compact: true,
            )
          else ...[
            Center(
              child: UiStatusPill(
                lost ? 'Signal lost' : heat!.label,
                status: lost ? UiStatus.neutral : status,
                icon: lost ? Icons.signal_wifi_off_rounded : Icons.local_fire_department_rounded,
              ),
            ),
            UiProgressBar(heatFraction(v), status: status, height: 14),
            Text(
              lost ? 'Not heard for a while. Go back to where you last heard it.' : heat!.hint,
              style: context.text.bodyLarge,
              textAlign: TextAlign.center,
            ),
            UiListGroup(
              children: [
                UiListTile(
                  'Trend',
                  icon: trend > 0
                      ? Icons.trending_up_rounded
                      : trend < 0
                          ? Icons.trending_down_rounded
                          : Icons.trending_flat_rounded,
                  value: trend > 0
                      ? 'Getting closer'
                      : trend < 0
                          ? 'Moving away'
                          : 'Steady',
                ),
                UiListTile('Smoothed signal', icon: Icons.network_wifi_rounded, value: '${v.round()} dBm'),
                UiListTile('Rough distance', icon: Icons.straighten_rounded, value: distanceHint(v)),
              ],
            ),
          ],
          Text(
            'Bodies, walls and bags change the signal, so treat distance as a guess. Walk slowly and turn the phone: '
            'the signal is strongest when you are closest.',
            style: context.text.bodySmall,
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
