import 'dart:convert';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

class UploadService {
  static const String baseUrl = 'https://battery-backend-t82v.onrender.com';
  static const MethodChannel _channel = MethodChannel('battery_study/battery');

  Future<bool> uploadPendingLogs() async {
    try {
      final List<dynamic> logs = await _channel.invokeMethod(
        'getUnuploadedLogs',
      );

      if (logs.isEmpty) {
        print('No pending logs');
        return true;
      }

      final List<Map<String, dynamic>> logsJson = logs
          .map((log) => Map<String, dynamic>.from(log))
          .toList();

      final response = await http
          .post(
            Uri.parse('$baseUrl/logs'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({'logs': logsJson}),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200 || response.statusCode == 201) {
        final List<dynamic> ids = logs
            .map((log) => int.parse(log['id'].toString()))
            .toList();
        await _channel.invokeMethod('markLogsUploaded', {'ids': ids});
        print('Uploaded ${logs.length} logs');
        return true;
      }
      return false;
    } catch (e) {
      print('Upload error: $e');
      return false;
    }
  }

  Future<bool> testConnection() async {
    try {
      final response = await http
          .get(Uri.parse('$baseUrl/health'))
          .timeout(const Duration(seconds: 5));
      return response.statusCode == 200;
    } catch (e) {
      return false;
    }
  }
}
