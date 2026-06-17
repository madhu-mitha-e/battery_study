import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:workmanager/workmanager.dart';
import 'screens/onboarding_screen.dart';
import 'screens/home_screen.dart';
import 'services/background_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Workmanager().initialize(
    callbackDispatcher,
    isInDebugMode: false,
  );

  final prefs = await SharedPreferences.getInstance();
  final bool onboardingDone = prefs.getBool('onboarding_done') ?? false;
  final bool loggingActive = prefs.getBool('logging_active') ?? true;

  if (onboardingDone && loggingActive) {
    await BackgroundService.startLogging();
  }

  runApp(BatteryStudyApp(onboardingDone: onboardingDone));
}

class BatteryStudyApp extends StatelessWidget {
  final bool onboardingDone;
  const BatteryStudyApp({super.key, required this.onboardingDone});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Battery Study',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
        useMaterial3: true,
      ),
      home: onboardingDone ? const HomeScreen() : const OnboardingScreen(),
    );
  }
}