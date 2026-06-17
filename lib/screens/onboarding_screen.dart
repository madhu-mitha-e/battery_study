import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:permission_handler/permission_handler.dart';
import '../services/background_service.dart';
import 'home_screen.dart';

class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final TextEditingController _cityController = TextEditingController();
  bool _agreed = false;
  bool _isLoading = false;

  Future<void> _startStudy() async {
    if (!_agreed) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please agree to participate first.')),
      );
      return;
    }

    if (_cityController.text.trim().isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter your city name.')),
      );
      return;
    }

    setState(() => _isLoading = true);

    await Permission.ignoreBatteryOptimizations.request();

    final prefs = await SharedPreferences.getInstance();
    final userId = 'USR_${DateTime.now().millisecondsSinceEpoch % 100000}';
    await prefs.setString('user_id', userId);
    await prefs.setString('city_name', _cityController.text.trim());
    await prefs.setBool('onboarding_done', true);
    await prefs.setBool('logging_active', true);

    await BackgroundService.startLogging();

    setState(() => _isLoading = false);

    if (mounted) {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (_) => const HomeScreen()),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 40),
              const Icon(
                Icons.battery_charging_full,
                size: 60,
                color: Colors.teal,
              ),
              const SizedBox(height: 24),
              const Text(
                'Battery Study',
                style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
              ),
              const Text(
                'SRM Institute of Science and Technology',
                style: TextStyle(fontSize: 14, color: Colors.grey),
              ),
              const SizedBox(height: 32),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.teal.shade50,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Text(
                  'This app collects battery level, temperature, '
                  'charging status, and city-level weather data for '
                  'a university research project at SRM.\n\n'
                  'No personal information such as contacts, messages, '
                  'or photos is collected. All data is anonymized.',
                  style: TextStyle(fontSize: 14, height: 1.6),
                ),
              ),
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.orange.shade50,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: Colors.orange.shade200),
                ),
                child: const Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Important setup step:',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: Colors.orange,
                      ),
                    ),
                    SizedBox(height: 8),
                    Text(
                      'After installation, please go to:\n'
                      'Settings → Battery → App Battery Saver\n'
                      '→ Battery Study → No Restrictions\n\n'
                      'This ensures the app can log data in background.',
                      style: TextStyle(fontSize: 13, height: 1.6),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                'What we collect:',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              _buildBullet('Battery %, temperature, voltage'),
              _buildBullet('Charging status and source'),
              _buildBullet('Charging current and capacity'),
              _buildBullet('Battery health percentage'),
              _buildBullet('Device brand and model'),
              _buildBullet('City-level ambient temperature'),
              _buildBullet('Screen on/off status'),
              const SizedBox(height: 24),
              TextField(
                controller: _cityController,
                decoration: InputDecoration(
                  labelText: 'Your city name',
                  hintText: 'e.g. Chennai',
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  prefixIcon: const Icon(Icons.location_city),
                ),
              ),
              const SizedBox(height: 20),
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Checkbox(
                    value: _agreed,
                    activeColor: Colors.teal,
                    onChanged: (val) => setState(() => _agreed = val ?? false),
                  ),
                  const Expanded(
                    child: Padding(
                      padding: EdgeInsets.only(top: 12),
                      child: Text(
                        'I agree to participate in this research study. '
                        'I understand my data will be used for academic '
                        'purposes only and I can uninstall anytime.',
                        style: TextStyle(fontSize: 13),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _startStudy,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.teal,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                  child: _isLoading
                      ? const CircularProgressIndicator(color: Colors.white)
                      : const Text(
                          'I Agree — Start Logging',
                          style: TextStyle(fontSize: 16),
                        ),
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                height: 50,
                child: OutlinedButton(
                  onPressed: () => Navigator.pop(context),
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10),
                    ),
                  ),
                  child: const Text('Exit'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBullet(String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          const Icon(Icons.check_circle_outline, size: 16, color: Colors.teal),
          const SizedBox(width: 8),
          Text(text, style: const TextStyle(fontSize: 13)),
        ],
      ),
    );
  }
}
