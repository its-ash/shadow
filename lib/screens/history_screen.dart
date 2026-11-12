import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import '../shadow/format.dart';
import '../shadow/models.dart';
import '../shadow/protection_store.dart';
import 'scan_screen.dart';

/// Every warning Shadow raised, with "Ignore this tracker" and a shortcut to Scan now.
class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key, required this.store});

  final ProtectionStore store;

  Future<void> _open(BuildContext context, AlertRecord a) async {
    final choice = await UiActionSheet.show<String>(
      context,
      title: a.label,
      message: alertSummary(a),
      actions: [
        const UiSheetAction('Find it with Scan now', value: 'scan', icon: Icons.radar_rounded),
        if (!a.ignored)
          const UiSheetAction(
            'Ignore this tracker',
            value: 'ignore',
            icon: Icons.notifications_off_rounded,
            subtitle: 'It is mine or belongs to someone I trust.',
          ),
      ],
    );
    if (!context.mounted) return;
    if (choice == 'scan') {
      await Navigator.of(context).push(
        MaterialPageRoute<void>(builder: (_) => ScanScreen(platform: store.platform)),
      );
    } else if (choice == 'ignore') {
      await store.ignoreAlert(a);
    }
  }

  Future<void> _clear(BuildContext context) async {
    final ok = await UiDialog.confirm(
      context,
      title: 'Clear history?',
      message: 'Deletes the warnings and everything Shadow remembered about nearby trackers. '
          'Trackers you chose to ignore stay ignored.',
      confirmLabel: 'Clear',
      destructive: true,
    );
    if (ok) await store.clearData();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: store,
      builder: (context, _) {
        final alerts = store.alerts;
        return UiScaffold(
          appBar: UiAppBar(
            title: 'Warnings',
            actions: [
              if (alerts.isNotEmpty)
                UiIconButton(
                  Icons.delete_outline_rounded,
                  tooltip: 'Clear history',
                  onPressed: () => _clear(context),
                ),
            ],
          ),
          scrollable: true,
          body: alerts.isEmpty
              ? const UiEmptyState(
                  title: 'No warnings',
                  message: 'When a tracker stays with you across time and places, it will be listed here.',
                  icon: Icons.verified_user_rounded,
                )
              : UiListGroup(
                  footer: 'Stored only on this phone.',
                  children: [
                    for (final a in alerts)
                      UiListTile(
                        a.label,
                        subtitle: alertSummary(a),
                        icon: a.ignored ? Icons.notifications_off_rounded : Icons.warning_amber_rounded,
                        iconStatus: a.ignored ? UiStatus.neutral : UiStatus.danger,
                        trailing: a.ignored ? const UiStatusPill('Ignored') : null,
                        chevron: !a.ignored,
                        onTap: () => _open(context, a),
                      ),
                  ],
                ),
        );
      },
    );
  }
}
