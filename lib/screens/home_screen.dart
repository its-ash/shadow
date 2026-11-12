import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../shadow/format.dart';
import '../shadow/protection_store.dart';
import '../shadow/shadow_platform.dart';
import 'help_screen.dart';
import 'history_screen.dart';
import 'scan_screen.dart';
import 'settings_screen.dart';

/// Protection switch, Scan now and the latest alerts.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.platform});

  final ShadowPlatform platform;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late final ProtectionStore _store = ProtectionStore(widget.platform);
  late final AppLifecycleListener _lifecycle;

  @override
  void initState() {
    super.initState();
    _store.load();
    // Back from a system permission screen or a notification tap: re-read everything.
    _lifecycle = AppLifecycleListener(onResume: _store.load);
  }

  @override
  void dispose() {
    _lifecycle.dispose();
    _store.dispose();
    super.dispose();
  }

  Future<void> _push(Widget page) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => page));
    _store.load();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _store,
      builder: (context, _) {
        final c = _store.config;
        final s = _store.status;
        final recent = _store.alerts.where((a) => !a.ignored).take(3).toList();
        return UiScaffold(
          appBar: UiAppBar(
            title: 'Shadow',
            actions: [
              UiIconButton(
                Icons.help_outline_rounded,
                tooltip: 'How it works',
                onPressed: () => _push(const HelpScreen()),
              ),
              UiIconButton(
                Icons.settings_rounded,
                tooltip: 'Settings',
                onPressed: () => _push(SettingsScreen(store: _store)),
              ),
            ],
          ),
          scrollable: true,
          body: UiColumn(
            gap: UiSpace.lg,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              UiListGroup(
                header: 'Background protection',
                footer: 'Scans for trackers for 20 seconds every few minutes, even with the screen off, and warns you '
                    'when one keeps showing up as you move. Data stays on this phone.',
                children: [
                  UiSwitchTile(
                    'Watch for trackers',
                    subtitle: c.protection ? 'On. Checking every few minutes.' : 'Off',
                    icon: Icons.shield_rounded,
                    status: c.protection ? UiStatus.success : UiStatus.neutral,
                    value: c.protection,
                    onChanged: _store.busy ? null : (v) => _store.setProtection(v),
                  ),
                ],
              ),
              if (_store.error != null)
                UiAlert(
                  _store.error!,
                  status: UiStatus.warning,
                  action: UiButton(
                    s.bluetoothPermission ? 'Bluetooth settings' : 'App settings',
                    size: UiSize.sm,
                    variant: UiButtonVariant.secondary,
                    onPressed: () => widget.platform.openPermission(s.bluetoothPermission ? 'bluetooth' : 'app'),
                  ),
                ),
              if (c.protection && !s.locationPermission)
                UiAlert(
                  'Location is not allowed. Without it Shadow cannot tell if a tracker moved with you, so it waits '
                  'about 2 hours before it warns.',
                  status: UiStatus.info,
                  action: UiButton(
                    'Allow location',
                    size: UiSize.sm,
                    variant: UiButtonVariant.secondary,
                    onPressed: _store.requestPermissions,
                  ),
                ),
              if (c.protection && !s.notifications)
                UiAlert(
                  'Notifications are off, so you would not see a warning.',
                  status: UiStatus.warning,
                  action: UiButton(
                    'Allow notifications',
                    size: UiSize.sm,
                    variant: UiButtonVariant.secondary,
                    onPressed: _store.requestPermissions,
                  ),
                ),
              if (c.protection && !s.batteryOptimizationsIgnored)
                UiListGroup(
                  children: [
                    UiListTile(
                      'Keep running in the background',
                      subtitle: 'Some phones stop background apps. Exclude Shadow from battery optimization.',
                      icon: Icons.battery_saver_rounded,
                      iconStatus: UiStatus.warning,
                      chevron: true,
                      onTap: () => widget.platform.openPermission('battery'),
                    ),
                  ],
                ),
              UiButton(
                'Scan now',
                icon: Icons.radar_rounded,
                size: UiSize.lg,
                expand: true,
                onPressed: () => _push(ScanScreen(platform: widget.platform)),
              ),
              if (recent.isEmpty)
                const UiEmptyState(
                  title: 'No warnings',
                  message: 'No tracker has been flagged yet. Switch on protection to keep watching.',
                  icon: Icons.verified_user_rounded,
                  compact: true,
                )
              else
                UiListGroup(
                  header: 'Recent warnings',
                  children: [
                    for (final a in recent)
                      UiListTile(
                        a.label,
                        subtitle: alertSummary(a),
                        icon: Icons.warning_amber_rounded,
                        iconStatus: UiStatus.danger,
                        chevron: true,
                        onTap: () => _push(HistoryScreen(store: _store)),
                      ),
                  ],
                ),
              if (_store.alerts.isNotEmpty)
                UiButton(
                  'All warnings',
                  icon: Icons.history_rounded,
                  variant: UiButtonVariant.ghost,
                  onPressed: () => _push(HistoryScreen(store: _store)),
                ),
            ],
          ),
        );
      },
    );
  }
}
