package com.example.battery_study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm fired")

        // Resurrect foreground service every alarm fire — self-heals if Vivo killed it
        try {
            val serviceIntent = Intent(context, LoggingService::class.java)
            context.startForegroundService(serviceIntent)
            Log.d("AlarmReceiver", "Foreground service restart triggered")
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Service restart failed: ${e.message}")
        }

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                logBatteryData(context)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Log error: ${e.message}")
            }
            try {
                uploadPendingLogs(context)
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Upload error: ${e.message}")
            } finally {
                // Always reschedule — this is the heartbeat of the entire system
                scheduleNextAlarm(context)
            }
        }
    }

    private fun logBatteryData(context: Context) {
        val prefs = context.getSharedPreferences(
            "FlutterSharedPreferences", Context.MODE_PRIVATE
        )
        val loggingActive = prefs.getBoolean("flutter.logging_active", true)
        if (!loggingActive) return

        // Skip logging if charging — foreground service handles that path
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            Log.d("AlarmReceiver", "Charging — foreground service handles logging, skipping alarm log")
            return
        }

        val userId = prefs.getString("flutter.user_id", "UNKNOWN") ?: "UNKNOWN"
        val cityName = prefs.getString("flutter.city_name", "Chennai") ?: "Chennai"

        val data = BatteryHelper.getBatteryData(context)
        val weatherData = WeatherHelper.fetchWeather(cityName)

        if (weatherData.success) {
            prefs.edit()
                .putFloat("cached_ambient_temp", weatherData.ambientTemperature.toFloat())
                .putFloat("cached_humidity", weatherData.humidity.toFloat())
                .apply()
        }

        val ambientTemp = if (weatherData.success) weatherData.ambientTemperature
        else prefs.getFloat("cached_ambient_temp", -1.0f).toDouble()

        val humidity = if (weatherData.success) weatherData.humidity
        else prefs.getFloat("cached_humidity", -1.0f).toDouble()

        val db = BatteryDatabase(context)
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
            ambientTemp = ambientTemp,
            humidity = humidity,
            logSource = "ALARM",
            chargingCurrentMa = data.chargingCurrentMa,
            remainingCapacityMah = data.remainingCapacityMah,
            batteryHealthPercent = data.batteryHealthPercent,
            batteryHealthState = data.batteryHealthState,
            deviceBrand = data.deviceBrand,
            deviceModel = data.deviceModel,
            osVersion = data.osVersion
        )
        Log.d("AlarmReceiver", "Logged SoC:${data.soc}% HealthState:${data.batteryHealthState}")
    }

    private fun uploadPendingLogs(context: Context) {
        val db = BatteryDatabase(context)
        val logs = db.getUnuploadedLogs()

        if (logs.isEmpty()) {
            Log.d("AlarmReceiver", "No pending logs to upload")
            return
        }

        Log.d("AlarmReceiver", "Uploading ${logs.size} pending logs")

        try {
            val logsArray = JSONArray()
            val idsToMark = mutableListOf<Int>()

            for (log in logs) {
                val obj = JSONObject()
                obj.put("userId", log["userId"] ?: "UNKNOWN")
                obj.put("deviceBrand", log["deviceBrand"] ?: "")
                obj.put("deviceModel", log["deviceModel"] ?: "")
                obj.put("osVersion", log["osVersion"] ?: "")
                obj.put("batterySoc", (log["batterySoc"] as? String)?.toIntOrNull() ?: -1)
                obj.put("batteryTemperatureC", (log["batteryTemperatureC"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("batteryVoltageMv", (log["batteryVoltageMv"] as? String)?.toIntOrNull() ?: -1)
                obj.put("chargingStatus", log["chargingStatus"] ?: "UNKNOWN")
                obj.put("chargingSource", log["chargingSource"] ?: "UNKNOWN")
                obj.put("isCharging", (log["isCharging"] as? String)?.toIntOrNull() ?: -1)
                obj.put("screenOn", (log["screenOn"] as? String)?.toIntOrNull() ?: -1)
                obj.put("chargingCurrentMa", (log["chargingCurrentMa"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("remainingCapacityMah", (log["remainingCapacityMah"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("batteryHealthPercent", (log["batteryHealthPercent"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("batteryHealthState", log["batteryHealthState"] ?: "UNKNOWN")
                obj.put("timestamp", log["timestamp"] ?: "")
                obj.put("ambientTemperatureC", (log["ambientTemperatureC"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("humidity", (log["humidity"] as? String)?.toDoubleOrNull() ?: -1.0)
                obj.put("cityName", log["cityName"] ?: "")
                obj.put("logSource", log["logSource"] ?: "UNKNOWN")
                logsArray.put(obj)

                (log["id"] as? String)?.toIntOrNull()?.let { idsToMark.add(it) }
            }

            val payload = JSONObject()
            payload.put("logs", logsArray)

            // 60s timeouts — critical for Render free tier cold starts (can take 30-50s)
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val body = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://battery-backend-t82v.onrender.com/logs")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    db.markAsUploaded(idsToMark)
                    Log.d("AlarmReceiver", "Uploaded ${idsToMark.size} logs successfully")
                } else {
                    Log.e("AlarmReceiver", "Upload failed: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmReceiver", "Upload exception: ${e.message}")
        }
    }

    companion object {
        private const val ALARM_REQUEST_CODE = 1001
        private const val IDLE_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        fun scheduleNextAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, AlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerTime = System.currentTimeMillis() + IDLE_INTERVAL_MS
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
                Log.d("AlarmReceiver", "Next alarm scheduled in 15 min")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to schedule alarm: ${e.message}")
            }
        }

        fun cancelAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, AlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    ALARM_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
                Log.d("AlarmReceiver", "Alarm cancelled")
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Failed to cancel alarm: ${e.message}")
            }
        }
    }
}