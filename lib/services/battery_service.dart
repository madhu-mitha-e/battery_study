import 'package:battery_plus/battery_plus.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/services.dart';

class BatteryService {
  static final Battery _battery = Battery();
  static final DeviceInfoPlugin _deviceInfo = DeviceInfoPlugin();
  static const MethodChannel _channel = MethodChannel('battery_study/battery');

  Future<Map<String, dynamic>> getBatteryData() async {
    final batteryLevel = await _battery.batteryLevel;
    final batteryState = await _battery.batteryState;

    String chargingStatus;
    bool isCharging;
    String chargingSource = 'UNKNOWN';

    switch (batteryState) {
      case BatteryState.charging:
        chargingStatus = 'CHARGING';
        isCharging = true;
        chargingSource = 'AC';
        break;
      case BatteryState.full:
        chargingStatus = 'FULL';
        isCharging = true;
        chargingSource = 'AC';
        break;
      case BatteryState.discharging:
        chargingStatus = 'DISCHARGING';
        isCharging = false;
        break;
      default:
        chargingStatus = 'UNKNOWN';
        isCharging = false;
    }

    double temperature = await _getBatteryTemperature();
    int voltage = await _getBatteryVoltage();
    bool screenOn = await _getScreenStatus();

    return {
      'batterySoc': batteryLevel,
      'batteryTemperatureC': temperature,
      'batteryVoltageMv': voltage,
      'chargingStatus': chargingStatus,
      'chargingSource': chargingSource,
      'isCharging': isCharging,
      'screenOn': screenOn,
    };
  }

  Future<Map<String, dynamic>> getDeviceInfo() async {
    try {
      final androidInfo = await _deviceInfo.androidInfo;
      return {
        'deviceBrand': androidInfo.brand,
        'deviceModel': androidInfo.model,
        'osVersion': 'Android ${androidInfo.version.release}',
      };
    } catch (e) {
      return {
        'deviceBrand': 'Unknown',
        'deviceModel': 'Unknown',
        'osVersion': 'Unknown',
      };
    }
  }

  Future<double> _getBatteryTemperature() async {
    try {
      final double temp = await _channel.invokeMethod('getBatteryTemperature');
      return temp;
    } catch (e) {
      return 0.0;
    }
  }

  Future<int> _getBatteryVoltage() async {
    try {
      final int voltage = await _channel.invokeMethod('getBatteryVoltage');
      return voltage;
    } catch (e) {
      return 0;
    }
  }

  Future<bool> _getScreenStatus() async {
    try {
      final bool screenOn = await _channel.invokeMethod('getScreenStatus');
      return screenOn;
    } catch (e) {
      return true;
    }
  }
}