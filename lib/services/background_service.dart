import 'package:flutter/services.dart';
import 'package:workmanager/workmanager.dart';
import 'upload_service.dart';

const String uploadTask = 'uploadTask';

@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    if (task == uploadTask) {
      await _uploadPendingLogs();
    }
    return Future.value(true);
  });
}

Future<void> _uploadPendingLogs() async {
  try {
    final uploadService = UploadService();
    await uploadService.uploadPendingLogs();
  } catch (e) {
    print('Error uploading: $e');
  }
}

class BackgroundService {
  static const MethodChannel _channel = MethodChannel('battery_study/battery');

  static Future<void> startLogging() async {
    try {
      await _channel.invokeMethod('startLogging');
    } catch (e) {
      print('Error starting: $e');
    }
    await Workmanager().registerPeriodicTask(
      uploadTask,
      uploadTask,
      frequency: const Duration(minutes: 30),
      existingWorkPolicy: ExistingPeriodicWorkPolicy.replace,
      constraints: Constraints(networkType: NetworkType.connected),
    );
  }

  static Future<void> stopLogging() async {
    try {
      await _channel.invokeMethod('stopLogging');
    } catch (e) {
      print('Error stopping: $e');
    }
    await Workmanager().cancelByUniqueName(uploadTask);
  }

  static Future<int> getTotalLogs() async {
    try {
      final int count = await _channel.invokeMethod('getTotalLogs');
      return count;
    } catch (e) {
      return 0;
    }
  }

  static Future<int> getUploadedCount() async {
    try {
      final int count = await _channel.invokeMethod('getUploadedCount');
      return count;
    } catch (e) {
      return 0;
    }
  }

  static Future<void> triggerManualLog() async {
    try {
      await _channel.invokeMethod('manualLog');
    } catch (e) {
      print('Error manual log: $e');
    }
  }

  static Future<bool> uploadNow() async {
    try {
      final uploadService = UploadService();
      return await uploadService.uploadPendingLogs();
    } catch (e) {
      return false;
    }
  }
}
