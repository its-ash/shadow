import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:theme/theme.dart';

import '../shadow/format.dart';
import '../shadow/models.dart';
import '../shadow/shadow_platform.dart';
import 'finder_screen.dart';

/// "Scan now": a 90 second foreground scan listing trackers nearby.
class ScanScreen extends StatefulWidget {
  const ScanScreen({super.key, required this.platform, this.seconds = 90});

  final ShadowPlatform platform;
  final int seconds;

  @override
  State<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends State<ScanScreen> {
  StreamSubscription<ScanUpdate>? _sub;
  ScanUpdate? _update;
  String? _error;
  bool _running = false;

  @override
  void initState() {
    super.initState();
    _start();
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  Future<void> _start() async {
    await _sub?.cancel();
    if (!mounted) return;
    setState(() {
      _error = null;
      _running = true;
      _update = null;
    });
    var status = await widget.platform.status();
    if (!status.bluetoothPermission) status = await widget.platform.requestPermissions();
    if (!mounted) return;
    if (!status.bluetoothSupported) return _fail('This phone has no Bluetooth LE.');
    if (!status.bluetoothPermission) return _fail('Allow Nearby devices (Bluetooth) for Shadow in the app settings.');
    if (!status.bluetoothOn) return _fail('Turn Bluetooth on, then scan again.');
    _sub = widget.platform.scan(seconds: widget.seconds).listen(
      (u) {
        if (!mounted) return;
        setState(() {
          _update = u;
          _running = !u.done;
        });
      },
      onError: (Object e) {
        if (!mounted) return;
        final msg = e is PlatformException ? e.message : null;
        _fail(msg == 'bluetooth_off' ? 'Turn Bluetooth on, then scan again.' : 'The scan could not start.');
      },
    );
  }

  void _fail(String message) {
    setState(() {
      _error = message;
      _running = false;
    });
  }

  Future<void> _openFinder(DetectedDevice d) async {
    await _sub?.cancel();
    _sub = null;
    if (!mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => FinderScreen(platform: widget.platform, device: d)),
    );
    if (mounted) _start();
  }

  Future<void> _ignore(DetectedDevice d) async {
    final ok = await UiDialog.confirm(
      context,
      title: 'Ignore this tracker?',
      message: 'Use this for your own tracker or a family member\'s. Shadow will not list it or warn about it again.',
      confirmLabel: 'Ignore',
    );
    if (!ok || !mounted) return;
    await widget.platform.ignore([d.id]);
    if (!mounted) return;
    final u = _update;
    if (u == null) return;
    setState(() => _update = ScanUpdate(
          elapsedMs: u.elapsedMs,
          totalMs: u.totalMs,
          done: u.done,
          devices: [for (final x in u.devices) if (x.id != d.id) x],
        ));
  }

  @override
  Widget build(BuildContext context) {
    final u = _update;
    final devices = u?.devices ?? const <DetectedDevice>[];
    final trackers = devices.where((d) => !d.isGeneric).toList();
    final generic = devices.where((d) => d.isGeneric).toList();
    return UiScaffold(
      appBar: const UiAppBar(title: 'Scan now'),
      scrollable: true,
      body: UiColumn(
        gap: UiSpace.lg,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_error != null)
            UiAlert(
              _error!,
              status: UiStatus.warning,
              action: UiButton('Try again', size: UiSize.sm, variant: UiButtonVariant.secondary, onPressed: _start),
            )
          else ...[
            UiProgressBar(
              _running ? u?.progress : 1,
              label: _running ? 'Scanning, keep the phone with you' : 'Scan finished',
            ),
            if (!_running)
              UiButton('Scan again', icon: Icons.refresh_rounded, variant: UiButtonVariant.secondary, onPressed: _start),
          ],
          if (u != null && trackers.isEmpty && generic.isEmpty && !_running)
            const UiEmptyState(
              title: 'No trackers heard',
              message: 'A tracker that is switched off, far away or in a shielded bag cannot be heard. '
                  'Some trackers only send every few seconds, so scan again or move around.',
              icon: Icons.search_off_rounded,
              compact: true,
            ),
          if (trackers.isNotEmpty)
            UiListGroup(
              header: 'Trackers nearby',
              footer: 'Tap one to find it. Long press to ignore it if it is yours.',
              children: [for (final d in trackers) _tile(d)],
            ),
          if (generic.isNotEmpty)
            UiListGroup(
              header: 'Unknown devices seen repeatedly',
              footer: 'Low confidence. Earbuds, watches and other phones look like this too.',
              children: [for (final d in generic) _tile(d)],
            ),
        ],
      ),
    );
  }

  Widget _tile(DetectedDevice d) => UiListTile(
        d.label,
        subtitle: '${signalWord(d.rssi)} (${d.rssi} dBm) · heard ${d.count}×${d.certain ? '' : ' · low confidence'}',
        icon: d.isGeneric ? Icons.help_outline_rounded : Icons.sensors_rounded,
        iconStatus: d.isGeneric || !d.certain ? UiStatus.neutral : UiStatus.warning,
        chevron: true,
        onTap: () => _openFinder(d),
        onLongPress: () => _ignore(d),
      );
}
