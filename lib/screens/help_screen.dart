import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

/// Plain-language help. Be honest about what a phone can and cannot detect.
class HelpScreen extends StatelessWidget {
  const HelpScreen({super.key});

  static const sections = <(String, List<String>)>[
    (
      'What Shadow does',
      [
        'Item trackers such as AirTags can be hidden in a bag or car to follow someone. Shadow listens for their '
            'Bluetooth signals and warns you when the same tracker keeps showing up near you as you move.',
      ],
    ),
    (
      'Trackers it recognises',
      [
        'Apple Find My: AirTag and other Find My accessories.',
        'Samsung SmartTag, Tile, Chipolo.',
        'Google Find My Device network trackers.',
        'The SmartTag, Chipolo and Google formats are recognised from public descriptions and have not been '
            'checked against every model. Treat them as a best effort.',
        'Other devices that keep appearing are listed in Scan now as "Unknown device seen repeatedly". That list '
            'is a guess and also catches earbuds, watches and phones. It never raises a warning.',
      ],
    ),
    (
      'When Shadow warns you',
      [
        'Background protection scans for about 20 seconds every 3 minutes. It warns you when one tracker has been '
            'around for 30 minutes and was seen at 2 or more places at least 400 m apart.',
        'Without location permission it cannot see movement, so it waits 2 hours instead.',
        'A tracker that stays at home or at work while you stay there too does not trigger a warning.',
      ],
    ),
    (
      'Limits you should know',
      [
        'AirTags and other Find My trackers change their Bluetooth address often (about every 15 minutes near '
            'their owner, and about daily when away from the owner for a long time). Shadow matches a tracker by '
            'its address and by key bytes inside the signal. If both change in between, Shadow may count it as two '
            'trackers and warn later, or not at all.',
        'Android can delay or stop background scans to save battery. Set Shadow to "Unrestricted" or "Don\'t '
            'optimize" in battery settings, and keep Bluetooth on.',
        'A tracker that is switched off, out of battery, far away or inside a shielded bag cannot be heard.',
        'Location is only used to compare places on your phone. After a reboot, background location may be '
            'unavailable until you open Shadow once.',
        'Shadow is a helper, not a guarantee. Android and iPhone also have their own unknown tracker alerts; '
            'keep them on.',
      ],
    ),
    (
      'If you find a tracker',
      [
        'Use Scan now, tap the tracker and follow hot and cold. The signal is strongest when you are closest, but '
            'bodies and walls change it.',
        'If it is yours or belongs to someone you trust, open its warning and tap "Ignore this tracker".',
        'If you feel unsafe, go to a public place and contact the police. Do not confront anyone alone. '
            'Keep the tracker as evidence if you can.',
      ],
    ),
    (
      'Privacy',
      [
        'Sightings, locations and warnings are stored only on this phone. The only network call is the update '
            'check in Settings. There is no account and no analytics.',
      ],
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return UiScaffold(
      appBar: const UiAppBar(title: 'How it works'),
      scrollable: true,
      body: UiColumn(
        gap: UiSpace.lg,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (final (title, paragraphs) in sections)
            UiSection(
              title: title,
              child: UiCard(
                child: UiColumn(
                  gap: UiSpace.sm,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [for (final p in paragraphs) Text(p, style: context.text.bodyMedium)],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
