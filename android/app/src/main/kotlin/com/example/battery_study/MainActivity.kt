package com.example.battery_study

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
            // Request battery optimization exemption every time app opens
            // until the user grants it — this is the #1 fix for Vivo App Freezer
            requestBatteryOptimizationExemption()

            // Re-arm the alarm chain (self-heals if Vivo froze+cancelled the alarm)
            AlarmReceiver.scheduleNextAlarm(this)

            // Ensure foreground service is alive
            val serviceIntent = Intent(this, LoggingService::class.java)
            startForegroundService(serviceIntent)

            Log.d("MainActivity", "onResume: alarm re-armed, service restarted")
        }
    }

    /**
     * Requests Android's battery optimization exemption via the system dialog.
     * This is the AOSP-level whitelist that Vivo's App Freezer respects,
     * unlike the generic "allow background activity" toggle in iManager.
     * Shows the dialog only if not already exempted.
     */
    private fun requestBatteryOptimizationExemption() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Log.d("MainActivity", "Requesting battery optimization exemption")
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Log.d("MainActivity", "Battery optimization already exempted")
            }
        } catch (e: Exception) {
            // Some OEM skins block this intent — fail silently, don't crash
            Log.e("MainActivity", "Battery exemption request failed: ${e.message}")
        }
    }

    private fun startLogging() {
        Log.d("MainActivity", "Starting 24/7 logging")
        requestBatteryOptimizationExemption()
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
            "cached_ambient_temp", -1.0f
        ).toDouble()

        val cachedHumidity = prefs.getFloat(
            "cached_humidity", -1.0f
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
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }
}