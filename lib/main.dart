import 'package:flutter/material.dart';
import 'package:theme/theme.dart';

import 'screens/home_screen.dart';
import 'shadow/shadow_platform.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(ShadowApp(platform: MethodChannelShadowPlatform()));
}

class ShadowApp extends StatelessWidget {
  const ShadowApp({super.key, required this.platform});

  final ShadowPlatform platform;

  @override
  Widget build(BuildContext context) {
    return UiApp(
      title: 'Shadow',
      home: HomeScreen(platform: platform),
    );
  }
}
