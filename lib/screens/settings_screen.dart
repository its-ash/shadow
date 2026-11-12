import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../shadow/protection_store.dart';
import 'help_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key, required this.store});

  final ProtectionStore store;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  int _ignored = 0;

  ProtectionStore get _store => widget.store;

  @override
  void initState() {
    super.initState();
    _store.load();
    _loadIgnored();
  }

  Future<void> _loadIgnored() async {
    final n = await _store.platform.ignoredCount();
    if (mounted) setState(() => _ignored = n);
  }

  Future<void> _resetIgnored() async {
    final ok = await UiDialog.confirm(
      context,
      title: 'Warn about ignored trackers again?',
      message: 'Shadow will stop ignoring the $_ignored identifier${_ignored == 1 ? '' : 's'} on the list.',
      confirmLabel: 'Reset',
    );
    if (!ok) return;
    await _store.platform.clearIgnored();
    await _store.load();
    await _loadIgnored();
  }

  Future<void> _clearAll() async {
    final ok = await UiDialog.confirm(
      context,
      title: 'Delete all data?',
      message: 'Removes sightings, warnings and the ignore list from this phone.',
      confirmLabel: 'Delete',
      destructive: true,
    );
    if (!ok) return;
    await _store.clearData(includeIgnored: true);
    await _loadIgnored();
  }

  @override
  Widget build(BuildContext context) {
    return UiScaffold(
      appBar: const UiAppBar(title: 'Settings'),
      scrollable: true,
      body: ListenableBuilder(
        listenable: _store,
        builder: (context, _) {
          if (!_store.loaded) return const Center(child: UiSpinner());
          final c = _store.config;
          final s = _store.status;
          return UiColumn(
            gap: UiSpace.lg,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              UiListGroup(
                header: 'Background protection',
                footer: 'Runs a notification while it works. Alerts need Bluetooth, and location to see whether a '
                    'tracker moved with you.',
                children: [
                  UiSwitchTile(
                    'Watch for trackers',
                    subtitle: c.protection ? 'On' : 'Off',
                    icon: Icons.shield_rounded,
                    status: c.protection ? UiStatus.success : UiStatus.neutral,
                    value: c.protection,
                    onChanged: _store.busy ? null : (v) => _store.setProtection(v),
                  ),
                  UiListTile(
                    'Bluetooth scan',
                    subtitle: s.bluetoothPermission ? 'Allowed' : 'Not allowed. Tap to allow.',
                    icon: Icons.bluetooth_searching_rounded,
                    iconStatus: s.bluetoothPermission ? UiStatus.success : UiStatus.warning,
                    chevron: !s.bluetoothPermission,
                    onTap: s.bluetoothPermission ? null : _store.requestPermissions,
                  ),
                  UiListTile(
                    'Location',
                    subtitle: s.locationPermission ? 'Allowed while the app or protection runs' : 'Not allowed. Tap to allow.',
                    icon: Icons.place_rounded,
                    iconStatus: s.locationPermission ? UiStatus.success : UiStatus.warning,
                    chevron: !s.locationPermission,
                    onTap: s.locationPermission ? null : _store.requestPermissions,
                  ),
                  UiListTile(
                    'Notifications',
                    subtitle: s.notifications ? 'Allowed' : 'Not allowed. Tap to allow.',
                    icon: Icons.notifications_rounded,
                    iconStatus: s.notifications ? UiStatus.success : UiStatus.warning,
                    chevron: !s.notifications,
                    onTap: s.notifications ? null : _store.requestPermissions,
                  ),
                  UiListTile(
                    'Battery optimization',
                    subtitle: s.batteryOptimizationsIgnored ? 'Shadow is not restricted' : 'May stop background scans',
                    icon: Icons.battery_saver_rounded,
                    iconStatus: s.batteryOptimizationsIgnored ? UiStatus.success : UiStatus.warning,
                    chevron: !s.batteryOptimizationsIgnored,
                    onTap: s.batteryOptimizationsIgnored ? null : () => _store.platform.openPermission('battery'),
                  ),
                  UiListTile(
                    'Phone permission settings',
                    icon: Icons.settings_applications_rounded,
                    chevron: true,
                    onTap: () => _store.platform.openPermission('app'),
                  ),
                ],
              ),
              UiListGroup(
                header: 'Your data',
                footer: 'Everything stays on this phone. Nothing is uploaded.',
                children: [
                  UiListTile(
                    'Ignored trackers',
                    subtitle: 'Your own trackers, so they never trigger a warning.',
                    icon: Icons.notifications_off_rounded,
                    value: '$_ignored',
                    chevron: _ignored > 0,
                    onTap: _ignored > 0 ? _resetIgnored : null,
                  ),
                  UiListTile(
                    'Delete all data',
                    subtitle: 'Sightings, warnings and the ignore list.',
                    icon: Icons.delete_outline_rounded,
                    iconStatus: UiStatus.danger,
                    destructive: true,
                    onTap: _clearAll,
                  ),
                ],
              ),
              UiListGroup(
                header: 'Help',
                children: [
                  UiListTile(
                    'How Shadow works and its limits',
                    icon: Icons.help_outline_rounded,
                    chevron: true,
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(builder: (_) => const HelpScreen()),
                    ),
                  ),
                ],
              ),
              const UiAppearanceSection(),
              const UiUpdateSection(storeId: 'shadow'),
              const UiDeveloperSection(),
            ],
          );
        },
      ),
    );
  }
}
