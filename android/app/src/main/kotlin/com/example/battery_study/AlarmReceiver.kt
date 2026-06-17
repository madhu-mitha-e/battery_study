package com.example.battery_study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("AlarmReceiver", "Alarm fired")

        // RESURRECT the foreground service every time the alarm fires
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
                Log.e("AlarmReceiver", "Error: ${e.message}")
            } finally {
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

        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        val status = batteryStatus?.getIntExtra(
            BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        if (isCharging) {
            Log.d("AlarmReceiver", "Charging — foreground service active, skipping alarm log")
            return
        }

        val userId = prefs.getString("flutter.user_id", "UNKNOWN") ?: "UNKNOWN"
        val cityName = prefs.getString("flutter.city_name", "Chennai") ?: "Chennai"

        val data = BatteryHelper.getBatteryData(context)
        val weatherData = WeatherHelper.fetchWeather(cityName)

        if (weatherData.success) {
            prefs.edit()
                .putFloat("cached_ambient_temp",
                    weatherData.ambientTemperature.toFloat())
                .putFloat("cached_humidity",
                    weatherData.humidity.toFloat())
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
        Log.d("AlarmReceiver", "Logged SoC:${data.soc}% " +
                "HealthState:${data.batteryHealthState}")
    }

    companion object {
        private const val ALARM_REQUEST_CODE = 1001
        private const val IDLE_INTERVAL_MS = 15 * 60 * 1000L

        fun scheduleNextAlarm(context: Context) {
            val alarmManager = context.getSystemService(
                Context.ALARM_SERVICE) as AlarmManager
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
            Log.d("AlarmReceiver", "Next alarm in 15 min")
        }

        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(
                Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d("AlarmReceiver", "Alarm cancelled")
        }
    }
}