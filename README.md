# Shadow

Warns when an unknown Bluetooth tracker, such as an AirTag, seems to travel with you.

**[Website](https://shadow.itsash.in)** · **[Download APK](https://github.com/its-ash/shadow/releases/latest/download/shadow.apk)** · Get it in **[Ash Store](https://store.itsash.in)**

## Features

- **Scan now**: a 90 second Bluetooth LE scan that lists the item trackers around you by type, signal
  strength and how often each was heard. Recognises Apple Find My (AirTag and accessories), Samsung
  SmartTag, Tile, Chipolo and Google Find My Device trackers. Unknown devices that keep appearing are
  listed separately as low confidence.
- **Hot/cold finder**: tap a tracker and follow a smoothed signal meter (cold, cool, warm, hot, very
  close) with a trend, to find it physically.
- **Background protection** (optional): a foreground service scans for 20 seconds every 3 minutes with
  tracker filters, so it also works with the screen off. It warns you when one tracker has been around
  for 30 minutes and was seen at two or more places at least 400 m apart. Without location permission it
  waits 2 hours instead. Starts again after a reboot.
- **Warnings history** with "Ignore this tracker" for your own trackers, and delete-all.
- **Honest help screen** about what a phone can and cannot detect (address rotation, battery limits,
  shielded bags).
- **Private**: sightings and locations stay on the phone. No account, no analytics. The only network call
  is the update check in Settings.

## Install

- **Ash Store**: install [Ash Store](https://store.itsash.in) once, then install and update Shadow from it.
- **APK**: download the [latest APK](https://github.com/its-ash/shadow/releases/latest/download/shadow.apk) on your phone and open it. Allow your browser to install unknown apps if Android asks.

Requires Android 8.0 (API 26) or later. Allow Nearby devices (Bluetooth), Location and Notifications. For
background protection also set Shadow to "Unrestricted" or "Don't optimize" in battery settings.

## Develop

Needs Flutter ≥ 3.44 and the shared theme package at `../theme`.

```bash
make run           # run on a connected device
make test          # flutter analyze + flutter test
make test-native   # JVM tests for the tracker formats, the alert rules and the scan session
make build         # release APK
make deploy        # bump version, build, tag, push, GitHub release (see store.mk)
```

## Release

`make deploy` follows the Ash Store kit (`store.mk`, `store.json`): it bumps the
version in `pubspec.yaml`, builds, commits, tags `vX.Y.Z`, pushes, and publishes
a GitHub release with `shadow.apk`. Ash Store and the website pick up the new
version from that release.

## Project layout

```
lib/
├── main.dart
├── shadow/
│   ├── models.dart             # channel models (mirror MainActivity.kt)
│   ├── shadow_platform.dart    # platform interface + MethodChannel/EventChannel
│   ├── protection_store.dart   # switch, permissions, alerts
│   ├── finder_math.dart        # RSSI smoothing, heat levels
│   └── format.dart
└── screens/                    # home, scan, finder, history, settings, help
android/app/src/main/kotlin/com/itsash/shadow/
├── AdParser.kt, TrackerFormats.kt   # pure: advertisement bytes -> tracker type and key
├── Ledger.kt, ScanSession.kt        # pure: alert rules, scan aggregation
├── BleScanner.kt, ProtectionService.kt, ShadowState.kt, Perms.kt, MainActivity.kt
android/app/src/test/kotlin/...      # JVM tests
```

## License

No license file is included yet.

Built by [itsash.in](https://itsash.in).
