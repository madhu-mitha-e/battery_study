package com.example.battery_study

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "battery_study/battery"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "getBatteryTemperature" -> {
                    val data = BatteryHelper.getBatteryData(this)
                    result.success(data.temperature)
                }

                "getBatteryVoltage" -> {
                    val data = BatteryHelper.getBatteryData(this)
                    result.success(data.voltage)
                }

                "getScreenStatus" -> {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    result.success(pm.isInteractive)
                }

                "startLogging" -> {
                    startLogging()
                    result.success(true)
                }

                "stopLogging" -> {
                    stopLogging()
                    result.success(true)
                }

                "getTotalLogs" -> {
                    val db = BatteryDatabase(this)
                    result.success(db.getTotalCount())
                }

                "getUploadedCount" -> {
                    val db = BatteryDatabase(this)
                    result.success(db.getUploadedCount())
                }

                "manualLog" -> {
                    manualLog()
                    result.success(true)
                }

                "getUnuploadedLogs" -> {
                    val db = BatteryDatabase(this)
                    val logs = db.getUnuploadedLogs()
                    result.success(logs)
                }

                "markLogsUploaded" -> {
                    val ids = call.argument<List<Int>>("ids") ?: emptyList()
                    val db = BatteryDatabase(this)
                    db.markAsUploaded(ids)
                    result.success(true)
                }

                else -> result.notImplemented()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val prefs = getSharedPreferences(
            "FlutterSharedPreferences",
            Context.MODE_PRIVATE
        )

        val onboardingDone = prefs.getBoolean("flutter.onboarding_done", false)
        val loggingActive = prefs.getBoolean("flutter.logging_active", true)

        if (onboardingDone && loggingActive) {
            AlarmReceiver.scheduleNextAlarm(this)
            val serviceIntent = Intent(this, LoggingService::class.java)
            startForegroundService(serviceIntent)
        }
    }

    private fun startLogging() {
        Log.d("MainActivity", "Starting 24/7 logging")
        AlarmReceiver.scheduleNextAlarm(this)
        val serviceIntent = Intent(this, LoggingService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun stopLogging() {
        Log.d("MainActivity", "Stopping logging")
        AlarmReceiver.cancelAlarm(this)
        val serviceIntent = Intent(this, LoggingService::class.java)
        serviceIntent.action = "STOP"
        startService(serviceIntent)
    }

    private fun manualLog() {
        val prefs = getSharedPreferences(
            "FlutterSharedPreferences",
            Context.MODE_PRIVATE
        )

        val userId = prefs.getString("flutter.user_id", "UNKNOWN") ?: "UNKNOWN"
        val cityName = prefs.getString("flutter.city_name", "Chennai") ?: "Chennai"

        val data = BatteryHelper.getBatteryData(this)

        val cachedAmbientTemp = prefs.getFloat(
            "cached_ambient_temp",
            -1.0f
        ).toDouble()

        val cachedHumidity = prefs.getFloat(
            "cached_humidity",
            -1.0f
        ).toDouble()

        val db = BatteryDatabase(this)
        db.insertLog(
            userId = userId,
            soc = data.soc,
            temperature = data.temperature,
            voltage = data.voltage,
            chargingStatus = data.chargingStatus,
            chargingSource = data.chargingSource,
            isCharging = data.isCharging,
            screenOn = data.screenOn,
            cityName = cityName,
            ambientTemp = cachedAmbientTemp,
            humidity = cachedHumidity,
            logSource = "MANUAL",
            chargingCurrentMa = data.chargingCurrentMa,
            remainingCapacityMah = data.remainingCapacityMah,
            batteryHealthPercent = data.batteryHealthPercent,
            batteryHealthState = data.batteryHealthState,
            deviceBrand = data.deviceBrand,
            deviceModel = data.deviceModel,
            osVersion = data.osVersion
        )

        Log.d(
            "MainActivity",
            "Manual log SoC:${data.soc}% HealthState:${data.batteryHealthState}"
        )
    }

    private fun isPhoneCharging(): Boolean {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, intentFilter)

        val status = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            -1
        ) ?: -1

        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }
}