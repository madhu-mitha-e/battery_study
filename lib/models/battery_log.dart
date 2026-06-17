class BatteryLog {
  final int? id;
  final String userId;
  final String deviceBrand;
  final String deviceModel;
  final String osVersion;
  final int batterySoc;
  final double batteryTemperatureC;
  final int batteryVoltageMv;
  final String chargingStatus;
  final String chargingSource;
  final bool isCharging;
  final bool screenOn;
  final String timestamp;
  final double ambientTemperatureC;
  final double humidity;
  final String cityName;
  final bool isUploaded;

  BatteryLog({
    this.id,
    required this.userId,
    required this.deviceBrand,
    required this.deviceModel,
    required this.osVersion,
    required this.batterySoc,
    required this.batteryTemperatureC,
    required this.batteryVoltageMv,
    required this.chargingStatus,
    required this.chargingSource,
    required this.isCharging,
    required this.screenOn,
    required this.timestamp,
    required this.ambientTemperatureC,
    required this.humidity,
    required this.cityName,
    required this.isUploaded,
  });

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'userId': userId,
      'deviceBrand': deviceBrand,
      'deviceModel': deviceModel,
      'osVersion': osVersion,
      'batterySoc': batterySoc,
      'batteryTemperatureC': batteryTemperatureC,
      'batteryVoltageMv': batteryVoltageMv,
      'chargingStatus': chargingStatus,
      'chargingSource': chargingSource,
      'isCharging': isCharging ? 1 : 0,
      'screenOn': screenOn ? 1 : 0,
      'timestamp': timestamp,
      'ambientTemperatureC': ambientTemperatureC,
      'humidity': humidity,
      'cityName': cityName,
      'isUploaded': isUploaded ? 1 : 0,
    };
  }

  factory BatteryLog.fromMap(Map<String, dynamic> map) {
    return BatteryLog(
      id: map['id'],
      userId: map['userId'],
      deviceBrand: map['deviceBrand'],
      deviceModel: map['deviceModel'],
      osVersion: map['osVersion'],
      batterySoc: map['batterySoc'],
      batteryTemperatureC: map['batteryTemperatureC'],
      batteryVoltageMv: map['batteryVoltageMv'],
      chargingStatus: map['chargingStatus'],
      chargingSource: map['chargingSource'],
      isCharging: map['isCharging'] == 1,
      screenOn: map['screenOn'] == 1,
      timestamp: map['timestamp'],
      ambientTemperatureC: map['ambientTemperatureC'],
      humidity: map['humidity'],
      cityName: map['cityName'],
      isUploaded: map['isUploaded'] == 1,
    );
  }
}