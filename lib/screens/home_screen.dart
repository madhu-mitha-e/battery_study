import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:async';
import '../services/background_service.dart';
import '../services/battery_service.dart';
import '../services/weather_service.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final BatteryService _batteryService = BatteryService();
  final WeatherService _weatherService = WeatherService();
  Timer? _timer;
  Timer? _weatherTimer;

  String _userId = '';
  String _cityName = '';
  bool _loggingActive = true;

  int _batterySoc = 0;
  double _batteryTemp = 0.0;
  int _batteryVoltage = 0;
  String _chargingStatus = '';
  bool _isCharging = false;
  bool _screenOn = true;

  double _ambientTemp = 0.0;
  double _humidity = 0.0;
  bool _weatherLoaded = false;

  int _totalLogs = 0;
  int _uploadedLogs = 0;
  String _lastLogTime = 'Not yet';
  bool _manualLogging = false;
  bool _uploading = false;

  @override
  void initState() {
    super.initState();
    _loadPrefs();
    _refreshData();
    _timer = Timer.periodic(
      const Duration(minutes: 1),
      (_) => _refreshData(),
    );
    _weatherTimer = Timer.periodic(
      const Duration(minutes: 30),
      (_) => _fetchWeather(),
    );
  }

  Future<void> _loadPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      _userId = prefs.getString('user_id') ?? '';
      _cityName = prefs.getString('city_name') ?? '';
      _loggingActive = prefs.getBool('logging_active') ?? true;
    });
    await _fetchWeather();
  }

  Future<void> _fetchWeather() async {
    if (_cityName.isEmpty) return;
    try {
      final weather = await _weatherService.getWeatherData(_cityName);
      setState(() {
        _ambientTemp = weather['ambientTemperatureC'];
        _humidity = weather['humidity'];
        _weatherLoaded = true;
      });
    } catch (e) {
      print('Weather error: $e');
    }
  }

  Future<void> _refreshData() async {
    try {
      final batteryData = await _batteryService.getBatteryData();
      final totalLogs = await BackgroundService.getTotalLogs();
      final uploadedLogs = await BackgroundService.getUploadedCount();
      setState(() {
        _batterySoc = batteryData['batterySoc'];
        _batteryTemp = batteryData['batteryTemperatureC'];
        _batteryVoltage = batteryData['batteryVoltageMv'];
        _chargingStatus = batteryData['chargingStatus'];
        _isCharging = batteryData['isCharging'];
        _screenOn = batteryData['screenOn'];
        _totalLogs = totalLogs;
        _uploadedLogs = uploadedLogs;
      });
    } catch (e) {
      print('Refresh error: $e');
    }
  }

  Future<void> _toggleLogging() async {
    final prefs = await SharedPreferences.getInstance();
    if (_loggingActive) {
      await BackgroundService.stopLogging();
      await prefs.setBool('logging_active', false);
      setState(() => _loggingActive = false);
    } else {
      await BackgroundService.startLogging();
      await prefs.setBool('logging_active', true);
      setState(() => _loggingActive = true);
    }
  }

  Future<void> _manualLog() async {
    setState(() => _manualLogging = true);
    try {
      await BackgroundService.triggerManualLog();
      await Future.delayed(const Duration(milliseconds: 500));
      final totalLogs = await BackgroundService.getTotalLogs();
      final uploadedLogs = await BackgroundService.getUploadedCount();
      setState(() {
        _totalLogs = totalLogs;
        _uploadedLogs = uploadedLogs;
        _lastLogTime =
            '${DateTime.now().hour}:${DateTime.now().minute.toString().padLeft(2, '0')}';
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Log saved!'),
            backgroundColor: Colors.teal,
            duration: Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
    setState(() => _manualLogging = false);
  }

  Future<void> _uploadNow() async {
    setState(() => _uploading = true);
    try {
      final success = await BackgroundService.uploadNow();
      await _refreshData();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              success
                  ? 'Uploaded successfully!'
                  : 'Upload failed — check WiFi',
            ),
            backgroundColor: success ? Colors.teal : Colors.red,
            duration: const Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
    setState(() => _uploading = false);
  }

  @override
  void dispose() {
    _timer?.cancel();
    _weatherTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        title: const Text('Battery Study'),
        backgroundColor: Colors.teal,
        foregroundColor: Colors.white,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () {
              _refreshData();
              _fetchWeather();
            },
          ),
          IconButton(
            icon: const Icon(Icons.info_outline),
            onPressed: _showInfo,
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refreshData,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildStatusCard(),
              const SizedBox(height: 16),
              _buildBatteryCard(),
              const SizedBox(height: 16),
              _buildWeatherCard(),
              const SizedBox(height: 16),
              _buildLoggingInfoCard(),
              const SizedBox(height: 16),
              _buildStatsCard(),
              const SizedBox(height: 16),
              _buildManualLogButton(),
              const SizedBox(height: 12),
              _buildUploadButton(),
              const SizedBox(height: 12),
              _buildToggleButton(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildStatusCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _loggingActive ? Colors.teal : Colors.grey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Icon(
            _loggingActive
                ? Icons.wifi_tethering
                : Icons.wifi_tethering_off,
            color: Colors.white,
            size: 32,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _loggingActive ? 'Logging Active' : 'Logging Paused',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  'ID: $_userId  |  City: $_cityName',
                  style: const TextStyle(
                    color: Colors.white70,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBatteryCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.grey.shade200,
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Battery Status',
            style: TextStyle(
              fontWeight: FontWeight.bold,
              fontSize: 16,
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildStatItem(
                Icons.battery_full,
                '$_batterySoc%',
                'Charge',
                Colors.teal,
              ),
              _buildStatItem(
                Icons.thermostat,
                '${_batteryTemp.toStringAsFixed(1)}°C',
                'Battery temp',
                Colors.orange,
              ),
              _buildStatItem(
                Icons.electric_bolt,
                '${_batteryVoltage}mV',
                'Voltage',
                Colors.purple,
              ),
              _buildStatItem(
                _isCharging ? Icons.power : Icons.power_off,
                _chargingStatus,
                'Status',
                _isCharging ? Colors.green : Colors.red,
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                _screenOn
                    ? Icons.phone_android
                    : Icons.phone_locked,
                size: 16,
                color: Colors.grey,
              ),
              const SizedBox(width: 4),
              Text(
                _screenOn ? 'Screen On' : 'Screen Off',
                style: const TextStyle(
                  fontSize: 12,
                  color: Colors.grey,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildWeatherCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.blue.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.blue.shade100),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Ambient Weather',
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                ),
              ),
              Text(
                _weatherLoaded ? 'Live' : 'Loading...',
                style: TextStyle(
                  fontSize: 12,
                  color: _weatherLoaded ? Colors.green : Colors.grey,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildStatItem(
                Icons.wb_sunny,
                '${_ambientTemp.toStringAsFixed(1)}°C',
                'Ambient temp',
                Colors.orange,
              ),
              _buildStatItem(
                Icons.water_drop,
                '${_humidity.toStringAsFixed(0)}%',
                'Humidity',
                Colors.blue,
              ),
              _buildStatItem(
                Icons.device_thermostat,
                '${(_batteryTemp - _ambientTemp).toStringAsFixed(1)}°C',
                'Temp delta',
                Colors.red,
              ),
            ],
          ),
          const SizedBox(height: 8),
          Center(
            child: Text(
              'Battery is ${(_batteryTemp - _ambientTemp).toStringAsFixed(1)}°C hotter than ambient',
              style: TextStyle(
                fontSize: 12,
                color: Colors.blue.shade700,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLoggingInfoCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.teal.shade50,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.teal.shade100),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Logging Method',
            style: TextStyle(
              fontWeight: FontWeight.bold,
              fontSize: 14,
            ),
          ),
          const SizedBox(height: 8),
          _buildInfoRow(
            Icons.alarm,
            'Alarm every 15 min active / 30 min idle',
          ),
          _buildInfoRow(
            Icons.notifications_active,
            _isCharging
                ? 'Foreground service active (charging)'
                : 'Foreground service inactive',
          ),
          _buildInfoRow(
            Icons.bolt,
            'Event logging: charge start/stop, screen on/off',
          ),
          _buildInfoRow(
            Icons.restart_alt,
            'Boot receiver: restarts after phone reboot',
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(IconData icon, String text) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          Icon(icon, size: 14, color: Colors.teal),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatsCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.grey.shade200,
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Data Collection',
            style: TextStyle(
              fontWeight: FontWeight.bold,
              fontSize: 16,
            ),
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceAround,
            children: [
              _buildStatItem(
                Icons.storage,
                '$_totalLogs',
                'Total logs',
                Colors.blue,
              ),
              _buildStatItem(
                Icons.cloud_done,
                '$_uploadedLogs',
                'Uploaded',
                Colors.green,
              ),
              _buildStatItem(
                Icons.pending,
                '${_totalLogs - _uploadedLogs}',
                'Pending',
                Colors.orange,
              ),
            ],
          ),
          const SizedBox(height: 8),
          Center(
            child: Text(
              'Last manual log: $_lastLogTime',
              style: const TextStyle(
                fontSize: 12,
                color: Colors.grey,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatItem(
    IconData icon,
    String value,
    String label,
    Color color,
  ) {
    return Column(
      children: [
        Icon(icon, color: color, size: 28),
        const SizedBox(height: 4),
        Text(
          value,
          style: TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 14,
            color: color,
          ),
        ),
        Text(
          label,
          style: const TextStyle(
            fontSize: 11,
            color: Colors.grey,
          ),
        ),
      ],
    );
  }

  Widget _buildManualLogButton() {
    return SizedBox(
      width: double.infinity,
      height: 50,
      child: ElevatedButton.icon(
        onPressed: _manualLogging ? null : _manualLog,
        icon: _manualLogging
            ? const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(
                  color: Colors.white,
                  strokeWidth: 2,
                ),
              )
            : const Icon(Icons.add_chart),
        label: Text(_manualLogging ? 'Saving...' : 'Log Now (Test)'),
        style: ElevatedButton.styleFrom(
          backgroundColor: Colors.blue,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      ),
    );
  }

  Widget _buildUploadButton() {
    return SizedBox(
      width: double.infinity,
      height: 50,
      child: ElevatedButton.icon(
        onPressed: _uploading ? null : _uploadNow,
        icon: _uploading
            ? const SizedBox(
                width: 18,
                height: 18,
                child: CircularProgressIndicator(
                  color: Colors.white,
                  strokeWidth: 2,
                ),
              )
            : const Icon(Icons.cloud_upload),
        label: Text(_uploading ? 'Uploading...' : 'Upload to Server'),
        style: ElevatedButton.styleFrom(
          backgroundColor: Colors.green,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      ),
    );
  }

  Widget _buildToggleButton() {
    return SizedBox(
      width: double.infinity,
      height: 50,
      child: ElevatedButton.icon(
        onPressed: _toggleLogging,
        icon: Icon(
          _loggingActive ? Icons.pause : Icons.play_arrow,
        ),
        label: Text(
          _loggingActive ? 'Pause Logging' : 'Resume Logging',
        ),
        style: ElevatedButton.styleFrom(
          backgroundColor: _loggingActive ? Colors.orange : Colors.teal,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
        ),
      ),
    );
  }

  void _showInfo() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('About This Study'),
        content: const Text(
          'This app is part of a battery lifespan research project '
          'at SRM Institute of Science and Technology.\n\n'
          'Data collected is used purely for academic research '
          'and is fully anonymized.\n\n'
          'Contact: your-email@srmist.edu.in',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}