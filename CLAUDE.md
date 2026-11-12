# Shadow (package: shadow / com.itsash.shadow)

Anti-stalking helper: warns when an unknown Bluetooth item tracker seems to travel with the user.
Native Kotlin scans BLE and runs the background service; Flutter is the UI (Home, Scan now, Finder,
Warnings, Settings, Help).

## Code layout

- `android/app/src/main/kotlin/com/itsash/shadow/`
  - `AdParser.kt`: pure parser, raw advertisement bytes -> `Adv` (manufacturer data, 16-bit service data / UUIDs, name).
  - `TrackerFormats.kt`: **the one format table** (`TrackerType`, `classify`, generic rule). Comments mark
    which formats are certain and which are not. Tests in `TrackerFormatsTest.kt`.
  - `Ledger.kt`: `AlertConfig` (all thresholds: 30 min, 2 places, 400 m, 2 h without location, 45 min gap,
    3 sightings), `Ledger` (matches sightings by payload key then address, episodes, `takeDue`, ignore list),
    haversine `Geo`. Tests in `LedgerTest.kt`.
  - `ScanSession.kt`: aggregates one foreground scan for Scan now (pure). Tests in `ScanSessionTest.kt`.
  - `BleScanner.kt`: platform scanner wrapper; `trackerFilters()` are the ScanFilters for background scans
    (Android 8.1+ ignores unfiltered scans with the screen off).
  - `ProtectionService.kt`: foreground service (connectedDevice, plus location when granted). An inexact
    allow-while-idle alarm fires every 3 min, the service scans 20 s under a short wake lock, records
    sightings with the freshest location (own NETWORK/PASSIVE updates or last known, max 15 min old) and
    notifies on `Ledger.takeDue()`. `BootReceiver` restarts it. Only alertable types are recorded.
  - `ShadowState.kt`: process-wide ledger + alert list saved to `files/shadow_state.json`; `ShadowPrefs`.
  - `Perms.kt`, `MainActivity.kt`: permissions and channels (below).
- `lib/shadow/`: `models`, `shadow_platform` (interface for fakes), `protection_store`, `finder_math`
  (pure, tested), `format` (pure, tested). `lib/screens/`: home, scan, finder, history, settings (ends with
  `UiAppearanceSection`, `UiUpdateSection(storeId: 'shadow')`, `UiDeveloperSection()`), help.
- `android/app/src/main/res/drawable/ic_stat_shadow.xml`: white notification icon.
- `tools/icon.py` regenerates the pixel-art icon SVGs.

## Channel contract

MethodChannel `com.itsash.shadow/control`: `getConfig`, `setProtection({value})`, `status`,
`requestPermissions`, `openPermission({name: app|bluetooth|battery})`, `alerts`, `ignore({ids})`,
`ignoredCount`, `clearIgnored`, `clearData({includeIgnored})`. EventChannel `com.itsash.shadow/scan`
(args `{seconds}`) emits one map per second `{elapsedMs, totalMs, done, devices[]}`; cancelling stops the scan.
Ignore ids are `k:<payload key>` or `a:<address>`. Keep Kotlin `MainActivity.kt` and `lib/shadow/models.dart` in sync.

## Limits to keep honest (also in the Help screen)

- AirTags rotate address/key; matching is by key then address, so a double change splits one tracker in two.
- Doze and OEM battery savers can delay or kill background scans. Location may be missing when the service
  is restarted from boot (Android restricts it); it falls back to connectedDevice only.
- Uncertain formats: Apple type 0x07, Samsung FD5A byte layout (key is a hint), Chipolo FE33, FMDN frame
  layout, Fast Pair FE2C. Verify with real advertisements before relying on them.

## Commands

- `make run`, `make build`, `make test` (analyze + Dart tests), `make test-native` (Kotlin JVM tests; needs one
  `make build` first), `make clean`.
- Icon: `python3 -I tools/icon.py assets/icon`, then `rsvg-convert -w 1024 -h 1024` each SVG to `*-1024.png`,
  then `dart run flutter_launcher_icons`.

## Conventions

- minSdk 26. UI only from `../theme` (read `../theme/COMPONENTS.md`).
- Only network use: `UiUpdateSection` in Settings. No analytics. Data never leaves the phone.

## Release (Ash Store)

- `store.json` (id `shadow`, repo its-ash/shadow), `store.mk`, `index.html` + `CNAME` (`shadow.itsash.in`).
  See `../ash-store/store-kit/STORE.md`.
- `make deploy` = bump version, build, commit, tag `vX.Y.Z`, push, GitHub release with `shadow.apk`, topics,
  homepage, Pages. Never create releases, tags or asset names by hand.
- Do not edit `store.mk` here. Edit `../ash-store/store-kit/store.mk`, then run `make store-sync` at the
  workspace root.
